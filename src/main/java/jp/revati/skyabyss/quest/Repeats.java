package jp.revati.skyabyss.quest;

import net.minecraft.server.MinecraftServer;

// 繰り返すクエストの周期。
//   daily  : オーバーワールドのゲーム内の日 (夜明けで替わる。寝て夜を飛ばしても次の日になる)
//   weekly : 7 日ごと (01 の「七日目の夜」と月のループの長さにそろえた。ループの本体 (M4) ができたら、ループの回数に替えてよい)
// 受けたときの周期を記録に残し、周期が替わるまで同じクエストは受け直せない。報酬は周期ごとに一度 (QuestLog.claimed)
public final class Repeats {
    public static final long DAY = 24000L;
    public static final int WEEK_DAYS = 7;

    private Repeats() {
    }

    public static long day(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), DAY);
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
        long t = server.overworld().getDayTime();
        long span = repeat.equals("weekly") ? DAY * WEEK_DAYS : DAY;
        return span - Math.floorMod(t, span);
    }

    // そのクエストを今の周期にもう受けているか (終えたものも含む)
    public static boolean takenThisCycle(MinecraftServer server, QuestDef def, QuestLog.Entry entry) {
        return entry != null && def.repeats() && entry.cycle == cycle(server, def.repeat());
    }
}
