package jp.revati.skyabyss.rpg;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;

// 記憶の断片の種類。今は名前だけを持つ (効果の枝は 03 の 5.1 節で後から作る)。
// 種類を足すときは ID をここに並べ、lang に fragment.skyabyss.<名前> を足す
public final class MemoryFragments {
    // 序章の報酬「最初の記憶の断片」(01 の章構成)
    public static final String PROLOGUE = SkyAbyss.MODID + ":prologue";
    // 岩鯨の心臓を止めた記憶
    public static final String STONE_WHALE = SkyAbyss.MODID + ":stone_whale";

    public static final List<String> KNOWN = List.of(PROLOGUE, STONE_WHALE);

    private MemoryFragments() {
    }

    public static String nameKey(String id) {
        if (KNOWN.contains(id)) {
            return "fragment.skyabyss." + id.substring(id.indexOf(':') + 1);
        }
        return "fragment.skyabyss.unknown";
    }
}
