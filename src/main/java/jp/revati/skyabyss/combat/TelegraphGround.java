package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.Map;

// 予告の床の印を「歩ける床の面にだけ」置くための、列ごとの床の選び方。画面にも Minecraft にも触れない計算だけなので、
// サーバーの自己テストが作り物の地形で確かめられる (TelegraphGroundTest)。
//
// 1 ブロックの列 (x, z) を半マス (0.5) の 4 つの小区画に分け、小区画ごとに「床の高さ」を 1 つ決める。
//   - 探す幅: 印の床の高さ floor から -3 〜 +2 マス (BELOW / ABOVE)。それより高い壁・柱の上、低い穴の底には置かない
//   - 床 = 固い面の上 (当たり判定の箱の上面)。その上に 1.8 マス以上の空きがあること (人が立てる)。天井の上面・壁の上は床でない
//   - 水・溶岩は、その面 (流体の高さ) を床とする (水面に印が出る)
//   - 小区画に床が無い (空中・穴・壁の中) = NaN。印は描かない
// 半ブロック・階段は、当たり判定の箱の上面をそのまま使う (階段の上の段は 1.0、下の段は 0.5)。
// 同じ列に床が 2 つ以上あるときは、高い方 (探す幅の中の)。モブ・アイテムなど、ブロックでないものは見ない。
public final class TelegraphGround {
    public static final double BELOW = 3.0;
    public static final double ABOVE = 2.0;
    public static final double HEADROOM = 1.8;
    private static final double EPS = 1e-4;

    // 世界の見え方 (本物は Level、テストは作り物)
    public interface Blocks {
        // その場所のブロックの当たり判定の箱 (ブロックの中の座標。{minX, minY, minZ, maxX, maxY, maxZ})。空 (空気) は長さ 0 の配列
        double[][] boxes(int x, int y, int z);

        // その列の読み込みが済んでいるか (済んでいなければ、描くのは先延ばし)
        boolean loaded(int x, int z);
    }

    private static final double[][] NONE = new double[0][];

    private final Blocks blocks;
    private final double floor;
    private final Map<Long, float[]> columns = new HashMap<>();
    private double flat = Double.NaN;
    private boolean flatKnown;

    public TelegraphGround(Blocks blocks, double floor) {
        this.blocks = blocks;
        this.floor = floor;
    }

    public double floor() {
        return floor;
    }

    // 列の 4 つの小区画の床 (添字 = sx + 2 * sz。sx・sz は 0 か 1)。読み込み前は null (まだ決めない)
    public float[] column(int bx, int bz) {
        long key = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
        float[] c = columns.get(key);
        if (c != null) {
            return c;
        }
        if (!blocks.loaded(bx, bz)) {
            return null;
        }
        c = compute(blocks, bx, bz, floor);
        columns.put(key, c);
        return c;
    }

    // その点の床の高さ (無ければ NaN)。粒など点で置くもの用
    public double heightAt(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        float[] c = column(bx, bz);
        if (c == null) {
            return floor;
        }
        int sx = x - bx >= 0.5 ? 1 : 0;
        int sz = z - bz >= 0.5 ? 1 : 0;
        return c[sx + 2 * sz];
    }

