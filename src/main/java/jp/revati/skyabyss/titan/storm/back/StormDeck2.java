package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.wallX;
import static jp.revati.skyabyss.titan.storm.back.StormDeck.wallZ;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Area;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Inscription;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Rest;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の「第二の渡り場」(docs/design/29 の 3.3・P3。五つの新作: S04 羽根の吊り橋・S07 二枚の鏡の間・S08 気圧の弁・S11 避雷の護送・S12 羽守長の頂)。
// 最初の渡り場 (StormDeck.YARD。z 64〜82) の南の壁の真ん中 (戸のない開口) から、5 幅の橋が S10・S09 の間 (x -3〜3) を南へ伸び、任意の翼の「尾羽の庭」(z 122〜164。中央の隙間 x -4〜4) を越えて、第二の渡り場 (YARD。z 172〜190) に着く。
// 戸は 5 つ (引かれた試練のものだけがそのグループに開く):
//   西の戸 → 羽根の吊り橋 (S04)   東の戸 → 気圧の弁 (S08)   南西の戸 → 二枚の鏡の間 (S07)   南の戸 → 羽守長の頂 (S12)   南東の戸 → 避雷の護送 (S11)
// どれも空に浮かぶ島 (下は尾羽の空。落ちたら休み場へ戻される)。座標はここだけ。建て方は StormDeck2Builder。尾羽の背 (y 147 まで。x ±50、z 〜180) の上に収まる
// (島の下の錐は深さ 12 まで)。StormTrialSelfTest が島が互いに重ならないこと・戸が離れていることを確かめる。
//
//   尾羽の上 (南) から見た平面 (北が上。x は西 -、東 +):
//
//        第一の渡り場 (z 64〜82) ── 橋 (x -2〜2, z 83〜171) ── 第二の渡り場 (z 172〜190)
//        S04 西 ← 戸 ・ 戸 → S08 東                  S07 南西 ・ S12 南 ・ S11 南東 (戸は z 191)
public final class StormDeck2 {
    private StormDeck2() {
    }

    public static final int Y = StormDeck.Y;
    public static final int G0 = Y + 1;
    public static final int G1 = Y + 5;

    // ---------------------------------------------------------------- 橋と渡り場
    public static final int BRIDGE_X1 = -2;
    public static final int BRIDGE_X2 = 2;
    // 第一の渡り場の南の縁 (z 83) から第二の渡り場の北の縁 (z 171) まで
    public static final int BRIDGE_Z1 = 83;
    public static final int BRIDGE_Z2 = 171;
    public static final Area YARD = new Area(-24, 24, 172, 190, Y, 12);
    public static final BlockPos YARD_FIRE = new BlockPos(0, Y + 1, 181);
    public static final BlockPos YARD_SPAWN = new BlockPos(2, Y + 1, 181);
    public static final BlockPos BOARD_YARD = new BlockPos(-6, Y + 1, 174);

    // ---------------------------------------------------------------- 五つの戸 (封印の扉。高さ 5)
    public static final List<BlockPos> S04_GATE = wallX(-25, 179, 183);
    public static final List<BlockPos> S08_GATE = wallX(25, 179, 183);
    public static final List<BlockPos> S07_GATE = wallZ(191, -24, -20);
    public static final List<BlockPos> S12_GATE = wallZ(191, -2, 2);
    public static final List<BlockPos> S11_GATE = wallZ(191, 18, 22);

    private static Island deck(int cx, int cz, int y, int half) {
        return new Island(cx, cz, y, half, false);
    }

    private static Island pad(int cx, int cz, int y, int r) {
        return new Island(cx, cz, y, r, true);
    }

    // ---------------------------------------------------------------- S04 羽根の吊り橋 (西)
    // 始めの台 → 橋 A (柱 3 本。教え: 突風はいつも北から) → 中ほどの台 (休み場) → 橋 B (柱 3 本。ひねり: 突風の向きが毎回変わる) → 向こうの台 (石)。
    // 橋は幅 7 (z 138〜144)。柱 (3x3、高さ 4) は橋の真ん中。柱の風下 (風の来ない側) の床が「陰」で、突風の予告のあいだ白く光る
    public static final Island S04_START = deck(-37, 181, Y, 5);
    public static final Island S04_MID = deck(-91, 181, Y, 4);
    public static final Island S04_END = deck(-145, 181, Y, 5);
    public static final int S04_BRIDGE_Z1 = 178;
    public static final int S04_BRIDGE_Z2 = 184;
    public static final int S04_CENTER_Z = 181;
    // 橋 A: x -43〜-86、橋 B: x -96〜-139
    public static final int S04_A_X1 = -86;
    public static final int S04_A_X2 = -43;
    public static final int S04_B_X1 = -139;
    public static final int S04_B_X2 = -96;
    // 柱の中心 x。前の三つが A (教え)、あとの三つが B (ひねり)
    public static final int[] S04_PILLARS = {-54, -66, -78, -108, -120, -132};
    public static final int S04_TEACH = 3;
    public static final BlockPos S04_STONE = new BlockPos(-147, Y + 1, 181);
    public static final AABB S04_AREA = new AABB(-152, Y - 12, 170, -26, Y + 52, 192);
    public static final Area S04_APPROACH = new Area(-31, -26, 179, 183, Y, 8);

