package jp.revati.skyabyss.story.ch1;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.WayfarerNotes;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

// 一章の備えの導き (docs/design/21 の 3.1〜3.2、P6) の名前と、あとの担当 (P4 職人・P5 旅装と砥石・P7 地上の敵・P8 背中) への差し込み口の一覧。
//
// 備えの頼み 3 本は data/skyabyss/quest/ch1/prep_gear|prep_blade|prep_pulse.json (tools/gen_ch1_prep.py)。型の決まりは 07 の 6.8。
//   prep_gear  (身ごしらえ。80)  鉄 8 と岩殻片 12 をそろえる → 会話で旅装を編んでもらう (P5 のレシピが入ったら、合図 ch1_travel_gear_made でも済む)
//   prep_blade (刃を研ぐ。160)   割れ殻の洞をクリアする (合図 site_clear:ch1_cleft_shell_cave) → 戻って話す
//   prep_pulse (鼓動を聞く。60)  護り糸 (洞の宝) を持ち帰る → 会話で鼓動守 (岩鯨) を編んでもらう。鼓動守は報酬
//
// ---------------------------------------------------------------- TODO の差し込み口 (一覧。コードは各所の TODO コメントも見る)
//   [P4 職人]  頼みの依頼主 (quest の "giver") と会話の置き場は、今は語り部 (skyabyss:storyteller) で代用している。職人 (skyabyss:artisan。dialogue/ch1/artisan.json) が入ったら、
//              1) prep_gear・prep_blade・prep_pulse の "giver" を ARTISAN にする (tools/gen_ch1_prep.py の GIVER)
//              2) dialogue/ch1/storyteller_prep.json の三つの入口 (ch1p_*) を artisan.json へ移し、語り部には「職人の荷が見つかった」の一言だけ残す
//              3) 荷車の傍らの道 (cart_track。SITE_CART_TRACK の印 "artisan") に職人を立たせる
//              4) 護り糸の補充 (鼓動守の再編) を職人に移す (HeartbeatWard の補充。ここは初回の一つだけ報酬で渡す)
//   [P5 旅装など] 旅装・砥石・糧・薬のレシピ ID は RECIPES の名前で WayfarerNotes に登録済み (レシピが無い間は何も解かない)。
//              旅装を作ったときに Quests.signal(player, SIGNAL_GEAR_MADE) を送ると prep_gear が済む。砥石を使って洞の主を倒す条件は、
//              prep_blade の "report" 段に足す (今は洞を抜けるだけ)。洞・集め場の宝の「道の固焼き」は minecraft:bread で代用 (road_hardtack に替える)。
//              素材 4 つ (岩殻片・骨片・棘針・護り糸) は guide/GuideSetup に置いてある
//   [P7 地上の敵] 入った (21 の 3.1。Ch1Mobs・entity/RockShellBeetle・SpineHare・BoneHauler・ElderSentry)。CleftCave.pads() は新しい敵を使う
//              (入口の間 岩殻虫 3、棘の間 棘うさぎ 3、奥の間 老いた歩哨 1)。ひとり歩きは Ch1Mobs.registerWanderers、骨負いは Ch1Mobs の骨の集め場の巡回。
//              残り: ch1/follow_hauler (語り部の頼み。50)
//   [P8 岩鯨の背] 歌の間の壁画の順 (BELL_ORDER) は数え歌の鐘楼 (西の翼 WD) が同じ並びを読む。歩哨の割符 (SENTRY_TALLY = GuideSetup.SENTRY_TALLY) は
//              老いた歩哨の落とし物 (そこにいる人ごとに一つ。持っていれば出ない)。
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

    // 歌の間の壁画の順 (鐘楼 P8 が読む。柱の高さ 1〜5 を低い順。歌石と同じ)
    public static final int[] BELL_ORDER = {1, 2, 3, 4, 5};
    // 歩哨の割符 (P8 の詰所の戸の鍵。人ごとの記念品。GuideSetup に登録済み)
    public static final String SENTRY_TALLY = "skyabyss:sentry_tally";

    // 伝承 (manual。コードが記す。lore/entry/ch1/)
    public static final String ROAD_CAIRNS = "ch1/road_cairns";
    public static final String SONG_CHAMBER = "ch1/song_chamber";
    public static final String CLEFT_CAVE_LORE = "ch1/cleft_cave";
    public static final List<String> LORE = List.of(ROAD_CAIRNS, SONG_CHAMBER, CLEFT_CAVE_LORE);
    // 「はじめて見た物」の経験値 (一人一回。章の枠では「既存の小要素」の側に数える小さな分)
    public static final Map<String, Integer> LORE_XP = Map.of(ROAD_CAIRNS, 10, SONG_CHAMBER, 10, CLEFT_CAVE_LORE, 0);

    // 旅の覚書 (一章) が解くレシピ。P5 のレシピの ID の予約 (存在しない ID は WayfarerNotes が無視する)
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
