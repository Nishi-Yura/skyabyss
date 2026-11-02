package jp.revati.skyabyss.story.ch6.gear;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.TitanGearSet;

// 六章「月」の装備の表と数値 (docs/design/03 の 12.2)。深淵のあとの段 (推奨 Lv 54、武器 +0 は 1 秒あたり 23.1・+10 で 30.1、
// 防具 26 / 強靭 20)。必要 Lv はどれも 50 (レベルの上限。ヒロイックも 52 にしない)。段は巨神の段の表に入れず、ここの STAGE に置く (深淵と同じ)。
//
// 職の武器は 6 職それぞれにレア一本とヒロイック一本 (2026-10-31 の声「全部レアとヒロイックでそれぞれひとつずつ」)。
// レア 6 本と残月の太刀は図鑑 (SET)、ほかのヒロイック 5 本は巨神のヒロイック武器と同じく月の核の別枠 (HEROICS。一本ずつ HEROIC_CHANCE)。
// | 品               | 種類        | 階級       | 1 秒あたり (+0 / +10) | 固有効果 |
// | 月渡りの装束 4   | 防具 (立体) | レア       | —             | 防御 5/9/7/5 = 26、強靭 4 ×4。2 部位で強靭 +4。4 部位「月の足」: 落下の傷 ×0.5、月の上で受ける傷 ×0.9 |
// | 月光の長剣       | 剣 (戦士)   | レア       | 23.0 / 29.9   | 月の上か夜は傷 +15% |
// | 月秤の鎚         | 鎚          | レア       | 23.0 / 29.9   | 相手の体力の割合が自分より高いと傷 +15% |
// | 満ち引きの杖     | 杖          | レア       | 22.4 / 29.2   | 満ちと引きが打つたびに入れ替わる (満ち +20% / 引き 傷の 10% を体力に) |
// | 下弦の弓         | 弓          | レア       | 19.7 (近接の 0.85 倍の目安) | 当たった相手に 1 秒後に残像の矢 (傷の 40%) |
// | 欠け月の短剣     | 短剣        | レア       | 23.0 / 29.9   | 背から刺すと +30% (表の値は半分の当たりが背) |
// | 星灯りの鈴杖     | 鈴杖        | レア       | 23.1 / 30.0   | 打った相手に星の印 4 秒 (だれの傷も +10%) |
// | 残月の太刀       | 太刀        | ヒロイック | 25.2 / 32.8   | 3 度に 1 度、1.5 秒後に残像の太刀 (攻撃力の 50%) |
// | 望月の大鎚       | 鎚          | ヒロイック | 25.2 / 32.8   | 4 度目ごとに満月の一撃 (傷 ×1.6、まわりを押す) |
// | 朔の杖           | 杖          | ヒロイック | 25.3 / 32.9   | 打った相手を 2 秒、影に縛る (遅く)。縛られた相手への傷 +17% |
// | 星落ちの弓       | 弓          | ヒロイック | 21.3          | 当たった所へ 0.8 秒後に星が落ちる (まわり 2.5 の敵に傷の 40%) |
// | 朔影の短剣       | 短剣        | ヒロイック | 25.2 / 32.8   | 打つたびに相手に影の印。3 つで弾けて攻撃力の 90% |
// | 月暈の鈴杖       | 鈴杖        | ヒロイック | 25.3 / 32.9   | 打つと月暈 5 秒: 自分の傷 +10%、打つたびにまわり 6 の仲間の体力 1 |
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
    public static final String SWORD = id("moon_light_sword");
    public static final String HAMMER = id("moon_scale_hammer");
    public static final String STAFF = id("moon_tide_staff");
    public static final String BOW = id("moon_waning_bow");
    public static final String DAGGER = id("moon_crescent_dagger");
    public static final String BELL = id("moon_starlight_bell_staff");
    public static final String BLADE = id("moon_waning_blade");
    public static final String FULL_HAMMER = id("moon_full_hammer");
    public static final String NEW_STAFF = id("moon_new_staff");
    public static final String STARFALL_BOW = id("moon_starfall_bow");
    public static final String UMBRA_DAGGER = id("moon_umbra_dagger");
    public static final String HALO_BELL = id("moon_halo_bell_staff");
    // 月の核の別枠のヒロイック武器 (図鑑に入れない。巨神のヒロイック武器 item/HeroicGear と同じ扱い)
    public static final List<String> HEROICS = List.of(FULL_HAMMER, NEW_STAFF, STARFALL_BOW, UMBRA_DAGGER, HALO_BELL);
    public static final float HEROIC_CHANCE = 0.02f;
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

    // 図鑑 (12 品)
    public static final TitanGearSet SET = new TitanGearSet(DUNGEON, SHARD, CORE, List.of(
            TitanGearSet.piece(HELMET, ARMOR_CHANCE),
            TitanGearSet.piece(CHESTPLATE, ARMOR_CHANCE),
            TitanGearSet.piece(LEGGINGS, ARMOR_CHANCE),
            TitanGearSet.piece(BOOTS, ARMOR_CHANCE),
            TitanGearSet.piece(SWORD, BASE_CHANCE),
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

    // ---------------------------------------------------------------- 武器 (レア)
    public static final double SWORD_ATTACK = 12.5;
    public static final double SWORD_SPEED = 1.6;
    public static final double SWORD_MOONLIT = 0.15;
    public static final double HAMMER_ATTACK = 12.5;
    public static final double HAMMER_SPEED = 1.6;
    public static final double HAMMER_BONUS = 0.15;
    public static final double STAFF_ATTACK = 17;
    public static final double STAFF_SPEED = 1.2;
    public static final double STAFF_SWELL = 0.20;
    public static final double STAFF_EBB_HEAL = 0.10;
    public static final double BOW_MULTIPLIER = 2.0;
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
    // ヒロイック (月の核の別枠)
    public static final double FULL_HAMMER_ATTACK = 13.7;
    public static final double FULL_HAMMER_SPEED = 1.6;
    public static final int FULL_HAMMER_EVERY = 4;
    public static final double FULL_HAMMER_BLOW = 0.6;
    public static final double NEW_STAFF_ATTACK = 18;
    public static final double NEW_STAFF_SPEED = 1.2;
    public static final int NEW_STAFF_BIND_TICKS = 40;
    public static final double NEW_STAFF_BOUND = 0.17;
    public static final double STARFALL_MULTIPLIER = 2.1;
    public static final double STARFALL_SHARE = 0.40;
    public static final int STARFALL_TICKS = 16;
    public static final double STARFALL_RADIUS = 2.5;
    public static final double UMBRA_ATTACK = 9.7;
    public static final double UMBRA_SPEED = 2.0;
    public static final int UMBRA_MARKS = 3;
    public static final double UMBRA_BURST = 0.9;
    public static final double HALO_ATTACK = 16.4;
    public static final double HALO_SPEED = 1.4;
    public static final int HALO_TICKS = 100;
    public static final double HALO_BONUS = 0.10;
    public static final double HALO_RADIUS = 6;

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

    // レベルの上限は 50 (GrowthRules.MAX_LEVEL。その先は転生)。段の推奨 Lv 54 から引くとヒロイックが 52 になり着けないので、上限で止める
    public static int requiredLevel(GearTier tier) {
        return Math.min(jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL, STAGE.level() - tier.levelMargin);
    }

    // ---------------------------------------------------------------- 一体への 1 秒あたり (固有効果の上乗せ込み。自己テストと 03 の表)
    private static double up(int enhance) {
        return 1 + 0.03 * enhance;
    }

    // 月の上で戦う前提 (月の上か夜)
    public static double swordDps(int enhance) {
        return SWORD_ATTACK * up(enhance) * SWORD_SPEED * (1 + SWORD_MOONLIT);
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

    public static double fullHammerDps(int enhance) {
        return FULL_HAMMER_ATTACK * up(enhance) * FULL_HAMMER_SPEED * (1 + FULL_HAMMER_BLOW / FULL_HAMMER_EVERY);
    }

    // 打つ間 (0.83 秒) が縛り (2 秒) より短いので、続けて打てばいつも縛られている
    public static double newStaffDps(int enhance) {
        return NEW_STAFF_ATTACK * up(enhance) * NEW_STAFF_SPEED * (1 + NEW_STAFF_BOUND);
    }

    public static double umbraDps(int enhance) {
        return UMBRA_ATTACK * up(enhance) * UMBRA_SPEED * (1 + UMBRA_BURST / UMBRA_MARKS);
    }

    public static double haloDps(int enhance) {
        return HALO_ATTACK * up(enhance) * HALO_SPEED * (1 + HALO_BONUS);
    }

    // 天秤: 相手の体力の割合が自分より高いか
    public static boolean heavier(float targetHp, float targetMax, float selfHp, float selfMax) {
        return targetMax > 0 && selfMax > 0 && targetHp / targetMax > selfHp / selfMax;
    }
}
