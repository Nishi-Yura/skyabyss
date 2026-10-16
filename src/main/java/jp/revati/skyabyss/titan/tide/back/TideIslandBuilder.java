package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.tide.heart.TideHeartArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の島を建てる (08 の 21 章「巨神の形の背中」)。最初の乗り込みと、配置の版が変わったときに一度だけ動く (数秒かかる)。
// 順番: 巨神の形の亀 (TideTurtle。中は詰まっている) → 浜 (甲羅の中の広間) → 潮門・到着の段・見送りの段・潮門の廊下 → くぐり道 →
//       広間 (月の窓の縦穴は頂の塔の柱の段まで) → 回り道と東の回廊 → 珊瑚の庭 (甲羅の中の洞) → 潮見の間 → 干潟 (甲羅の前の洞) →
//       螺旋の階段と殻の底 → 掘った所の内壁を潮の民の石に (skin) → 甲羅の上へ上る段 → 頂の塔の中 → 光の井戸 →
//       休み場・碑文・石・抜け道の床
// 潮で動く所 (水、門の開け閉め、庭の道の形、潮見の間の水位、巣) は試練の側 (TideBackTrials ほか) が置く
public final class TideIslandBuilder {
    private TideIslandBuilder() {
    }

    public static void build(TideKit kit) {
        long t0 = System.currentTimeMillis();
        int placed = TideTurtle.place(kit);
        SkyAbyss.LOGGER.info("Placed the tide turtle: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
        beach(kit);
        gate(kit);
        cave(kit);
        hub(kit);
        ramp(kit);
        garden(kit);
        eastRoom(kit);
        flats(kit);
        spiral(kit);
        TideHeartArena.build(kit.level());
        skin(kit);
        topStair(kit);
        tower(kit);
        lightWells(kit);
        fixtures(kit);
    }

    // ---------------------------------------------------------------- 甲羅の中 (掘ると見える、体の芯の石)
    static boolean shellMaterial(BlockState s) {
        return s.is(Blocks.TUFF) || s.is(Blocks.DEEPSLATE_TILES);
    }

    // なめらかな値の雑音 (0〜1)。格子の点に乱数を置き、間をなめらかにつなぐ
    static double noise(double x, double z, double scale, int seed) {
        double fx = x / scale;
        double fz = z / scale;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(ix, iz, seed);
        double b = lattice(ix + 1, iz, seed);
        double c = lattice(ix, iz + 1, seed);
        double d = lattice(ix + 1, iz + 1, seed);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private static double lattice(int x, int z, int seed) {
        long h = x * 374761393L + z * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    // 甲羅の地表: 甲羅の板の継ぎ目 (同心の環と放射の線) は暗い石、板の上は苔と砂の大きな斑、ところどころ珊瑚の色
    static BlockState reef(int i) {
        return switch (Math.floorMod(i, 5)) {
            case 0 -> Blocks.PINK_TERRACOTTA.defaultBlockState();
            case 1 -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case 2 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case 3 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            default -> Blocks.MAGENTA_TERRACOTTA.defaultBlockState();
        };
    }

    private static BlockState deadFan(int i) {
        return switch (Math.floorMod(i, 4)) {
            case 0 -> Blocks.DEAD_BRAIN_CORAL.defaultBlockState();
            case 1 -> Blocks.DEAD_FIRE_CORAL.defaultBlockState();
            case 2 -> Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState();
            default -> Blocks.DEAD_HORN_CORAL.defaultBlockState();
        };
    }

    // ---------------------------------------------------------------- 潮門の浜 (甲羅の中の広間)
    private static void beach(TideKit kit) {
        Area b = BEACH;
        for (int x = b.x1() - 1; x <= b.x2() + 1; x++) {
            for (int z = b.z1() - 1; z <= b.z2() + 1; z++) {
                boolean in = x >= b.x1() && x <= b.x2() && z >= b.z1() && z <= b.z2();
                int edge = in ? Math.min(Math.min(x - b.x1(), b.x2() - x), Math.min(z - b.z1(), b.z2() - z)) : -1;
                // 低い丸天井: 壁ぎわは 74、内へ 3 マスで 77
                int ceil = Math.min(BEACH_CEILING, b.floor() + 8 + edge);
                for (int y = 58; y <= BEACH_CEILING + 1; y++) {
                    BlockState s;
                    if (!in) {
                        s = kit.wall(x, y, z);
                    } else if (y < b.floor()) {
                        s = TideKit.SANDSTONE;
                    } else if (y == b.floor()) {
                        s = beachFloor(x, z);
                    } else if (y <= ceil) {
                        s = TideKit.AIR;
                    } else if (y == ceil + 1) {
                        // 真珠層の天井に、ところどころ灯り
                        s = Math.floorMod(x * 5 + z * 3, 13) == 0 ? TideKit.LAMP : TideKit.NACRE;
                    } else {
                        s = kit.wall(x, y, z);
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 浜の道具: 炉、焚き火と腰掛け、碑文の石柱 (看板は fixtures)、縁の灯り
        kit.set(-14, 67, 46, ModBlocks.RESONANCE_FORGE.get().defaultBlockState().setValue(ResonanceForgeBlock.FACING, Direction.WEST));
        kit.set(-18, 67, 54, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.stair(-20, 67, 54, Direction.WEST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-16, 67, 54, Direction.EAST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-18, 67, 56, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        for (int y = 67; y <= 69; y++) {
            kit.set(-17, y, 42, TideKit.DARK);
        }
        for (int[] l : new int[][] {{-31, 43}, {-31, 61}, {-13, 61}, {-22, 61}}) {
            kit.set(l[0], 67, l[1], Blocks.PRISMARINE_WALL.defaultBlockState());
            kit.set(l[0], 68, l[1], TideKit.LAMP);
        }
        // 砂の上の貝殻 (枯れた珊瑚の扇)
        for (int[] c : new int[][] {{-28, 58}, {-25, 47}, {-15, 59}, {-29, 50}}) {
            kit.set(c[0], 67, c[1], deadFan(c[0] + c[1]));
        }
    }

    private static BlockState beachFloor(int x, int z) {
        int h = Math.floorMod(x * 7 + z * 3, 9);
        return h < 6 ? TideKit.SAND : h < 8 ? TideKit.FLOOR : Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 潮門 (モデルの左後ろの門)、到着の段、見送りの段、潮門の廊下
    private static void gate(TideKit kit) {
        Area l = LANDING;
        // 到着の段 (床 64): 甲羅の縁と後ろのひれの付け根の上。段の上 6 段を空ける (縁の歯とひれの付け根の、段に出る所を削る)
        for (int x = l.x1(); x <= l.x2(); x++) {
            for (int z = l.z1(); z <= l.z2(); z++) {
                for (int y = 60; y <= l.floor(); y++) {
                    kit.set(x, y, z, y == l.floor() ? landingFloor(x, z) : TideKit.SANDSTONE);
                }
                for (int y = l.floor() + 1; y <= l.floor() + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 光る面の下 (x -55〜-53、y 65〜67) をくぐる所の床
        for (int x = l.x2() + 1; x <= -53; x++) {
            for (int z = GATE_HALL_Z1; z <= GATE_HALL_Z2; z++) {
                kit.set(x, GATE_FLOOR, z, landingFloor(x, z));
                for (int y = GATE_FLOOR + 1; y <= GATE_FLOOR + 3; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 見送りの段: 段の西 (x -68〜-71) を、海へ一段ずつ下りる。いちばん下 (x -71〜-69) に入ると地上へ戻る (inReturnGate)
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int i = 0; i < 4; i++) {
            int x = l.x1() - 1 - i;
            int top = l.floor() - 1 - i;
            for (int z = GATE_Z - 1; z <= GATE_Z + 2; z++) {
                boolean side = z == GATE_Z - 1 || z == GATE_Z + 2;
                for (int y = 58; y <= l.floor() + 6; y++) {
                    BlockState s;
                    if (side && i == 0) {
                        // 段の両脇の灯りの柱
                        s = y <= l.floor() ? TideKit.BRICK : y == l.floor() + 1 ? Blocks.PRISMARINE_WALL.defaultBlockState()
                                : y == l.floor() + 2 ? TideKit.LAMP : TideKit.AIR;
                    } else if (side) {
                        s = y < SEA ? water : TideKit.AIR;
                    } else if (y < top) {
                        s = TideKit.SANDSTONE;
                    } else if (y == top) {
                        s = Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST)
                                .setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, y < SEA);
                    } else {
                        s = y < SEA ? water : TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 潮門の廊下: 光る面のうしろ (x -52) から浜の西の壁 (x -33) まで。床 64、x -36・-35 の段で浜の床 66 へ
        for (int x = -52; x <= -33; x++) {
            int f = x <= -37 ? GATE_FLOOR : x == -36 ? GATE_FLOOR + 1 : BEACH.floor();
            for (int z = GATE_HALL_Z1 - 1; z <= GATE_HALL_Z2 + 1; z++) {
                boolean wall = z == GATE_HALL_Z1 - 1 || z == GATE_HALL_Z2 + 1;
                for (int y = GATE_FLOOR - 2; y <= f + 5; y++) {
                    BlockState s;
                    if (wall) {
                        s = Math.floorMod(x, 6) == 0 && y == f + 2 ? TideKit.LAMP : kit.wall(x, y, z);
                    } else if (y < f) {
                        s = TideKit.BRICK;
                    } else if (y == f) {
                        if (x == -36 || x == -35) {
                            kit.stair(x, y, z, Direction.EAST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                            continue;
                        }
                        s = TideKit.FLOOR;
                    } else if (y == f + 5) {
                        s = TideKit.DARK;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
    }

    private static BlockState landingFloor(int x, int z) {
        int h = Math.floorMod(x * 5 + z * 7, 11);
        return h < 6 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : h < 9 ? Blocks.CUT_SANDSTONE.defaultBlockState() : TideKit.ROUGH;
    }

    // 地上へ戻る見送りの段 (いちばん下の段とその先の水) に入っているか
    public static boolean inReturnGate(double x, double y, double z) {
        return x >= GATE_X - 1 && x < GATE_X + 2 && z >= GATE_Z && z < GATE_Z + 2 && y >= GATE_Y0 - 0.5 && y < GATE_Y1;
    }
    // ---------------------------------------------------------------- 縁のくぐり道 (浜 66 → 広間の南の段 74)
    static int caveFloor(int z) {
        int len = CAVE_Z_BEACH - CAVE_Z_HUB;
        return BEACH.floor() + (int) Math.round((HUB_LEDGE - BEACH.floor()) * (CAVE_Z_BEACH - z) / (double) len);
    }

    private static void cave(TideKit kit) {
        for (int z = CAVE_Z_BEACH + 1; z >= CAVE_Z_HUB - 1; z--) {
            int f = z > CAVE_Z_BEACH ? BEACH.floor() : caveFloor(Math.max(z, CAVE_Z_HUB));
            for (int x = CAVE_X1 - 1; x <= CAVE_X2 + 1; x++) {
                boolean wall = x < CAVE_X1 || x > CAVE_X2;
                for (int y = f; y <= f + 5; y++) {
                    if (wall) {
                        kit.set(x, y, z, z % 6 == 0 && y == f + 2 ? TideKit.LAMP : kit.wall(x, y, z));
                    } else if (y == f) {
                        boolean step = z <= CAVE_Z_BEACH && z > CAVE_Z_HUB - 1 && caveFloor(z) > caveFloor(z + 1);
                        if (step) {
                            kit.set(x, y - 1, z, TideKit.FLOOR);
                            kit.stair(x, y, z, Direction.NORTH, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else if (y == f + 5) {
                        kit.set(x, y, z, TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 潮の広間
    private static void hub(TideKit kit) {
        for (int x = -HUB_R - 3; x <= HUB_R + 3; x++) {
            for (int z = -HUB_R - 3; z <= HUB_R + 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                for (int y = HUB_FLOOR - 1; y <= HUB_CEILING + 2; y++) {
                    BlockState s;
                    if (r <= HUB_R + 0.5) {
                        if (y < HUB_FLOOR) {
                            s = TideKit.BRICK;
                        } else if (y == HUB_FLOOR) {
                            s = hubFloor(x, z, r);
                        } else if (y < HUB_CEILING) {
                            s = TideKit.AIR;
                        } else if (Math.hypot(x, z - MOON_CZ) <= MOON_R + 0.5) {
                            s = TideKit.AIR;
                        } else if (Math.hypot(x, z - MOON_CZ) <= MOON_R + 1.5) {
                            s = TideKit.DARK;
                        } else {
                            s = (y == HUB_CEILING && Math.floorMod(x * 3 + z * 7, 13) == 0) ? TideKit.LAMP : TideKit.BRICK;
                        }
                    } else if (r <= HUB_R + 2.5) {
                        // 壁: 潮の高さ (71〜73) は暗い帯、上は真珠層の筋、灯りは縁の段の高さ
                        if (y >= HUB_FLOOR + 1 && y <= HUB_FLOOR + 3) {
                            s = TideKit.DARK;
                        } else if (y == HUB_LEDGE + 3 && Math.floorMod((int) Math.round(Math.atan2(z, x) * 12 / Math.PI), 3) == 0 && r <= HUB_R + 1.5) {
                            s = TideKit.LAMP;
                        } else {
                            s = kit.wall(x, y, z);
                        }
                    } else {
                        continue;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 月の窓: 天井の穴 (中心 (0, MOON_CZ)) から、甲羅と頂の塔の中を抜けて、塔の柱の段の床 (TOWER_DECK) まで縦穴。
        // 柱の段の床はガラス (上から広間が見え、落ちない)。日と月の光は塔の屋根のガラスから、まっすぐ広間の水盤まで落ちる
        for (int x = -MOON_R - 1; x <= MOON_R + 1; x++) {
            for (int z = MOON_CZ - MOON_R - 1; z <= MOON_CZ + MOON_R + 1; z++) {
                double r = Math.hypot(x, z - MOON_CZ);
                for (int y = HUB_CEILING; y <= TOWER_DECK; y++) {
                    if (r <= MOON_R + 0.5) {
                        kit.set(x, y, z, y == TOWER_DECK ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : TideKit.AIR);
                    } else if (r <= MOON_R + 1.5) {
                        kit.set(x, y, z, y % 6 == 0 && y < TOWER_DECK ? TideKit.LAMP : TideKit.DARK);
                    }
                }
            }
        }
        // 縁の段 (南・西はつながる。東の回廊は離れる)。段の下は詰める
        for (Area a : new Area[] {LEDGE_S, LEDGE_W, GALLERY_E}) {
            for (int x = a.x1(); x <= a.x2(); x++) {
                for (int z = a.z1(); z <= a.z2(); z++) {
                    for (int y = HUB_FLOOR; y < HUB_LEDGE; y++) {
                        kit.set(x, y, z, TideKit.BRICK);
                    }
                    kit.set(x, HUB_LEDGE, z, (x + z) % 4 == 0 ? TideKit.DARK : TideKit.FLOOR);
                    for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 5; y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 段の縁の手すり (水盤の側の角に低い壁)。東の回廊の縁は開けておく (水から上がるため)
        // 南の段から水盤の底へ下りる階段
        for (int i = 0; i < 3; i++) {
            int z = 7 - i;
            int y = HUB_LEDGE - 1 - i;
            for (int x = HUB_STAIR_X1; x <= HUB_STAIR_X2; x++) {
                for (int yy = HUB_FLOOR + 1; yy < y; yy++) {
                    kit.set(x, yy, z, TideKit.BRICK);
                }
                kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
            }
        }
        // 干潟の門の先: 北へ下りる通路 (広間の床 70 → 干潟の前室 66)
        for (int z = FLATS_GATE_Z; z >= F0.z2() + 1; z--) {
            int f = switch (z) {
                case -13, -14 -> HUB_FLOOR;
                case -15 -> 69;
                case -16 -> 68;
                case -17 -> 67;
                default -> 66;
            };
            for (int x = -2; x <= 2; x++) {
                for (int y = f - 1; y <= f + 5; y++) {
                    boolean wall = Math.abs(x) == 2;
                    if (wall) {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    } else if (y == f - 1) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else if (y == f) {
                        if (z <= -15 && z >= -17) {
                            kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else if (y == f + 5 || (z == FLATS_GATE_Z && y == f + 4)) {
                        // 門の段は口を 3 マスの高さにする (水面の上のすき間から泳いで抜けないように)
                        kit.set(x, y, z, TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 西の段から庭の入口 g0 へ下りる通路 (x -13 → -18。74 → 68)
        for (int x = -13; x >= -18; x--) {
            int f = HUB_LEDGE + 1 + x; // -13: 72? を避けるため下で補正
            f = switch (x) {
                case -13 -> 73;
                case -14 -> 72;
                case -15 -> 71;
                case -16 -> 70;
                case -17 -> 69;
                default -> 68;
            };
            for (int z = -2; z <= 2; z++) {
                for (int y = f - 1; y <= f + 6; y++) {
                    boolean wall = Math.abs(z) == 2;
                    if (wall) {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    } else if (y == f - 1) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else if (y == f) {
                        if (x >= -17) {
                            kit.stair(x, y, z, Direction.EAST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else if (y == f + 6) {
                        kit.set(x, y, z, TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 東の回廊から潮見の前室への道
        Area h = EAST_HALL;
        kit.room(h.x1(), h.x2(), h.z1(), h.z2(), h.floor(), h.height(), TideKit.FLOOR);
    }

    private static BlockState hubFloor(int x, int z, double r) {
        if (Math.abs(x) <= BOTTOM_GATE_R && Math.abs(z) <= BOTTOM_GATE_R) {
            return TideKit.DARK;
        }
        int ring = (int) Math.round(r);
        if (ring == 4 || ring == 8) {
            return TideKit.DARK;
        }
        return ring % 2 == 0 ? TideKit.FLOOR : Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 回り道 (干潮の道)
    // 点を結ぶ幅 3 の通路。{x, z, 床の高さ} を返す (同じ所は一度だけ)
    public static List<int[]> rampCells() {
        List<int[]> cells = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (int i = 0; i + 1 < RAMP.length; i++) {
            int[] a = RAMP[i];
            int[] b = RAMP[i + 1];
            int len = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int k = 0; k <= len; k++) {
                int cx = a[0] + Integer.signum(b[0] - a[0]) * k;
                int cz = a[1] + Integer.signum(b[1] - a[1]) * k;
                int f = a[2] + (int) Math.floor((b[2] - a[2]) * k / (double) Math.max(1, len) + 1e-9);
                if (k == len) {
                    f = b[2];
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long key = ((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL);
                        if (seen.add(key)) {
                            cells.add(new int[] {cx + dx, cz + dz, f});
                        }
                    }
                }
            }
        }
        return cells;
    }

    private static void ramp(TideKit kit) {
        List<int[]> cells = rampCells();
        java.util.Map<Long, Integer> floorAt = new java.util.HashMap<>();
        for (int[] c : cells) {
            floorAt.put(((long) c[0] << 32) ^ (c[1] & 0xffffffffL), c[2]);
        }
        // 外壁 (通路のまわり 1 マス) を先に、それから中を掘る
        for (int[] c : cells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long k = ((long) (c[0] + dx) << 32) ^ ((c[1] + dz) & 0xffffffffL);
                    if (floorAt.containsKey(k) || Math.sqrt((c[0] + dx) * (c[0] + dx) + (c[1] + dz) * (c[1] + dz)) <= HUB_R + 0.5) {
                        continue;
                    }
                    for (int y = c[2]; y <= c[2] + 5; y++) {
                        // ほかの通路 (東の回廊への道) で空いている所はふさがない
                        if (!kit.get(c[0] + dx, y, c[1] + dz).isAir()) {
                            kit.set(c[0] + dx, y, c[1] + dz, kit.wall(c[0] + dx, y, c[1] + dz));
                        }
                    }
                }
            }
        }
        for (int[] c : cells) {
            if (Math.sqrt(c[0] * c[0] + c[1] * c[1]) <= HUB_R + 0.5) {
                // 広間の中 (床と天井はそのまま)
                continue;
            }
            kit.set(c[0], c[2] - 1, c[1], TideKit.BRICK);
            // 一段上がる所は階段 (登る向きは、低い隣の反対)
            Direction up = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Integer n = floorAt.get(((long) (c[0] - d.getStepX()) << 32) ^ ((c[1] - d.getStepZ()) & 0xffffffffL));
                if (n != null && n == c[2] - 1) {
                    up = d;
                }
            }
            if (up != null) {
                kit.stair(c[0], c[2], c[1], up, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
            } else {
                kit.set(c[0], c[2], c[1], TideKit.FLOOR);
            }
            for (int y = c[2] + 1; y <= c[2] + 4; y++) {
                kit.set(c[0], y, c[1], TideKit.AIR);
            }
            kit.set(c[0], c[2] + 5, c[1], (c[0] + c[1]) % 5 == 0 ? TideKit.LAMP : TideKit.DARK);
        }
    }

    // ---------------------------------------------------------------- 珊瑚の庭 (西)
    // 水路の L 字 (幅 3): 始めは z の向き、次に x の向き
    public static List<BlockPos> channelCells(Channel c) {
        List<BlockPos> list = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int fx = c.from[0];
        int fz = c.from[1];
        int tx = c.to[0];
        int tz = c.to[1];
        for (int z = Math.min(fz, tz); z <= Math.max(fz, tz); z++) {
            for (int d = -1; d <= 1; d++) {
                add(list, seen, fx + d, z);
            }
        }
        for (int x = Math.min(fx, tx); x <= Math.max(fx, tx); x++) {
            for (int d = -1; d <= 1; d++) {
                add(list, seen, x, tz + d);
            }
        }
        return list;
    }

    private static void add(List<BlockPos> list, Set<Long> seen, int x, int z) {
        if (seen.add(((long) x << 32) ^ (z & 0xffffffffL))) {
            list.add(new BlockPos(x, 0, z));
        }
    }

    // 庭の天井 (その柱の、いちばん上の空気の y)。甲羅の地表から 4 段下で、高くても GARDEN_CEILING + 2
    static int gardenCeiling(int x, int z) {
        int top = shellTop(x, z);
        if (top == Integer.MIN_VALUE) {
            return GARDEN_FLOOR;
        }
        return Math.min(top - 4, GARDEN_CEILING + (int) Math.floor(noise(x, z, 7, 11) * 2.99));
    }

    // 庭は甲羅の中の洞: 礁の壁 (水路の外) を詰め、その上を天井まで空ける。縁 (四方と、甲羅が低くて天井が取れない所) は天井まで壁。
    // 天井は真珠層と珊瑚で、ところどころ垂れた珊瑚と灯り。日の光は光の井戸 (lightWells) から差す
    private static void garden(TideKit kit) {
        Area g = GARDEN;
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                int bump = gardenBump(x, z);
                int ceil = gardenCeiling(x, z);
                boolean edge = x == g.x1() || x == g.x2() || z == g.z1() || z == g.z2() || ceil < GARDEN_WALL_TOP + 5;
                for (int y = 58; y <= Math.max(ceil, GARDEN_WALL_TOP) + 1; y++) {
                    BlockState s;
                    if (edge) {
                        s = gardenWall(x, y, z);
                    } else if (y <= GARDEN_WALL_TOP + bump) {
                        s = y > GARDEN_WALL_TOP - 1 + bump ? gardenCap(x, z) : gardenWall(x, y, z);
                    } else if (y <= ceil) {
                        s = TideKit.AIR;
                    } else {
                        s = gardenRoof(x, z);
                    }
                    kit.set(x, y, z, s);
                }
                if (edge) {
                    continue;
                }
                // 珊瑚の芽 (枯れた珊瑚の扇と、ときどき高い枝)
                double sp = noise(x, z, 2.2, 7);
                if (sp > 0.86) {
                    int hgt = 1 + (int) ((sp - 0.86) * 30);
                    for (int y = 1; y <= hgt; y++) {
                        kit.set(x, GARDEN_WALL_TOP + bump + y, z, reef(x + z));
                    }
                } else if (sp > 0.74) {
                    kit.set(x, GARDEN_WALL_TOP + bump + 1, z, deadFan(x * 3 + z));
                }
                // 天井から垂れた珊瑚 (壁の上より 4 段以上の高さだけ)
                double st = noise(x, z, 3.1, 12);
                if (st > 0.8) {
                    int len = 1 + (int) ((st - 0.8) * 20);
                    for (int i = 0; i < len && ceil - i > GARDEN_WALL_TOP + bump + 4; i++) {
                        kit.set(x, ceil - i, z, reef(x * 2 + z + i));
                    }
                }
            }
        }
        // 大貝の淵 (半径 3 の窪み。床 64)。大貝そのものは道の形と一緒に置く
        for (int x = CLAM[0] - 3; x <= CLAM[0] + 3; x++) {
            for (int z = CLAM[1] - 3; z <= CLAM[1] + 3; z++) {
                if (Math.hypot(x - CLAM[0], z - CLAM[1]) <= 3.3) {
                    kit.set(x, GARDEN_FLOOR, z, TideKit.SAND);
                    for (int y = GARDEN_FLOOR + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        carveGardenRoute(kit, 0, true);
    }

    // 庭の天井の石: 真珠層と珊瑚、ところどころ灯り (海のランタンと、珊瑚の色に合う菌の灯り)
    static BlockState gardenRoof(int x, int z) {
        int h = Math.floorMod(x * 7 + z * 11, 17);
        if (h == 0) {
            return TideKit.LAMP;
        }
        if (h == 9) {
            return Blocks.SHROOMLIGHT.defaultBlockState();
        }
        double n = noise(x, z, 4, 13);
        return n < 0.5 ? TideKit.NACRE : reef((int) (n * 11));
    }
    static int gardenBump(int x, int z) {
        return (int) Math.floor(noise(x, z, 5, 5) * 2.99);
    }

    // 壁のいちばん上 (苔と珊瑚)
    static BlockState gardenCap(int x, int z) {
        double n = noise(x, z, 4, 6);
        return n < 0.45 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.7 ? reef((int) (n * 13)) : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    static BlockState gardenWall(int x, int y, int z) {
        int h = Math.floorMod(x * 5 + y * 3 + z * 11 + (y / 3) * 7, 17);
        if (h < 6) {
            return reef(h + y / 2);
        }
        if (h < 9) {
            return Blocks.TERRACOTTA.defaultBlockState();
        }
        if (h < 12) {
            return Blocks.TUFF_BRICKS.defaultBlockState();
        }
        if (h < 14) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.WHITE_TERRACOTTA.defaultBlockState();
    }

    // 庭の道の形を置く: すべての水路を壁でふさいでから、選んだ形の水路と、いつも開いている水路 (大貝〜A、g0〜C) を掘る。
    // 台・祭壇・入口の段は最後に置き直す (水路が台を通るため)
    public static void carveGardenRoute(TideKit kit, int route, boolean full) {
        for (Channel c : Channel.values()) {
            for (BlockPos p : channelCells(c)) {
                int bump = gardenBump(p.getX(), p.getZ());
                for (int y = GARDEN_FLOOR; y <= GARDEN_WALL_TOP + bump; y++) {
                    kit.set(p.getX(), y, p.getZ(), y > GARDEN_WALL_TOP - 1 + bump ? gardenCap(p.getX(), p.getZ()) : gardenWall(p.getX(), y, p.getZ()));
                }
            }
        }
        List<Channel> open = new ArrayList<>(List.of(GARDEN_ROUTES[Math.floorMod(route, GARDEN_ROUTES.length)]));
        open.add(Channel.CLAM_A);
        open.add(Channel.G0_C);
        for (Channel c : open) {
            for (BlockPos p : channelCells(c)) {
                kit.set(p.getX(), GARDEN_FLOOR, p.getZ(), noise(p.getX(), p.getZ(), 3, 8) < 0.25 ? Blocks.GRAVEL.defaultBlockState() : TideKit.SAND);
                for (int y = GARDEN_FLOOR + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                    kit.set(p.getX(), y, p.getZ(), TideKit.AIR);
                }
            }
        }
        // 台 (9x9 の段。上の 3x3 が床 68)
        for (int[] c : new int[][] {PLAT_A, PLAT_B1, PLAT_B2, PLAT_C, PLAT_D1, PLAT_D2}) {
            ziggurat(kit, c[0], c[1], GARDEN_FLOOR, PLATFORM_TOP, open, c == PLAT_C);
        }
        // 祭壇の台 (床 69。上は 5x5)
        ziggurat(kit, ALTAR[0], ALTAR[1], GARDEN_FLOOR, ALTAR_TOP, open, false);
        // g1 の焚き火と、台の灯りを置き直す (台を建て直したため)
        Rest g1 = RESTS.get(2);
        kit.campfire(g1.campfire());
        if (!full) {
            return;
        }
        // 入口の段 g0 (x -24〜-19、z -3〜3、床 68) と、水路へ下りる階段 (西へ)
        for (int x = G0.x1(); x <= G0.x2(); x++) {
            for (int z = G0.z1(); z <= G0.z2(); z++) {
                for (int y = GARDEN_FLOOR; y <= PLATFORM_TOP; y++) {
                    kit.set(x, y, z, y == PLATFORM_TOP ? TideKit.FLOOR : TideKit.SANDSTONE);
                }
                for (int y = PLATFORM_TOP + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int x = G0.x1() - 1 - i;
            for (int z = -1; z <= 1; z++) {
                for (int y = GARDEN_FLOOR; y < PLATFORM_TOP - 1 - i; y++) {
                    kit.set(x, y, z, TideKit.SANDSTONE);
                }
                kit.stair(x, PLATFORM_TOP - 1 - i, z, Direction.EAST, Blocks.SANDSTONE_STAIRS.defaultBlockState());
                for (int y = PLATFORM_TOP - i; y <= GARDEN_WALL_TOP + 2; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 大貝 (淵の真ん中の台。真珠は試練の側が置く)
        int cx = CLAM[0];
        int cz = CLAM[1];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.set(cx + dx, GARDEN_FLOOR + 1, cz + dz, Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            kit.set(cx + dx, GARDEN_FLOOR + 2, cz + 1, Blocks.PINK_TERRACOTTA.defaultBlockState());
            kit.set(cx + dx, GARDEN_FLOOR + 3, cz + 1, dx == 0 ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.PINK_TERRACOTTA.defaultBlockState());
        }
        kit.set(cx - 1, GARDEN_FLOOR + 2, cz, Blocks.PINK_TERRACOTTA.defaultBlockState());
        kit.set(cx + 1, GARDEN_FLOOR + 2, cz, Blocks.PINK_TERRACOTTA.defaultBlockState());
    }

    // 段の台: 外周から内へ一段ずつ上がる。上の床は top (中心の 3x3、祭壇は 5x5)
    private static void ziggurat(TideKit kit, int cx, int cz, int floor, int top, List<Channel> open, boolean rest) {
        int steps = top - floor;
        int coreR = top == ALTAR_TOP ? 2 : 1;
        for (int dx = -coreR - steps; dx <= coreR + steps; dx++) {
            for (int dz = -coreR - steps; dz <= coreR + steps; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                int h = ring <= coreR ? top : top - (ring - coreR);
                if (h <= floor) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                for (int y = floor; y <= h; y++) {
                    kit.set(x, y, z, y == h ? (ring <= coreR ? TideKit.FLOOR : Blocks.SMOOTH_SANDSTONE.defaultBlockState()) : TideKit.SANDSTONE);
                }
                for (int y = h + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 台の隅に灯り (潮除けの台の目印)
        kit.set(cx + coreR, top + 1, cz + coreR, Blocks.PRISMARINE_WALL.defaultBlockState());
        kit.set(cx + coreR, top + 2, cz + coreR, TideKit.LAMP);
    }

    // ---------------------------------------------------------------- 潮見の間 (東)
    private static void eastRoom(TideKit kit) {
        // 外枠を詰め、上を空へ開ける。四方に高い胸壁 (外へ飛び降りないように)
        for (int x = 23; x <= 58; x++) {
            for (int z = -37; z <= -4; z++) {
                boolean edge = x == 23 || x == 58 || z == -37 || z == -4;
                for (int y = 58; y <= 96; y++) {
                    if (y < HUB_LEDGE) {
                        kit.set(x, y, z, TideKit.SHELL);
                    } else if (edge) {
                        kit.set(x, y, z, y == 96 ? TideKit.DARK : kit.wall(x, y, z));
                    } else if (y == HUB_LEDGE) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 西の壁の口 (回り道の終わり z -11〜-9 から前室へ)
        for (int z = -11; z <= -9; z++) {
            for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 4; y++) {
                kit.set(23, y, z, TideKit.AIR);
            }
            kit.set(23, HUB_LEDGE, z, TideKit.FLOOR);
        }
        // 前室 s0 と段 0 (床 74)。前室は屋根付き
        Area s0 = S0;
        for (int x = s0.x1(); x <= TIER0.x2(); x++) {
            for (int z = s0.z1(); z <= s0.z2(); z++) {
                kit.set(x, HUB_LEDGE, z, (x + z) % 3 == 0 ? TideKit.DARK : TideKit.FLOOR);
            }
        }
        for (int x = s0.x1(); x <= s0.x2(); x++) {
            for (int z = s0.z1() - 1; z <= s0.z2() + 1; z++) {
                kit.set(x, HUB_LEDGE + 6, z, TideKit.DARK);
            }
        }
        // 前室の外 (段 0 の南北と、西の通路の口以外) は壁で囲む
        for (int x = 24; x <= 33; x++) {
            for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 6; y++) {
                kit.set(x, y, s0.z1() - 1, kit.wall(x, y, s0.z1() - 1));
                kit.set(x, y, s0.z2() + 1, kit.wall(x, y, s0.z2() + 1));
            }
        }
        // 段 1・段 2・岩棚 (下は詰める)
        tierBlock(kit, TIER1, TideKit.FLOOR);
        tierBlock(kit, TIER2, TideKit.FLOOR);
        tierBlock(kit, LEDGE_E, TideKit.FLOOR);
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
                            kit.set(x, y, z, y < bottom ? TideKit.SHELL : y == bottom ? TideKit.DARK : TideKit.AIR);
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
        // 上の溜めの北の戸から岩棚へ上る階段 (x 52〜54、z -23〜-30)
        for (int i = 0; i <= 7; i++) {
            int z = -23 - i;
            int f = Math.min(LEDGE_FLOOR, TIER_FLOOR[2] + i);
            for (int x = 51; x <= 55; x++) {
                boolean wall = x == 51 || x == 55;
                for (int y = HUB_LEDGE; y <= f + 4; y++) {
                    if (wall) {
                        kit.set(x, y, z, y <= f + 1 ? kit.wall(x, y, z) : TideKit.AIR);
                    } else if (y < f) {
                        kit.set(x, y, z, TideKit.SHELL);
                    } else if (y == f) {
                        if (i > 0 && f > TIER_FLOOR[2] + i - 1 && f < LEDGE_FLOOR + 1 && i <= LEDGE_FLOOR - TIER_FLOOR[2]) {
                            kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
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
            kit.set(55, y, -36, TideKit.DARK);
        }
    }

    private static void tierBlock(TideKit kit, Area a, BlockState top) {
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                for (int y = HUB_LEDGE; y <= a.floor(); y++) {
                    kit.set(x, y, z, y == a.floor() ? top : TideKit.BRICK);
                }
            }
        }
    }

    // 溜めの壁。浮き石の届く高さ (5 段目) に目盛りの灯り
    private static BlockState basinWall(int x, int y, int z, int k) {
        int mark = TIER_FLOOR[k] + 5;
        if (y == mark && (x + z) % 2 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return (y - TIER_FLOOR[k]) % 2 == 0 ? TideKit.DARK : TideKit.BRICK;
    }

    // ---------------------------------------------------------------- 群れの干潟 (前): 甲羅の前の洞。北の壁の向こうは首
    // 窓 (北西・北東の角): 甲羅の前の縁を抜ける細い窓。ガラスの向こうに首と頭と海が見える
    public static final int[][] FLATS_WINDOWS = {{-20, 0}, {18, 0}};

    private static void flats(TideKit kit) {
        Area f = FLATS;
        for (int x = f.x1() - 2; x <= f.x2() + 2; x++) {
            for (int z = f.z1() - 2; z <= f.z2() + 2; z++) {
                boolean inside = x >= f.x1() && x <= f.x2() && z >= f.z1() && z <= f.z2();
                int edge = inside ? Math.min(Math.min(x - f.x1(), f.x2() - x), Math.min(z - f.z1(), f.z2() - z)) : -1;
                // 天井は壁ぎわで低く (74)、内へ 4 マスで FLATS_CEILING
                int ceil = FLATS_CEILING - Math.max(0, 4 - edge);
                for (int y = 58; y <= FLATS_CEILING + 1; y++) {
                    BlockState s;
                    if (!inside) {
                        s = rock(x, y, z);
                    } else if (y < FLATS_FLOOR) {
                        s = TideKit.SANDSTONE;
                    } else if (y == FLATS_FLOOR) {
                        s = flatsFloor(x, z);
                    } else if (y <= ceil) {
                        s = TideKit.AIR;
                    } else {
                        s = Math.floorMod(x * 3 + z * 7, 19) == 0 ? TideKit.LAMP : rock(x, y, z);
                    }
                    kit.set(x, y, z, s);
                }
                // 天井から垂れた石 (鍾乳石)。干潟の真ん中の高い所だけ
                if (inside && edge >= 6 && noise(x, z, 3, 14) > 0.82) {
                    int len = 1 + (int) ((noise(x, z, 3, 14) - 0.82) * 25);
                    for (int i = 0; i < len; i++) {
                        kit.set(x, ceil - i, z, i == len - 1 ? Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN) : rock(x, ceil - i, z));
                    }
                }
            }
        }
        // 岩の島 (7x7 の段。上 3x3 が床 66)
        for (int[] r : FLATS_ROCKS) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    int h = ring <= 1 ? ROCK_TOP : ring == 2 ? ROCK_TOP - 1 : ROCK_TOP - 2;
                    for (int y = FLATS_FLOOR; y <= h; y++) {
                        kit.set(r[0] + dx, y, r[1] + dz, y == h ? (ring <= 1 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState()) : Blocks.ANDESITE.defaultBlockState());
                    }
                }
            }
        }
        // 北の角の窓: 干潟の北の壁から甲羅の前の縁の外まで、幅 3・高さ 2 の穴。外の端にガラス
        for (int[] w : FLATS_WINDOWS) {
            int z = f.z1() - 1;
            while (z > f.z1() - 16 && !windowOpen(kit, w[0], z)) {
                for (int dx = -1; dx <= 1; dx++) {
                    kit.set(w[0] + dx, FLATS_FLOOR + 2, z, rock(w[0] + dx, FLATS_FLOOR + 2, z));
                    kit.set(w[0] + dx, FLATS_FLOOR + 3, z, TideKit.AIR);
                    kit.set(w[0] + dx, FLATS_FLOOR + 4, z, TideKit.AIR);
                    kit.set(w[0] + dx, FLATS_FLOOR + 5, z, TideKit.DARK);
                }
                z--;
            }
            for (int dx = -1; dx <= 1; dx++) {
                kit.set(w[0] + dx, FLATS_FLOOR + 3, z + 1, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                kit.set(w[0] + dx, FLATS_FLOOR + 4, z + 1, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
        }
        // 干潟の前室 f0 と、干潟へのトンネル (床 66)、干潟の口と、干潟へ下りる段
        Area f0 = F0;
        kit.room(f0, TideKit.FLOOR);
        for (int z = f0.z1() - 1; z >= MOUTH_Z; z--) {
            for (int x = -2; x <= 2; x++) {
                for (int y = TUNNEL_FLOOR - 1; y <= TUNNEL_FLOOR + 5; y++) {
                    boolean wall = Math.abs(x) == 2;
                    BlockState s;
                    if (wall) {
                        s = z % 7 == 0 && y == TUNNEL_FLOOR + 2 ? TideKit.LAMP : kit.wall(x, y, z);
                    } else if (y < TUNNEL_FLOOR) {
                        s = TideKit.BRICK;
                    } else if (y == TUNNEL_FLOOR) {
                        s = TideKit.FLOOR;
                    } else if (y == TUNNEL_FLOOR + 5 || (z == MOUTH_Z && y == TUNNEL_FLOOR + 4)) {
                        s = TideKit.DARK;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            int z = MOUTH_Z - 1 - i;
            for (int x = -1; x <= 1; x++) {
                for (int y = FLATS_FLOOR; y < TUNNEL_FLOOR - 1 - i; y++) {
                    kit.set(x, y, z, Blocks.ANDESITE.defaultBlockState());
                }
                kit.stair(x, TUNNEL_FLOOR - 1 - i, z, Direction.SOUTH, Blocks.STONE_STAIRS.defaultBlockState());
            }
        }
    }

    // 窓の穴が外 (甲羅の外の空気) に抜けたか: 穴の高さで、真ん中の 1 列の先が空気
    private static boolean windowOpen(TideKit kit, int x, int z) {
        return kit.get(x, FLATS_FLOOR + 3, z).isAir() && shellTop(x, z) < FLATS_FLOOR + 3;
    }
    private static BlockState flatsFloor(int x, int z) {
        double n = noise(x, z, 5, 9) * 0.75 + noise(x, z, 2, 10) * 0.25;
        return n < 0.55 ? TideKit.SAND : n < 0.72 ? Blocks.MUD.defaultBlockState() : n < 0.85 ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    private static BlockState rock(int x, int y, int z) {
        int h = Math.floorMod(x * 3 + y * 5 + z * 7, 9);
        return h < 4 ? Blocks.STONE.defaultBlockState() : h < 7 ? Blocks.ANDESITE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 潮の底の門の下: 螺旋の階段と、殻の底の前室への通路
    // 螺旋の段の並び (中心の柱のまわり 8 マス)。最後の段 (18 番目) は北西の角、そこから北の通路へ
    public static final int[][] SPIRAL_RING = {{-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}};
    public static final int SPIRAL_STEPS = SPIRAL_TOP - SPIRAL_BOTTOM + 1;

    private static void spiral(TideKit kit) {
        // 外枠 (海の中に石の塔)
        for (int x = -5; x <= 5; x++) {
            for (int z = -10; z <= 4; z++) {
                for (int y = 48; y < HUB_FLOOR; y++) {
                    kit.set(x, y, z, TideKit.BRICK);
                }
            }
        }
        // 縦穴 (x -1〜1、z -1〜1。中心は柱)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = SPIRAL_BOTTOM; y < HUB_FLOOR; y++) {
                    kit.set(x, y, z, x == 0 && z == 0 ? (y % 4 == 0 ? TideKit.LAMP : TideKit.DARK) : TideKit.AIR);
                }
                kit.set(x, SPIRAL_BOTTOM - 1, z, TideKit.FLOOR);
            }
        }
        for (int i = 0; i < SPIRAL_STEPS; i++) {
            int[] c = SPIRAL_RING[i % 8];
            int[] prev = SPIRAL_RING[Math.floorMod(i - 1, 8)];
            int y = SPIRAL_TOP - i;
            Direction up = Direction.fromDelta(prev[0] - c[0], 0, prev[1] - c[1]);
            kit.stair(c[0], y, c[1], up == null ? Direction.NORTH : up, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        }
        // 底から北の通路 (z -2〜-9) と、休み場の小部屋 (x -4〜4、z -9〜-3)
        Area r = HEART_REST;
        for (int x = r.x1() - 1; x <= r.x2() + 1; x++) {
            for (int z = r.z1() - 1; z <= r.z2() + 1; z++) {
                for (int y = r.floor(); y <= r.floor() + r.height() + 1; y++) {
                    boolean in = x >= r.x1() && x <= r.x2() && z >= r.z1() && z <= r.z2() && y > r.floor() && y <= r.floor() + r.height();
                    if (in) {
                        kit.set(x, y, z, TideKit.AIR);
                    } else if (y == r.floor()) {
                        kit.set(x, y, z, TideKit.FLOOR);
                    } else {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    }
                }
            }
        }
        // 縦穴から小部屋への口 (0, 51〜53, -2)
        for (int y = SPIRAL_BOTTOM; y <= SPIRAL_BOTTOM + 2; y++) {
            kit.set(0, y, -2, TideKit.AIR);
        }
        // 小部屋の北の口 (x -1〜1、z -10) は殻の底の前室へ
        for (int x = -1; x <= 1; x++) {
            for (int y = SPIRAL_BOTTOM; y <= SPIRAL_BOTTOM + 2; y++) {
                kit.set(x, y, -10, TideKit.AIR);
            }
            kit.set(x, SPIRAL_BOTTOM - 1, -10, TideKit.FLOOR);
        }
        kit.set(-4, SPIRAL_BOTTOM + 3, -3, TideKit.LAMP);
        kit.set(4, SPIRAL_BOTTOM + 3, -9, TideKit.LAMP);
    }

    // ---------------------------------------------------------------- 掘った所の内壁を、潮の民の石にする (体の芯の凝灰岩が見えたら)
    private static void skin(TideKit kit) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int x = -SHELL_RX - 2; x <= SHELL_RX + 2; x++) {
            for (int z = SHELL_CZ - SHELL_RZ - 2; z <= SHELL_CZ + SHELL_RZ + 2; z++) {
                int top = shellTop(x, z);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                boolean garden = x >= GARDEN.x1() && x <= GARDEN.x2() && z >= GARDEN.z1() && z <= GARDEN.z2();
                for (int y = TideTurtle.minY(); y <= top - 3; y++) {
                    m.set(x, y, z);
                    if (!shellMaterial(kit.level().getBlockState(m))) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        n.setWithOffset(m, d);
                        if (kit.level().getBlockState(n).isAir()) {
                            kit.set(x, y, z, garden ? gardenWall(x, y, z) : kit.wall(x, y, z));
                            break;
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 甲羅の上へ上る段
    // 浜の西の壁 (x -33、z 44) の口から通路で西へ、5x5 の四角い螺旋 (中心 (-44, 44)) を甲羅の地表の口まで上る。
    // 口は珊瑚の房 (浜の真上) を避け、甲羅の板の平らな所 (y 107) に開く。
    // 段の列は外周の 16 マス。一段ずつ 1 上がり、どの段の上も 3 マス空いている (落ちても 1 段だけ)
    public static List<int[]> topStairRing() {
        int cx = TOP_STAIR_X1 + 2;
        int cz = TOP_STAIR_Z1 + 2;
        List<int[]> ring = new ArrayList<>();
        // 東の真ん中 (浜の側) から北へ、反時計回り (上から見て)
        int[][] dirs = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
        int x = cx + 2;
        int z = cz;
        int d = 0;
        for (int i = 0; i < 16; i++) {
            ring.add(new int[] {x, z});
            int nx = x + dirs[d][0];
            int nz = z + dirs[d][1];
            if (Math.max(Math.abs(nx - cx), Math.abs(nz - cz)) != 2) {
                d = (d + 1) % 4;
                nx = x + dirs[d][0];
                nz = z + dirs[d][1];
            }
            x = nx;
            z = nz;
        }
        return ring;
    }

    private static void topStair(TideKit kit) {
        int cx = TOP_STAIR_X1 + 2;
        int cz = TOP_STAIR_Z1 + 2;
        int y0 = BEACH.floor();
        // 外の壁 (半径 3)・段の列 (半径 2) を詰め、中の柱 (半径 1) に灯り。甲羅の地表より上には出さない
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int top = shellTop(x, z);
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                for (int y = y0; y <= top; y++) {
                    BlockState s = cheb <= 1 ? (y % 6 == 0 && cheb == 1 && (dx == 0 || dz == 0) ? TideKit.LAMP : TideKit.DARK)
                            : cheb == 2 ? TideKit.BRICK : kit.wall(x, y, z);
                    kit.set(x, y, z, s);
                }
            }
        }
        // 浜の西の壁の口 (x -33、z 44) から段の下まで、床 66 の短い通路
        for (int x = cx + 3; x <= BEACH.x1() - 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                for (int y = y0; y <= y0 + 4; y++) {
                    boolean wall = z != cz;
                    kit.set(x, y, z, y == y0 ? TideKit.FLOOR : wall || y == y0 + 4 ? (x % 4 == 0 && y == y0 + 2 && wall ? TideKit.LAMP : kit.wall(x, y, z))
                            : TideKit.AIR);
                }
            }
        }
        List<int[]> ring = topStairRing();
        int y = y0 + 1;
        int[] prev = {cx + 3, cz};
        for (int k = 0; k < 160; k++) {
            int[] c = ring.get(k % ring.size());
            Direction facing = Direction.fromDelta(c[0] - prev[0], 0, c[1] - prev[1]);
            kit.stair(c[0], y, c[1], facing == null ? Direction.WEST : facing, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
            int top = shellTop(c[0], c[1]);
            for (int yy = y + 1; yy <= y + 3; yy++) {
                kit.set(c[0], yy, c[1], TideKit.AIR);
            }
            if (y + 1 >= top) {
                // 地表に出た: 段の上を空まで開け、口のまわりに低い遺跡の枠 (半分埋まった石の段)
                for (int yy = y + 1; yy <= top + 3; yy++) {
                    kit.set(c[0], yy, c[1], TideKit.AIR);
                }
                topStairExit(kit, cx, cz, c);
                return;
            }
            prev = c;
            y++;
        }
    }

    private static void topStairExit(TideKit kit, int cx, int cz, int[] last) {
        // 口の上の 5x5 を地表の高さまで空け、まわり (半径 3) に崩れた低い石の壁。最後の段の側 (外へ出る側) は開ける
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int top = shellTop(x, z);
                boolean pillar = Math.max(Math.abs(dx), Math.abs(dz)) <= 1;
                if (pillar) {
                    kit.set(x, top + 1, z, TideKit.LAMP);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 3) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                boolean open = Math.abs(x - last[0]) <= 1 && Math.abs(z - last[1]) <= 1 || Math.abs(x - last[0]) + Math.abs(z - last[1]) <= 2;
                int top = shellTop(x, z);
                if (open) {
                    continue;
                }
                int h = Math.floorMod(x * 3 + z * 5, 3);
                for (int y = top + 1; y <= top + h; y++) {
                    kit.set(x, y, z, y == top + h ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }
    }

    // ---------------------------------------------------------------- 頂の塔の中
    // 南の口 (甲羅の地表から) → 土台の部屋 (縦穴のまわりの輪、床 114) → 縦穴のまわりの螺旋 (中心から 5) → 柱の段 (床 133)。
    // 柱の段の真ん中はガラスの床 (下に広間の水盤が見える。縦穴は hub が掘る)。柱の段の縁に低い手すり。屋根の真ん中はガラス (月の光)
    public static List<int[]> towerRing() {
        List<int[]> ring = new ArrayList<>();
        int r = 5;
        // 南の真ん中から東へ (上から見て反時計回り)
        int x = 0;
        int z = TOWER_CZ + r;
        int[][] dirs = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};
        int d = 0;
        for (int i = 0; i < 8 * r; i++) {
            ring.add(new int[] {x, z});
            int nx = x + dirs[d][0];
            int nz = z + dirs[d][1];
            if (Math.max(Math.abs(nx), Math.abs(nz - TOWER_CZ)) != r) {
                d = (d + 1) % 4;
                nx = x + dirs[d][0];
                nz = z + dirs[d][1];
            }
            x = nx;
            z = nz;
        }
        return ring;
    }

    private static void tower(TideKit kit) {
        int cz = TOWER_CZ;
        int floor = shellTop(0, cz);
        // 土台の部屋: 中心から 5〜7 の輪 (縦穴の壁の外)、高さ 4
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb < 5) {
                    continue;
                }
                kit.set(dx, floor, cz + dz, (dx + dz) % 3 == 0 ? TideKit.DARK : TideKit.FLOOR);
                for (int y = floor + 1; y <= floor + 4; y++) {
                    kit.set(dx, y, cz + dz, TideKit.AIR);
                }
                kit.set(dx, floor + 5, cz + dz, cheb == 7 && (dx == 0 || dz == 0) ? TideKit.LAMP : TideKit.DARK);
            }
        }
        // 南の口 (x -1〜1)。土台の外の地表まで
        for (int z = cz + 8; z <= cz + 14; z++) {
            int top = shellTop(0, z);
            if (z > cz + 9 && TideTurtle.top(0, z) <= top) {
                break;
            }
            for (int x = -1; x <= 1; x++) {
                kit.set(x, floor, z, TideKit.FLOOR);
                for (int y = floor + 1; y <= floor + 3; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
                kit.set(x, floor + 4, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            }
        }
        // 螺旋: 中心から 5 の輪を、土台の部屋の床の上から柱の段 (TOWER_DECK) まで
        List<int[]> ring = towerRing();
        int y = floor + 1;
        int[] prev = {0, cz + 6};
        for (int k = 0; y <= TOWER_DECK; k++) {
            int[] c = ring.get(k % ring.size());
            Direction facing = Direction.fromDelta(c[0] - prev[0], 0, c[1] - prev[1]);
            kit.stair(c[0], y, c[1], facing == null ? Direction.EAST : facing, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
            for (int yy = y + 1; yy <= y + 3; yy++) {
                kit.set(c[0], yy, c[1], TideKit.AIR);
            }
            prev = c;
            y++;
        }
        // 柱の段の縁の手すり (中心から 6、柱のない所)。床は塔の胴の上 (TOWER_DECK)
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 6) {
                    continue;
                }
                if (kit.get(dx, TOWER_DECK + 1, cz + dz).isAir() && !kit.get(dx, TOWER_DECK, cz + dz).isAir()) {
                    kit.set(dx, TOWER_DECK + 1, cz + dz, Blocks.PRISMARINE_WALL.defaultBlockState());
                }
            }
        }
        // 屋根の真ん中 (半径 2.5) はガラス: 日と月の光が、縦穴を通って広間の水盤まで落ちる
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.hypot(dx, dz) > 2.5) {
                    continue;
                }
                for (int yy = TOWER_DECK + 2; yy <= TideTurtle.maxY(); yy++) {
                    if (!kit.get(dx, yy, cz + dz).isAir()) {
                        kit.set(dx, yy, cz + dz, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 光の井戸: 庭と干潟の天井から甲羅の地表まで 2x2 の縦穴。上はガラス
    public static final int[][] GARDEN_WELLS = {{-50, -28}, {-47, 17}, {-31, 31}, {-33, -16}, {-55, -6}};
    public static final int[][] FLATS_WELLS = {{-12, -70}, {12, -60}, {3, -76}, {-8, -56}};

    private static void lightWells(TideKit kit) {
        for (int[] w : GARDEN_WELLS) {
            well(kit, w[0], w[1], gardenCeiling(w[0], w[1]) + 1);
        }
        for (int[] w : FLATS_WELLS) {
            well(kit, w[0], w[1], FLATS_CEILING + 1);
        }
    }

    private static void well(TideKit kit, int x0, int z0, int from) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int top = Math.max(TideTurtle.top(x, z), shellTop(x, z));
                for (int y = from; y < top; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
                kit.set(x, top, z, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
        }
        // 井戸の口 (天井) のまわりに灯り
        kit.set(x0 - 1, from, z0, TideKit.LAMP);
        kit.set(x0 + 2, from, z0 + 1, TideKit.LAMP);
    }
    // ---------------------------------------------------------------- 休み場・碑文・潮鳴り石・抜け道の床
    // 看板は、試練の入口の一言 (3 枚) と碑文 (6 枚) だけ。使い方は最初の一度だけの囁き (TideWhispers)、形・光・音で伝える
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("sign.skyabyss.tide_garden", new BlockPos(-19, PLATFORM_TOP + 2, -3), Direction.SOUTH),
            new Inscription("sign.skyabyss.tide_watch", new BlockPos(24, HUB_LEDGE + 2, -14), Direction.SOUTH),
            new Inscription("sign.skyabyss.tide_flats", new BlockPos(3, TUNNEL_FLOOR + 2, -30), Direction.SOUTH));

    private static void fixtures(TideKit kit) {
        for (Rest r : RESTS) {
            kit.campfire(r.campfire());
        }
        for (Inscription i : LORE_SIGNS) {
            if (i.facing() == Direction.UP) {
                kit.set(i.pos(), Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
                kit.writeSign(i.pos(), i.key(), net.minecraft.world.item.DyeColor.CYAN);
            } else {
                kit.wallSign(i.pos(), i.facing(), i.key());
            }
        }
        // 干潟の門は閉じた形で建てる (干潮で水盤が空のときに、試練の側が開ける)
        for (int x = -1; x <= 1; x++) {
            for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++) {
                kit.set(x, y, FLATS_GATE_Z, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }
        // 抜け道の床 (石が鳴るまでは暗い石。鳴ると光る)
        for (BlockPos p : new BlockPos[] {RETURN_WEST, RETURN_EAST, RETURN_FRONT}) {
            kit.set(p, TideKit.DARK);
        }
        // 各試練の入口の一言
        for (Inscription i : ENTRANCE_SIGNS) {
            kit.wallSign(i.pos(), i.facing(), i.key());
        }
    }
}
