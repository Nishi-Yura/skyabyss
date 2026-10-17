package jp.revati.skyabyss.client.weapon;

// 武器の一振りの動き (キーフレーム)。時刻 t は 0..1 (バニラの振りの進み具合 attackAnim)。
// 三人称: 利き腕 (M) と反対の腕 (O) の x/y/z 回転 (ラジアン)、胴のひねり、肩の前後
// 一人称: 手元の移動 (ブロック) と回転 (度)。どれも右利きの値。左利きは左右を反転して使う
// 値の読み方 (右腕): xRot 負 = 前上へ上がる / yRot 負 = 体の内側へ / zRot 正 = 下がった腕は外へ、上がった腕は内へ
final class SwingMove {
    // チャンネル
    static final int MX = 0, MY = 1, MZ = 2;        // 利き腕
    static final int OX = 3, OY = 4, OZ = 5;        // 反対の腕 (何も持っていないときだけ動かす)
    static final int BY = 6, PZ = 7;                // 胴のひねり (ラジアン)、利き腕の肩の前後 (モデル単位。負 = 前へ出す)
    static final int TX = 8, TY = 9, TZ = 10;       // 一人称の移動
    static final int RX = 11, RY = 12, RZ = 13;     // 一人称の回転 (度)
    static final int LEAN = 14;                     // 体を前へ倒す角度 (度)。三人称の体ぜんぶ
    static final int CHANNELS = 15;

    private final float[] times;
    private final float[][] values = new float[CHANNELS][];
    // 普段の構え (三人称)。NaN = バニラの片手持ちのまま。利き腕の x/y と、反対の腕 (何も持っていないとき) の x/y
    float idleMX = Float.NaN, idleMY, idleOX = Float.NaN, idleOY;
    // 振りの前後で、普段の構えへなめらかにつなぐ幅
    final float blendIn;
    final float blendOut;

    private SwingMove(float[] times, float blendIn, float blendOut) {
        this.times = times;
        this.blendIn = blendIn;
        this.blendOut = blendOut;
    }

    static SwingMove at(float... times) {
        return new SwingMove(times, 0.12f, 0.2f);
    }

    SwingMove set(int channel, float... v) {
        if (v.length != times.length) {
            throw new IllegalArgumentException("channel " + channel + " needs " + times.length + " values");
        }
        values[channel] = v;
        return this;
    }

    // 普段の構え: 利き腕 (x, y)
    SwingMove idle(float mx, float my) {
        idleMX = mx;
        idleMY = my;
        return this;
    }

    // 普段の構え: 反対の腕 (x, y)。何も持っていないときだけ添える
    SwingMove idleOff(float ox, float oy) {
        idleOX = ox;
        idleOY = oy;
        return this;
    }

    boolean has(int channel) {
        return values[channel] != null;
    }

    // チャンネルの値。なければ fallback
    float get(int channel, float t, float fallback) {
        float[] v = values[channel];
        if (v == null) {
            return fallback;
        }
        if (t <= times[0]) {
            return v[0];
        }
        for (int i = 1; i < times.length; i++) {
            if (t <= times[i]) {
                float s = (t - times[i - 1]) / (times[i] - times[i - 1]);
                s = s * s * (3 - 2 * s);
                return v[i - 1] + (v[i] - v[i - 1]) * s;
            }
        }
        return v[v.length - 1];
    }

    // 普段の構えと振りの混ぜ具合 (0 = 構えのまま、1 = 振りの動き)
    float weight(float t) {
        float in = blendIn <= 0 ? 1 : Math.min(1f, t / blendIn);
        float out = blendOut <= 0 ? 1 : Math.min(1f, (1f - t) / blendOut);
        float w = Math.min(in, out);
        return w * w * (3 - 2 * w);
    }
}
