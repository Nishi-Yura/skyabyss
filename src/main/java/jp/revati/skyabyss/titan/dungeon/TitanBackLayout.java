package jp.revati.skyabyss.titan.dungeon;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

// 岩鯨の背の配置図。建てる側 (Builder) と仕組みの側 (試練・休憩地点・リセット) の両方がここを参照する。
//
// 島は、空を泳ぐ岩鯨と同じ形のブロックの鯨 (WhaleShape。頭は -Z、全長 412、体の幅 82、ゲームの岩鯨の 2.8 倍)。
// 試練の部屋は体の中に埋まっている。体内の主な床は y=93 (床ブロック、1つ上から空間)。
// 尾の付け根の背 (z 100) に到着の広場と錨石、背の中ほど (z 39) に祠。祠の階段で体の中の鼓動の間へ下りる。
// 西の翼は左のわき腹の肋骨の間 (肋骨の窓から空が見える)、東の翼は背骨に沿った広間、闇の水路は腹の中の水、心臓の間は胸の奥
//
//              [宝物庫]  (北: 封印の扉の奥)
//                 |
//   [西の翼]--[鼓動の間]--[東の翼]
//                 |  \__ 床の穴の下に [闇の水路] (y 77〜81)
//            [入口の通路]
//                 |
//             [祠] (背の中ほど) … 背の石の門 (アーチ) … [尾の付け根の広場・錨石] (到着地点)
//
// 西の翼 (裂け目の回廊): 西の前室 → 肋骨の谷 → 第一の休み場 → 竪穴 → 第二の休み場 → 崩れ橋 → 共鳴石
//                        足場の並びは毎回変わる
// 東の翼 (番兵の回廊):   東の前室 → 第一の広間 → 番兵長の控えの間 → 短い通路 → 番兵長の広間 → 共鳴石
//                        広間の扉は戦いの間だけ閉じる。制圧すると出口と入口が開く
// 心臓の回廊・心臓の間: 宝物庫の奥の扉から北へ。円形の大広間で岩鯨の心臓と戦う (最終ボス)
// 闇の水路: 3×3 の水の通路でできた迷路。毎回形が変わり、途中に息継ぎのくぼみだけがある
public final class TitanBackLayout {
    // 雲の海の高さの基準 (雲は BASE_Y - 55 = 45、BASE_Y - 50 より下に落ちたら地上へ)。鯨の腹の下端は y 58
    public static final int BASE_Y = 100;
    // 鯨の全体を包む範囲 (x -RX〜RX、z -RZ〜RZ)。番兵の掃除・看板の数え・地図に使う
    public static final int RX = 100;
    public static final int RZ = 256;
    public static final int FLOOR = 93;

    // 配置の版。部屋の配置を変えたら上げる。保存された版と違えば、起動時に背中を建て直す (TitanBackData)
    //  1: 最初の配置 / 2: 東の翼の第二の広間 (E2) と番兵長の控えの間 (旧 CE2) を除いた
    //  4: 案内の看板を減らした (試練の入口の一言と碑文だけ残す。古い看板が残らないよう建て直す)
    //  5: 楕円の丘を、岩鯨のモデルと同じ形のブロックの鯨にした (部屋は体の中のまま。到着は尾の付け根、祠は背の中ほど)
    //  7: 到着を、左わき腹の錨石の外の台にした (台から背の広場まで体の中を登る階段を足した。錨石・炉は台へ)
    //  8: 闇の水路の中ほどの息継ぎのくぼみに休み場の足場を足した。東の前室・控えの間の復活位置を扉から離した
    //  9: 東の第一の広間 (E1) を 13×23 → 13×30、番兵長の広間 (E3) を 9×23 → 9×31 に延ばした。心臓の間を内側の半径 14.5 → 19 に広げ、
    //     中心を 5 北へ (z -85)。テレグラフの円 (スタンプ 半径 5 など) が床の 4 割以下に収まり、避ける広さが残る (02 の 4.2、5.2)
    // 10: 詰み監査の修正 (西の翼の最後の岩棚と戸のあいだの溶岩の穴を埋めた。鼓動の間の西のはしごの頂を戸の足もとの高さに下げた)
    // 11: 闇の水路の入口 (はしごを下りた先) に、乾いた小部屋の休み場 (mz0) を足した
    public static final int LAYOUT_VERSION = 11;

