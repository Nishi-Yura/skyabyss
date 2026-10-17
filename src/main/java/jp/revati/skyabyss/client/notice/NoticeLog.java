package jp.revati.skyabyss.client.notice;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;

// 流れた知らせの記録 (新しい順に 200 件まで、この遊びの間だけ)。画面から消えた語りや獲得を、N の「知らせの記録」で読み返せる。
// チャットに残さない代わりの置き場
public final class NoticeLog {
    public record Entry(String time, Notice.Kind kind, Component text) {
    }

    public static final int MAX = 200;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private NoticeLog() {
    }

    public static void add(Notice.Kind kind, Component text) {
        ENTRIES.add(0, new Entry(LocalTime.now().format(CLOCK), kind, text));
        while (ENTRIES.size() > MAX) {
            ENTRIES.remove(ENTRIES.size() - 1);
        }
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
