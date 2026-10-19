package jp.revati.skyabyss.item;

import java.util.List;

// 強さの曲線 (03 の 9章「強さの曲線と調整」、2026-10-17)。ゲームの状態に触れない計算と目安の表だけを置く。
//  - バニラの基準 (剣と防具、エンチャント込みの典型)
//  - 巨神ごとの装備の目安 (推奨レベル、武器の 1 秒あたりの傷 +0 / +10、防具、ボスの一撃と体力)
//  - 心臓のような動かないボスとの「手ごたえ」= 戦いの間に受ける攻撃の数 ÷ 倒れるまでに耐えられる数
// 数は Normal 難易度、一人、近接で振りきる (攻撃の目盛りが満ちてから振る) とする
public final class GearCurve {
    private GearCurve() {
    }

    // ---------------------------------------------------------------- バニラの基準
    public static final double SWORD_SPEED = 1.6;
    public static final double IRON_SWORD = 6;
    public static final double DIAMOND_SWORD = 7;
    public static final double NETHERITE_SWORD = 8;
    // 鋭さ V: +3 (1.21 の式 0.5 × 段 + 0.5)
    public static final double SHARPNESS_V = 3;
    // ダメージ軽減 IV を 4 部位 (EPF 16 → 64%)
    public static final double PROTECTION_IV_SET = 0.36;

    // ダメージ軽減を 4 部位に同じ段で付けたときに受ける割合 (EPF = 段 × 4、1 につき 4%。上限 80%)
    public static double protectionSet(int level) {
        return 1 - Math.min(0.8, 0.04 * 4 * level);
    }

    // 耐性 I (岩鯨の甲殻一式。03 の 10.1節)。防具のあとの傷に掛かる
    public static final double RESISTANCE_I = 0.8;

    public static double diamondSwordDps() {
        return DIAMOND_SWORD * SWORD_SPEED;
    }

    public static double netheriteSharpVDps() {
        return (NETHERITE_SWORD + SHARPNESS_V) * SWORD_SPEED;
    }

    // ---------------------------------------------------------------- 巨神ごとの目安
    // dps0 / dps10: 一体への 1 秒あたりの傷 (筋力・相手の防具の前、固有効果の上乗せ込み、出血は除く)。段ごとに約 1.15 倍、
    // 次の巨神の +0 は前の巨神の +5 ほど。armor / toughness: 4 部位の合計 (+0)。setMultiplier: 一式の効果で受ける傷に掛かる値
    // bossHit / bossHp: 推奨レベル・その段の防具 +0 で、心臓の一撃に 7 回ほど耐え、+0 の武器で 75 秒ほどかかる値
    public record Stage(String id, int level, double dps0, double dps10, double armor, double toughness, double setMultiplier,
            double bossHit, int bossHp) {
    }

    public static final List<Stage> STAGES = List.of(
            new Stage("stone_whale", 12, 11.5, 15.0, 20, 8, 0.8, 12, 450),
            new Stage("tide_shell", 18, 13.2, 17.2, 20, 12, 0.8, 13.5, 540),
            new Stage("thunder_wing", 24, 15.2, 19.8, 21, 14, 0.8, 15.5, 650),
            new Stage("ember", 30, 17.5, 22.8, 22, 16, 0.8, 18.5, 790));

    // ---------------------------------------------------------------- 旅装 (21 の 1.2。章ごとの橋渡し。アンコモン、特性なし、炉で鍛えない)
    // armor / toughness: 4 部位の合計。level: 必要 Lv。whetstone: 砥石の攻撃の上乗せ。防御は、その段の防具 +0 の 85〜92% (巨神の装備は +0 でも旅装を上回る。
    // 周回の前の橋にとどめる)、強靭は +0 を超えない。一章だけ実装済み (guide/TravelGear)。二〜四章は同じ表で足す
    public record Travel(String id, int chapter, String stage, int level, int armor, int toughness, double whetstone) {
    }

    public static final List<Travel> TRAVEL = List.of(
            new Travel("whale_road", 1, "stone_whale", 7, 17, 5, 1.5),
            new Travel("tide_crossing", 2, "tide_shell", 13, 18, 9, 1.8),
            new Travel("peak_crossing", 3, "thunder_wing", 19, 19, 11, 2.2),
            new Travel("ash_walking", 4, "ember", 25, 20, 13, 2.6));

    // ---------------------------------------------------------------- 計算
    // バニラの防具の式 (CombatRules): 受ける割合
    public static double armorTaken(double damage, double armor, double toughness) {
        double f = 2 + toughness / 4;
        double eff = Math.max(armor * 0.2, Math.min(20, armor - damage / f));
        return 1 - eff / 25;
    }

    // 想定のステータス: 力の半分を筋力、4 分の 1 を耐久 (04 の 4章。どちらも上限 50)
    public static double strengthMultiplier(int level) {
        int points = 3 * (level - 1);
        return 1 + 0.006 * Math.min(50, points / 2);
    }

    public static double maxHealth(int level) {
        int points = 3 * (level - 1);
        return 20 + 0.5 * (Math.min(50, points / 4) / 2);
    }

    // 一体への 1 秒あたりの傷 (筋力・相手の防具の前)
    public static double rawDps(double attack, double speed, double procMultiplier) {
        return attack * speed * procMultiplier;
    }

    // 相手の防具を通した 1 秒あたりの傷。bleed は 1 秒あたりの出血 (防具を通らない)
    public static double dpsAgainst(double attack, double speed, double procMultiplier, double bleed, double strength,
            double targetArmor, double targetToughness) {
        double hit = attack * strength;
        return hit * armorTaken(hit, targetArmor, targetToughness) * speed * procMultiplier + bleed;
    }

    // 手ごたえ: 戦いの間に受ける攻撃の数 ÷ 倒れるまでに耐えられる数。1 を下回ると、避けずに殴り合っても勝てる (作業)。
    // uptime: 殴っていられる時間の割合、interval: ボスの技の間隔 (秒)
    public static double load(double dps, double bossHp, double uptime, double interval, double health, double hitTaken) {
        double seconds = bossHp / dps / uptime;
        double attacks = seconds / interval;
        double survive = health / hitTaken;
        return attacks / survive;
    }

    // ---------------------------------------------------------------- 岩鯨の装備の固有効果 (一体への上乗せ)
    // 番兵長の鉾: 3 度に 1 度 (+5 から 2 度に 1 度) 攻撃力の 60% の噴出が、斬った相手にも当たる
    public static double pikeProc(int enhance) {
        return 1 + 0.6 / (enhance >= ModGear.AWAKEN_LEVEL ? 2 : 3);
    }

    // 水路の顎牙: 出血 (1 秒に 1、+5 から II で 2)。振りの無敵時間に半分ほど食われるとみる
    public static double fangBleed(int enhance) {
        return enhance >= ModGear.AWAKEN_LEVEL ? 1.0 : 0.5;
    }
}
