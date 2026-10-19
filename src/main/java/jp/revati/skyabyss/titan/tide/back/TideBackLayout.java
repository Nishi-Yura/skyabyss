package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 潮殻の背 (tide_back) の配置図 (08 の 3 章)。建てる側 (TideIslandBuilder) と仕組みの側 (潮・試練・休み場・リセット) が参照する。
// 座標は「床のブロックの y」で書く (人が立つのは +1)。頭は -Z (北)。
// 島は巨神本体のモデルと同じ形のブロックの亀 (TideTurtle、ゲームの巨神の 1.7 倍)。試練はすべて甲羅の中に掘る (08 の 21 章)。
//
//                     [首と頭]  海から立ち上がる (z -82 より北)
//               [群れの干潟 (前の試練)]   甲羅の前の洞、z -82〜-50、床 63、潮で 0〜2 満ちる
//                        | 干潟の口 (z=-49)
//                  [干潟へのトンネル]      床 66
//                  [干潟の前室 f0]         z -30〜-18、床 66        真下に [殻の底 (心臓の間)] 中心 (0,-30) 床 50 (腹の中)
//                        | 干潟の門 (z=-13、干潮だけ開く)
//   [珊瑚の庭 (西)]--[潮の広間 (HUB)]--[潮見の間 (東)]           広間の床 70、潮で 0〜3 満ちる。縁の段 (74) から入る
//      床 64             | 縁のくぐり道            真上の甲羅の頂に [頂の塔] (月の窓の縦穴が塔の中を通る)
//                   [潮門の浜]  床 66  ── 北西の角から甲羅の上へ上る段 (外の景色と遺跡)
//                        | 潮門の廊下 (西へ)
//   [潮門] 甲羅の左後ろの縁、水際の大きな門。外に [到着の段] と、海へ下りる [見送りの段] (地上へ戻る)
//
// 広間は「沈んだ円い水盤」: 床 70 の円 (半径 12) が潮で 3 マス (71〜73) 満ちる。入口は高さ 74 の縁の段
// (南の段: 浜からのくぐり道、西の段: 庭への道、東の回廊: 潮見の間への道)。満潮なら水盤を泳いで東の回廊へ一段上がれる。
// 干潮なら水盤の底へ下り、南東の回り道 (底から東の回廊まで上る坂) を通る。北の干潟の門は床の高さで、干潮のあいだだけ開く。
public final class TideBackLayout {
    // 配置の版。2: 本番の島 (2026-10-17) / 3: 甲羅の地表・庭の壁・浜のすり鉢を直した (試遊 1 周目) /
    // 4: 巨神の形の亀にし、試練を甲羅の中に埋めた (08 の 21 章) /
    // 5: 亀を低ポリ案 B「段になった珊瑚礁の島」(四段の台地、頂に高い塔) の形に建て直し、上の三つの台地に封じた予約の間を足した (08 の 3.8節)
    // 6: 殻の底 (心臓の間) の天井を 1 段上げて、空気が 12 段にした (予告の高さ・心臓の大きさ 1.5 倍のため。08 の 5.5節)
    // 7: 詰み監査の修正 (甲羅の地表の 1 マス角の縦穴を埋めた。08 の「詰み監査」)
    // 8: 試練のわかりやすさ (入口の看板を 4 行にし、潮見の間の栓の色・戻しの栓・広間の案内の看板を足した。08 の 4.4節)
    public static final int LAYOUT_VERSION = 8;
    // 生成の海 (水の最上段は y=62)
    public static final int SEA = 63;
    public static final int SEA_FLOOR = 41;

    // ---------------------------------------------------------------- 甲羅 (モデルの超楕円。tide_shell_titan.py の plan と同じ形)
    // 案 B の甲羅は中心 z -3、半長 101、半幅 84、n 2.6 (単位)。1 単位 = 0.85 ブロック
    public static final int SHELL_CZ = -3;
    public static final int SHELL_RX = 71;
    public static final int SHELL_RZ = 85;
    public static final double SHELL_N = 2.6;
    // 甲羅の上 (外) の歩ける地面 = いちばん下の台地 (T1) の高さ。上の三つの台地は 114・126・137 (3.8節)
    public static final int TOP_Y = 101;
    public static final int[] TERRACE_Y = {101, 114, 126, 137};

    // 甲羅の地表の高さ (甲羅のいちばん上のブロックの y。上の遺跡・珊瑚は含まない)。甲羅の無い柱は Integer.MIN_VALUE
    public static int shellTop(int x, int z) {
        return TideTurtle.carapaceTop(x, z);
    }

