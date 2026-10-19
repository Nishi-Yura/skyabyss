package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Area;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Inscription;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Rest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の「試練の甲板」(docs/design/29 の 3.3・5.4。P2 の新作 4 本: S02 気嚢の滑空・S03 雷柱の渡り・S09 雷乞いの巣・S10 羽守の見張り台)。
// 背の広場 (StormExtra.PLAZA) の南の縁から、7 幅の橋が尾の上の空へ伸び、「渡り場」(YARD。休み場 yard) に着く。渡り場の四つの戸が四つの試練の入口:
//   西の戸 → 気嚢の滑空 (風の渡り)   東の戸 → 雷柱の渡り (雷の渡り)   南西の戸 → 羽守の見張り台   南東の戸 → 雷乞いの巣
// 引かれた試練の戸だけが、そのグループに開く (引いていない戸は閉じて見える)。どれも空に浮かぶ島 (下は尾羽の空。落ちたら休み場へ戻される)。
// 座標はここだけ。建て方は StormDeckBuilder、動かし方は jp.revati.skyabyss.trial.storm の四つのセル。背の作りを変えたら、StormTrialSelfTest が
// 島が互いに・ほかの部屋と重ならないこと、跳ぶ・滑空の距離が足りることを確かめる。
//
//   尾羽の上 (南) から見た平面 (北が上。x は西 -、東 +):
//
//        背の広場 (z 12〜35) ── 橋 (x 16〜22, z 36〜63) ──┐
//        S02 西 ← 渡り場 (z 64〜82) → S03 東                │
//        S10 南西 ← 南の戸 (z 83) → S09 南東
public final class StormDeck {
    private StormDeck() {
    }

    // 試練の甲板を置くか。既定は置く。自己テストが「置かない姿」(原型の三つだけ) を確かめるときだけ false にする
    // (開発用: 環境変数 SKYABYSS_STORM_DECK=0 で置かない姿にして比べられる)
    private static boolean placed = !"0".equals(System.getenv("SKYABYSS_STORM_DECK"));

    public static boolean placed() {
        return placed;
    }

    public static void setPlaced(boolean value) {
        placed = value;
    }

    // 甲板の床のブロックの高さ (背の広場と同じ)。足は Y + 1
    public static final int Y = StormExtra.DECK_Y;

    // ---------------------------------------------------------------- 橋と渡り場
    public static final int BRIDGE_X1 = 16;
    public static final int BRIDGE_X2 = 22;
    // 背の広場の南の縁 (z 36) から渡り場の北の縁 (z 63) まで
    public static final int BRIDGE_Z1 = 36;
    public static final int BRIDGE_Z2 = 63;
    public static final Area YARD = new Area(-24, 24, 64, 82, Y, 12);
    public static final BlockPos YARD_FIRE = new BlockPos(0, Y + 1, 73);
    public static final BlockPos YARD_SPAWN = new BlockPos(2, Y + 1, 73);
    // 試練の板 (渡り場の北の縁ぎわ。引いた三つが読める)
    public static final BlockPos BOARD_YARD = new BlockPos(-6, Y + 1, 66);
    // 風の広間 (鼓動の間) の板 (南の壁ぎわ。碑のそばで引きが読める)。数字は StormBackLayout (HUB.floor 134・HUB.z2 10) と同じ
    // (StormBackLayout が StormDeck を読むので、ここからは読まない。クラスの初期化が循環しないように。StormTrialSelfTest が同じかを確かめる)
    public static final BlockPos BOARD_HUB = new BlockPos(-10, 135, 10);

    // ---------------------------------------------------------------- 四つの戸 (封印の扉。高さ 5。渡り場の縁の手すりの列)
    public static final int G0 = Y + 1;
    public static final int G1 = Y + 5;
    public static final List<BlockPos> S02_GATE = wallX(-25, 71, 75);
    public static final List<BlockPos> S03_GATE = wallX(25, 71, 75);
    public static final List<BlockPos> S10_GATE = wallZ(83, -17, -13);
    public static final List<BlockPos> S09_GATE = wallZ(83, 13, 17);

