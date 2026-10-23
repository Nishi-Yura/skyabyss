package jp.revati.skyabyss.titan.tide.back;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

// 潮殻の背の潮の時計 (08 の 3.4節)。島全体で一つ。干潮 50 秒 → 満ちる 10 秒 → 満潮 50 秒 → 引く 10 秒 (一周 2 分)。
// 位置は TideBackState に保存し、背中に人がいる間だけ進む (人がいないと島のチャンクは読み込まれていないため)。
// リセットのときは「干潮の始まり」に戻す。テスト用のコマンドで干潮・満潮に止められる (force)。
public final class TideClock {
    public enum Phase {
        LOW, RISING, HIGH, FALLING;

        // 水がある間 (満ちはじめから引ききるまで)
        public boolean wet() {
            return this != LOW;
        }
    }

    public static final int LOW_TICKS = 50 * 20;
    public static final int RISE_TICKS = 10 * 20;
    public static final int HIGH_TICKS = 50 * 20;
    public static final int FALL_TICKS = 10 * 20;
    public static final int CYCLE = LOW_TICKS + RISE_TICKS + HIGH_TICKS + FALL_TICKS;
    // 満ちる・引くの 10 秒前に鳴らす
    public static final int WARN_TICKS = 10 * 20;

    @Nullable
    private static Phase forced;

    private TideClock() {
    }

    public static void force(@Nullable Phase phase) {
        forced = phase;
    }

    @Nullable
    public static Phase forced() {
        return forced;
    }

    public static Phase phase(ServerLevel level) {
        return forced != null ? forced : phaseAt(TideBackState.get(level).clock());
    }

    // 周期の中の位置 (0〜CYCLE-1) から段階
    public static Phase phaseAt(int t) {
        t = Math.floorMod(t, CYCLE);
        if (t < LOW_TICKS) {
            return Phase.LOW;
        }
        t -= LOW_TICKS;
        if (t < RISE_TICKS) {
            return Phase.RISING;
        }
        t -= RISE_TICKS;
        return t < HIGH_TICKS ? Phase.HIGH : Phase.FALLING;
    }

    // 段階の中の位置 (0 から)
    public static int intoPhase(int t) {
        t = Math.floorMod(t, CYCLE);
        if (t < LOW_TICKS) {
            return t;
        }
        t -= LOW_TICKS;
        if (t < RISE_TICKS) {
            return t;
        }
        t -= RISE_TICKS;
        return t < HIGH_TICKS ? t : t - HIGH_TICKS;
    }

    public static int phaseLength(Phase p) {
        return switch (p) {
            case LOW -> LOW_TICKS;
            case RISING -> RISE_TICKS;
            case HIGH -> HIGH_TICKS;
            case FALLING -> FALL_TICKS;
        };
    }

    // 次に段階が変わるまで (止めているときは -1)
    public static int ticksLeft(ServerLevel level) {
        if (forced != null) {
            return -1;
        }
        int t = TideBackState.get(level).clock();
        return phaseLength(phaseAt(t)) - intoPhase(t);
    }

    // 満ち具合 (0〜1)。満ちる・引くの間は時間に比例する
    public static double fullness(ServerLevel level) {
        if (forced != null) {
            return forced == Phase.LOW ? 0 : 1;
        }
        return fullnessAt(TideBackState.get(level).clock());
    }

    public static double fullnessAt(int t) {
        Phase p = phaseAt(t);
        int in = intoPhase(t);
        return switch (p) {
            case LOW -> 0;
            case HIGH -> 1;
            case RISING -> (in + 1) / (double) RISE_TICKS;
            case FALLING -> 1 - (in + 1) / (double) FALL_TICKS;
        };
    }

    // 1 tick 進める
    static void advance(ServerLevel level) {
        if (forced != null) {
            return;
        }
        TideBackState s = TideBackState.get(level);
        s.clock = (s.clock + 1) % CYCLE;
        if (s.clock % 100 == 0) {
            s.setDirty();
        }
    }
}
