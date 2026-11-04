package jp.revati.skyabyss.rpg.skill;

// キーストーン (鍵石): 1 点で取れる、ルールが変わる強い特性。代わりに何かを失う。1 人 2 つまで。
// 要るのは、そのタグの木に点を 4 つ以上 (SkillRules.KEYSTONE_TREE_POINTS)。回復・無敵は作らない。docs/design/31 の A20
public enum SkillKeystone {
    BULWARK("bulwark", SkillTag.ROCK),
    GLASS_BLADE("glass_blade", SkillTag.WIND),
    FULL_TIDE("full_tide", SkillTag.TIDE),
    ASH_PACT("ash_pact", SkillTag.ASH),
    LONE_STRING("lone_string", SkillTag.BONE),
    WIND_DANCE("wind_dance", SkillTag.WIND),
    // 技の木 v3 で奥義にした五つ (動きは KeystoneEffects・SkillEvents が節の数 (hex_mark・cd_kill・storm_call・sun_burst・blink_guard) から見る)
    HEX_CHANT("hex_chant", SkillTag.TIDE),
    EMBER_LUNG("ember_lung", SkillTag.ASH),
    STORM_CALLER("storm_caller", SkillTag.BONE),
    SUN_HEART("sun_heart", SkillTag.ASH),
    TIDE_WALKER("tide_walker", SkillTag.TIDE);

    public final String id;
    public final SkillTag tag;

    SkillKeystone(String id, SkillTag tag) {
        this.id = id;
        this.tag = tag;
    }

    public String nameKey() {
        return "keystone.skyabyss." + id;
    }

    public String effectKey() {
        return "keystone.skyabyss." + id + ".effect";
    }

    public static SkillKeystone byId(String id) {
        for (SkillKeystone k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }
}