    // ---------------------------------------------------------------- 原型の三つの入口の戸 (広間の三つの出口。引かなかったグループに閉じる。既定は開いている)
    // 西の肩の階段の平らなところ (x -24。z -27〜-21、高さ 135〜140)・東 (x 24)・上の回廊から冠への首 (z -60。x -3〜3、高さ 148〜154)
    // 数字は StormBackLayout (WZ -24・HUB.floor 134・NECK・UPPER_GALLERY_Y 147) と同じ (StormTrialSelfTest が確かめる)
    public static final List<BlockPos> WEST_GATE = wallX(-24, -27, -21, 135, 140);
    public static final List<BlockPos> EAST_GATE = wallX(24, -27, -21, 135, 140);
    public static final List<BlockPos> FRONT_GATE = wallZ(-60, -3, 3, 148, 154);

    // ---------------------------------------------------------------- 島 (床の形)
    // 四角 (中心から半分の幅 size) か丸 (半径 size)。y は床のブロックの高さ
    public record Island(int cx, int cz, int y, int size, boolean round) {
        public boolean has(int x, int z) {
            int dx = x - cx;
            int dz = z - cz;
            return round ? dx * dx + dz * dz <= size * size + size : Math.abs(dx) <= size && Math.abs(dz) <= size;
        }

        public int x1() {
            return cx - size;
        }

        public int x2() {
            return cx + size;
        }

        public int z1() {
            return cz - size;
        }

        public int z2() {
            return cz + size;
        }

        public BlockPos center() {
            return new BlockPos(cx, y, cz);
        }

