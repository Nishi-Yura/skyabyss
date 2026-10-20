package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;

// 順路 (docs/design/32 の 2 章) の自己テスト。段の名前は "trial route"。
//  - 形: 四体とも三つの鍵・四つの段の休み場があり、入口の箱がある
//  - 流れ (背が建ててある巨神): 入口に一人で立つ → 予告のあと段 1 へ運ばれる → 越えるたびに次の段の入口へ → 三つ越えたら心臓の前室。
//    別のグループの人は動かない。越えた順が前後しても段は戻らない
//  - あきらめ: 一人のグループは提案した瞬間に成立し、記録が消える。グループのリセット・心臓を止めたときも記録が消える
public final class TrialRouteSelfTest {
    private TrialRouteSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("trial route", 10, TrialRouteSelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int worlds = 0;
        try {
            for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
                List<String> keys = TrialRoute.keysOf(titan);
                check(f, keys.size() == 3, titan + ": expected three route keys, got " + keys);
                check(f, keys.stream().distinct().count() == 3, titan + ": route keys repeat: " + keys);
                check(f, TrialRegions.keys(titan).containsAll(keys), titan + ": route keys are not stele keys: " + keys + " vs " + TrialRegions.keys(titan));
                for (int step = 1; step <= TrialRoute.LAST_STEP; step++) {
                    check(f, TrialRoute.restOf(titan, step) != null, titan + ": no rest for step " + step);
                }
                check(f, "heart".equals(TrialRoute.restOf(titan, TrialRoute.LAST_STEP)), titan + ": the last step is not the heart rest");
                check(f, TrialRoute.restOf(titan, 0) == null && TrialRoute.restOf(titan, 9) == "heart", titan + ": restOf bounds");
                AABB entry = TrialRoute.entryBox(titan);
                check(f, entry != null && entry.getXsize() >= 3 && entry.getYsize() >= 3 && entry.getZsize() >= 3, titan + ": entry box missing or tiny: " + entry);
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                if (back == null) {
                    f.add(titan + ": the back dimension is missing");
                    continue;
                }
                if (ready(back, titan)) {
                    world(f, back, titan, keys);
                    worlds++;
                } else {
                    SkyAbyss.LOGGER.info("SELFTEST trial route: {} back is not built here, checked the shape only", titan);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial route crashed", e);
            f.add("crashed: " + e);
        } finally {
            TrialRoute.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial route PASSED (keys, rests, entry boxes for four titans; walked the route in {} built back(s))", worlds);
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial route FAILED: {}", s);
            }
        }
    }

    // 段 1 の休み場の足もとが固い (背が建ててある) か
    private static boolean ready(ServerLevel back, String titan) {
        String rest = TrialRoute.restOf(titan, 1);
        BlockPos at = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
        if (at == null) {
            return false;
        }
        back.getChunk(at);
        return !back.getBlockState(at.below()).isAir() && back.getBlockState(at).isAir();
    }

