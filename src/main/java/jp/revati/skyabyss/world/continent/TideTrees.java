package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 潮の浜 (東の大陸。サバンナ・湿地) の木 (2026-11-25。ラフ 3 案から B 汐傘の樹をユーザーが選んだ。岩鯨・鐘の野と同じ「弓なりの枝・垂れる房」の言葉)。
//   汐傘の樹: 細く高い灰色の幹 (ゆるい S 字) の上に、弓なりの骨が透けて見える薄い丸屋根の傘 (橙のブロックは使わない。ユーザーの指示)。
//   傘の縁から長い房がすだれのように垂れる (長さは波打つ)。
//   大きさは三つ: 若木 (小さな傘)、ふつう、古木 (幹が太く根を張り、横枝の先に低い二つ目の傘)。同じ形を並べない。
final class TideTrees {
    static final BlockState ACACIA = WhaleTrees.leaf(Blocks.ACACIA_LEAVES);
    static final Block BARK = Blocks.ACACIA_WOOD;

    // size: 0.7 = 若木、1.0 = ふつう、1.3 = 古木
    static void parasol(ContinentTrees.Grower g, BlockPos base, RandomSource r, double size) {
        boolean old = size >= 1.2;
        int h = (int) Math.round((10 + r.nextInt(3)) * size);
        int tw = size >= 1.0 ? 2 : 1;
        g.alt(Blocks.AZALEA_LEAVES, 0.3f);
        double ph = r.nextFloat() * 6;
        double sx = 0;
        double sz = 0;
        // 幹 (ゆるい S 字。下の半分は太い)
        for (double t = -1; t <= h; t += 0.5) {
            double f = Math.max(0, t) / h;
            sx = 1.4 * size * Math.sin(f * Math.PI * 1.2 + ph) * f;
            sz = 1.0 * size * Math.cos(f * Math.PI * 1.1 + ph) * f;
            int w = t < h * 0.45 ? tw : 1;
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    g.log(WhaleTrees.at(base.getX() + dx + sx, base.getY() + t, base.getZ() + dz + sz), BARK, Direction.Axis.Y);
                }
            }
        }
        // 古木の根 (弓なりに地面へ潜る)
        if (old) {
            for (int k = 0; k < 5; k++) {
                double a = k * 2 * Math.PI / 5 + (r.nextFloat() - 0.5) * 0.6;
                int len = 3 + r.nextInt(2);
                for (double i = 1; i <= len; i += 0.5) {
                    g.log(WhaleTrees.at(base.getX() + 1 + Math.cos(a) * (i + 0.5), base.getY() + 1.5 - i * 0.6, base.getZ() + 1 + Math.sin(a) * (i + 0.5)),
                            BARK, Direction.Axis.Y);
                }
            }
        }
        double cx = base.getX() + 0.5 + sx;
        double cz = base.getZ() + 0.5 + sz;
        int top = base.getY() + h;
        // 古木: 幹の中ほどから横枝が弓なりに出て、先に低い二つ目の傘
        if (old) {
            double a = r.nextFloat() * 2 * Math.PI;
            double f = 0.55;
            double bx = base.getX() + 0.5 + 1.4 * size * Math.sin(f * Math.PI * 1.2 + ph) * f;
            double bz = base.getZ() + 0.5 + 1.0 * size * Math.cos(f * Math.PI * 1.1 + ph) * f;
            int by = base.getY() + (int) (h * f);
            double reach = 7 + r.nextFloat() * 2;
            double rise = 3.5;
            double ex = bx;
            double ey = by;
            double ez = bz;
            for (double t = 0; t <= 1; t += 1 / (reach * 2.2)) {
                ex = bx + Math.cos(a) * reach * t;
                ez = bz + Math.sin(a) * reach * t;
                ey = by + rise * Math.sin(t * Math.PI / 2);
                g.log(WhaleTrees.at(ex, ey, ez), BARK, t > 0.85 ? Direction.Axis.Y : WhaleTrees.axisOf(Math.cos(a), Math.sin(a)));
            }
            cap(g, r, ex, (int) Math.round(ey) + 1, ez, 4.5 + r.nextFloat(), 1.6, 0.8);
        }
        cap(g, r, cx, top, cz, (8.0 + r.nextFloat() * 1.5) * size, 3.0 * size, size);
    }

    // 傘: 弓なりの骨 + 薄い丸屋根 + 縁の房。rad = 傘の半径、dome = 真ん中の高さ
    static void cap(ContinentTrees.Grower g, RandomSource r, double cx, int top, double cz, double rad, double dome, double size) {
        // 骨: 真ん中から弓なりに外へ下がる (屋根のすぐ下。縁で屋根の外に少し出る)
        int n = Math.max(6, (int) (rad * 1.2)) + r.nextInt(2);
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + (r.nextFloat() - 0.5) * 0.3;
            int steps = (int) (rad * 2.4);
            for (int i = 0; i <= steps; i++) {
                double d = i / (double) steps;
                double y = top + dome * (1 - d * d) - 1;
                g.log(WhaleTrees.at(cx + Math.cos(a) * rad * d, y, cz + Math.sin(a) * rad * d), BARK,
                        d < 0.25 ? Direction.Axis.Y : WhaleTrees.axisOf(Math.cos(a), Math.sin(a)));
            }
        }
        // 屋根: 二枚の薄い丸屋根 (真ん中だけ三枚)
        int ic = (int) Math.ceil(rad);
        for (int dx = -ic; dx <= ic; dx++) {
            for (int dz = -ic; dz <= ic; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / rad;
                if (d > 1 || d > 0.93 && r.nextFloat() < 0.3f) {
                    continue;
                }
                BlockPos p = WhaleTrees.at(cx + dx, Math.round(top + dome * (1 - d * d)), cz + dz);
                g.leaf(p, ACACIA);
                g.leaf(p.below(), ACACIA);
                if (d < 0.4) {
                    g.leaf(p.above(), ACACIA);
                }
            }
        }
        // 縁の房: すだれ。長さは縁をまわって波打つ (長い所と短い所)
        int m = (int) (rad * 2 * Math.PI / 1.5);
        double wave = r.nextFloat() * 6;
        int waves = 2 + r.nextInt(2);
        for (int k = 0; k < m; k++) {
            double a = k * 2 * Math.PI / m + (r.nextFloat() - 0.5) * 0.15;
            double d = 0.9 + r.nextFloat() * 0.1;
            double sw = 0.5 + 0.5 * Math.sin(a * waves + wave);
            int len = (int) Math.round((1.5 + 5.5 * sw) * size + r.nextInt(2));
            BlockPos p = WhaleTrees.at(cx + Math.cos(a) * rad * d, Math.round(top + dome * (1 - d * d)) - 1, cz + Math.sin(a) * rad * d);
            for (int q = 1; q <= len; q++) {
                g.leaf(p.below(q), ACACIA);
            }
        }
    }

    private TideTrees() {
    }
}
