package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 【空の実装 (敵の担当が置いた)】雷翼の背の嵐の拍 (12 の 3.4節、11.1 の API)。統合で背中の担当 (担当 1) の本物に置き換わる。
// ここでは、サーバーの tick を数えて 90 秒の周期 (凪 40・荒れる 5・嵐 40・静まる 5) を回すだけ (保存も、知らせの音もない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBackClock {
    public enum Phase { CALM, RISING, STORM, EASING }

    public static final int CALM_TICKS = 800;
    public static final int RISING_TICKS = 100;
    public static final int STORM_TICKS = 800;
    public static final int EASING_TICKS = 100;
    public static final int CYCLE = CALM_TICKS + RISING_TICKS + STORM_TICKS + EASING_TICKS;

    private static long ticks;

    private StormBackClock() {
    }

    public static Phase phase() {
        return phaseAt((int) Math.floorMod(ticks, (long) CYCLE));
    }

    public static Phase phaseAt(int t) {
        if (t < CALM_TICKS) {
            return Phase.CALM;
        }
        if (t < CALM_TICKS + RISING_TICKS) {
            return Phase.RISING;
        }
        if (t < CALM_TICKS + RISING_TICKS + STORM_TICKS) {
            return Phase.STORM;
        }
        return Phase.EASING;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        ticks++;
    }

    // 空の実装の柱 (StormRods) が接地の残りを数えるのに使う
    static long now() {
        return ticks;
    }
}
