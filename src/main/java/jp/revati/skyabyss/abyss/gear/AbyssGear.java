package jp.revati.skyabyss.abyss.gear;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// 五章「深淵」の装備の表と数値 (docs/design/03 の 12 章)。余燼のあとの段 (推奨 Lv 44、武器 +0 は 1 秒あたり 20.1・+10 で 26.2、
// 防具 23 / 強靭 18)。必要 Lv はレア 40・伝説 42 (GearLevel の決め方と同じ「推奨 −4 / −2」。深淵の段は巨神の段の表
// GearCurve.STAGES に入れず、ここの STAGE に置く: 備えの灯や心臓の釣り合いの表は巨神 4 体のまま)。
//
// | 品                  | 種類          | 階級 | 1 秒あたり (+0 / +10) | 固有効果 |
// | 淵潜りの鎧 4 部位   | 防具 (立体)   | レア | —            | 防御 4/9/6/4 = 23、強靭 3.75 ×4 = 15。2 部位で強靭 +3。4 部位「錨の足」: 深淵の上昇の重み ×0.6・闇のたまり ×0.8、どこでも暗い所 (明るさ 4 以下) で受ける傷 ×0.85 |
// | 灯り番の剣          | 剣            | レア | 19.4 / 25.2  | 灯りの中 (明るさ 8 以上) で傷 +10%。闇の中で斬った敵を 4 秒光らせる (+5 で 6 秒・まわり 4 の敵も) |
// | 鼓動の聴き筒        | 装飾          | レア | —            | 4 秒ごとに 12 ブロック内の敵 (壁の向こうも) を 2 秒光らせる。深淵では闇のたまり ×0.85 |
// | 灯籠の護符          | 護符 (H)      | レア | —            | 足もとに灯り (半径 8、60 秒。深淵の灯りと同じ)。半径 6 の敵を 8 秒光らせて押し返す。自分に暗視 30 秒。待ち 45 秒 |
// | 沈め錨              | 両手の錨      | 伝説 | 19.6 / 27.3  | 3 度に 1 度 (+5 で 2 度に 1 度) 錨の重し: 半径 3 (+10 で 4) に攻撃力の 50% と鈍足 III 1.5 秒、下へ引く |
// | 鼓動の短剣          | 短剣          | 伝説 | 20.3 / 27.3  | 同じ敵へ 4 度目 (+5 で 3 度目) の連撃ごとに拍: 攻撃力の 50% の追い打ちと、自分の体力 1 回復 |
// | 測量士の錘          | 装飾          | 伝説 | —            | 深さで与える傷が増える (深淵: 深さ 40 ごとに +1%、上限 +12%。地上: y 64 から 16 下がるごとに +1%、上限 +8%)。落下の傷 −30% |
public final class AbyssGear {
    public static final String DUNGEON = "abyss";

    // ---------------------------------------------------------------- 段 (GearCurve.Stage と同じ形)
    public static final GearCurve.Stage STAGE = new GearCurve.Stage(DUNGEON, 44, 20.1, 26.2, 23, 18, 0.8, 21, 950);
    public static final int RARE_LEVEL = STAGE.level() - 4;
    public static final int LEGEND_LEVEL = STAGE.level() - 2;

    // ---------------------------------------------------------------- 品の ID
    public static final String HELMET = id("abyss_diver_helmet");
    public static final String CHESTPLATE = id("abyss_diver_chestplate");
    public static final String LEGGINGS = id("abyss_diver_leggings");
    public static final String BOOTS = id("abyss_diver_boots");
    public static final String LAMP_SWORD = id("abyss_lamp_sword");
    public static final String LISTENER = id("abyss_pulse_listener");
    public static final String CHARM = id("abyss_lantern_charm");
    public static final String ANCHOR = id("abyss_sinking_anchor");
    public static final String DAGGER = id("abyss_pulse_dagger");
    public static final String PLUMB = id("abyss_surveyor_plumb");
    public static final String SHARD = id("abyss_anchor_iron");
    public static final String CORE = id("abyss_core");
    public static final String RESONANCE_SHARD = id("resonance_shard_abyss");

