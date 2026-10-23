package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の島を建てる (08 の 21 章「巨神の形の背中」、26 の 9 章)。最初の乗り込みと、配置の版が変わったときに一度だけ動く。
// 順番: 巨神の形の亀 (TideTurtle。中は詰まっている) → 浜 → 潮門・到着の段・見送りの段・潮門の廊下 → 回廊 → 予約の間の輪 (upper) →
//       広間 (月の窓の縦穴) → 回り道 → 珊瑚の庭 (TideGardenBuilder) → 潮見の間 (TideWatchBuilder) →
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
        jp.revati.skyabyss.titan.BackBuildJobs.Steps s = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("tide back island");
        restSteps(s, kit);
        while (!s.step(Long.MAX_VALUE)) {
            // 最後まで
        }
    }

    // buildRest の手順 (順は同じ。tick に分けて進められる)
    public static void restSteps(jp.revati.skyabyss.titan.BackBuildJobs.Steps s, TideKit kit) {
        // 仕上げが触る範囲 (背の監査の範囲) のチャンクを先に読み込む (読みに行くと、その場で tick が止まる)
        s.thenJob("load area", () -> jp.revati.skyabyss.titan.BackBuildJobs.loadArea(kit.level(), -190, -245, 190, 170));
        // 体の中は建てない (外見だけ。docs/design/32 の 2.5)。外から見える所の手直しと、地上へ戻る潮門の到着の段だけ
        s.then("trenches", () -> fillTrenches(kit));
        s.then("gate", () -> gate(kit));
        s.then("wells", () -> fillWells(kit));
        // 心臓の間 (殻の底) と休み場 heart は体の外の封じた部屋 (trial/raid/InstanceArenas)
        jp.revati.skyabyss.trial.raid.InstanceArenas.addHeartSteps(s, kit.level(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
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

    // ---------------------------------------------------------------- 珊瑚の庭 (西) / 潮見の間 (東) / 干潟 (前)。それぞれの Builder へ
    public static List<BlockPos> channelCells(Channel c) {
        return TideGardenBuilder.channelCells(c);
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

    // ---------------------------------------------------------------- 掘った所の内壁を、潮の民の石にする (体の芯の凝灰岩が見えたら)
    // 皮の手順の x の幅
    static final int SKIN_BAND = 16;

    private static int surface(int x, int z) {
        return Math.max(TideTurtle.top(x, z), shellTop(x, z));
    }

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
            new Inscription("sign.skyabyss.tide_watch_reset", new BlockPos(46, HUB_LEDGE + 3, -6), Direction.NORTH));

}
