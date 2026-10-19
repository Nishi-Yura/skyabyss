package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背 (storm_back) の部屋の座標 (12 の 3.3節・26 の 3.3)。頭は北 (-Z)、左の翼は西 (-X)。
// 鳥の形 (storm_back/bird.bin。水平 ×1.6・垂直 ×1.3) の中に、ここに書いた部屋を彫る (StormBirdBuilder ほか)。
// 「床」はいつも床のブロックの y (人が立つのはその 1 上)。
//
// ほかの担当へ (11.1 の約束): 雷獣の巣・竜骨の間・休み場はここから読む。数字を写さないこと (建て直しで動くことがある)。
//
// 版 20 の地帯 (巨神の形が読める順に、はじめから終わりまで一本の流れ):
//   尾羽の道 (羽軸の道・羽の広場) → 羽根の門 → 尾の間 → 風の広間 → [西: 風切りの骨 (骨の翼桁)] [東: 雷の骨 (銅の稲妻の筋)] [前: 嵐の冠・眼の間] → 竜骨 → 心臓
public final class StormBackLayout {
    // 縮尺 (docs/design/26)。水平 ×1.6。垂直は鳥の形だけ ×1.3 (部屋の高さは地帯ごとに決める)
    public static final double BACK_SCALE = 1.6;

    public static int S(int units) {
        return (int) Math.round(units * BACK_SCALE);
    }

    // 版が上がったら建て直す
    public static final int LAYOUT_VERSION = 20;   // 20: 26 の拡大 (水平 x1.6)。部屋ごとに作り直し、東の戸は 4 枚、嵐の冠・尾羽の広場・灯の道・目的の光の柱 (StormGuide)。 12: 歩く道の灯り (route_lamps)。 11: 肋の間の外を翼から生えた肋の尖塔に。 10: 西を縦の肋の登りに・東を雷の回路に・入口に拍の舵 (25 の 5章)。 9: ボス部屋の大門。 8: 建築の監査。 5: 詰み監査の修正。 4: 避雷の柱と戻しの鏡を専用のブロックに。 2: 風鳴り石・受け羽のブロックの名前を wind_stone・catch_feather に

    // 箱の部屋 (内側の範囲。床のブロックは floor、空くのは floor+1〜floor+height)
    public record Area(int x1, int x2, int z1, int z2, int floor, int height) {
        public AABB aabb() {
            return new AABB(x1, floor, z1, x2 + 1, floor + height + 1, z2 + 1);
        }

        public boolean contains(BlockPos p) {
            return p.getX() >= x1 && p.getX() <= x2 && p.getZ() >= z1 && p.getZ() <= z2 && p.getY() > floor && p.getY() <= floor + height;
        }

        public boolean contains(Vec3 p) {
            return aabb().contains(p);
        }
    }

    // 休み場: 焚き火 (青い焚き火)、目を覚ます所、着くと記録する範囲
    public record Rest(String id, BlockPos campfire, BlockPos spawn, Area area) {
    }

    // 看板 (碑文と入口の一言)。facing は看板の表の向き
    public record Inscription(String key, BlockPos pos, Direction facing) {
    }

    // ---------------------------------------------------------------- 尾羽の道 (到着 → 羽軸の道 → 羽の広場 → 羽根の門)
    // 到着 (尾羽の上)。北を向く。尾羽の背は y 144〜148 の薄い板 (鳥の形が決める)
    public static final BlockPos ARRIVAL = new BlockPos(0, 148, 164);
    public static final float ARRIVAL_YAW = 180f;
    // 羽軸の道 (尾羽の中心線に沿う、幅 7)。半幅
    public static final int PATH_X = 3;
    // 羽根の門 (腰の上)。くぐると腰の回廊を下りて尾の間へ。口 x -3〜3、高さ 7。鳥の形の門 (z 57〜62) に合わせる
    public static final int GATE_Z1 = 57;
    public static final int GATE_Z2 = 62;
    public static final int GATE_FLOOR = 155;
    public static final AABB FEATHER_GATE = new AABB(-10, GATE_FLOOR, GATE_Z1, 11, GATE_FLOOR + 19, GATE_Z2 + 1);
    // 地上へ戻る口: 到着の南の「下り風の輪」(床に光る輪、下へ吹く粒)。0.5 秒いると地上へ (StormTravel)
    public static final BlockPos DOWNDRAFT = new BlockPos(0, 148, 171);
    public static final AABB GATE_MOUTH = new AABB(-1, 147, 170, 2, 152, 173);
    // 羽の広場 (道の途中の広い台。休み場 tail。左右の翼の見晴らしに碑と宝箱)
    public static final Area TAIL_PLAZA = new Area(-21, 21, 104, 118, 146, 8);
    public static final BlockPos PLAZA_CENTER = new BlockPos(0, 147, 111);
    // 見晴らしの宝箱 (西・東)。一人に一度、小さな贈り物 (StormCaches)
    public static final BlockPos CACHE_WEST = new BlockPos(-19, 147, 106);
    public static final BlockPos CACHE_EAST = new BlockPos(19, 147, 106);
    // 嵐の雲の天井。ここより下へ落ちると嵐に投げ返される
    public static final int CLOUD_Y = 100;

