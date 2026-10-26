package jp.revati.skyabyss.titan.tide.gear;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.titan.tide.TideKeeper;

// 潮殻の装備の表と数値 (03 の 11章「潮殻の装備の実装」、2026-10-17)。
// 数値は 03 の 9.8節の目安 (推奨 Lv 18、武器 +0 は 1 秒あたり 13.2・+10 で 17.2、防具 20 / 強靭 12、一式 ×0.8 相当まで) に寄せた。
// 必要 Lv は GearLevel の決め方のまま (推奨 18 から、エピック以下 −4 = 14、ヒロイック以上 −2 = 16)。
//
// | 品            | 種類          | 階級 | 必要 Lv | 1 秒あたり (+0 / +10) | 固有効果 |
// | 三叉槍        | 槍            | エピック | 14 | 12.8 / 16.6 | 間合い +1。命中で鈍足 I 3 秒 (+5 で II) |
// | 珊瑚の盾      | 盾 (左手)     | エピック | 14 | —           | 構えている間、防げない向きの傷 −10% (+1%/段、上限 20%)。防いだ近接の相手に棘 2 (+0.2/段)、+5 で鈍足 |
// | 潮殻の鎧 4 部位 | 防具        | レア | 14 | —           | 防御 3/8/6/3 = 20、強靭 2.25 ×4 = 9。2 部位で強靭 +3。4 部位で受ける傷 ×0.85 (水に触れていれば ×0.8)、水の中でイルカの好意と水中呼吸 |
// | 潮鳴りの護符  | 護符 (H キー) | エピック | 14 | —           | 半径 5 の衝撃波: 4 の傷、押し返し、鈍足 II 3 秒。待ち 45 秒 |
// | 潮招きの大鋏  | 片手の重い武器 | ヒロイック | 16 | 12.8 / 17.9 | 3 度に 1 度 (+5 で 2 度に 1 度)、前へ水の線 (長さ 6、+10 で 9。攻撃力の 50%)。水の中で攻撃速度 +20% |
// | 珊瑚憑きの銛  | 槍            | ヒロイック | 16 | 13.5 / 17.6 | 間合い +1。命中した敵を珊瑚で 1 秒縛る (同じ敵には 8 秒に一度。+5 で 1.5 秒・6 秒) |
// | 月潮の真珠    | 装飾          | ヒロイック | 16 | —           | 体力が 30% を切ると泡の盾 (吸収 8、6 秒)。待ち 90 秒 |
// | 潮殻の心核    | 神器の素材    | —    | —  | —           | 心臓の初回は必ず、以後 25% |
public final class TideGear {
    public static final String TITAN = TideKeeper.SLOT;   // "tide_shell"

    // ---------------------------------------------------------------- 品の ID (TideLoot の ID と同じ)
    public static final String HELMET = id("tide_shell_helmet");
    public static final String CHESTPLATE = id("tide_shell_chestplate");
    public static final String LEGGINGS = id("tide_shell_leggings");
    public static final String BOOTS = id("tide_shell_boots");
    public static final String TRIDENT = id("tide_shell_trident");
    public static final String SHIELD = id("coral_shield");
    public static final String CHARM = id("tide_chime_charm");
    public static final String CLAW = id("tidecaller_claw");
    public static final String HARPOON = id("coral_harpoon");
    public static final String PEARL = id("moontide_pearl");
    public static final String CORE = id("tide_shell_core");
    public static final String SHARD = id("tide_shell_shard");

    // ---------------------------------------------------------------- 心臓の確率 (08 の 6.3節)
    public static final float ARMOR_CHANCE = 0.25f;
    public static final float TRIDENT_CHANCE = 0.20f;
    public static final float SHIELD_CHANCE = 0.20f;
    public static final float CHARM_CHANCE = 0.20f;
    public static final float CLAW_CHANCE = 0.04f;
    public static final float HARPOON_CHANCE = 0.04f;
    public static final float PEARL_CHANCE = 0.02f;
    // 道中と中ボス (08 の 6.1・6.2節)
    public static final float THRALL_ARMOR_CHANCE = 0.01f;
    public static final float BECKONER_CLAW_CHANCE = 0.05f;

