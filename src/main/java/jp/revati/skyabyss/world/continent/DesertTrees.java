package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 砂漠 (どの土地でも) と灰の荒野の外の荒野の木 (2026-11-25。ラフ 3 案から B 砂紐の樹をユーザーが選んだ。
// ほかの土地と同じ「弓なりの枝・垂れる房」の言葉。橙のブロックは使わない)。
//   砂紐の樹: 細く傾いた暗い幹が、杖の頭のように大きく弓なりに折れ返り、弧の外側の半分から乾いた紐 (マングローブの根) がまばらに垂れる。
//   弧の先は下へ曲がって紐が一本垂れ、弧の頂に乾いた葉 (樫の葉。砂漠ではオリーブ色に染まる) が少し。
//   大きさは三つ: 若木 (弧一つ)、ふつう (反対へ二つ目の弧)、古木 (下が太い幹・根・幹の途中から三つ目の小さな弧)。同じ形を並べない。
// 乾いた紐は木の一部として SiteJob・ShapeJob・ShrineBuild の一覧に入れてある。
final class DesertTrees {
    static final BlockState LEAF = WhaleTrees.OAK;
    static final BlockState CORD = Blocks.MANGROVE_ROOTS.defaultBlockState();
    static final Block BARK = Blocks.STRIPPED_DARK_OAK_WOOD;

    // size: 0.75 = 若木、1.0 = ふつう、1.3 = 古木
    static void crook(ContinentTrees.Grower g, BlockPos base, RandomSource r, double size) {
        boolean old = size >= 1.2;
        int h = (int) Math.round((8 + r.nextInt(3)) * size);
        double a = r.nextFloat() * Math.PI * 2;
        double dx = Math.cos(a);
        double dz = Math.sin(a);
        double lean = 2.2 * size;
        double tx = 0;
        double tz = 0;
        // 傾いた幹 (古木は下が太い)
        for (double t = 0; t <= h; t += 0.5) {
            double f = t / h;
            tx = base.getX() + 0.5 + dx * lean * f * f;
            tz = base.getZ() + 0.5 + dz * lean * f * f;
            int w = old && f < 0.4 ? 2 : 1;
            for (int ox = 0; ox < w; ox++) {
                for (int oz = 0; oz < w; oz++) {
                    g.log(WhaleTrees.at(tx + ox, base.getY() + t, tz + oz), BARK, Direction.Axis.Y);
                }
            }
        }
        int ground = base.getY();
        arc(g, r, tx, base.getY() + h, tz, a, (3.8 + r.nextFloat()) * size, size, ground);
        // ふつうと古木: 頂から反対寄りへ二つ目の弧
        if (size >= 0.9) {
            arc(g, r, tx, base.getY() + h - 1, tz, a + 2.3 + r.nextFloat() * 0.6, (2.6 + r.nextFloat()) * size, size * 0.8, ground);
        }
        if (old) {
            // 幹の途中から横へ小さな弧
            double f = 0.55;
            double bx = base.getX() + 0.5 + dx * lean * f * f;
            double bz = base.getZ() + 0.5 + dz * lean * f * f;
            arc(g, r, bx, base.getY() + (int) (h * f), bz, a - 1.6 - r.nextFloat() * 0.6, 2.2 * size, size * 0.7, ground);
            // 根 (弓なりに砂へ潜る)
            for (int k = 0; k < 4; k++) {
                double ra = a + k * Math.PI / 2 + 0.4;
                for (double i = 1; i <= 3; i += 0.5) {
                    g.log(WhaleTrees.at(base.getX() + 1 + Math.cos(ra) * (i + 0.4), base.getY() + 1 - i * 0.5, base.getZ() + 1 + Math.sin(ra) * (i + 0.4)),
                            BARK, Direction.Axis.Y);
                }
            }
        }
    }

    // 杖の頭の弧: (x, y, z) から向き a へ半径 rad で折れ返る。外側の半分から乾いた紐、先から一本
    static void arc(ContinentTrees.Grower g, RandomSource r, double x, int y, double z, double a, double rad, double size, int ground) {
        double cx = x + Math.cos(a) * rad;
        double cz = z + Math.sin(a) * rad;
        BlockPos tip = null;
        for (double s = 0; s <= Math.PI * 0.88; s += 0.5 / (rad * 1.5)) {
            double px = cx - Math.cos(s) * rad * Math.cos(a);
            double pz = cz - Math.cos(s) * rad * Math.sin(a);
            double py = y + Math.sin(s) * rad * 1.1;
            BlockPos p = WhaleTrees.at(px, py, pz);
            g.log(p, BARK, s > Math.PI * 0.3 && s < Math.PI * 0.7 ? WhaleTrees.axisOf(Math.cos(a), Math.sin(a)) : Direction.Axis.Y);
            if (s > Math.PI * 0.45 && r.nextFloat() < 0.22f) {
                cord(g, r, p.below(), Math.max(1, Math.min(p.getY() - ground - 2, (int) Math.round((2 + r.nextInt(4)) * size))));
            }
            tip = p;
        }
        // 弧の先: 下へ曲がって紐が一本
        if (tip != null) {
            g.log(tip.below(), BARK, Direction.Axis.Y);
            cord(g, r, tip.below(2), Math.max(1, Math.min(tip.getY() - ground - 3, 2 + r.nextInt(3))));
        }
        // 弧の頂に乾いた葉を少し
        g.blob(WhaleTrees.at(cx, y + rad * 1.1 + 1, cz), 1.3 * size + 0.3, 0.8, LEAF);
    }

    // 乾いた紐を下へ n 段 (先が葉の房になることもある)
    static void cord(ContinentTrees.Grower g, RandomSource r, BlockPos top, int n) {
        for (int q = 0; q < n; q++) {
            BlockPos p = top.below(q);
            if (!g.free(p)) {
                return;
            }
            g.leaf(p, q == n - 1 && r.nextFloat() < 0.4f ? LEAF : CORD);
        }
    }

    private DesertTrees() {
    }
}
