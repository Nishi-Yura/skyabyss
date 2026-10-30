package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;

// 刻印 (ルーン)。使う技 1 つにつき 3 択で、技の形が変わる (数字の増減ではなく)。
// 開くのは、その技を極めたとき (段が最大)。1 点で選び、同時に有効なのは 1 つ。付け替えは共鳴の炉で結晶 1 (docs/design/27 の 3.8、5)。
// 第一段階は 10 本 × 3 = 30。ほかの使う技 (巡りの唄・焼き入れ・灰散らし・骨の罠・天の驟雨・合図) は第二段階
public enum Rune {
    // ---- 巨神斬り
    WIDE_BLADE("wide_blade", Skill.TITAN_CLEAVE, 1),
    DEEP_BLADE("deep_blade", Skill.TITAN_CLEAVE, 2),
    GROUND_RUN("ground_run", Skill.TITAN_CLEAVE, 3),
    // ---- 鯨尾薙ぎ
    PUSH_TIDE("push_tide", Skill.WHALE_SWEEP, 1),
    PINCER("pincer", Skill.WHALE_SWEEP, 2),
    DOUBLE_SWEEP("double_sweep", Skill.WHALE_SWEEP, 3),
    // ---- 岩呼び
    LONG_VOICE("long_voice", Skill.ROCK_CALL, 1),
    HEAVY_VOICE("heavy_voice", Skill.ROCK_CALL, 2),
    ROCK_RETURN("rock_return", Skill.ROCK_CALL, 3),
    // ---- 踏み込み
    PASS_THROUGH("pass_through", Skill.LUNGE, 1),
    CHAIN_LUNGE("chain_lunge", Skill.LUNGE, 2),
    PIERCE_THROUGH("pierce_through", Skill.LUNGE, 3),
    // ---- 跳び退き
    RETURN_FIRE("return_fire", Skill.BACKSTEP, 1),
    LONG_LEAP("long_leap", Skill.BACKSTEP, 2),
    WIND_REMNANT("wind_remnant", Skill.BACKSTEP, 3),
    // ---- 風裂き
    BACK_TAKE("back_take", Skill.WIND_REND, 1),
    BLEED("bleed", Skill.WIND_REND, 2),
    COMPANION_WIND("companion_wind", Skill.WIND_REND, 3),
    // ---- 守りの唄
    THICK_SONG("thick_song", Skill.WARD_SONG, 1),
    WIDE_SONG("wide_song", Skill.WARD_SONG, 2),
    CYCLING_SONG("cycling_song", Skill.WARD_SONG, 3),
    // ---- 引き潮
    STRONG_TIDE("strong_tide", Skill.EBB_TIDE, 1),
    WHIRL("whirl", Skill.EBB_TIDE, 2),
    PUSH_BACK("push_back", Skill.EBB_TIDE, 3),
    // ---- 熾火の槌
    LONG_FIRE("long_fire", Skill.EMBER_HAMMER, 1),
    DOUBLE_STRIKE("double_strike", Skill.EMBER_HAMMER, 2),
    FIRE_PILLAR("fire_pillar", Skill.EMBER_HAMMER, 3),
    // ---- 雲穿ち
    THIN_ARROW("thin_arrow", Skill.CLOUD_PIERCE, 1),
    HEAVY_ARROW("heavy_arrow", Skill.CLOUD_PIERCE, 2),
    LIGHTNING("lightning", Skill.CLOUD_PIERCE, 3),
    // ---- 逸品の書き換え (4 番目。その武器を主の手に持つあいだだけ選べて、効く。docs/design/27 の 4.2)
    BONE_PIERCE("pike_bone", Skill.LUNGE, 4, "captain_pike"),
    BITE("fang_bite", Skill.WIND_REND, 4, "waterway_fang"),
    SNIP("shears_cut", Skill.WHALE_SWEEP, 4, "tidecaller_claw"),
    HARPOON_TIE("harpoon_tie", Skill.EBB_TIDE, 4, "coral_harpoon"),
    THUNDER_RING("claw_ring", Skill.BACKSTEP, 4, "raiju_claw"),
    HOOK_PULL("halberd_hook", Skill.ROCK_CALL, 4, "warden_glaive"),
    FIRE_BED("hammer_forge", Skill.EMBER_HAMMER, 4, "smith_hammer"),
    FIRE_PASS("poker_share", Skill.TEMPER, 4, "thrall_poker"),
    // ---- 深淵のアーティファクト (深淵の心臓から。docs/design/31 の B04)。まだ書き換えのない 4 つの技へ
    ABYSS_CLEAVE("abyss_cleave", Skill.TITAN_CLEAVE, 4, "abyss_greatsword"),
    SUNK_SONG("sunk_song", Skill.WARD_SONG, 4, "abyss_bell_staff"),
    SKY_FALL("sky_fall", Skill.CLOUD_PIERCE, 4, "abyss_storm_bow"),
    CREEPING_ASH("creeping_ash", Skill.ASH_SCATTER, 4, "abyss_ash_scythe"),
    // ---- 巨神ごとの 6 職の伝説 (abyss/LegendGear の 20 本。2026-10-27)。技の中身はそのままに、巨神の色の上乗せが続く (rpg/skill/LegendRewrites)。
    // 書き換えのない技は 4、すでに書き換えのある技は 6 (その武器を持っているほうが効く。選ぶ番号は 4 のまま)
    TIDE_WALL("tide_wall", Skill.SHIELD_RUSH, 4, "abyss_tide_sword"),
    STORM_CLEAVE("storm_cleave", Skill.TITAN_CLEAVE, 6, "abyss_storm_sword"),
    EMBER_SWEEP("ember_sweep", Skill.WHALE_SWEEP, 6, "abyss_ember_sword"),
    WHALE_STOMP("whale_stomp", Skill.FORGE_STOMP, 4, "abyss_whale_hammer"),
    TIDE_HAMMER("tide_hammer", Skill.EMBER_HAMMER, 6, "abyss_tide_hammer"),
    STORM_ASH("storm_ash", Skill.ASH_SCATTER, 6, "abyss_storm_hammer"),
    STORM_BOLT("storm_bolt", Skill.THUNDER_BOLT, 4, "abyss_storm_staff"),
    SPLIT_EMBER("split_ember", Skill.EMBER_ORB, 4, "abyss_ember_staff"),
    HIGH_TIDE("high_tide", Skill.FROST_TIDE, 4, "abyss_tide_staff"),
    STONE_DOUBLE("stone_double", Skill.BLINK, 4, "abyss_whale_staff"),
    WHALE_CAGE("whale_cage", Skill.BONE_TRAP, 4, "abyss_whale_bow"),
    TIDE_RAIN("tide_rain", Skill.SKY_RAIN, 4, "abyss_tide_bow"),
    FIRE_LEAP("fire_leap", Skill.ARCHER_LEAP, 4, "abyss_ember_bow"),
    TIDE_VEIL("tide_veil", Skill.MIST_VEIL, 4, "abyss_tide_dagger"),
    WHALE_FANG("whale_fang", Skill.LUNGE, 6, "abyss_whale_fang"),
    STORM_MARK("storm_mark", Skill.BACKSTEP, 6, "abyss_storm_dagger"),
    EMBER_REND("ember_rend", Skill.WIND_REND, 6, "abyss_ember_dagger"),
    ROCK_SONG("rock_song", Skill.CYCLE_SONG, 4, "abyss_whale_bell_staff"),
    STORM_CURSE("storm_curse", Skill.CURSE_MARK, 4, "abyss_storm_bell_staff"),
    EMBER_LAMP("ember_lamp", Skill.LAMP_CHARM, 4, "abyss_ember_bell_staff"),
    // ---- 巨神のヒロイック武器 (item/HeroicGear の 17 本。2026-10-28)。同じ巨神の伝説と違う技へ。書き換えが一つある技は 6、二つある技は 7
    WHALE_TEMPER("whale_temper", Skill.TEMPER, 6, "whale_choir_hammer"),
    WHALE_STARFALL("whale_starfall", Skill.THUNDER_BOLT, 6, "whale_star_staff"),
    WHALE_RIB_PIERCE("whale_rib_pierce", Skill.CLOUD_PIERCE, 6, "whale_rib_longbow"),
    WHALE_DEEP_WARD("whale_deep_ward", Skill.WARD_SONG, 6, "whale_deep_bell_staff"),
    TIDE_STOMP("tide_stomp", Skill.FORGE_STOMP, 6, "tide_reef_hammer"),
    TIDE_BUBBLE("tide_bubble", Skill.EMBER_ORB, 6, "tide_pearl_staff"),
    TIDE_CORAL_TRAP("tide_coral_trap", Skill.BONE_TRAP, 6, "tide_coral_bow"),
    TIDE_SURF_LAMP("tide_surf_lamp", Skill.LAMP_CHARM, 6, "tide_surf_bell_staff"),
    STORM_ANVIL("storm_anvil", Skill.EMBER_HAMMER, 7, "storm_anvil_hammer"),
    STORM_SURGE("storm_surge", Skill.FROST_TIDE, 6, "storm_cloud_staff"),
    STORM_GALE_RAIN("storm_gale_rain", Skill.SKY_RAIN, 6, "storm_gale_bow"),
    STORM_BELL_PULL("storm_bell_pull", Skill.EBB_TIDE, 6, "storm_thunder_bell_staff"),
    EMBER_RUSH("ember_rush", Skill.SHIELD_RUSH, 6, "ember_cinder_blade"),
    EMBER_ASH_VEIL("ember_ash_veil", Skill.MIST_VEIL, 6, "ember_ash_knife"),
    EMBER_KILN_BLINK("ember_kiln_blink", Skill.BLINK, 6, "ember_kiln_staff"),
    EMBER_FLARE("ember_flare", Skill.CLOUD_PIERCE, 7, "ember_flare_bow"),
    EMBER_CENSER_SONG("ember_censer_song", Skill.CYCLE_SONG, 6, "ember_censer_bell_staff"),
    // ---- 六章「月」のヒロイック武器 (story/ch6/gear の 6 本。2026-10-31)。まだ書き換えのない技へ (呪いの印だけはすでに一つあるので 6、ほかは 4)
    MOON_WANING_SPIN("moon_waning_spin", Skill.SPIN_CUT, 4, "moon_waning_blade"),
    MOON_FULL_BURST("moon_full_burst", Skill.SPARK_BURST, 4, "moon_full_hammer"),
    MOON_NEW_DUST("moon_new_dust", Skill.STARDUST, 4, "moon_new_staff"),
    MOON_STARFALL_SNARE("moon_starfall_snare", Skill.SNARE_SHOT, 4, "moon_starfall_bow"),
    MOON_UMBRA_TWIN("moon_umbra_twin", Skill.TWIN_FANG, 4, "moon_umbra_dagger"),
    MOON_HALO_CURSE("moon_halo_curse", Skill.CURSE_MARK, 6, "moon_halo_bell_staff"),
    // ---- 終章の神器 (story/finale/gear の 6 本。2026-10-30)。どれもその職の幹の技を、島と座で覚えた決まりの形に
    DIVINE_SKY_HAND("divine_sky_hand", Skill.TITAN_CLEAVE, 4, "divine_sky_sword"),
    DIVINE_BELL_WAVE("divine_bell_wave", Skill.EMBER_HAMMER, 4, "divine_bell_hammer"),
    DIVINE_DAWN_GAZE("divine_dawn_gaze", Skill.THUNDER_BOLT, 4, "divine_dawn_staff"),
    DIVINE_PLUME_RAIN("divine_plume_rain", Skill.CLOUD_PIERCE, 4, "divine_plume_bow"),
    DIVINE_REWIND_LUNGE("divine_rewind_lunge", Skill.LUNGE, 4, "divine_rewind_dagger"),
    DIVINE_PROMISE_CIRCLE("divine_promise_circle", Skill.WARD_SONG, 4, "divine_promise_bell_staff");

