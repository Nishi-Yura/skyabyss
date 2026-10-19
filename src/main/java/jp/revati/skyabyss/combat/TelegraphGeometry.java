package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 予告の床の印 (クライアントの decal) の形を、三角形と線分に刻む。画面にも Minecraft にも触れない計算だけなので、
// サーバーの自己テストが「描く面 = TelegraphShape.contains」を標本の点で確かめられる。
//
//   area(shape, out)            当たる範囲の全体 (うすく敷く面)。波は通り道全体
//   fill(shape, p, out)         予告が p (0〜1) まで進んだときの満ちた面 (中心から・手前から)
//   swept(shape, p, out)        振り回す帯が、始めから p まで通ったあとの面
//   lanes(shape, out)           波の安全な筋 (輪が抜ける所)
//   outline(shape, out)         範囲の縁 (線分)
//   front(shape, p, out)        満ちていく先頭の線
//
// 面は三角形の組。円弧は弦の誤差が 0.05 マス以下になるよう刻む。
public final class TelegraphGeometry {
    private static final double TWO_PI = 2 * Math.PI;
    // 面を刻む最大の幅 (マス)。クライアントが頂点ごとに床の高さを引くので、段差に沿える細かさ
    private static final double CELL = 0.8;

    @FunctionalInterface
    public interface Tri {
        void tri(double x0, double z0, double x1, double z1, double x2, double z2);
    }

    @FunctionalInterface
    public interface Seg {
        void seg(double x0, double z0, double x1, double z1);
    }

    private TelegraphGeometry() {
    }

    // ---- 面 ----

    public static void area(TelegraphShape shape, Tri out) {
        switch (shape) {
            case TelegraphShape.Circle c -> sector(c.cx(), c.cz(), 0, c.r(), 0, TWO_PI, out);
            case TelegraphShape.Ring r -> sector(r.cx(), r.cz(), r.inner(), r.outer(), 0, TWO_PI, out);
            case TelegraphShape.Line l -> rect(l.x(), l.z(), l.angle(), 0, l.length(), -l.halfWidth(), l.halfWidth(), out);
            case TelegraphShape.Cone c -> sector(c.x(), c.z(), 0, c.range(), c.angle() - c.halfAngle(), c.angle() + c.halfAngle(), out);
            case TelegraphShape.Sweep s -> sweepArea(s, out);
            case TelegraphShape.Wave w -> bandSectors(w.cx(), w.cz(), w.r0() - w.halfThickness(), w.r1() + w.halfThickness(), w.gaps(), w.gapHalf(), out);
            case TelegraphShape.Band b -> bandSectors(b.cx(), b.cz(), b.r() - b.halfThickness(), b.r() + b.halfThickness(), b.gaps(), b.gapHalf(), out);
            default -> {
            }
        }
    }

    public static void fill(TelegraphShape shape, double progress, Tri out) {
        double p = clamp01(progress);
        switch (shape) {
            case TelegraphShape.Circle c -> sector(c.cx(), c.cz(), 0, c.r() * p, 0, TWO_PI, out);
            case TelegraphShape.Ring r -> sector(r.cx(), r.cz(), r.inner(), r.inner() + (r.outer() - r.inner()) * p, 0, TWO_PI, out);
            case TelegraphShape.Line l -> rect(l.x(), l.z(), l.angle(), 0, l.length() * p, -l.halfWidth(), l.halfWidth(), out);
            case TelegraphShape.Cone c -> sector(c.x(), c.z(), 0, c.range() * p, c.angle() - c.halfAngle(), c.angle() + c.halfAngle(), out);
            case TelegraphShape.Sweep s -> {
                double lo = Math.min(s.start(), s.start() + s.span());
                double hi = Math.max(s.start(), s.start() + s.span());
                sector(s.x(), s.z(), 0, s.length() * p, lo, hi, out);
            }
            // 波は、はじまりの輪が最初から全部見える (濃さで近づきを見せる)
            case TelegraphShape.Wave w -> area(w.at(0), out);
            default -> area(shape, out);
        }
    }

    // 振り回す帯が通ったあとの面 (始めの向きから、いまの向きまで)
    public static void swept(TelegraphShape shape, double progress, Tri out) {
        if (shape instanceof TelegraphShape.Sweep s) {
            double a1 = s.start() + s.span() * clamp01(progress);
            sector(s.x(), s.z(), 0, s.length(), Math.min(s.start(), a1), Math.max(s.start(), a1), out);
        }
    }

