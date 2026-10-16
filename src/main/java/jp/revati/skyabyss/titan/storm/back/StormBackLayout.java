package jp.revati.skyabyss.titan.storm.back;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背 (storm_back) の部屋の座標 (12 の 3.3節)。頭は北 (-Z)、左の翼は西 (-X)。
// 鳥の形 (storm_back/bird.bin) の中に、ここに書いた部屋を彫る。
//
// ほかの担当へ (11.1 の約束): 雷獣の巣・竜骨の間・休み場はここから読む。数字を写さないこと (建て直しで動くことがある)。
public final class StormBackLayout {
    // 版が上がったら建て直す
    public static final int LAYOUT_VERSION = 1;

    // ---- 到着と門 ----
    // 到着 (尾羽の上)。北を向く
    public static final BlockPos ARRIVAL = new BlockPos(0, 143, 94);
    public static final float ARRIVAL_YAW = 180f;
    // 羽根の門 (腰の上)。口の面は z 36〜38、x -6〜6、y 154〜
    public static final AABB FEATHER_GATE = new AABB(-6, 154, 36, 7, 162, 39);
    // 地上へ戻る口 (門の外側の面)
    public static final AABB GATE_MOUTH = new AABB(-4, 154, 38, 5, 161, 40);
    // 嵐の雲の天井。ここより下へ落ちると嵐に投げ返される
    public static final int CLOUD_Y = 100;

    // ---- 尾の間・風の広間 ----
    public static final AABB TAIL_HALL = new AABB(-10, 146, 20, 11, 153, 35);
    public static final BlockPos TAIL_CENTER = new BlockPos(0, 146, 27);
    public static final AABB HUB = new AABB(-14, 137, -18, 15, 157, 11);
    public static final BlockPos HUB_CENTER = new BlockPos(0, 137, -4);
    // 広間の中央の昇り風 (嵐のとき 13 段上がって上の回廊へ)
    public static final BlockPos HUB_UPDRAFT = new BlockPos(0, 137, -12);
    public static final int UPPER_GALLERY_Y = 150;
    // 竜骨の門 (広間の床)
    public static final AABB KEEL_GATE = new AABB(-2, 136, -6, 3, 138, -1);

    // ---- 三つの試練 ----
    // 風切りの骨 (西)・雷の骨 (東): 翼の骨の中の回廊。床 142〜150
    public static final AABB WEST_BONE = new AABB(-118, 140, -30, -23, 158, -5);
    public static final AABB EAST_BONE = new AABB(23, 140, -30, 118, 158, -5);
    // 左手首の瘤 (西の石は岩棚の上)、右手首の瘤 (雷獣の巣)
    public static final BlockPos WEST_KNOB = new BlockPos(-128, 143, -18);
    public static final BlockPos WEST_STONE = new BlockPos(-128, 154, -18);
    public static final BlockPos RAIJU_NEST = new BlockPos(128, 143, -18);
    // 巣は直径 26・天井 158。避雷の柱は中心から半径 9、60 度おきに 6 本
    public static final int RAIJU_NEST_RADIUS = 13;
    public static final int RAIJU_NEST_CEILING = 158;
    public static final int RAIJU_ROD_RADIUS = 9;
    public static final BlockPos EAST_STONE = new BlockPos(128, 156, -18);
    // 巣の口 (雷獣の戦いの間だけ閉じる)
    public static final AABB RAIJU_NEST_MOUTH = new AABB(114, 143, -21, 117, 148, -14);
    // 首の回廊・前室、眼の間
    public static final AABB NECK = new AABB(-4, 150, -62, 5, 156, -43);
    public static final AABB EYE_HALL = new AABB(-8, 146, -86, 9, 157, -69);
    public static final BlockPos EYE_HALL_CENTER = new BlockPos(0, 146, -78);
    public static final BlockPos LEFT_EYE = new BlockPos(-11, 151, -80);
    public static final BlockPos FRONT_STONE = new BlockPos(0, 147, -92);

    // ---- 竜骨の間 (最終ボス) ----
    public static final BlockPos KEEL_ANTE = new BlockPos(0, 121, -33);
    public static final BlockPos HEART_ROOM = new BlockPos(0, 120, -14);
    public static final Vec3 HEART_POS = new Vec3(0.5, 121, -13.5);
    public static final int HEART_ROOM_RADIUS = 15;
    public static final int HEART_ROOM_CEILING = 134;
    // 竜骨の間の避雷の柱 (半径 9、北東・北西・南東・南西) と風穴 (北・南・東・西)
    public static final int HEART_ROD_RADIUS = 9;
    public static final List<BlockPos> HEART_RODS = List.of(
            HEART_ROOM.offset(6, 1, -6), HEART_ROOM.offset(-6, 1, -6), HEART_ROOM.offset(6, 1, 6), HEART_ROOM.offset(-6, 1, 6));
    public static final List<BlockPos> HEART_VENTS = List.of(
            HEART_ROOM.offset(0, 0, -9), HEART_ROOM.offset(0, 0, 9), HEART_ROOM.offset(9, 0, 0), HEART_ROOM.offset(-9, 0, 0));
    // 竜骨の間の入口 (入ると閉じる)
    public static final AABB HEART_DOOR = new AABB(-1, 121, -30, 2, 125, -29);

    // ---- 休み場 (焚き火) ----
    public static final Map<String, BlockPos> REST = Map.of(
            "hub", new BlockPos(-8, 137, 6),
            "w0", new BlockPos(-24, 142, -10),
            "w1", new BlockPos(-70, 142, -12),
            "e0", new BlockPos(24, 142, -10),
            "e1", new BlockPos(70, 142, -12),
            "f0", new BlockPos(2, 150, -52),
            "heart", new BlockPos(2, 121, -35));
    public static final List<String> REST_ORDER = List.of("hub", "w0", "w1", "e0", "e1", "f0", "heart");

    private StormBackLayout() {
    }

    // 雷獣の巣の避雷の柱 (6 本、60 度おき) の根元
    public static List<BlockPos> raijuRods() {
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
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
}
