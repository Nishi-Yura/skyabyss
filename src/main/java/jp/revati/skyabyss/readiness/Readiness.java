package jp.revati.skyabyss.readiness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import javax.annotation.Nullable;

import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.food.FoodProperties;

// 備えの三灯 (docs/design/21 の 1.2、docs/design/11 の 9 章)。心臓の前に、自分の身・刃・糧が見える。乗ることも戦うことも妨げない。
// ゲームの状態に触れない式 (body / blade / provisions) と、プレイヤーから値を読む入口 (of) を分けてある。
// 式は 03 の 5.5 (巨神ごとの目安、GearCurve.STAGES) を基準にする。クライアントとサーバーのどちらでも、同じ Player から同じ値が出る。
//
// しきい値 (どの灯も):  0.9 以上 = 緑 / 0.7 以上 0.9 未満 = 黄 / 0.7 未満 = 赤。最大 1.1 で頭打ち (CAP)
//   身 = 0.6 × min(CAP, (着ている防御 + 強靭) ÷ (その段の防具 +0 の防御 + 強靭)) + 0.4 × min(CAP, 最大体力 ÷ 目安の体力)
//   刃 = 手持ちで一番強い武器の 1 秒あたり (砥石の上乗せ・固有の上乗せ込み) ÷ その段の武器 +0 の 1 秒あたり
//   糧 = 持ち物の回復・吸収の合計 (❤) ÷ 12❤ に、鼓動守を持っていれば +0.2
// 旅装 (P5) や砥石・鼓動守 (P3) はこのあと入る品なので、足し込み口を用意した (registerWhetstone / registerProvision / registerCharm)。
// バニラと今の mod の品だけで式が動き、旅装は防御・強靭の属性、砥石は registerWhetstone、糧と薬は FoodProperties か registerProvision で自然に数に入る
public final class Readiness {
    public static final double CAP = 1.1;
    public static final double GREEN_AT = 0.9;
    public static final double YELLOW_AT = 0.7;
    // 糧: 回復・吸収が 12❤ (24 点) で 1.0。鼓動守で +0.2
    public static final double PROVISION_GOAL_POINTS = 24;
    public static final double CHARM_BONUS = 0.2;
    // 身の重み (防具と体力)
    public static final double ARMOR_WEIGHT = 0.6;
    public static final double HEALTH_WEIGHT = 0.4;

    // 目安の体力 (❤): 岩鯨 13、潮殻 15、雷翼 17、余燼 19 (21 の 1.2)。耐久に振った分と体力増強で届く値
    public static double goalHearts(String titan) {
        return switch (titan) {
            case "stone_whale" -> 13;
            case "tide_shell" -> 15;
            case "thunder_wing" -> 17;
            case "ember" -> 19;
            default -> 0;
        };
    }

    public enum Lamp {
        BODY("body"), BLADE("blade"), PROVISIONS("provisions");

        private final String id;