        // 床のブロックの座標 (立つ所の 1 下)
        public List<BlockPos> floor() {
            List<BlockPos> out = new ArrayList<>();
            for (int x = x1(); x <= x2(); x++) {
                for (int z = z1(); z <= z2(); z++) {
                    if (has(x, z)) {
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
            return out;
        }

        // その床の上にいるか (水平だけ)
        public boolean over(double x, double z) {
            return has((int) Math.floor(x), (int) Math.floor(z));
        }

        public Area area(int height) {
            return new Area(x1(), x2(), z1(), z2(), y, height);
        }
    }

    private static Island deck(int cx, int cz, int y, int half) {
        return new Island(cx, cz, y, half, false);
    }

    private static Island pad(int cx, int cz, int y, int r) {
        return new Island(cx, cz, y, r, true);
    }

    // ---------------------------------------------------------------- S02 気嚢の滑空 (西。渡り場の西の戸から)
    // 島の並び: 始めの台 (気嚢の見本。いつも膨らんでいる) → 気嚢 → 気嚢 → 中ほどの台 (休み場) → 小さな気嚢 → 小さな気嚢 → 向こうの台 (石)。
    // 前半 (A1・A2) は大きく教え、後半 (A4・A5) は小さく・窓が短い (ひねり)。中ほどの台の気嚢は、長めに膨らむ。
    public static final Island[] S02_ISLANDS = {
            deck(-37, 73, Y, 5), pad(-55, 70, Y, 3), pad(-73, 77, Y, 3), deck(-91, 70, Y, 5), pad(-108, 63, Y, 2), pad(-125, 70, Y, 2), deck(-143, 77, Y, 5)};
    // 気嚢の数 (昇り風の穴がある島。最後の台にはない)
    public static final int S02_SACS = 6;
    // 気嚢の昇り風の中心 (床の中心)。穴の幅は中心から 1.6 まで (3x3)
    public static BlockPos s02Spot(int i) {
        Island is = S02_ISLANDS[i];
        return new BlockPos(is.cx(), is.y(), is.cz());
    }

    public static final BlockPos S02_STONE = new BlockPos(-147, Y + 1, 77);
    public static final AABB S02_AREA = new AABB(-152, Y - 12, 57, -26, Y + 52, 85);
    public static final Area S02_APPROACH = new Area(-31, -26, 71, 75, Y, 8);

    // ---------------------------------------------------------------- S03 雷柱の渡り (東。渡り場の東の戸から)
    // 島の並び: 始めの台 → 柱 1・2・3 (雷が前へ走る。教え) → 休み場の台 → 柱 4・5・6・7 (雷が後ろから走る。ひねり) → 向こうの台 (石)。
    // 柱の上は 5x5 の格子。真ん中は金の床 (雷が避ける)。どの跳びも空気 2 マス (BackReach の跳べる距離)
    public static final Island[] S03_ISLANDS = {
            deck(37, 73, Y, 5), new Island(47, 73, Y, 2, false), new Island(54, 73, Y + 1, 2, false), new Island(61, 73, Y + 2, 2, false), deck(70, 73, Y + 2, 4),
            new Island(79, 73, Y + 3, 2, false), new Island(86, 74, Y + 4, 2, false), new Island(93, 73, Y + 3, 2, false), new Island(100, 72, Y + 4, 2, false),
            deck(110, 72, Y + 3, 5)};
    // 柱 (5x5) の島の番号 (1・2・3 と 5・6・7・8)。0 は始めの台、4 は休み場の台、9 は向こうの台
    public static final int[] S03_PILLARS = {1, 2, 3, 5, 6, 7, 8};
    public static final int S03_TEACH = 3;
    public static final BlockPos S03_STONE = new BlockPos(114, Y + 4, 72);
    public static final AABB S03_AREA = new AABB(26, Y - 12, 57, 153, Y + 52, 85);
    public static final Area S03_APPROACH = new Area(26, 31, 71, 75, Y, 8);

    // ---------------------------------------------------------------- S09 雷乞いの巣 (南東。渡り場の南東の戸から)
    // 控え (休み場) → 橋 → 丸い巣 (半径 13。避雷の柱 6 本が半径 8) → 橋 (勝つと開く戸) → 石の島
    public static final Island S09_FOYER = deck(15, 96, Y, 5);
    public static final Island S09_ARENA = pad(36, 104, Y, 13);
    public static final Island S09_EXIT = deck(58, 104, Y, 3);
    public static final int S09_ARENA_R = 13;
    public static final int S09_ROD_R = 8;
    // 巣の橋 (控え → 巣): x 21〜23、z 99〜101。出口の橋 (巣 → 石の島): x 50〜54、z 102〜106 (その x 51 の列が戸)
    public static final int S09_BRIDGE_X1 = 21;
    public static final int S09_BRIDGE_X2 = 23;
    public static final int S09_BRIDGE_Z1 = 99;
    public static final int S09_BRIDGE_Z2 = 101;
    public static final int S09_EXIT_X1 = 50;
    public static final int S09_EXIT_X2 = 54;
    public static final List<BlockPos> S09_EXIT_GATE = wallX(51, 102, 106);
    public static final BlockPos S09_STONE = new BlockPos(59, Y + 1, 104);
    public static final AABB S09_AREA = new AABB(6, Y - 12, 86, 64, Y + 52, 128);
    public static final Area S09_APPROACH = new Area(13, 17, 84, 90, Y, 8);

    // 避雷の柱の根元 (床のブロックの 1 上)。半径 8、60 度おき
    public static List<BlockPos> s09Rods() {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            out.add(new BlockPos(S09_ARENA.cx() + (int) Math.round(Math.cos(a) * S09_ROD_R), Y + 1, S09_ARENA.cz() + (int) Math.round(Math.sin(a) * S09_ROD_R)));
        }
        return out;
    }

    // ---------------------------------------------------------------- S10 羽守の見張り台 (南西。渡り場の南西の戸から)
    // 台 0 (始め。休み場) → 台 1 → 台 2 (休み場) → 台 3 → 台 4 (石)。台と台のあいだは空気 9 マスで、風の穴 (台の西の縁) でだけ越える
    public static final Island[] S10_PLATFORMS = {deck(-15, 96, Y, 6), deck(-38, 104, Y, 6), deck(-61, 96, Y, 6), deck(-84, 104, Y, 6), deck(-107, 96, Y, 6)};
    public static final BlockPos S10_STONE = new BlockPos(-111, Y + 1, 96);
    public static final AABB S10_AREA = new AABB(-116, Y - 12, 86, -6, Y + 52, 128);
    public static final Area S10_APPROACH = new Area(-17, -13, 84, 90, Y, 8);

    // 台 i の風の穴 (西の縁から 3 内側)。台 0〜3 の穴が、台 i + 1 へ運ぶ
    public static BlockPos s10Vent(int i) {
        Island p = S10_PLATFORMS[i];
        return new BlockPos(p.cx() - 3, p.y(), p.cz());
    }

    // ---------------------------------------------------------------- 風の穴・滑空の距離
    // 昇り風の穴の幅 (中心から。StormWest.liftVent と同じ 1.6)
    public static final double VENT_R = 1.6;
    // 風切りの羽根の滑空: 落ちる 1 に対し前へ 6.25 (GaleFeather.FORWARD / FALL)
    public static final double GLIDE = GaleFeather.FORWARD / GaleFeather.FALL;

    // 凪の昇り風から、次の島の手前の縁まで滑空して届く余裕 (1 = ぎりぎり)。0.7 以下を約束にする
    public static double glideMargin(BlockPos from, Island to, double lift) {
        double reach = GLIDE * (lift + 1.0 + from.getY() - to.y() - 1.0);
        double dx = to.cx() - from.getX();
        double dz = to.cz() - from.getZ();
        double edge = Math.max(0, Math.hypot(dx, dz) - (to.round() ? to.size() : to.size() * 1.0));
        return reach <= 0 ? 99 : edge / reach;
    }

    // 昇り風の穴から次の島へ滑空する道 (詰み監査のモデル。BackReach には滑空がないので、島から島を一つの動きとして足す)。
    // 距離が滑空の余裕に収まることは StormTrialSelfTest と各セルの audit が確かめる
    public record Hop(BlockPos from, Island to) {
    }

    public static List<Hop> hops() {
        List<Hop> out = new ArrayList<>();
        for (int i = 0; i < S02_SACS; i++) {
            out.add(new Hop(s02Spot(i), S02_ISLANDS[i + 1]));
        }
        for (int i = 0; i < S10_PLATFORMS.length - 1; i++) {
            out.add(new Hop(s10Vent(i), S10_PLATFORMS[i + 1]));
        }
        return out;
    }

    // ---------------------------------------------------------------- 休み場
    public static final Rest REST_YARD = new Rest("yard", YARD_FIRE, YARD_SPAWN, YARD);
    public static final Rest REST_S02A = rest("s02a", S02_ISLANDS[0], -4, -4, -3, -2);
    public static final Rest REST_S02M = rest("s02m", S02_ISLANDS[3], -4, -4, -3, -2);
    public static final Rest REST_S03A = rest("s03a", S03_ISLANDS[0], 4, -4, 3, -2);
    public static final Rest REST_S03M = rest("s03m", S03_ISLANDS[4], 3, -3, 2, -2);
    public static final Rest REST_S09A = rest("s09a", S09_FOYER, -3, -3, -2, -2);
    public static final Rest REST_S10A = rest("s10a", S10_PLATFORMS[0], 3, -3, 3, -1);
    public static final Rest REST_S10M = rest("s10m", S10_PLATFORMS[2], 3, -3, 3, -1);
    public static final List<Rest> RESTS = List.of(REST_YARD, REST_S02A, REST_S02M, REST_S03A, REST_S03M, REST_S09A, REST_S10A, REST_S10M);

    // 島の隅に焚き火 (島の中心から (fx, fz))、目を覚ます所 (島の中心から (sx, sz))
    private static Rest rest(String id, Island is, int fx, int fz, int sx, int sz) {
        BlockPos fire = new BlockPos(is.cx() + fx, is.y() + 1, is.cz() + fz);
        BlockPos spawn = new BlockPos(is.cx() + sx, is.y() + 1, is.cz() + sz);
        return new Rest(id, fire, spawn, is.area(8));
    }

    // 試練のセルの休み場 (試練の id から)
    public static List<Rest> restsOf(String cell) {
        return switch (cell) {
            case "S02" -> List.of(REST_S02A, REST_S02M);
            case "S03" -> List.of(REST_S03A, REST_S03M);
            case "S09" -> List.of(REST_S09A);
            case "S10" -> List.of(REST_S10A, REST_S10M);
            default -> List.of();
        };
    }

    // ---------------------------------------------------------------- 看板 (碑。立てた碑。床の 1 上)
    // 試練ごとに「入口 (何の試練か)・始め (教える決まり)・中ほど (ひねり)」の順に読める
    public static final List<Inscription> SIGNS = List.of(
            new Inscription("sign.skyabyss.storm_d_bridge", new BlockPos(BRIDGE_X1 + 1, Y + 1, BRIDGE_Z1 + 4), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_yard", new BlockPos(-3, Y + 1, 67), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s02", new BlockPos(-28, Y + 1, 71), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s02_start", new BlockPos(-35, Y + 1, 69), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s02_mid", new BlockPos(-87, Y + 1, 67), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s03", new BlockPos(28, Y + 1, 71), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s03_start", new BlockPos(35, Y + 1, 69), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s03_mid", new BlockPos(72, Y + 3, 70), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s10", new BlockPos(-17, Y + 1, 86), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s10_start", new BlockPos(-12, Y + 1, 92), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s10_mid", new BlockPos(-64, Y + 1, 92), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s09", new BlockPos(17, Y + 1, 86), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s09_start", new BlockPos(18, Y + 1, 93), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s09_arena", new BlockPos(22, Y + 1, 98), Direction.UP));

    // ---------------------------------------------------------------- 部屋 (入ったときの一行・面積)
    record Zone(String id, AABB box) {
    }

    static List<Zone> zones() {
        List<Zone> out = new ArrayList<>();
        out.add(new Zone("d_bridge", new AABB(BRIDGE_X1, Y, BRIDGE_Z1, BRIDGE_X2 + 1, Y + 8, BRIDGE_Z2 + 1)));
        out.add(new Zone("d_yard", YARD.aabb()));
        out.add(new Zone("d_s02", S02_AREA));
        out.add(new Zone("d_s03", S03_AREA));
        out.add(new Zone("d_s10", S10_AREA));
        out.add(new Zone("d_s09", S09_AREA));
        return out;
    }

    // ---------------------------------------------------------------- 着くべき所 (詰み監査・自己テスト)
    static Map<String, BlockPos> goals() {
        Map<String, BlockPos> m = new LinkedHashMap<>();
        m.put("d bridge", new BlockPos(19, Y + 1, 50));
        m.put("d yard", new BlockPos(0, Y + 1, 70));
        m.put("d s02 start", REST_S02A.spawn());
        m.put("d s02 mid", REST_S02M.spawn());
        m.put("d s02 stone", S02_STONE.west());
        m.put("d s03 start", REST_S03A.spawn());
        m.put("d s03 mid", REST_S03M.spawn());
        m.put("d s03 stone", S03_STONE.west());
        m.put("d s09 foyer", REST_S09A.spawn());
        m.put("d s09 arena", new BlockPos(S09_ARENA.cx() - 4, Y + 1, S09_ARENA.cz() + 3));
        m.put("d s09 stone", S09_STONE.west());
        m.put("d s10 start", REST_S10A.spawn());
        m.put("d s10 mid", REST_S10M.spawn());
        m.put("d s10 stone", S10_STONE.east());
        return m;
    }

    // ---------------------------------------------------------------- 外へ開いていてよい所 (seal・小さな穴の検査から除く): 甲板の上すべて
    static boolean open(int x, int y, int z) {
        return placed && y >= Y - 1 && z >= BRIDGE_Z1 - 1 && z <= 130 && x >= -160 && x <= 160;
    }

    // ---------------------------------------------------------------- 面積 (m²。歩ける床。26 の数え方)
    public static int areaTotal() {
        int sum = (BRIDGE_X2 - BRIDGE_X1 + 1) * (BRIDGE_Z2 - BRIDGE_Z1 + 1) + (YARD.x2() - YARD.x1() + 1) * (YARD.z2() - YARD.z1() + 1);
        for (Island i : S02_ISLANDS) {
            sum += i.floor().size();
        }
        for (Island i : S03_ISLANDS) {
            sum += i.floor().size();
        }
        sum += S09_FOYER.floor().size() + S09_ARENA.floor().size() + S09_EXIT.floor().size();
        for (Island i : S10_PLATFORMS) {
            sum += i.floor().size();
        }
        return sum;
    }

    // ---------------------------------------------------------------- 戸の座標
    // x の列 (固定 x、z a〜b、高さ G0〜G1)
    public static List<BlockPos> wallX(int x, int z1, int z2) {
        return wallX(x, z1, z2, G0, G1);
    }

    public static List<BlockPos> wallX(int x, int z1, int z2, int y1, int y2) {
        List<BlockPos> out = new ArrayList<>();
        for (int z = z1; z <= z2; z++) {
            for (int y = y1; y <= y2; y++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return List.copyOf(out);
    }

    public static List<BlockPos> wallZ(int z, int x1, int x2) {
        return wallZ(z, x1, x2, G0, G1);
    }

    public static List<BlockPos> wallZ(int z, int x1, int x2, int y1, int y2) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return List.copyOf(out);
    }

    // 位置が、どれかの試練の島の上にあるか (滑空の羽根を持たせたままにする所の判断に使う)
    public static boolean inGlideCell(Vec3 p) {
        return S02_AREA.contains(p) || S10_AREA.contains(p);
    }

    // 位置が、四つの試練のどれかの中か (リセットで広間へ戻す範囲・嵐に投げ返されたときの傷)
    public static boolean inCell(Vec3 p) {
        return placed && (S02_AREA.contains(p) || S03_AREA.contains(p) || S09_AREA.contains(p) || S10_AREA.contains(p));
    }
}
