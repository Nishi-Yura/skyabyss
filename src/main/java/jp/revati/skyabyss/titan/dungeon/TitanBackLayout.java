package jp.revati.skyabyss.titan.dungeon;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

// 岩鯨の背の配置図。建てる側 (Builder) と仕組みの側 (試練・休憩地点・リセット) の両方がここを参照する。
//
// 島は Z方向に長い楕円 (頭は -Z、全長240・幅96)。体内の主な床は y=93 (床ブロック、1つ上から空間)。
//
//              [宝物庫]  (北: 封印の扉の奥)
//                 |
//   [西の翼]--[鼓動の間]--[東の翼]
//                 |  \__ 床の穴の下に [闇の水路] (y 77〜81)
//            [入口の通路]
//                 |
//             [祠] (地表) ……… 背骨の道 ……… [尾の台・錨石] (到着地点)
//
// 西の翼 (裂け目の回廊): 西の前室 → 肋骨の谷 → 第一の休み場 → 竪穴 → 第二の休み場 → 崩れ橋 → 共鳴石
//                        足場の並びは毎回変わる
// 東の翼 (番兵の回廊):   東の前室 → 第一の広間 → 番兵長の控えの間 → 短い通路 → 番兵長の広間 → 共鳴石
//                        広間の扉は戦いの間だけ閉じる。制圧すると出口と入口が開く
// 心臓の回廊・心臓の間: 宝物庫の奥の扉から北へ。円形の大広間で岩鯨の心臓と戦う (最終ボス)
// 闇の水路: 3×3 の水の通路でできた迷路。毎回形が変わり、途中に息継ぎのくぼみだけがある
public final class TitanBackLayout {
    public static final int BASE_Y = 100;
    public static final int RX = 48;
    public static final int RZ = 120;
    public static final int FLOOR = 93;

    // 配置の版。部屋の配置を変えたら上げる。保存された版と違えば、起動時に背中を建て直す (TitanBackData)
    //  1: 最初の配置 / 2: 東の翼の第二の広間 (E2) と番兵長の控えの間 (旧 CE2) を除いた
    public static final int LAYOUT_VERSION = 2;

    // ---------------------------------------------------------------- 地表
    public static final int PLATFORM_Z = 96;
    public static final int SHRINE_Z = 24;
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
    // 心臓の間: 中心 (0, 94, -80)、床 y=93、天井 y=106 (体の表面の内側に収まる)。内側の半径 14.5、壁の外側 16.5
    public static final int HEART_CX = 0;
    public static final int HEART_CZ = -80;
    public static final Box HEART = new Box(-16, 16, -96, -64, FLOOR, 12);
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
    public static final Box E1 = new Box(24, 36, -34, -12, FLOOR, 8);
    // 番兵長の控えの間 (休み場 ce1)。E1 の出口の先。西の壁の戸口から短い通路で番兵長の広間へ
    public static final Box CE1 = new Box(26, 32, -42, -36, FLOOR, 5);
    public static final Box E3 = new Box(11, 19, -52, -30, FLOOR, 8);
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

    private TitanBackLayout() {
    }

    // 祠の階段の封印 (ピースフルのあいだだけ置く)。階段の幅 3 × 高さ 4
    public static List<BlockPos> shrineSeal() {
        int step = shrineY() - 1 - (SHRINE_Z - 1 - SHRINE_SEAL_Z);
        return wall(-1, 1, step + 1, step + 4, SHRINE_SEAL_Z, true);
    }

    // 尾の台。乗り移ってきた人はここに立つ
    public static BlockPos arrival() {
        return new BlockPos(0, platformY() + 1, PLATFORM_Z - 2);
    }

    // 錨石。右クリックすると地上へ降りる
    // 尾の台に置かれた共鳴の炉 (着いてすぐ装備を鍛えられる)
    public static BlockPos forge() {
        return new BlockPos(-3, platformY() + 1, PLATFORM_Z);
    }

    public static BlockPos anchor() {
        return new BlockPos(0, platformY(), PLATFORM_Z + 3);
    }

    public static int platformY() {
        return top(0, PLATFORM_Z);
    }

    public static int shrineY() {
        return top(0, SHRINE_Z);
    }

    public static double shape(double x, double z) {
        return 1.0 - (x / RX) * (x / RX) - (z / RZ) * (z / RZ);
    }

    public static int top(int x, int z) {
        double s = shape(x, z);
        return s <= 0 ? BASE_Y : BASE_Y + (int) Math.floor(14.0 * Math.pow(s, 0.8));
    }

    public static int bottom(int x, int z) {
        double s = shape(x, z);
        return s <= 0 ? BASE_Y : BASE_Y - (int) Math.floor(34.0 * Math.sqrt(s)) - 1;
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
