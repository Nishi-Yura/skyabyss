package jp.revati.skyabyss.rpg.skill;

// 属性タグ (岩・風・潮・灰・骨)。全技・全パッシブ・装飾に付き、同じタグを数えて小さな力になる (docs/design/31 の A03)。
// 数えるのはサーバーとクライアントのどちらでもできる (記録の技と、付けている装飾から)。表示は色の点だけ
public enum SkillTag {
    ROCK("rock", SkillTree.ROCK, 0xC9A66B),
    WIND("wind", SkillTree.WIND, 0x8FE3C2),
    TIDE("tide", SkillTree.TIDE, 0x6FB8FF),
    ASH("ash", SkillTree.ASH, 0xFF8A4A),
    BONE("bone", SkillTree.BONE, 0xE8E2CF);

    public final String id;
    public final SkillTree tree;
    public final int rgb;

    SkillTag(String id, SkillTree tree, int rgb) {
        this.id = id;
        this.tree = tree;
        this.rgb = rgb;
    }

    public String nameKey() {
        return "tag.skyabyss." + id;
    }

    public static SkillTag of(SkillTree tree) {
        for (SkillTag t : values()) {
            if (t.tree == tree) {
                return t;
            }
        }
        return null;
    }

    // 淵・前世の技は持ち味に合わせてタグを付ける
    public static SkillTag of(Skill skill) {
        if (skill.tree.callable()) {
            return of(skill.tree);
        }
        return ext(skill);
    }

    private static SkillTag ext(Skill skill) {
        return switch (skill) {
            case DEEP_PULL, GRAVE_BELL -> TIDE;
            case DEEP_MIRROR, OLD_VIGIL, DEEP_OATH -> ROCK;
            case DEEP_ECHO, FINAL_LIGHT -> ASH;
            case DEEP_TITHE, MEMORY_EDGE -> WIND;
            case DEEP_MAW, ECHO_STRIKE, SOUL_THREAD -> BONE;
            default -> WIND;
        };
    }
}
