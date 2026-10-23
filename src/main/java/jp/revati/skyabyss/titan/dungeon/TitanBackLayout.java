package jp.revati.skyabyss.titan.dungeon;

import java.util.List;

import jp.revati.skyabyss.titan.BackScale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

// 岩鯨の背の配置図 (版 13: 水平 x1.6・垂直 x1.3 に作り直した。設計書 26、02 の 3.1)。建てる側 (Builder) と仕組みの側 (試練・休憩地点・リセット) の両方がここを参照する。
//
// 島は、空を泳ぐ岩鯨と同じ形のブロックの鯨 (WhaleShape。頭は -Z、全長 約 680、体の幅 約 135、ゲームの岩鯨の 4.5 倍)。
// 試練の部屋は体の中に埋まっている。体内の主な床は y=103 (床ブロック、1つ上から空間)。腹の下端は y 58 のまま (雲の海の高さの基準 BASE_Y は動かさない)。
// 尾の付け根の背 (z 160) に到着の広場と錨石、背の中ほど (z 62) に祠。祠の階段で体の中の鼓動の間へ下りる。
// 西の翼は左のわき腹の肋骨の間 (肋骨の窓から空が見える)、東の翼は背骨に沿った広間、闇の水路は腹の中の水、心臓の間は胸の奥
//
//              [封印の回廊 → 心臓の間]  (北)         [歌の祠]
//                 |
//   [西の翼]--[鼓動の間]--[東の翼]   [図書室 (骨の書庫)]
//                 |  \__ 床の穴の下に [闇の水路] (y 84〜89。入口 2 つ)
//            [入口の通路]
//                 |
//             [祠] (背の中ほど) … 背の石の門 (アーチ) … [尾の付け根の広場・錨石] (到着地点) … [尾の展望]
//
// 西の翼 (裂け目の回廊): 西の前室 → 肋骨の谷 → 第一の休み場 → 竪穴 → 第二の休み場 (→ 数え歌の鐘楼) → 崩れ橋 → 共鳴石
//                        足場の並びは毎回変わる (谷ごとに 3 本の並列のレーン)
// 東の翼 (番兵の回廊):   東の前室 → 第一の広間 → 控えの間 → 第二の広間 (射手の波) → 前室 → 番兵の詰所 (老番兵が持つ「割符」) → 通路 → 番兵長の広間 → 共鳴石
//                        一本道 (戻らない)。広間の扉は戦いの間だけ閉じる。制圧すると次の戸が開く
// 心臓の回廊・心臓の間: 鼓動の間の北の封印から、そのまま北へ。円形の大広間で岩鯨の心臓と戦う (最終ボス)
// 闇の水路 (光海): 5×5 の水の通路でできた迷路 (15×21 マス)。毎回形が変わり、息継ぎのくぼみがどのマスからも 3 マス以内に、灯の台が 3 つある
public final class TitanBackLayout {
    // 雲の海の高さの基準 (雲は BASE_Y - 55 = 45、BASE_Y - 50 より下に落ちたら地上へ)。鯨の腹の下端は y 58
    public static final int BASE_Y = 100;
    // 鯨の全体を包む範囲 (x -RX〜RX、z -RZ〜RZ)。番兵の掃除・看板の数え・地図に使う
    public static final int RX = 180;
    public static final int RZ = 416;
    public static final int FLOOR = 103;

