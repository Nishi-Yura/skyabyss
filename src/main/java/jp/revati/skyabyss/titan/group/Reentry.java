package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 一つの戦い (心臓の間・中ボスの広間) の「倒れた参加者の再入場」の記録。docs/design/02 の 4.2、10 の 10.4 / 10.5。
//  - 倒れた人は、前室で目を覚ましてから WAIT_TICKS (20 秒) たつと、扉から戻れる
//  - 戻れる回数は、人ごとに戦い 1 回につき limit 回 (0 = 数えない)。使い切ると、戦いが終わるまで扉を通れない (外から見守る)
//  - 戦いが終わる・やり直しになる (中に生きた参加者がいない) と clear() で全部消える
// 時刻はゲーム時刻 (tick)。サーバーの時計に触らないので、自己テストで動かせる
public final class Reentry {
    public static final int WAIT_TICKS = 400;
    public static final int GLOW_TICKS = 60;

    public enum Status {
        // 倒れていない (戦いの中、または倒れずに外へ出た人)
        NONE,
        // 倒れてまだ目を覚ましていない・待ち時間の途中
        WAITING,
        // 戻れる
        READY,
        // 回数を使い切った
        EXHAUSTED
    }

    // 倒れていて、まだ中へ戻っていない人 → 目を覚ました時刻 (まだなら Long.MIN_VALUE)
    private final Map<UUID, Long> fallen = new HashMap<>();
    private final Map<UUID, Integer> used = new HashMap<>();

    public void fell(UUID id) {
        fallen.put(id, Long.MIN_VALUE);
    }

    // 倒れた人が、生きて外にいるのを見た (最初の 1 回だけが「目覚め」の時刻になる)
    public void observeAwake(UUID id, long now) {
        fallen.computeIfPresent(id, (k, v) -> v == Long.MIN_VALUE ? now : v);
    }

    public boolean isFallen(UUID id) {
        return fallen.containsKey(id);
    }

    public boolean hasWoken(UUID id) {
        Long woke = fallen.get(id);
        return woke != null && woke != Long.MIN_VALUE;
    }

    public int used(UUID id) {
        return used.getOrDefault(id, 0);
    }

    // 残りの待ち時間 (tick)。目を覚ましていなければ WAIT_TICKS
    public long remaining(UUID id, long now) {
        Long woke = fallen.get(id);
        if (woke == null) {
            return 0;
        }
        if (woke == Long.MIN_VALUE) {
            return WAIT_TICKS;
        }
        return Math.max(0, woke + WAIT_TICKS - now);
    }

    public Status status(UUID id, long now, int limit) {
        if (!fallen.containsKey(id)) {
            return Status.NONE;
        }
        if (limit > 0 && used(id) >= limit) {
            return Status.EXHAUSTED;
        }
        return remaining(id, now) > 0 ? Status.WAITING : Status.READY;
    }

    public boolean mayEnter(UUID id, long now, int limit) {
        Status s = status(id, now, limit);
        return s == Status.NONE || s == Status.READY;
    }

    // 中へ戻った (目を覚ましてから戻った人だけ数える)。回数を 1 つ使い、倒れた記録を消す
    public boolean entered(UUID id) {
        if (!hasWoken(id)) {
            return false;
        }
        fallen.remove(id);
        used.merge(id, 1, Integer::sum);
        return true;
    }

    // 倒れていて、中へ戻っていない人 (知らせを出す相手)
    public Set<UUID> fallenIds() {
        return new LinkedHashSet<>(fallen.keySet());
    }

    public void clear() {
        fallen.clear();
        used.clear();
    }
}
