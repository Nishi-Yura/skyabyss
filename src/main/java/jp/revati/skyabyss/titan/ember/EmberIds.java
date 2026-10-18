package jp.revati.skyabyss.titan.ember;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

// 第四の巨神「余燼の巨神」の ID の一覧 (docs/design/13_ember.md の 11.2節の「ID の約束」)。
// 余燼のすべての担当 (A 地上・B 体と広間・C1 灰の道・C2 鍛えの間・C3 火守り・D 敵・E 鍛え手・F 心臓・G 品と伝承) は、
// 登録の名前・音の名前・lang の鍵の頭をここから取る。新しい ID が要るときは、ここに足してから自分の Setup で登録する
// (自分の Setup で勝手に名前を作ると、ほかの担当と同じ名前を二度登録して起動が止まる。潮殻の心臓のときの注記と同じ問題)。
//
// 名前はすべて skyabyss の名前空間 (例: skyabyss:ash_thrall)。ここは文字列だけで、登録はしない。
// 各定数の右の「→ X」は、その ID を登録する担当 (13 の 11.2節)。登録した担当だけが DeferredHolder を持つ。
public final class EmberIds {
    private EmberIds() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    // ================================================================ 巨神・ディメンション (→ 0 骨組み)
    // 物語の進みの名前 (progress/Titans.EMBER、TitanWorldData の Titans/ember、TitanMemoryData の天井のカウンタ)
    public static final String TITAN = "ember";
    // 背中のディメンション「余燼の背」(data/skyabyss/dimension/ember_back.json)。灰の原は y 63 まで
    public static final String BACK_NAME = "ember_back";
    public static final ResourceKey<Level> BACK = ResourceKey.create(Registries.DIMENSION, id(BACK_NAME));
    public static final ResourceKey<Biome> BACK_BIOME = ResourceKey.create(Registries.BIOME, id(BACK_NAME));
    // 体の形の表 (tools/gen_ember_back_body.py が書く。読むのは TitanBodyBlocks。→ B)
    public static final String BODY_BIN = "/data/skyabyss/ember_back/body.bin";

    // ================================================================ 傷の種類 (data/skyabyss/damage_type。どれも #minecraft:is_fire に入れない。→ 0 骨組み)
    // 焦げ (熱 100 のあいだ 1 秒に 2)。防具を通る (#bypasses_armor)。火炎耐性では防げない
    public static final ResourceKey<DamageType> HEAT = damage("heat");
    // 熱の噴き: 噴気の割れ目 (火守り)、鍛え手の亀裂の噴気、心臓の熱の技のうち「地形から噴く」もの。防具で減る
    public static final ResourceKey<DamageType> HEAT_BURST = damage("heat_burst");
    // 呑まれた: 溶岩の湖・熾の底の溝・欠けた区画に落ちた (溶岩の傷にしない。品が燃えない)。防具を通る
    public static final ResourceKey<DamageType> ENGULFED = damage("engulfed");

