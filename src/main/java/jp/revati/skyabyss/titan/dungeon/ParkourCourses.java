package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.CourseGenerator.Spec;
import net.minecraft.server.level.ServerLevel;

// 西の翼の三つの谷のアスレチック。試練が元に戻るたびに、新しい種で作り直される。
// 入口と出口の岩棚の位置は決まっていて、そのあいだの足場の並びだけが毎回変わる。
// 谷ごとに並列のレーン (同じ入口の岩棚から同じ出口の岩棚へ行く道) を 2〜3 本通す (肋骨の谷 3 本、竪穴・崩れ橋 2 本。26 の 3.1)。
// 岩棚の座標は TitanBackLayout の WA / WB / WC / W1 / W2 と合わせてある
public final class ParkourCourses {
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
            WA.x1() + 2, WA.x2() - 2, WA.z1() + 2, WA.z2() - 2, FLOOR, FLOOR + 6, 14, 24, 3);
    // 竪穴: 第一の休み場の戸口 (谷の南の縁) から、第二の休み場の戸口 (谷の北の縁、y +4) まで (登っていく)
    public static final Spec B = new Spec(
            ledge(-49, -45, W1.floorY(), WB.z2(), WB.z2()),
            ledge(-46, -42, W2.floorY(), WB.z1(), WB.z1()),
            WB.x1() + 2, WB.x2() - 2, WB.z1() + 2, WB.z2() - 2, W1.floorY(), W2.floorY(), 13, 22, 2);
    // 崩れ橋: 第二の休み場からの通路の先 (谷の北の縁) から、共鳴石の岩棚 (谷の東の縁) まで (下っていく)
    public static final Spec C = new Spec(
            ledge(-31, -27, W2.floorY(), WC.z1(), WC.z1()),
            ledge(-25, -21, WEST_LEDGE_Y, -54, -53),
            WC.x1() + 2, WC.x2() - 2, WC.z1() + 2, WC.z2() - 4, WEST_LEDGE_Y - 1, W2.floorY(), 12, 20, 2);
    // 一つのレーンごとの指定 (同じ谷のレーンは同じ指定)。レーンの数: 肋骨の谷 3、竪穴 2、崩れ橋 2
    public static final List<Spec> ALL = List.of(A, A, A, B, B, C, C);
    private static final int[] PIT_OF_LANE = {0, 0, 0, 1, 1, 2, 2};

    private static long cachedSeed = Long.MIN_VALUE;
    private static List<List<int[]>> cached;

    private ParkourCourses() {
    }

    // 谷 (0 = 肋骨の谷、1 = 竪穴、2 = 崩れ橋) の何本目までがレーンか
    public static List<Integer> lanesOfPit(int pit) {
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < PIT_OF_LANE.length; k++) {
            if (PIT_OF_LANE[k] == pit) {
                out.add(k);
            }
        }
        return out;
    }

    // 種から、全レーンの足場を作る (同じ種なら同じ形)。ALL と同じ順
    public static List<List<int[]>> forSeed(long seed) {
        if (seed != cachedSeed) {
            List<List<int[]>> lanes = new ArrayList<>();
            for (int k = 0; k < ALL.size(); k++) {
                List<int[]> avoid = new ArrayList<>();
                for (int j = 0; j < k; j++) {
                    if (PIT_OF_LANE[j] == PIT_OF_LANE[k]) {
                        avoid.addAll(lanes.get(j));
                    }
                }
                List<int[]> lane = null;
                for (int retry = 0; retry < 12 && lane == null; retry++) {
                    try {
                        lane = CourseGenerator.generate(ALL.get(k), seed * 31 + k + 1 + retry * 1_000_003L, avoid);
                    } catch (IllegalStateException e) {
                        lane = null;
                    }
                }
                if (lane == null) {
                    // 隣のレーンから離せなかった: 離さずに作る (足場の数が同じ道は保たれる)
                    SkyAbyss.LOGGER.warn("Parkour lane {} could not keep clear of the others for seed {}", k, seed);
                    lane = CourseGenerator.generate(ALL.get(k), seed * 31 + k + 1);
                }
                lanes.add(lane);
            }
            cached = List.copyOf(lanes);
            cachedSeed = seed;
        }
        return cached;
    }

    public static List<List<int[]>> current(ServerLevel level) {
        return forSeed(TitanBackData.get(level).courseSeed());
    }
}
