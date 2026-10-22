package jp.revati.skyabyss.titan;

import java.util.List;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;

// 起動時に裏で建てる背が、すべて建ち終わるまで待つ自己テスト (段の名前は "back builds")。
// 建てるあいだの、いちばん長い tick と手順は BackBuildJobs の記録 ("Built the ... in the background") に出る。新しい世界で測る
public final class BackBuildsSelfTest {
    private BackBuildsSelfTest() {
    }

    // 待つ上限 (tick)。過ぎたら落とす
    static final int LIMIT = 20 * 60 * 8;

    public static List<Step> steps() {
        long[] start = new long[1];
        int[] ticks = new int[1];
        return List.of(Step.until("back builds", server -> {
            if (start[0] == 0) {
                start[0] = System.nanoTime();
            }
            ticks[0]++;
            int left = BackBuildJobs.pendingCount();
            if (left == 0) {
                SkyAbyss.LOGGER.info("SELFTEST back builds PASSED (all backs built {} s after the step began, {} ticks)",
                        (System.nanoTime() - start[0]) / 1_000_000_000L, ticks[0]);
                return true;
            }
            if (ticks[0] >= LIMIT) {
                SkyAbyss.LOGGER.error("SELFTEST back builds FAILED: {} builds still queued after {} ticks", left, ticks[0]);
                return true;
            }
            return false;
        }));
    }
}