    // ---------------------------------------------------------------- 腰の回廊・尾の間・風の広間
    public static final Area TAIL_HALL = new Area(-13, 13, 30, 45, 145, 6);
    public static final BlockPos TAIL_CENTER = new BlockPos(0, 146, 38);
    // 腰の回廊 (門 → 尾の間、尾の間 → 広間)。ピースフルのあいだは z SEAL_Z を封じる
    public static final int SEAL_Z = 25;
    public static final int CORRIDOR_HALF = 3;
    public static final Area HUB = new Area(-22, 22, -35, 10, 134, 20);
    public static final BlockPos HUB_CENTER = new BlockPos(0, 135, -12);
    // 広間の中央の昇り風 (嵐のとき 13 段上がって上の回廊へ)。穴は x -1〜1、z -27〜-25
    public static final BlockPos HUB_UPDRAFT = new BlockPos(0, 134, -26);
    public static final int UPPER_GALLERY_Y = 147;
    // 上の回廊 (北の壁の段) と、昇り風の上の着き場 (z -29〜-28)
    public static final Area GALLERY = new Area(-10, 10, -35, -30, UPPER_GALLERY_Y, 5);
    // 凪の道: 南東の螺旋の塔 (中心 (17, 6)、5x5) から東の壁ぞいの歩み板で上の回廊へ
    public static final int CALM_TOWER_X = 17;
    public static final int CALM_TOWER_Z = 6;
    // 竜骨の門 (広間の床)。広間の真ん中
    public static final AABB KEEL_GATE = new AABB(-2, 133, -14, 3, 135, -9);
    // 背の天窓 (広間の天井から背の上まで、ガラス)
    public static final int SKYLIGHT_R = 5;
    // 広間の羽軸の柱 (白い羽の柱。灯りと目当て)
    public static final int[][] HUB_PILLARS = {{-13, -6}, {13, -6}, {-13, -22}, {13, -22}, {-13, 5}, {9, 5}};

    // ---------------------------------------------------------------- 西の試練 風切りの骨 (骨の翼桁)
    // 道の中心の z (翼の軸)。回廊は z BONE_Z1〜BONE_Z2 (幅 23)
    public static final int WZ = -24;
    public static final int BONE_Z1 = -35;
    public static final int BONE_Z2 = -13;
    public static final int BONE_FLOOR = 151;
    public static final int BONE_HEIGHT = 16;
    // 肩の階段の広間 (広間の壁 → 肩の部屋。平らな 4 マスのあと 17 段上る。幅 7)
    public static final int PASS_FLAT = 4;
    public static final Area WEST_PASS = new Area(-50, -23, WZ - 3, WZ + 3, 134, 6);
    // 肩の部屋 (羽根・舵・休み場 w0)
    public static final Area W0 = new Area(-68, -51, BONE_Z1, BONE_Z2, BONE_FLOOR, 12);
    // 上腕 (肩 → 肘) と、肘の洞 (半径 14)、前腕 (肘 → 肋の間)
    public static final int WEST_UPPER_X1 = -120;
    public static final int WEST_UPPER_X2 = -69;
    public static final BlockPos WEST_ELBOW = new BlockPos(-132, BONE_FLOOR, WZ);
    public static final int ELBOW_R = 14;
    public static final int WEST_FORE_X1 = -178;
    public static final int WEST_FORE_X2 = -147;
    // 割れた床 (上腕の二つ。x の範囲。肩の側から手首の側へ。間は島 (休み場 wm))
    public static final int[][] WEST_GAPS = {{-86, -79}, {-110, -103}};
    // 島 (二つの割れた床のあいだ)
    public static final Area WEST_ISLAND = new Area(-102, -87, BONE_Z1, BONE_Z2, BONE_FLOOR, BONE_HEIGHT);
    // 昇り風の穴 (上腕の最初の割れた床の手前。凪 4・嵐 13 上がる)
    public static final int[] WEST_VENT_X = {-74};
    // 嵐の高い道 (昇り風で上がった梁。床 = BONE_FLOOR + 12)
    public static final int HIGH_BEAM_Y = BONE_FLOOR + 12;
    // 低い梁の幅 (z の本数)
    public static final int LOW_BEAM_WIDTH = 3;

