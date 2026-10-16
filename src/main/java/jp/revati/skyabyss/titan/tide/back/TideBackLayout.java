package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 潮殻の背 (tide_back) の配置図 (08 の 3 章)。建てる側 (TideIslandBuilder) と仕組みの側 (潮・試練・休み場・リセット) が参照する。
// 座標は「床のブロックの y」で書く (人が立つのは +1)。頭は -Z (北)。
//
//                     [首と頭]
//               [群れの干潟 (前の試練)]   z -104〜-72、床 63、潮で 0〜2 満ちる
//                        | 干潟の口 (z=-71)
//                  [干潟へのトンネル]      床 66
//                  [干潟の前室 f0]         z -30〜-18、床 66        真下に [殻の底 (心臓の間)] 中心 (0,-30) 床 50
//                        | 干潟の門 (z=-13、干潮だけ開く)
//   [珊瑚の庭 (西)]--[潮の広間 (HUB)]--[潮見の間 (東)]           広間の床 70、潮で 0〜3 満ちる。縁の段 (74) から入る
//      床 64             | 縁のくぐり道
//                   [潮門の浜 (到着)]      床 66
//
// 広間は「沈んだ円い水盤」: 床 70 の円 (半径 12) が潮で 3 マス (71〜73) 満ちる。入口は高さ 74 の縁の段
// (南の段: 浜からのくぐり道、西の段: 庭への道、東の回廊: 潮見の間への道)。満潮なら水盤を泳いで東の回廊へ一段上がれる。
// 干潮なら水盤の底へ下り、南東の回り道 (底から東の回廊まで上る坂) を通る。北の干潟の門は床の高さで、干潮のあいだだけ開く。
public final class TideBackLayout {
    // 配置の版。2: 本番の島 (2026-10-17)
    public static final int LAYOUT_VERSION = 2;
    // 生成の海 (水の最上段は y=62)
    public static final int SEA = 63;
    public static final int SEA_FLOOR = 41;

    // ---------------------------------------------------------------- 甲羅 (楕円の丘)
    public static final int SHELL_CZ = -4;
    public static final int SHELL_RX = 64;
    public static final int SHELL_RZ = 76;
    public static final int RIM_Y = 64;
    public static final int TOP_Y = 100;

    // 甲羅の地表の高さ (いちばん上のブロックの y)。甲羅の外は Integer.MIN_VALUE
    public static int shellTop(int x, int z) {
        double s2 = shellS2(x, z);
        if (s2 > 1) {
            return Integer.MIN_VALUE;
        }
        return RIM_Y + (int) Math.round((TOP_Y - RIM_Y) * Math.pow(1 - s2, 0.85));
    }

    public static double shellS2(double x, double z) {
        double nx = x / SHELL_RX;
        double nz = (z - SHELL_CZ) / SHELL_RZ;
        return nx * nx + nz * nz;
    }

    // ---------------------------------------------------------------- 場所 (x1〜x2、z1〜z2 は両端を含む。floor は床のブロックの y、height は床の上の空間の高さ)
    public record Area(int x1, int x2, int z1, int z2, int floor, int height) {
        public boolean contains(BlockPos p) {
            return p.getX() >= x1 && p.getX() <= x2 && p.getZ() >= z1 && p.getZ() <= z2 && p.getY() > floor && p.getY() <= floor + height;
        }

        public boolean contains(Vec3 v) {
            return contains(BlockPos.containing(v));
        }

        public AABB aabb() {
            return new AABB(x1, floor + 1, z1, x2 + 1, floor + 1 + height, z2 + 1);
        }

        public BlockPos center() {
            return new BlockPos((x1 + x2) / 2, floor + 1, (z1 + z2) / 2);
        }
    }

    // 潮門の浜 (到着)。焚き火・炉・腰掛け・潮見の目盛りの石盤・三つの試練の碑文
    public static final Area BEACH = new Area(-32, -12, 42, 62, 66, 8);
    public static final BlockPos ARRIVAL = new BlockPos(-22, 67, 50);
    // 地上へ戻る潮門の口 (x -27〜-25、y 67〜69、z 59〜60)。南の海を向く
    public static final int GATE_X = -26;
    public static final int GATE_Z = 59;
    public static final int GATE_Y0 = 67;
    public static final int GATE_Y1 = 70;

