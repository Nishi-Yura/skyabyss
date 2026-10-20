package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.FLOOR;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import jp.revati.skyabyss.trial.TrialCell.Rest;
import net.minecraft.core.BlockPos;

// 岩鯨の背の「試練の階」(docs/design/29 の 2.4、26 の体の空き)。鼓動の間の入口の通路の東の壁から、階段で 16 段上がった体の中の階 (床 y 119)。
// 下の東の翼 (第二の広間・詰所) の天井 (y 116) の上、祠の階段の東にある「詰まった石」の空きに、セル (試練の部屋) を並べる。
//
//   入口の通路 ── 階段 (x 5〜21、z 8〜12。16 段) ── 試練の階の広間 D0 (休み場・板) ── 西の戸 → 鐘楼の綱渡り W02 (縦の穴)
//                                                         └ 南の通路 C1 (戸 → 骨の楽譜 W06) ── 南の広間 S0 (休み場)
//                                                                                         ├ 戸 → 三つの水門 W07
//                                                                                         ├ 戸 → 骨の砲手の砦 W10
//                                                                                         └ 細い通路 C2 (W07 と W10 のあいだ) ── 最南の広間 S1 (休み場)
//                                                                                              ├ 戸 → 苔の滑り坂 W03 (段々に下る流れの床)
//                                                                                              ├ 戸 → 噴気孔の昇り W04 (縦の穴。吹き上げで昇る)
//                                                                                              ├ 戸 → 棺の護送 W11 (広間。棺を押して祭壇へ)
//                                                                                              └ 戸 → 鯨骨の闘技場 W12 (円い闘技場。波ごとに床が崩れる)
//
// 原型の三つ (西の翼・東の翼・闇の水路) はそのまま。引かなかったグループには、鼓動の間の脇の戸 (WEST_GATE・EAST_GATE・MAZE_LIDS) が閉じる。
public final class WhaleDeck {
    private WhaleDeck() {
    }

    // 岩鯨の背に試練の階を置くか。版 19 から本番でも置く (既定 true)。自己テストが「置かない姿」(原型の三つだけ) を確かめるときだけ false にする
    // (開発用: 環境変数 SKYABYSS_TRIAL_DECK=0 で置かない姿にして比べられる)
    private static boolean placed = !"0".equals(System.getenv("SKYABYSS_TRIAL_DECK"));

    public static boolean placed() {
        return placed;
    }

    public static void setPlaced(boolean value) {
        placed = value;
    }

    // 試練の階の床ブロックの高さ。足は Y + 1
    public static final int Y = 119;

    // ---------------------------------------------------------------- 階段と広間・通路
    // 階段: x 5〜20 は東向きの階段 (段のブロックの高さ = stairY(x))、x 21 は広間 D0 に面した平らな踊り場 (床ブロック y 119)。幅 5 (z 8〜12)
    public static final int STAIR_X0 = 5;
    public static final int STAIR_X1 = 20;
    public static final int LAND_X = 21;
    public static final int STAIR_Z0 = 8;
    public static final int STAIR_Z1 = 12;

    public static int stairY(int x) {
        return 99 + x;
    }

    // 階段の穴 (走査・形の検査用の箱。床は鼓動の間の床)
    public static final Box STAIR = new Box(STAIR_X0, LAND_X, STAIR_Z0, STAIR_Z1, FLOOR, 22);
    // 入口の通路の東の壁の戸口 (x 4)
    public static final int STAIR_DOOR_X = 4;

    public static final Box D0 = new Box(22, 44, 4, 24, Y, 9);
    // 鐘楼の綱渡り: 縦の穴。床 y 112、足場 (岩棚・梁) の高さは Y
    public static final Box W02 = new Box(7, 20, 20, 66, Y - 7, 21);
    // 広間 D0 の南の戸口から S0 へ下る通路
    public static final Box C1 = new Box(40, 44, 26, 66, Y, 6);
    public static final Box W06 = new Box(24, 38, 27, 42, Y, 8);
    public static final Box S0 = new Box(-22, 46, 68, 80, Y, 9);
    public static final Box W07 = new Box(-20, 8, 83, 111, Y, 10);
    public static final Box W10 = new Box(14, 46, 83, 113, Y, 10);

