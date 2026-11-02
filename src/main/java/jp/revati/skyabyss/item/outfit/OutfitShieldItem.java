package jp.revati.skyabyss.item.outfit;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;

// 旅の盾 (市の武具屋)。バニラの盾と同じに、構えると正面の傷を防ぐ。見た目は低ポリ (client/weapon の WeaponItemRenderer。珊瑚の盾と同じ描き方)
public class OutfitShieldItem extends ShieldItem {
    public OutfitShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.travel_shield.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
