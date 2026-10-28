package jp.revati.skyabyss.rpg.skill;

import net.minecraft.util.Mth;

// 技とマナの数値。ゲームの状態に触れない計算だけを置く (自己テストで直接たしかめる)。
// 表と理由は docs/design/04_growth_and_skills.md (10 章) と 27_builds_and_classes.md
public final class SkillRules {
    // 枠の数: はじめは 4、2 つ目の天職を迎えると 5 (記録の配列は最大の 5)
    public static final int BASE_SLOTS = 4;
    public static final int SLOTS = 5;
    // 1 秒 = 20 tick
    public static final int SECOND = 20;
    // ---------------------------------------------------------------- 自由度 (docs/design/31 の A 案)
    // 木のセット (A04): 同じ木に点を 2・4・6 使うたびの小さな追加
    public static final int[] SET_AT = {2, 4, 6};
    // タグ (A03): 数えるのは 10 まで。1 つにつき、そのタグの技の力 +1%。4 と 7 で小さな足し (SkillBuild.tagThreshold)
    public static final int TAG_COUNT_CAP = 10;
    public static final double TAG_POWER_PER = 0.01;
    public static final int TAG_STEP1 = 4;
    public static final int TAG_STEP2 = 7;
    // 条件の特性 (A15): そのタグが 3 つ以上あるとき。5 以上で大きいほう
    public static final int COND_MIN_TAGS = 3;
    public static final int COND_BIG_TAGS = 5;
    public static final int COND_DODGE_TICKS = 3 * SECOND;
    public static final int COND_PERFECT_TICKS = 4 * SECOND;
    // 無傷のあとにこの間 (5 秒) 傷を受けていなければ、予告をよけたのは「完璧」
    public static final int PERFECT_CLEAN_TICKS = 5 * SECOND;
    // 「低 HP」は 3❤ (6 点) 以下。回復や無敵は付けない
    public static final float COND_LOW_HP = 6f;
    public static final double COND_FULL_MANA = 0.98;
    // 熟練 (A13): 使う +1、当てる +1、予告をよけた直後 (4 秒) に当てる +3。1〜5 の境
    public static final int[] MASTERY_AT = {15, 45, 100, 180, 300};
    public static final int MASTERY_RUNE_LEVEL = 3;
    public static final int MASTERY_VISUAL_LEVEL = 5;
    public static final int MASTERY_DODGE_HIT = 3;
    // 熟練の刻印 (刻印の 4 つ目): 力 +10%、待ち −8%
    public static final double MASTERY_RUNE_POWER = 0.10;
    public static final double MASTERY_RUNE_COOLDOWN = -0.08;
    // キーストーン (A20): 2 つまで。その木に点 4 つ以上
    public static final int KEYSTONE_MAX = 2;
    public static final int KEYSTONE_TREE_POINTS = 4;
    // キーストーンの数字 (得 / 代価)。2 つ組み合わせても「与える傷」は +26% まで (SkillBuild)
    public static final double KEY_BULWARK_ARMOR = 5;
    public static final double KEY_BULWARK_KNOCKBACK = 0.35;
    public static final double KEY_BULWARK_MOVE = -0.10;
    public static final double KEY_GLASS_DAMAGE = 0.14;
    public static final double KEY_GLASS_ARMOR = -4;
    public static final double KEY_TIDE_MANA = 0.40;
    public static final double KEY_TIDE_REGEN = -0.30;
    public static final double KEY_ASH_DAMAGE = 0.12;
    public static final double KEY_ASH_MANA_COST = 0.30;
    public static final double KEY_STRING_PROJECTILE = 0.22;
    public static final double KEY_STRING_COOLDOWN = 0.15;
    public static final double KEY_DANCE_MOVE = 0.12;
    public static final double KEY_DANCE_COOLDOWN = -0.10;
    public static final double KEY_DANCE_DAMAGE = -0.08;
    // 外から足せる上限 (SkillModifier を含む合計)
    public static final double POWER_CAP = 1.0;
    public static final double COOLDOWN_MIN = -0.40;
    public static final double COOLDOWN_MAX = 0.50;
    public static final double MANA_COST_CAP = 0.50;

    public static int masteryLevel(int xp) {
        int lv = 0;
        for (int at : MASTERY_AT) {
            if (xp >= at) {
                lv++;
            }
        }
        return lv;
    }

    // どの技を使ったあとも、次の技まで 0.5 秒あける (連打で重ねがけしない・手触りを重くする)
    public static final int GLOBAL_COOLDOWN = 10;

