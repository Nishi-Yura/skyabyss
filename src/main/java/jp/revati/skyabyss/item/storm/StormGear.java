package jp.revati.skyabyss.item.storm;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 雷翼の装備の表と数値 (03 の 12章「雷翼の装備の実装」、12 の 6章、2026-10-17)。
// 数値は 03 の 9.8節の目安 (推奨 Lv 24、武器 +0 は 1 秒あたり 15.2・+10 で 19.8、防具 21 / 14、一式 ×0.8 相当まで) に寄せた。
// 必要 Lv は GearLevel の決め方のまま (推奨 24 から、レア −4 = 20、伝説 −2 = 22)。
//
// | 品              | 種類            | 階級 | 必要 Lv | 1 秒あたり (+0 / +10) | 固有効果 |
// | 雷翼の羽衣 4 部位 | 防具 (立体)    | レア | 20 | —           | 防御 3/8/7/3 = 21、強靭 3.5 ×4 = 14。2 部位で落下の傷 −50%。4 部位で「嵐の衣」(受ける傷 ×0.85、空中で ×0.8) と空駆け (空中でもう一度跳ぶと前へ、待ち 8 秒) |
// | 雷翼の弓        | 弓              | レア | 20 | 12.9 / 16.4 | 矢の傷 ×1.75。命中で感電 (3 秒光る、まわり 2.5 の別の敵へ 1.5。+5 で 2.5 を 2 体へ) |
// | 嵐纏の足環      | 装飾            | レア | 20 | —           | 滑空 (風切りの羽根・エリトラ) の速さ +15%、落下の傷 −25% (羽衣の 2 部位と掛けて −62.5%) |
// | 嵐の護符        | 護符 (H キー)   | レア | 20 | —           | 次の 3 回の近接・射撃に雷 5 (まわりへは届かない)。待ち 30 秒 |
// | 雷獣の爪        | 片手の爪        | 伝説 | 22 | 15.0 / 19.5 | 3 回に 1 回 (+5 で 2 回に 1 回)、4 ブロック内の別の敵へ雷が跳ぶ (攻撃力の 40%。+10 で 2 体) |
// | 羽守の鉤槍      | 槍              | 伝説 | 22 | 15.0 / 19.5 | 間合い +1。空中の敵と、落下中の自分の攻撃で傷 ×1.3 (+5 で ×1.4) |
// | 嵐の目の羽根    | 装飾            | 伝説 | 22 | —           | まわり 6 ブロックへ飛んでくる飛び道具を、3 秒に一度そらす |
// | 雷翼の羽軸・心核・雲間の干し果・鱗粉 | 素材・食べ物 | — | — | — | 干し果は回復 5・飽和 0.6、30 秒の静電避け。鱗粉は矢 2 本と光の矢 2 本 |
public final class StormGear {
    public static final String TITAN = "thunder_wing";

    // ---------------------------------------------------------------- 品の ID (12 の 11.1節と 6章。報酬の表はこの ID で引く)
    public static final String HELMET = id("thunder_wing_helmet");
    public static final String CHESTPLATE = id("thunder_wing_chestplate");
    public static final String LEGGINGS = id("thunder_wing_leggings");
    public static final String BOOTS = id("thunder_wing_boots");
    public static final String BOW = id("thunder_wing_bow");
    public static final String ANKLET = id("storm_anklet");
    public static final String CHARM = id("storm_charm");
    public static final String CLAW = id("raiju_claw");
    public static final String GLAIVE = id("warden_glaive");
    public static final String EYE_FEATHER = id("storm_eye_feather");
    // 素材 (12 の 11.1節)
    public static final String SHARD = id("thunder_wing_shard");
    public static final String CORE = id("thunder_wing_core");
    public static final String FRUIT = id("cloudgap_fruit");
    public static final String DUST = id("moth_dust");
    // 雲間の干し果の効き目 (雷蛾の担当は ID で引く: 持っていれば連鎖を受けない・次へ跳ばない)
    public static final String STATIC_WARD = id("static_ward");

    // ---------------------------------------------------------------- 心臓の確率 (12 の 6.3節)
    public static final float ARMOR_CHANCE = 0.25f;
    public static final float BOW_CHANCE = 0.20f;
    public static final float ANKLET_CHANCE = 0.20f;
    public static final float CHARM_CHANCE = 0.20f;
    public static final float CLAW_CHANCE = 0.04f;
    public static final float GLAIVE_CHANCE = 0.04f;
    public static final float EYE_FEATHER_CHANCE = 0.02f;
    // 道中と中ボス (12 の 6.1・6.2節)
    public static final float WARDEN_ARMOR_CHANCE = 0.01f;
    public static final float RAIJU_CLAW_CHANCE = 0.05f;

