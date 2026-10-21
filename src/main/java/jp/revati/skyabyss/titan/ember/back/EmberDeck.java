package jp.revati.skyabyss.titan.ember.back;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// 余燼の背の「試練の階」(docs/design/29 の 2.4・3.4、26 の 11 章)。炉心の広間の南東の壁の螺旋 (床 92 → 119) の上の踊り場から、
// 背の中の浅い所 (火守りの谷・鍛えの間の天井の上。背の表の 6〜8 ブロック下) に、新作の試練のセルを並べる。
//
//   螺旋の踊り場 (床 119) ── 通路 C0 (南へ) ── 試練の階の広間 D0 (休み場 k0・試練の板)
//        ├ 東の回廊 KE ── 戸 → 噴気の渡り E02 (A) ／ 戸 → 鞴持ちの炉 E10 (B)
//        └ 西の回廊 KW ── 戸 → 煙突の風読み E06 (C) ／ 戸 → 三つの鞴 E07 (D)
//
// 床はみな y 119 (人は 120 に立つ)、天井は 126。背の亀裂 (CRACK。x -3〜3、z 22〜52) と体の外の肩からは 5 ブロック以上離れている。
// 原型の三つ (灰の道・鍛えの間・火守り) はそのまま。引かなかったグループには、広間からの冷えた溶岩洞の口の戸 (WEST_DOOR・EAST_DOOR・SOUTH_DOOR) が閉じる
public final class EmberDeck {
    private EmberDeck() {
    }

    // 試練の階を建てるか。版 23 から本番でも置く (既定 true)。自己テストが「置かない姿」(原型の三つだけ) を確かめるときだけ false にする
    // (開発用: 環境変数 SKYABYSS_TRIAL_DECK=0 で置かない姿にして比べられる)
    private static boolean placed = !"0".equals(System.getenv("SKYABYSS_TRIAL_DECK"));

    public static boolean placed() {
        return placed;
    }

    public static void setPlaced(boolean value) {
        placed = value;
    }

    // 床のブロックの y (足は Y + 1)・天井の空気の上端
    public static final int Y = 119;
    public static final int TOP = 126;
    public static final int G0 = Y + 1;
    public static final int G1 = Y + 5;

    // ---------------------------------------------------------------- 部屋の箱 (床を含む。minY = 床)
    // 螺旋の踊り場 (x 15〜18、z -30〜-28) から南へ。幅 5・高さ 5
    public static final Box C0 = new Box(15, Y, -27, 19, Y + 5, 5);
    // 試練の階の広間
    public static final Box D0 = new Box(-12, Y, 6, 22, TOP, 18);
    // 回廊 (東は x 8〜12 で E02 と E10 へ、西は x -12〜-8 で E06 と E07 へ)。幅 5・高さ 6
    // 回廊は南の端 (z 104) まで延びて、南の廊下 SP (z 105〜108) につながる (版 24)
    public static final Box KE = new Box(8, Y, 19, 12, Y + 6, 104);
    public static final Box KW = new Box(-12, Y, 19, -8, Y + 6, 104);
    // セル。A = 噴気の渡り E02、B = 鞴持ちの炉 E10、C = 煙突の風読み E06、D = 三つの鞴 E07 (内側の幅 21)
    public static final Box A = new Box(14, Y, 22, 34, TOP, 66);
    public static final Box B = new Box(14, Y, 72, 34, TOP, 103);
    public static final Box C = new Box(-34, Y, 22, -14, TOP, 60);
    public static final Box D = new Box(-34, Y, 64, -14, TOP, 102);
    // 版 24 の新しい部屋 (docs/design/29 の 3.4 の E03・E04・E08・E11・E12)。
    // 北西の鎖橋の間 NW (E03: 床の下が溶岩の坑。一本の鎖の橋)、北東の鋳型の間 NE (E08)、南の廊下 SP、
    // 南の三つの広間 SW (E11 の荷車の道)・SC (E12 の巣)・SE (E04 の熾の道)。南の広間は床 119・天井 124 (体の上 129 以上)
    public static final Box NW = new Box(-32, Y, 0, -14, TOP, 18);
    public static final Box NE = new Box(24, Y, 6, 34, TOP, 18);
    public static final Box SP = new Box(-32, Y, 105, 32, Y + 5, 108);
    public static final Box SW = new Box(-32, Y, 110, -15, Y + 5, 118);
    public static final Box SC = new Box(-13, Y, 110, 13, Y + 5, 118);
    public static final Box SE = new Box(15, Y, 110, 32, Y + 5, 118);

