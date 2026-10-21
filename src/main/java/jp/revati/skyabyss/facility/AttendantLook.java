package jp.revati.skyabyss.facility;

// 係の人の見た目の案 (NpcEntity.variant: 0 = 既定、1〜3 = 案 a〜c。docs/review/npc_roughs/)。描画と自己テストが使う
public final class AttendantLook {
    // 既定の案 (A 上着と前掛け。いまの NPC の作りにいちばん近い)
    public static final String DEFAULT = "a";
    public static final String[] LOOKS = {"a", "b", "c"};

    private AttendantLook() {
    }

    public static String look(int variant) {
        return variant >= 1 && variant <= 3 ? LOOKS[variant - 1] : DEFAULT;
    }

    public static String texture(int variant, String role) {
        return "attendant_" + look(variant) + "_" + role;
    }
}
