package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 息の間 (亀の肺) の配置と、運びの道の計算 (docs/design/24 の案 C、08 の 3.8節)。ゲームの状態には触れない、形と式だけ (自己テストが同じ式を見る)。
//
// 形: 月の窓の縦穴と、予約の間 1〜3 を一つの大空洞にした。中心は (0, -3) のブロック (世界の座標では (0.5, -2.5))。
//   下の広い気室 (A): 半径 9.5、y 98〜137。底は深い水たまり (半径 4.8) と、それを巻く縁 (予約の間 1 の床と同じ高さ 101)。
//     水を湛えた「殻の皿」が螺旋に 8 つ浮かぶ。皿のまんなかの光る泡に立つと、次の皿へ運ばれる (運びは BreathLift)。
//   上の細い気室 (B): 頂の塔の中。半径 6 から 3 へ細る円錐 (外の壁が 1.5 ブロック以上残る所まで。塔は上へ細るため)、y 139〜172。
//     いちばん下 (y 138 の床) が真珠守りの舞台。そこから塔の頂の展望の間 (y 173 の床) へ、まっすぐ昇る風の柱が通る。
//   展望の間 (O): 塔の頂の柱廊の中。窓は四方。下りの近道の台 (縦穴の滑降) がある。
// 床の高さ・皿の並びは「床のブロックの y」で書く (人が立つのは +1。皿の水の中は盆の底の上 = floorY)。
public final class BreathLayout {
    private BreathLayout() {
    }

    // ---------------------------------------------------------------- 中心・半径・高さ
    public static final int CX = 0;
    public static final int CZ = TOWER_CZ;
    // 世界の座標での中心 (ブロック (CX, CZ) の真ん中)
    public static final double WX = CX + 0.5;
    public static final double WZ = CZ + 0.5;
    // 下の気室 A の半径 (ブロックの座標で hypot(x - CX, z - CZ) <= R_A が空気)
    public static final double R_A = 9.5;
    // 水たまりの底 (石)・水の段・縁の高さ。縁 = 予約の間 1 の床 (人が立つのは 102)
    public static final int POOL_FLOOR = 97;
    public static final int LIP = UPPER_1.floor();
    public static final double POOL_R = 4.8;
    // 気室 A の天井の下 (空気の最上段) と、舞台の床
    public static final int A_TOP = 137;
    public static final int ARENA_FLOOR = 138;
    public static final double ARENA_R = 6.0;
    // 気室 B (塔の中) の空気の最上段。その上の y 173 が展望の間の床
    public static final int B_TOP = 172;
    public static final int DECK_FLOOR = TOWER_DECK;
    // 展望の間: 柱廊の中の床 (x -9〜8、z -12〜6)、空気は 174〜176、屋根は 177
    public static final int OBS_X1 = -9;
    public static final int OBS_X2 = 8;
    public static final int OBS_Z1 = -12;
    public static final int OBS_Z2 = 6;
    public static final int OBS_ROOF = 177;

    // 気室 B の半径 (y ごと。外の壁が 1.5 ブロック以上残る値。tools/tide_turtle_radius.py が塔の形から測った下限)
    public static double radiusB(int y) {
        if (y <= 146) {
            return 6.0;
        }
        if (y <= 149) {
            return 5.5;
        }
        if (y <= 164) {
            return 5.0;
        }
        if (y <= 169) {
            return 4.0;
        }
        return 3.0;
    }

    // ブロック (x, z) が気室の中心から何ブロックか
    public static double dist(int x, int z) {
        return Math.hypot(x - CX, z - CZ);
    }

    // ---------------------------------------------------------------- 皿
    public static final int DISHES = 8;
    // 皿の盆の底の高さ (盆の上に水が 1 段。縁の上面は +1)。2 番目の 114 は予約の間 2、5 番目の 126 は予約の間 3 の床と同じ
    public static final int[] DISH_FLOOR = {106, 110, 114, 118, 122, 126, 129, 132};
    // 皿の中心の気室の中心からの距離。最後の皿は舞台の下の風の柱になるので中へ寄る
    public static final double DISH_R = 6.0;
    public static final double VENT_R = 3.8;
    // 皿の床 (縁の輪を含む) の半径、盆の半径
    public static final double RING_R = 3.0;
    public static final double BASIN_R = 2.4;
    // 皿が螺旋に並ぶ角度 (度。+z が 90 度、反時計回りに 1 つごと -72)
    public static final double ANGLE0 = 90;
    public static final double ANGLE_STEP = -72;
    // 光る泡 (近い皿への運び) に立つ半径と、遠い皿への運びの泡の位置・半径
    public static final double PAD_R = 0.7;
    public static final double FAR_OFFSET = 1.3;
    public static final double FAR_PAD_R = 0.5;
    // 着地の位置 (皿の中心から前の皿の向きへ)
    public static final double LAND_OFFSET = 1.4;
    // 足もとの高さの上限 (気室 A の天井 138 の下で頭が当たらない)
    public static final double CEILING_FEET = A_TOP - 2.0;

