package jp.revati.skyabyss.item;

import java.util.List;

import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

// このModの装備がそなえる共通の性質: 階級、どのダンジョンの品か、共鳴の炉で鍛えられるか
public interface ModGear {
    int MAX_ENHANCE = 10;
    // この段階から、装備ごとの固有効果が強まる
    int AWAKEN_LEVEL = 5;

    GearTier tier();

    // 由来のダンジョン (欠片の種類を決める)。今は岩鯨のみ
    default String dungeon() {
        return "stone_whale";
    }

    default boolean enhanceable() {
        return true;
    }

    static int enhance(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENHANCE.get(), 0);
    }

    static boolean awakened(ItemStack stack) {
        return enhance(stack) >= AWAKEN_LEVEL;
    }

    // 名前: 階級の色で、鍛えた段階を「+3」のように添える
    static Component name(ItemStack stack, Component base, GearTier tier) {
        MutableComponent name = base.copy().withStyle(s -> s.withColor(tier.color));
        int level = enhance(stack);
        if (level > 0) {
            name.append(Component.literal(" +" + level).withStyle(s -> s.withColor(tier.color).withBold(true)));
        }
        return name;
    }

    // ツールチップ: 階級と由来、説明、強化の段階
    static void tooltip(ItemStack stack, List<Component> tooltip, GearTier tier, String descKey, boolean enhanceable) {
        tooltip.add(Component.translatable("gear.skyabyss.tier." + tier.key).withStyle(s -> s.withColor(tier.color))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("gear.skyabyss.origin.stone_whale").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        // ランダムな特性。まだ決まっていない装備 (創造の持ち物や古いセーブの品) は、手にしたときに決まると書く
        if (AffixRules.eligible(stack)) {
            if (!stack.has(ModDataComponents.AFFIXES.get())) {
                tooltip.add(Component.translatable("gear.skyabyss.affix_unrolled").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            } else {
                for (Affix affix : AffixRules.affixes(stack)) {
                    tooltip.add(AffixRules.line(affix));
                }
            }
            int offers = AffixRules.offers(stack).size();
            if (offers > 0) {
                tooltip.add(Component.translatable("gear.skyabyss.reforge_pending", offers, AffixRules.MAX_TRIALS)
                        .withStyle(s -> s.withColor(AffixRules.PENDING_COLOR)));
            }
        }
        if (enhanceable) {
            int level = enhance(stack);
            tooltip.add(Component.translatable("gear.skyabyss.enhance", level, MAX_ENHANCE).withStyle(s -> s.withColor(0x9FE8FF)));
            if (level >= AWAKEN_LEVEL) {
                tooltip.add(Component.translatable("gear.skyabyss.awakened").withStyle(s -> s.withColor(0xF2B53A)));
            }
        }
    }
}