    // ---------------------------------------------------------------- S08 気圧の弁 (東)
    // 広間 (21x21)。北の壁ぎわに四つの気嚢の目盛り (A〜D)、南の壁ぎわに三つの弁。石は広間の東
    public static final Island S08_HALL = deck(42, 181, Y, 10);
    public static final int S08_TANK_Z = 172;
    public static final int[] S08_TANK_X = {36, 40, 44, 48};
    public static final int S08_TANK_H = 5;
    public static final int S08_VALVE_Z = 189;
    public static final int[] S08_VALVE_X = {36, 42, 48};
    public static final BlockPos S08_STONE = new BlockPos(51, Y + 1, 181);
    public static final AABB S08_AREA = new AABB(26, Y - 12, 170, 64, Y + 40, 192);
    public static final Area S08_APPROACH = new Area(26, 31, 179, 183, Y, 8);

    public static BlockPos s08Valve(int v) {
        return new BlockPos(S08_VALVE_X[v], Y + 1, S08_VALVE_Z);
    }

    // 弁 v の効きの表 (壁の板): tank j の板の位置 (南の壁 z 151、高さ Y + 3)
    public static BlockPos s08Panel(int v, int tank) {
        return new BlockPos(S08_VALVE_X[v] - 2 + tank, Y + 3, 191);
    }

    // 目盛りの鍵の色の位置 (板の一段上。世界のブロックで置く)
    public static BlockPos s08PanelKey(int v, int tank) {
        return new BlockPos(S08_VALVE_X[v] - 2 + tank, Y + 4, 191);
    }

    // 気嚢 tank の目盛りのブロック (level 1〜5 の高さ)
    public static BlockPos s08Gauge(int tank, int level) {
        return new BlockPos(S08_TANK_X[tank], Y + level, S08_TANK_Z);
    }

    // 気嚢 tank の印の位置 (目盛りの東の柱の level の高さ)
    public static BlockPos s08Mark(int tank, int level) {
        return new BlockPos(S08_TANK_X[tank] + 1, Y + level, S08_TANK_Z);
    }

    // ---------------------------------------------------------------- S07 二枚の鏡の間 (南西)
    // 広間 x -64〜-20、z 199〜225 (45 x 27)。3x3 のマスの格子 15 列 x 9 行。仕切りの列 (gx 7) の真ん中 (行 4) が窓。
    //   部屋 1 (gx 0〜6): 鏡 L0〜L3。部屋 2 (gx 8〜14): 鏡 R0・R1n・R2n・R3・R1s・R2s。光の出る所は西の壁 (行 4)、受け羽は東の壁 (行 4)
    public static final int S07_X0 = -64;
    public static final int S07_Z0 = 199;
    public static final int S07_COLS = 15;
    public static final int S07_ROWS = 9;
    public static final int S07_X1 = S07_X0;
    public static final int S07_X2 = S07_X0 + S07_COLS * 3 - 1;
    public static final int S07_Z1 = S07_Z0;
    public static final int S07_Z2 = S07_Z0 + S07_ROWS * 3 - 1;
    public static final int S07_SRC_ROW = 4;
    public static final int S07_WINDOW_COL = 7;
    // 鏡の格子の座標 {gx, gz}。0〜3 が部屋 1 (L0〜L3)、4〜9 が部屋 2 (R0・R1n・R2n・R3・R1s・R2s)
    public static final int[][] S07_MIRRORS = {{2, 4}, {2, 1}, {5, 1}, {5, 4}, {9, 4}, {9, 1}, {12, 1}, {12, 4}, {9, 7}, {12, 7}};
    public static final BlockPos S07_STONE = new BlockPos(-22, Y + 1, 223);
    public static final AABB S07_AREA = new AABB(-68, Y - 12, 192, -17, Y + 40, 228);
    public static final Area S07_APPROACH = new Area(-24, -20, 192, 198, Y, 8);

