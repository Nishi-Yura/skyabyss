package jp.revati.skyabyss.titan.storm.back;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.titan.BackCycle;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

// 雷翼の背の嵐の拍 (12 の 3.4節)。背中全体で一つ。凪 40 秒 → 荒れる 5 秒 → 嵐 40 秒 → 静まる 5 秒 (一周 90 秒)。
// 地形は変えず、力 (昇り風の高さ・横風・落雷・敵の動き) だけを変える。どの拍でも道は閉じない。
// 位置は StormBackState に保存し、背中に人がいる間だけ進む。リセットで凪の始まりへ戻す。テスト用に凪・嵐で止められる (force)。
//
// ほかの担当へ (11.1 の約束): StormBackClock.phase() で今の拍。phase().stormy() は「嵐の力が出ている間」(荒れる・嵐)。
public final class StormBackClock {
    public enum Phase {
        CALM, RISING, STORM, FALLING;

        // 嵐の力が出ている間 (羽守が飛ぶ、横風、落雷、昇り風が高い)。荒れるは予告だけなので入れない
        public boolean stormy() {
            return this == STORM;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final int CALM_TICKS = 40 * 20;
    public static final int RISE_TICKS = 5 * 20;
    public static final int STORM_TICKS = 40 * 20;
    public static final int FALL_TICKS = 5 * 20;
    // 荒れる・静まるの 10 秒前に、それぞれの音を鳴らす
    public static final int WARN_TICKS = 10 * 20;
    public static final BackCycle CYCLE = new BackCycle(WARN_TICKS, CALM_TICKS, RISE_TICKS, STORM_TICKS, FALL_TICKS);

    // 昇り風の上がる高さ (凪 4・嵐 13)
    public static final double CALM_LIFT = 4;
    public static final double STORM_LIFT = 13;

    @Nullable
    private static Phase forced;

    private StormBackClock() {
    }

    public static void force(@Nullable Phase phase) {
        forced = phase;
    }

    @Nullable
    public static Phase forced() {
        return forced;
    }

    // 今の拍 (サーバーの中ならどこから呼んでもよい。背中のディメンションがなければ凪)
    public static Phase phase() {
        ServerLevel back = back();
        return back == null ? (forced != null ? forced : Phase.CALM) : phase(back);
    }

    public static Phase phase(ServerLevel level) {
        return forced != null ? forced : phaseAt(StormBackState.get(storm(level)).clock());
    }

    public static Phase phaseAt(int t) {
        return Phase.values()[CYCLE.phaseAt(t)];
    }

    // 次に変わるまでの tick (止めているときは -1)
    public static int ticksLeft(ServerLevel level) {
        if (forced != null) {
            return -1;
        }
        return CYCLE.ticksLeft(StormBackState.get(storm(level)).clock());
    }

    // 嵐の強さ (0〜1)。荒れる・静まるの間は時間に比例する
    public static double strength(ServerLevel level) {
        if (forced != null) {
            return forced == Phase.STORM || forced == Phase.RISING ? 1 : 0;
        }
        return strengthAt(StormBackState.get(storm(level)).clock());
    }

    public static double strengthAt(int t) {
        int in = CYCLE.intoPhase(t);
        return switch (phaseAt(t)) {
            case CALM -> 0;
            case STORM -> 1;
            case RISING -> (in + 1) / (double) RISE_TICKS;
            case FALLING -> 1 - (in + 1) / (double) FALL_TICKS;
        };
    }

    // 今の昇り風の高さ (凪 4・嵐 13。荒れる・静まるは間)
    public static double liftHeight(ServerLevel level) {
        return CALM_LIFT + (STORM_LIFT - CALM_LIFT) * strength(level);
    }

    // 1 tick 進める (背中に人がいる間だけ)。段階が変わる 10 秒前なら、その段階を返す (音を鳴らすため)
    @Nullable
    static Phase advance(ServerLevel level) {
        if (forced != null) {
            return null;
        }
        StormBackState s = StormBackState.get(level);
        s.clock = (s.clock + 1) % CYCLE.length();
        if (s.clock % 100 == 0) {
            s.setDirty();
        }
        if (CYCLE.warnsNow(s.clock, Phase.RISING.ordinal())) {
            return Phase.RISING;
        }
        if (CYCLE.warnsNow(s.clock, Phase.FALLING.ordinal())) {
            return Phase.FALLING;
        }
        return null;
    }

    @Nullable
    private static ServerLevel back() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(StormTravel.STORM_BACK);
    }

    // 背中以外のレベルを渡されても、背中の時計を読む
    private static ServerLevel storm(ServerLevel level) {
        if (level.dimension() == StormTravel.STORM_BACK) {
            return level;
        }
        ServerLevel back = level.getServer().getLevel(StormTravel.STORM_BACK);
        return back == null ? level : back;
    }
}
