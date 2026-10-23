package jp.revati.skyabyss.abyss.gear;

import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.abyss.arts.AbyssLight;
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

// 灯籠の護符 (レア): 心臓の間の灯籠を小さく写した護符。護符の枠に付けて H キーで使うと、足もとに灯りを置く
// (半径 8、60 秒。光の術の灯りと同じ深淵の灯り = 層の主・番人に刃が通り、闇のたまりも払う)。半径 6 の敵を 8 秒光らせて押し返し、
// 自分に暗視 30 秒。待ち 45 秒。地上でも使える (灯りは灯り番の剣の「灯りの中」にも数える)。光の術の灯りの数 (一人 3 つ) とは別に数える
public class LanternCharmItem extends Item implements ModGear, Accessory {
    public LanternCharmItem(Properties properties) {
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
        return AbyssGear.CHARM_COOLDOWN;
    }

    // 護符の灯りの持ち主 (光の術の灯りと数を分ける)
    public static UUID lampOwner(UUID player) {
        return UUID.nameUUIDFromBytes(("skyabyss:lantern_charm:" + player).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long until = player.server.overworld().getGameTime() + AbyssGear.CHARM_LIGHT_TICKS;
        AbyssLight.add(level, player.blockPosition().above(), AbyssGear.CHARM_LIGHT_RADIUS, until, lampOwner(player.getUUID()));
        double r = AbyssGear.CHARM_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 2, r),
                e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= r * r)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, AbyssGear.CHARM_GLOW_TICKS, 0, true, false));
            Vec3 away = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1e-4) {
                away = away.normalize().scale(0.9);
                e.push(away.x, 0.25, away.z);
                e.hurtMarked = true;
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, AbyssGear.CHARM_NIGHT_VISION_TICKS, 0, true, false, true));
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40;
            level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX() + Math.cos(a) * 1.4, player.getY() + 1.0, player.getZ() + Math.sin(a) * 1.4,
                    0, Math.cos(a), 0.05, Math.sin(a), 0.25);
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.6, player.getZ(), 24, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.6f);
        player.getCooldowns().addCooldown(this, AbyssGear.CHARM_COOLDOWN);
        return true;
    }

    @Override
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public String dungeon() {
        return AbyssGear.DUNGEON;
    }

    @Override
    public int requiredLevel() {
        return AbyssGear.requiredLevel(tier());
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
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.abyss_lantern_charm.desc", false);
    }
}
