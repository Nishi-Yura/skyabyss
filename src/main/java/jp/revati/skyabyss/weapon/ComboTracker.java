package jp.revati.skyabyss.weapon;

// 連撃の数え方 (クライアントの SwingClock とサーバーの WeaponStrikes が同じ関数を使い、同じ段を見る)。
// 前の振りの始まりから「振りの長さ + 受け付けの猶予 (tick)」以内に次の振りが始まれば、次の段へ。超えたら 1 段目へ戻る。最後の段のあとは 1 段目へ戻る
public final class ComboTracker {
    private ComboTracker() {
    }

    // prevStep = 前の段 (前の振りがなければ -1)、sinceStart = 前の振りの始まりからの tick
    public static int next(int prevStep, int sinceStart, int duration, int window, int steps) {
        if (steps <= 1 || prevStep < 0 || sinceStart > duration + window) {
            return 0;
        }
        return (prevStep + 1) % steps;
    }
}
