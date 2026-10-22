package jp.revati.skyabyss.story.ch1;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.WayfarerNotes;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

// 一章の支度 (docs/design/34 の 4.2、21 の 3.1〜3.2) の名前と、職人 (P4)・旅装と砥石 (P5)・地上の敵 (P7)・岩鯨の背 (P8) との差し込み口の一覧。
//
// 支度の段 3 本 (メインの段。順不同) は data/skyabyss/quest/ch1/trail_iron|trail_blade|trail_shield.json (tools/gen/ch1/gen_ch1_trails.py)。型の決まりは 07 の 6.8。
// イサナの「鯨の話」(印 c_whale_heard) のあと、一章の人に三本とも出る。旧い備えの頼み (prep_gear・prep_blade・prep_pulse) はこの三本に吸収した。
//   trail_iron   (鉄の跡。イサナ。100)   骨の坑 (BonePit) の奥の前の私の荷で頁 ch1/trail_iron → 鉄の防具 4 部位を着る (目的 wear)
//   trail_blade  (折れた剣。職人。100)  割れ殻の洞 (CleftCave) を抜けると台座に折れた剣 = 頁 ch1/trail_blade → 職人に話す (作り方を教わる) →
//                                       鉄の剣を持つ + 岩の砥石を使う (合図 SIGNAL_WHETSTONE_USED)
//   trail_shield (盾と糧。語り部。50)   盾と道の固焼き 8 を持つ。報酬で頁 ch1/trail_shield。鼓動守 (護り糸を職人に編んでもらう) と鯨道の旅装は任意
//   三本そろう   合図 ch1_ready (PrepQuests) と記念品「鯨追いの印」(WHALE_CHASER_MARK。見た目だけ)、進捗 whale_trail
//
// ---------------------------------------------------------------- 差し込み口の状況
//   [P4 職人 済]  旅の職人 (skyabyss:artisan) が trail_blade の依頼主。立つ所は荷車の傍らの道 (印 "artisan") = npc/ArtisanSpawner の Spot。
//              会話は dialogue/artisan.json (全章の共通) + dialogue/ch1/artisan.json (一章の足し書き。tools/gen/items/gen_artisan_dialogue.py)。
//              護り糸から最初の鼓動守を編む (入口 ch1t_ward_weave)・編み直し (選択肢 ward。岩殻片 8 つ・1 日 1 つ。Action remake_ward / Condition ward_ok)
//   [P5 旅装など 済] 鯨道の旅装 (guide/TravelGear)・岩の砥石・道の固焼き・苔の軟膏のレシピは RECIPES の名前で WayfarerNotes に登録済みで、
//              覚書を開く・職人に教わる (Action unlock_recipes) までは作れない (guide/RecipeLock)。旅装は任意 (一式で合図 SIGNAL_GEAR_MADE。今はどの段も読まない)
//   [P7 地上の敵] 入った (Ch1Mobs)。割れ殻の洞: 入口の間 岩殻虫 3、棘の間 棘うさぎ 3、奥の間 老いた歩哨 1。骨の坑: 坑口の間 苔這い 3、巣の間 苔這い 2、荷の間 岩殻虫 2
//   [P8 岩鯨の背] 歌の間の壁画の順 (BELL_ORDER) は数え歌の鐘楼が同じ並びを読む。歩哨の割符 (SENTRY_TALLY) は老いた歩哨の落とし物。
public final class Ch1Prep {
    // 支度の段 (PrepQuests.ids(1) と同じ並び)
    public static final String Q_IRON = "ch1/trail_iron";
    public static final String Q_BLADE = "ch1/trail_blade";
    public static final String Q_SHIELD = "ch1/trail_shield";
    public static final List<String> TRAILS = List.of(Q_IRON, Q_BLADE, Q_SHIELD);

