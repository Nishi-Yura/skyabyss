package jp.revati.skyabyss.titan.ember.gear;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

// 余燼の装備の表と数値 (docs/design/03_gear_and_endgame.md の 12章「余燼の装備の実装」、13_ember.md の 6.5節)。
// 数値は 03 の 9.8節の余燼の段 (推奨 Lv 30、武器 +0 は 1 秒あたり 17.5・+10 で 22.8、防具 22 / 強靭 16) に寄せた。
// 必要 Lv は GearLevel の決め方のまま (推奨 30 から、レア −4 = 26、伝説 −2 = 28)。
//
// | 品              | 種類            | 階級 | 必要 Lv | 1 秒あたり (+0 / +10) | 固有効果 |
// | 余燼の鎧 4 部位 | 防具 (立体)     | レア | 26 | —             | 防御 4/8/6/4 = 22、強靭 3.25 ×4 = 13。2 部位で強靭 +3。4 部位で熱のたまり方 ×0.7・火照りの攻撃 +10% (背中)、火に触れると筋力 I 10 秒 |
// | 余燼の戦斧      | 斧 (剣の扱い)   | レア | 26 | 17.6 / 22.9   | 命中で 4 秒くすぶらせる (燃えない相手にも印)。燃えている・くすぶる相手に +10% |
// | 熾火の外套      | 装飾            | レア | 26 | —             | 熱のたまり方 ×0.75、燃焼・溶岩の傷 −30% (火炎耐性ではない) |
// | 熾りの護符      | 護符 (H キー)   | レア | 26 | —             | 自分の熱を 0 に。半径 5 の敵に 6 の傷と 4 秒の燃焼。待ち 50 秒 |
// | 鍛え手の大鎚    | 両手の大鎚      | 伝説 | 28 | 16.8 / 23.4   | 3 度に 1 度 (+5 で 2 度に 1 度)、床に亀裂が走り 0.8 秒後に噴く (長さ 5、+10 で 7。攻撃力の 50%) |
// | 灰被りの火掻き  | 長柄            | 伝説 | 28 | 17.6 / 22.9   | 間合い +1。命中で灰を積む。5 つ (+5 で 4 つ) で鈍足 II 3 秒・防御 −4 |
// | 余燼の灯        | 装飾            | 伝説 | 28 | —             | 倒れる傷を一度だけ体力 4 で踏みとどまり、まわり半径 3 に 6 の傷と燃焼。待ち 600 秒。不死のトーテムが先 |
// | 熾火の角杯      | 食べ物          | —    | —  | —             | 回復 6・飽和 0.8。背中では 3 分 熱のたまり方 ×0.6、地上では火炎耐性 30 秒 |
// | 余燼の心核      | 神器の素材      | —    | —  | —             | 心臓の初回は必ず、以後 25% |
public final class EmberGear {
    public static final String TITAN = EmberIds.TITAN;   // "ember"

    // ---------------------------------------------------------------- 品の ID (EmberIds と同じ。図鑑・報酬は名前空間つきの ID で持つ)
    public static final String HELMET = id(EmberIds.EMBER_HELMET);
    public static final String CHESTPLATE = id(EmberIds.EMBER_CHESTPLATE);
    public static final String LEGGINGS = id(EmberIds.EMBER_LEGGINGS);
    public static final String BOOTS = id(EmberIds.EMBER_BOOTS);
    public static final String WAR_AXE = id(EmberIds.EMBER_WAR_AXE);
    public static final String MANTLE = id(EmberIds.EMBER_MANTLE);
    public static final String CHARM = id(EmberIds.KINDLING_CHARM);
    public static final String HAMMER = id(EmberIds.SMITH_HAMMER);
    public static final String POKER = id(EmberIds.THRALL_POKER);
    public static final String LANTERN = id(EmberIds.EMBER_LANTERN);
    public static final String CORE = id(EmberIds.EMBER_CORE);
    public static final String SHARD = id(EmberIds.EMBER_SHARD);

    // ---------------------------------------------------------------- 心臓の確率 (13 の 6.3節)
    public static final float ARMOR_CHANCE = 0.25f;
    public static final float WAR_AXE_CHANCE = 0.20f;
    public static final float MANTLE_CHANCE = 0.20f;
    public static final float CHARM_CHANCE = 0.20f;
    public static final float HAMMER_CHANCE = 0.04f;
    public static final float POKER_CHANCE = 0.04f;
    public static final float LANTERN_CHANCE = 0.02f;
    // 道中と中ボス (13 の 6.1・6.2節)
    public static final float THRALL_ARMOR_CHANCE = 0.01f;
    public static final float SMITH_HAMMER_CHANCE = 0.05f;

    // 図鑑 (10 品)。並びは EmberIds.CODEX (13 の 6.3節の表) と同じ
    public static final TitanGearSet SET = new TitanGearSet(TITAN, SHARD, CORE, List.of(
            TitanGearSet.piece(HELMET, ARMOR_CHANCE),
            TitanGearSet.piece(CHESTPLATE, ARMOR_CHANCE),
            TitanGearSet.piece(LEGGINGS, ARMOR_CHANCE),
            TitanGearSet.piece(BOOTS, ARMOR_CHANCE),
            TitanGearSet.piece(WAR_AXE, WAR_AXE_CHANCE),
            TitanGearSet.piece(MANTLE, MANTLE_CHANCE),
            TitanGearSet.piece(CHARM, CHARM_CHANCE),
            TitanGearSet.exclusive(HAMMER, HAMMER_CHANCE),
            TitanGearSet.exclusive(POKER, POKER_CHANCE),
            TitanGearSet.exclusive(LANTERN, LANTERN_CHANCE)));

