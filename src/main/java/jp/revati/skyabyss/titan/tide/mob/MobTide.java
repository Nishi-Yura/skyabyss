package jp.revati.skyabyss.titan.tide.mob;

import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.world.level.Level;

// 潮殻の背の敵が見る「いまの潮」。
// 背中の潮の時計 (08 の 3.4節、TideClock) はまだない。それまでは同じ周期 (干潮 50 秒・満ちる 10 秒・満潮 50 秒・引く 10 秒) を
// ワールドの時刻から計算する。潮の時計ができたら phase() の中身だけを差し替える。
// 背中の外 (スポーンエッグで出した地上など) は、いつも干潮 (敵は起きている)。テスト用コマンドで止められる
public final class MobTide {
    public enum Phase {
        LOW, RISING, HIGH, FALLING;

        // 水がある間 (満ちはじめから引ききるまで)。珊瑚憑きはこの間、像になり、干潮で目覚める
        public boolean wet() {
            return this != LOW;
        }
    }

    public static final int LOW_TICKS = 50 * 20;
    public static final int RISE_TICKS = 10 * 20;
    public static final int HIGH_TICKS = 50 * 20;
    public static final int FALL_TICKS = 10 * 20;
    public static final int CYCLE = LOW_TICKS + RISE_TICKS + HIGH_TICKS + FALL_TICKS;

    // テスト用コマンドで止めた潮 (null なら周期どおり)。サーバーを止めると戻る
    private static Phase forced;

    private MobTide() {
    }

    public static void force(Phase phase) {
        forced = phase;
    }

    public static Phase forced() {
        return forced;
    }

    public static Phase phase(Level level) {
        if (forced != null) {
            return forced;
        }
        if (level.dimension() != TideTravel.TIDE_BACK) {
            return Phase.LOW;
        }
        return phaseAt(level.getGameTime());
    }

    // 周期の中の位置から段階を出す (自己テスト用に公開)
    public static Phase phaseAt(long gameTime) {
        int t = (int) Math.floorMod(gameTime, (long) CYCLE);
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
}
