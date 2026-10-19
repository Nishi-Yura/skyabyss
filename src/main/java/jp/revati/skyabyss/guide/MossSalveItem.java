package jp.revati.skyabyss.guide;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 苔の軟膏 (一章の薬。21 の 1.2): 鈍足を治し、再生 I を 8 秒。再生は FoodProperties の効果 (備えの糧の灯がそのまま数える)、鈍足を治すのはここ
public class MossSalveItem extends Item {
    public MossSalveItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.moss_salve.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