    // 縁のくぐり道: 浜の北 (z=41) から広間の南の段 (y 74) へ上る屋根のある坂 (x -12〜-10)
    public static final int CAVE_X1 = -12;
    public static final int CAVE_X2 = -10;
    public static final int CAVE_Z_BEACH = 41;
    public static final int CAVE_Z_HUB = 12;
    // ピースフルのあいだ封じる所 (くぐり道の途中)
    public static final int CAVE_SEAL_Z = 30;

    // 潮の広間
    public static final int HUB_R = 12;
    public static final int HUB_FLOOR = 70;
    public static final int HUB_LEDGE = 74;
    public static final int HUB_CEILING = 84;
    public static final int MOON_R = 3;
    public static final Area HUB = new Area(-HUB_R, HUB_R, -HUB_R, HUB_R, HUB_FLOOR, 13);
    // 南の段 (浜から) と西の段 (庭へ) はつながった L 字。東の回廊 (潮見の間へ) は離れている
    public static final Area LEDGE_S = new Area(-11, 1, 8, 12, HUB_LEDGE, 6);
    public static final Area LEDGE_W = new Area(-12, -8, -6, 7, HUB_LEDGE, 6);
    public static final Area GALLERY_E = new Area(8, 12, -8, 2, HUB_LEDGE, 6);
    // 南の段から水盤の底へ下りる階段 (x -3〜-1、z 7〜5 の 3 段)
    public static final int HUB_STAIR_X1 = -3;
    public static final int HUB_STAIR_X2 = -1;
    // 干潟の門 (北の壁、x -1〜1、y 71〜73、z -13)。干潮のあいだだけ開く
    public static final int FLATS_GATE_Z = -13;
    // 潮の底の門 (床の格子、x -1〜1、z -1〜1、y 70)。三つの石で開く
    public static final int BOTTOM_GATE_R = 1;
    // 三つの石の灯り (底の門のまわりの床。西・東・前)
    public static final BlockPos LAMP_WEST = new BlockPos(-3, HUB_FLOOR, 0);
    public static final BlockPos LAMP_EAST = new BlockPos(3, HUB_FLOOR, 0);
    public static final BlockPos LAMP_FRONT = new BlockPos(0, HUB_FLOOR, -3);

    // 回り道 (干潮の道): 水盤の底の南東の戸口から、東の回廊の先 (潮見の前室) まで上る坂。点を結ぶ幅 3 の通路
    // { x, z, 床の高さ }。最初の点は広間の戸口 (水盤の一部として満ちる)
    public static final int[][] RAMP = {
            {9, 8, 70}, {16, 8, 70}, {16, 16, 71}, {24, 16, 72}, {24, 4, 73}, {22, 4, 74}, {22, -10, 74}};
    // 東の回廊から潮見の前室への道 (y 74 の床、z -12〜-8 は前室)
    public static final Area EAST_HALL = new Area(13, 23, -5, -1, HUB_LEDGE, 4);

    // 珊瑚の庭 (西)。屋根のない、潮で 0〜3 満ちる水路の庭
    public static final Area GARDEN = new Area(-62, -19, -42, 42, 64, 30);
    public static final int GARDEN_FLOOR = 64;
    public static final int GARDEN_DEPTH = 3;
    public static final int GARDEN_WALL_TOP = 71;
    public static final int PLATFORM_TOP = 68;
    // 広間の西の段から庭の入口 g0 へ下りる道 (x -13〜-19、y 74→68)
    public static final Area G0 = new Area(-24, -19, -3, 3, PLATFORM_TOP, 5);
    // 台 (潮除けの台) の中心。上は 5x5、高さ 4 (床 68)
    public static final int[] PLAT_A = {-50, 26};
    public static final int[] PLAT_B1 = {-55, 9};
    public static final int[] PLAT_B2 = {-41, 12};
    public static final int[] PLAT_C = {-48, -4};
    public static final int[] PLAT_D1 = {-53, -20};
    public static final int[] PLAT_D2 = {-41, -20};
    public static final int[] ALTAR = {-40, -34};
    public static final int[] CLAM = {-52, 36};
    public static final int ALTAR_TOP = 69;
    public static final Area G1 = new Area(PLAT_C[0] - 2, PLAT_C[0] + 2, PLAT_C[1] - 2, PLAT_C[1] + 2, PLATFORM_TOP, 4);
    // 水路 (台と台をつなぐ L 字、幅 3)。道の形 (三通り) で開くものが変わる
    public enum Channel {
        CLAM_A(CLAM, PLAT_A), A_B1(PLAT_A, PLAT_B1), A_B2(PLAT_A, PLAT_B2), B1_C(PLAT_B1, PLAT_C), B2_C(PLAT_B2, PLAT_C),
        C_D1(PLAT_C, PLAT_D1), C_D2(PLAT_C, PLAT_D2), D1_E(PLAT_D1, ALTAR), D2_E(PLAT_D2, ALTAR), G0_C(new int[] {-29, 0}, PLAT_C);

