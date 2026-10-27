package jp.revati.skyabyss.quest;

import net.minecraft.server.MinecraftServer;

// 繰り返すクエストの周期。
//   daily  : オーバーワールドのゲーム内の日 (夜明けで替わる。寝て夜を飛ばしても次の日になる。
//            doDaylightCycle を切っていても、経ったゲーム時間 (24000 tick) ごとに次の日になる)
//   weekly : 7 日ごと (週替わり)
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
        long day = day(server);
        return switch (repeat) {
            case "weekly" -> Math.floorDiv(day, WEEK_DAYS);
            case "daily" -> day;
            default -> -1;
        };
    }

    // 次の周期までのゲーム内の時間 (tick)。板に「次の入れ替えまで」を出すのに使う
    public static long untilNext(MinecraftServer server, String repeat) {
        return untilNext(server.overworld().getDayTime(), server.overworld().getGameTime(), repeat);
    }

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
