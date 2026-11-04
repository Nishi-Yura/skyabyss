package jp.revati.skyabyss.item.extra;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.affix.AffixType;
import jp.revati.skyabyss.rpg.skill.SkillTree;

// 巨神の粉の属性 (岩・風・潮・灰・骨)。差すと小さな数字が上がる (段の数だけ掛ける)。数字は武器と防具で別 (弓は GearFreedom.line)。
// 特性と同じ修飾子の道 (AffixRules.effective) で効く。tree は粉の名前と木の対応 (2026-11-05 から技のタグには数えない)
public enum Element {
    STONE("stone", SkillTree.ROCK, AffixType.SHARP, 0.02, AffixType.BULWARK, 0.25),
    WIND("wind", SkillTree.WIND, AffixType.SWIFT, 0.02, AffixType.STRIDE, 0.01),
    TIDE("tide", SkillTree.TIDE, AffixType.RESONANT, 3, AffixType.CALM, 2),
    ASH("ash", SkillTree.ASH, AffixType.KEEN, 0.02, AffixType.WARD, 0.01),
    BONE("bone", SkillTree.BONE, AffixType.THIRST, 0.01, AffixType.STEADY, 0.03);

    public final String id;
    public final SkillTree tree;
    public final AffixType weapon;
    public final double weaponValue;
    public final AffixType armor;
    public final double armorValue;

    Element(String id, SkillTree tree, AffixType weapon, double weaponValue, AffixType armor, double armorValue) {
        this.id = id;
        this.tree = tree;
        this.weapon = weapon;
        this.weaponValue = weaponValue;
        this.armor = armor;
        this.armorValue = armorValue;
    }

    public String nameKey() {
        return "element.skyabyss." + id;
    }

    @Nullable
    public static Element byId(String id) {
        for (Element e : values()) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    @Nullable
    public static Element ofTree(SkillTree tree) {
        for (Element e : values()) {
            if (e.tree == tree) {
                return e;
            }
        }
        return null;
    }
}