    // 部屋の名前 → 箱 (EmberBackLayout.ROOMS へも入る。自己テスト・コマンド・リセットの範囲)
    public static final List<String> NAMES = List.of("deck_c0", "deck_hall", "deck_ke", "deck_kw", "deck_e02", "deck_e10", "deck_e06", "deck_e07",
            "deck_e03", "deck_e08", "deck_sp", "deck_e11", "deck_e12", "deck_e04");

    public static Box box(String name) {
        return switch (name) {
            case "deck_c0" -> C0;
            case "deck_hall" -> D0;
            case "deck_ke" -> KE;
            case "deck_kw" -> KW;
            case "deck_e02" -> A;
            case "deck_e10" -> B;
            case "deck_e06" -> C;
            case "deck_e07" -> D;
            case "deck_e03" -> NW;
            case "deck_e08" -> NE;
            case "deck_sp" -> SP;
            case "deck_e11" -> SW;
            case "deck_e12" -> SC;
            case "deck_e04" -> SE;
            default -> throw new IllegalArgumentException(name);
        };
    }

    public static final List<Box> ROOMS = List.of(C0, D0, KE, KW, A, B, C, D, NW, NE, SP, SW, SC, SE);
    // 試練のセル (中にいる人は、グループが変わる・リセット・ログインで広間へ戻される)。版 24 で九つ
    public static final List<Box> CELLS = List.of(A, B, C, D, NW, NE, SW, SC, SE);

    // ---------------------------------------------------------------- 戸 (封印の扉。グループごとに開け閉め。高さ 5)
    // 入口: 回廊とセルのあいだの壁 (東は x 13、西は x -13)
    public static final List<BlockPos> E02_GATE = wallX(13, 23, 27);
    public static final List<BlockPos> E10_GATE = wallX(13, 73, 77);
    public static final List<BlockPos> E06_GATE = wallX(-13, 23, 27);
    public static final List<BlockPos> E07_GATE = wallX(-13, 65, 69);
    // 出口 (石の前の戸): 煙突の風読み・三つの鞴の奥の仕切り (z 54・96。x -27〜-23)
    public static final int E06_EXIT_Z = 54;
    public static final int E07_EXIT_Z = 96;
    public static final List<BlockPos> E06_EXIT = wallZ(E06_EXIT_Z, -27, -23);
    public static final List<BlockPos> E07_EXIT = wallZ(E07_EXIT_Z, -27, -23);
    // 版 24 の入口: 鎖橋の間 (西壁 x -13)・鋳型の間 (東壁 x 23)・南の三つの広間 (南の壁 z 109)
    public static final List<BlockPos> E03_GATE = wallX(-13, 8, 12);
    public static final List<BlockPos> E08_GATE = wallX(23, 8, 12);
    public static final List<BlockPos> E11_GATE = wallZ(109, -26, -20);
    public static final List<BlockPos> E12_GATE = wallZ(109, -4, 4);
    public static final List<BlockPos> E04_GATE = wallZ(109, 20, 26);
    // 入口の戸の一覧 (ブロックの配置・床の掘り直し・監査で同じ順に使う)
    public static final List<List<BlockPos>> GATES = List.of(E02_GATE, E10_GATE, E06_GATE, E07_GATE, E03_GATE, E08_GATE, E11_GATE, E12_GATE, E04_GATE);
    // 試練の階の戸のブロックの位置 (入口 4・出口 2)。どのグループに開くかは引きが決める (詰み監査・歩きの検査は開いているものとして通る)
    private static final java.util.Set<Long> POOL_GATES = new java.util.HashSet<>();

    static {
        for (List<BlockPos> g : List.of(E02_GATE, E10_GATE, E06_GATE, E07_GATE, E03_GATE, E08_GATE, E11_GATE, E12_GATE, E04_GATE, E06_EXIT, E07_EXIT)) {
            for (BlockPos p : g) {
                POOL_GATES.add(p.asLong());
            }
        }
    }

    public static boolean isPoolGate(BlockPos p) {
        return POOL_GATES.contains(p.asLong());
    }