    // ---- 肋の間 (25 の 5章 A「骨を縦に」): 前腕の骨を、縦の空洞の登りにした。
    // 空洞 (x RIB_X1〜RIB_X2・z BONE_Z1〜BONE_Z2) の床 = 層 0。梁 (3x3 の銅の格子 = 昇り風の穴) の螺旋が 9 段 (RIB_HOP ずつ上がる)。
    // 3 段おきが「層の岩棚」(5x5 の板。休み場・灯り)。頂の岩棚 (層 RIB_LAYERS) に西の石。凪の昇り風は RIB_HOP (4) 上がるので
    // 一段ずつ登れる。嵐の昇り風は 13 上がり、風切りの羽根で滑空して次の層の岩棚へ (滑空の輪がつなぐ近道)。降りは頂から輪の流れで床へ
    public static final int RIB_X1 = -178;
    public static final int RIB_X2 = -157;
    public static final int RIB_LAYERS = 3;
    public static final int RIB_HOP = 4;
    public static final int RIB_HOPS = 3;
    public static final int RIB_RISE = RIB_HOP * RIB_HOPS;
    public static final int RIB_TOP_Y = BONE_FLOOR + RIB_LAYERS * RIB_RISE;
    // 空洞の高さ (床の上。頂の岩棚の 5 上まで)
    public static final int RIB_HEIGHT = RIB_TOP_Y - BONE_FLOOR + 5;
    // 梁の格子 (3x3 の穴が 3 列 3 行。中心の座標。空洞の真ん中)
    public static final int RIB_GRID_X0 = (RIB_X1 + RIB_X2) / 2 - 3;
    public static final int RIB_GRID_Z0 = WZ - 3;
    public static final int RIB_STEP = 3;
    // 螺旋の順 (列 u・行 v)。0 段目は入口に近い東の真ん中
    public static final int[][] RIB_RING = {{2, 1}, {2, 0}, {1, 0}, {0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}};
    public static final Area RIB_HALL = new Area(RIB_X1, RIB_X2, BONE_Z1, BONE_Z2, BONE_FLOOR, RIB_HEIGHT);

    // 梁 i (0〜RIB_LAYERS*RIB_HOPS) の格子の穴の中心 (床のブロックの y)
    public static BlockPos ribLedge(int i) {
        int[] uv = RIB_RING[Math.floorMod(i, RIB_RING.length)];
        return new BlockPos(RIB_GRID_X0 + RIB_STEP * uv[0], BONE_FLOOR + RIB_HOP * i, RIB_GRID_Z0 + RIB_STEP * uv[1]);
    }

    // 層 k (0〜RIB_LAYERS) の岩棚の板の中心 (床のブロックの y)。層 0 は床の上
    public static BlockPos ribLanding(int layer) {
        return ribLedge(layer * RIB_HOPS);
    }

    // 層の岩棚の板 (5x5。肋の間の中に収まる所だけ)
    public static Area ribSlab(int layer) {
        BlockPos c = ribLanding(layer);
        return new Area(Math.max(RIB_X1, c.getX() - 2), Math.min(RIB_X2, c.getX() + 2), Math.max(BONE_Z1, c.getZ() - 2), Math.min(BONE_Z2, c.getZ() + 2), c.getY(), 4);
    }

    // 風切りの羽根の滑空の輪 (嵐の高い昇り風から次の層の岩棚へ。層 k → k+1 の中ほど、嵐の昇り風の上の端の高さ)
    public static Vec3 ribRing(int layer) {
        BlockPos a = ribLanding(layer);
        BlockPos b = ribLanding(layer + 1);
        return new Vec3((a.getX() + b.getX()) / 2.0 + 0.5, a.getY() + 1 + StormBackClock.STORM_LIFT + 0.6, (a.getZ() + b.getZ()) / 2.0 + 0.5);
    }