        Lamp(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum Colour {
        RED, YELLOW, GREEN
    }

    // 三灯の値 (0〜CAP)
    public record Result(String titan, double body, double blade, double provisions) {
        public double value(Lamp lamp) {
            return switch (lamp) {
                case BODY -> body;
                case BLADE -> blade;
                case PROVISIONS -> provisions;
            };
        }

        public Colour colour(Lamp lamp) {
            return Readiness.colour(value(lamp));
        }

        public boolean anyRed() {
            for (Lamp l : Lamp.values()) {
                if (colour(l) == Colour.RED) {
                    return true;
                }
            }
            return false;
        }

        // 緑でない灯のうち一番低いもの。全部緑なら null
        @Nullable
        public Lamp weakest() {
            Lamp worst = null;
            for (Lamp l : Lamp.values()) {
                if (colour(l) != Colour.GREEN && (worst == null || value(l) < value(worst))) {
                    worst = l;
                }
            }
            return worst;
        }

        // 三つの灯のうち一番悪い色
        public Colour worst() {
            Colour w = Colour.GREEN;
            for (Lamp l : Lamp.values()) {
                if (colour(l).ordinal() < w.ordinal()) {
                    w = colour(l);
                }
            }
            return w;
        }
    }

    private Readiness() {
    }

    // 見知らぬ巨神 (表にない) は、全部緑として扱う
    public static Result none(String titan) {
        return new Result(titan, CAP, CAP, CAP);
    }

    public static Colour colour(double value) {
        return value >= GREEN_AT ? Colour.GREEN : value >= YELLOW_AT ? Colour.YELLOW : Colour.RED;
    }

    private static double cap(double v) {
        return Math.max(0, Math.min(CAP, v));
    }

    // ---------------------------------------------------------------- 式
    public static double body(double armor, double toughness, double maxHealthPoints, GearCurve.Stage stage, double goalHearts) {
        double armorRatio = cap((armor + toughness) / (stage.armor() + stage.toughness()));
        double healthRatio = cap(maxHealthPoints / (goalHearts * 2));
        return cap(ARMOR_WEIGHT * armorRatio + HEALTH_WEIGHT * healthRatio);
    }

    public static double blade(double dps, GearCurve.Stage stage) {
        return cap(dps / stage.dps0());
    }

    public static double provisions(double healingPoints, boolean charm) {
        return cap(healingPoints / PROVISION_GOAL_POINTS + (charm ? CHARM_BONUS : 0));
    }

    // ---------------------------------------------------------------- 足し込み口
    private static final List<ToDoubleFunction<Player>> WHETSTONES = new CopyOnWriteArrayList<>();
    private static final List<ToDoubleFunction<ItemStack>> PROVISIONS = new CopyOnWriteArrayList<>();
    private static final List<Predicate<Player>> CHARMS = new CopyOnWriteArrayList<>();

    // 砥石: 今の攻撃の上乗せ (攻撃力の点) を返す。効いていなければ 0。刃に「(攻撃 + 上乗せ) × 速さ」で入る
    public static void registerWhetstone(ToDoubleFunction<Player> bonusAttack) {
        WHETSTONES.add(bonusAttack);
    }

    // 回復・吸収の品: 1 個あたりの体力の点 (回復 + 吸収) を返す。効果で表せる品 (食べ物・薬) は登録なしで数える
    public static void registerProvision(ToDoubleFunction<ItemStack> pointsPerItem) {
        PROVISIONS.add(pointsPerItem);
    }

    // 鼓動守 (21 の 1.4): 持っていれば糧に +0.2
    public static void registerCharm(Predicate<Player> has) {
        CHARMS.add(has);
    }

    // 手段の登録口 (A7): 灯が赤・黄のとき、理由のあとに「〇〇を作ろう」と出す品の名前。登録がなければ、灯ごとの一般の言い回し
    private static final java.util.Map<Lamp, List<java.util.function.Supplier<net.minecraft.network.chat.Component>>> REMEDIES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerRemedy(Lamp lamp, java.util.function.Supplier<net.minecraft.network.chat.Component> name) {
        REMEDIES.computeIfAbsent(lamp, l -> new CopyOnWriteArrayList<>()).add(name);
    }

    // 砥石が一つでも登録されているか (登録があれば、刃の手段に「砥石で鍛える」を出す。なければ今ある炉の強化を勧める)
    public static boolean hasWhetstone() {
        return !WHETSTONES.isEmpty();
    }

    public static List<net.minecraft.network.chat.Component> remedies(Lamp lamp) {
        List<net.minecraft.network.chat.Component> out = new ArrayList<>();
        for (java.util.function.Supplier<net.minecraft.network.chat.Component> s : REMEDIES.getOrDefault(lamp, List.of())) {
            out.add(s.get());
        }
        return out;
    }

    // 身の内訳 {防具の割合, 体力の割合} (0〜CAP)。身が低い理由を言い分けるため
    public static double[] bodyParts(Player player, String titan) {
        GearCurve.Stage stage = stage(titan);
        if (stage == null) {
            return new double[] {CAP, CAP};
        }
        return new double[] {
                cap((player.getAttributeValue(Attributes.ARMOR) + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)) / (stage.armor() + stage.toughness())),
                cap(player.getMaxHealth() / (goalHearts(titan) * 2))};
    }

    // ---------------------------------------------------------------- プレイヤーから
    public static Result of(Player player, String titan) {
        GearCurve.Stage stage = stage(titan);
        if (stage == null) {
            return none(titan);
        }
        double body = body(player.getAttributeValue(Attributes.ARMOR), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                player.getMaxHealth(), stage, goalHearts(titan));
        double blade = blade(bestDps(player), stage);
        double provisions = provisions(healingPoints(player), hasCharm(player));
        return new Result(titan, body, blade, provisions);
    }