    // 地上の新しい場所の要素 ID (ChapterSites)
    public static final String CAIRN_PREFIX = "ch1_road_cairn_";
    public static final int CAIRNS = 7;
    public static final String BONE_CACHE = "ch1_bone_cache";
    public static final String CLEFT_CAVE = "ch1_cleft_shell_cave";
    public static final String BONE_PIT = "ch1_bone_pit";
    // 荷車の傍らの道は失せ物の荷車 (ch1_lost_cart) の建て終わりに隣へ置く (印 "artisan" = 職人が立つ所)
    public static final String CART_TRACK_BLUEPRINT = "ch1_cart_track";
    public static final String SONG_CHAMBER_BLUEPRINT = "ch1_song_chamber";

    // 合図
    public static final String SIGNAL_GEAR_MADE = "ch1_travel_gear_made";
    // 岩の砥石を使った (TravelGear.applyWhetstone)。trail_blade の鍛えの段の「砥石を使う」
    public static final String SIGNAL_WHETSTONE_USED = "ch1_whetstone_used";

    // 歌の間の壁画の順 (鐘楼 P8 が読む。柱の高さ 1〜5 を低い順。歌石と同じ)
    public static final int[] BELL_ORDER = {1, 2, 3, 4, 5};
    // 歩哨の割符 (P8 の詰所の戸の鍵。人ごとの記念品。GuideSetup に登録済み)
    public static final String SENTRY_TALLY = "skyabyss:sentry_tally";

    // 伝承 (manual。コードが記す。lore/entry/ch1/)
    public static final String ROAD_CAIRNS = "ch1/road_cairns";
    public static final String SONG_CHAMBER = "ch1/song_chamber";
    public static final String CLEFT_CAVE_LORE = "ch1/cleft_cave";
    // 前の私の頁 (支度の段。34 の 5 章) と冷えの戸の碑文
    public static final String PAGE_IRON = "ch1/trail_iron";
    public static final String PAGE_BLADE = "ch1/trail_blade";
    public static final String PAGE_SHIELD = "ch1/trail_shield";
    public static final String COLD_DOOR = "ch1/cold_door";
    public static final List<String> LORE = List.of(ROAD_CAIRNS, SONG_CHAMBER, CLEFT_CAVE_LORE, PAGE_IRON, PAGE_BLADE, PAGE_SHIELD, COLD_DOOR);
    // 「はじめて見た物」の経験値 (一人一回。章の枠では「既存の小要素」の側に数える小さな分。頁は 0 = 経験値は支度の段が持つ)
    public static final Map<String, Integer> LORE_XP = Map.of(ROAD_CAIRNS, 10, SONG_CHAMBER, 10, CLEFT_CAVE_LORE, 0);

    // 旅の覚書 (一章) が解くレシピ (data/skyabyss/recipe。解くまで作れない = guide/RecipeLock。hare_quill_arrow は予約だけ (棘針の矢。まだ無い。存在しない ID は WayfarerNotes が無視する))
    public static final List<String> RECIPES = List.of("whale_road_helmet", "whale_road_chestplate", "whale_road_leggings", "whale_road_boots",
            "rock_whetstone", "road_hardtack", "moss_salve", "hare_quill_arrow");

    private Ch1Prep() {
    }

    // 登録 (Ch1 の共通の初期化から)
    static void register() {
        GrowthRewards.LORE_XP.putIfAbsent(ROAD_CAIRNS, 10);
        GrowthRewards.LORE_XP.putIfAbsent(SONG_CHAMBER, 10);
        GrowthRewards.LORE_XP.putIfAbsent(CLEFT_CAVE_LORE, 0);
        ResourceLocation[] ids = RECIPES.stream().map(n -> ResourceLocation.fromNamespaceAndPath("skyabyss", n)).toArray(ResourceLocation[]::new);
        WayfarerNotes.registerRecipes(1, ids);
    }

    // 登録済みの敵か (P7 が入ったか)
    public static boolean entityExists(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ENTITY_TYPE.containsKey(key);
    }
}
