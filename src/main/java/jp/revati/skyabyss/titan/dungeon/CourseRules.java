package jp.revati.skyabyss.titan.dungeon;

// アスレチックで「跳んで届く」とみなす距離。足場 p の上から足場 q の上へ (どちらもブロックの座標)
public final class CourseRules {
    private CourseRules() {
    }

    public static boolean reachable(int[] p, int[] q) {
        int dy = q[1] - p[1];
        double d = Math.hypot(q[0] - p[0], q[2] - p[2]);
        if (dy > 1 || dy < -1 || d < 2) {
            return false;
        }
        // 1段上へは走り跳びで約3マス、同じ高さなら4マス、1段下へなら少し遠くまで
        double limit = dy == 1 ? 3.2 : dy == 0 ? 4.0 : 4.3;
        return d <= limit;
    }
}
