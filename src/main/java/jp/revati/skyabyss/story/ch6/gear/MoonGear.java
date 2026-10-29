package jp.revati.skyabyss.story.ch6.gear;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.TitanGearSet;

// 六章「月」の装備の表と数値 (docs/design/03 の 12.2)。深淵のあとの段 (推奨 Lv 54、武器 +0 は 1 秒あたり 23.1・+10 で 30.1、
// 防具 26 / 強靭 20)。必要 Lv はエピック以下 50・ヒロイック以上 52。段は巨神の段の表に入れず、ここの STAGE に置く (深淵と同じ)。
//
// | 品               | 種類        | 階級       | 1 秒あたり (+0 / +10) | 固有効果 |
// | 月渡りの装束 4   | 防具 (立体) | レア       | —             | 防御 5/9/7/5 = 26、強靭 4 ×4。2 部位で強靭 +4。4 部位「月の足」: 落下の傷 ×0.5、月の上で受ける傷 ×0.9 |
// | 月秤の鎚         | 鎚          | エピック   | 23.0 / 29.9   | 相手の体力の割合が自分より高いと傷 +15% |
// | 満ち引きの杖     | 杖          | エピック   | 22.4 / 29.2   | 満ちと引きが打つたびに入れ替わる (満ち +20% / 引き 傷の 10% を体力に) |
// | 下弦の弓         | 弓          | エピック   | —             | 当たった相手に 1 秒後に残像の矢 (傷の 40%) |
// | 欠け月の短剣     | 短剣        | エピック   | 23.0 / 29.9   | 背から刺すと +30% (表の値は半分の当たりが背) |
// | 星灯りの鈴杖     | 鈴杖        | エピック   | 23.1 / 30.0   | 打った相手に星の印 4 秒 (だれの傷も +10%) |
// | 残月の太刀       | 太刀        | ヒロイック | 25.2 / 32.8   | 3 度に 1 度、1.5 秒後に残像の太刀 (攻撃力の 50%) |
// | 満月の環         | 装飾        | ヒロイック | —             | 月の上か夜、3 秒ごと (満月 2 秒ごと) に体力 1 |
// | 月の涙           | 消耗品      | レア       | —             | 30 秒のあいだ、次の傷を一度 0 に (待ち 60 秒) |
public final class MoonGear {
    public static final String DUNGEON = "moon";

    // ---------------------------------------------------------------- 段 (GearCurve.Stage と同じ形)
    public static final GearCurve.Stage STAGE = new GearCurve.Stage(DUNGEON, 54, 23.1, 30.1, 26, 20, 0.8, 24, 1100);

    // ---------------------------------------------------------------- 品の ID
    public static final String HELMET = id("moon_walker_helmet");
    public static final String CHESTPLATE = id("moon_walker_chestplate");
    public static final String LEGGINGS = id("moon_walker_leggings");
    public static final String BOOTS = id("moon_walker_boots");
    public static final String HAMMER = id("moon_scale_hammer");
    public static final String STAFF = id("moon_tide_staff");
    public static final String BOW = id("moon_waning_bow");
    public static final String DAGGER = id("moon_crescent_dagger");
    public static final String BELL = id("moon_starlight_bell_staff");
    public static final String BLADE = id("moon_waning_blade");
    public static final String RING = id("moon_full_ring");
    public static final String TEAR = id("moon_tear");
    public static final String SHARD = id("moon_fragment");
    public static final String CORE = id("moon_heart");
    public static final String RESONANCE_SHARD = id("resonance_shard_moon");

    public static final List<String> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    // ---------------------------------------------------------------- 月の核 (と位相の挑戦) の確率
    public static final float ARMOR_CHANCE = 0.25f;
    public static final float BASE_CHANCE = 0.20f;
    public static final float EXCLUSIVE_CHANCE = 0.04f;
    public static final float TEAR_CHANCE = 0.30f;
    public static final float CORE_CHANCE = 0.25f;

    // 図鑑 (11 品)
    public static final TitanGearSet SET = new TitanGearSet(DUNGEON, SHARD, CORE, List.of(
            TitanGearSet.piece(HELMET, ARMOR_CHANCE),
            TitanGearSet.piece(CHESTPLATE, ARMOR_CHANCE),
            TitanGearSet.piece(LEGGINGS, ARMOR_CHANCE),
            TitanGearSet.piece(BOOTS, ARMOR_CHANCE),
            TitanGearSet.piece(HAMMER, BASE_CHANCE),
            TitanGearSet.piece(STAFF, BASE_CHANCE),
            TitanGearSet.piece(BOW, BASE_CHANCE),
            TitanGearSet.piece(DAGGER, BASE_CHANCE),
            TitanGearSet.piece(BELL, BASE_CHANCE),
            TitanGearSet.exclusive(BLADE, EXCLUSIVE_CHANCE),
            TitanGearSet.exclusive(RING, EXCLUSIVE_CHANCE)));

