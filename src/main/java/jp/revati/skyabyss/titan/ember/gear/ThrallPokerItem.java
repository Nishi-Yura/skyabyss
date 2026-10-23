package jp.revati.skyabyss.titan.ember.gear;

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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;

// 灰被りの火掻き (ヒロイック): 灰被りが握っていた長い火掻き棒 (敵の火掻き棒と同じ形)。攻撃力 11、速さ 1.65、間合い +1。
// 打つたびに相手へ「灰」を積む。5 つ (+5 から 4 つ) 積もると灰が固まり、3 秒 鈍足 II と防御 −4 (EmberGearEvents.brittle)。
// 灰は最後に積んでから 6 秒で崩れる
public class ThrallPokerItem extends EmberWeaponItem {
    private static final String ASH = SkyAbyss.MODID + ":poker_ash";
    private static final String ASH_AT = SkyAbyss.MODID + ":poker_ash_at";

    public ThrallPokerItem(Properties properties) {
        // 攻撃力 11 = 1 + 5 + 5、攻撃速度 1.65 = 4 − 2.35 (ヒロイックなのでエピックの戦斧 17.6 より上。2026-10-24 に 1.6 から)
        super(properties.attributes(SwordItem.createAttributes(TIER, 5, -2.35f)
                .withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "thrall_poker_reach"), EmberGear.POKER_REACH,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)), GearTier.HEROIC, "item.skyabyss.thrall_poker.desc");
    }

    public static int stacksNeeded(ItemStack stack) {
        return ModGear.awakened(stack) ? EmberGear.POKER_ASH_STACKS_AWAKENED : EmberGear.POKER_ASH_STACKS;
    }

    // 相手に積もっている灰の数 (崩れていれば 0)
    public static int ash(LivingEntity target) {
        long at = target.getPersistentData().getLong(ASH_AT);
        if (target.level().getGameTime() - at > EmberGear.POKER_ASH_DECAY_TICKS) {
            return 0;
        }
        return target.getPersistentData().getInt(ASH);
    }

    // 灰を一つ積む。固まったら true (数は 0 に戻る)
    public static boolean addAsh(LivingEntity target, int needed) {
        int ash = ash(target) + 1;
        boolean set = ash >= needed;
        target.getPersistentData().putInt(ASH, set ? 0 : ash);
        target.getPersistentData().putLong(ASH_AT, target.level().getGameTime());
        return set;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            boolean set = addAsh(target, stacksNeeded(stack));
            level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY(0.7), target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            if (set) {
                EmberGearEvents.brittle(target, attacker);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                        target.getX(), target.getY(0.5), target.getZ(), 24, 0.35, 0.5, 0.35, 0.1);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TUFF_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
