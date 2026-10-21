package jp.revati.skyabyss.facility;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 鐘銭。鐘の国の古い銭 (都の店で使う。エメラルドの代わり)
public class BellCoinItem extends Item {
    public BellCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.bell_coin.desc").withStyle(s -> s.withColor(0x7FD6D0).withItalic(true)));
        tooltip.add(Component.translatable("item.skyabyss.bell_coin.use").withStyle(ChatFormatting.GRAY));
    }
}
