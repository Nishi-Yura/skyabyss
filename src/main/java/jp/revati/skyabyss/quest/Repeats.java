package jp.revati.skyabyss.quest;

import net.minecraft.server.MinecraftServer;

// 繰り返すクエストの周期。
//   daily  : 実時間の一日 (朝 5 時で替わる。util/DayClock。塔・巡り路・旗取り・店の品揃えと同じ時計。2026-11-02 に一本にした)
//   weekly : 実時間の一週 (ISO 週。月曜の朝 5 時で替わる)
//   day()  : ゲーム内の日 (夜明けで替わる。寝て夜を飛ばしても次の日。doDaylightCycle を切っていても 24000 tick ごとに次の日)。
//            物語の「毎日の小さな異変」(story/ch1/Oddities) など、遊びの中の日数を数える所だけが使う
// 受けたときの周期を記録に残し、周期が替わるまで同じクエストは受け直せない。報酬は周期ごとに一度 (QuestLog.claimed)
public final class Repeats {
    public static final long DAY = 24000L;
    public static final int WEEK_DAYS = 7;

    private Repeats() {
    }

    // 日の数え方: 昼夜の時刻 (寝る・/time set で進む) と、経ったゲーム時間 (昼夜が止まっていても進む) の大きいほう
    public static long day(MinecraftServer server) {
        return day(server.overworld().getDayTime(), server.overworld().getGameTime());
    }

    public static long day(long dayTime, long gameTime) {
        return Math.max(Math.floorDiv(dayTime, DAY), Math.floorDiv(gameTime, DAY));
    }

    public static long cycle(MinecraftServer server, String repeat) {
        return switch (repeat) {
            case "weekly" -> jp.revati.skyabyss.util.DayClock.weekNumber();
            case "daily" -> jp.revati.skyabyss.util.DayClock.epochDay();
            default -> -1;
        };
    }

    // 次の周期までの時間 (tick。実時間を 20 tick = 1 秒で)。板に「次の入れ替えまで」を出すのに使う
    public static long untilNext(MinecraftServer server, String repeat) {
        return repeat.equals("weekly") ? jp.revati.skyabyss.util.DayClock.ticksUntilNextWeek() : jp.revati.skyabyss.util.DayClock.ticksUntilNextDay();
    }

    // ゲーム内の日で数えたときの残り (純粋な計算。自己テストが見る)
    public static long untilNext(long dayTime, long gameTime, String repeat) {
        long span = repeat.equals("weekly") ? DAY * WEEK_DAYS : DAY;
        // 今の日を決めているほうの時計で測る (昼夜が止まっていればゲーム時間)
        long t = Math.floorDiv(gameTime, DAY) > Math.floorDiv(dayTime, DAY) ? gameTime : dayTime;
        return span - Math.floorMod(t, span);
    }

    // そのクエストを今の周期にもう受けているか (終えたものも含む)
    public static boolean takenThisCycle(MinecraftServer server, QuestDef def, QuestLog.Entry entry) {
        return entry != null && def.repeats() && entry.cycle == cycle(server, def.repeat());
    }
}
