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
//   作る (名前・印の長さ・重なり)、申請と受ける / 断る、誰でも入れるギルド、30 人まで、長は抜けられない (譲る・解散)、
//   名札の印 (人の写し)、保存と読み直し、名札の頭の文。いまあるギルドは試しの前に写して、終わったら戻す
//   位と倉庫 (副長が申請を受ける・取り出せる位・預けられない品)、旗の作り直しと旗の品、週替わりの頼み (選び方・週替わり・納める・
//   狩る・済むと報い・いない人の報い)、旗の模様と名札の色 (はじめからの模様・功績で開く模様と色)、功績 (頼みの回数・納めた量・人の数)
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
                    + "ranks, storage, emblem, banner, weekly request, leader stays/hand over/disband, patterns, achievements)");
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
        check(f, Guilds.leave(a) == Guilds.Result.LEADER_STAYS && g.members.contains(a.getUUID()), "the leader cannot leave");
        check(f, Guilds.transfer(b, a.getUUID()) == Guilds.Result.NOT_LEADER, "only the leader hands over");
        check(f, Guilds.transfer(a, UUID.randomUUID()) == Guilds.Result.NOT_MEMBER, "hand over only to a member");
        check(f, Guilds.transfer(a, b.getUUID()) == Guilds.Result.NEW_LEADER && g.leader.equals(b.getUUID())
                && g.rank(a.getUUID()) == GuildData.RANK_OFFICER, "hand over: new leader, old leader becomes officer");
        check(f, Guilds.leave(a) == Guilds.Result.OK && !a.getData(FacilitySetup.BADGE).present(), "leave clears the badge");
        for (FakePlayer m : many) {
            Guilds.leave(m);
        }
        check(f, Guilds.leave(b) == Guilds.Result.LEADER_STAYS, "the last member, the leader, still cannot leave");
        check(f, Guilds.disband(many.get(0)) == Guilds.Result.NOT_IN_GUILD, "outsiders cannot disband");
        check(f, Guilds.disband(b) == Guilds.Result.DISBANDED && data.byName("鐘の守り") == null && !b.getData(FacilitySetup.BADGE).present(),
                "the leader disbands");
        check(f, Guilds.leave(b) == Guilds.Result.NOT_IN_GUILD, "leave after disband");
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
        // 功績: 頼みを一度済ませた → 花の模様が開く。名札の金は 10 回から
        check(f, g.reqCompleted == 1 && g.totalContrib == 51 && g.unlocked.contains("requests_1") && !g.unlocked.contains("requests_5"),
                "a finished request counts toward achievements " + g.reqCompleted + " " + g.totalContrib + " " + g.unlocked);
        check(f, GuildEmblems.available(java.util.Set.of()).size() >= 16, "many patterns from the start");
        check(f, Guilds.setEmblem(a, "KR", DyeColor.BLACK.getId(), DyeColor.WHITE.getId(), "globe", "") == Guilds.Result.PATTERN_LOCKED,
                "locked pattern refused");
        check(f, Guilds.setEmblem(a, "KR", DyeColor.BLACK.getId(), DyeColor.WHITE.getId(), "flower", "") == Guilds.Result.OK
                && g.pattern.equals("flower"), "unlocked pattern used");
        var fl = Guilds.bannerItem(server, g).get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
        check(f, fl != null && fl.layers().size() == 1 && fl.layers().get(0).pattern().is(net.minecraft.world.level.block.entity.BannerPatterns.FLOWER),
                "banner item follows the pattern");
        check(f, Guilds.setEmblem(a, "KR", DyeColor.BLACK.getId(), DyeColor.WHITE.getId(), "cross", "") == Guilds.Result.OK, "free pattern used");
        check(f, Guilds.setEmblem(a, "KR", DyeColor.BLACK.getId(), DyeColor.WHITE.getId(), "cross", "gold") == Guilds.Result.PLATE_LOCKED,
                "locked plate colour refused");
        g.reqCompleted = 10;
        check(f, GuildAchievements.check(server, g) >= 2 && g.unlocked.contains("requests_10") && g.unlocked.contains("requests_5"), "achievements unlock");
        check(f, Guilds.setEmblem(a, "KR", DyeColor.BLACK.getId(), DyeColor.WHITE.getId(), "flow", "gold") == Guilds.Result.OK
                && a.getData(FacilitySetup.BADGE).plate().equals("gold") && a.getData(FacilitySetup.BADGE).textColor() == 0xF2C14E,
                "plate colour shows on the badge");
        g.totalContrib = 600;
        GuildAchievements.check(server, g);
        check(f, g.unlocked.contains("contrib_500") && GuildEmblems.usable("skull", g.unlocked) && !GuildEmblems.usable("globe", g.unlocked),
                "contribution milestone");
        check(f, GuildAchievements.value(g, GuildAchievements.Kind.MEMBERS) == 4 && !g.unlocked.contains("members_10"), "member milestone counts");
        // 保存と読み直し (位・倉庫・頼み・旗・功績)
        GuildData copy = data.roundTrip(server.registryAccess());
        GuildData.Guild cg = copy.byName("倉の守り");
        check(f, cg != null && cg.officers.contains(b.getUUID()) && cg.takeRank == 2 && cg.storage.get(3).getCount() == 7 && cg.pattern.equals("flow")
                && cg.plate.equals("gold") && cg.unlocked.contains("requests_10") && cg.reqCompleted == 10 && cg.totalContrib == 600
                && cg.reqId.equals("hunt") && cg.contrib.get(d.getUUID()) == 3 && cg.rewardsDue.getOrDefault(c.getUUID(), 0) == 1,
                "ranks, storage, banner, achievements and request survive save");
        // 長を副長へ譲る
        check(f, Guilds.leave(a) == Guilds.Result.LEADER_STAYS, "leader stays (extras)");
        check(f, Guilds.transfer(a, b.getUUID()) == Guilds.Result.NEW_LEADER && g.leader.equals(b.getUUID()) && !g.officers.contains(b.getUUID())
                && g.officers.contains(a.getUUID()), "officer takes over");
        c.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(c, jp.revati.skyabyss.facility.Coins.count(c));
        b.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(b, jp.revati.skyabyss.facility.Coins.count(b));
    }
}
