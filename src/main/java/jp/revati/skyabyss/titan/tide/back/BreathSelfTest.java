package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.BreathLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

// 息の間 (亀の肺。docs/design/24 の案 C) の自己テスト (SKYABYSS_SELFTEST。結果は SELFTEST breath PASSED / FAILED)。潮殻の背を建てたあと (TideBackSelfTest の段のあと) に走る。
//  A. 形: 皿の並び (一歩の高さ・皿の間の距離・気室に収まる)、乗り場の道が天井に頭をぶつけない、予約の間 2・3 の床と同じ高さの皿
//  B. 建物: 水たまり・皿・光る泡・風の柱の穴と壁・舞台の円・展望の間・碑。掘ったところが外へ開いていない (TideBackSelfTest の flood が見る)
//  C. 運びの確かさ (試作): すべての運び・下りを、乗る位置のずれ・遅れ・風のようなふらつきを変えて何百回も走らせ、着地の皿・舞台に着く割合と、壁・床にぶつからないこと。
//     比べに、バニラの泡の柱のふるまい (人が空中で動ける量だけで操作する) を同じ道で走らせた割合を出す (採らなかった理由の数)
//  D. 落ちても詰まない・死なない: 気室の落下は傷 0、速く落ちるとゆっくり落ちる息、すべての乗り場から縁まで降りられる (詰み監査は TideBackAudit の extra)
//  E. 偽のプレイヤーで通し: 縁の泡 → 皿 → … → 舞台 → (柱が眠っていれば乗れない / 退けた・周回・ピースフルなら) 展望の間 → 下りの台。
//     遠い泡は満ちた潮のあいだだけ、近い泡はどの潮でも昇れる (潮・時間・天気で閉じない)。しゃがみで途中をやめられる。しゃがみ続けて降りられる
//  F. 進み・周回: 高さの記録と壁の輪、周回のグループ (心臓を止めた) は真珠守りが出ず、既知の道の光る床、再戦の台
public final class BreathSelfTest {
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();

