package jp.revati.skyabyss.facility;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.pouch.PouchConfig;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.record.Keepsakes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 預かり所 (docs/design/35 の 3.1、00 の 225)。人ごとの 54 枠 (StashData)。どの預かり番から開いても同じ中身。
// 画面はバニラの大きな箱 (generic_9x6。絵柄は 18 の 3.2 の同梱のパック)。旅嚢の大事な品 (笛・鍵・心核・書) と、守る品 (Keepsakes) は預けられない
public final class StashMenu extends ChestMenu {
    @Nullable
    private final Entity keeper;

    private StashMenu(int id, Inventory inventory, Container stash, @Nullable Entity keeper) {
        super(MenuType.GENERIC_9x6, id, inventory, stash, 6);
        this.keeper = keeper;
        // 預かりの枠を、預けられない品を弾く枠に差し替える (位置と番号は同じ)
        for (int i = 0; i < StashData.SIZE; i++) {
            Slot old = slots.get(i);
            Slot s = new Slot(stash, old.getContainerSlot(), old.x, old.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return allowed(stack);
                }
            };
            s.index = i;
            slots.set(i, s);
        }
    }

    // 預けられる品か (旅嚢の「取り出せない」区分 = 道具・鍵と心核・書、守る品は不可)
    public static boolean allowed(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (Keepsakes.guardedItems().contains(stack.getItem())) {
            return false;
        }
        PouchEntries.Entry e = PouchEntries.any(stack.getItem());
        return e == null || PouchConfig.WITHDRAW_CATEGORIES.contains(e.category());
    }

    public static void open(ServerPlayer player, @Nullable Entity keeper) {
        StashData data = player.getData(FacilitySetup.STASH);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StashMenu(id, inv, new StashContainer(data), keeper),
                Component.translatable("gui.skyabyss.stash.title")));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && (keeper == null || keeper.isAlive() && keeper.distanceToSqr(player) <= 8 * 8);
    }

    // StashData の枠をそのまま箱として見せる (書き換えはそのまま人の記録に残る)
    static final class StashContainer implements Container {
        private final StashData data;

        StashContainer(StashData data) {
            this.data = data;
        }

        @Override
        public int getContainerSize() {
            return StashData.SIZE;
        }

        @Override
        public boolean isEmpty() {
            return data.items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return data.items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack s = data.items.get(slot);
            return s.isEmpty() ? ItemStack.EMPTY : s.split(amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack s = data.items.get(slot);
            data.items.set(slot, ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            data.items.set(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return allowed(stack);
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < StashData.SIZE; i++) {
                data.items.set(i, ItemStack.EMPTY);
            }
        }
    }
}
