package jp.revati.skyabyss.titan.dungeon;

import java.util.List;

// 西の翼のアスレチックの谷ごとの指定 (入口・出口の岩棚と、足場を置ける範囲)。版 18 で足場の並びは筋の通った一本の道 (ParkourCourses) になり、
// 毎回ちがう乱数の道はやめた。ここには指定の形と、跳べる距離の決まり (CourseRules) の確かめだけを残す
public final class CourseGenerator {
    public record Spec(List<int[]> start, List<int[]> end, int x1, int x2, int z1, int z2, int yMin, int yMax) {
    }

    private CourseGenerator() {
    }

    // 足場 p から q へ、歩いて (隣り合う) か跳んで行けるか。隣り合う (水平 2 未満) なら高さの差 1 まで歩ける
    public static boolean connected(int[] p, int[] q) {
        int dy = q[1] - p[1];
        double d = Math.hypot(q[0] - p[0], q[2] - p[2]);
        if (d < 2) {
            return Math.abs(dy) <= 1;
        }
        return CourseRules.reachable(p, q);
    }
}
