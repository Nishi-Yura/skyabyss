package jp.revati.skyabyss.item.wild;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.item.wild.WildEffect.Fx;
import jp.revati.skyabyss.item.wild.WildEffect.Kind;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// 野の装飾の個体差 (2026-11-08 の声「装飾、同じの手に入っても使わないよね」→ 個体差 + 分解に決まり)。
// Wynncraft の個体値のように、品が初めて持ち物に入ったとき効き目の強さを 70〜130% で振る (二つの一様の平均。端ほど出にくい)。
// 振るのは「強さ」の数だけ (確率・上乗せ・量)。しきい値・長さ・待ちは振らない (下の STRENGTH)。
// 振る前の品 (古い品・表示用) は 100%。余った品は共鳴の炉で分解できる (forge/ForgeRules.dismantleYield)
public final class AccessoryRolls {
    public static final String KEY = "skyabyss_roll";
    public static final int MIN = 70;
    public static final int MAX = 130;
    // 段 (2026-11-10 の選び a_grow): 同じ品を共鳴の炉で重ねると段が上がり、強さが段ごとに +STAGE_STEP%。段 I〜V
    public static final String STAGE_KEY = "skyabyss_stage";
    public static final int MAX_STAGE = 5;
    public static final int STAGE_STEP = 10;

    private AccessoryRolls() {
    }

    // 振った強さ (%)。振っていなければ 100
    public static int roll(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        int v = d == null ? 0 : d.copyTag().getInt(KEY);
        return v <= 0 ? 100 : v;
    }

    public static boolean rolled(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getInt(KEY) > 0;
    }

    // 段 (1〜5。重ねていなければ 1)
    public static int stage(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        int v = d == null ? 0 : d.copyTag().getInt(STAGE_KEY);
        return Math.max(1, Math.min(MAX_STAGE, v));
    }

    public static void setStage(ItemStack s, int stage) {
        int v = Math.max(1, Math.min(MAX_STAGE, stage));
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(STAGE_KEY, v));
    }

    // 効き目に掛ける強さ (%): 振った強さ + 段の上乗せ
    public static int effective(ItemStack s) {
        return roll(s) + STAGE_STEP * (stage(s) - 1);
    }

    public static String roman(int n) {
        return switch (n) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "I";
        };
    }

    public static int draw(RandomSource r) {
        double t = (r.nextDouble() + r.nextDouble()) / 2.0;
        return MIN + (int) Math.round(t * (MAX - MIN));
    }

    public static void set(ItemStack s, int percent) {
        int v = Math.max(MIN, Math.min(MAX, percent));
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(KEY, v));
    }

    // まだなら振る。振ったか
    public static boolean ensure(ItemStack s, RandomSource r) {
        if (rolled(s)) {
            return false;
        }
        set(s, draw(r));
        return true;
    }

    // 種類ごとの「強さ」の数の位置 (ほかの数は振らない)
    public static int[] strength(Kind k) {
        return switch (k) {
            case MAX_HEALTH, MAX_MANA, MOVE_SPEED, TOUGHNESS, MANA_REGEN, COOLDOWN, FIRE_TAKEN, FALL_TAKEN, LIGHTNING_TAKEN, REFLECT -> new int[] {0};
            case CRIT -> new int[] {0};
            case BERSERK, DARK_BONUS, EXECUTE, FAR_BONUS, OPENER -> new int[] {1};
            default -> strengthByName(k.name());
        };
    }

    // 表に新しく足した種類 (名前で見る)。知らない種類は振らない
    private static int[] strengthByName(String n) {
        return switch (n) {
            case "REGEN_TICK", "SWIM", "THORNS", "BURN_BONUS_SET", "THORN_BOLT", "CHARM_HEAL", "CHARM_HEART", "CHARM_BLINK" -> new int[] {0};
            case "GRIT" -> new int[] {0, 1};
            case "MOSS_STILL", "HURT_MANA", "KILL_COOLDOWN", "BONE_WARD", "AIR_STRIKE", "WET_MANA", "STORM_DAMAGE", "BURNING_RAGE",
                    "WATER_REGEN", "HURT_IGNITE" -> new int[] {0};
            case "FALL_SHOCK", "HURT_BURST" -> new int[] {1};
            case "CHARM_RALLY" -> new int[] {1};
            default -> new int[0];
        };
    }

    // 振った強さを掛けた効き目
    public static List<Fx> apply(ItemStack s, List<Fx> fx) {
        int pct = effective(s);
        if (pct == 100) {
            return fx;
        }
        double k = pct / 100.0;
        List<Fx> out = new ArrayList<>(fx.size());
        for (Fx f : fx) {
            double[] p = f.p().clone();
            for (int i : strength(f.kind())) {
                if (i < p.length) {
                    p[i] *= k;
                    // 受ける傷の倍率は −100% より下げない (治りに変わらない)
                    if (f.kind().name().endsWith("_TAKEN")) {
                        p[i] = Math.max(-1.0, p[i]);
                    }
                }
            }
            out.add(new Fx(f.kind(), p));
        }
        return out;
    }

    // 説明の一行 (色: 低いは赤み、高いは緑、125% 以上は金)
    public static void tooltip(ItemStack s, List<Component> out) {
        if (!rolled(s) && stage(s) <= 1) {
            return;
        }
        int pct = effective(s);
        int color = pct >= 125 ? 0xF2C14E : pct >= 110 ? 0x7BD66B : pct < 85 ? 0xD9779A : 0xC8CCD6;
        out.add(Component.translatable("tooltip.skyabyss.accessory_roll", pct, roman(stage(s))).withStyle(x -> x.withColor(color)));
    }
}
