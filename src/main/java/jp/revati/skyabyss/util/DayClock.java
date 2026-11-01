package jp.revati.skyabyss.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

// 日替わり・週替わりの時計 (実時間。サーバーの時計)。2026-11-02 の全体監査で一本にした:
//   前は塔・巡り路・組合の旗取り・釣り大会・週の賭けが実時間、言伝ての板・店の品揃え・組合の週の頼みがゲーム内の日 (1 日 = 20 分) で、
//   同じ「日替わり」「組合の週」が二つの長さで動いていた。今は戻ってくる理由になるものはすべてこの時計を見る (docs/design/54 の 1 章)。
//   日の境は朝 5 時、週は ISO 週 (月曜の朝 5 時に替わる)。物語の「日 1・日 2」や毎日の小さな異変 (story/ch1/Oddities) はゲーム内の日のまま (quest/Repeats.day)。
//   自己テストは testOffsetDays で今日を先へずらす (ゲームの時刻を進めても替わらない)
public final class DayClock {
    public static final int DAY_START_HOUR = 5;
    // 自己テスト用: 今日をこれだけ先へずらす (日)。終わったら 0 に戻す
    public static volatile int testOffsetDays = 0;

    private DayClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().plusDays(testOffsetDays);
    }

    // 今日 (朝 5 時で切り替わる)
    public static LocalDate today() {
        return now().minusHours(DAY_START_HOUR).toLocalDate();
    }

    // 日替わりの番号 (1970-01-01 からの日数。人のデータに書いて比べる)
    public static long epochDay() {
        return today().toEpochDay();
    }

    // 週の名前 "2026-W44" (ISO 週。保存して比べる)
    public static String weekId() {
        LocalDate d = today();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    // 週替わりの番号 (年 × 100 + ISO 週。抽選の種と比べるのに使う。年をまたぐと飛ぶが、比べる・種にするだけなので構わない)
    public static long weekNumber() {
        LocalDate d = today();
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    // 次の日替わり (明日の朝 5 時) までのゲームの tick (20 tick = 1 秒)。板の「次の入れ替えまで」の表示用
    public static long ticksUntilNextDay() {
        LocalDateTime next = today().plusDays(1).atTime(LocalTime.of(DAY_START_HOUR, 0));
        return Math.max(1, Duration.between(now(), next).getSeconds() * 20);
    }

    // 次の週替わり (次の月曜の朝 5 時) までのゲームの tick
    public static long ticksUntilNextWeek() {
        LocalDate monday = today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDateTime next = monday.atTime(LocalTime.of(DAY_START_HOUR, 0));
        return Math.max(1, Duration.between(now(), next).getSeconds() * 20);
    }
}
