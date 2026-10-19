package jp.revati.skyabyss.rebirth;

// 転生の回数から色の段・記号を決める計算だけの部品 (Minecraft の型を使わない。自己テストが当てる)。docs/design/31 の C01・C02。
// 段: 白 (0) → 青緑 (1〜4) → 金 (5〜14) → プリズム (15 以上)。回数の上限は 20。
// 記号: 転生 1 回につき ✦ を 1 つ。5 つで ✧、10 で ✺ に束ねる (例: 7 回 = ✧✦✦、17 回 = ✺✧✦✦)
public final class RebirthTier {
    public static final int CAP = 20;
    public static final int TEAL_FROM = 1;
    public static final int GOLD_FROM = 5;
    public static final int PRISM_FROM = 15;

    public static final int WHITE_RGB = 0xE9EDF0;
    public static final int TEAL_RGB = 0x4FD0C4;
    public static final int TEAL_TEXT = 0x9FF3EA;
    public static final int GOLD_RGB = 0xE6B84A;
    public static final int GOLD_TEXT = 0xFFE08A;
    public static final int GOLD_SYMBOL = 0xFFD45A;
    // プリズムの色の巡り (レビューページの帯と同じ)。4 秒で一周
    private static final int[] PRISM_STOPS = {0xFF7AA8, 0xFFD45A, 0x6DF2B5, 0x6CB7FF, 0xC18CFF};
    public static final long PRISM_PERIOD_MS = 4000L;

    public enum Tier {
        WHITE, TEAL, GOLD, PRISM
    }

    private RebirthTier() {
    }

    public static int clamp(int count) {
        return Math.max(0, Math.min(CAP, count));
    }

    public static Tier of(int count) {
        int c = clamp(count);
        if (c >= PRISM_FROM) {
            return Tier.PRISM;
        }
        if (c >= GOLD_FROM) {
            return Tier.GOLD;
        }
        return c >= TEAL_FROM ? Tier.TEAL : Tier.WHITE;
    }

    // 名札・チャット・一覧の文字色。プリズムは timeMs で巡る (動きを減らす設定のときは staticPrism = true で止める。止めた色は金寄りの 1 色)
    public static int color(int count, long timeMs, boolean staticPrism) {
        return switch (of(count)) {
            case WHITE -> WHITE_RGB;
            case TEAL -> TEAL_TEXT;
            case GOLD -> GOLD_TEXT;
            case PRISM -> staticPrism ? PRISM_STOPS[1] : prism(timeMs, 0f);
        };
    }

    // 記号の色 (文字より少し濃い)
    public static int accent(int count, long timeMs, boolean staticPrism) {
        return switch (of(count)) {
            case WHITE -> WHITE_RGB;
            case TEAL -> TEAL_RGB;
            case GOLD -> GOLD_SYMBOL;
            case PRISM -> staticPrism ? PRISM_STOPS[1] : prism(timeMs, 0f);
        };
    }

    // 縁の色: 金は金、プリズムは回る色。offset (0..1) で文字ごとにずらす
    public static int border(int count, long timeMs, boolean staticPrism, float offset) {
        return switch (of(count)) {
            case WHITE -> WHITE_RGB;
            case TEAL -> TEAL_RGB;
            case GOLD -> GOLD_RGB;
            case PRISM -> staticPrism ? PRISM_STOPS[1] : prism(timeMs, offset);
        };
    }

    // プリズムの色: 5 色の間を滑らかに混ぜる。offset は一周の割合 (0..1)
    public static int prism(long timeMs, float offset) {
        double t = ((double) Math.floorMod(timeMs, PRISM_PERIOD_MS) / PRISM_PERIOD_MS + offset) % 1.0;
        if (t < 0) {
            t += 1.0;
        }
        double x = t * PRISM_STOPS.length;
        int i = (int) Math.floor(x) % PRISM_STOPS.length;
        double f = x - Math.floor(x);
        int a = PRISM_STOPS[i];
        int b = PRISM_STOPS[(i + 1) % PRISM_STOPS.length];
        int r = mix((a >> 16) & 255, (b >> 16) & 255, f);
        int g = mix((a >> 8) & 255, (b >> 8) & 255, f);
        int bl = mix(a & 255, b & 255, f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int mix(int a, int b, double f) {
        return (int) Math.round(a + (b - a) * f);
    }

    // 束ねた記号 ("" = 転生なし)
    public static String symbols(int count) {
        int c = clamp(count);
        return "✺".repeat(c / 10) + "✧".repeat((c % 10) / 5) + "✦".repeat(c % 5);
    }

    // チャットの接頭辞 「✦3」 (転生 0 は "")
    public static String chatPrefix(int count) {
        int c = clamp(count);
        return c <= 0 ? "" : "✦" + c;
    }

    // 「Lv 50+n」 (転生 0 は Lv のまま)
    public static String levelLabel(int level, int count) {
        int c = clamp(count);
        return c <= 0 ? "Lv " + level : "Lv 50+" + c;
    }

    // 色だけに頼らない: 段ごとの縁の形 (0 = なし、1 = 細い実線、2 = 二重線、3 = 角に飾り)
    public static int frameStyle(int count) {
        return switch (of(count)) {
            case WHITE -> 0;
            case TEAL -> 1;
            case GOLD -> 2;
            case PRISM -> 3;
        };
    }
}
