package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import net.minecraft.server.MinecraftServer;
import jp.revati.skyabyss.facility.Coins;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// ギルド (docs/design/35 の 3.4) の自己テスト。段: "facility guild"。
//   作る (名前・印の長さ・重なり)、申請と受ける / 断る、誰でも入れるギルド、30 人まで、抜けると長が移り、最後の人が抜けると解散、
//   名札の印 (人の写し)、保存と読み直し、名札の頭の文。いまあるギルドは試しの前に写して、終わったら戻す
//   位と倉庫 (副長が申請を受ける・取り出せる位・預けられない品)、旗の作り直しと旗の品、週替わりの頼み (選び方・週替わり・納める・
//   狩る・済むと報い・いない人の報い)、空き区画 (借りる・重ならない・手放す・中で建てられる人)
public final class GuildSelfTest {
    private GuildSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        GuildData data = GuildData.get(server);
        GuildData saved = data.roundTrip(server.registryAccess());
        try {
            data.clearForTest();
            body(f, server, data);
            data.clearForTest();
            extras(f, server, data);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST facility guild crashed", e);
            f.add("crashed: " + e);
        } finally {
            data.restoreFrom(saved);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST facility guild PASSED (create, apply/accept/deny, open join, 30 cap, leave/leader/disband, badge, save, "
                    + "ranks, storage, emblem, banner, weekly request, lots)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST facility guild FAILED: {}", m));
        }
    }

    private static FakePlayer fake(MinecraftServer server, String name) {
        return new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), name));
    }

    private static void body(List<String> f, MinecraftServer server, GuildData data) {
        FakePlayer a = fake(server, "guild_a");
        FakePlayer b = fake(server, "guild_b");
        FakePlayer c = fake(server, "guild_c");
        check(f, Guilds.create(a, "鐘", "K", 1, 2) == Guilds.Result.NAME_LENGTH, "one-letter name refused");
        check(f, Guilds.create(a, "鐘の守り", "KANEX", 1, 2) == Guilds.Result.TAG_LENGTH, "five-letter tag refused");
        check(f, Guilds.create(a, "  鐘の守り ", "kane", DyeColor.BLUE.getId(), DyeColor.WHITE.getId()) == Guilds.Result.OK, "create");
        GuildData.Guild g = data.byName("鐘の守り");
        check(f, g != null && g.tag.equals("KANE") && g.leader.equals(a.getUUID()), "name trimmed, tag upper-cased, creator leads");
        check(f, a.getData(FacilitySetup.BADGE).present() && a.getData(FacilitySetup.BADGE).tag().equals("KANE"), "creator wears the badge");
        check(f, Guilds.create(a, "別の名", "X", 0, 0) == Guilds.Result.IN_GUILD, "one guild per person");
        check(f, Guilds.create(c, "鐘の守り", "Y", 0, 0) == Guilds.Result.NAME_TAKEN, "names do not repeat");
        // 申請 → 受ける
        check(f, Guilds.apply(b, "鐘の守り") == Guilds.Result.APPLIED, "apply");
        check(f, Guilds.apply(b, "鐘の守り") == Guilds.Result.ALREADY_APPLIED, "apply twice");
        check(f, Guilds.answer(b, b.getUUID(), true) == Guilds.Result.NOT_IN_GUILD, "only members answer");
        check(f, Guilds.answer(a, b.getUUID(), true) == Guilds.Result.JOINED && g.members.contains(b.getUUID()), "leader accepts");
        // 申請 → 断る
        check(f, Guilds.apply(c, g.id) == Guilds.Result.APPLIED && Guilds.answer(a, c.getUUID(), false) == Guilds.Result.OK
                && !g.members.contains(c.getUUID()) && !g.applicants.contains(c.getUUID()), "leader declines");
        check(f, Guilds.setOpen(b, true) == Guilds.Result.NOT_LEADER, "only the leader opens the guild");
        // 誰でも入れる → そのまま入る
        check(f, Guilds.setOpen(a, true) == Guilds.Result.OK && Guilds.apply(c, "鐘の守り") == Guilds.Result.JOINED
                && c.getData(FacilitySetup.BADGE).present(), "open guild joins at once");
        // 30 人まで
        List<FakePlayer> many = new ArrayList<>();
        while (g.members.size() < GuildData.MAX_MEMBERS) {
            FakePlayer m = fake(server, "guild_m" + many.size());
            many.add(m);
            Guilds.apply(m, g.id);
        }
        check(f, g.members.size() == GuildData.MAX_MEMBERS, "filled to 30");
        check(f, Guilds.apply(fake(server, "guild_late"), g.id) == Guilds.Result.FULL, "31st is refused");
        // 保存と読み直し
        GuildData copy = data.roundTrip(server.registryAccess());
        GuildData.Guild cg = copy.byName("鐘の守り");
        check(f, cg != null && cg.members.size() == GuildData.MAX_MEMBERS && cg.leader.equals(a.getUUID()) && cg.open && cg.tag.equals("KANE")
                && cg.color == DyeColor.BLUE.getId() && cg.nameOf(b.getUUID()).equals("guild_b"), "guild survives save");
        check(f, copy.of(c.getUUID()) != null && copy.of(UUID.randomUUID()) == null, "member lookup after load");
        // 外す・抜ける・長が移る
        check(f, Guilds.kick(b, c.getUUID()) == Guilds.Result.NOT_LEADER, "only the leader removes");
        check(f, Guilds.kick(a, c.getUUID()) == Guilds.Result.OK && !g.members.contains(c.getUUID()), "leader removes a member");
        check(f, Guilds.leave(a) == Guilds.Result.OK && !a.getData(FacilitySetup.BADGE).present(), "leave clears the badge");
        check(f, g.leader.equals(b.getUUID()), "the oldest member becomes leader");
        for (FakePlayer m : many) {
            Guilds.leave(m);
        }
        check(f, Guilds.leave(b) == Guilds.Result.OK && data.byName("鐘の守り") == null, "last member leaving disbands");
        check(f, Guilds.leave(b) == Guilds.Result.NOT_IN_GUILD, "leave twice");
        // 名札の頭の文
        GuildBadge badge = new GuildBadge("g1", "KANE", DyeColor.BLACK.getId(), DyeColor.YELLOW.getId());
        check(f, Guilds.prefix(badge).getString().equals("[KANE] "), "name plate prefix " + Guilds.prefix(badge).getString());
        check(f, (badge.textColor() & 0xFF) > 0x40, "dark banner colours are lightened for text");
    }

    private static void extras(List<String> f, MinecraftServer server, GuildData data) {
        FakePlayer a = fake(server, "guild_x_a");
        FakePlayer b = fake(server, "guild_x_b");
        FakePlayer c = fake(server, "guild_x_c");
        FakePlayer d = fake(server, "guild_x_d");
        check(f, Guilds.create(a, "倉の守り", "KURA", DyeColor.RED.getId(), DyeColor.YELLOW.getId()) == Guilds.Result.OK, "create (extras)");
        GuildData.Guild g = data.byName("倉の守り");
        if (g == null) {
            f.add("extras guild missing");
            return;
        }
        Guilds.setOpen(a, true);
        Guilds.apply(b, g.id);
        Guilds.apply(c, g.id);
        Guilds.setOpen(a, false);
        // 位: 副長は申請を受けられる、人はできない
        check(f, Guilds.setOfficer(b, c.getUUID(), true) == Guilds.Result.NOT_LEADER, "only the leader promotes");
        check(f, Guilds.setOfficer(a, b.getUUID(), true) == Guilds.Result.OK && g.rank(b.getUUID()) == GuildData.RANK_OFFICER, "promote to officer");
        check(f, g.rank(a.getUUID()) == GuildData.RANK_LEADER && g.rank(c.getUUID()) == GuildData.RANK_MEMBER, "ranks");
        Guilds.apply(d, g.id);
        check(f, Guilds.answer(c, d.getUUID(), true) == Guilds.Result.NOT_OFFICER, "members cannot accept");
        check(f, Guilds.answer(b, d.getUUID(), true) == Guilds.Result.JOINED, "officers accept");
        // 倉庫の決まり
        check(f, g.takeRank == GuildData.RANK_OFFICER && g.canTake(b.getUUID()) && !g.canTake(c.getUUID()), "default: officers withdraw, members deposit");
        check(f, Guilds.setTakeRank(b, 0) == Guilds.Result.NOT_LEADER, "only the leader sets withdraw rank");
        check(f, Guilds.setTakeRank(a, 0) == Guilds.Result.OK && g.canTake(c.getUUID()), "anyone may withdraw when opened up");
        check(f, Guilds.setTakeRank(a, 2) == Guilds.Result.OK && !g.canTake(b.getUUID()) && g.canTake(a.getUUID()), "leader only");
        check(f, !g.canTake(UUID.randomUUID()), "outsiders never withdraw");
        g.storage.set(3, new ItemStack(Items.IRON_INGOT, 7));
        // 旗
        check(f, Guilds.setEmblem(c, "X", 1, 2) == Guilds.Result.NOT_LEADER, "only the leader remakes the banner");
        check(f, Guilds.setEmblem(a, "kr", DyeColor.BLACK.getId(), DyeColor.WHITE.getId()) == Guilds.Result.OK && g.tag.equals("KR")
                && a.getData(FacilitySetup.BADGE).tag().equals("KR"), "banner remade and badge updated");
        ItemStack banner = Guilds.bannerItem(server, g);
        var layers = banner.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
        check(f, banner.is(Items.BLACK_BANNER) && layers != null && layers.layers().size() == 2, "banner item colours and pattern");
        check(f, Guilds.takeBanner(c) == Guilds.Result.NO_COINS, "banner costs coins");
        Coins.give(c, Guilds.BANNER_COST);
        check(f, Guilds.takeBanner(c) == Guilds.Result.BANNER && c.getInventory().countItem(Items.BLACK_BANNER) == 1 && Coins.count(c) == 0, "take banner");
        // 頼み
        long w = GuildRequests.week(server);
        check(f, GuildRequests.pick(g.id, w) == GuildRequests.pick(g.id, w), "pick is stable within a week");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            seen.add(GuildRequests.pick(g.id, w + i).id());
        }
        check(f, seen.size() >= 3, "requests rotate across weeks " + seen);
        g.reqWeek = w;
        g.reqId = "iron";
        g.reqProgress = 0;
        g.reqDone = false;
        g.contrib.clear();
        check(f, GuildRequests.deliver(c) == Guilds.Result.NOTHING_TO_GIVE, "nothing to deliver");
        c.getInventory().add(new ItemStack(Items.IRON_INGOT, 40));
        check(f, GuildRequests.deliver(c) == Guilds.Result.DELIVERED && g.reqProgress == 40 && g.contrib.get(c.getUUID()) == 40
                && c.getInventory().countItem(Items.IRON_INGOT) == 0, "deliver iron");
        b.getInventory().add(new ItemStack(Items.IRON_INGOT, 20));
        check(f, GuildRequests.deliver(b) == Guilds.Result.DELIVERED && g.reqDone && g.reqProgress == 48
                && b.getInventory().countItem(Items.IRON_INGOT) == 12, "deliver only what is needed, then done");
        check(f, g.rewardsDue.getOrDefault(c.getUUID(), 0) == 1 && g.rewardsDue.getOrDefault(b.getUUID(), 0) == 1
                && !g.rewardsDue.containsKey(a.getUUID()), "rewards for those who helped (kept for absent players)");
        check(f, GuildRequests.deliver(b) == Guilds.Result.REQ_DONE, "done request takes no more");
        // 週が替わると新しい頼み
        g.reqWeek = w - 1;
        GuildRequests.current(server, g);
        check(f, g.reqWeek == w && !g.reqDone && g.reqProgress == 0 && g.contrib.isEmpty(), "a new week resets the request");
        // 狩る頼み
        g.reqId = "hunt";
        GuildRequests.add(server, g, d.getUUID(), 3);
        check(f, g.reqProgress == 3 && g.contrib.get(d.getUUID()) == 3, "hunt counts kills");
        // 空き区画
        check(f, GuildLots.rent(c, "lot_1") == Guilds.Result.NOT_LEADER, "only the leader rents");
        check(f, GuildLots.rent(a, "lot_1") == Guilds.Result.NO_COINS, "rent costs coins");
        Coins.give(a, GuildLots.RENT_COINS);
        check(f, GuildLots.rent(a, "lot_9") == Guilds.Result.NO_LOT, "unknown lot");
        check(f, GuildLots.rent(a, "lot_1") == Guilds.Result.RENTED && g.lot.equals("lot_1") && Coins.count(a) == 0
                && GuildLots.holder(server, "lot_1") == g, "rent a lot");
        check(f, GuildLots.rent(a, "lot_2") == Guilds.Result.HAS_LOT, "one lot per guild");
        FakePlayer e = fake(server, "guild_x_e");
        Guilds.create(e, "隣の家", "TONA", 0, 0);
        Coins.give(e, GuildLots.RENT_COINS);
        check(f, GuildLots.rent(e, "lot_1") == Guilds.Result.LOT_TAKEN && Coins.count(e) == GuildLots.RENT_COINS, "one guild per lot");
        GuildLots.Area area = GuildLots.areaOf(server, "lot_1");
        if (area != null) {
            check(f, GuildLots.mayBuild(server, c, area.center()) && !GuildLots.mayBuild(server, e, area.center()), "only members build in the lot");
            check(f, GuildLots.mayBuild(server, e, area.center().offset(200, 0, 200)), "outside lots anyone builds");
        }
        // 保存と読み直し (位・倉庫・区画・頼み)
        GuildData copy = data.roundTrip(server.registryAccess());
        GuildData.Guild cg = copy.byName("倉の守り");
        check(f, cg != null && cg.officers.contains(b.getUUID()) && cg.takeRank == 2 && cg.storage.get(3).getCount() == 7 && cg.lot.equals("lot_1")
                && cg.reqId.equals("hunt") && cg.contrib.get(d.getUUID()) == 3 && cg.rewardsDue.getOrDefault(c.getUUID(), 0) == 1,
                "ranks, storage, lot and request survive save");
        check(f, GuildLots.release(a) == Guilds.Result.RELEASED && GuildLots.holder(server, "lot_1") == null, "release the lot");
        // 長が抜けると副長が長に
        Guilds.leave(a);
        check(f, g.leader.equals(b.getUUID()) && !g.officers.contains(b.getUUID()), "officer succeeds the leader");
        c.getInventory().clearContent();
        b.getInventory().clearContent();
    }
}
