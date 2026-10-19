package jp.revati.skyabyss.story.ch1;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.WayfarerNotes;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

// 一章の備えの導き (docs/design/21 の 3.1〜3.2、P6) の名前と、職人 (P4)・旅装と砥石 (P5) の済んだ分、あとの担当 (P7 地上の敵・P8 背中) への差し込み口の一覧。
//
// 備えの頼み 3 本は data/skyabyss/quest/ch1/prep_gear|prep_blade|prep_pulse.json (tools/gen_ch1_prep.py)。型の決まりは 07 の 6.8。
//   prep_gear  (身ごしらえ。80)  鉄 7・岩殻片 12・革 3・骨片 2 をそろえる (旅装 4 部位の素材の合計) → 職人に作り方を教わって (覚書でも可) 旅装を一式作る (合図 ch1_travel_gear_made)
//   prep_blade (刃を研ぐ。160)   割れ殻の洞をクリアする (合図 site_clear:ch1_cleft_shell_cave) + 岩の砥石を使う (合図 ch1_whetstone_used) → 職人に戻って話す
//   prep_pulse (鼓動を聞く。60)  護り糸 (洞の宝) を持ち帰る → 職人に鼓動守 (岩鯨) を編んでもらう。鼓動守は報酬
//
// ---------------------------------------------------------------- 差し込み口の状況 (P4 職人・P5 旅装など: 済。P7・P8 は未)
//   [P4 職人 済]  旅の職人 (skyabyss:artisan。低ポリのモデルは案 B、章ごとの 4 色。npc/NpcSetup・client/npc/NpcRenderer) が頼みの依頼主。
//              立つ所は荷車の傍らの道 (印 "artisan") = npc/ArtisanSpawner の Spot (章・場所の ID・印・条件の表。二〜四章は Spot を足すだけ)。
//              会話は dialogue/artisan.json (全章の共通) + dialogue/ch1/artisan.json (一章の足し書き。tools/gen_artisan_dialogue.py)、
//              語り部 (dialogue/ch1/storyteller_prep.json) には「荷車の職人」を指す一言だけ残した。
//              護り糸の補充 (鼓動守の再編) は職人の選択肢 (岩殻片 8 つ・1 日 1 つ・鼓動守を持っていない人。Action remake_ward / Condition ward_ok)。
//   [P5 旅装など 済] 鯨道の旅装 (案 A 殻肩。guide/TravelGear・WhaleRoadArmorItem)・岩の砥石・道の固焼き・苔の軟膏と、そのレシピ (data/skyabyss/recipe。
//              tools/gen_ch1_travel.py) は RECIPES の名前で WayfarerNotes に登録済みで、覚書を開く・職人に教わる (Action unlock_recipes) までは作れない
//              (guide/RecipeLock)。旅装を一式作ると合図 SIGNAL_GEAR_MADE が立ち、prep_gear が済む。砥石は Readiness.registerWhetstone で登録 (刃の手段の言い回しが替わる)。
//              prep_blade の洞の段は「洞を抜ける」+「砥石を使う (SIGNAL_WHETSTONE_USED)」。素材 4 つ (岩殻片・骨片・棘針・護り糸) は guide/GuideSetup に置いてある。
//              骨の集め場・洞の宝の「道の固焼き」は road_hardtack (guide/TravelGear)
//   [P7 地上の敵] CleftCave.pads() が、各部屋の敵を `Ch1Mobs.pick(P7 の ID, 今の代用)` で選ぶ。P7 の敵が登録されれば自動でそちらを使う:
//              岩殻虫 (rock_shell_beetle) = 入口の間 3、棘うさぎ (spine_hare) = 棘の間 3、老いた歩哨 (elder_sentry) = 奥の間 1 (代用は苔這い・骨の射手・骨の番兵)。
//              骨負い (bone_hauler) は BoneCache の骨の山を歩かせ、ch1/follow_hauler (語り部の頼み。50) を足す。ひとり歩きの湧き (Ch1Mobs.registerWanderers) に
//              岩殻虫 (丘・山・荒れ地、昼夜)・棘うさぎ (草原の夜) を足す。岩殻片・骨片・棘針の落とし物は P7 の loot。
//              老いた歩哨ができたら、prep_blade の「砥石を使う」を「研いだ刃 (TravelGear.WHETTED) で洞の主を倒す」に締める (TravelGear.applyWhetstone の TODO)
//   [P8 岩鯨の背] 歌の間の壁画の順 (BELL_ORDER) は数え歌の鐘楼 (西の翼 WD) が同じ並びを読む。歩哨の割符 (SENTRY_TALLY) は老いた歩哨の落とし物。
//              詰所の戸の鍵として P8 が読む (持つ人がいればそのグループは老番兵を倒さずに戸を開けられる)
public final class Ch1Prep {
    public static final String Q_GEAR = PrepQuests.id(1, "gear");
    public static final String Q_BLADE = PrepQuests.id(1, "blade");
    public static final String Q_PULSE = PrepQuests.id(1, "pulse");

    // 地上の新しい場所の要素 ID (ChapterSites)
    public static final String CAIRN_PREFIX = "ch1_road_cairn_";
    public static final int CAIRNS = 7;
    public static final String BONE_CACHE = "ch1_bone_cache";
    public static final String CLEFT_CAVE = "ch1_cleft_shell_cave";
    // 荷車の傍らの道は失せ物の荷車 (ch1_lost_cart) の建て終わりに隣へ置く (印 "artisan" = 職人が立つ所)
    public static final String CART_TRACK_BLUEPRINT = "ch1_cart_track";
    public static final String SONG_CHAMBER_BLUEPRINT = "ch1_song_chamber";

    // 合図
    public static final String SIGNAL_GEAR_MADE = "ch1_travel_gear_made";
    // 岩の砥石を使った (TravelGear.applyWhetstone)。prep_blade の洞の段の「砥石を使う」
    public static final String SIGNAL_WHETSTONE_USED = "ch1_whetstone_used";

    // 歌の間の壁画の順 (鐘楼 P8 が読む。柱の高さ 1〜5 を低い順。歌石と同じ)
    public static final int[] BELL_ORDER = {1, 2, 3, 4, 5};
    // 歩哨の割符 (P8 の詰所の戸の鍵。人ごとの記念品。P7 が老いた歩哨の落とし物にする。Items は未登録)
    public static final String SENTRY_TALLY = "skyabyss:sentry_tally";

    // 伝承 (manual。コードが記す。lore/entry/ch1/)
    public static final String ROAD_CAIRNS = "ch1/road_cairns";
    public static final String SONG_CHAMBER = "ch1/song_chamber";
    public static final String CLEFT_CAVE_LORE = "ch1/cleft_cave";
    public static final List<String> LORE = List.of(ROAD_CAIRNS, SONG_CHAMBER, CLEFT_CAVE_LORE);
    // 「はじめて見た物」の経験値 (一人一回。章の枠では「既存の小要素」の側に数える小さな分)
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
