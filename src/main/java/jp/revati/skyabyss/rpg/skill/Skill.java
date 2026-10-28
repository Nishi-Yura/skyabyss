package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;

// 技の一覧 (36 本)。木ごとに 4 行 (tier 0..3、Lv 1・5・12・20)。
//   1 行目: 左 (使う技) と右 (常に効く技)。前提なし
//   2 行目: 真上の技が要る
//   3 行目: 奥義の使う技 (2 行目のどちらかが要る)。真ん中
//   4 行目: 要 (かなめ。癖のある常に効く技。3 行目が要る。段は 1)
// 数値は SkillRules、表と理由は docs/design/04_growth_and_skills.md と 27_builds_and_classes.md。
// 保存には id (文字列) を使う。並びを変えても記録は壊れない (前の版の 10 の技は id をそのまま木だけ移した)
public enum Skill {
    // ---------------------------------------------------------------- 岩守り (筋力・耐久 ／ 壁・近接)
    TITAN_CLEAVE("titan_cleave", SkillTree.ROCK, Kind.ACTIVE, 3, 0, 0),
    RIPOSTE("riposte", SkillTree.ROCK, Kind.PASSIVE, 3, 0, 1),
    ROCK_CALL("rock_call", SkillTree.ROCK, Kind.ACTIVE, 2, 1, 0, "titan_cleave"),
    STONE_STANCE("stone_stance", SkillTree.ROCK, Kind.PASSIVE, 3, 1, 1, "riposte"),
    WHALE_SWEEP("whale_sweep", SkillTree.ROCK, Kind.ACTIVE, 2, 2, 2, "rock_call", "stone_stance"),
    STONE_OATH("stone_oath", SkillTree.ROCK, Kind.KEYSTONE, 1, 3, 2, "whale_sweep"),
    // ---------------------------------------------------------------- 風読み (敏捷 ／ 機動・短い間合いの攻め)
    LUNGE("lunge", SkillTree.WIND, Kind.ACTIVE, 2, 0, 0),
    NIMBLE("nimble", SkillTree.WIND, Kind.PASSIVE, 3, 0, 1),
    BACKSTEP("backstep", SkillTree.WIND, Kind.ACTIVE, 2, 1, 0, "lunge"),
    FORESIGHT("foresight", SkillTree.WIND, Kind.PASSIVE, 3, 1, 1, "nimble"),
    WIND_REND("wind_rend", SkillTree.WIND, Kind.ACTIVE, 2, 2, 2, "backstep", "foresight"),
    LONE_WIND("lone_wind", SkillTree.WIND, Kind.KEYSTONE, 1, 3, 2, "wind_rend"),
    // ---------------------------------------------------------------- 潮歌い (精神 ／ 支え・崩し)
    WARD_SONG("ward_song", SkillTree.TIDE, Kind.ACTIVE, 3, 0, 0),
    CALM_HEART("calm_heart", SkillTree.TIDE, Kind.PASSIVE, 3, 0, 1),
    EBB_TIDE("ebb_tide", SkillTree.TIDE, Kind.ACTIVE, 2, 1, 0, "ward_song"),
    RIPPLE("ripple", SkillTree.TIDE, Kind.PASSIVE, 3, 1, 1, "calm_heart"),
    CYCLE_SONG("cycle_song", SkillTree.TIDE, Kind.ACTIVE, 2, 2, 2, "ebb_tide", "ripple"),
    CHORUS_VOICE("chorus_voice", SkillTree.TIDE, Kind.KEYSTONE, 1, 3, 2, "cycle_song"),
    // ---------------------------------------------------------------- 灰の鍛え人 (筋力・精神 ／ 近接の攻め・火)
    TEMPER("temper", SkillTree.ASH, Kind.ACTIVE, 3, 0, 0),
    EMBER_HEART("ember_heart", SkillTree.ASH, Kind.PASSIVE, 3, 0, 1),
    EMBER_HAMMER("ember_hammer", SkillTree.ASH, Kind.ACTIVE, 2, 1, 0, "temper"),
    FORGE_MEMORY("forge_memory", SkillTree.ASH, Kind.PASSIVE, 3, 1, 1, "ember_heart"),
    ASH_SCATTER("ash_scatter", SkillTree.ASH, Kind.ACTIVE, 2, 2, 2, "ember_hammer", "forge_memory"),
    SMITH_EYE("smith_eye", SkillTree.ASH, Kind.KEYSTONE, 1, 3, 2, "ash_scatter"),
    // ---------------------------------------------------------------- 骨の射手 (敏捷 ／ 遠い間合いの攻め・罠)
    CLOUD_PIERCE("cloud_pierce", SkillTree.BONE, Kind.ACTIVE, 3, 0, 0),
    FAR_SHOT("far_shot", SkillTree.BONE, Kind.PASSIVE, 3, 0, 1),
    BONE_TRAP("bone_trap", SkillTree.BONE, Kind.ACTIVE, 2, 1, 0, "cloud_pierce"),
    HAWK_EYE("hawk_eye", SkillTree.BONE, Kind.PASSIVE, 3, 1, 1, "far_shot"),
    SKY_RAIN("sky_rain", SkillTree.BONE, Kind.ACTIVE, 2, 2, 2, "bone_trap", "hawk_eye"),
    STILL_STRING("still_string", SkillTree.BONE, Kind.KEYSTONE, 1, 3, 2, "sky_rain"),
    // ---------------------------------------------------------------- 淵 (B09。転生 1 回で入口、5 回で全段。混ぜる前提の木)
    DEEP_PULL("deep_pull", SkillTree.DEEP, Kind.ACTIVE, 2, 0, 0),
    DEEP_MIRROR("deep_mirror", SkillTree.DEEP, Kind.PASSIVE, 3, 0, 1),
    DEEP_ECHO("deep_echo", SkillTree.DEEP, Kind.ACTIVE, 2, 1, 0, "deep_pull"),
    DEEP_TITHE("deep_tithe", SkillTree.DEEP, Kind.PASSIVE, 3, 1, 1, "deep_mirror"),
    DEEP_MAW("deep_maw", SkillTree.DEEP, Kind.ACTIVE, 2, 2, 2, "deep_echo", "deep_tithe"),
    DEEP_OATH("deep_oath", SkillTree.DEEP, Kind.KEYSTONE, 1, 3, 2, "deep_maw"),
    // ---------------------------------------------------------------- 前世の技 (B08。転生の回数で開く。点は別枠。前提なし)
    ECHO_STRIKE("echo_strike", SkillTree.PAST, Kind.ACTIVE, 2, 0, 0),
    OLD_VIGIL("old_vigil", SkillTree.PAST, Kind.PASSIVE, 2, 0, 1),
    GRAVE_BELL("grave_bell", SkillTree.PAST, Kind.ACTIVE, 2, 1, 0),
    MEMORY_EDGE("memory_edge", SkillTree.PAST, Kind.PASSIVE, 2, 1, 1),
    FINAL_LIGHT("final_light", SkillTree.PAST, Kind.ACTIVE, 2, 2, 2),
    SOUL_THREAD("soul_thread", SkillTree.PAST, Kind.KEYSTONE, 1, 3, 2),
    // ---------------------------------------------------------------- 星読み (魔法使い。精神 ／ 遠い魔法の攻め。docs/design/43)
    THUNDER_BOLT("thunder_bolt", SkillTree.STAR, Kind.ACTIVE, 3, 0, 0),
    STAR_READING("star_reading", SkillTree.STAR, Kind.PASSIVE, 3, 0, 1),
    EMBER_ORB("ember_orb", SkillTree.STAR, Kind.ACTIVE, 2, 1, 0, "thunder_bolt"),
    FROST_TIDE("frost_tide", SkillTree.STAR, Kind.ACTIVE, 2, 1, 1, "star_reading"),
    BLINK("blink", SkillTree.STAR, Kind.ACTIVE, 2, 2, 2, "ember_orb", "frost_tide"),
    STAR_OATH("star_oath", SkillTree.STAR, Kind.KEYSTONE, 1, 3, 2, "blink"),
    // ---------------------------------------------------------------- 職の 4 つ目の技 (3 行目の左右。docs/design/43 の 2 節)
    FORGE_STOMP("forge_stomp", SkillTree.ASH, Kind.ACTIVE, 2, 2, 1, "forge_memory"),
    MIST_VEIL("mist_veil", SkillTree.WIND, Kind.ACTIVE, 2, 2, 1, "foresight"),
    CURSE_MARK("curse_mark", SkillTree.TIDE, Kind.ACTIVE, 2, 2, 0, "ebb_tide"),
    LAMP_CHARM("lamp_charm", SkillTree.TIDE, Kind.ACTIVE, 2, 2, 1, "ripple"),
    // 戦士・弓使いの 4 つ目は、風読みの踏み込み・跳び退きと同じ動き (刻印は付かない)
    SHIELD_RUSH("shield_rush", SkillTree.ROCK, Kind.ACTIVE, 2, 2, 1, "stone_stance"),
    ARCHER_LEAP("archer_leap", SkillTree.BONE, Kind.ACTIVE, 2, 2, 1, "hawk_eye"),
    // 技の木 v2 (docs/design/48) で足した使う技 (4 本の枠から選ぶための増やし)。絵は icons2.png の 37 番から
    SHIELD_BASH("shield_bash", SkillTree.ROCK, Kind.ACTIVE, 2, 1, 2, "titan_cleave"),
    BONE_SWING("bone_swing", SkillTree.ROCK, Kind.ACTIVE, 2, 2, 0, "titan_cleave"),
    SPIN_CUT("spin_cut", SkillTree.ROCK, Kind.ACTIVE, 2, 2, 2, "titan_cleave"),
    TWIN_FANG("twin_fang", SkillTree.WIND, Kind.ACTIVE, 2, 1, 2, "lunge"),
    SPARK_BURST("spark_burst", SkillTree.ASH, Kind.ACTIVE, 2, 1, 2, "temper"),
    SNARE_SHOT("snare_shot", SkillTree.BONE, Kind.ACTIVE, 2, 1, 2, "cloud_pierce"),
    STARDUST("stardust", SkillTree.STAR, Kind.ACTIVE, 2, 1, 2, "thunder_bolt");

