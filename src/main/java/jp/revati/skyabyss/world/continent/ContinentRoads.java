package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

// 道の均し (2026-10-26 の試遊「道がおかしいときがある」)。道は地形の起伏をそのままなぞっていたので、坂で段々になり、
// 横に傾いた所で縁の砂利が段から飛び出していた。道の線に沿って高さをならし (前後 STEPS 点の平均と、1 ブロックあたり MAX_GRADE までの勾配)、
// 道の幅 (CORE) はその高さに、外へ FADE まで元の地形へなじませる (切り通しと盛り土)。水の上 (川・海) の点はならさない (橋はそのまま)。
// ContinentMap.surface が使う (どの所でも同じ高さ。建物の置き場・高さの図も同じ値)
final class ContinentRoads {
    static final double SPACING = 2.0;
    static final int STEPS = 9;
    static final double MAX_GRADE = 0.34;
    // 道を幅 8 にしたので、ならす幅も広げた (2026-10-28)
    static final double CORE = 5.4;
    static final double FADE = 10.0;
    static final int CELL = 32;
    // 道の端がこの距離より近い別の道に合わさるとき、端の高さをその道に合わせる
    static final double JOIN = 16.0;

    // 道の点 (x, z, 均した高さ。NaN はならさない点)
    private final double[] xs;
    private final double[] zs;
    private final double[] hs;
    // 升 (CELL 四方) → その升にかかる点の番号
    private final Map<Long, int[]> grid = new HashMap<>();