    // 熟練 3 で開く 4 つ目の刻印 (熟練の刻印)。どの使う技にもあり、番号は 5 (4 は逸品の書き換え)。docs/design/31 の A13
    public static final int MASTERY = 5;

    public static boolean valid(Skill skill, int index) {
        return of(skill, index) != null || index == MASTERY && skill.active;
    }

    // その刻印を今選べる手の状態か (熟練の刻印は常に。逸品の書き換えはその技のどれかの書き換えの武器を持っているとき)
    public static boolean heldOf(Skill skill, int index, net.minecraft.world.entity.player.Player player) {
        if (index == 4) {
            return heldLegend(skill, player) != null;
        }
        Rune r = of(skill, index);
        return r == null ? index == MASTERY : r.held(player);
    }

    // その技の書き換えのうち、いま持っている (刻んだ) 武器のもの。一つの技に書き換えの武器が二つあっても、持っているほうが効く
    public static Rune heldLegend(Skill skill, net.minecraft.world.entity.player.Player player) {
        for (Rune r : values()) {
            if (r.skill == skill && r.legendary() && r.held(player)) {
                return r;
            }
        }
        return null;
    }

    // 画面に出す書き換え: 持っているものがあればそれ、なければその技の最初の書き換え
    public static Rune legendFor(Skill skill, net.minecraft.world.entity.player.Player player) {
        Rune held = player == null ? null : heldLegend(skill, player);
        return held != null ? held : legendaryOf(skill);
    }