    private static void world(List<String> f, ServerLevel back, String titan, List<String> keys) {
        TrialRoute.clearAll();
        UUID ida = UUID.fromString("7a0b1d00-0000-4000-8000-00000000a001");
        UUID idb = UUID.fromString("7a0b1d00-0000-4000-8000-00000000b002");
        FakePlayer a = FakePlayerFactory.get(back, new GameProfile(ida, "route_a"));
        FakePlayer b = FakePlayerFactory.get(back, new GameProfile(idb, "route_b"));
        UUID ga = Groups.keyOf(a);
        UUID gb = Groups.keyOf(b);
        TrialRoute.finish(back, ga);
        TrialRoute.finish(back, gb);
        long now = back.getGameTime();
        AABB entry = TrialRoute.entryBox(titan);
        Vec3 door = entry.getCenter();
        Vec3 bAt = new Vec3(door.x + 40, door.y, door.z + 40);
        a.setPos(door);
        b.setPos(bAt);
        List<net.minecraft.server.level.ServerPlayer> players = List.of(a, b);
        TrialRoute.PLAYERS = l -> players;
        // 入口: 立っただけでは始まらず、予告のあと始まる
        TrialRoute.tick(back, titan, players, now);
        check(f, TrialRoute.step(back, ga) == 0, titan + ": the route started without the warning");
        TrialRoute.tick(back, titan, players, now + TrialRoute.ENTRY_WAIT + 10);
        check(f, TrialRoute.step(back, ga) == 1, titan + ": the route did not start after the warning (step " + TrialRoute.step(back, ga) + ")");
        check(f, TrialRoute.step(back, gb) == 0, titan + ": another group's route started");
        check(f, near(a, back, titan, 1), titan + ": the starter was not carried to the first rest: " + a.position());
        check(f, b.position().distanceTo(bAt) < 0.5, titan + ": a player of another group was moved at the start");
        // 越えるたびに次へ (4 秒後)。別のグループは動かない
        for (int i = 0; i < 3; i++) {
            // 運びの期限は本当のゲームの時刻 (now) から数える
            long t = now + 1;
            TrialRoute.onCleared(back, ga, titan, keys.get(i));
            int expect = i + 2;
            check(f, TrialRoute.step(back, ga) == expect, titan + ": after clearing " + keys.get(i) + " the step is " + TrialRoute.step(back, ga) + ", expected " + expect);
            check(f, TrialRoute.pendingCount() == 1, titan + ": the carry was not queued");
            TrialRoute.tick(back, titan, players, t);
            check(f, TrialRoute.pendingCount() == 1, titan + ": the carry ran before its delay");
            TrialRoute.tick(back, titan, players, t + TrialRoute.CARRY_DELAY);
            check(f, TrialRoute.pendingCount() == 0, titan + ": the carry stayed queued");
            check(f, near(a, back, titan, expect), titan + ": not carried to step " + expect + " rest " + TrialRoute.restOf(titan, expect) + ": " + a.position());
            check(f, b.position().distanceTo(bAt) < 0.5, titan + ": a player of another group was carried");
        }
        check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP, titan + ": the route did not reach the heart");
        // 越える順が前後しても段は戻らない
        TrialRoute.onCleared(back, ga, titan, keys.get(0));
        check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP, titan + ": clearing an earlier trial again moved the step back");
        TrialRoute.clearAll();
        // 順路の途中で入口に立っても始め直さない
        a.setPos(door);
        TrialRoute.tick(back, titan, players, now + 5000);
        TrialRoute.tick(back, titan, players, now + 5000 + TrialRoute.ENTRY_WAIT + 10);
        check(f, TrialRoute.step(back, ga) == TrialRoute.LAST_STEP, titan + ": standing at the entrance mid-route restarted the route");
        // グループのリセットで消える
        BackGroupData.Record r = GroupProgress.of(back, ga);
        TrialRoute.reset(r);
        check(f, TrialRoute.step(back, ga) == 0 && !r.has(TrialRoute.ROUTE), titan + ": reset left the route record");
        // あきらめ: 一人のグループは提案で成立し、記録が消える (地上へ降ろすのは偽の人では確かめない)
        TrialRoute.start(back, ga, titan);
        check(f, TrialRoute.step(back, ga) == 1, titan + ": start did not set step 1");
        try {
            TrialRoute.propose(a);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.info("SELFTEST trial route: sendDown on a fake player threw ({}); checking the record only", e.toString());
        }
        check(f, TrialRoute.step(back, ga) == 0, titan + ": abandoning alone did not end the route (step " + TrialRoute.step(back, ga) + ")");
        check(f, !TrialRoute.abandonPending(ga), titan + ": an abandon proposal stayed after settling");
        // 心臓を止めたときも消える
        TrialRoute.start(back, ga, titan);
        TrialData.onHeartStopped(GroupProgress.of(back, ga));
        check(f, TrialRoute.step(back, ga) == 0, titan + ": the heart stop did not end the route");
        TrialRoute.finish(back, ga);
        TrialRoute.finish(back, gb);
    }

    private static boolean near(FakePlayer p, ServerLevel back, String titan, int step) {
        String rest = TrialRoute.restOf(titan, step);
        BlockPos at = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
        return at != null && p.position().distanceTo(Vec3.atBottomCenterOf(at)) < 2.5;
    }
}
