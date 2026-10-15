package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

// 開発用の自己テスト。環境変数 SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く。
// 岩鯨の背を建て、遊べる形になっているか (通れる・届く・近道がない・空に穴が開いていない・元に戻せる) を確かめて、サーバーを止める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanBackSelfTest {
    private TitanBackSelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        List<String> failures = new ArrayList<>();
        if (back == null) {
            failures.add("titan_back dimension is missing");
        } else {
            TitanBackBuilder.build(back, TitanBackData.get(back));
            checkSurface(back, failures);
            checkRooms(back, failures);
            checkDoors(back, failures);
            checkCourses(back, failures);
            checkHalls(back, failures);
            checkMaze(back, failures);
            checkTreasury(back, failures);
            checkReset(back, failures);
            if (!back.getBlockState(TitanBackLayout.forge()).is(ModBlocks.RESONANCE_FORGE.get())) {
                failures.add("resonance forge missing at " + TitanBackLayout.forge());
            }
            failures.addAll(jp.revati.skyabyss.loot.TitanLoot.selfTest());
            String mapDir = System.getenv("SKYABYSS_SELFTEST_MAP");
            if (mapDir != null) {
                writeMaps(back, java.nio.file.Path.of(mapDir));
            }
        }
        PENDING.addAll(failures);
        if (back != null) {
            // 番兵たちを実際に湧かせて、しばらく動かしてみる (落ちたり消えたりしないか)。
            // プレイヤーがいないので、湧かせる場所のチャンクを読み込んだままにしておく
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
        waitTicks = 100;
    }

    private static final List<String> PENDING = new ArrayList<>();
    private static final List<net.minecraft.world.entity.Mob> SPAWNED = new ArrayList<>();
    private static int waitTicks = -1;

    @SubscribeEvent
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (waitTicks < 0 || --waitTicks > 0) {
            return;
        }
        waitTicks = -1;
        for (var mob : SPAWNED) {
            if (!mob.isAlive() || mob.isRemoved()) {
                PENDING.add(mob.getType() + " did not survive 5 seconds (" + mob.getRemovalReason() + ", hp " + mob.getHealth() + ")");
            }
        }
        if (PENDING.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST PASSED ({} mobs ran for 5 seconds)", SPAWNED.size());
        } else {
            PENDING.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST FAILED: {}", f));
            SkyAbyss.LOGGER.error("SELFTEST FAILED: {} problems", PENDING.size());
        }
        event.getServer().halt(false);
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
        List<Box> rooms = List.of(HUB, ENTRY, TREASURY, W0, WA, W1, WB, W2, WC, E0, E1, CE1, E2, CE2, E3, MAZE_EXIT_ROOM);
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
                {23, FLOOR + 1, -22}, {28, FLOOR + 1, -43}, {17, FLOOR + 1, -55}, {6, 94, -7}};
        for (int[] d : open) {
            BlockPos p = new BlockPos(d[0], d[1], d[2]);
            air(level, p, "doorway", failures);
            air(level, p.above(), "doorway head", failures);
        }
        for (List<BlockPos> sealed : List.of(GATE, INNER_GATE, WEST_RETURN, EAST_RETURN, LOWER_RETURN, E1_EXIT, E2_EXIT)) {
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
        for (Box hall : List.of(E1, E2, E3)) {
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
        int pockets = 0;
        for (BlockPos p : seen) {
            if (p.getY() == 80 && level.getBlockState(p).isAir()) {
                pockets++;
            }
        }
        if (pockets < 9 * 3) {
            failures.add("maze has too few air pockets (" + pockets / 9 + ")");
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

    private static void checkTreasury(ServerLevel level, List<String> failures) {
        if (!level.getBlockState(PEDESTAL).is(Blocks.LODESTONE)) {
            failures.add("fragment pedestal missing");
        }
        for (BlockPos p : List.of(CHEST, CHEST_2)) {
            if (!(level.getBlockEntity(p) instanceof ChestBlockEntity chest) || chest.isEmpty()) {
                failures.add("treasure chest missing or empty at " + p);
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
        long courseSeed = data.courseSeed();
        long mazeSeed = data.mazeSeed();
        TitanBackTrials.resetTrials(level);
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
        for (List<BlockPos> sealed : List.of(GATE, E1_EXIT)) {
            for (BlockPos p : sealed) {
                if (!level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                    failures.add("reset did not reseal " + p);
                }
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
