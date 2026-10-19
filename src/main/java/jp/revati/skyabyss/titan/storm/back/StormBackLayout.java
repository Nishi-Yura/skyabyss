package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背 (storm_back) の部屋の座標 (12 の 3.3節)。頭は北 (-Z)、左の翼は西 (-X)。
// 鳥の形 (storm_back/bird.bin) の中に、ここに書いた部屋を彫る (StormBirdBuilder)。
// 「床」はいつも床のブロックの y (人が立つのはその 1 上)。
//
// ほかの担当へ (11.1 の約束): 雷獣の巣・竜骨の間・休み場はここから読む。数字を写さないこと (建て直しで動くことがある)。
public final class StormBackLayout {
    // 版が上がったら建て直す
    // 9: ボス部屋の大門と建築の監査の両方
    public static final int LAYOUT_VERSION = 9;   // 8: 建築の監査 (肩の道の最初の段が広間の床に埋まっていた)。 7: 眼の間の碑を受け羽の候補から離した (南の壁 x -4 → 4。25 の 3.2)・西の岩棚の穴はどの拍でも届く。 6: 尾の間の西の壁の螺旋 (背の上へ出る段) をふさいだ (どこへも通じていなかった)。 5: 詰み監査の修正 (竜骨の螺旋の最後の段・背の小さな縦穴)。 4: 避雷の柱と戻しの鏡を専用のブロックに (storm_rod_*・return_mirror)。3: 外の鳥の色を低ポリ案 A (嵐の大鷲) に合わせた。2: 風鳴り石・受け羽のブロックの名前を wind_stone・catch_feather に

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

    // ---------------------------------------------------------------- 到着と羽根の門
    // 到着 (尾羽の上)。北を向く。y は鳥の形を建てたときの尾羽の上に合わせる
    public static final BlockPos ARRIVAL = new BlockPos(0, 145, 94);
    public static final float ARRIVAL_YAW = 180f;
    // 尾羽の上から羽根の門までの歩く道 (幅 3)
    public static final int PATH_X = 1;
    // 羽根の門 (腰の上)。くぐると腰の回廊を下りて尾の間へ。口 x -2〜2、高さ 5
    public static final int GATE_Z1 = 40;
    public static final int GATE_Z2 = 42;
    public static final int GATE_FLOOR = 154;
    public static final AABB FEATHER_GATE = new AABB(-6, GATE_FLOOR, GATE_Z1, 7, GATE_FLOOR + 9, GATE_Z2 + 1);
    // 地上へ戻る口: 到着の南の「下り風の輪」(床に光る輪、下へ吹く粒)。0.5 秒いると地上へ (StormTravel)
    public static final BlockPos DOWNDRAFT = new BlockPos(0, 145, 99);
    public static final AABB GATE_MOUTH = new AABB(-1, 144, 98, 2, 148, 101);
    // 嵐の雲の天井。ここより下へ落ちると嵐に投げ返される
    public static final int CLOUD_Y = 100;

    // ---------------------------------------------------------------- 腰の回廊・尾の間・風の広間
    public static final Area TAIL_HALL = new Area(-10, 10, 20, 32, 146, 6);
    public static final BlockPos TAIL_CENTER = new BlockPos(0, 147, 26);
    // 腰の回廊 (門 → 尾の間、尾の間 → 広間)。ピースフルのあいだは z SEAL_Z を封じる
    public static final int SEAL_Z = 19;
    public static final Area HUB = new Area(-14, 14, -18, 10, 137, 19);
    public static final BlockPos HUB_CENTER = new BlockPos(0, 138, -4);
    // 広間の中央の昇り風 (嵐のとき 13 段上がって上の回廊へ)。穴は x -1〜1、z -13〜-11
    public static final BlockPos HUB_UPDRAFT = new BlockPos(0, 137, -12);
    public static final int UPPER_GALLERY_Y = 150;
    // 上の回廊 (北の壁の段) と、昇り風の上の着き場
    public static final Area GALLERY = new Area(-6, 6, -18, -16, UPPER_GALLERY_Y, 4);
    // 凪の道: 南東の螺旋の塔 (中心 (9, 4)、5x5) から東の壁ぞいの高い歩み板で上の回廊へ
    public static final int CALM_TOWER_X = 10;
    public static final int CALM_TOWER_Z = 5;
    // 竜骨の門 (広間の床)
    public static final AABB KEEL_GATE = new AABB(-2, 136, -6, 3, 138, -1);
    // 背の天窓 (広間の天井から背の上まで、ガラス)
    public static final int SKYLIGHT_R = 4;