    // マス (gx, gz) の中心の床のブロック
    public static BlockPos s07Cell(int gx, int gz) {
        return new BlockPos(S07_X0 + gx * 3 + 1, Y, S07_Z0 + gz * 3 + 1);
    }

    // 光の出る所のブロック (西の壁の外側の端)、受け羽のブロック (東の壁の内側)
    public static BlockPos s07Emitter() {
        return new BlockPos(S07_X1 - 1, Y + 2, S07_Z0 + S07_SRC_ROW * 3 + 1);
    }

    public static BlockPos s07Catcher() {
        return new BlockPos(S07_X2 + 1, Y + 2, S07_Z0 + S07_SRC_ROW * 3 + 1);
    }

    // 窓の戸のブロック (仕切りの列の真ん中の 3x3、高さ 4)
    public static List<BlockPos> s07Window() {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = s07Cell(S07_WINDOW_COL, S07_SRC_ROW);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = Y + 1; y <= Y + 4; y++) {
                    out.add(new BlockPos(c.getX() + dx, y, c.getZ() + dz));
                }
            }
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- S12 羽守長の頂 (南)
    // 控え (休み場) → 橋 → 丸い頂 (半径 13) → 橋 (勝つと開く戸) → 石の島。頂は真ん中の芯 (半径 4.4。いつもある) と、六つの扇の羽の足場 (封印の扉のブロック)
    public static final Island S12_FOYER = deck(0, 204, Y, 5);
    public static final Island S12_ARENA = pad(0, 228, Y, 13);
    public static final Island S12_EXIT = deck(0, 250, Y, 4);
    public static final double S12_CORE_R = 4.4;
    public static final int S12_ARENA_R = 13;
    // 入口の橋: x -1〜1、z 210〜214。出口の橋: x -2〜2、z 242〜245 (戸は z 243)
    public static final int S12_BRIDGE_Z1 = 210;
    public static final int S12_BRIDGE_Z2 = 214;
    public static final int S12_EXIT_Z1 = 242;
    public static final int S12_EXIT_Z2 = 245;
    public static final List<BlockPos> S12_EXIT_GATE = wallZ(243, -2, 2);
    public static final BlockPos S12_STONE = new BlockPos(0, Y + 1, 251);
    public static final AABB S12_AREA = new AABB(-16, Y - 12, 192, 16, Y + 52, 258);
    public static final Area S12_APPROACH = new Area(-2, 2, 192, 198, Y, 8);

    // 扇 w (0〜5) の足場の床のブロック。芯の外 (半径 4.4〜12.6) を六つの角度で分ける
    public static int s12Wedge(int dx, int dz) {
        double r = Math.hypot(dx, dz);
        // 芯・入口と出口への通り道 (幅 3 の南北の筋。いつもある)・縁の輪 (いつもある) は足場に入らない
        if (r <= S12_CORE_R || r > S12_ARENA_R - 0.4 || Math.abs(dx) <= 1) {
            return -1;
        }
        double a = Math.toDegrees(Math.atan2(dz, dx));
        if (a < 0) {
            a += 360;
        }
        return ((int) Math.floor(a / 60.0)) % 6;
    }

    public static List<BlockPos> s12Pads(int wedge) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                if (S12_ARENA.has(S12_ARENA.cx() + dx, S12_ARENA.cz() + dz) && s12Wedge(dx, dz) == wedge) {
                    out.add(new BlockPos(S12_ARENA.cx() + dx, Y, S12_ARENA.cz() + dz));
                }
            }
        }
        return List.copyOf(out);
    }

    public static List<BlockPos> s12AllPads() {
        List<BlockPos> out = new ArrayList<>();
        for (int w = 0; w < 6; w++) {
            out.addAll(s12Pads(w));
        }
        return List.copyOf(out);
    }

    public static boolean isFloorGate(BlockPos p) {
        if (p.getY() != Y) {
            return false;
        }
        return S12_ARENA.has(p.getX(), p.getZ()) && s12Wedge(p.getX() - S12_ARENA.cx(), p.getZ() - S12_ARENA.cz()) >= 0;
    }

    // ---------------------------------------------------------------- S11 避雷の護送 (南東)
    // 大きな甲板 (53x53。x 18〜70、z 198〜250)。籠は道 (3 通り) に沿って、北西の入口から南東の塔へ。籠のまわり 2.5 マスは雷が落ちる
    public static final Island S11_DECK = deck(44, 224, Y, 26);
    public static final int[][][] S11_ROUTES = {
            {{20, 201}, {20, 216}, {40, 216}, {40, 230}, {24, 230}, {24, 244}, {62, 244}},
            {{20, 201}, {34, 201}, {34, 220}, {58, 220}, {58, 234}, {28, 234}, {28, 244}, {62, 244}},
            {{20, 201}, {20, 210}, {50, 210}, {50, 226}, {30, 226}, {30, 240}, {62, 240}, {62, 244}}};
    // 塔 (籠の終わり)。石は塔の脇
    public static final BlockPos S11_TOWER = new BlockPos(66, Y + 1, 244);
    public static final BlockPos S11_STONE = new BlockPos(67, Y + 1, 247);
    public static final AABB S11_AREA = new AABB(17, Y - 12, 192, 72, Y + 40, 252);
    public static final Area S11_APPROACH = new Area(18, 22, 192, 197, Y, 8);

    // ---------------------------------------------------------------- 休み場
    public static final Rest REST_YARD = new Rest("yard2", YARD_FIRE, YARD_SPAWN, YARD);
    public static final Rest REST_S04A = rest("s04a", S04_START, -4, -4, -3, -2);
    public static final Rest REST_S04M = rest("s04m", S04_MID, -3, -3, -2, -2);
    public static final Rest REST_S07A = new Rest("s07a", new BlockPos(-20, Y + 1, 201), new BlockPos(-21, Y + 1, 202), new Area(-64, -20, 199, 225, Y, 8));
    public static final Rest REST_S08A = new Rest("s08a", new BlockPos(34, Y + 1, 175), new BlockPos(35, Y + 1, 176), S08_HALL.area(8));
    public static final Rest REST_S11A = new Rest("s11a", new BlockPos(21, Y + 1, 204), new BlockPos(22, Y + 1, 205), S11_DECK.area(8));
    public static final Rest REST_S12A = rest("s12a", S12_FOYER, -3, -3, -2, -2);
    public static final List<Rest> RESTS = List.of(REST_YARD, REST_S04A, REST_S04M, REST_S07A, REST_S08A, REST_S11A, REST_S12A);

    private static Rest rest(String id, Island is, int fx, int fz, int sx, int sz) {
        BlockPos fire = new BlockPos(is.cx() + fx, is.y() + 1, is.cz() + fz);
        BlockPos spawn = new BlockPos(is.cx() + sx, is.y() + 1, is.cz() + sz);
        return new Rest(id, fire, spawn, is.area(8));
    }

    public static List<Rest> restsOf(String cell) {
        return switch (cell) {
            case "S04" -> List.of(REST_S04A, REST_S04M);
            case "S07" -> List.of(REST_S07A);
            case "S08" -> List.of(REST_S08A);
            case "S11" -> List.of(REST_S11A);
            case "S12" -> List.of(REST_S12A);
            default -> List.of();
        };
    }

    // ---------------------------------------------------------------- 看板 (碑)
    public static final List<Inscription> SIGNS = List.of(
            new Inscription("sign.skyabyss.storm_d2_bridge", new BlockPos(BRIDGE_X1 + 1, Y + 1, BRIDGE_Z1 + 4), Direction.UP),
            new Inscription("sign.skyabyss.storm_d2_yard", new BlockPos(-3, Y + 1, 175), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s04", new BlockPos(-28, Y + 1, 178), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s04_start", new BlockPos(-35, Y + 1, 178), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s04_mid", new BlockPos(-89, Y + 1, 178), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s08", new BlockPos(28, Y + 1, 178), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s08_start", new BlockPos(38, Y + 1, 175), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s08_mid", new BlockPos(46, Y + 1, 175), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s07", new BlockPos(-22, Y + 1, 193), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s07_start", new BlockPos(-24, Y + 1, 201), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s07_mid", new BlockPos(-39, Y + 1, 201), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s12", new BlockPos(2, Y + 1, 193), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s12_start", new BlockPos(3, Y + 1, 201), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s12_arena", new BlockPos(2, Y + 1, 211), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s11", new BlockPos(20, Y + 1, 193), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s11_start", new BlockPos(24, Y + 1, 200), Direction.UP),
            new Inscription("sign.skyabyss.storm_d_s11_mid", new BlockPos(32, Y + 1, 202), Direction.UP));

    // ---------------------------------------------------------------- 部屋 (入ったときの一行)
    static List<StormDeck.Zone> zones() {
        List<StormDeck.Zone> out = new ArrayList<>();
        out.add(new StormDeck.Zone("d2_bridge", new AABB(BRIDGE_X1, Y, BRIDGE_Z1, BRIDGE_X2 + 1, Y + 8, BRIDGE_Z2 + 1)));
        out.add(new StormDeck.Zone("d2_yard", YARD.aabb()));
        out.add(new StormDeck.Zone("d_s04", S04_AREA));
        out.add(new StormDeck.Zone("d_s08", S08_AREA));
        out.add(new StormDeck.Zone("d_s07", S07_AREA));
        out.add(new StormDeck.Zone("d_s12", S12_AREA));
        out.add(new StormDeck.Zone("d_s11", S11_AREA));
        return out;
    }

    // ---------------------------------------------------------------- 着くべき所 (詰み監査)
    static Map<String, BlockPos> goals() {
        Map<String, BlockPos> m = new LinkedHashMap<>();
        m.put("d2 bridge", new BlockPos(0, Y + 1, 110));
        m.put("d2 yard", new BlockPos(0, Y + 1, 183));
        m.put("d s04 start", REST_S04A.spawn());
        m.put("d s04 mid", REST_S04M.spawn());
        m.put("d s04 stone", S04_STONE.east());
        m.put("d s08 hall", REST_S08A.spawn());
        m.put("d s08 valve", s08Valve(1).north());
        m.put("d s08 stone", S08_STONE.west());
        m.put("d s07 hall", REST_S07A.spawn());
        m.put("d s07 room 2", new BlockPos(S07_X0 + 11 * 3, Y + 1, S07_Z0 + 4 * 3 + 1));
        m.put("d s07 stone", S07_STONE.west());
        m.put("d s12 foyer", REST_S12A.spawn());
        m.put("d s12 arena", new BlockPos(S12_ARENA.cx() - 2, Y + 1, S12_ARENA.cz() + 1));
        m.put("d s12 stone", S12_STONE.north());
        m.put("d s11 deck", REST_S11A.spawn());
        m.put("d s11 tower", S11_TOWER.west(2));
        m.put("d s11 stone", S11_STONE.west());
        return m;
    }

    // 外へ開いていてよい所 (seal・小さな穴の検査から除く): 第二の甲板の上すべて
    static boolean open(int x, int y, int z) {
        return StormDeck.placed() && y >= Y - 1 && z >= BRIDGE_Z1 - 1 && z <= 275 && x >= -160 && x <= 160;
    }

    // 面積 (m²。歩ける床)
    public static int areaTotal() {
        int sum = (BRIDGE_X2 - BRIDGE_X1 + 1) * (BRIDGE_Z2 - BRIDGE_Z1 + 1) + (YARD.x2() - YARD.x1() + 1) * (YARD.z2() - YARD.z1() + 1);
        for (Island i : List.of(S04_START, S04_MID, S04_END, S08_HALL, S12_FOYER, S12_ARENA, S12_EXIT, S11_DECK)) {
            sum += i.floor().size();
        }
        sum += (S04_A_X2 - S04_A_X1 + 1 + S04_B_X2 - S04_B_X1 + 1) * 7;
        sum += (S07_X2 - S07_X1 + 1) * (S07_Z2 - S07_Z1 + 1);
        return sum;
    }

    // 位置が、第二の甲板の試練のどれかの中か (リセットで広間へ戻す範囲)
    public static boolean inCell(Vec3 p) {
        return StormDeck.placed() && (S04_AREA.contains(p) || S08_AREA.contains(p) || S07_AREA.contains(p) || S12_AREA.contains(p) || S11_AREA.contains(p));
    }

    // 位置が第二の渡り場・橋の上か (目的の導き)
    public static boolean onLanding(Vec3 v) {
        return v.y > Y - 2 && (YARD.aabb().inflate(1, 4, 1).contains(v)
                || v.x >= BRIDGE_X1 - 1 && v.x <= BRIDGE_X2 + 2 && v.z >= BRIDGE_Z1 && v.z <= BRIDGE_Z2 + 1);
    }

    public static boolean isNewCell(String id) {
        return switch (id) {
            case "S04", "S07", "S08", "S11", "S12" -> true;
            default -> false;
        };
    }

    // 試練の戸の中心 (その試練へ入る所)
    public static BlockPos gateCenter(String id) {
        return switch (id) {
            case "S04" -> new BlockPos(-25, Y + 1, 181);
            case "S08" -> new BlockPos(25, Y + 1, 181);
            case "S07" -> new BlockPos(-22, Y + 1, 191);
            case "S12" -> new BlockPos(0, Y + 1, 191);
            default -> new BlockPos(20, Y + 1, 191);
        };
    }

    public static List<BlockPos> gateOf(String id) {
        return switch (id) {
            case "S04" -> S04_GATE;
            case "S08" -> S08_GATE;
            case "S07" -> S07_GATE;
            case "S12" -> S12_GATE;
            default -> S11_GATE;
        };
    }
}
