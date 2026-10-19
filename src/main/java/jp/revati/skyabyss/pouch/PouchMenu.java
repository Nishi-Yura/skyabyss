package jp.revati.skyabyss.pouch;

import java.util.List;

import jp.revati.skyabyss.pouch.PouchEntries.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 旅嚢の画面のメニュー (docs/design/22 の 9 章 A つまみ)。品ごとに決まった枠 (固定枠) だけを持ち、持ち物の枠は出さない。
// 枠の中身は袋 (PouchData) の個数を直接読む見かけ上の品で、中身の入れ替えはぜんぶこのメニューの clicked が決める (サーバーが決め、クライアントは何もしない):
//   - カーソルに品を持って枠を左クリック = その品が袋の品なら全部入れる、右クリック = 一つ入れる (枠と違う品は入らない)
//   - カーソルが空で枠を左クリック = 一スタック取り出す、右クリック = その半分 (取り出し可のとき。ゲームルール skyabyssPouchWithdraw が 0 なら何も起きない)
//   - シフトクリック = 取り出して持ち物へ (取り出し可のとき)
//   - 上限 (999 など) まで。取り出した品には「取り出し済み」の印が付き、掃除が袋へ戻さない。拾い直す・袋へ置くと外れる
// 「作り直し」と「修理」はボタン (clickMenuButton) で、PouchRebuild が決める
public class PouchMenu extends AbstractContainerMenu {
    private final Player player;
    private final PouchLayout.Result layout;
    // 取り出せるか (サーバー → クライアント。ゲームルールはクライアントにないので 1 つの値で送る)
    private final DataSlot withdrawFlag = DataSlot.standalone();
    // 作り直しの一覧を見ているか (クライアントの画面だけの状態。見ている間は枠を隠して押せなくする)
    public boolean rebuildView;

    public PouchMenu(int id, Inventory inventory) {
        super(PouchSetup.MENU.get(), id);
        this.player = inventory.player;
        this.layout = PouchLayout.compute(PouchEntries.enabled());
        Container view = new View(player, layout.spots());
        for (int i = 0; i < layout.spots().size(); i++) {
            PouchLayout.Spot spot = layout.spots().get(i);
            addSlot(new PouchSlot(view, i, spot.x(), spot.y()));
        }
        addDataSlot(withdrawFlag);
        refreshFlag();
    }

    public PouchLayout.Result layout() {
        return layout;
    }

    public Entry entryAt(int slotIndex) {
        return layout.spots().get(slotIndex).entry();
    }

    public int pouchSlotCount() {
        return layout.spots().size();
    }

    // 画面が枠を「取り出せる」と見せてよいか (サーバーの決まりを写した値)
    public boolean withdrawAllowed() {
        return withdrawFlag.get() > 0;
    }

    private void refreshFlag() {
        if (player instanceof ServerPlayer sp) {
            withdrawFlag.set(PouchConfig.withdrawEnabled(sp.server) ? 1 : 0);
        }
    }

    @Override
    public void broadcastChanges() {
        refreshFlag();
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player p) {
        if (slotId < 0) {
            // 枠の外 (画面の外を押して落とす) は何もしない。袋の品は落とせず、カーソルの品は閉じるときに戻る
            if (clickType != ClickType.PICKUP) {
                super.clicked(slotId, button, clickType, p);
            }
            return;
        }
        if (slotId >= layout.spots().size() || !(p instanceof ServerPlayer sp)) {
            return;
        }
        Entry entry = entryAt(slotId);
        ItemStack carried = getCarried();
        switch (clickType) {
            case PICKUP -> {
                if (!carried.isEmpty()) {
                    deposit(sp, entry, carried, button == 1 ? 1 : carried.getCount());
                } else {
                    int have = Pouch.data(sp).count(entry.id());
                    int stack = Math.max(1, entry.item().getDefaultMaxStackSize());
                    int want = button == 1 ? Math.max(1, Math.min(have, stack) / 2) : stack;
                    ItemStack out = Pouch.takeOut(sp, entry.item(), want, false);
                    if (!out.isEmpty()) {
                        setCarried(out);
                    }
                }
            }
            case QUICK_MOVE -> {
                int stack = Math.max(1, entry.item().getDefaultMaxStackSize());
                Pouch.withdraw(sp, entry.item(), stack, false);
            }
            default -> {
            }
        }
        broadcastChanges();
    }

    // カーソルの品 n 個を袋へ入れる (枠の品と同じものだけ)。入れた数を返す
    public int deposit(ServerPlayer sp, Entry entry, ItemStack carried, int n) {
        if (!carried.is(entry.item()) || n <= 0) {
            return 0;
        }
        ItemStack part = carried.copyWithCount(Math.min(n, carried.getCount()));
        // 取り出し済みの印は、袋へ置くと外れる (Pouch.add が印を無視して数えるため、印を外して入れる)
        part.remove(PouchSetup.LOAN.get());
        int moved = Pouch.add(sp, part);
        carried.shrink(moved);
        if (carried.isEmpty()) {
            setCarried(ItemStack.EMPTY);
        }
        return moved;
    }

    // 作り直し・修理のボタン
    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof ServerPlayer sp)) {
            return false;
        }
        if (id == PouchRebuild.BUTTON_REPAIR) {
            PouchRebuild.repair(sp);
            broadcastChanges();
            return true;
        }
        if (id >= PouchRebuild.BUTTON_RECIPE_BASE) {
            PouchRebuild.rebuild(sp, id - PouchRebuild.BUTTON_RECIPE_BASE);
            broadcastChanges();
            return true;
        }
        return false;
    }

    // シフトクリックは clicked が受ける (枠は袋の品だけで、行き先の枠は無い)
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.isAlive();
    }

    // 枠: 見かけの品を映すだけ。置く・取るは clicked が決めるので、標準の動きは全部断る
    private final class PouchSlot extends Slot {
        PouchSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player p) {
            return false;
        }

        @Override
        public boolean isActive() {
            return !rebuildView;
        }

        @Override
        public int getMaxStackSize() {
            return 999;
        }
    }

    // 袋の個数を映す入れ物。getItem は個数つきの品 (個数 0 は空)。書き込みは無視する (書き込みはメニューの clicked だけ)
    private static final class View implements Container {
        private final Player player;
        private final List<PouchLayout.Spot> spots;

        View(Player player, List<PouchLayout.Spot> spots) {
            this.player = player;
            this.spots = spots;
        }

        @Override
        public int getContainerSize() {
            return spots.size();
        }

        @Override
        public boolean isEmpty() {
            return Pouch.data(player).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            Entry e = spots.get(slot).entry();
            int n = Pouch.data(player).count(e.id());
            return n <= 0 ? ItemStack.EMPTY : new ItemStack(e.item(), n);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
        }

        @Override
        public int getMaxStackSize() {
            return 999;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player p) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }
}
