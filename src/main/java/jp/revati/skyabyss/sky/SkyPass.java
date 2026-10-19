package jp.revati.skyabyss.sky;

// 雲の上を「何か」が飛んで通過する1回分の情報。サーバーとクライアントで同じ値を共有し、
// ゲーム時間から影の位置と形を同じように計算する。
public record SkyPass(long startTick, double originX, double originZ, float dirX, float dirZ, float speed, int duration) {
    // 羽ばたきの周期 (tick)。ゆっくり大きく羽ばたく
    public static final double FLAP_INTERVAL = 70.0;
    // 影の中心から端までの最大距離 (ブロック)。描画や判定の範囲に使う
    public static final double REACH = 165.0;
    // 呼び声が降ってくる時点（通過全体に対する割合）
    private static final double[] CALL_POINTS = {0.38, 0.62};

    public boolean isActive(long gameTime) {
        return gameTime >= startTick && gameTime < startTick + duration;
    }

    public double age(long gameTime, float partialTick) {
        return gameTime - startTick + partialTick;
    }

    public double centerX(double age) {
        return originX + dirX * speed * age;
    }

    public double centerZ(double age) {
        return originZ + dirZ * speed * age;
    }

    public int flapIndex(double age) {
        return (int) Math.floor(age / FLAP_INTERVAL);
    }

    // 何回目の呼び声まで来たか (0〜2)
    public int callIndex(double age) {
        int count = 0;
        for (double point : CALL_POINTS) {
            if (age >= duration * point) {
                count++;
            }
        }
        return count;
    }

    // 空の荒れ具合 (0〜1)。到着前から雲が厚くなり、去ったあとゆっくり晴れる
    public float storm(double age) {
        double t = age / duration;
        double in = smooth((t - 0.05) / 0.25);
        double out = 1.0 - smooth((t - 0.8) / 0.2);
        return (float) Math.min(in, out);
    }

    // 0 = 翼を広げきった状態, 1 = 振り上げて影が細くなった状態
    public double fold(double age) {
        return 0.5 - 0.5 * Math.cos(age * 2.0 * Math.PI / FLAP_INTERVAL);
    }

    // 地点 (x, z) の影の濃さ (0〜1)。
    // 胴体・首・頭・細くなる尾と、胴体につながった左右の巨大な翼でできている。
    // 形は「輪郭までの距離」で作るので、どの部分も途切れずひと続きになる
    public float shadow(double x, double z, double age) {
        double rx = x - centerX(age);
        double rz = z - centerZ(age);
        if (Math.abs(rx) > REACH || Math.abs(rz) > REACH) {
            return 0f;
        }
        double along = rx * dirX + rz * dirZ;
        double side = Math.abs(-rx * dirZ + rz * dirX);
        // 形の外 (翼の端・頭・尾の先から、ぼかしの幅を足した外) は計算しない。画面の影の地図が毎フレーム何万点も引くので
        if (side > 150.0 || along > 135.0 || along < -175.0) {
            return 0f;
        }
        double d = distance(along, side, fold(age));
        // 高い所からの影なので、輪郭の前後5ブロックをぼかす
        return (float) smooth(0.5 - d / 10.0);
    }

    // 地点 (x, z) から輪郭までの距離 (ブロック。内側は負、外側は正)。描画側が、ぼかし・ゆらぎ・濃淡の層を自分で重ねるために使う。範囲の外は大きな正の値
    public double outlineDistance(double x, double z, double age) {
        double rx = x - centerX(age);
        double rz = z - centerZ(age);
        if (Math.abs(rx) > REACH || Math.abs(rz) > REACH) {
            return 1000.0;
        }
        double along = rx * dirX + rz * dirZ;
        double side = Math.abs(-rx * dirZ + rz * dirX);
        return distance(along, side, fold(age));
    }

