package jp.revati.skyabyss.titan.storm.mob;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import net.minecraft.world.level.Level;

// 雷翼の背の敵が見る「いまの拍」(12 の 3.4節)。背中では背中の時計 (StormBackClock) の段階をそのまま返す。
// 背中の外 (スポーンエッグで地上に出した個体) は、ワールドの時刻から同じ 90 秒の周期を出す (凪と嵐の両方の動きが見えるように)。
// テスト用コマンドで止められる (止めるのは敵の見る拍だけ。背中の時計は動いたまま)
public final class MobStorm {
    public enum Phase {
        CALM, RISING, STORM, EASING;

        // 羽守が突っ込み、雷蛾の帯電が速くなる間
        public boolean stormy() {
            return this == STORM;
        }

        // 背中の時計の段階から (名前で合わせる: 凪 CALM・荒れる RISING・嵐 STORM・静まる EASING)
        static Phase of(Enum<?> p) {
            for (Phase v : values()) {
                if (v.name().equals(p.name())) {
                    return v;
                }
            }
            return values()[Math.min(p.ordinal(), 3)];
        }
    }

    // 12 の 3.4節: 凪 40 秒・荒れる 5・嵐 40・静まる 5 (背中の外で使う周期)
    public static final int CALM_TICKS = 800;
    public static final int RISING_TICKS = 100;
    public static final int STORM_TICKS = 800;
    public static final int EASING_TICKS = 100;
    public static final int CYCLE = CALM_TICKS + RISING_TICKS + STORM_TICKS + EASING_TICKS;

    @Nullable
    private static Phase forced;

    private MobStorm() {
    }

    public static void force(@Nullable Phase phase) {
        forced = phase;
    }

    @Nullable
    public static Phase forced() {
        return forced;
    }

    public static Phase phase(Level level) {
        if (forced != null) {
            return forced;
        }
        if (level.dimension() == StormMob.STORM_BACK) {
            return Phase.of(StormBackClock.phase());
        }
        return phaseAt(level.getGameTime());
    }

    public static Phase phaseAt(long tick) {
        int t = (int) Math.floorMod(tick, (long) CYCLE);
        if (t < CALM_TICKS) {
            return Phase.CALM;
        }
        if (t < CALM_TICKS + RISING_TICKS) {
            return Phase.RISING;
        }
        return t < CALM_TICKS + RISING_TICKS + STORM_TICKS ? Phase.STORM : Phase.EASING;
    }
}