    private BreathSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        List<SelfTestRunner.Step> steps = new ArrayList<>();
        steps.add(SelfTestRunner.Step.once("breath (load)", server -> {
            ServerLevel back = server.getLevel(TideTravel.TIDE_BACK);
            if (back != null) {
                for (int cx = -2; cx <= 1; cx++) {
                    for (int cz = -2; cz <= 1; cz++) {
                        back.setChunkForced(cx, cz, true);
                    }
                }
            }
        }));
        steps.add(SelfTestRunner.Step.once("breath", 40, BreathSelfTest::run));
        return steps;
    }

    static void check(boolean ok, String message) {
        if (!ok) {
            FAILURES.add(message);
        }
    }

    static void run(MinecraftServer server) {
        FAILURES.clear();
        NOTES.clear();
        ServerLevel back = server.getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            SkyAbyss.LOGGER.error("SELFTEST breath FAILED: tide_back is missing");
            return;
        }
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            Breath.clear();
            BreathSummit.clear();
            geometry();
            structure(back);
            lifts(back);
            falls(back);
            rides(back);
            progressAndRepeat(back);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST breath crashed", e);
            FAILURES.add("crashed: " + e);
        } finally {
            server.setDifficulty(before, true);
            TideClock.force(null);
            Breath.clear();
            BreathSummit.clear();
            for (int cx = -2; cx <= 1; cx++) {
                for (int cz = -2; cz <= 1; cz++) {
                    back.setChunkForced(cx, cz, false);
                }
            }
        }
        if (FAILURES.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST breath PASSED ({})", String.join("; ", NOTES));
        } else {
            FAILURES.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST breath FAILED: {}", f));
            SkyAbyss.LOGGER.error("SELFTEST breath FAILED: {} problems ({})", FAILURES.size(), String.join("; ", NOTES));
        }
    }

    // ---------------------------------------------------------------- A. 形
    private static void geometry() {
        List<Dish> d = dishes();
        check(d.size() == DISHES, "dish count");
        double maxRise = 0;
        double minChord = 1e9;
        double maxChord = 0;
        for (int k = 0; k < d.size(); k++) {
            Dish a = d.get(k);
            // 皿が気室に収まる (縁の輪の外が気室の壁の内側)
            check(Math.hypot(a.wx() - WX, a.wz() - WZ) + RING_R <= R_A + 0.01, "dish " + k + " pokes out of the chamber");
            if (k + 1 < d.size()) {
                Dish b = d.get(k + 1);
                double rise = b.floorY() - a.floorY();
                double chord = Math.hypot(b.wx() - a.wx(), b.wz() - a.wz());
                maxRise = Math.max(maxRise, rise);
                minChord = Math.min(minChord, chord);
                maxChord = Math.max(maxChord, chord);
                check(rise >= 3 && rise <= 7, "dish " + k + " -> " + (k + 1) + " rises " + rise);
                check(chord >= 7.0 && chord <= 13.0, "dish " + k + " -> " + (k + 1) + " is " + chord + " apart");
            }
            check(a.floorY() + 4 <= A_TOP, "dish " + k + " has no head room under the ceiling");
        }
        check(DISH_FLOOR[2] == UPPER_2_FLOOR() && DISH_FLOOR[5] == UPPER_3_FLOOR(), "dishes 2 and 5 are not level with reserved rooms 2 and 3");
        // 風の柱: 最後の皿のまんなかが、舞台の円の中で、穴 + 壁が収まる
        Dish v = vent();
        check(Math.hypot(v.wx() - WX, v.wz() - WZ) + VENT_WALL_R <= ARENA_R + 0.6, "the vent wall leaves the arena");
        // 乗り場の道: 折れ線の高さが天井 (気室 A は 137) と展望の間の屋根を越えない、道のりが長すぎない (3 秒以内)
        for (Lift l : Breath.LIFTS) {
            List<Vec3> path = l.path();
            double len = length(path);
            double ticks = 0;
            double maxY = 0;
            for (int i = 0; i + 1 < path.size(); i++) {
                double seg = path.get(i).distanceTo(path.get(i + 1));
                ticks += seg / (Math.abs(path.get(i + 1).y - path.get(i).y) < 0.05 ? SPEED_FLAT
                        : (l.to() == 10 ? SPEED_TOWER : (path.get(i + 1).y > path.get(i).y ? SPEED_UP : SPEED_DOWN)));
                maxY = Math.max(maxY, path.get(i + 1).y);
            }
            check(ticks <= 66, "lift " + l.from() + " -> " + l.to() + " takes " + Math.round(ticks) + " ticks (the server kicks floating players at 80)");
            if (l.b().kind() == Kind.DISH) {
                check(maxY <= CEILING_FEET + 0.01, "lift " + l.from() + " -> " + l.to() + " hits the ceiling (" + maxY + ")");
            }
            check(len > 1, "lift " + l.from() + " -> " + l.to() + " is empty");
            // 着地は行き先の泡の外 (着いてすぐ乗り直さない)
            for (Lift other : Breath.LIFTS) {
                if (other.a().id() == l.b().id()) {
                    double[] p = other.pad();
                    check(Math.hypot(l.b().landX() - p[0], l.b().landZ() - p[1]) > other.padRadius() + 0.45,
                            "landing of " + l.from() + " -> " + l.to() + " is on the bubble of " + other.from() + " -> " + other.to());
                }
            }
        }
        // 遠い泡の道が一つ飛ばし
        long far = Breath.LIFTS.stream().filter(Lift::far).count();
        check(far == DISHES - 2, "far lifts " + far);
        // 近い泡だけで縁から展望の間まで着く (潮が引いていても昇れる)
        boolean[] reach = new boolean[nodes().size()];
        reach[0] = true;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Lift l : Breath.LIFTS) {
                if (!l.far() && reach[l.from()] && !reach[l.to()]) {
                    reach[l.to()] = true;
                    changed = true;
                }
            }
        }
        for (int i = 0; i < reach.length; i++) {
            check(reach[i], "node " + nodes().get(i).label() + " is not reached by the near bubbles alone");
        }
        check(farActive(0.0) == false && farActive(1.0) == true && farActive(0.74) == false, "far bubble rule");
        NOTES.add("geometry: " + DISHES + " dishes, rise<=" + (int) maxRise + ", spacing " + String.format("%.1f", minChord) + "-" + String.format("%.1f", maxChord)
                + ", " + Breath.LIFTS.size() + " lifts (" + far + " far)");
    }

    private static int UPPER_2_FLOOR() {
        return TideBackLayout.UPPER_2.floor();
    }

    private static int UPPER_3_FLOOR() {
        return TideBackLayout.UPPER_3.floor();
    }

    // ---------------------------------------------------------------- B. 建物
    private static void structure(ServerLevel back) {
        // 水は水たまりと皿の盆だけ (漏れて滝にならない): 縁の上から気室の天井の下まで、盆の外に水が無い
        int stray = 0;
        BlockPos firstStray = null;
        for (int x = CX - 16; x <= CX + 16; x++) {
            for (int z = CZ - 16; z <= CZ + 16; z++) {
                for (int y = LIP + 1; y <= A_TOP; y++) {
                    BlockPos q = new BlockPos(x, y, z);
                    if (back.getFluidState(q).isEmpty()) {
                        continue;
                    }
                    boolean basin = false;
                    for (Dish dd : dishes()) {
                        basin |= y == dd.floorY() && dd.basinContains(x, z);
                    }
                    if (!basin) {
                        stray++;
                        if (firstStray == null) {
                            firstStray = q;
                        }
                    }
                }
            }
        }
        check(stray == 0, stray + " stray water cells in the chamber (first " + firstStray + ")");
        // 水たまり (縁と同じ高さの水面、深さ 4)・底はガラス (光が広間まで)
        BlockState water = Blocks.WATER.defaultBlockState();
        check(back.getBlockState(new BlockPos(CX, LIP, CZ)).getFluidState().isSource(), "pool surface is not water");
        check(back.getBlockState(new BlockPos(CX, POOL_FLOOR, CZ)).is(Blocks.LIGHT_BLUE_STAINED_GLASS), "pool floor is not glass");
        check(back.getBlockState(new BlockPos(CX + 11, LIP, CZ)).getFluidState().isEmpty(), "water leaked onto the rim");
        // 皿
        for (Dish d : dishes()) {
            BlockPos c = new BlockPos(d.cx(), d.floorY(), d.cz());
            check(back.getBlockState(c).getFluidState().isSource(), "dish " + d.index() + " has no water");
            check(back.getBlockState(d.pad()).is(Blocks.SEA_LANTERN), "dish " + d.index() + " has no glowing bubble");
            check(!back.getBlockState(new BlockPos(d.cx() + 3, d.floorY(), d.cz())).isAir() || !back.getBlockState(new BlockPos(d.cx(), d.floorY(), d.cz() + 3)).isAir(),
                    "dish " + d.index() + " ring is missing");
            check(back.getBlockState(c.above(2)).isAir(), "dish " + d.index() + " is not open above");
        }
        for (Lift l : Breath.LIFTS) {
            if (l.far() && l.a().kind() == Kind.DISH) {
                double[] p = l.pad();
                BlockPos at = new BlockPos((int) Math.floor(p[0]), l.a().dish() >= 0 ? dish(l.a().dish()).floorY() - 1 : 0, (int) Math.floor(p[1]));
                check(back.getBlockState(at).is(Blocks.OCHRE_FROGLIGHT), "far bubble of dish " + l.a().dish() + " is not amber at " + at.toShortString());
            }
        }
        // 気室は空気 (A と B)、舞台の床は固い、柱の穴は抜けている・壁がある
        for (int y = LIP + 1; y <= A_TOP; y += 6) {
            check(back.getBlockState(new BlockPos(CX, y, CZ)).isAir(), "chamber A is not carved at y=" + y);
        }
        for (int y = ARENA_FLOOR + 1; y <= B_TOP; y += 6) {
            check(back.getBlockState(new BlockPos(CX, y, CZ + 1)).isAir(), "chamber B is not carved at y=" + y);
        }
        check(back.getBlockState(new BlockPos(CX - 3, ARENA_FLOOR, CZ + 2)).blocksMotion(), "arena floor is not solid: " + back.getBlockState(new BlockPos(CX - 3, ARENA_FLOOR, CZ + 2)));
        Dish v = vent();
        for (int y = v.floorY() + 1; y <= ARENA_FLOOR + 1; y++) {
            check(back.getBlockState(new BlockPos(v.cx(), y, v.cz())).isAir(), "vent shaft blocked at y=" + y);
        }
        check(back.getBlockState(new BlockPos(v.cx() + 2, ARENA_FLOOR + 1, v.cz())).blocksMotion() || back.getBlockState(new BlockPos(v.cx(), ARENA_FLOOR + 1, v.cz() + 2)).blocksMotion(),
                "vent has no wall around it");
        // 舞台の円: 半径 ARENA_R の中の床がすべて固い (縁から落ちない・床の穴は風の柱だけ)
        int holes = 0;
        int cells = 0;
        for (int x = CX - 7; x <= CX + 7; x++) {
            for (int z = CZ - 7; z <= CZ + 7; z++) {
                if (dist(x, z) <= ARENA_R) {
                    cells++;
                    if (!back.getBlockState(new BlockPos(x, ARENA_FLOOR, z)).blocksMotion()) {
                        holes++;
                        check(Math.hypot(x - v.cx(), z - v.cz()) <= VENT_HOLE_R + 0.01, "arena floor hole at " + x + "," + z + " is not the vent");
                    }
                    // 円の外側は壁 (空気でない)
                    if (dist(x, z) > ARENA_R - 1 && dist(x, z) <= ARENA_R && Math.hypot(x - v.cx(), z - v.cz()) > VENT_WALL_R) {
                        check(!back.getBlockState(new BlockPos(x, ARENA_FLOOR, z)).isAir(), "arena edge has a gap at " + x + "," + z);
                    }
                }
            }
        }
        // 舞台の縁: 床の円の外側 1 マスは固い壁 (落ちない)
        for (int k = 0; k < 72; k++) {
            double a = Math.PI * 2 * k / 72;
            int x = CX + (int) Math.round(Math.cos(a) * (radiusB(ARENA_FLOOR + 3) + 1.2));
            int z = CZ + (int) Math.round(Math.sin(a) * (radiusB(ARENA_FLOOR + 3) + 1.2));
            check(back.getBlockState(new BlockPos(x, ARENA_FLOOR + 3, z)).blocksMotion(), "tower wall around the arena has a gap at " + x + "," + z);
        }
        // 展望の間: 床・空気・屋根・光る台・追憶の石・休み場
        check(back.getBlockState(new BlockPos(CX - 4, DECK_FLOOR, CZ - 4)).blocksMotion(), "lookout floor");
        check(back.getBlockState(new BlockPos(CX, DECK_FLOOR, CZ)).isAir() && back.getBlockState(new BlockPos(CX, DECK_FLOOR + 1, CZ)).isAir(), "lookout hole");
        check(back.getBlockState(RETURN_PAD).is(Blocks.SEA_LANTERN), "return pad");
        check(back.getBlockState(KEEPSAKE).is(Blocks.LODESTONE) && back.getBlockState(REMATCH).is(Blocks.LODESTONE), "memory stone / rematch stand");
        check(back.getBlockState(new BlockPos(CX, OBS_ROOF, CZ - 5)).blocksMotion(), "lookout roof");
        // 戸口はいつも開いている (封印の戸のブロックがない)
        for (int z = TideBackLayout.UPPER_GATE_Z - 1; z <= TideBackLayout.UPPER_GATE_Z + 1; z++) {
            for (int y = LIP + 1; y <= LIP + 3; y++) {
                check(!back.getBlockState(new BlockPos(TideBackLayout.UPPER_GATE_X, y, z)).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())
                        && back.getBlockState(new BlockPos(TideBackLayout.UPPER_GATE_X, y, z)).getCollisionShape(back, new BlockPos(TideBackLayout.UPPER_GATE_X, y, z)).isEmpty(),
                        "the chamber door is closed at y=" + y);
            }
        }
        NOTES.add("structure: pool " + (int) Math.PI * (int) (POOL_R * POOL_R) + " water cells, arena " + cells + " cells (" + holes + " vent hole cells)");
    }

    // ---------------------------------------------------------------- C. 運びの確かさ
    private record Run(boolean arrived, int ticks, int blocked, String where) {
    }

    private static boolean free(ServerLevel back, Vec3 p) {
        return back.noCollision(new AABB(p.x - 0.3, p.y + 0.001, p.z - 0.3, p.x + 0.3, p.y + 1.8, p.z + 0.3));
    }

    // 1 回走らせる: 位置のずれ・遅れ (delay tick 前の速度が効く)・ふらつき (noise)
    private static BlockPos findBlock(ServerLevel back, Vec3 p) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos b = BlockPos.containing(p.x + dx * 0.3, p.y + dy * 0.9, p.z + dz * 0.3);
                    if (!back.getBlockState(b).getCollisionShape(back, b).isEmpty()) {
                        return b;
                    }
                }
            }
        }
        return BlockPos.containing(p);
    }

    private static Run simulate(ServerLevel back, List<Vec3> path, Vec3 start, boolean tower, Random rng, int delay, double noise) {
        Vec3 pos = start;
        double s = 0;
        ArrayDeque<Vec3> pending = new ArrayDeque<>();
        int blocked = 0;
        String where = "";
        for (int t = 0; t < Breath.MAX_RIDE_TICKS; t++) {
            BreathLayout.Step st = BreathLayout.step(path, pos, s, tower);
            if (st.done()) {
                return new Run(true, t, blocked, where);
            }
            s = st.s();
            pending.add(st.velocity());
            Vec3 applied = pending.size() > delay ? pending.poll() : Vec3.ZERO;
            Vec3 move = applied.add(rng.nextGaussian() * noise, 0, rng.nextGaussian() * noise);
            Vec3 next = pos.add(move);
            if (!free(back, next)) {
                Vec3 slid = pos;
                for (Vec3 axis : new Vec3[] {new Vec3(move.x, 0, 0), new Vec3(0, move.y, 0), new Vec3(0, 0, move.z)}) {
                    Vec3 trial = slid.add(axis);
                    if (free(back, trial)) {
                        slid = trial;
                    }
                }
                // 数えるのは、着地の 1 ブロック上までに、横から壁・縁・床にぶつかったとき (着地のときの床へのめり込み・縁のすれすれは、人なら床の上に立つだけ)
                boolean horizontal = Math.abs(slid.x - next.x) > 1e-9 || Math.abs(slid.z - next.z) > 1e-9;
                if (horizontal && pos.y > path.get(path.size() - 1).y + 1.7) {
                    blocked++;
                    if (where.isEmpty()) {
                        BlockPos bp = findBlock(back, next);
                        where = "t=" + t + " at " + next + " block " + bp.toShortString() + " " + back.getBlockState(bp).getBlock().getDescriptionId();
                    }
                }
                next = slid;
            }
            pos = next;
        }
        return new Run(false, Breath.MAX_RIDE_TICKS, blocked, where);
    }

    private static void lifts(ServerLevel back) {
        Random rng = new Random(4242);
        int total = 0;
        int ok = 0;
        int harshTotal = 0;
        int harshOk = 0;
        int touched = 0;
        int longest = 0;
        for (Lift l : Breath.LIFTS) {
            List<Vec3> path = l.path();
            Vec3 end = path.get(path.size() - 1);
            double[] pad = l.pad();
            for (int i = 0; i < 160; i++) {
                // 乗る位置: 泡の中のどこでも (半径いっぱい)
                double a = rng.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(rng.nextDouble()) * l.padRadius();
                Vec3 start = new Vec3(pad[0] + Math.cos(a) * r, l.padY() + rng.nextDouble() * 0.15, pad[1] + Math.sin(a) * r);
                Run run = simulate(back, path, start, l.to() == 10, rng, 2, 0.05);
                total++;
                longest = Math.max(longest, run.ticks());
                if (run.arrived() && run.blocked() == 0) {
                    ok++;
                } else {
                    touched++;
                    if (touched <= 4) {
                        FAILURES.add("lift " + l.from() + " -> " + l.to() + (l.far() ? " (far)" : "") + " failed from " + start + " (arrived " + run.arrived() + ", blocked " + run.blocked()
                                + " ticks, " + run.ticks() + " ticks; " + run.where() + ")");
                    }
                }
                // 厳しい通信 (遅れ 4 tick、ふらつき 2 倍)
                Run harsh = simulate(back, path, start, l.to() == 10, rng, 4, 0.1);
                harshTotal++;
                if (harsh.arrived()) {
                    harshOk++;
                }
            }
            // 着地は行き先の中: 皿なら盆の中、舞台・展望の間なら床の上
            check(free(back, end) && !free(back, end.subtract(0, 0.4, 0)) || back.getFluidState(BlockPos.containing(end)).isSource(),
                    "landing of " + l.from() + " -> " + l.to() + " is not on a floor/in the basin: " + end);
        }
        check(ok == total, "lifts: only " + ok + "/" + total + " ordinary rides arrived cleanly");
        check(harshOk * 100 >= harshTotal * 98, "lifts under bad latency: " + harshOk + "/" + harshTotal + " (needs 98%)");
        // 下り
        int dTotal = 0;
        int dOk = 0;
        for (Node n : nodes()) {
            if (n.kind() == Kind.ENTRY) {
                continue;
            }
            for (int i = 0; i < 80; i++) {
                Vec3 start = standSpot(n, rng);
                List<Vec3> path = BreathLayout.descent(n.id(), start);
                Run run = simulate(back, path, start, n.kind() == Kind.DECK, rng, 2, 0.05);
                dTotal++;
                if (run.arrived() && run.blocked() == 0) {
                    dOk++;
                } else if (dTotal - dOk <= 4) {
                    FAILURES.add("descent from " + n.label() + " failed from " + start + " (arrived " + run.arrived() + ", blocked " + run.blocked() + "; " + run.where() + ")");
                }
            }
        }
        check(dOk == dTotal, "descents: " + dOk + "/" + dTotal);
        // 比べ: バニラの泡の柱 (上へ押すだけ。人は空中で 0.02 の加速しかできない) は、操作なしでは次の皿に着かない
        int vanillaBlind = 0;
        int vanillaSteer = 0;
        int vTotal = 0;
        for (Lift l : Breath.LIFTS) {
            if (l.far() || l.to() >= 9 || l.from() == 0) {
                continue;
            }
            for (int i = 0; i < 60; i++) {
                vTotal++;
                if (vanillaColumn(l, rng, false)) {
                    vanillaBlind++;
                }
                if (vanillaColumn(l, rng, true)) {
                    vanillaSteer++;
                }
            }
        }
        NOTES.add("lifts: " + ok + "/" + total + " clean rides, " + harshOk + "/" + harshTotal + " under bad latency, longest " + longest + " ticks; descents " + dOk + "/" + dTotal
                + "; vanilla bubble column prototype: " + vanillaBlind * 100 / Math.max(1, vTotal) + "% land on the next dish with no steering, " + vanillaSteer * 100 / Math.max(1, vTotal)
                + "% with a perfect-steering bot (chosen: scripted lift)");
        check(vanillaBlind * 100 < vTotal * 50, "vanilla column prototype is unexpectedly reliable without steering");
    }

    // バニラの泡の柱 (上向き): 中は vy = min(0.7, vy + 0.06)、水面の上で打ち上がる。皿のまんなかに柱を立て、行き先まで人が空中で動ける分だけ
    private static boolean vanillaColumn(Lift l, Random rng, boolean steer) {
        double[] pad = l.pad();
        Node b = l.b();
        double x = pad[0] + (rng.nextDouble() - 0.5) * l.padRadius();
        double z = pad[1] + (rng.nextDouble() - 0.5) * l.padRadius();
        double y = l.padY();
        double vx = 0;
        double vy = 0;
        double vz = 0;
        double columnTop = l.padY() + (b.landY() - l.padY());
        for (int t = 0; t < 120; t++) {
            boolean inside = y < columnTop && Math.hypot(x - pad[0], z - pad[1]) < 1.0;
            if (inside) {
                vy = Math.min(0.7, vy + 0.06);
            } else {
                vy = (vy - 0.08) * 0.98;
            }
            double ax = 0;
            double az = 0;
            if (steer && !inside) {
                double dx = b.landX() - x;
                double dz = b.landZ() - z;
                double dl = Math.hypot(dx, dz);
                if (dl > 0.2) {
                    ax = dx / dl * 0.02;
                    az = dz / dl * 0.02;
                }
            }
            vx = (vx + ax) * 0.91;
            vz = (vz + az) * 0.91;
            x += vx;
            z += vz;
            y += vy;
            if (y < b.landY() - 0.01 && t > 4 && vy < 0) {
                return Math.hypot(x - b.landX(), z - b.landZ()) < 2.2 && Math.hypot(x - (b.landX()), z - b.landZ()) < RING_R;
            }
            if (y <= l.padY() && t > 4) {
                return false;
            }
        }
        return false;
    }

    private static Vec3 standSpot(Node n, Random rng) {
        double a = rng.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(rng.nextDouble());
        return switch (n.kind()) {
            case DISH -> {
                Dish d = dish(n.dish());
                double rr = r * 1.3;
                yield new Vec3(d.wx() + Math.cos(a) * rr, d.floorY(), d.wz() + Math.sin(a) * rr);
            }
            case ARENA -> {
                double rr = 1.0 + r * 3.0;
                Vec3 p = new Vec3(WX + Math.cos(a) * rr, ARENA_FLOOR + 1, WZ + Math.sin(a) * rr);
                Dish v = vent();
                if (Math.hypot(p.x - (REMATCH.getX() + 0.5), p.z - (REMATCH.getZ() + 0.5)) < 2.0) {
                    p = new Vec3(WX + 1.5, ARENA_FLOOR + 1, WZ - 1.0);
                }
                // 風の柱の壁の中は避ける
                yield Math.hypot(p.x - v.wx(), p.z - v.wz()) < VENT_WALL_R + 1.2 ? new Vec3(WX - 2, ARENA_FLOOR + 1, WZ + 1) : p;
            }
            default -> new Vec3(WX - 3 + r * 4, DECK_FLOOR + 1, WZ - 2 + Math.sin(a) * 2);
        };
    }

    // ---------------------------------------------------------------- D. 落ちても詰まない・死なない
    private static void falls(ServerLevel back) {
        UUID id = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b1");
        TideBackSelfTest.TestPlayer p = TideBackSelfTest.join(back, id, "[BreathFall]", new BlockPos(CX, LIP + 30, CZ));
        try {
            // 気室の落下は傷にならない
            LivingFallEvent event = new LivingFallEvent(p, 30f, 1f);
            NeoForge.EVENT_BUS.post(event);
            check(event.getDamageMultiplier() == 0f, "a fall inside the chamber still hurts");
            LivingFallEvent outside = new LivingFallEvent(p, 30f, 1f);
            p.moveTo(-60, 70, 0);
            NeoForge.EVENT_BUS.post(outside);
            check(outside.getDamageMultiplier() == 1f, "falls outside the chamber must keep their normal damage");
            p.moveTo(CX + 0.5, LIP + 30, CZ + 0.5);
            // 速く落ちたらゆっくり落ちる息が受けとめる
            Breath.tick(back, false);
            p.moveTo(CX + 0.5, LIP + 28.9, CZ + 0.5);
            Breath.tick(back, false);
            check(p.hasEffect(MobEffects.SLOW_FALLING), "a fast fall is not caught by the breath");
        } finally {
            Breath.forget(id);
            TideBackSelfTest.leave(back, p);
        }
        NOTES.add("falls: no fall damage in the chamber, slow-falling catch");
    }

    // ---------------------------------------------------------------- E. 偽のプレイヤーで通し
    private static void settle(ServerLevel back, TideBackSelfTest.TestPlayer p, boolean peaceful, int maxTicks) {
        for (int t = 0; t < maxTicks; t++) {
            Breath.tick(back, peaceful);
            if (Breath.riding(p)) {
                p.setPos(p.position().add(p.getDeltaMovement()));
                p.setOnGround(false);
            } else {
                p.setOnGround(true);
                if (t > 4 && !Breath.riding(p)) {
                    return;
                }
            }
            back.getGameTime();
        }
    }

    private static boolean rideTo(ServerLevel back, TideBackSelfTest.TestPlayer p, boolean peaceful, int maxTicks) {
        boolean ridden = false;
        for (int t = 0; t < maxTicks; t++) {
            Breath.tick(back, peaceful);
            if (Breath.riding(p)) {
                ridden = true;
                p.setPos(p.position().add(p.getDeltaMovement()));
                p.setOnGround(false);
            } else {
                p.setOnGround(true);
                if (ridden) {
                    return true;
                }
            }
        }
        return ridden;
    }

    private static void rides(ServerLevel back) {
        UUID id = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b2");
        TideBackSelfTest.TestPlayer p = TideBackSelfTest.join(back, id, "[BreathRide]", new BlockPos(CX, LIP + 1, CZ));
        BackGroupData data = BackGroupData.get(back);
        try {
            TideBackState.Group st = TideBackState.of(back, id);
            // 近い泡で、縁から舞台まで (潮が引いていても)
            TideClock.force(TideClock.Phase.LOW);
            Node entry = entry();
            p.moveTo(entry.padX(), entry.padY(), entry.padZ());
            int steps = 0;
            for (int hop = 0; hop < DISHES + 1; hop++) {
                boolean rode = rideTo(back, p, false, 140);
                if (!rode) {
                    FAILURES.add("no ride started at hop " + hop + " from " + p.position());
                    break;
                }
                steps++;
                Node target = hop < DISHES ? dishNode(hop) : arena();
                check(p.position().distanceTo(new Vec3(target.landX(), target.landY(), target.landZ())) < 1.2,
                        "hop " + hop + " ended at " + p.position() + " instead of " + target.label());
                // 着地は泡の外: 勝手にもう一度乗り始めない
                for (int t = 0; t < 30; t++) {
                    Breath.tick(back, false);
                    p.setOnGround(true);
                }
                check(!Breath.riding(p), "the rider boarded again right after landing at " + target.label());
                if (hop == DISHES) {
                    break;
                }
                // 次の泡へ歩いて乗る
                Node here = dishNode(hop);
                p.moveTo(here.padX(), here.padY(), here.padZ());
                if (hop == DISHES - 1) {
                    // 皿 7 の泡 (舞台へ)
                    p.moveTo(here.padX(), here.padY(), here.padZ());
                }
            }
            check(steps == DISHES + 1, "climb took " + steps + " rides");
            // 遠い泡: 引き潮では働かず、満潮で皿を一つ飛ばす
            Lift far = Breath.LIFTS.stream().filter(Lift::far).findFirst().orElseThrow();
            double[] fp = far.pad();
            TideClock.force(TideClock.Phase.LOW);
            p.moveTo(fp[0], far.padY(), fp[1]);
            p.setOnGround(true);
            Breath.clear();
            Breath.tick(back, false);
            check(!Breath.riding(p), "the far bubble worked at low tide");
            TideClock.force(TideClock.Phase.HIGH);
            Breath.clear();
            p.moveTo(fp[0], far.padY(), fp[1]);
            boolean rode = rideTo(back, p, false, 140);
            check(rode && p.position().distanceTo(new Vec3(far.b().landX(), far.b().landY(), far.b().landZ())) < 1.2, "the far bubble did not carry to " + far.b().label() + " at high tide");
            // 舞台のまんなかの泡 (展望の間へ): 眠っているあいだ (初回・退けていない) は乗れない。退けた・周回・ピースフルで乗れる
            Node arena = arena();
            Breath.clear();
            p.moveTo(arena.padX(), arena.padY(), arena.padZ());
            p.setOnGround(true);
            st.record.setFlag("keep.first_cleared", false);
            for (int t = 0; t < 12; t++) {
                Breath.tick(back, false);
            }
            check(!Breath.riding(p) && !Breath.deckOpen(back, p, false), "the wind pillar worked before the keeper was stilled");
            check(Breath.deckOpen(back, p, true), "the wind pillar does not work in peaceful");
            st.markKeeperDefeated();
            boolean up = rideTo(back, p, false, 160);
            check(up && p.position().distanceTo(new Vec3(deck().landX(), deck().landY(), deck().landZ())) < 1.2, "the wind pillar did not carry to the lookout (" + p.position() + ")");
            // 展望の間から、しゃがみ続けて降りる
            Breath.clear();
            p.setShiftKeyDown(true);
            boolean down = false;
            for (int t = 0; t < Breath.DESCEND_SNEAK_TICKS + 120; t++) {
                Breath.tick(back, false);
                if (Breath.riding(p)) {
                    down = true;
                    p.setPos(p.position().add(p.getDeltaMovement()));
                    p.setOnGround(false);
                } else {
                    p.setOnGround(true);
                    if (down) {
                        break;
                    }
                }
            }
            p.setShiftKeyDown(false);
            check(down && p.position().distanceTo(new Vec3(arena.landX(), arena.landY(), arena.landZ())) < 1.3, "sneak descent from the lookout ended at " + p.position());
            // しゃがんで途中でやめられる (落ちても痛くない)
            Breath.clear();
            Node d3 = dishNode(3);
            p.moveTo(d3.padX(), d3.padY(), d3.padZ());
            p.setShiftKeyDown(false);
            int age = 0;
            boolean cancelled = false;
            for (int t = 0; t < 100; t++) {
                Breath.tick(back, false);
                if (Breath.riding(p)) {
                    age++;
                    p.setPos(p.position().add(p.getDeltaMovement()));
                    p.setOnGround(false);
                    if (age == 16) {
                        p.setShiftKeyDown(true);
                    }
                } else if (age > 0) {
                    cancelled = age < 40;
                    break;
                } else {
                    p.setOnGround(true);
                }
            }
            p.setShiftKeyDown(false);
            check(cancelled, "sneaking did not cancel the ride (age " + age + ")");
            check(p.hasEffect(MobEffects.SLOW_FALLING), "a cancelled ride is not caught softly");
            NOTES.add("rides: " + steps + " hops from the rim to the summit at low tide, far bubble low=off/high=on, wind pillar sleeps until the keeper is stilled (open in peaceful), sneak cancel/descend");
        } finally {
            TideClock.force(null);
            Breath.clear();
            p.setShiftKeyDown(false);
            TideBackSelfTest.leave(back, p);
            data.remove(id);
        }
    }

    // ---------------------------------------------------------------- F. 進み・周回
    private static void progressAndRepeat(ServerLevel back) {
        UUID id = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b3");
        TideBackSelfTest.TestPlayer p = TideBackSelfTest.join(back, id, "[BreathProgress]", new BlockPos(CX, LIP + 1, CZ));
        BackGroupData data = BackGroupData.get(back);
        try {
            TideBackState.Group st = TideBackState.of(back, id);
            check(Breath.ringsLit(0) == 0 && Breath.ringsLit(16) == 1 && Breath.ringsLit(32) == 2 && Breath.ringsLit(47) == 3, "ring thresholds");
            check(st.upBest() == 0, "fresh group has progress");
            // 皿 2 (114) に立つと 1 つ、皿 5 (126) で 2 つ、舞台で 3 つ
            int[] heights = {2, 5, 8};
            int[] expect = {1, 2, 3};
            int[] ys = {DISH_FLOOR[2] + 1, DISH_FLOOR[5] + 1, ARENA_FLOOR + 1};
            for (int i = 0; i < 3; i++) {
                p.moveTo(WX + 8.5, ys[i], WZ);
                p.setOnGround(true);
                Breath.tick(back, false);
                check(Breath.ringsLit(st.upBest()) == expect[i], "after standing at y=" + ys[i] + " the rings lit " + Breath.ringsLit(st.upBest()) + ", expected " + expect[i]);
            }
            // 壁の輪の見かけ: 3 つ灯ると、そのグループにだけ輪の石が光る
            var overlay = GroupOverlays.compute(back, id);
            long lit = overlay.values().stream().filter(s -> s.is(Blocks.SEA_LANTERN)).count();
            check(lit > 30, "wall rings are not lit in the group's overlay (" + lit + ")");
            UUID other = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b4");
            var otherOverlay = GroupOverlays.compute(back, other);
            long otherLit = otherOverlay.values().stream().filter(s -> s.is(Blocks.SEA_LANTERN)).count();
            check(otherLit < lit, "another group sees these wall rings lit (" + otherLit + " vs " + lit + ")");
            data.remove(other);
            // 目的の板: 高さと輪の数
            p.moveTo(WX + 3, DISH_FLOOR[5] + 1, WZ + 3);
            var goal = TideGuide.upGoal(back, p, st);
            check(goal != null && goal.id().equals("up.climb") && goal.done() == 3 && goal.a() == DISH_FLOOR[5] + 1 - (LIP + 1) && goal.b() == TOTAL_HEIGHT,
                    "goal panel while climbing: " + goal);
            p.moveTo(WX - 2, ARENA_FLOOR + 1, WZ + 1);
            goal = TideGuide.upGoal(back, p, st);
            check(goal != null && goal.id().equals("up.arena"), "goal panel on the arena (asleep): " + goal);
            st.markKeeperDefeated();
            goal = TideGuide.upGoal(back, p, st);
            check(goal != null && goal.id().equals("up.lift"), "goal panel on the arena (open): " + goal);
            p.moveTo(WX - 4, DECK_FLOOR + 1, WZ);
            goal = TideGuide.upGoal(back, p, st);
            check(goal != null && goal.id().equals("up.done"), "goal panel on the lookout: " + goal);
            for (String key : List.of("up.start", "up.climb", "up.arena", "up.keeper", "up.lift", "up.done")) {
                check(TideGuide.GOAL_IDS.contains(key), "goal id missing: " + key);
                check(jp.revati.skyabyss.SignFit.lang("ja_jp").containsKey("gui.skyabyss.tide_goal." + key) && jp.revati.skyabyss.SignFit.lang("en_us").containsKey("gui.skyabyss.tide_goal." + key),
                        "goal text missing: " + key);
            }
            // 周回: 心臓を止めたグループは、真珠守りが出ず (既知の道)、風の柱は退けなくても働き、光る床の道すじがそのグループの画面に出る
            UUID repeat = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b5");
            TideBackState.Group rs = TideBackState.of(back, repeat);
            check(BreathSummit.canStart(back, repeat), "a first-run group cannot meet the keeper");
            rs.record.markFirstCleared();
            check(!FirstRun.isFirstRun(back, repeat), "first_cleared did not stick");
            check(!BreathSummit.canStart(back, repeat), "the keeper woke for a repeat-run group (known path must be quiet)");
            TideBackSelfTest.TestPlayer rp = TideBackSelfTest.join(back, repeat, "[BreathRepeat]", new BlockPos(CX, ARENA_FLOOR + 1, CZ));
            try {
                check(Breath.deckOpen(back, rp, false), "the wind pillar sleeps for a repeat-run group");
                var guide = GroupOverlays.compute(back, repeat);
                long path = guide.values().stream().filter(s -> s.is(Blocks.SEA_LANTERN)).count();
                var firstGuide = GroupOverlays.compute(back, UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b6"));
                long firstLit = firstGuide.values().stream().filter(s -> s.is(Blocks.SEA_LANTERN)).count();
                check(path > firstLit + 10, "the known-path guide floor is not shown to the repeat-run group (" + path + " lit vs " + firstLit + ")");
            } finally {
                TideBackSelfTest.leave(back, rp);
            }
            data.remove(repeat);
            // ピースフル: 真珠守りは出ず、風の柱は働く
            BreathSummit.clear();
            check(!BreathSummit.busy(back), "keeper fight state is dirty");
            NOTES.add("progress: rings 1/2/3 at 12/24/37 blocks, per-group overlay, goal panel ids; repeat run is quiet (no keeper, pillar open, guide path)");
        } finally {
            Breath.clear();
            TideBackSelfTest.leave(back, p);
            data.remove(id);
        }
    }
}