    // 図鑑 (10 品)。並びは図鑑の並び
    public static final TitanGearSet SET = new TitanGearSet(TITAN, SHARD, CORE, List.of(
            TitanGearSet.piece(HELMET, ARMOR_CHANCE),
            TitanGearSet.piece(CHESTPLATE, ARMOR_CHANCE),
            TitanGearSet.piece(LEGGINGS, ARMOR_CHANCE),
            TitanGearSet.piece(BOOTS, ARMOR_CHANCE),
            TitanGearSet.piece(BOW, BOW_CHANCE),
            TitanGearSet.piece(ANKLET, ANKLET_CHANCE),
            TitanGearSet.piece(CHARM, CHARM_CHANCE),
            TitanGearSet.exclusive(CLAW, CLAW_CHANCE),
            TitanGearSet.exclusive(GLAIVE, GLAIVE_CHANCE),
            TitanGearSet.exclusive(EYE_FEATHER, EYE_FEATHER_CHANCE)));

    public static final List<String> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    // ---------------------------------------------------------------- 防具
    public static final int[] ARMOR_DEFENSE = {3, 8, 7, 3};
    public static final float ARMOR_TOUGHNESS = 3.5f;
    public static final float ARMOR_KNOCKBACK = 0.05f;
    public static final double SET2_FALL = 0.5;
    public static final double SET4_TAKEN = 0.85;
    public static final double SET4_TAKEN_AIR = 0.80;
    // 空駆け: 前へ 1.15、上へ 0.32 (6 ブロックほど)。待ち 8 秒
    public static final int DASH_COOLDOWN = 20 * 8;
    public static final double DASH_FORWARD = 1.15;
    public static final double DASH_LIFT = 0.32;

    // ---------------------------------------------------------------- 弓 (矢の傷の倍率。強化で +3%/段)
    public static final double BOW_DAMAGE = 1.75;
    public static final double BOW_SHOCK = 1.5;
    public static final double BOW_SHOCK_AWAKENED = 2.5;
    public static final double BOW_SHOCK_RADIUS = 2.5;
    public static final int BOW_GLOW_TICKS = 60;
    // 満月まで引いてから放すまでの 1 回の長さ (引き 1 秒 + 狙い直し 0.1 秒)。1 秒あたりの計算に使う
    public static final double BOW_CYCLE = 1.1;

    // ---------------------------------------------------------------- 手の武器 (攻撃力 = 素手の 1 + 品の修飾子)
    public static final double CLAW_ATTACK = 7.5;
    public static final double CLAW_SPEED = 2.0;
    public static final double CLAW_CHAIN_RATE = 0.4;
    public static final double CLAW_CHAIN_RANGE = 4;

    public static final double GLAIVE_ATTACK = 10;
    public static final double GLAIVE_SPEED = 1.5;
    public static final double GLAIVE_REACH = 1.0;
    public static final double GLAIVE_AERIAL = 1.3;
    public static final double GLAIVE_AERIAL_AWAKENED = 1.4;
    // これより大きな相手 (巨神の心臓 450〜) には、自分が宙にいるときだけ空中の上乗せ (心臓はいつも宙にいるため)
    public static final float GLAIVE_BIG_TARGET_HP = 200;

    // ---------------------------------------------------------------- 護符・装飾・食べ物
    public static final int CHARM_COOLDOWN = 20 * 30;
    public static final int CHARM_CHARGES = 3;
    public static final float CHARM_DAMAGE = 5;
    public static final double ANKLET_GLIDE = 0.15;
    public static final double ANKLET_FALL = 0.75;
    public static final double FEATHER_RADIUS = 6;
    public static final int FEATHER_COOLDOWN = 20 * 3;
    public static final int FRUIT_NUTRITION = 5;
    public static final float FRUIT_SATURATION = 0.6f;
    public static final int FRUIT_WARD_TICKS = 20 * 30;

    // ---------------------------------------------------------------- 背中 (担当 1 の StormBackLayout と同じ値。統合で本物を呼ぶ)
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));

    private StormGear() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }
}