    // ---------------------------------------------------------------- 版 21: 最南の広間と新しい四つのセル (P2。docs/design/29 の 3.1)
    // W07 と W10 の壁のあいだの細い通路 (x 10〜12) を S0 の南の壁から南へ。その先の最南の広間 S1 から、四つのセルへ戸が並ぶ
    public static final Box C2 = new Box(10, 12, 81, 117, Y, 5);
    public static final Box S1 = new Box(-44, 36, 118, 126, Y, 9);
    // W03 苔の滑り坂: 段々に下る (床 119 → 114)。区画 = {z1, z2, 床}。流れの床 (苔) 5 本・島の列 3 本・休み場の広間 2 つ
    public static final int W03_X1 = -46;
    public static final int W03_X2 = -34;
    // (z 160 より南は、到着の登り口の階段 (x -54 から東へ、z 162〜166) が通る)
    public static final int[][] W03_SEGS = {{129, 131, 119}, {132, 134, 119}, {135, 136, 118}, {137, 139, 118}, {140, 141, 117}, {142, 144, 117},
            {145, 147, 116}, {148, 150, 116}, {151, 152, 115}, {153, 155, 115}, {156, 159, 114}};
    public static final Box W03 = new Box(W03_X1, W03_X2, 129, 159, 114, 14);
    // W04 噴気孔の昇り: 縦の穴 (床 107、天井 132)。入口の岩棚 (北、y 119) から飛び降り、吹き上げで 111・115・119 の岩棚へ昇って、南の出口の岩棚へ
    public static final Box W04 = new Box(-30, -10, 129, 150, 107, 24);
    public static final Box W04_STONE_ROOM = new Box(-24, -16, 153, 158, Y, 6);
    // W11 棺の護送: 広間 (x -6〜10、z 129〜165) と、石の小部屋 (z 168〜172)
    public static final Box W11 = new Box(-6, 10, 129, 165, Y, 8);
    public static final Box W11_STONE_ROOM = new Box(-1, 5, 168, 172, Y, 6);
    // W12 鯨骨の闘技場: 入口の控え (z 129〜135)・円い闘技場 (中心 (24, 148)、床の半径 10)・出口の控え (z 161〜166)
    public static final Box W12_FOYER_N = new Box(14, 34, 129, 135, Y, 9);
    public static final int W12_CX = 24;
    public static final int W12_CZ = 148;
    public static final Box W12_FOYER_S = new Box(18, 28, 161, 166, Y, 9);
    public static final Box W12 = new Box(13, 35, 129, 166, Y - 7, 16);

    // 版 24: 影絵の間 W08 (docs/design/29 の 3.7)。最南の広間 S1 の北の壁 (z 116・117) の戸から北へ、W07 の西の体の中。出口 (石の前の戸) は東の壁
    public static final Box W08 = new Box(-48, -32, 98, 115, Y, 10);
    public static final Box W08_STONE_ROOM = new Box(-29, -25, 104, 110, Y, 6);
    public static final Box W08_REST = new Box(-48, -32, 112, 115, Y, 9);

    public static final List<Box> ROOMS = List.of(STAIR, D0, W02, C1, W06, S0, W07, W10, C2, S1, W03, W04, W04_STONE_ROOM, W11, W11_STONE_ROOM, W12, W08, W08_STONE_ROOM);

    // ---------------------------------------------------------------- 戸 (封印の扉。グループごとに開け閉め)
    // 戸の高さは 5 (足の高さ Y + 1 〜 Y + 5)
    private static final int G0 = Y + 1;
    private static final int G1 = Y + 5;