    // ---------------------------------------------------------------- 西の試練 風切りの骨
    public static final Area WEST_PASS = new Area(-22, -15, -7, -5, 137, 5);
    public static final Area W0 = new Area(-30, -23, -12, 0, 142, 6);
    public static final int BONE_Z1 = -24;
    public static final int BONE_Z2 = -10;
    public static final int BONE_FLOOR = 142;
    public static final int BONE_HEIGHT = 14;
    // 上腕 (肩 → 肘)、肘の洞、前腕 (肘 → 手首)
    public static final int WEST_UPPER_X1 = -64;
    public static final int WEST_UPPER_X2 = -31;
    public static final BlockPos WEST_ELBOW = new BlockPos(-71, 142, -17);
    public static final int ELBOW_R = 7;
    public static final int WEST_FORE_X1 = -115;
    public static final int WEST_FORE_X2 = -78;
    // 割れた床 (x の範囲。肩の側から手首の側へ)
    public static final int[][] WEST_GAPS = {{-44, -39}, {-59, -54}, {-92, -87}, {-108, -103}};
    // 昇り風の穴 (割れた床の手前。凪 4・嵐 13 上がる)
    public static final int[] WEST_VENT_X = {-37, -85};
    // 嵐の高い道 (昇り風で上がった梁。床 = BONE_FLOOR + 12)
    public static final int HIGH_BEAM_Y = BONE_FLOOR + 12;
    // 左手首の瘤 (西の石は高い岩棚の上。岩棚の下の穴は、どの拍でも岩棚まで持ち上げる)
    public static final BlockPos WEST_KNOB = new BlockPos(-128, 143, -17);
    public static final int KNOB_R = 12;
    public static final int KNOB_CEILING = 158;
    public static final Area WEST_LEDGE = new Area(-139, -134, -21, -13, 154, 3);
    public static final BlockPos WEST_STONE = new BlockPos(-137, 155, -17);
    public static final BlockPos WEST_KNOB_VENT = new BlockPos(-132, 143, -17);

    // ---------------------------------------------------------------- 東の試練 雷の骨
    public static final Area EAST_PASS = new Area(15, 22, -7, -5, 137, 5);
    public static final Area E0 = new Area(23, 30, -12, 0, 142, 6);
    public static final int EAST_X1 = 31;
    public static final int EAST_X2 = 115;
    public static final int EAST_HEIGHT = 10;
    public static final BlockPos EAST_ELBOW = new BlockPos(71, 142, -17);
    // 骨の戸 (x)。戸の口は z -18〜-16、高さ 4。戸の手前 (肩の側) に避雷の柱
    public static final int[] EAST_DOORS = {48, 62, 95};
    public static final List<BlockPos> EAST_RODS = List.of(new BlockPos(43, 143, -21), new BlockPos(57, 143, -13), new BlockPos(90, 143, -21));
    // 右手首の瘤 (雷獣の巣)。直径 26・天井 158。避雷の柱は中心から半径 9、60 度おきに 6 本
    public static final BlockPos RAIJU_NEST = new BlockPos(128, 143, -17);
    public static final int RAIJU_NEST_RADIUS = 13;
    public static final int RAIJU_NEST_CEILING = 158;
    public static final int RAIJU_ROD_RADIUS = 9;
    // 巣の口 (雷獣の戦いの間だけ閉じる。雷獣の担当が閉じる)
    public static final AABB RAIJU_NEST_MOUTH = new AABB(114, 143, -19, 116, 148, -14);
    // 東の石は洞の頂の台の上。雷獣を倒すと、洞の真ん中の昇り風が立って台へ届く
    public static final BlockPos EAST_STONE = new BlockPos(132, 156, -17);
    public static final Area EAST_PERCH = new Area(130, 134, -19, -15, 155, 2);
    public static final BlockPos RAIJU_VENT = new BlockPos(127, 143, -17);

