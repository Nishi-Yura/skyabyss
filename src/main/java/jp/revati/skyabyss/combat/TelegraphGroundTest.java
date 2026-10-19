package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 床の印の「歩ける床だけに出す」選び方 (TelegraphGround) の自己テスト (TelegraphSelfTest から呼ぶ。作り物の地形で、画面にも世界にも触れない)。
// 床の高さ 64 (= y 63 のブロックの上面) を基準に、列ごとの床を数値で確かめる。
final class TelegraphGroundTest {
    private static final double F = 64;
    private static final double[] FULL = {0, 0, 0, 1, 1, 1};

    private TelegraphGroundTest() {
    }

    // 作り物の地形: 登録したブロックだけ。何も無い所は空気
    private static final class World implements TelegraphGround.Blocks {
        final Map<Long, double[][]> map = new HashMap<>();
        boolean loaded = true;

        void put(int x, int y, int z, double[]... boxes) {
            map.put(key(x, y, z), boxes);
        }

        void fill(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    put(x, y, z, FULL);
                }
            }
        }

        @Override
        public double[][] boxes(int x, int y, int z) {
            double[][] b = map.get(key(x, y, z));
            return b == null ? TelegraphGround.none() : b;
        }

        @Override
        public boolean loaded(int x, int z) {
            return loaded;
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }
    }

    // 平らな床 (y 63) を 0..15 に敷いた世界
    private static World flatWorld() {
        World w = new World();
        w.fill(0, 0, 15, 15, 63);
        return w;
    }

    static void run(List<String> f, List<String> notes) {
        int n = 0;
        World w = flatWorld();
        TelegraphGround g = new TelegraphGround(w, F);
        n += flat(g, f);

        // 1 段・2 段高い床にも出る。3 段 (壁) には出ない
        w = flatWorld();
        w.put(2, 64, 2, FULL);
        w.put(4, 64, 2, FULL);
        w.put(4, 65, 2, FULL);
        w.put(6, 64, 2, FULL);
        w.put(6, 65, 2, FULL);
        w.put(6, 66, 2, FULL);
        w.put(8, 64, 2, FULL);
        w.put(8, 65, 2, FULL);
        w.put(8, 66, 2, FULL);
        w.put(8, 67, 2, FULL);
        g = new TelegraphGround(w, F);
        n += expect(g, 2, 2, 65, "step 1 up", f);
        n += expect(g, 4, 2, 66, "step 2 up", f);
        n += expect(g, 6, 2, Double.NaN, "wall 3 high (top above the band)", f);
        n += expect(g, 8, 2, Double.NaN, "wall 4 high", f);
        n += expect(g, 5, 2, 64, "floor between the steps", f);
        check(!TelegraphGround.uniform(g.column(2, 2)) == false, "step column is uniform", f);

        // 穴: 2 段下は出る、4 段下は出ない
        w = flatWorld();
        w.put(3, 63, 3);
        w.put(3, 62, 3, FULL);
        w.put(5, 63, 5);
        w.put(5, 62, 5);
        w.put(5, 61, 5);
        w.put(5, 60, 5);
        w.put(5, 59, 5, FULL);
        g = new TelegraphGround(w, F);
        n += expect(g, 3, 3, 63, "pit 1 deep keeps a floor", f);
        n += expect(g, 5, 5, Double.NaN, "pit too deep (floor below -3)", f);

        // 床が無い (空中・縁の外)
        n += expect(g, 20, 20, Double.NaN, "void outside the floor", f);

        // 半ブロック・階段
        w = flatWorld();
        w.put(1, 64, 1, new double[] {0, 0, 0, 1, 0.5, 1});
        w.put(3, 64, 1, new double[] {0, 0, 0, 1, 0.5, 1}, new double[] {0.5, 0.5, 0, 1, 1, 1});
        g = new TelegraphGround(w, F);
        n += expect(g, 1, 1, 64.5, "bottom slab top surface", f);
        float[] stair = g.column(3, 1);
        check(Math.abs(stair[0] - 64.5) < 1e-4 && Math.abs(stair[1] - 65.0) < 1e-4 && Math.abs(stair[2] - 64.5) < 1e-4 && Math.abs(stair[3] - 65.0) < 1e-4,
                "stairs: low half 64.5 and high half 65.0 (got " + java.util.Arrays.toString(stair) + ")", f);
        check(!TelegraphGround.uniform(stair), "stairs column is not uniform", f);
        n += 3;

        // 天井: 2 段の空きがあれば床に出て、天井の上面には出ない。1 段の空きなら床も出ない (天井の上にも出ない)
        w = flatWorld();
        w.fill(0, 0, 3, 3, 66);
        w.fill(6, 0, 9, 3, 65);
        g = new TelegraphGround(w, F);
        n += expect(g, 1, 1, 64, "floor under a ceiling that leaves 2 free blocks", f);
        n += expect(g, 7, 1, Double.NaN, "no floor where the gap is 1 block, and nothing on top of that ceiling", f);
        // 天井がもっと高い
        w = flatWorld();
        w.fill(0, 0, 3, 3, 69);
        g = new TelegraphGround(w, F);
        n += expect(g, 1, 1, 64, "high ceiling is ignored", f);

        // 床の高さ 64 の 1 段上の床の下が空いている足場 (橋) の上にも出る
        w = flatWorld();
        w.put(8, 64, 8, FULL);
        w.put(8, 63, 8);
        w.put(8, 62, 8);
        w.put(8, 61, 8);
        w.put(8, 60, 8);
        g = new TelegraphGround(w, F);
        n += expect(g, 8, 8, 65, "a one-block pillar of air below is fine, top surface at +1", f);

        // 水: 水の面に出る
        w = new World();
        w.fill(0, 0, 4, 4, 62);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                w.put(x, 63, z, new double[] {0, 0, 0, 1, 0.89, 1});
            }
        }
        g = new TelegraphGround(w, F);
        check(Math.abs(g.heightAt(2.2, 2.2) - 63.89) < 1e-3, "water: decal sits on the water surface (got " + g.heightAt(2.2, 2.2) + ")", f);
        n++;

        // 平らな範囲の判定: 平ら / 段差あり / 壁あり / 読み込み前
        w = flatWorld();
        g = new TelegraphGround(w, F);
        check(Math.abs(g.flatHeight(new double[] {2.2, 2.2, 9.5, 9.5}) - 64) < 1e-6, "flatHeight: flat floor gives 64", f);
        w.put(5, 64, 5, FULL);
        g = new TelegraphGround(w, F);
        check(Double.isNaN(g.flatHeight(new double[] {2.2, 2.2, 9.5, 9.5})), "flatHeight: a step makes the area not flat", f);
        check(Math.abs(new TelegraphGround(w, F).flatHeight(new double[] {7, 7, 12, 12}) - 64) < 1e-6, "flatHeight: only the area counts (the step is outside)", f);
        w.loaded = false;
        g = new TelegraphGround(w, F);
        check(g.column(1, 1) == null && Double.isNaN(g.flatHeight(new double[] {2, 2, 4, 4})), "unloaded chunk: no column, flatHeight unknown", f);
        n += 4;

        // 切り出し: 円を段差のある床に置いて、床のある所の面積だけが残る (面積の合計 = 床のある小区画の面積の合計)
        w = flatWorld();
        w.put(6, 64, 6, FULL);
        w.put(6, 65, 6, FULL);
        w.put(6, 66, 6, FULL);
        w.put(7, 64, 6, FULL);
        w.put(8, 64, 6, new double[] {0, 0, 0, 1, 0.5, 1});
        g = new TelegraphGround(w, F);
        TelegraphShape circle = TelegraphShape.circle(7.5, 6.5, 3.2);
        double[] areas = clipArea(g, circle, 0);
        // 壁の列 (6,6) は無く、高さごとの面積の合計は壁の分だけ減る
        double total = Math.PI * 3.2 * 3.2;
        check(Math.abs(areas[0] + areas[1] + areas[2] - total + wallShare(circle)) < total * 0.03,
                "clipped circle area equals the circle minus the wall column (got " + (areas[0] + areas[1] + areas[2]) + ")", f);
        check(areas[0] > 0 && areas[1] > 0 && areas[2] > 0, "clipped circle covers flat (64), +1 (65) and slab (64.5) heights", f);
        check(areas[3] == 0, "no piece lands on the wall top (height 67)", f);
        n += 3;

        // 多角形の切り出しの面積が合う
        double[] in = {0.2, 0.1, 3.7, 0.6, 1.4, 3.3};
        double whole = polyArea(in, 3);
        double sum = 0;
        double[] out = new double[40];
        double[] tmp = new double[40];
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                int m = TelegraphGround.clipRect(in, 3, x, z, x + 1, z + 1, out, tmp);
                sum += polyArea(out, m);
            }
        }
        check(Math.abs(sum - whole) < 1e-9, "clipRect pieces sum to the triangle (" + sum + " vs " + whole + ")", f);
        n++;

        notes.add("ground: " + n + " column / clip checks (flat, steps +1 +2, walls, pits, slab, stairs, ceilings, water, flat shortcut)");
    }

    private static int flat(TelegraphGround g, List<String> f) {
        int n = 0;
        for (int x = 0; x < 16; x += 5) {
            n += expect(g, x, 3, 64, "flat floor", f);
        }
        float[] c = g.column(1, 1);
        check(TelegraphGround.uniform(c), "flat column is uniform", f);
        return n + 1;
    }

    private static int expect(TelegraphGround g, int x, int z, double want, String what, List<String> f) {
        float[] c = g.column(x, z);
        boolean ok = c != null;
        if (ok) {
            for (float v : c) {
                if (Double.isNaN(want) ? !Float.isNaN(v) : Float.isNaN(v) || Math.abs(v - want) > 1e-4) {
                    ok = false;
                }
            }
        }
        check(ok, what + ": want " + want + ", got " + (c == null ? "null" : java.util.Arrays.toString(c)), f);
        return 1;
    }

    // 円の面を TelegraphGround の列で切り、高さごとの面積を数える (0 = 64, 1 = 65, 2 = 64.5, 3 = 壁の上など)
    private static double[] clipArea(TelegraphGround g, TelegraphShape shape, int unused) {
        double[] areas = new double[4];
        double[] out = new double[40];
        double[] tmp = new double[40];
        double[] poly = new double[6];
        TelegraphGeometry.area(shape, (x0, z0, x1, z1, x2, z2) -> {
            poly[0] = x0;
            poly[1] = z0;
            poly[2] = x1;
            poly[3] = z1;
            poly[4] = x2;
            poly[5] = z2;
            int cx0 = (int) Math.floor(Math.min(x0, Math.min(x1, x2)));
            int cx1 = (int) Math.floor(Math.max(x0, Math.max(x1, x2)));
            int cz0 = (int) Math.floor(Math.min(z0, Math.min(z1, z2)));
            int cz1 = (int) Math.floor(Math.max(z0, Math.max(z1, z2)));
            for (int x = cx0; x <= cx1; x++) {
                for (int z = cz0; z <= cz1; z++) {
                    float[] c = g.column(x, z);
                    for (int s = 0; s < 4; s++) {
                        if (Float.isNaN(c[s])) {
                            continue;
                        }
                        double sx = x + (s & 1) * 0.5;
                        double sz = z + (s >> 1) * 0.5;
                        int m = TelegraphGround.clipRect(poly, 3, sx, sz, sx + 0.5, sz + 0.5, out, tmp);
                        double a = polyArea(out, m);
                        int idx = Math.abs(c[s] - 64) < 1e-4 ? 0 : Math.abs(c[s] - 65) < 1e-4 ? 1 : Math.abs(c[s] - 64.5) < 1e-4 ? 2 : 3;
                        areas[idx] += a;
                    }
                }
            }
        });
        return areas;
    }

    // 円のうち、壁の列 (6,6) にかかる面積 (あとで差し引く)。数値積分
    private static double wallShare(TelegraphShape shape) {
        TelegraphShape.Circle c = (TelegraphShape.Circle) shape;
        int hits = 0;
        int total = 0;
        int steps = 800;
        for (int i = 0; i < steps; i++) {
            for (int k = 0; k < steps; k++) {
                double x = 6 + (i + 0.5) / steps;
                double z = 6 + (k + 0.5) / steps;
                total++;
                if (c.contains(x, z)) {
                    hits++;
                }
            }
        }
        return (double) hits / total;
    }

    private static double polyArea(double[] p, int n) {
        double a = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a += p[2 * i] * p[2 * j + 1] - p[2 * j] * p[2 * i + 1];
        }
        return Math.abs(a) / 2;
    }

    private static void check(boolean ok, String message, List<String> f) {
        if (!ok) {
            f.add("ground: " + message);
        }
    }
}