    public static void lanes(TelegraphShape shape, Tri out) {
        if (shape instanceof TelegraphShape.Wave w) {
            for (double[] iv : gapIntervals(w.gaps(), w.gapHalf())) {
                sector(w.cx(), w.cz(), Math.max(0, w.r0() - w.halfThickness()), w.r1() + w.halfThickness(), iv[0], iv[1], out);
            }
        }
    }

    // 振り回す帯は、動く範囲の全体 = 扇 + 始めと終わりの帯の外側の半分 (Line の幅が扇の外へはみ出すぶん)
    private static void sweepArea(TelegraphShape.Sweep s, Tri out) {
        double lo = Math.min(s.start(), s.start() + s.span());
        double hi = Math.max(s.start(), s.start() + s.span());
        sector(s.x(), s.z(), 0, s.length(), lo, hi, out);
        rect(s.x(), s.z(), lo, 0, s.length(), -s.halfWidth(), 0, out);
        rect(s.x(), s.z(), hi, 0, s.length(), 0, s.halfWidth(), out);
    }

    // ---- 線 ----

    public static void outline(TelegraphShape shape, Seg out) {
        switch (shape) {
            case TelegraphShape.Circle c -> arcSegs(c.cx(), c.cz(), c.r(), 0, TWO_PI, out);
            case TelegraphShape.Ring r -> {
                arcSegs(r.cx(), r.cz(), r.inner(), 0, TWO_PI, out);
                arcSegs(r.cx(), r.cz(), r.outer(), 0, TWO_PI, out);
            }
            case TelegraphShape.Line l -> rectSegs(l, out);
            case TelegraphShape.Cone c -> {
                radial(c.x(), c.z(), c.angle() - c.halfAngle(), 0, c.range(), out);
                radial(c.x(), c.z(), c.angle() + c.halfAngle(), 0, c.range(), out);
                arcSegs(c.x(), c.z(), c.range(), c.angle() - c.halfAngle(), c.angle() + c.halfAngle(), out);
            }
            case TelegraphShape.Sweep s -> {
                double lo = Math.min(s.start(), s.start() + s.span());
                double hi = Math.max(s.start(), s.start() + s.span());
                radial(s.x(), s.z(), lo, 0, s.length(), out);
                radial(s.x(), s.z(), hi, 0, s.length(), out);
                arcSegs(s.x(), s.z(), s.length(), lo, hi, out);
            }
            case TelegraphShape.Wave w -> {
                // 通り道の両端 (はじまりの輪と終わりの輪)
                bandEdges(w.cx(), w.cz(), w.r0(), w.gaps(), w.gapHalf(), out);
                bandEdges(w.cx(), w.cz(), w.r1(), w.gaps(), w.gapHalf(), out);
            }
            case TelegraphShape.Band b -> {
                bandEdges(b.cx(), b.cz(), b.r() - b.halfThickness(), b.gaps(), b.gapHalf(), out);
                bandEdges(b.cx(), b.cz(), b.r() + b.halfThickness(), b.gaps(), b.gapHalf(), out);
            }
            default -> {
            }
        }
    }

    public static void front(TelegraphShape shape, double progress, Seg out) {
        double p = clamp01(progress);
        switch (shape) {
            case TelegraphShape.Circle c -> arcSegs(c.cx(), c.cz(), Math.max(0.05, c.r() * p), 0, TWO_PI, out);
            case TelegraphShape.Ring r -> arcSegs(r.cx(), r.cz(), Math.max(0.05, r.inner() + (r.outer() - r.inner()) * p), 0, TWO_PI, out);
            case TelegraphShape.Line l -> {
                double along = l.length() * p;
                double c = Math.cos(l.angle());
                double s = Math.sin(l.angle());
                out.seg(l.x() + c * along + s * l.halfWidth(), l.z() + s * along - c * l.halfWidth(),
                        l.x() + c * along - s * l.halfWidth(), l.z() + s * along + c * l.halfWidth());
            }
            case TelegraphShape.Cone c -> arcSegs(c.x(), c.z(), Math.max(0.05, c.range() * p), c.angle() - c.halfAngle(), c.angle() + c.halfAngle(), out);
            case TelegraphShape.Sweep s -> {
                double lo = Math.min(s.start(), s.start() + s.span());
                double hi = Math.max(s.start(), s.start() + s.span());
                arcSegs(s.x(), s.z(), Math.max(0.05, s.length() * p), lo, hi, out);
            }
            default -> {
            }
        }
    }

