package jp.revati.skyabyss.notice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

// 同じ場所に出す知らせの順番待ち。重ならない (いつも一つだけ映る)、混んだら一つあたりを短くする、あふれたら手がかりから捨てる。
// 時間はミリ秒で数える純粋な部品 (描画から切り離してあり、自己テストで検める)
public final class NoticeQueue<T> {
    public static final int MAX_WAITING = 6;
    public static final long FADE_IN = 450;
    public static final long FADE_OUT = 650;

    public static final class Entry<T> {
        public final T value;
        public final Notice.Kind kind;
        public final String key;
        final int length;
        long hold;
        long age;

        Entry(T value, Notice.Kind kind, String key, int length) {
            this.value = value;
            this.kind = kind;
            this.key = key;
            this.length = length;
        }

        public long age() {
            return age;
        }

        public long hold() {
            return hold;
        }
    }

    private final ArrayDeque<Entry<T>> waiting = new ArrayDeque<>();
    private Entry<T> current;
    private double speed = 1.0;
    // 画面に出た順の記録 (自己テストの順番の検査用)
    private final List<String> shown = new ArrayList<>();

    // 設定の速さ (0.5 = ゆっくり、2 = 速い)
    public void setSpeed(double speed) {
        this.speed = Math.max(0.25, Math.min(4.0, speed));
    }

    // 表示の長さ (ホールド): 字数に応じ、種類ごとの基準を、設定の速さと待ち数で縮める
    public static long holdMillis(Notice.Kind kind, int length, double speed, int pending) {
        double base = switch (kind) {
            case NARRATE -> Math.max(2600, Math.min(7500, 1700 + 95L * length));
            case HINT -> Math.max(2200, Math.min(5500, 1500 + 70L * length));
            case LEVEL_UP -> 2600;
            case PLACE -> 3400;
            case ALERT -> 2300;
            default -> 3000;
        };
        double pressure = Math.max(0.4, 1.0 / (1.0 + 0.35 * pending));
        return Math.round(base * pressure / Math.max(0.25, speed));
    }

    public void offer(T value, Notice.Kind kind, String key, int length) {
        // 同じ文が続けて来ても一度だけ (出ているものと、最後尾)
        if (current != null && current.key.equals(key) && current.age < FADE_IN + current.hold) {
            return;
        }
        if (!waiting.isEmpty() && waiting.peekLast().key.equals(key)) {
            return;
        }
        if (waiting.size() >= MAX_WAITING) {
            // あふれた: 手がかり (HINT) を古い順に捨て、なければ一番古い待ちを捨てる
            Iterator<Entry<T>> it = waiting.iterator();
            boolean dropped = false;
            while (it.hasNext()) {
                if (it.next().kind == Notice.Kind.HINT) {
                    it.remove();
                    dropped = true;
                    break;
                }
            }
            if (!dropped) {
                waiting.pollFirst();
            }
        }
        waiting.addLast(new Entry<>(value, kind, key, length));
    }

    public void advance(long dtMillis) {
        long left = Math.max(0, dtMillis);
        while (true) {
            if (current == null) {
                current = waiting.pollFirst();
                if (current == null) {
                    return;
                }
                current.hold = holdMillis(current.kind, current.length, speed, waiting.size());
                shown.add(current.key);
            }
            long room = FADE_IN + current.hold + FADE_OUT - current.age;
            if (left < room) {
                current.age += left;
                return;
            }
            left -= room;
            current = null;
        }
    }

    @Nullable
    public Entry<T> current() {
        return current;
    }

    // 0〜1: いまの知らせの濃さ
    public float alpha() {
        if (current == null) {
            return 0f;
        }
        long a = current.age;
        if (a < FADE_IN) {
            return a / (float) FADE_IN;
        }
        if (a < FADE_IN + current.hold) {
            return 1f;
        }
        return Math.max(0f, 1f - (a - FADE_IN - current.hold) / (float) FADE_OUT);
    }

    public int waiting() {
        return waiting.size();
    }

    public boolean idle() {
        return current == null && waiting.isEmpty();
    }

    public List<String> shownKeys() {
        return List.copyOf(shown);
    }

    public void clear() {
        waiting.clear();
        current = null;
    }
}
