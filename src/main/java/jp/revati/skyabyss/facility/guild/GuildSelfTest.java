package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.common.util.FakePlayer;

// ギルド (docs/design/35 の 3.4) の自己テスト。段: "facility guild"。
//   作る (名前・印の長さ・重なり)、申請と受ける / 断る、誰でも入れるギルド、30 人まで、抜けると長が移り、最後の人が抜けると解散、
//   名札の印 (人の写し)、保存と読み直し、名札の頭の文。いまあるギルドは試しの前に写して、終わったら戻す
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
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST facility guild crashed", e);
            f.add("crashed: " + e);
        } finally {
            data.clearForTest();
            for (GuildData.Guild g : saved.all()) {
                GuildData.Guild copy = data.create(g.name, g.tag, g.color, g.accent, g.leader, g.nameOf(g.leader), g.created);
                copy.open = g.open;
                g.members.forEach(u -> {
                    copy.members.add(u);
                    copy.names.put(u, g.nameOf(u));
                });
                g.applicants.forEach(u -> {
                    copy.applicants.add(u);
                    copy.names.put(u, g.nameOf(u));
                });
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST facility guild PASSED (create, apply/accept/deny, open join, 30 cap, leave/leader/disband, badge, save)");
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
}
