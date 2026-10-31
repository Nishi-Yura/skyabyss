package jp.revati.skyabyss.tower;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

// 週替わりの「賭け」(docs/design/54 の 2.7)。毎週月曜 (朝 5 時で切り替わる日の数え方) に、八つの中から六つが出る (週の数で決まる抽選。全員が同じ顔ぶれ)。
// 隊長が受付で 0〜3 個を選んで入る。選んだ一つごとに塔の欠片 +30%。効き方はそれぞれの所で isOn を見る:
//   half_heal    回復が半分 (Towers.onHeal)              no_revive   倒れた人を起こせない (WildGearEvents.reviveStep)
//   fast_adds    湧く敵の足が速い (TowerFight)          short_time  時間の限りが 25 分 (TowerRun.limit)
//   fast_moon    月の進みが速い (TowerFight)            hard_eyes   側の目が壊れない (TowerFight)
//   heavy_hits   主の傷 +20% (TowerFight.spawn)          thick_master 主の体力 +25% (TowerFight.spawn)
public final class TowerWagers {
    public static final List<String> POOL = List.of("half_heal", "no_revive", "fast_adds", "short_time", "fast_moon", "hard_eyes", "heavy_hits", "thick_master");
    public static final int PER_WEEK = 6;
    public static final int MAX_PICK = 3;
    public static final double SHARD_BONUS = 0.30;
    public static final double FAST_ADDS = 1.3;
    public static final int SHORT_LIMIT = 25 * 60 * 20;
    public static final double FAST_MOON = 0.75;
    public static final double HEAVY_HITS = 1.2;
    public static final double THICK_MASTER = 1.25;

    private TowerWagers() {
    }

    // 今週の月曜 (Towers.today と同じ朝 5 時の切り替わり)
    public static LocalDate week() {
        return Towers.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // 今週の六つ (並びも週で決まる)
    public static List<String> thisWeek() {
        List<String> left = new ArrayList<>(POOL);
        Random r = new Random(week().toEpochDay() * 0x9E3779B97F4A7C15L);
        List<String> out = new ArrayList<>();
        while (out.size() < PER_WEEK && !left.isEmpty()) {
            out.add(left.remove(r.nextInt(left.size())));
        }
        return out;
    }

    public static boolean isOn(TowerRun run, String id) {
        return run != null && run.wagers.contains(id);
    }

    // 欠片の倍率
    public static double shardScale(Set<String> wagers) {
        return 1 + SHARD_BONUS * wagers.size();
    }
}
