package jp.revati.skyabyss.facility;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;

// 都の施設の係の人の役 (docs/design/35 の 3 章)。一つの役 = 施設一つ。
//   id     : 役の名前。都の設計図の印 npc_<id> で立つ所を決め、会話は data/skyabyss/dialogue/attendant_<id>.json
//   shop   : 店を開く役の、店の ID (data/skyabyss/shop/<shop>.json)。店でなければ null
//   street : 1 = 大通りの 1 列目 (よく使う)、2 = 章で開く、3 = 裏通り
// 頭の上の印は textures/gui/npc_marker/fac_<id>.png (tools/gen/ui/gen_facility_icons.py)
public enum AttendantRole {
    GUILD_CLERK("guild_clerk", null, 1),
    SMITH("smith", null, 1),
    TOOL_MERCHANT("tool_merchant", "tool_shop", 1),
    APOTHECARY("apothecary", "apothecary", 1),
    STORAGE_KEEPER("storage_keeper", null, 1),
    INNKEEPER("innkeeper", null, 1),
    PERCH_KEEPER("perch_keeper", null, 1),
    SHRINE_PRIEST("shrine_priest", null, 2),
    TRAINING_MASTER("training_master", null, 2),
    BLACK_MARKETEER("black_marketeer", "black_market", 3),
    GRAVEKEEPER("gravekeeper", null, 3),
    DRUNKARD("drunkard", null, 3),
    // 取引所 (プレイヤーどうしの売り買い。35 の 6.11)
    TRADE_KEEPER("trade_keeper", null, 1),
    // 厩舎 (乗り物の店。docs/design/38)
    STABLE_KEEPER("stable_keeper", null, 1),
    // 都の案内人 (門と広場に何人も。印は npc_guide_<所>。話すと施設の一覧 → 選んだ戸まで灯りの道。facility/TownGuide、35 の 6.12)
    GUIDE("guide", null, 1),
    // 市の三人 (2026-10-27 の声「道具屋がパンまで売るのはおかしい。専門の人に」): 食べ物・得物と防具・素材
    FOOD_VENDOR("food_vendor", "food_shop", 1),
    OUTFITTER("outfitter", "outfit_shop", 1),
    MATERIAL_TRADER("material_trader", "material_shop", 1),
    // 封切り師 (封の箱を開ける。docs/design/52 の 5 章)。設計図の印は持たず、鍛冶屋の係の人のそばの空いた床に立つ (loot/box/BoxOpeners)
    BOX_OPENER("box_opener", null, 1);

    // 設計図の印の頭 (npc_<役>)
    public static final String MARK_PREFIX = "npc_";
    // 消した役 (2026-10-31「書庫番や地図屋って必要？」→ 両方消す。砦の老兵も同日に消した: 試練の記録は J の記録、本丸の頁は社の祭司)。前の世界に立っている人は AttendantEntity が消す。設計図の印は残っていても立たない
    public static final java.util.Set<String> RETIRED = java.util.Set.of("archivist", "mapmaker", "keep_veteran");
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

    // 印の名前 (npc_smith) から役。印でなければ null。案内人は所ごとに印がある (npc_guide_n・npc_guide_plaza …)
    @Nullable
    public static AttendantRole byMark(String mark) {
        if (!mark.startsWith(MARK_PREFIX)) {
            return null;
        }
        String id = mark.substring(MARK_PREFIX.length());
        return id.startsWith(GUIDE_ID + "_") ? GUIDE : byId(id);
    }

    private static final String GUIDE_ID = "guide";

    // 会話の役 (skyabyss:attendant_smith) から役
    @Nullable
    public static AttendantRole byDialogueRole(String role) {
        return role.startsWith(DIALOGUE_PREFIX) ? byId(role.substring(DIALOGUE_PREFIX.length())) : null;
    }
}