    // 倍率 (版 13)。設計の単位 (版 12 の座標) を水平 x1.6・垂直 x1.3 にする。腹の下端 (y 58) を動かさず、旧 y 95 (体の中心) を y 106 へ
    public static final BackScale SCALE = new BackScale(1.6, 1.3, 95, 106);

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
    // 12: 詰み監査の穴埋め (背の外の凹み) / 13: 建築の監査 (ベンチの向き) / 13': ボス部屋の大門 (BossGates) / 14: 大門と建築の監査の両方
    // 15: 水平 x1.6・垂直 x1.3 に作り直した (設計書 26)。部屋 x2.6、広間 x3.6、心臓の間 x1.3。通路は幅 5〜7・高さ 6。第二の広間・番兵の詰所・
    //     数え歌の鐘楼・骨の書庫・歌の祠・胸びれの道・尾の展望・闇の水路 15×21 (入口 2 つ)・グループごとの幕を足した。体の bin は深い内側を 1 種類にした。
    //     ボス部屋の大門は、広げた入口 (心臓の間・番兵長の広間) に合わせて建てる (BossGates)
    // 16: 光海の作り直し (23 の 11 章。灯の台 3 つ・息継ぎのくぼみを 3 マス以内・近道・水域の色・広い入口の小部屋)
    // 17: 15 と 16 の両方 (背の拡大の迷路 15×21 マス・1 マス 5×5 の上に、光海の導きをのせた。入口の小部屋は 21×14)
    // 18: 東の翼を一本道に (E1 → CE1 → E2 → 前室 → 詰所 → 割符の戸 → E3。戻らない)・少人数の仕切り (幕を段にした)・宝物庫を無くして封印の回廊に・
    //     西の翼のアスレチックを筋の通った道に作り直し (中ほどの休み場)・鐘楼の作り直し・胸びれの先の碑と宝・ゾーンごとの材質 (ZonePalette)・
    //     鎖・くぼみの修正・灯り (設計書 26 の 8.6)
    // 19: 試練の階 (WhaleDeck。入口の通路の東の壁から 16 段上る階段・広間・鐘楼 W02・骨の楽譜 W06・三つの水門 W07・骨の砲手の砦 W10・試練の板・
    //     鼓動の間の試練の碑。原型の三つの入口の脇に戸。docs/design/29)
    // 20: 歩く道の灯り (RouteLamps。下の迷路を除く歩ける所の暗い所に海のランタンを壁へ埋める。心臓の間の仕掛けの置き場の修正)
    // 21: 試練の階の南の拡張 (P2。細い通路 C2・最南の広間 S1・苔の滑り坂 W03・噴気孔の昇り W04・棺の護送 W11・鯨骨の闘技場 W12。docs/design/29 の 3.1)
    // 24: 影絵の間 W08 (P3。最南の広間 S1 の北の壁の戸から北へ。docs/design/29 の 3.7)。他の背の版の最大 (余燼 23) の次
    // 25: 試遊の声 2 回目 (四体の最大 24 の次): 東の翼の戦いを 4 → 3 (第二の広間 E2 は戦わない射手の回廊に・幅 7)・西の翼の谷の途中の休み場 (島 wa1 wa2 wb1 wc1) をなくした・
    //     鼓動の間 と 試練の階の光る床 (HUB_PAD・D0_PAD)・案内の碑 (WhaleDeck.SIGNS)・歌の祠をなくした・鼓動の間の碑を弧と台座に。docs/design/26 の 8.8
    // 27: 闇の水路の休み場を最低 5×5 の歩ける床に (入口の小部屋 2 つめを 5×5 → 7×7・休み場の火は休み場の遺物に・中ほどの息継ぎのくぼみを 5×5 の足場に)。
    //     建て直しは、前の版の部屋や小部屋が残らないよう、体の範囲の外の鯨の外周も空にしてから建てる (TitanBackBuilder.clearStale)
    // 28: 試練の流れ (石を無くしてゴールの印に・紫の抜け道の戸を壁に・今週の試練の入口。docs/design/26 の 8.8、29 の 4.6・4.7)。四体とも 28
    // 29: 闇の水路の中ほどの休み場の足場に、つながる通路の口から真ん中の穴までの水路 (幅 1・高さ 2) を抜いた (足場が口をふさぎ、泳いで通れず道が途切れていた)
    // 30: 任意の翼をなくした (骨の書庫・胸びれの道・尾の展望・宿・胸びれの先の宝箱)。来た道へ戻る抜け道の戸と、試練の入口の看板、台の共鳴の炉をなくした
    // 31: 周回の前の谷 (32 の 4 章、R5): 西の前室 W0 の南に、前の道の休み場 wp (WP0)・前の谷 WP (崩れる骨と飛び石と島二つ)・W0 の南の壁へ上る通路 WPC と戸。
    //     周回のグループだけが wp へ運ばれ、前の谷を越えて W0 へ入る (trial/LongWalk)
    // 32: 封じた鼓動の間・入口の通路・体内への階段の飾りと灯りを外した (trial/RouteSeals。2026-10-22「誰も見ない豪華な建築は省いて」)
    // 33: 心臓の間を体の外 (区画 0 番の枠の封じた部屋。trial/raid/InstanceArenas) へ移した
    // 34: 巨神の中は建てない (外見だけ。体・背の上の飾り・到着の台と、体の外の心臓の間)。2026-10-23
    public static final int LAYOUT_VERSION = 34;

