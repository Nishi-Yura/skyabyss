package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 二人以上の一行の順路 (docs/design/32 の 2.2〜2.4) の自己テスト。段の名前は "route party"。偽の人の二人組 (同じパーティ) で、岩鯨の背で:
//  - 入口の箱 (乗り込むと着く到着の台): 一行の全員が箱に入るまで始まらない (一人だけでは待つ)。そろうと予告のあと始まり、二人とも段 1 の休み場へ運ばれる
//  - 途中参加: 順路が動いてからパーティに入って到着の台に着いた人は、今の部屋へ運ばれて参加者になり、次の運びにも乗る
//  - あきらめ: 一人の提案では成立しない。もう一人が「いいえ」なら取りやめ、30 秒の返事がなければ流れる、全員が「はい」で終わる
//  - 祠から (周回): 輪の二人を、段 1 の写しの写しが済むのを待ってから、二人とも区画の写しの休み場へ運ぶ
// 祠の輪の数え方 (サーバーの人の一覧) は偽の人では確かめられないので、輪の中の人を渡す TrialRoute.startSoon から確かめる
public final class RoutePartySelfTest {
    private RoutePartySelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("route party", 10, RoutePartySelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        String titan = Titans.STONE_WHALE;
        PartyData parties = PartyData.get(server);
        CompoundTag snapshot = parties.snapshot(server.registryAccess());
        ServerLevel back = null;
        UUID group = null;
        try {
            jp.revati.skyabyss.dev.StructureDump.ensureBack(server, "titan_back");
            back = server.getLevel(Titans.backOf(titan));
            if (back == null) {
                f.add("the stone whale's back is missing");
                return;
            }
            FakePlayer a = fake(back, "7a0b1d00-0000-4000-8000-0000000002a1", "party_a");
            FakePlayer b = fake(back, "7a0b1d00-0000-4000-8000-0000000002b2", "party_b");
            FakePlayer c = fake(back, "7a0b1d00-0000-4000-8000-0000000002c3", "party_c");
            parties.clearAll();
            PartyData.Party party = parties.create(a.getUUID(), "party_a");
            parties.add(party, b.getUUID(), "party_b");
            group = Groups.keyOf(a);
            check(f, group.equals(Groups.keyOf(b)) && !group.equals(Groups.keyOf(c)), "the two fake players are not one group");
            clean(back, group);
            TrialRoute.clearAll();
            AABB box = TrialRoute.entryBox(titan);
            Vec3 door = box.getCenter();
            Vec3 away = new Vec3(door.x + 30, door.y, door.z + 30);
            Vec3 cAway = new Vec3(door.x - 30, door.y, door.z - 30);
            a.setPos(door);
            b.setPos(away);
            c.setPos(cAway);
            List<ServerPlayer> players = List.of(a, b, c);
            TrialRoute.PLAYERS = l -> players;
            long now = back.getGameTime();

            // ---- 入口の箱: 一人だけでは始まらない
            TrialRoute.tick(back, titan, players, now);
            TrialRoute.tick(back, titan, players, now + TrialRoute.ENTRY_WAIT + 10);
            check(f, TrialRoute.step(back, group) == 0, "the route started with one of two in the entry box");
            // 二人そろうと、予告のあと始まり、二人とも運ばれる
            b.setPos(door);
            long t1 = now + 1000;
            TrialRoute.tick(back, titan, players, t1);
            check(f, TrialRoute.step(back, group) == 0, "the route started without the warning when the second player stepped in");
            TrialRoute.tick(back, titan, players, t1 + TrialRoute.ENTRY_WAIT + 10);
            check(f, TrialRoute.step(back, group) == 1, "the route did not start with the whole party in the box (step " + TrialRoute.step(back, group) + ")");
            TrialDraw.Draw draw = TrialData.current(back, group);
            check(f, near(a, back, titan, draw, 1) && near(b, back, titan, draw, 1), "the two were not both carried to the first rest: " + a.position() + " / " + b.position());
            check(f, TrialRoute.isParticipant(back, group, a.getUUID()) && TrialRoute.isParticipant(back, group, b.getUUID()), "the party record misses one of the two");

            // ---- 途中参加: 始まったあとに乗り込んで到着の台に着いた仲間は、今の部屋へそのまま運ばれ、参加者になる (docs/design/32 の 2.2)
            parties.add(party, c.getUUID(), "party_c");
            check(f, Groups.keyOf(c).equals(group), "the late member did not join the party");
            c.setPos(door);
            TrialRoute.tick(back, titan, players, t1 + 2000);
            check(f, TrialRoute.isParticipant(back, group, c.getUUID()), "a member who boarded after the start did not become a participant");
            check(f, near(c, back, titan, draw, 1), "the late member was not carried into the current room: " + c.position());
            check(f, TrialRoute.step(back, group) == 1, "a late member changed the route (step " + TrialRoute.step(back, group) + ")");
            TrialRoute.onCleared(back, group, titan, TrialRoute.keyOf(titan, draw, 1), null);
            TrialRoute.flushPending();
            TrialRoute.tick(back, titan, players, t1 + 4000);
            check(f, TrialRoute.step(back, group) == 2, "clearing the first trial did not move the party on (step " + TrialRoute.step(back, group) + ")");
            check(f, near(a, back, titan, draw, 2) && near(b, back, titan, draw, 2) && near(c, back, titan, draw, 2), "the three were not all carried to the second room");
            parties.remove(c.getUUID());
            c.setPos(cAway);

            // ---- あきらめ: 一人の提案では成立しない。「いいえ」で取りやめ
            check(f, TrialRoute.propose(a), "the proposal was not accepted");
            check(f, TrialRoute.step(back, group) == 2 && TrialRoute.abandonPending(group), "one proposal ended the route of two (step " + TrialRoute.step(back, group) + ")");
            TrialRoute.reply(b, false);
            check(f, TrialRoute.step(back, group) == 2 && !TrialRoute.abandonPending(group), "a no did not cancel the proposal");
            // 返事がなければ流れる (提案の間隔の待ちは、テストでは消す)
            TrialRoute.clearCooldownForTest(group);
            TrialRoute.propose(a);
            long asked = back.getGameTime();
            TrialRoute.tick(back, titan, players, asked + TrialRoute.ABANDON_WAIT + 1);
            check(f, TrialRoute.step(back, group) == 2 && !TrialRoute.abandonPending(group), "an unanswered proposal did not expire (step " + TrialRoute.step(back, group) + ")");
            // 全員が「はい」で終わる
            TrialRoute.clearCooldownForTest(group);
            TrialRoute.propose(a);
            try {
                TrialRoute.reply(b, true);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.info("SELFTEST route party: sending out a fake player threw ({}); checking the record only", e.toString());
            }
            check(f, TrialRoute.step(back, group) == 0 && !TrialRoute.abandonPending(group), "a yes from everyone did not end the route (step " + TrialRoute.step(back, group) + ")");

            // ---- 祠から (周回): 二人とも、写しの休み場へ。あきらめで外へ出された二人は別の次元にいるので、新しい二人組で
            clean(back, group);
            TrialRoute.clearAll();
            parties.clearAll();
            a = fake(back, "7a0b1d00-0000-4000-8000-0000000002d4", "party_d");
            b = fake(back, "7a0b1d00-0000-4000-8000-0000000002e5", "party_e");
            PartyData.Party replayParty = parties.create(a.getUUID(), "party_d");
            parties.add(replayParty, b.getUUID(), "party_e");
            group = Groups.keyOf(a);
            List<ServerPlayer> pair = List.of(a, b, c);
            TrialRoute.PLAYERS = l -> pair;
            clean(back, group);
            GroupProgress.of(back, group).markFirstCleared();
            a.setPos(door);
            b.setPos(door);
            TrialRoute.startSoon(back, group, titan, List.of(a, b));
            check(f, TrialRoute.step(back, group) == 1, "the shrine start did not set step 1");
            check(f, TrialRoute.pending(group), "the shrine start did not queue the carry");
            TrialDraw.Draw replay = TrialData.current(back, group);
            long due = back.getGameTime();
            TrialRoute.tick(back, titan, pair, due + 1);
            check(f, !TrialRoute.pending(group), "the shrine carry stayed queued");
            check(f, near(a, back, titan, replay, 1) && near(b, back, titan, replay, 1), "the shrine did not carry both into the first room: " + a.position() + " / " + b.position());
            check(f, TrialSlot.of(GroupProgress.of(back, group)) > 0, "the replay has no slot");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST route party crashed", e);
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
            SkyAbyss.LOGGER.info("SELFTEST route party PASSED (arrival pad waits for the whole party and carries it into room 1, late boarders rejoin the current room, abandon needs every yes and expires, the shrine carries the ring together)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST route party FAILED: {}", s);
            }
        }
    }

    private static void clean(ServerLevel back, UUID g) {
        TrialRoute.finish(back, g);
        GroupGates.reset(back, g);
        BackGroupData.get(back).remove(g);
    }

    private static FakePlayer fake(ServerLevel back, String id, String name) {
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.fromString(id), name));
        p.setPos(0, 0, 0);
        return p;
    }

    private static boolean near(ServerPlayer p, ServerLevel back, String titan, TrialDraw.Draw draw, int step) {
        // レイドの部屋の段は、区画に建てた部屋の中へ (二人とも同じ部屋の入口。docs/design/32 の 7 章)
        if (jp.revati.skyabyss.trial.raid.RaidRun.isRaidStep(back, Groups.keyOf(p), step)) {
            var run = jp.revati.skyabyss.trial.raid.RaidRun.of(Groups.keyOf(p));
            return run != null && run.step == step && run.ctx.cell().bounds().contains(p.position());
        }
        return false;
    }
}
