package jp.revati.skyabyss.dungeon;

import java.util.Map;

import jp.revati.skyabyss.item.extra.Element;
import jp.revati.skyabyss.world.continent.ContinentMap;

// 地下のダンジョン四つ (docs/design/55 の 1・3 章)。巨神の土地に一つずつ。
//   層       FLOORS = 一層目・二層目・主の間 (data/skyabyss/dungeon/<id>_<1|2|boss>.json)
//   敵       層の印 foe_<部屋>_<n>_<種> の種 → 敵 (foes)。種の半分ほどはダンジョン専用の敵 (55 の 4 の 7)、残りは土地の敵。
//            key のついた敵は扉の鍵を持つ。mid は中ボス (ダンジョン専用。名づきの大物と同じ扱い)
//   主       boss (dungeon/mob の主。55 の 1 章の形)
//   入口     大陸の見どころ dungeon_<id> (data/skyabyss/continent/pois.json。入口の間は本物の地下)
// 小ダンジョン (mini。2026-11-07 の声「メインクエストの小さなダンジョンを中ダンジョンのように tp で組ごとに」): 章の跡地の小さなダンジョン 6 つ。
//   id = 章の場所の要素の id (地上の口は ChapterSites の要素のまま。口の門から組ごとの写しへ。dungeon/MiniDungeons)。
//   層は「部屋 3 つの層」と「主の間」の二つ (<id>_1 / <id>_boss)。扉は前の部屋の敵を全部倒すと開く (door_<n>_clear<部屋>)。
//   主は毎回出る。越えた報いは章の場所のもの (SiteSpawner の初回の宝・経験値・合図 site_clear:<id>・頁)。日替わりの様子・深く潜る・装飾品はない
public enum Dungeon {
    BONE_PIT("bone_pit", ContinentMap.WHALE, 10, Element.STONE, "skyabyss:pit_foreman", "skyabyss:bone_centipede",
            Map.of("footman", "skyabyss:pit_digger", "bowman", "skyabyss:rib_archer", "miner", "skyabyss:bone_roller",
                    "standard", "skyabyss:bone_standard", "hauler", "skyabyss:bone_hauler")),
    TIDE_CAVE("tide_cave", ContinentMap.TIDE, 16, Element.TIDE, "skyabyss:bell_hermit", "skyabyss:shell_octopus",
            Map.of("scuttler", "skyabyss:cave_jelly", "clam", "skyabyss:tidepool_clam", "hermit", "skyabyss:drowned_sailor",
                    "angler", "skyabyss:mist_angler", "warden", "skyabyss:shore_warden")),
    STORM_HOLLOW("storm_hollow", ContinentMap.STORM, 22, Element.WIND, "skyabyss:geode_golem", "skyabyss:crystal_lizard",
            Map.of("puff", "skyabyss:crystal_eye", "gleaner", "skyabyss:lightning_gleaner", "wolf", "skyabyss:horn_beetle",
                    "warden", "skyabyss:scorched_warden", "fledgling", "skyabyss:peak_fledgling")),
    MOLTEN_MINE("molten_mine", ContinentMap.EMBER, 28, Element.ASH, "skyabyss:ox_smith", "skyabyss:forge_giant",
            Map.of("mite", "skyabyss:lava_slug", "husk", "skyabyss:bellows_imp", "dragger", "skyabyss:ash_dragger",
                    "thrall", "skyabyss:elder_thrall")),
    // ---- 奥のダンジョン (外れの帯。issue #19 の ②。tools/gen/world/dungeons_deep.py・dungeons_deep2.py) ----
    // 層は中ダンジョンと同じ一層・二層・主の間。二層の終わりの中ボスは土地の名のある強敵 (2026-11-09 の声「ボスの前の中ボス」「1層と2層に」)
    DEEP_FORT("deep_fort", ContinentMap.WHALE, 15, Element.STONE, "skyabyss:rock_boar", "skyabyss:fort_warlord",
            Map.of("bandit", "skyabyss:bandit", "archer", "skyabyss:bandit_archer", "chief", "skyabyss:bandit_chief", "dog", "skyabyss:feral_dog"), false, true),
    DEEP_TEMPLE("deep_temple", ContinentMap.TIDE, 21, Element.TIDE, "skyabyss:tide_crab", "skyabyss:temple_keeper",
            Map.of("thrall", "skyabyss:coral_thrall", "sailor", "skyabyss:drowned_sailor", "angler", "skyabyss:mist_angler", "warden", "skyabyss:shore_warden",
                    "jelly", "skyabyss:cave_jelly"), false, true),
    DEEP_ICE("deep_ice", ContinentMap.STORM, 27, Element.WIND, "skyabyss:storm_ram", "skyabyss:ice_lord",
            Map.of("tortoise", "skyabyss:crag_tortoise", "jelly", "skyabyss:storm_jelly", "slinger", "skyabyss:bolt_slinger", "wolf", "skyabyss:thunder_wolf",
                    "warden", "skyabyss:scorched_warden"), false, true),
    DEEP_LAVA("deep_lava", ContinentMap.EMBER, 33, Element.ASH, "skyabyss:ash_lizard", "skyabyss:lava_wyrm",
            Map.of("scorpion", "skyabyss:basalt_scorpion", "spitter", "skyabyss:ember_spitter", "jackal", "skyabyss:ember_jackal", "whirl", "skyabyss:ash_whirl",
                    "thrall", "skyabyss:elder_thrall"), false, true),
    // ---- 小ダンジョン (章の跡地) ----
    MINI_BONE_PIT("ch1_bone_pit", ContinentMap.WHALE, 6, Element.STONE, null, "skyabyss:pit_foreman",
            Map.of("crawler", "skyabyss:moss_crawler", "beetle", "skyabyss:rock_shell_beetle", "archer", "skyabyss:rib_archer",
                    "hauler", "skyabyss:bone_hauler"), true),
    MINI_CLEFT_CAVE("ch1_cleft_shell_cave", ContinentMap.WHALE, 8, Element.STONE, null, "skyabyss:elder_sentry",
            Map.of("beetle", "skyabyss:rock_shell_beetle", "hare", "skyabyss:spine_hare", "archer", "skyabyss:rib_archer",
                    "crawler", "skyabyss:moss_crawler"), true),
    MINI_SALT_VAULT("ch2_salt_vault", ContinentMap.TIDE, 14, Element.TIDE, null, "skyabyss:shore_warden",
            Map.of("clam", "skyabyss:tidepool_clam", "burrower", "skyabyss:sand_burrower", "scuttler", "skyabyss:reef_scuttler",
                    "thrall", "skyabyss:coral_thrall"), true),
    MINI_ROD_TOWER("storm_ruin", ContinentMap.STORM, 19, Element.WIND, null, "skyabyss:feather_warden",
            Map.of("puff", "skyabyss:static_puff", "fledgling", "skyabyss:peak_fledgling", "gleaner", "skyabyss:lightning_gleaner"), true),
    MINI_SCORCHED_WATCH("ch3_scorched_watch", ContinentMap.STORM, 20, Element.WIND, null, "skyabyss:scorched_warden",
            Map.of("puff", "skyabyss:static_puff", "fledgling", "skyabyss:peak_fledgling", "gleaner", "skyabyss:lightning_gleaner"), true),
    MINI_FORGE_PIT("ch4_dead_forge_pit", ContinentMap.EMBER, 26, Element.ASH, null, "skyabyss:elder_thrall",
            Map.of("mite", "skyabyss:cinder_mite", "husk", "skyabyss:ash_husk"), true);

