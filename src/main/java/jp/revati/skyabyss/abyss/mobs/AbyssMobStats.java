package jp.revati.skyabyss.abyss.mobs;

// 深淵の敵 A と B の動きの時間と当たりの数字 (docs/design/36 の 4.2)。すべて純粋な計算 (自己テストが直接見る)。
//   体力・傷の伸びは AbyssScaling (A と B で共通)。ここには行動の時間・割合・距離だけを置く。
public final class AbyssMobStats {
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
}
