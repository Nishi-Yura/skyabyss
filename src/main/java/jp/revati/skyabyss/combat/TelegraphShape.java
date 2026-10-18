package jp.revati.skyabyss.combat;

// 予告の形 (床の上の平面)。「当たる範囲」と「床に描く印」は、この同じ形の contains / outline / edge から出る。
// 角度は ラジアン、向き = (cos a, sin a) を (x, z) で見たもの。モブの向きからは Telegraph.angleOf で求める。
//
//   circle(cx, cz, r)                  円 (足元の叩きつけ・噴出)
//   ring(cx, cz, inner, outer)         輪 (内側は安全)
//   line(x, z, angle, length, halfW)   帯 (突き・亀裂・光線)
//   cone(x, z, angle, range, halfAng)  扇 (薙ぎ払い)
//   sweep(x, z, start, span, len, hw)  振り回す帯 (鎖・回る光線)。印は動く範囲全体、当たりはその瞬間の帯 (at(progress))
//   wave(cx, cz, r0, r1, halfT, gaps, gapHalf)  広がる輪 (半径 r0 から r1。厚さ halfT×2。gaps の角度の前後 gapHalf は輪が抜ける安全な筋)。
//                                      印の線は輪の中心線 (厚さの分だけ当たりは広い)
public interface TelegraphShape {
    @FunctionalInterface
    interface PointSink {
        void point(double x, double z);
    }

    // この点 (床の上の x, z) は当たる範囲の中か
    boolean contains(double x, double z);

    // 当たりが続く間 (active) の、進み具合 0〜1 の瞬間の範囲。止まった形は自分を返す
    default TelegraphShape at(double progress) {
        return this;
    }

    // 範囲の輪郭 (全体)。spacing は粒の間隔 (マス)
    void outline(PointSink sink, double spacing);

    // 予告が進むにつれて満ちていく縁 (progress 0〜1)。いつも範囲の内側か縁にある
    void edge(double progress, PointSink sink, double spacing);

    // {minX, minZ, maxX, maxZ}: 動く範囲も含む全体
    double[] bounds();

    // ---- 通信 (クライアントの床の印へ形を送る): 形の種類と数値。種類が -1 の形 (独自の実装) は床の印を出さない ----
    int KIND_NONE = -1;
    int KIND_CIRCLE = 0;
    int KIND_RING = 1;
    int KIND_LINE = 2;
    int KIND_CONE = 3;
    int KIND_SWEEP = 4;
    int KIND_WAVE = 5;

    default int kind() {
        return KIND_NONE;
    }

    default double[] params() {
        return new double[0];
    }

