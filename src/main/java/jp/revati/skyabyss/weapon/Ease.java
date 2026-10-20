package jp.revati.skyabyss.weapon;

import java.util.Locale;

// 武器の動きのイージング。キーフレームの「その key へ向かう区間」にかける曲線 (0..1 → 0..1)。
// 溜めは IN (ゆっくり動き出す)、打ち抜きは IN_CUBIC / SNAP (速く)、残身と戻りは OUT (最後を緩める)、跳ね返りは BACK / SPRING
public enum Ease {
    LINEAR,
    SMOOTH,
    IN,
    OUT,
    IN_OUT,
    IN_CUBIC,
    OUT_CUBIC,
    IN_BACK,
    OUT_BACK,
    SNAP,
    SPRING;

    public float apply(float s) {
        s = s < 0f ? 0f : Math.min(s, 1f);
        switch (this) {
            case SMOOTH:
                return s * s * (3 - 2 * s);
            case IN:
                return s * s;
            case OUT:
                return 1f - (1f - s) * (1f - s);
            case IN_OUT:
                return s < 0.5f ? 2f * s * s : 1f - 2f * (1f - s) * (1f - s);
            case IN_CUBIC:
                return s * s * s;
            case OUT_CUBIC: {
                float u = 1f - s;
                return 1f - u * u * u;
            }
            case IN_BACK: // 一度逆へ引いてから出る (予備動作のため)
                return 2.2f * s * s * s - 1.2f * s * s;
            case OUT_BACK: { // 行き過ぎて戻る
                float u = s - 1f;
                return 1f + 2.4f * u * u * u + 1.4f * u * u;
            }
            case SNAP: // 立ち上がりが鋭い (打ち抜き)
                return 1f - (float) Math.pow(1f - s, 4);
            case SPRING: // 行き過ぎて、揺れながら落ち着く (弦・布・柄のしなり)
                return 1f - (float) (Math.exp(-5.0 * s) * Math.cos(9.0 * s));
            default:
                return s;
        }
    }

    public static Ease parse(String name) {
        if (name == null || name.isEmpty()) {
            return SMOOTH;
        }
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
