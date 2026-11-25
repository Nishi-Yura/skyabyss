package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;

// 鐘の野 (都のまわり) の木 (2026-11-25。ラフ 3 案から B 花傘の樹をユーザーが選んだ。岩鯨の土地と同じ「弓なりの枝・垂れる房」の言葉)。
//   花傘の樹: ねじれた暗い桜の幹 (根もとは太く張る)。幹の上から弓なりの枝が五〜七本出て、枝先ごとに桜色の丸い房。
//   房が重なって大きな傘になり、房の縁は幕のように垂れる。根もとに花びら。大きさは三つ (若木・ふつう・古木) で、同じ形を並べない。
final class MeadowTrees {
    static final BlockState CHERRY = WhaleTrees.leaf(Blocks.CHERRY_LEAVES);

    // size: 0.65 = 若木、1.0 = ふつう、1.35 = 古木
    static void blossomUmbrella(ContinentTrees.Grower g, BlockPos base, RandomSource r, double size) {
        int h = (int) Math.round((7 + r.nextInt(3)) * (0.75 + 0.25 * size));
        double ph = r.nextFloat() * 6;
        int tw = size >= 0.9 ? 2 : 1;
        double lx = 0;
        double lz = 0;
        for (int t = -1; t < h; t++) {
            lx = 1.5 * size * Math.sin(t * 0.45 + ph);
            lz = 1.1 * size * Math.cos(t * 0.35 + ph);
            for (int dx = 0; dx < tw; dx++) {
                for (int dz = 0; dz < tw; dz++) {
                    if (t < h - 2 || (dx + dz) % 2 == 0) {
                        g.log(WhaleTrees.at(base.getX() + dx + lx * Math.min(1, t / 3.0), base.getY() + t, base.getZ() + dz + lz * Math.min(1, t / 3.0)),
                                Blocks.CHERRY_WOOD, Direction.Axis.Y);
                    }
                }
            }
        }
        // 根の張り出し (ふつうと古木)
        if (tw == 2) {
            for (int k = 0; k < 5; k++) {
                double a = k * 2 * Math.PI / 5 + (r.nextFloat() - 0.5) * 0.6;
                int len = 2 + (int) (size * 2) + r.nextInt(2);
                for (int i = 1; i <= len; i++) {
                    g.log(WhaleTrees.at(base.getX() + 0.5 + Math.cos(a) * (i + 0.8), base.getY() + 1 - i * 0.55, base.getZ() + 0.5 + Math.sin(a) * (i + 0.8)),
                            Blocks.CHERRY_WOOD, Direction.Axis.Y);
                }
            }
        }
        double cx = base.getX() + 0.5 + lx;
        double cz = base.getZ() + 0.5 + lz;
        int top = base.getY() + h;
        // 真ん中の房
        dome(g, r, cx, top + 1, cz, (3.6 + r.nextFloat()) * size);
        // 枝: 弓なりに外へ上がり、先に房
        int n = size < 0.9 ? 4 : 5 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + (r.nextFloat() - 0.5) * 0.5;
            double reach = (5.0 + r.nextFloat() * 3.0) * size;
            double rise = (1.5 + r.nextFloat() * 2.5) * size;
            int steps = (int) (reach * 2) + 1;
            double ex = cx;
            double ey = top;
            double ez = cz;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                ex = cx + Math.cos(a) * reach * t;
                ez = cz + Math.sin(a) * reach * t;
                ey = top - 1 + rise * Math.sin(t * Math.PI * 0.65);
                g.log(WhaleTrees.at(ex, ey, ez), Blocks.CHERRY_WOOD, WhaleTrees.axisOf(Math.cos(a), Math.sin(a)));
            }
            dome(g, r, ex, (int) Math.round(ey) + 1, ez, (3.0 + r.nextFloat() * 1.4) * size);
        }
        // 根もとの花びら (傘の広さほど)
        int pr = (int) Math.round(8 * size);
        for (int k = 0; k < (int) (34 * size * size); k++) {
            int dx = r.nextInt(pr * 2 + 1) - pr;
            int dz = r.nextInt(pr * 2 + 1) - pr;
            BlockPos gp = base.offset(dx, -1, dz);
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos q = gp.above(dy);
                if (g.level.getBlockState(q).is(Blocks.GRASS_BLOCK) && g.level.getBlockState(q.above()).isAir()) {
                    g.level.setBlock(q.above(), Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + r.nextInt(4))
                            .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue(r.nextInt(4))), 2);
                    break;
                }
            }
        }
    }

    // 丸い房 (上は丸く、下は平ら)。縁から幕のように垂れる
    static void dome(ContinentTrees.Grower g, RandomSource r, double x, int y, double z, double rad) {
        g.blob(WhaleTrees.at(x, y, z), rad, rad * 0.5, CHERRY);
        int ir = (int) Math.ceil(rad);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rad - 1.2 && d <= rad && r.nextFloat() < 0.45f) {
                    int drop = 1 + r.nextInt(rad > 3.5 ? 4 : 3);
                    for (int q = 1; q <= drop; q++) {
                        g.leaf(WhaleTrees.at(x + dx, y - q, z + dz), CHERRY);
                    }
                }
            }
        }
    }

    private MeadowTrees() {
    }
}