    // ---------------------------------------------------------------- 地表
    // 尾の付け根の背の広場 (到着) と、背の中ほどの祠。祠の階段は 1 段ごとに 1 下がり、入口の通路の南の端 (z 15) で床に着く
    public static final int PLATFORM_Z = 160;
    public static final int SHRINE_Z = 62;
    // 広場と祠のあいだの、背の石の門 (モデルの半ば埋まったアーチ、z 77〜96)
    public static final int RIDGE_Z = 85;
    // モデルの錨石 (左のわき腹の留め具、x -61〜-43、y 71〜107、z 170〜183)。鎖はここから西へ張り出し (x = ANCHOR_CHAIN_X)、雲の海へ垂れる
    public static final int ANCHOR_STONE_X = -61;
    public static final int ANCHOR_CHAIN_X = -77;
    public static final int ANCHOR_CHAIN_Y = 102;
    public static final int ANCHOR_CHAIN_Z = 176;
    // 左のわき腹の台 (到着): 錨石 (モデルの鉄の留め具) の外に張り出した石の台。床の高さ・範囲 (x は西の端から体の面まで、z)。
    // 台の北の端から、体の中を東へ登る階段が、背の広場 (尾の付け根) の石畳へ出る
    public static final int FLANK_FLOOR_Y = 97;
    public static final int FLANK_X0 = -75;
    public static final int FLANK_X1 = -50;
    public static final int FLANK_Z0 = 160;
    public static final int FLANK_Z1 = 186;
    // 到着の向き (東 = 体のほう。錨石・炉・登り口の階段が見える)
    public static final float ARRIVAL_YAW = 270f;
    // 登り口の階段: x = STAIR_X0 から東へ、1 ブロックごとに 1 上がる。幅 5 (z STAIR_Z0〜STAIR_Z1)、高さ 6
    public static final int STAIR_X0 = -54;
    public static final int STAIR_Z0 = 162;
    public static final int STAIR_Z1 = 166;

    // ピースフルのあいだ祠の階段を塞ぐ封印の z (階段の 6 段目。ここまで階段の頭が祠の床を割って空へ開くので、この面で閉じる)。これより北の体内は試練の場
    public static final int SHRINE_SEAL_Z = SHRINE_Z - 6;

    // ---------------------------------------------------------------- 中央
    public static final Box HUB = new Box(-18, 18, -55, -15, FLOOR, 16);
    // 入口の通路: 祠の階段の足もと (z 15) から鼓動の間まで
    public static final Box ENTRY = new Box(-3, 3, -14, 15, FLOOR, 6);
    // 封印 (三つの石で開く)。鼓動の間の北の壁 (z -56)。開くとそのまま心臓の回廊 (版 18: 天の鍵の欠片は心臓から受け取るので、宝物庫の部屋は無くした)
    public static final List<BlockPos> GATE = wall(-3, 3, FLOOR + 1, FLOOR + 6, -56, true);
    // 鼓動の間の中央の台座 (眠る心臓)
    public static final int HUB_CX = 0;
    public static final int HUB_CZ = -35;

    // ---------------------------------------------------------------- 心臓の間 (最終ボス。体の外の封じた部屋)
    // 2026-10-23: 心臓の間は体の中から、背の次元の区画 0 番の枠 (trial/raid/InstanceArenas) の封じた一室へ移した。形と戦いは元のまま、
    // 元の座標 (中心 (0, 104, -136)) を HEART_DX / HEART_DZ だけずらす。順路の段 4 では、運ばれた人は部屋の中へ直に着く。
    // 部屋の南に、倒れた人が目を覚ます小さな前室 (休み場 heart) と大門が付く (戦いの途中に扉から戻る。再入場)
    public static final int HEART_DX = jp.revati.skyabyss.trial.raid.InstanceArenas.HEART_X;
    public static final int HEART_DZ = jp.revati.skyabyss.trial.raid.InstanceArenas.HEART_Z + 136;
    // 大門から休み場までの短い通路 (x -3〜3)
    public static final Box HEART_CORRIDOR = new Box(-3 + HEART_DX, 3 + HEART_DX, -108 + HEART_DZ, -88 + HEART_DZ, FLOOR, 6);
    public static final Box HEART_REST = new Box(-6 + HEART_DX, 6 + HEART_DX, -100 + HEART_DZ, -88 + HEART_DZ, FLOOR, 7);
    // 心臓の間: 床 y=103、天井 y=120。内側の半径 25、壁の外側 27 (入口の壁が円の南の端)
    public static final int HEART_CX = HEART_DX;
    public static final int HEART_CZ = -136 + HEART_DZ;
    public static final Box HEART = new Box(-27 + HEART_DX, 27 + HEART_DX, -163 + HEART_DZ, -109 + HEART_DZ, FLOOR, 16);
    // 心臓の間の入口 (戦いが始まると閉じる)。z=HEART_GATE_Z の壁
    public static final int HEART_GATE_Z = -109 + HEART_DZ;
    public static final List<BlockPos> HEART_GATE = wall(-3 + HEART_DX, 3 + HEART_DX, FLOOR + 1, FLOOR + 6, HEART_GATE_Z, true);
    // 版 24: 歌の祠 (休み場 heart の西の小部屋。看板だけで用がなかった) はなくした

