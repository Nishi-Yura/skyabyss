package jp.revati.skyabyss.rpg.skill;

// 構え (守・疾・歌)。同時にとれるのは 1 つ。切り替えは短い待ち (SkillRules.STANCE_SWAP_TICKS)。docs/design/31 の A11
public enum SkillStance {
    NONE("none"),
    GUARD("guard"),
    SWIFT("swift"),
    SONG("song");

    public final String id;

    SkillStance(String id) {
        this.id = id;
    }

    public String nameKey() {
        return "stance.skyabyss." + id;
    }

    public String descKey() {
        return "stance.skyabyss." + id + ".desc";
    }

    public static SkillStance of(int i) {
        SkillStance[] v = values();
        return i >= 0 && i < v.length ? v[i] : NONE;
    }
}
