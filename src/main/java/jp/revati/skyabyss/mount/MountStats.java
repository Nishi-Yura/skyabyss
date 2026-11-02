package jp.revati.skyabyss.mount;

// 乗り物の性能 (docs/design/38 の 3 章)。速さ (8 段) と跳び (5 段) を鐘銭で上げる。どの見た目にも同じ段が掛かる。
// 段 0 = 買う前。値段は段が上がるほど高い。
// 体力は段を持たない (2026-11-02 の声「ペットの体力を増やす仕組みはいらない」): いつも FIXED_HEALTH (前の段 5 と同じ)。
// 前に買った体力の段は、入ったときに鐘銭で返す (upgrade/PlayerUpgrade の段 2。healthRefund)。保存の並び (tiers[2]) は残す
public final class MountStats {
    public static final int SPEED = 0;
    public static final int JUMP = 1;
    public static final int HEALTH = 2;
    // 店に並ぶ性能 (この順に行が出る)
    public static final String[] KEYS = {"speed", "jump"};
    // 種類ごとの段の数 (体力は前の段の数。返す額を数えるためだけ)
    private static final int[] TIER_COUNT = {8, 5, 5};
    public static final int MAX_TIERS = 8;
    public static final double FIXED_HEALTH = 60;

    // 段 1〜n にするとき払う額 (PRICE[種類][段 - 1])
    private static final int[][] PRICE = {
            {25, 50, 85, 130, 200, 300, 420, 600},
            {18, 36, 60, 95, 150},
            {18, 36, 60, 95, 150},
    };
    // 段 0〜n の値。速さは馬の中ほどから、段 5 で馬の最上位 (0.35)、段 8 で 0.44 (2026-11-02 の声「速さはもう少し速く」)。跳びは馬の下から上
    private static final double[] SPEED_VALUE = {0.20, 0.23, 0.26, 0.29, 0.32, 0.35, 0.38, 0.41, 0.44};
    private static final double[] JUMP_VALUE = {0.50, 0.57, 0.64, 0.72, 0.80, 0.90};

    private MountStats() {
    }

    public static int tiers(int kind) {
        return TIER_COUNT[Math.max(0, Math.min(TIER_COUNT.length - 1, kind))];
    }

    // 種類 kind を段 tier (1〜n) にするときの値段
    public static int price(int kind, int tier) {
        return PRICE[kind][Math.max(1, Math.min(tiers(kind), tier)) - 1];
    }

    public static double speed(int tier) {
        return SPEED_VALUE[clamp(SPEED, tier)];
    }

    public static double jump(int tier) {
        return JUMP_VALUE[clamp(JUMP, tier)];
    }

    // 前に買った体力の段 1〜tier の値段の合計 (返す額)
    public static int healthRefund(int tier) {
        int n = 0;
        for (int t = 1; t <= clamp(HEALTH, tier); t++) {
            n += price(HEALTH, t);
        }
        return n;
    }

    public static int clamp(int kind, int t) {
        return Math.max(0, Math.min(tiers(kind), t));
    }
}
