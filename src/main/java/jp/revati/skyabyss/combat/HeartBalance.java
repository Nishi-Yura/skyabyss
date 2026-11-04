package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.readiness.Readiness;

// 四つの心臓の手ごたえの式 (ゲームの状態に触れない計算だけ。docs/design 02 の 5.6 / 08 / 12 / 13 の「手ごたえ」の節)。
// 「その章の想定の装備のプレイヤーが、心臓と何分戦い、何度の間違いで倒れるか」を、心臓ごとの Profile から出す。
// 自己テスト (HeartBalanceSelfTest) が、人数 1・2・4・6 で戦いの長さが帯 (3〜5 分) に収まるか、
// 避けそこねた回数ごとの受ける傷が体力のどれだけかを確かめる。
//
// 前提 (想定のプレイヤー): その章の巨神の装備 +0 (GearCurve.STAGES の dps0・防具・強靭・耐性 I)、その章の想定の体力 (Readiness.goalHearts)、
// 近接で、動かない心臓を殴っていられる時間は UPTIME (技を避け、間合いを詰め直し、殻や脚に阻まれる分を引く)。
public final class HeartBalance {
    private HeartBalance() {
    }

    // 殴っていられる時間の割合 (一人)。人数が増えると、避ける線が増えるぶん 1 人増えるごとに PARTY_UPTIME_LOSS だけ下がる
    public static final double UPTIME = 0.55;
    public static final double PARTY_UPTIME_LOSS = 0.03;
    // 人数による強さ: 受ける傷を (1 + PARTY_STEP × (人数 − 1)) で割る。一人あたりの戦いの長さがほぼ同じになる値
    public static final double PARTY_STEP = 0.85;
    public static final int MAX_PARTY = 6;
    // 一人のときの戦いの長さの帯 (秒)。2026-11-04 の表 3-3: 序盤は短く、後ろの心臓ほど少し長く (崩しゲージ込み。BreakModel)
    public static double[] band(String stage) {
        return switch (stage) {
            case "stone_whale" -> new double[] {150, 180};
            case "tide_shell" -> new double[] {170, 200};
            case "thunder_wing" -> new double[] {190, 220};
            default -> new double[] {210, 240};
        };
    }
    // 最大体力 (属性の上限 1024 の下)。実質の体力はこれに基本の硬さ (resist) を掛けた値
    public static final float MAX_HP = 1000f;
    // 最終段階 (憤怒) に入る体力の割合と、各段階が受け持つ体力の割合 (段階 1 / 2 / 3 / 憤怒)
    public static final float ENRAGE_AT = 0.15f;
    public static final double[] PHASE_SHARE = {0.34, 0.33, 0.18, 0.15};
    // 心臓の一撃の狙い: 想定の防具の +0 で、最も重い一撃が想定の体力の何割か (3〜4 回で危ない)
    // 2026-10-25: 一撃を重く (前は 0.28・0.22〜0.34)。2〜3 回で危ない
    // 心臓の技の傷の倍率 (BossOpenings が掛ける)。2026-10-28 に 1.2 を試したが、避けそこね 4 回で体力の 1.5 倍までの公平の決まりを越えたので 1.0 のまま。
    // ごり押しは、崩しゲージ (BreakGauge) と戦いの中の食事の間 (FightRations) で防ぐ (docs/design/47 の 2.3)
    public static final double HIT_SCALE = 1.0;
    public static final double HEAVY_SHARE = 0.38;
    public static final double HEAVY_SHARE_MIN = 0.30;
    public static final double HEAVY_SHARE_MAX = 0.48;
    // 補給: 想定の備え (糧の灯 1.0) = 回復・吸収 24 点、戦いの中の自然回復は 1 秒あたり 0.1 点とみる
    public static final double PROVISIONS = Readiness.PROVISION_GOAL_POINTS;
    public static final double REGEN_PER_SECOND = 0.1;

