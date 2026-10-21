package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.*;

import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Inscription;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

// 第二の渡り場と五つの試練 (StormDeck2。docs/design/29 の P3) を建てる。StormDeckBuilder.build の最後から呼ぶ。
// 部品 (島の床・錐・灯り・戸の枠・石の台) は StormDeckBuilder のものをそのまま使う。外へ開いた所なので kit.put で置く。
final class StormDeck2Builder {
    private static final BlockState AIR = StormKit.AIR;
    private static final BlockState GATE = ModBlocks.SEALED_GATE.get().defaultBlockState();
    private static final BlockState BLACK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState DARK2 = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    // 陰になりうる床 (S04。突風の予告で、風下の側が白く光る)
    static final BlockState SHADE = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
    // 気嚢の鍵の色 (A〜D)
    static final BlockState[] KEYS = {Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.BLUE_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(),
            Blocks.PURPLE_CONCRETE.defaultBlockState()};

    private StormDeck2Builder() {
    }

    static void build(StormKit kit) {
        bridge(kit);
        yard(kit);
        s04(kit);
        s08(kit);
        s07(kit);
        s12(kit);
        s11(kit);
        for (Inscription i : SIGNS) {
            kit.standingTablet(i.pos(), i.key());
        }
    }

    private static void put(StormKit kit, BlockPos p, BlockState s) {
        kit.put(p.getX(), p.getY(), p.getZ(), s);
    }

