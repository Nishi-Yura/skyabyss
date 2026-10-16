package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.rpg.GrowthRules.Stat;

// スキルツリー (クラス)。クラスは固定せず、どの木にも技の点を振れる (01 の「混合型」)。
// 星術師と深淵術師は枠だけ置き、章が進むまで封じておく (技はまだない)
public enum SkillTree {
    SWORD("sword", Stat.STRENGTH, false),
    BOW("bow", Stat.AGILITY, false),
    // 3 章 (月) で目覚める
    STAR("star", Stat.SPIRIT, true),
    // 2 章 (深淵) で目覚める
    ABYSS("abyss", Stat.SPIRIT, true);

    public final String id;
    // 効果の元になるステータス (画面に出すだけ。数値は技ごとに SkillRules で決める)
    public final Stat mainStat;
    public final boolean sealed;

    SkillTree(String id, Stat mainStat, boolean sealed) {
        this.id = id;
        this.mainStat = mainStat;
        this.sealed = sealed;
    }

    public String nameKey() {
        return "skilltree.skyabyss." + id;
    }

    public String descKey() {
        return "skilltree.skyabyss." + id + ".desc";
    }

    public String sealedKey() {
        return "skilltree.skyabyss." + id + ".sealed";
    }

    public static SkillTree byId(String id) {
        for (SkillTree t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
