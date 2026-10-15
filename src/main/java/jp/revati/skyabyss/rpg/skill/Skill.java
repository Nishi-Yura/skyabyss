package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;

// 技の一覧。木ごとに 3 段 (tier 0..2)。1 段目は左右の 2 つ、2 段目は真上の技が要る、3 段目 (奥義) は 2 段目のどちらかが要る。
// 数値は SkillRules、表と理由は docs/design/04_growth_and_skills.md の 10 章。
// 保存には id (文字列) を使う。並びを変えても記録は壊れない
public enum Skill {
    // ---------------------------------------------------------------- 剣士 (筋力)
    TITAN_CLEAVE("titan_cleave", SkillTree.SWORD, true, 3, 0, 0, 1),
    RIPOSTE("riposte", SkillTree.SWORD, false, 3, 0, 1, 1),
    LUNGE("lunge", SkillTree.SWORD, true, 2, 1, 0, 5, "titan_cleave"),
    STONE_STANCE("stone_stance", SkillTree.SWORD, false, 3, 1, 1, 5, "riposte"),
    WHALE_SWEEP("whale_sweep", SkillTree.SWORD, true, 2, 2, 2, 12, "lunge", "stone_stance"),
    // ---------------------------------------------------------------- 弓使い (敏捷)
    CLOUD_PIERCE("cloud_pierce", SkillTree.BOW, true, 3, 0, 0, 1),
    FAR_SHOT("far_shot", SkillTree.BOW, false, 3, 0, 1, 1),
    BACKSTEP("backstep", SkillTree.BOW, true, 2, 1, 0, 5, "cloud_pierce"),
    HAWK_EYE("hawk_eye", SkillTree.BOW, false, 3, 1, 1, 5, "far_shot"),
    SKY_RAIN("sky_rain", SkillTree.BOW, true, 2, 2, 2, 12, "backstep", "hawk_eye");

    public final String id;
    public final SkillTree tree;
    // true = スロットに置いて使う技 (能動)。false = 覚えるだけで効く技 (常時)
    public final boolean active;
    public final int maxRank;
    // 段 (0 = 1 段目) と、木の中の列 (0 = 左、1 = 右、2 = 真ん中)
    public final int tier;
    public final int column;
    // 覚えるのに要るレベル
    public final int minLevel;
    // 前提: このうちどれか一つを覚えていること (空なら前提なし)
    private final String[] requiresAny;

    Skill(String id, SkillTree tree, boolean active, int maxRank, int tier, int column, int minLevel, String... requiresAny) {
        this.id = id;
        this.tree = tree;
        this.active = active;
        this.maxRank = maxRank;
        this.tier = tier;
        this.column = column;
        this.minLevel = minLevel;
        this.requiresAny = requiresAny;
    }

    public List<Skill> requires() {
        List<Skill> list = new ArrayList<>();
        for (String r : requiresAny) {
            Skill s = byId(r);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    public String nameKey() {
        return "skill.skyabyss." + id;
    }

    // 段ごとの効果の説明 (引数は SkillRules.describeArgs)
    public String effectKey() {
        return "skill.skyabyss." + id + ".effect";
    }

    // 一行の言い伝え (画面の説明の頭に出す)
    public String flavorKey() {
        return "skill.skyabyss." + id + ".flavor";
    }

    // アイコンの位置 (gui/skill/icons.png の 32px の升目)
    public int iconIndex() {
        return ordinal();
    }

    public static Skill byId(String id) {
        if (id == null) {
            return null;
        }
        for (Skill s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static List<Skill> inTree(SkillTree tree) {
        List<Skill> list = new ArrayList<>();
        for (Skill s : values()) {
            if (s.tree == tree) {
                list.add(s);
            }
        }
        return list;
    }
}
