package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.tide.back.TideBackLayout.Area;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout.Rest;
import net.minecraft.core.BlockPos;

// 潮殻の背の「試練の階」(docs/design/29 の 2.4・3.2。岩鯨の WhaleDeck の潮殻版)。潮の広間の南の段 (LEDGE_S) の戸口から、細い通路 TC を南へ、試練の広間 H。
// H の南の壁に四つの戸が並び、引いた三つだけがそのグループに開く:
//
//   潮の広間の南の段 ── 通路 TC (x -5〜-3、z 27〜37) ── 試練の広間 H (x -8〜90、z 38〜46。休み場・板)
//        ├ 戸 → 潮の背渡り T02 (x 2〜22、z 49〜92。干潟の入江を砂州・浮き石で渡る)
//        ├ 戸 → 満ち潮の円環 T10 (x 28〜52、z 49〜86。円い闘技場)
//        ├ 戸 → 貝の合唱 T06 (x 56〜68、z 49〜64 + 石の小部屋)
//        └ 戸 → 二つの海 T07 (x 72〜88、z 49〜62 + 石の小部屋)
//
// 原型の三つ (西の庭・東の潮見の間・前の干潟) はそのまま。引かなかったグループには、原型の入口の戸が閉じる (TrialRuntime が扱う。ここは新作の戸)。
// 床の高さは広間の南の段と同じ 74 (人が立つのは 75)。入江 T02 だけ、水の底 (70)・水 (71〜73) を掘る。
// 位置は、背の甲羅 (turtle.bin) の体の中の空き (x 0〜100、z 27〜100) を使う。ほかの部屋と重ならず、体の中に収まることを自己テストが調べる。
public final class TideDeck {
    private TideDeck() {
    }

    // 版 21 から本番でも置く (既定 true)。自己テストが「置かない姿」(原型の三つだけ) を確かめるときだけ false にする
    // (開発用: 環境変数 SKYABYSS_TRIAL_DECK=0 で、岩鯨・潮殻とも置かない姿にして比べられる)
    private static boolean placed = !"0".equals(System.getenv("SKYABYSS_TRIAL_DECK"));

    public static boolean placed() {
        return placed;
    }

    public static void setPlaced(boolean value) {
        placed = value;
    }

    // 床のブロックの y (潮の広間の南の段と同じ)
    public static final int F = 74;

    public static final Area TC = new Area(-5, -3, 27, 37, F, 5);
    public static final Area H = new Area(-8, 90, 38, 46, F, 10);
    public static final Area T02 = new Area(2, 22, 49, 92, F, 9);
    public static final Area T10_FOYER_N = new Area(31, 49, 49, 55, F, 10);
    public static final Area T10_FOYER_S = new Area(35, 45, 81, 86, F, 10);
    // 円い闘技場の広間 (中心 (40, 68)。床の半径 10.2、広間の半径 11.6)。箱は外接
    public static final int T10_CX = 40;
    public static final int T10_CZ = 68;
    public static final Area T10 = new Area(28, 52, 49, 86, F - 7, 17);
    public static final Area T06 = new Area(56, 68, 49, 64, F, 10);
    public static final Area T06_STONE_ROOM = new Area(58, 66, 67, 71, F, 6);
    public static final Area T07 = new Area(72, 88, 49, 62, F, 12);
    public static final Area T07_STONE_ROOM = new Area(76, 84, 65, 69, F, 6);

    // 入江 T02 の寸法 (z)。入口の岸 49〜54・入江 55〜86 (中ほどに中島 68〜71)・向こうの岸 87〜92
    public static final int T02_SHORE_Z1 = 49;
    public static final int T02_SHORE_Z2 = 54;
    public static final int T02_FAR_Z1 = 87;
    public static final int T02_FAR_Z2 = 92;
    public static final Area T02_ISLET = new Area(8, 16, 68, 71, F, 9);
    public static final int T02_BED = 70;
    public static final int T02_WATER_TOP = F;

    // 試練の広間の板
    public static final BlockPos BOARD_H = new BlockPos(4, F + 1, 38);

