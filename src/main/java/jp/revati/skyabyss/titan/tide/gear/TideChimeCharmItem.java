package jp.revati.skyabyss.titan.tide.gear;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessoryKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 潮鳴りの護符 (レア): 潮の満ち引きで鳴る貝の護符。護符の枠に付けて H キーで鳴らすと、
// 半径 5 の敵に潮の衝撃 (4 の傷、外へ押し返し、鈍足 II 3 秒)。待ち 45 秒
public class TideChimeCharmItem extends Item implements ModGear, Accessory {
    public TideChimeCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp && !Accessories.equipFromHand(sp, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public AccessoryKind accessoryKind() {
        return AccessoryKind.CHARM;
    }

    @Override
    public int cooldownTicks() {
        return TideGear.CHARM_COOLDOWN;
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        double r = TideGear.CHARM_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 2, r),
                e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= r * r)) {
            e.hurt(player.damageSources().indirectMagic(player, player), TideGear.CHARM_DAMAGE);
            Vec3 away = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1e-4) {
                away = away.normalize();
                e.knockback(1.2, -away.x, -away.z);
            }
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), player);
        }
        // 足もとから外へ広がる水の輪
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            for (double d = 1.5; d <= r; d += 1.75) {
                level.sendParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(a) * d, player.getY() + 0.2, player.getZ() + Math.sin(a) * d, 2, 0.1, 0.05, 0.1, 0.05);
            }
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1, player.getZ(), 30, 1.2, 0.6, 1.2, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        player.getCooldowns().addCooldown(this, TideGear.CHARM_COOLDOWN);
        return true;
    }

    @Override
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public String dungeon() {
        return TideGear.TITAN;
    }

    @Override
    public boolean enhanceable() {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.tide_chime_charm.desc", false);
    }
}
