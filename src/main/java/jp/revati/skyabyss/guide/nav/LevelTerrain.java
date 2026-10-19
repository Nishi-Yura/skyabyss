package jp.revati.skyabyss.guide.nav;

import java.util.HashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

// 本物の世界の地形 (読み込み済みのチャンクだけ。読み込みは起こさない)。一回の探索のあいだ使う (チャンクの引きを覚える)
public final class LevelTerrain implements PathFinder.Terrain {
    private final Level level;
    private final HashMap<Long, LevelChunk> chunks = new HashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public LevelTerrain(Level level) {
        this.level = level;
    }

    @Override
    public PathFinder.Cell cell(int x, int y, int z) {
        if (y < level.getMinBuildHeight()) {
            return PathFinder.Cell.SOLID;
        }
        if (y >= level.getMaxBuildHeight()) {
            return PathFinder.Cell.AIR;
        }
        long ck = ChunkPos.asLong(x >> 4, z >> 4);
        LevelChunk chunk = chunks.get(ck);
        if (chunk == null) {
            if (chunks.containsKey(ck)) {
                return PathFinder.Cell.UNLOADED;
            }
            chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            chunks.put(ck, chunk);
            if (chunk == null) {
                return PathFinder.Cell.UNLOADED;
            }
        }
        pos.set(x, y, z);
        return classify(chunk.getBlockState(pos), level, pos);
    }

    static PathFinder.Cell classify(BlockState s, Level level, BlockPos p) {
        if (s.isAir()) {
            return PathFinder.Cell.AIR;
        }
        if (s.getFluidState().is(FluidTags.LAVA)) {
            return PathFinder.Cell.HAZARD;
        }
        if (s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.CACTUS) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.COBWEB) || s.is(Blocks.LAVA_CAULDRON) || s.is(Blocks.BAMBOO_SAPLING)
                || s.is(Blocks.POINTED_DRIPSTONE)) {
            return PathFinder.Cell.HAZARD;
        }
        boolean empty = s.getCollisionShape(level, p).isEmpty();
        if (s.getFluidState().is(FluidTags.WATER) && empty) {
            return PathFinder.Cell.WATER;
        }
        // 扉・柵の門は開けて通れる
        if (s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES)) {
            return PathFinder.Cell.AIR;
        }
        return empty ? PathFinder.Cell.AIR : PathFinder.Cell.SOLID;
    }
}