    // 波の安全な筋の縁 (筋ごとに、輪の通り道を横切る 2 本の線)
    public static void laneEdges(TelegraphShape shape, Seg out) {
        if (shape instanceof TelegraphShape.Wave w) {
            double r0 = Math.max(0, w.r0() - w.halfThickness());
            double r1 = w.r1() + w.halfThickness();
            for (double[] iv : gapIntervals(w.gaps(), w.gapHalf())) {
                if (iv[1] - iv[0] >= TWO_PI - 1e-9) {
                    continue;
                }
                radial(w.cx(), w.cz(), iv[0], r0, r1, out);
                radial(w.cx(), w.cz(), iv[1], r0, r1, out);
            }
        }
    }

    // いまの帯 (振り回す帯の Line、広がる輪の Band) の縁。動く先頭として太く描く
    public static void edgesOf(TelegraphShape current, Seg out) {
        if (current instanceof TelegraphShape.Line l) {
            rectSegs(l, out);
        } else if (current instanceof TelegraphShape.Band) {
            outline(current, out);
        }
    }

    // ---- 部品 ----

    // 扇形の面 (a0 < a1。半径 r0〜r1)
    public static void sector(double cx, double cz, double r0, double r1, double a0, double a1, Tri out) {
        if (r1 <= r0 + 1e-9 || a1 <= a0 + 1e-9) {
            return;
        }
        // 弦の誤差 (r(1 - cos(d/2))) が 0.008 以下で、弧の長さが CELL 以下
        double cell = cellFor(r1);
        double maxStep = Math.min(cell / Math.max(r1, 1e-6), 2 * Math.acos(Math.max(0, 1 - 0.008 / Math.max(r1, 0.06))));
        int na = Math.max(1, (int) Math.ceil((a1 - a0) / Math.max(maxStep, 1e-3)));
        int nr = Math.max(1, (int) Math.ceil((r1 - r0) / cell));
        for (int i = 0; i < na; i++) {
            double t0 = a0 + (a1 - a0) * i / na;
            double t1 = a0 + (a1 - a0) * (i + 1) / na;
            double c0 = Math.cos(t0);
            double s0 = Math.sin(t0);
            double c1 = Math.cos(t1);
            double s1 = Math.sin(t1);
            for (int k = 0; k < nr; k++) {
                double ra = r0 + (r1 - r0) * k / nr;
                double rb = r0 + (r1 - r0) * (k + 1) / nr;
                double ax = cx + c0 * ra;
                double az = cz + s0 * ra;
                double bx = cx + c1 * ra;
                double bz = cz + s1 * ra;
                double dx = cx + c0 * rb;
                double dz = cz + s0 * rb;
                double ex = cx + c1 * rb;
                double ez = cz + s1 * rb;
                if (ra > 1e-9) {
                    out.tri(ax, az, bx, bz, ex, ez);
                }
                out.tri(ax, az, ex, ez, dx, dz);
            }
        }
    }

    // 帯の面 (向き angle の原点から along a0〜a1、横 c0〜c1)
    public static void rect(double ox, double oz, double angle, double a0, double a1, double c0, double c1, Tri out) {
        if (a1 <= a0 + 1e-9 || c1 <= c0 + 1e-9) {
            return;
        }
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double cell = cellFor(Math.max(a1 - a0, c1 - c0));
        int na = Math.max(1, (int) Math.ceil((a1 - a0) / cell));
        int nc = Math.max(1, (int) Math.ceil((c1 - c0) / cell));
        for (int i = 0; i < na; i++) {
            double u0 = a0 + (a1 - a0) * i / na;
            double u1 = a0 + (a1 - a0) * (i + 1) / na;
            for (int k = 0; k < nc; k++) {
                double v0 = c0 + (c1 - c0) * k / nc;
                double v1 = c0 + (c1 - c0) * (k + 1) / nc;
                double x00 = ox + c * u0 - s * v0;
                double z00 = oz + s * u0 + c * v0;
                double x10 = ox + c * u1 - s * v0;
                double z10 = oz + s * u1 + c * v0;
                double x11 = ox + c * u1 - s * v1;
                double z11 = oz + s * u1 + c * v1;
                double x01 = ox + c * u0 - s * v1;
                double z01 = oz + s * u0 + c * v1;
                out.tri(x00, z00, x10, z10, x11, z11);
                out.tri(x00, z00, x11, z11, x01, z01);
            }
        }
    }

