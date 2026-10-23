package jp.revati.skyabyss.titan.tide.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.SimpleTier;

// 珊瑚憑きの銛 (ヒロイック): 珊瑚憑きが握っていた珊瑚の銛。間合い +1。
// 突いた敵の足もとから珊瑚が生え、1 秒動けなくする (同じ敵には 8 秒に一度。+5 から 1.5 秒・6 秒に一度)
public class CoralHarpoonItem extends TideWeaponItem {
    // 攻撃力 7.5 = 1 + 2.5 + 4、攻撃速度 1.8。1 秒あたり 13.5
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2200, 6.0f, 2.5f, 16,
            () -> Ingredient.of(TideGearSetup.shard()));
    // 敵ごとの「次に縛れる時刻」(ゲームの時刻)
    private static final String NEXT_BIND = SkyAbyss.MODID + ":coral_bind_next";

    public CoralHarpoonItem(Properties properties) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 4, -2.2f)
                .withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "coral_harpoon_reach"), TideGear.HARPOON_REACH,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)), GearTier.HEROIC, "item.skyabyss.coral_harpoon.desc");
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            long now = level.getGameTime();
            if (now >= target.getPersistentData().getLong(NEXT_BIND)) {
                boolean awakened = ModGear.awakened(stack);
                int ticks = awakened ? TideGear.HARPOON_BIND_TICKS_AWAKENED : TideGear.HARPOON_BIND_TICKS;
                target.getPersistentData().putLong(NEXT_BIND, now + (awakened ? TideGear.HARPOON_BIND_EVERY_AWAKENED : TideGear.HARPOON_BIND_EVERY));
                bind(level, target, attacker, ticks);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    // 珊瑚で縛る: 歩けないほどの鈍足と、跳べない重さ。動かないボス (ノックバック耐性 1) には見た目だけ
    public static void bind(ServerLevel level, LivingEntity target, LivingEntity attacker, int ticks) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true), attacker);
        target.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 250, false, false), attacker);
        target.setDeltaMovement(target.getDeltaMovement().multiply(0, 1, 0));
        target.hurtMarked = true;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BRAIN_CORAL_BLOCK.defaultBlockState()),
                target.getX(), target.getY() + 0.2, target.getZ(), 30, 0.35, 0.2, 0.35, 0.1);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUBE_CORAL_BLOCK.defaultBlockState()),
                target.getX(), target.getY() + 0.4, target.getZ(), 14, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CORAL_BLOCK_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
    }
}