    // 頂の岩棚の板の中心 (層 RIB_LAYERS)。西の石・焚き火・光る床はこの板の上
    public static final BlockPos RIB_TOP = ribLanding(RIB_LAYERS);
    public static final BlockPos WEST_STONE = RIB_TOP.offset(1, 1, -1);
    // 降りの輪 (頂の岩棚の東の縁 → 床の東側。輪の流れで降りる近道)
    public static final Vec3[] RIB_DOWN_RINGS = {new Vec3(RIB_TOP.getX() + 3.4, RIB_TOP_Y + 0.9, RIB_TOP.getZ() + 0.5),
            new Vec3(RIB_TOP.getX() + 4.2, RIB_TOP_Y - 11, RIB_TOP.getZ() + 1.5), new Vec3(RIB_TOP.getX() + 4.6, RIB_TOP_Y - 24, RIB_TOP.getZ() + 2.5)};
    public static final Vec3 RIB_DOWN_LANDING = new Vec3(RIB_TOP.getX() + 4.6, BONE_FLOOR + 2.9, RIB_TOP.getZ() + 3.5);
    public static final BlockPos RIB_TOP_REST = RIB_TOP.offset(1, 1, 1);

    // ---------------------------------------------------------------- 東の試練 雷の骨 (銅の稲妻の筋)
    public static final Area EAST_PASS = new Area(23, 50, WZ - 3, WZ + 3, 134, 6);
    public static final Area E0 = new Area(51, 68, BONE_Z1, BONE_Z2, BONE_FLOOR, 12);
    public static final int EAST_X1 = 69;
    public static final int EAST_X2 = 191;
    public static final int EAST_HEIGHT = 13;
    // 骨の戸 (x)。戸の口は z WZ-2〜WZ+2、高さ 5。戸の手前 (肩の側) に避雷の柱と回路。4 枚 (四つ目は鎖の回路)
    public static final int[] EAST_DOORS = {96, 123, 150, 177};
    // 戸の口 (z の半幅・高さ)
    public static final int DOOR_HALF = 2;
    public static final int DOOR_HEIGHT = 5;
    // 戸の柱 (回路の出口。雷がここへ届くと対の戸が開く)。回路の全体は StormCircuit
    public static final List<BlockPos> EAST_RODS = List.of(new BlockPos(91, BONE_FLOOR + 1, WZ - 7), new BlockPos(120, BONE_FLOOR + 1, WZ + 8),
            new BlockPos(148, BONE_FLOOR + 1, WZ + 9), new BlockPos(174, BONE_FLOOR + 1, WZ));
    // 右手首の瘤 (雷獣の巣)。直径 26。避雷の柱は中心から半径 9、60 度おきに 6 本
    public static final BlockPos RAIJU_NEST = new BlockPos(204, BONE_FLOOR + 1, WZ);
    public static final int RAIJU_NEST_RADIUS = 13;
    public static final int RAIJU_NEST_CEILING = BONE_FLOOR + 16;
    public static final int RAIJU_ROD_RADIUS = 9;
    // 巣の口 (雷獣の戦いの間だけ閉じる。雷獣の担当が閉じる)
    public static final AABB RAIJU_NEST_MOUTH = new AABB(EAST_X2 - 1, BONE_FLOOR + 1, WZ - 2, EAST_X2 + 1, BONE_FLOOR + 6, WZ + 3);
    // 東の石は洞の頂の台の上。雷獣を倒すと、洞の真ん中の昇り風が立って台へ届く
    public static final BlockPos EAST_STONE = new BlockPos(RAIJU_NEST.getX() + 4, BONE_FLOOR + 14, WZ);
    public static final Area EAST_PERCH = new Area(RAIJU_NEST.getX() + 2, RAIJU_NEST.getX() + 6, WZ - 2, WZ + 2, BONE_FLOOR + 13, 2);
    public static final BlockPos RAIJU_VENT = new BlockPos(RAIJU_NEST.getX() - 1, BONE_FLOOR + 1, WZ);

