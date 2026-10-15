package jp.revati.skyabyss.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

// 出血: 1秒ごとに体力を1失う。段階が上がるほど間隔が短くなる (II で0.5秒ごと)
public class BleedingEffect extends MobEffect {
    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A1C1C);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().magic(), 1.0f);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(5, 20 >> amplifier);
        return duration % interval == 0;
    }
}
