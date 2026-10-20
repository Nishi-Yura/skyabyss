package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.dungeon.CourseGenerator.Spec;
import net.minecraft.server.level.ServerLevel;

// 西の翼の三つの谷のアスレチック (版 18 で作り直し。26 の 8.6)。足場の並びは毎回ちがう乱数ではなく、読める一本の道:
//   飛び石 (落ちない石を 3 マスおきに) → 島 (足場の島。休み場ではない。落ちたら谷の入口の休み場へ戻る) → 骨の橋 (崩れる骨が一列に続き、ところどころ 2 マスの溝を跳ぶ) → …
// 足場はひとつずつ、次の足場へ必ず跳べる (CourseRules)。道は谷の入口の岩棚 (start) から出口の岩棚 (end) まで一本。
// 島は 3×3 の石の台 (wa1・wa2・wb1・wc1)。版 22 でコースの途中の休み場をなくした (休み場は谷と谷のあいだの岩室 w0・w1・w2 だけ)。
// 足場の種類 (4 番目の値): 0 = 崩れる骨 (乗ると崩れ、4.5 秒で戻る)、1 = 石 (飛び石。崩れない)。島の石は ISLANDS から建てる
public final class ParkourCourses {
    // コースの途中の足場の島 (休み場ではない): 中心 (cx, cz)・床の高さ y。3×3
    public record Island(String id, int cx, int y, int cz) {
        public boolean has(int x, int z) {
            return Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1;
        }
    }

