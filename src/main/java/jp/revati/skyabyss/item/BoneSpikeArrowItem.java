package jp.revati.skyabyss.item;

import java.util.List;
import java.util.Optional;

import jp.revati.skyabyss.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// 骨棘の矢 (アンコモン): 肋骨の射手の棘を矢じりにした矢。ふつうの矢より重く、刺さると血を流させる (出血 3秒)
public class BoneSpikeArrowItem extends ArrowItem {
    public BoneSpikeArrowItem(Properties properties) {
        super(properties.component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0xD8CDB2),
                List.of(new MobEffectInstance(ModEffects.BLEEDING, 60, 0)))));
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        Arrow arrow = new Arrow(level, shooter, ammo.copyWithCount(1), weapon);
        arrow.setBaseDamage(arrow.getBaseDamage() + 1.0);
        return arrow;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(s -> s.withColor(GearTier.UNCOMMON.color));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gear.skyabyss.tier.uncommon").withStyle(s -> s.withColor(GearTier.UNCOMMON.color)));
        tooltip.add(Component.translatable("item.skyabyss.bone_spike_arrow.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
