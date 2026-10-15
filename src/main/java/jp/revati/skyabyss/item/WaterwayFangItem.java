package jp.revati.skyabyss.item;

import java.util.List;

import jp.revati.skyabyss.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 水路の顎牙 (伝説): 闇の水路に棲む顎の牙を削った短剣。
// 軽く速い。水の中ではさらに速く振れ (攻撃速度 +30%、GearEvents)、斬った相手は血を流す
public class WaterwayFangItem extends SwordItem implements ModGear {
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 1400, 8.0f, 3.0f, 18,
            () -> Ingredient.of(Items.PRISMARINE_SHARD));

    public WaterwayFangItem(Properties properties) {
        // 攻撃力 6、攻撃速度 2.2 (ふつうの剣より速い)
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 2, -1.8f)));
    }

    @Override
    public GearTier tier() {
        return GearTier.LEGENDARY;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 出血 2秒。+5 からは 3秒の出血 II
        boolean awakened = ModGear.awakened(stack);
        target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, awakened ? 60 : 40, awakened ? 1 : 0), attacker);
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DRIPPING_WATER, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.waterway_fang.desc", true);
    }
}
