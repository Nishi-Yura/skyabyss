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
    // ---- 伝説の書き換え (4 番目。その武器を主の手に持つあいだだけ選べて、効く。docs/design/27 の 4.2)
    BONE_PIERCE("pike_bone", Skill.LUNGE, 4, "captain_pike"),
    BITE("fang_bite", Skill.WIND_REND, 4, "waterway_fang"),
    SNIP("shears_cut", Skill.WHALE_SWEEP, 4, "tidecaller_claw"),
    HARPOON_TIE("harpoon_tie", Skill.EBB_TIDE, 4, "coral_harpoon"),
    THUNDER_RING("claw_ring", Skill.BACKSTEP, 4, "raiju_claw"),
    HOOK_PULL("halberd_hook", Skill.ROCK_CALL, 4, "warden_glaive"),
    FIRE_BED("hammer_forge", Skill.EMBER_HAMMER, 4, "smith_hammer"),
    FIRE_PASS("poker_share", Skill.TEMPER, 4, "thrall_poker");

    // 熟練 3 で開く 4 つ目の刻印 (熟練の刻印)。どの使う技にもあり、番号は 5 (4 は伝説の書き換え)。docs/design/31 の A13
    public static final int MASTERY = 5;

    public static boolean valid(Skill skill, int index) {
        return of(skill, index) != null || index == MASTERY && skill.active;
    }

    // その刻印を今選べる手の状態か (熟練の刻印は常に。伝説は武器を持っているとき)
    public static boolean heldOf(Skill skill, int index, net.minecraft.world.entity.player.Player player) {
        Rune r = of(skill, index);
        return r == null ? index == MASTERY : r.held(player);
    }

    public final String id;
    public final Skill skill;
    // 1..3 (同じ技の中での番号。記録にはこの番号を入れる)。伝説の書き換えは 4
    public final int index;
    // 伝説の書き換えに要る武器 (品の id の path。ふつうの刻印は空)
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
        // 自分の伝説を持っている (抽出して抜け殻になったものは除く)、または装備に刻んだアスペクト (item/extra/GearFreedom)
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

    // 伝説の書き換えの説明に添える、書き換わる技の名前の鍵
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
