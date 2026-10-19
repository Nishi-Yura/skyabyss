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
import net.minecraft.world.entity.Mob;
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
// 三つの試練の進み (真珠、四つの配置が解けること・水位と戸、巣と潮招き)、三つの石で潮の底が開く、リセットと眠る試練、ピースフルの封印、
// 二つのグループが同じ背中で (石・扉の当たり判定・敵の印・リセット・部屋の占有・6 人の数え方・パーティの記録の引き継ぎ)
public final class TideBackSelfTest {
    // 水を流してから数えるまでの tick (流れる水が広がりきるのを待つ。岩鯨の水路の息継ぎの穴が、隣の水で埋まっていた反省)
    public static final int SETTLE_TICKS = 100;
    // テストのグループ (一人のグループ = 偽のプレイヤーの UUID)。進みはグループごと
    static final java.util.UUID TEST = java.util.UUID.fromString("7e1de0c4-0000-4000-8000-000000000001");
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
            checkHighGate(back, FAILURES);
            checkWaterlogged(back, FAILURES, NOTES, "full");
            drainPools(back);
        })));
        steps.add(jp.revati.skyabyss.SelfTestRunner.Step.once("tide back (pools drained)", SETTLE_TICKS, server -> guarded(server, back -> {
            checkDrained(back, FAILURES, "after building");
            checkWaterlogged(back, FAILURES, NOTES, "ebb");
            checkOpenings(back, FAILURES, NOTES);
            checkWalk(back, FAILURES, NOTES);
            // 詰み監査 (到達・戻れるか・はしご・床の穴)。干潮、戸は開いているものとして
            jp.revati.skyabyss.titan.audit.BackReach.Report reach = TideBackAudit.runAll(back, TEST);
            reach.notes.forEach(n -> jp.revati.skyabyss.SkyAbyss.LOGGER.info("SELFTEST {}", n));
            reach.failures.forEach(f -> jp.revati.skyabyss.SkyAbyss.LOGGER.warn("REACH {}", f));
            FAILURES.addAll(reach.failures);
            checkGuide(back, FAILURES, NOTES);
            checkPuzzle(back, FAILURES, NOTES);
            checkGarden(back, FAILURES);
            checkFlats(back, FAILURES);
            checkBottom(back, FAILURES);
            checkReset(back, FAILURES, NOTES);
            checkGroups(back, FAILURES, NOTES);
            checkAnyState(back, FAILURES, NOTES);
            // リセットのあと (庭の道の形が変わる) にも、もう一度満たして漏れを数える。新しい道の形は、庭を持ったときに掘る
            TideRooms.claim(back, TideRooms.GARDEN, TEST);
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
            // テストのグループの記録を消し、部屋を手放す (ほかのグループの記録には触れない)
            TideBackTrials.resetGroup(back, TEST, false, null, true);
            TideRooms.releaseAll(back);
            jp.revati.skyabyss.titan.group.BackGroupData.get(back).remove(TEST);
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
        // 扉はいつもブロック (開いているものも OPEN の扉)
        for (List<BlockPos> gate : List.of(TideGates.flats(), TideGates.bottom(), TideGates.mouth(), TideGates.door(0), TideGates.door(1), TideGates.door(2),
                TideGates.cave(), jp.revati.skyabyss.titan.tide.heart.TideHeartArena.gate())) {
            for (BlockPos p : gate) {
                check(back.getBlockState(p).is(ModBlocks.SEALED_GATE.get()), "gate block missing at " + p.toShortString(), failures);
            }
        }
        check(!TideGates.defaultOpen(back, new BlockPos(-1, HUB_FLOOR, 0)), "bottom gate is not closed after building", failures);
        check(TideGates.defaultOpen(back, TideGates.mouth().get(0)) && TideGates.defaultOpen(back, TideGates.cave().get(0))
                && TideGates.defaultOpen(back, jp.revati.skyabyss.titan.tide.heart.TideHeartArena.gate().get(0)), "mouth, cave seal or heart entrance is not open by default", failures);
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
                    // 看板の裏に壁がある (空中に浮いた看板を作らない)
                    BlockState tb = back.getBlockState(be.getBlockPos());
                    if (tb.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) && !tb.getValue(jp.revati.skyabyss.tablet.TabletBlock.STANDING)) {
                        BlockPos behind = be.getBlockPos().relative(tb.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getOpposite());
                        check(back.getBlockState(behind).isSolidRender(back, behind), "tablet at " + be.getBlockPos() + " has no wall behind it", failures);
                    }
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
        int[] box = {x0, x1, z0, z1, y0, y1};
        flood(start, open, enclosed, box, failures, notes, "rooms", true);
        // 息の間 (予約の間 1〜3・気室・舞台・展望の間): 中から調べて、外へ抜ける口が戸口 (崖の穴) のほかに 0 であること
        BlockPos up = new BlockPos(20, UPPER_1.floor() + 1, 20);
        check(open.test(up) && enclosed.test(up), "reserved room 1 is not an enclosed room at " + up.toShortString(), failures);
        flood(up, open, enclosed, box, failures, notes, "breath chamber rooms", true);
    }

    private static void flood(BlockPos start, java.util.function.Predicate<BlockPos> open, java.util.function.Predicate<BlockPos> enclosed,
            int[] box, List<String> failures, List<String> notes, String label, boolean allowIntended) {
        int x0 = box[0];
        int x1 = box[1];
        int z0 = box[2];
        int z1 = box[3];
        int y0 = box[4];
        int y1 = box[5];
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
                if (allowIntended && intendedOpening(p)) {
                    expected++;
                } else if (++unexpected <= 5) {
                    failures.add("hole from the " + label + " to the outside at " + p.toShortString());
                }
            }
        }
        notes.add(label + " " + seen.size() + " cells, openings " + expected + " intended / " + unexpected + " unexpected");
    }

    // わざと開けた口: 潮門と到着の段・ひれ・見送りの段 (左後ろの縁)、甲羅の上へ出る段の口
    private static boolean intendedOpening(BlockPos p) {
        if (p.getX() <= GATE_MOUTH_X2 && p.getZ() >= GATE_MOUTH_Z1 - 6 && p.getY() <= GATE_FLOOR + 32) {
            return true;
        }
        if (p.getX() <= UPPER_GATE_X + 1 && Math.abs(p.getZ() - UPPER_GATE_Z) <= 3 && p.getY() >= UPPER_1.floor() && p.getY() <= UPPER_1.floor() + 6) {
            return true;
        }
        return p.getY() >= 95 && Math.abs(p.getX() - (TOP_STAIR_X1 + 2)) <= 3 && Math.abs(p.getZ() - (TOP_STAIR_Z1 + 2)) <= 3;
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
        if (Math.abs(x - UPPER_GATE_X) <= 3 && Math.abs(z - UPPER_GATE_Z) <= 3) {
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
        // 干潟の門のブロックは、満ちるときいつも閉じている (開いた扉は水を通す)。グループへの開放は上書きで、checkHighGate で見る
        jp.revati.skyabyss.titan.group.GroupGates.setDefault(back, TideGates.flats(), false);
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
        TideWatch.reset(back, null);
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
    // 扉は、walkGroup のグループにとって開いていれば通れる (null ならワールドの既定)
    @javax.annotation.Nullable
    static java.util.UUID walkGroup;

    static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.is(ModBlocks.SEALED_GATE.get())) {
            return walkGroup == null ? s.getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN)
                    : jp.revati.skyabyss.titan.group.GroupGates.isOpen(l, walkGroup, p);
        }
        return s.getCollisionShape(l, p).isEmpty();
    }

    static boolean standable(ServerLevel l, BlockPos p) {
        if (!passable(l, p) || !passable(l, p.above())) {
            return false;
        }
        BlockState below = l.getBlockState(p.below());
        // 足もとの扉は、walkGroup にとって閉じていれば床
        boolean floor = below.is(ModBlocks.SEALED_GATE.get()) ? !passable(l, p.below()) : !below.getCollisionShape(l, p.below()).isEmpty();
        return floor || l.getBlockState(p).is(Blocks.WATER);
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
        // テストのグループの新しい周で、三つの部屋をそのグループの形にする
        TideBackTrials.resetGroup(back, TEST, false, null, true);
        TideRooms.claim(back, TideRooms.GARDEN, TEST);
        TideRooms.claim(back, TideRooms.WATCH, TEST);
        TideRooms.claim(back, TideRooms.FLATS, TEST);
        // 干潟の門を (既定で) 開ける (干潮で水盤が空)
        jp.revati.skyabyss.titan.group.GroupGates.setDefault(back, TideGates.flats(), true);
        walkGroup = TEST;
        Map<Long, Integer> dist = walk(back, ARRIVAL);
        Map<String, BlockPos> should = new java.util.LinkedHashMap<>();
        for (Rest r : RESTS) {
            // 殻の底の前室は三つの石のあと、息の間の足場 (u1〜u3) は光る泡で昇る (歩いては行けない)
            if (!r.id().equals("heart") && !r.id().equals("u1") && !r.id().equals("u2") && !r.id().equals("u3")) {
                should.put("rest " + r.id(), r.spawn());
            }
        }
        should.put("breath chamber (room 1 floor)", new BlockPos(20, UPPER_1.floor() + 1, 20));
        should.put("breath entry bubble", TideBackAudit.landingBlock(BreathLayout.entry()));
        should.put("clam", new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1] - 2));
        should.put("altar", new BlockPos(ALTAR[0] + 1, ALTAR_TOP + 1, ALTAR[1] + 1));
        should.put("east gallery", new BlockPos(10, HUB_LEDGE + 1, -2));
        should.put("hub floor", new BlockPos(5, HUB_FLOOR + 1, 5));
        should.put("flats", new BlockPos(0, FLATS_FLOOR + 1, -85 + FLATS_DZ));
        should.put("front stone", STONE_FRONT.offset(0, 0, 1));
        // 甲羅の上 (外) と頂の塔の柱の段へは、浜の西の壁の段と塔の螺旋で行ける (21 章)
        should.put("beach", new BlockPos(-22, BEACH.floor() + 1, 50));
        should.put("shell top", new BlockPos(TOP_STAIR_X1 + 2, shellTop(TOP_STAIR_X1 + 2, TOP_STAIR_Z1 - 4) + 1, TOP_STAIR_Z1 - 4));
        should.put("shell top (far)", new BlockPos(50, shellTop(50, -40) + 1, -40));
        should.put("shell top (north)", new BlockPos(0, TideTurtle.top(0, -66) + 1, -66));
        should.put("shell top (before the reserved gate)", new BlockPos(UPPER_GATE_X - 3, TOP_Y + 1, UPPER_GATE_Z));
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
        // 息の間: 光る泡に乗らないと昇れない (皿・足場・舞台・展望の間へは歩いて行けない)
        shouldNot.put("breath dish 0", TideBackAudit.landingBlock(BreathLayout.dishNode(0)));
        shouldNot.put("breath ledge u1", RESTS.get(7).spawn());
        shouldNot.put("breath ledge u2", RESTS.get(8).spawn());
        shouldNot.put("breath summit", TideBackAudit.landingBlock(BreathLayout.arena()));
        shouldNot.put("breath deck", RESTS.get(9).spawn());
        shouldNot.put("terrace 2", new BlockPos(0, TERRACE_Y[1] + 1, 46));
        shouldNot.put("garden from above", new BlockPos(PLAT_C[0], PLATFORM_TOP + 1, PLAT_C[1]).above(30));
        shouldNot.put("garden wall top", new BlockPos(-30, GARDEN_WALL_TOP + 1, 20));
        for (Map.Entry<String, BlockPos> e : shouldNot.entrySet()) {
            if (dist.containsKey(e.getValue().asLong())) {
                failures.add("can walk to " + e.getKey() + " without solving (" + e.getValue() + "): " + pathTo(e.getValue()));
            }
        }
        PARENT.clear();
        String mapDir = System.getenv("SKYABYSS_TIDE_MAP");
        if (mapDir != null) {
            writeMap(back, dist, java.nio.file.Path.of(mapDir, "tide_walk.png"));
        }
        walkGroup = null;
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
        // 溜めの戸は扉のブロックのまま、既定 (OPEN) で開け閉めする
        check(TideGates.defaultOpen(back, BASIN_DOOR[0]), "lower door closed at level 1", failures);
        check(back.getBlockState(BASIN_DOOR[1]).is(ModBlocks.SEALED_GATE.get()) && !TideGates.defaultOpen(back, BASIN_DOOR[1]), "middle door open at level 5", failures);
        check(TideGates.defaultOpen(back, BASIN_DOOR[2]) && TideGates.defaultOpen(back, T_EXIT_DOOR), "upper doors closed while empty", failures);
        check(back.getBlockState(T_EXIT_DOOR).is(ModBlocks.SEALED_GATE.get()), "the open upper door is not a gate block", failures);
        // 浮き石が 5 段なら、次の段の床へ一段で上がれる高さ
        check(TIER_FLOOR[1] + 1 - (TIER_FLOOR[1] + TidePuzzle.REACH + 1) <= 1, "middle float at 5 cannot reach the upper tier", failures);
        check(TIER_FLOOR[0] + TidePuzzle.REACH + 1 >= TIER_FLOOR[1], "lower float at 5 cannot reach the middle tier", failures);
        TideWatch.setLevels(back, 0, 0, 3);
        check(!TideGates.defaultOpen(back, BASIN_DOOR[2]), "upper door open with water", failures);
        TideWatch.reset(back, TideBackState.of(back, TEST));
    }

    // ---------------------------------------------------------------- 試練のわかりやすさ (目的の板・進みの点・栓の知らせ・看板の裏)
    private static void checkGuide(ServerLevel back, List<String> failures, List<String> notes) {
        var ja = jp.revati.skyabyss.SignFit.lang("ja_jp");
        var en = jp.revati.skyabyss.SignFit.lang("en_us");
        for (String id : TideGuide.GOAL_IDS) {
            check(ja.containsKey("gui.skyabyss.tide_goal." + id) && en.containsKey("gui.skyabyss.tide_goal." + id), "goal text missing: " + id, failures);
        }
        for (String t : new String[] {"west", "east", "front", "hub"}) {
            check(ja.containsKey("gui.skyabyss.tide_goal.title." + t) && en.containsKey("gui.skyabyss.tide_goal.title." + t), "goal title missing: " + t, failures);
        }
        TestPlayer p = join(back, TEST, "[TideGuide]", new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1]));
        try {
            TideBackState.Group st = TideBackState.of(back, TEST);
            // 西: 取る → 運ぶ → 祭壇の近く (据える) → 半ばを越える → 戻る
            TideRooms.claim(back, TideRooms.GARDEN, TEST);
            expectGoal(back, p, "west.take", 0, 3, failures);
            p.getInventory().clearContent();
            TideGarden.useClam(back, TideGarden.pearlBlock(), p);
            expectGoal(back, p, "west.carry", 1, 3, failures);
            p.moveTo(ALTAR[0] + 3, ALTAR_TOP + 1, ALTAR[1] + 4);
            expectGoal(back, p, "west.place", 1, 3, failures);
            p.moveTo(CLAM[0] + 2, GARDEN_FLOOR + 1, PLAT_C[1] - 6);
            TideGuide.tick(back);
            check(TideGuide.westPast(), "the pearl crossing the midpoint was not noticed", failures);
            expectGoal(back, p, "west.carry", 2, 3, failures);
            TideGarden.returnPearl(back, "message.skyabyss.tide.pearl_dropped");
            TideGuide.tick(back);
            check(!TideGuide.westPast(), "the midpoint stayed after the pearl returned", failures);
            expectGoal(back, p, "west.take", 0, 3, failures);
            // 東: 水位と、いちばん上まで来た段
            TideRooms.claim(back, TideRooms.WATCH, TEST);
            p.moveTo(26, HUB_LEDGE + 1, -10);
            TidePuzzle.Config c = TideWatch.cfg();
            TideGuide.Goal g = TideGuide.goalFor(back, p);
            check(g.id().equals("east.go") && g.a() == c.init()[0] && g.b() == c.init()[1] && g.c() == c.init()[2] && g.done() == 0,
                    "east goal at the start is " + g, failures);
            p.moveTo(41, TIER_FLOOR[1] + 1, -10);
            TideGuide.tick(back);
            p.moveTo(53, TIER_FLOOR[2] + 1, -12);
            TideGuide.tick(back);
            check(TideGuide.eastStage() == 2, "east stage after reaching the top tier is " + TideGuide.eastStage(), failures);
            // 栓を引く・重石を踏む・水位が変わる (知らせの経路が落ちない。戸が開く)
            for (int i = 0; i < c.plugs().length; i++) {
                TideWatch.setLever(back, TideWatch.leverSlot(i), true);
            }
            TideGuide.tick(back);
            TideWatch.setLevels(back, 5, 5, 0);
            TideGuide.tick(back);
            expectGoal(back, p, "east.exit", 3, 3, failures);
            check(TideGuide.goalFor(back, p).c() == 0, "east goal does not carry the water levels", failures);
            TideWatch.reset(back, st);
            check(TideGuide.eastStage() == 0, "east stage stayed after the watch room reset", failures);
            // 前: 巣・潮招き・石
            TideRooms.claim(back, TideRooms.FLATS, TEST);
            p.moveTo(0, FLATS_FLOOR + 1, -60);
            int nests = Integer.bitCount(st.nestSites());
            TideGuide.Goal f = TideGuide.goalFor(back, p);
            check(f.id().equals("front.nests") && f.done() == 0 && f.total() == nests + 1 && f.a() == nests, "front goal at the start is " + f, failures);
            int broken = 0;
            for (int s = 0; s < 6; s++) {
                if ((st.nestSites() & (1 << s)) != 0) {
                    st.breakNest(s);
                    broken++;
                    if (broken < nests) {
                        expectGoal(back, p, "front.nests", broken, nests + 1, failures);
                    }
                }
            }
            expectGoal(back, p, "front.boss", nests, nests + 1, failures);
            st.markBeckonerDefeated();
            expectGoal(back, p, "front.stone", nests + 1, nests + 1, failures);
            // 広間: 石の数
            p.moveTo(-6, HUB_LEDGE + 1, 10);
            expectGoal(back, p, "hub.stones", 0, 3, failures);
            st.ring(TideBackState.Trial.WEST);
            st.ring(TideBackState.Trial.EAST);
            st.ring(TideBackState.Trial.FRONT);
            expectGoal(back, p, "hub.bottom", 3, 3, failures);
            // 進みの点と光の経路 (落ちない)
            TideGuide.pips(back, p, net.minecraft.world.phys.Vec3.atCenterOf(STONE_WEST).add(0, 1.4, 0), 1, 3);
            // 外にいれば出ない
            p.moveTo(-20, TOP_Y + 1, 44);
            check(TideGuide.goalFor(back, p).id().isEmpty(), "a goal is shown on the shell top", failures);
            // 目的の板の本文は 4 行に収まる (板の本文の幅 140 px、折り返しの余裕を見て 130 で数える)
            int longest = 0;
            for (var lang : java.util.List.of(ja, en)) {
                for (String id : TideGuide.GOAL_IDS) {
                    int w = jp.revati.skyabyss.SignFit.approxWidth(lang.get("gui.skyabyss.tide_goal." + id));
                    longest = Math.max(longest, w);
                    check(w <= 130 * 4, "goal text " + id + " needs more than four lines (" + w + " px)", failures);
                }
            }
            notes.add("goal texts up to " + longest + " px");
        } finally {
            leave(back, p);
            TideBackTrials.resetGroup(back, TEST, false, null, true);
            TideRooms.releaseAll(back);
        }
    }

    private static void expectGoal(ServerLevel back, TestPlayer p, String id, int done, int total, List<String> failures) {
        TideGuide.Goal g = TideGuide.goalFor(back, p);
        check(g.id().equals(id) && g.done() == done && g.total() == total, "goal is " + g + ", expected " + id + " " + done + "/" + total, failures);
    }

    // ---------------------------------------------------------------- 珊瑚の庭 (真珠)
    private static void checkGarden(ServerLevel back, List<String> failures) {
        FakePlayer fake = FakePlayerFactory.get(back, new GameProfile(TEST, "[TideTest]"));
        BlockPos at = new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1]);
        fake.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        fake.getInventory().clearContent();
        TideRooms.claim(back, TideRooms.GARDEN, TEST);
        TideBackState.Group state = TideBackState.of(back, TEST);
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
        // 光るのは鳴らしたグループの見かけだけ。ワールドの石は「まだ鳴っていない」形のまま
        Map<BlockPos, BlockState> overlay = jp.revati.skyabyss.titan.group.GroupOverlays.compute(back, TEST);
        check(overlay.containsKey(STONE_WEST) && overlay.get(STONE_WEST).getValue(TideChimeStoneBlock.ACTIVE), "west stone not lit for the group", failures);
        check(TideKit.LAMP.equals(overlay.get(RETURN_WEST)), "west return floor not lit for the group", failures);
        check(!back.getBlockState(STONE_WEST).getValue(TideChimeStoneBlock.ACTIVE), "the world's west stone was lit (it should stay unlit for other groups)", failures);
        // 前の石は潮招きを倒すまで黙る
        TideBackTrials.useStone(back, STONE_FRONT, fake, net.minecraft.world.item.ItemStack.EMPTY);
        check(!state.rung(TideBackState.Trial.FRONT), "front stone rang before the beckoner fell", failures);
    }

    // ---------------------------------------------------------------- 群れの干潟 (巣と潮招き)
    private static void checkFlats(ServerLevel back, List<String> failures) {
        TideBackState.Group state = TideBackState.of(back, TEST);
        TideRooms.claim(back, TideRooms.FLATS, TEST);
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
            // パーティは 6 人まで: 1 + 0.6 × 5
            boss.setChallengers(jp.revati.skyabyss.party.Parties.MAX_MEMBERS);
            check(Math.abs(boss.multiplier() - 4.0f) < 1e-4, "beckoner health multiplier for 6 is " + boss.multiplier(), failures);
            check(Math.abs(boss.getMaxHealth() - TideBeckoner.HEALTH) < 0.01, "beckoner max health " + boss.getMaxHealth(), failures);
            boss.addTag(jp.revati.skyabyss.titan.tide.mob.TideMob.TRIAL_TAG);
            check(!boss.shouldBeSaved(), "trial beckoner would be saved", failures);
            boss.discard();
        }
        // 潮招きを倒した記録で、前の石が鳴らせる
        state.markBeckonerDefeated();
        FakePlayer fake = FakePlayerFactory.get(back, new GameProfile(TEST, "[TideTest]"));
        TideBackTrials.useStone(back, STONE_FRONT, fake, net.minecraft.world.item.ItemStack.EMPTY);
        check(state.rung(TideBackState.Trial.FRONT), "front stone did not ring after the beckoner", failures);
    }

    // ---------------------------------------------------------------- 満潮でも干潟の門は (グループには) 開いていて、広間の水が門を抜けてトンネルへ漏れない
    private static void checkHighGate(ServerLevel back, List<String> failures) {
        List<BlockPos> flats = TideGates.flats();
        check(jp.revati.skyabyss.titan.group.GroupGates.isGate(back, flats.get(0)) && !TideGates.defaultOpen(back, flats.get(0)),
                "the flats gate block must stay shut in the world (an open gate lets the flood through)", failures);
        TestPlayer p = join(back, TEST, "[TideTest]", RESTS.get(0).spawn());
        try {
            TideBackTrials.tickGatesForTest(back);
        } finally {
            leave(back, p);
        }
        check(jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, TEST, flats), "the flats gate is not open for the group at flood", failures);
        TideField.Pool hub = TideBackTrials.FIELD.pool("hub");
        check(hub != null && hub.level() == hub.depth(), "the hub basin is not full for the flood check", failures);
        int leaks = 0;
        BlockPos first = null;
        for (int x = -1; x <= 1; x++) {
            for (int z = FLATS_GATE_Z - 1; z >= FLATS_GATE_Z - 30; z--) {
                for (int y = TUNNEL_FLOOR; y <= HUB_FLOOR + 6; y++) {
                    BlockPos c = new BlockPos(x, y, z);
                    if (!back.getFluidState(c).isEmpty()) {
                        leaks++;
                        first = first == null ? c : first;
                    }
                }
            }
        }
        check(leaks == 0, "flood water leaked through the flats gate into the tunnel at " + leaks + " cells (first " + first + ")", failures);
    }

    // ---------------------------------------------------------------- どの潮・どの静かな試練でも、入れて最後までできる (08 の 3.5・7.2節)
    private static void checkAnyState(ServerLevel back, List<String> failures, List<String> notes) {
        FakePlayer fp = FakePlayerFactory.get(back, new GameProfile(TEST, "[TideTest]"));
        // 干潟の門: どの段階でも、背中にいるグループには開いている (戦っているグループだけに閉じる)
        TestPlayer gatePlayer = join(back, TEST, "[TideTest]", RESTS.get(0).spawn());
        try {
            for (TideClock.Phase ph : TideClock.Phase.values()) {
                TideClock.force(ph);
                jp.revati.skyabyss.titan.group.GroupGates.reset(back, TEST);
                TideBackTrials.tickGatesForTest(back);
                check(jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, TEST, TideGates.flats()), "the flats gate is shut for the group at " + ph, failures);
            }
        } finally {
            leave(back, gatePlayer);
        }
        // 巣: 潮に関わらず壊せる。満ちているあいだは通る傷が減るだけ
        ReefNest nest = TideBackSetup.REEF_NEST.get().create(back);
        if (nest == null) {
            failures.add("nest cannot be created");
        } else {
            float[] loss = new float[2];
            TideClock.Phase[] phases = {TideClock.Phase.LOW, TideClock.Phase.HIGH};
            for (int i = 0; i < 2; i++) {
                TideClock.force(phases[i]);
                nest.setHealth(ReefNest.BASE_HEALTH);
                nest.invulnerableTime = 0;
                nest.hurt(back.damageSources().playerAttack(fp), 20f);
                loss[i] = ReefNest.BASE_HEALTH - nest.getHealth();
            }
            check(loss[0] > 0 && loss[1] > 0 && loss[1] < loss[0], "a nest takes " + loss[0] + " at ebb and " + loss[1] + " at flood (want 0 < flood < ebb)", failures);
            notes.add("nest damage ebb/flood " + loss[0] + "/" + loss[1]);
            nest.discard();
        }
        // 潮招き: 満潮でも、集合のあとすぐ現れる
        TideBackTrials.resetGroup(back, TEST, false, null, true);
        TideRooms.claim(back, TideRooms.FLATS, TEST);
        BlockPos flats = new BlockPos(0, FLATS_FLOOR + 1, -85 + FLATS_DZ);
        TestPlayer fighter = join(back, TEST, "[TideTest]", flats);
        try {
            TideClock.force(TideClock.Phase.HIGH);
            check(TideFlats.waitingSpawnsForTest(back, fighter.getUUID()), "the beckoner did not rise at flood", failures);
        } finally {
            leave(back, fighter);
            TideClock.force(TideClock.Phase.LOW);
        }
        // 静かな試練 (三つとも): 石は自分で鳴らし、満潮でも潮の底が開く。静かな庭は敵が少ない
        for (TideBackState.Trial t : TideBackState.Trial.values()) {
            TideBackTrials.resetGroup(back, TEST, false, t, false);
            TideBackState.Group state = TideBackState.of(back, TEST);
            check(state.quiet() == t && state.rungCount() == 0 && !state.rung(t), "quiet " + t + " is not rung-free at the start", failures);
            check(t != TideBackState.Trial.EAST || state.eastConfig() == 0, "quiet east is not the gentlest layout", failures);
            if (t == TideBackState.Trial.WEST) {
                TideRooms.claim(back, TideRooms.GARDEN, TEST);
                TideGarden.spawnForTest(back, TEST, 1);
                int quietGuards = TideGarden.guards(back, TEST);
                TideRooms.releaseAll(back);
                TideBackTrials.resetGroup(back, TEST, false, null, true);
                TideRooms.claim(back, TideRooms.GARDEN, TEST);
                TideGarden.spawnForTest(back, TEST, 1);
                int normalGuards = TideGarden.guards(back, TEST);
                TideRooms.releaseAll(back);
                check(quietGuards > 0 && quietGuards < normalGuards, "quiet garden has " + quietGuards + " guards (normal " + normalGuards + ")", failures);
                notes.add("garden guards quiet/normal " + quietGuards + "/" + normalGuards);
                TideBackTrials.resetGroup(back, TEST, false, t, false);
                state = TideBackState.of(back, TEST);
            }
            for (TideBackState.Trial u : TideBackState.Trial.values()) {
                TideBackTrials.ring(back, state, u, null);
            }
            check(state.rungCount() == 3, "quiet " + t + ": three stones did not ring", failures);
            // 満潮でも、三つの石で潮の底が開く
            TideClock.force(TideClock.Phase.HIGH);
            TideField.Pool hub = TideBackTrials.FIELD.pool("hub");
            TideField.settleNow(back, hub, hub.depth());
            TestPlayer p = join(back, TEST, "[TideTest]", RESTS.get(0).spawn());
            try {
                TideBackTrials.tickGatesForTest(back);
            } finally {
                leave(back, p);
            }
            check(state.bottomOpen(), "quiet " + t + ": the bottom gate did not open at flood", failures);
            TideField.settleNow(back, hub, 0);
            TideClock.force(TideClock.Phase.LOW);
        }
        TideRooms.releaseAll(back);
        TideBackTrials.resetGroup(back, TEST, false, null, true);
    }

    // ---------------------------------------------------------------- 三つの石で潮の底が開く (そのグループにだけ)
    private static void checkBottom(ServerLevel back, List<String> failures) {
        TideBackState.Group state = TideBackState.of(back, TEST);
        TideBackTrials.ring(back, state, TideBackState.Trial.EAST, null);
        check(state.rungCount() == 3, "three stones did not ring", failures);
        TideField.Pool hub = TideBackTrials.FIELD.pool("hub");
        check(TideBackTrials.target(back, hub) == 0, "hub is not empty at low tide", failures);
        TideField.settleNow(back, hub, 0);
        // 門を開けるのは、背中にいるグループ (人を入れて調べる)
        TestPlayer p = join(back, TEST, "[TideTest]", RESTS.get(0).spawn());
        try {
            TideBackTrials.tickGatesForTest(back);
        } finally {
            leave(back, p);
        }
        check(state.bottomOpen(), "bottom gate did not open", failures);
        check(jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, TEST, TideGates.bottom()), "the grate did not sink for the group", failures);
        check(!TideGates.defaultOpen(back, new BlockPos(-1, HUB_FLOOR, 1)), "the grate sank for everyone", failures);
        walkGroup = TEST;
        Map<Long, Integer> dist = walk(back, RESTS.get(0).spawn());
        walkGroup = null;
        check(dist.containsKey(RESTS.get(5).spawn().asLong()), "cannot walk from the hall down to the heart rest", failures);
        check(dist.containsKey(BlockPos.containing(jp.revati.skyabyss.titan.tide.heart.TideHeartArena.anteSpawn()).asLong()),
                "cannot walk to the heart antechamber", failures);
        // ほかのグループ (ワールドの既定) には閉じたまま
        Map<Long, Integer> other = walk(back, RESTS.get(0).spawn());
        check(!other.containsKey(RESTS.get(5).spawn().asLong()), "another group can walk down to the heart rest", failures);
        check(TideHeartArenaAccess.chamberBuilt(back), "heart chamber is not built at the shell bottom", failures);
    }

    // ---------------------------------------------------------------- リセットと眠る試練 (そのグループだけ)
    private static void checkReset(ServerLevel back, List<String> failures, List<String> notes) {
        jp.revati.skyabyss.titan.group.BackGroupData.Record record = jp.revati.skyabyss.titan.group.BackGroupData.get(back).of(TEST);
        int epoch = record.epoch();
        TideBackTrials.resetGroup(back, TEST, false, null, true);
        TideBackState.Group state = TideBackState.of(back, TEST);
        check(record.epoch() == epoch + 1, "reset did not advance the trial number", failures);
        check(state.rungCount() == 0 && !state.bottomOpen() && !state.beckonerDefeated(), "reset left progress", failures);
        check(!jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, TEST, TideGates.bottom()), "reset did not close the bottom gate", failures);
        check(!jp.revati.skyabyss.titan.group.GroupOverlays.compute(back, TEST).containsKey(STONE_WEST), "reset left the west stone lit", failures);
        check(back.getBlockState(TideGarden.pearlBlock()).is(Blocks.PEARLESCENT_FROGLIGHT), "reset did not return the pearl", failures);
        check(TideRooms.owner(TideRooms.GARDEN) == null && TideRooms.owner(TideRooms.FLATS) == null, "reset kept the group's rooms", failures);
        // 2 周目から: 一つ静かな試練になる。同じ試練が二回続けて静かにならない
        state.markEverDefeated();
        TideBackState.Trial last = null;
        int[] counts = new int[3];
        for (int i = 0; i < 24; i++) {
            state.reset(back.random, null, false);
            TideBackState.Trial s = state.quiet();
            if (s == null) {
                failures.add("no trial is quiet after the heart fell");
                break;
            }
            counts[s.ordinal()]++;
            check(s != last, "the same trial was quiet twice in a row (" + s + ")", failures);
            check(!state.rung(s) && state.rungCount() == 0, "a quiet trial's stone rings for free (it must be rung like the others)", failures);
            check(s != TideBackState.Trial.EAST || state.eastConfig() == 0, "a quiet east does not use the gentlest plug layout", failures);
            last = s;
        }
        notes.add("quiet west/east/front " + counts[0] + "/" + counts[1] + "/" + counts[2]);
        check(counts[0] > 0 && counts[1] > 0 && counts[2] > 0, "some trial is never quiet", failures);
        check(state.everDefeated(), "a reset forgot that the group once defeated the heart", failures);
        // 静かな試練も石は鳴っていない (そのグループに光って見えない)。三つとも自分で鳴らす
        TideBackTrials.resetGroup(back, TEST, false, TideBackState.Trial.EAST, false);
        Map<BlockPos, BlockState> overlay = jp.revati.skyabyss.titan.group.GroupOverlays.compute(back, TEST);
        check(state.quiet() == TideBackState.Trial.EAST && !state.rung(TideBackState.Trial.EAST)
                && !(overlay.containsKey(STONE_EAST) && overlay.get(STONE_EAST).getValue(TideChimeStoneBlock.ACTIVE)), "a forced quiet trial is lit before it is rung", failures);
        // 休み場の記録は、そのグループのリセットの番号で無効になる
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("skyabyss_tide_checkpoint", "f0");
        tag.putInt("skyabyss_tide_checkpoint_epoch", record.epoch() - 1);
        check(TideBackTrials.validCheckpoint(tag, record.epoch()).isEmpty(), "old checkpoint survived a reset", failures);
        tag.putInt("skyabyss_tide_checkpoint_epoch", record.epoch());
        check(TideBackTrials.validCheckpoint(tag, record.epoch()).equals("f0"), "current checkpoint is not valid", failures);
    }

    // ---------------------------------------------------------------- 二つのグループが同じ背中で (08 の 7.5節)
    static final java.util.UUID GROUP_A = java.util.UUID.fromString("7e1de0c4-0000-4000-8000-00000000000a");
    static final java.util.UUID GROUP_B = java.util.UUID.fromString("7e1de0c4-0000-4000-8000-00000000000b");

    private static void checkGroups(ServerLevel back, List<String> failures, List<String> notes) {
        jp.revati.skyabyss.titan.group.BackGroupData data = jp.revati.skyabyss.titan.group.BackGroupData.get(back);
        TestPlayer a = join(back, GROUP_A, "[TideA]", RESTS.get(0).spawn());
        TestPlayer b = join(back, GROUP_B, "[TideB]", RESTS.get(0).spawn().east(2));
        List<Mob> made = new ArrayList<>();
        try {
            TideBackTrials.resetGroup(back, GROUP_A, false, null, true);
            TideBackTrials.resetGroup(back, GROUP_B, false, null, true);
            check(jp.revati.skyabyss.titan.group.Groups.keyOf(a).equals(GROUP_A) && jp.revati.skyabyss.titan.group.Groups.keyOf(b).equals(GROUP_B),
                    "solo players are not their own group", failures);
            // 石: A が鳴らしても、B の石は鳴っていない。光って見えるのも A だけ
            TideBackTrials.ring(back, TideBackState.of(back, GROUP_A), TideBackState.Trial.EAST, null);
            check(TideBackState.of(back, GROUP_A).rung(TideBackState.Trial.EAST) && !TideBackState.of(back, GROUP_B).rung(TideBackState.Trial.EAST),
                    "a stone rung by group A also rang for group B", failures);
            check(jp.revati.skyabyss.titan.group.GroupOverlays.compute(back, GROUP_A).containsKey(STONE_EAST)
                    && !jp.revati.skyabyss.titan.group.GroupOverlays.compute(back, GROUP_B).containsKey(STONE_EAST), "stone overlay is not per group", failures);
            // 抜け道: B が踏んでも動かない (B の石は鳴っていない)
            check(TideBackTrials.gauge(back, a).stones() == 1 && TideBackTrials.gauge(back, b).stones() == 0, "the tide gauge does not count the viewer's stones", failures);
            // 扉: A にだけ開けた扉は、A の体には当たらず、B の体には当たる
            List<BlockPos> bottom = TideGates.bottom();
            jp.revati.skyabyss.titan.group.GroupGates.open(back, GROUP_A, bottom);
            BlockPos g = bottom.get(0);
            BlockState gs = back.getBlockState(g);
            check(gs.getCollisionShape(back, g, net.minecraft.world.phys.shapes.CollisionContext.of(a)).isEmpty(), "the gate opened for A still blocks A", failures);
            check(!gs.getCollisionShape(back, g, net.minecraft.world.phys.shapes.CollisionContext.of(b)).isEmpty(), "the gate opened for A lets B through", failures);
            check(jp.revati.skyabyss.titan.group.GroupGates.effective(back, GROUP_A, g, false) && !jp.revati.skyabyss.titan.group.GroupGates.effective(back, GROUP_B, g, false),
                    "gate overrides are not per group", failures);
            // 干潟の門: 開ける既定は全員に効き、戦っているグループにだけ閉じる
            jp.revati.skyabyss.titan.group.GroupGates.setDefault(back, TideGates.flats(), true);
            jp.revati.skyabyss.titan.group.GroupGates.seal(back, GROUP_A, TideGates.flats());
            check(!jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, GROUP_A, TideGates.flats())
                    && jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, GROUP_B, TideGates.flats()), "the flats gate sealed for A also closed for B", failures);
            jp.revati.skyabyss.titan.group.GroupGates.release(back, GROUP_A, TideGates.flats());
            check(jp.revati.skyabyss.titan.group.GroupGates.isOpen(back, GROUP_A, TideGates.flats()), "releasing A's seal did not go back to the tide's default", failures);
            jp.revati.skyabyss.titan.group.GroupGates.setDefault(back, TideGates.flats(), false);
            // 敵: A の敵は A の印を持ち、B には見えず、B を狙わず、B の傷を受けない
            BlockPos spot = new BlockPos(0, HUB_LEDGE + 1, 10);
            Mob mob = TideFlats.spawn(back, jp.revati.skyabyss.titan.tide.mob.TideMobSetup.REEF_SCUTTLER.get(), spot, GROUP_A);
            if (mob == null) {
                failures.add("could not spawn a trial mob");
            } else {
                made.add(mob);
                check(mob instanceof jp.revati.skyabyss.titan.group.GroupOwned o && GROUP_A.equals(o.groupId()), "trial mob has no group", failures);
                check(jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(mob, a) && !jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(mob, b),
                        "A's mob is visible to B", failures);
                check(mob.canAttack(a) && !mob.canAttack(b), "A's mob may target B (" + mob.canAttack(a) + "/" + mob.canAttack(b) + ")", failures);
                mob.invulnerableTime = 0;
                float hp = mob.getHealth();
                boolean hitB = mob.hurt(back.damageSources().playerAttack(b), 2f);
                check(!hitB && mob.getHealth() == hp, "B damaged A's mob", failures);
                mob.invulnerableTime = 0;
                boolean hitA = mob.hurt(back.damageSources().playerAttack(a), 2f);
                check(hitA && mob.getHealth() < hp, "A could not damage its own mob", failures);
                // そのグループのリセットは、そのグループの敵だけを片付ける
                Mob other = TideFlats.spawn(back, jp.revati.skyabyss.titan.tide.mob.TideMobSetup.REEF_SCUTTLER.get(), spot.east(), GROUP_B);
                if (other != null) {
                    made.add(other);
                    int epochB = data.of(GROUP_B).epoch();
                    TideBackState.of(back, GROUP_B).ring(TideBackState.Trial.WEST);
                    TideBackTrials.resetGroup(back, GROUP_A, false, null, true);
                    check(mob.isRemoved() && !other.isRemoved(), "resetting group A touched group B's mob or kept A's", failures);
                    check(!TideBackState.of(back, GROUP_A).rung(TideBackState.Trial.EAST), "resetting group A did not reset A's stones", failures);
                    check(data.of(GROUP_B).epoch() == epochB && TideBackState.of(back, GROUP_B).rung(TideBackState.Trial.WEST),
                            "resetting group A changed group B's record", failures);
                }
            }
            // 部屋の占有: A が庭にいるあいだ、B は庭へ入れない (入口の外へ戻す)
            TideRooms.releaseAll(back);
            BlockPos inGarden = RESTS.get(1).spawn();
            a.moveTo(inGarden.getX() + 0.5, inGarden.getY(), inGarden.getZ() + 0.5);
            TideRooms.tick(back);
            check(GROUP_A.equals(TideRooms.owner(TideRooms.GARDEN)), "A did not take the garden", failures);
            b.moveTo(inGarden.getX() + 1.5, inGarden.getY(), inGarden.getZ() + 0.5);
            TideRooms.tick(back);
            check(GROUP_A.equals(TideRooms.owner(TideRooms.GARDEN)), "B took the garden from A", failures);
            check(!TideRooms.gardenBox().contains(b.position()) && b.blockPosition().distSqr(TideRooms.GARDEN_EJECT) < 4,
                    "B was not sent out of the garden (at " + b.blockPosition().toShortString() + ")", failures);
            // 6 人のパーティの数え方: 庭の珊瑚憑き 5 + 2 × 5 = 15、磯走り 3 + 5 = 8
            TideGarden.spawnForTest(back, GROUP_A, jp.revati.skyabyss.party.Parties.MAX_MEMBERS);
            int guards = TideGarden.guards(back, GROUP_A);
            notes.add("garden guards for 6: " + guards);
            check(guards == 15 + 8, "garden guards for a party of 6 are " + guards + " (want 23)", failures);
            check(TideGarden.guards(back, GROUP_B) == 0, "group B has garden guards", failures);
            TideRooms.releaseAll(back);
            check(TideGarden.guards(back, GROUP_A) == 0, "releasing the garden left its guards", failures);
            checkPartyRecord(back, failures);
        } finally {
            made.forEach(Mob::discard);
            leave(back, a);
            leave(back, b);
            TideRooms.releaseAll(back);
            data.remove(GROUP_A);
            data.remove(GROUP_B);
        }
    }

    // パーティを組む・入る・抜けるときの記録 (GroupEvents の決まり): 組む = リーダーの写し、入る = パーティの記録、抜ける = パーティの写し
    private static void checkPartyRecord(ServerLevel back, List<String> failures) {
        jp.revati.skyabyss.party.PartyData parties = jp.revati.skyabyss.party.PartyData.get(back.getServer());
        jp.revati.skyabyss.titan.group.BackGroupData data = jp.revati.skyabyss.titan.group.BackGroupData.get(back);
        java.util.UUID leader = java.util.UUID.randomUUID();
        java.util.UUID mate = java.util.UUID.randomUUID();
        TideBackState.of(back, leader).ring(TideBackState.Trial.WEST);
        TideBackState.of(back, mate).ring(TideBackState.Trial.FRONT);
        jp.revati.skyabyss.party.PartyData.Party party = parties.create(leader, "[TideLeader]");
        java.util.UUID pid = party.id;
        try {
            check(TideBackState.of(back, pid).rung(TideBackState.Trial.WEST), "a new party did not start from the leader's record", failures);
            parties.add(party, mate, "[TideMate]");
            java.util.UUID key = jp.revati.skyabyss.titan.group.Groups.keyOf(back.getServer(), mate);
            check(key.equals(pid), "the joined player's group is not the party", failures);
            TideBackState.Group joined = TideBackState.of(back, key);
            check(joined.rung(TideBackState.Trial.WEST) && !joined.rung(TideBackState.Trial.FRONT), "the joined player did not adopt the party's record", failures);
            TideBackState.of(back, pid).ring(TideBackState.Trial.EAST);
            // 6 人まで入れる
            List<java.util.UUID> more = new ArrayList<>();
            for (int i = 0; i < jp.revati.skyabyss.party.Parties.MAX_MEMBERS - 2; i++) {
                java.util.UUID m = java.util.UUID.randomUUID();
                more.add(m);
                parties.add(party, m, "[TideMate" + i + "]");
            }
            check(party.members.size() == 6, "party size is " + party.members.size(), failures);
            for (java.util.UUID m : more) {
                parties.remove(m);
                data.remove(m);
            }
            parties.remove(mate);
            check(TideBackState.of(back, mate).rung(TideBackState.Trial.EAST) && TideBackState.of(back, mate).rung(TideBackState.Trial.WEST),
                    "the player who left did not keep a copy of the party's record", failures);
        } finally {
            if (parties.of(leader) != null) {
                parties.disband(parties.of(leader));
            }
            parties.remove(mate);
            data.remove(leader);
            data.remove(mate);
            data.remove(pid);
        }
    }

    // ---------------------------------------------------------------- ピースフル
    private static void checkPeaceful(ServerLevel back, List<String> failures) {
        Difficulty before = back.getDifficulty();
        back.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        TideBackTrials.tickPeacefulForTest(back, true);
        BlockPos seal = new BlockPos(CAVE_X1, TideIslandBuilder.caveFloor(CAVE_SEAL_Z) + 1, CAVE_SEAL_Z);
        check(back.getBlockState(seal).is(ModBlocks.SEALED_GATE.get()) && !TideGates.defaultOpen(back, seal), "peaceful did not seal the cave", failures);
        back.getServer().setDifficulty(before == Difficulty.PEACEFUL ? Difficulty.NORMAL : before, true);
        TideBackTrials.tickPeacefulForTest(back, false);
        check(TideGates.defaultOpen(back, seal), "leaving peaceful did not unseal the cave", failures);
    }

    // 心臓の間が建っているか (TideHeartArena.isBuilt を使う)
    static final class TideHeartArenaAccess {
        static boolean chamberBuilt(ServerLevel back) {
            return jp.revati.skyabyss.titan.tide.heart.TideHeartArena.isBuilt(back);
        }
    }

    // ---------------------------------------------------------------- 自己テストの人
    // ワールドに入れられ (部屋の占有・門を開けるグループの判定で見つかる)、同じディメンションの中の移動 (teleportTo) が効く偽のプレイヤー
    static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level, java.util.UUID id, String name) {
            super(level, new GameProfile(id, name));
        }

        @Override
        public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            if (level == level()) {
                moveTo(x, y, z, yaw, pitch);
            }
        }

        @Override
        public void teleportTo(double x, double y, double z) {
            moveTo(x, y, z);
        }
    }

    static TestPlayer join(ServerLevel back, java.util.UUID id, String name, BlockPos at) {
        TestPlayer p = new TestPlayer(back, id, name);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        back.addNewPlayer(p);
        return p;
    }

    static void leave(ServerLevel back, net.minecraft.server.level.ServerPlayer p) {
        if (p.level() == back && !p.isRemoved()) {
            back.removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }
}