    // ---------------------------------------------------------------- 地表
    // 尾の付け根の背の広場 (到着) と、背の中ほどの祠。祠の階段は 1 段ごとに 1 下がり、入口の通路の南の端 (z 4) で床に着く
    public static final int PLATFORM_Z = 100;
    public static final int SHRINE_Z = 39;
    // 広場と祠のあいだの、背の石の門 (モデルの半ば埋まったアーチ、z 48〜60)
    public static final int RIDGE_Z = 53;
    // モデルの錨石 (左のわき腹の留め具、x -38〜-27、y 83〜95、z 107〜113)。鎖はここから西へ張り出し (x = ANCHOR_CHAIN_X)、雲の海へ垂れる
    public static final int ANCHOR_STONE_X = -38;
    public static final int ANCHOR_CHAIN_X = -48;
    public static final int ANCHOR_CHAIN_Y = 92;
    public static final int ANCHOR_CHAIN_Z = 110;
    // 左のわき腹の台 (到着): 錨石 (モデルの鉄の留め具) の外に張り出した石の台。床の高さ・範囲 (x は西の端から体の面まで、z)。
    // 台の北の端から、体の中を東へ登る階段が、背の広場 (尾の付け根) の石畳へ出る
    public static final int FLANK_FLOOR_Y = 88;
    public static final int FLANK_X0 = -47;
    public static final int FLANK_X1 = -31;
    public static final int FLANK_Z0 = 100;
    public static final int FLANK_Z1 = 116;
    // 到着の向き (東 = 体のほう。錨石・炉・登り口の階段が見える)
    public static final float ARRIVAL_YAW = 270f;
    // 登り口の階段: x = STAIR_X0 から東へ、1 ブロックごとに 1 上がる。幅 3 (z STAIR_Z0〜STAIR_Z1)
    public static final int STAIR_X0 = -34;
    public static final int STAIR_Z0 = 104;
    public static final int STAIR_Z1 = 106;

    // ピースフルのあいだ祠の階段を塞ぐ封印の z (階段の 5 段目、祠の床の下に入った所)。これより北の体内は試練の場
    public static final int SHRINE_SEAL_Z = SHRINE_Z - 5;

    // ---------------------------------------------------------------- 中央
    public static final Box HUB = new Box(-9, 9, -32, -12, FLOOR, 9);
    public static final Box ENTRY = new Box(-1, 1, -11, 4, FLOOR, 4);
    public static final Box TREASURY = new Box(-6, 6, -44, -34, FLOOR, 7);
    // 宝物庫の封印 (三つの石で開く)
    public static final List<BlockPos> GATE = wall(-1, 1, 94, 97, -33, true);
    // 宝物庫の奥の扉 (三つの石で封印が開くと、一緒に開く)。その先は心臓の回廊
    public static final List<BlockPos> INNER_GATE = wall(-1, 1, 94, 97, -45, true);
    // 天の鍵の欠片の台座 (飾り。欠片は岩鯨の心臓を倒すと、そこで受け取る)
    public static final BlockPos PEDESTAL = new BlockPos(0, FLOOR + 1, -41);

    // ---------------------------------------------------------------- 心臓の回廊と心臓の間 (最終ボス)
    // 奥の扉から北へ伸びる回廊 (x -1〜1、z -46〜-63)。途中に休み場 heart がある
    public static final Box HEART_REST = new Box(-3, 3, -57, -51, FLOOR, 4);
    // 心臓の間: 中心 (0, 94, -85)、床 y=93、天井 y=106 (体の表面の内側に収まる)。内側の半径 19、壁の外側 21 (入口の壁 z=-64 が円の南の端)
    public static final int HEART_CX = 0;
    public static final int HEART_CZ = -85;
    public static final Box HEART = new Box(-21, 21, -106, -64, FLOOR, 12);
    // 心臓の間の入口 (戦いが始まると閉じる)。z=HEART_GATE_Z の壁
    public static final int HEART_GATE_Z = -64;
    public static final List<BlockPos> HEART_GATE = wall(-1, 1, 94, 97, HEART_GATE_Z, true);
    // 心臓を倒すと現れる光の柱。上に立つと尾の台へ戻される
    public static final BlockPos HEART_PILLAR = new BlockPos(HEART_CX, FLOOR + 1, HEART_CZ);

