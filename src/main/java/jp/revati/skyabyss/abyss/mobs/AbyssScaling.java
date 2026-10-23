package jp.revati.skyabyss.abyss.mobs;

// 深淵の敵の数字の決まり (docs/design/36 の 4.2)。A 組も B 組も、この一つの決まりで体力と傷を出す。
//   数字は湧いた高さ (y) だけで決まる。縦穴の上端 (y 240) は元の数字 (1.00 倍)、心臓の間の底 (y -300) で体力 1.50 倍・傷 1.25 倍。
//   間は直線なので、層の境で跳ねない。深さの割合は 0〜1 で、外は端で止める。
//   段 (元の姿 1・歪み 2・崩れ 3) は見た目と、上の層の敵が混ざる割合・落とし物の確率にだけ使う。段で数字は変えない。
//   経験値の推奨の段 (ABYSS_LEVEL) もここに一つだけ置く (A 組の CONTENT_LEVEL と B 組の ABYSS_LEVEL を統一)。
public final class AbyssScaling {
    // 深淵の敵の成長の推奨の段 (docs/design/36 の 4.2)。五章の帯 Lv 32〜50 (番人の前の上限 40) の、番人の前の上限に合わせる。
    // Lv 43 (40 + 猶予 3) までは満額、その先は 1 レベルごとに 10% 減る (GrowthRules.levelFalloff)
    public static final int ABYSS_LEVEL = 40;
    // 成長の経験値の倍率 (表 4.2.1 の経験値に掛ける。バニラの経験値の玉 xpReward には掛けない)。
    // 2026-10-23: 四章の終わり (Lv 30〜33) から五章の帯へ届くように 1.5 倍
    public static final double GROWTH_XP_MUL = 1.5;

    // 縦穴の上端 (骨の層の上) と、心臓の間の底。数字はこの間で直線に増える
    public static final int TOP_Y = 240;
    public static final int HEART_Y = -300;
    public static final double HP_AT_HEART = 1.5;
    public static final double DMG_AT_HEART = 1.25;

    // 層の番号 0 骨 (y 120 より上)・1 塩 (0〜119)・2 雷 (-120〜-1)・3 灰 (-121〜-240)・4 深み (それより下。心臓の間を含む)
    public static final int LAYER_MAX = 4;
    // 心臓の下 (果てない深み。docs/design/36 の 9) の上乗せ。心臓の下の深さ DEEP_STEP ごとに一段、上限 DEEP_CAP 段。
    //   体力 +15% ×段、傷 +8% ×段 (上限で体力 2.2 倍・傷 1.64 倍)。縦穴の数字 (上の関数) の上に掛ける。縦穴の中では段 0 なので変わらない
    public static final double DEEP_STEP = 150;
    public static final int DEEP_CAP = 8;
    public static final double DEEP_HP_PER_STEP = 0.15;
    public static final double DEEP_DMG_PER_STEP = 0.08;

    // 段 1 元の姿・2 歪み・3 崩れ
    public static final int FORM_MAX = 3;

    private AbyssScaling() {
    }

    // 成長の経験値 (表の値 × GROWTH_XP_MUL。四捨五入、1 以上)
    public static int growthXp(int base) {
        return Math.max(1, (int) Math.round(base * GROWTH_XP_MUL));
    }

    // 深さの割合 (0 = 縦穴の上端、1 = 心臓の間の底。外は端で止める)
    public static double depth(double y) {
        double t = (TOP_Y - y) / (double) (TOP_Y - HEART_Y);
        return Math.max(0, Math.min(1, t));
    }

    public static double hpMul(double y) {
        return 1 + (HP_AT_HEART - 1) * depth(y);
    }

    public static double dmgMul(double y) {
        return 1 + (DMG_AT_HEART - 1) * depth(y);
    }

    // 体力と傷 (base は元の姿の数字。湧いた高さ y で倍になる)
    public static double hp(double base, double y) {
        return base * hpMul(y);
    }

    public static double dmg(double base, double y) {
        return base * dmgMul(y);
    }

    // 心臓の下の深さ (心臓の間の底より下へ何ブロック) から上乗せの段 (0〜DEEP_CAP)
    public static double deepSteps(double beyondHeart) {
        return Math.max(0, Math.min(DEEP_CAP, beyondHeart / DEEP_STEP));
    }

    public static double hpDeep(double base, double y, double steps) {
        return hp(base, y) * (1 + DEEP_HP_PER_STEP * steps);
    }

    public static double dmgDeep(double base, double y, double steps) {
        return dmg(base, y) * (1 + DEEP_DMG_PER_STEP * steps);
    }

    // 立っている y から層の番号 (0〜4)
    public static int layerOf(double y) {
        if (y >= 120) {
            return 0;
        }
        if (y >= 0) {
            return 1;
        }
        if (y >= -120) {
            return 2;
        }
        if (y >= -240) {
            return 3;
        }
        return LAYER_MAX;
    }

    // 段: 元の層 (native) より何層下で湧いたか (0 → 1、1 → 2、2 以上 → 3)。元の層より上へ出るときは段 1
    public static int form(int nativeLayer, double y) {
        return Math.max(1, Math.min(FORM_MAX, 1 + Math.max(0, layerOf(y) - nativeLayer)));
    }
}
