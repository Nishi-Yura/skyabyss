package jp.revati.skyabyss.titan.end;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

// 巨神が人を地上へ降ろす場所 (TitanEnd の運び出し) の探し方。「ブロックの中・水の中・溶岩・火・木の上には降ろさない」を守る。
// 中心のまわりを、内側の半径 minR から外へ、一定の刻みで輪に沿って見る。読み込まれていないチャンクは、budget の数だけ読み込んでよい
// (運び出しの暗転の間に一度だけ使う)。見つからなければ null。
public final class SafeSpot {
    // 探す刻み (ブロック)
    private static final int STEP = 4;

    public enum Kind {
        // 固い地面で、足もと・頭上が空いていて、木の葉の屋根がない
        GROUND,
        // 水際: 地面で、6 ブロック以内に水がある (潮殻の浜)
        SHORE
    }

    private SafeSpot() {
    }

    // 中心 (cx, cz) から minR〜maxR の輪の中で、いちばん近い立てる地面。足の位置 (中心のブロックの上) を返す
    @Nullable
    public static Vec3 find(ServerLevel level, double cx, double cz, int minR, int maxR, Kind kind, int budget) {
        int[] left = {budget};
        for (int r = Math.max(0, minR); r <= maxR; r += STEP) {
            if (r == 0) {
                Vec3 v = judge(level, Mth.floor(cx), Mth.floor(cz), kind, left);
                if (v != null) {
                    return v;
                }
                continue;
            }
            // 輪の長さに合わせた数の点を、角度をずらしながら見る
            int n = Math.max(8, (int) Math.round(2 * Math.PI * r / STEP));
            double offset = (r * 0.37) % (2 * Math.PI);
            for (int i = 0; i < n; i++) {
                double a = offset + 2 * Math.PI * i / n;
                Vec3 v = judge(level, Mth.floor(cx + Math.cos(a) * r), Mth.floor(cz + Math.sin(a) * r), kind, left);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    // 柱 (x, z) の地面が降ろしてよい所なら、足の位置を返す。読み込みの budget が尽きていて読み込まれていなければ null
    @Nullable
    private static Vec3 judge(ServerLevel level, int x, int z, Kind kind, int[] left) {
        if (!loaded(level, x, z, left)) {
            return null;
        }
        return standable(level, x, z, kind);
    }

    private static boolean loaded(ServerLevel level, int x, int z, int[] left) {
        if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return true;
        }
        if (left[0] <= 0) {
            return false;
        }
        left[0]--;
        ChunkAccess chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        return chunk != null;
    }

    // 柱 (x, z) に立てるか (読み込まれている前提)。足の位置を返す
    @Nullable
    public static Vec3 standable(ServerLevel level, int x, int z, Kind kind) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int bare = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= level.getMinBuildHeight() + 1 || top != bare) {
            // 木の葉の屋根の下・上は避ける
            return null;
        }
        BlockPos feet = new BlockPos(x, top, z);
        BlockState ground = level.getBlockState(feet.below());
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (!at.getCollisionShape(level, feet).isEmpty() || !head.getCollisionShape(level, feet.above()).isEmpty()) {
            return null;
        }
        if (!at.getFluidState().isEmpty() || !head.getFluidState().isEmpty() || !ground.getFluidState().isEmpty()) {
            return null;
        }
        if (ground.getCollisionShape(level, feet.below()).isEmpty() || ground.is(BlockTags.LEAVES) || dangerous(ground) || dangerous(at)) {
            return null;
        }
        if (kind == Kind.SHORE && !waterNear(level, x, top - 1, z, 6)) {
            return null;
        }
        return new Vec3(x + 0.5, top, z + 0.5);
    }

    private static boolean waterNear(ServerLevel level, int x, int y, int z, int range) {
        for (int dx = -range; dx <= range; dx += 2) {
            for (int dz = -range; dz <= range; dz += 2) {
                int px = x + dx;
                int pz = z + dz;
                if (!level.getChunkSource().hasChunk(px >> 4, pz >> 4)) {
                    continue;
                }
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz);
                if (level.getFluidState(new BlockPos(px, h - 1, pz)).is(Fluids.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean dangerous(BlockState state) {
        return state.getFluidState().is(Fluids.LAVA) || state.getFluidState().is(Fluids.FLOWING_LAVA)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB);
    }
}