    public enum Kind {
        ACTIVE, PASSIVE, KEYSTONE
    }

    // 行ごとの要るレベルは 1・5・12・20 (minLevel)
    public final String id;
    public final SkillTree tree;
    public final Kind kind;
    // true = スロットに置いて使う技 (能動)。false = 覚えるだけで効く技 (常時・要)
    public final boolean active;
    public final int maxRank;
    // 行 (0 = 1 行目) と、木の中の列 (0 = 左、1 = 右、2 = 真ん中)
    public final int tier;
    public final int column;
    // 覚えるのに要るレベル
    public final int minLevel;
    // 前提: このうちどれか一つを覚えていること (空なら前提なし)
    private final String[] requiresAny;

    Skill(String id, SkillTree tree, Kind kind, int maxRank, int tier, int column, String... requiresAny) {
        this.id = id;
        this.tree = tree;
        this.kind = kind;
        this.active = kind == Kind.ACTIVE;
        this.maxRank = maxRank;
        this.tier = tier;
        this.column = column;
        this.minLevel = tree == SkillTree.PAST ? 1 : tier == 0 ? 1 : tier == 1 ? 5 : tier == 2 ? 12 : 20;
        this.requiresAny = requiresAny;
    }

    public boolean keystone() {
        return kind == Kind.KEYSTONE;
    }

