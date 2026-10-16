package jp.revati.skyabyss.rpg.skill;

import net.minecraft.util.Mth;

// 技とマナの数値。ゲームの状態に触れない計算だけを置く (自己テストで直接たしかめる)。
// 表と理由は docs/design/04_growth_and_skills.md の 10 章
public final class SkillRules {
    public static final int SLOTS = 4;
    // 1 秒 = 20 tick
    public static final int SECOND = 20;
    // どの技を使ったあとも、次の技まで 0.5 秒あける (連打で重ねがけしない・手触りを重くする)
    public static final int GLOBAL_COOLDOWN = 10;

    // ---------------------------------------------------------------- 技の点
    // レベル 1 で 1、そのあと奇数のレベル (3, 5, 7, ...) ごとに 1。レベル 50 で 25
    public static int pointsForLevel(int level) {
        int l = Mth.clamp(level, 1, 50);
        return (l + 1) / 2;
    }

    // ---------------------------------------------------------------- マナ
    // 戦いの最中の回復 (1 秒あたり) = 0.4 + 最大マナの 2%。静かなとき (5 秒、傷を受けも与えもしない) は 2.5 倍
    public static final double REGEN_FLAT = 0.4;
    public static final double REGEN_PERCENT = 0.02;
    public static final double REGEN_CALM_MULTIPLIER = 2.5;
    public static final int CALM_AFTER_TICKS = 5 * SECOND;

    public static double regenPerSecond(double maxMana, boolean calm) {
        double r = REGEN_FLAT + REGEN_PERCENT * maxMana;
        return calm ? r * REGEN_CALM_MULTIPLIER : r;
    }

    // ---------------------------------------------------------------- 振り直し
    // 初回は無料。そのあと共鳴の結晶 1, 2, 3, 4, 5 (5 で頭打ち)
    public static final int RESPEC_MAX_COST = 5;
    // 共鳴の炉からこの距離 (ブロック) 以内で振り直せる
    public static final int RESPEC_FORGE_RANGE = 6;

    public static int respecCost(int respecsSoFar) {
        return Math.min(Math.max(0, respecsSoFar), RESPEC_MAX_COST);
    }

    // ---------------------------------------------------------------- 消費マナとクールダウン
    public static int manaCost(Skill skill, int rank) {
        return switch (skill) {
            case TITAN_CLEAVE -> 10;
            case LUNGE -> 6;
            case WHALE_SWEEP -> 16;
            case CLOUD_PIERCE -> 10;
            case BACKSTEP -> 5;
            case SKY_RAIN -> 18;
            default -> 0;
        };
    }

    // tick。段が上がると短くなる技もある
    public static int cooldownTicks(Skill skill, int rank) {
        int r = Math.max(1, rank);
        return switch (skill) {
            case TITAN_CLEAVE -> 8 * SECOND;
            case LUNGE -> (r >= 2 ? 5 : 7) * SECOND;
            case WHALE_SWEEP -> (r >= 2 ? 11 : 14) * SECOND;
            case CLOUD_PIERCE -> 8 * SECOND;
            case BACKSTEP -> (r >= 2 ? 4 : 6) * SECOND;
            case SKY_RAIN -> (r >= 2 ? 14 : 18) * SECOND;
            default -> 0;
        };
    }

    // ---------------------------------------------------------------- 剣士
    // 巨神斬り: 前方の扇 (半径 4、左右 60 度) に、攻撃力 × (0.9 + 0.3 × 段)。大きな相手には さらに × 1.5
    public static final double CLEAVE_RANGE = 4.0;
    public static final double CLEAVE_HALF_ANGLE = 60;
    public static final int CLEAVE_MAX_TARGETS = 5;
    public static final double CLEAVE_LARGE_BONUS = 1.5;

    public static double cleaveMultiplier(int rank) {
        return 0.9 + 0.3 * rank;
    }

    // 大きな相手: 幅 × 高さ が 2.5 以上 (アイアンゴーレム、ラヴェジャー、番兵長) か、最大体力 100 以上 (ボス)
    public static boolean isLarge(float width, float height, float maxHealth) {
        return width * height >= 2.5f || maxHealth >= 100f;
    }

    // 返し刃: 生き物から傷を受けてから 3 秒以内の、次の近接攻撃の威力 +15% / +30% / +45%
    public static final int RIPOSTE_WINDOW = 3 * SECOND;

    public static double riposteBonus(int rank) {
        return 0.15 * rank;
    }

    // 踏み込み: 8 ブロック以内の敵の手前まで一気に詰め、攻撃力 × 0.8 で斬る。足場のない所へは踏み出さない
    public static final double LUNGE_RANGE = 8.0;
    public static final double LUNGE_STOP = 1.4;
    public static final double LUNGE_MULTIPLIER = 0.8;

    // 岩の構え: 段ごとに防具 +1、ノックバック耐性 +0.1
    public static double stanceArmor(int rank) {
        return rank;
    }

    public static double stanceKnockback(int rank) {
        return 0.1 * rank;
    }

    // 鯨尾薙ぎ: 周り (半径 3.5) の敵すべてに 攻撃力 × (0.9 / 1.2)。外へ軽く押し出す
    public static final double SWEEP_RADIUS = 3.5;