    // 鐘楼 W02 の入口: D0 の西の壁 (x 21、z 20〜24)
    public static final List<BlockPos> W02_GATE = TitanBackLayout.wall(20, 24, G0, G1, 21, false);
    // 骨の楽譜 W06 の入口: C1 の西の壁 (x 39、z 32〜36)。出口 (石の前の戸): 部屋の南西の小部屋 (x 28、z 38〜42)
    public static final List<BlockPos> W06_GATE = TitanBackLayout.wall(32, 36, G0, G1, 39, false);
    public static final List<BlockPos> W06_EXIT = TitanBackLayout.wall(38, 42, G0, G1, 28, false);
    // 三つの水門 W07 の入口: S0 の南の壁 2 枚 (z 81・82、x -8〜-4)。出口: 小部屋の戸 (z 108)
    public static final List<BlockPos> W07_GATE = concat(TitanBackLayout.wall(-8, -4, G0, G1, 81, true), TitanBackLayout.wall(-8, -4, G0, G1, 82, true));
    public static final List<BlockPos> W07_EXIT = TitanBackLayout.wall(-8, -4, G0, G1, 108, true);
    // 骨の砲手の砦 W10 の入口 (z 81・82、x 28〜32)。出口 (z 111)
    public static final List<BlockPos> W10_GATE = concat(TitanBackLayout.wall(28, 32, G0, G1, 81, true), TitanBackLayout.wall(28, 32, G0, G1, 82, true));
    public static final List<BlockPos> W10_EXIT = TitanBackLayout.wall(28, 32, G0, G1, 111, true);
    // 最南の広間 S1 の南の壁 (z 127・128) の四つの戸。入口は 2 枚の壁
    public static final List<BlockPos> W03_GATE = concat(TitanBackLayout.wall(-42, -38, G0, G1, 127, true), TitanBackLayout.wall(-42, -38, G0, G1, 128, true));
    public static final List<BlockPos> W04_GATE = concat(TitanBackLayout.wall(-22, -18, G0, G1, 127, true), TitanBackLayout.wall(-22, -18, G0, G1, 128, true));
    public static final List<BlockPos> W04_EXIT = concat(TitanBackLayout.wall(-22, -18, G0, G1, 151, true), TitanBackLayout.wall(-22, -18, G0, G1, 152, true));
    public static final List<BlockPos> W11_GATE = concat(TitanBackLayout.wall(0, 4, G0, G1, 127, true), TitanBackLayout.wall(0, 4, G0, G1, 128, true));
    public static final List<BlockPos> W11_EXIT = concat(TitanBackLayout.wall(0, 4, G0, G1, 166, true), TitanBackLayout.wall(0, 4, G0, G1, 167, true));
    public static final List<BlockPos> W12_GATE = concat(TitanBackLayout.wall(22, 26, G0, G1, 127, true), TitanBackLayout.wall(22, 26, G0, G1, 128, true));
    // 影絵の間 W08: 入口は S1 の北の壁 (z 116・117、x -42〜-38)。出口は東の壁 (x -31・-30、z 104〜108)
    public static final List<BlockPos> W08_GATE = concat(TitanBackLayout.wall(-42, -38, G0, G1, 116, true), TitanBackLayout.wall(-42, -38, G0, G1, 117, true));
    public static final List<BlockPos> W08_EXIT = concat(TitanBackLayout.wall(104, 108, G0, G1, -31, false), TitanBackLayout.wall(104, 108, G0, G1, -30, false));

    // ---------------------------------------------------------------- 原型の三つの入口の戸 (引かなかったグループに閉じる。既定は開いている)
    // 鼓動の間 → 西の前室 (x 22 の線は西の前室の中ほど。x -25〜-19)、東の前室 (x 19〜25)
    public static final List<BlockPos> WEST_GATE = TitanBackLayout.wall(-38, -34, FLOOR + 1, FLOOR + 6, -22, false);
    public static final List<BlockPos> EAST_GATE = TitanBackLayout.wall(-38, -34, FLOOR + 1, FLOOR + 6, 22, false);