    // 碑 (四つの戸の脇と、広間の案内)。文は lang の sign.skyabyss.tide_* (assets/skyabyss_trial)
    public static final List<TideBackLayout.Inscription> SIGNS = List.of(
            new TideBackLayout.Inscription("sign.skyabyss.tide_t02", new BlockPos(9, F + 3, 46), net.minecraft.core.Direction.NORTH),
            new TideBackLayout.Inscription("sign.skyabyss.tide_t10", new BlockPos(37, F + 3, 46), net.minecraft.core.Direction.NORTH),
            new TideBackLayout.Inscription("sign.skyabyss.tide_t06", new BlockPos(59, F + 3, 46), net.minecraft.core.Direction.NORTH),
            new TideBackLayout.Inscription("sign.skyabyss.tide_t07", new BlockPos(77, F + 3, 46), net.minecraft.core.Direction.NORTH),
            new TideBackLayout.Inscription("sign.skyabyss.tide_deck_guide", new BlockPos(1, F + 3, 38), net.minecraft.core.Direction.SOUTH));

    // ---------------------------------------------------------------- 戸 (封印の扉。H の南の壁 2 枚・石の前の戸)
    private static List<BlockPos> door(int x1, int x2, int z1, int z2) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = F + 1; y <= F + 5; y++) {
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
        return List.copyOf(out);
    }

    public static final List<BlockPos> T02_GATE = door(10, 14, 47, 48);
    public static final List<BlockPos> T10_GATE = door(38, 42, 47, 48);
    public static final List<BlockPos> T06_GATE = door(60, 64, 47, 48);
    public static final List<BlockPos> T07_GATE = door(78, 82, 47, 48);
    public static final List<BlockPos> T06_EXIT = door(60, 64, 65, 66);
    public static final List<BlockPos> T07_EXIT = door(78, 82, 63, 64);

    // ---------------------------------------------------------------- 石・戻りの光る床
    public static final BlockPos T02_STONE = new BlockPos(10, F + 1, 90);
    public static final BlockPos T02_PAD = new BlockPos(14, F, 90);
    public static final BlockPos T10_STONE = new BlockPos(37, F + 1, 84);
    public static final BlockPos T10_PAD = new BlockPos(43, F, 84);
    public static final BlockPos T06_STONE = new BlockPos(59, F + 1, 70);
    public static final BlockPos T06_PAD = new BlockPos(65, F, 70);
    public static final BlockPos T07_STONE = new BlockPos(77, F + 1, 68);
    public static final BlockPos T07_PAD = new BlockPos(83, F, 68);

    // ---------------------------------------------------------------- 休み場 (名前は TideBackLayout.RESTS の id。lang の checkpoint.skyabyss.tide_<id>)
    public static final String REST_H = "td0";
    public static final String REST_T02 = "t02a";
    public static final String REST_T02_ISLET = "t02m";
    public static final String REST_T06 = "t06a";
    public static final String REST_T07 = "t07a";
    public static final String REST_T10 = "t10a";

    public static List<Rest> rests() {
        return List.of(
                new Rest(REST_H, new Area(-8, 10, 38, 46, F, 10), new BlockPos(0, F + 1, 42), new BlockPos(1, F + 1, 43)),
                new Rest(REST_T02, new Area(2, 22, T02_SHORE_Z1, T02_SHORE_Z2, F, 9), new BlockPos(6, F + 1, 51), new BlockPos(8, F + 1, 52)),
                new Rest(REST_T02_ISLET, T02_ISLET, new BlockPos(12, F + 1, 69), new BlockPos(13, F + 1, 70)),
                new Rest(REST_T06, new Area(56, 68, 49, 52, F, 10), new BlockPos(58, F + 1, 50), new BlockPos(59, F + 1, 51)),
                new Rest(REST_T07, new Area(72, 88, 49, 52, F, 12), new BlockPos(74, F + 1, 50), new BlockPos(75, F + 1, 51)),
                new Rest(REST_T10, T10_FOYER_N, new BlockPos(35, F + 1, 51), new BlockPos(36, F + 1, 52)));
    }

    // 部屋の一覧 (重なりの検査・歩ける所の検査に使う。T10 は箱が円を含むので、控えと広間の外接を別に持つ)
    public static final List<Area> ROOMS = List.of(TC, H, T02, T10, T06, T06_STONE_ROOM, T07, T07_STONE_ROOM);
}
