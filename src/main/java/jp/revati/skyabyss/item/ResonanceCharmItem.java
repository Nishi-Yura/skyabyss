package jp.revati.skyabyss.item;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 共鳴の護符: 握りしめると共鳴石の歌が響き、傷がふさがり、しばらく身を守る。使うと1分休む
public class ResonanceCharmItem extends Item implements ModGear {
    private static final int COOLDOWN = 20 * 60;

    public ResonanceCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 0));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.2f);
            ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.03);
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
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
    public boolean enhanceable() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.resonance_charm.desc", false);
    }
}