    // ---------------------------------------------------------------- 西の翼 (アスレチック)
    public static final Box W0 = new Box(-22, -16, -26, -18, FLOOR, 6);
    public static final Box WA = new Box(-36, -24, -34, -12, FLOOR, 11);
    public static final Box W1 = new Box(-34, -28, -42, -36, 97, 5);
    public static final Box WB = new Box(-31, -22, -56, -44, FLOOR, 11);
    public static final Box W2 = new Box(-24, -16, -63, -58, 100, 4);
    public static final Box WC = new Box(-19, -11, -52, -30, FLOOR, 11);
    // 足場の並びは試練が元に戻るたびに作り直される (ParkourCourses)
    // 落ちたら直前の休み場へ戻される高さ (この高さより下に落ちたら)
    public static final int FALL_LINE = 90;
    public static final List<Box> PITS = List.of(WA, WB, WC);
    // 西の石を鳴らすと開く、鼓動の間へ下りる抜け道
    public static final List<BlockPos> WEST_RETURN = List.of(new BlockPos(-10, 98, -31), new BlockPos(-10, 99, -31));

    // ---------------------------------------------------------------- 東の翼 (戦闘)
    public static final Box E0 = new Box(16, 22, -26, -18, FLOOR, 6);
    public static final Box E1 = new Box(24, 36, -34, -5, FLOOR, 8);
    // 番兵長の控えの間 (休み場 ce1)。E1 の出口の先。西の壁の戸口から短い通路で番兵長の広間へ
    public static final Box CE1 = new Box(26, 32, -42, -36, FLOOR, 5);
    public static final Box E3 = new Box(11, 19, -60, -30, FLOOR, 8);
    // 控えの間 → 番兵長の広間の通路 (x 21〜24)。両端の壁 x=25 (戸口) と x=20 (番兵長の広間の入口)
    public static final Box E3_CORRIDOR = new Box(21, 24, -40, -38, FLOOR, 4);
    public static final List<BlockPos> E1_ENTRANCE = wall(-23, -21, 94, 97, 23, false);
    public static final List<BlockPos> E1_EXIT = wall(28, 30, 94, 97, -35, true);
    public static final List<BlockPos> CE1_WEST_DOOR = wall(-40, -38, 94, 97, 25, false);
    public static final List<BlockPos> E3_ENTRANCE = wall(-40, -38, 94, 97, 20, false);
    // 番兵長の広間の前室 (扉が閉じるとき、ここにいる人も中へ入れる): 控えの間と通路
    public static final AABB E3_ANTE = CE1.aabb().minmax(E3_CORRIDOR.aabb());
    // 東の石を鳴らすと開く、鼓動の間へ戻る扉
    public static final List<BlockPos> EAST_RETURN = List.of(new BlockPos(10, 94, -31), new BlockPos(10, 95, -31));

    // ---------------------------------------------------------------- 闇の水路
    // 11×15 マス、1マス 3×3 の水、壁の厚さ1。x -22〜22, z -62〜-2、床 y 77 (水は y 78〜80、天井 y 81)
    public static final int MAZE_X0 = -22;
    public static final int MAZE_Z0 = -62;
    public static final int MAZE_W = 11;
    public static final int MAZE_H = 15;
    public static final int MAZE_CELL = 3;
    public static final int MAZE_FLOOR = 77;
    public static final int MAZE_START_I = 6;
    public static final int MAZE_START_J = 9;
    public static final int MAZE_EXIT_I = 8;
    public static final int MAZE_EXIT_J = 13;
    // 入口のはしごを下りた先の、乾いた小部屋 (迷路の天井の上。床は迷路の天井の石、中は 3×3×3。はしごが北の隅を通る)
    public static final Box MAZE_ENTRY_ROOM = new Box(3, 5, -25, -23, MAZE_FLOOR + 4, 3);
    public static final Box MAZE_EXIT_ROOM = new Box(10, 14, -10, -6, 81, 4);
    // 出口の小部屋から入口の通路へ戻る抜け道 (下の石が歌うまで閉じている)
    public static final List<BlockPos> LOWER_RETURN = List.of(new BlockPos(2, 94, -7), new BlockPos(2, 95, -7));

