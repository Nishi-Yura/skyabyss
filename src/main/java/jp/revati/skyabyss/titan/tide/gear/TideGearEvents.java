package jp.revati.skyabyss.titan.tide.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 潮殻の装備のうち、持ち主の状態で変わる効果 (03 の 11章):
//  - 潮殻の鎧: 2 部位で強靭 +3。4 部位で「潮の殻」(受ける傷 ×0.85、水に触れていれば ×0.8。耐性の効果があるときは重ねない)、
//    水の中ではイルカの好意と水中呼吸
//  - 潮招きの大鋏: 水の中で攻撃速度 +20%
//  - 珊瑚の盾: 構えている間、防げなかった傷を 10% 減らす (+1%/段、上限 20%)。近接で防いだ相手に棘、+5 で鈍足
//  - 月潮の真珠: 装飾の枠に付けていて、体力が 30% を切ったら泡の盾 (吸収 8、6 秒)。待ち 90 秒
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideGearEvents {
    static final ResourceLocation SET2 = id("tide_set2_toughness");
    private static final ResourceLocation CLAW_WATER = id("tide_claw_water");

    private TideGearEvents() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    // 4 部位の潮の殻で受ける傷に掛ける値 (着ていない・耐性の効果があるときは 1)
    public static double setTaken(LivingEntity entity) {
        if (TideShellArmorItem.piecesWorn(entity) < 4 || entity.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            return 1;
        }
        return entity.isInWaterRainOrBubble() ? TideGear.SET4_TAKEN_WET : TideGear.SET4_TAKEN;
    }

    // 珊瑚の盾を構えているときの、防げなかった傷に掛ける値
    public static double shieldTaken(LivingEntity entity) {
        if (!entity.isBlocking()) {
            return 1;
        }
        ItemStack use = entity.getUseItem();
        if (!(use.getItem() instanceof CoralShieldItem) || !GearLevel.usable(entity, use)) {
            return 1;
        }
        return 1 - CoralShieldItem.guard(use);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getNewDamage() <= 0) {
            return;
        }
        double m = setTaken(entity) * shieldTaken(entity);
        if (m < 1) {
            event.setNewDamage((float) (event.getNewDamage() * m));
        }
    }

    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || event.getNewDamage() <= 0) {
            return;
        }
        // 月潮の真珠
        if (player.getHealth() >= player.getMaxHealth() * TideGear.PEARL_THRESHOLD || !Accessories.worn(player, TideGearSetup.PEARL.get())
                || player.getCooldowns().isOnCooldown(TideGearSetup.PEARL.get())) {
            return;
        }
        pearlShield(player);
    }

    public static void pearlShield(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, TideGear.PEARL_ABSORB_TICKS, 1));
        player.getCooldowns().addCooldown(TideGearSetup.PEARL.get(), TideGear.PEARL_COOLDOWN);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.8, 0.6, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 12, 0.5, 0.7, 0.5, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.2f, 1.3f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
    }

    // 珊瑚の盾で近接を防いだ: 相手に棘
    @SubscribeEvent
    static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity defender = event.getEntity();
        if (!event.getBlocked() || defender.level().isClientSide) {
            return;
        }
        ItemStack use = defender.getUseItem();
        if (!(use.getItem() instanceof CoralShieldItem) || !GearLevel.usable(defender, use)) {
            return;
        }
        if (!(event.getDamageSource().getDirectEntity() instanceof LivingEntity attacker) || attacker == defender) {
            return;
        }
        attacker.hurt(defender.damageSources().thorns(defender), CoralShieldItem.thorns(use));
        if (ModGear.awakened(use)) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), defender);
        }
        if (defender.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(0.6), attacker.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 大鋏: 手に持って水の中にいる間だけ速く振れる
        ItemStack hand = player.getMainHandItem();
        boolean clawInWater = hand.getItem() instanceof TidecallerClawItem && player.isInWaterOrBubble() && GearLevel.meets(player, hand);
        toggle(player, Attributes.ATTACK_SPEED, CLAW_WATER, clawInWater ? TideGear.CLAW_WATER_SPEED : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        if (player.tickCount % 20 != 5) {
            return;
        }
        int pieces = TideShellArmorItem.piecesWorn(player);
        toggle(player, Attributes.ARMOR_TOUGHNESS, SET2, pieces >= 2 ? TideGear.SET2_TOUGHNESS : 0, AttributeModifier.Operation.ADD_VALUE);
        if (pieces >= 4 && player.isInWaterOrBubble()) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, true));
        }
    }

    private static void toggle(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }
}
