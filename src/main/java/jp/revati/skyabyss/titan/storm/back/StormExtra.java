package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Area;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Inscription;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の「任意の翼」(26 の 3.3・9.5)。背の上の道と二つの塔・翼の外の廊・尾羽の庭。必須ではない (どの拍でも・どの進みでも、いつも開いている)。
// 一つの建物に一つの決まりを教える (ウィンクラフトのレイドの作り):
//   尾羽の庭 西 = 昇り風は凪で 4・嵐で 13 (見本)   尾羽の庭 東 = 雷は柱へ落ちる (見本の輪。傷はない)
//   風見の塔   = 凪は階段、嵐は外の昇り風で一段 12 ずつ昇る     避雷の塔 = 嵐のあいだ、足もとを狙う輪。柱の 3 ブロック以内なら柱が受ける
//   風の外廊 (西) = 嵐の横風は風上の柱の陰で防ぐ                   雷の外廊 (東) = 嵐の放電の線は、低いのは跳び、高いのはしゃがむ
// 道: 尾の間の北西の螺旋 → 背の広場 (休み場 back) → 二つの塔の一階 (関所) → 翼の外の廊 → 翼の先の見張り台。
// 座標はここだけ。建て方は StormExtraBuilder、拍で動く所は StormExtraRules
final class StormExtra {
    private StormExtra() {
    }

    // ---------------------------------------------------------------- 背の広場
    // 背の上の甲板 (床のブロックの y)。背の稜線 (y 161) のすぐ上
    static final int DECK_Y = 162;
    static final int TIER_H = 12;
    static final int TIERS = 5;
    // 塔の頂の甲板の床 (DECK_Y + 60)
    static final int TOP_Y = DECK_Y + TIER_H * TIERS;
    // 屋根のない広場。西の塔 (x -45〜-25)・東の塔 (x 25〜45) の間
    static final Area PLAZA = new Area(-24, 24, 12, 35, DECK_Y, 12);
    static final BlockPos PLAZA_FIRE = new BlockPos(0, DECK_Y + 1, 24);
    static final BlockPos PLAZA_SPAWN = new BlockPos(2, DECK_Y + 1, 24);
    // 尾の間の北西の螺旋の塔 (5x5。中心)。尾の間の床 145 から甲板まで 17 段
    static final int TURRET_X = -11;
    static final int TURRET_Z = 39;
    static final int TURRET_STEPS = 17;
    // 螺旋の先頭は北の辺の真ん中。出口は北の壁 (z TURRET_Z-3) の戸 → 広場の南の縁
    static final int TURRET_DOOR_Z = TURRET_Z - 3;

    // ---------------------------------------------------------------- 塔 (二つ)
    // 戸の面の壁の x (faceX) と、広場へ向かう向き (dir)。内側は u = 0〜18 (戸の面から奥へ)、z 13〜33
    record Tower(String id, int faceX, int dir) {
        // 内側の x (u 0〜18)
        int x(int u) {
            return faceX - dir * (1 + u);
        }

        // 外の台 (バルコニー) の x (d 1〜7。広場の側へ張り出す)
        int bx(int d) {
            return faceX + dir * d;
        }

        // 奥の壁の x (外の廊への戸)
        int farWallX() {
            return x(19);
        }

        int xMin() {
            return Math.min(faceX, farWallX());
        }

        int xMax() {
            return Math.max(faceX, farWallX());
        }

        // 塔の中の箱 (一階から頂の甲板の床まで)
        AABB box() {
            return new AABB(xMin(), DECK_Y, Z1 - 1, xMax() + 1, TOP_Y + 8, Z2 + 2);
        }

        // 外の台と昇り風の柱の空間
        AABB balconyBox() {
            int a = Math.min(bx(1), bx(7));
            int b = Math.max(bx(1), bx(7));
            return new AABB(a, DECK_Y + 1, BZ1 - 1, b + 1, TOP_Y + 10, BZ2 + 2);
        }
    }

