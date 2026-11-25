package jp.revati.skyabyss.world.continent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の土地の木 (2026-11-25。鯨見の町の試作でユーザーが決めた形。作る道具の元は tools/gen/world/authored/shapes_big.py)。
// どれも「弓なりの枝・垂れる房」の言葉でそろえる。バニラの形にしない。
//   垂れ枝の樅 (FIR / 大きい FIR_BIG): まっすぐな幹、段ごとに外へ出て先が垂れる枝の房、頂は細い槍。大きいものは幹 2×2 と根。
//     枝先にまれに青緑 (灯り葉の樹の親戚)
//   骨白の細木 (PALE): 白い細い幹が少し傾き、短い枝に平たい葉の房、ところどころ光る実のつる
//   苔の低木 (MOSS_LOW): 短くねじれた幹、横に広い平たい苔色の傘 (草地にぽつんと)
//   灯り葉の樹 (TEAL): 弓なりの枝が四〜五本、青緑と唐檜の葉の房が垂れ、房の芯に光る菌、柳のような光る実のつる。森の中の林だけ
final class WhaleTrees {
    static final BlockState SPRUCE = leaf(Blocks.SPRUCE_LEAVES);
    static final BlockState DARK_OAK = leaf(Blocks.DARK_OAK_LEAVES);
    static final BlockState BIRCH = leaf(Blocks.BIRCH_LEAVES);
    static final BlockState AZALEA = leaf(Blocks.AZALEA_LEAVES);
    static final BlockState OAK = leaf(Blocks.OAK_LEAVES);
    static final BlockState WART = Blocks.WARPED_WART_BLOCK.defaultBlockState();

