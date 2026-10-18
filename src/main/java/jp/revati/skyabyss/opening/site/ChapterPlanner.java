package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteFinder.Area;
import jp.revati.skyabyss.opening.site.SiteFinder.Ground;
import net.minecraft.core.BlockPos;

// 章の小要素の場所の決め方 (純粋な計算。地形は Ground の問い合わせだけで見るので、自己テストでは合成の地形で流せる)。
// 拠点 (base) のまわりに 120 度おきの 3 方向の扇を作り、要素を順に 3 方向へ配る。方向ごとに、拠点から minDist〜maxDist (どちらも 24〜150 に収める) の
// 陸 (水がない・群系がよい・高さの差が小さい) を探す。探して見つからなければ、高さの差・扇の開き・群系の条件を段階的にゆるめる
// (最後は群系を問わず 360 度、高さの差 80 まで。地形は SiteJob が実際にならす)。どの地形でも 2 つは 150 ブロック以内に置く、が目的。
// ほかの場所 (序盤の構造物・ほかの章の小要素) とは、半径 + 余白だけ離す。人の建物は、ここでは見えない (SiteJob が建てる直前に見て、あればずらす)。
final class ChapterPlanner {
    // 拠点から小要素までの水平距離の範囲 (どの要素もこの中に収める)
    static final int MIN_DIST = 24;
    static final int MAX_DIST = 150;
    // 進行方向の先に置くときの距離
    static final int AHEAD_MIN = 60;
    static final int AHEAD_MAX = 120;
    // 段階: {高さの差の上限, 扇の開き (片側の度数), 群系を見るか (1 = 見る)}
    static final int[][] STAGES = {{3, 20, 1}, {6, 40, 1}, {10, 70, 1}, {16, 110, 1}, {12, 180, 1}, {16, 180, 0}, {28, 180, 0}, {80, 180, 0}};

    // 一つの要素の一つ分の注文 (half = 部品の半分の大きさ、radius = ほかを避ける半径)
    record Req(String id, int index, int half, int radius, int minDist, int maxDist, Prefer prefer) {
    }

    record Obstacle(BlockPos pos, int radius) {
    }

    record Placed(String id, int index, BlockPos pos, int yaw, int radius) {
    }

    private ChapterPlanner() {
    }

    // 扇の最初の向き (ラジアン)。拠点の位置と章から決まる (同じ入力なら同じ)
    static double baseAngle(BlockPos base, int salt) {
        return new Random(base.asLong() * 31L + salt).nextDouble() * Math.PI * 2;
    }

    // reqs を順に置く。aheadAngle が NaN でなければ、最初の注文だけをその向きの 60〜120 ブロック先に置く (残りの注文は同じ拠点のまわりの扇)。
    // 置けなかった注文は結果に入らない
    static List<Placed> plan(Ground g, BlockPos base, List<Req> reqs, List<Obstacle> obstacles, double aheadAngle, int salt) {
        List<Obstacle> obs = new ArrayList<>(obstacles);
        boolean ahead = !Double.isNaN(aheadAngle);
        double a0 = ahead ? aheadAngle : baseAngle(base, salt);
        List<Placed> out = new ArrayList<>();
        for (int n = 0; n < reqs.size(); n++) {
            Req r = reqs.get(n);
            double angle = a0 + (n % 3) * Math.PI * 2 / 3;
            int dmin = Math.max(MIN_DIST, r.minDist());
            int dmax = Math.min(MAX_DIST, Math.max(dmin, r.maxDist()));
            if (ahead && n == 0) {
                dmin = AHEAD_MIN;
                dmax = AHEAD_MAX;
            }
            BlockPos at = findSpot(g, base, angle, dmin, dmax, r, obs);
            if (at == null) {
                continue;
            }
            int rot = SiteFinder.rotToward(base.getX() - at.getX(), base.getZ() - at.getZ(), true);
            out.add(new Placed(r.id(), r.index(), at, SiteFinder.encode(rot, false), r.radius()));
            obs.add(new Obstacle(at, r.radius()));
        }
        return out;
    }

    @Nullable
    static BlockPos findSpot(Ground g, BlockPos base, double angle, int dmin, int dmax, Req r, List<Obstacle> obs) {
        int mid = (dmin + dmax) / 2;
        for (int si = 0; si < STAGES.length; si++) {
            int[] st = STAGES[si];
            int step = si < 4 ? 10 : 15;
            List<Integer> dists = new ArrayList<>();
            for (int d = dmin; d <= dmax; d += step) {
                dists.add(d);
            }
            if (dists.isEmpty() || dists.get(dists.size() - 1) != dmax) {
                dists.add(dmax);
            }
            dists.sort(Comparator.comparingInt(d -> Math.abs(d - mid)));
            int want = r.prefer() == Prefer.ANY ? 1 : 8;
            BlockPos best = null;
            double bestScore = -1e9;
            int found = 0;
            search:
            for (int k = 0; k * 10 <= st[1] * 2; k++) {
                int off = ((k + 1) / 2) * 10 * (k % 2 == 1 ? 1 : -1);
                if (Math.abs(off) > st[1]) {
                    continue;
                }
                double a = angle + Math.toRadians(off);
                for (int d : dists) {
                    int x = base.getX() + (int) Math.round(Math.cos(a) * d);
                    int z = base.getZ() + (int) Math.round(Math.sin(a) * d);
                    if (blocked(x, z, r.radius(), obs)) {
                        continue;
                    }
                    Area ar = SiteFinder.area(g, x, z, r.half(), r.half(), st[0], false, st[2] == 1);
                    if (!ar.ok()) {
                        continue;
                    }
                    double score = switch (r.prefer()) {
                        case HIGH -> ar.median() - ar.range() * 0.5;
                        case SHORE -> shoreScore(g, x, z) * 10 - ar.range() * 0.5;
                        default -> 0;
                    } - Math.abs(off) * 0.01 - Math.abs(d - mid) * 0.005;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new BlockPos(x, ar.median() + 1, z);
                    }
                    if (++found >= want) {
                        break search;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    // 水に近いほど高い (まわり 8 方向の 14 ブロック先のうち、水の数)
    private static int shoreScore(Ground g, int x, int z) {
        int n = 0;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            if (g.wetCoarse(x + (int) Math.round(Math.cos(a) * 14), z + (int) Math.round(Math.sin(a) * 14))) {
                n++;
            }
        }
        return n;
    }

    private static boolean blocked(int x, int z, int radius, List<Obstacle> obs) {
        for (Obstacle o : obs) {
            if (Math.hypot(x - o.pos().getX(), z - o.pos().getZ()) < radius + o.radius() + 8) {
                return true;
            }
        }
        return false;
    }
}
