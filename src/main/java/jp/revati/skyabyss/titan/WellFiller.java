package jp.revati.skyabyss.titan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// 巨神の体 (モデルから置いたブロックの形) の地表にできた、小さな縦穴 (まわりが壁で、上だけ空いている。1〜4 マス) を埋める。
// 落ちると自力では出られない (詰み監査 titan/audit/BackReach で見つかった。docs/design/08 の「詰み監査」)。縁の高さまで、まわりと同じ石で埋める。
// 底が固い所だけ (光の井戸・塔の窓のように掘った穴の上は触らない)。頭の上が石の低いすきま (1 マスの高さ) も穴の一部として埋める
public final class WellFiller {
    private WellFiller() {
    }

    // top(x, z) = その柱の体のいちばん上のブロックの y (無ければ Integer.MIN_VALUE)。minTop より低い柱は飛ばす。埋めたブロックの数を返す
    public static int fill(ServerLevel level, int x1, int x2, int z1, int z2, IntBinaryOperator top, int minTop) {
        int filled = 0;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int t = top.applyAsInt(x, z);
                if (t == Integer.MIN_VALUE || t < minTop) {
                    continue;
                }
                for (int y = t + 1; y <= t + 10; y++) {
                    List<BlockPos> comp = layer(level, new BlockPos(x, y, z));
                    if (comp == null) {
                        break;
                    }
                    BlockState wall = null;
                    for (BlockPos c : comp) {
                        for (Direction d : Direction.Plane.HORIZONTAL) {
                            BlockPos nb = c.relative(d);
                            if (!comp.contains(nb) && solid(level, nb)) {
                                wall = level.getBlockState(nb);
                            }
                        }
                    }
                    if (wall == null) {
                        break;
                    }
                    for (BlockPos c : comp) {
                        level.setBlock(c, wall, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        filled++;
                    }
                }
            }
        }
        if (filled > 0) {
            SkyAbyss.LOGGER.info("{}: filled {} blocks of small wells in the body surface", level.dimension().location(), filled);
        }
        return filled;
    }

    private static boolean solid(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.getCollisionShape(level, p).isEmpty() && s.isSolidRender(level, p);
    }

    // p の高さで、まわりが全部壁で、底が固い、4 マス以下の空気のかたまり (縦穴の一段)。そうでなければ null
    private static List<BlockPos> layer(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).isAir()) {
            return null;
        }
        List<BlockPos> comp = new ArrayList<>();
        List<BlockPos> crawl = new ArrayList<>();
        comp.add(p);
        for (int i = 0; i < comp.size(); i++) {
            BlockPos c = comp.get(i);
            if (!solid(level, c.below())) {
                return null;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos nb = c.relative(d);
                if (comp.contains(nb) || crawl.contains(nb)) {
                    continue;
                }
                if (level.getBlockState(nb).isAir() && solid(level, nb.above())) {
                    crawl.add(nb);
                } else if (level.getBlockState(nb).isAir()) {
                    comp.add(nb);
                    if (comp.size() > 4) {
                        return null;
                    }
                } else if (!solid(level, nb)) {
                    return null;
                }
            }
        }
        comp.addAll(crawl);
        return comp;
    }
}