    // ---------------------------------------------------------------- 前の試練 眼の間
    public static final Area NECK = new Area(-2, 2, -43, -19, UPPER_GALLERY_Y, 4);
    public static final Area F0 = new Area(-4, 4, -56, -44, UPPER_GALLERY_Y, 5);
    public static final Area EYE_HALL = new Area(-8, 8, -86, -70, 146, 10);
    public static final BlockPos EYE_HALL_CENTER = new BlockPos(0, 147, -78);
    // 鏡の台の格子 (5x5、3 ブロックおき)。光は床から 2 の高さ
    public static final int GRID_X0 = -6;
    public static final int GRID_Z0 = -84;
    public static final int GRID_STEP = 3;
    public static final int BEAM_Y = 148;
    public static final BlockPos LEFT_EYE = new BlockPos(-11, 151, -80);
    // 戻しの鏡 (前室の台の取っ手)
    public static final BlockPos MIRROR_RESET = new BlockPos(-3, 151, -54);
    // もう一つの戻しの鏡 (眼の間の入口の東の隅。前室まで戻らずに引ける。25 の 3.2)
    public static final BlockPos MIRROR_RESET_HALL = new BlockPos(7, 148, -70);
    // 前の石の小部屋 (嘴の付け根)。三つの受け羽が光ると戸が開く
    public static final Area BEAK_ROOM = new Area(-3, 3, -95, -89, 146, 4);
    public static final int BEAK_DOOR_Z1 = -88;
    public static final int BEAK_DOOR_Z2 = -87;
    public static final BlockPos FRONT_STONE = new BlockPos(0, 147, -93);

    // ---------------------------------------------------------------- 竜骨の間 (最終ボス)
    // 竜骨の門 → 広間の床の下の低い道 (床 134、x -16〜-2) → 西の螺旋 (中心 (-19, -4)) → 床 121 の道 → 前室
    public static final int KEEL_TUNNEL_FLOOR = 134;
    public static final BlockPos KEEL_SPIRAL = new BlockPos(-19, 121, -4);
    public static final Area KEEL_ANTE = new Area(-3, 3, -36, -31, 121, 4);
    public static final BlockPos HEART_ROOM = new BlockPos(0, 120, -14);
    public static final Vec3 HEART_POS = new Vec3(0.5, 121, -13.5);
    public static final int HEART_ROOM_RADIUS = 15;
    // 竜骨の間の空気のいちばん上 (天井のブロックは 134。その上は竜骨の門からの低い道の床なので、134 は埋めたままにする)
    public static final int HEART_ROOM_CEILING = 133;
    // 竜骨の間の避雷の柱 (半径 9、北東・北西・南東・南西) と風穴 (北・南・東・西)
    public static final int HEART_ROD_RADIUS = 9;
    public static final List<BlockPos> HEART_RODS = List.of(
            HEART_ROOM.offset(6, 1, -6), HEART_ROOM.offset(-6, 1, -6), HEART_ROOM.offset(6, 1, 6), HEART_ROOM.offset(-6, 1, 6));
    public static final List<BlockPos> HEART_VENTS = List.of(
            HEART_ROOM.offset(0, 0, -9), HEART_ROOM.offset(0, 0, 9), HEART_ROOM.offset(9, 0, 0), HEART_ROOM.offset(-9, 0, 0));
    // 竜骨の間の入口 (入ると閉じる)。前室の北の口
    public static final AABB HEART_DOOR = new AABB(-1, 122, -30, 2, 125, -29);

    // ---------------------------------------------------------------- 抜け道 (石が鳴ると光る床。踏むと広間の近くへ)
    public static final BlockPos RETURN_WEST = new BlockPos(-124, 143, -10);
    public static final BlockPos RETURN_EAST = new BlockPos(124, 143, -10);
    public static final BlockPos RETURN_FRONT = new BlockPos(2, 146, -91);
    public static final Map<String, BlockPos> RETURN_TO = Map.of(
            "west", new BlockPos(-26, 143, -6), "east", new BlockPos(26, 143, -6), "front", new BlockPos(0, 151, -50));
    // 広間の床の三つの灯り (鳴った石)
    public static final BlockPos LAMP_WEST = new BlockPos(-4, 137, 4);
    public static final BlockPos LAMP_EAST = new BlockPos(4, 137, 4);
    public static final BlockPos LAMP_FRONT = new BlockPos(0, 137, 7);