    // 輪 (r の左右 ht) のうち、安全な筋でない角度のぶん
    private static void bandSectors(double cx, double cz, double r0, double r1, double[] gaps, double gapHalf, Tri out) {
        for (double[] iv : openIntervals(gaps, gapHalf)) {
            sector(cx, cz, Math.max(0, r0), r1, iv[0], iv[1], out);
        }
    }

    private static void bandEdges(double cx, double cz, double r, double[] gaps, double gapHalf, Seg out) {
        if (r <= 0.01) {
            return;
        }
        for (double[] iv : openIntervals(gaps, gapHalf)) {
            arcSegs(cx, cz, r, iv[0], iv[1], out);
        }
    }

    private static void arcSegs(double cx, double cz, double r, double a0, double a1, Seg out) {
        int n = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) * r / 0.25));
        double px = cx + Math.cos(a0) * r;
        double pz = cz + Math.sin(a0) * r;
        for (int i = 1; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            out.seg(px, pz, x, z);
            px = x;
            pz = z;
        }
    }

    private static void radial(double cx, double cz, double angle, double r0, double r1, Seg out) {
        out.seg(cx + Math.cos(angle) * r0, cz + Math.sin(angle) * r0, cx + Math.cos(angle) * r1, cz + Math.sin(angle) * r1);
    }

    private static void rectSegs(TelegraphShape.Line l, Seg out) {
        double c = Math.cos(l.angle());
        double s = Math.sin(l.angle());
        double[][] p = new double[4][];
        double[] alongs = {0, l.length(), l.length(), 0};
        double[] acrosses = {-l.halfWidth(), -l.halfWidth(), l.halfWidth(), l.halfWidth()};
        for (int i = 0; i < 4; i++) {
            p[i] = new double[] {l.x() + c * alongs[i] - s * acrosses[i], l.z() + s * alongs[i] + c * acrosses[i]};
        }
        for (int i = 0; i < 4; i++) {
            double[] a = p[i];
            double[] b = p[(i + 1) % 4];
            out.seg(a[0], a[1], b[0], b[1]);
        }
    }

    // ---- 角度の区間 (波の安全な筋) ----

    // 安全な筋 (gaps の角度の前後 gapHalf)。まとめて [0, 2π] に収めた区間の並び (重なりは合わせる)
    static List<double[]> gapIntervals(double[] gaps, double gapHalf) {
        List<double[]> raw = new ArrayList<>();
        if (gapHalf >= Math.PI) {
            raw.add(new double[] {0, TWO_PI});
        } else if (gapHalf > 0) {
            for (double g : gaps) {
                double lo = ((g - gapHalf) % TWO_PI + TWO_PI) % TWO_PI;
                double hi = lo + 2 * gapHalf;
                if (hi > TWO_PI) {
                    raw.add(new double[] {lo, TWO_PI});
                    raw.add(new double[] {0, hi - TWO_PI});
                } else {
                    raw.add(new double[] {lo, hi});
                }
            }
        }
        raw.sort((a, b) -> Double.compare(a[0], b[0]));
        List<double[]> merged = new ArrayList<>();
        for (double[] iv : raw) {
            if (!merged.isEmpty() && iv[0] <= merged.get(merged.size() - 1)[1]) {
                double[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], iv[1]);
            } else {
                merged.add(Arrays.copyOf(iv, 2));
            }
        }
        return merged;
    }

    // 輪が通る角度 (安全な筋の外)
    static List<double[]> openIntervals(double[] gaps, double gapHalf) {
        List<double[]> gapsIv = gapIntervals(gaps, gapHalf);
        List<double[]> open = new ArrayList<>();
        double at = 0;
        for (double[] iv : gapsIv) {
            if (iv[0] > at) {
                open.add(new double[] {at, iv[0]});
            }
            at = Math.max(at, iv[1]);
        }
        if (at < TWO_PI) {
            open.add(new double[] {at, TWO_PI});
        }
        return open;
    }

    // 刻みの幅: 小さい形は細かく (段差に沿う)、大きい形は粗く (三角形の数を抑える。ボスの広間は平ら)
    private static double cellFor(double extent) {
        return Math.max(CELL, Math.min(1.6, extent / 12));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