    // 輪郭までの距離。内側は負、外側は正
    private static double distance(double along, double side, double fold) {
        // 部位はなめらかに溶け合わせ、継ぎ目の段差を出さない
        double d = capsule(along, side, -45, 0, 55, 0, 19, 17);
        d = smoothMin(d, capsule(along, side, 50, 0, 88, 0, 13, 7), 10);
        d = smoothMin(d, ellipse(along, side, 102, 0, 22, 11), 8);
        d = smoothMin(d, capsule(along, side, -45, 0, -155, 0, 10, 2), 6);
        if (d < -5.0) {
            return d;
        }
        double[] wing = wingFor(fold);
        return smoothMin(d, polygon(along, side, wing), 6);
    }

    // 翼の多角形は fold だけで決まる。fold が前と同じなら作り直さず (毎点の確保をやめて) 使い回す。書き込まれない配列なのでスレッドをまたいでよい
    private record Wing(double fold, double[] v) {
    }

    private static volatile Wing wingCache;

    private static double[] wingFor(double fold) {
        Wing w = wingCache;
        if (w != null && w.fold == fold) {
            return w.v;
        }
        double span = 135.0 - 50.0 * fold;
        double sweep = 10.0 * fold;
        double[] v = {
                30, 8,
                10 + sweep, span * 0.45,
                -25 + sweep * 2, span,
                -58, span * 0.84,
                -47, span * 0.72,
                -70, span * 0.60,
                -52, span * 0.47,
                -68, span * 0.33,
                -48, span * 0.21,
                -40, 8,
        };
        wingCache = new Wing(fold, v);
        return v;
    }

    // 2つの形をなめらかにつなぐ (k が大きいほど継ぎ目が丸くなる)
    private static double smoothMin(double a, double b, double k) {
        double h = Math.max(0.0, Math.min(1.0, 0.5 + 0.5 * (b - a) / k));
        return b + (a - b) * h - k * h * (1.0 - h);
    }

    // 楕円までのおおよその距離
    private static double ellipse(double px, double py, double cx, double cy, double ra, double rb) {
        double ex = (px - cx) / ra;
        double ey = (py - cy) / rb;
        return (Math.sqrt(ex * ex + ey * ey) - 1.0) * Math.min(ra, rb);
    }

    // a から b へ、半径 ra から rb へ細くなる棒までの距離
    private static double capsule(double px, double py, double ax, double ay, double bx, double by, double ra, double rb) {
        double wx = px - ax;
        double wy = py - ay;
        double ex = bx - ax;
        double ey = by - ay;
        double h = Math.max(0.0, Math.min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey)));
        double qx = wx - ex * h;
        double qy = wy - ey * h;
        // 距離の計算。Math.hypot は遅く、画素ごとの影の地図が毎フレーム何万点も引くので sqrt にする (差は最後の桁の丸めだけ)
        return Math.sqrt(qx * qx + qy * qy) - (ra + (rb - ra) * h);
    }

    // 多角形までの符号付き距離 (頂点は x, y の順に並べた配列)
    private static double polygon(double px, double py, double[] v) {
        int n = v.length / 2;
        double d = (px - v[0]) * (px - v[0]) + (py - v[1]) * (py - v[1]);
        double sign = 1.0;
        for (int i = 0, j = n - 1; i < n; j = i, i++) {
            double ex = v[j * 2] - v[i * 2];
            double ey = v[j * 2 + 1] - v[i * 2 + 1];
            double wx = px - v[i * 2];
            double wy = py - v[i * 2 + 1];
            double h = Math.max(0.0, Math.min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey)));
            double bx = wx - ex * h;
            double by = wy - ey * h;
            d = Math.min(d, bx * bx + by * by);
            boolean c1 = py >= v[i * 2 + 1];
            boolean c2 = py < v[j * 2 + 1];
            boolean c3 = ex * wy > ey * wx;
            if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) {
                sign = -sign;
            }
        }
        return sign * Math.sqrt(d);
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }
}
