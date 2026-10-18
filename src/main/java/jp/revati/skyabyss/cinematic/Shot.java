package jp.revati.skyabyss.cinematic;

import net.minecraft.world.phys.Vec3;

// 一つの撮影: seconds 秒かけて、カメラの位置が from → to、見る点が lookFrom → lookTo へ動く (緩急は ease)。
// 位置は場面の座標 (右, 上, 前)。fov は度 (0 以下なら設定のまま)。sway は手ぶれのような小さな揺れ (見る点の遠さの割合)
public record Shot(float seconds, Vec3 posFrom, Vec3 posTo, Look lookFrom, Look lookTo, float fovFrom, float fovTo, Ease ease, float sway) {
    public static Builder of(float seconds) {
        return new Builder(seconds);
    }

    public static final class Builder {
        private final float seconds;
        private Vec3 posFrom = new Vec3(0, 1.62, 0);
        private Vec3 posTo = null;
        private Look lookFrom = Look.frame(0, 1.62, 10);
        private Look lookTo = null;
        private float fovFrom = 0f;
        private float fovTo = -1f;
        private Ease ease = Ease.SMOOTH;
        private float sway = 0f;

        private Builder(float seconds) {
            this.seconds = seconds;
        }

        public Builder pos(double r, double u, double f) {
            this.posFrom = new Vec3(r, u, f);
            return this;
        }

        public Builder posTo(double r, double u, double f) {
            this.posTo = new Vec3(r, u, f);
            return this;
        }

        public Builder look(Look look) {
            this.lookFrom = look;
            return this;
        }

        public Builder lookTo(Look look) {
            this.lookTo = look;
            return this;
        }

        public Builder fov(float from, float to) {
            this.fovFrom = from;
            this.fovTo = to;
            return this;
        }

        public Builder ease(Ease ease) {
            this.ease = ease;
            return this;
        }

        public Builder sway(float sway) {
            this.sway = sway;
            return this;
        }

        public Shot build() {
            return new Shot(seconds, posFrom, posTo == null ? posFrom : posTo, lookFrom, lookTo == null ? lookFrom : lookTo, fovFrom,
                    fovTo < 0f ? fovFrom : fovTo, ease, sway);
        }
    }
}