    // kind() と params() から形を戻す。壊れた入力は null
    static TelegraphShape decode(int kind, double[] p) {
        try {
            return switch (kind) {
                case KIND_CIRCLE -> p.length == 3 ? circle(p[0], p[1], p[2]) : null;
                case KIND_RING -> p.length == 4 ? ring(p[0], p[1], p[2], p[3]) : null;
                case KIND_LINE -> p.length == 5 ? line(p[0], p[1], p[2], p[3], p[4]) : null;
                case KIND_CONE -> p.length == 5 ? cone(p[0], p[1], p[2], p[3], p[4]) : null;
                case KIND_SWEEP -> p.length == 6 ? sweep(p[0], p[1], p[2], p[3], p[4], p[5]) : null;
                case KIND_WAVE -> p.length >= 6 ? wave(p[0], p[1], p[2], p[3], p[4], java.util.Arrays.copyOfRange(p, 6, p.length), p[5]) : null;
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    default double centerX() {
        double[] b = bounds();
        return (b[0] + b[2]) / 2;
    }

    default double centerZ() {
        double[] b = bounds();
        return (b[1] + b[3]) / 2;
    }

    // ---- 形 ----

    static TelegraphShape circle(double cx, double cz, double radius) {
        return new Circle(cx, cz, radius);
    }

    static TelegraphShape ring(double cx, double cz, double inner, double outer) {
        return new Ring(cx, cz, inner, outer);
    }

    static TelegraphShape line(double x, double z, double angle, double length, double halfWidth) {
        return new Line(x, z, angle, length, halfWidth);
    }

    static TelegraphShape cone(double x, double z, double angle, double range, double halfAngle) {
        return new Cone(x, z, angle, range, halfAngle);
    }

    static TelegraphShape sweep(double x, double z, double startAngle, double span, double length, double halfWidth) {
        return new Sweep(x, z, startAngle, span, length, halfWidth);
    }

    static TelegraphShape wave(double cx, double cz, double r0, double r1, double halfThickness, double[] gaps, double gapHalf) {
        return new Wave(cx, cz, r0, r1, halfThickness, gaps, gapHalf);
    }

    private static boolean inGap(double[] gaps, double gapHalf, double angle) {
        for (double g : gaps) {
            if (angleDiff(angle, g) <= gapHalf) {
                return true;
            }
        }
        return false;
    }

    private static void arc(PointSink sink, double cx, double cz, double r, double from, double to, double spacing) {
        double len = Math.abs(to - from) * r;
        int n = Math.max(2, (int) Math.ceil(len / spacing));
        for (int i = 0; i <= n; i++) {
            double a = from + (to - from) * i / n;
            sink.point(cx + Math.cos(a) * r, cz + Math.sin(a) * r);
        }
    }

    private static void segment(PointSink sink, double x0, double z0, double x1, double z1, double spacing) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = Math.max(1, (int) Math.ceil(len / spacing));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            sink.point(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t);
        }
    }

    private static double angleDiff(double a, double b) {
        double d = (a - b) % (2 * Math.PI);
        if (d > Math.PI) {
            d -= 2 * Math.PI;
        } else if (d < -Math.PI) {
            d += 2 * Math.PI;
        }
        return Math.abs(d);
    }

    record Circle(double cx, double cz, double r) implements TelegraphShape {
        @Override
        public int kind() {
            return KIND_CIRCLE;
        }

        @Override
        public double[] params() {
            return new double[] {cx, cz, r};
        }

        @Override
        public boolean contains(double x, double z) {
            double dx = x - cx;
            double dz = z - cz;
            return dx * dx + dz * dz <= r * r;
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            arc(sink, cx, cz, r, 0, 2 * Math.PI, spacing);
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
            double rr = r * Math.max(0.05, Math.min(1, progress));
            arc(sink, cx, cz, rr, 0, 2 * Math.PI, spacing * 1.3);
        }

        @Override
        public double[] bounds() {
            return new double[] {cx - r, cz - r, cx + r, cz + r};
        }
    }

    record Ring(double cx, double cz, double inner, double outer) implements TelegraphShape {
        @Override
        public int kind() {
            return KIND_RING;
        }

        @Override
        public double[] params() {
            return new double[] {cx, cz, inner, outer};
        }

        @Override
        public boolean contains(double x, double z) {
            double dx = x - cx;
            double dz = z - cz;
            double d2 = dx * dx + dz * dz;
            return d2 >= inner * inner && d2 <= outer * outer;
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            arc(sink, cx, cz, inner, 0, 2 * Math.PI, spacing);
            arc(sink, cx, cz, outer, 0, 2 * Math.PI, spacing);
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
            double rr = inner + (outer - inner) * Math.max(0, Math.min(1, progress));
            arc(sink, cx, cz, Math.max(0.05, rr), 0, 2 * Math.PI, spacing * 1.3);
        }

        @Override
        public double[] bounds() {
            return new double[] {cx - outer, cz - outer, cx + outer, cz + outer};
        }
    }

    record Line(double x, double z, double angle, double length, double halfWidth) implements TelegraphShape {
        @Override
        public int kind() {
            return KIND_LINE;
        }

        @Override
        public double[] params() {
            return new double[] {x, z, angle, length, halfWidth};
        }

        @Override
        public boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            double along = dx * c + dz * s;
            double across = -dx * s + dz * c;
            return along >= 0 && along <= length && Math.abs(across) <= halfWidth;
        }

        private double[] corner(double along, double across) {
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            return new double[] {x + c * along - s * across, z + s * along + c * across};
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            double[] a = corner(0, -halfWidth);
            double[] b = corner(length, -halfWidth);
            double[] c = corner(length, halfWidth);
            double[] d = corner(0, halfWidth);
            segment(sink, a[0], a[1], b[0], b[1], spacing);
            segment(sink, b[0], b[1], c[0], c[1], spacing);
            segment(sink, c[0], c[1], d[0], d[1], spacing);
            segment(sink, d[0], d[1], a[0], a[1], spacing);
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
            double along = length * Math.max(0.02, Math.min(1, progress));
            double[] a = corner(along, -halfWidth);
            double[] b = corner(along, halfWidth);
            segment(sink, a[0], a[1], b[0], b[1], spacing);
        }

        @Override
        public double[] bounds() {
            double[] a = corner(0, -halfWidth);
            double[] b = corner(length, -halfWidth);
            double[] c = corner(length, halfWidth);
            double[] d = corner(0, halfWidth);
            return new double[] {
                    Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0])), Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1])),
                    Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0])), Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]))};
        }
    }

    record Cone(double x, double z, double angle, double range, double halfAngle) implements TelegraphShape {
        @Override
        public int kind() {
            return KIND_CONE;
        }

        @Override
        public double[] params() {
            return new double[] {x, z, angle, range, halfAngle};
        }

        @Override
        public boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            double d = Math.hypot(dx, dz);
            if (d > range) {
                return false;
            }
            return d < 1e-6 || angleDiff(Math.atan2(dz, dx), angle) <= halfAngle;
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            segment(sink, x, z, x + Math.cos(angle - halfAngle) * range, z + Math.sin(angle - halfAngle) * range, spacing);
            segment(sink, x, z, x + Math.cos(angle + halfAngle) * range, z + Math.sin(angle + halfAngle) * range, spacing);
            arc(sink, x, z, range, angle - halfAngle, angle + halfAngle, spacing);
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
            double r = range * Math.max(0.05, Math.min(1, progress));
            arc(sink, x, z, r, angle - halfAngle, angle + halfAngle, spacing * 1.3);
        }

        @Override
        public double[] bounds() {
            return new double[] {x - range, z - range, x + range, z + range};
        }
    }

    // 回る帯。at(progress) がその瞬間の帯。contains は動く範囲の全体 (印の目安。当たりには at を使う)
    record Sweep(double x, double z, double start, double span, double length, double halfWidth) implements TelegraphShape {
        @Override
        public int kind() {
            return KIND_SWEEP;
        }

        @Override
        public double[] params() {
            return new double[] {x, z, start, span, length, halfWidth};
        }

        @Override
        public TelegraphShape at(double progress) {
            return new Line(x, z, start + span * Math.max(0, Math.min(1, progress)), length, halfWidth);
        }

        @Override
        public boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            if (Math.hypot(dx, dz) > length) {
                return false;
            }
            double lo = Math.min(start, start + span);
            double hi = Math.max(start, start + span);
            double a = Math.atan2(dz, dx);
            double mid = (lo + hi) / 2;
            return angleDiff(a, mid) <= (hi - lo) / 2 + Math.atan2(halfWidth, Math.max(0.5, Math.hypot(dx, dz)));
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            double lo = Math.min(start, start + span);
            double hi = Math.max(start, start + span);
            segment(sink, x, z, x + Math.cos(lo) * length, z + Math.sin(lo) * length, spacing);
            segment(sink, x, z, x + Math.cos(hi) * length, z + Math.sin(hi) * length, spacing);
            arc(sink, x, z, length, lo, hi, spacing);
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
            double p = Math.max(0.05, Math.min(1, progress));
            segment(sink, x, z, x + Math.cos(start) * length * p, z + Math.sin(start) * length * p, spacing);
        }

        @Override
        public double[] bounds() {
            return new double[] {x - length, z - length, x + length, z + length};
        }
    }

    // 広がる輪。at(progress) がその瞬間の輪 (Band)。警告のあいだは、はじまりの輪 (半径 r0) の線だけ描く
    record Wave(double cx, double cz, double r0, double r1, double halfThickness, double[] gaps, double gapHalf) implements TelegraphShape {
        @Override
        public int kind() {
            return KIND_WAVE;
        }

        @Override
        public double[] params() {
            return wireParams();
        }

        private double[] wireParams() {
            double[] out = new double[6 + gaps.length];
            out[0] = cx;
            out[1] = cz;
            out[2] = r0;
            out[3] = r1;
            out[4] = halfThickness;
            out[5] = gapHalf;
            System.arraycopy(gaps, 0, out, 6, gaps.length);
            return out;
        }

        @Override
        public TelegraphShape at(double progress) {
            return new Band(cx, cz, r0 + (r1 - r0) * Math.max(0, Math.min(1, progress)), halfThickness, gaps, gapHalf);
        }

        @Override
        public boolean contains(double x, double z) {
            return false;
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            at(0).outline(sink, spacing);
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
        }

        @Override
        public double[] bounds() {
            double r = r1 + halfThickness;
            return new double[] {cx - r, cz - r, cx + r, cz + r};
        }
    }

    record Band(double cx, double cz, double r, double halfThickness, double[] gaps, double gapHalf) implements TelegraphShape {
        @Override
        public boolean contains(double x, double z) {
            double dx = x - cx;
            double dz = z - cz;
            double d = Math.hypot(dx, dz);
            return Math.abs(d - r) <= halfThickness && !inGap(gaps, gapHalf, Math.atan2(dz, dx));
        }

        @Override
        public void outline(PointSink sink, double spacing) {
            int n = Math.max(8, (int) Math.ceil(2 * Math.PI * r / spacing));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                if (!inGap(gaps, gapHalf, a)) {
                    sink.point(cx + Math.cos(a) * r, cz + Math.sin(a) * r);
                }
            }
        }

        @Override
        public void edge(double progress, PointSink sink, double spacing) {
            outline(sink, spacing);
        }

        @Override
        public double[] bounds() {
            double rr = r + halfThickness;
            return new double[] {cx - rr, cz - rr, cx + rr, cz + rr};
        }
    }
}
