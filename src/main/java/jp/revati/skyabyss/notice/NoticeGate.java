package jp.revati.skyabyss.notice;

import java.util.ArrayList;
import java.util.List;

// 画面に出す知らせの「出す順と間隔」の関所 (first_hour_log の B-2・B-9)。右上の札 (クエスト・伝承・獲得)・経験値とレベルアップ・中央の演出・手がかりが、
// 会話や場面の画面の上に同時に積まれないよう、画面が開いている間は溜め、閉じたあとに優先の順で間隔をあけて一つずつ出す。
// 画面が開いておらず、前の一つから間隔がたっていれば、すぐ出す (ふだんの遊びでは遅れない)。
// 時間はミリ秒で数える純粋な部品 (描画・ゲームから切り離してあり、自己テストで検める)
public final class NoticeGate {
    // 優先 (小さいほど先): 札 → 経験値・レベルアップ → 中央の演出 → 手がかり
    public static final int P_CARD = 0;
    public static final int P_GROWTH = 1;
    public static final int P_CENTER = 2;
    public static final int P_HINT = 3;
    // 溜められる数。あふれたら手がかり、なければ一番古いものから捨てる
    public static final int MAX_PENDING = 16;

    private record Item(Runnable run, int priority, long gap, long seq) {
    }

    private final List<Item> pending = new ArrayList<>();
    private long nextAt = Long.MIN_VALUE;
    private long seq;

    // 出す仕事を預ける。gapAfter は、これを出したあと次を出すまでの間隔 (ミリ秒)
    public void offer(int priority, long gapAfter, Runnable run) {
        if (pending.size() >= MAX_PENDING) {
            int drop = -1;
            for (int i = 0; i < pending.size(); i++) {
                if (pending.get(i).priority == P_HINT) {
                    drop = i;
                    break;
                }
            }
            pending.remove(drop >= 0 ? drop : 0);
        }
        pending.add(new Item(run, priority, gapAfter, seq++));
    }

    // 毎 tick と、預けた直後に呼ぶ。busy (会話・場面の画面が開いている) の間は出さない。出したら一つだけ。出した数を返す
    public int drain(long nowMillis, boolean busy) {
        if (busy || pending.isEmpty() || nowMillis < nextAt) {
            return 0;
        }
        Item best = pending.get(0);
        for (Item it : pending) {
            if (it.priority < best.priority || (it.priority == best.priority && it.seq < best.seq)) {
                best = it;
            }
        }
        pending.remove(best);
        nextAt = nowMillis + best.gap;
        best.run.run();
        return 1;
    }

    public int pending() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
        nextAt = Long.MIN_VALUE;
    }
}