    // 中ダンジョンの層 (一層目・二層目・主の間) と、小ダンジョンの層 (部屋の層・主の間)
    public static final String[] FLOORS = {"1", "2", "boss"};
    public static final String[] MINI_FLOORS = {"1", "boss"};
    // 中ダンジョンの主の間の番 (小ダンジョンは bossFloor())
    public static final int BOSS_FLOOR = 2;
    // いちばん多い層の数 (写しの区画の大きさ)
    public static final int MAX_FLOORS = 3;

    public final String id;
    public final int region;
    public final int level;
    public final Element element;
    public final String midBoss;
    public final String boss;
    public final Map<String, String> foes;
    public final boolean mini;
    // 奥のダンジョン (入口は外れの帯の見どころ。層と報いは中ダンジョンと同じ)
    public final boolean deep;

    Dungeon(String id, int region, int level, Element element, String midBoss, String boss, Map<String, String> foes) {
        this(id, region, level, element, midBoss, boss, foes, false, false);
    }

    Dungeon(String id, int region, int level, Element element, String midBoss, String boss, Map<String, String> foes, boolean mini) {
        this(id, region, level, element, midBoss, boss, foes, mini, false);
    }

    Dungeon(String id, int region, int level, Element element, String midBoss, String boss, Map<String, String> foes, boolean mini, boolean deep) {
        this.deep = deep;
        this.id = id;
        this.region = region;
        this.level = level;
        this.element = element;
        this.midBoss = midBoss;
        this.boss = boss;
        this.foes = foes;
        this.mini = mini;
    }

    public String[] floors() {
        return mini ? MINI_FLOORS : FLOORS;
    }

    public int bossFloor() {
        return floors().length - 1;
    }

    public String terrain(int floor) {
        return id + "_" + floors()[floor];
    }

    // 中ダンジョン (見どころの入口のあるもの) だけ
    public static java.util.List<Dungeon> mids() {
        java.util.List<Dungeon> out = new java.util.ArrayList<>();
        for (Dungeon d : values()) {
            if (!d.mini) {
                out.add(d);
            }
        }
        return out;
    }

    // 小ダンジョンだけ
    public static java.util.List<Dungeon> minis() {
        java.util.List<Dungeon> out = new java.util.ArrayList<>();
        for (Dungeon d : values()) {
            if (d.mini) {
                out.add(d);
            }
        }
        return out;
    }

    // 入口の見どころの種類 (pois.json の kind)
    public String poiKind() {
        return "dungeon_" + id;
    }

    public String nameKey() {
        return mini ? "dungeon.skyabyss.mini." + id : "dungeon.skyabyss." + id;
    }

    public static Dungeon byId(String id) {
        for (Dungeon d : values()) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return null;
    }

    public static Dungeon byPoiKind(String kind) {
        return kind.startsWith("dungeon_") ? byId(kind.substring("dungeon_".length())) : null;
    }
}