    private ContinentRoads(double[] xs, double[] zs, double[] hs) {
        this.xs = xs;
        this.zs = zs;
        this.hs = hs;
        Map<Long, List<Integer>> tmp = new HashMap<>();
        int reach = (int) Math.ceil(FADE + SPACING);
        for (int i = 0; i < xs.length; i++) {
            if (Double.isNaN(hs[i])) {
                continue;
            }
            int cx0 = Math.floorDiv((int) Math.floor(xs[i]) - reach, CELL);
            int cx1 = Math.floorDiv((int) Math.floor(xs[i]) + reach, CELL);
            int cz0 = Math.floorDiv((int) Math.floor(zs[i]) - reach, CELL);
            int cz1 = Math.floorDiv((int) Math.floor(zs[i]) + reach, CELL);
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    tmp.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(i);
                }
            }
        }
        for (Map.Entry<Long, List<Integer>> e : tmp.entrySet()) {
            grid.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // 道の点を作り、高さをならす (raw = ならす前の地面の高さ)
    static ContinentRoads build(List<List<BlockPos>> roads, java.util.function.BiFunction<Double, Double, Double> raw) {
        List<double[]> pts = new ArrayList<>();
        List<List<double[]>> lines = new ArrayList<>();
        List<double[]> avgs = new ArrayList<>();
        List<boolean[]> wets = new ArrayList<>();
        for (List<BlockPos> line : roads) {
            List<double[]> ln = new ArrayList<>();
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
                int n = Math.max(1, (int) Math.ceil(len / SPACING));
                for (int k = 0; k < n; k++) {
                    double t = k / (double) n;
                    ln.add(new double[] {a.getX() + (b.getX() - a.getX()) * t, a.getZ() + (b.getZ() - a.getZ()) * t, 0});
                }
            }
            if (!line.isEmpty()) {
                BlockPos e = line.get(line.size() - 1);
                ln.add(new double[] {e.getX(), e.getZ(), 0});
            }
            int n = ln.size();
            double[] h = new double[n];
            boolean[] wet = new boolean[n];
            for (int i = 0; i < n; i++) {
                h[i] = raw.apply(ln.get(i)[0] + 0.5, ln.get(i)[1] + 0.5);
                wet[i] = h[i] < ContinentChunkGenerator.SEA_LEVEL;
            }
            // 平均 (水の上の点は数えない)
            double[] avg = new double[n];
            for (int i = 0; i < n; i++) {
                double s = 0;
                int c = 0;
                for (int k = Math.max(0, i - STEPS); k <= Math.min(n - 1, i + STEPS); k++) {
                    if (!wet[k]) {
                        s += h[k];
                        c++;
                    }
                }
                avg[i] = c == 0 ? h[i] : s / c;
            }
            // 勾配の上限 (前から・後ろから。高い方を削る)
            double step = MAX_GRADE * SPACING;
            for (int i = 1; i < n; i++) {
                avg[i] = Math.min(avg[i], avg[i - 1] + step);
            }
            for (int i = n - 2; i >= 0; i--) {
                avg[i] = Math.min(avg[i], avg[i + 1] + step);
            }
            lines.add(ln);
            avgs.add(avg);
            wets.add(wet);
        }
        // 道の合わさる所 (2026-11-07 の自己テスト island trail: 外れの道が今の道に合わさる所で 2〜3 の段): 端が別の道のそば (JOIN 以内) なら、
        // その道の高さから 1 点ごとに勾配の上限までしか離れないよう、端から内へ寄せる
        double step = MAX_GRADE * SPACING;
        for (int li = 0; li < lines.size(); li++) {
            List<double[]> ln = lines.get(li);
            double[] avg = avgs.get(li);
            int n = ln.size();
            for (int end = 0; end < 2 && n > 1; end++) {
                double[] tip = ln.get(end == 0 ? 0 : n - 1);
                double target = Double.NaN;
                double bestD = JOIN * JOIN;
                for (int lj = 0; lj < lines.size(); lj++) {
                    if (lj == li) {
                        continue;
                    }
                    List<double[]> other = lines.get(lj);
                    for (int k = 0; k < other.size(); k++) {
                        double dx = other.get(k)[0] - tip[0];
                        double dz = other.get(k)[1] - tip[1];
                        double d2 = dx * dx + dz * dz;
                        if (d2 < bestD && !wets.get(lj)[k]) {
                            bestD = d2;
                            target = avgs.get(lj)[k];
                        }
                    }
                }
                if (Double.isNaN(target)) {
                    continue;
                }
                for (int k = 0; k < n; k++) {
                    int i = end == 0 ? k : n - 1 - k;
                    double lim = step * k;
                    avg[i] = Math.max(target - lim, Math.min(target + lim, avg[i]));
                }
            }
        }
        for (int li = 0; li < lines.size(); li++) {
            List<double[]> ln = lines.get(li);
            double[] avg = avgs.get(li);
            boolean[] wet = wets.get(li);
            for (int i = 0; i < ln.size(); i++) {
                boolean flat = flattened(ln.get(i)[0], ln.get(i)[1]);
                pts.add(new double[] {ln.get(i)[0], ln.get(i)[1], wet[i] || flat ? Double.NaN : Math.max(avg[i], ContinentChunkGenerator.SEA_LEVEL)});
            }
        }
        double[] xs = new double[pts.size()];
        double[] zs = new double[pts.size()];
        double[] hs = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            xs[i] = pts.get(i)[0];
            zs[i] = pts.get(i)[1];
            hs[i] = pts.get(i)[2];
        }
        return new ContinentRoads(xs, zs, hs);
    }

    // 都・町・跡地の平らにした所 (gen_continent の flatten の内側の半径) は、道をならさない (平らな所の高さを変えない)
    static boolean flattened(double x, double z) {
        ContinentLayout lay = ContinentLayout.get();
        if (Math.hypot(x - lay.capital().getX(), z - lay.capital().getZ()) < 140) {
            return true;
        }
        for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
            BlockPos c = lay.town(t);
            if (c != null && Math.hypot(x - c.getX(), z - c.getZ()) < 56) {
                return true;
            }
            BlockPos s = lay.site(t);
            if (s != null && Math.hypot(x - s.getX(), z - s.getZ()) < 48) {
                return true;
            }
        }
        return false;
    }

    // 道のそばなら、ならした高さへ寄せる (raw = 揺らぎを足した地面の高さ)
    double grade(int x, int z, double raw) {
        int[] idx = grid.get(key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
        if (idx == null) {
            return raw;
        }
        double best = Double.MAX_VALUE;
        double px = x + 0.5;
        double pz = z + 0.5;
        // 近い点の高さを、近いほど重く平均する (道が合わさる所で、二本の道の高さの違いが段にならない)
        double sum = 0;
        double wsum = 0;
        for (int i : idx) {
            double d2 = (xs[i] - px) * (xs[i] - px) + (zs[i] - pz) * (zs[i] - pz);
            if (d2 < best) {
                best = d2;
            }
            if (d2 < FADE * FADE) {
                double wi = 1.0 / (1.0 + d2);
                sum += hs[i] * wi;
                wsum += wi;
            }
        }
        double d = Math.sqrt(best);
        if (wsum <= 0 || d >= FADE) {
            return raw;
        }
        double w = d <= CORE ? 1.0 : 1.0 - (d - CORE) / (FADE - CORE);
        w = w * w * (3 - 2 * w);
        // 道の高さは整数に (道の面が段にならない)
        double road = Math.floor(sum / wsum) + 0.5;
        return raw * (1 - w) + road * w;
    }
}
