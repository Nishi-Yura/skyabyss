package jp.revati.skyabyss.titan.storm;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.titan.TitanGround;
import jp.revati.skyabyss.titan.solid.TitanVolume;
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

    // 止まり木の候補を高い順に何点まで体の大きさで確かめるか。体が地形にめり込まない (要る持ち上がりが小さい) 最初の点を選ぶ
    static final int FOOTPRINT_CANDIDATES = 12;
    static final double OK_RISE = 4.0;

    // (cx, cz) から rMin〜rMax ブロックの中で、読み込まれた乾いた地表のうち一番高い点 (ただし体がめり込まない所を優先)。なければ null。
    // 戻りの y は雷翼の位置の y (体の底): 足もとの地表か、翼や体がまわりの丘・木に触れないよう持ち上げた高さのうち高い方。
    // 向きは降りてから決まるので、8 方向 (45 度ごと) のどれでもめり込まない高さにする
    @Nullable
    public static Peak find(ServerLevel level, double cx, double cz, double rMin, double rMax) {
        java.util.List<Peak> candidates = new java.util.ArrayList<>();
        int r = (int) Math.ceil(rMax);
        int x0 = Mth.floor((cx - r) / STEP) * STEP;
        int z0 = Mth.floor((cz - r) / STEP) * STEP;
        for (int x = x0; x <= cx + r; x += STEP) {
            for (int z = z0; z <= cz + r; z += STEP) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d < rMin || d > rMax || !dry(level, x + 0.5, z + 0.5) || jp.revati.skyabyss.titan.TitanAvoid.inside(level, x + 0.5, z + 0.5, 0)) {
                    continue;
                }
                candidates.add(new Peak(x + 0.5, surfaceAt(level, x + 0.5, z + 0.5), z + 0.5));
            }
        }
        candidates.sort((a, b) -> Double.compare(b.y(), a.y()));
        TitanVolume volume = TitanVolume.get("thunder_wing");
        Peak best = null;
        double bestRise = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(FOOTPRINT_CANDIDATES, candidates.size()); i++) {
            Peak c = candidates.get(i);
            double need = TitanGround.requiredAnyYaw(level, TitanGround.Surface.TOP, volume, c.x(), c.z(), 1);
            double rise = Double.isFinite(need) ? Math.max(0, need - c.y()) : 0;
            if (rise < bestRise) {
                bestRise = rise;
                best = new Peak(c.x(), c.y() + rise, c.z());
            }
            if (rise <= OK_RISE) {
                break;
            }
        }
        return best;
    }
}
