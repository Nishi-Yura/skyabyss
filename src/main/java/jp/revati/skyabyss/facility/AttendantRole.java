package jp.revati.skyabyss.facility;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;

// 都の施設の係の人の役 (docs/design/35 の 3 章)。一つの役 = 施設一つ。
//   id     : 役の名前。都の設計図の印 npc_<id> で立つ所を決め、会話は data/skyabyss/dialogue/attendant_<id>.json
//   shop   : 店を開く役の、店の ID (data/skyabyss/shop/<shop>.json)。店でなければ null
//   street : 1 = 大通りの 1 列目 (よく使う)、2 = 章で開く、3 = 裏通り
// 頭の上の印は textures/gui/npc_marker/fac_<id>.png (tools/gen_facility_icons.py)
public enum AttendantRole {
    GUILD_CLERK("guild_clerk", null, 1),
    SMITH("smith", null, 1),
    TOOL_MERCHANT("tool_merchant", "tool_shop", 1),
    APOTHECARY("apothecary", "apothecary", 1),
    STORAGE_KEEPER("storage_keeper", null, 1),
    INNKEEPER("innkeeper", null, 1),
    PERCH_KEEPER("perch_keeper", null, 1),
    SHRINE_PRIEST("shrine_priest", null, 2),
    KEEP_VETERAN("keep_veteran", null, 2),
    ARCHIVIST("archivist", null, 2),
    TRAINING_MASTER("training_master", null, 2),
    BLACK_MARKETEER("black_marketeer", "black_market", 3),
    GRAVEKEEPER("gravekeeper", null, 3),
    MAPMAKER("mapmaker", null, 3),
    DRUNKARD("drunkard", null, 3);

    // 設計図の印の頭 (npc_<役>)
    public static final String MARK_PREFIX = "npc_";
    // 会話の役の ID の頭 (skyabyss:attendant_<役>)
    public static final String DIALOGUE_PREFIX = SkyAbyss.MODID + ":attendant_";

    public final String id;
    @Nullable
    public final String shop;
    public final int street;

    AttendantRole(String id, @Nullable String shop, int street) {
        this.id = id;
        this.shop = shop;
        this.street = street;
    }

    public String mark() {
        return MARK_PREFIX + id;
    }

    public String dialogueRole() {
        return DIALOGUE_PREFIX + id;
    }

    public String nameKey() {
        return "entity.skyabyss.attendant." + id;
    }

    @Nullable
    public static AttendantRole byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (AttendantRole r : values()) {
            if (r.id.equals(s)) {
                return r;
            }
        }
        return null;
    }

    // 印の名前 (npc_smith) から役。印でなければ null
    @Nullable
    public static AttendantRole byMark(String mark) {
        return mark.startsWith(MARK_PREFIX) ? byId(mark.substring(MARK_PREFIX.length())) : null;
    }

    // 会話の役 (skyabyss:attendant_smith) から役
    @Nullable
    public static AttendantRole byDialogueRole(String role) {
        return role.startsWith(DIALOGUE_PREFIX) ? byId(role.substring(DIALOGUE_PREFIX.length())) : null;
    }
}