    public record Dish(int index, int cx, int cz, int floorY, double angle) {
        // 世界の座標の中心
        public double wx() {
            return cx + 0.5;
        }

        public double wz() {
            return cz + 0.5;
        }

        // 盆の中に立つ足もとの高さ
        public double standY() {
            return floorY;
        }

        public boolean ringContains(int x, int z) {
            return Math.hypot(x - cx, z - cz) <= RING_R;
        }

        public boolean basinContains(int x, int z) {
            return Math.hypot(x - cx, z - cz) <= BASIN_R;
        }

        public BlockPos pad() {
            return new BlockPos(cx, floorY - 1, cz);
        }
    }

    private static final List<Dish> DISH_LIST = buildDishes();

    private static List<Dish> buildDishes() {
        List<Dish> list = new ArrayList<>();
        for (int k = 0; k < DISHES; k++) {
            double a = Math.toRadians(ANGLE0 + ANGLE_STEP * k);
            double r = k == DISHES - 1 ? VENT_R : DISH_R;
            int bx = CX + (int) Math.round(r * Math.cos(a));
            int bz = CZ + (int) Math.round(r * Math.sin(a));
            list.add(new Dish(k, bx, bz, DISH_FLOOR[k], Math.toDegrees(a)));
        }
        return List.copyOf(list);
    }

    public static List<Dish> dishes() {
        return DISH_LIST;
    }

    public static Dish dish(int k) {
        return DISH_LIST.get(k);
    }

    public static Dish vent() {
        return DISH_LIST.get(DISHES - 1);
    }

    // ---------------------------------------------------------------- 乗り場 (運びの始まり・終わりの場所)
    // 入口の泡 (縁の上。広間の側の西南西。皿 0 へ運ぶ)
    public static final int ENTRY_X;
    public static final int ENTRY_Z;

    static {
        double a = Math.toRadians(ANGLE0 - ANGLE_STEP);
        ENTRY_X = CX + (int) Math.round(7.2 * Math.cos(a));
        ENTRY_Z = CZ + (int) Math.round(7.2 * Math.sin(a));
    }

    // 舞台と展望の間の乗り場の位置
    public static final double ARENA_LAND_DIST = 4.4;

    public enum Kind {
        ENTRY, DISH, ARENA, DECK
    }

    // 乗り場: 運びが始まる泡の位置 (x, z, 足もとの高さ) と、着地の位置 (x, z, 足もとの高さ)
    public record Node(int id, Kind kind, int dish, double padX, double padZ, double padY, double landX, double landZ, double landY, double ceiling) {
        public String label() {
            return switch (kind) {
                case ENTRY -> "entry";
                case DISH -> "dish " + dish;
                case ARENA -> "arena";
                case DECK -> "deck";
            };
        }
    }

    private static final List<Node> NODES = buildNodes();

    private static List<Node> buildNodes() {
        List<Node> list = new ArrayList<>();
        // 0: 入口 (縁)。着地は泡の少し外 (下りてきたとき泡に重ならない)
        double ex = ENTRY_X + 0.5;
        double ez = ENTRY_Z + 0.5;
        double[] toward = unit(WX - ex, WZ - ez);
        list.add(new Node(0, Kind.ENTRY, -1, ex, ez, LIP + 1, ex + toward[0] * -1.8, ez + toward[1] * -1.8, LIP + 1, 1e9));
        // 1..8: 皿
        for (int k = 0; k < DISHES; k++) {
            Dish d = dish(k);
            Node prev = list.get(k);
            double[] to = unit(prev.padX - d.wx(), prev.padZ - d.wz());
            list.add(new Node(1 + k, Kind.DISH, k, d.wx(), d.wz(), d.standY(), d.wx() + to[0] * LAND_OFFSET, d.wz() + to[1] * LAND_OFFSET, d.standY(),
                    CEILING_FEET));
        }
        // 9: 舞台 (真珠守りの床)。風の柱から昇り、柱の外の床へ降りる。泡は舞台の真ん中 (展望の間への昇り)
        Dish v = vent();
        double[] in = unit(WX - v.wx(), WZ - v.wz());
        list.add(new Node(9, Kind.ARENA, -1, WX, WZ, ARENA_FLOOR + 1, WX - in[1] * 2.6, WZ + in[0] * 2.6, ARENA_FLOOR + 1,
                DECK_FLOOR + 2.0));
        // 10: 展望の間
        list.add(new Node(10, Kind.DECK, -1, WX - 4.5, WZ + 0.0, DECK_FLOOR + 1, WX - 4.5, WZ + 0.0, DECK_FLOOR + 1, OBS_ROOF - 2.3));
        return List.copyOf(list);
    }

