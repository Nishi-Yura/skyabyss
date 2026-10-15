package jp.revati.skyabyss.titan.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

// 鎖で降ろす (乗せる) 場所を探す。center のまわりで、空が開けていて、足もとが危なくない所。
// 1. 木の葉の屋根がなく、足もとが固い地面  2. 水面  3. 木の上  の順に選ぶ。
// 読み込まれていないチャンクは調べない (同期の読み込みを起こさない)。見つからなければ center の上
public final class LandingFinder {
    private LandingFinder() {
    }

    // 地面の上の、立てる位置 (足の高さ) を返す
    public static BlockPos find(ServerLevel level, BlockPos center, int minRadius, int maxRadius) {
        BlockPos water = null;
        BlockPos canopy = null;
        for (int r = minRadius; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }
                    Spot spot = judge(level, x, z);
                    if (spot == Spot.GOOD) {
                        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    }
                    if (spot == Spot.WATER && water == null) {
                        water = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    }
                    if (spot == Spot.CANOPY && canopy == null) {
                        canopy = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    }
                }
            }
        }
        if (water != null) {
            return water;
        }
        if (canopy != null) {
            return canopy;
        }
        if (level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
            return new BlockPos(center.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ()), center.getZ());
        }
        return center.above();
    }

    enum Spot { GOOD, WATER, CANOPY, BAD }

    static Spot judge(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int bare = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= level.getMinBuildHeight() + 1) {
            return Spot.BAD;
        }
        BlockPos feet = new BlockPos(x, top, z);
        BlockState ground = level.getBlockState(feet.below());
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
            return Spot.BAD;
        }
        if (dangerous(ground) || dangerous(level.getBlockState(feet))) {
            return Spot.BAD;
        }
        if (ground.getFluidState().is(Fluids.WATER) || ground.getFluidState().is(Fluids.FLOWING_WATER)) {
            return Spot.WATER;
        }
        if (!ground.getFluidState().isEmpty()) {
            return Spot.BAD;
        }
        if (top != bare || ground.is(BlockTags.LEAVES)) {
            return Spot.CANOPY;
        }
        return Spot.GOOD;
    }

    static boolean dangerous(BlockState state) {
        return state.getFluidState().is(Fluids.LAVA) || state.getFluidState().is(Fluids.FLOWING_LAVA)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.WITHER_ROSE);
    }
}
