package jp.revati.skyabyss.prologue;

// 見る者がどう消えたか。クライアントがサーバーへ知らせ、次に現れるまでの間隔と段階の進み方に使う
public enum WatcherOutcome {
    // 見つめ続けたので、薄れて消えた
    STARED(true),
    // 目を離したすきに消えた
    LOOKED_AWAY(true),
    // 近づいたので消えた
    APPROACHED(true),
    // 一度も気づかれないまま、時間切れで消えた
    UNSEEN(false),
    // この画面では見る者を出さない設定になっている
    DISABLED(false),
    // 気づかれる前に、朝・移動・死亡などで打ち切られた
    INTERRUPTED(false);

    private final boolean seen;

    WatcherOutcome(boolean seen) {
        this.seen = seen;
    }

    public boolean seen() {
        return seen;
    }

    public static WatcherOutcome byId(int id) {
        WatcherOutcome[] all = values();
        return id >= 0 && id < all.length ? all[id] : INTERRUPTED;
    }
}
