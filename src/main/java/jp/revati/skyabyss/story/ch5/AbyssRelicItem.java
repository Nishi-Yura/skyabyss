package jp.revati.skyabyss.story.ch5;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 前の私の落とし物 (遺品)。持ち物にあるあいだ効く (効果の数は AbyssRelics.Bonus)。一人に一つずつ、重ねない
public final class AbyssRelicItem extends Item {
    private final String descKey;

    public AbyssRelicItem(Properties properties, String descKey) {
        super(properties);
        this.descKey = descKey;
    }

    // 作業台で遺品をなぞって深淵の装備を作るとき (灯籠の護符・鼓動の聴き筒。data/skyabyss/recipe/abyss_*)、遺品は減らずに残る
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    // 品の一行 (墓の文の要点) と、組で効く三つの段 (二・四・六つ) を出す
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.abyss_relic.set_hint").withStyle(ChatFormatting.DARK_GRAY));
        for (AbyssRelics.Bonus b : AbyssRelics.Bonus.values()) {
            tooltip.add(Component.translatable("item.skyabyss.abyss_relic.bonus." + b.needed).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
