package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;
import static jp.revati.skyabyss.titan.storm.back.StormBackSelfTest.GROUP_A;
import static jp.revati.skyabyss.titan.storm.back.StormBackSelfTest.GROUP_B;
import static jp.revati.skyabyss.titan.storm.back.StormBackSelfTest.fresh;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.back.StormBackState.Trial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の作り直し (25 の 5章 A 肋の間・B 雷の回路・C 拍の舵) の自己テスト。StormBackSelfTest の流れから呼ぶ
final class StormReworkSelfTest {
    private StormReworkSelfTest() {
    }

    static void run(ServerLevel l, List<String> f) {
        checkRibGeometry(l, f);
        checkCircuitLogic(f);
        checkCircuitWorld(l, f);
        checkHelm(l, f);
        checkFirstRun(l, f);
    }

    private static void check(boolean ok, String message, List<String> f) {
        if (!ok) {
            f.add(message);
        }
    }

    // ---------------------------------------------------------------- 肋の間
    private static boolean clear(ServerLevel l, Vec3 at) {
        AABB box = new AABB(at.x - 0.25, at.y, at.z - 0.25, at.x + 0.25, at.y + 1.8, at.z + 0.25);
        return l.noCollision(box);
    }

    private static void checkRibGeometry(ServerLevel l, List<String> f) {
        // 梁: 穴の真上は 4 段分あいている (凪の昇り風が上がる高さ)。隣の梁は穴の縁から一マスで届く (3 離れている)
        for (int i = 0; i < RIB_LAYERS * RIB_HOPS; i++) {
            BlockPos c = ribLedge(i);
            check(l.getBlockState(c).is(StormKit.GRATE.getBlock()), "rib ledge " + i + " has no grate at " + c, f);
            for (int h = 1; h <= RIB_HOP + 1; h++) {
                check(l.getBlockState(c.above(h)).isAir(), "rib ledge " + i + " is blocked " + h + " above at " + c, f);
            }
            BlockPos n = ribLedge(i + 1);
            check(Math.abs(n.getX() - c.getX()) + Math.abs(n.getZ() - c.getZ()) == RIB_STEP && n.getY() - c.getY() == RIB_HOP,
                    "rib ledge " + (i + 1) + " is not one step from ledge " + i, f);
        }
        // 層の岩棚: 休み場の spawn・焚き火は昇り風の穴から 1.7 以上離れている。頂に石と光る床がある
        for (int layer = 1; layer <= RIB_LAYERS; layer++) {
            BlockPos spawn = rest("w" + (layer + 1));
            BlockPos c = ribLanding(layer);
            double d = Math.hypot(spawn.getX() - c.getX(), spawn.getZ() - c.getZ());
            check(layer == RIB_LAYERS || d > 1.7, "rest w" + (layer + 1) + " is inside the vent of its landing (" + d + ")", f);
            check(StormRib.layerAt(Vec3.atBottomCenterOf(spawn)) == layer, "layerAt does not see rest w" + (layer + 1), f);
            check(l.getBlockState(StormRib.layerLight(layer)).is(StormKit.SLATE.getBlock()), "layer light " + layer + " should start dark", f);
        }
        check(l.getBlockState(WEST_STONE).is(StormBackSetup.CHIME_STONE.get()), "no wind stone at the top ledge", f);
        // 輪の流れが通る線 (人の大きさの箱がぶつからない)
        for (int k = 0; k < RIB_LAYERS; k++) {
            lineClear(l, StormRib.upRing(k), StormRib.landingTarget(k + 1), "up ring " + k, f);
        }
        Vec3 prev = RIB_DOWN_RINGS[0];
        for (int j = 1; j <= RIB_DOWN_RINGS.length; j++) {
            Vec3 next = j < RIB_DOWN_RINGS.length ? RIB_DOWN_RINGS[j] : RIB_DOWN_LANDING;
            lineClear(l, prev, next, "down ring " + j, f);
            prev = next;
        }
        // 頂の岩棚の東の縁から最初の降りの輪まで人が届く
        Vec3 edge = new Vec3(RIB_TOP.getX() + 2.5, RIB_TOP_Y + 1, RIB_TOP.getZ() + 0.5);
        check(edge.add(0, 0.9, 0).distanceTo(RIB_DOWN_RINGS[0]) <= StormRib.CAPTURE + 0.6, "the first down ring is too far from the top ledge edge", f);
        // 輪をくぐる・層に着く・灯り・戻り方の動作 (人を一人置く)
        StormProgress a = fresh(l, GROUP_A);
        StormTestPlayer pa = StormTestPlayer.join(l, GROUP_A, "[storm_rib]", Vec3.atBottomCenterOf(rest("w1")));
        try {
            pa.moveTo(StormRib.upRing(0).x, StormRib.upRing(0).y - 0.9, StormRib.upRing(0).z, 0f, 0f);
            StormRib.tick(l);
            check(StormRib.riding(pa.getUUID()) && a.ribRingLit(0), "passing the first ring should start the current and light it", f);
            pa.moveTo(RIB_DOWN_RINGS[0].x, RIB_DOWN_RINGS[0].y - 0.9, RIB_DOWN_RINGS[0].z, 0f, 0f);
            StormRib.clear();
            StormRib.tick(l);
            check(StormRib.riding(pa.getUUID()), "passing the first down ring should start the current", f);
            StormRib.clear();
            a.setRibLayer(2);
            check(GroupOverlays.compute(l, GROUP_A).get(StormRib.layerLight(2)) != null && GroupOverlays.compute(l, GROUP_A).get(StormRib.layerLight(3)) == null,
                    "layer lights are not per group progress", f);
            check(GroupOverlays.compute(l, GROUP_B).get(StormRib.layerLight(2)) == null, "group B sees A's layer light", f);
        } finally {
            pa.leave();
            StormRib.clear();
            fresh(l, GROUP_A);
        }
        // 肋の間の落下は 8 に頭打ち (傷は 5 まで)
        check(StormRib.FALL_CAP <= 8f, "fall cap", f);
    }