    public static final List<String> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    // ---------------------------------------------------------------- 心臓の番人 (と悪夢) の確率。巨神の心臓と同じ並び
    public static final float ARMOR_CHANCE = 0.25f;
    public static final float BASE_CHANCE = 0.20f;
    public static final float EXCLUSIVE_CHANCE = 0.04f;
    public static final float PLUMB_CHANCE = 0.02f;
    public static final float CORE_CHANCE = 0.25f;

    // 図鑑 (10 品)
    public static final TitanGearSet SET = new TitanGearSet(DUNGEON, SHARD, CORE, List.of(
            TitanGearSet.piece(HELMET, ARMOR_CHANCE),
            TitanGearSet.piece(CHESTPLATE, ARMOR_CHANCE),
            TitanGearSet.piece(LEGGINGS, ARMOR_CHANCE),
            TitanGearSet.piece(BOOTS, ARMOR_CHANCE),
            TitanGearSet.piece(LAMP_SWORD, BASE_CHANCE),
            TitanGearSet.piece(LISTENER, BASE_CHANCE),
            TitanGearSet.piece(CHARM, BASE_CHANCE),
            TitanGearSet.exclusive(ANCHOR, EXCLUSIVE_CHANCE),
            TitanGearSet.exclusive(DAGGER, EXCLUSIVE_CHANCE),
            TitanGearSet.exclusive(PLUMB, PLUMB_CHANCE)));

    // 層の主: 初めて倒した人に、その層の鎧の一部位を必ず (骨 = 兜・塩 = 胴・雷 = すね当て・灰 = 靴)
    public static String lordPiece(String layer) {
        return switch (layer) {
            case "salt" -> CHESTPLATE;
            case "storm" -> LEGGINGS;
            case "ash" -> BOOTS;
            default -> HELMET;
        };
    }

    // ---------------------------------------------------------------- 武器 (攻撃力 = 素手の 1 + 品の 5 + 品ごとの値)
    // 灯り番の剣: 11 × 1.6 = 17.6、灯りの中で ×1.1 → 19.36
    public static final double SWORD_ATTACK = 11;
    public static final double SWORD_SPEED = 1.6;
    public static final double SWORD_LIGHT_BONUS = 0.10;
    public static final int SWORD_LIGHT_LEVEL = 8;
    public static final int SWORD_MARK_TICKS = 80;
    public static final int SWORD_MARK_TICKS_AWAKENED = 120;
    public static final double SWORD_MARK_RADIUS = 4;
    // 沈め錨: 21 × 0.8 = 16.8、重し (攻撃力の 50%) が 3 度に 1 度 → 19.6。+5 から 2 度に 1 度 (+10 で 27.3)
    public static final double ANCHOR_ATTACK = 21;
    public static final double ANCHOR_SPEED = 0.8;
    public static final double ANCHOR_RATE = 0.5;
    public static final double ANCHOR_RADIUS = 3;
    public static final double ANCHOR_RADIUS_MAX = 4;
    public static final int ANCHOR_SLOW_TICKS = 30;
    // 鼓動の短剣: 9 × 2.0 = 18、拍 (攻撃力の 50%) が 4 度に 1 度 → 20.25。+5 から 3 度に 1 度 (+10 で 27.3)
    public static final double DAGGER_ATTACK = 9;
    public static final double DAGGER_SPEED = 2.0;
    public static final double DAGGER_RATE = 0.5;
    public static final int DAGGER_EVERY = 4;
    public static final int DAGGER_EVERY_AWAKENED = 3;
    public static final float DAGGER_HEAL = 1f;
    // 連撃が続く間 (同じ敵を 1.5 秒の内に打ち続ける)
    public static final int DAGGER_CHAIN_TICKS = 30;

