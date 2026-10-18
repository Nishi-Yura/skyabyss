package jp.revati.skyabyss.item.affix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

// 特性の決まりごと (抽選・再鍛造・表示)。ゲームの状態に触れないので、自己テストからそのまま呼べる
//  - 付くのはレア以上の武器と防具だけ。護符・護石・補強板 (持っているだけで効く品) には付けない
//  - 数: レア 1〜2、伝説 2〜3。同じ種類は一つの装備に二度付かない
//  - 再鍛造: 結晶を払って候補を一組ずつ作る (最大 3 組)。今の特性か候補のどれかを選んで確定する
public final class AffixRules {
    public static final int MAX_TRIALS = 3;
    // 再鍛造の候補一組の費用 (共鳴の結晶)
    public static final int TRIAL_CRYSTALS = 1;
    // 少ないほうの数になる確率 (レアなら 1 個、伝説なら 2 個)
    public static final float FEWER_CHANCE = 0.6f;
    // 守り (受ける傷の軽減) の合計の上限
    public static final double WARD_CAP = 0.10;

    // 特性の行の色: 控えめな淡い青灰、印は暗い青緑
    public static final int LINE_COLOR = 0xB4CCD8;
    public static final int MARK_COLOR = 0x4F8C96;
    public static final int PENDING_COLOR = 0xB89CFF;

    private AffixRules() {
    }

    // ---------------------------------------------------------------- 対象
    @Nullable
    public static AffixType.Slot slotOf(Item item) {
        if (!(item instanceof ModGear gear) || (gear.tier() != GearTier.RARE && gear.tier() != GearTier.LEGENDARY)) {
            return null;
        }
        if (item instanceof SwordItem) {
            return AffixType.Slot.WEAPON;
        }
        // 盾 (珊瑚の盾) は防具の特性。左手に持っているあいだ効く (AffixEvents)
        if (item instanceof ArmorItem || item instanceof net.minecraft.world.item.ShieldItem) {
            return AffixType.Slot.ARMOR;
        }
        return null;
    }

    public static boolean eligible(ItemStack stack) {
        return !stack.isEmpty() && slotOf(stack.getItem()) != null;
    }

    public static boolean legendary(ItemStack stack) {
        return stack.getItem() instanceof ModGear gear && gear.tier() == GearTier.LEGENDARY;
    }

    public static int minCount(GearTier tier) {
        return tier == GearTier.LEGENDARY ? 2 : tier == GearTier.RARE ? 1 : 0;
    }

    public static int maxCount(GearTier tier) {
        return tier == GearTier.LEGENDARY ? 3 : tier == GearTier.RARE ? 2 : 0;
    }

    public static List<AffixType> pool(AffixType.Slot slot) {
        List<AffixType> pool = new ArrayList<>();
        for (AffixType t : AffixType.values()) {
            if (t.slot == slot) {
                pool.add(t);
            }
        }
        return pool;
    }

