package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.tide.TideBackBuilder;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.mob.MobTide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 潮殻の背の本番の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST tide back PASSED / FAILED。
// 配置の形 (歩いて行ける所・行けない所、休み場の復活位置、碑文と石)、潮の時計、潮の溜め (満ちても漏れない、引けば残らない)、
// 三つの試練の進み (真珠、四つの配置が解けること・水位と戸、巣と潮招き)、三つの石で潮の底が開く、リセットと眠る試練、ピースフルの封印
public final class TideBackSelfTest {
    // 水を流してから数えるまでの tick (流れる水が広がりきるのを待つ。岩鯨の水路の息継ぎの穴が、隣の水で埋まっていた反省)
    public static final int SETTLE_TICKS = 100;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();

    private TideBackSelfTest() {
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    // SelfTestRunner の段。重い所 (建てる・溜めを満たす) は段を分け、水を流したあとに数える段は SETTLE_TICKS 待つ
    public static List<jp.revati.skyabyss.SelfTestRunner.Step> steps() {
        List<jp.revati.skyabyss.SelfTestRunner.Step> steps = new ArrayList<>();
        steps.add(jp.revati.skyabyss.SelfTestRunner.Step.once("tide back (build)", server -> guarded(server, back -> {
            FAILURES.clear();
            NOTES.clear();
            forceChunks(back, true);
            long t0 = System.currentTimeMillis();
            TideBackBuilder.build(back, TitanBackData.get(back));
            NOTES.add("built in " + (System.currentTimeMillis() - t0) + " ms");
            TideClock.force(TideClock.Phase.LOW);
            checkClock(FAILURES);
            checkFixtures(back, FAILURES);
            checkSigns(back, FAILURES);
            checkTurtle(back, FAILURES);
            fillPools(back, FAILURES, NOTES);
        })));
        steps.add(jp.revati.skyabyss.SelfTestRunner.Step.once("tide back (pools settled)", SETTLE_TICKS, server -> guarded(server, back -> {
            checkSettled(back, FAILURES, "after building");
            checkWatchSettled(back, FAILURES, "after building");
            checkWaterlogged(back, FAILURES, NOTES, "full");
            drainPools(back);
        })));
        steps.add(jp.revati.skyabyss.SelfTestRunner.Step.once("tide back (pools drained)", SETTLE_TICKS, server -> guarded(server, back -> {
            checkDrained(back, FAILURES, "after building");
            checkWaterlogged(back, FAILURES, NOTES, "ebb");
            checkOpenings(back, FAILURES, NOTES);
            checkWalk(back, FAILURES, NOTES);
            checkPuzzle(back, FAILURES, NOTES);
            checkGarden(back, FAILURES);
            checkFlats(back, FAILURES);
            checkBottom(back, FAILURES);
            checkReset(back, FAILURES, NOTES);
            // リセットのあと (庭の道の形が変わる) にも、もう一度満たして漏れを数える
            TideBackTrials.initPools(back);
            fillPools(back, FAILURES, NOTES);
        })));
        steps.add(jp.revati.skyabyss.SelfTestRunner.Step.once("tide back (pools settled after reset)", SETTLE_TICKS, server -> guarded(server, back -> {
            checkSettled(back, FAILURES, "after a reset");
            checkWatchSettled(back, FAILURES, "after a reset");
            drainPools(back);
        })));
        steps.add(jp.revati.skyabyss.SelfTestRunner.Step.once("tide back", SETTLE_TICKS, server -> guarded(server, back -> {
            checkDrained(back, FAILURES, "after a reset");
            checkPeaceful(back, FAILURES);
            TideClock.force(null);
            TideBackTrials.reset(back, false, null, true);
            forceChunks(back, false);
            if (FAILURES.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST tide back PASSED ({})", String.join("; ", NOTES));
            } else {
                FAILURES.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST tide back FAILED: {}", f));
                SkyAbyss.LOGGER.error("SELFTEST tide back FAILED: {} problems ({})", FAILURES.size(), String.join("; ", NOTES));
            }
        })));
        return steps;
    }

    private static void guarded(MinecraftServer server, java.util.function.Consumer<ServerLevel> body) {
        ServerLevel back = server.getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            FAILURES.add("tide_back is missing");
            return;
        }
        try {
            body.accept(back);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide back crashed", e);
            FAILURES.add("crashed: " + e);
        }
    }

