package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.advancement.Beats;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.pouch.PouchEntries.Entry;
import jp.revati.skyabyss.record.KeepsakeBonds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 留め袋の API (docs/design/22 の 8 章)。count / add / take / withdraw / sweep。
// 袋に入るのは PouchEntries にある品で、部品のない (その品のままの) 品だけ。ほかの部品 (強化・名前・特性など) のある品は入れず、持ち物に残す。
// 天の鍵の欠片・天の鍵は持ち主の印 (KeepsakeBonds) を外して数だけにし、出すときに今の代の印を付け直す
public final class Pouch {
    // 品ごとの「初めて入った」知らせを止める (墓から取り戻すときなど。サーバーの主の糸だけで使う)
    public static boolean quiet;

    private Pouch() {
    }

    public static PouchData data(Player player) {
        return player.getData(PouchSetup.DATA);
    }

    // 持ち主のクライアントへ送る (サーバーだけ。作り物のプレイヤーは送らない)
    public static void sync(Player player) {
        if (player instanceof ServerPlayer sp && !(player instanceof FakePlayer) && sp.connection != null) {
            sp.syncData(PouchSetup.DATA);
        }
    }

    // ---------------------------------------------------------------- 数える
    // 袋の中の数 (クライアントでも同期された値を読める)
    public static int count(Player player, Item item) {
        Entry e = PouchEntries.any(item);
        return e == null ? 0 : data(player).count(e.id());
    }

    // 袋 + 持ち物 (メイン・防具・左手) の数。強化・交換・振り直しの費用の足りるかを見る数え方
    public static int countAll(Player player, Item item) {
        return count(player, item) + countInventory(player.getInventory(), item);
    }