    // ---------------------------------------------------------------- 前の試練 嵐の冠・眼の間
    // 首の回廊 (広間の上の回廊 → 冠の間)。幅 7
    public static final Area NECK = new Area(-3, 3, -76, -36, UPPER_GALLERY_Y, 7);
    // 冠の間 (嵐の冠。7 本の銅の避雷針が頭の上に立つ。休み場 f0・舵・戻しの鏡)
    public static final Area F0 = new Area(-10, 10, -97, -77, UPPER_GALLERY_Y, 9);
    // 眼の間 (鏡の台 5x5、間隔 4)
    public static final Area EYE_HALL = new Area(-11, 11, -137, -115, 148, 10);
    public static final BlockPos EYE_HALL_CENTER = new BlockPos(0, 149, -126);
    // 鏡の台の格子。光は床から 2 の高さ
    public static final int GRID_X0 = -8;
    public static final int GRID_Z0 = -134;
    public static final int GRID_STEP = 4;
    public static final int BEAM_Y = 150;
    public static final BlockPos LEFT_EYE = new BlockPos(-14, 153, -126);
    // 戻しの鏡 (冠の間の台の取っ手)
    public static final BlockPos MIRROR_RESET = new BlockPos(-8, 148, -95);
    // もう一つの戻しの鏡 (眼の間の入口の東の隅。冠の間まで戻らずに引ける。25 の 3.2)
    public static final BlockPos MIRROR_RESET_HALL = new BlockPos(10, 150, -115);
    // 前の石の小部屋 (嘴の付け根)。三つの受け羽が光ると戸が開く
    public static final Area BEAK_ROOM = new Area(-3, 3, -146, -140, 148, 6);
    public static final int BEAK_DOOR_Z1 = -139;
    public static final int BEAK_DOOR_Z2 = -138;
    public static final BlockPos FRONT_STONE = new BlockPos(0, 149, -144);
    // 冠の避雷針 (頭の上の 7 本。鳥の形に合わせた飾りの根元。眼の間の南の上)
    public static final int CROWN_Z = -114;

    // ---------------------------------------------------------------- 竜骨の間 (最終ボス)
    // 竜骨の門 → 広間の床の下の低い道 (床 131、x -22〜-2) → 西の螺旋 (中心 (-25, -12)) → 床 118 の道 → 前室
    public static final int KEEL_TUNNEL_FLOOR = 131;
    public static final int KEEL_TUNNEL_X1 = -22;
    public static final BlockPos KEEL_SPIRAL = new BlockPos(-25, 118, -12);
    public static final Area KEEL_ANTE = new Area(-4, 4, -47, -41, 118, 5);
    public static final BlockPos HEART_ROOM = new BlockPos(0, 117, -24);
    public static final Vec3 HEART_POS = new Vec3(0.5, 118, -23.5);
    public static final int HEART_ROOM_RADIUS = 15;
    // 竜骨の間の空気のいちばん上 (天井のブロックは 131。その上は竜骨の門からの低い道の床なので、131 は埋めたままにする)
    public static final int HEART_ROOM_CEILING = 130;
    // 竜骨の間の避雷の柱 (半径 9、北東・北西・南東・南西) と風穴 (北・南・東・西)
    public static final int HEART_ROD_RADIUS = 9;
    public static final List<BlockPos> HEART_RODS = List.of(
            HEART_ROOM.offset(6, 1, -6), HEART_ROOM.offset(-6, 1, -6), HEART_ROOM.offset(6, 1, 6), HEART_ROOM.offset(-6, 1, 6));
    public static final List<BlockPos> HEART_VENTS = List.of(
            HEART_ROOM.offset(0, 0, -9), HEART_ROOM.offset(0, 0, 9), HEART_ROOM.offset(9, 0, 0), HEART_ROOM.offset(-9, 0, 0));
    // 竜骨の間の入口 (入ると閉じる)。前室の北の口
    public static final AABB HEART_DOOR = new AABB(-1, KEEL_ANTE.floor() + 1, -40, 2, KEEL_ANTE.floor() + 4, -39);