    // 島のチャンクを読み込んだままにする (水が流れ、溜めが動くように)
    private static void forceChunks(ServerLevel back, boolean on) {
        for (int cx = TideTurtle.minX() >> 4; cx <= TideTurtle.maxX() >> 4; cx++) {
            for (int cz = TideTurtle.minZ() >> 4; cz <= TideTurtle.maxZ() >> 4; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    // ---------------------------------------------------------------- 潮の時計
    private static void checkClock(List<String> failures) {
        check(TideClock.CYCLE == 2400, "tide cycle should be 2 minutes", failures);
        check(TideClock.phaseAt(0) == TideClock.Phase.LOW && TideClock.phaseAt(999) == TideClock.Phase.LOW, "low tide is the first 50 s", failures);
        check(TideClock.phaseAt(1000) == TideClock.Phase.RISING && TideClock.phaseAt(1199) == TideClock.Phase.RISING, "rising is 10 s", failures);
        check(TideClock.phaseAt(1200) == TideClock.Phase.HIGH && TideClock.phaseAt(2199) == TideClock.Phase.HIGH, "high tide is 50 s", failures);
        check(TideClock.phaseAt(2200) == TideClock.Phase.FALLING && TideClock.phaseAt(2399) == TideClock.Phase.FALLING, "falling is 10 s", failures);
        check(TideClock.fullnessAt(500) == 0 && TideClock.fullnessAt(1500) == 1, "fullness at low/high", failures);
        check(TideClock.levelFor(TideClock.fullnessAt(1199), 3) == 3 && TideClock.levelFor(TideClock.fullnessAt(1066), 3) == 1, "levels while rising", failures);
        check(TideClock.levelFor(TideClock.fullnessAt(2399), 3) == 0, "falling ends dry", failures);
        // 敵の見る潮 (MobTide) は時計そのもの
        TideClock.force(TideClock.Phase.HIGH);
        check(MobTide.forced() == MobTide.Phase.HIGH, "mob tide does not follow the clock", failures);
        TideClock.force(TideClock.Phase.LOW);
    }

    // ---------------------------------------------------------------- 碑文・石・休み場・門
    private static void checkFixtures(ServerLevel back, List<String> failures) {
        for (Inscription i : LORE_SIGNS) {
            check(back.getBlockEntity(i.pos()) instanceof TabletBlockEntity, "inscription " + i.key() + " missing at " + i.pos(), failures);
        }
        for (TideBackState.Trial t : TideBackState.Trial.values()) {
            check(back.getBlockState(TideBackTrials.stonePos(t)).is(TideBackSetup.CHIME_STONE.get()), "chime stone " + t + " missing", failures);
        }
        for (Rest r : RESTS) {
            check(TitanBackTrials.isSafeStand(back, r.spawn()), "cannot stand at the rest spawn " + r.id() + " " + r.spawn(), failures);
            check(back.getBlockState(r.campfire()).is(Blocks.SOUL_CAMPFIRE), "rest campfire " + r.id() + " missing", failures);
            check(r.area().contains(r.spawn()) || r.id().equals("hub"), "rest spawn " + r.id() + " outside its area", failures);
        }
        check(TitanBackTrials.isSafeStand(back, ARRIVAL), "cannot stand at the arrival", failures);
        check(back.getBlockState(TideGarden.pearlBlock()).is(Blocks.PEARLESCENT_FROGLIGHT), "the clam has no pearl", failures);
        check(back.getBlockState(new BlockPos(0, HUB_FLOOR + 1, FLATS_GATE_Z)).is(ModBlocks.SEALED_GATE.get())
                || back.getBlockState(new BlockPos(0, HUB_FLOOR + 1, FLATS_GATE_Z)).isAir(), "flats gate cell is neither open nor sealed", failures);
        check(back.getBlockState(new BlockPos(-1, HUB_FLOOR, 0)).is(ModBlocks.SEALED_GATE.get()), "bottom gate is not closed after building", failures);
        check(back.getBlockState(new BlockPos(0, HUB_FLOOR + 1, 0)).isAir(), "hub is not open above the bottom gate", failures);
        check(back.getBlockState(TideWatch.RESET_LEVER.above()).is(Blocks.LEVER), "reset plug missing", failures);
        // 螺旋の階段の段がある
        int stairs = 0;
        for (int y = SPIRAL_BOTTOM; y <= SPIRAL_TOP; y++) {
            for (int[] c : TideIslandBuilder.SPIRAL_RING) {
                if (back.getBlockState(new BlockPos(c[0], y, c[1])).getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
                    stairs++;
                }
            }
        }
        check(stairs == TideIslandBuilder.SPIRAL_STEPS, "spiral stair has " + stairs + " steps", failures);
    }

    // ---------------------------------------------------------------- 看板: 碑文 6 枚と試練の入口の一言 3 枚だけ。どの行も看板の幅に収まる (SignFit)
    private static void checkSigns(ServerLevel back, List<String> failures) {
        java.util.Set<BlockPos> expected = new java.util.HashSet<>();
        LORE_SIGNS.forEach(i -> expected.add(i.pos()));
        TideIslandBuilder.ENTRANCE_SIGNS.forEach(i -> expected.add(i.pos()));
        java.util.Set<String> keys = new java.util.TreeSet<>();
        int count = 0;
        for (int cx = TideTurtle.minX() >> 4; cx <= TideTurtle.maxX() >> 4; cx++) {
            for (int cz = TideTurtle.minZ() >> 4; cz <= TideTurtle.maxZ() >> 4; cz++) {
                for (var be : back.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof TabletBlockEntity tablet)) {
                        continue;
                    }
                    count++;
                    if (!expected.contains(be.getBlockPos())) {
                        failures.add("unexpected tablet at " + be.getBlockPos() + " (only inscriptions and trial entrances keep tablets)");
                    }
                    keys.add(tablet.key());
                }
            }
        }
        check(count == expected.size(), "found " + count + " tablets on the tide back, expected " + expected.size(), failures);
        failures.addAll(jp.revati.skyabyss.SignFit.check(keys));
        var ja = jp.revati.skyabyss.SignFit.lang("ja_jp");
        var en = jp.revati.skyabyss.SignFit.lang("en_us");
        for (String k : TideWhispers.keys()) {
            check(ja.containsKey(k) && en.containsKey(k), "whisper text missing: " + k, failures);
        }
        NOTES.add(count + " signs");
    }

    // ---------------------------------------------------------------- 亀の形: 甲羅・頭・ひれ・尾がモデルの通りに置かれ、掘った部屋が甲羅を突き抜けていない
    private static void checkTurtle(ServerLevel back, List<String> failures) {
        check(shellTop(0, 20) >= 110, "the shell is not domed (top at (0,20) " + shellTop(0, 20) + ")", failures);
        int[][] parts = {{0, -125}, {-90, -25}, {90, -25}, {-75, 67}, {75, 67}, {0, 90}};
        String[] names = {"head", "left front flipper", "right front flipper", "left rear flipper", "right rear flipper", "tail"};
        for (int i = 0; i < parts.length; i++) {
            int t = TideTurtle.top(parts[i][0], parts[i][1]);
            check(t != Integer.MIN_VALUE && !back.getBlockState(new BlockPos(parts[i][0], t, parts[i][1])).isAir(),
                    "the " + names[i] + " is missing at " + parts[i][0] + "," + parts[i][1], failures);
        }
        // 甲羅の地表に、掘った穴が開いていない (光の井戸・上る段の口・塔のまわりだけ)
        int holes = 0;
        for (int x = -60; x <= 60; x += 2) {
            for (int z = -75; z <= 70; z += 2) {
                int top = shellTop(x, z);
                if (top == Integer.MIN_VALUE || top < 100) {
                    continue;
                }
                if (back.getBlockState(new BlockPos(x, top, z)).isAir() && back.getBlockState(new BlockPos(x, top - 1, z)).isAir()
                        && back.getBlockState(new BlockPos(x, top - 2, z)).isAir() && !nearOpening(x, z)) {
                    holes++;
                    if (holes <= 5) {
                        failures.add("hole in the shell at " + x + "," + top + "," + z);
                    }
                }
            }
        }
        NOTES.add("shell holes " + holes);
    }

    // ---------------------------------------------------------------- 構造の検査 (tools/structure の check.py と同じ見方)
    // 水に浸かった形 (waterlogged) のブロックの横か下が空いていると、隣が変わったときに水が流れ出す。
    // 溜めを満たして SETTLE_TICKS 流したあと (full) と、引いたあと (ebb) に数える
    private static void checkWaterlogged(ServerLevel back, List<String> failures, List<String> notes, String when) {
        int bad = 0;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = TideTurtle.minX(); x <= TideTurtle.maxX(); x++) {
            for (int z = TideTurtle.minZ(); z <= TideTurtle.maxZ(); z++) {
                for (int y = TideTurtle.minY(); y <= TideTurtle.maxY(); y++) {
                    BlockState s = back.getBlockState(c.set(x, y, z));
                    if (!s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                            || !s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                        continue;
                    }
                    BlockPos at = c.immutable();
                    for (BlockPos n : new BlockPos[] {at.below(), at.north(), at.south(), at.east(), at.west()}) {
                        BlockState ns = back.getBlockState(n);
                        if (ns.getCollisionShape(back, n).isEmpty() && ns.getFluidState().isEmpty()) {
                            bad++;
                            if (bad <= 5) {
                                failures.add("waterlogged block next to open space (" + when + ") at " + at.toShortString() + " " + s);
                            }
                            break;
                        }
                    }
                }
            }
        }
        notes.add("waterlogged leaks " + when + " " + bad);
    }