    // ---------------------------------------------------------------- 抽選
    public static List<Affix> roll(AffixType.Slot slot, GearTier tier, RandomSource random) {
        int count = random.nextFloat() < FEWER_CHANCE ? minCount(tier) : maxCount(tier);
        boolean legendary = tier == GearTier.LEGENDARY;
        List<AffixType> pool = pool(slot);
        List<Affix> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            AffixType pick = weighted(pool, random);
            pool.remove(pick);
            out.add(new Affix(pick, value(pick, legendary, random)));
        }
        return List.copyOf(out);
    }

    public static List<Affix> roll(ItemStack stack, RandomSource random) {
        AffixType.Slot slot = slotOf(stack.getItem());
        if (slot == null) {
            return List.of();
        }
        return roll(slot, ((ModGear) stack.getItem()).tier(), random);
    }

    private static AffixType weighted(List<AffixType> pool, RandomSource random) {
        int total = 0;
        for (AffixType t : pool) {
            total += t.weight;
        }
        int r = random.nextInt(total);
        for (AffixType t : pool) {
            r -= t.weight;
            if (r < 0) {
                return t;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // 幅の中で刻みごとに等しい確率
    public static double value(AffixType type, boolean legendary, RandomSource random) {
        double min = type.min(legendary);
        double max = type.max(legendary);
        int steps = (int) Math.round((max - min) / type.step);
        return type.round(min + random.nextInt(steps + 1) * type.step);
    }

    // 付いていなければ抽選して付ける (第1段階のセーブにある装備、コマンドで出した装備)。付けたら true
    public static boolean ensureRolled(ItemStack stack, RandomSource random) {
        if (!eligible(stack) || stack.has(ModDataComponents.AFFIXES.get())) {
            return false;
        }
        stack.set(ModDataComponents.AFFIXES.get(), roll(stack, random));
        return true;
    }

    // 新しく出た装備に付けて返す (報酬・交換)
    public static ItemStack withAffixes(ItemStack stack, RandomSource random) {
        ensureRolled(stack, random);
        return stack;
    }

    public static List<Affix> affixes(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.AFFIXES.get(), List.of());
    }

    // その装備の、ある種類の値 (付いていなければ 0)
    public static double value(ItemStack stack, AffixType type) {
        double sum = 0;
        for (Affix a : affixes(stack)) {
            if (a.kind() == type) {
                sum += a.value();
            }
        }
        return sum;
    }

    // 正しい特性か (知らない種類・重なり・幅の外・数の外がない)。自己テストとコマンドで使う
    public static boolean valid(ItemStack stack, List<Affix> list) {
        AffixType.Slot slot = slotOf(stack.getItem());
        if (slot == null) {
            return list.isEmpty();
        }
        GearTier tier = ((ModGear) stack.getItem()).tier();
        if (list.size() < minCount(tier) || list.size() > maxCount(tier)) {
            return false;
        }
        boolean legendary = tier == GearTier.LEGENDARY;
        List<AffixType> seen = new ArrayList<>();
        for (Affix a : list) {
            AffixType t = a.kind();
            if (t == null || t.slot != slot || seen.contains(t)) {
                return false;
            }
            if (a.value() < t.min(legendary) - 1e-9 || a.value() > t.max(legendary) + 1e-9) {
                return false;
            }
            seen.add(t);
        }
        return true;
    }

    // ---------------------------------------------------------------- 再鍛造
    public static List<List<Affix>> offers(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.REFORGE_OFFERS.get(), List.of());
    }

    public static boolean canTrial(ItemStack stack) {
        return eligible(stack) && offers(stack).size() < MAX_TRIALS;
    }

    // 候補を一組足す (費用は呼ぶ側が払う)
    public static List<Affix> addTrial(ItemStack stack, RandomSource random) {
        ensureRolled(stack, random);
        List<Affix> offer = roll(stack, random);
        List<List<Affix>> next = new ArrayList<>(offers(stack));
        next.add(offer);
        stack.set(ModDataComponents.REFORGE_OFFERS.get(), List.copyOf(next));
        return offer;
    }

    // 確定する。choice が -1 なら今の特性のまま。候補は消える
    public static boolean confirm(ItemStack stack, int choice) {
        List<List<Affix>> offers = offers(stack);
        if (offers.isEmpty() || choice < -1 || choice >= offers.size()) {
            return false;
        }
        if (choice >= 0) {
            stack.set(ModDataComponents.AFFIXES.get(), offers.get(choice));
        }
        stack.remove(ModDataComponents.REFORGE_OFFERS.get());
        return true;
    }

    // ---------------------------------------------------------------- 表示
    // 「攻撃力 +1.2」「技の待ち −6%」
    public static MutableComponent describe(Affix affix) {
        AffixType t = affix.kind();
        if (t == null) {
            return Component.translatable("affix.skyabyss.unknown");
        }
        return Component.translatable(t.translationKey(), number(t, affix.value()));
    }

    public static String number(AffixType t, double v) {
        if (t == AffixType.VIGOR) {
            return jp.revati.skyabyss.Hearts.of(v);
        }
        if (t.percent) {
            double pct = v * 100;
            return Math.abs(pct - Math.round(pct)) < 1e-6 ? String.valueOf(Math.round(pct)) : String.format(Locale.ROOT, "%.1f", pct);
        }
        return Math.abs(v - Math.round(v)) < 1e-6 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ツールチップの一行: 「 ◇ 攻撃力 +1.2」
    public static MutableComponent line(Affix affix) {
        return Component.literal(" ◇ ").withStyle(s -> s.withColor(MARK_COLOR))
                .append(describe(affix).withStyle(s -> s.withColor(LINE_COLOR)));
    }
}