    // 闇の水路の入口のはしごの蓋 (床の穴の上の 1 マス。2 か所)
    public static List<BlockPos> mazeLids() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos hatch : List.of(TitanBackLayout.mazeCell(TitanBackLayout.MAZE_START_I, TitanBackLayout.MAZE_START_J),
                TitanBackLayout.mazeCell(TitanBackLayout.MAZE_START2_I, TitanBackLayout.MAZE_START2_J))) {
            out.add(new BlockPos(hatch.getX() + TitanBackLayout.MAZE_CELL / 2, FLOOR + 1, hatch.getZ()));
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- 石の差し口・戻りの光る床
    public static final BlockPos W02_STONE = new BlockPos(13, Y + 1, 65);
    public static final BlockPos W02_PAD = new BlockPos(17, Y, 65);
    public static final BlockPos W06_STONE = new BlockPos(25, Y + 1, 40);
    public static final BlockPos W06_PAD = new BlockPos(27, Y, 40);
    public static final BlockPos W07_STONE = new BlockPos(-6, Y + 1, 110);
    public static final BlockPos W07_PAD = new BlockPos(-6, Y, 111);
    public static final BlockPos W10_STONE = new BlockPos(30, Y + 1, 112);
    public static final BlockPos W10_PAD = new BlockPos(30, Y, 113);
    public static final BlockPos W03_STONE = new BlockPos(-42, 115, 158);
    public static final BlockPos W03_PAD = new BlockPos(-38, 114, 158);
    public static final BlockPos W04_STONE = new BlockPos(-22, Y + 1, 157);
    public static final BlockPos W04_PAD = new BlockPos(-18, Y, 157);
    public static final BlockPos W11_STONE = new BlockPos(0, Y + 1, 171);
    public static final BlockPos W11_PAD = new BlockPos(4, Y, 171);
    public static final BlockPos W12_STONE = new BlockPos(22, Y + 1, 164);
    public static final BlockPos W12_PAD = new BlockPos(26, Y, 164);
    public static final BlockPos W08_STONE = new BlockPos(-27, Y + 1, 107);
    public static final BlockPos W08_PAD = new BlockPos(-27, Y, 109);

    // 試練の板 (鼓動の間の南の壁ぎわ・試練の階の広間の北の壁ぎわ)
    public static final BlockPos BOARD_HUB = new BlockPos(-8, FLOOR + 1, -15);
    public static final BlockPos BOARD_D0 = new BlockPos(26, Y + 1, 4);

    // ---------------------------------------------------------------- 休み場
    public static final String REST_D0 = "d0";
    public static final String REST_S0 = "s0";
    public static final String REST_W02_A = "w02a";
    public static final String REST_W02_M = "w02m";
    public static final String REST_W02_C = "w02c";
    public static final String REST_W10 = "w10a";
    public static final String REST_S1 = "s1";
    public static final String REST_W03_A = "w03a";
    public static final String REST_W03_M = "w03m";
    public static final String REST_W04 = "w04a";
    public static final String REST_W11 = "w11a";
    public static final String REST_W12 = "w12a";
    public static final String REST_W08 = "w08a";

    // 鐘楼の足場 (岩棚 A・中ほどの台 M・向こうの岩棚 C)
    public static final Box W02_LEDGE_A = new Box(7, 20, 20, 26, Y, 8);
    public static final Box W02_MID = new Box(11, 15, 42, 46, Y, 6);
    public static final Box W02_LEDGE_C = new Box(7, 20, 62, 66, Y, 8);
    public static final Box W10_REST = new Box(15, 21, 84, 90, Y, 8);
    // 新しい四つのセルの休み場
    public static final Box W03_REST_A = new Box(W03_X1, W03_X2, 129, 131, Y, 9);
    public static final Box W03_REST_M = new Box(W03_X1, W03_X2, 145, 147, 116, 9);
    public static final Box W04_BOTTOM = new Box(-30, -10, 132, 147, 107, 3);
    public static final Box W11_REST = new Box(-6, 10, 129, 132, Y, 8);

    public static List<Rest> rests() {
        return List.of(
                new Rest(REST_D0, D0, D0.center().offset(2, 0, 2)),
                new Rest(REST_S0, S0, S0.center().offset(2, 0, 2)),
                new Rest(REST_W02_A, W02_LEDGE_A, new BlockPos(16, Y + 1, 24)),
                new Rest(REST_W02_M, W02_MID, new BlockPos(14, Y + 1, 45)),
                new Rest(REST_W02_C, W02_LEDGE_C, new BlockPos(16, Y + 1, 63)),
                new Rest(REST_W10, W10_REST, new BlockPos(20, Y + 1, 89)),
                new Rest(REST_S1, S1, S1.center().offset(2, 0, 2)),
                new Rest(REST_W03_A, W03_REST_A, new BlockPos(-38, Y + 1, 131)),
                new Rest(REST_W03_M, W03_REST_M, new BlockPos(-38, 117, 147)),
                new Rest(REST_W04, W04_BOTTOM, new BlockPos(-24, 108, 134)),
                new Rest(REST_W11, W11_REST, new BlockPos(7, Y + 1, 132)),
                new Rest(REST_W12, W12_FOYER_N, new BlockPos(29, Y + 1, 133)),
                new Rest(REST_W08, W08_REST, new BlockPos(-40, Y + 1, 113)));
    }

    // 休み場の焚き火の位置 (建てるとき)。広間は箱の中心、鐘楼の岩棚・砦の隅は決めた位置
    public static BlockPos campfire(String id) {
        return switch (id) {
            case REST_D0 -> D0.center();
            case REST_S0 -> S0.center();
            case REST_W02_A -> new BlockPos(13, Y + 1, 23);
            case REST_W02_M -> W02_MID.center();
            case REST_W02_C -> new BlockPos(11, Y + 1, 64);
            case REST_W10 -> W10_REST.center();
            case REST_S1 -> S1.center();
            case REST_W03_A -> new BlockPos(-40, Y + 1, 130);
            case REST_W03_M -> new BlockPos(-40, 117, 146);
            case REST_W04 -> new BlockPos(-25, 108, 133);
            case REST_W11 -> new BlockPos(8, Y + 1, 131);
            case REST_W12 -> new BlockPos(30, Y + 1, 132);
            case REST_W08 -> new BlockPos(-44, Y + 1, 113);
            default -> throw new IllegalArgumentException(id);
        };
    }

    // 全部の戸 (原型の入口の戸は別。新作の入口と出口)
    public static List<List<BlockPos>> closedGates() {
        return List.of(W02_GATE, W06_GATE, W06_EXIT, W07_GATE, W07_EXIT, W10_GATE, W10_EXIT, W03_GATE, W04_GATE, W04_EXIT, W11_GATE, W11_EXIT, W12_GATE, W08_GATE, W08_EXIT);
    }

    public static List<List<BlockPos>> legacyGates() {
        return List.of(WEST_GATE, EAST_GATE, mazeLids());
    }

    private static List<BlockPos> concat(List<BlockPos> a, List<BlockPos> b) {
        List<BlockPos> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }
}
