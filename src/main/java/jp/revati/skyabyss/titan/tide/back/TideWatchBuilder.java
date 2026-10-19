package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 東の試練 潮見の間 (水位の謎解きの部屋) を建てる (26 の 9 章)。緑青の銅と海の石の「水門の広間」。
// 広間の形は L 字: 南の広い部分 (x 40〜98、z -45〜-6。西の端に前庭、まん中に三段の溜めの仕掛け、東の端まで歩ける床) と、
// 北東の「灯台のテラス」(x 76〜98、z -64〜-46。岩棚から続く、景色と碑と宝の場所)。
// 仕掛けは版 12 までの形を (+30, -4) 動かしたもの (溜め・戸・取っ手の相互の位置は TideWatch と一体)。
final class TideWatchBuilder {
    private TideWatchBuilder() {
    }

    // 灯台のテラス (岩棚の北。床 92。仕掛けを解いた先の景色の報い)
    static final Area TERRACE = new Area(76, 97, -62, -41, LEDGE_FLOOR, 9);
    // 広間の屋根の高さ (最初の天井ブロック)
    static final int HALL_ROOF = HUB_LEDGE + WATCH_HALL.height() + 1;
    // 広間の南の部分と、北東のテラスの部分 (どちらも x1〜x2、z1〜z2 を含む)
    private static final int[] SOUTH = {40, 98, -45, -6};
    private static final int[] NORTHEAST = {76, 98, -64, -46};

    static boolean inHall(int x, int z) {
        return in(SOUTH, x, z) || in(NORTHEAST, x, z);
    }

    private static boolean in(int[] r, int x, int z) {
        return x >= r[0] && x <= r[1] && z >= r[2] && z <= r[3];
    }