    public final String id;
    public final Skill skill;
    // 1..3 (同じ技の中での番号。記録にはこの番号を入れる)。逸品の書き換えは 4
    public final int index;
    // 逸品の書き換えに要る武器 (品の id の path。ふつうの刻印は空)
    public final String weapon;

    Rune(String id, Skill skill, int index) {
        this(id, skill, index, "");
    }

    Rune(String id, Skill skill, int index, String weapon) {
        this.id = id;
        this.skill = skill;
        this.index = index;
        this.weapon = weapon;
    }

    public boolean legendary() {
        return !weapon.isEmpty();
    }

    // その武器を主の手に持っているか (ふつうの刻印は常に true)
    public boolean held(net.minecraft.world.entity.player.Player player) {
        if (!legendary()) {
            return true;
        }
        net.minecraft.world.item.ItemStack hand = player.getMainHandItem();
        // 書き換えの品を持っている (抽出して抜け殻になったものは除く)、または装備に刻んだアスペクト (item/extra/GearFreedom)
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem()).getPath().equals(weapon)
                && !jp.revati.skyabyss.item.extra.GearFreedom.isHollow(hand)) {
            return true;
        }
        return jp.revati.skyabyss.item.extra.GearFreedom.carries(player, this);
    }

    public static Rune legendaryOf(Skill skill) {
        for (Rune r : values()) {
            if (r.skill == skill && r.legendary()) {
                return r;
            }
        }
        return null;
    }

    public String nameKey() {
        return (legendary() ? "legend.skyabyss." : "rune.skyabyss.") + id;
    }

    public String effectKey() {
        return nameKey() + ".effect";
    }

    // 逸品の書き換えの説明に添える、書き換わる技の名前の鍵
    public String skillNameKey() {
        return skill.nameKey();
    }

    public static List<Rune> of(Skill skill) {
        List<Rune> list = new ArrayList<>();
        for (Rune r : values()) {
            if (r.skill == skill && !r.legendary()) {
                list.add(r);
            }
        }
        return list;
    }

    // その技の index 番の刻印 (なければ null)
    public static Rune of(Skill skill, int index) {
        for (Rune r : values()) {
            if (r.skill == skill && r.index == index) {
                return r;
            }
        }
        return null;
    }

    public static Rune byId(String id) {
        for (Rune r : values()) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }
}
