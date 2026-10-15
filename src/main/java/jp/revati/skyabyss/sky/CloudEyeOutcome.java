package jp.revati.skyabyss.sky;

// 雲の目がどう閉じたか。クライアントがサーバーへ知らせ、次の間隔と段階に使う
public enum CloudEyeOutcome {
    // 見つめた (視線が合った)。音が止み、目は閉じた
    MET(true),
    // 画面には入ったが、見つめないうちに閉じた
    SEEN(true),
    // 一度も画面に入らないまま閉じた
    MISSED(false),
    // この画面では出さない設定
    DISABLED(false),
    // 屋内に入った・ディメンションを移った・死んだ・雲より上にいた、などで打ち切った
    CANCELLED(false);

    private final boolean seen;

    CloudEyeOutcome(boolean seen) {
        this.seen = seen;
    }

    public boolean seen() {
        return seen;
    }

    public static CloudEyeOutcome byId(int id) {
        CloudEyeOutcome[] all = values();
        return id >= 0 && id < all.length ? all[id] : CANCELLED;
    }
}
