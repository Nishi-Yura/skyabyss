package jp.revati.skyabyss.abyss.arts;

// 深淵術 (docs/design/36 の 5 節)。光・重さ・鼓動の三つ。覚える溜まりは設計図の印 (pool_*)、数字はランクごと (1・2・3)
public enum AbyssArt {
    // 灯りを一つ置く (共有)。半径・続く秒数はランク順
    LIGHT("light", "pool_bone", 12, 160, 61),
    // 上昇の重みを緩める (使った人だけ)
    WEIGHT("weight", "pool_salt", 20, 600, 45),
    // 敵の輪郭を見せる・味方を癒す
    HEARTBEAT("heartbeat", "pool_storm", 25, 800, 92);

    // ランクごとの表 (添字 0 = ランク 1)
    public static final int MAX_RANK = 3;
    static final int[] LIGHT_RADIUS = {6, 8, 10};
    static final int[] LIGHT_LIFE_SECONDS = {120, 180, 240};
    static final int LIGHT_GLOW_TICKS = 100;
    static final int LIGHT_NIGHT_VISION_TICKS = 600;
    static final double[] WEIGHT_RELIEF = {0.5, 0.6, 0.7};
    static final int[] WEIGHT_LIFE_SECONDS = {20, 30, 40};
    static final int[] HEAL = {4, 6, 8};
    static final double HEAR_RADIUS = 24;
    static final double HEAL_RADIUS = 8;
    static final int HEAR_GLOW_TICKS = 160;

    public final String id;
    // 覚える溜まりの印 (AbyssBuild.mark)。灰の溜まり (pool_ash) は術を覚えず、木を開く
    public final String pool;
    public final int mana;
    public final int cooldownTicks;
    // 既定の鍵 (GLFW)。KeyDefaults に載せる
    public final int key;

    AbyssArt(String id, String pool, int mana, int cooldownTicks, int key) {
        this.id = id;
        this.pool = pool;
        this.mana = mana;
        this.cooldownTicks = cooldownTicks;
        this.key = key;
    }

    public String nameKey() {
        return "abyss_art." + id;
    }

    public String effectKey() {
        return "abyss_art." + id + ".effect";
    }

    public static AbyssArt byPool(String pool) {
        for (AbyssArt a : values()) {
            if (a.pool.equals(pool)) {
                return a;
            }
        }
        return null;
    }
}
