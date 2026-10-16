package jp.revati.skyabyss.titan.storm;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

// 雷翼の止まり木の探し方 (12 の 2.3節)。読み込まれているチャンクの高さマップだけを見る (同期の読み込みをしない)。
// 中心 (人) から rMin〜rMax ブロックの範囲を 8 ブロックおきに見て、一番高い地表を選ぶ。水の上と木の葉の上は除く
// (MOTION_BLOCKING_NO_LEAVES は葉を数えない)。平らな地面なら必ずどこかが選ばれる。
public final class StormPerch {
    public static final int STEP = 8;

    // 止まり木の点 (y は地表の高さ = 立つ所)
    public record Peak(double x, double y, double z) {
    }

    private StormPerch() {
    }

    // 地表の高さ (立つ所の y)。読み込まれていなければ NaN
    public static double surfaceAt(ServerLevel level, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    // 水の上でない、読み込まれた地表か
    static boolean dry(ServerLevel level, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return !level.getFluidState(new BlockPos(bx, top - 1, bz)).is(FluidTags.WATER)
                && !level.getFluidState(new BlockPos(bx, top, bz)).is(FluidTags.WATER);
    }

    // (cx, cz) から rMin〜rMax ブロックの中で、読み込まれた乾いた地表のうち一番高い点。なければ null
    @Nullable
    public static Peak find(ServerLevel level, double cx, double cz, double rMin, double rMax) {
        Peak best = null;
        int r = (int) Math.ceil(rMax);
        int x0 = Mth.floor((cx - r) / STEP) * STEP;
        int z0 = Mth.floor((cz - r) / STEP) * STEP;
        for (int x = x0; x <= cx + r; x += STEP) {
            for (int z = z0; z <= cz + r; z += STEP) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d < rMin || d > rMax || !dry(level, x + 0.5, z + 0.5)) {
                    continue;
                }
                double y = surfaceAt(level, x + 0.5, z + 0.5);
                if (best == null || y > best.y()) {
                    best = new Peak(x + 0.5, y, z + 0.5);
                }
            }
        }
        return best;
    }
}
