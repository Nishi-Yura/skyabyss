package jp.revati.skyabyss.rpg;

import net.minecraft.util.Mth;

// 成長の数値。ゲームの状態に触れない計算だけを置く (自己テストで直接たしかめる)。
// 表と理由は docs/design/04_growth_and_skills.md
public final class GrowthRules {
    public static final int MAX_LEVEL = 50;
    // レベルで振り分けられる力はない。基礎は技の木の節で上がる (docs/design/48)。前の版の記録の検めで使う
    public static final int POINTS_PER_LEVEL = 0;
    // ステータス一つの上限 (木の基礎の節の合計がこれを超えることはない)
    public static final int STAT_CAP = 100;
    // 記憶の断片の器の、はじめの大きさ (03 の E9 で広げる)
    public static final int FRAGMENT_CAPACITY = 12;

    // ---------------------------------------------------------------- ステータスの効果
    // 筋力: 近接の攻撃力 ×(1 + 0.6% × 筋力)。武器ごと掛かる
    public static final double STRENGTH_MELEE = 0.006;
    // 敏捷: 移動速度 +0.2% (素の速さに対して)、攻撃速度 +0.2%、飛び道具の威力 +0.4% (1 あたり)
    public static final double AGILITY_MOVE = 0.002;
    public static final double AGILITY_ATTACK_SPEED = 0.002;
    public static final double AGILITY_PROJECTILE = 0.004;
    // 精神: 最大マナ。素の値 20 に、1 あたり +2
    public static final double BASE_MANA = 20;
    public static final double SPIRIT_MANA = 2;
    // 精神: 魔法 (星読みの技) の威力 ×(1 + 0.6% × 精神)。筋力の近接と同じ伸び (docs/design/43)
    public static final double SPIRIT_SPELL = 0.006;
    // 耐久: 2 ごとに最大体力 +0.5 点 (ハート 0.25 個。表示は Hearts)
    public static final double VITALITY_HEALTH_PER_TWO = 0.5;

    // ---------------------------------------------------------------- 経験値の補正
    // 内容の推奨レベル + この差までは満額。超えた 1 レベルごとに 10% 減り、最低 20%
    public static final int FALLOFF_GRACE = 3;
    public static final double FALLOFF_STEP = 0.10;
    public static final double FALLOFF_FLOOR = 0.20;
    // 一緒に倒した人が増えるごとに、全体の量が 25% 増える (一人あたりは減る)
    public static final double PARTY_BONUS = 0.25;
    // バニラの敵は 1 日 (ゲーム内 20 分) にこの量までは満額、そのあとは 20%
    public static final int VANILLA_DAILY = 100;
    public static final double VANILLA_TIRED = 0.20;

    private GrowthRules() {
    }

    public enum Stat {
        STRENGTH("strength"),
        AGILITY("agility"),
        SPIRIT("spirit"),
        VITALITY("vitality");

        public final String id;

        Stat(String id) {
            this.id = id;
        }

        public String nameKey() {
            return "stat.skyabyss." + id;
        }

        public static Stat byId(String id) {
            for (Stat s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    // 転生した回数を踏まえた、次のレベルへ要る経験値 (転生ごとに −6%、下限 −40%。docs/design/31 の B03)
    public static int xpToNext(int level, int rebirths) {
        return jp.revati.skyabyss.rebirth.RebirthRules.scaledXp(xpToNext(level), rebirths);
    }

    // レベル L から L+1 へ上がるのに要る経験値。上限では 0
    public static int xpToNext(int level) {
        if (level >= MAX_LEVEL || level < 1) {
            return 0;
        }
        int k = level - 1;
        return 50 + 25 * k + 2 * k * k;
    }

    // レベル 1 の 0 からそのレベルに着くまでの合計
    public static long totalXpFor(int level) {
        long sum = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            sum += xpToNext(l);
        }
        return sum;
    }

    // そのレベルまでにもらえる力の合計
    public static int pointsForLevel(int level) {
        return POINTS_PER_LEVEL * (Mth.clamp(level, 1, MAX_LEVEL) - 1);
    }

    public static double meleeMultiplier(int strength) {
        return STRENGTH_MELEE * strength;
    }

    public static double moveMultiplier(int agility) {
        return AGILITY_MOVE * agility;
    }

    public static double attackSpeedMultiplier(int agility) {
        return AGILITY_ATTACK_SPEED * agility;
    }

    public static double projectileMultiplier(int agility) {
        return AGILITY_PROJECTILE * agility;
    }

    public static double bonusMana(int spirit) {
        return SPIRIT_MANA * spirit;
    }

    public static double bonusHealth(int vitality) {
        return VITALITY_HEALTH_PER_TWO * (vitality / 2);
    }

    // 内容の推奨レベルより上がりすぎた人への補正
    public static double levelFalloff(int playerLevel, int contentLevel) {
        int over = playerLevel - (contentLevel + FALLOFF_GRACE);
        if (over <= 0) {
            return 1.0;
        }
        return Math.max(FALLOFF_FLOOR, 1.0 - FALLOFF_STEP * over);
    }

    // n 人で倒したときの、一人あたりの割合 (1 人 = 1.0、2 人 = 0.625、4 人 = 0.4375)
    public static double partyShare(int n) {
        if (n <= 1) {
            return 1.0;
        }
        return (1.0 + PARTY_BONUS * (n - 1)) / n;
    }

    // 端数は確率で切り上げる (小さな値が切り捨てで消えないように)。r は 0 以上 1 未満
    public static int roundRandom(double value, double r) {
        int base = (int) Math.floor(value);
        return base + (r < value - base ? 1 : 0);
    }

    // ステータスの振り直しの費用 (共鳴の結晶の数)。技の振り直しと同じ表を使う (初回無料、以後 1〜5)
    public static int statRespecCost(int respecsSoFar) {
        return jp.revati.skyabyss.rpg.skill.SkillRules.respecCost(respecsSoFar);
    }
}