        public final int[] from;
        public final int[] to;

        Channel(int[] from, int[] to) {
            this.from = from;
            this.to = to;
        }
    }

    // 道の形: 開く水路の組 (clam-A と g0-C はいつも開く)
    public static final Channel[][] GARDEN_ROUTES = {
            {Channel.A_B1, Channel.B1_C, Channel.C_D1, Channel.D1_E},
            {Channel.A_B2, Channel.B2_C, Channel.C_D2, Channel.D2_E},
            {Channel.A_B1, Channel.B1_C, Channel.C_D2, Channel.D2_E}};

    // 潮見の間 (東)。三段の溜め (下 B・中 M・上 T)。段の床は 74・80・86、岩棚は 92
    public static final Area S0 = new Area(24, 29, -14, -6, HUB_LEDGE, 5);
    public static final int[] TIER_FLOOR = {74, 80, 86};
    public static final int LEDGE_FLOOR = 92;
    // 溜めの内側 (x1, x2, z1, z2)。床のブロックは段の床 - 1。浮き石は B と M だけ
    public static final int[][] BASIN = {{34, 38, -12, -8}, {45, 49, -12, -8}, {51, 55, -21, -17}};
    // 溜めの戸 (人が浮き石へ乗る口。溜めの水が 1 段以下のときだけ開く)。T は南と北の二つ (水が 0 のときだけ開く)
    public static final BlockPos[] BASIN_DOOR = {new BlockPos(33, 75, -10), new BlockPos(44, 81, -10), new BlockPos(53, 87, -16)};
    public static final BlockPos T_EXIT_DOOR = new BlockPos(53, 87, -22);
    // 段の広さ (浮き石を降りて立つ所)
    public static final Area TIER0 = new Area(28, 32, -14, -6, 74, 5);
    public static final Area TIER1 = new Area(39, 43, -13, -7, 80, 5);
    public static final Area TIER2 = new Area(50, 56, -15, -8, 86, 5);
    public static final Area LEDGE_E = new Area(50, 56, -35, -31, LEDGE_FLOOR, 5);

    // 群れの干潟 (前)
    public static final Area FLATS = new Area(-24, 24, -104, -72, 63, 12);
    public static final int FLATS_FLOOR = 63;
    public static final int FLATS_DEPTH = 2;
    public static final Area F0 = new Area(-4, 4, -30, -18, 66, 5);
    public static final int TUNNEL_FLOOR = 66;
    // 干潟の口 (トンネルの北の端。潮招きの戦いの間だけ閉じる)
    public static final int MOUTH_Z = -71;
    // 岩の島 (中心、上は 3x3、上の床 66)
    public static final int[][] FLATS_ROCKS = {{-16, -80}, {0, -79}, {16, -80}, {-19, -93}, {-7, -91}, {7, -95}, {19, -92}, {0, -101}};
    public static final int ROCK_TOP = 66;
    // 巣の候補地 (6 か所から、リセットの種で 3 つ)
    public static final int[][] NEST_SITES = {{-10, -78}, {9, -77}, {-20, -86}, {20, -86}, {-12, -99}, {12, -101}};
    public static final BlockPos BECKONER_HOME = new BlockPos(0, 64, -88);

    // ---------------------------------------------------------------- 殻の底 (心臓の間) と、潮の底の門からの螺旋の階段
    public static final int SPIRAL_TOP = 69;
    public static final int SPIRAL_BOTTOM = 51;
    public static final Area SPIRAL = new Area(-2, 2, -2, 2, 50, 20);
    public static final Area HEART_HALL = new Area(-1, 1, -9, -3, 50, 4);
    public static final Area HEART_REST = new Area(-4, 4, -9, -3, 50, 4);

