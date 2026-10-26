package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.rpg.GrowthRules.Stat;

// スキルツリー (系統)。5 つの系統と共通の木 (旅人)、封じた 2 本 (星術師・深淵術師。技はまだない)。
// 木の形は 4 行 (Lv 1・5・12・20): 入口の 2 つ、2 行目の 2 つ、奥義の使う技 1、要 1 (docs/design/27_builds_and_classes.md の 2.2)
public enum SkillTree {
    ROCK("rock", Stat.STRENGTH, false, "sword"),
    WIND("wind", Stat.AGILITY, false, "sword"),
    TIDE("tide", Stat.SPIRIT, false, "tide"),
    ASH("ash", Stat.STRENGTH, false, "sword"),
    BONE("bone", Stat.AGILITY, false, "bow"),
    // 共通の木。誰でも取れる。天職の対象にはならない
    TRAVELER("traveler", null, false, "traveler"),
    // 魔法使いの木 (docs/design/43)。はじめから開いている
    STAR("star", Stat.SPIRIT, false, "star"),
    // 2 章 (深淵) で目覚める
    ABYSS("abyss", Stat.SPIRIT, true, "abyss"),
    // 橋 (A09)・淵 (B09。転生者だけ)・前世の技 (B08。転生者だけ。点は別枠)。封じではなく、画面の出し分けは転生の回数で
    HYBRID("hybrid", null, false, "hybrid"),
    DEEP("deep", Stat.SPIRIT, false, "deep"),
    PAST("past", null, false, "past");

    public final String id;
    // 効果の元になるステータス (画面に出すだけ。数値は技ごとに SkillRules で決める)。共通の木は null
    public final Stat mainStat;
    public final boolean sealed;
    // 実績 (skill:tree:<名>) へ出す名前。前の版の「剣士の木」「弓使いの木」の実績をそのまま使うため、近接は sword、骨の射手は bow
    public final String beat;

    SkillTree(String id, Stat mainStat, boolean sealed, String beat) {
        this.id = id;
        this.mainStat = mainStat;
        this.sealed = sealed;
        this.beat = beat;
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

    // 天職にできる木 (5 つの系統)
    public boolean callable() {
        return this == ROCK || this == WIND || this == TIDE || this == ASH || this == BONE;
    }

    // 画面の木のつまみに出す絵 (icons.png の升目)
    public int iconIndex() {
        return ordinal() < 8 ? 36 + ordinal() : 100 + 17 + (ordinal() - 8);
    }

    // 転生した人にだけ見える木 (淵・前世の技)
    public boolean rebornOnly() {
        return this == DEEP || this == PAST;
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
