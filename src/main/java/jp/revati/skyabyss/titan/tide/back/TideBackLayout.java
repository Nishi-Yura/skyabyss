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
    // 24: 試練の階の下の階 (TideDeck。広間の西の階段から下る H2・くらげの回廊 T03・渦潮の滑り台 T04・大貝の護送 T11・珊瑚の標本棚 T08・真珠守りの試し T12。P3。
    //     docs/design/29 の 3.7)。他の背の版の最大 (余燼 23) の次
    // 30: 任意の翼をなくした (息の間・ひれの道・殻の上の道・冠・光の井戸・宝箱・息の間の休み場 u0〜u3)。周回の既知の道もなくした
    // 31: 順路の入口の箱 (潮門の内側の段) の床の灯り (R6 の通しで真っ暗だった)
    // 32: 封じた潮の広間の灯り・壁の柱を外した (trial/RouteSeals。2026-10-22)
    // 33: 心臓の間 (殻の底) と休み場 heart を体の外 (trial/raid/InstanceArenas) へ移した
    // 34: 巨神の中は建てない (外見だけ。体・甲羅の溝の手直し・潮門の到着の段と、体の外の心臓の間)。2026-10-23
    public static final int LAYOUT_VERSION = 34;
    // 生成の海 (水の最上段は y=62)
    public static final int SEA = 63;

    // ---------------------------------------------------------------- 甲羅 (モデルの超楕円。tide_shell_titan.py の plan と同じ形、x1.6)
    // 版 20 の甲羅は中心 z -5、半長 137、半幅 114、n 2.6
    public static final int SHELL_CZ = -5;
    public static final double SHELL_N = 2.6;
    public static final int TOP_Y = 113;

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

    // 潮の広間
    public static final int HUB_R = 23;
    public static final int HUB_FLOOR = 70;
    public static final int HUB_LEDGE = 74;
    public static final Area HUB = new Area(-HUB_R, HUB_R, -HUB_R, HUB_R, HUB_FLOOR, 22);
    // 三つの石の灯り (底の門のまわりの床。西・東・前)
    public static final BlockPos LAMP_WEST = new BlockPos(-3, HUB_FLOOR, 0);
    public static final BlockPos LAMP_EAST = new BlockPos(3, HUB_FLOOR, 0);
    public static final BlockPos LAMP_FRONT = new BlockPos(0, HUB_FLOOR, -3);

    // 回り道 (干潮の道): 水盤の底の南東の戸口から、東の回廊の先まで上る坂。点を結ぶ幅 5 の通路
    // { x, z, 床の高さ }。最初の点は広間の戸口 (水盤の一部として満ちる)
    public static final int RAMP_HALF = 2;
    // 東の回廊から潮見の間の前庭への道 (y 74 の床)
    public static final Area EAST_HALL = new Area(27, 39, -10, -4, HUB_LEDGE, 8);

    public static final int PLATFORM_TOP = 68;
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

    public static final Area S0 = new Area(40, 59, -20, -6, HUB_LEDGE, 8);
    public static final int LEDGE_FLOOR = 92;
    // 溜めの内側 (x1, x2, z1, z2)。床のブロックは段の床 - 1。浮き石は B と M だけ
    public static final int[][] BASIN = {{64, 68, -16, -12}, {75, 79, -16, -12}, {81, 85, -25, -21}};

    // 群れの干潟 (前)。甲羅の前の洞 (天井 84)。北の壁の向こうに首がある。北東・北西の角の窓から首と頭が見える
    public static final Area FLATS = new Area(-38, 38, -131, -80, 63, 21);
    // 首の長い廊下の前室 f0 (z -64〜-48、床 66)
    public static final Area F0 = new Area(-8, 8, -64, -48, 66, 8);
    public static final int TUNNEL_FLOOR = 66;
    // 干潟の口 (廊下の北の端。潮招きの戦いの間だけ閉じる)
    public static final int MOUTH_Z = -79;
    public static final int MOUTH_HALF = 2;
    public static final int ROCK_TOP = 66;
    // 巣の候補地 (10 か所から、リセットの種で 5 つ)
    public static final int NEST_COUNT = 5;
    // 周回の長い干潟 (32 の 4 章、R5): 10 か所から 8 つ
    public static final int NEST_COUNT_LONG = 8;
    public static final int[][] NEST_SITES = {{-19, -97}, {19, -98}, {-32, -112}, {32, -112}, {-12, -113}, {14, -113}, {-27, -126}, {28, -125},
            {-3, -85}, {5, -122}};

    public static final Area SPIRAL = new Area(-2, 2, -2, 2, 50, 20);
    // 心臓の間 (殻の底) は体の外の封じた部屋 (区画 0 番の枠。trial/raid/InstanceArenas) へ移した (2026-10-23)。元の座標 (中心 (0, 51, -30)) を
    // HEART_DX / HEART_DZ だけずらす。前室の南の休み場 heart も一緒に動く (螺旋の階段とはつながらない)
    public static final int HEART_DX = jp.revati.skyabyss.trial.raid.InstanceArenas.HEART_X;
    public static final int HEART_DZ = jp.revati.skyabyss.trial.raid.InstanceArenas.HEART_Z + 30;
    public static final Area HEART_HALL = new Area(-1 + HEART_DX, 1 + HEART_DX, -9 + HEART_DZ, -3 + HEART_DZ, 50, 4);
    public static final Area HEART_REST = new Area(-4 + HEART_DX, 4 + HEART_DX, -9 + HEART_DZ, -3 + HEART_DZ, 50, 4);

    // ---------------------------------------------------------------- 潮鳴り石と、抜け道 (石が鳴ると光る床。踏むと戻れる)
    public static final BlockPos STONE_WEST = new BlockPos(ALTAR[0], ALTAR_TOP + 1, ALTAR[1] - 2);
    public static final BlockPos STONE_EAST = new BlockPos(83, LEDGE_FLOOR + 1, -38);

    // ---------------------------------------------------------------- 休み場 (焚き火のまわりに立つ)
    public record Rest(String id, Area area, BlockPos campfire, BlockPos spawn) {
    }

    public static final List<Rest> RESTS = concat(List.of(
            new Rest("hub", HUB, new BlockPos(-7, HUB_LEDGE + 1, 23), new BlockPos(-5, HUB_LEDGE + 1, 21)),
            new Rest("g0", G0, new BlockPos(-37, PLATFORM_TOP + 1, 4), new BlockPos(-35, PLATFORM_TOP + 1, 0)),
            new Rest("g1", G1, new BlockPos(PLAT_C[0] - 2, PLATFORM_TOP + 1, PLAT_C[1] + 2), new BlockPos(PLAT_C[0] + 2, PLATFORM_TOP + 1, PLAT_C[1] - 2)),
            new Rest("s0", S0, new BlockPos(42, HUB_LEDGE + 1, -18), new BlockPos(45, HUB_LEDGE + 1, -13)),
            new Rest("f0", F0, new BlockPos(-6, TUNNEL_FLOOR + 1, -62), new BlockPos(0, TUNNEL_FLOOR + 1, -56)),
            new Rest("heart", HEART_REST, new BlockPos(3 + HEART_DX, 51, -6 + HEART_DZ), new BlockPos(-2 + HEART_DX, 51, -6 + HEART_DZ))),
            List.of());

    private static List<Rest> concat(List<Rest> a, List<Rest> b) {
        List<Rest> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- 碑文 (05 の型。4 行の光る看板)
    public record Inscription(String key, BlockPos pos, Direction facing) {
    }

    public static final List<Inscription> LORE_SIGNS = List.of(
            new Inscription("sign.skyabyss.lore_tide_beach", new BlockPos(-36, 68, 67), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_hub", new BlockPos(-8, HUB_LEDGE + 2, 26), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_tide_west", new BlockPos(ALTAR[0] - 2, ALTAR_TOP + 2, ALTAR[1] - 4), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_east", new BlockPos(85, LEDGE_FLOOR + 2, -39), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_tide_front", new BlockPos(-7, ROCK_TOP + 1, -119), Direction.UP),
            new Inscription("sign.skyabyss.lore_tide_heart", new BlockPos(-4 + HEART_DX, 52, -8 + HEART_DZ), Direction.EAST));

    // 潮の底の門より奥 (螺旋の階段・前室・殻の底)
    public static boolean pastBottomGate(Vec3 p) {
        // 心臓の間と休み場 heart は体の外の封じた部屋 (trial/raid/InstanceArenas)
        return jp.revati.skyabyss.trial.raid.InstanceArenas.heartArena(jp.revati.skyabyss.progress.Titans.TIDE_SHELL).inflate(12).contains(p);
    }

    private TideBackLayout() {
    }
}
