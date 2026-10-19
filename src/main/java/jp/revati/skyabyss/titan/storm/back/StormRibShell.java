package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 肋の間の外の姿 (25 の 5章 A の見た目)。翼から生えた肋の尖塔: 根元は裾が広がって翼に溶け込み、骨の鰭 (肋) が壁へ寄りかかるように立ち上がり、
// 屋根の上で内へ曲がって頂で束ねる。肋のあいだは羽の石の膜。銅の帯・風の目の灯り・頂の避雷針は嵐の印。
// 内側の箱 (空洞・壁・屋根) は変えない。置くのは空気の所だけ (翼の体は変えない)。壁の面の上は帯と灯りだけ置き換える。
// 建てるのは StormBirdBuilder.buildRest の最後の方 (seal のあと)
final class StormRibShell {
    // 肋の鰭の広がり (壁からのマス数)。膜 (肋のあいだ) は WEB_REACH までで、肋は膜より外へ足を伸ばす
    static final double REACH = 8.5;
    static final double WEB_REACH = 6.0;
    // 翼の背のだいたいの高さ (裾の式の基準)
    static final int SURF = 160;
    // 面の肋 (北・南の面は x、西・東の面は z)。真ん中の肋だけ頂まで行く
    static final int[][] RIB_NS = {{-100, -99}, {-104, -103}, {-96, -95}};
    static final int[][] RIB_EW = {{-18, -17, -16}, {-23, -22, -21}, {-13, -12, -11}};

    private static final BlockState TUFF_POLISHED = Blocks.POLISHED_TUFF.defaultBlockState();
    private static final BlockState TUFF_BRICKS = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState TUFF_RAW = Blocks.TUFF.defaultBlockState();

    private StormRibShell() {
    }

    static int roofY() {
        return BONE_FLOOR + RIB_HEIGHT + 1;
    }

