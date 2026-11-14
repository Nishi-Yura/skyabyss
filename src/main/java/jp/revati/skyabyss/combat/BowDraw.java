package jp.revati.skyabyss.combat;

// 弓を引き切るまでの時間 (バニラは 20 tick)。2026-11-15 の声「弓がちょっとソロだと弱いので、完全にうてるまでの時間を 1/2 に」で 10 tick。
// 撃つ強さ (BowItem.getPowerForTime) は mixin/BowPowerMixin がこの比で引いた時間を伸ばして読む。一人称の引きの絵・弓のモデルの「pull」・反動も同じ値で割る
public final class BowDraw {
    public static final int FULL_TICKS = 10;

    private BowDraw() {
    }

    // 引いた時間 (tick) を、バニラの 20 tick で引き切る物差しに直す
    public static int vanillaTicks(int ticks) {
        return ticks * 20 / FULL_TICKS;
    }

    // 引きの割合 (0〜、1 で引き切り)
    public static float pull(float ticks) {
        return ticks / FULL_TICKS;
    }
}
