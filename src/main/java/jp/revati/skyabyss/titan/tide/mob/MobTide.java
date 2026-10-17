package jp.revati.skyabyss.titan.tide.mob;

import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideClock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

// 潮殻の背の敵が見る「いまの潮」。中身は島の潮の時計 (TideClock、08 の 3.4節) の段階をそのまま返す。
// (敵の担当が作った仮の周期 (ワールドの時刻から計算) を、背中の本番で時計に差し替えた。18.7節)
// 背中の外 (スポーンエッグで出した地上など) は、いつも干潮 (敵は起きている)。テスト用コマンドで止めると、時計ごと止まる
public final class MobTide {
    public enum Phase {
        LOW, RISING, HIGH, FALLING;

        // 水がある間 (満ちはじめから引ききるまで)。珊瑚憑きはこの間、像になり、干潮で目覚める
        public boolean wet() {
            return this != LOW;
        }

        static Phase of(TideClock.Phase p) {
            return values()[p.ordinal()];
        }

        TideClock.Phase clock() {
            return TideClock.Phase.values()[ordinal()];
        }
    }

    public static final int LOW_TICKS = TideClock.LOW_TICKS;
    public static final int RISE_TICKS = TideClock.RISE_TICKS;
    public static final int HIGH_TICKS = TideClock.HIGH_TICKS;
    public static final int FALL_TICKS = TideClock.FALL_TICKS;
    public static final int CYCLE = TideClock.CYCLE;

    private MobTide() {
    }

    // 潮を止める (null で時計に戻す)。島の潮の時計そのものを止める
    public static void force(Phase phase) {
        TideClock.force(phase == null ? null : phase.clock());
    }

    public static Phase forced() {
        TideClock.Phase p = TideClock.forced();
        return p == null ? null : Phase.of(p);
    }

    public static Phase phase(Level level) {
        if (level.dimension() != TideTravel.TIDE_BACK || !(level instanceof ServerLevel server)) {
            TideClock.Phase p = TideClock.forced();
            return p == null ? Phase.LOW : Phase.of(p);
        }
        return Phase.of(TideClock.phase(server));
    }

    // 周期の中の位置から段階を出す (自己テスト用に公開)
    public static Phase phaseAt(long tick) {
        return Phase.of(TideClock.phaseAt((int) Math.floorMod(tick, (long) CYCLE)));
    }
}