    // ---------------------------------------------------------------- 抜け道 (石が鳴ると光る床。踏むと広間の近くへ)
    public static final BlockPos RETURN_WEST = RIB_TOP.offset(2, 0, 0);
    public static final BlockPos RETURN_EAST = new BlockPos(RAIJU_NEST.getX(), BONE_FLOOR + 1, WZ + 6);
    public static final BlockPos RETURN_FRONT = new BlockPos(2, 148, -142);
    public static final Map<String, BlockPos> RETURN_TO = Map.of(
            "west", new BlockPos(-52, BONE_FLOOR + 1, WZ), "east", new BlockPos(52, BONE_FLOOR + 1, WZ), "front", new BlockPos(0, UPPER_GALLERY_Y + 1, -92));
    // 広間の床の三つの灯り (鳴った石)
    public static final BlockPos LAMP_WEST = new BlockPos(-5, 134, 7);
    public static final BlockPos LAMP_EAST = new BlockPos(5, 134, 7);
    public static final BlockPos LAMP_FRONT = new BlockPos(0, 134, 9);

    // ---------------------------------------------------------------- 休み場 (焚き火)
    public static final List<Rest> RESTS = List.of(
            new Rest("tail", new BlockPos(0, 147, 115), new BlockPos(2, 147, 114), TAIL_PLAZA),
            new Rest("hub", new BlockPos(-17, 135, 7), new BlockPos(-15, 135, 7), HUB),
            new Rest("w0", new BlockPos(-56, BONE_FLOOR + 1, BONE_Z2 - 1), new BlockPos(-58, BONE_FLOOR + 1, BONE_Z2 - 1), W0),
            new Rest("wm", new BlockPos(-95, BONE_FLOOR + 1, BONE_Z2 - 2), new BlockPos(-97, BONE_FLOOR + 1, BONE_Z2 - 2), WEST_ISLAND),
            new Rest("w1", new BlockPos(-132, BONE_FLOOR + 1, WZ + 7), new BlockPos(-132, BONE_FLOOR + 1, WZ + 5),
                    new Area(-146, -118, BONE_Z1 - 3, BONE_Z2 + 3, BONE_FLOOR, 12)),
            ribRest("w2", 1), ribRest("w3", 2), ribRest("w4", RIB_LAYERS),
            new Rest("e0", new BlockPos(56, BONE_FLOOR + 1, BONE_Z2 - 1), new BlockPos(58, BONE_FLOOR + 1, BONE_Z2 - 1), E0),
            eastRest("e1", 0), eastRest("e2", 1), eastRest("e3", 2),
            new Rest("f0", new BlockPos(7, UPPER_GALLERY_Y + 1, -84), new BlockPos(5, UPPER_GALLERY_Y + 1, -84), F0),
            new Rest("heart", new BlockPos(2, KEEL_ANTE.floor() + 1, -44), new BlockPos(0, KEEL_ANTE.floor() + 1, -43), KEEL_ANTE));
    public static final Map<String, BlockPos> REST;
    public static final List<String> REST_ORDER;

    static {
        java.util.LinkedHashMap<String, BlockPos> m = new java.util.LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (Rest r : RESTS) {
            m.put(r.id(), r.spawn());
            order.add(r.id());
        }
        REST = java.util.Collections.unmodifiableMap(m);
        REST_ORDER = List.copyOf(order);
    }