    static BlockState leaf(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockPos at(double x, double y, double z) {
        return BlockPos.containing(x, y, z);
    }

    static Direction.Axis axisOf(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
    }

    // 葉の下に垂れる光る実のつる。上が葉か幹でなければ垂らさない
    static void hang(ContinentTrees.Grower g, BlockPos top, int n) {
        if (g.free(top.above())) {
            return;
        }
        for (int k = 0; k < n; k++) {
            BlockPos p = top.below(k);
            if (!g.level.getBlockState(p).isAir()) {
                return;
            }
            boolean tip = k == n - 1;
            BlockState s = tip ? Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, true)
                    : Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, false);
            g.level.setBlock(p, s, 2);
        }
    }

    static void fir(ContinentTrees.Grower g, BlockPos base, RandomSource r, boolean big) {
        int h = big ? 26 + r.nextInt(9) : 13 + r.nextInt(8);
        int tw = big ? 2 : 1;
        for (int t = -1; t < h; t++) {
            for (int dx = 0; dx < tw; dx++) {
                for (int dz = 0; dz < tw; dz++) {
                    g.log(base.offset(dx, t, dz), Blocks.SPRUCE_LOG, Direction.Axis.Y);
                }
            }
        }
        double c = base.getX() + (tw - 1) * 0.5;
        double d = base.getZ() + (tw - 1) * 0.5;
        if (big) {
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + (r.nextFloat() - 0.5) * 0.6;
                int len = 3 + r.nextInt(3);
                for (int i = 1; i < len; i++) {
                    g.log(at(c + 0.5 + Math.cos(a) * (i + 1), base.getY() + 1 - i * 0.5, d + 0.5 + Math.sin(a) * (i + 1)), Blocks.SPRUCE_WOOD,
                            Direction.Axis.Y);
                }
            }
        }
        g.alt(Blocks.DARK_OAK_LEAVES, 0.33f);
        int y0 = base.getY() + Math.max(4, h / 4);
        int top = base.getY() + h;
        int tier = 0;
        for (int yy = y0; yy < top - 1; yy += big ? 4 : 3) {
            double t = (yy - y0) / (double) Math.max(1, top - y0);
            double reach = Math.pow(1 - t, 0.8) * (big ? 7.0 : 4.2) + 1.0;
            int n = reach > 4 ? 5 : 4;
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n + tier * 0.7 + (r.nextFloat() - 0.5) * 0.4;
                int steps = (int) (reach * 1.6) + 1;
                for (int i = 0; i <= steps; i++) {
                    double q = i / (double) steps;
                    double bx = c + Math.cos(a) * reach * q;
                    double bz = d + Math.sin(a) * reach * q;
                    double by = yy + 0.6 * q - 1.6 * q * q;
                    if (reach > 5 && q < 0.7) {
                        g.log(at(bx, by, bz), Blocks.SPRUCE_WOOD, axisOf(Math.cos(a), Math.sin(a)));
                    }
                    if (i % 2 == 1 && i != steps) {
                        continue;
                    }
                    double rr = 0.9 + 0.7 * q;
                    g.blob(at(bx, by, bz), rr, rr * 0.7, SPRUCE);
                }
                if (r.nextFloat() < (big ? 0.25f : 0.08f)) {
                    BlockPos tip = at(c + Math.cos(a) * reach * 0.8, yy - 0.6, d + Math.sin(a) * reach * 0.8);
                    if (g.free(tip) || g.level.getBlockState(tip).getBlock() instanceof LeavesBlock) {
                        g.level.setBlock(tip, WART, 2);
                    }
                }
            }
            tier++;
        }
        // 先: 幹の先を葉で包んで細い槍に
        for (int k = -2; k < (big ? 3 : 2); k++) {
            int rad = k < 1 ? 1 : 0;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= rad) {
                        g.leaf(at(c + dx, top + k, d + dz), SPRUCE);
                    }
                }
            }
        }
    }

    static void pale(ContinentTrees.Grower g, BlockPos base, RandomSource r) {
        int h = 9 + r.nextInt(6);
        double lx = (r.nextFloat() - 0.5) * 0.5;
        double lz = (r.nextFloat() - 0.5) * 0.5;
        for (int t = -1; t < h; t++) {
            g.log(at(base.getX() + lx * t * t / h, base.getY() + t, base.getZ() + lz * t * t / h), Blocks.BIRCH_WOOD, Direction.Axis.Y);
        }
        g.alt(Blocks.DARK_OAK_LEAVES, 0.4f);
        int nb = 2 + r.nextInt(2);
        for (int k = 0; k < nb; k++) {
            double a = r.nextFloat() * 2 * Math.PI;
            int by = base.getY() + (int) (h * (0.55 + 0.35 * r.nextFloat()));
            double len = 2.5 + r.nextFloat() * 2;
            double sx = base.getX() + lx * Math.pow(by - base.getY(), 2) / h;
            double sz = base.getZ() + lz * Math.pow(by - base.getY(), 2) / h;
            double ex = sx + Math.cos(a) * len;
            double ez = sz + Math.sin(a) * len;
            for (int i = 0; i <= (int) len; i++) {
                g.log(at(sx + Math.cos(a) * i, by + i * 0.4, sz + Math.sin(a) * i), Blocks.BIRCH_WOOD, axisOf(Math.cos(a), Math.sin(a)));
            }
            BlockPos cl = at(ex, by + len * 0.4 + 1, ez);
            double rr = 2.4 + r.nextFloat() * 0.9;
            g.blob(cl, rr, rr * 0.5, BIRCH);
            if (r.nextBoolean()) {
                hang(g, cl.offset(r.nextInt(5) - 2, -2, r.nextInt(5) - 2), 2 + r.nextInt(2));
            }
        }
        g.blob(at(base.getX() + lx * h, base.getY() + h, base.getZ() + lz * h), 2.6, 1.6, BIRCH);
    }

    static void mossLow(ContinentTrees.Grower g, BlockPos base, RandomSource r) {
        int h = 4 + r.nextInt(3);
        double ph = r.nextFloat() * 6;
        for (int t = -1; t < h; t++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    if (t < 2 || (dx + dz) % 2 == 0) {
                        g.log(at(base.getX() + dx + 0.6 * Math.sin(t * 0.8 + ph), base.getY() + t, base.getZ() + dz), Blocks.OAK_WOOD,
                                Direction.Axis.Y);
                    }
                }
            }
        }
        g.alt(Blocks.OAK_LEAVES, 0.3f);
        int nb = 3 + r.nextInt(2);
        for (int k = 0; k < nb; k++) {
            double a = k * Math.PI / 2 + (r.nextFloat() - 0.5) * 0.8;
            double len = 3.5 + r.nextFloat() * 2.5;
            for (int i = 0; i <= (int) len; i++) {
                g.log(at(base.getX() + 0.5 + Math.cos(a) * i, base.getY() + h - 1 + i * 0.3, base.getZ() + 0.5 + Math.sin(a) * i), Blocks.OAK_WOOD,
                        axisOf(Math.cos(a), Math.sin(a)));
            }
            double rr = 3.5 + r.nextFloat() * 1.3;
            g.blob(at(base.getX() + Math.cos(a) * len, base.getY() + h + len * 0.3 + 1, base.getZ() + Math.sin(a) * len), rr, rr * 0.38, AZALEA);
        }
        g.blob(base.above(h + 2), 4.0, 1.6, AZALEA);
    }

    static void teal(ContinentTrees.Grower g, BlockPos base, RandomSource r) {
        int h = 18 + r.nextInt(7);
        int split = (int) (h * 0.4);
        for (int t = -2; t < split + 2; t++) {
            double rr = t < 3 ? 2.0 : 1.5;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= rr * rr) {
                        g.log(base.offset(dx, t, dz), Blocks.SPRUCE_WOOD, Direction.Axis.Y);
                    }
                }
            }
        }
        g.alt(Blocks.SPRUCE_LEAVES, 0.5f);
        int n = 4 + r.nextInt(2);
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + (r.nextFloat() - 0.5) * 0.6;
            double reach = 9 + r.nextFloat() * 4;
            double rise = 8 + r.nextFloat() * 5;
            int steps = (int) (reach * 2.2);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double px = base.getX() + Math.cos(a) * reach * t;
                double pz = base.getZ() + Math.sin(a) * reach * t;
                double yy = base.getY() + split + rise * Math.sin(t * Math.PI * 0.75);
                g.log(at(px, yy, pz), Blocks.SPRUCE_WOOD, axisOf(Math.cos(a), Math.sin(a)));
                if (t > 0.3 && i % 5 == 0 || i == steps) {
                    BlockPos cl = at(px, yy + 1, pz);
                    double rr = (3.2 + r.nextFloat() * 1.4) * (0.7 + 0.5 * t);
                    g.blob(cl, rr, rr * 0.55, WART);
                    g.level.setBlock(cl.below(), Blocks.SHROOMLIGHT.defaultBlockState(), 2);
                    for (int q = 0; q < (int) (rr * 3); q++) {
                        double qa = r.nextFloat() * 2 * Math.PI;
                        int hx = (int) Math.round(px + Math.cos(qa) * rr * 0.85);
                        int hz = (int) Math.round(pz + Math.sin(qa) * rr * 0.85);
                        int top = cl.getY();
                        while (top > cl.getY() - 4 && g.level.getBlockState(new BlockPos(hx, top, hz)).isAir()) {
                            top--;
                        }
                        if (!g.level.getBlockState(new BlockPos(hx, top, hz)).isAir()) {
                            hang(g, new BlockPos(hx, top - 1, hz), 3 + r.nextInt(5));
                        }
                    }
                }
            }
        }
        g.blob(base.above(split + (int) (h * 0.55)), 4.5, 2.7, WART);
    }

    private WhaleTrees() {
    }
}
