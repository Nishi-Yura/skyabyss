package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 潮殻の背 (tide_back) の配置図 (08 の 3 章、26 の 9 章)。建てる側 (TideIslandBuilder) と仕組みの側 (潮・試練・休み場・リセット) が参照する。
// 座標は「床のブロックの y」で書く (人が立つのは +1)。頭は -Z (北)。
// 島は巨神本体のモデルと同じ形のブロックの亀 (TideTurtle。水平 x1.6・垂直 x1.3 で作り直した版 20)。試練はすべて甲羅の中に掘る。
//
//   [首と頭]                                         海から立ち上がる (z -135 より北)
//     [群れの干潟 (前の試練)]  z -131〜-80、床 63、潮で 0〜2 満ちる。巣 5・岩の島 14
//        | 干潟の口 (z=-79)
//     [首の長い廊下]  床 66。まん中に前室 f0 (z -64〜-48)。壁の窪みに碑と宝
//        | 干潟の門 (z=-24、潮に関わらずグループにはいつも開けてある)         真下に [殻の底 (心臓の間)] 中心 (0,-30) 床 50
//   [珊瑚の庭 (西)]--[潮の広間 (HUB, 半径 23)]--[東の潮見の間 (広間 + 二つの廊下)]       広間の床 70、潮で 0〜3 満ちる
//      床 64            | 縁のくぐり道 (長い階段回廊)             真上の甲羅の頂に [頂の塔]
//                    [潮門の浜]  床 66 (35x35)  ── 北西から甲羅の上へ上る段 (外の景色と遺跡)
//                       | 潮門の廊下 (西へ、幅 6)
//   [潮門] 甲羅の左後ろの縁、水際の大きな門。外に [到着の段] と、海へ下りる [見送りの段] (地上へ戻る)
//
// 広間は「沈んだ円い水盤」: 床 70 の円 (半径 23) が潮で 3 マス (71〜73) 満ちる。入口は高さ 74 の縁の段
// (南の段: 浜からの回廊、西の段: 庭への道、東の回廊: 潮見の間への道)。満潮なら水盤を泳いで東の回廊へ一段上がれる。
// 干潮なら水盤の底へ下り、南東の回り道 (底から東の回廊まで上る坂) を通る。北の干潟の門は床の高さで、潮に関わらずいつも開いている (満潮は水の中を泳いでくぐる)。
public final class TideBackLayout {
    // 配置の版。2: 本番の島 (2026-10-17) / 3: 甲羅の地表・庭の壁・浜のすり鉢を直した (試遊 1 周目) /
    // 4: 巨神の形の亀にし、試練を甲羅の中に埋めた (08 の 21 章) /
    // 5: 亀を低ポリ案 B「段になった珊瑚礁の島」(四段の台地、頂に高い塔) の形に建て直し、上の三つの台地に封じた予約の間を足した (08 の 3.8節)
    // 6: 殻の底 (心臓の間) の天井を 1 段上げて、空気が 12 段にした (予告の高さ・心臓の大きさ 1.5 倍のため。08 の 5.5節)
    // 7: 詰み監査の修正 (甲羅の地表の 1 マス角の縦穴を埋めた。08 の「詰み監査」)
    // 8: 試練のわかりやすさ (入口の看板を 4 行にし、潮見の間の栓の色・戻しの栓・広間の案内の看板を足した。08 の 4.4節)
    // 9: 干潟の門を潮に関わらず開けておき、眠る試練を「静かな試練」(石は自分で鳴らす) にした (08 の 3.5・7.2節)
    // 10: ボス部屋の大門 (干潟の口・殻の底の入口。TideBossGates)
    // 10: 息の間 (亀の肺。予約の間 1〜3 と月の窓の縦穴を一つの大空洞にし、水を湛えた皿と風の柱、頂の真珠守りと展望の間を足した。封印の戸と階段はなくなった。
    //    docs/design/24 の案 C、08 の 3.8節)
    // 12: 建築の監査 (体の 1 ブロックの浮き・くぼみを直す)。11: 建築の監査 (階段の向き・庭の入口の段・甲羅の出口の段)
    // 14: 歩く道の灯り (route_lamps)
    // 20: 背の拡大 (水平 x1.6・垂直 x1.3。広間・庭・潮見の間・干潟・息の間を作り直し、ひれの道・貝の間・冠を足した。26 の 9 章)。
    //    他の背の版の最大 (岩鯨 19) の次
    // 21: 試練の階 (TideDeck。潮の広間の南の段の戸口から通路・試練の広間・潮の背渡り T02・満ち潮の円環 T10・貝の合唱 T06・二つの海 T07・試練の板。docs/design/29 の 3.2)
    public static final int LAYOUT_VERSION = 21;
    // 生成の海 (水の最上段は y=62)
    public static final int SEA = 63;
    public static final int SEA_FLOOR = 41;