    public static List<BlockPos> wallX(int x, int z1, int z2) {
        List<BlockPos> out = new ArrayList<>();
        for (int z = z1; z <= z2; z++) {
            for (int y = G0; y <= G1; y++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return List.copyOf(out);
    }

    public static List<BlockPos> wallZ(int z, int x1, int x2) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            for (int y = G0; y <= G1; y++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- 石の差し口 (熾り石)・戻りの光る床 (中心の床)
    public static final BlockPos E02_STONE = new BlockPos(30, Y + 1, 64);
    public static final BlockPos E02_PAD = new BlockPos(24, Y, 61);
    public static final BlockPos E10_STONE = new BlockPos(22, Y + 1, 101);
    public static final BlockPos E10_PAD = new BlockPos(28, Y, 100);
    public static final BlockPos E06_STONE = new BlockPos(-25, Y + 1, 59);
    public static final BlockPos E06_PAD = new BlockPos(-25, Y, 56);
    public static final BlockPos E07_STONE = new BlockPos(-25, Y + 1, 101);
    public static final BlockPos E07_PAD = new BlockPos(-25, Y, 98);
    // 版 24: 鎖橋 (E03。石は橋の向こうの岸)・鋳型 (E08。石は奥の隅)・荷車 (E11。石は道のわき)・巣 (E12)・熾の道 (E04。石は道の終わり)
    public static final BlockPos E03_STONE = new BlockPos(-31, Y + 1, 10);
    public static final BlockPos E03_PAD = new BlockPos(-18, Y, 14);
    public static final BlockPos E08_STONE = new BlockPos(33, Y + 1, 8);
    public static final BlockPos E08_PAD = new BlockPos(31, Y, 12);
    public static final BlockPos E11_STONE = new BlockPos(-16, Y + 1, 112);
    public static final BlockPos E11_PAD = new BlockPos(-24, Y, 116);
    public static final BlockPos E12_STONE = new BlockPos(12, Y + 1, 111);
    public static final BlockPos E12_PAD = new BlockPos(0, Y, 116);
    public static final BlockPos E04_STONE = new BlockPos(31, Y + 1, 116);
    public static final BlockPos E04_PAD = new BlockPos(20, Y, 116);


    // ---------------------------------------------------------------- 休み場 (EmberBackLayout.RESTS の続き。5 × 5 の台、焚き火は南の端の真ん中)
    public static final List<EmberBackLayout.Rest> RESTS = List.of(
            new EmberBackLayout.Rest("k0", new BlockPos(3, Y, 12), 0f),
            new EmberBackLayout.Rest("k_e02a", new BlockPos(17, Y, 25), 0f),
            new EmberBackLayout.Rest("k_e02b", new BlockPos(24, Y, 44), 0f),
            new EmberBackLayout.Rest("k_e10", new BlockPos(17, Y, 75), 0f),
            new EmberBackLayout.Rest("k_e06", new BlockPos(-19, Y, 25), 0f),
            new EmberBackLayout.Rest("k_e07", new BlockPos(-19, Y, 67), 0f),
            // 版 24 の休み場 (5 × 5 の台。道と戸の前をあけてある)
            new EmberBackLayout.Rest("k_e03", new BlockPos(-17, Y, 4), 0f),
            new EmberBackLayout.Rest("k_e08", new BlockPos(27, Y, 12), 0f),
            new EmberBackLayout.Rest("k_e11", new BlockPos(-30, Y, 116), 0f),
            new EmberBackLayout.Rest("k_e12", new BlockPos(9, Y, 116), 0f),
            new EmberBackLayout.Rest("k_e04", new BlockPos(25, Y, 116), 0f));

    public static final List<String> REST_NAMES = RESTS.stream().map(EmberBackLayout.Rest::name).toList();

    // 休み場の名前 → その部屋 (自己テスト)
    public static String roomOfRest(String rest) {
        return switch (rest) {
            case "k0" -> "deck_hall";
            case "k_e02a", "k_e02b" -> "deck_e02";
            case "k_e10" -> "deck_e10";
            case "k_e06" -> "deck_e06";
            case "k_e03" -> "deck_e03";
            case "k_e08" -> "deck_e08";
            case "k_e11" -> "deck_e11";
            case "k_e12" -> "deck_e12";
            case "k_e04" -> "deck_e04";
            default -> "deck_e07";
        };
    }

    // ---------------------------------------------------------------- 灰溜まり (冷える。灰色の羊毛。3 × 2 の長方形の左上の角)
    public static final List<int[]> ASH = List.of(
            // 噴気の渡り: 帯のあいだの 3 つ + 奥の岸
            new int[] {22, 36}, new int[] {22, 52}, new int[] {22, 60},
            // 鞴持ちの炉: 四隅
            new int[] {16, 79}, new int[] {30, 79}, new int[] {16, 97}, new int[] {30, 94},
            // 煙突・鞴の間: 入口の脇
            new int[] {-32, 26}, new int[] {-32, 68},
            // 版 24: 鎖橋の間の岸 (橋の手前の二つ)・南の広間の角 (荷車・巣・熾の道の入口の脇)
            new int[] {-30, 2}, new int[] {-30, 16}, new int[] {-28, 110}, new int[] {10, 110}, new int[] {16, 115});

    // ---------------------------------------------------------------- 掲示の碑 (規則を一つずつ教える。lang の sign.skyabyss.ember_board_<id>.1〜4)。向き = 文が向く向き (後ろは壁)
    public static final List<EmberBackLayout.Inscription> BOARDS = List.of(
            new EmberBackLayout.Inscription("deck_enter", new BlockPos(15, Y + 2, -24), Direction.EAST),
            new EmberBackLayout.Inscription("deck_hall", new BlockPos(-2, Y + 2, 6), Direction.SOUTH),
            new EmberBackLayout.Inscription("e02_rule", new BlockPos(22, Y + 2, 22), Direction.SOUTH),
            new EmberBackLayout.Inscription("e02_band", new BlockPos(14, Y + 2, 36), Direction.EAST),
            new EmberBackLayout.Inscription("e02_end", new BlockPos(30, Y + 2, 66), Direction.NORTH),
            new EmberBackLayout.Inscription("e10_rule", new BlockPos(22, Y + 2, 72), Direction.SOUTH),
            new EmberBackLayout.Inscription("e10_heat", new BlockPos(14, Y + 2, 82), Direction.EAST),
            new EmberBackLayout.Inscription("e06_rule", new BlockPos(-24, Y + 2, 22), Direction.SOUTH),
            new EmberBackLayout.Inscription("e06_decoy", new BlockPos(-34, Y + 2, 38), Direction.EAST),
            new EmberBackLayout.Inscription("e06_away", new BlockPos(-34, Y + 2, 46), Direction.EAST),
            new EmberBackLayout.Inscription("e07_rule", new BlockPos(-24, Y + 2, 64), Direction.SOUTH),
            new EmberBackLayout.Inscription("e07_weight", new BlockPos(-34, Y + 2, 72), Direction.EAST),
            new EmberBackLayout.Inscription("e07_leaky", new BlockPos(-34, Y + 2, 84), Direction.EAST),
            // 版 24 の碑 (規則を一つずつ。lang の sign.skyabyss.ember_board_<id>.1〜4)
            new EmberBackLayout.Inscription("e03_rule", new BlockPos(-15, Y + 2, 7), Direction.EAST),
            new EmberBackLayout.Inscription("e03_hot", new BlockPos(-22, Y + 2, 0), Direction.SOUTH),
            new EmberBackLayout.Inscription("e08_rule", new BlockPos(26, Y + 2, 6), Direction.SOUTH),
            new EmberBackLayout.Inscription("e08_mirror", new BlockPos(32, Y + 2, 6), Direction.SOUTH),
            new EmberBackLayout.Inscription("e11_rule", new BlockPos(-31, Y + 2, 110), Direction.SOUTH),
            new EmberBackLayout.Inscription("e11_road", new BlockPos(-19, Y + 2, 110), Direction.SOUTH),
            new EmberBackLayout.Inscription("e12_rule", new BlockPos(-8, Y + 2, 110), Direction.SOUTH),
            new EmberBackLayout.Inscription("e12_vent", new BlockPos(8, Y + 2, 110), Direction.SOUTH),
            new EmberBackLayout.Inscription("e04_rule", new BlockPos(22, Y + 2, 110), Direction.SOUTH),
            new EmberBackLayout.Inscription("e04_wind", new BlockPos(28, Y + 2, 110), Direction.SOUTH));
}