    private static ResourceKey<DamageType> damage(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, id(path));
    }

    // ================================================================ エンティティ
    public static final String EMBER_TITAN = "ember_titan";         // → A 巨神本体 (地の中を泳ぐ・夜に眠る・息)
    public static final String EMBER_MOUTH = "ember_mouth";         // → A 熱の口 (見えない入口の判定)
    public static final String ASH_THRALL = "ash_thrall";           // → D 灰被り (火守りの「灰運び」も同じ型)
    public static final String EMBER_MOTH = "ember_moth";           // → D 火の粉蛾
    public static final String CINDER_ROLLER = "cinder_roller";     // → D 燠虫
    public static final String ASH_SMITH = "ash_smith";             // → E 灰の鍛え手 (中ボス)
    public static final String EMBER_HEART = "ember_heart";         // → F 余燼の心臓 (最終ボス)
    public static final String ASH_PELLET = "ash_pellet";           // → F 灰の礫 (心臓の段階 2、人を追う遅い礫)
    // スポーンエッグ (クリエイティブのタブとテスト用。→ D・E・F がそれぞれ)
    public static final String ASH_THRALL_EGG = "ash_thrall_spawn_egg";
    public static final String EMBER_MOTH_EGG = "ember_moth_spawn_egg";
    public static final String CINDER_ROLLER_EGG = "cinder_roller_spawn_egg";

    // ================================================================ ブロック
    public static final String COOLED_CRUST = "cooled_crust";       // → C1 冷え殻 (灰の道。熾の底の区画の床も F がこれを使う)
    public static final String ASH_URN_STAND = "ash_urn_stand";     // → C1 灰の壺の台
    public static final String ASH_FORGE = "ash_forge";             // → C2 大炉 (炉の熱 0〜100、塊の色)
    public static final String FORGE_BELLOWS = "forge_bellows";     // → C2 鞴の踏み板
    public static final String ASH_ANVIL = "ash_anvil";             // → C2 金床 (刻みの色の印)
    public static final String QUENCH_TROUGH = "quench_trough";     // → C2 焼き入れの槽 (鍛え手の間の槽も E が置く)
    public static final String EMBER_HEARTH = "ember_hearth";       // → C3 余燼の火床
    public static final String HEAT_VENT = "heat_vent";             // → C3 噴気の割れ目 (鍛え手の亀裂は E が粒と判定で出す)
    public static final String KINDLE_STONE = "kindle_stone";       // → B 熾り石 (三つ。ResonanceStoneBlock の見た目違い)
    public static final String CINDER_LAMP = "cinder_lamp";         // → G 燠の灯 (光 12 の吊り灯。余裕があれば)
    // 封印 (鍛え手の扉・熾の底の門・熾の底の入口) は共通の SealedGateBlock、灰溜まりは灰色の羊毛 (バニラ) を使う

    // ================================================================ アイテム
    // 試練の品 (背中の外へ持ち出せない)
    public static final String ASH_URN = "ash_urn";                 // → C1 灰の壺 (灰 5 回分)
    public static final String FIRE_KEY_HOT = "fire_key_hot";       // → C2 焼き入れ前の火の鍵 (10 秒で冷えて割れる)
    public static final String FIRE_KEY = "fire_key";               // → C2 焼き入れた火の鍵 (鍛え手の扉に差す)
    public static final String LIVE_COAL = "live_coal";             // → C3 熾 (火床に +8)
    // 地上
    public static final String WARM_STONE = "warm_stone";           // → A 温石 (巨神の向き・距離、眠る場所を頼む)
    // 素材
    public static final String EMBER_SHARD = "ember_shard";         // → G 余燼の燠石
    public static final String EMBER_CORE = "ember_core";           // → G 余燼の心核
    public static final String SPARK_DUST = "spark_dust";           // → G 火の粉の粉 (蛾から)
    public static final String CINDER_HUSK = "cinder_husk";         // → G 燠の殻 (燠虫から)
    public static final String EMBER_HORN = "ember_horn";           // → G 熾火の角杯 (食べ物)
    // 図鑑の 10 品 (6.3節)
    public static final String EMBER_HELMET = "ember_helmet";       // → G 余燼の鎧
    public static final String EMBER_CHESTPLATE = "ember_chestplate";
    public static final String EMBER_LEGGINGS = "ember_leggings";
    public static final String EMBER_BOOTS = "ember_boots";
    public static final String EMBER_WAR_AXE = "ember_war_axe";     // → G 余燼の戦斧
    public static final String EMBER_MANTLE = "ember_mantle";       // → G 熾火の外套 (装飾の枠)
    public static final String KINDLING_CHARM = "kindling_charm";   // → G 熾りの護符 (護符の枠)
    public static final String SMITH_HAMMER = "smith_hammer";       // → G 鍛え手の大鎚 (専用伝説。E のモデルと同じ形)
    public static final String THRALL_POKER = "thrall_poker";       // → G 灰被りの火掻き (専用伝説。D のモデルと同じ形)
    public static final String EMBER_LANTERN = "ember_lantern";     // → G 余燼の灯 (専用伝説、装飾の枠)
    // 図鑑の並び (EmberLoot・記録の画面・自己テストが使う。順は 6.3節の表)
    public static final List<String> CODEX = List.of(EMBER_HELMET, EMBER_CHESTPLATE, EMBER_LEGGINGS, EMBER_BOOTS, EMBER_WAR_AXE, EMBER_MANTLE,
            KINDLING_CHARM, SMITH_HAMMER, THRALL_POKER, EMBER_LANTERN);
    // 初回の選択の専用伝説三つ
    public static final List<String> FIRST_CLEAR_CHOICES = List.of(SMITH_HAMMER, THRALL_POKER, EMBER_LANTERN);
    // 天の鍵の欠片は共通の ModItems.SKY_KEY_FRAGMENT (四つ目)。天の鍵は story.SkyKey がそろえる (14 の 4章)

    // ================================================================ 音 (SoundEvent の名前。assets/skyabyss/sounds.json の鍵も同じ。音の絵は tools/gen_ember_sounds.py → A)
    // 0 骨組み (熱)
    public static final String SOUND_HEAT_WARN = "ember_heat_warn";         // 熱 85 を超えた (一度)
    public static final String SOUND_SCORCH = "ember_scorch";               // 焦げの傷
    // A 地上
    public static final String SOUND_RUMBLE = "ember_rumble";               // 地鳴り (潜行・浮かぶ・沈む)
    public static final String SOUND_BREATH_IN = "ember_breath_in";
    public static final String SOUND_BREATH_OUT = "ember_breath_out";
    public static final String SOUND_SWALLOW = "ember_swallow";             // 口へ渡る・口から出る
    public static final String SOUND_WALL_SCRAPE = "ember_wall_scrape";     // 洞窟の壁の向こうを通る音
    public static final String SOUND_FAR_BEAT = "ember_far_beat";           // 遠い鼓動
    public static final String SOUND_WORLD_TURN = "ember_world_turn";       // 1 章の締めの夜明けの地鳴り (9.4節)
    // B 体と広間
    public static final String SOUND_BACK_AMBIENT = "ember_back_ambient";   // 遠い溶岩と風
    public static final String SOUND_STONE_RING = "ember_stone_ring";       // 熾り石が鳴る
    public static final String SOUND_FORGE_DIM = "ember_forge_dim";         // 広間の炉の火が落ちる (三度)
    // C1 灰の道
    public static final String SOUND_ASH_SCATTER = "ember_ash_scatter";
    public static final String SOUND_CRUST_CRACK = "ember_crust_crack";
    public static final String SOUND_CRUST_SINK = "ember_crust_sink";
    public static final String SOUND_ENGULF = "ember_engulf";               // 呑まれた (C1・F が使う)
    // C2 鍛えの間
    public static final String SOUND_BELLOWS = "ember_bellows";
    public static final String SOUND_ANVIL_STRIKE = "ember_anvil_strike";
    public static final String SOUND_ANVIL_TING = "ember_anvil_ting";       // 打てる色に入った瞬間の「ちん」
    public static final String SOUND_BLOOM_CRACK = "ember_bloom_crack";
    public static final String SOUND_QUENCH = "ember_quench";               // 焼き入れ (E の鍛え手の焼き入れも使う)
    public static final String SOUND_DOOR_KINDLE = "ember_door_kindle";     // 鍛え手の扉の文様が灯る
    // C3 火守り
    public static final String SOUND_VENT_HISS = "ember_vent_hiss";         // 噴く 1.5 秒前 (E の亀裂の予告も使う)
    public static final String SOUND_VENT_BURST = "ember_vent_burst";
    public static final String SOUND_HEARTH_FEED = "ember_hearth_feed";
    public static final String SOUND_HEARTH_DOUSE = "ember_hearth_douse";
    // D 敵
    public static final String SOUND_THRALL_AMBIENT = "ash_thrall_ambient";
    public static final String SOUND_THRALL_HURT = "ash_thrall_hurt";
    public static final String SOUND_THRALL_DEATH = "ash_thrall_death";
    public static final String SOUND_THRALL_SHELL_BREAK = "ash_thrall_shell_break";
    public static final String SOUND_THRALL_IGNITE = "ash_thrall_ignite";
    public static final String SOUND_MOTH_FLUTTER = "ember_moth_flutter";
    public static final String SOUND_MOTH_DEATH = "ember_moth_death";
    public static final String SOUND_ROLLER_CURL = "cinder_roller_curl";
    public static final String SOUND_ROLLER_ROLL = "cinder_roller_roll";
    public static final String SOUND_ROLLER_BONK = "cinder_roller_bonk";
    public static final String SOUND_ROLLER_HURT = "cinder_roller_hurt";
    public static final String SOUND_ROLLER_DEATH = "cinder_roller_death";
    // E 灰の鍛え手
    public static final String SOUND_SMITH_SLAM = "ash_smith_slam";
    public static final String SOUND_SMITH_INHALE = "ash_smith_inhale";
    public static final String SOUND_SMITH_BLAST = "ash_smith_blast";
    public static final String SOUND_SMITH_SPARKS = "ash_smith_sparks";
    public static final String SOUND_SMITH_RAGE = "ash_smith_rage";
    public static final String SOUND_SMITH_HURT = "ash_smith_hurt";
    public static final String SOUND_SMITH_DEATH = "ash_smith_death";
    // F 余燼の心臓
    public static final String SOUND_HEART_BEAT = "ember_heart_beat";
    public static final String SOUND_HEART_ROAR = "ember_heart_roar";
    public static final String SOUND_HEART_BREATH = "ember_heart_breath";   // 火の息
    public static final String SOUND_HEART_PULSE = "ember_heart_pulse";     // 熱の脈
    public static final String SOUND_HEART_ORB = "ember_heart_orb";         // 熔け玉
    public static final String SOUND_HEART_FLOOR = "ember_heart_floor";     // 崩れる床
    public static final String SOUND_HEART_GALE = "ember_heart_gale";       // 鞴の嵐
    public static final String SOUND_HEART_PELLET = "ember_heart_pellet";   // 灰の礫
    public static final String SOUND_HEART_CHILL = "ember_heart_chill";     // 冷え (段階 3 に入る)
    public static final String SOUND_HEART_EMBERS = "ember_heart_embers";   // 残り火
    public static final String SOUND_HEART_FADING = "ember_heart_fading";   // 消えかけの鼓動
    public static final String SOUND_HEART_ECHO = "ember_heart_echo";       // 下から返る拍 (15 章の判断 5)
    public static final String SOUND_HEART_VEIL = "ember_heart_veil";       // 灰の帳
    public static final String SOUND_HEART_DEATH = "ember_heart_death";

    // ================================================================ 休み場 (7.3節。EmberBackLayout.RESTS の名前。→ B)
    public static final List<String> RESTS = List.of("throat", "hub", "w0", "w1", "e0", "s0", "heart");
    // 試練の名前 (眠る試練・石・コマンド)
    public static final String WEST = "west";
    public static final String EAST = "east";
    public static final String SOUTH = "south";
    public static final List<String> TRIALS = List.of(WEST, EAST, SOUTH);

    // ================================================================ 碑文 (9.2節。看板の文の鍵は sign.skyabyss.<ID>.1〜4、伝承は chapter1/inscription_<ID の lore_ を除いた残り>。→ B が置く、G が文)
    public static final List<String> INSCRIPTIONS = List.of("lore_ember_mouth", "lore_ember_hub", "lore_ember_west", "lore_ember_east",
            "lore_ember_south", "lore_ember_heart");

    // ================================================================ 伝承の書 (9.3節。→ G。chapter1/ember は 14 の 5.2節の約束の仮の項目で、A が見つけたときに記す)
    public static final String LORE_FOUND = "chapter1/ember";
    public static final String LORE_TITAN = "chapter1/ember_titan";
    public static final String LORE_RUINS = "chapter1/ember_ruins";
    public static final String LORE_SMITH = "chapter1/ash_smith";
    public static final String LORE_STILLED = "chapter1/ember_stilled";
    public static final String LORE_LEDGER = "chapter1/forge_ledger";

    // ================================================================ lang の鍵の頭 (assets/skyabyss_ember/lang/{ja_jp,en_us}.json。
    // 同じファイルを複数の担当が直すので、自分の頭の行は自分の区切りの行 ("__ember.X": "") の下にまとめる)
    public static final String LANG_HEAT = "message.skyabyss.ember.heat.";            // 0 熱の知らせ
    public static final String LANG_HEAT_GUI = "gui.skyabyss.ember_heat.";           // B 熱の目盛り
    public static final String LANG_SURFACE = "message.skyabyss.ember.surface.";      // A 地上・乗り込み・温石
    public static final String LANG_COMMANDS = "commands.skyabyss.ember.";           // A・B のテスト用コマンド
    public static final String LANG_BACK = "message.skyabyss.ember.back.";            // B 背中・休み場・リセット・眠る試練
    public static final String LANG_WHISPER = "message.skyabyss.ember.whisper.";      // B 一度だけの囁き
    public static final String LANG_WEST = "message.skyabyss.ember.trial.west.";      // C1
    public static final String LANG_EAST = "message.skyabyss.ember.trial.east.";      // C2
    public static final String LANG_SOUTH = "message.skyabyss.ember.trial.south.";    // C3
    public static final String LANG_MOB = "message.skyabyss.ember.mob.";              // D
    public static final String LANG_SMITH = "message.skyabyss.ember.smith.";          // E
    public static final String LANG_HEART = "message.skyabyss.ember.heart.";          // F
    public static final String LANG_TOOLTIP = "tooltip.skyabyss.ember.";              // G
    // 品・ブロック・エンティティの名前はバニラの決まり (item.skyabyss.<ID>、block.skyabyss.<ID>、entity.skyabyss.<ID>) で、登録した担当が書く
    // 傷の死亡文: death.attack.skyabyss.heat、heat_burst、engulfed (と .player)。→ 0
    // 地上へ戻る・着いたときの題: title.skyabyss.ember_back(.sub)。→ A
}
