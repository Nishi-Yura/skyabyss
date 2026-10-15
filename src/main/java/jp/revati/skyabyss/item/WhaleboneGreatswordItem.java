package jp.revati.skyabyss.item;

import java.util.List;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.SimpleTier;

// 鯨骨の大剣: 重く遅いが、一撃が重い。当たった相手は、骨に響く衝撃で足が鈍る
public class WhaleboneGreatswordItem extends SwordItem implements ModGear {
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 1800, 6.0f, 4.0f, 14,
            () -> Ingredient.of(Items.BONE_BLOCK));

    public WhaleboneGreatswordItem(Properties properties) {
        // 攻撃力 10、攻撃速度 1.0 (ふつうの剣よりかなり遅い)
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 5, -3.0f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // +5 からは衝撃が深く響き、鈍足が強く長くなる
        boolean awakened = ModGear.awakened(stack);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, awakened ? 70 : 50, awakened ? 2 : 1), attacker);
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 14, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
        }
        return super.hurtEnemy(stack, target, attacker);
    }


    @Override
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.whalebone_greatsword.desc", true);
    }
}
