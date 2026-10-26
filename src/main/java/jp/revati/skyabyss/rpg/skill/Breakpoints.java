package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import net.minecraft.world.entity.player.Player;

// ステータスの刻み目: 15 と 30 に 1 つずつ、小さな分かれ目 (4 つ × 2 = 8)。効果の線形 (04 の 4.2) は変えない。
// 記録は増やさず、今の数値から計算する。docs/design/27 の 2.1
public final class Breakpoints {
    public static final int FIRST = 15;
    public static final int SECOND = 30;

    // 筋力 15: 重の武器の振りの後隙 −8% (攻撃速度 +8% として効かせる)
    public static final double STRENGTH_15_SWING = 0.08;
    // 筋力 30: 範囲の技の押し出し ×1.25
    public static final double STRENGTH_30_PUSH = 1.25;
    // 敏捷 15: 予告をよけた直後 2 秒、移動 +8%
    public static final double AGILITY_15_MOVE = 0.08;
    public static final int AGILITY_15_TICKS = 2 * SkillRules.SECOND;
    // 敏捷 30: 踏み込み・跳び退き・風裂きの待ち −10%
    public static final double AGILITY_30_COOLDOWN = 0.10;
    // 精神 15: マナの回復 +10%
    public static final double SPIRIT_15_REGEN = 0.10;
    // 精神 30: 仲間に向ける技 (唄・合図) の効果 +10%
    public static final double SPIRIT_30_ALLY = 0.10;
    // 耐久 15: ノックバック耐性 +10%
    public static final double VITALITY_15_KNOCKBACK = 0.10;
    // 耐久 30: 火・毒の傷 −15%
    public static final double VITALITY_30_FIRE = 0.15;

    private Breakpoints() {
    }

    // ステータスの刻み目は 43 (6 職) で削った (2026-10-26)。どこでも届いていないことにする
    public static boolean reached(int stat, int at) {
        return false;
    }

    public static boolean reached(Player player, Stat stat, int at) {
        return false;
    }

    // 8 つの刻み目 (id は lang の breakpoint.skyabyss.<id> と一対一)
    public enum Mark {
        STRENGTH_15("strength_15", Stat.STRENGTH, FIRST),
        STRENGTH_30("strength_30", Stat.STRENGTH, SECOND),
        AGILITY_15("agility_15", Stat.AGILITY, FIRST),
        AGILITY_30("agility_30", Stat.AGILITY, SECOND),
        SPIRIT_15("spirit_15", Stat.SPIRIT, FIRST),
        SPIRIT_30("spirit_30", Stat.SPIRIT, SECOND),
        VITALITY_15("vitality_15", Stat.VITALITY, FIRST),
        VITALITY_30("vitality_30", Stat.VITALITY, SECOND);

        public final String id;
        public final Stat stat;
        public final int at;

        Mark(String id, Stat stat, int at) {
            this.id = id;
            this.stat = stat;
            this.at = at;
        }

        public String key() {
            return "breakpoint.skyabyss." + id;
        }

        public static Mark of(Stat stat, int at) {
            for (Mark m : values()) {
                if (m.stat == stat && m.at == at) {
                    return m;
                }
            }
            return null;
        }
    }
}