    private static double[] unit(double x, double z) {
        double l = Math.hypot(x, z);
        return l < 1e-9 ? new double[] {1, 0} : new double[] {x / l, z / l};
    }

    public static List<Node> nodes() {
        return NODES;
    }

    public static Node entry() {
        return NODES.get(0);
    }

    public static Node dishNode(int k) {
        return NODES.get(1 + k);
    }

    public static Node arena() {
        return NODES.get(9);
    }

    public static Node deck() {
        return NODES.get(10);
    }

    // 舞台の風の柱 (皿 7 の真上) の穴の中心と半径、まわりの壁
    public static final double VENT_HOLE_R = 1.5;
    public static final double VENT_WALL_R = 2.3;

    // 展望の間の下りの台 (縦穴の滑降)・碑・追憶の石
    public static final BlockPos RETURN_PAD = new BlockPos(CX + 5, DECK_FLOOR, CZ);
    public static final BlockPos KEEPSAKE = new BlockPos(CX + 5, DECK_FLOOR + 1, CZ + 4);
    public static final BlockPos DECK_REST = new BlockPos(CX - 6, DECK_FLOOR + 1, CZ - 5);

    // 舞台の再戦の台 (周回のグループが真珠守りに挑み直す)
    public static final BlockPos REMATCH = new BlockPos(CX - 2, ARENA_FLOOR + 1, CZ + 4);

    // ---------------------------------------------------------------- 運び
    // 運び = 乗り場 from の泡から、乗り場 to の着地までの折れ線 (世界の座標)。far = 遠い泡 (一つ飛ばす)
    public record Lift(int from, int to, boolean far) {
        public Node a() {
            return NODES.get(from);
        }

        public Node b() {
            return NODES.get(to);
        }

        // 泡の位置 (近い泡は乗り場の泡、遠い泡は皿の上で行き先の向きへずらした所)
        public double[] pad() {
            Node a = a();
            if (!far) {
                return new double[] {a.padX(), a.padZ()};
            }
            Node b = b();
            double[] u = unit(b.landX() - a.padX(), b.landZ() - a.padZ());
            return new double[] {a.padX() + u[0] * FAR_OFFSET, a.padZ() + u[1] * FAR_OFFSET};
        }

        public double padRadius() {
            return far ? FAR_PAD_R : PAD_R;
        }

        // 乗る足もとの高さ
        public double padY() {
            return a().padY();
        }

        // 運びの折れ線 (始め = 泡の上、終わり = 行き先の着地)
        public List<Vec3> path() {
            double[] p = pad();
            Node a = a();
            Node b = b();
            double top;
            if (b.kind() == Kind.ARENA) {
                // 風の柱の穴を昇って、柱の壁 (床から 2 段) を越える
                top = b.landY() + 3.3;
            } else {
                top = Math.min(b.landY() + 3.3, Math.min(a.ceiling(), b.ceiling()));
            }
            top = Math.max(top, a.padY() + 2.2);
            List<Vec3> pts = new ArrayList<>();
            pts.add(new Vec3(p[0], a.padY(), p[1]));
            pts.add(new Vec3(p[0], top, p[1]));
            pts.add(new Vec3(b.landX(), top, b.landZ()));
            pts.add(new Vec3(b.landX(), b.landY(), b.landZ()));
            return pts;
        }
    }

    // 上りの運びの一覧: 近い泡 (入口→皿 0、皿 k→皿 k+1、皿 7→舞台)、遠い泡 (皿 k→皿 k+2、k = 0〜5)、舞台→展望の間
    public static List<Lift> lifts() {
        List<Lift> list = new ArrayList<>();
        list.add(new Lift(0, 1, false));
        for (int k = 0; k < DISHES - 1; k++) {
            list.add(new Lift(1 + k, 2 + k, false));
        }
        list.add(new Lift(DISHES, 9, false));
        for (int k = 0; k <= DISHES - 3; k++) {
            list.add(new Lift(1 + k, 3 + k, true));
        }
        list.add(new Lift(9, 10, false));
        return list;
    }