    private static List<int[]> ledge(int x1, int x2, int y, int z1, int z2) {
        List<int[]> list = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                list.add(new int[]{x, y, z});
            }
        }
        return List.copyOf(list);
    }

    // 肋骨の谷: 西の前室の戸口 (谷の東の縁) から、第一の休み場の戸口 (谷の北の縁、y +5) まで
    public static final Spec A = new Spec(
            ledge(WA.x2(), WA.x2(), FLOOR, -38, -34),
            ledge(-52, -48, W1.floorY(), WA.z1(), WA.z1()),
            WA.x1() + 2, WA.x2() - 2, WA.z1() + 2, WA.z2() - 2, FLOOR, FLOOR + 6);
    // 竪穴: 第一の休み場の戸口 (谷の南の縁) から、第二の休み場の戸口 (谷の北の縁、y +4) まで (登っていく)
    public static final Spec B = new Spec(
            ledge(-49, -45, W1.floorY(), WB.z2(), WB.z2()),
            ledge(-46, -42, W2.floorY(), WB.z1(), WB.z1()),
            WB.x1() + 2, WB.x2() - 2, WB.z1() + 2, WB.z2() - 2, W1.floorY(), W2.floorY());
    // 崩れ橋: 第二の休み場からの通路の先 (谷の北の縁) から、共鳴石の岩棚 (谷の東の縁) まで (下っていく)
    public static final Spec C = new Spec(
            ledge(-31, -27, W2.floorY(), WC.z1(), WC.z1()),
            ledge(-25, -21, WEST_LEDGE_Y, -54, -53),
            WC.x1() + 2, WC.x2() - 2, WC.z1(), WC.z2() - 2, WEST_LEDGE_Y - 1, W2.floorY());
    // 谷ごとの指定 (谷 0 = 肋骨の谷、1 = 竪穴、2 = 崩れ橋)
    public static final List<Spec> ALL = List.of(A, B, C);

    // コースの途中の足場の島 (休み場ではない) (谷ごと)
    public static final List<Island> ISLANDS_A = List.of(new Island("wa1", -49, FLOOR, -26), new Island("wa2", -49, FLOOR + 2, -40));
    public static final List<Island> ISLANDS_B = List.of(new Island("wb1", -52, W1.floorY() + 2, -81));
    public static final List<Island> ISLANDS_C = List.of(new Island("wc1", -29, W2.floorY(), -70));
    public static final List<List<Island>> ISLANDS = List.of(ISLANDS_A, ISLANDS_B, ISLANDS_C);

    private static List<List<int[]>> built;

    private ParkourCourses() {
    }

    // 道を作る小さな道具: 足場を順に積む
    private static final class Path {
        final List<int[]> nodes = new ArrayList<>();
        int x;
        int y;
        int z;

        Path(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        // 飛び石 (石。崩れない)
        Path stone(int tx, int ty, int tz) {
            nodes.add(new int[]{tx, ty, tz, 1});
            x = tx;
            y = ty;
            z = tz;
            return this;
        }

        // 骨の橋: (tx, tz) まで 1 マスずつ崩れる骨を並べる (軸に沿って)。高さは y のまま
        Path bridge(int tx, int tz) {
            while (x != tx || z != tz) {
                if (x != tx) {
                    x += Integer.signum(tx - x);
                } else {
                    z += Integer.signum(tz - z);
                }
                nodes.add(new int[]{x, y, z, 0});
            }
            return this;
        }

        // 位置だけ動かす (島の縁へ)
        Path at(int nx, int ny, int nz) {
            x = nx;
            y = ny;
            z = nz;
            return this;
        }

        // 溝を跳んだ先の骨 1 つ
        Path bone(int tx, int ty, int tz) {
            nodes.add(new int[]{tx, ty, tz, 0});
            x = tx;
            y = ty;
            z = tz;
            return this;
        }
    }

    // 谷ごとの道 (島の石は含まない)
    private static List<List<int[]>> make() {
        List<List<int[]>> all = new ArrayList<>();
        // ---- 肋骨の谷 (東の岩棚 x -37, z -36, y 103 → 北の岩棚 z -54, y 108)
        // 一 飛び石を南西へ (3 マスずつ) → 島 wa1 (-49, 103, -26)
        Path a = new Path(-37, FLOOR, -36);
        a.stone(-40, FLOOR, -36).stone(-40, FLOOR, -33).stone(-43, FLOOR, -33).stone(-43, FLOOR, -30).stone(-46, FLOOR, -30).stone(-46, FLOOR, -27);
        // 二 島から北へ骨の橋 (z -28〜-32)、2 マスの溝を跳んで (z -35〜-38、1 段上) → 島 wa2 (-49, 105, -40)
        a.at(-49, FLOOR, -27).bridge(-49, -32).bone(-49, FLOOR + 1, -35).bridge(-49, -38);
        // 三 島 wa2 の北から、溝を跳んで (z -44〜-47、1 段上) → 溝 (z -50〜-52、1 段上) → 北の岩棚 (z -54、y 108)
        a.at(-49, FLOOR + 2, -41);
        a.bone(-49, FLOOR + 3, -44).bridge(-49, -47).bone(-49, FLOOR + 4, -50).bridge(-49, -52);
        all.add(a.nodes);
        // ---- 竪穴 (南の岩棚 z -69, y 108 → 北の岩棚 z -90, y 112)
        // 一 飛び石を西へ (3 マスずつ、ところどころ 1 段上) → 島 wb1 (-52, 110, -81)
        Path b = new Path(-47, W1.floorY(), -69);
        b.stone(-47, W1.floorY(), -72).stone(-50, W1.floorY() + 1, -72).stone(-53, W1.floorY() + 1, -74).stone(-53, W1.floorY() + 2, -77);
        // 二 島から東へ骨の橋 (x -50〜-47、y 110)、溝を跳んで (x -44〜-42)、北へ飛び石で登る
        b.at(-51, W1.floorY() + 2, -81).bridge(-47, -81).bone(-44, W1.floorY() + 2, -81).bridge(-42, -81);
        b.stone(-42, W1.floorY() + 3, -84).stone(-43, W1.floorY() + 4, -87);
        all.add(b.nodes);
        // ---- 崩れ橋 (北の岩棚 z -85, y 112 → 東の岩棚 x -25〜-21, z -54, y 108。下っていく)
        // 一 一本の長い骨の橋を南へ (z -84〜-80)、2 マスの溝を二度跳ぶ → 島 wc1 (-29, 112, -70)
        Path c = new Path(-29, W2.floorY(), -85);
        c.bridge(-29, -80).bone(-29, W2.floorY(), -77).bridge(-29, -76).bone(-29, W2.floorY(), -73);
        // 二 島から南へ、飛び石で下りる (1 段ずつ)
        c.at(-29, W2.floorY(), -69);
        c.stone(-29, W2.floorY() - 1, -66).stone(-27, W2.floorY() - 2, -63).stone(-25, W2.floorY() - 3, -60).stone(-24, W2.floorY() - 4, -57);
        all.add(c.nodes);
        return all;
    }

    // 全部の谷の足場 ({x, y, z, 種類})。谷 0・1・2 の順。いつも同じ内容 (seed は旧い呼び出しとの互換のためで、使わない)
    public static List<List<int[]>> forSeed(long seed) {
        if (built == null) {
            built = make();
        }
        return built;
    }

    public static List<List<int[]>> current(ServerLevel level) {
        return forSeed(0);
    }

    // 谷 pit の島の石 (3×3 の床の座標)
    public static List<int[]> islandCells(int pit) {
        List<int[]> out = new ArrayList<>();
        for (Island i : ISLANDS.get(pit)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(new int[]{i.cx() + dx, i.y(), i.cz() + dz, 1});
                }
            }
        }
        return out;
    }

    // 谷 pit の、道の全部のブロック (足場 + 島の石)
    public static List<int[]> allBlocks(int pit) {
        List<int[]> out = new ArrayList<>(forSeed(0).get(pit));
        out.addAll(islandCells(pit));
        return out;
    }
}