    // 区画の主: 初めて倒した人に、その主の鎧の一部位を必ず (影抱き = 兜・月秤 = 胴・灯守 = すね当て・剣守 = 靴)
    public static String lordPiece(String lord) {
        return switch (lord) {
            case "cliff" -> CHESTPLATE;
            case "field" -> LEGGINGS;
            case "rift" -> BOOTS;
            default -> HELMET;
        };
    }

    // ---------------------------------------------------------------- 武器
    public static final double HAMMER_ATTACK = 12.5;
    public static final double HAMMER_SPEED = 1.6;
    public static final double HAMMER_BONUS = 0.15;
    public static final double STAFF_ATTACK = 17;
    public static final double STAFF_SPEED = 1.2;
    public static final double STAFF_SWELL = 0.20;
    public static final double STAFF_EBB_HEAL = 0.10;
    public static final double BOW_MULTIPLIER = 2.4;
    public static final double BOW_ECHO = 0.40;
    public static final int BOW_ECHO_TICKS = 20;
    public static final double DAGGER_ATTACK = 10;
    public static final double DAGGER_SPEED = 2.0;
    public static final double DAGGER_BACK = 0.30;
    public static final double BELL_ATTACK = 15;
    public static final double BELL_SPEED = 1.4;
    public static final double BELL_MARK = 0.10;
    public static final int BELL_MARK_TICKS = 80;
    public static final double BLADE_ATTACK = 13.5;
    public static final double BLADE_SPEED = 1.6;
    public static final double BLADE_ECHO = 0.5;
    public static final int BLADE_EVERY = 3;
    public static final int BLADE_ECHO_TICKS = 30;
    public static final double BLADE_ECHO_REACH = 5;

    // ---------------------------------------------------------------- 防具・装飾・消耗品
    public static final int[] ARMOR_DEFENSE = {5, 9, 7, 5};
    public static final float ARMOR_TOUGHNESS = 4f;
    public static final float ARMOR_KNOCKBACK = 0.05f;
    public static final double SET2_TOUGHNESS = 4;
    public static final double SET4_FALL_TAKEN = 0.5;
    public static final double SET4_MOON_TAKEN = 0.9;
    public static final int RING_PERIOD = 60;
    public static final int RING_PERIOD_FULL = 40;
    public static final int TEAR_TICKS = 600;
    public static final int TEAR_COOLDOWN = 1200;

    private MoonGear() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    public static int requiredLevel(GearTier tier) {
        return STAGE.level() - tier.levelMargin;
    }

    // ---------------------------------------------------------------- 一体への 1 秒あたり (固有効果の上乗せ込み。自己テストと 03 の表)
    private static double up(int enhance) {
        return 1 + 0.03 * enhance;
    }

    public static double hammerDps(int enhance) {
        return HAMMER_ATTACK * up(enhance) * HAMMER_SPEED * (1 + HAMMER_BONUS);
    }

    // 満ちと引きが交互 (満ちの +20% が半分)
    public static double staffDps(int enhance) {
        return STAFF_ATTACK * up(enhance) * STAFF_SPEED * (1 + STAFF_SWELL / 2);
    }

    // 半分の当たりが背
    public static double daggerDps(int enhance) {
        return DAGGER_ATTACK * up(enhance) * DAGGER_SPEED * (1 + DAGGER_BACK / 2);
    }

    public static double bellDps(int enhance) {
        return BELL_ATTACK * up(enhance) * BELL_SPEED * (1 + BELL_MARK);
    }

    public static double bladeDps(int enhance) {
        return BLADE_ATTACK * up(enhance) * BLADE_SPEED * (1 + BLADE_ECHO / BLADE_EVERY);
    }

    // 天秤: 相手の体力の割合が自分より高いか
    public static boolean heavier(float targetHp, float targetMax, float selfHp, float selfMax) {
        return targetMax > 0 && selfMax > 0 && targetHp / targetMax > selfHp / selfMax;
    }
}
