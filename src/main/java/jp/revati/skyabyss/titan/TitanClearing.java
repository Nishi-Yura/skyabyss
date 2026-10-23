package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// 地面に休む巨神 (潮殻・燃えがらの巨神) が座るとき、体のなかに食い込む地形・木を取り除く (「ブロックにめり込まない」)。
// 体の柱 (TitanVolume.coreColumns) ごとに、体の底から頂までの高さにある固いブロックを消す (水・流体・岩盤・都や場所の中は触らない。
// 海の底を削ったところは水で埋める)。
// 体の頂より上に残った葉・雪も払う (宙に浮く葉を残さない)。
public final class TitanClearing {
    // 1 回で壊す上限 (重さの歯止め)
    public static final int MAX_BLOCKS = 120_000;

    private TitanClearing() {
    }

    // base = 体の底 (巨神の位置 y)。壊したブロック数を返す
    public static int carve(ServerLevel level, String volumeName, double x, double base, double z, float yaw) {
        TitanVolume volume = TitanVolume.get(volumeName);
        if (volume == null || !TitanGround.enabled) {
            return 0;
        }
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int removed = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (TitanVolume.Column c : volume.coreColumns()) {
            double wx = x + fx * c.fwd() + fz * c.side();
            double wz = z + fz * c.fwd() - fx * c.side();
            for (int dx = -1; dx <= 0; dx++) {
                for (int dz = -1; dz <= 0; dz++) {
                    int bx = Mth.floor(wx) + dx;
                    int bz = Mth.floor(wz) + dz;
                    if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
                        continue;
                    }
                    if (OpeningSites.siteAt(level.getServer(), new BlockPos(bx, 0, bz), 0).isPresent()) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                    int lo = Mth.floor(base + c.lo());
                    int hi = Math.min(Mth.ceil(base + c.hi()), top);
                    for (int y = hi; y >= lo; y--) {
                        p.set(bx, y, bz);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK) || s.getDestroySpeed(level, p) < 0) {
                            continue;
                        }
                        // 海の底 (海面より下で、真上が水) は水で埋める。空気にすると、近所の更新を出さないので海の底に空気の穴が残る
                        boolean wet = y < level.getSeaLevel() && level.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER);
                        level.setBlock(p, wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                        if (++removed >= MAX_BLOCKS) {
                            return removed;
                        }
                    }
                    for (int y = hi + 1; y <= top; y++) {
                        p.set(bx, y, bz);
                        BlockState s = level.getBlockState(p);
                        if (s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                        }
                    }
                }
            }
        }
        return removed;
    }
}
