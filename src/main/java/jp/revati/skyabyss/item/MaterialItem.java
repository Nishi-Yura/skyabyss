package jp.revati.skyabyss.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 素材 (岩鯨の欠片、共鳴の結晶、共鳴の欠片、岩鯨の心核)。名前の色と説明だけを持つ
public class MaterialItem extends Item {
    private final TextColor color;
    private final String descKey;
    private final boolean foil;

    public MaterialItem(Properties properties, int rgb, String descKey, boolean foil) {
        super(properties);
        this.color = TextColor.fromRgb(rgb);
        this.descKey = descKey;
        this.foil = foil;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(s -> s.withColor(color));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