    // ---------------------------------------------------------------- 西の翼 (アスレチック)
    public static final Box W0 = new Box(-35, -26, -42, -29, FLOOR, 8);
    public static final Box WA = new Box(-57, -37, -54, -19, FLOOR, 15);
    public static final Box W1 = new Box(-54, -45, -67, -56, FLOOR + 5, 7);
    public static final Box WB = new Box(-55, -38, -90, -69, FLOOR, 15);
    public static final Box W2 = new Box(-47, -36, -101, -92, FLOOR + 9, 6);
    public static final Box WC = new Box(-35, -20, -85, -49, FLOOR, 15);
    // 鼓動の間 → 西の前室 / 西の前室 → 肋骨の谷 (つなぎの通路は壁の厚さの分を含む)
    public static final Box WEST_HALL = new Box(-25, -19, -38, -34, FLOOR, 6);
    public static final int FALL_LINE = FLOOR - 4;
    // ---- 前の谷 (周回の長い裂け目の回廊。版 31。trial/LongWalk): 休み場 WP0 → 前の谷 WP (南東から北西へ四つの脚) → 通路 WPC → W0 の南の壁の戸
    public static final Box WP0 = new Box(-24, -15, 18, 29, FLOOR, 8);
    public static final Box WP = new Box(-40, -12, -10, 16, FLOOR, 15);
    public static final List<Box> PITS = List.of(WA, WB, WC, WP);
    public static final int WEST_LEDGE_Y = FLOOR + 5;

    // ---------------------------------------------------------------- 東の翼 (戦闘。版 18: 一本道。戻らずに先へ進む)
    // 東の前室 E0 → 第一の広間 E1 → (北の戸) 控えの間 CE1 (休み場) → 射手の回廊 E2 (通路。戦わない。版 25) → 前室 CK_PASS (休み場) → 番兵の詰所 CK (老番兵)
    //   → (西の戸 = 割符の戸) 通路 E3_CORRIDOR (休み場) → 番兵長の広間 E3 (共鳴石) → 鼓動の間へ戻る扉
    // どの戸も、手前の広間を鎮めたあとにだけ開く (道の途中で二つの戸が同時に開くことはない)。戦いの広間の入口は、戦いの間だけ閉じる
    public static final Box E0 = new Box(26, 32, -42, -29, FLOOR, 8);
    public static final Box E1 = new Box(34, 54, -54, -8, FLOOR, 11);
    public static final Box CE1 = new Box(41, 53, -68, -56, FLOOR, 7);
    public static final Box CK_PASS = new Box(42, 54, -99, -94, FLOOR, 6);
    public static final Box CK = new Box(41, 55, -118, -101, FLOOR, 8);
    public static final Box E3_CORRIDOR = new Box(34, 39, -105, -101, FLOOR, 6);
    public static final Box E3 = new Box(20, 32, -106, -52, FLOOR, 11);
    public static final List<BlockPos> E1_ENTRANCE = wall(-38, -34, FLOOR + 1, FLOOR + 6, 33, false);
    public static final List<BlockPos> E1_EXIT = wall(42, 46, FLOOR + 1, FLOOR + 6, -55, true);
    // 番兵長の広間への戸: 初回は老番兵が持つ「割符」が鍵 (詰所を制したグループにだけ開く)。詰所の西の壁 (x 40)
    public static final List<BlockPos> E3_LOCK = wall(-105, -101, FLOOR + 1, FLOOR + 6, 40, false);
    public static final List<BlockPos> E3_ENTRANCE = wall(-105, -101, FLOOR + 1, FLOOR + 6, 33, false);
    // 東の前室 → 第一の広間 のつなぎ / 鼓動の間 → 東の前室
    public static final Box EAST_HALL = new Box(19, 25, -38, -34, FLOOR, 6);