    public static double sweepMultiplier(int rank) {
        return rank >= 2 ? 1.2 : 0.9;
    }

    // ---------------------------------------------------------------- 弓使い
    // 雲穿ち: 弓を構えずに放つ、まっすぐ飛ぶ貫く矢 (矢を 1 本使う)。速さ 3.6、基礎威力 2.2 / 2.6 / 3.0、貫く数 2 / 3 / 4
    public static final float PIERCE_SPEED = 3.6f;
    public static final int PIERCE_LIFETIME = 30;

    public static double pierceBaseDamage(int rank) {
        return 1.8 + 0.4 * rank;
    }

    public static int piercePierce(int rank) {
        return 1 + rank;
    }

    // 矢の威力 (バニラと同じく 速さ × 基礎威力 の切り上げ)。会心はない
    public static int pierceDamage(int rank) {
        return Mth.ceil(PIERCE_SPEED * pierceBaseDamage(rank));
    }

    // 遠矢の心得: 飛び道具の威力が距離で上がる。8 ブロックで 0、24 ブロック以上で最大 (+6% / +12% / +18%)
    public static final double FAR_MIN = 8;
    public static final double FAR_FULL = 24;

    public static double farShotBonus(int rank, double distance) {
        double t = Mth.clamp((distance - FAR_MIN) / (FAR_FULL - FAR_MIN), 0, 1);
        return 0.06 * rank * t;
    }

    // 跳び退き: 地に足が着いていて、10 ブロック以内に敵がいるときだけ。後ろへ跳ぶ (水平 0.85、上 0.42)。
    // 跳ぶ最初の tick は地面の摩擦が掛かるので、届くのはおよそ 4 ブロック (走って跳ぶのと同じくらい。アスレの近道にならない)
    public static final double BACKSTEP_ENEMY_RANGE = 10;
    public static final double BACKSTEP_PUSH = 0.85;
    public static final double BACKSTEP_LIFT = 0.42;

    // 鷹の目: 自分の矢が当たった敵に 5 秒の印。印の敵は、誰の攻撃でも 5% / 8% / 12% 多く傷つく (重ならない。高いほうを使う)
    public static final int MARK_TICKS = 5 * SECOND;

    public static double markBonus(int rank) {
        return switch (Mth.clamp(rank, 0, 3)) {
            case 1 -> 0.05;
            case 2 -> 0.08;
            case 3 -> 0.12;
            default -> 0;
        };
    }

    // 天の驟雨: 見ている先 (24 ブロック以内) の半径 3 に、0.5 秒ごとに 6 回、敵だけを射る。1 回 2.0 / 3.0 (敏捷の射撃の補正も掛かる)
    public static final double RAIN_REACH = 24;
    public static final double RAIN_RADIUS = 3;
    public static final int RAIN_PULSES = 6;
    public static final int RAIN_INTERVAL = 10;

    public static double rainDamage(int rank) {
        return rank >= 2 ? 3.0 : 2.0;
    }

    // ---------------------------------------------------------------- 画面の説明に渡す値
    // 翻訳の %s に入れる値 (段ごと)。lang の skill.skyabyss.<id>.effect と順をそろえる
    public static Object[] describeArgs(Skill skill, int rank) {
        int r = Math.max(1, rank);
        return switch (skill) {
            case TITAN_CLEAVE -> new Object[] {pct(cleaveMultiplier(r)), fmt(CLEAVE_LARGE_BONUS)};
            case RIPOSTE -> new Object[] {pct(riposteBonus(r)), RIPOSTE_WINDOW / SECOND};
            case LUNGE -> new Object[] {fmt(LUNGE_RANGE), pct(LUNGE_MULTIPLIER), fmtSeconds(cooldownTicks(skill, r))};
            case STONE_STANCE -> new Object[] {fmt(stanceArmor(r)), fmt(stanceKnockback(r) * 100)};
            case WHALE_SWEEP -> new Object[] {fmt(SWEEP_RADIUS), pct(sweepMultiplier(r)), fmtSeconds(cooldownTicks(skill, r))};
            case CLOUD_PIERCE -> new Object[] {pierceDamage(r), piercePierce(r)};
            case FAR_SHOT -> new Object[] {fmt(0.06 * r * 100), fmt(FAR_FULL)};
            case BACKSTEP -> new Object[] {fmt(BACKSTEP_ENEMY_RANGE), fmtSeconds(cooldownTicks(skill, r))};
            case HAWK_EYE -> new Object[] {fmt(markBonus(r) * 100), MARK_TICKS / SECOND};
            case SKY_RAIN -> new Object[] {fmt(RAIN_RADIUS), RAIN_PULSES, fmt(rainDamage(r)), fmtSeconds(cooldownTicks(skill, r))};
        };
    }

    // tick を「8」「5.5」のような秒の文字に
    public static String fmtSeconds(int ticks) {
        return fmt(ticks / (double) SECOND);
    }

    static String pct(double v) {
        return String.valueOf(Math.round(v * 100));
    }

    static String fmt(double v) {
        // 0.3 * 100 = 30.000000000000004 のような誤差を「30.0」と出さないよう、先に小数 1 桁に丸める
        v = Math.round(v * 10) / 10.0;
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private SkillRules() {
    }
}
