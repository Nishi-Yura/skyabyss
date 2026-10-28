package jp.revati.skyabyss.facility;

// 係の人の見た目 (案 A 上着と前掛け。B・C の案は 2026-10-29 に消した。NpcEntity.variant は 0 か 1 で、どちらも a)。描画と自己テストが使う
public final class AttendantLook {
    // 既定の案 (A 上着と前掛け。いまの NPC の作りにいちばん近い)
    public static final String DEFAULT = "a";
    public static final String[] LOOKS = {"a"};

    private AttendantLook() {
    }

    public static String look(int variant) {
        return variant >= 1 && variant <= LOOKS.length ? LOOKS[variant - 1] : DEFAULT;
    }

    public static String texture(int variant, String role) {
        return "attendant_" + look(variant) + "_" + role;
    }
}