    // ---------------------------------------------------------------- 闇の水路
    // 15×21 マス、1マス 5×5 の水、壁の厚さ1。x -45〜45, z -122〜4、床 y 84 (水は y 85〜87、天井 y 88)
    public static final int MAZE_X0 = -45;
    public static final int MAZE_Z0 = -122;
    public static final int MAZE_W = 15;
    public static final int MAZE_H = 21;
    public static final int MAZE_CELL = 5;
    public static final int MAZE_FLOOR = FLOOR - 19;
    public static final int MAZE_START2_I = 5;
    public static final int MAZE_START2_J = 15;
    // 入口のはしごを下りた先の、乾いた小部屋 (迷路の天井の上。床は迷路の天井の石、はしごが北の辺を通る)
    // 1 つめは 21×14×4 の広い休み場 (6 人が焚き火を囲み、苗床・数え石・掲示板の前に立てる。はしごの穴は北の辺の x 6)。2 つめは 7×7×4 (はしごの穴は北の辺の真ん中)
    public static final Box MAZE_ENTRY_ROOM = new Box(-4, 16, -49, -36, MAZE_FLOOR + 4, 4);
    public static final Box MAZE_ENTRY_ROOM2 = new Box(mazeCellX(MAZE_START2_I) - 1, mazeCellX(MAZE_START2_I) + 5, mazeCellZ(MAZE_START2_J), mazeCellZ(MAZE_START2_J) + 6, MAZE_FLOOR + 4, 4);
    public static final Box MAZE_EXIT_ROOM = new Box(21, 27, -7, -3, MAZE_FLOOR + 4, 5);

    // ---------------------------------------------------------------- 共鳴石 (0 = 西, 1 = 東, 2 = 下)
    public static final List<BlockPos> GOAL_SPOTS = List.of(
            new BlockPos(-23, WEST_LEDGE_Y + 1, -50), new BlockPos(25, FLOOR + 1, -53), new BlockPos(26, MAZE_FLOOR + 5, -5));

    // ---------------------------------------------------------------- 看板 (心臓の間の入口の一言)
    // facing は看板の向く側。看板は反対側の壁に掛かる
    public record Inscription(String key, BlockPos pos, net.minecraft.core.Direction facing) {
    }

    // 心臓の間の入口 (最後の試練)
    public static final Inscription HEART_GATE_SIGN =
            new Inscription("sign.skyabyss.heart_gate", new BlockPos(4 + HEART_DX, FLOOR + 3, -107 + HEART_DZ), net.minecraft.core.Direction.WEST);

    private TitanBackLayout() {
    }

    // 迷路のマス (i, j) の x / z の南西 (最小) の角
    public static int mazeCellX(int i) {
        return MAZE_X0 + 1 + i * (MAZE_CELL + 1);
    }

    public static int mazeCellZ(int j) {
        return MAZE_Z0 + 1 + j * (MAZE_CELL + 1);
    }

    // 到着の場所: 左のわき腹の台。乗り移ってきた人はここに立つ (向きは ARRIVAL_YAW)
    public static BlockPos arrival() {
        return new BlockPos(-66, FLANK_FLOOR_Y + 1, 173);
    }

    // 尾の付け根の広場 (背の上)。台から階段を登った先。倒れたとき・あきらめたとき・光の柱の行き先 (記録がなければ)
    public static BlockPos plaza() {
        return new BlockPos(0, platformY() + 1, PLATFORM_Z - 3);
    }

    // 錨石 (台の上、モデルの留め具の外の面に寄せた石の磁石)。右クリックすると地上へ降りる
    public static BlockPos anchor() {
        return new BlockPos(ANCHOR_STONE_X + 1, FLANK_FLOOR_Y + 1, ANCHOR_CHAIN_Z);
    }

    // 広場の石畳の高さ: 広場 (x -12〜12、z ±16) の背のいちばん高い所。背の丸みに沈まず、北の背を見渡せる
    private static int platformY = Integer.MIN_VALUE;

    public static int platformY() {
        if (platformY == Integer.MIN_VALUE) {
            // 広場の円 (rx 11・rz 14) の中の背の高さの 65 番目の百分位。小さな背びれの出っ張りに引きずられて、広場が高い台にならない
            // (出っ張りは広場の空きで削る)
            java.util.List<Integer> tops = new java.util.ArrayList<>();
            for (int x = -11; x <= 11; x++) {
                for (int z = PLATFORM_Z - 14; z <= PLATFORM_Z + 14; z++) {
                    if (Math.hypot(x / 11.0, (z - PLATFORM_Z) / 14.0) <= 1.0) {
                        tops.add(top(x, z));
                    }
                }
            }
            java.util.Collections.sort(tops);
            platformY = Math.max(BASE_Y, tops.get((int) (tops.size() * 0.65)));
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

    // 戸口の位置の一覧。alongX = true なら z が一定で x が a〜b、false なら x が一定で z が a〜b
    static List<BlockPos> wall(int a, int b, int y1, int y2, int fixed, boolean alongX) {
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

        // 床の面積 (m²)
        public int area() {
            return (x2 - x1 + 1) * (z2 - z1 + 1);
        }
    }
}