    private static int hash(int x, int y, int z) {
        return Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 97);
    }

    // 肋の鰭の上の縁 (壁からの距離 dd)
    static int ribTop(double dd) {
        return (int) Math.floor(SURF + (roofY() - SURF) * Math.pow(Math.max(0, 1 - dd / REACH), 1.4));
    }

    // 膜の上の縁 (鰭より低い)
    static int webTop(double dd) {
        return (int) Math.floor(SURF + (roofY() - 1 - SURF) * Math.pow(Math.max(0, 1 - dd / WEB_REACH), 1.0));
    }

    // 屋根の上の弧の高さ (中心まで di、半分の長さ half、高さ tall)
    static int crownY(double di, double half, double tall) {
        return roofY() + Math.max(1, (int) Math.round(tall * Math.sqrt(Math.min(1, di / half))));
    }

    // 肋: 骨のブロックの白に、4 段ごとに方解石の節、ところどころ閃緑岩
    private static BlockState ribBlock(int x, int y, int z) {
        int h = hash(x, y, z);
        return y % 4 == 0 ? StormKit.CALCITE : h < 10 ? StormKit.DIORITE : StormKit.BONE;
    }

    // 膜: 根元は翼の暗い石 (深層岩・凝灰岩)、上へ行くほど羽の白に変わる。翼から生えて見えるように、高さで色を移す
    private static BlockState webBlock(int x, int y, int z) {
        int h = hash(x, y, z);
        double t = Math.min(1, Math.max(0, (y - 156) / 26.0)) + (h - 48) / 400.0;
        if (t < 0.28) {
            return h < 28 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : h < 50 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : h < 70 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                    : h < 88 ? TUFF_RAW : StormKit.FEATHER;
        }
        if (t < 0.62) {
            return h < 30 ? TUFF_RAW : h < 55 ? TUFF_POLISHED : h < 80 ? StormKit.FEATHER : TUFF_BRICKS;
        }
        return h < 35 ? StormKit.CALCITE : h < 60 ? StormKit.FEATHER_WHITE : h < 85 ? StormKit.FEATHER : StormKit.DIORITE;
    }

    private static boolean in(int[][] sets, int v) {
        for (int[] set : sets) {
            for (int e : set) {
                if (e == v) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void ext(StormKit kit, int x, int y, int z, BlockState s, boolean overSolid) {
        if (kit.isCarved(x, y, z)) {
            return;
        }
        if (!overSolid && !kit.get(x, y, z).isAir()) {
            return;
        }
        kit.set(x, y, z, s);
    }

    static void build(StormKit kit) {
        if (!StormBirdBuilder.BIRD.exists()) {
            return;
        }
        int x0 = RIB_X1 - 1;
        int x1 = RIB_X2 + 1;
        int z0 = BONE_Z1 - 1;
        int z1 = BONE_Z2 + 1;
        int roof = roofY();
        // 1. 根元の裾・膜・鰭
        int reachI = (int) Math.ceil(REACH);
        for (int x = x0 - reachI; x <= x1 + reachI; x++) {
            for (int z = z0 - reachI; z <= z1 + reachI; z++) {
                int dx = Math.max(Math.max(x0 - x, x - x1), 0);
                int dz = Math.max(Math.max(z0 - z, z - z1), 0);
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double dd = Math.sqrt(dx * dx + dz * dz);
                if (dd >= REACH) {
                    continue;
                }
                int gnd = StormBirdBuilder.top(x, z);
                if (gnd == Integer.MIN_VALUE) {
                    gnd = SURF - 3;
                }
                boolean rib;
                if (dx == 0) {
                    rib = in(RIB_NS, x);
                } else if (dz == 0) {
                    rib = in(RIB_EW, z);
                } else {
                    rib = Math.abs(dx - dz) <= 1;
                }
                int top = rib ? ribTop(dd) : dd >= WEB_REACH ? Integer.MIN_VALUE : webTop(dd);
                if (rib && dd <= 1.5) {
                    // 壁ぎわの肋は壁の柱になって、屋根の弧までつながる
                    top = roof - 1;
                }
                for (int y = gnd + 1; y <= top; y++) {
                    ext(kit, x, y, z, rib && y >= top - 1 ? ribBlock(x, y, z) : webBlock(x, y, z), false);
                }
            }
        }
        // 2. 屋根の上の弧 (面の肋)。真ん中の肋は頂まで、脇の肋は壁の縁で短く曲がって終わる
        double halfNS = (z1 - z0) / 2.0;
        double halfEW = (x1 - x0) / 2.0;
        for (int[] set : RIB_NS) {
            boolean center = set[0] == -100;
            for (int x : set) {
                crown(kit, x, z0, 0, 1, center ? (int) halfNS : 4, center ? halfNS : 4, center ? 7 : 3);
                crown(kit, x, z1, 0, -1, center ? (int) halfNS : 4, center ? halfNS : 4, center ? 7 : 3);
            }
        }
        for (int[] set : RIB_EW) {
            boolean center = set[0] == -18;
            for (int z : set) {
                crown(kit, x0, z, 1, 0, center ? (int) halfEW : 4, center ? halfEW : 4, center ? 7 : 3);
                crown(kit, x1, z, -1, 0, center ? (int) halfEW : 4, center ? halfEW : 4, center ? 7 : 3);
            }
        }
        // 3. 角の肋 (斜め 3 マス幅)。屋根の上も頂まで
        int[] cxs = {x0, x1};
        int[] czs = {z0, z1};
        for (int ix = 0; ix < 2; ix++) {
            for (int iz = 0; iz < 2; iz++) {
                int sx = ix == 0 ? 1 : -1;
                int sz = iz == 0 ? 1 : -1;
                for (int k = 1; k <= 8; k++) {
                    int ya = k == 1 ? roof + 1 : crownY(k - 1, 8, 7);
                    int yb = crownY(k, 8, 7);
                    for (int y = ya; y <= yb; y++) {
                        int px = cxs[ix] + sx * k;
                        int pz = czs[iz] + sz * k;
                        ext(kit, px, y, pz, ribBlock(px, y, pz), false);
                        ext(kit, px + sx, y, pz, ribBlock(px + sx, y, pz), false);
                        ext(kit, px, y, pz + sz, ribBlock(px, y, pz + sz), false);
                    }
                }
            }
        }
        // 4. 頂の尖り (束ねた所に骨の柱と避雷針) と、四隅の角 (短い骨の角と避雷針)
        for (int y = roof + 1; y <= roof + 10; y++) {
            ext(kit, -100, y, -17, y == roof + 8 ? StormKit.COPPER : StormKit.BONE, false);
            ext(kit, -99, y, -17, y > roof + 8 ? StormKit.AIR : (y > roof + 6 ? StormKit.CALCITE : ribBlock(-99, y, -17)), false);
        }
        ext(kit, -100, roof + 11, -17, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
        for (int ix = 0; ix < 2; ix++) {
            for (int iz = 0; iz < 2; iz++) {
                int hx = ix == 0 ? x0 : x1;
                int hz = iz == 0 ? z0 : z1;
                for (int y = roof + 1; y <= roof + 3; y++) {
                    ext(kit, hx, y, hz, y == roof + 3 ? StormKit.COPPER : StormKit.BONE, false);
                }
                ext(kit, hx, roof + 4, hz, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
            }
        }
        // 5. 壁の面の銅の帯 (屋根の縁と 4 段下) と風の目 (肋のあいだの灯り)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) {
                    continue;
                }
                ext(kit, x, roof, z, StormKit.COPPER, true);
                ext(kit, x, roof - 4, z, StormKit.COPPER, true);
            }
        }
        for (int x : new int[] {-102, -97, -94}) {
            ext(kit, x, roof - 2, z0, StormKit.LAMP, true);
            ext(kit, x, roof - 2, z1, StormKit.LAMP, true);
        }
        for (int z : new int[] {-20, -14}) {
            ext(kit, x0, roof - 2, z, StormKit.LAMP, true);
            ext(kit, x1, roof - 2, z, StormKit.LAMP, true);
        }
    }

    // 面の肋の屋根の上の弧。(bx, bz) は壁の面の上のマス、(inX, inZ) は内向き
    private static void crown(StormKit kit, int bx, int bz, int inX, int inZ, int reach, double half, double tall) {
        int roof = roofY();
        for (int k = 1; k <= reach; k++) {
            int ya = k == 1 ? roof + 1 : crownY(k - 1, half, tall);
            int yb = crownY(k, half, tall);
            int px = bx + inX * k;
            int pz = bz + inZ * k;
            for (int y = ya; y <= yb; y++) {
                ext(kit, px, y, pz, ribBlock(px, y, pz), false);
            }
        }
    }
}