    // ---------------------------------------------------------------- 看板 (碑文 6 枚と入口の一言 3 枚と、見晴らしの碑 2 枚)
    public static final List<Inscription> LORE_SIGNS = List.of(
            new Inscription("sign.skyabyss.lore_storm_tail", new BlockPos(-5, 147, 30), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_storm_hub", new BlockPos(-6, 136, 10), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_west", new BlockPos(-60, BONE_FLOOR + 2, BONE_Z2), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_east", new BlockPos(60, BONE_FLOOR + 2, BONE_Z2), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_eye", new BlockPos(6, 150, -114), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_heart", new BlockPos(-3, KEEL_ANTE.floor() + 2, -48), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_storm_vane_w", new BlockPos(-19, 148, 104), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_storm_vane_e", new BlockPos(19, 148, 104), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_storm_crown", new BlockPos(6, UPPER_GALLERY_Y + 2, -98), Direction.SOUTH));
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("sign.skyabyss.storm_west", new BlockPos(-23, 137, WZ - 5), Direction.EAST),
            new Inscription("sign.skyabyss.storm_east", new BlockPos(23, 137, WZ - 5), Direction.WEST),
            new Inscription("sign.skyabyss.storm_front", new BlockPos(-6, UPPER_GALLERY_Y + 2, -98), Direction.SOUTH));

    private StormBackLayout() {
    }

    // 肋の間の層 layer の岩棚の休み場 (板の隅に焚き火。昇り風の穴から 1.7 以上離す)
    private static Rest ribRest(String id, int layer) {
        BlockPos c = ribLanding(layer);
        boolean top = layer == RIB_LAYERS;
        BlockPos fire = top ? RIB_TOP_REST : c.offset(2, 1, -2);
        BlockPos spawn = top ? RIB_TOP.offset(0, 1, 1) : c.offset(2, 1, 2);
        return new Rest(id, fire, spawn, ribSlab(layer));
    }

    // 東の回廊の休み場 (戸 i の向こう側の、南の壁ぎわ)
    private static Rest eastRest(String id, int door) {
        int x = door < 2 ? EAST_DOORS[door] + 5 : EAST_X2 - 4;
        int z = BONE_Z2 - 1;
        return new Rest(id, new BlockPos(x, BONE_FLOOR + 1, z), new BlockPos(x + 2, BONE_FLOOR + 1, z), new Area(x - 5, x + 7, BONE_Z1, BONE_Z2, BONE_FLOOR, EAST_HEIGHT));
    }

    // 雷獣の巣の避雷の柱 (6 本、60 度おき) の根元
    public static List<BlockPos> raijuRods() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            out.add(RAIJU_NEST.offset((int) Math.round(Math.cos(a) * RAIJU_ROD_RADIUS), 0, (int) Math.round(Math.sin(a) * RAIJU_ROD_RADIUS)));
        }
        return out;
    }

    public static BlockPos rest(String id) {
        BlockPos p = REST.get(id);
        return p == null ? REST.get("hub") : p;
    }

    // 試練の中 (リセットのときに広間へ移す所)
    public static boolean inWest(Vec3 p) {
        return p.x < -22.5 && p.x > RIB_X1 - 14 && p.z > BONE_Z1 - 4 && p.z < BONE_Z2 + 4 && p.y > 138 && p.y < RIB_TOP_Y + 12;
    }

    public static boolean inEast(Vec3 p) {
        return p.x > 22.5 && p.x < RAIJU_NEST.getX() + 20 && p.z > BONE_Z1 - 4 && p.z < BONE_Z2 + 4 && p.y > 138 && p.y < BONE_FLOOR + 26;
    }

    public static boolean inFront(Vec3 p) {
        return p.z < HUB.z1() - 2 && p.z > -152 && Math.abs(p.x) < 18 && p.y > 140 && p.y < 172;
    }

    // 竜骨の門より奥 (広間の床の下の道・螺旋・前室・竜骨の間)
    public static boolean pastKeel(Vec3 p) {
        return p.y < HUB.floor() && p.y > 108 && p.x > -34 && p.x < 20 && p.z > -52 && p.z < 6;
    }

    public static boolean inTrial(Vec3 p) {
        return inWest(p) || inEast(p) || inFront(p) || pastKeel(p);
    }

    // 体の中 (尾の間より奥。ピースフルのあいだは入れない)。腰の回廊の封印より北で、体の中の高さ
    public static boolean pastSeal(Vec3 p) {
        return p.z < SEAL_Z && p.y < 170 && p.y > 108 && Math.abs(p.x) < 240 && p.z > -152;
    }

    // 外へ開いていてよい所: 尾羽の上 (到着の台・羽軸の道・羽の広場)・羽根の門・天窓
    public static boolean openToSky(int x, int y, int z) {
        if (z >= GATE_Z2 + 1 && z <= DOWNDRAFT.getZ() + 4 && Math.abs(x) <= 24 && y >= 143) {
            return true;
        }
        if (x >= FEATHER_GATE.minX - 2 && x <= FEATHER_GATE.maxX + 2 && z >= FEATHER_GATE.minZ - 5 && z <= FEATHER_GATE.maxZ + 4) {
            return true;
        }
        return y > HUB.floor() + HUB.height() && x * x + (z + 12) * (z + 12) <= (SKYLIGHT_R + 1) * (SKYLIGHT_R + 1);
    }

    // 眼の間の格子の台の座標 (列 c・行 r)
    public static BlockPos gridCell(int c, int r) {
        return new BlockPos(GRID_X0 + GRID_STEP * c, BEAM_Y, GRID_Z0 + GRID_STEP * r);
    }

    // 羽軸の道の床 (到着の台 → 羽根の門の 1 段ずつの坂。鳥の背の高さに沿う)。builder と同じ式
    public static int roadFloor(int z, int topAtCenter) {
        return topAtCenter + 1;
    }
}
