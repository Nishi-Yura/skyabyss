package jp.revati.skyabyss.tutorial;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// 「できた」の表示の状態 (画面や時計に触れない純粋な部分)。出した一回だけ DONE_MS の間見せて消える。
// 時計は「見せている間に経ったミリ秒」を足していく (画面が出せない間は足さない。壊れた「済んだ時刻」を持たない)。
// 同じ ID は一度見せたら、もう一度送られてきても出し直さない
public final class DoneBanners {
    public static final long DONE_MS = 1800;
    public static final long FADE_MS = 600;

    private final Map<String, Long> shownFor = new LinkedHashMap<>();
    private final Set<String> seen = new HashSet<>();

    // 受け取る。初めての ID なら true
    public boolean add(String id) {
        if (!seen.add(id)) {
            return false;
        }
        shownFor.put(id, 0L);
        return true;
    }

    // dtMs だけ進める。visible が false (会話や画面を開いている) の間は進めない。DONE_MS を過ぎたものは消す
    public void tick(long dtMs, boolean visible) {
        if (!visible || dtMs <= 0) {
            return;
        }
        for (var it = shownFor.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            e.setValue(e.getValue() + dtMs);
            if (e.getValue() > DONE_MS) {
                it.remove();
            }
        }
    }

    public Set<String> ids() {
        return shownFor.keySet();
    }

    public boolean isEmpty() {
        return shownFor.isEmpty();
    }

    // 濃さ (終わりの FADE_MS で 1 → 0)
    public float alpha(String id) {
        Long t = shownFor.get(id);
        if (t == null) {
            return 0f;
        }
        float a = 1f - (t - (DONE_MS - FADE_MS)) / (float) FADE_MS;
        return Math.max(0f, Math.min(1f, a));
    }

    public void clear() {
        shownFor.clear();
        seen.clear();
    }
}
