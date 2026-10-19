package jp.revati.skyabyss.guide;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// 研いだ刃 (岩の砥石を使って 20 分)。手持ちの武器の攻撃が +1.5 (GearCurve.TRAVEL の一章)。武器そのものは変えない (属性の修飾子)
public class WhettedEffect extends MobEffect {
    public WhettedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8FA3B8);
        TravelGear.addAttackModifier(this);
    }
}