    // ---------------------------------------------------------------- 共鳴石 (0 = 西, 1 = 東, 2 = 下)
    public static final List<BlockPos> RESONANCE_STONES = List.of(
            new BlockPos(-15, 98, -30), new BlockPos(15, 94, -31), new BlockPos(13, 82, -9));

    // ---------------------------------------------------------------- 碑文 (物語の断片)。sign.skyabyss.lore_<name>
    // facing は看板の向く側。看板は反対側の壁に掛かる
    public record Inscription(String key, BlockPos pos, net.minecraft.core.Direction facing) {
    }

    public static final List<Inscription> LORE_SIGNS = List.of(
            new Inscription("sign.skyabyss.lore_heart", new BlockPos(3, 96, -54), net.minecraft.core.Direction.WEST),
            new Inscription("sign.skyabyss.lore_vault", new BlockPos(-6, 96, -38), net.minecraft.core.Direction.EAST),
            new Inscription("sign.skyabyss.lore_west", new BlockPos(-17, 99, -30), net.minecraft.core.Direction.NORTH),
            new Inscription("sign.skyabyss.lore_east", new BlockPos(17, 96, -30), net.minecraft.core.Direction.NORTH),
            new Inscription("sign.skyabyss.lore_below", new BlockPos(11, 83, -6), net.minecraft.core.Direction.NORTH));

    // ---------------------------------------------------------------- 試練の入口の一言 (看板)。背中に置く看板は、これと碑文だけ
    // 案内 (錨石・炉・休み場・封印の扉・台座・抜け道) は看板にせず、形・光・音と、最初の一度だけの囁き (BackWhispers) で伝える
    public static final List<Inscription> TRIAL_SIGNS = List.of(
            new Inscription("sign.skyabyss.chasm", new BlockPos(-9, FLOOR + 3, -25), net.minecraft.core.Direction.EAST),
            new Inscription("sign.skyabyss.arena", new BlockPos(9, FLOOR + 3, -25), net.minecraft.core.Direction.WEST),
            new Inscription("sign.skyabyss.maze", new BlockPos(5, FLOOR + 3, -32), net.minecraft.core.Direction.SOUTH));
    // 心臓の間の入口 (最後の試練)
    public static final Inscription HEART_GATE_SIGN =
            new Inscription("sign.skyabyss.heart_gate", new BlockPos(1, FLOOR + 3, -62), net.minecraft.core.Direction.WEST);

    // 背中に置く看板すべて (自己テストで、この数より多く置かれていないか・各行が看板の幅に収まるかを見る)
    public static List<Inscription> allSigns() {
        List<Inscription> all = new java.util.ArrayList<>(LORE_SIGNS);
        all.addAll(TRIAL_SIGNS);
        all.add(HEART_GATE_SIGN);
        return List.copyOf(all);
    }

    private TitanBackLayout() {
    }

    // 祠の階段の封印 (ピースフルのあいだだけ置く)。階段の幅 3 × 高さ 4
    public static List<BlockPos> shrineSeal() {
        int step = shrineY() - 1 - (SHRINE_Z - 1 - SHRINE_SEAL_Z);
        return wall(-1, 1, step + 1, step + 4, SHRINE_SEAL_Z, true);
    }

    // 到着の場所: 左のわき腹の台。乗り移ってきた人はここに立つ (向きは ARRIVAL_YAW)
    public static BlockPos arrival() {
        return new BlockPos(-41, FLANK_FLOOR_Y + 1, 108);
    }

