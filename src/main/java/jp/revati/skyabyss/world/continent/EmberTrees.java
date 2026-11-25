package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 灰の荒野 (南の荒れ地。荒野・林の荒野) の木 (2026-11-25。ラフ 3 案から A 煤垂れの樹をユーザーが選んだ。
// 岩鯨・鐘の野・潮の浜・嵐の高地と同じ「弓なりの枝・垂れる房」の言葉。橙のブロックは使わない)。
//   煤垂れの樹: ねじれた暗い幹から、枝が傘の骨のように弓なりに広がって地面近くまで垂れる。枝の背から細い房が煙のように下がる。
//   大きさは三つ: 若木 (枝が少なく低い)、ふつう、古木 (根を張り、幹の途中から二つ目の小さな傘が横へ出る)。同じ形を並べない。
final class EmberTrees {
    static final BlockState DARK_OAK = WhaleTrees.DARK_OAK;
    static final Block SOOT = Blocks.DARK_OAK_WOOD;

    // size: 0.75 = 若木、1.0 = ふつう、1.3 = 古木
    static void sootWeeper(ContinentTrees.Grower g, BlockPos base, RandomSource r, double size) {
        boolean old = size >= 1.2;
        int h = (int) Math.round((8 + r.nextInt(3)) * size);
        double ph = r.nextFloat() * 6;
        double sx = 0;
        double sz = 0;
        int tw = size >= 0.9 ? 2 : 1;
        // ねじれた幹
        for (double t = -1; t <= h; t += 0.5) {
            double f = Math.max(0, t) / h;
            sx = 1.6 * size * Math.sin(f * 4.0 + ph) * f;
            sz = 1.6 * size * Math.cos(f * 3.2 + ph) * f;
            int w = t < h * 0.5 ? tw : 1;
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    g.log(WhaleTrees.at(base.getX() + dx + sx, base.getY() + t, base.getZ() + dz + sz), SOOT, Direction.Axis.Y);
                }
            }
        }
        // 古木の根 (弓なりに地面へ潜る)
        if (old) {
            for (int k = 0; k < 5; k++) {
                double a = k * 2 * Math.PI / 5 + (r.nextFloat() - 0.5) * 0.6;
                for (double i = 1; i <= 3 + r.nextInt(2); i += 0.5) {
                    g.log(WhaleTrees.at(base.getX() + 1 + Math.cos(a) * (i + 0.5), base.getY() + 1.5 - i * 0.6, base.getZ() + 1 + Math.sin(a) * (i + 0.5)),
                            SOOT, Direction.Axis.Y);
                }
            }
        }
        double cx = base.getX() + 0.5 + sx;
        double cz = base.getZ() + 0.5 + sz;
        int top = base.getY() + h;
        weep(g, r, cx, top, cz, size, h - 2, size < 0.9 ? 5 + r.nextInt(2) : 7 + r.nextInt(3));
        // 古木: 幹の途中から横へ弓なりの枝、先に二つ目の小さな傘
        if (old) {
            double a = r.nextFloat() * 2 * Math.PI;
            double f = 0.5;
            double bx = base.getX() + 0.5 + 1.6 * size * Math.sin(f * 4.0 + ph) * f;
            double bz = base.getZ() + 0.5 + 1.6 * size * Math.cos(f * 3.2 + ph) * f;
            int by = base.getY() + (int) (h * f);
            double reach = 5.5 + r.nextFloat() * 1.5;
            double ex = bx;
            double ey = by;
            double ez = bz;
            for (double t = 0; t <= 1; t += 1 / (reach * 2.4)) {
                ex = bx + Math.cos(a) * reach * t;
                ez = bz + Math.sin(a) * reach * t;
                ey = by + 3.0 * Math.sin(t * Math.PI / 2);
                g.log(WhaleTrees.at(ex, ey, ez), SOOT, t > 0.8 ? Direction.Axis.Y : WhaleTrees.axisOf(Math.cos(a), Math.sin(a)));
            }
            weep(g, r, ex, (int) Math.round(ey), ez, 0.6, (int) (ey - base.getY()) - 2, 5);
        }
    }

    // 傘: 頂の小さな葉の房と、弓なりに上がってから地面近くまで垂れる枝。枝の背から煙のような房
    static void weep(ContinentTrees.Grower g, RandomSource r, double cx, int top, double cz, double size, int fall, int n) {
        g.blob(WhaleTrees.at(cx, top + 1, cz), 2.2 * size + 0.4, 1.4, DARK_OAK);
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + (r.nextFloat() - 0.5) * 0.4;
            double reach = (7.0 + r.nextFloat() * 2.5) * size;
            double up = (2.0 + r.nextFloat()) * size;
            double down = Math.max(2, fall) * (0.55 + r.nextFloat() * 0.2);
            double px;
            double py;
            double pz;
            for (double t = 0; t <= 1; t += 1 / ((reach + down) * 2.4)) {
                px = cx + Math.cos(a) * reach * t;
                pz = cz + Math.sin(a) * reach * t;
                py = top + up * Math.sin(Math.min(t / 0.35, 1) * Math.PI / 2) - (t > 0.35 ? (up + down) * Math.pow((t - 0.35) / 0.65, 1.8) : 0);
                g.log(WhaleTrees.at(px, py, pz), SOOT, t > 0.75 ? Direction.Axis.Y : WhaleTrees.axisOf(Math.cos(a), Math.sin(a)));
                if (t > 0.15 && t < 0.8 && r.nextFloat() < 0.5f) {
                    BlockPos p = WhaleTrees.at(px + (r.nextFloat() - 0.5) * 2, py + 1, pz + (r.nextFloat() - 0.5) * 2);
                    g.leaf(p, DARK_OAK);
                    int len = 2 + r.nextInt(4);
                    for (int q = 1; q <= len; q++) {
                        g.leaf(p.below(q), DARK_OAK);
                    }
                }
            }
        }
    }

    private EmberTrees() {
    }
}
