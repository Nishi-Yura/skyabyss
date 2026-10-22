package jp.revati.skyabyss.abyss.mobs;

// 深淵の敵 A の数字と時間 (docs/design/36 の 4.2)。すべて純粋な計算 (自己テストが直接見る)。
//   層の番号  0 骨 (y 120 より上) ・ 1 塩 (y 0〜119) ・ 2 雷 (y -120〜-1) ・ 3 灰 (y -121〜-240) ・ 4 深み (それより下)
//   段      1 元の姿 (元の層) ・ 2 歪み (元の層の一つ下) ・ 3 崩れ (二つ下以上・深み)
//   伸び    層ごとに体力 +5%・攻撃 +4%、段ごとに体力 ×1.0 → 1.2 → 1.45、攻撃 ×1.0 → 1.15 → 1.3 (急には上げない)
public final class AbyssMobStats {
    public static final int DEPTH_MAX = 4;
    public static final int STAGE_MAX = 3;
    public static final double HP_PER_LAYER = 0.05;
    public static final double DAMAGE_PER_LAYER = 0.04;
    private static final double[] STAGE_HP = {0, 1.0, 1.2, 1.45};
    private static final double[] STAGE_DAMAGE = {0, 1.0, 1.15, 1.3};

    // 予告の長さ (tick。20 = 1 秒)。赤は予告の色だけ (ほかの赤い粒は使わない)
    public static final int WARN_RING = 30;       // 潮だまりの手・避雷の像の輪
    public static final int WARN_SHADOW = 24;     // 焦げた鳥の影 (暗い粒)
    public static final int WARN_CALL = 30;       // 骨笛が呼ぶ前
    public static final int WARN_CRACK = 30;      // 塩の抜け殻がひびから動く前
    public static final int SNARE_TICKS = 60;     // 縛られる時間 (3 秒)
    public static final int SNARE_SLOW_AMP = 1;   // 鈍足 II
    public static final int STRIKE_EVERY = 120;   // 避雷の像 (6 秒)
    public static final int SNARE_EVERY = 160;    // 潮だまりの手 (8 秒)
    public static final int CALL_EVERY = 200;     // 骨笛 (10 秒)
    public static final int SHELL_PERIOD = 160;   // 白い貝守 (8 秒の周期)
    public static final int SHELL_OPEN = 60;      // 開いている間 (3 秒)
    public static final int DIVE_EVERY = 100;     // 焦げた鳥の影 (5 秒)
    public static final double AMBUSH_WAKE = 4.0; // 塩の抜け殻が割れる距離 (ブロック)
    public static final double FRONT_DOT = 0.5;   // 盾の正面 (cos 60 度より前)
    public static final double SHIELD_KEEP = 0.1; // 盾で受ける割合 (正面からは 10%)
    public static final double SNARE_RADIUS = 2.5;
    public static final double STRIKE_RADIUS = 1.5;
    public static final double PIERCE_LENGTH = 8.0;
    public static final double SHELL_KEEP = 0.1;  // 殻にこもっている間に通る割合

    private AbyssMobStats() {
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
        return DEPTH_MAX;
    }

    // 段: 元の層より何段下で出たか (0 → 1、1 → 2、2 以上 → 3)
    public static int stage(int nativeLayer, int spawnLayer) {
        return Math.max(1, Math.min(STAGE_MAX, 1 + Math.max(0, spawnLayer - nativeLayer)));
    }

    public static double hp(AbyssMobKind k, int spawnLayer, int stage) {
        return k.baseHp * (1 + HP_PER_LAYER * clampDepth(spawnLayer)) * STAGE_HP[clampStage(stage)];
    }

    public static double damage(AbyssMobKind k, int spawnLayer, int stage) {
        return k.baseDamage * (1 + DAMAGE_PER_LAYER * clampDepth(spawnLayer)) * STAGE_DAMAGE[clampStage(stage)];
    }

    // 経験値 (段ごとに +10%)
    public static int xp(AbyssMobKind k, int stage) {
        return Math.round(k.xp * (1 + 0.1f * (clampStage(stage) - 1)));
    }

    // 塩の抜け殻が割れて動く距離 (以下なら割れる)
    public static boolean ambushWakes(double distance) {
        return distance <= AMBUSH_WAKE;
    }

    // 白い貝守の殻が開いているか (tick は敵の生きている tick)
    public static boolean shellOpen(int tick) {
        return Math.floorMod(tick, SHELL_PERIOD) < SHELL_OPEN;
    }

    // 盾の正面か (正面の向きと、攻撃してきた方向の内積。両方とも水平の単位ベクトル)
    public static boolean inFrontOfShield(double dot) {
        return dot >= FRONT_DOT;
    }

    // 盾の正面からの攻撃の残り (正面なら 10%、横と後ろは全部)
    public static double shieldKeep(double dot) {
        return inFrontOfShield(dot) ? SHIELD_KEEP : 1.0;
    }

    // 殻にこもった敵が通すぶん
    public static double shellKeep(int tick) {
        return shellOpen(tick) ? 1.0 : SHELL_KEEP;
    }

    private static int clampDepth(int d) {
        return Math.max(0, Math.min(DEPTH_MAX, d));
    }

    private static int clampStage(int s) {
        return Math.max(1, Math.min(STAGE_MAX, s));
    }
}
