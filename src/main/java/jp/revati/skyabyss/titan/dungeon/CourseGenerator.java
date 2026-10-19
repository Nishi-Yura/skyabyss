package jp.revati.skyabyss.titan.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// アスレチックの足場を、種から毎回ちがう形に作る。
// 作った道は必ず次の決まりを守る:
//  - 足場から次の足場へ跳べる (高さの差は ±1、横の距離は高さに応じた上限まで)
//  - ひとつ前の足場 (や入口の岩棚) から先の足場へは届かない (足場を飛ばす近道がない)
//  - 足場どうしは横に2マス以上離れている (頭の上が空いていて、まとめて飛び越えられない)
//  - 最後の足場から出口の岩棚へ跳べ、それより前からは届かない
// 道は、部屋の中にいくつか置いた「寄り道の目印」を順にめぐってから出口へ向かうので、長く曲がりくねる
public final class CourseGenerator {
    // 入口・出口の岩棚、足場を置ける範囲、高さの範囲、足場の数の範囲
    public record Spec(List<int[]> start, List<int[]> end, int x1, int x2, int z1, int z2, int yMin, int yMax,
                       int minSteps, int maxSteps, int detours) {
    }

    private static final int[] DY = {-1, 0, 1};

    private CourseGenerator() {
    }

    // 足場の並び ({x, y, z, 崩れるなら1}) を返す。足場はすべて崩れる骨。同じ種からは同じ道になる
    public static List<int[]> generate(Spec spec, long seed) {
        return generate(spec, seed, List.of());
    }

    // 別のレーンの足場 (avoid) から水平に LANE_GAP 以上離して作る。レーンは同じ入口の岩棚から同じ出口の岩棚へ行く並列の道で、
    // 隣のレーンへ跳び移って足場を飛ばせないようにする (26 の 3.1)。作れなければ例外
    public static List<int[]> generate(Spec spec, long seed, List<int[]> avoid) {
        Random random = new Random(seed);
        // 隣のレーンを避けるときは、作れなければ早く諦める (呼ぶ側が別の種で試し直す)
        int tries = avoid.isEmpty() ? 2000 : 300;
        for (int attempt = 0; attempt < tries; attempt++) {
            List<int[]> steps = attempt(spec, random, avoid);
            if (steps != null) {
                // すべての足場を崩れる骨にする (入口と出口の岩棚は石のまま、ここには含まれない)
                for (int[] step : steps) {
                    step[3] = 1;
                }
                return steps;
            }
        }
        throw new IllegalStateException("Could not generate a parkour course for seed " + seed);
    }

    // 隣のレーンとの水平の距離の下限
    public static final double LANE_GAP = 4.5;