    public static final List<String> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    // ---------------------------------------------------------------- 武器 (攻撃力 = 素手の 1 + 品の修飾子)
    // 戦斧: 攻撃力 16、速さ 1.0。くすぶる相手に +10% (2 打目から。4 秒の印は 1 秒ごとに振れば切れない) → 一体へ 17.6
    public static final double WAR_AXE_ATTACK = 16;
    public static final double WAR_AXE_SPEED = 1.0;
    public static final double WAR_AXE_SMOLDER_BONUS = 0.10;
    public static final int WAR_AXE_SMOLDER_TICKS = 80;
    // 大鎚: 攻撃力 18、速さ 0.8。亀裂の噴き (攻撃力の 50%) が 3 度に 1 度 → 一体へ 16.8。+5 から 2 度に 1 度 (+10 で 23.4)
    public static final double HAMMER_ATTACK = 18;
    public static final double HAMMER_SPEED = 0.8;
    public static final double HAMMER_CRACK_RATE = 0.5;
    public static final double HAMMER_CRACK_LENGTH = 5;
    public static final double HAMMER_CRACK_LENGTH_MAX = 7;
    public static final int HAMMER_CRACK_DELAY = 16;
    // 火掻き: 攻撃力 11、速さ 1.6、間合い +1 → 一体へ 17.6。灰 5 つ (+5 から 4 つ) で鈍足 II 3 秒・防御 −4 (3 秒)
    public static final double POKER_ATTACK = 11;
    public static final double POKER_SPEED = 1.6;
    public static final double POKER_REACH = 1.0;
    public static final int POKER_ASH_STACKS = 5;
    public static final int POKER_ASH_STACKS_AWAKENED = 4;
    public static final int POKER_BRITTLE_TICKS = 60;
    public static final double POKER_BRITTLE_ARMOR = -4;
    // 灰は最後に積んでから 6 秒で崩れる (同じ相手を叩き続けたときだけ溜まる)
    public static final int POKER_ASH_DECAY_TICKS = 120;

    // ---------------------------------------------------------------- 防具 (ArmorMaterial。EmberGearSetup)
    public static final int[] ARMOR_DEFENSE = {4, 8, 6, 4};
    public static final float ARMOR_TOUGHNESS = 3.25f;
    public static final float ARMOR_KNOCKBACK = 0.05f;
    public static final double SET2_TOUGHNESS = 3;
    // 4 部位 (背中): 熱のたまり方、火照り (熱 50 以上) のときの攻撃
    public static final float SET4_HEAT_GAIN = 0.7f;
    public static final double SET4_FLUSHED_ATTACK = 0.10;
    // 4 部位 (どこでも): 火に触れたら筋力 I
    public static final int SET4_FIRE_STRENGTH_TICKS = 200;

    // ---------------------------------------------------------------- 装飾・護符・食べ物
    public static final float MANTLE_HEAT_GAIN = 0.75f;
    public static final double MANTLE_FIRE_TAKEN = 0.70;
    public static final int CHARM_COOLDOWN = 20 * 50;
    public static final double CHARM_RADIUS = 5;
    public static final float CHARM_DAMAGE = 6;
    public static final int CHARM_BURN_TICKS = 80;
    public static final int LANTERN_COOLDOWN = 20 * 600;
    public static final float LANTERN_HEALTH = 4;
    public static final double LANTERN_RADIUS = 3;
    public static final float LANTERN_DAMAGE = 6;
    public static final int LANTERN_BURN_TICKS = 60;
    public static final int HORN_NUTRITION = 6;
    public static final float HORN_SATURATION = 0.8f;
    public static final float HORN_HEAT_GAIN = 0.6f;
    public static final int HORN_BACK_TICKS = 20 * 180;
    public static final int HORN_SURFACE_FIRE_TICKS = 20 * 30;
    // 熱のたまり方の倍率 (13 の 3.4節): 火炎耐性 0.5 と角杯 0.6 は強いほうだけ、鎧と外套は重ねる。下限 0.4
    public static final float FIRE_RESISTANCE_HEAT_GAIN = 0.5f;
    public static final float HEAT_GAIN_FLOOR = 0.4f;

    private EmberGear() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    // ---------------------------------------------------------------- 熱の担当 (B) が読む口 (13 の 17.8節「品の側は持っているかを返す関数だけ」)
    // 余燼の鎧を 4 部位着ているか (必要 Lv に届かない鎧は着られないので、着ていれば効く)
    public static boolean armorSet(Player player) {
        return EmberArmorItem.piecesWorn(player) >= 4;
    }

    // 熾火の外套を装飾の枠に付けているか
    public static boolean mantleWorn(Player player) {
        return Accessories.worn(player, EmberGearSetup.MANTLE.get());
    }

    // 熾火の角杯が効いているか (背中で飲んでから 3 分)
    public static boolean hornActive(Player player) {
        return EmberHornItem.active(player);
    }

    // 熱のたまり方の倍率 (13 の 3.4節の決まりをまとめた値)。EmberHeat.gainMultiplier からこれを呼べばよい。
    // 火炎耐性 0.5 と角杯 0.6 は強いほうだけ、鎧 0.7 と外套 0.75 は重ねる。下限 0.4
    public static float heatGain(Player player) {
        float drink = 1f;
        if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            drink = Math.min(drink, FIRE_RESISTANCE_HEAT_GAIN);
        }
        if (hornActive(player)) {
            drink = Math.min(drink, HORN_HEAT_GAIN);
        }
        float m = drink;
        if (armorSet(player)) {
            m *= SET4_HEAT_GAIN;
        }
        if (mantleWorn(player)) {
            m *= MANTLE_HEAT_GAIN;
        }
        return Math.max(HEAT_GAIN_FLOOR, m);
    }
}
