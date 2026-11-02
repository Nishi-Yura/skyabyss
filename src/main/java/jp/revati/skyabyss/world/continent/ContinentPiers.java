package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// 船着き場の桟橋を水の上まで延ばす (はじまりの島と内海の渡し船。部品の桟橋は短く、置き場が岸から離れると陸の上で終わっていた)。
// 部品の印 stand から ferry への向きに、板の道 (幅 3) を水の上へ 5 ブロック出るまで (最長 MAX) 延ばし、水の上は杭を海の底まで打つ。
// 陸の上が高ければ削る。延ばした先の点を新しい ferry の印にする (水が見つからなければ元のまま)
final class ContinentPiers {
    static final int MAX = 48;
    static final int OVER_WATER = 5;

    private ContinentPiers() {
    }

    static BlockPos extend(ServerLevel level, BlockPos ferry, BlockPos stand) {
        int dx = ferry.getX() - stand.getX();
        int dz = ferry.getZ() - stand.getZ();
        Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        Direction side = dir.getClockWise();
        int deck = ferry.getY() - 1;
        // 先に水があるかを見る
        int reach = -1;
        int wet = 0;
        for (int k = 1; k <= MAX; k++) {
            BlockPos c = ferry.relative(dir, k);
            if (water(level, c.getX(), c.getZ())) {
                if (++wet >= OVER_WATER) {
                    reach = k;
                    break;
                }
            }
        }
        if (reach < 0) {
            return ferry;
        }
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        for (int k = 1; k <= reach; k++) {
            BlockPos c = ferry.relative(dir, k).atY(deck);
            for (int s = -1; s <= 1; s++) {
                BlockPos p = c.relative(side, s);
                level.setBlock(p, plank, 2);
                for (int y = 1; y <= 3; y++) {
                    BlockPos a = p.above(y);
                    if (!level.getBlockState(a).isAir() && level.getFluidState(a).isEmpty()) {
                        level.setBlock(a, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // 下が水か空なら、杭 (三つおきの両端) か何もしない
                if (Math.abs(s) == 1 && k % 3 == 0) {
                    for (int y = deck - 1; y > level.getMinBuildHeight() + 1; y--) {
                        BlockPos q = new BlockPos(p.getX(), y, p.getZ());
                        BlockState b = level.getBlockState(q);
                        if (!b.isAir() && b.getFluidState().isEmpty()) {
                            break;
                        }
                        level.setBlock(q, post, 2);
                    }
                    level.setBlock(p.above(), fence, 2);
                }
            }
        }
        // 先の灯り (両端の柵の上)
        BlockPos end = ferry.relative(dir, reach).atY(deck);
        for (int s : new int[] {-1, 1}) {
            BlockPos p = end.relative(side, s).above();
            level.setBlock(p, fence, 2);
            level.setBlock(p.above(), Blocks.LANTERN.defaultBlockState(), 2);
        }
        return ferry.relative(dir, reach - 1);
    }

    static boolean water(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        return !level.getFluidState(new BlockPos(x, top, z)).isEmpty();
    }
}