    private static void box(StormKit kit, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    kit.put(x, y, z, s);
                }
            }
        }
    }

    // 四角の島の縁の壁 (高さ h。floor の 1 上から)。opening = その区間は空ける (x 方向の壁なら z、z 方向の壁なら x の範囲 {a, b})
    private static void rimWall(StormKit kit, Island is, int h, int[] openNorth, int[] openSouth, int[] openWest, int[] openEast) {
        for (int x = is.x1(); x <= is.x2(); x++) {
            for (int[] side : new int[][] {{is.z1(), 0}, {is.z2(), 1}}) {
                int[] open = side[1] == 0 ? openNorth : openSouth;
                if (open != null && x >= open[0] && x <= open[1]) {
                    continue;
                }
                wallColumn(kit, x, is.y(), side[0], h);
            }
        }
        for (int z = is.z1(); z <= is.z2(); z++) {
            for (int[] side : new int[][] {{is.x1(), 0}, {is.x2(), 1}}) {
                int[] open = side[1] == 0 ? openWest : openEast;
                if (open != null && z >= open[0] && z <= open[1]) {
                    continue;
                }
                wallColumn(kit, side[0], is.y(), z, h);
            }
        }
    }

    private static void wallColumn(StormKit kit, int x, int fy, int z, int h) {
        for (int y = fy + 1; y <= fy + h; y++) {
            kit.put(x, y, z, y == fy + 1 || y == fy + h ? StormKit.COPPER : StormKit.CALCITE);
        }
    }

    // ---------------------------------------------------------------- 橋 (第一の渡り場の南の壁 → 第二の渡り場の北の縁)
    private static void bridge(StormKit kit) {
        // 第一の渡り場の南の手すりを、橋の幅だけあける (z 83)
        for (int x = BRIDGE_X1; x <= BRIDGE_X2; x++) {
            kit.put(x, Y, BRIDGE_Z1, StormKit.COPPER);
            kit.put(x, Y + 1, BRIDGE_Z1, AIR);
        }
        for (int z = BRIDGE_Z1 + 1; z <= BRIDGE_Z2; z++) {
            for (int x = BRIDGE_X1 - 1; x <= BRIDGE_X2 + 1; x++) {
                boolean walk = x >= BRIDGE_X1 && x <= BRIDGE_X2;
                if (walk) {
                    boolean border = x == BRIDGE_X1 || x == BRIDGE_X2;
                    kit.put(x, Y, z, border ? StormKit.COPPER : (x == 0 && z % 4 == 3 ? StormKit.LAMP : StormDeckBuilder.windFloor(x, z)));
                    kit.put(x, Y - 1, z, StormKit.CALCITE);
                    StormDeckBuilder.clearAbove(kit, x, z, Y, 9);
                } else {
                    kit.put(x, Y, z, StormKit.COPPER);
                    kit.put(x, Y + 1, z, StormDeco.RAIL);
                    kit.put(x, Y - 1, z, StormKit.CALCITE);
                    StormDeckBuilder.clearAbove(kit, x, z, Y + 1, 8);
                }
            }
        }
        // 灯りの柱 (9 おき)。橋の下は羽軸の道 (床 147〜155、頭上 5。x -3〜3) の上なので、脚は立てず、橋の下の羽の梁も置かない (y 161 の方解石の一枚だけ)
        for (int z = BRIDGE_Z1 + 5; z <= BRIDGE_Z2 - 2; z += 9) {
            for (int sx : new int[] {BRIDGE_X1 - 1, BRIDGE_X2 + 1}) {
                StormDeckBuilder.lamp(kit, sx, Y + 1, z);
            }
        }
        for (int z = BRIDGE_Z1 + 8; z <= BRIDGE_Z2 - 6; z += 12) {
            StormDeco.chevron(kit, 0, z, Direction.SOUTH, StormKit.COPPER, zz -> Y);
        }
    }

    // ---------------------------------------------------------------- 第二の渡り場 (x -24〜24、z 132〜150)
    private static void yard(StormKit kit) {
        for (int x = -25; x <= 25; x++) {
            for (int z = 171; z <= 191; z++) {
                boolean in = x >= -24 && x <= 24 && z >= 172 && z <= 190;
                if (in) {
                    boolean border = x == -24 || x == 24 || z == 172 || z == 190;
                    int dx = Math.abs(x);
                    int dz = Math.abs(z - 181);
                    boolean ring = Math.max(dx, dz) == 4 || (dx + dz <= 2);
                    kit.put(x, Y, z, border || ring ? StormKit.COPPER : StormDeckBuilder.windFloor(x, z));
                    StormDeckBuilder.clearAbove(kit, x, z, Y, 12);
                } else {
                    kit.put(x, Y, z, StormKit.COPPER);
                    kit.put(x, Y + 1, z, StormDeco.RAIL);
                    StormDeckBuilder.clearAbove(kit, x, z, Y + 1, 8);
                }
            }
        }
        // 橋の口 (北の縁の手すりを橋の幅だけあける)
        for (int x = BRIDGE_X1; x <= BRIDGE_X2; x++) {
            kit.put(x, Y + 1, 171, AIR);
            kit.put(x, Y, 171, StormKit.COPPER);
        }
        // 戸の口 (手すりを戸の幅だけあけ、床を渡す)
        for (List<BlockPos> g : List.of(S04_GATE, S08_GATE, S07_GATE, S12_GATE, S11_GATE)) {
            for (BlockPos p : g) {
                kit.put(p.getX(), p.getY(), p.getZ(), AIR);
            }
        }
        StormDeckBuilder.gateFrame(kit, S04_GATE, true);
        StormDeckBuilder.gateFrame(kit, S08_GATE, true);
        StormDeckBuilder.gateFrame(kit, S07_GATE, false);
        StormDeckBuilder.gateFrame(kit, S12_GATE, false);
        StormDeckBuilder.gateFrame(kit, S11_GATE, false);
        // 下: 逆さの羽の錐 (深さ 3)
        for (int d = 1; d <= 3; d++) {
            int hx = 24 - d * 3;
            int hz = 9 - d * 2;
            if (hx < 0 || hz < 0) {
                break;
            }
            for (int x = -hx; x <= hx; x++) {
                for (int z = 181 - hz; z <= 181 + hz; z++) {
                    kit.put(x, Y - d, z, StormDeckBuilder.featherUnder(new int[] {d, x, z}));
                }
            }
        }
        // 下の灯り: 第二の渡り場の下は尾羽の背 (歩ける所)。空が見えないので、銅の柱に海のランタンを下げる (建築の監査の暗さ)
        for (int x : new int[] {-16, -9, -3, 3, 9, 16}) {
            for (int z : new int[] {177, 185}) {
                StormDeckBuilder.underLamp(kit, x, z, Y - 4, Math.max(StormBirdBuilder.top(x, z) + 3, Y - 15));
            }
        }
        for (int x : new int[] {-19, 19}) {
            StormDeckBuilder.underLamp(kit, x, 181, Y - 4, Math.max(StormBirdBuilder.top(x, 181) + 3, Y - 15));
        }
        // 灯りの柱: 四隅と、橋の口・戸のそば
        for (int x : new int[] {-24, 24}) {
            for (int z : new int[] {172, 190}) {
                StormDeckBuilder.lamp(kit, x, Y + 1, z);
            }
        }
        StormDeckBuilder.lamp(kit, 6, Y + 1, 171);
        StormDeckBuilder.lamp(kit, -6, Y + 1, 171);
        StormDeckBuilder.lamp(kit, -10, Y + 1, 191);
        StormDeckBuilder.lamp(kit, 10, Y + 1, 191);
        StormDeckBuilder.lamp(kit, -25, Y + 1, 174);
        StormDeckBuilder.lamp(kit, 25, Y + 1, 174);
        StormDeckBuilder.lamp(kit, -25, Y + 1, 188);
        StormDeckBuilder.lamp(kit, 25, Y + 1, 188);
        // 色の線: 橋の口から広場の中心を通り、各戸へ。西と南西 = 水色、東と南東 = 金、南 = 白
        StormDeco.strip(kit, Y, StormDeco.WEST_LINE, StormDeco.WEST_PIP, 4, 0, new int[] {-2, 181}, new int[] {-24, 181});
        StormDeco.strip(kit, Y, StormDeco.EAST_LINE, StormDeco.EAST_PIP, 4, 0, new int[] {2, 181}, new int[] {24, 181});
        StormDeco.strip(kit, Y, StormDeco.WEST_LINE, StormDeco.WEST_PIP, 4, 0, new int[] {-6, 182}, new int[] {-22, 182}, new int[] {-22, 190});
        StormDeco.strip(kit, Y, StormDeco.EAST_LINE, StormDeco.EAST_PIP, 4, 0, new int[] {6, 182}, new int[] {20, 182}, new int[] {20, 190});
        StormDeco.strip(kit, Y, StormDeco.FRONT_LINE, StormDeco.FRONT_PIP, 4, 0, new int[] {0, 184}, new int[] {0, 190});
        StormDeco.strip(kit, Y, StormKit.COPPER, StormKit.COPPER, 99, 0, new int[] {0, 172}, new int[] {0, 177}, new int[] {5, 177});
        // 休み場・板・羽軸の柱・腰掛け
        kit.campfire(YARD_FIRE);
        for (int dz : new int[] {-2, 2}) {
            kit.stair(YARD_FIRE.getX(), YARD_FIRE.getY(), YARD_FIRE.getZ() + dz, dz < 0 ? Direction.NORTH : Direction.SOUTH,
                    Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
        }
        StormDeco.featherPillar(kit, -12, 177, Y, 6);
        StormDeco.featherPillar(kit, 12, 177, Y, 6);
    }

    // ---------------------------------------------------------------- S04 羽根の吊り橋
    private static void s04(StormKit kit) {
        StormDeckBuilder.approach(kit, -31, -26, 179, 183, true);
        for (Island is : new Island[] {S04_START, S04_MID, S04_END}) {
            StormDeckBuilder.floor(kit, is, StormKit.COPPER, StormDeckBuilder::windFloor);
            StormDeckBuilder.hangSquare(kit, is, 8, StormDeckBuilder::featherUnder);
            for (int[] c : new int[][] {{is.x1(), is.z1()}, {is.x1(), is.z2()}, {is.x2(), is.z1()}, {is.x2(), is.z2()}}) {
                StormDeckBuilder.lamp(kit, c[0], is.y(), c[1]);
            }
        }
        // 橋 A・B (幅 7。手すりなし。縁の床は銅、真ん中の列に灯り)
        int[][] spans = {{S04_A_X1, S04_A_X2}, {S04_B_X1, S04_B_X2}};
        for (int[] sp : spans) {
            for (int x = sp[0]; x <= sp[1]; x++) {
                for (int z = S04_BRIDGE_Z1; z <= S04_BRIDGE_Z2; z++) {
                    boolean edge = z == S04_BRIDGE_Z1 || z == S04_BRIDGE_Z2;
                    boolean lamp = (z == S04_BRIDGE_Z1 || z == S04_BRIDGE_Z2) && x % 6 == 0;
                    kit.put(x, Y, z, lamp ? StormKit.LAMP : edge ? StormKit.COPPER : StormDeckBuilder.windFloor(x, z));
                    StormDeckBuilder.clearAbove(kit, x, z, Y, 9);
                    if (z >= S04_BRIDGE_Z1 + 1 && z <= S04_BRIDGE_Z2 - 1) {
                        kit.put(x, Y - 1, z, StormKit.CALCITE);
                    }
                }
                for (int z = S04_BRIDGE_Z1 + 2; z <= S04_BRIDGE_Z2 - 2; z++) {
                    kit.put(x, Y - 2, z, StormKit.FEATHER);
                }
            }
        }
        // 柱 (3x3、高さ 4)。風下の「陰」になる床 (柱の前後 2 マス、両側) は青い床
        for (int px : S04_PILLARS) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = Y + 1; y <= Y + 4; y++) {
                        BlockState s = y == Y + 1 || y == Y + 4 ? StormKit.COPPER : BLACK;
                        kit.put(px + dx, y, S04_CENTER_Z + dz, s);
                    }
                }
            }
            kit.put(px, Y + 4, S04_CENTER_Z, StormKit.LAMP);
            for (int x = px - 2; x <= px + 2; x++) {
                for (int z : new int[] {S04_CENTER_Z - 3, S04_CENTER_Z - 2, S04_CENTER_Z + 2, S04_CENTER_Z + 3}) {
                    kit.put(x, Y, z, SHADE);
                }
            }
        }
        // 橋の下の灯り (ぶら下がる)
        for (int x = -60; x >= -135; x -= 15) {
            if (x > -88 || x < -94) {
                StormDeckBuilder.underLamp(kit, x, S04_CENTER_Z, Y - 3, Y - 6);
            }
        }
        StormDeckBuilder.fire(kit, REST_S04A);
        StormDeckBuilder.fire(kit, REST_S04M);
        StormDeckBuilder.station(kit, S04_STONE, S04_END);
        for (Island a : new Island[] {S04_START, S04_MID}) {
            StormDeco.chevron(kit, a.cx() - a.size() + 1, a.cz(), Direction.WEST, StormDeco.WEST_LINE, zz -> a.y());
        }
    }

    // ---------------------------------------------------------------- S08 気圧の弁
    private static void s08(StormKit kit) {
        StormDeckBuilder.approach(kit, 26, 31, 179, 183, true);
        Island h = S08_HALL;
        StormDeckBuilder.floor(kit, h, StormKit.COPPER, StormDeckBuilder::windFloor);
        StormDeckBuilder.hangSquare(kit, h, 9, StormDeckBuilder::featherUnder);
        // 壁: 北・南・東。西は戸の道のところだけあける
        rimWall(kit, h, 6, null, null, new int[] {179, 183}, null);
        // 気嚢の目盛り (北の壁の前。ガラスの柱と、両脇の銅の柱、てっぺんに鍵の色)
        for (int t = 0; t < 4; t++) {
            int x = S08_TANK_X[t];
            for (int y = Y + 1; y <= Y + 6; y++) {
                kit.put(x - 1, y, S08_TANK_Z, StormKit.COPPER);
                kit.put(x + 1, y, S08_TANK_Z, StormKit.COPPER);
            }
            for (int level = 1; level <= S08_TANK_H; level++) {
                put(kit, s08Gauge(t, level), StormKit.GLASS);
            }
            kit.put(x, Y + 6, S08_TANK_Z, KEYS[t]);
            kit.put(x, Y + 7, S08_TANK_Z, StormKit.LAMP);
            // 目盛りの前の床に鍵の色
            kit.put(x, Y, S08_TANK_Z + 1, KEYS[t]);
        }
        // 弁と効きの板
        for (int v = 0; v < 3; v++) {
            BlockPos valve = s08Valve(v);
            kit.put(valve.getX(), valve.getY(), valve.getZ(), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
            kit.put(valve.getX(), valve.getY() + 1, valve.getZ(), StormKit.LAMP);
            kit.put(valve.getX(), Y, valve.getZ() + 1, StormKit.LAMP);
            for (int t = 0; t < 4; t++) {
                put(kit, s08PanelKey(v, t), KEYS[t]);
                put(kit, s08Panel(v, t), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
        }
        for (int[] c : new int[][] {{h.x1() + 1, h.z1() + 1}, {h.x1() + 1, h.z2() - 1}, {h.x2() - 1, h.z1() + 1}, {h.x2() - 1, h.z2() - 1}, {h.cx(), h.z1() + 1}}) {
            StormDeckBuilder.lamp(kit, c[0], Y, c[1]);
        }
        StormDeckBuilder.fire(kit, REST_S08A);
        StormDeckBuilder.station(kit, S08_STONE, h);
    }

    // ---------------------------------------------------------------- S07 二枚の鏡の間
    private static void s07(StormKit kit) {
        StormDeckBuilder.approach(kit, -24, -20, 192, 198, false);
        int x1 = S07_X1;
        int x2 = S07_X2;
        int z1 = S07_Z1;
        int z2 = S07_Z2;
        // 床 (3x3 のマスで市松) と周りの縁
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean in = x >= x1 && x <= x2 && z >= z1 && z <= z2;
                if (in) {
                    int gx = (x - x1) / 3;
                    int gz = (z - z1) / 3;
                    kit.put(x, Y, z, ((gx + gz) & 1) == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : DARK2);
                    StormDeckBuilder.clearAbove(kit, x, z, Y, 9);
                } else {
                    kit.put(x, Y, z, StormKit.COPPER);
                }
            }
        }
        // 縁の壁 (高さ 4)。北の壁は戸の道の幅 (x -24〜-20) だけあける
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z : new int[] {z1 - 1, z2 + 1}) {
                if (z == z1 - 1 && x >= -24 && x <= -20) {
                    continue;
                }
                wallColumn(kit, x, Y, z, 4);
            }
        }
        for (int z = z1; z <= z2; z++) {
            wallColumn(kit, x1 - 1, Y, z, 4);
            wallColumn(kit, x2 + 1, Y, z, 4);
        }
        // 壁の灯り (上から 2 段目)
        for (int x = x1 + 3; x <= x2 - 2; x += 6) {
            kit.put(x, Y + 3, z1 - 1, StormKit.LAMP);
            kit.put(x, Y + 3, z2 + 1, StormKit.LAMP);
        }
        for (int z = z1 + 3; z <= z2 - 2; z += 6) {
            kit.put(x1 - 1, Y + 3, z, StormKit.LAMP);
            kit.put(x2 + 1, Y + 3, z, StormKit.LAMP);
        }
        // 床の灯り (鏡の置いてあるマスでないマスの南西の隅)
        for (int gx = 0; gx < S07_COLS; gx++) {
            for (int gz = 0; gz < S07_ROWS; gz++) {
                boolean mirror = false;
                for (int[] m : S07_MIRRORS) {
                    mirror |= m[0] == gx && m[1] == gz;
                }
                if (!mirror && ((gx + gz) & 1) == 0 && gx != S07_WINDOW_COL && (gx + gz) % 4 == 0) {
                    BlockPos c = s07Cell(gx, gz);
                    kit.put(c.getX() - 1, Y, c.getZ() + 1, StormKit.LAMP);
                }
            }
        }
        // 仕切り (gx 7 の列。窓のマスのほかは高さ 4 の柱)
        for (int gz = 0; gz < S07_ROWS; gz++) {
            if (gz == S07_SRC_ROW) {
                continue;
            }
            BlockPos c = s07Cell(S07_WINDOW_COL, gz);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = Y + 1; y <= Y + 4; y++) {
                        kit.put(c.getX() + dx, y, c.getZ() + dz, y == Y + 4 ? StormKit.COPPER : BLACK);
                    }
                }
            }
            kit.put(c.getX(), Y + 3, c.getZ() - 2 + 0, StormKit.LAMP);
        }
        // 窓の戸 (閉じた戸。引いたグループは光が届くと開く)
        for (BlockPos p : s07Window()) {
            put(kit, p, GATE);
        }
        // 光の出る所と受け羽
        BlockPos em = s07Emitter();
        put(kit, em, StormKit.LAMP);
        put(kit, em.above(), StormKit.COPPER);
        put(kit, em.below(), StormKit.COPPER);
        BlockPos ca = s07Catcher();
        put(kit, ca, StormBackSetup.RECEIVER.get().defaultBlockState());
        put(kit, ca.above(), StormKit.COPPER);
        put(kit, ca.below(), StormKit.COPPER);
        // 鏡 (台の床と、羽鏡)
        for (int[] m : S07_MIRRORS) {
            BlockPos c = s07Cell(m[0], m[1]);
            kit.put(c.getX(), Y, c.getZ(), StormKit.COPPER_BLOCK);
            kit.put(c.getX(), Y + 1, c.getZ(), StormBackSetup.MIRROR.get().defaultBlockState());
        }
        // 下: 逆さの角錐 (矩形が一段ごとに 1 縮む)
        for (int d = 1; d <= 11; d++) {
            for (int x = x1 + d; x <= x2 - d; x++) {
                for (int z = z1 + d; z <= z2 - d; z++) {
                    kit.put(x, Y - d, z, StormDeckBuilder.slateUnder(new int[] {d, x, z}));
                }
            }
        }
        StormDeckBuilder.fire(kit, REST_S07A);
        StormDeckBuilder.station(kit, S07_STONE, null);
    }

    // ---------------------------------------------------------------- S12 羽守長の頂
    private static void s12(StormKit kit) {
        StormDeckBuilder.approach(kit, -2, 2, 192, 198, false);
        StormDeckBuilder.floor(kit, S12_FOYER, StormKit.COPPER, StormDeckBuilder::windFloor);
        StormDeckBuilder.hangSquare(kit, S12_FOYER, 8, StormDeckBuilder::featherUnder);
        for (int[] c : new int[][] {{S12_FOYER.x1(), S12_FOYER.z1()}, {S12_FOYER.x1(), S12_FOYER.z2()}, {S12_FOYER.x2(), S12_FOYER.z1()},
                {S12_FOYER.x2(), S12_FOYER.z2()}}) {
            StormDeckBuilder.lamp(kit, c[0], Y, c[1]);
        }
        StormDeckBuilder.fire(kit, REST_S12A);
        // 入口の橋 (x -1〜1、z 170〜174。手すりは x -2・2)
        for (int z = S12_BRIDGE_Z1; z <= S12_BRIDGE_Z2; z++) {
            for (int x = -2; x <= 2; x++) {
                boolean rail = x == -2 || x == 2;
                kit.put(x, Y, z, rail ? StormKit.COPPER : DARK2);
                kit.put(x, Y + 1, z, rail ? StormDeco.RAIL : AIR);
                StormDeckBuilder.clearAbove(kit, x, z, Y + 1, 8);
            }
        }
        // 頂: 芯と筋と縁の輪は固い床、そのほか (扇の足場) は閉じた戸のブロック
        Island a = S12_ARENA;
        for (BlockPos p : a.floor()) {
            int dx = p.getX() - a.cx();
            int dz = p.getZ() - a.cz();
            double r = Math.hypot(dx, dz);
            int w = s12Wedge(dx, dz);
            if (w >= 0) {
                kit.put(p.getX(), p.getY(), p.getZ(), GATE);
            } else {
                boolean ring = Math.abs(r - 4.0) < 0.6 || r > S12_ARENA_R - 0.6;
                kit.put(p.getX(), p.getY(), p.getZ(), ring ? StormKit.COPPER : StormDeckBuilder.thunderFloor(p.getX(), p.getZ()));
            }
            StormDeckBuilder.clearAbove(kit, p.getX(), p.getZ(), p.getY(), 14);
        }
        // 芯の真ん中の灯り
        kit.put(a.cx(), Y, a.cz(), StormKit.LAMP);
        // 下: 芯の下の錐 (深さ 9)。筋の下の梁。縁の輪の下
        StormDeckBuilder.hang(kit, a.cx(), a.cz(), Y, 5, 9, 0, StormDeckBuilder::slateUnder);
        for (BlockPos p : a.floor()) {
            int dx = p.getX() - a.cx();
            int dz = p.getZ() - a.cz();
            double r = Math.hypot(dx, dz);
            if (s12Wedge(dx, dz) < 0 && r > S12_CORE_R) {
                kit.put(p.getX(), Y - 1, p.getZ(), StormKit.CALCITE);
            }
        }
        // 縁の灯りの柱 (輪の上、30 度おき)。芯の灯りの柱 (四つ)
        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(15 + 30 * i);
            int x = a.cx() + (int) Math.round(Math.cos(ang) * (S12_ARENA_R - 0.5));
            int z = a.cz() + (int) Math.round(Math.sin(ang) * (S12_ARENA_R - 0.5));
            if (a.has(x, z) && s12Wedge(x - a.cx(), z - a.cz()) < 0) {
                StormDeckBuilder.lamp(kit, x, Y, z);
            }
        }
        for (int[] d : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            StormDeckBuilder.lamp(kit, a.cx() + d[0], Y, a.cz() + d[1]);
        }
        // 出口の橋 (z 202〜205。戸は z 203) と石の島
        for (int z = S12_EXIT_Z1; z <= S12_EXIT_Z2; z++) {
            for (int x = -2; x <= 2; x++) {
                kit.put(x, Y, z, DARK2);
                StormDeckBuilder.clearAbove(kit, x, z, Y, 9);
                if (x == -2 || x == 2) {
                    kit.put(x, Y + 1, z, StormDeco.RAIL);
                }
            }
        }
        StormDeckBuilder.floor(kit, S12_EXIT, StormKit.COPPER, StormDeckBuilder::thunderFloor);
        StormDeckBuilder.hangSquare(kit, S12_EXIT, 6, StormDeckBuilder::slateUnder);
        StormDeckBuilder.station(kit, S12_STONE, S12_EXIT);
        for (int[] c : new int[][] {{S12_EXIT.x1(), S12_EXIT.z1()}, {S12_EXIT.x1(), S12_EXIT.z2()}, {S12_EXIT.x2(), S12_EXIT.z1()}, {S12_EXIT.x2(), S12_EXIT.z2()}}) {
            StormDeckBuilder.lamp(kit, c[0], Y, c[1]);
        }
    }

    // ---------------------------------------------------------------- S11 避雷の護送
    private static void s11(StormKit kit) {
        StormDeckBuilder.approach(kit, 18, 22, 192, 197, false);
        Island d = S11_DECK;
        StormDeckBuilder.floor(kit, d, StormKit.COPPER, StormDeckBuilder::thunderFloor);
        StormDeckBuilder.hangSquare(kit, d, 11, StormDeckBuilder::slateUnder);
        // 縁の手すり (入口の幅だけあける)
        for (int x = d.x1(); x <= d.x2(); x++) {
            for (int z : new int[] {d.z1(), d.z2()}) {
                if (z == d.z1() && x >= 18 && x <= 22) {
                    continue;
                }
                kit.put(x, Y + 1, z, StormDeco.RAIL);
            }
        }
        for (int z = d.z1(); z <= d.z2(); z++) {
            kit.put(d.x1(), Y + 1, z, StormDeco.RAIL);
            kit.put(d.x2(), Y + 1, z, StormDeco.RAIL);
        }
        // 床と縁の灯り
        for (int x = d.x1() + 3; x <= d.x2() - 2; x += 7) {
            for (int z = d.z1() + 3; z <= d.z2() - 2; z += 7) {
                kit.put(x, Y, z, StormKit.LAMP);
            }
        }
        for (int x = d.x1(); x <= d.x2(); x += 8) {
            StormDeckBuilder.lamp(kit, x, Y, d.z2());
        }
        for (int z = d.z1() + 8; z <= d.z2(); z += 8) {
            StormDeckBuilder.lamp(kit, d.x1(), Y, z);
            StormDeckBuilder.lamp(kit, d.x2(), Y, z);
        }
        // 塔 (籠の終わり): 3x3 の銅の台と、避雷針
        BlockPos t = S11_TOWER;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = Y + 1; y <= Y + 3; y++) {
                    kit.put(t.getX() + dx, y, t.getZ() + dz, y == Y + 3 ? StormKit.COPPER : BLACK);
                }
            }
        }
        for (int y = Y + 4; y <= Y + 8; y++) {
            kit.put(t.getX(), y, t.getZ(), y == Y + 8 ? Blocks.LIGHTNING_ROD.defaultBlockState() : StormKit.COPPER);
        }
        kit.put(t.getX(), Y + 6, t.getZ(), StormKit.LAMP);
        StormDeckBuilder.fire(kit, REST_S11A);
        StormDeckBuilder.station(kit, S11_STONE, d);
    }
}