    static final Tower VANE = new Tower("vane", -25, 1);
    static final Tower ROD = new Tower("rod", 25, -1);
    static final List<Tower> TOWERS = List.of(VANE, ROD);
    // 内側の z の範囲 (塔の外の壁は z 12 と 34)
    static final int Z1 = 13;
    static final int Z2 = 33;
    // 戸 (z 21〜25 の 5 マス、高さ 4)。面の戸・外の廊への戸・外の台への戸が同じ
    static final int DOOR_Z1 = 21;
    static final int DOOR_Z2 = 25;
    static final int DOOR_H = 4;
    // 外の台 (z 19〜27。7 x 9)。昇り風の柱は北端 (z 19〜21。中心 20) か南端 (z 25〜27。中心 26)
    static final int BZ1 = 19;
    static final int BZ2 = 27;
    static final int PAD_N = 20;
    static final int PAD_S = 26;
    // 階段 (3 幅): 偶数の階は奥の帯 A (u 15〜17、z 14 → 25 へ上る)、奇数の階は手前の帯 B (u 11〜13、z 32 → 21)
    static final int STRIP_A_U = 15;
    static final int STRIP_B_U = 11;
    static final int STEPS = TIER_H - 1;
    // 軸の柱 (風見の軸・避雷の芯)。塔の内側の u 7、z 23
    static final int AXLE_U = 7;
    static final int AXLE_Z = 23;

    static int floorY(int tier) {
        return DECK_Y + TIER_H * tier;
    }

    // 階 tier の外の台の昇り風の柱の中心 z (偶数階 = 北、奇数階 = 南)
    static int padZ(int tier) {
        return (tier & 1) == 0 ? PAD_N : PAD_S;
    }

