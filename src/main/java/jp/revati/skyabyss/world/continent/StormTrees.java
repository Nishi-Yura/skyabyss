package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 嵐の高地 (北西の山地。雪の斜面・吹きさらしの丘・砂利の丘・松の森) の木 (2026-11-25。ラフ 3 案から C 雷晶の螺旋木をユーザーが選んだ。
// 岩鯨・鐘の野・潮の浜と同じ「弓なりの枝・垂れる房」の言葉。橙のブロックは使わない)。
//   雷晶の螺旋木: 細く高い暗い幹のまわりを、枝が風の渦のように螺旋に巻き上がる (下ほど長い)。枝は弓なりに上がって先が垂れ、
//   枝の背に針葉の小さな棚、枝先に葉の房。房の縁から針葉が垂れ、その先に紫の雷晶が下がる。寒い所では上に雪。
//   大きさは三つ (若木・ふつう・古木)。古木は幹の下が太く根を張る。同じ形を並べない。
final class StormTrees {
    static final BlockState SPRUCE = WhaleTrees.SPRUCE;
    static final BlockState CRYSTAL = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN);
    static final Block DARK = Blocks.SPRUCE_WOOD;

    // size: 0.7 = 若木、1.0 = ふつう、1.3 = 古木
    static void spiral(ContinentTrees.Grower g, BlockPos base, RandomSource r, double size) {
        int h = (int) Math.round((16 + r.nextInt(4)) * size);
        boolean old = size >= 1.2;
        g.snowOnTop = g.level.getBiome(base).value().coldEnoughToSnow(base);
        for (int t = -1; t <= h; t++) {
            int w = t < h * (old ? 0.55 : 0.4) && size >= 0.85 ? 2 : 1;
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    g.log(base.offset(dx, t, dz), DARK, Direction.Axis.Y);
                }
            }
        }
        // 古木の根 (弓なりに地面へ潜る)
        if (old) {
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + (r.nextFloat() - 0.5) * 0.7;
                for (double i = 1; i <= 3 + r.nextInt(2); i += 0.5) {
                    g.log(WhaleTrees.at(base.getX() + 1 + Math.cos(a) * (i + 0.5), base.getY() + 1.5 - i * 0.6, base.getZ() + 1 + Math.sin(a) * (i + 0.5)),
                            DARK, Direction.Axis.Y);
                }
            }
        }
        double cx = base.getX() + (size >= 0.85 ? 1.0 : 0.5);
        double cz = base.getZ() + (size >= 0.85 ? 1.0 : 0.5);
        double a = r.nextFloat() * 2 * Math.PI;
        int n = Math.max(5, (int) Math.round(7 * size));
        for (int k = 0; k < n; k++) {
            double f = 0.25 + 0.7 * k / (n - 1.0);
            int y0 = base.getY() + (int) (h * f);
            a += 2.4 + (r.nextFloat() - 0.5) * 0.3;
            double reach = (8.0 * (1.1 - f) + 3.0) * size;
            double rise = (2.5 + r.nextFloat() * 1.5) * size;
            double turn = 1.9;
            double px = cx;
            double py = y0;
            double pz = cz;
            // 枝: 巻きながら外へ、弓なりに上がって先が少し垂れる (根もとは二段の太さ)
            for (double t = 0; t <= 1; t += 1 / (reach * 3)) {
                double aa = a + turn * t;
                px = cx + Math.cos(aa) * reach * t;
                pz = cz + Math.sin(aa) * reach * t;
                py = y0 + rise * Math.sin(t * Math.PI * 0.8);
                Direction.Axis ax = WhaleTrees.axisOf(Math.cos(aa), Math.sin(aa));
                g.log(WhaleTrees.at(px, py, pz), DARK, ax);
                if (t < 0.5) {
                    g.log(WhaleTrees.at(px, py + 1, pz), DARK, ax);
                }
                // 枝の背の針葉の棚 (中ほどと先の手前)
                if (Math.abs(t - 0.5) < 0.5 / (reach * 3) || Math.abs(t - 0.78) < 0.5 / (reach * 3)) {
                    g.disc(WhaleTrees.at(px, py + (t < 0.5 ? 2 : 1), pz), 1.2 + 0.6 * size, SPRUCE);
                }
            }
            double rr = 1.8 * size + 0.4;
            g.blob(WhaleTrees.at(px, py + 1, pz), rr, 1.2, SPRUCE);
            // 房の縁から針葉が垂れ、その先に雷晶
            for (int q = 0; q < 6; q++) {
                double qa = r.nextFloat() * 2 * Math.PI;
                double qd = rr * (0.4 + 0.5 * r.nextFloat());
                BlockPos hp = WhaleTrees.at(px + Math.cos(qa) * qd, py + 2, pz + Math.sin(qa) * qd);
                // 房の下の最初の空き
                int s = 0;
                while (s < 5 && !g.free(hp.below(s))) {
                    s++;
                }
                if (s == 0 || s == 5) {
                    continue;
                }
                BlockPos p = hp.below(s);
                int len = 1 + r.nextInt(3);
                for (int i = 0; i < len && g.free(p); i++) {
                    g.leaf(p, SPRUCE);
                    p = p.below();
                }
                if (r.nextFloat() < 0.8f && g.free(p)) {
                    g.level.setBlock(p, CRYSTAL, 2);
                }
            }
        }
        g.blob(WhaleTrees.at(cx, base.getY() + h + 1, cz), 1.6 * size + 0.3, 2.2, SPRUCE);
    }

    private StormTrees() {
    }
}