    // 図鑑 (14 品。職の章の武器 4 つを足した。item/ChapterGear)。並びは図鑑の並び
    public static final TitanGearSet SET = new TitanGearSet(TITAN, SHARD, CORE, List.of(
            TitanGearSet.piece(HELMET, ARMOR_CHANCE),
            TitanGearSet.piece(CHESTPLATE, ARMOR_CHANCE),
            TitanGearSet.piece(LEGGINGS, ARMOR_CHANCE),
            TitanGearSet.piece(BOOTS, ARMOR_CHANCE),
            TitanGearSet.piece(TRIDENT, TRIDENT_CHANCE),
            TitanGearSet.piece(SHIELD, SHIELD_CHANCE),
            TitanGearSet.piece(CHARM, CHARM_CHANCE),
            TitanGearSet.piece(jp.revati.skyabyss.item.ChapterGear.TIDE_STAFF, jp.revati.skyabyss.item.ChapterGear.CHANCE),
            TitanGearSet.piece(jp.revati.skyabyss.item.ChapterGear.TIDE_BELL, jp.revati.skyabyss.item.ChapterGear.CHANCE),
            TitanGearSet.piece(jp.revati.skyabyss.item.ChapterGear.TIDE_HAMMER, jp.revati.skyabyss.item.ChapterGear.CHANCE),
            TitanGearSet.piece(jp.revati.skyabyss.item.ChapterGear.TIDE_BOW, jp.revati.skyabyss.item.ChapterGear.CHANCE),
            TitanGearSet.exclusive(CLAW, CLAW_CHANCE),
            TitanGearSet.exclusive(HARPOON, HARPOON_CHANCE),
            TitanGearSet.exclusive(PEARL, PEARL_CHANCE)));

    public static final List<String> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    // ---------------------------------------------------------------- 武器 (攻撃力 = 素手の 1 + 品の修飾子)
    public static final double TRIDENT_ATTACK = 8.5;
    public static final double TRIDENT_SPEED = 1.5;
    public static final double TRIDENT_REACH = 1.0;
    public static final int TRIDENT_SLOW_TICKS = 60;

    public static final double CLAW_ATTACK = 11;
    public static final double CLAW_SPEED = 1.0;
    public static final double CLAW_LINE_RATE = 0.5;
    public static final double CLAW_LINE_LENGTH = 6;
    public static final double CLAW_LINE_LENGTH_MAX = 9;
    public static final double CLAW_WATER_SPEED = 0.2;

    public static final double HARPOON_ATTACK = 7.5;
    public static final double HARPOON_SPEED = 1.8;
    public static final double HARPOON_REACH = 1.0;
    public static final int HARPOON_BIND_TICKS = 20;
    public static final int HARPOON_BIND_TICKS_AWAKENED = 30;
    public static final int HARPOON_BIND_EVERY = 160;
    public static final int HARPOON_BIND_EVERY_AWAKENED = 120;

    // ---------------------------------------------------------------- 盾
    public static final int SHIELD_DURABILITY = 900;
    public static final double SHIELD_GUARD = 0.10;
    public static final double SHIELD_GUARD_PER_LEVEL = 0.01;
    public static final double SHIELD_THORNS = 2;
    public static final double SHIELD_THORNS_PER_LEVEL = 0.2;

    // ---------------------------------------------------------------- 防具 (ModArmorMaterials の岩鯨と同じ形。TideGearSetup)
    public static final int[] ARMOR_DEFENSE = {3, 8, 6, 3};
    public static final float ARMOR_TOUGHNESS = 2.25f;
    public static final float ARMOR_KNOCKBACK = 0.05f;
    public static final double SET2_TOUGHNESS = 3;
    public static final double SET4_TAKEN = 0.85;
    public static final double SET4_TAKEN_WET = 0.80;

    // ---------------------------------------------------------------- 護符・装飾
    public static final int CHARM_COOLDOWN = 20 * 45;
    public static final double CHARM_RADIUS = 5;
    public static final float CHARM_DAMAGE = 4;
    public static final int PEARL_COOLDOWN = 20 * 90;
    public static final float PEARL_THRESHOLD = 0.30f;
    public static final int PEARL_ABSORB_TICKS = 120;

    private TideGear() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }
}