    // ---------------------------------------------------------------- 潮鳴り石と、抜け道 (石が鳴ると光る床。踏むと戻れる)
    public static final BlockPos STONE_WEST = new BlockPos(ALTAR[0], ALTAR_TOP + 1, ALTAR[1] - 2);
    public static final BlockPos STONE_EAST = new BlockPos(53, LEDGE_FLOOR + 1, -34);
    public static final BlockPos STONE_FRONT = new BlockPos(0, ROCK_TOP + 1, -102);
    // 抜け道の床 (石の近く) と、運ぶ先
    public static final BlockPos RETURN_WEST = new BlockPos(ALTAR[0] + 2, ALTAR_TOP, ALTAR[1] + 1);
    public static final BlockPos RETURN_EAST = new BlockPos(51, LEDGE_FLOOR, -32);
    public static final BlockPos RETURN_FRONT = new BlockPos(1, ROCK_TOP, -100);
    public static final BlockPos RETURN_WEST_TO = new BlockPos(-10, HUB_LEDGE + 1, 0);
    public static final BlockPos RETURN_EAST_TO = new BlockPos(10, HUB_LEDGE + 1, -2);
    public static final BlockPos RETURN_FRONT_TO = new BlockPos(2, 67, -22);

    // ---------------------------------------------------------------- 休み場 (焚き火のまわりに立つ)
    public record Rest(String id, Area area, BlockPos campfire, BlockPos spawn) {
    }

    public static final List<Rest> RESTS = List.of(
            new Rest("hub", HUB, new BlockPos(-9, HUB_LEDGE + 1, 10), new BlockPos(-6, HUB_LEDGE + 1, 10)),
            new Rest("g0", G0, new BlockPos(-22, PLATFORM_TOP + 1, 2), new BlockPos(-20, PLATFORM_TOP + 1, 0)),
            new Rest("g1", G1, new BlockPos(PLAT_C[0] - 1, PLATFORM_TOP + 1, PLAT_C[1] + 1), new BlockPos(PLAT_C[0] + 1, PLATFORM_TOP + 1, PLAT_C[1] - 1)),
            new Rest("s0", S0, new BlockPos(25, HUB_LEDGE + 1, -13), new BlockPos(26, HUB_LEDGE + 1, -9)),
            new Rest("f0", F0, new BlockPos(-3, TUNNEL_FLOOR + 1, -28), new BlockPos(0, TUNNEL_FLOOR + 1, -24)),
            new Rest("heart", HEART_REST, new BlockPos(3, 51, -6), new BlockPos(-2, 51, -6)));

    // ---------------------------------------------------------------- 碑文 (05 の型。4 行の光る看板)
    public record Inscription(String key, BlockPos pos, Direction facing) {
    }

    public static final List<Inscription> LORE_SIGNS = List.of(
            new Inscription("sign.skyabyss.lore_tide_beach", new BlockPos(-17, 68, 43), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_hub", new BlockPos(-4, HUB_LEDGE + 2, 12), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_tide_west", new BlockPos(ALTAR[0] - 2, ALTAR_TOP + 2, ALTAR[1] - 2), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_east", new BlockPos(55, LEDGE_FLOOR + 2, -35), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_front", new BlockPos(-7, ROCK_TOP + 1, -91), Direction.UP),
            new Inscription("sign.skyabyss.lore_tide_heart", new BlockPos(-4, 52, -8), Direction.EAST));

    // ---------------------------------------------------------------- リセットで広間へ移す所 (試練の中)
    public static List<Area> trialAreas() {
        List<Area> list = new ArrayList<>();
        list.add(GARDEN);
        list.add(new Area(23, 60, -38, -2, 70, 30));
        list.add(new Area(-26, 26, -106, -31, 60, 20));
        list.add(new Area(-18, 18, -48, -3, 45, 18));
        list.add(new Area(-2, 2, -2, 2, 48, 22));
        return list;
    }

    // 潮の底の門より奥 (螺旋の階段・前室・殻の底)
    public static boolean pastBottomGate(Vec3 p) {
        return p.y < HUB_FLOOR && p.y > 45 && Math.abs(p.x) < 20 && p.z > -50 && p.z < 3;
    }

    private TideBackLayout() {
    }
}