    @Nullable
    public static GearCurve.Stage stage(String titan) {
        for (GearCurve.Stage s : GearCurve.STAGES) {
            if (s.id().equals(titan)) {
                return s;
            }
        }
        return null;
    }

    private static boolean hasCharm(Player player) {
        for (Predicate<Player> c : CHARMS) {
            if (c.test(player)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> carried(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        return all;
    }

    // 持ち物で一番強い武器の 1 秒あたり (筋力・相手の防具の前。GearCurve.Stage.dps0 と同じ基準)
    public static double bestDps(Player player) {
        double whetstone = 0;
        for (ToDoubleFunction<Player> w : WHETSTONES) {
            whetstone += Math.max(0, w.applyAsDouble(player));
        }
        double best = 0;
        for (ItemStack stack : carried(player)) {
            best = Math.max(best, weaponDps(stack, whetstone));
        }
        return best;
    }

    // 一つの品の 1 秒あたり。攻撃力の修飾子を持たない品は 0。固有の上乗せ (番兵長の鉾の噴出・顎牙の出血) は GearCurve の式
    public static double weaponDps(ItemStack stack, double whetstoneAttack) {
        if (stack.isEmpty()) {
            return 0;
        }
        double[] attack = {1, 0, 1};
        double[] speed = {4, 0, 1};
        boolean[] weapon = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                weapon[0] = true;
                apply(modifier, attack);
            } else if (attribute.equals(Attributes.ATTACK_SPEED)) {
                apply(modifier, speed);
            }
        });
        if (!weapon[0]) {
            return 0;
        }
        double dmg = attack[0] * (1 + attack[1]) * attack[2] + Math.max(0, whetstoneAttack);
        double rate = Math.max(0.1, speed[0] * (1 + speed[1]) * speed[2]);
        double dps = dmg * rate;
        if (stack.getItem() instanceof SwordItem && stack.getItem() instanceof ModGear) {
            int enhance = ModGear.enhance(stack);
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (path.equals("captain_pike")) {
                dps *= GearCurve.pikeProc(enhance);
            } else if (path.equals("waterway_fang")) {
                dps += GearCurve.fangBleed(enhance);
            }
        }
        return dps;
    }

    // v = {加算, 基礎への倍率の合計, 全体への倍率の積}
    private static void apply(AttributeModifier m, double[] v) {
        switch (m.operation()) {
            case ADD_VALUE -> v[0] += m.amount();
            case ADD_MULTIPLIED_BASE -> v[1] += m.amount();
            case ADD_MULTIPLIED_TOTAL -> v[2] *= 1 + m.amount();
        }
    }

    // 持ち物の回復・吸収の合計 (体力の点。1❤ = 2 点)
    public static double healingPoints(Player player) {
        double total = 0;
        for (ItemStack stack : carried(player)) {
            if (!stack.isEmpty()) {
                total += pointsPerItem(stack) * stack.getCount();
            }
        }
        return total;
    }

    public static double pointsPerItem(ItemStack stack) {
        double points = 0;
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            for (MobEffectInstance e : potion.getAllEffects()) {
                points += effectPoints(e);
            }
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            for (FoodProperties.PossibleEffect pe : food.effects()) {
                points += effectPoints(pe.effect()) * pe.probability();
            }
        }
        for (ToDoubleFunction<ItemStack> extra : PROVISIONS) {
            points += Math.max(0, extra.applyAsDouble(stack));
        }
        return points;
    }

    // 回復の効果が与える体力の点: 即時回復 4 << 段、再生は (50 >> 段) tick に 1 点、吸収は 4 × (段 + 1)。ほかの効果は 0
    public static double effectPoints(MobEffectInstance e) {
        int amp = e.getAmplifier();
        if (e.getEffect().is(MobEffects.HEAL)) {
            return 4 << Math.min(amp, 8);
        }
        if (e.getEffect().is(MobEffects.REGENERATION)) {
            return (double) e.getDuration() / Math.max(1, 50 >> Math.min(amp, 5));
        }
        if (e.getEffect().is(MobEffects.ABSORPTION)) {
            return 4.0 * (amp + 1);
        }
        return 0;
    }
}