    // ---------------------------------------------------------------- 休み場 (焚き火)
    public static final List<Rest> RESTS = List.of(
            new Rest("hub", new BlockPos(-9, 138, 7), new BlockPos(-7, 138, 7), HUB),
            new Rest("w0", new BlockPos(-28, 143, -1), new BlockPos(-26, 143, -2), W0),
            new Rest("w1", new BlockPos(-71, 143, -12), new BlockPos(-71, 143, -14), new Area(-77, -65, -23, -11, 142, 8)),
            new Rest("e0", new BlockPos(28, 143, -1), new BlockPos(26, 143, -2), E0),
            new Rest("e1", new BlockPos(71, 143, -12), new BlockPos(71, 143, -14), new Area(65, 77, -23, -11, 142, 8)),
            new Rest("f0", new BlockPos(3, 151, -45), new BlockPos(1, 151, -46), F0),
            new Rest("heart", new BlockPos(2, 122, -35), new BlockPos(0, 122, -34), KEEL_ANTE));
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

    // ---------------------------------------------------------------- 看板 (碑文 6 枚と入口の一言 3 枚。これだけ)
    public static final List<Inscription> LORE_SIGNS = List.of(
            new Inscription("sign.skyabyss.lore_storm_tail", new BlockPos(0, 148, 20), Direction.SOUTH),
            new Inscription("sign.skyabyss.lore_storm_hub", new BlockPos(-6, 139, 10), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_west", new BlockPos(-128, 145, -5), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_east", new BlockPos(128, 145, -5), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_eye", new BlockPos(4, 148, -69), Direction.NORTH),
            new Inscription("sign.skyabyss.lore_storm_heart", new BlockPos(-3, 123, -37), Direction.SOUTH));
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("sign.skyabyss.storm_west", new BlockPos(-15, 140, -8), Direction.EAST),
            new Inscription("sign.skyabyss.storm_east", new BlockPos(15, 140, -8), Direction.WEST),
            new Inscription("sign.skyabyss.storm_front", new BlockPos(-2, 152, -57), Direction.SOUTH));

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
        return p.x < -22.5 && p.x > -142 && p.z > -32 && p.z < 2 && p.y > 138 && p.y < 162;
    }

    public static boolean inEast(Vec3 p) {
        return p.x > 22.5 && p.x < 142 && p.z > -32 && p.z < 2 && p.y > 138 && p.y < 162;
    }

    public static boolean inFront(Vec3 p) {
        return p.z < -43 && p.z > -100 && Math.abs(p.x) < 12 && p.y > 140 && p.y < 160;
    }

    // 竜骨の門より奥 (広間の床の下の道・螺旋・前室・竜骨の間)
    public static boolean pastKeel(Vec3 p) {
        return p.y < 137 && p.y > 110 && p.x > -26 && p.x < 20 && p.z > -40 && p.z < 4;
    }

    public static boolean inTrial(Vec3 p) {
        return inWest(p) || inEast(p) || inFront(p) || pastKeel(p);
    }

    // 体の中 (尾の間より奥。ピースフルのあいだは入れない)。腰の回廊の封印より北で、体の中の高さ
    public static boolean pastSeal(Vec3 p) {
        return p.z < SEAL_Z && p.y < 158 && p.y > 110 && Math.abs(p.x) < 145 && p.z > -100;
    }

    // 外へ開いていてよい所: 尾羽の上の到着の台と下り風の輪・羽根の門・天窓
    public static boolean openToSky(int x, int y, int z) {
        if (Math.abs(x - ARRIVAL.getX()) <= 4 && z >= ARRIVAL.getZ() - 4 && z <= DOWNDRAFT.getZ() + 4) {
            return true;
        }
        if (x >= FEATHER_GATE.minX - 2 && x <= FEATHER_GATE.maxX + 2 && z >= FEATHER_GATE.minZ - 4 && z <= FEATHER_GATE.maxZ + 4) {
            return true;
        }
        return y > HUB.floor() + HUB.height() && x * x + (z + 4) * (z + 4) <= (SKYLIGHT_R + 1) * (SKYLIGHT_R + 1);
    }

    // 眼の間の格子の台の座標 (列 c・行 r)
    public static BlockPos gridCell(int c, int r) {
        return new BlockPos(GRID_X0 + GRID_STEP * c, BEAM_Y, GRID_Z0 + GRID_STEP * r);
    }
}
