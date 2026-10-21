package jp.revati.skyabyss.guide;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// 塩の刃 (塩の砥石を使って 20 分)。手持ちの武器の攻撃が +1.8 (GearCurve.TRAVEL の二章)。水の中で当てると +1 (Ch2Gear.onIncoming)
public class SaltWhettedEffect extends MobEffect {
    public SaltWhettedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD8EEF0);
        Ch2Gear.addAttackModifier(this);
    }
}
