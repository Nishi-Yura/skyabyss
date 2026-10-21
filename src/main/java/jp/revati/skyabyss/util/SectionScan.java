package jp.revati.skyabyss.util;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

// 箱の中を一つずつ読む前に、重なるチャンクの区画 (16³) の「色の表」(パレット) を見て、探す物がどこにも無ければ読まずに済ませる。
// 答えは一つずつ読むのと同じ (表に無ければ、その区画のどこにも無い)。表にあれば、呼ぶ側はいつもの読み方をする
public final class SectionScan {
    private SectionScan() {
    }

    public static boolean mayContain(Level level, BlockPos min, BlockPos max, Predicate<BlockState> want) {
        int sx0 = SectionPos.blockToSectionCoord(min.getX());
        int sx1 = SectionPos.blockToSectionCoord(max.getX());
        int sz0 = SectionPos.blockToSectionCoord(min.getZ());
        int sz1 = SectionPos.blockToSectionCoord(max.getZ());
        int y0 = Math.max(min.getY(), level.getMinBuildHeight());
        int y1 = Math.min(max.getY(), level.getMaxBuildHeight() - 1);
        if (y0 > y1) {
            return false;
        }
        int sy0 = SectionPos.blockToSectionCoord(y0);
        int sy1 = SectionPos.blockToSectionCoord(y1);
        for (int sx = sx0; sx <= sx1; sx++) {
            for (int sz = sz0; sz <= sz1; sz++) {
                var chunk = level.getChunk(sx, sz);
                for (int sy = sy0; sy <= sy1; sy++) {
                    int idx = level.getSectionIndexFromSectionY(sy);
                    if (idx < 0 || idx >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection sec = chunk.getSection(idx);
                    if (!sec.hasOnlyAir() && sec.maybeHas(want)) {
                        return true;
                    }
                    if (sec.hasOnlyAir() && want.test(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
