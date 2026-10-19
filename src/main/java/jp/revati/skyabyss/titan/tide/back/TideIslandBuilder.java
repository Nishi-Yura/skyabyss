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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の島を建てる (08 の 21 章「巨神の形の背中」、26 の 9 章)。最初の乗り込みと、配置の版が変わったときに一度だけ動く。
// 順番: 巨神の形の亀 (TideTurtle。中は詰まっている) → 浜 → 潮門・到着の段・見送りの段・潮門の廊下 → 回廊 → 予約の間の輪 (upper) →
//       広間 (月の窓の縦穴は気室の底まで) → 息の間 (BreathBuilder) → 回り道 → 珊瑚の庭 (TideGardenBuilder) → 潮見の間 (TideWatchBuilder) →
//       首の廊下と干潟 (TideFlatsBuilder) → 螺旋の階段と殻の底 → 掘った所の内壁を潮の民の石に (skin) → 甲羅の上へ上る段 → 頂の塔の中 → 光の井戸 →
//       休み場・碑文・石・抜け道の床
// 潮で動く所 (水、門の開け閉め、庭の道の形、潮見の間の水位、巣) は試練の側 (TideBackTrials ほか) が置く
public final class TideIslandBuilder {
    private TideIslandBuilder() {
    }

    public static void build(TideKit kit) {
        long t0 = System.currentTimeMillis();
        int placed = TideTurtle.place(kit);
        SkyAbyss.LOGGER.info("Placed the tide turtle: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
        buildRest(kit);
    }

    // 亀の体を置いたあと (部屋を掘る・中身を建てる)。起動時は体を何 tick かに分けて置いてから呼ぶ (BackBuildJobs)
    public static void buildRest(TideKit kit) {
        fillTrenches(kit);
        beach(kit);
        gate(kit);
        cave(kit);
        upper(kit);
        hub(kit);
        BreathBuilder.chamber(kit);
        BreathBuilder.arena(kit);
        ramp(kit);
        TideGardenBuilder.build(kit);
        TideWatchBuilder.build(kit);
        TideFlatsBuilder.neck(kit);
        TideFlatsBuilder.flats(kit);
        TideFlatsBuilder.mouthStairs(kit);
        TideFins.build(kit);
        TideBossGates.flats(kit.level());
        spiral(kit);
        TideHeartArena.build(kit.level());
        skin(kit);
        topStair(kit);
        tower(kit);
        BreathBuilder.observatory(kit);
        lightWells(kit);
        crown(kit);
        fixtures(kit);
        fillWells(kit);
    }

    // 甲羅の台地の上の、幅 1 の溝 (両隣が 2 以上高い。模型の薄い壁の抜けたあと) を、深さ 1 になるまで埋める (落ちると戻れない穴を作らない)
    static void fillTrenches(TideKit kit) {
        int x0 = -130;
        int z0 = -175;
        int w = 261;
        int d = 340;
        int[][] h = new int[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                int t = TideTurtle.top(x0 + i, z0 + j);
                h[i][j] = t == Integer.MIN_VALUE ? -1 : t;
            }
        }
        int filled = 0;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 1; i < w - 1; i++) {
                for (int j = 1; j < d - 1; j++) {
                    int t = h[i][j];
                    if (t < 64) {
                        continue;
                    }
                    int a = Math.min(h[i - 1][j], h[i + 1][j]);
                    int b = Math.min(h[i][j - 1], h[i][j + 1]);
                    int to = Math.max(a >= t + 2 ? a - 1 : t, b >= t + 2 ? b - 1 : t);
                    // 四方が 1 以上高いくぼみ (1 マスの凹み) も、いちばん低い隣の高さまで埋める
                    int m4 = Math.min(a, b);
                    if (m4 >= t + 1) {
                        to = Math.max(to, m4);
                    }
                    if (to <= t) {
                        continue;
                    }
                    BlockState top = TideTurtle.at(x0 + i, Math.min(t, TideTurtle.maxY()), z0 + j);
                    if (top == null) {
                        top = Blocks.TUFF.defaultBlockState();
                    }
                    for (int y = t + 1; y <= to; y++) {
                        kit.set(x0 + i, y, z0 + j, top);
                    }
                    h[i][j] = to;
                    filled++;
                }
            }
        }
        SkyAbyss.LOGGER.info("Filled {} trench cells on the shell terraces", filled);
    }

    // 亀の体 (珊瑚・遺跡の形) の凹みにできた小さな縦穴を埋める (詰み監査)
    static void fillWells(TideKit kit) {
        jp.revati.skyabyss.titan.WellFiller.fill(kit.level(), TideTurtle.minX(), TideTurtle.maxX(), TideTurtle.minZ(), TideTurtle.maxZ(),
                TideIslandBuilder::surface, TOP_Y - 2);
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

    // 枯れた珊瑚は、ふだんの形 (defaultBlockState) が水に浸かっている。空気の中に置くと、隣が変わったときに水が流れ出すので、乾いた形で置く
    // (潮で庭が満ちるときは TideField が水に沈める)
    static BlockState deadFan(int i) {
        BlockState s = switch (Math.floorMod(i, 4)) {
            case 0 -> Blocks.DEAD_BRAIN_CORAL.defaultBlockState();
            case 1 -> Blocks.DEAD_FIRE_CORAL.defaultBlockState();
            case 2 -> Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState();
            default -> Blocks.DEAD_HORN_CORAL.defaultBlockState();
        };
        return s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
    }

    // ---------------------------------------------------------------- 潮門の浜 (甲羅の中の広間。砂岩と貝の白、天井は肋のドーム)
    // 潮だまり (浅い水盤)・肋の梁・天窓・焚き火と炉・灯りの道。西の廊下から入り、北東の回廊で広間へ、北西の口から甲羅の上へ
    static final int[] BEACH_POOL = {-36, 94};
    static final int[][] BEACH_WELLS = {{-41, 72}, {-30, 78}, {-22, 95}, {-40, 96}};

    private static void beach(TideKit kit) {
        kit.zone(TideZone.BEACH);
        Area b = BEACH;
        for (int x = b.x1() - 1; x <= b.x2() + 1; x++) {
            for (int z = b.z1() - 1; z <= b.z2() + 1; z++) {
                boolean in = x >= b.x1() && x <= b.x2() && z >= b.z1() && z <= b.z2();
                int edge = in ? Math.min(Math.min(x - b.x1(), b.x2() - x), Math.min(z - b.z1(), b.z2() - z)) : -1;
                // ドーム: 壁ぎわは 75、内へ 9 マスで 84
                int ceil = Math.min(BEACH_CEILING, b.floor() + 9 + edge);
                boolean rib = in && Math.floorMod(x - b.x1(), 7) == 3;
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
                        // 天井: 肋の梁 (骨)、ところどころ灯り、その他は真珠層
                        s = rib ? Blocks.BONE_BLOCK.defaultBlockState() : Math.floorMod(x * 5 + z * 3, 11) == 0 ? TideKit.LAMP : TideKit.NACRE;
                    } else {
                        s = kit.wall(x, y, z);
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 潮だまり (半径 5、深さ 1。水の高さは床より下)
        int px = BEACH_POOL[0];
        int pz = BEACH_POOL[1];
        for (int x = px - 7; x <= px + 7; x++) {
            for (int z = pz - 7; z <= pz + 7; z++) {
                double d = Math.hypot(x - px, z - pz);
                if (d <= 5.4) {
                    kit.set(x, b.floor() - 1, z, (x + z) % 5 == 0 ? TideKit.LAMP : Blocks.PRISMARINE.defaultBlockState());
                    kit.set(x, b.floor(), z, Blocks.WATER.defaultBlockState());
                } else if (d <= 6.4) {
                    kit.set(x, b.floor(), z, Blocks.CUT_SANDSTONE.defaultBlockState());
                    if (d > 5.9 && Math.floorMod((int) Math.round(Math.atan2(z - pz, x - px) * 8 / Math.PI), 2) == 0) {
                        kit.set(x, b.floor() + 1, z, deadFan(x * 3 + z));
                    }
                }
            }
        }
        // 浜の道具: 炉、焚き火と腰掛け、碑文の石柱 (看板は fixtures)、縁の灯り
        kit.set(-20, 67, 76, ModBlocks.RESONANCE_FORGE.get().defaultBlockState().setValue(ResonanceForgeBlock.FACING, Direction.WEST));
        kit.set(-44, 67, 80, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.stair(-46, 67, 80, Direction.WEST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-42, 67, 80, Direction.EAST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-44, 67, 82, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-44, 67, 78, Direction.NORTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        for (int y = 67; y <= 69; y++) {
            kit.set(-36, y, b.z1(), TideZone.BEACH.dark);
        }
        for (int[] l : new int[][] {{-45, 67}, {-45, 99}, {-13, 99}, {-29, 99}, {-45, 83}, {-13, 83}}) {
            kit.set(l[0], 67, l[1], Blocks.PRISMARINE_WALL.defaultBlockState());
            kit.set(l[0], 68, l[1], TideKit.LAMP);
        }
        // 砂の上の貝殻 (枯れた珊瑚の扇)
        for (int[] c : new int[][] {{-48, 96}, {-40, 70}, {-24, 97}, {-49, 74}, {-28, 88}, {-30, 70}}) {
            kit.set(c[0], 67, c[1], deadFan(c[0] + c[1]));
        }
        // 灯りの道: 西の廊下の口から、北東の回廊の口へ (床に埋めた灯り)
        kit.routeStrip(b.floor(), TideKit.line(b.x1(), 86, -16, 86, -16, b.z1()), TideKit.LAMP, TideKit.NACRE);
        // 肋の根もとの柱 (骨の柱。肋の梁を支える)
        for (int z : new int[] {b.z1(), b.z2()}) {
            for (int x = b.x1() + 3; x <= b.x2(); x += 7) {
                if (z == b.z1() && x == -36) {
                    continue;
                }
                if (kit.get(x, 67, z).isAir()) {
                    for (int y = 67; y <= 72; y++) {
                        kit.set(x, y, z, y == 72 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                    }
                }
            }
        }
    }

    private static BlockState beachFloor(int x, int z) {
        int h = Math.floorMod(x * 7 + z * 3, 9);
        return h < 6 ? TideKit.SAND : h < 8 ? TideKit.FLOOR : Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 潮門 (モデルの左後ろの門)、到着の段、見送りの段、潮門の廊下
    private static void gate(TideKit kit) {
        kit.zone(TideZone.BEACH);
        Area l = LANDING;
        // 到着の段 (床 64): 甲羅の縁と後ろのひれの付け根の上。段の上 8 段を空ける (光る面の下をくぐる)
        for (int x = l.x1(); x <= l.x2(); x++) {
            for (int z = l.z1(); z <= l.z2(); z++) {
                for (int y = 60; y <= l.floor(); y++) {
                    kit.set(x, y, z, y == l.floor() ? landingFloor(x, z) : TideKit.SANDSTONE);
                }
                for (int y = l.floor() + 1; y <= l.floor() + 8; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // ひれの上 (南) から到着の段へ下りる 5 段 (登る向きは南。幅 7)
        int top0 = 69;
        for (int i = 0; i < 5; i++) {
            int z = l.z2() + 1 + i;
            int f = l.floor() + 1 + i;
            for (int x = -106; x <= -100; x++) {
                for (int y = GATE_FLOOR - 2; y < f; y++) {
                    kit.set(x, y, z, TideKit.SANDSTONE);
                }
                kit.stair(x, f, z, Direction.SOUTH, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                for (int y = f + 1; y <= top0 + 4; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 見送りの段: 段の西 (x -109〜-112) を、海へ一段ずつ下りる。いちばん下 (x -113〜-111) に入ると地上へ戻る (inReturnGate)
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
                        s = Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
                                .setValue(StairBlock.WATERLOGGED, y < SEA);
                    } else {
                        s = y < SEA ? water : TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 潮門の廊下: 光る面のうしろ (x -90) から浜の西の壁 (x -53) まで。幅 6 (床は z 84〜89)、高さ 6。床 64、x -56・-55 の段で浜の床 66 へ
        int bx = BEACH.x1();
        for (int x = l.x2() + 1; x <= bx - 1; x++) {
            int f = x <= bx - 5 ? GATE_FLOOR : x == bx - 4 ? GATE_FLOOR + 1 : BEACH.floor();
            for (int z = GATE_HALL_Z1 - 1; z <= GATE_HALL_Z2 + 1; z++) {
                boolean wall = z == GATE_HALL_Z1 - 1 || z == GATE_HALL_Z2 + 1;
                for (int y = GATE_FLOOR - 2; y <= f + 7; y++) {
                    BlockState s;
                    if (wall) {
                        // 壁: 6 マスおきに、骨の柱の間に灯り
                        boolean pil = Math.floorMod(x, 8) == 0 && y > f && y <= f + 6;
                        s = pil ? (y == f + 3 ? TideKit.LAMP : Blocks.BONE_BLOCK.defaultBlockState()) : kit.wall(x, y, z);
                    } else if (y < f) {
                        s = TideKit.BRICK;
                    } else if (y == f) {
                        if (x == bx - 4 || x == bx - 3) {
                            kit.stair(x, y, z, Direction.EAST, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                            continue;
                        }
                        s = kit.floorAt(x, z);
                        if ((z == GATE_HALL_Z1 || z == GATE_HALL_Z2) && Math.floorMod(x, 4) == 0) {
                            s = TideKit.NACRE;
                        }
                    } else if (y == f + 7) {
                        s = Math.floorMod(x, 8) == 4 ? TideKit.LAMP : TideKit.DARK;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 廊下の骨のアーチ (8 マスおき。柱の頭を結ぶ梁)
        for (int x = l.x2() + 4; x <= bx - 6; x += 8) {
            for (int z = GATE_HALL_Z1; z <= GATE_HALL_Z2; z++) {
                kit.set(x, GATE_FLOOR + 6, z, Blocks.BONE_BLOCK.defaultBlockState());
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

    // ---------------------------------------------------------------- 縁の回廊 (浜 66 → 広間の南の段 74): 幅 7、高さ 7。柱とアーチの階段回廊。途中に碑と宝の窪み
    static int caveFloor(int z) {
        int len = CAVE_Z_BEACH - CAVE_Z_HUB;
        return BEACH.floor() + (int) Math.round((HUB_LEDGE - BEACH.floor()) * (CAVE_Z_BEACH - z) / (double) len);
    }

    // 回廊の脇の窪み (東の壁、z 47〜52。床 69〜70)
    static final int[] CAVE_NICHE = {-11, -5, 48, 53};

    private static void cave(TideKit kit) {
        kit.zone(TideZone.VAULT);
        for (int z = CAVE_Z_BEACH + 1; z >= CAVE_Z_HUB - 1; z--) {
            int f = z > CAVE_Z_BEACH ? BEACH.floor() : caveFloor(Math.max(z, CAVE_Z_HUB));
            for (int x = CAVE_X1 - 1; x <= CAVE_X2 + 1; x++) {
                boolean wall = x < CAVE_X1 || x > CAVE_X2;
                for (int y = f; y <= f + 7; y++) {
                    if (wall) {
                        boolean pil = Math.floorMod(z, 6) == 0 && y > f && y < f + 7;
                        kit.set(x, y, z, pil ? (y == f + 3 ? TideKit.LAMP : Blocks.CALCITE.defaultBlockState()) : kit.wall(x, y, z));
                    } else if (y == f) {
                        boolean step = z <= CAVE_Z_BEACH && z > CAVE_Z_HUB - 1 && caveFloor(z) > caveFloor(z + 1);
                        if (step) {
                            kit.set(x, y - 1, z, TideKit.FLOOR);
                            kit.stair(x, y, z, Direction.NORTH, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, Math.abs(x - (CAVE_X1 + CAVE_X2) / 2) <= 0 ? TideKit.NACRE : kit.floorAt(x, z));
                        }
                    } else if (y == f + 7) {
                        kit.set(x, y, z, Math.floorMod(z, 6) == 3 && Math.abs(x - (CAVE_X1 + CAVE_X2) / 2) <= 1 ? TideKit.LAMP : TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // アーチ (6 マスおき。柱の頭の梁)
        for (int z = CAVE_Z_BEACH - 3; z > CAVE_Z_HUB + 1; z -= 6) {
            int f = caveFloor(z);
            for (int x = CAVE_X1; x <= CAVE_X2; x++) {
                kit.set(x, f + 6, z, Blocks.CALCITE.defaultBlockState());
            }
        }
        // 窪み (行き止まり。碑と宝)
        int[] n = CAVE_NICHE;
        int nf = caveFloor((n[2] + n[3]) / 2) + 1;
        for (int x = n[0] - 1; x <= n[1] + 1; x++) {
            for (int z = n[2] - 1; z <= n[3] + 1; z++) {
                boolean in = x >= n[0] && x <= n[1] && z >= n[2] && z <= n[3];
                for (int y = nf - 1; y <= nf + 5; y++) {
                    BlockState s;
                    if (!in) {
                        s = kit.wall(x, y, z);
                    } else if (y == nf - 1) {
                        s = TideKit.FLOOR;
                    } else if (y == nf + 5) {
                        s = (x + z) % 3 == 0 ? TideKit.LAMP : TideKit.DARK;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 窪みの口 (回廊の東の壁 x -6 を抜く)。床は回廊の床と同じ高さに
        for (int z = n[2] + 1; z <= n[3] - 1; z++) {
            for (int y = nf; y <= nf + 3; y++) {
                kit.set(CAVE_X2 + 1, y, z, y == nf - 0 ? TideKit.AIR : TideKit.AIR);
            }
            kit.set(CAVE_X2 + 1, nf - 1, z, TideKit.FLOOR);
        }
    }

    // ---------------------------------------------------------------- 潮の広間 (殻の大広間。半径 23 の沈んだ水盤。肋のドーム)
    // 月の窓: 天井の穴 (中心 (0, MOON_CZ)) から、甲羅の中を息の間の底 (気室 A のガラスの床 y 108) の下まで縦穴。
    private static int hubCeil(double r) {
        return HUB_CEILING + (int) Math.floor(Math.max(0, HUB_R - r) * 0.27);
    }

    private static void hub(TideKit kit) {
        kit.zone(TideZone.VAULT);
        for (int x = -HUB_R - 3; x <= HUB_R + 3; x++) {
            for (int z = -HUB_R - 3; z <= HUB_R + 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                int ceilY = hubCeil(r);
                double ang = Math.atan2(z, x);
                boolean ribLine = Math.floorMod((int) Math.round(ang * 16 / Math.PI), 2) == 0 && r > 6;
                for (int y = HUB_FLOOR - 1; y <= HUB_CEILING + 8; y++) {
                    BlockState s;
                    if (r <= HUB_R + 0.5) {
                        if (y < HUB_FLOOR) {
                            s = TideKit.BRICK;
                        } else if (y == HUB_FLOOR) {
                            s = hubFloor(x, z, r);
                        } else if (y < ceilY) {
                            s = TideKit.AIR;
                        } else if (y > ceilY) {
                            continue;
                        } else if (Math.hypot(x, z - MOON_CZ) <= MOON_R + 0.5) {
                            s = TideKit.AIR;
                        } else if (Math.hypot(x, z - MOON_CZ) <= MOON_R + 1.5) {
                            s = TideKit.DARK;
                        } else {
                            // 天井: 放射状の肋 (骨と方解石)、ところどころ灯り
                            s = ribLine ? (r > 12 && Math.floorMod((int) Math.round(r), 5) == 0 ? TideKit.LAMP : Blocks.BONE_BLOCK.defaultBlockState())
                                    : (Math.floorMod(x * 3 + z * 7, 13) == 0 ? TideKit.LAMP : Blocks.CALCITE.defaultBlockState());
                        }
                    } else if (r <= HUB_R + 2.5) {
                        if (y > ceilY) {
                            continue;
                        }
                        // 壁: 潮の高さ (71〜73) は暗い帯、上は真珠層の筋、灯りは縁の段の高さと、その 6 上
                        if (y >= HUB_FLOOR + 1 && y <= HUB_FLOOR + 3) {
                            s = TideKit.DARK;
                        } else if ((y == HUB_LEDGE + 3 || y == HUB_LEDGE + 10) && Math.floorMod((int) Math.round(ang * 12 / Math.PI), 3) == 0 && r <= HUB_R + 1.5) {
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
        // 壁の柱 (4 本。縁の段・戸口のない角度。骨と方解石)
        for (double deg : new double[] {22.5, 67.5, 247.5, 292.5}) {
            int px = (int) Math.round(Math.cos(Math.toRadians(deg)) * (HUB_R + 1));
            int pz = (int) Math.round(Math.sin(Math.toRadians(deg)) * (HUB_R + 1));
            for (int y = HUB_LEDGE; y <= hubCeil(HUB_R + 1) - 1; y++) {
                kit.set(px, y, pz, y == HUB_LEDGE + 8 ? TideKit.LAMP : y == hubCeil(HUB_R + 1) - 1 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
            }
        }
        // 月の窓の縦穴: 広間の天井から気室 A の底の下まで
        for (int x = -MOON_R - 1; x <= MOON_R + 1; x++) {
            for (int z = MOON_CZ - MOON_R - 1; z <= MOON_CZ + MOON_R + 1; z++) {
                double r = Math.hypot(x, z - MOON_CZ);
                for (int y = HUB_CEILING; y < BreathLayout.POOL_FLOOR; y++) {
                    if (r <= MOON_R + 0.5) {
                        kit.set(x, y, z, TideKit.AIR);
                    } else if (r <= MOON_R + 1.5) {
                        kit.set(x, y, z, y % 6 == 0 ? TideKit.LAMP : TideKit.DARK);
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
                    for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + a.height(); y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 南の段から水盤の底へ下りる階段 (幅 5)
        for (int i = 0; i < 3; i++) {
            int z = LEDGE_S.z1() - 1 - i;
            int y = HUB_LEDGE - 1 - i;
            for (int x = HUB_STAIR_X1; x <= HUB_STAIR_X2; x++) {
                for (int yy = HUB_FLOOR + 1; yy < y; yy++) {
                    kit.set(x, yy, z, TideKit.BRICK);
                }
                kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
            }
        }
        // 西の段から庭の入口 g0 へ下りる通路 (x -27 → -32。74 → 68)。幅 7 (床 z -3〜3)
        kit.zone(TideZone.CORAL);
        for (int x = -HUB_R - 4; x >= G0.x2(); x--) {
            int f = 73 - (-HUB_R - 4 - x);
            f = Math.max(f, PLATFORM_TOP);
            for (int z = -4; z <= 4; z++) {
                for (int y = f - 1; y <= f + 6; y++) {
                    boolean wall = Math.abs(z) == 4;
                    if (wall) {
                        kit.set(x, y, z, y == f + 3 && Math.floorMod(x, 3) == 0 ? TideKit.LAMP : kit.wall(x, y, z));
                    } else if (y == f - 1) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else if (y == f) {
                        if (x >= G0.x2() + 1) {
                            kit.stair(x, y, z, Direction.EAST, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, kit.floorAt(x, z));
                        }
                    } else if (y == f + 6) {
                        kit.set(x, y, z, Math.floorMod(x, 4) == 0 && Math.abs(z) <= 1 ? TideKit.LAMP : TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 東の回廊から潮見の間の前庭への道
        kit.zone(TideZone.GAUGE);
        Area h = EAST_HALL;
        kit.room(h.x1(), h.x2(), h.z1(), h.z2(), h.floor(), h.height(), TideKit.FLOOR);
        for (int x = h.x1() + 2; x <= h.x2(); x += 6) {
            kit.set(x, h.floor() + h.height(), (h.z1() + h.z2()) / 2, TideKit.LAMP);
        }
        kit.routeStrip(h.floor(), TideKit.line(GALLERY_E.x2(), -7, h.x2(), -7), TideKit.LAMP, TideKit.FLOOR);
    }

    // 広間の床: 同心の環 (暗い石の輪)、八方の放射の筋 (真珠層)、まん中は底の門
    private static BlockState hubFloor(int x, int z, double r) {
        if (Math.abs(x) <= BOTTOM_GATE_R && Math.abs(z) <= BOTTOM_GATE_R) {
            return TideKit.DARK;
        }
        int ring = (int) Math.round(r);
        if (ring == 4 || ring == 9 || ring == 15 || ring == 21) {
            return TideKit.DARK;
        }
        if (r > 5.5 && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z))) {
            return TideKit.NACRE;
        }
        return ring % 2 == 0 ? TideKit.FLOOR : Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 回り道 (干潮の道)
    // 点を結ぶ幅 5 の通路。{x, z, 床の高さ} を返す (同じ所は一度だけ)
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
                for (int dx = -RAMP_HALF; dx <= RAMP_HALF; dx++) {
                    for (int dz = -RAMP_HALF; dz <= RAMP_HALF; dz++) {
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
        kit.zone(TideZone.VAULT);
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
                    for (int y = c[2]; y <= c[2] + 6; y++) {
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
            for (int y = c[2] + 1; y <= c[2] + 5; y++) {
                kit.set(c[0], y, c[1], TideKit.AIR);
            }
            kit.set(c[0], c[2] + 6, c[1], (c[0] + c[1]) % 5 == 0 ? TideKit.LAMP : TideKit.DARK);
        }
    }

    // ---------------------------------------------------------------- 珊瑚の庭 (西) / 潮見の間 (東) / 干潟 (前)。それぞれの Builder へ
    public static List<BlockPos> channelCells(Channel c) {
        return TideGardenBuilder.channelCells(c);
    }

    static int gardenCeiling(int x, int z) {
        return TideGardenBuilder.ceiling(x, z);
    }

    public static void carveGardenRoute(TideKit kit, int route, boolean full) {
        TideGardenBuilder.carveRoute(kit, route, full);
    }

    // 干潟の窓 (北西・北東の角。x だけ使う)
    public static final int[][] FLATS_WINDOWS = {{-32, 0}, {30, 0}};

    // ---------------------------------------------------------------- 潮の底の門の下: 螺旋の階段と、殻の底の前室への通路
    // 螺旋の段の並び (中心の柱のまわり 8 マス)。最後の段 (18 番目) は北西の角、そこから北の通路へ
    public static final int[][] SPIRAL_RING = {{-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}};
    public static final int SPIRAL_STEPS = SPIRAL_TOP - SPIRAL_BOTTOM + 1;

    private static void spiral(TideKit kit) {
        kit.zone(TideZone.SHELL);
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
        for (int x = -SHELL_RX - 14; x <= SHELL_RX + 14; x++) {
            for (int z = SHELL_CZ - SHELL_RZ - 14; z <= SHELL_CZ + SHELL_RZ + 14; z++) {
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
                            kit.set(x, y, z, garden ? TideGardenBuilder.wall(x, y, z) : zoneAt(x, y, z).wall(x, y, z));
                            break;
                        }
                    }
                }
            }
        }
    }

    // 内壁の材質を場所ごとに (上の三つの台地・東・北・浜)
    static TideZone zoneAt(int x, int y, int z) {
        if (y >= 109) {
            return TideZone.CROWN;
        }
        if (z > 60 && x < -45) {
            return TideZone.BEACH;
        }
        if (z < -22) {
            return z < -80 ? TideZone.HATCH : TideZone.NECK;
        }
        if (x > 36) {
            return TideZone.GAUGE;
        }
        return TideZone.SHELL;
    }

    // ---------------------------------------------------------------- 甲羅の上へ上る段
    // 浜の西の壁 (x -53、z 70) の口から通路で西へ、5x5 の四角い螺旋 (中心 (-64, 70)) を甲羅の地表の口まで上る。
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
        kit.zone(TideZone.BEACH);
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
        // 浜の西の壁の口 (x -53、z 70) から段の下まで、床 66 の短い通路 (幅 3)
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
            // 段の背の高い側が地表の上面 (top + 1) にそろうのは、段が top にあるとき (top - 1 で止めると、外へ出るときに 1 ブロック登ることになる)
            if (y >= top) {
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

    // ---------------------------------------------------------------- 予約の間 1〜3 (上の三つの台地の中。冠の回廊。08 の 3.8節)
    // 気室 A (半径 R_A) を巻く輪の回廊 (外半径 31・27・23)。真ん中は息の間 (BreathBuilder.chamber) が円く掘り抜く。
    // 入口は西の崖 (x -78) の戸口と廊下 (幅 5)。戸はない (いつも開いている)。階段もない (昇りは気室の皿と風の運び)
    private static void upper(TideKit kit) {
        kit.zone(TideZone.CROWN);
        for (int i = 0; i < UPPER_ROOMS.size(); i++) {
            Area a = UPPER_ROOMS.get(i);
            int ro = UPPER_RING_R[i];
            int cx = BreathLayout.CX;
            int cz = BreathLayout.CZ;
            for (int x = cx - ro - 2; x <= cx + ro + 2; x++) {
                for (int z = cz - ro - 2; z <= cz + ro + 2; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > ro + 1.5) {
                        continue;
                    }
                    boolean in = d <= ro + 0.5;
                    for (int y = a.floor(); y <= a.floor() + a.height() + 1; y++) {
                        BlockState s;
                        if (in) {
                            if (y == a.floor()) {
                                s = upperFloor(x, z, d, i);
                            } else if (y == a.floor() + a.height() + 1) {
                                s = Math.floorMod(x * 3 + z * 5, 7) == 0 && d > BreathLayout.R_A + 1 ? TideKit.LAMP : (d > BreathLayout.R_A + 1 && Math.floorMod((int) Math.round(Math.atan2(z - cz, x - cx) * 12 / Math.PI), 2) == 0
                                        ? Blocks.BONE_BLOCK.defaultBlockState() : TideKit.NACRE);
                            } else {
                                s = TideKit.AIR;
                            }
                        } else {
                            s = kit.wall(x, y, z);
                        }
                        kit.set(x, y, z, s);
                    }
                }
            }
        }
        // 輪の内側の柱の列 (気室との境。骨と方解石の柱、灯り)。気室へ歩いて出る所 (入口の泡・皿 2 と 5 の足場・入口の廊下) は空ける
        for (int i = 0; i < UPPER_ROOMS.size(); i++) {
            Area a = UPPER_ROOMS.get(i);
            double keep = i == 0 ? 162 : i == 1 ? -54 : 90;
            for (int k = 0; k < 24; k++) {
                double deg = k * 15.0;
                double diff = Math.abs(((deg - keep + 540) % 360) - 180);
                if (diff < 24 || (i == 0 && Math.abs(((deg - 180 + 540) % 360) - 180) < 22)) {
                    continue;
                }
                int px = BreathLayout.CX + (int) Math.round(Math.cos(Math.toRadians(deg)) * (BreathLayout.R_A + 1.4));
                int pz = BreathLayout.CZ + (int) Math.round(Math.sin(Math.toRadians(deg)) * (BreathLayout.R_A + 1.4));
                for (int y = a.floor() + 1; y <= a.floor() + a.height(); y++) {
                    kit.set(px, y, pz, y == a.floor() + a.height() ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
                            : y == a.floor() + 4 ? TideKit.LAMP : (y - a.floor()) % 5 == 0 ? Blocks.QUARTZ_PILLAR.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
        // 入口の廊下 (x -77〜-31、z -2〜2、高さ 6) と、崖の前の窪み (x -80〜-79)。戸口は廊下の西の端 (x -78)
        int gx = UPPER_GATE_X;
        kit.room(gx + 1, UPPER_HALL_X2, UPPER_GATE_Z - 2, UPPER_GATE_Z + 2, UPPER_1.floor(), 6, TideKit.FLOOR);
        for (int x = gx + 3; x <= UPPER_HALL_X2 - 1; x += 7) {
            for (int z : new int[] {UPPER_GATE_Z - 3, UPPER_GATE_Z + 3}) {
                for (int y = UPPER_1.floor() + 1; y <= UPPER_1.floor() + 6; y++) {
                    kit.set(x, y, z, y == UPPER_1.floor() + 3 ? TideKit.LAMP : Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
        kit.routeStrip(UPPER_1.floor(), TideKit.line(gx + 1, UPPER_GATE_Z, UPPER_HALL_X2, UPPER_GATE_Z), TideKit.LAMP, TideKit.NACRE);
        for (int x = gx - 2; x <= gx; x++) {
            for (int z = UPPER_GATE_Z - 2; z <= UPPER_GATE_Z + 2; z++) {
                for (int y = UPPER_1.floor() + 1; y <= UPPER_1.floor() + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 戸口の枠 (光る石の柱)
        for (int z : new int[] {UPPER_GATE_Z - 3, UPPER_GATE_Z + 3}) {
            for (int y = UPPER_1.floor() + 1; y <= UPPER_1.floor() + 6; y++) {
                kit.set(gx, y, z, y == UPPER_1.floor() + 3 ? TideKit.LAMP : TideKit.NACRE);
            }
        }
    }

    // 輪の回廊の床: 気室の縁は水平な床、外へ向かって同心の輪と放射の筋
    private static BlockState upperFloor(int x, int z, double d, int level) {
        int ring = (int) Math.round(d);
        if (d > BreathLayout.R_A && d <= BreathLayout.R_A + 1.2) {
            return TideKit.NACRE;
        }
        if (ring % 6 == 0) {
            return TideKit.DARK;
        }
        double ang = Math.atan2(z - BreathLayout.CZ, x - BreathLayout.CX);
        if (Math.floorMod((int) Math.round(ang * 12 / Math.PI), 3) == 0 && ring % 2 == 0) {
            return Blocks.POLISHED_DIORITE.defaultBlockState();
        }
        return TideKit.FLOOR;
    }

    // ---------------------------------------------------------------- 頂の塔の屋根 (月の窓の光)
    // 塔の中は詰まっている (縦穴は広間が掘る)。縦穴の上端 (TOWER_DECK のガラス) から塔の頂まで、半径 3.5 の柱をガラスにする。
    // 日と月の光が、縦穴を通って広間の水盤まで落ちる
    private static void tower(TideKit kit) {
        int cz = TOWER_CZ;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.hypot(dx, dz) > 3.5) {
                    continue;
                }
                for (int yy = TOWER_DECK + 1; yy <= TideTurtle.maxY(); yy++) {
                    if (!kit.get(dx, yy, cz + dz).isAir()) {
                        kit.set(dx, yy, cz + dz, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- フジツボの冠 (頂の台地と塔の根もとに、白い貝の尖塔を輪に立てる)
    private static void crown(TideKit kit) {
        kit.zone(TideZone.CROWN);
        for (int k = 0; k < 14; k++) {
            double a = Math.toRadians(k * (360.0 / 14) + 7);
            double r = 19 + (k % 3) * 3;
            int cx = (int) Math.round(Math.cos(a) * r);
            int cz = TOWER_CZ + (int) Math.round(Math.sin(a) * r);
            int top = shellTop(cx, cz);
            if (top < TERRACE_Y[3] - 2 || top > TERRACE_Y[3] + 2) {
                continue;
            }
            int h = 8 + (k * 5) % 7;
            for (int y = 1; y <= h; y++) {
                double rr = 0.7 + (h - y) * 0.32;
                int ri = (int) Math.ceil(rr);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (Math.hypot(dx, dz) <= rr) {
                            BlockState s = y == h ? TideKit.LAMP : (y + dx + dz) % 4 == 0 ? Blocks.CALCITE.defaultBlockState()
                                    : (y % 3 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState());
                            kit.set(cx + dx, top + y, cz + dz, s);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 光の井戸: 浜・庭・干潟・潮見の間の天井から甲羅の地表まで 2x2 の縦穴。上はガラス
    public static final int[][] GARDEN_WELLS = {{-80, -45}, {-75, 27}, {-64, 48}, {-64, -29}, {-88, -10}, {-50, -20}, {-50, 30}, {-92, 50}, {-60, 5}};
    public static final int[][] FLATS_WELLS = {{-26, -100}, {26, -98}, {0, -96}, {-10, -118}, {12, -122}, {30, -114}, {-30, -120}, {0, -112}};
    public static final int[][] WATCH_WELLS = {{60, -30}, {90, -20}, {70, -50}, {90, -56}};

    private static void lightWells(TideKit kit) {
        for (int[] w : GARDEN_WELLS) {
            well(kit, w[0], w[1], TideGardenBuilder.ceiling(w[0], w[1]) + 1);
        }
        for (int[] w : FLATS_WELLS) {
            well(kit, w[0], w[1], FLATS_CEILING + 1);
        }
        for (int[] w : BEACH_WELLS) {
            well(kit, w[0], w[1], BEACH_CEILING + 1);
        }
        for (int[] w : WATCH_WELLS) {
            well(kit, w[0], w[1], TideWatchBuilder.HALL_ROOF);
        }
    }

    // 縦穴は、井戸と、そのまわり 1 マスの地表のいちばん低い所の下までで止め、そこから上は各柱の地表までガラスで埋める。
    // 前は柱ごとに地表まで掘っていて、甲羅の斜面では高い側の柱の横腹が、低い側の地表の上に出て外へ開いていた
    private static void well(TideKit kit, int x0, int z0, int from) {
        int cap = Integer.MAX_VALUE;
        for (int x = x0 - 1; x <= x0 + 2; x++) {
            for (int z = z0 - 1; z <= z0 + 2; z++) {
                cap = Math.min(cap, groundTop(x, z));
            }
        }
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int top = groundTop(x, z);
                for (int y = from; y < cap; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
                for (int y = cap; y <= top; y++) {
                    kit.set(x, y, z, glass);
                }
            }
        }
        // 井戸の口 (天井) のまわりに灯り
        kit.set(x0 - 1, from, z0, TideKit.LAMP);
        kit.set(x0 + 2, from, z0 + 1, TideKit.LAMP);
    }

    private static int surface(int x, int z) {
        return Math.max(TideTurtle.top(x, z), shellTop(x, z));
    }

    // 光の井戸のふた: 甲羅の地表 (飾りの珊瑚や輪の上ではなく、地表の高さ)
    private static int groundTop(int x, int z) {
        int top = shellTop(x, z);
        return top == Integer.MIN_VALUE ? TideTurtle.top(x, z) : top;
    }

    // ---------------------------------------------------------------- 休み場・碑文・潮鳴り石・抜け道の床
    // 看板は、試練の入口の一言 (3 枚)・その補い (栓の色の読み方・戻しの栓・広間の案内。3 枚) と碑文 (6 枚)。使い方は最初の一度だけの囁き (TideWhispers)、形・光・音で伝える
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("sign.skyabyss.tide_garden", new BlockPos(-32, PLATFORM_TOP + 2, -6), Direction.SOUTH),
            new Inscription("sign.skyabyss.tide_watch", new BlockPos(40, HUB_LEDGE + 2, -14), Direction.EAST),
            new Inscription("sign.skyabyss.tide_flats", new BlockPos(3, TUNNEL_FLOOR + 2, -64), Direction.SOUTH),
            // 潮見の間: 栓の色の読み方 (入口の隣)・戻しの栓の上。広間: 三つの試練の一言ずつ (どこへ行って何をするか)
            new Inscription("sign.skyabyss.tide_watch_color", new BlockPos(40, HUB_LEDGE + 2, -17), Direction.EAST),
            new Inscription("sign.skyabyss.tide_watch_reset", new BlockPos(46, HUB_LEDGE + 3, -6), Direction.NORTH),
            new Inscription("sign.skyabyss.tide_hub_guide", new BlockPos(-6, HUB_LEDGE + 2, 26), Direction.NORTH),
            // 息の間の入口の廊下 (どの試練も、条件なしで入れる。昇り方の一言)
            new Inscription("sign.skyabyss.tide_breath", new BlockPos(-50, UPPER_1.floor() + 2, -2), Direction.SOUTH));

    private static void fixtures(TideKit kit) {
        for (Rest r : RESTS) {
            kit.campfire(r.campfire());
        }
        for (Inscription i : LORE_SIGNS) {
            if (i.facing() == Direction.UP) {
                kit.standingTablet(i.pos(), i.key());
            } else {
                kit.wallSign(i.pos(), i.facing(), i.key());
            }
        }
        // 干潟の門は閉じた形で建てる (干潮で水盤が空のときに、試練の側が開ける)
        for (int x = -FLATS_GATE_HALF; x <= FLATS_GATE_HALF; x++) {
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
        // 隠れた宝 (窪みの碑と宝箱)
        TideCaches.build(kit);
    }
}