    // 心臓ごとの数 (心臓の側が組んで渡す)。段階は 0 から 3 (3 = 憤怒)
    //  gap / action: 次の技までの間と技の長さの平均 (tick)。overlap: 技が重なる (二つ目が割り込む) 割合。
    //  hits[段階]: その段階の技の一撃 (防具の前の傷。同じ重みで選ばれる)。invulnTicks: 咆哮などで傷を受けない合計の長さ
    public record Profile(String stage, float maxHp, float resist, int[] gap, int[] action, double[] overlap, float[][] hits, int invulnTicks) {
        public GearCurve.Stage gear() {
            return HeartBalance.stage(stage);
        }

        public double effectiveHp() {
            return maxHp * resist;
        }
    }

    public static GearCurve.Stage stage(String id) {
        for (GearCurve.Stage s : GearCurve.STAGES) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        throw new IllegalArgumentException(id);
    }

    // 心臓の防御・強靭 (属性。各心臓の createAttributes と同じ値)
    public static double heartArmor(String stage) {
        return switch (stage) {
            case "stone_whale", "tide_shell" -> 8;
            default -> 9;
        };
    }

    public static double heartToughness(String stage) {
        return switch (stage) {
            case "stone_whale", "tide_shell", "thunder_wing" -> 6;
            default -> 7;
        };
    }

    // ---------------------------------------------------------------- 人数
    public static double partyScale(int players) {
        return 1 + PARTY_STEP * (Math.max(1, Math.min(MAX_PARTY, players)) - 1);
    }

    public static double partyUptime(int players) {
        return UPTIME * (1 - PARTY_UPTIME_LOSS * (Math.max(1, Math.min(MAX_PARTY, players)) - 1));
    }

    // ---------------------------------------------------------------- 攻め
    // 技の木の上乗せ (与える傷)。2026-11-04 に木の節を大きくした (docs/design/48 の 4.1) ので、主の硬さの見積もりに入れる。
    // 木のデータから、レベル (= SP) で幹と型ひとつを傷の節から取ったときの上乗せ (近接・矢 0.6 + 技 0.4) を六職で平均し、その 0.75 倍 (ふつうの取り方) にした値。
    // 技の木 v3 (2026-11-04) で数え直した (前提の道の SP を輪なしで数える)。前の表とほぼ同じ (中ほどで少し上、後ろで少し下)
    static final int[] TREE_LEVELS = {0, 4, 12, 18, 24, 30, 46};
    static final double[] TREE_BONUS = {0, 0.0, 0.055, 0.164, 0.235, 0.266, 0.284};

    public static double treeBonus(int level) {
        if (level <= TREE_LEVELS[0]) {
            return 0;
        }
        for (int i = 1; i < TREE_LEVELS.length; i++) {
            if (level <= TREE_LEVELS[i]) {
                double t = (level - TREE_LEVELS[i - 1]) / (double) (TREE_LEVELS[i] - TREE_LEVELS[i - 1]);
                return TREE_BONUS[i - 1] + (TREE_BONUS[i] - TREE_BONUS[i - 1]) * t;
            }
        }
        return TREE_BONUS[TREE_BONUS.length - 1];
    }

    // 一人の 1 秒あたりの傷 (心臓の防具を通したあと、殴り続けたとき。技の木の上乗せ込み)。weaponMultiplier: 想定の武器に対する倍 (+10 なら dps10 / dps0)
    public static double playerDps(String stage, double weaponMultiplier) {
        GearCurve.Stage s = stage(stage);
        double strength = GearCurve.strengthMultiplier(s.level());
        double hit = s.dps0() / GearCurve.SWORD_SPEED * strength;
        double taken = GearCurve.armorTaken(hit, heartArmor(stage), heartToughness(stage));
        return s.dps0() * weaponMultiplier * strength * taken * (1 + treeBonus(s.level()));
    }

