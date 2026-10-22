package jp.revati.skyabyss.abyss.mobs;

// 深淵の敵 B 組の段と数値 (docs/design/36 の 4.2・A3)。
//   段 0 = 元の姿、1 = 歪み、2 = 崩れ。同じ骨・同じ動きで、絵 (textures/lowpoly/<id>_s1・_s2) と数値だけが替わる。
//   灰の層の敵と深淵だけの敵は、y で段を決める (灰の層の中で下がるほど歪む。灰の層より上の層でも、下へ行く者は段 0)。
//   深み (心臓の下、y < -300) の敵は、深さで段を決める (-300 から 200 ごとに一段)。
//   数値は段ごとに少しずつ上げる (HP +15%・傷 +10% を一段ごと)。深みは 100 ブロックごとに HP +4%・傷 +2% (上限 HP +40%・傷 +20%)。
public final class AbyssMobsBStage {
    public static final int MAX = 2;
    public static final int DEPTH_TOP = -300;
    public static final double HP_STEP = 0.15;
    public static final double DMG_STEP = 0.10;
    public static final double DEPTH_HP_PER_100 = 0.04;
    public static final double DEPTH_DMG_PER_100 = 0.02;
    public static final double DEPTH_HP_CAP = 0.40;
    public static final double DEPTH_DMG_CAP = 0.20;

    private AbyssMobsBStage() {
    }

    // 灰の層と深淵だけの敵の段 (y): 灰の層の上の方は元の姿、中ほどで歪み、灰の層の底近くで崩れ
    public static int stageAt(int y) {
        if (y > -160) {
            return 0;
        }
        return y > -220 ? 1 : 2;
    }

    // 深みの段 (y < -300): 深さ 200 ごとに一段 (-300〜-500 は 0、-500〜-700 は 1、それより下は 2)
    public static int depthStage(int y) {
        int d = DEPTH_TOP - y;
        if (d < 200) {
            return 0;
        }
        return d < 400 ? 1 : 2;
    }

    // 段の HP・傷の倍率
    public static double hpMul(int stage) {
        return 1 + HP_STEP * clamp(stage);
    }

    public static double dmgMul(int stage) {
        return 1 + DMG_STEP * clamp(stage);
    }

    // 深さの加算 (y < -300 のとき。100 ブロックごとに上げ、上限まで)
    public static double depthHp(int y) {
        return 1 + Math.min(DEPTH_HP_CAP, depthDepth(y) / 100.0 * DEPTH_HP_PER_100);
    }

    public static double depthDmg(int y) {
        return 1 + Math.min(DEPTH_DMG_CAP, depthDepth(y) / 100.0 * DEPTH_DMG_PER_100);
    }

    public static double hp(double base, int stage, int y) {
        return base * hpMul(stage) * depthHp(y);
    }

    public static double dmg(double base, int stage, int y) {
        return base * dmgMul(stage) * depthDmg(y);
    }

    static int depthDepth(int y) {
        return Math.max(0, DEPTH_TOP - y);
    }

    static int clamp(int stage) {
        return Math.max(0, Math.min(MAX, stage));
    }
}
