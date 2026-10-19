package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;


import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// 闇の水路 (光海): 鼓動の間の床の穴から下りる、水没した迷路。通路は 3×3 の水で、明かりはほとんどない。
// 試練が元に戻るたびに、新しい種で迷路を掘り直す。入口のマスから、すぐに道が分かれる。
// 息継ぎのくぼみ (天井がくぼみ、泡の柱が空気へ押し上げる光る場所) は、どのマスからも 3 マス以内にある (MazePlan)。
// 水域は正しい道が灯の台を越えるたびに色が変わる (床・壁の石・くぼみの光)。
// 下へ引きずり込む流れ、水路の顎の巣、行き止まりのかすかな明かり。
// 出口は湧き上がる泡の柱で、上の小部屋へ押し上げられる。小部屋の石が歌うと、入口の通路へ戻る抜け道が開く
public final class MazeBuilder {
    // 入口から出口までの道が、少なくともこれだけのマスを通ること
    private static final int MIN_PATH_CELLS = 26;
    // 水路の顎のスポナーが同時に生かしておく数（近くの人には最大 2 匹まで）
    public static final int MAW_MAX_NEARBY = 2;

    private MazeBuilder() {
    }

    // 種から迷路を作る。道が短すぎる・入口が一本道になる種は避け、次の種を試す
    public static FloodedMaze maze(long seed) {
        for (long s = seed; ; s++) {
            FloodedMaze maze = FloodedMaze.generate(MAZE_W, MAZE_H, MAZE_START_I, MAZE_START_J, MAZE_EXIT_I, MAZE_EXIT_J, s);
            if (maze.solution().size() >= MIN_PATH_CELLS && maze.links(MAZE_START_I, MAZE_START_J) >= 2) {
                return maze;
            }
        }
    }

    // 正しい道の上の、息継ぎのくぼみを置くマス (マスの北西の隅の水の一段目)。道の順
    public static List<BlockPos> pockets(FloodedMaze maze) {
        List<BlockPos> result = new java.util.ArrayList<>();
        for (int[] c : MazePlan.compute(maze).pathPockets) {
            result.add(mazeCell(c[0], c[1]));
        }
        return result;
    }

    // 枝道も含めた、すべての息継ぎのくぼみ
    public static List<BlockPos> allPockets(FloodedMaze maze) {
        List<BlockPos> result = new java.util.ArrayList<>();
        for (int[] c : MazePlan.compute(maze).pockets) {
            result.add(mazeCell(c[0], c[1]));
        }
        return result;
    }

    // 迷路の中ほどの休み場にする息継ぎのくぼみ (正しい道の途中。マスの北西の隅の水の一段目)
    public static BlockPos midPocket(FloodedMaze maze) {
        List<BlockPos> pockets = pockets(maze);
        return pockets.get(pockets.size() / 2);
    }

    // 中ほどの休み場の復活位置: くぼみの北西の隅の足場の上 (足元は空気の層の一段目)
    public static BlockPos restSpot(BlockPos pocket) {
        return pocket.offset(0, 2, 0);
    }

