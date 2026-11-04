package jp.revati.skyabyss.combat;

// 崩しゲージを入れた戦いの長さの見積もり (docs/design/47 の 2.1。ゲームの状態に触れない計算だけ)。
// 主の体力 maxHp を、倍率 1 のときの 1 秒あたりの削り hps (殴っていられる割合 uptime をもう掛けた値) で削るとき、
//   ふだん: 削り ×BreakGauge.NORMAL。与えた傷 × FILL_MEAN だけゲージが溜まる
//   崩れ: BreakGauge.BREAK_TICKS のあいだ削り ×BreakGauge.BROKEN。主が止まるので殴っていられる割合は BREAK_UPTIME (uptime の代わり)
//   ゲージの大きさ: maxHp × BreakGauge.SIZE × BreakGauge.GROW[崩れた回数]
// 仕掛け (心臓の領域) で溜まる半分は入れない (その分、実際の戦いは少し短い)
public final class BreakModel {
    private BreakModel() {
    }

    // 溜まりの平均の掛け目: 満ちた近接は ×1、技のあとの隙 (×3) に入る傷を 3 割とみる (0.3 × 3 + 0.7 × 1)
    public static final double FILL_MEAN = 1.6;
    // 崩れて止まった主を殴っていられる割合
    public static final double BREAK_UPTIME = 0.85;

    public record Fight(double seconds, int breaks) {
    }

    public static Fight fight(double maxHp, double hps, double uptime) {
        double hp = maxHp;
        double t = 0;
        int breaks = 0;
        double normal = BreakGauge.NORMAL * hps;
        double broken = BreakGauge.BROKEN * hps * BREAK_UPTIME / Math.max(0.05, uptime);
        double breakSeconds = BreakGauge.BREAK_TICKS / 20.0;
        for (int guard = 0; guard < 100 && hp > 1.0E-9; guard++) {
            double size = maxHp * BreakGauge.SIZE * BreakGauge.GROW[Math.min(breaks, BreakGauge.GROW.length - 1)];
            double toFill = size / FILL_MEAN;
            if (hp <= toFill) {
                t += hp / normal;
                break;
            }
            hp -= toFill;
            t += toFill / normal;
            breaks++;
            double during = broken * breakSeconds;
            if (hp <= during) {
                t += hp / broken;
                break;
            }
            hp -= during;
            t += breakSeconds;
        }
        return new Fight(t, breaks);
    }
}
