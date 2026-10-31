package jp.revati.skyabyss.item.wild;

import java.util.List;

import jp.revati.skyabyss.combat.BossFooting;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessoryKind;
import jp.revati.skyabyss.item.wild.WildEffect.Fx;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 野の装備の装飾の枠の品 (docs/design/51 の 6 章)。装飾 (TRINKET) は付けているあいだ表の能力が効き (WildArmorEvents.active が一式の効果と一緒に数える)、
// 護符 (CHARM) は H キーで使う (activate)。鍛えない (装飾品は 03 の 4 章のとおり強化しない)
public class WildAccessoryItem extends Item implements WildGear, Accessory {
    final WildGearTypes.Accessory row;

    public WildAccessoryItem(Item.Properties properties, WildGearTypes.Accessory row) {
        super(properties);
        this.row = row;
    }

    public WildGearTypes.Accessory row() {
        return row;
    }

    @Override
    public String region() {
        return row.region();
    }

    @Override
    public int wildLevel() {
        return row.level();
    }

    @Override
    public GearTier tier() {
        return row.tier();
    }

    @Override
    public boolean enhanceable() {
        return false;
    }

    @Override
    public AccessoryKind accessoryKind() {
        return row.charm() ? AccessoryKind.CHARM : AccessoryKind.TRINKET;
    }

    @Override
    public int cooldownTicks() {
        return Math.max(20, row.cooldown());
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
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss." + row.id() + ".desc", false);
    }

    // ---------------------------------------------------------------- 護符を使う
    @Override
    public boolean activate(ServerPlayer player, ItemStack stack) {
        if (!row.charm() || player.getCooldowns().isOnCooldown(this) || !GearLevel.meets(player, stack)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        for (Fx f : row.fx()) {
            switch (f.kind()) {
                case CHARM_HEAL -> {
                    player.heal((float) f.p(0));
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 14, 0.4, 0.6, 0.4, 0.02);
                    WildGearEvents.sound(level, player, SoundEvents.HONEY_DRINK, 1.2f);
                }
                case CHARM_FOG -> {
                    for (LivingEntity e : WildGearEvents.foes(level, player, player.position(), f.p(0))) {
                        WildGearEvents.effect(e, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(1), 1);
                        WildGearEvents.effect(e, MobEffects.BLINDNESS, f.ticks(1), 0);
                        if (e instanceof Mob m && !BossFooting.planted(e)) {
                            m.setTarget(null);
                        }
                    }
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.6, player.getZ(), 60, f.p(0) * 0.4, 0.4, f.p(0) * 0.4, 0.02);
                    WildGearEvents.sound(level, player, SoundEvents.BELL_BLOCK, 0.7f);
                }
                case CHARM_CLEANSE -> {
                    for (MobEffectInstance e : List.copyOf(player.getActiveEffects())) {
                        if (!e.getEffect().value().isBeneficial()) {
                            player.removeEffect(e.getEffect());
                        }
                    }
                    player.clearFire();
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, f.ticks(0), 0, true, true));
                    level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
                    WildGearEvents.sound(level, player, SoundEvents.BOOK_PAGE_TURN, 0.8f);
                }
                case CHARM_RALLY -> {
                    for (Player ally : WildGearEvents.withSelf(level, player, f.p(0))) {
                        WildGearEvents.buff(ally, WildGearEvents.BUFF, WildGearEvents.BUFF_AMT, f.p(1), now + f.ticks(2));
                        level.sendParticles(ParticleTypes.FLAME, ally.getX(), ally.getY() + 2.2, ally.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                    }
                    level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, player.getX(), player.getY() + 1.5, player.getZ(), 10, 0.2, 0.5, 0.2, 0.04);
                    WildGearEvents.sound(level, player, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), 1.0f);
                }
                case CHARM_LIGHT -> {
                    for (Player ally : WildGearEvents.withSelf(level, player, f.p(0))) {
                        ally.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, f.ticks(2) + 40, 0, true, false));
                    }
                    for (LivingEntity e : WildGearEvents.foes(level, player, player.position(), f.p(0))) {
                        WildGearEvents.mark(level, e, f.p(1), f.ticks(2), now);
                    }
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.5, player.getZ(), 40, 1.5, 0.8, 1.5, 0.02);
                    WildGearEvents.sound(level, player, SoundEvents.FIRECHARGE_USE, 1.6f);
                }
                case CHARM_HEART -> {
                    player.heal((float) (player.getMaxHealth() * f.p(0)));
                    level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
                    WildGearEvents.sound(level, player, SoundEvents.WARDEN_HEARTBEAT, 1.0f);
                }
                case CHARM_BLINK -> blink(level, player, f.p(0));
                case CHARM_STOP -> {
                    for (LivingEntity e : WildGearEvents.foes(level, player, player.position(), f.p(0))) {
                        boolean boss = BossFooting.planted(e);
                        WildGearEvents.effect(e, MobEffects.MOVEMENT_SLOWDOWN, boss ? f.ticks(2) : f.ticks(1), boss ? 2 : 7);
                        if (e instanceof Mob m && !boss) {
                            m.setTarget(null);
                            m.getNavigation().stop();
                        }
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY(0.6), e.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                    }
                    WildGearEvents.sound(level, player, SoundEvents.BEACON_DEACTIVATE, 1.4f);
                }
                case CHARM_HYMN -> {
                    for (Player ally : WildGearEvents.withSelf(level, player, f.p(0))) {
                        ally.heal((float) f.p(1));
                        WildGearEvents.buff(ally, WildGearEvents.HYMN, WildGearEvents.HYMN_AMT, f.p(2), now + f.ticks(3));
                    }
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.4, player.getZ(), 30, 2.0, 0.3, 2.0, 0.02);
                    WildGearEvents.sound(level, player, SoundEvents.BELL_RESONATE, 1.6f);
                }
                default -> {
                }
            }
        }
        player.getCooldowns().addCooldown(this, cooldownTicks());
        Notice.bar(player, Component.translatable("message.skyabyss.wild_charm.used", stack.getHoverName()));
        return true;
    }

    // 向いた方へ、ぶつかる手前まで跳ぶ
    static void blink(ServerLevel level, ServerPlayer player, double dist) {
        Vec3 from = player.getEyePosition();
        Vec3 dir = player.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) {
            return;
        }
        dir = dir.normalize();
        HitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, from.add(dir.scale(dist)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        double go = Math.max(0, from.distanceTo(hit.getLocation()) - 0.8);
        Vec3 to = player.position().add(dir.scale(go));
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
        player.teleportTo(to.x, to.y, to.z);
        player.fallDistance = 0;
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 1, to.z, 20, 0.3, 0.6, 0.3, 0.05);
        WildGearEvents.sound(level, player, SoundEvents.ENDERMAN_TELEPORT, 1.4f);
    }
}