    // ---------------------------------------------------------------- 技の点
    // Lv 10 までは (L + 1) / 2 (Lv 1 で 1、Lv 10 で 5)、そのあとは 1 レベルごとに 1。Lv 50 で 45
    // 1 レベルにつき 1 点 (Lv 1 で 1、Lv 50 で 50。docs/design/48 の 2.1)。職ごとに別に数える
    public static int pointsForLevel(int level) {
        return Mth.clamp(level, 1, 50);
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

    // ---------------------------------------------------------------- 振り直し・刻印の付け替え・型の保存
    // 全振り直しは共鳴の炉のそばで、いつでも結晶 1 (A05)。目利きがあれば無料。Lv 15 になるまでは炉も結晶も要らない
    public static final int RESPEC_COST = 1;
    // ステータスの振り直し (GrowthRules) の費用の頭打ち
    public static final int RESPEC_MAX_COST = 5;
    // 共鳴の炉からこの距離 (ブロック) 以内で振り直せる
    public static final int RESPEC_FORGE_RANGE = 6;
    // このレベルになるまで、全振り直しは無料 (炉も結晶も要らない。回数も数えない)
    public static final int FREE_RESPEC_LEVEL = 15;
    // 刻印の付け替え (別の刻印に変える) の費用。初回は無料
    public static final int RUNE_SWAP_COST = 1;
    // 型の保存の数 (A05 で 2 から 4)
    // 型の保存は職ごとに 2 つ (docs/design/43)。箱は職の順に 2 つずつ並ぶ
    public static final int LOADOUTS_PER_JOB = 2;
    public static final int LOADOUTS = LOADOUTS_PER_JOB * 6;
    // 刻印の付け替え・型の切り替えは、この間 (30 秒) 傷を受けも与えもしていないときだけ
    public static final int QUIET_TICKS = 30 * SECOND;

    // ステータスの振り直しの費用 (回数が増えると上がる。初回は無料。GrowthRules が使う)。技の振り直しは下の 3 つの引数の版
    public static int respecCost(int respecsSoFar) {
        return Math.min(Math.max(0, respecsSoFar), RESPEC_MAX_COST);
    }

    // レベルと「目利き」を見た全振り直しの費用 (結晶の数)。Lv 15 になるまでと、目利きがあるときは 0
    public static int respecCost(int respecsSoFar, int level) {
        if (level < FREE_RESPEC_LEVEL) {
            return 0;
        }
        return RESPEC_COST;
    }

    // ---------------------------------------------------------------- 消費マナとクールダウン
    public static int manaCost(Skill skill, int rank) {
        return switch (skill) {
            case TITAN_CLEAVE -> 10;
            case ROCK_CALL -> 12;
            case WHALE_SWEEP -> 16;
            case LUNGE -> 6;
            case BACKSTEP -> 4;
            case WIND_REND -> 8;
            case WARD_SONG -> 12;
            case EBB_TIDE -> 12;
            case CYCLE_SONG -> 16;
            case TEMPER -> 12;
            case EMBER_HAMMER -> 14;
            case ASH_SCATTER -> 10;
            case CLOUD_PIERCE -> 10;
            case BONE_TRAP -> 6;
            case SKY_RAIN -> 16;
            case DEEP_PULL -> 12;
            case DEEP_ECHO -> 14;
            case DEEP_MAW -> 18;
            case ECHO_STRIKE -> 10;
            case GRAVE_BELL -> 12;
            case FINAL_LIGHT -> 18;
            // 職の新しい技 (docs/design/43。星読みの木と、鍛冶師・暗殺者・呪術師の 4 つ目)
            case THUNDER_BOLT -> 8;
            case EMBER_ORB -> 14;
            case FROST_TIDE -> 12;
            case BLINK -> 10;
            case FORGE_STOMP -> 12;
            case MIST_VEIL -> 10;
            case CURSE_MARK -> 8;
            case LAMP_CHARM -> 14;
            case SHIELD_RUSH -> 6;
            case ARCHER_LEAP -> 4;
            // 木 v2 で足した技 (docs/design/48)
            case SHIELD_BASH -> 8;
            case BONE_SWING -> 14;
            case SPIN_CUT -> 10;
            case TWIN_FANG -> 7;
            case SPARK_BURST -> 10;
            case SNARE_SHOT -> 8;
            case STARDUST -> 12;
            default -> 0;
        };
    }

    // tick。段が上がると短くなる技もある (刻印の分は別。cooldownTicks(skill, rank, rune))
    public static int cooldownTicks(Skill skill, int rank) {
        int r = Math.max(1, rank);
        return switch (skill) {
            case TITAN_CLEAVE -> 8 * SECOND;
            case ROCK_CALL -> 14 * SECOND;
            case WHALE_SWEEP -> (r >= 2 ? 11 : 14) * SECOND;
            case LUNGE -> (r >= 2 ? 4 : 6) * SECOND;
            case BACKSTEP -> (r >= 2 ? 3 : 5) * SECOND;
            case WIND_REND -> (r >= 2 ? 6 : 8) * SECOND;
            case WARD_SONG -> 15 * SECOND;
            case EBB_TIDE -> 10 * SECOND;
            case CYCLE_SONG -> 16 * SECOND;
            case TEMPER -> 13 * SECOND;
            case EMBER_HAMMER -> 10 * SECOND;
            case ASH_SCATTER -> 12 * SECOND;
            case CLOUD_PIERCE -> 6 * SECOND;
            case BONE_TRAP -> 9 * SECOND;
            case SKY_RAIN -> (r >= 2 ? 11 : 15) * SECOND;
            case DEEP_PULL -> 14 * SECOND;
            case DEEP_ECHO -> 20 * SECOND;
            case DEEP_MAW -> (r >= 2 ? 14 : 17) * SECOND;
            case ECHO_STRIKE -> 12 * SECOND;
            case GRAVE_BELL -> 14 * SECOND;
            case FINAL_LIGHT -> (r >= 2 ? 18 : 22) * SECOND;
            case THUNDER_BOLT -> 50;
            case EMBER_ORB -> 8 * SECOND;
            case FROST_TIDE -> 10 * SECOND;
            case BLINK -> (r >= 2 ? 7 : 9) * SECOND;
            case FORGE_STOMP -> 10 * SECOND;
            case MIST_VEIL -> 14 * SECOND;
            case CURSE_MARK -> 9 * SECOND;
            case LAMP_CHARM -> 18 * SECOND;
            case SHIELD_RUSH -> (r >= 2 ? 4 : 6) * SECOND;
            case ARCHER_LEAP -> (r >= 2 ? 3 : 5) * SECOND;
            case SHIELD_BASH -> (r >= 2 ? 6 : 7) * SECOND;
            case BONE_SWING -> 12 * SECOND;
            case SPIN_CUT -> (r >= 2 ? 8 : 9) * SECOND;
            case TWIN_FANG -> (r >= 2 ? 5 : 6) * SECOND;
            case SPARK_BURST -> 9 * SECOND;
            case SNARE_SHOT -> 8 * SECOND;
            case STARDUST -> 9 * SECOND;
            default -> 0;
        };
    }

    // 刻印を入れたときの待ち。長い声は +4 秒
    public static int cooldownTicks(Skill skill, int rank, int rune) {
        int base = cooldownTicks(skill, rank);
        if (skill == Skill.ROCK_CALL && rune == 1) {
            base += LONG_VOICE_COOLDOWN;
        }
        if (skill == Skill.LUNGE && rune == 2) {
            // 連ねは当たったときだけ待ちが半分になる (SkillActions)。ここは基準のまま
            return base;
        }
        return base;
    }

    // ---------------------------------------------------------------- 型のおまけ
    // 技ごとの噛み合う型 (なければ null)。おまけの中身は技ごと (lang の skill.skyabyss.<id>.type に一行)
    public static WeaponType affinity(Skill skill) {
        return switch (skill) {
            case TITAN_CLEAVE, TEMPER, EMBER_HAMMER -> WeaponType.HEAVY;
            case WHALE_SWEEP, LUNGE, EBB_TIDE -> WeaponType.LONG;
            case NIMBLE, FORESIGHT, WIND_REND -> WeaponType.FAST;
            case CLOUD_PIERCE, HAWK_EYE -> WeaponType.RANGED;
            case ROCK_CALL, STONE_STANCE -> WeaponType.GUARD;
            default -> null;
        };
    }

    // 巨神斬りの「重で +10%」(威力)
    public static final double CLEAVE_HEAVY_BONUS = 0.10;

    // ---------------------------------------------------------------- 木 v2 で足した技 (docs/design/48。TreeActions)
    // 盾殴り: 前 3 ブロック・80 度の扇、3 体まで。押し出して 2 秒の鈍足
    public static final double BASH_RANGE = 3.0;
    public static final double BASH_HALF_ANGLE = 40;
    public static final int BASH_MAX_TARGETS = 3;
    public static final double BASH_SLOW = 0.6;
    public static final int BASH_SLOW_TICKS = 2 * SECOND;
    public static final double BASH_PUSH = 0.8;

    public static double bashMultiplier(int rank) {
        return rank >= 2 ? 1.0 : 0.9;
    }

    // 鯨骨の振り: 見ている 4 ブロック以内の 1 体に ×2.2 (段 2 で ×2.5)。大きな相手には ×1.3
    public static final double SWING_RANGE = 4.0;
    public static final double SWING_LARGE = 1.3;

    public static double swingMultiplier(int rank) {
        return rank >= 2 ? 2.5 : 2.2;
    }

    // 回し斬り: まわり 2.5 ブロックを ×0.8 で薙ぎ、前へ 3 ブロックほど進む
    public static final double SPIN_RADIUS = 2.5;
    public static final double SPIN_PUSH = 0.4;
    public static final double SPIN_DASH = 0.9;
    public static final double SPIN_LIFT = 0.15;
    public static final int SPIN_DASH_BLOCKS = 3;

    public static double spinMultiplier(int rank) {
        return rank >= 2 ? 0.9 : 0.8;
    }

    // 二連牙: 見ている 3.5 ブロック以内の敵に ×0.7 を二度。背後なら ×1.5
    public static final double FANG_RANGE = 3.5;
    public static final double FANG_MULTIPLIER = 0.7;
    public static final double FANG_BACK = 1.5;
    public static final int FANG_GAP = 4;

    // 火花散らし: まわり 3 ブロックに ×0.8 と 2 秒の炎
    public static final double SPARK_RADIUS = 3.0;
    public static final int SPARK_BURN = 2 * SECOND;

    public static double sparkMultiplier(int rank) {
        return rank >= 2 ? 0.9 : 0.8;
    }

    // 足止めの矢: 見ている 12 ブロック以内の 1 体に矢の傷 (雲穿ちと同じ) と 50% の鈍足 3 秒 (段 2 で 4 秒)
    public static final double SNARE_RANGE = 12;
    public static final double SNARE_MULTIPLIER = 1.0;
    public static final double SNARE_SLOW = 0.5;

    public static int snareTicks(int rank) {
        return (rank >= 2 ? 4 : 3) * SECOND;
    }

    // 星屑: まわり 4 ブロックに魔法の傷 ×0.9 (段 2 で ×1.0)
    public static final double DUST_RADIUS = 4.0;
    public static final double DUST_PUSH = 0.4;

    public static double dustMultiplier(int rank) {
        return rank >= 2 ? 1.0 : 0.9;
    }

    // ---------------------------------------------------------------- 力 (傷・量に掛ける倍率) の部品
    // 潮歌いの要・共鳴の声: 近くの仲間 1 人につき +4% (最大 5 人)
    public static final double CHORUS_PER_ALLY = 0.04;
    public static final int CHORUS_MAX_ALLIES = 5;
    public static final double ALLY_RANGE = 8;

    // 風読みの要・孤風: 16 ブロック以内に他のプレイヤーがいないあいだ、攻撃力 +12%・移動 +6%
    public static final double LONE_RANGE = 16;
    public static final double LONE_ATTACK = 0.12;
    public static final double LONE_MOVE = 0.06;

    // 灰の鍛え人の要・鍛え人の目: 手の武器の強化段階 1 つにつき技の傷 +1.5%
    public static final double SMITH_PER_LEVEL = 0.015;

    // 骨の射手の要・静かな弦: 3 秒動かずに引いた矢の威力 +25%
    public static final int STILL_TICKS = 3 * SECOND;
    public static final double STILL_BONUS = 0.25;

    // 岩守りの要・守り石の約束: 8 ブロック以内の仲間が受ける傷の 10% を自分が受ける (1 回 1❤ = 2 点まで)
    public static final double OATH_SHARE = 0.10;
    public static final float OATH_CAP = 2f;

    // ---------------------------------------------------------------- 岩守り
    // 巨神斬り: 前方の扇 (半径 4、左右 60 度) に、攻撃力 × (0.9 + 0.3 × 段)。大きな相手には さらに × 1.5
    public static final double CLEAVE_RANGE = 4.0;
    public static final double CLEAVE_HALF_ANGLE = 60;
    public static final int CLEAVE_MAX_TARGETS = 5;
    public static final double CLEAVE_LARGE_BONUS = 1.5;
    // 刻印: 広刃 (180 度、倍率 −10%)、深刃 (30 度、1 体に ×1.4)、地走り (前方 8 ブロック、幅 1.5 の衝撃波)
    public static final double WIDE_BLADE_HALF_ANGLE = 90;
    public static final double WIDE_BLADE_SCALE = 0.9;
    public static final double DEEP_BLADE_HALF_ANGLE = 15;
    public static final double DEEP_BLADE_SCALE = 1.4;
    public static final double GROUND_RUN_LENGTH = 8;
    public static final double GROUND_RUN_WIDTH = 1.5;

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

    // 岩呼び: 周り 8 ブロックの敵の狙いを 4 / 5 秒、自分へ引く。引いた敵の攻撃のノックバックは 0.5 倍。守で +1 秒
    public static final double ROCK_CALL_RADIUS = 8;
    public static final double ROCK_CALL_KNOCKBACK = 0.5;
    public static final int LONG_VOICE_COOLDOWN = 4 * SECOND;
    public static final double LONG_VOICE_RADIUS = 12;
    // 重い声: 狙われている間、受ける傷 −10%。岩の返り: 引いた敵の攻撃を受けるたび次の近接 +10%
    public static final double HEAVY_VOICE_REDUCTION = 0.10;
    public static final double ROCK_RETURN_BONUS = 0.10;

    public static int rockCallTicks(int rank) {
        return (rank >= 2 ? 5 : 4) * SECOND;
    }

    // 岩の構え: 段ごとに防具 +1、ノックバック耐性 +0.1。守で +1
    public static double stanceArmor(int rank) {
        return rank;
    }

    public static double stanceKnockback(int rank) {
        return 0.1 * rank;
    }

    // 鯨尾薙ぎ: 周り (半径 3.5) の敵すべてに 攻撃力 × (0.9 / 1.2)。外へ軽く押し出す。長で半径 +0.5
    public static final double SWEEP_RADIUS = 3.5;
    public static final double SWEEP_PUSH = 0.6;
    // 刻印: 押し潮 (押し出し 2 倍、傷 −20%)、挟み (当てた敵を 0.5 秒足もとへ)、二度薙ぎ (半分の力で 2 回)
    public static final double PUSH_TIDE_SCALE = 0.8;
    public static final int DOUBLE_SWEEP_GAP = 8;

    public static double sweepMultiplier(int rank) {
        return rank >= 2 ? 1.2 : 0.9;
    }

    // ---------------------------------------------------------------- 風読み
    // 踏み込み: 8 ブロック以内の敵の手前まで一気に詰め、攻撃力 × 0.8 で斬る。足場のない所へは踏み出さない。長で +1
    public static final double LUNGE_RANGE = 8.0;
    public static final double LUNGE_STOP = 1.4;
    public static final double LUNGE_MULTIPLIER = 1.3;
    // 突き通し: 通り道の全員に ×0.6
    public static final double PIERCE_THROUGH_MULTIPLIER = 0.6;
    // 逸品の書き換え (docs/design/27 の 4.2)
    // 突き通し・骨: 通り道の全員に ×1.0、3 回に 1 度、着いた足もとから骨 (半径 2.5、×0.5)
    public static final double BONE_PIERCE_MULTIPLIER = 1.0;
    public static final int BONE_EVERY = 3;
    public static final double BONE_RADIUS = 2.5;
    public static final double BONE_SCALE = 0.5;
    // 噛みつき: 三連。背後の上乗せは出血 (3 秒) に変わる
    public static final int BITE_HITS = 3;
    // 挟み切り: 1 秒 (大きな相手は 0.5 秒) 動けなくする
    public static final int SNIP_TICKS = SECOND;
    public static final int SNIP_LARGE_TICKS = SECOND / 2;
    // 銛打ち: 結んで 3 秒。結ばれた敵が動くと、近く (5 ブロック) の敵が引かれる
    public static final int HARPOON_TICKS = 3 * SECOND;
    public static final double HARPOON_LINK_RADIUS = 5;
    // 雷の輪: 跳んだ元の場所と着地点を結び、間の敵に ×0.8
    public static final double THUNDER_RING_MULTIPLIER = 0.8;
    public static final int THUNDER_RING_DELAY = 14;
    public static final double THUNDER_RING_WIDTH = 1.6;
    // 鉤寄せ: 狙いを引いた敵を、前の 1 か所へ寄せる
    public static final double HOOK_POINT_AHEAD = 2.5;
    // 火床: 熾火 8 秒、中の敵の攻撃力 −10%
    public static final double FIRE_BED_WEAKEN = 0.10;
    // 火移し: 焼き入れを仲間 2 人まで同時に
    public static final int FIRE_PASS_ALLIES = 2;

    // 軽業: 地上での移動 +2 / 4 / 6%、落下の傷が −1 / 2 / 3 ブロック分。速で +2%
    public static double nimbleMove(int rank) {
        return 0.02 * rank;
    }

    public static double nimbleFall(int rank) {
        return rank;
    }

    public static final double NIMBLE_FAST_BONUS = 0.02;

    // 跳び退き: 地に足が着いていて、10 ブロック以内に敵がいるときだけ。後ろへ跳ぶ (水平 0.85、上 0.42)。
    // 跳ぶ最初の tick は地面の摩擦が掛かるので、届くのはおよそ 4 ブロック (走って跳ぶのと同じくらい。アスレの近道にならない)
    public static final double BACKSTEP_ENEMY_RANGE = 10;
    public static final double BACKSTEP_PUSH = 0.85;
    public static final double BACKSTEP_LIFT = 0.42;
    // 刻印: 射返し (直後 3 秒の飛び道具 ×1.2)、長跳び (およそ 6 ブロック)、風の置き土産 (元の場所に 2 秒の渦 = 鈍足)
    public static final double LONG_LEAP_PUSH = 1.15;
    public static final double RETURN_FIRE_BONUS = 0.20;
    public static final int RETURN_FIRE_TICKS = 3 * SECOND;
    public static final int WHIRL_TICKS = 2 * SECOND;
    public static final double WHIRL_RADIUS = 2.5;

    // 見切り: 予告 (円・線) が自分に当たらず終わった直後 3 秒、攻撃速度 +5 / 10 / 15%。速で +5%
    public static final int FORESIGHT_TICKS = 3 * SECOND;
    public static final double FORESIGHT_FAST_BONUS = 0.05;

    public static double foresightBonus(int rank) {
        return 0.05 * rank;
    }

    // 風裂き: 素早い二連。1 撃は正面なら ×0.8 / 0.9、背後なら その 1.5 倍 (×1.2 / 1.35)。速で ×0.1
    public static final double REND_RANGE = 4.0;
    public static final int REND_GAP = 4;
    public static final double REND_FAST_BONUS = 0.1;

    public static final double REND_BACK_FACTOR = 1.5;

    public static double rendBack(int rank) {
        return rendFront(rank) * REND_BACK_FACTOR;
    }

    public static double rendFront(int rank) {
        return rank >= 2 ? 1.4 : 1.2;
    }

    // 刻印: 背取り (背後は正面の 1.8 倍、正面は 0.75 倍)、出血 (3 秒の継続傷)、連れ風 (近くの仲間の移動 +10%、4 秒)
    public static final double BACK_TAKE_BACK_FACTOR = 1.8;
    public static final double BACK_TAKE_FRONT_FACTOR = 0.75;
    // 出血: 1 秒に 攻撃力 × 0.2 を 3 秒
    public static final double BLEED_PER_SECOND = 0.2;
    public static final int BLEED_TICKS = 3 * SECOND;
    public static final double COMPANION_WIND_MOVE = 0.10;
    public static final int COMPANION_WIND_TICKS = 4 * SECOND;

    // ---------------------------------------------------------------- 潮歌い
    // 守りの唄: 8 ブロックの仲間 (自分を含む) に 8 秒の吸収 2 / 3 / 4 点 (黄ハート 1〜2❤)。重ねがけで増えない。上限 4
    public static final double WARD_SONG_RADIUS = 8;
    public static final int WARD_SONG_TICKS = 10 * SECOND;
    public static final float ABSORB_CAP = 6f;
    // 刻印: 厚い歌 (吸収 +1、4 秒)、広い歌 (半径 12、吸収 −1)、巡る歌 (吸収が切れたとき近くの 1 人へ半分)
    public static final int THICK_SONG_TICKS = 4 * SECOND;
    public static final double WIDE_SONG_RADIUS = 12;

    public static float wardAbsorb(int rank) {
        return 2 + rank;
    }

    // 凪の心得: 自分と近くの仲間のマナ回復 +10 / 20 / 30% (高いほう。重ならない)
    public static double calmHeartBonus(int rank) {
        return 0.10 * rank;
    }

    // 引き潮: 前方 6 / 7 ブロックの敵を足もとへ引き寄せ、鈍足 II を 2 / 3 秒。大きな相手は引かず鈍足だけ。長で +1
    public static final double EBB_ANGLE = 40;
    public static final double WHIRL_SLOW = 0.45;

    public static double ebbRange(int rank) {
        return rank >= 2 ? 9 : 8;
    }

    public static int ebbSlowTicks(int rank) {
        return (rank >= 2 ? 4 : 3) * SECOND;
    }

    // 波紋: 自分の技が当たった敵の移動 −10 / 15 / 20% (2 秒)。大きな相手には半分
    public static final int RIPPLE_TICKS = 2 * SECOND;

    public static double rippleSlow(int rank) {
        return switch (Mth.clamp(rank, 0, 3)) {
            case 1 -> 0.10;
            case 2 -> 0.15;
            case 3 -> 0.20;
            default -> 0;
        };
    }

    // 巡りの唄: 8 ブロックの仲間のマナ回復を 10 / 12 秒、+50% (自分は +25%)
    public static final double CYCLE_SONG_RADIUS = 8;
    public static final double CYCLE_SONG_ALLY = 0.50;
    public static final double CYCLE_SONG_SELF = 0.25;

    public static int cycleSongTicks(int rank) {
        return (rank >= 2 ? 15 : 12) * SECOND;
    }

    // ---------------------------------------------------------------- 灰の鍛え人
    // 焼き入れ: 手の武器を 10 秒「熾」にする。攻撃に炎の傷 (攻撃力 × 0.15 / 0.20 / 0.25。重で +0.05)、敵を 2 秒燃やす。
    // 自分か、見ている仲間 1 人へ
    public static final int TEMPER_TICKS = 14 * SECOND;
    public static final int TEMPER_BURN_TICKS = 2 * SECOND;
    public static final double TEMPER_RANGE = 6;
    public static final double TEMPER_HEAVY_BONUS = 0.05;

    public static double temperMultiplier(int rank) {
        return 0.20 + 0.08 * rank;
    }

    // 熾の心得: 火の傷を受ける量 −15 / 25 / 35%
    public static double emberHeartReduction(int rank) {
        return switch (Mth.clamp(rank, 0, 3)) {
            case 1 -> 0.15;
            case 2 -> 0.25;
            case 3 -> 0.35;
            default -> 0;
        };
    }

    // 熾火の槌: 叩きつけ (前方 2 ブロックを中心に半径 3) に ×1.4 / 1.6、地に 4 秒の熾火 (敵だけ燃やす)。重で半径 +0.5
    public static final double HAMMER_RADIUS = 3.5;
    public static final double HAMMER_REACH = 2.0;
    public static final int EMBER_TICKS = 4 * SECOND;
    public static final double HAMMER_HEAVY_RADIUS = 0.5;
    // 刻印: 長い火 (熾火 8 秒、傷 −30%)、二度叩き (2 回、×0.6 ずつ)、火柱 (半径 1.5、中心の敵に ×1.8)
    public static final int LONG_FIRE_TICKS = 8 * SECOND;
    public static final double LONG_FIRE_SCALE = 0.7;
    public static final double DOUBLE_STRIKE_SCALE = 0.6;
    public static final int DOUBLE_STRIKE_GAP = 10;
    public static final double FIRE_PILLAR_RADIUS = 1.5;
    public static final double FIRE_PILLAR_CENTER = 1.8;

    public static double hammerMultiplier(int rank) {
        return rank >= 2 ? 2.2 : 1.9;
    }

    // 炉の記憶: 技を使うたびに熾が 1 つ溜まる (段 = 最大の数)。次の近接の攻撃 +8% を 1 つにつき (使うと全部消える)
    public static final double FORGE_MEMORY_BONUS = 0.08;

    // 灰散らし: 周り 4 ブロックの敵に 3 / 4 秒の「灰かぶり」(攻撃力 −10%、飛び道具の速さ −20%)
    public static final double ASH_SCATTER_RADIUS = 5;
    public static final double ASH_ATTACK = 0.15;
    public static final double ASH_PROJECTILE = 0.30;

    public static int ashScatterTicks(int rank) {
        return (rank >= 2 ? 6 : 5) * SECOND;
    }

    // ---------------------------------------------------------------- 骨の射手
    // 雲穿ち: 弓を構えずに放つ、まっすぐ飛ぶ貫く矢 (矢を 1 本使う)。速さ 3.6、基礎威力 2.2 / 2.6 / 3.0、貫く数 2 / 3 / 4。遠で +1 体
    public static final float PIERCE_SPEED = 3.6f;
    public static final int PIERCE_LIFETIME = 30;
    // 刻印: 細矢 (貫通 +2、威力 −10%)、重矢 (貫通 −2 (最低 0)、威力 ×1.3、速さ −30%)、稲光 (貫いた敵の間に小さな雷 ×0.3)
    public static final int THIN_ARROW_PIERCE = 2;
    public static final double THIN_ARROW_SCALE = 0.9;
    public static final double HEAVY_ARROW_SCALE = 1.3;
    public static final double HEAVY_ARROW_SPEED = 0.7;
    public static final double LIGHTNING_SCALE = 0.3;
    public static final double LIGHTNING_RANGE = 4;

    public static double pierceBaseDamage(int rank) {
        return 2.4 + 0.6 * rank;
    }

    public static int piercePierce(int rank) {
        return 2 + rank;
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

    // 骨の罠: 足もとに骨の罠 (最大 2)。踏んだ敵を 2 / 3 秒縛り、傷 ×0.8 / 1.0。大きな相手は縛られず ×1.2 だけ
    public static final int TRAP_MAX = 3;
    public static final int TRAP_LIFETIME = 45 * SECOND;
    public static final double TRAP_TRIGGER = 2.0;
    public static final double TRAP_LARGE_MULTIPLIER = 1.5;

    public static int trapBindTicks(int rank) {
        return (rank >= 2 ? 4 : 3) * SECOND;
    }

    public static double trapMultiplier(int rank) {
        return rank >= 2 ? 1.6 : 1.3;
    }

    // 鷹の目: 自分の矢が当たった敵に 5 秒の印。印の敵は、誰の攻撃でも 5% / 8% / 12% 多く傷つく (重ならない。高いほうを使う)。遠で +2%
    public static final int MARK_TICKS = 5 * SECOND;
    public static final double HAWK_RANGED_BONUS = 0.02;

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
    public static final double RAIN_RADIUS = 4;
    public static final int RAIN_PULSES = 8;
    public static final int RAIN_INTERVAL = 10;

    public static double rainDamage(int rank) {
        return rank >= 2 ? 4.5 : 3.0;
    }

    // ---------------------------------------------------------------- 職の新しい技 (docs/design/43)
    // 魔法の傷 = 手の武器の攻撃力 × 倍率 × (1 + 精神 × GrowthRules.SPIRIT_SPELL)。星読みの技 (JobActions.spell)
    // 雷の矢: 見ている向きへ 24 ブロックの稲妻。最初の敵に当たる。3 段で近くの敵へ 50% で跳ねる
    public static final double BOLT_RANGE = 24;
    public static final double BOLT_CHAIN = 0.5;
    public static final double BOLT_CHAIN_RANGE = 5;

    public static double boltMultiplier(int rank) {
        return 1.2 + 0.4 * Math.max(1, rank);
    }

    // 星読みの心得: 星読みの木の武器 (杖) を持っている間、マナの戻りが増える
    public static double starReadingRegen(int rank) {
        return 0.15 * Math.max(1, rank);
    }

    // 熾火の玉: ゆっくり飛ぶ火の玉。当たる (敵か壁) と 3 ブロックに弾け、燃やす
    public static final double ORB_RADIUS = 3;
    public static final double ORB_SPEED = 1.0;
    public static final int ORB_LIFE = 30;
    public static final int ORB_BURN = 3 * SECOND;

    public static double orbMultiplier(int rank) {
        return rank >= 2 ? 2.8 : 2.2;
    }

    // 凍て潮: 自分のまわりに冷たい潮。傷と、強い鈍足 (大きな相手は半分)
    public static final double FROST_SLOW = 0.5;

    public static double frostRadius(int rank) {
        return rank >= 2 ? 6 : 5;
    }

    public static double frostMultiplier(int rank) {
        return rank >= 2 ? 1.3 : 1.0;
    }

    public static int frostTicks(int rank) {
        return (rank >= 2 ? 4 : 3) * SECOND;
    }

    // 転移: 見ている向きへ跳ぶ (壁の手前まで)。落ちる傷は消える
    public static double blinkRange(int rank) {
        return rank >= 2 ? 11 : 8;
    }

    // 星の誓い (要): 星読みの技の傷 +25%、そのマナ +25%
    public static final double STAR_OATH_POWER = 0.25;
    public static final double STAR_OATH_MANA = 0.25;

    // 炉の踏み (鍛冶師): 足もとを踏み鳴らして 3.5 ブロックに火の波。浮かせて燃やす
    public static final double STOMP_RADIUS = 3.5;
    public static final int STOMP_BURN = 2 * SECOND;

    public static double stompMultiplier(int rank) {
        return rank >= 2 ? 1.2 : 0.9;
    }

    // 霧隠れ (暗殺者): 姿を消し、速くなる。狙っていた敵は見失う。消えている間の最初の一撃 +50%
    public static final double VEIL_SPEED = 0.2;
    public static final double VEIL_STRIKE = 1.5;
    public static final double VEIL_LOSE_RANGE = 16;

    public static int veilTicks(int rank) {
        return (rank >= 2 ? 4 : 3) * SECOND;
    }

    // 呪いの印 (呪術師): 見ている敵に印。受ける傷が増え、攻撃が弱る
    public static final double CURSE_RANGE = 16;
    public static final double CURSE_WEAKEN = 0.2;

    public static double curseBonus(int rank) {
        return rank >= 2 ? 0.2 : 0.15;
    }

    public static int curseTicks(int rank) {
        return (rank >= 2 ? 8 : 6) * SECOND;
    }

    // 灯の守り札 (呪術師): 足もとに灯を置く。そばの自分と仲間に、2 秒ごとに吸収 (黄ハート) を足す (回復はしない)
    public static final double LAMP_RADIUS = 4;
    public static final int LAMP_PULSE = 2 * SECOND;

    public static int lampTicks(int rank) {
        return (rank >= 2 ? 10 : 8) * SECOND;
    }

    public static float lampAbsorb(int rank) {
        return rank >= 2 ? 3f : 2f;
    }

    // ---------------------------------------------------------------- 画面の説明に渡す値
    // 翻訳の %s に入れる値 (段ごと)。lang の skill.skyabyss.<id>.effect と順をそろえる
    public static Object[] describeArgs(Skill skill, int rank) {
        int r = Math.max(1, rank);
        return switch (skill) {
            case TITAN_CLEAVE -> new Object[] {pct(cleaveMultiplier(r)), fmt(CLEAVE_LARGE_BONUS)};
            case RIPOSTE -> new Object[] {pct(riposteBonus(r)), RIPOSTE_WINDOW / SECOND};
            case ROCK_CALL -> new Object[] {fmt(ROCK_CALL_RADIUS), rockCallTicks(r) / SECOND, fmt(ROCK_CALL_KNOCKBACK)};
            case STONE_STANCE -> new Object[] {fmt(stanceArmor(r)), fmt(stanceKnockback(r) * 100)};
            case WHALE_SWEEP -> new Object[] {fmt(SWEEP_RADIUS), pct(sweepMultiplier(r)), fmtSeconds(cooldownTicks(skill, r))};
            case STONE_OATH -> new Object[] {pct(OATH_SHARE), 2, fmt(ALLY_RANGE)};
            case LUNGE, SHIELD_RUSH -> new Object[] {fmt(LUNGE_RANGE), pct(LUNGE_MULTIPLIER), fmtSeconds(cooldownTicks(skill, r))};
            case NIMBLE -> new Object[] {fmt(nimbleMove(r) * 100), fmt(nimbleFall(r))};
            case BACKSTEP, ARCHER_LEAP -> new Object[] {fmt(BACKSTEP_ENEMY_RANGE), fmtSeconds(cooldownTicks(skill, r))};
            case FORESIGHT -> new Object[] {fmt(foresightBonus(r) * 100), FORESIGHT_TICKS / SECOND};
            case WIND_REND -> new Object[] {pct(rendBack(r)), pct(rendFront(r))};
            case LONE_WIND -> new Object[] {pct(LONE_ATTACK), pct(LONE_MOVE), fmt(LONE_RANGE)};
            case WARD_SONG -> new Object[] {fmt(WARD_SONG_RADIUS), fmt(wardAbsorb(r)), fmt(wardAbsorb(r)), WARD_SONG_TICKS / SECOND};
            case CALM_HEART -> new Object[] {fmt(calmHeartBonus(r) * 100)};
            case EBB_TIDE -> new Object[] {fmt(ebbRange(r)), ebbSlowTicks(r) / SECOND};
            case RIPPLE -> new Object[] {fmt(rippleSlow(r) * 100), RIPPLE_TICKS / SECOND};
            case CYCLE_SONG -> new Object[] {fmt(CYCLE_SONG_RADIUS), cycleSongTicks(r) / SECOND, pct(CYCLE_SONG_ALLY), pct(CYCLE_SONG_SELF)};
            case CHORUS_VOICE -> new Object[] {fmt(CHORUS_PER_ALLY * 100), CHORUS_MAX_ALLIES, fmt(ALLY_RANGE)};
            case TEMPER -> new Object[] {TEMPER_TICKS / SECOND, pct(temperMultiplier(r)), TEMPER_BURN_TICKS / SECOND, fmt(TEMPER_RANGE)};
            case EMBER_HEART -> new Object[] {fmt(emberHeartReduction(r) * 100)};
            case EMBER_HAMMER -> new Object[] {fmt(HAMMER_RADIUS), pct(hammerMultiplier(r)), EMBER_TICKS / SECOND};
            case FORGE_MEMORY -> new Object[] {r, pct(FORGE_MEMORY_BONUS)};
            case ASH_SCATTER -> new Object[] {fmt(ASH_SCATTER_RADIUS), ashScatterTicks(r) / SECOND, pct(ASH_ATTACK), pct(ASH_PROJECTILE)};
            case SMITH_EYE -> new Object[] {fmt(SMITH_PER_LEVEL * 100), fmt(SMITH_PER_LEVEL * 100 * 10)};
            case CLOUD_PIERCE -> new Object[] {pierceDamage(r), piercePierce(r)};
            case FAR_SHOT -> new Object[] {fmt(0.06 * r * 100), fmt(FAR_FULL)};
            case BONE_TRAP -> new Object[] {TRAP_MAX, trapBindTicks(r) / SECOND, pct(trapMultiplier(r)), pct(TRAP_LARGE_MULTIPLIER)};
            case HAWK_EYE -> new Object[] {fmt(markBonus(r) * 100), MARK_TICKS / SECOND};
            case SKY_RAIN -> new Object[] {fmt(RAIN_RADIUS), RAIN_PULSES, fmt(rainDamage(r)), fmtSeconds(cooldownTicks(skill, r))};
            case STILL_STRING -> new Object[] {pct(STILL_BONUS), STILL_TICKS / SECOND};
            case DEEP_PULL -> new Object[] {fmt(PULL_RADIUS), pct(pullMultiplier(r)), fmt(PULL_SLOW * 100), PULL_SLOW_TICKS / SECOND};
            case DEEP_MIRROR -> new Object[] {fmt(mirrorPower(r) * 100), MIRROR_TAGS};
            case DEEP_ECHO -> new Object[] {fmt(echoPower(r) * 100), ECHO_TICKS / SECOND, fmtSeconds(cooldownTicks(skill, r))};
            case DEEP_TITHE -> new Object[] {r};
            case DEEP_MAW -> new Object[] {fmt(MAW_RADIUS), pct(mawMultiplier(r)), fmt(MAW_REACH), fmtSeconds(cooldownTicks(skill, r))};
            case DEEP_OATH -> new Object[] {fmt(OATH_DEEP_POWER * 100), fmt(OATH_DEEP_REGEN * 100)};
            case ECHO_STRIKE -> new Object[] {pct(echoStrikeMultiplier(r)), fmt(ECHO_STRIKE_RANGE)};
            case OLD_VIGIL -> new Object[] {fmt(vigilArmor(r)), fmt(vigilMana(r))};
            case GRAVE_BELL -> new Object[] {fmt(BELL_RADIUS), pct(bellMultiplier(r)), fmt(BELL_SLOW * 100), BELL_TICKS / SECOND};
            case MEMORY_EDGE -> new Object[] {fmt(memoryPower(r, 6) * 100), fmt(MEMORY_PER_REBIRTH * 100), MEMORY_REBIRTH_MAX};
            case FINAL_LIGHT -> new Object[] {fmt(LIGHT_RADIUS), pct(lightMultiplier(r)), fmtSeconds(cooldownTicks(skill, r))};
            case SOUL_THREAD -> new Object[] {fmt(-THREAD_COOLDOWN * 100)};
            case THUNDER_BOLT -> new Object[] {fmt(BOLT_RANGE), pct(boltMultiplier(r)), r >= 3 ? pct(BOLT_CHAIN) : "0%", fmt(BOLT_CHAIN_RANGE)};
            case STAR_READING -> new Object[] {fmt(starReadingRegen(r) * 100)};
            case EMBER_ORB -> new Object[] {fmt(ORB_RADIUS), pct(orbMultiplier(r)), ORB_BURN / SECOND};
            case FROST_TIDE -> new Object[] {fmt(frostRadius(r)), pct(frostMultiplier(r)), fmt(FROST_SLOW * 100), frostTicks(r) / SECOND};
            case BLINK -> new Object[] {fmt(blinkRange(r)), fmtSeconds(cooldownTicks(skill, r))};
            case STAR_OATH -> new Object[] {fmt(STAR_OATH_POWER * 100), fmt(STAR_OATH_MANA * 100)};
            case FORGE_STOMP -> new Object[] {fmt(STOMP_RADIUS), pct(stompMultiplier(r)), STOMP_BURN / SECOND};
            case SHIELD_BASH -> new Object[] {fmt(BASH_RANGE), pct(bashMultiplier(r)), BASH_SLOW_TICKS / SECOND};
            case BONE_SWING -> new Object[] {fmt(SWING_RANGE), pct(swingMultiplier(r)), fmt(SWING_LARGE)};
            case SPIN_CUT -> new Object[] {fmt(SPIN_RADIUS), pct(spinMultiplier(r)), SPIN_DASH_BLOCKS};
            case TWIN_FANG -> new Object[] {pct(FANG_MULTIPLIER), fmt(FANG_BACK)};
            case SPARK_BURST -> new Object[] {fmt(SPARK_RADIUS), pct(sparkMultiplier(r)), SPARK_BURN / SECOND};
            case SNARE_SHOT -> new Object[] {fmt(SNARE_RANGE), fmt(pierceDamage(r) * SNARE_MULTIPLIER), fmt(SNARE_SLOW * 100), snareTicks(r) / SECOND};
            case STARDUST -> new Object[] {fmt(DUST_RADIUS), pct(dustMultiplier(r))};
            case MIST_VEIL -> new Object[] {veilTicks(r) / SECOND, fmt(VEIL_SPEED * 100), pct(VEIL_STRIKE)};
            case CURSE_MARK -> new Object[] {fmt(CURSE_RANGE), fmt(curseBonus(r) * 100), curseTicks(r) / SECOND};
            case LAMP_CHARM -> new Object[] {fmt(LAMP_RADIUS), lampTicks(r) / SECOND, fmt(lampAbsorb(r)), LAMP_PULSE / SECOND};
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

    // ---------------------------------------------------------------- 淵・前世の技・紋・変異 (docs/design/31 の A08・A14・B08・B09)
    public static final double PULL_RADIUS = 7;
    public static final double PULL_SLOW = 0.35;
    public static final int PULL_SLOW_TICKS = 5 * SECOND / 2;

    public static double pullMultiplier(int rank) {
        return rank >= 2 ? 0.7 : 0.5;
    }

    public static final int MIRROR_TAGS = 3;

    public static double mirrorPower(int rank) {
        return 0.01 + 0.02 * rank;
    }

    public static final int ECHO_TICKS = 5 * SECOND;

    public static double echoPower(int rank) {
        return rank >= 2 ? 0.18 : 0.12;
    }

    public static final double MAW_RADIUS = 4;
    public static final double MAW_REACH = 10;

    public static double mawMultiplier(int rank) {
        return rank >= 2 ? 1.7 : 1.3;
    }

    public static final double OATH_DEEP_POWER = 0.08;
    public static final double OATH_DEEP_REGEN = 0.05;
    public static final double ECHO_STRIKE_RANGE = 7;

    public static double echoStrikeMultiplier(int rank) {
        return rank >= 2 ? 1.6 : 1.3;
    }

    public static double vigilArmor(int rank) {
        return rank;
    }

    public static double vigilMana(int rank) {
        return 4 * rank;
    }

    public static final double BELL_RADIUS = 5;
    public static final double BELL_SLOW = 0.40;
    public static final int BELL_TICKS = 3 * SECOND;

    public static double bellMultiplier(int rank) {
        return rank >= 2 ? 0.8 : 0.6;
    }

    public static final double MEMORY_PER_REBIRTH = 0.005;
    public static final int MEMORY_REBIRTH_MAX = 6;

    public static double memoryPower(int rank, int rebirth) {
        return MEMORY_PER_REBIRTH * Math.min(MEMORY_REBIRTH_MAX, Math.max(0, rebirth)) * rank;
    }

    public static final double LIGHT_RADIUS = 5.5;

    public static double lightMultiplier(int rank) {
        return rank >= 2 ? 2.0 : 1.5;
    }

    public static final double THREAD_COOLDOWN = -0.10;

    // 紋の盤 (A14): 穴は最初 3、転生 1 回ごとに 1 つ開いて最大 6。持てる紋は 24。
    // 紋の効きは盤の合計で、力 +20%・マナ −10%・待ち −20% が上限 (全部足した上限でも心臓 15 秒・番兵長 5 秒の下限を割らない)
    public static final int GLYPH_SOCKETS = 6;
    public static final int GLYPH_SOCKETS_BASE = 3;
    public static final int GLYPH_BAG_MAX = 24;
    public static final double GLYPH_POWER_CAP = 0.20;
    public static final double GLYPH_MANA_CAP = 0.10;
    public static final double GLYPH_COOLDOWN_CAP = 0.20;

    public static int glyphSockets(int rebirth) {
        return Math.min(GLYPH_SOCKETS, GLYPH_SOCKETS_BASE + Math.max(0, rebirth));
    }

    private SkillRules() {
    }
}