    private static void lineClear(ServerLevel l, Vec3 a, Vec3 b, String name, List<String> f) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.ceil(d.length() / 0.25);
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n)).add(0, -0.9, 0);
            if (!clear(l, q)) {
                StringBuilder around = new StringBuilder();
                BlockPos bq = BlockPos.containing(q);
                for (BlockPos p : BlockPos.betweenClosed(bq.offset(-1, 0, -1), bq.offset(1, 2, 1))) {
                    if (!l.getBlockState(p).getCollisionShape(l, p).isEmpty()) {
                        around.append(' ').append(p.toShortString()).append('=').append(l.getBlockState(p).getBlock().getDescriptionId());
                    }
                }
                f.add("the current path of " + name + " is blocked at " + q + " by" + around);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- 回路の論理
    private static void checkCircuitLogic(List<String> f) {
        Set<BlockPos> seen = new HashSet<>();
        for (StormCircuit.Circuit c : StormCircuit.CIRCUITS) {
            List<BlockPos> levers = c.levers();
            // 解は一つだけ・全部戻した状態は戸に届かない
            int solutions = 0;
            for (int mask = 0; mask < (1 << levers.size()); mask++) {
                final int m = mask;
                StormCircuit.Trace t = StormCircuit.trace(c, p -> (m & (1 << levers.indexOf(p))) != 0);
                if (t.outcome() == StormCircuit.Outcome.DOOR) {
                    solutions++;
                    check(t.nodes().get(t.nodes().size() - 1).rod().equals(c.doorRod()), "circuit " + c.index() + " reaches a different door rod", f);
                }
                if (mask == 0) {
                    check(t.outcome() != StormCircuit.Outcome.DOOR, "circuit " + c.index() + " is solved with every lever down", f);
                }
            }
            check(solutions == 1, "circuit " + c.index() + " should have exactly one lever solution, has " + solutions, f);
            check(StormCircuit.solution(c).size() >= 1, "circuit " + c.index() + " solution needs a lever", f);
            // 導線はつながっている (隣り合う床)・ほかの回路や金の床・レバーの下とぶつからない
            for (StormCircuit.Edge e : c.edges()) {
                for (int i = 1; i < e.cells().size(); i++) {
                    BlockPos p = e.cells().get(i - 1);
                    BlockPos q = e.cells().get(i);
                    check(Math.abs(p.getX() - q.getX()) + Math.abs(p.getZ() - q.getZ()) == 1, "circuit " + c.index() + " edge " + e.id() + " has a gap near " + p, f);
                }
                check(e.cells().get(0).getX() == c.node(e.from()).rod().getX() && e.cells().get(0).getZ() == c.node(e.from()).rod().getZ(),
                        "edge " + e.id() + " does not start at its rod", f);
                for (BlockPos p : e.cells()) {
                    boolean shared = isNodeCell(c, p);
                    if (!shared) {
                        check(seen.add(p), "conduit cell " + p + " is used twice", f);
                    }
                    check(p.getZ() >= BONE_Z1 && p.getZ() <= BONE_Z2 && p.getX() > EAST_X1 && p.getX() < EAST_DOORS[c.index()], "conduit cell " + p + " is outside circuit " + c.index() + "'s span", f);
                }
            }
            for (BlockPos lv : c.levers()) {
                BlockPos below = lv.below();
                for (StormCircuit.Edge e : c.edges()) {
                    check(!e.cells().contains(below), "lever " + lv + " stands on a conduit", f);
                }
                check(Math.abs(lv.getX() - c.pad().getX()) > 1 || Math.abs(lv.getZ() - c.pad().getZ()) > 1, "lever " + lv + " is on the pad", f);
            }
            // 受け柱と金の床の近さ (人が立つ位置から受け柱まで 4 以内。輪は必ず受け柱へ落ちるが、見て分かる近さ)
            BlockPos in = c.node(c.intake()).rod();
            check(Math.hypot(in.getX() - c.pad().getX(), in.getZ() - c.pad().getZ()) <= 4.0, "pad of circuit " + c.index() + " is far from its intake rod", f);
        }
    }

    private static boolean isNodeCell(StormCircuit.Circuit c, BlockPos p) {
        for (StormCircuit.Node n : c.nodes()) {
            if (n.rod().getX() == p.getX() && n.rod().getZ() == p.getZ()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 回路 (世界で)
    private static void checkCircuitWorld(ServerLevel l, List<String> f) {
        for (StormBackClock.Phase ph : new StormBackClock.Phase[] {StormBackClock.Phase.CALM, StormBackClock.Phase.STORM}) {
            StormBackClock.force(ph);
            for (StormCircuit.Circuit c : StormCircuit.CIRCUITS) {
                runCircuit(l, c, ph, f);
            }
        }
        StormBackClock.force(null);
    }

    private static void runCircuit(ServerLevel l, StormCircuit.Circuit c, StormBackClock.Phase ph, List<String> f) {
        String tag = "circuit " + c.index() + " (" + ph + ")";
        StormProgress state = fresh(l, GROUP_A);
        RoomLocks.release(StormBackTrials.EAST_ROOM);
        StormEast.apply(l, state);
        Vec3 padAt = new Vec3(c.pad().getX() + 0.5, BONE_FLOOR + 1, c.pad().getZ() + 0.5);
        StormTestPlayer pa = StormTestPlayer.join(l, GROUP_A, "[storm_c" + c.index() + "]", padAt);
        try {
            boolean storm = ph == StormBackClock.Phase.STORM;
            pa.setOnGround(true);
            check(StormCircuit.padAt(pa) == c, tag + ": the pad is not recognised at its centre", f);
            // 何も倒していない: 暗い導線 (橙なし)。受け柱から先は戸に届かない
            if (c.index() == 0) {
                check(l.getBlockState(c.edges().get(0).cells().get(1)).equals(StormCircuit.B_OFF), tag + ": the grounded circuit's conduit should be dark", f);
            }
            // 誤った状態で呼ぶ: 戸は開かない (行き止まり・接地)
            fire(l, c, pa, storm);
            StormEast.openFullDoors(l, state);
            check(!state.eastDoorOpen(c.index()), tag + ": the door opened with the levers down", f);
            StormCircuit.clearFlash();
            // 解の状態に倒すと橙の筋が戸の柱まで通る (先読み)
            for (BlockPos lv : StormCircuit.solution(c)) {
                StormCircuit.setLever(l, lv, true);
            }
            StormCircuit.refreshVisuals(l, state);
            for (StormCircuit.Edge e : StormCircuit.solutionTrace(c).edges()) {
                for (BlockPos p : e.cells()) {
                    check(l.getBlockState(p).equals(StormCircuit.B_ARMED), tag + ": solution conduit " + p + " is not armed (" + l.getBlockState(p).getBlock() + ")", f);
                }
            }
            // 呼ぶ → 雷が柱から柱へ → 戸の柱が満ちる → 戸が開く
            fireWait(l, c, pa, storm);
            StormEast.openFullDoors(l, state);
            check(state.eastDoorOpen(c.index()), tag + ": the door did not open after the right levers", f);
            check(GroupGates.isOpen(l, GROUP_A, new BlockPos(EAST_DOORS[c.index()], BONE_FLOOR + 1, WZ)), tag + ": the door block is not open for the group", f);
            StormCircuit.refreshVisuals(l, state);
            for (StormCircuit.Edge e : StormCircuit.solutionTrace(c).edges()) {
                for (BlockPos p : e.cells()) {
                    check(l.getBlockState(p).equals(StormCircuit.B_LIT), tag + ": solved conduit " + p + " is not lit", f);
                }
            }
            // 置き直し (持ち主が変わった・再起動) のあとも光ったまま・柱は満ちたまま
            StormEast.apply(l, state);
            check(StormRods.isFull(l, c.doorRod()) && l.getBlockState(StormCircuit.solutionTrace(c).edges().get(0).cells().get(1)).equals(StormCircuit.B_LIT),
                    tag + ": lit conduits did not survive re-applying", f);
            // 別のグループには空の回廊
            StormEast.applyNeutral(l);
            check(!StormRods.isFull(l, c.doorRod()) && !l.getBlockState(StormCircuit.solutionTrace(c).edges().get(0).cells().get(1)).equals(StormCircuit.B_LIT),
                    tag + ": the neutral corridor is not dark", f);
            check(!StormProgress.of(l, GROUP_B).eastDoorOpen(c.index()), tag + ": group B's door opened", f);
        } finally {
            pa.leave();
            StormStrike.cancelAll(l);
            fresh(l, GROUP_A);
            StormEast.applyNeutral(l);
        }
    }

    // 呼んで、落雷まで進め、雷が走り終わるまで進める
    private static void fire(ServerLevel l, StormCircuit.Circuit c, StormTestPlayer pa, boolean storm) {
        fireWait(l, c, pa, storm);
    }

    private static void fireWait(ServerLevel l, StormCircuit.Circuit c, StormTestPlayer pa, boolean storm) {
        StormProgress state = StormProgress.of(l, GROUP_A);
        StormStrike.Strike s = StormCircuit.lure(l, GROUP_A, c, pa, storm);
        // 人には当たらず、受け柱へ落ちる
        float hp = pa.getHealth();
        for (int i = 0; i < 80 && !s.landed(); i++) {
            StormStrike.tick(l);
        }
        for (int i = 0; i < 600 && (StormCircuit.running() || i < 3); i++) {
            StormCircuit.tick(l, GROUP_A, state, storm, false);
        }
        if (pa.getHealth() < hp) {
            throw new IllegalStateException("the lure strike hurt the player on the pad");
        }
        StormCircuit.tickFlash(l);
    }

    // ---------------------------------------------------------------- 拍の舵
    private static void checkHelm(ServerLevel l, List<String> f) {
        StormHelm.clear();
        fresh(l, GROUP_A);
        fresh(l, GROUP_B);
        // 舵が三つの入口にある・押せる
        for (StormHelm.Helm h : StormHelm.HELMS) {
            check(l.getBlockState(h.calm()).is(StormBackSetup.HELM_CALM.get()) && l.getBlockState(h.storm()).is(StormBackSetup.HELM_STORM.get()),
                    "no helm blocks at the " + h.trial() + " entrance", f);
            check(StormHelm.helmAt(h.calm()) == h && StormHelm.helmAt(h.storm()) == h, "helmAt " + h.trial(), f);
        }
        StormTestPlayer pa = StormTestPlayer.join(l, GROUP_A, "[storm_h]", Vec3.atBottomCenterOf(new BlockPos(RIB_X2 - 1, BONE_FLOOR + 1, WZ)));
        try {
            // 全体の拍は嵐。A が西の凪の舵を回す: A の西だけ凪 (昇り風 4・横風なし)、B は嵐のまま
            StormBackClock.force(StormBackClock.Phase.STORM);
            check(StormHelm.liftHeight(l, pa) == StormBackClock.STORM_LIFT, "helm: lift should be 13 in the storm before the helm", f);
            StormHelm.set(l, GROUP_A, Trial.WEST, StormBackClock.Phase.CALM, pa);
            check(StormHelm.phase(l, Trial.WEST, GROUP_A) == StormBackClock.Phase.CALM && StormHelm.phase(l, Trial.WEST, GROUP_B) == StormBackClock.Phase.STORM,
                    "helm: calm lock is not per group", f);
            check(StormHelm.liftHeight(l, pa) == StormBackClock.CALM_LIFT, "helm: calm lock should lower the vent to 4", f);
            check(StormWest.gust(l, pa) == 0, "helm: no crosswind in the calm lock", f);
            check(StormHelm.phase(l, Trial.EAST, GROUP_A) == StormBackClock.Phase.STORM, "helm: the west helm changed the east", f);
            check(StormHelm.remaining(l, Trial.WEST, GROUP_A) > StormHelm.LOCK_TICKS - 40, "helm: lock should last 60 s", f);
            check(GroupOverlays.compute(l, GROUP_A).containsKey(StormHelm.HELMS.get(0).calm()) && !GroupOverlays.compute(l, GROUP_B).containsKey(StormHelm.HELMS.get(0).calm()),
                    "helm: the lit helm is not per group", f);
            check(StormBackTrials.gauge(l, pa).locked() && StormBackTrials.gauge(l, pa).phase() == StormBackClock.Phase.CALM.ordinal(), "helm: gauge does not show the lock", f);
            // 同じ舵をもう一度 = 放す。嵐に戻る
            StormHelm.set(l, GROUP_A, Trial.WEST, StormBackClock.Phase.CALM, pa);
            check(StormHelm.phase(l, Trial.WEST, GROUP_A) == StormBackClock.Phase.STORM && StormHelm.lockOf(l, Trial.WEST, GROUP_A) == null, "helm: turning it again should release", f);
            // 全体の拍が凪のとき、嵐の舵: 昇り風 13・横風 (予告から吹くまでが来る)
            StormBackClock.force(StormBackClock.Phase.CALM);
            StormHelm.set(l, GROUP_A, Trial.WEST, StormBackClock.Phase.STORM, pa);
            check(StormHelm.liftHeight(l, pa) == StormBackClock.STORM_LIFT, "helm: storm lock should raise the vent to 13", f);
            check(StormWest.gustAt(0) == 1 && StormWest.gustAt(StormWest.GUST_WARN) == 2 && StormWest.gustAt(StormWest.GUST_WARN + StormWest.GUST_BLOW) == 0, "helm: gust stages", f);
            check(StormWest.gust(l, pa) == 1, "helm: a fresh storm lock should start with the crosswind warning", f);
            StormHelm.clearGroup(l, GROUP_A);
            check(StormHelm.lockOf(l, Trial.WEST, GROUP_A) == null, "helm: clearGroup", f);
            // 前: 嵐の舵 + A が眼の間を持つ → 雷蛾が一匹。凪の舵 → いなくなる
            RoomLocks.release(StormBackTrials.EYE_ROOM);
            pa.moveTo(EYE_HALL_CENTER.getX() + 0.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 6.5);
            StormBackTrials.tickRooms(l);
            StormHelm.set(l, GROUP_A, Trial.FRONT, StormBackClock.Phase.STORM, pa);
            StormHelm.tickMoths(l);
            int moths = mothCount(l);
            check(moths == 1, "helm: the front storm lock should bring one moth, found " + moths, f);
            StormHelm.set(l, GROUP_A, Trial.FRONT, StormBackClock.Phase.CALM, pa);
            StormHelm.tickMoths(l);
            check(mothCount(l) == 0, "helm: the calm lock should clear the moth", f);
            // 東: A の舵が回廊の持ち主 A の拍を決める
            StormHelm.set(l, GROUP_A, Trial.EAST, StormBackClock.Phase.STORM, pa);
            check(StormHelm.phase(l, Trial.EAST, GROUP_A) == StormBackClock.Phase.STORM && StormHelm.phase(l, Trial.EAST, GROUP_B) == StormBackClock.Phase.CALM, "helm: east lock", f);
        } finally {
            pa.leave();
            StormHelm.clear();
            StormHelm.tickMoths(l);
            RoomLocks.release(StormBackTrials.EYE_ROOM);
            StormBackClock.force(null);
            fresh(l, GROUP_A);
        }
    }

    private static int mothCount(ServerLevel l) {
        List<Entity> m = l.getEntities((Entity) null, StormBackTrials.eyeRoom(), e -> e.getTags().contains("skyabyss_helm_moth") && e.isAlive());
        return m.size();
    }

    // ---------------------------------------------------------------- 周回の既知の道
    private static void checkFirstRun(ServerLevel l, List<String> f) {
        StormBackTrials.registerFirstRun();
        StormProgress a = fresh(l, GROUP_A);
        check(FirstRun.room("storm.west.rib") != null && FirstRun.room("storm.east.circuit") != null, "first-run rooms are not registered", f);
        check(!FirstRun.isKnownPath(l, GROUP_A, "storm.west.rib"), "a fresh group should be on its first run", f);
        check(!GroupOverlays.compute(l, GROUP_A).containsKey(ribLedge(1)), "the rib guide shows on a first run", f);
        a.record.markFirstCleared();
        check(FirstRun.isKnownPath(l, GROUP_A, "storm.west.rib") && FirstRun.isKnownPath(l, GROUP_A, "storm.east.circuit"), "a cleared group should have the known path", f);
        check(GroupOverlays.compute(l, GROUP_A).containsKey(ribLedge(1)), "the rib guide does not show on a repeat run", f);
        List<BlockPos> sol = new ArrayList<>(StormCircuit.solutionCells());
        check(!sol.isEmpty() && GroupOverlays.compute(l, GROUP_A).containsKey(sol.get(0)), "the circuit guide does not show on a repeat run", f);
        check(!GroupOverlays.compute(l, GROUP_B).containsKey(ribLedge(1)), "group B sees A's known path", f);
        fresh(l, GROUP_A);
        jp.revati.skyabyss.titan.group.BackGroupData.get(l).remove(GROUP_A);
    }
}
