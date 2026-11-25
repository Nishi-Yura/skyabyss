package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 外れ (大陸の縁の帯。草地・森・白樺の森。外れの集落がある) の木 (2026-11-25。ラフ 3 案から B 雲玉の樫をユーザーが選んだ。
// ほかの土地と同じ「弓なりの枝・垂れる房」の言葉。橙のブロックは使わない)。
//   雲玉の樫: 太く短い幹から枝が横へ弓なりに伸び、枝先ごとに丸い雲のような葉の玉 (小さな玉を寄せる)。玉の下から短い房が垂れる。
//   大きさは三つ: 若木 (枝が少ない)、ふつう、古木 (根を張り、枝が多く、幹の途中から低い玉が一つ出る)。同じ形を並べない。
final class OutskirtsTrees {
    static final BlockState OAK = WhaleTrees.OAK;

    // size: 0.75 = 若木、1.0 = ふつう、1.3 = 古木
    static void cloudOak(ContinentTrees.Grower g, BlockPos base, RandomSource r, double size) {
        boolean old = size >= 1.2;
        int h = (int) Math.round((8 + r.nextInt(2)) * size);
        g.alt(Blocks.AZALEA_LEAVES, 0.3f);
        // 太い幹 (根もとは太い)
        for (int t = -1; t <= h; t++) {
            double rad = (t < 2 ? 1.7 : 1.2) * size + 0.2;
            int ir = (int) Math.ceil(rad);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    if (dx * dx + dz * dz <= rad * rad) {
                        g.log(base.offset(dx, t, dz), Blocks.OAK_WOOD, Direction.Axis.Y);
                    }
                }
            }
        }
        // 古木の根 (弓なりに地面へ潜る)
        if (old) {
            for (int k = 0; k < 5; k++) {
                double a = k * 2 * Math.PI / 5 + (r.nextFloat() - 0.5) * 0.6;
                for (double i = 1.5; i <= 4 + r.nextInt(2); i += 0.5) {
                    g.log(WhaleTrees.at(base.getX() + 0.5 + Math.cos(a) * (i + 0.5), base.getY() + 1.5 - (i - 1.5) * 0.6, base.getZ() + 0.5 + Math.sin(a) * (i + 0.5)),
                            Blocks.OAK_WOOD, Direction.Axis.Y);
                }
            }
        }
        double cx = base.getX() + 0.5;
        double cz = base.getZ() + 0.5;
        int top = base.getY() + h;
        ball(g, r, cx, top + 3, cz, 3.2 * size + 0.4);
        int n = size < 0.9 ? 4 : old ? 6 + r.nextInt(2) : 5 + r.nextInt(2);
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + (r.nextFloat() - 0.5) * 0.5;
            branch(g, r, cx, top - 2 - r.nextInt(4), cz, a, (8.0 + r.nextFloat() * 3.0) * size, (3.0 + r.nextFloat() * 3.0) * size,
                    (2.4 + r.nextFloat() * 0.8) * size + 0.3);
        }
        // 古木: 幹の途中から低い枝が一本、先に玉
        if (old) {
            branch(g, r, cx, base.getY() + (int) (h * 0.45), cz, r.nextFloat() * 2 * Math.PI, 7.5, 2.5, 2.6);
        }
    }

    // 弓なりの枝 (外へ出ながら上がる) と、先の雲の玉
    static void branch(ContinentTrees.Grower g, RandomSource r, double cx, int y0, double cz, double a, double reach, double rise, double rad) {
        double px = cx;
        double py = y0;
        double pz = cz;
        for (double t = 0; t <= 1; t += 1 / (reach * 2.4)) {
            px = cx + Math.cos(a) * reach * t;
            pz = cz + Math.sin(a) * reach * t;
            py = y0 + rise * Math.sin(t * Math.PI * 0.75);
            g.log(WhaleTrees.at(px, py, pz), Blocks.OAK_WOOD, WhaleTrees.axisOf(Math.cos(a), Math.sin(a)));
        }
        ball(g, r, px, (int) Math.round(py) + 2, pz, rad);
    }

    // 丸い雲の玉 (まわりに小さな玉を二つ三つ寄せる)。下から短い房
    static void ball(ContinentTrees.Grower g, RandomSource r, double x, int y, double z, double rad) {
        g.blob(WhaleTrees.at(x, y, z), rad, rad * 0.8, OAK);
        for (int k = 0; k < 2 + r.nextInt(2); k++) {
            double a = r.nextFloat() * 2 * Math.PI;
            g.blob(WhaleTrees.at(x + Math.cos(a) * rad * 0.8, y - 1 + r.nextInt(2), z + Math.sin(a) * rad * 0.8), rad * 0.55, rad * 0.5, OAK);
        }
        int ir = (int) Math.ceil(rad);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                if (dx * dx + dz * dz < rad * rad * 0.7 && r.nextFloat() < 0.15f) {
                    BlockPos p = WhaleTrees.at(x + dx, y, z + dz);
                    int s = 0;
                    while (s < ir + 2 && !g.free(p.below(s))) {
                        s++;
                    }
                    for (int i = 0; i < 1 + r.nextInt(3); i++) {
                        g.leaf(p.below(s + i), OAK);
                    }
                }
            }
        }
    }

    private OutskirtsTrees() {
    }
}
