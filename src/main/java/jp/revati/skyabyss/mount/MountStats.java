package jp.revati.skyabyss.mount;

// 乗り物の性能 (docs/design/38 の 3 章)。速さ・跳び・体力の三つを、5 段ずつ鐘銭で上げる。どの見た目にも同じ段が掛かる。
// 段 0 = 買う前、段 5 = 最大。値段は段が上がるほど高い
public final class MountStats {
    public static final int TIERS = 5;
    public static final int SPEED = 0;
    public static final int JUMP = 1;
    public static final int HEALTH = 2;
    public static final String[] KEYS = {"speed", "jump", "health"};

    // 段 1〜5 にするとき払う額 (PRICE[種類][段 - 1])
    private static final int[][] PRICE = {
            {40, 80, 140, 220, 340},
            {30, 60, 100, 160, 250},
            {30, 60, 100, 160, 250},
    };
    // 段 0〜5 の値。速さは馬の中ほどから最上位まで、跳びは馬の下から上、体力はハート 10〜30
    private static final double[] SPEED_VALUE = {0.20, 0.23, 0.26, 0.29, 0.32, 0.35};
    private static final double[] JUMP_VALUE = {0.50, 0.57, 0.64, 0.72, 0.80, 0.90};
    private static final double[] HEALTH_VALUE = {20, 26, 32, 40, 50, 60};

    private MountStats() {
    }

    // 種類 kind を段 tier (1〜5) にするときの値段
    public static int price(int kind, int tier) {
        return PRICE[kind][Math.max(1, Math.min(TIERS, tier)) - 1];
    }

    public static double speed(int tier) {
        return SPEED_VALUE[clamp(tier)];
    }

    public static double jump(int tier) {
        return JUMP_VALUE[clamp(tier)];
    }

    public static double health(int tier) {
        return HEALTH_VALUE[clamp(tier)];
    }

    private static int clamp(int t) {
        return Math.max(0, Math.min(TIERS, t));
    }
}