    // 下りの運び (しゃがみ続ける): 乗り場 from から、ひとつ前の乗り場の着地へ
    public static List<Vec3> descent(int from, Vec3 start) {
        Node a = NODES.get(from);
        Node b = NODES.get(from - 1);
        List<Vec3> pts = new ArrayList<>();
        double ceiling = Math.min(a.ceiling(), 1e9);
        double mid = Math.min(start.y + 2.5, ceiling);
        pts.add(start);
        pts.add(new Vec3(start.x, mid, start.z));
        if (a.kind() == Kind.ARENA) {
            // 風の柱の穴の真上を通って降りる
            Dish v = vent();
            double up = ARENA_FLOOR + 1 + 3.3;
            pts.set(1, new Vec3(start.x, Math.max(start.y, up), start.z));
            pts.add(new Vec3(v.wx(), up, v.wz()));
            pts.add(new Vec3(v.wx(), CEILING_FEET, v.wz()));
            pts.add(new Vec3(b.landX(), CEILING_FEET, b.landZ()));
            pts.add(new Vec3(b.landX(), b.landY(), b.landZ()));
            return pts;
        }
        if (a.kind() == Kind.DECK) {
            pts.add(new Vec3(WX, mid, WZ));
            pts.add(new Vec3(WX, ARENA_FLOOR + 1 + 3.3, WZ));
            pts.add(new Vec3(b.landX(), ARENA_FLOOR + 1 + 3.3, b.landZ()));
            pts.add(new Vec3(b.landX(), b.landY(), b.landZ()));
            return pts;
        }
        double top = Math.min(Math.max(mid, b.landY() + 3.3), CEILING_FEET);
        pts.set(1, new Vec3(start.x, Math.max(start.y, top), start.z));
        pts.add(new Vec3(b.landX(), top, b.landZ()));
        pts.add(new Vec3(b.landX(), b.landY(), b.landZ()));
        return pts;
    }

