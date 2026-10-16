package jp.revati.skyabyss.titan.tide.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 潮殻の三叉槍 (レア): 殻の三つ叉を潮の柄に据えた槍。投げられない (バニラの三叉槍とは別物)。
// 間合い +1。突いた相手は水の衝撃で足が鈍る (鈍足 I 3 秒、+5 で II)
public class TideTridentItem extends TideWeaponItem {
    // 攻撃力 8.5 = 1 + 3.5 + 4、攻撃速度 1.5 = 4 − 2.5。1 秒あたり 12.75 (03 の 9.8節の潮殻 +0 の目安 13.2 の少し下、レアの位置)
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2000, 6.0f, 3.5f, 14,
            () -> Ingredient.of(TideGearSetup.shard()));

    public TideTridentItem(Properties properties) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 4, -2.5f)
                .withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_trident_reach"), TideGear.TRIDENT_REACH,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)), GearTier.RARE, "item.skyabyss.tide_shell_trident.desc");
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (GearLevel.usable(attacker, stack)) {
            boolean awakened = ModGear.awakened(stack);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TideGear.TRIDENT_SLOW_TICKS, awakened ? 1 : 0), attacker);
            if (attacker.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY(0.5), target.getZ(), 16, 0.35, 0.3, 0.35, 0.2);
                level.sendParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getY(0.6), target.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
