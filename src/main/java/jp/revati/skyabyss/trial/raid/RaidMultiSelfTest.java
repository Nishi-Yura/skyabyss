package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.dev.play.PlayRaid;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.TrialRoute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 二人で同じレイドの部屋にいるとき (RaidRun)。SKYABYSS_SELFTEST_ONLY="raid multi"
// 偽の二人 (同じパーティ) を岩鯨の段 1 の部屋へ運んで:
//  - 一人が倒れても、もう一人が立っていれば全滅にならない。二人とも倒れたら全滅 (二人とも起きて、入口の休み場へ)
//  - 部屋の外へ出た人 (途中で抜けて入り直した・背中の休み場で目を覚ました) は、猶予のあと部屋の休み場へ戻る。猶予の前には戻さない
//  - 別のグループの人は、部屋の外にいても運ばない
public final class RaidMultiSelfTest {
    private RaidMultiSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        PartyData parties = PartyData.get(server);
        CompoundTag snapshot = parties.snapshot(server.registryAccess());
        String titan = Titans.STONE_WHALE;
        ServerLevel back = server.getLevel(Titans.backOf(titan));
        UUID group = null;
        try {
            if (back == null) {
                f.add("the stone whale's back is missing");
            } else {
                FakePlayer a = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000004a1"), "rm_a"));
                FakePlayer b = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000004b2"), "rm_b"));
                FakePlayer other = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000004c3"), "rm_other"));
                parties.clearAll();
                PartyData.Party party = parties.create(a.getUUID(), "rm_a");
                parties.add(party, b.getUUID(), "rm_b");
                group = Groups.keyOf(a);
                clean(back, group);
                List<ServerPlayer> all = List.of(a, b, other);
                TrialRoute.PLAYERS = l -> all;
                PlayRaid.prepare(a, 0);
                PlayRaid.prepare(b, 0);
                a.setPos(0, 0, 0);
                b.setPos(0, 0, 0);
                other.setPos(0, 0, 0);
                String miss = PlayRaid.enter(back, group, titan, List.of(a, b), "1");
                check(f, miss == null, "raid 1 returned " + miss);
                RaidRun.Run run = RaidRun.of(group);
                if (run == null) {
                    f.add("no raid run after entering step 1");
                } else {
                    downs(run, a, b, f);
                    strays(run, a, b, other, f);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST raid multi crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (back != null && group != null) {
                clean(back, group);
            }
            TrialRoute.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
            parties.restore(snapshot, server.registryAccess());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid multi PASSED (one down with a mate standing is no wipe, both down wipes and revives both at the rest, "
                    + "a member outside the room is carried back after the grace and not before, another group's player is left alone)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST raid multi FAILED: {}", x));
        }
    }

    // 一人が倒れる・二人とも倒れる
    private static void downs(RaidRun.Run run, FakePlayer a, FakePlayer b, List<String> f) {
        long now = run.ctx.now();
        run.phase = RaidRun.Phase.ACTIVE;
        int wipes = run.wipes;
        Downed.down(a);
        RaidRun.tickRun(run, now);
        check(f, run.wipes == wipes && run.phase == RaidRun.Phase.ACTIVE, "one member down while the other stands was taken as a wipe");
        Downed.down(b);
        RaidRun.tickRun(run, now);
        check(f, run.wipes == wipes + 1, "both members down did not wipe the room (wipes " + run.wipes + ")");
        check(f, !Downed.isDowned(a) && !Downed.isDowned(b), "after the wipe a member is still down");
        check(f, run.ctx.cell().bounds().contains(a.position()) && run.ctx.cell().bounds().contains(b.position()),
                "after the wipe the two are not back in the room: " + a.position() + " / " + b.position());
        Downed.reviveFull(a);
        Downed.reviveFull(b);
    }

    // 部屋の外へ出た人を戻す
    private static void strays(RaidRun.Run run, FakePlayer a, FakePlayer b, FakePlayer other, List<String> f) {
        run.phase = RaidRun.Phase.ACTIVE;
        long t = run.ctx.now() + 1000;
        a.setPos(0.5, 200, 0.5);
        other.setPos(0.5, 200, 0.5);
        RaidRun.gather(run, t);
        check(f, !run.ctx.cell().bounds().contains(a.position()), "a member outside the room was carried back at once (no grace)");
        RaidRun.gather(run, t + RaidRun.STRAY_GRACE - 1);
        check(f, !run.ctx.cell().bounds().contains(a.position()), "a member outside the room was carried back before the grace ran out");
        RaidRun.gather(run, t + RaidRun.STRAY_GRACE);
        check(f, run.ctx.cell().bounds().contains(a.position()), "a member outside the room was not carried back after the grace: " + a.position());
        check(f, run.ctx.cell().bounds().contains(b.position()), "the member who stayed in the room was moved out");
        check(f, other.position().y > 150, "a player of another group was carried into the room");
        // 戻ったあと、もう一人が倒れれば全滅になる (外に立つ人が全滅を止めない)
        int wipes = run.wipes;
        Downed.down(a);
        Downed.down(b);
        RaidRun.tickRun(run, t + RaidRun.STRAY_GRACE + 1);
        check(f, run.wipes == wipes + 1, "after the carry back, both down did not wipe");
        Downed.reviveFull(a);
        Downed.reviveFull(b);
    }

    private static void clean(ServerLevel back, UUID g) {
        RaidRun.clearAll();
        TrialRoute.finish(back, g);
        GroupGates.reset(back, g);
        BackGroupData.get(back).remove(g);
    }
}