    public static int countInventory(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // 袋を空にする (管理者・自己テスト用。いちど入れた印・取り出しの印は残す)
    public static void clear(Player player) {
        data(player).clear();
        sync(player);
    }

    // ---------------------------------------------------------------- 入れる
    // 袋へ入れてよい品か (登録にあり、部品が付いていない。欠片・天の鍵は、ほかの人の品・前の代の品でない)
    public static boolean storable(Player player, ItemStack stack) {
        Entry e = PouchEntries.entry(stack);
        return e != null && storable(player, stack, e);
    }

    private static boolean storable(Player player, ItemStack stack, Entry e) {
        if (stack.isEmpty()) {
            return false;
        }
        if (e.bonded() && !(player instanceof ServerPlayer sp && KeepsakeBonds.acceptable(sp, stack))) {
            return false;
        }
        return plain(stack, e).getComponentsPatch().isEmpty();
    }

    // 印 (取り出し済み・持ち主) を外した写し。部品が残っていなければ「その品のまま」
    private static ItemStack plain(ItemStack stack, Entry e) {
        ItemStack copy = stack.copy();
        copy.remove(PouchSetup.LOAN.get());
        if (e.bonded()) {
            KeepsakeBonds.stripBond(copy);
        }
        return copy;
    }

    // 入る分だけ袋へ入れ、stack を入れた数だけ減らす。入れた数を返す (上限・入れられない品は 0)
    public static int add(ServerPlayer player, ItemStack stack) {
        Entry e = PouchEntries.entry(stack);
        if (!PouchConfig.enabled(player.server) || e == null || !storable(player, stack, e)) {
            return 0;
        }
        PouchData d = data(player);
        int have = d.count(e.id());
        int room = e.limit() - have;
        int want = stack.getCount();
        int n = Math.min(room, want);
        if (n <= 0) {
            noteFull(player, stack);
            return 0;
        }
        d.set(e.id(), have + n);
        stack.shrink(n);
        boolean first = d.markSeen(e.id());
        if (first) {
            d.markFresh(e.id());
        }
        sync(player);
        if (first && !(player instanceof FakePlayer)) {
            // 墓からまとめて取り戻すあいだは、品ごとの知らせを出さない (左に並びすぎた。2026-10-21 の試遊)
            if (!quiet) {
                Notice.gain(player, "skyabyss_pouch.first", Component.translatable("skyabyss_pouch.name"), e.item().getDescription());
            }
            Beats.fire(player, "pouch:" + e.id().getPath());
        }
        if (n < want) {
            noteFull(player, stack);
        }
        return n;
    }

    // 「袋がいっぱい」(上限の品は持ち物へそのまま入れる。同じ人へ短い間に繰り返さない)
    private static final Map<Player, Integer> FULL_AT = new WeakHashMap<>();

    private static void noteFull(ServerPlayer player, ItemStack stack) {
        if (player instanceof FakePlayer) {
            return;
        }
        Integer last = FULL_AT.get(player);
        if (last != null && player.tickCount - last < PouchConfig.FULL_NOTICE_COOLDOWN && player.tickCount >= last) {
            return;
        }
        FULL_AT.put(player, player.tickCount);
        Notice.warn(player, "skyabyss_pouch.full", Component.translatable("skyabyss_pouch.name"), stack.getHoverName());
    }

    // ---------------------------------------------------------------- 出す・払う
    // 袋から n 個まで取り除く (品は作らない)。取れた数を返す
    public static int take(Player player, Item item, int n) {
        Entry e = PouchEntries.any(item);
        if (e == null || n <= 0) {
            return 0;
        }
        PouchData d = data(player);
        int have = d.count(e.id());
        int taken = Math.min(have, n);
        if (taken > 0) {
            d.set(e.id(), have - taken);
            sync(player);
        }
        return taken;
    }

    // 費用を払う: 袋から先に、足りない分を持ち物から。払えた数を返す (払いきれなければ、払えた分だけ減らして返す)
    public static int takeAny(Player player, Item item, int n) {
        int taken = take(player, item, n);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && taken < n; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int d = Math.min(n - taken, s.getCount());
                s.shrink(d);
                taken += d;
            }
        }
        if (taken > 0) {
            inv.setChanged();
        }
        return taken;
    }

    // 袋から持ち物へ出す。force でなければ、ゲームルール skyabyssPouchWithdraw と、品の区分 (PouchConfig.WITHDRAW_CATEGORIES) に従う。
    // 出した品には「取り出し済み」の印が付き、掃除が袋へ戻さない。入りきらない分は袋に残す。出せた数を返す
    public static int withdraw(ServerPlayer player, Item item, int n, boolean force) {
        Entry e = PouchEntries.any(item);
        if (e == null || n <= 0) {
            return 0;
        }
        if (!force && (!PouchConfig.withdrawEnabled(player.server) || !e.withdrawable())) {
            return 0;
        }
        PouchData d = data(player);
        int want = Math.min(n, d.count(e.id()));
        int moved = 0;
        while (want > 0) {
            int c = Math.min(want, Math.max(1, item.getDefaultMaxStackSize()));
            ItemStack s = new ItemStack(item, c);
            s.set(PouchSetup.LOAN.get(), Unit.INSTANCE);
            if (e.bonded()) {
                KeepsakeBonds.bindFor(player, s);
            }
            player.getInventory().add(s);
            int placed = c - s.getCount();
            moved += placed;
            want -= c;
            if (placed < c) {
                break;
            }
        }
        if (moved > 0) {
            d.set(e.id(), d.count(e.id()) - moved);
            sync(player);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return moved;
    }

    // 画面のカーソルへ出す分: 取り出しの決まりに従い、一スタックまで (n 個まで) を袋から外して、「取り出し済み」の印つきの品を返す。出せなければ空
    public static ItemStack takeOut(ServerPlayer player, Item item, int n, boolean force) {
        Entry e = PouchEntries.any(item);
        if (e == null || n <= 0 || (!force && !canWithdraw(player, e))) {
            return ItemStack.EMPTY;
        }
        int c = Math.min(Math.min(n, data(player).count(e.id())), Math.max(1, item.getDefaultMaxStackSize()));
        if (c <= 0) {
            return ItemStack.EMPTY;
        }
        data(player).set(e.id(), data(player).count(e.id()) - c);
        sync(player);
        ItemStack s = new ItemStack(item, c);
        s.set(PouchSetup.LOAN.get(), Unit.INSTANCE);
        if (e.bonded()) {
            KeepsakeBonds.bindFor(player, s);
        }
        return s;
    }

    // いまの決まり (ゲームルール・区分) で、この品を取り出せるか
    public static boolean canWithdraw(ServerPlayer player, Entry e) {
        return PouchConfig.withdrawEnabled(player.server) && e.withdrawable();
    }

    // ---------------------------------------------------------------- 掃除
    // 持ち物 (メイン・左手) の袋の品を袋へ移す。印のある (取り出し済みの) 品は移さない。移した数を返す
    public static int sweep(ServerPlayer player) {
        if (data(player).hold) {
            return 0;
        }
        int moved = 0;
        Inventory inv = player.getInventory();
        for (List<ItemStack> part : List.of(inv.items, inv.offhand)) {
            for (ItemStack s : part) {
                moved += sweepStack(player, s);
            }
        }
        if (moved > 0) {
            inv.setChanged();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
        return moved;
    }

    // 画面を閉じた直後: 画面からもどった品やカーソルの品も含めて掃除する
    static int sweepAfterClose(ServerPlayer player) {
        int moved = sweep(player);
        ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        int c = data(player).hold ? 0 : sweepStack(player, carried);
        if (c > 0) {
            player.containerMenu.broadcastChanges();
        }
        return moved + c;
    }

    private static int sweepStack(ServerPlayer player, ItemStack s) {
        if (s.isEmpty() || s.has(PouchSetup.LOAN.get()) || PouchEntries.entry(s) == null) {
            return 0;
        }
        return add(player, s);
    }

    // ---------------------------------------------------------------- 袋の一覧 (確かめ用)
    public static List<String> describe(Player player) {
        List<String> lines = new ArrayList<>();
        for (Entry e : PouchEntries.enabled()) {
            int n = data(player).count(e.id());
            if (n > 0) {
                lines.add(e.id() + " x" + n + " (" + e.category().id + ")");
            }
        }
        return lines;
    }
}
