package jp.revati.skyabyss.guide;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 真珠の粉薬 (二章の薬。21 の 1.2): 飲み下すと即時に 3❤ 回復し、浮遊と鈍足を治す。食べる時間は短い (fast)。
// 回復は食べ物の効果ではないので、備えの糧の点は Ch2Gear の registerProvision で足す (6 点)
public class PearlPowderItem extends Item {
    public PearlPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            Ch2Gear.applyPowder(entity);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.pearl_powder.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
