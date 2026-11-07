package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

// 巨神の体の口を閉じる継ぎ当て (2026-11-07 の声「雷と亀の巨神の、もともとの建築を消していい。完全に閉じちゃって」)。
// 体の形 (bird.bin・turtle.bin) に残る門 (雷翼の羽根の門の輪・潮殻の潮門の枠と光る面) を、まわりの体の面に沿う石で埋め、門の上を空ける。
// 部品 data/skyabyss/opening/seal_<背>.json は世界の座標のまま (原点 0)。空気も置く。作るのは tools/gen/world/build_back_seals.py
// (背を建てた直後の書き出し (tools/shots/back_seal_dump.txt) から、門の箱の列の面をまわりの列から結んで決める)
public final class BackSeal {
    private BackSeal() {
    }

    // 置いた数。部品がなければ 0
    public static int apply(ServerLevel level, String name) {
        Blueprint bp;
        try {
            bp = Blueprint.get("seal_" + name);
        } catch (RuntimeException e) {
            return 0;
        }
        int n = 0;
        for (Blueprint.Cell c : bp.cells) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (level.getBlockState(p) != c.state()) {
                level.setBlock(p, c.state(), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        // 継ぎ当ての縁 (区画の外 2 列まで) に一つだけ残ったくぼみを、隣の面の石で埋める (建築の監査の FLOOR_DIMPLE)
        int filled = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int x = bp.rx0 - 2; x <= bp.rx1 + 2; x++) {
                for (int z = bp.rz0 - 2; z <= bp.rz1 + 2; z++) {
                    int t = top(level, x, z);
                    if (t == Integer.MIN_VALUE) {
                        continue;
                    }
                    int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    boolean dimple = true;
                    for (int[] d : nb) {
                        int q = top(level, x + d[0], z + d[1]);
                        if (q == Integer.MIN_VALUE || q <= t) {
                            dimple = false;
                            break;
                        }
                    }
                    if (dimple) {
                        BlockPos up = new BlockPos(x, t + 1, z);
                        level.setBlock(up, level.getBlockState(new BlockPos(x + 1, t + 1, z)).isAir() ? level.getBlockState(new BlockPos(x, t, z))
                                : level.getBlockState(new BlockPos(x + 1, t + 1, z)), Block.UPDATE_CLIENTS);
                        filled++;
                    }
                }
            }
        }
        SkyAbyss.LOGGER.info("Sealed the old gate of the {}: {} blocks, {} dimples filled", name, n, filled);
        return n + filled;
    }

    // 列のいちばん上の当たりのあるブロックの高さ (なければ MIN_VALUE)。上から 40 段まで見る
    private static int top(ServerLevel level, int x, int z) {
        int start = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        for (int y = start; y > start - 40 && y > level.getMinBuildHeight(); y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