    // 崩しゲージ込みの戦い (BreakModel)。基本の硬さと人数の強さは受ける傷を割るので、体力の削りの速さを割る
    public static BreakModel.Fight fight(Profile p, int players, double weaponMultiplier) {
        double hps = playerDps(p.stage(), weaponMultiplier) * players * partyUptime(players) / (p.resist() * partyScale(players));
        return BreakModel.fight(p.maxHp(), hps, partyUptime(players));
    }

    // 倒すまでの秒 (咆哮などの傷を受けない時間込み)
    public static double fightSeconds(Profile p, int players, double weaponMultiplier) {
        return fight(p, players, weaponMultiplier).seconds() + p.invulnTicks() / 20.0;
    }

    // 段階ごとの戦いの秒 (傷を受けない時間は最初の段階以外に均等に割る)
    public static double phaseSeconds(Profile p, int phase, int players, double weaponMultiplier) {
        double base = fight(p, players, weaponMultiplier).seconds() * PHASE_SHARE[phase];
        return base + (phase == 0 ? 0 : p.invulnTicks() / 20.0 / 3);
    }

    // ---------------------------------------------------------------- 守り
    // 想定の体力 (点)
    public static double expectedHealth(String stage) {
        return Readiness.goalHearts(stage) * 2;
    }

    // 防具の前の傷 raw を、その章の想定の防具 +0 と耐性 I を通して受ける傷
    public static double taken(String stage, double raw) {
        GearCurve.Stage s = stage(stage);
        double hit = raw * HIT_SCALE;
        return hit * GearCurve.armorTaken(hit, s.armor(), s.toughness()) * s.setMultiplier();
    }

    // 一撃が想定の体力のどれだけか
    public static double share(String stage, double raw) {
        return taken(stage, raw) / expectedHealth(stage);
    }

    // 最も重い一撃 (防具の前)
    public static double heaviest(Profile p) {
        double max = 0;
        for (float[] phase : p.hits()) {
            for (float h : phase) {
                max = Math.max(max, h);
            }
        }
        return max;
    }

    // 段階ごとの 1 秒あたりの技の数 (二つ重なる分を足す)
    public static double attacksPerSecond(Profile p, int phase) {
        return (1 + p.overlap()[phase]) * 20.0 / (p.gap()[phase] + p.action()[phase]);
    }

    // 段階ごとの、技一つで受ける傷の平均 (想定の防具を通したあと)
    public static double meanTaken(Profile p, int phase) {
        double sum = 0;
        for (float h : p.hits()[phase]) {
            sum += taken(p.stage(), h);
        }
        return sum / p.hits()[phase].length;
    }

    // N 回の避けそこね (どれも平均の傷) が想定の体力のどれだけか。1 以上なら、補給なしで倒れる
    public static double failedDodges(Profile p, int phase, int n) {
        return n * meanTaken(p, phase) / expectedHealth(p.stage());
    }

    // 戦いの間に技が出る数 (一人、想定の武器、全段階)
    public static double totalAttacks(Profile p, int players) {
        double sum = 0;
        for (int phase = 0; phase < 4; phase++) {
            sum += phaseSeconds(p, phase, players, 1.0) * attacksPerSecond(p, phase);
        }
        return sum;
    }

    // 避ける確率 dodge のとき、戦いの間に受ける傷の合計 (点。想定の防具の +0)
    public static double expectedDamage(Profile p, int players, double dodge) {
        double sum = 0;
        for (int phase = 0; phase < 4; phase++) {
            sum += phaseSeconds(p, phase, players, 1.0) * attacksPerSecond(p, phase) * (1 - dodge) * meanTaken(p, phase);
        }
        return sum;
    }

    // 耐えられる合計 (体力 + 補給 + 自然回復)
    public static double budget(Profile p, int players) {
        return expectedHealth(p.stage()) + PROVISIONS + REGEN_PER_SECOND * fightSeconds(p, players, 1.0);
    }
}
