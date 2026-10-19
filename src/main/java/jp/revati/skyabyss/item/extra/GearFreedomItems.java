package jp.revati.skyabyss.item.extra;

import java.util.List;

import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

// 自由度の三つの品 (欠片・アスペクトの芯・技の修飾ルーン)。穴へ差す・刻むのは共鳴の炉 (forge/ForgeRules)
public final class GearFreedomItems {
    private GearFreedomItems() {
    }

    // 属性の欠片 (岩・風・潮・灰・骨)。巨神の素材から作る (data/skyabyss/recipe/socket_shard_*.json)
    public static class ElementShard extends Item {
        public final Element element;

        public ElementShard(Element element) {
            super(new Item.Properties().rarity(Rarity.UNCOMMON));
            this.element = element;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.socket_shard.desc", Component.translatable(element.nameKey()),
                    Component.translatable(element.tree.nameKey())).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            tooltip.add(Component.literal(" ◇ ").withStyle(s -> s.withColor(AffixRules.MARK_COLOR))
                    .append(Component.translatable("item.skyabyss.socket_shard.weapon").withStyle(ChatFormatting.DARK_GRAY))
                    .append(AffixRules.describe(new Affix(element.weapon, element.weaponValue)).withStyle(s -> s.withColor(AffixRules.LINE_COLOR))));
            tooltip.add(Component.literal(" ◇ ").withStyle(s -> s.withColor(AffixRules.MARK_COLOR))
                    .append(Component.translatable("item.skyabyss.socket_shard.armor").withStyle(ChatFormatting.DARK_GRAY))
                    .append(AffixRules.describe(new Affix(element.armor, element.armorValue)).withStyle(s -> s.withColor(AffixRules.LINE_COLOR))));
            tooltip.add(Component.translatable("item.skyabyss.socket_shard.tag", Math.round(Element.TAG_POWER * 100)).withStyle(s -> s.withColor(GearFreedom.TAG_COLOR)));
        }
    }

    // アスペクトの芯: 伝説の書き換えを一つ取り出したもの。レアの武器・防具へ刻む (1 装備に 1 つ)
    public static class AspectCore extends Item {
        public AspectCore() {
            super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        }

        @Override
        public Component getName(ItemStack stack) {
            Rune r = GearFreedom.coreRune(stack);
            Component base = super.getName(stack);
            return r == null ? base : base.copy().append(Component.literal(" ─ ").append(Component.translatable(r.nameKey()))).withStyle(s -> s.withColor(GearFreedom.ASPECT_COLOR));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            Rune r = GearFreedom.coreRune(stack);
            tooltip.add(Component.translatable("item.skyabyss.aspect_core.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            if (r != null) {
                tooltip.add(Component.translatable("tooltip.skyabyss.legend_rewrite", Component.translatable(r.skillNameKey()), Component.translatable(r.nameKey()))
                        .withStyle(s -> s.withColor(GearFreedom.ASPECT_COLOR)));
                tooltip.add(Component.translatable(r.effectKey()).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // 技の修飾ルーン: 一つの技に、力 ×1.10〜1.30 か待ち ×0.80〜0.90。穴へ差す (1 つの技に 1 枚)
    public static class SkillRune extends Item {
        public SkillRune() {
            super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        }

        @Override
        public Component getName(ItemStack stack) {
            RuneData d = stack.get(ModDataComponents.SKILL_RUNE.get());
            Skill s = d == null ? null : Skill.byId(d.skill());
            Component base = super.getName(stack);
            return s == null ? base : base.copy().append(Component.literal(" ─ ").append(Component.translatable(s.nameKey())));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.skill_rune.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            RuneData d = stack.get(ModDataComponents.SKILL_RUNE.get());
            if (d != null && d.valid()) {
                tooltip.add(GearFreedom.runeLine(d));
            }
            tooltip.add(Component.translatable("item.skyabyss.skill_rune.cap").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static ItemStack rune(RuneData data) {
        ItemStack stack = new ItemStack(jp.revati.skyabyss.registry.ModItems.SKILL_RUNE.get());
        stack.set(ModDataComponents.SKILL_RUNE.get(), data);
        return stack;
    }

    public static RuneData runeOf(ItemStack stack) {
        return stack.get(ModDataComponents.SKILL_RUNE.get());
    }
}
