package jp.revati.skyabyss.item.storm;

import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 雷獣の爪 (伝説): 雷獣の前脚の爪を、握りの革に三本並べた片手の爪の武器。速く振れる。
// 三度目ごとに (+5 から二度目ごとに)、4 ブロック内の別の敵へ雷が跳ぶ (攻撃力の 40%。+10 で二体へ)。
// 跳ぶ雷は斬った相手には当たらないので、一体への 1 秒あたりは攻撃力 × 速さのまま (7.5 × 2.0 = 15.0)
public class RaijuClawItem extends StormWeaponItem {
    // 攻撃力 7.5 = 1 + 2.5 + 4、攻撃速度 2.0 = 4 − 2.0
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2400, 7.0f, 2.5f, 16,
            () -> Ingredient.of(StormGearSetup.SHARD.get()));
    private static final String HITS = SkyAbyss.MODID + ":raiju_claw_hits";

    public RaijuClawItem(Properties properties) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 4, -2.0f)), GearTier.LEGENDARY, "item.skyabyss.raiju_claw.desc");
    }

    public static int every(ItemStack stack) {
        return ModGear.awakened(stack) ? 2 : 3;
    }

    public static int chainTargets(ItemStack stack) {
        return ModGear.enhance(stack) >= ModGear.MAX_ENHANCE ? 2 : 1;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            int hits = attacker.getPersistentData().getInt(HITS) + 1;
            if (hits >= every(stack)) {
                hits = 0;
                float damage = (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * StormGear.CLAW_CHAIN_RATE);
                double r = StormGear.CLAW_CHAIN_RANGE;
                List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r),
                        e -> e != target && e != attacker && e.isAlive() && e instanceof Enemy && e.distanceToSqr(target) <= r * r);
                near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));
                LivingEntity from = target;
                for (int i = 0; i < Math.min(chainTargets(stack), near.size()); i++) {
                    LivingEntity to = near.get(i);
                    StormGearEvents.arc(level, from, to);
                    StormGearEvents.zap(level, to, attacker, damage);
                    from = to;
                }
                if (near.isEmpty()) {
                    StormGearEvents.sparks(level, target);
                }
            }
            attacker.getPersistentData().putInt(HITS, hits);
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