    // 範囲 (bounds = {minX, minZ, maxX, maxZ}) の全部が同じ高さの平らな床なら、その高さ。そうでなければ NaN。
    // 読み込みが終わっていない間は決めない (isFlatKnown が false)。広間のように平らなら、描くときの刻みを一切せず速い
    public double flatHeight(double[] bounds) {
        if (flatKnown) {
            return flat;
        }
        int x0 = (int) Math.floor(bounds[0]);
        int x1 = (int) Math.floor(bounds[2] - 1e-9);
        int z0 = (int) Math.floor(bounds[1]);
        int z1 = (int) Math.floor(bounds[3] - 1e-9);
        // 広すぎる範囲は調べずに平らでないとする (壊れた入力)
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 20000) {
            flatKnown = true;
            return flat;
        }
        double h = Double.NaN;
        boolean first = true;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                float[] c = column(x, z);
                if (c == null) {
                    return Double.NaN;
                }
                for (float v : c) {
                    if (Float.isNaN(v)) {
                        flatKnown = true;
                        return flat;
                    }
                    if (first) {
                        h = v;
                        first = false;
                    } else if (Math.abs(v - h) > EPS) {
                        flatKnown = true;
                        return flat;
                    }
                }
            }
        }
        flat = h;
        flatKnown = true;
        return flat;
    }

    public static boolean uniform(float[] c) {
        return same(c[0], c[1]) && same(c[0], c[2]) && same(c[0], c[3]);
    }

    public static boolean same(float a, float b) {
        return Float.isNaN(a) ? Float.isNaN(b) : !Float.isNaN(b) && Math.abs(a - b) <= EPS;
    }

    // ---- 列の床を決める ----

    public static float[] compute(Blocks blocks, int bx, int bz, double floor) {
        float[] out = new float[4];
        double lo = floor - BELOW - EPS;
        double hi = floor + ABOVE + EPS;
        int yMax = (int) Math.floor(hi);
        int yMin = (int) Math.floor(lo - 1);
        for (int s = 0; s < 4; s++) {
            double cx = bx + (s & 1) * 0.5 + 0.25;
            double cz = bz + (s >> 1) * 0.5 + 0.25;
            out[s] = Float.NaN;
            for (int y = yMax; y >= yMin; y--) {
                double top = topAt(blocks.boxes(bx, y, bz), bx, bz, cx, cz);
                if (Double.isNaN(top)) {
                    continue;
                }
                double surface = y + top;
                if (surface > hi || surface < lo) {
                    continue;
                }
                // 上の空き: 上の 2 ブロックのうち、この小区画を覆う箱の下面のいちばん低い所
                double ceiling = Double.POSITIVE_INFINITY;
                for (int up = 1; up <= 2; up++) {
                    double bottom = bottomAt(blocks.boxes(bx, y + up, bz), bx, bz, cx, cz);
                    if (!Double.isNaN(bottom)) {
                        ceiling = Math.min(ceiling, y + up + bottom);
                    }
                }
                if (ceiling - surface < HEADROOM) {
                    continue;
                }
                // 低い天井の上面は床でない: この固まりの下に、人が立てない狭い空きがあるなら、いまの面は天井の上
                if (coversTightGap(blocks, bx, bz, cx, cz, y, yMin)) {
                    continue;
                }
                // 候補のうち、印の高さにいちばん近いもの (同じなら高い方)
                if (Float.isNaN(out[s]) || Math.abs(surface - floor) < Math.abs(out[s] - floor) - EPS) {
                    out[s] = (float) surface;
                }
            }
        }
        return out;
    }

    // y のブロックから続く固い固まりの底の下に、頭が入らない空き (HEADROOM 未満) があるか。底に当たらなければ (地面まで詰まっていれば) false
    private static boolean coversTightGap(Blocks blocks, int bx, int bz, double cx, double cz, int y, int yMin) {
        int low = y;
        while (low > yMin - 4 && !Double.isNaN(topAt(blocks.boxes(bx, low - 1, bz), bx, bz, cx, cz))) {
            low--;
        }
        if (low <= yMin - 4) {
            return false;
        }
        double[][] lowest = blocks.boxes(bx, low, bz);
        double bottom = bottomAt(lowest, bx, bz, cx, cz);
        if (Double.isNaN(bottom)) {
            return false;
        }
        double undersideY = low + bottom;
        // 固まりの底より下を探す: 下のブロックは空気で始まるはず
        for (int yy = low - 1; yy >= low - 4; yy--) {
            double top = topAt(blocks.boxes(bx, yy, bz), bx, bz, cx, cz);
            if (!Double.isNaN(top)) {
                return undersideY - (yy + top) < HEADROOM;
            }
        }
        return false;
    }

    // 小区画の中心を覆う箱の上面のいちばん高い所 (ブロックの中の高さ)。覆う箱が無ければ NaN
    private static double topAt(double[][] boxes, int bx, int bz, double cx, double cz) {
        double top = Double.NaN;
        for (double[] b : boxes) {
            if (covers(b, bx, bz, cx, cz) && (Double.isNaN(top) || b[4] > top)) {
                top = b[4];
            }
        }
        return top;
    }

    private static double bottomAt(double[][] boxes, int bx, int bz, double cx, double cz) {
        double bottom = Double.NaN;
        for (double[] b : boxes) {
            if (covers(b, bx, bz, cx, cz) && (Double.isNaN(bottom) || b[1] < bottom)) {
                bottom = b[1];
            }
        }
        return bottom;
    }

    private static boolean covers(double[] b, int bx, int bz, double cx, double cz) {
        double lx = cx - bx;
        double lz = cz - bz;
        return b[4] > b[1] && lx > b[0] && lx < b[3] && lz > b[2] && lz < b[5];
    }

    public static double[][] none() {
        return NONE;
    }

    // ---- 多角形を矩形で切る (Sutherland-Hodgman) ----

    // in = {x0, z0, x1, z1, ...} の n 頂点を、矩形 [x0, x1] × [z0, z1] で切り、out に書く。戻りは頂点の数 (out は 2 * (n + 4) 以上)
    public static int clipRect(double[] in, int n, double minX, double minZ, double maxX, double maxZ, double[] out, double[] tmp) {
        int m = clipEdge(in, n, 0, minX, true, tmp);
        m = clipEdge(tmp, m, 0, maxX, false, out);
        m = clipEdge(out, m, 1, minZ, true, tmp);
        return clipEdge(tmp, m, 1, maxZ, false, out);
    }

    // 1 本の軸 (axis 0 = x, 1 = z) の線で切る。keepGreater = 線より大きい側を残す
    private static int clipEdge(double[] in, int n, int axis, double at, boolean keepGreater, double[] out) {
        int m = 0;
        if (n == 0) {
            return 0;
        }
        double px = in[2 * (n - 1)];
        double pz = in[2 * (n - 1) + 1];
        double pv = axis == 0 ? px : pz;
        boolean pin = keepGreater ? pv >= at : pv <= at;
        for (int i = 0; i < n; i++) {
            double cx = in[2 * i];
            double cz = in[2 * i + 1];
            double cv = axis == 0 ? cx : cz;
            boolean cin = keepGreater ? cv >= at : cv <= at;
            if (cin != pin) {
                double t = (at - pv) / (cv - pv);
                out[2 * m] = axis == 0 ? at : px + (cx - px) * t;
                out[2 * m + 1] = axis == 0 ? pz + (cz - pz) * t : at;
                m++;
            }
            if (cin) {
                out[2 * m] = cx;
                out[2 * m + 1] = cz;
                m++;
            }
            px = cx;
            pz = cz;
            pv = cv;
            pin = cin;
        }
        return m;
    }
}