    private static List<int[]> attempt(Spec spec, Random random, List<int[]> avoid) {
        // 寄り道の目印: 部屋の中のランダムな地点
        List<int[]> targets = new ArrayList<>();
        for (int i = 0; i < spec.detours(); i++) {
            targets.add(new int[]{spec.x1() + random.nextInt(spec.x2() - spec.x1() + 1), 0, spec.z1() + random.nextInt(spec.z2() - spec.z1() + 1)});
        }
        int[] endCenter = spec.end().get(spec.end().size() / 2);
        targets.add(endCenter);

        List<int[]> path = new ArrayList<>();
        int target = 0;
        int[] cur = null;
        while (path.size() <= spec.maxSteps()) {
            // 出口に届き、それより前からは届かなければ完成
            if (cur != null && path.size() >= spec.minSteps() && target == targets.size() - 1
                    && reachesAny(cur, spec.end()) && !earlierReach(spec, path, spec.end())) {
                return path;
            }
            int[] goal = targets.get(target);
            List<int[]> options = new ArrayList<>();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy : DY) {
                        int[] q = cur == null ? null : new int[]{cur[0] + dx, cur[1] + dy, cur[2] + dz, 0};
                        if (cur == null) {
                            // 最初の一歩は、入口の岩棚のどこかから跳べればよい
                            int[] s = spec.start().get(spec.start().size() / 2);
                            q = new int[]{s[0] + dx, s[1] + dy, s[2] + dz, 0};
                            if (!reachedFromAny(spec.start(), q)) {
                                continue;
                            }
                        } else if (!CourseRules.reachable(cur, q)) {
                            continue;
                        }
                        if (valid(spec, path, q, avoid)) {
                            options.add(q);
                        }
                    }
                }
            }
            if (options.isEmpty()) {
                return null;
            }
            // 目印に近づく候補を優先しつつ、少しゆらぎを入れる。高さは出口の高さへ寄せていく
            int[] best = null;
            double bestScore = Double.MAX_VALUE;
            for (int[] q : options) {
                double d = Math.hypot(q[0] - goal[0], q[2] - goal[2]);
                double heightNeed = Math.abs(endCenter[1] - q[1]);
                int left = Math.max(1, spec.minSteps() - path.size());
                double heightPenalty = heightNeed > left ? (heightNeed - left) * 6 : 0;
                double score = d + heightPenalty + random.nextDouble() * 2.5;
                if (score < bestScore) {
                    bestScore = score;
                    best = q;
                }
            }
            path.add(best);
            cur = best;
            if (target < targets.size() - 1 && Math.hypot(cur[0] - goal[0], cur[2] - goal[2]) < 3.5) {
                target++;
            }
        }
        return null;
    }

    // 新しい足場 q を置けるか
    private static boolean valid(Spec spec, List<int[]> path, int[] q, List<int[]> avoid) {
        if (q[0] < spec.x1() || q[0] > spec.x2() || q[2] < spec.z1() || q[2] > spec.z2() || q[1] < spec.yMin() || q[1] > spec.yMax()) {
            return false;
        }
        // (入口・出口の岩棚のそば 6 マスは、どのレーンも同じ岩棚を使うので、離さない)
        boolean nearLedge = false;
        for (List<int[]> ledge : List.of(spec.start(), spec.end())) {
            for (int[] b : ledge) {
                nearLedge |= Math.hypot(b[0] - q[0], b[2] - q[2]) < 6;
            }
        }
        if (!nearLedge) {
            for (int[] b : avoid) {
                if (Math.hypot(b[0] - q[0], b[2] - q[2]) < LANE_GAP) {
                    return false;
                }
            }
        }
        // 入口・出口の岩棚、これまでの足場から、横に2マス以上離す
        for (List<int[]> group : List.of(spec.start(), spec.end(), path)) {
            for (int[] b : group) {
                if (Math.max(Math.abs(b[0] - q[0]), Math.abs(b[2] - q[2])) < 2) {
                    return false;
                }
            }
        }
        // ひとつ前より昔の足場 (と入口の岩棚) から届いてしまうと、足場を飛ばせるので不可
        if (!path.isEmpty()) {
            if (anyReach(q, spec.start()) && path.size() >= 1) {
                return false;
            }
            for (int i = 0; i < path.size() - 1; i++) {
                if (CourseRules.reachable(path.get(i), q)) {
                    return false;
                }
            }
        }
        // 出口の岩棚に、最後の一歩より前に届いてしまう場所は、最後の一歩でしか置けない
        return true;
    }

    // p から blocks のどれかへ跳べるか
    private static boolean reachesAny(int[] p, List<int[]> blocks) {
        for (int[] b : blocks) {
            if (CourseRules.reachable(p, b)) {
                return true;
            }
        }
        return false;
    }

    // blocks のどれかから q へ跳べるか
    private static boolean reachedFromAny(List<int[]> blocks, int[] q) {
        for (int[] b : blocks) {
            if (CourseRules.reachable(b, q)) {
                return true;
            }
        }
        return false;
    }

    // どちら向きでも届くか (近道の判定は厳しめにする)
    private static boolean anyReach(int[] q, List<int[]> blocks) {
        for (int[] b : blocks) {
            if (CourseRules.reachable(b, q) || CourseRules.reachable(q, b)) {
                return true;
            }
        }
        return false;
    }

    // 最後の足場より前 (入口の岩棚を含む) から、blocks のどれかに届くか
    private static boolean earlierReach(Spec spec, List<int[]> path, List<int[]> blocks) {
        for (int[] b : blocks) {
            for (int[] s : spec.start()) {
                if (CourseRules.reachable(s, b)) {
                    return true;
                }
            }
            for (int i = 0; i < path.size() - 1; i++) {
                if (CourseRules.reachable(path.get(i), b)) {
                    return true;
                }
            }
        }
        return false;
    }
}
