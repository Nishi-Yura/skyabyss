package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 開発用の自己テスト。環境変数 SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く。
// 岩鯨の背を建て、遊べる形になっているか (通れる・届く・近道がない・空に穴が開いていない・元に戻せる) を確かめて、サーバーを止める。
// 自己テストの入口と順番は SelfTestRunner
public final class TitanBackSelfTest {
    private TitanBackSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき)。1 tick に 1 段ずつ:
    // 建てる → 部屋・扉・アスレ・水路などを調べる → リセットを調べる (ここまでは最初に流す)。
    // 番兵を湧かせて 5 秒動かす → ピースフルで眠るか → 難易度を戻して封印が外れるか → まとめを書いてサーバーを止める (最後に流す)
    public static List<SelfTestRunner.Step> buildSteps() {
        return List.of(
                SelfTestRunner.Step.once("titan back (build)", server -> guarded(() -> {
                    PENDING.clear();
                    SPAWNED.clear();
                    stage = 0;
                    originalDifficulty = null;
                    ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
                    if (back == null) {
                        PENDING.add("titan_back dimension is missing");
                        return;
                    }
                    TitanBackData built = TitanBackData.get(back);
                    TitanBackBuilder.build(back, built);
                    if (built.needsBuild() || built.layoutVersion() != LAYOUT_VERSION) {
                        PENDING.add("layout version was not recorded after building (" + built.layoutVersion() + ")");
                    }
                })),
                SelfTestRunner.Step.once("titan back (layout)", server -> withBack(server, back -> {
                    checkSurface(back, PENDING);
                    checkRooms(back, PENDING);
                    checkDoors(back, PENDING);
                    checkCourses(back, PENDING);
                    checkHalls(back, PENDING);
                    checkMaze(back, PENDING);
                    checkTreasury(back, PENDING);
                    checkHeart(back, PENDING);
                    checkRestPoints(back, PENDING);
                    checkSigns(back, PENDING);
                    forceMazeChunks(back, true);
                })),
                // 水が流れ広がるのを待ってから、息継ぎの空気が残っているかを数える (水は 5 tick ごとに一段広がる)
                SelfTestRunner.Step.once("titan back (maze settled)", MAZE_SETTLE_TICKS, server -> withBack(server, back -> checkPockets(back, PENDING))),
                SelfTestRunner.Step.once("titan back (reset)", server -> withBack(server, back -> {
                    checkReset(back, PENDING);
                    if (!back.getBlockState(TitanBackLayout.forge()).is(ModBlocks.RESONANCE_FORGE.get())) {
                        PENDING.add("resonance forge missing at " + TitanBackLayout.forge());
                    }
                    PENDING.addAll(jp.revati.skyabyss.loot.TitanLoot.selfTest());
                    String mapDir = System.getenv("SKYABYSS_SELFTEST_MAP");
                    if (mapDir != null) {
                        writeMaps(back, java.nio.file.Path.of(mapDir));
                    }
                })),
                SelfTestRunner.Step.once("titan back (reset maze settled)", MAZE_SETTLE_TICKS, server -> withBack(server, back -> {
                    List<String> after = new ArrayList<>();
                    checkPockets(back, after);
                    after.forEach(f -> PENDING.add("after reset: " + f));
                    forceMazeChunks(back, false);
                })));
    }

    public static List<SelfTestRunner.Step> trialSteps() {
        return List.of(
                SelfTestRunner.Step.once("titan back (guards)", server -> withBack(server, TitanBackSelfTest::spawnGuards)),
                // 番兵たちを 5 秒動かしてから
                SelfTestRunner.Step.once("titan back (peaceful)", 100, server -> withBack(server, back -> {
                    checkGuardsAlive();
                    enterPeaceful(server, back);
                })),
                SelfTestRunner.Step.once("titan back (unseal)", 40, server -> withBack(server, back -> leavePeaceful(server, back))),
                SelfTestRunner.Step.once("titan back", 20, TitanBackSelfTest::finish));
    }

    private static void guarded(Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan back crashed", e);
            PENDING.add("crashed: " + e);
        }
    }

    private static void withBack(MinecraftServer server, java.util.function.Consumer<ServerLevel> check) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            guarded(() -> check.accept(back));
        }
    }

    private static final List<String> PENDING = new ArrayList<>();
    private static final List<net.minecraft.world.entity.Mob> SPAWNED = new ArrayList<>();

    // 2 段目: ピースフルにすると、試練が眠るか (祠の封印・番兵と心臓が消える・閉じた扉が開く)。
    // 3 段目: 難易度を戻すと、祠の封印が外れるか
    private static int stage;
    private static net.minecraft.world.Difficulty originalDifficulty;

    // 番兵たちを実際に湧かせて、しばらく動かしてみる (落ちたり消えたりしないか)。
    // プレイヤーがいないので、湧かせる場所のチャンクを読み込んだままにしておく
    private static void spawnGuards(ServerLevel back) {
        BlockPos at = HUB.center();
        BlockPos waterAt = mazeCell(MAZE_START_I, MAZE_START_J);
        for (BlockPos c : List.of(at, waterAt)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    back.setChunkForced((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, true);
                }
            }
        }
        for (String kind : List.of("sentinel", "archer", "crawler", "captain")) {
            var mob = TitanBackTrials.spawnGuard(back, kind, at);
            if (mob == null) {
                PENDING.add("could not spawn " + kind);
            } else {
                SPAWNED.add(mob);
                // 試練の番兵は保存しない (保存して終了したあとに二重に湧かないように)
                if (mob.shouldBeSaved()) {
                    PENDING.add(kind + " spawned by the trials would be saved to disk");
                }
            }
        }
        BlockPos water = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 1, 1);
        var maw = jp.revati.skyabyss.registry.ModEntities.WATERWAY_MAW.get().create(back);
        if (maw != null) {
            maw.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5);
            back.addFreshEntity(maw);
            SPAWNED.add(maw);
        }
    }

    private static void checkGuardsAlive() {
        for (var mob : SPAWNED) {
            if (!mob.isAlive() || mob.isRemoved()) {
                PENDING.add(mob.getType() + " did not survive 5 seconds (" + mob.getRemovalReason() + ", hp " + mob.getHealth() + ")");
            }
        }
    }

    private static void enterPeaceful(MinecraftServer server, ServerLevel back) {
        stage = 1;
        SPAWNED.forEach(net.minecraft.world.entity.Entity::discard);
        SPAWNED.clear();
        // 祠の封印の場所を読み込んでおく (プレイヤーがいないので)
        BlockPos seal = shrineSeal().get(0);
        back.setChunkForced(seal.getX() >> 4, seal.getZ() >> 4, true);
        // 戦いの途中を作る: 第一の広間の入口と心臓の間の入口を閉じ、試練の番兵と心臓を置く
        TitanBackTrials.seal(back, E1_ENTRANCE);
        TitanBackTrials.seal(back, HEART_GATE);
        var guard = TitanBackTrials.spawnGuard(back, "sentinel", HUB.center());
        if (guard != null) {
            SPAWNED.add(guard);
        }
        var heart = jp.revati.skyabyss.registry.ModEntities.STONE_WHALE_HEART.get().create(back);
        if (heart != null) {
            BlockPos at = HUB.center();
            heart.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0f, 0f);
            heart.addTag(TitanBackTrials.HEART_TAG);
            heart.bindArena(TitanBackTrials.heartArena(), 1);
            back.addFreshEntity(heart);
            SPAWNED.add(heart);
            if (heart.shouldBeSaved()) {
                PENDING.add("the trial heart would be saved to disk");
            }
        }
        originalDifficulty = server.getWorldData().getDifficulty();
        if (originalDifficulty == net.minecraft.world.Difficulty.PEACEFUL) {
            originalDifficulty = net.minecraft.world.Difficulty.NORMAL;
        }
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
    }

    private static void leavePeaceful(MinecraftServer server, ServerLevel back) {
        if (stage != 1) {
            return;
        }
        stage = 2;
        for (var mob : SPAWNED) {
            if (mob.isAlive() && !mob.isRemoved()) {
                PENDING.add(mob.getType() + " (trial) was not put to sleep in peaceful");
            }
        }
        for (BlockPos p : shrineSeal()) {
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                PENDING.add("peaceful did not seal the shrine stairs at " + p);
                break;
            }
        }
        for (List<BlockPos> door : List.of(E1_ENTRANCE, HEART_GATE)) {
            for (BlockPos p : door) {
                if (!back.getBlockState(p).isAir()) {
                    PENDING.add("peaceful did not reopen the door at " + p);
                    break;
                }
            }
        }
        server.setDifficulty(originalDifficulty, true);
    }

    private static void finish(MinecraftServer server) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null && stage == 2) {
            for (BlockPos p : shrineSeal()) {
                if (!back.getBlockState(p).isAir()) {
                    PENDING.add("leaving peaceful did not unseal the shrine stairs at " + p);
                    break;
                }
            }
        }
        if (PENDING.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST PASSED (guards ran for 5 seconds; peaceful sealed the shrine and put the trials to sleep)");
        } else {
            PENDING.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST FAILED: {}", f));
            SkyAbyss.LOGGER.error("SELFTEST FAILED: {} problems", PENDING.size());
        }
        if (originalDifficulty != null) {
            server.setDifficulty(originalDifficulty, true);
        }
        server.halt(false);
    }

    private static void checkSurface(ServerLevel level, List<String> failures) {
        BlockPos arrival = arrival();
        air(level, arrival, "arrival", failures);
        air(level, arrival.above(), "arrival head", failures);
        solid(level, arrival.below(), "arrival floor", failures);
        if (!level.getBlockState(anchor()).is(Blocks.LODESTONE)) {
            failures.add("anchor lodestone missing at " + anchor());
        }
        // 祠から体内へ、階段が途切れずにつながっていること
        for (int z = SHRINE_Z - 1; z >= 4; z--) {
            boolean walkable = false;
            for (int y = FLOOR; y <= shrineY(); y++) {
                BlockPos p = new BlockPos(0, y, z);
                if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()) {
                    walkable = true;
                    break;
                }
            }
            if (!walkable) {
                failures.add("stairwell blocked at z=" + z);
            }
        }
    }

    // どの部屋も、天井と床 (谷は溶岩の底) がそろっていて、空や奈落に穴が開いていないこと
    private static void checkRooms(ServerLevel level, List<String> failures) {
        List<Box> rooms = List.of(HUB, ENTRY, TREASURY, W0, WA, W1, WB, W2, WC, E0, E1, CE1, E3_CORRIDOR, E3, MAZE_EXIT_ROOM);
        for (Box room : rooms) {
            int holes = 0;
            for (int x = room.x1(); x <= room.x2(); x++) {
                for (int z = room.z1(); z <= room.z2(); z++) {
                    if (level.getBlockState(new BlockPos(x, room.ceilingY(), z)).isAir()) {
                        // 天井の穴は、はしごや階段の縦穴だけ許す
                        if (!level.getBlockState(new BlockPos(x, room.ceilingY() + 1, z)).isAir()) {
                            continue;
                        }
                        holes++;
                    }
                }
            }
            if (holes > 4) {
                failures.add("room " + room + " has " + holes + " holes in its ceiling");
            }
        }
        for (Box pit : PITS) {
            for (int x = pit.x1(); x <= pit.x2(); x++) {
                for (int z = pit.z1(); z <= pit.z2(); z++) {
                    if (!level.getBlockState(new BlockPos(x, 86, z)).is(Blocks.LAVA)) {
                        failures.add("pit has no lava at " + x + "," + z);
                        return;
                    }
                }
            }
        }
    }

    private static void checkDoors(ServerLevel level, List<String> failures) {
        // 通れるはずの戸口 (足元の高さと頭の高さ)
        int[][] open = {
                {0, FLOOR + 1, -11}, {0, FLOOR + 1, -12}, {-12, FLOOR + 1, -22}, {12, FLOOR + 1, -22},
                {-23, FLOOR + 1, -22}, {-30, 98, -35}, {-29, 98, -43}, {-23, 101, -57}, {-17, 101, -55},
                {23, FLOOR + 1, -22}, {25, FLOOR + 1, -39}, {22, FLOOR + 1, -39}, {20, FLOOR + 1, -39},
                {6, 94, -7},
                // 心臓の回廊と心臓の間の入口 (戦いの前は開いている)
                {0, 94, -46}, {0, 94, -63}, {0, 94, -64}, {0, 94, -65}};
        for (int[] d : open) {
            BlockPos p = new BlockPos(d[0], d[1], d[2]);
            air(level, p, "doorway", failures);
            air(level, p.above(), "doorway head", failures);
        }
        for (List<BlockPos> sealed : List.of(GATE, INNER_GATE, WEST_RETURN, EAST_RETURN, LOWER_RETURN, E1_EXIT)) {
            for (BlockPos p : sealed) {
                if (!level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                    failures.add("should start sealed: " + p);
                }
            }
        }
        for (BlockPos p : RESONANCE_STONES) {
            if (!level.getBlockState(p).is(ModBlocks.RESONANCE_STONE.get())) {
                failures.add("resonance stone missing at " + p);
            }
            air(level, p.above(), "above resonance stone", failures);
        }
        // 抜け道の内側 (鼓動の間の側) が柱などでふさがれていないこと
        air(level, new BlockPos(9, 94, -31), "east return (hub side)", failures);
        air(level, new BlockPos(11, 94, -31), "east return (hall side)", failures);
        if (!level.getBlockState(new BlockPos(-9, 98, -31)).is(Blocks.LADDER)) {
            failures.add("west return ladder missing");
        }
        if (!level.getBlockState(new BlockPos(4, FLOOR, -25)).is(Blocks.LADDER)) {
            failures.add("maze entrance ladder missing at the hub floor");
        }
        // 配置の版 2 で除いた第二の広間・旧番兵長の控えの間・その間の通路は、埋まっていること (古いワールドを建て直したとき)
        for (BlockPos p : List.of(new BlockPos(26, 94, -50), new BlockPos(28, 94, -43), new BlockPos(20, 94, -60), new BlockPos(17, 94, -55))) {
            if (level.getBlockState(p).isAir()) {
                failures.add("removed east hall space is still open at " + p);
            }
        }
        // 祠の階段の封印の場所は、ピースフルでないときは通れること
        for (BlockPos p : shrineSeal()) {
            air(level, p, "shrine stair (seal spot)", failures);
        }
        if (level.getBlockState(shrineSeal().get(0).below()).isAir()) {
            failures.add("shrine seal is not on the stairs at " + shrineSeal().get(0));
        }
        // 物語の碑文と試練の入口の看板: 看板があり、掛かっている壁が固いこと
        for (Inscription lore : allSigns()) {
            BlockState sign = level.getBlockState(lore.pos());
            BlockPos wall = lore.pos().relative(lore.facing().getOpposite());
            if (!(sign.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock)) {
                failures.add("lore sign " + lore.key() + " missing at " + lore.pos() + " (" + sign + ")");
            } else if (!level.getBlockState(wall).isFaceSturdy(level, wall, lore.facing())) {
                failures.add("lore sign " + lore.key() + " has no wall behind it at " + wall);
            }
        }
    }

    // 看板: 背中に置かれた看板は、碑文と試練の入口の一言だけ (allSigns) で、どの行も看板の幅に収まること (日本語・英語)。
    // 囁き (看板の代わりの案内) の文が lang にあること
    private static void checkSigns(ServerLevel level, List<String> failures) {
        Set<BlockPos> expected = new HashSet<>();
        allSigns().forEach(i -> expected.add(i.pos()));
        Set<String> keys = new java.util.TreeSet<>();
        int count = 0;
        for (int cx = (-RX - 8) >> 4; cx <= (RX + 8) >> 4; cx++) {
            for (int cz = (-RZ - 8) >> 4; cz <= (RZ + 8) >> 4; cz++) {
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) {
                        continue;
                    }
                    count++;
                    if (!expected.contains(be.getBlockPos())) {
                        failures.add("unexpected sign at " + be.getBlockPos() + " (only inscriptions and trial entrances keep signs)");
                    }
                    for (boolean front : new boolean[]{true, false}) {
                        var text = front ? sign.getFrontText() : sign.getBackText();
                        for (int i = 0; i < 4; i++) {
                            var line = text.getMessage(i, false);
                            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                                String k = tc.getKey();
                                keys.add(k.substring(0, k.lastIndexOf('.')));
                            } else if (!line.getString().isEmpty()) {
                                failures.add("sign at " + be.getBlockPos() + " has untranslated text: " + line.getString());
                            }
                        }
                    }
                }
            }
        }
        if (count != expected.size()) {
            failures.add("found " + count + " signs on the back, expected " + expected.size());
        }
        failures.addAll(jp.revati.skyabyss.SignFit.check(keys));
        var ja = jp.revati.skyabyss.SignFit.lang("ja_jp");
        var en = jp.revati.skyabyss.SignFit.lang("en_us");
        for (String k : BackWhispers.keys()) {
            if (!ja.containsKey(k) || !en.containsKey(k)) {
                failures.add("whisper text missing: " + k);
            }
        }
        SkyAbyss.LOGGER.info("Titan back signs: {} placed ({}), every line measured against {} px", count, keys,
                jp.revati.skyabyss.SignFit.MAX_LINE_WIDTH);
    }

    // アスレチック: どの足場も次の足場へ届き、一つ飛ばしでは届かず、頭上が空いていること。谷に床が残っていないこと。
    // 今の形に加えて、ほかの種でも作れることを確かめる (リセットのたびに作り直すため)
    private static void checkCourses(ServerLevel level, List<String> failures) {
        List<List<int[]>> courses = ParkourCourses.current(level);
        for (int c = 0; c < courses.size(); c++) {
            CourseGenerator.Spec spec = ParkourCourses.ALL.get(c);
            for (int[] s : courses.get(c)) {
                BlockPos p = new BlockPos(s[0], s[1], s[2]);
                if (level.getBlockState(p).isAir()) {
                    failures.add("course step missing at " + p);
                }
                for (int h = 1; h <= 3; h++) {
                    air(level, p.above(h), "room above course step", failures);
                }
            }
            validateCourse(spec, courses.get(c), "course " + c, failures);
        }
        for (long seed = 1; seed <= 12; seed++) {
            List<List<int[]>> other = ParkourCourses.forSeed(seed * 7919);
            for (int c = 0; c < other.size(); c++) {
                validateCourse(ParkourCourses.ALL.get(c), other.get(c), "seed " + seed + " course " + c, failures);
            }
        }
        ParkourCourses.forSeed(TitanBackData.get(level).courseSeed());
        for (Box pit : PITS) {
            for (int x = pit.x1(); x <= pit.x2(); x++) {
                for (int z = pit.z1(); z <= pit.z2(); z++) {
                    BlockState floor = level.getBlockState(new BlockPos(x, FLOOR, z));
                    boolean ledge = (x == -24 && z >= -23 && z <= -21) || isStep(courses, x, FLOOR, z);
                    if (!floor.isAir() && !ledge) {
                        failures.add("pit floor remains at " + x + "," + z + " (" + floor + ")");
                        return;
                    }
                }
            }
        }
    }

    private static void validateCourse(CourseGenerator.Spec spec, List<int[]> steps, String name, List<String> failures) {
        if (steps.size() < spec.minSteps() || steps.size() > spec.maxSteps()) {
            failures.add(name + " has " + steps.size() + " steps");
        }
        List<int[]> earlier = new ArrayList<>(spec.start());
        int[] prev = null;
        for (int[] s : steps) {
            boolean ok = prev == null ? spec.start().stream().anyMatch(b -> reachable(b, s)) : reachable(prev, s);
            if (!ok) {
                failures.add(name + " jump too far to " + s[0] + "," + s[1] + "," + s[2]);
            }
            for (int[] e : earlier) {
                if (e != prev && reachable(e, s) && prev != null) {
                    failures.add(name + " can skip to " + s[0] + "," + s[1] + "," + s[2]);
                    break;
                }
            }
            if (prev != null) {
                earlier.add(prev);
            }
            prev = s;
        }
        final int[] last = prev;
        if (last == null || spec.end().stream().noneMatch(b -> reachable(last, b))) {
            failures.add(name + " does not reach its end ledge");
        }
        for (int[] e : earlier) {
            if (spec.end().stream().anyMatch(b -> reachable(e, b))) {
                failures.add(name + " end ledge reachable too early");
                break;
            }
        }
    }

    private static boolean isStep(List<List<int[]>> courses, int x, int y, int z) {
        for (List<int[]> course : courses) {
            for (int[] s : course) {
                if (s[0] == x && s[1] == y && s[2] == z) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean reachable(int[] p, int[] q) {
        int dy = q[1] - p[1];
        double d = Math.hypot(q[0] - p[0], q[2] - p[2]);
        if (dy > 1 || dy < -1 || d < 2) {
            return false;
        }
        double limit = dy == 1 ? 3.2 : dy == 0 ? 4.0 : 4.3;
        return d <= limit;
    }


    private static void checkHalls(ServerLevel level, List<String> failures) {
        for (Box hall : List.of(E1, E3)) {
            for (BlockPos p : EastWingBuilder.spawnPoints(hall)) {
                air(level, p, "hall spawn point", failures);
                air(level, p.above(), "hall spawn point head", failures);
            }
        }
    }

    // 闇の水路: 入口のはしごの下から、水の中を通って出口の泡の柱までたどり着けること。休みの泡がいくつかあること
    private static void checkMaze(ServerLevel level, List<String> failures) {
        BlockPos start = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 2, 0);
        BlockPos goal = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J).offset(1, 1, 1);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = cur.relative(d);
                if (n.getX() < MAZE_X0 || n.getX() > 22 || n.getZ() < MAZE_Z0 || n.getZ() > -2 || n.getY() < 78 || n.getY() > 83 || seen.contains(n)) {
                    continue;
                }
                BlockState state = level.getBlockState(n);
                if (state.getFluidState().isSource() || state.isAir()) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        if (!seen.contains(goal)) {
            failures.add("maze exit is unreachable from the ladder");
        }
        if (MazeBuilder.maze(TitanBackData.get(level).mazeSeed()).links(MAZE_START_I, MAZE_START_J) < 2) {
            failures.add("maze entrance is not a junction");
        }
        BlockPos exit = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J).offset(1, 0, 1);
        if (!level.getBlockState(new BlockPos(exit.getX(), MAZE_EXIT_ROOM.floorY(), exit.getZ())).is(Blocks.BUBBLE_COLUMN)) {
            failures.add("maze exit has no rising bubble column");
        }
        if (!level.getBlockState(new BlockPos(MAZE_EXIT_ROOM.x1(), FLOOR, -7)).is(Blocks.LADDER)) {
            failures.add("maze return ladder missing");
        }
    }

    // 息継ぎのくぼみを置いてから水を流す時間
    private static final int MAZE_SETTLE_TICKS = 100;

    // 迷路のチャンクを読み込んだままにする (プレイヤーがいないと、水が流れる tick が来ないため)
    private static void forceMazeChunks(ServerLevel level, boolean forced) {
        int x2 = MAZE_X0 + MAZE_W * (MAZE_CELL + 1);
        int z2 = MAZE_Z0 + MAZE_H * (MAZE_CELL + 1);
        for (int cx = MAZE_X0 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = MAZE_Z0 >> 4; cz <= z2 >> 4; cz++) {
                level.setChunkForced(cx, cz, forced);
            }
        }
    }

    // 水を流したあとでも、正しい道の息継ぎのくぼみごとに 3×3×2 の空気が残っていること (流れる水で埋まっていないこと)。
    // くぼみの下の段は水源のままで、泳いで入れること
    private static void checkPockets(ServerLevel level, List<String> failures) {
        List<BlockPos> pockets = MazeBuilder.pockets(MazeBuilder.maze(TitanBackData.get(level).mazeSeed()));
        if (pockets.size() < 2) {
            failures.add("maze has too few breathing pockets on the path (" + pockets.size() + ")");
        }
        int flooded = 0;
        for (BlockPos p : pockets) {
            String bad = null;
            for (int dx = 0; dx < MAZE_CELL && bad == null; dx++) {
                for (int dz = 0; dz < MAZE_CELL && bad == null; dz++) {
                    for (int dy = 2; dy <= 3; dy++) {
                        if (!level.getBlockState(p.offset(dx, dy, dz)).isAir()) {
                            bad = level.getBlockState(p.offset(dx, dy, dz)) + " at " + p.offset(dx, dy, dz).toShortString();
                        }
                    }
                    if (!level.getFluidState(p.offset(dx, 1, dz)).isSource()) {
                        bad = "no water source under the air at " + p.offset(dx, 1, dz).toShortString();
                    }
                }
            }
            if (bad != null) {
                flooded++;
                failures.add("breathing pocket at " + p.toShortString() + " is flooded or blocked after " + MAZE_SETTLE_TICKS + " ticks: " + bad);
            }
        }
        SkyAbyss.LOGGER.info("SELFTEST maze pockets: {} on the path, {} flooded", pockets.size(), flooded);
    }

    // 宝物庫: 台座は飾りとして残る。宝箱はもうない (欠片と装備は心臓を倒したときに渡す)
    private static void checkTreasury(ServerLevel level, List<String> failures) {
        if (!level.getBlockState(PEDESTAL).is(Blocks.LODESTONE)) {
            failures.add("pedestal missing");
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -43; z <= -36; z++) {
                if (level.getBlockEntity(new BlockPos(x, FLOOR + 1, z)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                    failures.add("treasure chest should be gone at " + x + "," + z);
                }
            }
        }
    }

    // 心臓の回廊と心臓の間: 中は空いていて、天井・壁が体の中で閉じていること (抜け道がないこと)。休み場の焚き火があること
    private static void checkHeart(ServerLevel level, List<String> failures) {
        int cx = HEART_CX;
        int cz = HEART_CZ;
        if (!level.getBlockState(new BlockPos(cx, FLOOR + 1, cz)).isAir()) {
            failures.add("heart arena floor-level center is blocked");
        }
        if (!level.getBlockState(new BlockPos(cx, HEART.ceilingY() - 1, cz)).isAir()) {
            failures.add("heart arena top-level center is blocked");
        }
        if (!level.getBlockState(new BlockPos(cx, FLOOR, cz)).isSolid() || !level.getBlockState(new BlockPos(cx, HEART.ceilingY(), cz)).isSolid()) {
            failures.add("heart arena floor or ceiling is not solid");
        }
        int holes = 0;
        for (int dx = -17; dx <= 17; dx++) {
            for (int dz = -17; dz <= 17; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 272) {
                    continue;
                }
                BlockPos ceil = new BlockPos(cx + dx, HEART.ceilingY(), cz + dz);
                if (d2 <= 210) {
                    // 内側の天井は、すべて固体であること
                    if (!level.getBlockState(ceil).isSolid()) {
                        holes++;
                    }
                } else if (!level.getBlockState(new BlockPos(cx + dx, 100, cz + dz)).isSolid()) {
                    // 壁の輪は、どこも空いていないこと
                    holes++;
                }
            }
        }
        if (holes > 0) {
            failures.add("heart arena shell has " + holes + " holes");
        }
        if (!level.getBlockState(new BlockPos(0, 94, -54)).is(Blocks.SOUL_CAMPFIRE)) {
            failures.add("heart rest campfire missing");
        }
    }

    // 休み場の復活位置: 足元と頭が空いていて、床が固いこと (鼓動の間の台座や腰掛けに埋まらない)
    private static void checkRestPoints(ServerLevel level, List<String> failures) {
        for (var e : TitanBackTrials.checkpointSpawns().entrySet()) {
            if (!TitanBackTrials.isSafeStand(level, e.getValue())) {
                failures.add("rest point " + e.getKey() + " spawn is not a safe standing spot at " + e.getValue()
                        + " (feet " + level.getBlockState(e.getValue()) + ", floor " + level.getBlockState(e.getValue().below()) + ")");
            }
        }
    }

    // 試練を進めた状態から、リセットで元に戻ること
    private static void checkReset(ServerLevel level, List<String> failures) {
        for (BlockPos p : RESONANCE_STONES) {
            level.setBlock(p, level.getBlockState(p).setValue(ResonanceStoneBlock.ACTIVE, true), 3);
        }
        TitanBackTrials.open(level, GATE);
        TitanBackTrials.open(level, E1_EXIT);
        TitanBackData data = TitanBackData.get(level);
        data.markGateOpened();
        data.markHallCleared("e1");
        data.markHeartDefeated();
        long courseSeed = data.courseSeed();
        long mazeSeed = data.mazeSeed();
        int epoch = data.epoch();
        // 休み場の記録は、同じ番号の間だけ有効。リセットのあとは無効 (尾の台) になること
        net.minecraft.nbt.CompoundTag rest = new net.minecraft.nbt.CompoundTag();
        TitanBackTrials.writeCheckpoint(rest, "heart", epoch);
        if (!"heart".equals(TitanBackTrials.validCheckpoint(rest, epoch))) {
            failures.add("a fresh rest point was not accepted");
        }
        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("skyabyss_checkpoint", "heart");
        if (!TitanBackTrials.validCheckpoint(legacy, epoch).isEmpty()) {
            failures.add("a rest point without an epoch (older than this fix) was accepted");
        }
        TitanBackTrials.resetTrials(level);
        if (data.epoch() == epoch) {
            failures.add("reset did not advance the epoch");
        }
        if (!TitanBackTrials.validCheckpoint(rest, data.epoch()).isEmpty()) {
            failures.add("a rest point from before the reset was still accepted");
        }
        if (data.courseSeed() == courseSeed || data.mazeSeed() == mazeSeed) {
            failures.add("reset did not reshape the parkour and maze");
        }
        List<String> after = new ArrayList<>();
        checkCourses(level, after);
        checkMaze(level, after);
        after.forEach(f -> failures.add("after reset: " + f));
        for (BlockPos p : RESONANCE_STONES) {
            if (level.getBlockState(p).getValue(ResonanceStoneBlock.ACTIVE)) {
                failures.add("reset left a stone singing at " + p);
            }
        }
        for (List<BlockPos> sealed : List.of(GATE, INNER_GATE, E1_EXIT)) {
            for (BlockPos p : sealed) {
                if (!level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                    failures.add("reset did not reseal " + p);
                }
            }
        }
        for (BlockPos p : HEART_GATE) {
            if (!level.getBlockState(p).isAir()) {
                failures.add("reset did not open the heart gate at " + p);
            }
        }
        if (data.hasProgress()) {
            failures.add("reset left progress in the saved data");
        }
    }

    // 見取り図を画像で書き出す (地表を真上から、体内の歩く高さ、アスレの高さ、水路の高さ)
    private static void writeMaps(ServerLevel level, java.nio.file.Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            int[][] slices = {{-1}, {FLOOR + 1}, {98}, {79}};
            String[] names = {"surface", "floor94", "level98", "maze79"};
            for (int k = 0; k < slices.length; k++) {
                int w = RX * 2 + 9;
                int h = RZ * 2 + 9;
                int scale = 4;
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * scale, h * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int x = -RX - 4; x <= RX + 4; x++) {
                    for (int z = -RZ - 4; z <= RZ + 4; z++) {
                        int color = 0x1A2233;
                        if (slices[k][0] < 0) {
                            for (int y = BASE_Y + 40; y > BASE_Y - 50; y--) {
                                BlockState st = level.getBlockState(new BlockPos(x, y, z));
                                if (!st.isAir()) {
                                    color = shade(st.getMapColor(level, new BlockPos(x, y, z)).col, (y - BASE_Y) * 4);
                                    break;
                                }
                            }
                        } else {
                            BlockPos p = new BlockPos(x, slices[k][0], z);
                            BlockState st = level.getBlockState(p);
                            if (st.isAir()) {
                                BlockState below = level.getBlockState(p.below());
                                color = below.isAir() ? 0x05060A : shade(below.getMapColor(level, p.below()).col, 40);
                            } else if (!st.getFluidState().isEmpty()) {
                                color = 0x2E5BD6;
                            } else {
                                color = shade(st.getMapColor(level, p).col, -60);
                            }
                        }
                        for (int dx = 0; dx < scale; dx++) {
                            for (int dz = 0; dz < scale; dz++) {
                                img.setRGB((x + RX + 4) * scale + dx, (z + RZ + 4) * scale + dz, color);
                            }
                        }
                    }
                }
                javax.imageio.ImageIO.write(img, "png", dir.resolve(names[k] + ".png").toFile());
            }
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.error("Failed to write maps", e);
        }
    }

    private static int shade(int rgb, int amount) {
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + amount));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + amount));
        int b = Math.max(0, Math.min(255, (rgb & 255) + amount));
        return (r << 16) | (g << 8) | b;
    }

    private static void air(ServerLevel level, BlockPos pos, String what, List<String> failures) {
        if (!level.getBlockState(pos).isAir()) {
            failures.add(what + " is blocked at " + pos + " by " + level.getBlockState(pos));
        }
    }

    private static void solid(ServerLevel level, BlockPos pos, String what, List<String> failures) {
        if (level.getBlockState(pos).isAir()) {
            failures.add(what + " is missing at " + pos);
        }
    }
}
