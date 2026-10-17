package jp.revati.skyabyss.titan.ember.gear;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessoryKind;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 熾りの護符 (レア): 熾を一つ抱いた玄武岩の護符。護符の枠に付けて H キーで使うと、体にたまった熱を護符が吸い、
// まわりへ吐き出す: 自分の熱を 0 にし、半径 5 の敵に 6 の傷と 4 秒の燃焼 (火に強い相手にはくすぶりの印)。待ち 50 秒。
// 背中の外でも使える (熱が 0 のまま、敵を焼くだけ)
public class KindlingCharmItem extends Item implements ModGear, Accessory {
    public KindlingCharmItem(Properties properties) {
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
        return EmberGear.CHARM_COOLDOWN;
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        float drawn = EmberHeat.get(player);
        EmberHeat.cool(player, EmberHeat.MAX);
        double r = EmberGear.CHARM_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 2, r),
                e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= r * r)) {
            e.hurt(player.damageSources().indirectMagic(player, player), EmberGear.CHARM_DAMAGE);
            EmberGearEvents.smolder(e, EmberGear.CHARM_BURN_TICKS);
        }
        // 足もとから外へ広がる熾の輪。吸った熱が多いほど火の粉が多い
        int sparks = 24 + (int) (drawn * 0.4f);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            level.sendParticles(ParticleTypes.FLAME, player.getX() + cx * 1.2, player.getY() + 0.15, player.getZ() + cz * 1.2, 0, cx, 0.02, cz, 0.3);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.LAVA, player.getX() + cx * r * 0.8, player.getY() + 0.3, player.getZ() + cz * r * 0.8, 1, 0.1, 0.1, 0.1, 0);
            }
        }
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.2, player.getZ(), sparks, 0.5, 0.7, 0.5, 0.04);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.8f);
        player.getCooldowns().addCooldown(this, EmberGear.CHARM_COOLDOWN);
        return true;
    }

    @Override
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public String dungeon() {
        return EmberGear.TITAN;
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
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.kindling_charm.desc", false);
    }
}
