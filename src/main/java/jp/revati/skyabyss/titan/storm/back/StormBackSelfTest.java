package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 雷翼の背の自己テスト (SELFTEST storm back)。SelfTestRunner の段に入れる。
// 建てる (時間を測る) → 100 tick 待つ (落ちるブロック・空気の置き換えが落ち着いてから) → 形・看板・囁き・拍・鏡・柱・横風・
// 歩いて行ける所 (凪・嵐・戸が開いたあと・雷獣のあと・鏡のあと・竜骨の門のあと)・リセット・眠る試練・ピースフル
public final class StormBackSelfTest {
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();
    static final int SETTLE_TICKS = 100;

    private StormBackSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("storm back (build)", server -> guarded(server, back -> {
            FAILURES.clear();
            NOTES.clear();
            forceChunks(back, true);
            long t0 = System.currentTimeMillis();
            StormBackBuilder.build(back, TitanBackData.get(back));
            NOTES.add("built in " + (System.currentTimeMillis() - t0) + " ms");
            if (StormBirdBuilder.BIRD.exists()) {
                NOTES.add("bird " + StormBirdBuilder.BIRD.count() + " blocks");
            }
        })));
        steps.add(Step.once("storm back", SETTLE_TICKS, server -> guarded(server, back -> {
            check(back.getEntitiesOfClass(FallingBlockEntity.class, new AABB(-220, 60, -140, 220, 200, 140)).isEmpty(),
                    "blocks are falling in the storm back after building", FAILURES);
            checkClock(FAILURES);
            checkBird(back, FAILURES);
            checkShell(back, FAILURES, NOTES);
            checkSigns(back, FAILURES);
            checkWhispers(FAILURES);
            checkMirrors(back, FAILURES, NOTES);
            checkRods(back, FAILURES);
            checkWind(back, FAILURES);
            checkWalk(back, FAILURES, NOTES);
            checkReset(back, FAILURES);
            checkPeaceful(back, FAILURES);
            check(TitanBacks.isBack(StormTravel.STORM_BACK), "storm_back is not in the back list", FAILURES);
            StormBackClock.force(null);
            StormBackTrials.reset(back, false, null, true);
            forceChunks(back, false);
            if (FAILURES.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST storm back PASSED ({})", String.join("; ", NOTES));
            } else {
                FAILURES.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: {}", f));
                SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: {} problems ({})", FAILURES.size(), String.join("; ", NOTES));
            }
        })));
        return steps;
    }

    private static void guarded(MinecraftServer server, java.util.function.Consumer<ServerLevel> body) {
        ServerLevel back = server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            FAILURES.add("storm_back is missing");
            SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: storm_back is missing");
            return;
        }
        try {
            body.accept(back);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm back crashed", e);
            FAILURES.add("crashed: " + e);
            SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: crashed: {}", e.toString());
        }
    }

    private static void forceChunks(ServerLevel back, boolean on) {
        for (int cx = -220 >> 4; cx <= 220 >> 4; cx++) {
            for (int cz = -140 >> 4; cz <= 140 >> 4; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    // ---------------------------------------------------------------- 嵐の拍
    private static void checkClock(List<String> f) {
        check(StormBackClock.CYCLE.length() == 1800, "storm beat should be 90 s", f);
        check(StormBackClock.phaseAt(0) == StormBackClock.Phase.CALM && StormBackClock.phaseAt(799) == StormBackClock.Phase.CALM, "calm is 40 s", f);
        check(StormBackClock.phaseAt(800) == StormBackClock.Phase.RISING && StormBackClock.phaseAt(899) == StormBackClock.Phase.RISING, "rising is 5 s", f);
        check(StormBackClock.phaseAt(900) == StormBackClock.Phase.STORM && StormBackClock.phaseAt(1699) == StormBackClock.Phase.STORM, "storm is 40 s", f);
        check(StormBackClock.phaseAt(1700) == StormBackClock.Phase.FALLING && StormBackClock.phaseAt(1799) == StormBackClock.Phase.FALLING, "falling is 5 s", f);
        check(StormBackClock.CYCLE.warnsNow(600, 1) && StormBackClock.CYCLE.warnsNow(1500, 3), "the 10 s warnings are at 30 s and 75 s", f);
        check(StormBackClock.strengthAt(0) == 0 && StormBackClock.strengthAt(1000) == 1, "storm strength", f);
    }

    // ---------------------------------------------------------------- 鳥の形
    private static void checkBird(ServerLevel l, List<String> f) {
        if (!StormBirdBuilder.BIRD.exists()) {
            f.add("storm_back/bird.bin is missing (the back is not bird-shaped)");
            return;
        }
        BackShapeProbe p = new BackShapeProbe(l);
        // 頭 (北)・左右の翼の手首 (西・東)・尾 (南) に鳥のブロックがある
        check(p.columnHas(0, -77), "no head at (0, -77)", f);
        check(p.columnHas(-150, -18) && p.columnHas(150, -18), "no wings out to x ±150", f);
        check(p.columnHas(0, 105), "no tail at (0, 105)", f);
        check(!p.columnHas(0, -130) && !p.columnHas(230, 0), "the bird is larger than planned", f);
        // 部屋は体の中: 広間の真ん中の上は鳥 (天窓のガラスを除く)
        check(p.columnHas(10, -10) && StormBirdBuilder.top(10, -10) > HUB.floor() + HUB.height(), "the wind hall is not inside the body", f);
        check(StormBirdBuilder.top(-128, -17) > KNOB_CEILING, "the left wrist knob pokes out of the wing", f);
        check(StormBirdBuilder.top(128, -17) > RAIJU_NEST_CEILING, "the right wrist knob pokes out of the wing", f);
        check(StormBirdBuilder.top(0, -78) > EYE_HALL.floor() + EYE_HALL.height(), "the eye hall pokes out of the head", f);
    }

    // ---------------------------------------------------------------- 体の皮 (データで確かめる。ダンジョンの試遊はしない)
    // 1) 背の穴: 鳥のいちばん上のブロックとその下が空いている柱 (羽根の門・背の上へ出る段・到着の台・天窓を除く)
    // 2) 漏れ: 風の広間から空気をたどって、鳥の形の外 (上か下) へ出る所 (羽根の門と背の上の段の口は出口として許す)
    // 3) 水と溶岩: 建てて 100 tick 置いたあと、体の中の空気に流れる物が無い。休み場と部屋の真ん中は空気
    private static void checkShell(ServerLevel l, List<String> f, List<String> notes) {
        if (!StormBirdBuilder.BIRD.exists()) {
            return;
        }
        var bird = StormBirdBuilder.BIRD;
        int holes = 0;
        BlockPos firstHole = null;
        for (int x = bird.minX(); x <= bird.maxX(); x++) {
            for (int z = bird.minZ(); z <= bird.maxZ(); z++) {
                int t = bird.top(x, z);
                if (t == Integer.MIN_VALUE || allowedOpening(x, t, z)) {
                    continue;
                }
                // 空が見えて (上に何も建っていない)、鳥の上の 2 段が空いている
                if (skyAt(l, x, z) < t && !l.getBlockState(new BlockPos(x, t - 1, z)).blocksMotion()) {
                    holes++;
                    if (firstHole == null) {
                        firstHole = new BlockPos(x, t, z);
                    }
                }
            }
        }
        check(holes == 0, "the bird's back has " + holes + " holes (first at " + firstHole + ")", f);
        if (holes > 0 || DUMP) {
            dumpHoles(l, bird);
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(HUB_CENTER.above());
        seen.add(HUB_CENTER.above());
        List<BlockPos> leaks = new ArrayList<>();
        int fluids = 0;
        BlockPos firstFluid = null;
        while (!queue.isEmpty() && seen.size() < 3_000_000) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.contains(q)) {
                    continue;
                }
                var st = l.getBlockState(q);
                if (st.blocksMotion() && st.getFluidState().isEmpty()) {
                    continue;
                }
                seen.add(q);
                if (!st.getFluidState().isEmpty()) {
                    fluids++;
                    if (firstFluid == null) {
                        firstFluid = q;
                    }
                }
                // 外: 空が見える (上に固いブロックが無い) か、鳥の下
                boolean outside = q.getY() >= skyAt(l, q.getX(), q.getZ())
                        || (q.getY() < bird.bottom(q.getX(), q.getZ()) && openBelow(l, q, bird.minY() - 2));
                if (outside) {
                    if (!allowedOpening(q.getX(), q.getY(), q.getZ()) && leaks.size() < 5) {
                        leaks.add(q);
                    }
                    continue;
                }
                queue.add(q);
            }
        }
        check(leaks.isEmpty(), "the inside of the bird opens to the sky at " + leaks, f);
        check(fluids == 0, fluids + " fluid blocks inside the bird (first at " + firstFluid + ")", f);
        for (Rest r : RESTS) {
            check(l.getBlockState(r.spawn()).isAir() && l.getBlockState(r.spawn().above()).isAir(), "rest " + r.id() + " is not open air", f);
        }
        for (BlockPos c : List.of(TAIL_CENTER, HUB_CENTER.above(), EYE_HALL_CENTER.offset(1, 0, 6), HEART_ROOM.offset(0, 2, 8))) {
            check(l.getBlockState(c).isAir(), "the room around " + c + " is not air", f);
        }
        notes.add("inside air " + seen.size() + ", back holes " + holes + ", leaks " + leaks.size() + ", fluids " + fluids);
    }

    // いちばん上の固いブロックの 1 上 (ここから上は空が見える)
    static int skyAt(ServerLevel l, int x, int z) {
        return l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    // 下に固いブロックが一つもない (嵐の雲へ落ちる)
    static boolean openBelow(ServerLevel l, BlockPos q, int minY) {
        for (int y = q.getY() - 1; y >= minY; y--) {
            if (l.getBlockState(new BlockPos(q.getX(), y, q.getZ())).blocksMotion()) {
                return false;
            }
        }
        return true;
    }

    static final boolean DUMP = System.getenv("SKYABYSS_STORM_DUMP") != null;

    // 背の穴の地図と断面図を run/storm_back_dump/ に書く (上から: '#' 穴、'+' 鳥、'.' なし。断面: '#' 固い、' ' 空、'~' 流れる物)
    static void dumpHoles(ServerLevel l, jp.revati.skyabyss.titan.BackShape bird) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("storm_back_dump");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder top = new StringBuilder();
            for (int z = bird.minZ(); z <= bird.maxZ(); z++) {
                for (int x = bird.minX(); x <= bird.maxX(); x++) {
                    int t = bird.top(x, z);
                    char c = '.';
                    if (t != Integer.MIN_VALUE) {
                        boolean hole = !allowedOpening(x, t, z) && skyAt(l, x, z) < t && !l.getBlockState(new BlockPos(x, t - 1, z)).blocksMotion();
                        c = hole ? '#' : '+';
                    }
                    top.append(c);
                }
                top.append('\n');
            }
            java.nio.file.Files.writeString(dir.resolve("holes_top.txt"), top.toString());
            for (int z : new int[] {-17, -10, -3, 0, -78, 22, 26, 31, 40}) {
                StringBuilder sec = new StringBuilder("z=" + z + " x " + bird.minX() + ".." + bird.maxX() + "\n");
                for (int y = 170; y >= 110; y--) {
                    sec.append(String.format("%3d ", y));
                    for (int x = bird.minX(); x <= bird.maxX(); x++) {
                        var st = l.getBlockState(new BlockPos(x, y, z));
                        boolean inBird = bird.at(x, y, z) != null;
                        sec.append(!st.getFluidState().isEmpty() ? '~' : st.blocksMotion() ? (inBird ? '#' : '@') : (inBird ? ' ' : (y > bird.top(x, z) ? '.' : ',')));
                    }
                    sec.append('\n');
                }
                java.nio.file.Files.writeString(dir.resolve("section_z" + z + ".txt"), sec.toString());
            }
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.error("could not write the storm back dump", e);
        }
    }

    // 外へ開いていてよい所: 空へ開く口 (StormBackLayout.openToSky) と、風切りの骨の割れた床 (下の嵐の雲へ抜ける。12 の 4.1節)
    static boolean allowedOpening(int x, int y, int z) {
        if (y < HIGH_BEAM_Y && z >= BONE_Z1 - 1 && z <= BONE_Z2 + 1) {
            for (int[] g : WEST_GAPS) {
                if (x >= g[0] - 1 && x <= g[1] + 1) {
                    return true;
                }
            }
        }
        return openToSky(x, y, z);
    }

    private record BackShapeProbe(ServerLevel l) {
        boolean columnHas(int x, int z) {
            return StormBirdBuilder.BIRD.top(x, z) != Integer.MIN_VALUE;
        }
    }

    // ---------------------------------------------------------------- 碑 (ちょうど 9 枚、どの行も 90 px に収まる)
    private static void checkSigns(ServerLevel l, List<String> f) {
        Set<String> keys = new HashSet<>();
        int n = 0;
        for (int cx = -220 >> 4; cx <= 220 >> 4; cx++) {
            for (int cz = -140 >> 4; cz <= 140 >> 4; cz++) {
                for (BlockEntity be : l.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof TabletBlockEntity) {
                        n++;
                    }
                }
            }
        }
        check(n == 9, "there should be exactly 9 tablets on the storm back, found " + n, f);
        for (Inscription i : LORE_SIGNS) {
            keys.add(i.key());
            check(l.getBlockEntity(i.pos()) instanceof TabletBlockEntity, "sign " + i.key() + " is missing at " + i.pos(), f);
        }
        for (Inscription i : ENTRANCE_SIGNS) {
            keys.add(i.key());
            check(l.getBlockEntity(i.pos()) instanceof TabletBlockEntity, "sign " + i.key() + " is missing at " + i.pos(), f);
        }
        // 文の幅: 伝承の担当がまとめた lang (skyabyss_storm) で測る
        for (String lang : SignFit.LANGUAGES) {
            Map<String, String> m = SignFit.lang(lang);
            for (String k : keys) {
                if (!m.containsKey(k + ".1")) {
                    f.add("sign " + k + " has no text in " + lang);
                    continue;
                }
                for (int i = 1; i <= 4; i++) {
                    String line = m.getOrDefault(k + "." + i, "");
                    int w = SignFit.approxWidth(line);
                    check(w <= SignFit.MAX_LINE_WIDTH, "sign " + k + "." + i + " (" + lang + ") is " + w + " px wide: " + line, f);
                    check(!lang.equals("ja_jp") || line.length() <= 8, "sign " + k + "." + i + " (ja) is longer than 8 characters: " + line, f);
                }
            }
        }
    }

    // この担当と地上の担当が使う文の鍵 (lang/parts/{back,ground}.json。tools/gen_storm_lore.py が ja_jp・en_us にまとめる)
    static List<String> partKeys() {
        List<String> out = new ArrayList<>();
        for (String part : List.of("back", "ground")) {
            try (InputStream in = StormBackSelfTest.class.getResourceAsStream("/assets/skyabyss_storm/lang/parts/" + part + ".json")) {
                if (in != null) {
                    out.addAll(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().keySet());
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.error("could not read the storm lang part {}", part, e);
            }
        }
        return out;
    }

    private static void checkWhispers(List<String> f) {
        List<String> keys = new ArrayList<>(partKeys());
        keys.addAll(jp.revati.skyabyss.titan.storm.lore.StormWhispers.keys());
        check(keys.size() > 60, "the storm lang parts are missing", f);
        for (String lang : SignFit.LANGUAGES) {
            for (String k : keys) {
                check(SignFit.lang(lang).containsKey(k), "text " + k + " is missing in " + lang + " (run tools/gen_storm_lore.py)", f);
            }
        }
    }

    // ---------------------------------------------------------------- 眼の間 (四つの配置を解く。世界の鏡でも同じ手で解ける)
    private static void checkMirrors(ServerLevel l, List<String> f, List<String> notes) {
        StormBackState state = StormBackState.get(l);
        List<String> mins = new ArrayList<>();
        for (int i = 0; i < EyeMirrors.CONFIGS.size(); i++) {
            EyeMirrors.Config cfg = EyeMirrors.CONFIGS.get(i);
            int m = EyeMirrors.solve(cfg);
            mins.add(String.valueOf(m));
            check(m == EyeMirrors.MIN_MOVES[i], "mirror config " + i + " needs " + m + " moves (planned " + EyeMirrors.MIN_MOVES[i] + ")", f);
            check(m >= 4 && m <= 7, "mirror config " + i + " should take 4-7 moves", f);
            check(cfg.mirrors().size() >= 6 && cfg.mirrors().size() <= 8, "mirror config " + i + " should have 6-8 mirrors", f);
            // 世界の格子でたどった光が、格子だけの計算と同じ
            state.setRoutes(state.westRoute(), i);
            EyeMirrors.apply(l, state, false);
            EyeMirrors.Trace a = EyeMirrors.trace(cfg.entry(), EyeMirrors.worldGrid(l));
            EyeMirrors.Trace b = EyeMirrors.trace(cfg.entry(), EyeMirrors.grid(cfg, cfg.init()));
            check(java.util.Objects.equals(a.exit(), b.exit()), "mirror config " + i + ": the world beam differs from the grid", f);
            check(l.getBlockState(new BlockPos(0, EYE_HALL.floor() + 1, BEAK_DOOR_Z1)).is(ModBlocks.SEALED_GATE.get()), "beak door should start closed", f);
            // 世界の鏡を、解き方のとおりに回す
            List<Integer> path = EyeMirrors.solvePath(cfg);
            if (path == null) {
                f.add("mirror config " + i + " has no solution");
                continue;
            }
            for (int k : path) {
                int[] c = cfg.mirrors().get(k);
                BlockPos p = gridCell(c[0], c[1]);
                BlockState s = l.getBlockState(p);
                l.setBlock(p, s.setValue(StormBlocks.Mirror.ROT, (s.getValue(StormBlocks.Mirror.ROT) + 1) % 4), 3);
                EyeMirrors.tick(l, state);
            }
            check(state.receivers() == 7, "mirror config " + i + ": receivers lit " + Integer.toBinaryString(state.receivers()) + " after solving", f);
            check(l.getBlockState(new BlockPos(0, EYE_HALL.floor() + 1, BEAK_DOOR_Z1)).isAir(), "mirror config " + i + ": beak door did not open", f);
        }
        notes.add("mirror moves " + String.join("/", mins));
        state.setRoutes(state.westRoute(), 0);
        EyeMirrors.apply(l, state, false);
    }

    // ---------------------------------------------------------------- 避雷の柱・招雷・骨の戸・予告線
    private static void checkRods(ServerLevel l, List<String> f) {
        StormBackState state = StormBackState.get(l);
        StormEast.apply(l, state);
        BlockPos rod = EAST_RODS.get(0);
        check(StormRods.find(l, rod.offset(2, 0, 1), 3.0) != null, "a ring 2 blocks from the rod should go to the rod", f);
        check(StormRods.find(l, rod.offset(5, 0, 0), 3.0) == null, "a ring 5 blocks from the rod should not go to the rod", f);
        StormStrike.Strike s = StormStrike.call(l, net.minecraft.world.phys.Vec3.atBottomCenterOf(rod.offset(1, 0, 1)), 1, 11f, null);
        check(s.rod != null, "the strike next to the rod did not pick the rod", f);
        // 落雷は次の tick。ここでは柱を満たして、戸が開くのを見る
        StormRods.charge(l, rod, StormRods.FULL);
        check(StormRods.isFull(l, rod), "the first rod is not full after 3 charges", f);
        StormEast.openFullDoors(l, state);
        check(state.eastDoorOpen(0) && l.getBlockState(new BlockPos(EAST_DOORS[0], BONE_FLOOR + 1, -17)).isAir(), "the first bone door did not open", f);
        StormRods.ground(rod, 40);
        check(StormRods.isGrounded(rod) && StormRods.groundedTicks(rod) > 0, "grounding the rod", f);
        // 予告線: 低い線は跳べば当たらない、高い線はしゃがめば当たらない
        double fl = BONE_FLOOR + 1;
        check(StormEast.hits(fl, 1.8, false) && !StormEast.hits(fl + 0.8, 1.8, false), "low line: jumping should dodge", f);
        check(StormEast.hits(fl, 1.8, true) && !StormEast.hits(fl, 1.5, true), "high line: crouching should dodge", f);
        StormStrike.cancelAll(l);
    }

    // ---------------------------------------------------------------- 横風
    private static void checkWind(ServerLevel l, List<String> f) {
        StormBackClock.force(StormBackClock.Phase.CALM);
        check(StormWest.gust(l) == 0, "no crosswind in calm", f);
        check(StormBackClock.liftHeight(l) == StormBackClock.CALM_LIFT, "calm vent height", f);
        StormBackClock.force(StormBackClock.Phase.STORM);
        check(StormBackClock.liftHeight(l) == StormBackClock.STORM_LIFT, "storm vent height", f);
        StormBackClock.force(null);
    }

    // ---------------------------------------------------------------- 歩いて行ける所
    static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && !s.is(ModBlocks.SEALED_GATE.get());
    }

    static boolean standable(ServerLevel l, BlockPos p) {
        if (!passable(l, p) || !passable(l, p.above())) {
            return false;
        }
        BlockPos b = p.below();
        return !l.getBlockState(b).getCollisionShape(l, b).isEmpty();
    }

    // 昇り風の穴 (中心の床、上がる高さ)
    record Lift(BlockPos floor, double height) {
    }

    static List<Lift> lifts(ServerLevel l, boolean storm) {
        StormBackState state = StormBackState.get(l);
        double h = storm ? StormBackClock.STORM_LIFT : StormBackClock.CALM_LIFT;
        List<Lift> out = new ArrayList<>();
        for (StormWest.Vent v : StormWest.vents()) {
            out.add(new Lift(v.floor(), v.fixedHeight() >= 0 ? v.fixedHeight() : h));
        }
        if (state.raijuDefeated() || state.rung(StormBackState.Trial.EAST)) {
            out.add(new Lift(RAIJU_VENT, EAST_PERCH.floor() + 2 - RAIJU_VENT.getY() - 1));
        }
        return out;
    }

    // 到着から歩いて (跳んで 1 段、落ちて 3 段。西の骨の中は風切りの羽根で滑空するので何段でも落ちられる)、昇り風の穴にも乗って行ける所
    static Map<Long, Integer> walk(ServerLevel l, BlockPos start, boolean storm) {
        List<Lift> lifts = lifts(l, storm);
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        dist.put(start.asLong(), 0);
        q.add(start);
        while (!q.isEmpty() && dist.size() < 600000) {
            BlockPos p = q.poll();
            int d = dist.get(p.asLong());
            List<BlockPos> next = new ArrayList<>();
            boolean glide = StormWest.inBone(p.getCenter()) || p.getX() < -110;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                int maxFall = glide ? 40 : 3;
                for (int dy = 1; dy >= -maxFall; dy--) {
                    BlockPos m = n.above(dy);
                    if (dy == 1 && !passable(l, p.above(2))) {
                        continue;
                    }
                    if (dy < 0 && !(passable(l, n) && passable(l, n.above()) && passable(l, m))) {
                        break;
                    }
                    if (standable(l, m)) {
                        next.add(m);
                        break;
                    }
                }
            }
            // 昇り風: 穴の上 (中心から 1 マス以内) に立っていれば、穴の上の柱の空いている所から、横 3 マス以内の立てる所へ
            for (Lift lf : lifts) {
                BlockPos c = lf.floor();
                if (p.getY() == c.getY() + 1 && Math.abs(p.getX() - c.getX()) <= 1 && Math.abs(p.getZ() - c.getZ()) <= 1) {
                    int top = c.getY() + 1 + (int) Math.floor(lf.height());
                    for (int y = c.getY() + 1; y <= top; y++) {
                        if (!passable(l, new BlockPos(p.getX(), y + 1, p.getZ()))) {
                            top = y;
                            break;
                        }
                    }
                    for (int y = c.getY() + 2; y <= top + 1; y++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dz = -3; dz <= 3; dz++) {
                                BlockPos m = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                                if (standable(l, m)) {
                                    next.add(m);
                                }
                            }
                        }
                    }
                }
            }
            for (BlockPos m : next) {
                if (m.getY() < 105 || m.getY() > 200 || Math.abs(m.getX()) > 220 || Math.abs(m.getZ()) > 140) {
                    continue;
                }
                if (!dist.containsKey(m.asLong())) {
                    dist.put(m.asLong(), d + 1);
                    q.add(m);
                }
            }
        }
        return dist;
    }

    private static void expect(Map<Long, Integer> dist, ServerLevel l, String name, BlockPos g, boolean should, List<String> f) {
        boolean can = dist.containsKey(g.asLong());
        if (can != should) {
            String extra = "";
            if (!can) {
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
                extra = " (closest " + best + ", standable " + standable(l, g) + ")";
            }
            f.add((should ? "cannot reach " : "can reach ") + name + " " + g + extra);
        }
    }

    private static void checkWalk(ServerLevel l, List<String> f, List<String> notes) {
        StormBackState state = StormBackState.get(l);
        StormBackTrials.reset(l, false, null, true);
        // 凪、戸は閉じたまま
        Map<Long, Integer> calm = walk(l, ARRIVAL, false);
        Map<String, BlockPos> yes = new LinkedHashMap<>();
        for (String id : List.of("hub", "w0", "w1", "e0", "f0")) {
            yes.put("rest " + id, rest(id));
        }
        yes.put("gallery", new BlockPos(0, UPPER_GALLERY_Y + 1, -17));
        yes.put("left knob floor", WEST_KNOB.offset(4, 1, 0));
        yes.put("tail hall", TAIL_CENTER);
        yes.put("eye hall", EYE_HALL_CENTER.offset(1, 0, 6));
        yes.put("back top", new BlockPos(TOP_STAIR.getX() - 4, StormBirdBuilder.top(TOP_STAIR.getX() - 4, TOP_STAIR.getZ()) + 1, TOP_STAIR.getZ()));
        for (Map.Entry<String, BlockPos> e : yes.entrySet()) {
            expect(calm, l, e.getKey() + " (calm)", e.getValue(), true, f);
        }
        expect(calm, l, "west stone ledge in calm", WEST_STONE.offset(1, 0, 0), false, f);
        expect(calm, l, "rest e1 with closed doors", rest("e1"), false, f);
        expect(calm, l, "east perch before the raiju", EAST_STONE.offset(-1, 0, 0), false, f);
        expect(calm, l, "beak room before the mirrors", FRONT_STONE.offset(1, 0, 0), false, f);
        expect(calm, l, "keel antechamber before the stones", rest("heart"), false, f);
        Integer toHub = calm.get(rest("hub").asLong());
        notes.add("arrival to hall " + toHub + " steps");
        check(toHub != null && toHub <= 220, "the wind hall is too far from the arrival (" + toHub + " steps)", f);
        // 嵐: 西の岩棚へ届く
        Map<Long, Integer> storm = walk(l, ARRIVAL, true);
        expect(storm, l, "west stone ledge in storm", WEST_STONE.offset(1, 0, 0), true, f);
        expect(storm, l, "gallery in storm (by the hub updraft)", new BlockPos(0, UPPER_GALLERY_Y + 1, -15), true, f);
        // 骨の戸が開くと、肘・雷獣の巣へ。雷獣のあとは頂の台へ
        for (int i = 0; i < EAST_DOORS.length; i++) {
            state.openEastDoor(i);
            StormEast.setDoor(l, i, true);
        }
        state.setRaijuDefeated(true);
        Map<Long, Integer> open = walk(l, ARRIVAL, false);
        expect(open, l, "rest e1 after the doors", rest("e1"), true, f);
        expect(open, l, "raiju nest after the doors", RAIJU_NEST.offset(-6, 1, 0), true, f);
        expect(open, l, "east perch after the raiju", EAST_STONE.offset(-1, 0, 0), true, f);
        // 鏡を解く (戸を開ける) と嘴の小部屋へ。三つの石で竜骨の門が開くと、前室と竜骨の間へ
        EyeMirrors.setDoor(l, true);
        state.setKeelOpen(true);
        StormBirdBuilder.setKeelGate(l, true);
        Map<Long, Integer> all = walk(l, ARRIVAL, false);
        expect(all, l, "beak room after the mirrors", FRONT_STONE.offset(1, 0, 0), true, f);
        expect(all, l, "keel antechamber", rest("heart"), true, f);
        expect(all, l, "heart room", HEART_ROOM.offset(0, 2, 8), true, f);
        notes.add("standing spots " + all.size());
        StormBackTrials.reset(l, false, null, true);
    }

    // ---------------------------------------------------------------- リセットと眠る試練
    private static void checkReset(ServerLevel l, List<String> f) {
        StormBackState state = StormBackState.get(l);
        TitanBackData data = TitanBackData.get(l);
        int epoch = data.epoch();
        state.setRung(StormBackState.Trial.WEST, true);
        StormBackTrials.reset(l, false, null, true);
        check(data.epoch() != epoch, "reset should advance the trial number", f);
        check(state.rungCount() == 0 && !state.keelOpen() && state.receivers() == 0, "reset should clear the stones, keel and receivers", f);
        check(StormBackClock.phaseAt(state.clock()) == StormBackClock.Phase.CALM && state.clock() == 0, "reset should go back to the start of calm", f);
        check(l.getBlockState(new BlockPos(0, HUB.floor(), -4)).is(ModBlocks.SEALED_GATE.get()), "the keel gate should be closed after reset", f);
        // 眠る試練: 2 周目から一つ眠り、同じものは続けない。24 回で三つとも眠る
        boolean was = state.everDefeated();
        state.markDefeated();
        StormBackState.Trial prev = null;
        Set<StormBackState.Trial> seen = new HashSet<>();
        for (int i = 0; i < 24; i++) {
            state.reset(l.random, null, false);
            StormBackState.Trial s = state.sleeping();
            check(s != null && s != prev, "sleeping trial repeated or missing at reset " + i, f);
            check(s == null || state.rung(s), "the sleeping trial's stone should be ringing", f);
            seen.add(s);
            prev = s;
        }
        check(seen.size() == 3, "all three trials should sleep sometimes", f);
        if (!was) {
            // 心臓の撃破の記録は消せないので、テストのあとも 2 周目の扱い (自己テストのワールドだけ)
            SkyAbyss.LOGGER.info("SELFTEST storm back: marked the world as having defeated the heart");
        }
        state.reset(l.random, null, true);
    }

    // ---------------------------------------------------------------- ピースフル (腰の回廊を封じる)
    private static void checkPeaceful(ServerLevel l, List<String> f) {
        Difficulty before = l.getDifficulty();
        l.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        StormBackTrials.tickPeacefulForTest(l, true);
        check(l.getBlockState(new BlockPos(0, TAIL_HALL.floor() + 2, SEAL_Z)).is(ModBlocks.SEALED_GATE.get()), "peaceful should seal the waist corridor", f);
        l.getServer().setDifficulty(before, true);
        StormBackTrials.tickPeacefulForTest(l, false);
        check(!l.getBlockState(new BlockPos(0, TAIL_HALL.floor() + 2, SEAL_Z)).is(ModBlocks.SEALED_GATE.get()), "the seal should open again", f);
        check(l.getBlockState(new BlockPos(0, TAIL_HALL.floor() + 2, SEAL_Z)).is(Blocks.AIR), "the corridor should be air after peaceful", f);
    }
}