    // 風見の塔の昇り風の穴 (階 0 は広場の地面、1〜4 は外の台。頂の甲板の台へ昇る)。凪 4・嵐 13
    static List<BlockPos> vanePads() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < TIERS; i++) {
            out.add(new BlockPos(VANE.bx(4), floorY(i), padZ(i)));
        }
        return out;
    }

    // ---------------------------------------------------------------- 翼の外の廊 (西 = -1、東 = +1)
    static final int OUT_Z1 = 14;
    static final int OUT_Z2 = 32;
    // 塔の外の壁の外 (x 46) から先の見張り台の手前 (x 185) まで
    static final int OUT_X1 = 46;
    static final int OUT_X2 = 185;
    // 先の見張り台 (x 186〜204)
    static final int TIP_X1 = 186;
    static final int TIP_X2 = 204;

    // side -1 の廊の x の範囲 (外側へ)。a <= b
    static int[] outerX(int side) {
        return side < 0 ? new int[] {-OUT_X2, -OUT_X1} : new int[] {OUT_X1, OUT_X2};
    }

    static int[] tipX(int side) {
        return side < 0 ? new int[] {-TIP_X2, -TIP_X1} : new int[] {TIP_X1, TIP_X2};
    }

    static Area outerArea(int side) {
        int[] x = outerX(side);
        return new Area(x[0], x[1], OUT_Z1, OUT_Z2, DECK_Y, 8);
    }

    static Area tipArea(int side) {
        int[] x = tipX(side);
        return new Area(x[0], x[1], OUT_Z1, OUT_Z2, DECK_Y, 8);
    }

    // 風の外廊 (西) の風よけの柱 (風上 = 西の側に立つ。x の位置と z の中心。互い違い)
    static List<int[]> windPiers() {
        List<int[]> out = new ArrayList<>();
        for (int k = 1; k <= 9; k++) {
            out.add(new int[] {-(OUT_X1 + 14 * k), (k & 1) == 1 ? 19 : 27});
        }
        return out;
    }

    // 雷の外廊 (東) の碍子の柱 (根元。飾り。放電の線の道しるべ)。互い違い
    static List<BlockPos> outerPylons() {
        List<BlockPos> out = new ArrayList<>();
        for (int k = 1; k <= 6; k++) {
            out.add(new BlockPos(OUT_X1 + 22 * k - 8, DECK_Y + 1, (k & 1) == 1 ? 18 : 28));
        }
        return out;
    }

    // ---------------------------------------------------------------- 尾羽の庭 (道の両脇。西 = -1、東 = +1)
    static final int GARDEN_Y = 147;
    static final int GARDEN_Z1 = 122;
    static final int GARDEN_Z2 = 164;
    static final int GARDEN_X1 = 5;
    static final int GARDEN_X2 = 34;

    static Area garden(int side) {
        return side < 0 ? new Area(-GARDEN_X2, -GARDEN_X1, GARDEN_Z1, GARDEN_Z2, GARDEN_Y, 8) : new Area(GARDEN_X1, GARDEN_X2, GARDEN_Z1, GARDEN_Z2, GARDEN_Y, 8);
    }

    // 西の庭の昇り風 (見本)。凪 4 = 鐘の台、嵐 13 = 見張りの台。床のブロック y = GARDEN_Y
    static final BlockPos GARDEN_VENT = new BlockPos(-16, GARDEN_Y, 143);
    // 見張りの台 (床 GARDEN_Y + 13。x -25〜-17、z 137〜145)。凪は階段 (南西の坂) で
    static final Area LOOKOUT = new Area(-26, -18, 137, 145, GARDEN_Y + 13, 6);
    // 鐘の台 (凪の昇り風の先。床 GARDEN_Y + 4 の柱の頂)
    static final BlockPos BELL = new BlockPos(-12, GARDEN_Y + 5, 143);
    // 見張りの台への階段 (幅 3。x -34〜-22 を東へ 13 段、z 134〜136)
    static final int LOOKOUT_STAIR_X1 = -34;
    static final int LOOKOUT_STAIR_Z1 = 134;
    static final BlockPos LOOKOUT_CACHE = new BlockPos(-24, GARDEN_Y + 14, 141);

    // 東の庭の見本の柱 (根元)。三角に 3 本。見本の輪はこの庭にいる人の足もとへ (傷なし)
    static List<BlockPos> gardenRods() {
        return List.of(new BlockPos(14, GARDEN_Y + 1, 135), new BlockPos(26, GARDEN_Y + 1, 135), new BlockPos(20, GARDEN_Y + 1, 150));
    }

    // ---------------------------------------------------------------- 宝箱 (StormCaches。side 3 以上がこの章)
    // 風見の塔の頂・避雷の塔の頂・西の見張りの台・東の外廊の先・西の外廊の先・尾羽の庭の見張り
    static final BlockPos CACHE_VANE_TOP = new BlockPos(-31, TOP_Y + 1, 23);
    static final BlockPos CACHE_ROD_TOP = new BlockPos(31, TOP_Y + 1, 23);
    static final BlockPos CACHE_TIP_W = new BlockPos(-199, DECK_Y + 1, 23);
    static final BlockPos CACHE_TIP_E = new BlockPos(199, DECK_Y + 1, 23);

    static Map<Integer, BlockPos> caches() {
        return Map.of(3, CACHE_VANE_TOP, 4, CACHE_ROD_TOP, 5, CACHE_TIP_W, 6, CACHE_TIP_E, 7, LOOKOUT_CACHE);
    }

    // ---------------------------------------------------------------- 休み場
    static final StormBackLayout.Rest REST_BACK = new StormBackLayout.Rest("back", PLAZA_FIRE, PLAZA_SPAWN, PLAZA);
    static final StormBackLayout.Rest REST_TIP_W = new StormBackLayout.Rest("tipw", new BlockPos(-196, DECK_Y + 1, 29), new BlockPos(-196, DECK_Y + 1, 27), tipArea(-1));
    static final StormBackLayout.Rest REST_TIP_E = new StormBackLayout.Rest("tipe", new BlockPos(196, DECK_Y + 1, 29), new BlockPos(196, DECK_Y + 1, 27), tipArea(1));
    static final List<StormBackLayout.Rest> RESTS = List.of(REST_BACK, REST_TIP_W, REST_TIP_E);

    // ---------------------------------------------------------------- 看板 (碑)
    static final List<Inscription> SIGNS = List.of(
            new Inscription("sign.skyabyss.storm_x_plaza", new BlockPos(-6, DECK_Y + 2, 33), Direction.NORTH),
            new Inscription("sign.skyabyss.storm_x_turret", new BlockPos(TURRET_X + 4, 148, TURRET_Z), Direction.EAST),
            new Inscription("sign.skyabyss.storm_x_vane", new BlockPos(VANE.bx(1) + 1, DECK_Y + 2, 16), Direction.EAST),
            new Inscription("sign.skyabyss.storm_x_rod", new BlockPos(ROD.bx(1) - 1, DECK_Y + 2, 16), Direction.WEST),
            new Inscription("sign.skyabyss.storm_x_garden_w", new BlockPos(-8, GARDEN_Y + 2, 126), Direction.EAST),
            new Inscription("sign.skyabyss.storm_x_garden_e", new BlockPos(8, GARDEN_Y + 2, 126), Direction.WEST),
            new Inscription("sign.skyabyss.storm_x_out_w", new BlockPos(-48, DECK_Y + 2, 30), Direction.NORTH),
            new Inscription("sign.skyabyss.storm_x_out_e", new BlockPos(48, DECK_Y + 2, 30), Direction.NORTH),
            new Inscription("sign.skyabyss.storm_x_tip_w", new BlockPos(-194, DECK_Y + 2, 30), Direction.NORTH),
            new Inscription("sign.skyabyss.storm_x_tip_e", new BlockPos(194, DECK_Y + 2, 30), Direction.NORTH),
            new Inscription("sign.skyabyss.storm_x_vane_top", new BlockPos(VANE.x(3), TOP_Y + 2, 30), Direction.NORTH),
            new Inscription("sign.skyabyss.storm_x_rod_top", new BlockPos(ROD.x(3), TOP_Y + 2, 30), Direction.NORTH));

    // ---------------------------------------------------------------- 部屋 (名前と箱。入ったときの一行・面積の数え)
    // 歩ける床の箱 (floor は床のブロック y、height は空き)。窓・柱・穴は引かない (26 の 1.1 と同じ数え方)
    record Zone(String id, Area area) {
    }

    static List<Zone> zones() {
        List<Zone> out = new ArrayList<>();
        out.add(new Zone("x_plaza", PLAZA));
        for (Tower t : TOWERS) {
            for (int i = 0; i < TIERS; i++) {
                int x1 = Math.min(t.x(0), t.x(18));
                int x2 = Math.max(t.x(0), t.x(18));
                out.add(new Zone("x_" + t.id() + "_t" + i, new Area(x1, x2, Z1, Z2, floorY(i), TIER_H - 1)));
            }
            out.add(new Zone("x_" + t.id() + "_top", new Area(t.xMin(), t.xMax(), 12, 34, TOP_Y, 8)));
            // 外の台 (一階は広場。1〜4 階と頂の台)
            for (int i = 1; i <= TIERS; i++) {
                int a = Math.min(t.bx(1), t.bx(7));
                int b = Math.max(t.bx(1), t.bx(7));
                out.add(new Zone("x_" + t.id() + "_b" + i, new Area(a, b, BZ1, BZ2, floorY(i), 6)));
            }
        }
        out.add(new Zone("x_out_w", outerArea(-1)));
        out.add(new Zone("x_out_e", outerArea(1)));
        out.add(new Zone("x_tip_w", tipArea(-1)));
        out.add(new Zone("x_tip_e", tipArea(1)));
        out.add(new Zone("x_garden_w", garden(-1)));
        out.add(new Zone("x_garden_e", garden(1)));
        out.add(new Zone("x_lookout", LOOKOUT));
        return out;
    }

    // 入ったときの一行を出す部屋 (外の台は除く)
    static List<Zone> titledZones() {
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones()) {
            if (!z.id().matches("x_(vane|rod)_b[0-9]")) {
                out.add(z);
            }
        }
        return out;
    }

    // 着くべき所 (監査と自己テスト)。名前 → 立つ位置 (どれも凪・戸が閉じたままで着く)
    static Map<String, BlockPos> goals() {
        Map<String, BlockPos> m = new java.util.LinkedHashMap<>();
        m.put("x plaza", new BlockPos(0, DECK_Y + 1, 20));
        for (Tower t : TOWERS) {
            m.put("x " + t.id() + " lobby", new BlockPos(t.x(3), floorY(0) + 1, 23));
            m.put("x " + t.id() + " tier 2", new BlockPos(t.x(3), floorY(2) + 1, 23));
            m.put("x " + t.id() + " tier 4", new BlockPos(t.x(3), floorY(4) + 1, 23));
            m.put("x " + t.id() + " top", new BlockPos(t.x(3), TOP_Y + 1, 23));
            m.put("x " + t.id() + " balcony 3", new BlockPos(t.bx(2), floorY(3) + 1, 23));
        }
        m.put("x out w", new BlockPos(-100, DECK_Y + 1, 23));
        m.put("x out e", new BlockPos(100, DECK_Y + 1, 23));
        m.put("x tip w", CACHE_TIP_W.offset(2, 0, 0));
        m.put("x tip e", CACHE_TIP_E.offset(-2, 0, 0));
        m.put("x garden w", new BlockPos(-20, GARDEN_Y + 1, 132));
        m.put("x garden e", new BlockPos(20, GARDEN_Y + 1, 142));
        m.put("x bell", new BlockPos(-14, GARDEN_Y + 5, 143));
        m.put("x lookout", LOOKOUT_CACHE.offset(2, 0, 0));
        return m;
    }

    // どれかの部屋 (外の台を含む) の中か
    static boolean inAnyZone(Vec3 p) {
        for (Zone z : ZONES) {
            if (z.area().contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static final List<Zone> ZONES = zones();

    // 歩ける面積の合計 (m²)
    static int areaTotal() {
        int sum = 0;
        for (Zone z : zones()) {
            Area a = z.area();
            sum += (a.x2() - a.x1() + 1) * (a.z2() - a.z1() + 1);
        }
        return sum;
    }

    // ---------------------------------------------------------------- 外へ開いていてよい所 (seal・小さな穴の検査から除く)
    static boolean open(int x, int y, int z) {
        if (y < DECK_Y - 20) {
            return false;
        }
        // 背の広場・外の台・頂の甲板・翼の外の廊・見張り台
        if (x >= -25 && x <= 25 && z >= 11 && z <= 37 && y >= DECK_Y) {
            return true;
        }
        for (Tower t : TOWERS) {
            AABB b = t.balconyBox();
            if (x >= b.minX && x < b.maxX && y >= b.minY && y < b.maxY && z >= b.minZ && z < b.maxZ) {
                return true;
            }
            // 頂の甲板
            if (y >= TOP_Y && x >= t.xMin() - 1 && x <= t.xMax() + 1 && z >= 11 && z <= 35) {
                return true;
            }
            // 外の廊への戸の外側
            if (Math.abs(x - t.farWallX() + t.dir()) <= 1 && z >= DOOR_Z1 - 1 && z <= DOOR_Z2 + 1 && y >= DECK_Y) {
                return true;
            }
        }
        if (Math.abs(x) >= OUT_X1 - 2 && Math.abs(x) <= TIP_X2 + 2 && z >= OUT_Z1 - 2 && z <= OUT_Z2 + 2 && y >= DECK_Y) {
            return true;
        }
        // 螺旋の塔の出口 (広場の南の縁)
        if (Math.abs(x - TURRET_X) <= 3 && z >= TURRET_DOOR_Z - 1 && z <= TURRET_DOOR_Z + 1 && y >= DECK_Y) {
            return true;
        }
        // 尾羽の庭と、庭への口 (道の手すりの切れ目)
        if (Math.abs(x) >= GARDEN_X1 - 3 && Math.abs(x) <= GARDEN_X2 + 2 && z >= GARDEN_Z1 - 1 && z <= GARDEN_Z2 + 1 && y >= GARDEN_Y) {
            return true;
        }
        return false;
    }

    // 風の外廊の中 (西)・雷の外廊の中 (東)・避雷の塔の中 (柱と輪の決まりが働く所)
    static boolean inOuter(Vec3 p, int side) {
        int[] x = outerX(side);
        return p.x >= x[0] - 0.5 && p.x < x[1] + 1.5 && p.z >= OUT_Z1 - 0.5 && p.z < OUT_Z2 + 1.5 && p.y >= DECK_Y && p.y < DECK_Y + 8;
    }

    static boolean inRodTower(Vec3 p) {
        AABB b = ROD.box();
        AABB c = ROD.balconyBox();
        return b.contains(p) || c.contains(p);
    }

    static boolean inGardenEast(Vec3 p) {
        Area a = garden(1);
        return p.x >= a.x1() && p.x < a.x2() + 1 && p.z >= a.z1() && p.z < a.z2() + 1 && p.y >= GARDEN_Y && p.y < GARDEN_Y + 8;
    }

    // 昇り風の穴 (StormWest.vents に足す)。風見の塔の外の台の柱と、西の庭の見本
    static List<StormWest.Vent> vents() {
        List<StormWest.Vent> out = new ArrayList<>();
        for (BlockPos p : vanePads()) {
            out.add(new StormWest.Vent(p, -1));
        }
        out.add(new StormWest.Vent(GARDEN_VENT, -1));
        return out;
    }

    // 避雷の柱 (根元)。避雷の塔 (各階 2 本と頂の 1 本)・雷の外廊・東の庭
    static List<BlockPos> towerRods() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < TIERS; i++) {
            out.add(new BlockPos(ROD.x(5), floorY(i) + 1, 17));
            out.add(new BlockPos(ROD.x(5), floorY(i) + 1, 29));
        }
        out.add(new BlockPos(ROD.x(9), TOP_Y + 1, 23));
        return out;
    }

    static List<BlockPos> allRods() {
        List<BlockPos> out = new ArrayList<>(towerRods());
        out.addAll(gardenRods());
        return out;
    }
}