    // 甲羅の中の部屋 (広間からつながる、上にも下にも固いものがある空き所) が外に接する口。
    // わざと開けた口 (潮門・到着の段・見送りの段・甲羅の上へ出る段) のほかに口が 0 であること。
    // 水は壁とみなす (海の下で部屋が外とつながって見えないように)。干潮で、門は閉じたまま
    private static void checkOpenings(ServerLevel back, List<String> failures, List<String> notes) {
        int x0 = TideTurtle.minX() - 1;
        int x1 = TideTurtle.maxX() + 1;
        int z0 = TideTurtle.minZ() - 1;
        int z1 = TideTurtle.maxZ() + 1;
        int y0 = TideTurtle.minY() - 1;
        int y1 = TideTurtle.maxY() + 8;
        int w = x1 - x0 + 1;
        int[] lo = new int[w * (z1 - z0 + 1)];
        int[] hi = new int[lo.length];
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = (z - z0) * w + (x - x0);
                lo[i] = Integer.MAX_VALUE;
                hi[i] = Integer.MIN_VALUE;
                for (int y = y0; y <= y1; y++) {
                    BlockState s = back.getBlockState(c.set(x, y, z));
                    if (!s.getCollisionShape(back, c).isEmpty()) {
                        lo[i] = Math.min(lo[i], y);
                        hi[i] = Math.max(hi[i], y);
                    }
                }
            }
        }
        java.util.function.Predicate<BlockPos> open = p -> {
            BlockState s = back.getBlockState(p);
            return s.getCollisionShape(back, p).isEmpty() && s.getFluidState().isEmpty();
        };
        java.util.function.Predicate<BlockPos> enclosed = p -> {
            int i = (p.getZ() - z0) * w + (p.getX() - x0);
            return lo[i] < p.getY() && p.getY() < hi[i];
        };
        // 広間の休み場から始める
        BlockPos start = null;
        BlockPos seed = RESTS.get(0).spawn();
        for (int r = 0; r <= 3 && start == null; r++) {
            for (BlockPos q : BlockPos.betweenClosed(seed.offset(-r, -r, -r), seed.offset(r, r, r))) {
                if (open.test(q) && enclosed.test(q)) {
                    start = q.immutable();
                    break;
                }
            }
        }
        if (start == null) {
            failures.add("openings: no enclosed room around " + seed.toShortString());
            return;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start.asLong());
        q.add(start);
        int unexpected = 0;
        int expected = 0;
        while (!q.isEmpty() && seen.size() < 2_000_000) {
            BlockPos p = q.poll();
            boolean mouth = false;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < x0 || n.getX() > x1 || n.getZ() < z0 || n.getZ() > z1 || n.getY() < y0 || n.getY() > y1) {
                    mouth = true;
                    continue;
                }
                if (!open.test(n)) {
                    continue;
                }
                if (!enclosed.test(n)) {
                    mouth = true;
                } else if (seen.add(n.asLong())) {
                    q.add(n);
                }
            }
            if (mouth) {
                if (intendedOpening(p)) {
                    expected++;
                } else if (++unexpected <= 5) {
                    failures.add("hole from the rooms to the outside at " + p.toShortString());
                }
            }
        }
        notes.add("rooms " + seen.size() + " cells, openings " + expected + " intended / " + unexpected + " unexpected");
    }

    // わざと開けた口: 潮門と到着の段・ひれ・見送りの段 (左後ろの縁)、甲羅の上へ出る段の口
    private static boolean intendedOpening(BlockPos p) {
        if (p.getX() <= GATE_MOUTH_X2 && p.getZ() >= GATE_MOUTH_Z1 - 1 && p.getY() <= GATE_FLOOR + 24) {
            return true;
        }
        return p.getY() >= 100 && Math.abs(p.getX() - (TOP_STAIR_X1 + 2)) <= 3 && Math.abs(p.getZ() - (TOP_STAIR_Z1 + 2)) <= 3;
    }

    private static boolean nearOpening(int x, int z) {
        int sx = TOP_STAIR_X1 + 2;
        int sz = TOP_STAIR_Z1 + 2;
        if (Math.abs(x - sx) <= 3 && Math.abs(z - sz) <= 3) {
            return true;
        }
        if (Math.abs(x) <= 11 && Math.abs(z - TOWER_CZ) <= 14) {
            return true;
        }
        for (int[][] wells : new int[][][] {TideIslandBuilder.GARDEN_WELLS, TideIslandBuilder.FLATS_WELLS}) {
            for (int[] w : wells) {
                if (Math.abs(x - w[0] - 0.5) <= 2 && Math.abs(z - w[1] - 0.5) <= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 潮の溜め: 満ちても漏れない、引けば残らない
    // 満たした直後ではなく、SETTLE_TICKS のあいだ水を流してから数える (流れる水がすき間や空気の所を埋めていないか)
    private static void fillPools(ServerLevel back, List<String> failures, List<String> notes) {
        TideBackTrials.initPools(back);
        StringBuilder sizes = new StringBuilder();
        for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
            sizes.append(p.id).append('=').append(p.size()).append(' ');
            check(p.size() > 50, "pool " + p.id + " is too small (" + p.size() + ")", failures);
            for (int k = 0; k < p.depth(); k++) {
                check(!p.layer(k).isEmpty(), "pool " + p.id + " layer " + k + " is empty", failures);
            }
        }
        notes.add("pools " + sizes.toString().trim());
        // 満ちるときは干潟の門が閉じている
        for (int x = -1; x <= 1; x++) {
            for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++) {
                back.setBlock(new BlockPos(x, y, FLATS_GATE_Z), ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
            }
        }
        TideField.Pool hub = TideBackTrials.FIELD.pool("hub");
        for (List<BlockPos> layer : hub.layers) {
            for (BlockPos c : layer) {
                if (c.getZ() <= FLATS_GATE_Z || c.getY() > HUB_LEDGE - 1) {
                    failures.add("hub pool reaches outside the hall at " + c);
                    break;
                }
            }
        }
        for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
            TideField.settleNow(back, p, p.depth());
            check(p.level() == p.depth(), "pool " + p.id + " did not fill", failures);
        }
        // 潮見の間の三段の溜めも、いちばん高い水位にして流しておく (戸は閉じる)
        TideWatch.setLevels(back, 7, 7, 6);
    }

    // 潮見の間: 溜めの内側のほかに水が出ていない (溜めを満たして流したあと)
    private static void checkWatchSettled(ServerLevel back, List<String> failures, String when) {
        int leaks = 0;
        BlockPos first = null;
        for (int x = 24; x <= 57; x++) {
            for (int z = -36; z <= -5; z++) {
                for (int y = TIER_FLOOR[0]; y <= LEDGE_FLOOR + 2; y++) {
                    BlockPos c = new BlockPos(x, y, z);
                    if (back.getFluidState(c).isEmpty()) {
                        continue;
                    }
                    boolean inBasin = false;
                    for (int[] b : BASIN) {
                        inBasin |= x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3];
                    }
                    if (!inBasin) {
                        leaks++;
                        if (first == null) {
                            first = c;
                        }
                    }
                }
            }
        }
        check(leaks == 0, "tidewatch " + when + ": water outside the basins at " + leaks + " cells (first " + first + ")", failures);
        TideWatch.reset(back, TideBackState.get(back));
    }

    // 溜めの箱 (置く所のまわり 2 マス、水の段の高さだけ)
    private static int[] box(TideField.Pool p) {
        int[] b = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (List<BlockPos> layer : p.layers) {
            for (BlockPos c : layer) {
                b[0] = Math.min(b[0], c.getX());
                b[1] = Math.max(b[1], c.getX());
                b[2] = Math.min(b[2], c.getZ());
                b[3] = Math.max(b[3], c.getZ());
            }
        }
        return new int[] {b[0] - 2, b[1] + 2, b[2] - 2, b[3] + 2};
    }

    // 満たして水を流したあと: 置く所はすべて水の源、まわりに漏れた水がない
    private static void checkSettled(ServerLevel back, List<String> failures, String when) {
        for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
            Set<Long> cells = new HashSet<>();
            int holes = 0;
            BlockPos firstHole = null;
            for (List<BlockPos> layer : p.layers) {
                for (BlockPos c : layer) {
                    cells.add(c.asLong());
                    BlockState st = back.getBlockState(c);
                    if (!st.getFluidState().isSource()) {
                        holes++;
                        if (firstHole == null) {
                            firstHole = c;
                        }
                    }
                }
            }
            check(holes == 0, "pool " + p.id + " " + when + ": " + holes + " cells are not still water (first " + firstHole + ")", failures);
            int[] b = box(p);
            int leaks = 0;
            BlockPos firstLeak = null;
            for (int x = b[0]; x <= b[1]; x++) {
                for (int z = b[2]; z <= b[3]; z++) {
                    for (int y = p.base - 1; y < p.base + p.depth() + 1; y++) {
                        BlockPos c = new BlockPos(x, y, z);
                        if (!cells.contains(c.asLong()) && !back.getFluidState(c).isEmpty() && y >= TideBackLayout.SEA) {
                            leaks++;
                            if (firstLeak == null) {
                                firstLeak = c;
                            }
                        }
                    }
                }
            }
            check(leaks == 0, "pool " + p.id + " " + when + ": water spread to " + leaks + " cells outside it (first " + firstLeak + ")", failures);
        }
    }

    private static void drainPools(ServerLevel back) {
        for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
            TideField.settleNow(back, p, 0);
        }
    }

    // 引いて水を流したあと: 置く所にも、まわりにも水が残らない
    private static void checkDrained(ServerLevel back, List<String> failures, String when) {
        for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
            int[] b = box(p);
            int left = 0;
            BlockPos first = null;
            for (int x = b[0]; x <= b[1]; x++) {
                for (int z = b[2]; z <= b[3]; z++) {
                    for (int y = Math.max(p.base - 1, TideBackLayout.SEA); y < p.base + p.depth() + 1; y++) {
                        BlockPos c = new BlockPos(x, y, z);
                        if (!back.getFluidState(c).isEmpty()) {
                            left++;
                            if (first == null) {
                                first = c;
                            }
                        }
                    }
                }
            }
            check(left == 0, "pool " + p.id + " " + when + ": " + left + " water blocks left after draining (first " + first + ")", failures);
        }
    }

    // ---------------------------------------------------------------- 歩いて行ける所 (干潮、門は閉じたまま。干潟の門だけ開ける)
    static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && !s.is(ModBlocks.SEALED_GATE.get());
    }

    static boolean standable(ServerLevel l, BlockPos p) {
        if (!passable(l, p) || !passable(l, p.above())) {
            return false;
        }
        BlockState below = l.getBlockState(p.below());
        return !below.getCollisionShape(l, p.below()).isEmpty() || l.getBlockState(p).is(Blocks.WATER);
    }

    // 到着地点から歩いて (跳んで 1 段、落ちて 3 段まで) 行ける所と、その歩数
    // 歩いた道のり (自己テストの失敗の説明用): 行き先 → ひとつ前
    static final Map<Long, Long> PARENT = new HashMap<>();

    static String pathTo(BlockPos goal) {
        List<String> steps = new ArrayList<>();
        Long cur = goal.asLong();
        int guard = 0;
        while (cur != null && guard++ < 400) {
            BlockPos p = BlockPos.of(cur);
            steps.add(p.getX() + "," + p.getY() + "," + p.getZ());
            cur = PARENT.get(cur);
        }
        java.util.Collections.reverse(steps);
        // 長いときは、高さが変わる所だけ
        List<String> out = new ArrayList<>();
        String prevY = null;
        for (String st : steps) {
            String y = st.split(",")[1];
            if (!y.equals(prevY)) {
                out.add(st);
                prevY = y;
            }
        }
        return String.join(" > ", out);
    }

    // 歩いて調べる範囲 (甲羅と、まわりの海の水面を少し)
    static final int WALK_X0 = -80;
    static final int WALK_X1 = 80;
    static final int WALK_Z0 = -100;
    static final int WALK_Z1 = 85;

    static Map<Long, Integer> walk(ServerLevel l, BlockPos start) {
        PARENT.clear();
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        dist.put(start.asLong(), 0);
        q.add(start);
        while (!q.isEmpty() && dist.size() < 400000) {
            BlockPos p = q.poll();
            int d = dist.get(p.asLong());
            List<BlockPos> next = new ArrayList<>();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos m = n.above(dy);
                    if (dy == 1 && !passable(l, p.above(2))) {
                        continue;
                    }
                    if (dy < 0) {
                        boolean clear = true;
                        for (int k = dy; k <= 1; k++) {
                            if (!passable(l, n.above(k))) {
                                clear = false;
                            }
                        }
                        if (!clear) {
                            continue;
                        }
                    }
                    if (standable(l, m)) {
                        next.add(m);
                        break;
                    }
                }
            }
            if (l.getBlockState(p).is(Blocks.WATER) && standable(l, p.above())) {
                next.add(p.above());
            }
            for (BlockPos m : next) {
                if (m.getX() < WALK_X0 || m.getX() > WALK_X1 || m.getZ() < WALK_Z0 || m.getZ() > WALK_Z1 || m.getY() < 45 || m.getY() > 150) {
                    continue;
                }
                if (!dist.containsKey(m.asLong())) {
                    dist.put(m.asLong(), d + 1);
                    PARENT.put(m.asLong(), p.asLong());
                    q.add(m);
                }
            }
        }
        return dist;
    }

    private static void checkWalk(ServerLevel back, List<String> failures, List<String> notes) {
        for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
            TideField.settleNow(back, p, 0);
        }
        TideBackState state = TideBackState.get(back);
        state.reset(back.random, null, true);
        TideBackTrials.applyTrials(back, state);
        // 干潟の門を開ける (干潮で水盤が空)
        for (int x = -1; x <= 1; x++) {
            for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++) {
                back.setBlock(new BlockPos(x, y, FLATS_GATE_Z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        Map<Long, Integer> dist = walk(back, ARRIVAL);
        Map<String, BlockPos> should = new java.util.LinkedHashMap<>();
        for (Rest r : RESTS) {
            if (!r.id().equals("heart")) {
                should.put("rest " + r.id(), r.spawn());
            }
        }
        should.put("clam", new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1] - 2));
        should.put("altar", new BlockPos(ALTAR[0] + 1, ALTAR_TOP + 1, ALTAR[1] + 1));
        should.put("east gallery", new BlockPos(10, HUB_LEDGE + 1, -2));
        should.put("hub floor", new BlockPos(5, HUB_FLOOR + 1, 5));
        should.put("flats", new BlockPos(0, FLATS_FLOOR + 1, -85 + FLATS_DZ));
        should.put("front stone", STONE_FRONT.offset(0, 0, 1));
        // 甲羅の上 (外) と頂の塔の柱の段へは、浜の西の壁の段と塔の螺旋で行ける (21 章)
        should.put("beach", new BlockPos(-22, BEACH.floor() + 1, 50));
        should.put("shell top", new BlockPos(TOP_STAIR_X1 + 2, shellTop(TOP_STAIR_X1 + 2, TOP_STAIR_Z1 - 4) + 1, TOP_STAIR_Z1 - 4));
        should.put("shell top (far)", new BlockPos(30, shellTop(30, -40) + 1, -40));
        should.put("tower deck", new BlockPos(4, TOWER_DECK + 1, TOWER_CZ));
        for (Map.Entry<String, BlockPos> e : should.entrySet()) {
            BlockPos g = e.getValue();
            if (!dist.containsKey(g.asLong())) {
                // いちばん近くまで行けた所
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (long k : dist.keySet()) {
                    BlockPos q = BlockPos.of(k);
                    double dd = q.distSqr(g);
                    if (dd < bd) {
                        bd = dd;
                        best = q;
                    }
                }
                failures.add("cannot walk from the arrival to " + e.getKey() + " " + g + " (closest " + best + ", standable " + standable(back, g) + ")");
            }
        }
        Integer toHub = dist.get(RESTS.get(0).spawn().asLong());
        notes.add("arrival to hall " + toHub + " steps (" + dist.size() + " standing spots)");
        // 到着 (ひれの上) → 潮門 → 廊下 → 浜 → くぐり道 → 広間。歩いて 40 秒ほど (3.2節の「1 分以内」)
        check(toHub != null && toHub <= 160, "the hall is too far from the arrival (" + toHub + " steps)", failures);
        Map<String, BlockPos> shouldNot = new java.util.LinkedHashMap<>();
        shouldNot.put("middle tier", new BlockPos(41, TIER_FLOOR[1] + 1, -10));
        shouldNot.put("upper tier", new BlockPos(52, TIER_FLOOR[2] + 1, -11));
        shouldNot.put("east ledge", new BlockPos(53, LEDGE_FLOOR + 1, -32));
        shouldNot.put("heart rest", RESTS.get(5).spawn());
        shouldNot.put("garden from above", new BlockPos(PLAT_C[0], PLATFORM_TOP + 1, PLAT_C[1]).above(30));
        shouldNot.put("garden wall top", new BlockPos(-30, GARDEN_WALL_TOP + 1, 20));
        for (Map.Entry<String, BlockPos> e : shouldNot.entrySet()) {
            if (dist.containsKey(e.getValue().asLong())) {
                failures.add("can walk to " + e.getKey() + " without solving (" + e.getValue() + "): " + pathTo(e.getValue()));
            }
        }
        String mapDir = System.getenv("SKYABYSS_TIDE_MAP");
        if (mapDir != null) {
            writeMap(back, dist, java.nio.file.Path.of(mapDir, "tide_walk.png"));
        }
    }

    // 上から見た地図: 歩いて行ける所は高さで色、立てるが行けない所は灰、立てない所は黒。1 マス 4 画素
    static void writeMap(ServerLevel l, Map<Long, Integer> dist, java.nio.file.Path file) {
        int x0 = WALK_X0;
        int x1 = WALK_X1;
        int z0 = WALK_Z0;
        int z1 = WALK_Z1;
        int sc = 4;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage((x1 - x0 + 1) * sc, (z1 - z0 + 1) * sc, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int color = 0x101010;
                for (int y = 150; y >= 45; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (dist.containsKey(p.asLong())) {
                        float t = Math.max(0, Math.min(1, (y - 60) / 60f));
                        color = java.awt.Color.HSBtoRGB(0.66f - 0.66f * t, 0.8f, 0.95f) & 0xFFFFFF;
                        break;
                    }
                    if (standable(l, p)) {
                        color = 0x505050 + ((y - 45) & 0x1F);
                        break;
                    }
                }
                for (int i = 0; i < sc; i++) {
                    for (int j = 0; j < sc; j++) {
                        img.setRGB((x - x0) * sc + i, (z - z0) * sc + j, color);
                    }
                }
            }
        }
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            javax.imageio.ImageIO.write(img, "png", file.toFile());
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.warn("could not write {}", file, e);
        }
    }

    // ---------------------------------------------------------------- 潮見の間
    private static void checkPuzzle(ServerLevel back, List<String> failures, List<String> notes) {
        StringBuilder sb = new StringBuilder("puzzle moves");
        for (int i = 0; i < TidePuzzle.CONFIGS.length; i++) {
            int m = TidePuzzle.solve(TidePuzzle.CONFIGS[i]);
            sb.append(' ').append(m);
            check(m >= 5 && m <= 9, "puzzle config " + i + " needs " + m + " moves", failures);
            TidePuzzle.Config c = TidePuzzle.CONFIGS[i];
            for (int k = 0; k < 3; k++) {
                check(c.caps()[k] <= 7 && c.init()[k] <= c.caps()[k], "config " + i + " basin " + k + " does not fit", failures);
            }
        }
        notes.add(sb.toString());
        check(TidePuzzle.solve(TidePuzzle.CONFIGS[0]) <= TidePuzzle.solve(TidePuzzle.CONFIGS[1]), "the first config is not the easiest", failures);
        // 一歩: 栓が開いていれば一段だけ移る。重石で逆になる
        TidePuzzle.Config c0 = TidePuzzle.CONFIGS[0];
        int[] lv = c0.init().clone();
        TidePuzzle.step(c0, lv, new boolean[] {false, true}, false);
        check(lv[0] == c0.init()[0] + 1 && lv[1] == c0.init()[1] - 1, "a plug did not move one level", failures);
        lv = c0.init().clone();
        TidePuzzle.step(c0, lv, new boolean[] {true, false}, true);
        check(lv[2] == c0.init()[2] + 1 && lv[1] == c0.init()[1] - 1, "the weight floor did not reverse the plug", failures);
        // ブロック: 水の段、浮き石の高さ、戸
        TideWatch.setLevels(back, 1, 5, 0);
        int water = 0;
        int[] b = BASIN[0];
        for (int x = b[0]; x <= b[1]; x++) {
            for (int z = b[2]; z <= b[3]; z++) {
                for (int y = TIER_FLOOR[0]; y <= TIER_FLOOR[1]; y++) {
                    if (back.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)) {
                        water++;
                    }
                }
            }
        }
        check(water == 25, "lower basin at level 1 has " + water + " water", failures);
        check(!back.getBlockState(new BlockPos(36, TIER_FLOOR[0] + 1, -10)).isAir(), "lower float is not at level 1", failures);
        check(back.getBlockState(BASIN_DOOR[0]).isAir(), "lower door closed at level 1", failures);
        check(back.getBlockState(BASIN_DOOR[1]).is(ModBlocks.SEALED_GATE.get()), "middle door open at level 5", failures);
        check(back.getBlockState(BASIN_DOOR[2]).isAir() && back.getBlockState(T_EXIT_DOOR).isAir(), "upper doors closed while empty", failures);
        // 浮き石が 5 段なら、次の段の床へ一段で上がれる高さ
        check(TIER_FLOOR[1] + 1 - (TIER_FLOOR[1] + TidePuzzle.REACH + 1) <= 1, "middle float at 5 cannot reach the upper tier", failures);
        check(TIER_FLOOR[0] + TidePuzzle.REACH + 1 >= TIER_FLOOR[1], "lower float at 5 cannot reach the middle tier", failures);
        TideWatch.setLevels(back, 0, 0, 3);
        check(back.getBlockState(BASIN_DOOR[2]).is(ModBlocks.SEALED_GATE.get()), "upper door open with water", failures);
        TideWatch.reset(back, TideBackState.get(back));
    }

    // ---------------------------------------------------------------- 珊瑚の庭 (真珠)
    private static void checkGarden(ServerLevel back, List<String> failures) {
        FakePlayer fake = FakePlayerFactory.get(back, new GameProfile(java.util.UUID.fromString("7e1de0c4-0000-4000-8000-000000000001"), "[TideTest]"));
        BlockPos at = new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1]);
        fake.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        fake.getInventory().clearContent();
        TideBackState state = TideBackState.get(back);
        check(TideGarden.useClam(back, TideGarden.pearlBlock(), fake), "clam did not react", failures);
        check(TideGarden.holds(fake) && TideGarden.pearlOut(), "pearl was not given", failures);
        check(!back.getBlockState(TideGarden.pearlBlock()).is(Blocks.PEARLESCENT_FROGLIGHT), "clam still shows the pearl", failures);
        // 落とした真珠は大貝へ戻る
        TideGarden.returnPearl(back, "message.skyabyss.tide.pearl_dropped");
        check(!TideGarden.pearlOut() && back.getBlockState(TideGarden.pearlBlock()).is(Blocks.PEARLESCENT_FROGLIGHT), "pearl did not return to the clam", failures);
        TideGarden.strip(fake);
        // 据える
        TideGarden.useClam(back, TideGarden.pearlBlock(), fake);
        TideBackTrials.useStone(back, STONE_WEST, fake, fake.getMainHandItem());
        check(state.rung(TideBackState.Trial.WEST), "placing the pearl did not ring the west stone", failures);
        check(!TideGarden.holds(fake), "pearl stayed in the inventory after placing", failures);
        check(back.getBlockState(STONE_WEST).getValue(TideChimeStoneBlock.ACTIVE), "west stone not lit", failures);
        check(back.getBlockState(RETURN_WEST).is(Blocks.SEA_LANTERN), "west return floor not lit", failures);
        // 前の石は潮招きを倒すまで黙る
        TideBackTrials.useStone(back, STONE_FRONT, fake, net.minecraft.world.item.ItemStack.EMPTY);
        check(!state.rung(TideBackState.Trial.FRONT), "front stone rang before the beckoner fell", failures);
    }

    // ---------------------------------------------------------------- 群れの干潟 (巣と潮招き)
    private static void checkFlats(ServerLevel back, List<String> failures) {
        TideBackState state = TideBackState.get(back);
        check(Integer.bitCount(state.nestSites()) == 3, "nest sites should be 3 of 6", failures);
        for (int s = 0; s < 6; s++) {
            if ((state.nestSites() & (1 << s)) != 0) {
                check(!back.getBlockState(TideFlats.nestPos(s)).isAir(), "nest mound missing at site " + s, failures);
            }
        }
        TideBeckoner boss = TideBackSetup.TIDE_BECKONER.get().create(back);
        check(boss != null, "beckoner cannot be created", failures);
        if (boss != null) {
            boss.setChallengers(3);
            check(Math.abs(boss.multiplier() - 2.2f) < 1e-4, "beckoner health multiplier for 3 is " + boss.multiplier(), failures);
            check(Math.abs(boss.getMaxHealth() - TideBeckoner.HEALTH) < 0.01, "beckoner max health " + boss.getMaxHealth(), failures);
            boss.addTag(jp.revati.skyabyss.titan.tide.mob.TideMob.TRIAL_TAG);
            check(!boss.shouldBeSaved(), "trial beckoner would be saved", failures);
            boss.discard();
        }
        // 潮招きを倒した記録で、前の石が鳴らせる
        state.markBeckonerDefeated();
        FakePlayer fake = FakePlayerFactory.get(back, new GameProfile(java.util.UUID.fromString("7e1de0c4-0000-4000-8000-000000000001"), "[TideTest]"));
        TideBackTrials.useStone(back, STONE_FRONT, fake, net.minecraft.world.item.ItemStack.EMPTY);
        check(state.rung(TideBackState.Trial.FRONT), "front stone did not ring after the beckoner", failures);
    }

    // ---------------------------------------------------------------- 三つの石で潮の底が開く
    private static void checkBottom(ServerLevel back, List<String> failures) {
        TideBackState state = TideBackState.get(back);
        TideBackTrials.ring(back, state, TideBackState.Trial.EAST, null);
        check(state.rungCount() == 3, "three stones did not ring", failures);
        TideField.Pool hub = TideBackTrials.FIELD.pool("hub");
        check(TideBackTrials.target(back, hub) == 0, "hub keeps filling after three stones", failures);
        TideField.settleNow(back, hub, 0);
        TideBackTrials.tickGatesForTest(back, state);
        check(state.bottomOpen(), "bottom gate did not open", failures);
        check(back.getBlockState(new BlockPos(-1, HUB_FLOOR, 1)).isAir(), "the grate did not sink", failures);
        Map<Long, Integer> dist = walk(back, RESTS.get(0).spawn());
        check(dist.containsKey(RESTS.get(5).spawn().asLong()), "cannot walk from the hall down to the heart rest", failures);
        check(dist.containsKey(BlockPos.containing(jp.revati.skyabyss.titan.tide.heart.TideHeartArena.anteSpawn()).asLong()),
                "cannot walk to the heart antechamber", failures);
        check(TideHeartArenaAccess.chamberBuilt(back), "heart chamber is not built at the shell bottom", failures);
    }

    // ---------------------------------------------------------------- リセットと眠る試練
    private static void checkReset(ServerLevel back, List<String> failures, List<String> notes) {
        TitanBackData data = TitanBackData.get(back);
        TideBackState state = TideBackState.get(back);
        int epoch = data.epoch();
        boolean ever = state.everDefeated();
        TideBackTrials.reset(back, false, null, true);
        check(data.epoch() == epoch + 1, "reset did not advance the trial number", failures);
        check(state.rungCount() == 0 && !state.bottomOpen() && !state.beckonerDefeated(), "reset left progress", failures);
        check(back.getBlockState(new BlockPos(-1, HUB_FLOOR, 0)).is(ModBlocks.SEALED_GATE.get()), "reset did not close the bottom gate", failures);
        check(!back.getBlockState(STONE_WEST).getValue(TideChimeStoneBlock.ACTIVE), "reset left the west stone lit", failures);
        check(back.getBlockState(TideGarden.pearlBlock()).is(Blocks.PEARLESCENT_FROGLIGHT), "reset did not return the pearl", failures);
        // 2 周目から: 一つ眠る。同じ試練が二回続けて眠らない
        state.markEverDefeated();
        TideBackState.Trial last = null;
        int[] counts = new int[3];
        for (int i = 0; i < 24; i++) {
            state.reset(back.random, null, false);
            TideBackState.Trial s = state.sleeping();
            if (s == null) {
                failures.add("no trial sleeps after the heart fell");
                break;
            }
            counts[s.ordinal()]++;
            check(s != last, "the same trial slept twice in a row (" + s + ")", failures);
            check(state.rung(s) && state.rungCount() == 1, "sleeping trial's stone is not ringing", failures);
            last = s;
        }
        notes.add("sleeping west/east/front " + counts[0] + "/" + counts[1] + "/" + counts[2]);
        check(counts[0] > 0 && counts[1] > 0 && counts[2] > 0, "some trial never sleeps", failures);
        // 眠った試練は石が鳴っていて、残り二つで潮の底が開く
        TideBackTrials.reset(back, false, TideBackState.Trial.EAST, false);
        check(state.sleeping() == TideBackState.Trial.EAST && state.rung(TideBackState.Trial.EAST)
                && back.getBlockState(STONE_EAST).getValue(TideChimeStoneBlock.ACTIVE), "forced sleeping trial is not lit", failures);
        // 休み場の記録は、リセットの番号で無効になる
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("skyabyss_tide_checkpoint", "f0");
        tag.putInt("skyabyss_tide_checkpoint_epoch", data.epoch() - 1);
        check(TideBackTrials.validCheckpoint(tag, data.epoch()).isEmpty(), "old checkpoint survived a reset", failures);
        tag.putInt("skyabyss_tide_checkpoint_epoch", data.epoch());
        check(TideBackTrials.validCheckpoint(tag, data.epoch()).equals("f0"), "current checkpoint is not valid", failures);
        if (!ever) {
            // 元に戻す (ほかの自己テストのため)
            notes.add("ever defeated set by this test");
        }
    }

    // ---------------------------------------------------------------- ピースフル
    private static void checkPeaceful(ServerLevel back, List<String> failures) {
        Difficulty before = back.getDifficulty();
        back.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        TideBackTrials.tickPeacefulForTest(back, true);
        BlockPos seal = new BlockPos(CAVE_X1, TideIslandBuilder.caveFloor(CAVE_SEAL_Z) + 1, CAVE_SEAL_Z);
        check(back.getBlockState(seal).is(ModBlocks.SEALED_GATE.get()), "peaceful did not seal the cave", failures);
        back.getServer().setDifficulty(before == Difficulty.PEACEFUL ? Difficulty.NORMAL : before, true);
        TideBackTrials.tickPeacefulForTest(back, false);
        check(!back.getBlockState(seal).is(ModBlocks.SEALED_GATE.get()), "leaving peaceful did not unseal the cave", failures);
    }

    // 心臓の間が建っているか (TideHeartArena.isBuilt を使う)
    static final class TideHeartArenaAccess {
        static boolean chamberBuilt(ServerLevel back) {
            return jp.revati.skyabyss.titan.tide.heart.TideHeartArena.isBuilt(back);
        }
    }
}
