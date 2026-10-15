package jp.revati.skyabyss.titan.dungeon;

import java.util.List;

import jp.revati.skyabyss.titan.dungeon.CourseGenerator.Spec;
import net.minecraft.server.level.ServerLevel;

// 西の翼の三つの谷のアスレチック。試練が元に戻るたびに、新しい種で作り直される。
// 入口と出口の岩棚の位置は決まっていて、そのあいだの足場の並びだけが毎回変わる
public final class ParkourCourses {
    // 肋骨の谷: 西の前室の戸口から、第一の休み場の戸口まで
    public static final Spec A = new Spec(
            List.of(new int[]{-24, 93, -23}, new int[]{-24, 93, -22}, new int[]{-24, 93, -21}),
            List.of(new int[]{-31, 97, -34}, new int[]{-30, 97, -34}, new int[]{-29, 97, -34}),
            -36, -26, -32, -13, 93, 98, 9, 14, 2);
    // 竪穴: 第一の休み場の戸口から、第二の休み場の戸口まで (登っていく)
    public static final Spec B = new Spec(
            List.of(new int[]{-30, 97, -44}, new int[]{-29, 97, -44}, new int[]{-28, 97, -44}),
            List.of(new int[]{-24, 100, -56}, new int[]{-23, 100, -56}, new int[]{-22, 100, -56}),
            -31, -22, -54, -46, 96, 100, 8, 13, 2);
    // 崩れ橋: 第二の休み場からの通路の先から、共鳴石の岩棚まで (下っていく)
    public static final Spec C = new Spec(
            List.of(new int[]{-18, 100, -52}, new int[]{-17, 100, -52}, new int[]{-16, 100, -52}),
            List.of(new int[]{-16, 97, -31}, new int[]{-15, 97, -31}, new int[]{-14, 97, -31}, new int[]{-13, 97, -31}, new int[]{-12, 97, -31},
                    new int[]{-16, 97, -30}, new int[]{-15, 97, -30}, new int[]{-14, 97, -30}, new int[]{-13, 97, -30}, new int[]{-12, 97, -30}),
            -19, -11, -50, -33, 96, 100, 7, 11, 1);
    public static final List<Spec> ALL = List.of(A, B, C);

    private static long cachedSeed = Long.MIN_VALUE;
    private static List<List<int[]>> cached;

    private ParkourCourses() {
    }

    // 種から三つの谷の足場を作る (同じ種なら同じ形)
    public static List<List<int[]>> forSeed(long seed) {
        if (seed != cachedSeed) {
            cached = List.of(
                    CourseGenerator.generate(A, seed * 31 + 1),
                    CourseGenerator.generate(B, seed * 31 + 2),
                    CourseGenerator.generate(C, seed * 31 + 3));
            cachedSeed = seed;
        }
        return cached;
    }

    public static List<List<int[]>> current(ServerLevel level) {
        return forSeed(TitanBackData.get(level).courseSeed());
    }
}
