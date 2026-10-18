package jp.revati.skyabyss.cinematic;

// 一つの撮影の動きの緩急
public enum Ease {
    LINEAR,
    SMOOTH,
    SMOOTHER,
    IN,
    OUT;

    public float apply(float x) {
        float c = x < 0f ? 0f : Math.min(x, 1f);
        return switch (this) {
            case LINEAR -> c;
            case SMOOTH -> c * c * (3f - 2f * c);
            case SMOOTHER -> c * c * c * (c * (c * 6f - 15f) + 10f);
            case IN -> c * c;
            case OUT -> 1f - (1f - c) * (1f - c);
        };
    }
}