    private static boolean nearHall(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (inHall(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void build(TideKit kit) {
        kit.zone(TideZone.GAUGE);
        Area h = WATCH_HALL;
        // 外枠を詰め、中を空ける。上は甲羅の中の部屋。壁の灯りは段ごとの高さ (77・83・89・95・101)
        for (int x = h.x1() - 1; x <= h.x2() + 1; x++) {
            for (int z = h.z1() - 1; z <= h.z2() + 1; z++) {
                boolean in = inHall(x, z);
                if (!in && !nearHall(x, z)) {
                    continue;
                }
                for (int y = 58; y <= HALL_ROOF; y++) {
                    if (y < HUB_LEDGE) {
                        kit.set(x, y, z, TideKit.SHELL);
                    } else if (!in) {
                        kit.set(x, y, z, y == HALL_ROOF ? TideZone.GAUGE.dark : Math.floorMod(y - 77, 6) == 0 && Math.floorMod(x + z, 5) == 0 ? TideKit.LAMP : kit.wall(x, y, z));
                    } else if (y == HUB_LEDGE) {
                        kit.set(x, y, z, hallFloor(x, z));
                    } else if (y == HALL_ROOF) {
                        kit.set(x, y, z, Math.floorMod(x * 3 + z * 7, 13) == 0 ? TideKit.LAMP : kit.wall(x, y, z));
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 前庭へ入る口 (西の壁。東の回廊の高さ。幅 7)
        for (int z = EAST_HALL.z1(); z <= EAST_HALL.z2(); z++) {
            for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 7; y++) {
                kit.set(h.x1() - 1, y, z, TideKit.AIR);
            }
            kit.set(h.x1() - 1, HUB_LEDGE, z, TideKit.FLOOR);
        }
        foyer(kit);
        // 段 1・段 2・岩棚 (下は詰める)
        tierBlock(kit, TIER1);
        tierBlock(kit, TIER2);
        tierBlock(kit, LEDGE_E);
        // 溜め (内側は空。壁は外周 1 マス、上は次の段の床の高さまで)
        for (int k = 0; k < 3; k++) {
            int[] b = BASIN[k];
            int bottom = TIER_FLOOR[k] - 1;
            int wallTop = k < 2 ? TIER_FLOOR[k + 1] : LEDGE_FLOOR;
            for (int x = b[0] - 1; x <= b[1] + 1; x++) {
                for (int z = b[2] - 1; z <= b[3] + 1; z++) {
                    boolean inside = x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3];
                    for (int y = HUB_LEDGE; y <= wallTop; y++) {
                        if (inside) {
                            kit.set(x, y, z, y < bottom ? TideKit.SHELL : y == bottom ? TideZone.GAUGE.dark : TideKit.AIR);
                        } else {
                            kit.set(x, y, z, basinWall(x, y, z, k));
                        }
                    }
                }
            }
        }
        // 溜めの戸の下の敷居 (戸は試練の側が開け閉めする)
        for (int k = 0; k < 3; k++) {
            BlockPos d = BASIN_DOOR[k];
            kit.set(d.below(), TideKit.FLOOR);
            kit.set(d, TideKit.AIR);
            kit.set(d.above(), TideKit.AIR);
        }
        kit.set(T_EXIT_DOOR.below(), TideKit.FLOOR);
        kit.set(T_EXIT_DOOR, TideKit.AIR);
        kit.set(T_EXIT_DOOR.above(), TideKit.AIR);
        // 上の溜めの北の戸から岩棚へ上る階段 (x 81〜85、z -27〜-34)
        for (int i = 0; i <= 7; i++) {
            int z = -27 - i;
            int f = Math.min(LEDGE_FLOOR, TIER_FLOOR[2] + i);
            for (int x = 80; x <= 86; x++) {
                boolean wall = x == 80 || x == 86;
                for (int y = HUB_LEDGE; y <= f + 4; y++) {
                    if (wall) {
                        kit.set(x, y, z, y <= f + 1 ? kit.wall(x, y, z) : TideKit.AIR);
                    } else if (y < f) {
                        kit.set(x, y, z, TideKit.SHELL);
                    } else if (y == f) {
                        if (i > 0 && f > TIER_FLOOR[2] + i - 1 && f < LEDGE_FLOOR + 1 && i <= LEDGE_FLOOR - TIER_FLOOR[2]) {
                            kit.stair(x, y, z, Direction.NORTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 岩棚の碑文の石柱
        for (int y = LEDGE_FLOOR + 1; y <= LEDGE_FLOOR + 3; y++) {
            kit.set(85, y, -40, TideZone.GAUGE.dark);
        }
        terrace(kit);
        landmarks(kit);
    }

    // 広間の床: 銅の板と海の石の市松
    private static BlockState hallFloor(int x, int z) {
        if (Math.floorMod(x + z, 6) == 0 && Math.floorMod(x - z, 2) == 0) {
            return Blocks.WAXED_CUT_COPPER.defaultBlockState();
        }
        return ((x >> 1) + (z >> 1)) % 2 == 0 ? TideKit.FLOOR : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    }

    // 前庭: 柱の列 (緑青の銅の柱と灯り)、まん中に「潮位の柱」(目盛りの塔)
    private static void foyer(TideKit kit) {
        for (int x = 44; x <= 56; x += 6) {
            for (int z : new int[] {-43, -9}) {
                kit.pillar(x, z, HUB_LEDGE + 1, HUB_LEDGE + 12);
                kit.set(x, HUB_LEDGE + 5, z, TideKit.LAMP);
            }
        }
        // 潮位の柱 (3x3、高さ 24。目盛りの灯りが 3 段ごと。一番上は大きな銅の冠)
        int cx = 49;
        int cz = -27;
        for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 24; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean tick = y % 3 == 0 && Math.abs(dx) + Math.abs(dz) == 1;
                    kit.set(cx + dx, y, cz + dz, tick ? TideKit.LAMP : (y % 6 < 2 ? Blocks.WAXED_CHISELED_COPPER.defaultBlockState() : Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState()));
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                kit.set(cx + dx, HUB_LEDGE + 25, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? TideKit.LAMP : Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState());
            }
        }
        // 柱の足もとの輪 (低い腰掛け)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
                    kit.set(cx + dx, HUB_LEDGE + 1, cz + dz, Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState());
                }
            }
        }
    }

    private static void tierBlock(TideKit kit, Area a) {
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                for (int y = HUB_LEDGE; y <= a.floor(); y++) {
                    kit.set(x, y, z, y == a.floor() ? TideKit.FLOOR : TideKit.BRICK);
                }
            }
        }
    }

    // 溜めの壁。浮き石の届く高さ (5 段目) に目盛りの灯り
    private static BlockState basinWall(int x, int y, int z, int k) {
        int mark = TIER_FLOOR[k] + TidePuzzle.REACH;
        if (y == mark && (x + z) % 2 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return (y - TIER_FLOOR[k]) % 2 == 0 ? TideKit.DARK : TideKit.BRICK;
    }

    // 灯台のテラス (岩棚 y 92 の北): 広い床・柱。碑と宝は fixtures で置く
    private static void terrace(TideKit kit) {
        Area t = TERRACE;
        for (int x = t.x1(); x <= t.x2(); x++) {
            for (int z = t.z1(); z <= t.z2(); z++) {
                for (int y = HUB_LEDGE; y <= t.floor(); y++) {
                    boolean ring = (x + z) % 7 == 0;
                    kit.set(x, y, z, y == t.floor() ? (ring ? Blocks.WAXED_CUT_COPPER.defaultBlockState() : TideKit.FLOOR) : TideKit.BRICK);
                }
            }
        }
        // 岩棚の北の縁と、テラスの間の平らなつなぎ (ledge の北、z -39〜-41)
        for (int x = LEDGE_E.x1(); x <= LEDGE_E.x2(); x++) {
            for (int z = LEDGE_E.z1() - 2; z < LEDGE_E.z1(); z++) {
                for (int y = HUB_LEDGE; y <= LEDGE_FLOOR; y++) {
                    kit.set(x, y, z, y == LEDGE_FLOOR ? TideKit.FLOOR : TideKit.BRICK);
                }
            }
        }
        // 柱 (4 本。緑青の銅と灯り)
        for (int x : new int[] {80, 93}) {
            for (int z : new int[] {-58, -45}) {
                kit.pillar(x, z, LEDGE_FLOOR + 1, LEDGE_FLOOR + 8);
                kit.set(x, LEDGE_FLOOR + 5, z, TideKit.LAMP);
            }
        }
    }

    // 広間の目印: 灯りの道 (前庭から溜め B の前の段へ導く)、広間の南の縁を東へ歩く道 (東のひれの道の入口へ)、東の壁の柱の列
    private static void landmarks(TideKit kit) {
        kit.routeStrip(HUB_LEDGE, TideKit.line(40, -7, 56, -7, 56, -14, 61, -14), TideKit.LAMP, TideKit.FLOOR);
        kit.routeStrip(HUB_LEDGE, TideKit.line(62, -8, 97, -8, 97, -12, 98, -12), TideKit.LAMP, TideKit.FLOOR);
        for (int z = -45; z <= -25; z += 10) {
            kit.pillar(97, z, HUB_LEDGE + 1, HUB_LEDGE + 9);
            kit.set(97, HUB_LEDGE + 5, z, TideKit.LAMP);
        }
    }
}
