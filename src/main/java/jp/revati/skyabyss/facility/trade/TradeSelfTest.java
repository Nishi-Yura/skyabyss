package jp.revati.skyabyss.facility.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// 取引所 (docs/design/35 の 6.11) の自己テスト。段: "facility trade"。
//   出す (預かる・10 品まで・空・大事な品)、申請 (品は動かない・二度目・自分の出品・持っていない)、断る (知らせ)、
//   受ける (一度に入れ替え・ほかの申請へ知らせ)、品が足りない・持ち物が一杯 (何も動かず申請は残る)、いない人 (受けられない・知らせは次に入ったとき)、
//   別の申し出 (自分で選んだ品と鐘銭)、取り下げ (品が戻る・一杯なら受け取り箱)、期限切れ (受け取り箱と知らせ)、受け取る、保存と読み直し
public final class TradeSelfTest {
    private TradeSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        TradeData data = TradeData.get(server);
        TradeData saved = data.roundTrip(server.registryAccess());
        BiFunction<MinecraftServer, UUID, ServerPlayer> realOnline = Trades.online;
        Map<UUID, ServerPlayer> here = new HashMap<>();
        Trades.online = (s, u) -> here.get(u);
        try {
            data.clearForTest();
            body(f, server, data, here);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST facility trade crashed", e);
            f.add("crashed: " + e);
        } finally {
            Trades.online = realOnline;
            data.clearForTest();
            data.restoreFrom(saved);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST facility trade PASSED (list/escrow/limits, apply keeps items, reject, accept swap, lacking items, full inventory, "
                    + "offline applicant and notes, counter offer, cancel returns, expiry to mail, claim, save)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST facility trade FAILED: {}", m));
        }
    }

    private static FakePlayer fake(MinecraftServer server, String name) {
        return new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), name));
    }

    private static List<Trades.Pick> slot(int s, int n) {
        return List.of(new Trades.Pick(s, n));
    }

    private static boolean told(UUID who, String key) {
        return Trades.RECENT.contains(who + " message.skyabyss.trade." + key);
    }

    private static void body(List<String> f, MinecraftServer server, TradeData data, Map<UUID, ServerPlayer> here) {
        FakePlayer s = fake(server, "trade_s");
        FakePlayer b = fake(server, "trade_b");
        FakePlayer c = fake(server, "trade_c");
        here.put(s.getUUID(), s);
        here.put(b.getUUID(), b);
        here.put(c.getUUID(), c);
        // ---- 出す: 鉄 10 を出して、ダイヤ 2 と鐘銭 5 がほしい
        s.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        check(f, Trades.list(s, List.of(), List.of(Items.DIAMOND), List.of(2), 5) == Trades.Result.EMPTY_GIVE, "nothing to give refused");
        check(f, Trades.list(s, slot(0, 10), List.of(), List.of(), 0) == Trades.Result.EMPTY_WANT, "wanting nothing refused");
        check(f, Trades.list(s, slot(0, 99), List.of(Items.DIAMOND), List.of(2), 0) == Trades.Result.LACK, "cannot give more than held");
        check(f, Trades.list(s, slot(40, 1), List.of(Items.DIAMOND), List.of(2), 0) == Trades.Result.BAD_SLOT, "bad slot refused");
        check(f, Trades.list(s, slot(0, 10), List.of(Items.DIAMOND), List.of(2), 5) == Trades.Result.LISTED, "list");
        TradeData.Listing l = data.of(s.getUUID()).isEmpty() ? null : data.of(s.getUUID()).get(0);
        if (l == null) {
            f.add("listing missing");
            return;
        }
        check(f, s.getInventory().getItem(0).getCount() == 6 && l.give.size() == 1 && l.give.get(0).getCount() == 10, "seller's items are held in escrow");
        check(f, l.want.size() == 2 && Trades.count(l.want, new ItemStack(Items.DIAMOND)) == 2 && Trades.count(l.want, Trades.coins(1)) == 5,
                "want = diamonds and coins");
        check(f, l.expires - l.created == Trades.EXPIRE_MS, "listing expires in 7 days");
        // 大事な品 (クエストの印の品) は出せない
        ItemStack token = new ItemStack(Items.PAPER);
        net.minecraft.nbt.CompoundTag tg = new net.minecraft.nbt.CompoundTag();
        tg.putString(jp.revati.skyabyss.quest.QuestItems.TOKEN_KEY, "x");
        token.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tg));
        if (jp.revati.skyabyss.quest.QuestItems.token(token) != null) {
            s.getInventory().setItem(1, token);
            check(f, Trades.list(s, slot(1, 1), List.of(Items.DIAMOND), List.of(1), 0) == Trades.Result.NOT_ALLOWED, "quest items cannot be listed");
            s.getInventory().setItem(1, ItemStack.EMPTY);
        }
        // ---- 申請: 品は動かない
        check(f, Trades.apply(s, l.id, null, 0) == Trades.Result.OWN_LISTING, "cannot apply to own listing");
        check(f, Trades.apply(b, l.id, null, 0) == Trades.Result.LACK, "cannot apply without the wanted items");
        b.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        Coins.give(b, 7);
        check(f, Trades.apply(b, l.id, null, 0) == Trades.Result.APPLIED, "apply");
        check(f, b.getInventory().countItem(Items.DIAMOND) == 3 && Coins.count(b) == 7, "applying takes nothing");
        check(f, told(s.getUUID(), "note_applied"), "seller told of the application");
        check(f, Trades.apply(b, l.id, null, 0) == Trades.Result.ALREADY_APPLIED, "one application per listing");
        // ---- 断る
        c.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
        Coins.give(c, 5);
        check(f, Trades.apply(c, l.id, null, 0) == Trades.Result.APPLIED, "second applicant");
        check(f, Trades.reject(b, l.id, c.getUUID()) == Trades.Result.NOT_SELLER, "only the seller rejects");
        here.remove(c.getUUID());
        check(f, Trades.reject(s, l.id, c.getUUID()) == Trades.Result.REJECTED && !l.apps.containsKey(c.getUUID()), "reject");
        check(f, data.notes(c.getUUID()).stream().anyMatch(n -> n.key().endsWith("note_rejected")), "offline applicant gets the rejection later");
        here.put(c.getUUID(), c);
        check(f, Trades.flush(c) >= 1 && data.notes(c.getUUID()).isEmpty(), "notes are shown on login");
        check(f, Trades.apply(c, l.id, null, 0) == Trades.Result.APPLIED, "apply again after rejection");
        // ---- 足りない: b がダイヤを手放したあとに受けようとする
        b.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 1));
        check(f, Trades.accept(s, l.id, b.getUUID()) == Trades.Result.APPLICANT_LACKS && l.apps.containsKey(b.getUUID())
                && data.byId(l.id) != null && s.getInventory().countItem(Items.DIAMOND) == 0, "lacking applicant: nothing moves, application stays");
        b.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        // ---- いない人: 受けられない
        here.remove(b.getUUID());
        check(f, Trades.accept(s, l.id, b.getUUID()) == Trades.Result.APPLICANT_AWAY && data.byId(l.id) != null, "cannot accept an offline applicant");
        here.put(b.getUUID(), b);
        // ---- 持ち物が一杯: 受ける人の側
        List<ItemStack> keep = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            keep.add(b.getInventory().getItem(i).copy());
            if (b.getInventory().getItem(i).isEmpty()) {
                b.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            }
        }
        // ダイヤ 2 は出るが一枠が空くとは限らない (3 → 1)。鐘銭 7 → 2。鉄 10 は入らない
        check(f, Trades.accept(s, l.id, b.getUUID()) == Trades.Result.ROOM_APPLICANT && data.byId(l.id) != null
                && b.getInventory().countItem(Items.DIAMOND) == 3, "full applicant: nothing moves");
        for (int i = 0; i < 36; i++) {
            b.getInventory().setItem(i, keep.get(i));
        }
        // ---- 受ける: 一度に入れ替え
        int sIron = s.getInventory().countItem(Items.IRON_INGOT);
        check(f, Trades.accept(s, l.id, b.getUUID()) == Trades.Result.ACCEPTED, "accept");
        check(f, data.byId(l.id) == null, "listing closes on accept");
        check(f, b.getInventory().countItem(Items.IRON_INGOT) == 10 && b.getInventory().countItem(Items.DIAMOND) == 1 && Coins.count(b) == 2,
                "applicant paid and received " + b.getInventory().countItem(Items.IRON_INGOT) + " " + b.getInventory().countItem(Items.DIAMOND) + " " + Coins.count(b));
        check(f, s.getInventory().countItem(Items.DIAMOND) == 2 && Coins.count(s) == 5 && s.getInventory().countItem(Items.IRON_INGOT) == sIron,
                "seller received the payment");
        check(f, told(s.getUUID(), "note_sold") && told(b.getUUID(), "note_accepted") && told(c.getUUID(), "note_taken"),
                "both told, other applicants told it went to someone else");
        check(f, c.getInventory().countItem(Items.DIAMOND) == 2 && Coins.count(c) == 5, "other applicant keeps everything");
        // ---- 別の申し出: 鉄 3 と鐘銭 1 で、s の新しい出品 (金 4 に ダイヤ 1)
        s.getInventory().setItem(5, new ItemStack(Items.GOLD_INGOT, 4));
        check(f, Trades.list(s, slot(5, 4), List.of(Items.DIAMOND), List.of(1), 0) == Trades.Result.LISTED, "second listing");
        TradeData.Listing l2 = data.of(s.getUUID()).get(0);
        int ironSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (b.getInventory().getItem(i).is(Items.IRON_INGOT)) {
                ironSlot = i;
            }
        }
        check(f, ironSlot >= 0 && Trades.apply(b, l2.id, slot(ironSlot, 3), 1) == Trades.Result.APPLIED, "counter offer");
        TradeData.Application ca = l2.apps.get(b.getUUID());
        check(f, ca != null && Trades.count(ca.offer, new ItemStack(Items.IRON_INGOT)) == 3 && Trades.count(ca.offer, Trades.coins(1)) == 1,
                "counter offer holds the chosen items");
        check(f, Trades.accept(s, l2.id, b.getUUID()) == Trades.Result.ACCEPTED && b.getInventory().countItem(Items.GOLD_INGOT) == 4
                && b.getInventory().countItem(Items.IRON_INGOT) == 7 && Coins.count(b) == 1, "counter offer swap");
        // ---- 取り下げ: 品が戻る (一杯なら受け取り箱)
        s.getInventory().setItem(6, new ItemStack(Items.EMERALD, 5));
        Trades.list(s, slot(6, 5), List.of(Items.APPLE), List.of(1), 0);
        TradeData.Listing l3 = data.of(s.getUUID()).get(0);
        Trades.apply(c, l3.id, slot(0, 1), 0);
        check(f, Trades.withdraw(b, l3.id) == Trades.Result.NO_APPLICATION, "withdraw needs an application");
        check(f, Trades.cancel(b, l3.id) == Trades.Result.NOT_SELLER, "only the seller cancels");
        check(f, Trades.cancel(s, l3.id) == Trades.Result.CANCELLED && s.getInventory().countItem(Items.EMERALD) == 5 && data.byId(l3.id) == null,
                "cancel returns the items");
        check(f, told(c.getUUID(), "note_cancelled"), "applicants told of the cancel");
        for (int i = 0; i < 36; i++) {
            if (s.getInventory().getItem(i).is(Items.EMERALD)) {
                s.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        s.getInventory().setItem(6, new ItemStack(Items.EMERALD, 5));
        Trades.list(s, slot(6, 5), List.of(Items.APPLE), List.of(1), 0);
        TradeData.Listing l4 = data.of(s.getUUID()).get(0);
        List<ItemStack> sKeep = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            sKeep.add(s.getInventory().getItem(i).copy());
            if (s.getInventory().getItem(i).isEmpty()) {
                s.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            }
        }
        s.getInventory().offhand.set(0, new ItemStack(Items.DIRT, 64));
        check(f, Trades.cancel(s, l4.id) == Trades.Result.CANCELLED && s.getInventory().countItem(Items.EMERALD) == 0
                && Trades.count(data.mail(s.getUUID()), new ItemStack(Items.EMERALD)) == 5, "full inventory: returned items wait in the mail (inv "
                + s.getInventory().countItem(Items.EMERALD) + ", mail " + data.mail(s.getUUID()) + ", creative " + s.hasInfiniteMaterials() + ")");
        for (int i = 0; i < 36; i++) {
            s.getInventory().setItem(i, sKeep.get(i));
        }
        s.getInventory().offhand.set(0, ItemStack.EMPTY);
        check(f, Trades.claim(s) == Trades.Result.CLAIMED && s.getInventory().countItem(Items.EMERALD) == 5 && data.mail(s.getUUID()).isEmpty(), "claim");
        check(f, Trades.claim(s) == Trades.Result.NOTHING_TO_CLAIM, "claim twice");
        // ---- 期限切れ: 受け取り箱へ、いない人には知らせを残す
        s.getInventory().setItem(7, new ItemStack(Items.COAL, 8));
        Trades.list(s, slot(7, 8), List.of(Items.APPLE), List.of(1), 0);
        TradeData.Listing l5 = data.of(s.getUUID()).get(0);
        b.getInventory().setItem(20, new ItemStack(Items.APPLE, 1));
        Trades.apply(b, l5.id, null, 0);
        here.remove(s.getUUID());
        here.remove(b.getUUID());
        check(f, Trades.expire(server, l5.expires + 1) == 1 && data.byId(l5.id) == null
                && Trades.count(data.mail(s.getUUID()), new ItemStack(Items.COAL)) == 8, "expired listing goes to the seller's mail");
        check(f, data.notes(s.getUUID()).stream().anyMatch(n -> n.key().endsWith("note_expired"))
                && data.notes(b.getUUID()).stream().anyMatch(n -> n.key().endsWith("note_closed")), "expiry notes for offline players");
        here.put(s.getUUID(), s);
        here.put(b.getUUID(), b);
        // ---- 10 品まで
        s.getInventory().setItem(8, new ItemStack(Items.STICK, 64));
        int made = 0;
        while (Trades.list(s, slot(8, 1), List.of(Items.APPLE), List.of(1), 0) == Trades.Result.LISTED) {
            made++;
            if (made > 20) {
                break;
            }
        }
        check(f, data.of(s.getUUID()).size() == Trades.MAX_LISTINGS, "ten listings per player " + data.of(s.getUUID()).size());
        // ---- 保存と読み直し (出品・申請・受け取り箱・知らせ)
        TradeData.Listing lx = data.of(s.getUUID()).get(0);
        Trades.apply(b, lx.id, null, 0);
        TradeData copy = data.roundTrip(server.registryAccess());
        TradeData.Listing cl = copy.byId(lx.id);
        check(f, cl != null && cl.seller.equals(s.getUUID()) && cl.give.get(0).is(Items.STICK) && cl.want.get(0).is(Items.APPLE)
                && cl.apps.containsKey(b.getUUID()) && cl.apps.get(b.getUUID()).offer.get(0).is(Items.APPLE) && cl.expires == lx.expires,
                "listings and applications survive save");
        check(f, Trades.count(copy.mail(s.getUUID()), new ItemStack(Items.COAL)) == 8 && !copy.notes(b.getUUID()).isEmpty(), "mail and notes survive save");
        // 大きな数のほしいもの (鐘銭 300) も保存で欠けない
        Trades.cancel(s, lx.id);
        Trades.list(s, slot(8, 1), List.of(), List.of(), 300);
        TradeData copy2 = data.roundTrip(server.registryAccess());
        List<TradeData.Listing> mine2 = copy2.of(s.getUUID());
        TradeData.Listing big = mine2.get(mine2.size() - 1);
        check(f, Trades.count(big.want, Trades.coins(1)) == 300, "large wanted counts survive save");
        s.getInventory().clearContent();
        b.getInventory().clearContent();
        c.getInventory().clearContent();
    }
}