    // 尾の付け根の広場 (背の上)。台から階段を登った先。倒れたとき・あきらめたとき・光の柱の行き先 (記録がなければ)
    public static BlockPos plaza() {
        return new BlockPos(0, platformY() + 1, PLATFORM_Z - 2);
    }

    // 台に置かれた共鳴の炉 (着いてすぐ装備を鍛えられる)
    public static BlockPos forge() {
        return new BlockPos(-40, FLANK_FLOOR_Y + 1, 105);
    }

    // 錨石 (台の上、モデルの留め具の外の面に寄せた石の磁石)。右クリックすると地上へ降りる
    public static BlockPos anchor() {
        return new BlockPos(ANCHOR_STONE_X + 1, FLANK_FLOOR_Y + 1, ANCHOR_CHAIN_Z);
    }

    // 登り口の階段の段数 (台の高さから広場の石畳の高さまで。段 i の足の高さ = FLANK_FLOOR_Y + 1 + i)
    public static int stairSteps() {
        return platformY() - FLANK_FLOOR_Y;
    }

    // 広場の石畳の高さ: 広場 (x -8〜8、z ±10) の背のいちばん高い所。背の丸みに沈まず、北の背を見渡せる
    private static int platformY = Integer.MIN_VALUE;

    public static int platformY() {
        if (platformY == Integer.MIN_VALUE) {
            int y = BASE_Y;
            for (int x = -8; x <= 8; x++) {
                for (int z = PLATFORM_Z - 10; z <= PLATFORM_Z + 10; z++) {
                    y = Math.max(y, top(x, z));
                }
            }
            platformY = y;
        }
        return platformY;
    }

    public static int shrineY() {
        return top(0, SHRINE_Z);
    }

    // 鯨の体がある柱なら 1、無ければ 0 (前の楕円の島の名残の名前。> 0 で「島の上」)
    public static double shape(double x, double z) {
        return WhaleShape.bodyTop((int) Math.floor(x), (int) Math.floor(z)) == Integer.MIN_VALUE ? 0 : 1;
    }

    // 体 (頭・顎・胴・尾) の背のいちばん上。背の遺跡・背骨は含まない。体の無い柱は BASE_Y
    public static int top(int x, int z) {
        int t = WhaleShape.bodyTop(x, z);
        return t == Integer.MIN_VALUE ? BASE_Y : t;
    }

    // 鯨の腹のいちばん下。無い柱は BASE_Y
    public static int bottom(int x, int z) {
        int b = WhaleShape.bottom(x, z);
        return b == Integer.MIN_VALUE ? BASE_Y : b;
    }

    // 迷路のマス (i, j) の北西の角
    public static BlockPos mazeCell(int i, int j) {
        return new BlockPos(MAZE_X0 + 1 + i * (MAZE_CELL + 1), MAZE_FLOOR + 1, MAZE_Z0 + 1 + j * (MAZE_CELL + 1));
    }

    // 戸口の位置の一覧。alongX = true なら z が一定で x が a〜b、false なら x が一定で z が a〜b
    private static List<BlockPos> wall(int a, int b, int y1, int y2, int fixed, boolean alongX) {
        List<BlockPos> list = new java.util.ArrayList<>();
        for (int y = y1; y <= y2; y++) {
            for (int v = a; v <= b; v++) {
                list.add(alongX ? new BlockPos(v, y, fixed) : new BlockPos(fixed, y, v));
            }
        }
        return List.copyOf(list);
    }

    // 部屋: 内側の x1〜x2, z1〜z2、床ブロックの高さ floorY、内側の高さ height
    public record Box(int x1, int x2, int z1, int z2, int floorY, int height) {
        public int ceilingY() {
            return floorY + height + 1;
        }

        public boolean contains(BlockPos p) {
            return p.getX() >= x1 && p.getX() <= x2 && p.getZ() >= z1 && p.getZ() <= z2 && p.getY() > floorY - 12 && p.getY() < ceilingY();
        }

        public AABB aabb() {
            return new AABB(x1, floorY + 1, z1, x2 + 1, ceilingY(), z2 + 1);
        }

        public BlockPos center() {
            return new BlockPos((x1 + x2) / 2, floorY + 1, (z1 + z2) / 2);
        }
    }
}