    public List<Skill> requires() {
        List<Skill> list = new ArrayList<>();
        for (String r : requiresAny) {
            Skill s = byId(r);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    public String nameKey() {
        return "skill.skyabyss." + id;
    }

    // 段ごとの効果の説明 (引数は SkillRules.describeArgs)
    public String effectKey() {
        return "skill.skyabyss." + id + ".effect";
    }

    // 一行の言い伝え (画面の説明の頭に出す)
    public String flavorKey() {
        return "skill.skyabyss." + id + ".flavor";
    }

    // 型 (武器の型) のおまけの一行 (なければ空の文字。lang の skill.skyabyss.<id>.type)
    public String typeKey() {
        return "skill.skyabyss." + id + ".type";
    }

    // アイコンの位置 (gui/skill/icons.png の 32px の升目。並びは宣言の順)。淵・前世の 12 本は icons2.png (番号 105 から)、
    // 職の新しい技 (星読みから) は icons2.png の 25 番から (17〜24 は木の印と紋。tools/gen/ui/gen_deep_skill_assets.py)
    public int iconIndex() {
        int o = ordinal();
        // 30 未満は icons.png の升目。淵・前世の技 (12 本) は icons2.png の 5 番から (0〜4 は消した橋の技の絵)。職の新しい技は 25 番から
        return o < 30 ? o : o < THUNDER_BOLT.ordinal() ? 105 + (o - 30) : 125 + (o - THUNDER_BOLT.ordinal());
    }

    // 転生の回数がこれ以上で取れる (淵・前世の技。ほかは 0)
    public int rebirthAt() {
        return switch (tree) {
            case DEEP -> new int[] {1, 3, 4, 5}[tier];
            case PAST -> switch (this) {
                case ECHO_STRIKE, OLD_VIGIL -> 1;
                case GRAVE_BELL -> 2;
                case MEMORY_EDGE -> 3;
                case FINAL_LIGHT -> 4;
                default -> 5;
            };
            default -> 0;
        };
    }

    // この技の刻印 (第一段階。ない技は空)
    public List<Rune> runes() {
        return Rune.of(this);
    }

    public static Skill byId(String id) {
        if (id == null) {
            return null;
        }
        for (Skill s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static List<Skill> inTree(SkillTree tree) {
        List<Skill> list = new ArrayList<>();
        for (Skill s : values()) {
            if (s.tree == tree) {
                list.add(s);
            }
        }
        return list;
    }
}