    // 水域ごとの色 (0 = 入口の水域 … 3 = 出口の水域): 床・壁のところどころの石・くぼみの光
    static final BlockState[] ZONE_FLOOR = {Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
            Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState()};
    private static final BlockState[][] ZONE_ACCENT = {
            {Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState()},
            {Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState()},
            {Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.PURPUR_PILLAR.defaultBlockState()},
            {Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()}};
    static final BlockState[] ZONE_LIGHT = {Blocks.SEA_LANTERN.defaultBlockState(), Blocks.VERDANT_FROGLIGHT.defaultBlockState(),
            Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), Blocks.OCHRE_FROGLIGHT.defaultBlockState()};

    // 迷路の部分 (水と壁と仕掛け) だけを掘り直す。試練のリセットのたびに呼ばれる
    public static void buildMaze(BuildKit kit, long seed) {
        FloodedMaze maze = maze(seed);
        MazePlan plan = MazePlan.compute(maze);
        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        int x2 = MAZE_X0 + MAZE_W * (MAZE_CELL + 1);
        int z2 = MAZE_Z0 + MAZE_H * (MAZE_CELL + 1);
        kit.fill(MAZE_X0, MAZE_FLOOR, MAZE_Z0, x2, MAZE_FLOOR + 4, z2, wall);
        // 壁に、水域の色の石を混ぜる (一番上の段は部屋の床・天井の石なので混ぜない)
        for (int i = 0; i < 900; i++) {
            int x = MAZE_X0 + kit.random.nextInt(x2 - MAZE_X0 + 1);
            int z = MAZE_Z0 + kit.random.nextInt(z2 - MAZE_Z0 + 1);
            int y = MAZE_FLOOR + 1 + kit.random.nextInt(3);
            int[] cell = MazePlan.cellAt(x, z);
            BlockState[] accent = ZONE_ACCENT[plan.zoneOf(cell[0], cell[1])];
            kit.set(x, y, z, accent[kit.random.nextInt(10) < 6 ? 0 : 1]);
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int i = 0; i < MAZE_W; i++) {
            for (int j = 0; j < MAZE_H; j++) {
                BlockPos c = mazeCell(i, j);
                BlockState floor = ZONE_FLOOR[plan.zoneOf(i, j)];
                kit.fill(c.getX(), c.getY(), c.getZ(), c.getX() + MAZE_CELL - 1, c.getY() + 2, c.getZ() + MAZE_CELL - 1, water);
                kit.fill(c.getX(), MAZE_FLOOR, c.getZ(), c.getX() + MAZE_CELL - 1, MAZE_FLOOR, c.getZ() + MAZE_CELL - 1, floor);
                if (maze.linked(i, j, 1, 0)) {
                    kit.fill(c.getX() + MAZE_CELL, c.getY(), c.getZ(), c.getX() + MAZE_CELL, c.getY() + 2, c.getZ() + MAZE_CELL - 1, water);
                    kit.fill(c.getX() + MAZE_CELL, MAZE_FLOOR, c.getZ(), c.getX() + MAZE_CELL, MAZE_FLOOR, c.getZ() + MAZE_CELL - 1, floor);
                }
                if (maze.linked(i, j, 0, 1)) {
                    kit.fill(c.getX(), c.getY(), c.getZ() + MAZE_CELL, c.getX() + MAZE_CELL - 1, c.getY() + 2, c.getZ() + MAZE_CELL, water);
                    kit.fill(c.getX(), MAZE_FLOOR, c.getZ() + MAZE_CELL, c.getX() + MAZE_CELL - 1, MAZE_FLOOR, c.getZ() + MAZE_CELL, floor);
                }
            }
        }

        List<int[]> path = maze.solution();
        BlockPos midPocket = midPocket(maze);
        for (int[] c : plan.pockets) {
            // 息継ぎのくぼみ: マスの天井を一段くぼませ、上の二段を空気にする。
            // 通路の水は水源なので、空気と同じ高さでつながっていると、流れる水が空気へ広がって数秒で埋めてしまう。
            // そこで、このマスへの通路の一番上の段 (空気と同じ高さ) を壁でふさぎ、潜鐘のように空気を閉じ込める。
            // 下の二段は水源のまま。真ん中は魂の砂から湧く泡の柱で、乗ると空気の層まで押し上げられる (遠くからも泡で分かる)
            BlockPos p = mazeCell(c[0], c[1]);
            BlockState light = ZONE_LIGHT[plan.zoneOf(c[0], c[1])];
            sealPocketMouths(kit, maze, c, wall);
            kit.fill(p.getX(), p.getY() + 2, p.getZ(), p.getX() + 2, p.getY() + 3, p.getZ() + 2, BuildKit.AIR);
            // 暗い水の中から見つけられるよう、床の四隅と天井に水域の色の光をともし、四隅にシーピクルスを置く
            kit.set(p.getX() + 1, p.getY() - 1, p.getZ() + 1, Blocks.SOUL_SAND.defaultBlockState());
            for (int y = p.getY(); y <= p.getY() + 1; y++) {
                kit.set(p.getX() + 1, y, p.getZ() + 1, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
            }
            if (!kit.get(p.getX() + 1, p.getY() + 4, p.getZ() + 1).isAir()) {
                kit.set(p.getX() + 1, p.getY() + 4, p.getZ() + 1, light);
            }
            for (int[] corner : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
                kit.set(p.getX() + corner[0], p.getY() - 1, p.getZ() + corner[1], light);
                kit.set(p.getX() + corner[0], p.getY(), p.getZ() + corner[1], Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3));
            }
            if (p.equals(midPocket)) {
                // 中ほどの休み場: 四隅に乾いた足場 (水の二段目の高さ) を置く。真ん中は水のままで、そこから浮かび上がって上がれる。
                // 空気の層 (足元と頭の二段) は通路の口が壁でふさがれているので、魚は入れない。紫水晶が目印。
                // 隅の足場は水の一段目 (y = p.y) のシーピクルスの上に重ねる
                for (int[] corner : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
                    kit.set(p.getX() + corner[0], p.getY() + 1, p.getZ() + corner[1], BuildKit.POLISHED);
                }
                kit.set(p.getX() + 2, p.getY() + 2, p.getZ() + 2, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
        }
        // 下へ引きずり込む流れ (くぼみにつながる通路と、出口の手前の戸の通路には置かない: MazePlan が避ける)
        for (int k : plan.currents) {
            current(kit, path.get(k), path.get(k + 1));
        }

        // 行き止まり: 水路の顎の巣と、かすかなシーピクルスの明かりと、揺れるケルプ
        List<int[]> deadEnds = maze.deadEnds();
        for (int n = 0; n < deadEnds.size(); n++) {
            BlockPos p = mazeCell(deadEnds.get(n)[0], deadEnds.get(n)[1]).offset(1, 0, 1);
            if (n % 3 == 1) {
                kit.set(p, Blocks.SPAWNER.defaultBlockState());
                if (kit.level().getBlockEntity(p) instanceof SpawnerBlockEntity be) {
                    be.setEntityId(ModEntities.WATERWAY_MAW.get(), kit.random);
                    // 同時にいる数は 2 まで。入った瞬間に囲まれないように
                    CompoundTag tag = be.saveWithoutMetadata(kit.level().registryAccess());
                    tag.putShort("MaxNearbyEntities", (short) MAW_MAX_NEARBY);
                    be.getSpawner().load(kit.level(), p, tag);
                }
            } else if (n % 2 == 0) {
                kit.set(p.below(), Blocks.SAND.defaultBlockState());
                kit.set(p, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + n % 3));
            } else {
                kit.set(p.below(), Blocks.SAND.defaultBlockState());
                kit.set(p, Blocks.KELP_PLANT.defaultBlockState());
                kit.set(p.above(), Blocks.KELP.defaultBlockState());
            }
        }

        // 入口: 鼓動の間の床から、水面のすぐ上まで下りるはしご
        BlockPos start = mazeCell(MAZE_START_I, MAZE_START_J);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = MAZE_FLOOR + 4; y <= FLOOR; y++) {
            kit.set(start.getX() + 1, y, start.getZ(), ladder);
        }
        // 出口: 泡の柱が、上の小部屋へ押し上げる
        BlockPos exit = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J).offset(1, 0, 1);
        kit.set(exit.below(), Blocks.SOUL_SAND.defaultBlockState());
        for (int y = exit.getY(); y <= MAZE_EXIT_ROOM.floorY(); y++) {
            kit.set(exit.getX(), y, exit.getZ(), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
        }
        // 出口の小部屋の床を張り直す (迷路の天井と同じ高さなので、掘り直しで上書きされる)
        for (int x = MAZE_EXIT_ROOM.x1(); x <= MAZE_EXIT_ROOM.x2(); x++) {
            for (int z = MAZE_EXIT_ROOM.z1(); z <= MAZE_EXIT_ROOM.z2(); z++) {
                if (x != exit.getX() || z != exit.getZ()) {
                    kit.set(x, MAZE_EXIT_ROOM.floorY(), z, BuildKit.POLISHED);
                }
            }
        }
        // 入口の小部屋の床 (迷路の天井の石を張り直したあとに)
        buildEntryFloor(kit);
        // 灯の台・出口の手前の戸・近道の戸・中ほどの苗床 (docs/design/23)
        AnemoneLights.build(kit, maze);
    }

    // 入口の小部屋の床の飾り: 焚き火の下の彫刻石と、水への入口 (床の穴) の両脇の灯り。
    // 床は迷路の天井の石と同じ高さなので、迷路を掘り直すたびに張り直す
    private static void buildEntryFloor(BuildKit kit) {
        TitanBackLayout.Box er = MAZE_ENTRY_ROOM;
        BlockPos c = er.center();
        kit.set(c.getX(), er.floorY(), c.getZ(), BuildKit.CHISELED);
        BlockPos hatch = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 0, 0);
        kit.set(hatch.getX() - 1, er.floorY(), hatch.getZ(), Blocks.SEA_LANTERN.defaultBlockState());
        kit.set(hatch.getX() + 1, er.floorY(), hatch.getZ(), Blocks.SEA_LANTERN.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = c.getX() + dx * 2;
                int z = c.getZ() + dz * 2;
                if ((dx != 0 || dz != 0) && Math.abs(dx) + Math.abs(dz) <= 2) {
                    kit.set(x, er.floorY(), z, BuildKit.POLISHED);
                }
            }
        }
    }

    // 出口の泡の柱の根もと (出口のマスの真ん中、水の一段目)
    public static BlockPos exitBubble() {
        return mazeCell(MAZE_EXIT_I, MAZE_EXIT_J).offset(1, 0, 1);
    }

    // 迷路のまわりの決まった設備: 出口の小部屋と、入口の通路へ戻る抜け道。島を建てるときに一度だけ
    static void buildFixtures(BuildKit kit) {
        // 入口の小部屋: はしごを下りた先の、水の外の休み場。周りは壁で、水は入らない (床は迷路の天井)。はしごは buildMaze が通す
        TitanBackLayout.Box er = MAZE_ENTRY_ROOM;
        kit.fill(er.x1() - 1, er.floorY() + 1, er.z1() - 1, er.x2() + 1, er.ceilingY(), er.z2() + 1, BuildKit.TILES);
        kit.fill(er.x1(), er.floorY() + 1, er.z1(), er.x2(), er.ceilingY() - 1, er.z2(), BuildKit.AIR);
        kit.restPoint(er);
        // 広い部屋 (13×9): 壁の灯りを天井近くに。灯の数え石・苗床・灯の数の掲示板 (docs/design/23)
        kit.wallLamps(er, 4, er.ceilingY() - 1);
        AnemoneLights.buildEntry(kit);
        kit.room(MAZE_EXIT_ROOM, BuildKit.POLISHED, BuildKit.TILES);
        kit.lantern(MAZE_EXIT_ROOM.x2(), MAZE_EXIT_ROOM.floorY() + 1, MAZE_EXIT_ROOM.z2());
        kit.set(MAZE_EXIT_ROOM.x2(), MAZE_EXIT_ROOM.floorY() + 1, MAZE_EXIT_ROOM.z1(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        kit.room(new TitanBackLayout.Box(3, 10, -7, -7, FLOOR, 3));
        BlockState ladderEast = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = MAZE_EXIT_ROOM.floorY() + 1; y <= FLOOR; y++) {
            kit.set(MAZE_EXIT_ROOM.x1(), y, -7, ladderEast);
        }
        for (BlockPos p : LOWER_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        // 鼓動の間の床の入口を、黒い石の縁取りと灯りで目立たせる
        BlockPos hatch = mazeCell(MAZE_START_I, MAZE_START_J).offset(1, 0, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    kit.set(hatch.getX() + dx, FLOOR, hatch.getZ() + dz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    kit.set(hatch.getX() + dx, FLOOR + 1, hatch.getZ() + dz, BuildKit.AIR);
                }
            }
        }
        kit.set(hatch.getX(), FLOOR + 1, hatch.getZ(), BuildKit.AIR);
        kit.lantern(hatch.getX() - 2, FLOOR + 1, hatch.getZ());
        kit.lantern(hatch.getX() + 2, FLOOR + 1, hatch.getZ());
    }

    // 息継ぎのマス c へつながるすべての通路 (正しい道も枝道も) の、一番上の段を壁にする
    private static void sealPocketMouths(BuildKit kit, FloodedMaze maze, int[] c, BlockState wall) {
        BlockPos p = mazeCell(c[0], c[1]);
        int y = p.getY() + 2;
        for (int[] d : FloodedMaze.DIRS) {
            if (!maze.linked(c[0], c[1], d[0], d[1])) {
                continue;
            }
            for (int t = 0; t < MAZE_CELL; t++) {
                int x = d[0] != 0 ? p.getX() + (d[0] > 0 ? MAZE_CELL : -1) : p.getX() + t;
                int z = d[1] != 0 ? p.getZ() + (d[1] > 0 ? MAZE_CELL : -1) : p.getZ() + t;
                kit.set(x, y, z, wall);
            }
        }
    }

    // a から b へ向かう通路を、下へ引く泡の流れで満たす
    private static void current(BuildKit kit, int[] a, int[] b) {
        BlockPos ca = mazeCell(a[0], a[1]);
        int dx = b[0] - a[0];
        int dz = b[1] - a[1];
        for (int t = 0; t < MAZE_CELL; t++) {
            int x = dx != 0 ? ca.getX() + (dx > 0 ? MAZE_CELL : -1) : ca.getX() + t;
            int z = dz != 0 ? ca.getZ() + (dz > 0 ? MAZE_CELL : -1) : ca.getZ() + t;
            kit.set(x, MAZE_FLOOR, z, Blocks.MAGMA_BLOCK.defaultBlockState());
            for (int y = MAZE_FLOOR + 1; y <= MAZE_FLOOR + 3; y++) {
                kit.set(x, y, z, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true));
            }
        }
    }
}