    // ---------------------------------------------------------------- 防具
    public static final int[] ARMOR_DEFENSE = {4, 9, 6, 4};
    public static final float ARMOR_TOUGHNESS = 3.75f;
    public static final float ARMOR_KNOCKBACK = 0.05f;
    public static final double SET2_TOUGHNESS = 3;
    public static final double SET4_WEIGHT = 0.6;
    public static final double SET4_VOICE = 0.8;
    public static final double SET4_DARK_TAKEN = 0.85;
    public static final int DARK_LEVEL = 4;

    // ---------------------------------------------------------------- 装飾・護符
    public static final int LISTENER_PERIOD = 80;
    public static final double LISTENER_RADIUS = 12;
    public static final int LISTENER_GLOW_TICKS = 40;
    public static final double LISTENER_VOICE = 0.85;
    public static final int CHARM_COOLDOWN = 20 * 45;
    public static final double CHARM_LIGHT_RADIUS = 8;
    public static final int CHARM_LIGHT_TICKS = 20 * 60;
    public static final double CHARM_RADIUS = 6;
    public static final int CHARM_GLOW_TICKS = 160;
    public static final int CHARM_NIGHT_VISION_TICKS = 20 * 30;
    public static final double PLUMB_ABYSS_STEP = 40;
    public static final double PLUMB_ABYSS_MAX = 0.12;
    public static final double PLUMB_SURFACE_TOP = 64;
    public static final double PLUMB_SURFACE_STEP = 16;
    public static final double PLUMB_SURFACE_MAX = 0.08;
    public static final double PLUMB_FALL_TAKEN = 0.70;

    private AbyssGear() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    public static int requiredLevel(GearTier tier) {
        return tier.ordinal() >= GearTier.LEGENDARY.ordinal() ? LEGEND_LEVEL : RARE_LEVEL;
    }

    // ---------------------------------------------------------------- 明るさ・深さ (純粋な部分は自己テストが呼ぶ)
    // その場の明るさ: ワールドの明るさと、深淵の灯り (光の術・灯籠の護符) の大きい方
    public static int light(LivingEntity e) {
        return AbyssLight.lightAt(e.level(), e.blockPosition());
    }

    public static boolean inLight(LivingEntity e) {
        return light(e) >= SWORD_LIGHT_LEVEL;
    }

    public static boolean inDark(LivingEntity e) {
        return light(e) <= DARK_LEVEL;
    }

    // 測量士の錘の上乗せ (割合)。abyss: 深淵の中か、depth: 深淵の深さ、y: 地上の高さ
    public static double plumbBonus(boolean abyss, int depth, double y) {
        if (abyss) {
            return Math.min(PLUMB_ABYSS_MAX, Math.floor(Math.max(0, depth) / PLUMB_ABYSS_STEP) * 0.01);
        }
        return Math.min(PLUMB_SURFACE_MAX, Math.floor(Math.max(0, PLUMB_SURFACE_TOP - y) / PLUMB_SURFACE_STEP) * 0.01);
    }

    public static double plumbBonus(Player player) {
        if (!Accessories.worn(player, AbyssGearSetup.PLUMB.get())) {
            return 0;
        }
        boolean abyss = player.level().dimension() == AbyssWorld.LEVEL;
        int depth = abyss && player instanceof ServerPlayer sp ? AbyssPressure.depth(sp) : 0;
        return plumbBonus(abyss, depth, player.getY());
    }

    // 一体への 1 秒あたり (固有効果の上乗せ込み、自己テストと 03 の表)
    public static double swordDps(int enhance) {
        return SWORD_ATTACK * (1 + 0.03 * enhance) * SWORD_SPEED * (1 + SWORD_LIGHT_BONUS);
    }

    public static double anchorDps(int enhance) {
        int every = enhance >= 5 ? 2 : 3;
        return ANCHOR_ATTACK * (1 + 0.03 * enhance) * ANCHOR_SPEED * (1 + ANCHOR_RATE / every);
    }

    public static double daggerDps(int enhance) {
        int every = enhance >= 5 ? DAGGER_EVERY_AWAKENED : DAGGER_EVERY;
        return DAGGER_ATTACK * (1 + 0.03 * enhance) * DAGGER_SPEED * (1 + DAGGER_RATE / every);
    }
}
