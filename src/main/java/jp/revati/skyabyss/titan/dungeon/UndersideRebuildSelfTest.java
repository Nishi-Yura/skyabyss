package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の下の休み場 (入口の小部屋 2 つ・迷路の中ほどの足場) が 5×5 以上の歩ける床・頭上・灯り・休み場の遺物を持つこと (版 27) と、
// 版を上げる建て直しで前の配置の残りが無いこと。建ててある背 (titan back (build)) の後に流す。段の名前は "titan back (rebuild)"
final class UndersideRebuildSelfTest {
    private UndersideRebuildSelfTest() {
    }

    private static final int MIN_SIDE = 5;

    // 床の歩ける面 (足元と頭の二段が空いている) の数と広がりを数える。hearth / lit は床の上 height 段に遺物・光る物があるか
    private static void checkRoom(ServerLevel back, String name, int x1, int x2, int z1, int z2, int floorY, int height, int minCells, List<String> f) {
        int walk = 0;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean hearth = false;
        boolean lit = false;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                BlockPos floor = new BlockPos(x, floorY, z);
                BlockPos feet = floor.above();
                boolean ok = !back.getBlockState(floor).getCollisionShape(back, floor).isEmpty()
                        && back.getBlockState(feet).getCollisionShape(back, feet).isEmpty()
                        && back.getBlockState(feet.above()).getCollisionShape(back, feet.above()).isEmpty()
                        && back.getFluidState(feet).isEmpty() && back.getFluidState(feet.above()).isEmpty()
                        && !back.getBlockState(feet).is(Blocks.LADDER);
                if (ok) {
                    walk++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
                for (int y = floorY + 1; y <= floorY + height; y++) {
                    BlockState s = back.getBlockState(new BlockPos(x, y, z));
                    hearth |= s.is(ModBlocks.REST_HEARTH.get());
                    lit |= s.getLightEmission() > 0;
                }
            }
        }
        if (walk < minCells || maxX - minX + 1 < MIN_SIDE || maxZ - minZ + 1 < MIN_SIDE) {
            f.add(name + " is smaller than 5x5 of walkable floor (" + walk + " cells, " + (maxX - minX + 1) + "x" + (maxZ - minZ + 1) + ")");
        }
        if (!hearth) {
            f.add(name + " has no rest hearth relic");
        }
        if (!lit) {
            f.add(name + " is not lit");
        }
    }

    static void checkRestSpots(ServerLevel back, List<String> f) {
        if (MAZE_CELL < MIN_SIDE) {
            f.add("maze cell is smaller than a rest room");
        }
        List<Box> rooms = List.of(MAZE_ENTRY_ROOM, MAZE_ENTRY_ROOM2);
        for (int i = 0; i < rooms.size(); i++) {
            Box b = rooms.get(i);
            checkRoom(back, "entry room " + (i + 1), b.x1(), b.x2(), b.z1(), b.z2(), b.floorY(), b.height(), MIN_SIDE * MIN_SIDE, f);
        }
        // 迷路の中ほどの休み場: くぼみの 5×5 の足場 (水の二段目の高さ) の上
        var maze = MazeBuilder.maze(TitanBackData.get(back).mazeSeed());
        if (!MazeBuilder.pockets(maze).isEmpty()) {
            BlockPos p = MazeBuilder.midPocket(maze);
            checkRoom(back, "mid rest pocket", p.getX(), p.getX() + MAZE_CELL - 1, p.getZ(), p.getZ() + MAZE_CELL - 1, p.getY() + 1, 2, 16, f);
        }
    }

    // 前の版 (N-1) で建てた島に、前の配置の残りの目印 (金のブロック) を置き、版 N で建て直して、残りが消えること
    static void checkRebuild(ServerLevel back, List<String> f) {
        TitanBackData data = TitanBackData.get(back);
        BlockState old = Blocks.GOLD_BLOCK.defaultBlockState();
        List<BlockPos> stale = new ArrayList<>();
        // 古い 3×3×3 の入口の小部屋 (版 11 の位置) の殻、鯨の外周のはし・体の型の上下の外に置いた前の版の残り
        for (int x = 3; x <= 5; x++) {
            for (int z = -25; z <= -23; z++) {
                stale.add(new BlockPos(x, 81, z));
                stale.add(new BlockPos(x, 83, z));
            }
        }
        stale.add(new BlockPos(RX, FLOOR + 5, 0));
        stale.add(new BlockPos(0, 230, 0));
        stale.add(new BlockPos(0, 40, -RZ));
        stale.add(new BlockPos(10, 30, 20));
        stale.add(new BlockPos(MAZE_ENTRY_ROOM.x1() + 1, MAZE_ENTRY_ROOM.floorY() + 2, MAZE_ENTRY_ROOM.z1() + 1));
        for (BlockPos p : stale) {
            back.setBlock(p, old, 2);
        }
        data.markBuilt(LAYOUT_VERSION - 1);
        if (!data.needsBuild()) {
            f.add("a layout version N-1 world does not ask for a rebuild");
        }
        TitanBackBuilder.build(back, data);
        TitanBackTrials.installGates(back);
        if (data.needsBuild() || data.layoutVersion() != LAYOUT_VERSION) {
            f.add("the rebuild did not record layout version " + LAYOUT_VERSION);
        }
        int left = 0;
        BlockPos first = null;
        for (BlockPos p : stale) {
            if (back.getBlockState(p).is(Blocks.GOLD_BLOCK)) {
                left++;
                first = first == null ? p : first;
            }
        }
        if (left > 0) {
            f.add("the rebuild left " + left + " blocks of the old layout (first at " + first + ")");
        }
        checkRestSpots(back, f);
    }

    static List<String> run(ServerLevel back) {
        List<String> f = new ArrayList<>();
        checkRestSpots(back, f);
        checkRebuild(back, f);
        return f;
    }
}
