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
    public static final int LAYOUT_VERSION = 35;   // 35: 巨神の中は建てない (外見だけ。体・尾羽の上の到着の台と道と広場・肋の尖塔の外の姿と、体の外の竜骨の間)。 34: 竜骨の間と前室を体の外 (trial/raid/InstanceArenas) へ移した。 33: 入口の箱の床の四隅に海のランタン (StormBackBuilder の entry lights)。 32: 封じた風の広間・尾の間の飾りと灯り・丸天井と肋と羽の壁を外す (trial/RouteSeals)。 31: 周回の風切りの骨の前の道「風の渡り」(翼の前の縁の外の空の羽の台・休み場 wp・翼の付け根の洞・肩の部屋の北の壁の戸。StormLongWalk。32 の 4 章、R5)。   // 30: 任意の翼 (尾羽の庭・二つの塔・翼の外の廊・先の見張り台の宝箱と休み場)・羽の広場の西と東の宝箱・抜け道の床・雷翼の共鳴の炉を外す。試練の甲板への螺旋と広場の床は残す。 25: 遺物ブロック (宝の箱・休み火・レバー・碑・鐘を雷翼の様式 = 銅と羽・淡い金に)。 24: 第二の渡り場と五つの新作 (29 の P3。S04 羽根の吊り橋・S07 二枚の鏡の間・S08 気圧の弁・S11 避雷の護送・S12 羽守長の頂。第一の渡り場の南の橋から)。 22: 試練の甲板 (29 の P2。尾の上の渡り場と四つの試練 S02・S03・S09・S10。背の広場から橋)・広間の三つの出口の戸。 21: 任意の翼 (26 の 9.5。背の広場・風見の塔・避雷の塔・翼の外の廊・翼の先・尾羽の庭)・広間の屋根の肋と羽の壁・銅の筋・二つ目の鏡の間。 20: 26 の拡大 (水平 x1.6)。部屋ごとに作り直し、東の戸は 4 枚、嵐の冠・尾羽の広場・灯の道・目的の光の柱 (StormGuide)。 12: 歩く道の灯り (route_lamps)。 11: 肋の間の外を翼から生えた肋の尖塔に。 10: 西を縦の肋の登りに・東を雷の回路に・入口に拍の舵 (25 の 5章)。 9: ボス部屋の大門。 8: 建築の監査。 5: 詰み監査の修正。 4: 避雷の柱と戻しの鏡を専用のブロックに。 2: 風鳴り石・受け羽のブロックの名前を wind_stone・catch_feather に

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
    public static final int GATE_Z2 = 62;
    public static final int GATE_FLOOR = 155;
    // 地上へ戻る口: 到着の南の「下り風の輪」(床に光る輪、下へ吹く粒)。0.5 秒いると地上へ (StormTravel)
    public static final BlockPos DOWNDRAFT = new BlockPos(0, 148, 171);
    public static final AABB GATE_MOUTH = new AABB(-1, 147, 170, 2, 152, 173);
    // 羽の広場 (道の途中の広い台。休み場 tail。左右の翼の見晴らしに碑)
    public static final Area TAIL_PLAZA = new Area(-21, 21, 104, 118, 146, 8);
    public static final BlockPos PLAZA_CENTER = new BlockPos(0, 147, 111);
    // 嵐の雲の天井。ここより下へ落ちると嵐に投げ返される
    public static final int CLOUD_Y = 100;

    // ---------------------------------------------------------------- 腰の回廊・尾の間・風の広間
    public static final Area TAIL_HALL = new Area(-13, 13, 30, 45, 145, 6);
    public static final BlockPos TAIL_CENTER = new BlockPos(0, 146, 38);
    public static final Area HUB = new Area(-22, 22, -35, 10, 134, 20);
    public static final BlockPos HUB_CENTER = new BlockPos(0, 135, -12);
    public static final int UPPER_GALLERY_Y = 147;
    // 上の回廊 (北の壁の段) と、昇り風の上の着き場 (z -29〜-28)
    public static final Area GALLERY = new Area(-10, 10, -35, -30, UPPER_GALLERY_Y, 5);

    // ---------------------------------------------------------------- 西の試練 風切りの骨 (骨の翼桁)
    // 道の中心の z (翼の軸)。回廊は z BONE_Z1〜BONE_Z2 (幅 23)
    public static final int WZ = -24;
    public static final int BONE_Z1 = -35;
    public static final int BONE_Z2 = -13;
    public static final int BONE_FLOOR = 151;
    public static final int BONE_HEIGHT = 16;
    // 肩の部屋 (羽根・舵・休み場 w0)
    public static final Area W0 = new Area(-68, -51, BONE_Z1, BONE_Z2, BONE_FLOOR, 12);
    // 島 (二つの割れた床のあいだ)
    public static final Area WEST_ISLAND = new Area(-102, -87, BONE_Z1, BONE_Z2, BONE_FLOOR, BONE_HEIGHT);

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
    // 梁の格子 (3x3 の穴が 3 列 3 行。中心の座標。空洞の真ん中)
    public static final int RIB_GRID_X0 = (RIB_X1 + RIB_X2) / 2 - 3;
    public static final int RIB_GRID_Z0 = WZ - 3;
    public static final int RIB_STEP = 3;
    // 螺旋の順 (列 u・行 v)。0 段目は入口に近い東の真ん中
    public static final int[][] RIB_RING = {{2, 1}, {2, 0}, {1, 0}, {0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}};

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

    // 層 layer の岩棚の「着いたら灯る」灯りの場所 (板の西の縁の上)。まだ着いていないグループには消えた石
    public static BlockPos ribLayerLight(int layer) {
        Area s = ribSlab(layer);
        return new BlockPos(s.x1(), s.floor() + 3, s.z1());
    }

    // 頂の岩棚の板の中心 (層 RIB_LAYERS)。西の石・焚き火・光る床はこの板の上
    public static final BlockPos RIB_TOP = ribLanding(RIB_LAYERS);
    public static final BlockPos WEST_STONE = RIB_TOP.offset(1, 1, -1);
    public static final BlockPos RIB_TOP_REST = RIB_TOP.offset(1, 1, 1);

    public static final Area E0 = new Area(51, 68, BONE_Z1, BONE_Z2, BONE_FLOOR, 12);
    public static final int EAST_X2 = 191;
    public static final int EAST_HEIGHT = 13;
    // 骨の戸 (x)。戸の口は z WZ-2〜WZ+2、高さ 5。戸の手前 (肩の側) に避雷の柱と回路。4 枚 (四つ目は鎖の回路)
    public static final int[] EAST_DOORS = {96, 123, 150, 177};
    // 右手首の瘤 (雷獣の巣)。直径 26。避雷の柱は中心から半径 9、60 度おきに 6 本
    public static final BlockPos RAIJU_NEST = new BlockPos(204, BONE_FLOOR + 1, WZ);
    public static final int RAIJU_NEST_RADIUS = 13;
    public static final int RAIJU_NEST_CEILING = BONE_FLOOR + 16;
    public static final int RAIJU_ROD_RADIUS = 9;
    // 巣の口 (雷獣の戦いの間だけ閉じる。雷獣の担当が閉じる)
    public static final AABB RAIJU_NEST_MOUTH = new AABB(EAST_X2 - 1, BONE_FLOOR + 1, WZ - 2, EAST_X2 + 1, BONE_FLOOR + 6, WZ + 3);
    // 東の石は洞の頂の台の上。雷獣を倒すと、洞の真ん中の昇り風が立って台へ届く
    public static final BlockPos EAST_STONE = new BlockPos(RAIJU_NEST.getX() + 4, BONE_FLOOR + 14, WZ);

    // ---------------------------------------------------------------- 前の試練 嵐の冠
    // 首の回廊 (広間の上の回廊 → 冠の間)。幅 7
    public static final Area NECK = new Area(-3, 3, -76, -36, UPPER_GALLERY_Y, 7);
    // 冠の間 (嵐の冠。7 本の銅の避雷針が頭の上に立つ。休み場 f0・舵・戻しの鏡)
    public static final Area F0 = new Area(-10, 10, -97, -77, UPPER_GALLERY_Y, 9);
    public static final BlockPos FRONT_STONE = new BlockPos(0, 149, -144);

    // 竜骨の間 (心臓の間) と前室 (休み場 heart) は体の外の封じた部屋 (区画 0 番の枠。trial/raid/InstanceArenas) へ移した (2026-10-23)。
    // 元の座標 (中心 (0, 117, -24)) を HEART_DX / HEART_DZ だけずらす。前室は螺旋の階段とはつながらない
    public static final int HEART_DX = jp.revati.skyabyss.trial.raid.InstanceArenas.HEART_X;
    public static final int HEART_DZ = jp.revati.skyabyss.trial.raid.InstanceArenas.HEART_Z + 24;
    public static final Area KEEL_ANTE = new Area(-4 + HEART_DX, 4 + HEART_DX, -47 + HEART_DZ, -41 + HEART_DZ, 118, 5);
    public static final BlockPos HEART_ROOM = new BlockPos(HEART_DX, 117, -24 + HEART_DZ);
    public static final int HEART_ROOM_RADIUS = 15;
    // 竜骨の間の空気のいちばん上 (天井のブロックは 131。その上は竜骨の門からの低い道の床なので、131 は埋めたままにする)
    public static final int HEART_ROOM_CEILING = 130;
    public static final List<BlockPos> HEART_RODS = List.of(
            HEART_ROOM.offset(6, 1, -6), HEART_ROOM.offset(-6, 1, -6), HEART_ROOM.offset(6, 1, 6), HEART_ROOM.offset(-6, 1, 6));
    public static final List<BlockPos> HEART_VENTS = List.of(
            HEART_ROOM.offset(0, 0, -9), HEART_ROOM.offset(0, 0, 9), HEART_ROOM.offset(9, 0, 0), HEART_ROOM.offset(-9, 0, 0));
    // 竜骨の間の入口 (入ると閉じる)。前室の北の口
    public static final AABB HEART_DOOR = new AABB(-1 + HEART_DX, KEEL_ANTE.floor() + 1, -40 + HEART_DZ, 2 + HEART_DX, KEEL_ANTE.floor() + 4, -39 + HEART_DZ);

    // ---------------------------------------------------------------- 広間の灯り (鳴った石の三つ)
    // 広間の床の三つの灯り (鳴った石)
    public static final BlockPos LAMP_WEST = new BlockPos(-5, 134, 7);
    public static final BlockPos LAMP_EAST = new BlockPos(5, 134, 7);
    public static final BlockPos LAMP_FRONT = new BlockPos(0, 134, 9);

    // ---------------------------------------------------------------- 休み場 (焚き火)
    // 背は外見だけ (体の中は建てない) なので、休み場は外の尾の広場と、心臓の間の前室 (区画の封じた部屋) だけ
    public static final List<Rest> RESTS = List.of(
            new Rest("tail", new BlockPos(0, 147, 115), new BlockPos(2, 147, 114), TAIL_PLAZA),
            new Rest("heart", new BlockPos(2 + HEART_DX, KEEL_ANTE.floor() + 1, -44 + HEART_DZ), new BlockPos(HEART_DX, KEEL_ANTE.floor() + 1, -43 + HEART_DZ), KEEL_ANTE));
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

    // ---------------------------------------------------------------- 看板 (試練の入口の一言 3 枚)
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("sign.skyabyss.storm_west", new BlockPos(-23, 137, WZ - 5), Direction.EAST),
            new Inscription("sign.skyabyss.storm_east", new BlockPos(23, 137, WZ - 5), Direction.WEST),
            new Inscription("sign.skyabyss.storm_front", new BlockPos(-6, UPPER_GALLERY_Y + 2, -98), Direction.SOUTH));

    private StormBackLayout() {
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
        return p.x < -22.5 && p.x > RIB_X1 - 14 && p.z > BONE_Z1 - 14 && p.z < BONE_Z2 + 4 && p.y > 138 && p.y < RIB_TOP_Y + 12;
    }

    public static boolean inEast(Vec3 p) {
        return p.x > 22.5 && p.x < RAIJU_NEST.getX() + 20 && p.z > BONE_Z1 - 14 && p.z < BONE_Z2 + 4 && p.y > 138 && p.y < BONE_FLOOR + 26;
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

}