    // ---------------------------------------------------------------- 甲羅 (モデルの超楕円。tide_shell_titan.py の plan と同じ形、x1.6)
    // 版 20 の甲羅は中心 z -5、半長 137、半幅 114、n 2.6
    public static final int SHELL_CZ = -5;
    public static final int SHELL_RX = 114;
    public static final int SHELL_RZ = 136;
    public static final double SHELL_N = 2.6;
    // 甲羅の上 (外) の歩ける地面 = いちばん下の台地 (T1) の高さ。上の三つの台地は 130・146・159
    public static final int TOP_Y = 113;
    public static final int[] TERRACE_Y = {113, 130, 146, 159};

    // 甲羅の地表の高さ (甲羅のいちばん上のブロックの y。上の遺跡・珊瑚は含まない)。甲羅の無い柱は Integer.MIN_VALUE
    public static int shellTop(int x, int z) {
        return TideTurtle.carapaceTop(x, z);
    }

    // 甲羅の平面の位置の 2 乗 (縁が 1。鋸歯の先は 1.2 くらい)。島から海に落ちたかを見るのに使う
    public static double shellS2(double x, double z) {
        double nx = Math.abs(x / 114.2);
        double nz = Math.abs((z + 4.2) / 137.4);
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

    // 潮門の浜 (甲羅の中の広間)。焚き火・炉・腰掛け・碑文・潮だまり。西の潮門の廊下から入り、北の回廊で広間へ
    public static final Area BEACH = new Area(-46, -12, 66, 100, 66, 12);
    public static final int BEACH_CEILING = 84;
    // 潮門 (モデルの左後ろの縁の大きな門。光る面は x -101〜-99、z 74〜99。口の下は y 65〜72)。
    // 光る面の下をくぐって中へ入り、廊下 (z 84〜89、床 64 → 66) で浜の西の壁へ
    public static final int GATE_MOUTH_X1 = -98;
    public static final int GATE_MOUTH_X2 = -80;
    public static final int GATE_MOUTH_Z1 = 77;
    public static final int GATE_MOUTH_Z2 = 96;
    public static final int GATE_FLOOR = 64;
    public static final int GATE_HALL_Z1 = 84;
    public static final int GATE_HALL_Z2 = 89;
    // 到着の段 (潮門の外。甲羅の縁と後ろのひれの付け根の上、床 64)
    public static final Area LANDING = new Area(-108, -91, 78, 95, GATE_FLOOR, 8);
    // 着く所: 左後ろのひれの上 (ひれの面は y 66)。北東を向くと、甲羅の脇腹に開いた潮門の大きな枠と、丸い甲羅が見える
    public static final BlockPos ARRIVAL = new BlockPos(-124, 67, 114);
    public static final float ARRIVAL_YAW = -140f;
    // 見送りの段: 到着の段の西から海へ下りる段。いちばん下の段 (x -113〜-111、z 85〜86) に入ると地上へ戻る
    public static final int GATE_X = -112;
    public static final int GATE_Z = 85;
    public static final int GATE_Y0 = 63;
    public static final int GATE_Y1 = 66;
    // 甲羅の上へ上る段 (浜の西の壁の外、x -66〜-62、z 68〜72 の四角い螺旋。浜の西の壁の口 (-53, 70) から通路で入る)。上は甲羅の地表の口
    public static final int TOP_STAIR_X1 = -66;
    public static final int TOP_STAIR_Z1 = 68;
    // 頂の塔 (中心 (0, -5)、幅 20、頂の台地 159 の上に 160〜216。塔の中は詰まっていて、月の窓の縦穴だけが通る)
    public static final int TOWER_CZ = -5;
    // 月の窓の縦穴の上端のガラス (塔の窓の高さ。ここから屋根の頂まで、半径 3.5 の柱がガラス)
    public static final int TOWER_DECK = 206;

    // 予約の間 1〜3 (08 の 3.8節)。上の三つの台地の中の部屋。息の間 (BreathLayout) が真ん中を円く掘り抜いて一つの気室にした。
    // 部屋は気室を巻く輪の回廊 (外半径 31・27・23) になる (TideIslandBuilder.upper)。箱は輪の外接
    public static final Area UPPER_1 = new Area(-31, 31, -36, 26, 113, 12);
    public static final Area UPPER_2 = new Area(-27, 27, -32, 22, 130, 12);
    public static final Area UPPER_3 = new Area(-23, 23, -28, 18, 146, 9);
    public static final List<Area> UPPER_ROOMS = List.of(UPPER_1, UPPER_2, UPPER_3);
    // 輪の外半径 (気室の中心から)
    public static final int[] UPPER_RING_R = {31, 27, 23};
    // 入口: 西の面 (T2 の崖、x -78) の戸口 (x -78、z -2〜2、y 114〜117。いつも開いている。潮・時間・天気で閉じない)。外は T1 の地面 (113)。
    // 戸口の奥は廊下 (x -77〜-30) で UPPER_1 の輪へ
    public static final int UPPER_GATE_X = -78;
    public static final int UPPER_GATE_Z = 0;
    public static final int UPPER_HALL_X2 = -31;
    // 縁の回廊 (浜から広間の南の段へ上る屋根のある坂。x -13〜-7)
    public static final int CAVE_X1 = -19;
    public static final int CAVE_X2 = -13;
    public static final int CAVE_Z_BEACH = 65;
    public static final int CAVE_Z_HUB = 26;
    // ピースフルのあいだ封じる所 (回廊の途中)
    public static final int CAVE_SEAL_Z = 45;

    // 潮の広間
    public static final int HUB_R = 23;
    public static final int HUB_FLOOR = 70;
    public static final int HUB_LEDGE = 74;
    public static final int HUB_CEILING = 93;
    public static final int MOON_R = 4;
    // 月の窓の縦穴の中心 (頂の塔の真下。広間の中心から 5 だけ北)。塔の柱の段のガラスの床まで抜ける
    public static final int MOON_CZ = TOWER_CZ;
    public static final Area HUB = new Area(-HUB_R, HUB_R, -HUB_R, HUB_R, HUB_FLOOR, 22);
    // 南の段 (浜から) と西の段 (庭へ) はつながった L 字。東の回廊 (潮見の間へ) は離れている
    public static final Area LEDGE_S = new Area(-22, -3, 19, 26, HUB_LEDGE, 8);
    public static final Area LEDGE_W = new Area(-26, -19, -11, 18, HUB_LEDGE, 8);
    public static final Area GALLERY_E = new Area(19, 26, -14, 4, HUB_LEDGE, 8);
    // 南の段から水盤の底へ下りる階段 (x -12〜-8、z 18〜16 の 3 段)
    public static final int HUB_STAIR_X1 = -12;
    public static final int HUB_STAIR_X2 = -8;
    // 干潟の門 (北の壁、x -2〜2、y 71〜73、z -24)。いつも開いている (戦っているグループにだけ閉じる)
    public static final int FLATS_GATE_Z = -24;
    public static final int FLATS_GATE_HALF = 2;
    // 潮の底の門 (床の格子、x -1〜1、z -1〜1、y 70)。三つの石で開く
    public static final int BOTTOM_GATE_R = 1;
    // 三つの石の灯り (底の門のまわりの床。西・東・前)
    public static final BlockPos LAMP_WEST = new BlockPos(-3, HUB_FLOOR, 0);
    public static final BlockPos LAMP_EAST = new BlockPos(3, HUB_FLOOR, 0);
    public static final BlockPos LAMP_FRONT = new BlockPos(0, HUB_FLOOR, -3);

    // 回り道 (干潮の道): 水盤の底の南東の戸口から、東の回廊の先まで上る坂。点を結ぶ幅 5 の通路
    // { x, z, 床の高さ }。最初の点は広間の戸口 (水盤の一部として満ちる)
    public static final int RAMP_HALF = 2;
    public static final int[][] RAMP = {
            {16, 14, 70}, {27, 14, 70}, {27, 22, 71}, {40, 22, 72}, {40, 8, 73}, {33, 8, 74}, {33, -4, 74}};
    // 東の回廊から潮見の間の前庭への道 (y 74 の床)
    public static final Area EAST_HALL = new Area(27, 39, -10, -4, HUB_LEDGE, 8);

    // 珊瑚の庭 (西)。屋根のない、潮で 0〜3 満ちる水路の庭 (水路の幅 5)
    public static final Area GARDEN = new Area(-99, -32, -67, 64, 64, 36);
    public static final int GARDEN_FLOOR = 64;
    public static final int GARDEN_DEPTH = 3;
    public static final int GARDEN_WALL_TOP = 73;
    // 庭は甲羅の中の洞。天井はおよそ 96〜98 (甲羅の低い縁では、地表から 4 段下)
    public static final int GARDEN_CEILING = 96;
    public static final int PLATFORM_TOP = 68;
    public static final int CHANNEL_HALF = 2;
    // 台の中心 (潮除けの台)。上は 7x7、高さ 4 (床 68)
    public static final int PLAT_CORE = 3;
    public static final int[] PLAT_A = {-80, 42};
    public static final int[] PLAT_B1 = {-88, 14};
    public static final int[] PLAT_B2 = {-66, 19};
    public static final int[] PLAT_C = {-77, -6};
    public static final int[] PLAT_D1 = {-85, -32};
    public static final int[] PLAT_D2 = {-66, -32};
    public static final int[] ALTAR = {-64, -54};
    public static final int[] CLAM = {-83, 58};
    public static final int ALTAR_TOP = 69;
    // 広間の西の段から庭の入口 g0 へ下りる道 (x -27〜-32。74 → 68)。g0 から庭の水路へ 4 段で下りる (x -40〜-43)
    public static final Area G0 = new Area(-39, -32, -6, 6, PLATFORM_TOP, 8);
    public static final Area G1 = new Area(PLAT_C[0] - PLAT_CORE, PLAT_C[0] + PLAT_CORE, PLAT_C[1] - PLAT_CORE, PLAT_C[1] + PLAT_CORE, PLATFORM_TOP, 4);
    // 水路 (台と台をつなぐ L 字、幅 5)。道の形 (三通り) で開くものが変わる
    public enum Channel {
        CLAM_A(CLAM, PLAT_A), A_B1(PLAT_A, PLAT_B1), A_B2(PLAT_A, PLAT_B2), B1_C(PLAT_B1, PLAT_C), B2_C(PLAT_B2, PLAT_C),
        C_D1(PLAT_C, PLAT_D1), C_D2(PLAT_C, PLAT_D2), D1_E(PLAT_D1, ALTAR), D2_E(PLAT_D2, ALTAR), G0_C(new int[] {-44, 0}, PLAT_C);

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

    // 潮見の間 (東)。広間 (x 40〜98、z -64〜-6) の中に、三段の溜め (下 B・中 M・上 T) の仕掛け (版 12 までの形を (+30, -4) 動かした)。
    // 段の床は 74・80・86、岩棚は 92。溜めと戸と取っ手の相互の位置は仕掛け (TideWatch) と一体なので動かさない
    public static final int WATCH_DX = 30;
    public static final int WATCH_DZ = -4;
    public static final Area WATCH_HALL = new Area(40, 98, -64, -6, HUB_LEDGE, 30);
    public static final Area S0 = new Area(40, 59, -20, -6, HUB_LEDGE, 8);
    public static final int[] TIER_FLOOR = {74, 80, 86};
    public static final int LEDGE_FLOOR = 92;
    // 溜めの内側 (x1, x2, z1, z2)。床のブロックは段の床 - 1。浮き石は B と M だけ
    public static final int[][] BASIN = {{64, 68, -16, -12}, {75, 79, -16, -12}, {81, 85, -25, -21}};
    // 溜めの戸 (人が浮き石へ乗る口。溜めの水が 1 段以下のときだけ開く)。T は南と北の二つ (水が 0 のときだけ開く)
    public static final BlockPos[] BASIN_DOOR = {new BlockPos(63, 75, -14), new BlockPos(74, 81, -14), new BlockPos(83, 87, -20)};
    public static final BlockPos T_EXIT_DOOR = new BlockPos(83, 87, -26);
    // 段の広さ (浮き石を降りて立つ所)
    public static final Area TIER0 = new Area(58, 62, -18, -10, 74, 5);
    public static final Area TIER1 = new Area(69, 73, -17, -11, 80, 5);
    public static final Area TIER2 = new Area(80, 86, -19, -12, 86, 5);
    public static final Area LEDGE_E = new Area(80, 86, -39, -35, LEDGE_FLOOR, 5);

    // 群れの干潟 (前)。甲羅の前の洞 (天井 84)。北の壁の向こうに首がある。北東・北西の角の窓から首と頭が見える
    public static final Area FLATS = new Area(-38, 38, -131, -80, 63, 21);
    public static final int FLATS_CEILING = 84;
    public static final int FLATS_FLOOR = 63;
    public static final int FLATS_DEPTH = 2;
    // 首の長い廊下の前室 f0 (z -64〜-48、床 66)
    public static final Area F0 = new Area(-8, 8, -64, -48, 66, 8);
    public static final int TUNNEL_FLOOR = 66;
    // 干潟の口 (廊下の北の端。潮招きの戦いの間だけ閉じる)
    public static final int MOUTH_Z = -79;
    public static final int MOUTH_HALF = 2;
    // 版 12 までの干潟のずらし (今は 0。式が残っている所のため)
    public static final int FLATS_DZ = 0;
    // 岩の島 (中心、上は 3x3、上の床 66)。北のはし (0, -126) は東の石の島
    public static final int[][] FLATS_ROCKS = {{-28, -92}, {-10, -90}, {10, -93}, {28, -91}, {-32, -104}, {-18, -107}, {-4, -102}, {12, -107},
            {26, -105}, {-24, -119}, {-9, -121}, {9, -117}, {24, -120}, {0, -127}};
    public static final int ROCK_TOP = 66;
    // 巣の候補地 (10 か所から、リセットの種で 5 つ)
    public static final int NEST_COUNT = 5;
    public static final int[][] NEST_SITES = {{-19, -97}, {19, -98}, {-32, -112}, {32, -112}, {-12, -113}, {14, -113}, {-27, -126}, {28, -125},
            {-3, -85}, {5, -122}};
    public static final BlockPos BECKONER_HOME = new BlockPos(0, 64, -108);

    // ---------------------------------------------------------------- 殻の底 (心臓の間) と、潮の底の門からの螺旋の階段
    public static final int SPIRAL_TOP = 69;
    public static final int SPIRAL_BOTTOM = 51;
    public static final Area SPIRAL = new Area(-2, 2, -2, 2, 50, 20);
    public static final Area HEART_HALL = new Area(-1, 1, -9, -3, 50, 4);
    public static final Area HEART_REST = new Area(-4, 4, -9, -3, 50, 4);

    // ---------------------------------------------------------------- 潮鳴り石と、抜け道 (石が鳴ると光る床。踏むと戻れる)
    public static final BlockPos STONE_WEST = new BlockPos(ALTAR[0], ALTAR_TOP + 1, ALTAR[1] - 2);
    public static final BlockPos STONE_EAST = new BlockPos(83, LEDGE_FLOOR + 1, -38);
    public static final BlockPos STONE_FRONT = new BlockPos(0, ROCK_TOP + 1, -127);
    // 抜け道の床 (石の近く) と、運ぶ先
    public static final BlockPos RETURN_WEST = new BlockPos(ALTAR[0] + 2, ALTAR_TOP, ALTAR[1] + 1);
    public static final BlockPos RETURN_EAST = new BlockPos(81, LEDGE_FLOOR, -36);
    public static final BlockPos RETURN_FRONT = new BlockPos(1, ROCK_TOP, -125);
    public static final BlockPos RETURN_WEST_TO = new BlockPos(-22, HUB_LEDGE + 1, 0);
    public static final BlockPos RETURN_EAST_TO = new BlockPos(22, HUB_LEDGE + 1, -4);
    public static final BlockPos RETURN_FRONT_TO = new BlockPos(2, 67, -56);

    // ---------------------------------------------------------------- 休み場 (焚き火のまわりに立つ)
    public record Rest(String id, Area area, BlockPos campfire, BlockPos spawn) {
    }

    public static final List<Rest> RESTS = concat(List.of(
            new Rest("hub", HUB, new BlockPos(-7, HUB_LEDGE + 1, 23), new BlockPos(-5, HUB_LEDGE + 1, 21)),
            new Rest("g0", G0, new BlockPos(-37, PLATFORM_TOP + 1, 4), new BlockPos(-35, PLATFORM_TOP + 1, 0)),
            new Rest("g1", G1, new BlockPos(PLAT_C[0] - 2, PLATFORM_TOP + 1, PLAT_C[1] + 2), new BlockPos(PLAT_C[0] + 2, PLATFORM_TOP + 1, PLAT_C[1] - 2)),
            new Rest("s0", S0, new BlockPos(42, HUB_LEDGE + 1, -18), new BlockPos(45, HUB_LEDGE + 1, -13)),
            new Rest("f0", F0, new BlockPos(-6, TUNNEL_FLOOR + 1, -62), new BlockPos(0, TUNNEL_FLOOR + 1, -56)),
            new Rest("heart", HEART_REST, new BlockPos(3, 51, -6), new BlockPos(-2, 51, -6)),
            // 息の間 (BreathLayout)。u0 = 縁 (入口の泡の近く)、u1 = 予約の間 2 の床 (皿 2 の隣)、u2 = 予約の間 3 の床 (皿 5 の隣)、u3 = 展望の間。
            // 位置は BreathLayout の式と同じ (ここでは数で書く: クラスの初期化が互いを待たないように。自己テストが合っているか見る)
            new Rest("u0", new Area(-16, -6, -13, 4, 113, 8), new BlockPos(-13, 114, -6), new BlockPos(-12, 114, -9)),
            new Rest("u1", new Area(5, 16, -26, -12, 130, 8), new BlockPos(11, 131, -20), new BlockPos(9, 131, -17)),
            new Rest("u2", new Area(-10, 10, 10, 19, 146, 8), new BlockPos(-4, 147, 16), new BlockPos(3, 147, 16)),
            new Rest("u3", new Area(-9, -2, -15, -4, 206, 3), new BlockPos(-6, 207, -10), new BlockPos(-5, 207, -9))),
            // 試練の階 (TideDeck。番号は 10 から。上の 10 個の並びは変えない: RESTS.get(n) で引く所がある)
            TideDeck.rests());

    private static List<Rest> concat(List<Rest> a, List<Rest> b) {
        List<Rest> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- 碑文 (05 の型。4 行の光る看板)
    public record Inscription(String key, BlockPos pos, Direction facing) {
    }

    // LORE_SIGNS のうち、伝承ではなく使い方の立て札 (伝承の項目を持たない)
    public static final java.util.Set<String> SIGNPOSTS = java.util.Set.of("sign.skyabyss.tide_breath_pad", "sign.skyabyss.tide_breath_rematch");

    public static final List<Inscription> LORE_SIGNS = List.of(
            new Inscription("sign.skyabyss.lore_tide_beach", new BlockPos(-36, 68, 67), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_hub", new BlockPos(-8, HUB_LEDGE + 2, 26), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_tide_west", new BlockPos(ALTAR[0] - 2, ALTAR_TOP + 2, ALTAR[1] - 4), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_east", new BlockPos(85, LEDGE_FLOOR + 2, -39), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_front", new BlockPos(-7, ROCK_TOP + 1, -119), Direction.UP),
            new Inscription("sign.skyabyss.lore_tide_heart", new BlockPos(-4, 52, -8), Direction.EAST),
            // 息の間: 展望の間の碑文 2 枚 (北の壁。窓の外)、縁の入口の泡の脇の立て札、舞台の再戦の台の脇の立て札
            new Inscription("sign.skyabyss.lore_tide_breath_a", new BlockPos(-8, 208, -14), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_breath_b", new BlockPos(6, 208, -14), Direction.SOUTH),
            new Inscription("sign.skyabyss.tide_breath_pad", new BlockPos(-13, 114, -2), Direction.UP),
            new Inscription("sign.skyabyss.tide_breath_rematch", new BlockPos(-3, 161, -1), Direction.UP));

    // ---------------------------------------------------------------- リセットで広間へ移す所 (試練の中)
    public static List<Area> trialAreas() {
        List<Area> list = new ArrayList<>();
        list.add(GARDEN);
        list.add(new Area(39, 99, -65, -5, 70, 34));
        list.add(new Area(-40, 40, -133, -31, 60, 26));
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
