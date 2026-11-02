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
                    "thrall", "skyabyss:elder_thrall"));

    public static final String[] FLOORS = {"1", "2", "boss"};
    public static final int BOSS_FLOOR = 2;

    public final String id;
    public final int region;
    public final int level;
    public final Element element;
    public final String midBoss;
    public final String boss;
    public final Map<String, String> foes;

    Dungeon(String id, int region, int level, Element element, String midBoss, String boss, Map<String, String> foes) {
        this.id = id;
        this.region = region;
        this.level = level;
        this.element = element;
        this.midBoss = midBoss;
        this.boss = boss;
        this.foes = foes;
    }

    public String terrain(int floor) {
        return id + "_" + FLOORS[floor];
    }

    // 入口の見どころの種類 (pois.json の kind)
    public String poiKind() {
        return "dungeon_" + id;
    }

    public String nameKey() {
        return "dungeon.skyabyss." + id;
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