    // ---------------------------------------------------------------- 運びの動き (サーバーが毎 tick 速度を決める式。自己テストが同じ式で何度も確かめる)
    public static final double SPEED_UP = 0.62;
    public static final double SPEED_FLAT = 0.42;
    public static final double SPEED_DOWN = 0.34;
    public static final double SPEED_TOWER = 0.78;
    // 折れ線のどこにいるか (道のりの割合)。位置から最も近い辺の上の点を取る
    public static double progress(List<Vec3> path, Vec3 p) {
        double best = Double.MAX_VALUE;
        double bestS = 0;
        double along = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            Vec3 ab = b.subtract(a);
            double len = ab.length();
            double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / (len * len)));
            Vec3 q = a.add(ab.scale(t));
            double d = q.distanceToSqr(p);
            if (d <= best + 1e-9) {
                best = Math.min(best, d);
                bestS = along + t * len;
            }
            along += len;
        }
        return bestS;
    }

    public static double length(List<Vec3> path) {
        double l = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            l += path.get(i).distanceTo(path.get(i + 1));
        }
        return l;
    }

    public static Vec3 pointAt(List<Vec3> path, double s) {
        double along = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            double len = a.distanceTo(b);
            if (s <= along + len || i + 2 == path.size()) {
                double t = len < 1e-9 ? 1 : Math.max(0, Math.min(1, (s - along) / len));
                return a.add(b.subtract(a).scale(t));
            }
            along += len;
        }
        return path.get(path.size() - 1);
    }

    // 折れ線の辺ごとの速さ (上へ = 上り、水平 = 平ら、下へ = 降り)
    public static double edgeSpeed(List<Vec3> path, double s, boolean tower) {
        double along = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            double len = a.distanceTo(b);
            if (s <= along + len || i + 2 == path.size()) {
                double dy = b.y - a.y;
                if (Math.abs(dy) < 0.05) {
                    return SPEED_FLAT;
                }
                return tower ? SPEED_TOWER : (dy > 0 ? SPEED_UP : SPEED_DOWN);
            }
            along += len;
        }
        return SPEED_DOWN;
    }

    // 1 tick の速度: 道のり s に進む目標の点へ、いまの位置から向かう。道から外れたぶんは引き戻す (遅れ・入力のずれに強い)。
    // 返す速度の長さは MAX_STEP まで。着地の近く (終わりの辺の残りが短い) は目標の点が着地に止まる
    public static final double MAX_STEP = 0.85;

    public record Step(Vec3 velocity, double s, boolean done) {
    }

    // 道のりの s にいる辺の向き (単位ベクトル)
    public static Vec3 tangentAt(List<Vec3> path, double s) {
        double along = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            double len = a.distanceTo(b);
            if (s <= along + len || i + 2 == path.size()) {
                return len < 1e-9 ? Vec3.ZERO : b.subtract(a).scale(1.0 / len);
            }
            along += len;
        }
        return Vec3.ZERO;
    }

    // いまの辺の終わりまでの距離が短く、次の辺が大きく曲がるとき、速さを 0.35〜1 に
    static double cornerFactor(List<Vec3> path, double s) {
        double along = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            double len = a.distanceTo(b);
            if (s < along + len || i + 2 == path.size()) {
                if (i + 2 >= path.size()) {
                    return 1;
                }
                Vec3 t1 = b.subtract(a).normalize();
                Vec3 t2 = path.get(i + 2).subtract(b).normalize();
                if (t1.dot(t2) > 0.8) {
                    return 1;
                }
                double rem = along + len - s;
                return Math.max(0.35, Math.min(1.0, 0.35 + rem / 1.6));
            }
            along += len;
        }
        return 1;
    }

    // 位置の最も近い道の点へ向かう引き戻し (遅れ・ふらつきでも振動しないよう弱く: 1 tick に 0.3)
    public static final double PULL = 0.3;

    public static Step step(List<Vec3> path, Vec3 pos, double s, boolean tower) {
        double total = length(path);
        double near = progress(path, pos);
        double cur = Math.max(s, near);
        double speed = edgeSpeed(path, near + 0.2, tower);
        // 曲がり角 (上り → 横、横 → 下り) の手前は速さを落とす (遅れて行きすぎ、天井・縁にぶつからないように)
        speed *= cornerFactor(path, near);
        Vec3 q = pointAt(path, near);
        Vec3 v = tangentAt(path, near + 0.2).scale(speed).add(q.subtract(pos).scale(PULL));
        double l = v.length();
        if (l > MAX_STEP) {
            v = v.scale(MAX_STEP / l);
        }
        // 終わりの近く: 着地へ向かって速さを落とす
        double left = total - near;
        if (left < 1.0) {
            Vec3 end = path.get(path.size() - 1);
            v = end.subtract(pos).scale(Math.min(1.0, 0.5 + left)).scale(0.5);
        }
        boolean done = left < 0.35 && pos.distanceTo(path.get(path.size() - 1)) < 0.9;
        return new Step(v, Math.min(near + 2.5, cur + speed), done);
    }

    // ---------------------------------------------------------------- 範囲
    // 気室の全体 (落ちても着ける所を含む)
    public static AABB chamberBox() {
        return new AABB(CX - R_A - 1, POOL_FLOOR, CZ - R_A - 1, CX + R_A + 2, DECK_FLOOR + 1, CZ + R_A + 2);
    }

    // 気室 A (下の広い所)・舞台と B (塔の中)・展望の間
    public static AABB boxA() {
        return new AABB(CX - R_A - 1, POOL_FLOOR, CZ - R_A - 1, CX + R_A + 2, A_TOP + 1, CZ + R_A + 2);
    }

    public static AABB arenaBox() {
        return new AABB(CX - ARENA_R - 1, ARENA_FLOOR + 1, CZ - ARENA_R - 1, CX + ARENA_R + 2, DECK_FLOOR, CZ + ARENA_R + 2);
    }

    public static AABB deckBox() {
        return new AABB(OBS_X1 - 1, DECK_FLOOR + 1, OBS_Z1 - 1, OBS_X2 + 2, OBS_ROOF, OBS_Z2 + 2);
    }

    public static boolean inChamber(Vec3 p) {
        return chamberBox().contains(p) && Math.hypot(p.x - WX, p.z - WZ) <= R_A + 1.2;
    }

    // 進みの輪の高さ (下・中・上。壁の帯。足もとの高さがこの値に着くと灯る)
    public static final int[] RING_Y = {114, 126, ARENA_FLOOR + 1};
    // 輪の帯の y (壁の帯の 1 段)
    public static final int[] RING_BAND = {112, 124, 136};

    // 足もとの高さから「息の目盛り」(縁の高さを 0 とした高さ)
    public static int heightOf(double feetY) {
        return (int) Math.max(0, Math.floor(feetY) - (LIP + 1));
    }

    public static final int TOTAL_HEIGHT = DECK_FLOOR + 1 - (LIP + 1);

    // 遠い泡が働くか (潮が満ちている間。満ち具合 0.75 以上)。働かなくても近い泡で昇れる (潮は道筋・速さを変えるだけで、道を閉ざさない)
    public static boolean farActive(double fullness) {
        return fullness >= 0.75;
    }
}