    // 甲羅の平面の位置の 2 乗 (縁が 1。鋸歯の先は 1.2 くらい)。島から海に落ちたかを見るのに使う
    public static double shellS2(double x, double z) {
        double nx = Math.abs(x / 71.4);
        double nz = Math.abs((z + 2.6) / 85.9);
        double r = Math.pow(Math.pow(nx, SHELL_N) + Math.pow(nz, SHELL_N), 1.0 / SHELL_N);
        return r * r;
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

    // 潮門の浜 (甲羅の中の広間)。焚き火・炉・腰掛け・碑文。西の潮門の廊下から入り、北のくぐり道で広間へ
    public static final Area BEACH = new Area(-32, -12, 42, 62, 66, 8);
    public static final int BEACH_CEILING = 77;
    // 潮門 (モデルの左後ろの縁の大きな門。口は x -61〜-50、z 47〜61、y 65〜86。光る面は x -54〜-53、y 68〜82)。
    // 光る面の下 (y 65〜67) をくぐって中へ入り、廊下 (z 52〜56、床 64 → 66) で浜の西の壁へ
    public static final int GATE_MOUTH_X1 = -61;
    public static final int GATE_MOUTH_X2 = -50;
    public static final int GATE_MOUTH_Z1 = 48;
    public static final int GATE_MOUTH_Z2 = 60;
    public static final int GATE_FLOOR = 64;
    public static final int GATE_HALL_Z1 = 52;
    public static final int GATE_HALL_Z2 = 56;
    // 到着の段 (潮門の外。甲羅の縁と後ろのひれの付け根の上、床 64)
    public static final Area LANDING = new Area(-67, -56, 49, 59, GATE_FLOOR, 8);
    // 着く所: 左後ろのひれの上 (ひれの面は y 67)。北東を向くと、甲羅の脇腹に開いた潮門の大きな枠と、丸い甲羅が見える。
    // ひれの付け根から到着の段へ下り (3 段)、潮門の光る面の下をくぐって中へ
    public static final BlockPos ARRIVAL = new BlockPos(-80, 66, 70);
    public static final float ARRIVAL_YAW = -140f;
    // 見送りの段: 到着の段の西から海へ下りる段。いちばん下の段 (x -71〜-69、z 52〜56) に入ると地上へ戻る
    public static final int GATE_X = -70;
    public static final int GATE_Z = 53;
    public static final int GATE_Y0 = 63;
    public static final int GATE_Y1 = 66;
    // 甲羅の上へ上る段 (浜の西の壁の外、x -46〜-42、z 42〜46 の四角い螺旋。浜の西の壁の口 (-33, 44) から通路で入る)。上は甲羅の地表の口
    public static final int TOP_STAIR_X1 = -46;
    public static final int TOP_STAIR_Z1 = 42;
    // 頂の塔 (案 B の塔。中心 (0, -3)、幅 14、頂の台地 137 の上に 138〜181。塔の中は詰まっていて、月の窓の縦穴だけが通る)
    public static final int TOWER_CZ = -3;
    // 月の窓の縦穴の上端のガラス (塔の窓の高さ。ここから屋根の頂まで、半径 2.5 の柱がガラス)
    public static final int TOWER_DECK = 173;

    // 封じた予約の間 (08 の 3.8節)。上の三つの台地の中の、空の歩ける部屋と、そのあいだの階段。今は何もない (将来の巨神の内容用)
    public static final Area UPPER_1 = new Area(-32, 30, -38, 34, 101, 9);
    public static final Area UPPER_2 = new Area(-22, 22, -28, 22, 114, 9);
    public static final Area UPPER_3 = new Area(-10, 10, -16, 12, 126, 8);
    public static final List<Area> UPPER_ROOMS = List.of(UPPER_1, UPPER_2, UPPER_3);
    // 入口: 西の面 (T2 の崖、x -49) の封印の戸 (x -49、z -1〜1、y 102〜104)。外は T1 の地面 (101)。戸の奥は廊下 (x -48〜-33) で UPPER_1 へ
    public static final int UPPER_GATE_X = -49;
    public static final int UPPER_GATE_Z = 0;
    public static final int UPPER_HALL_X2 = -33;
    // 階段 (登る向き +z、幅 3、1 段ずつ。UPPER_1 の床 101 → UPPER_2 の床 114。x -20〜-18、z 8 から 13 段)
    public static final int UPPER_STAIR12_X = -20;
    public static final int UPPER_STAIR12_Z0 = 8;
    // 階段 (登る向き +x、幅 3。UPPER_2 の床 114 → UPPER_3 の床 126。z 8〜10、x -20 から 12 段)
    public static final int UPPER_STAIR23_Z = 8;
    public static final int UPPER_STAIR23_X0 = -20;

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
    // 月の窓の縦穴の中心 (頂の塔の真下。広間の中心から 3 だけ北)。塔の柱の段のガラスの床まで抜ける
    public static final int MOON_CZ = TOWER_CZ;
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
    // 北の縁は z 40 (浜の北の列 z 41〜42 と重ならないように。20.12節)
    public static final Area GARDEN = new Area(-62, -19, -42, 40, 64, 30);
    public static final int GARDEN_FLOOR = 64;
    public static final int GARDEN_DEPTH = 3;
    public static final int GARDEN_WALL_TOP = 71;
    // 庭は甲羅の中の洞。天井はおよそ 84〜86 (甲羅の低い縁では、地表から 4 段下)
    public static final int GARDEN_CEILING = 84;
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

    // 群れの干潟 (前)。甲羅の前の洞 (天井 78)。北の壁の向こうに首がある。北東・北西の角の窓から首と頭が見える
    public static final Area FLATS = new Area(-24, 24, -82, -50, 63, 12);
    public static final int FLATS_CEILING = 78;
    public static final int FLATS_FLOOR = 63;
    public static final int FLATS_DEPTH = 2;
    public static final Area F0 = new Area(-4, 4, -30, -18, 66, 5);
    public static final int TUNNEL_FLOOR = 66;
    // 干潟の口 (トンネルの北の端。潮招きの戦いの間だけ閉じる)
    public static final int MOUTH_Z = -49;
    // 干潟を前の版 (甲羅の外、z -104〜-72) から南へ動かした量。岩の島・巣・石の並びは前の版と同じ
    public static final int FLATS_DZ = 22;
    // 岩の島 (中心、上は 3x3、上の床 66)
    public static final int[][] FLATS_ROCKS = {{-16, -80 + FLATS_DZ}, {0, -79 + FLATS_DZ}, {16, -80 + FLATS_DZ}, {-19, -93 + FLATS_DZ},
            {-7, -91 + FLATS_DZ}, {7, -95 + FLATS_DZ}, {19, -92 + FLATS_DZ}, {0, -101 + FLATS_DZ}};
    public static final int ROCK_TOP = 66;
    // 巣の候補地 (6 か所から、リセットの種で 3 つ)
    public static final int[][] NEST_SITES = {{-10, -78 + FLATS_DZ}, {9, -77 + FLATS_DZ}, {-20, -86 + FLATS_DZ}, {20, -86 + FLATS_DZ},
            {-12, -99 + FLATS_DZ}, {12, -101 + FLATS_DZ}};
    public static final BlockPos BECKONER_HOME = new BlockPos(0, 64, -88 + FLATS_DZ);

    // ---------------------------------------------------------------- 殻の底 (心臓の間) と、潮の底の門からの螺旋の階段
    public static final int SPIRAL_TOP = 69;
    public static final int SPIRAL_BOTTOM = 51;
    public static final Area SPIRAL = new Area(-2, 2, -2, 2, 50, 20);
    public static final Area HEART_HALL = new Area(-1, 1, -9, -3, 50, 4);
    public static final Area HEART_REST = new Area(-4, 4, -9, -3, 50, 4);

    // ---------------------------------------------------------------- 潮鳴り石と、抜け道 (石が鳴ると光る床。踏むと戻れる)
    public static final BlockPos STONE_WEST = new BlockPos(ALTAR[0], ALTAR_TOP + 1, ALTAR[1] - 2);
    public static final BlockPos STONE_EAST = new BlockPos(53, LEDGE_FLOOR + 1, -34);
    public static final BlockPos STONE_FRONT = new BlockPos(0, ROCK_TOP + 1, -102 + FLATS_DZ);
    // 抜け道の床 (石の近く) と、運ぶ先
    public static final BlockPos RETURN_WEST = new BlockPos(ALTAR[0] + 2, ALTAR_TOP, ALTAR[1] + 1);
    public static final BlockPos RETURN_EAST = new BlockPos(51, LEDGE_FLOOR, -32);
    public static final BlockPos RETURN_FRONT = new BlockPos(1, ROCK_TOP, -100 + FLATS_DZ);
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
            new Inscription("sign.skyabyss.lore_tide_front", new BlockPos(-7, ROCK_TOP + 1, -91 + FLATS_DZ), Direction.UP),
            new Inscription("sign.skyabyss.lore_tide_heart", new BlockPos(-4, 52, -8), Direction.EAST));

    // ---------------------------------------------------------------- リセットで広間へ移す所 (試練の中)
    public static List<Area> trialAreas() {
        List<Area> list = new ArrayList<>();
        list.add(GARDEN);
        list.add(new Area(23, 60, -38, -2, 70, 30));
        list.add(new Area(-26, 26, -84, -31, 60, 20));
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
