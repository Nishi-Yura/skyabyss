package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

// 最大体力の上限 (バニラは 1024) を上げる (2026-11-09 の声「耐えはこのゲームから消していい。hp 増やすのだけで敵を固くする」)。
// 前は上限を越えられない主を「受ける傷を割る」で固くしていた。いまは体力そのものを combat/Hardness で掛ける
@Mixin(RangedAttribute.class)
public abstract class MaxHealthCapMixin {
    private static final double CAP = 10_000_000.0;

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void skyabyss$raiseHealthCap(double value, CallbackInfoReturnable<Double> cir) {
        if ("attribute.name.generic.max_health".equals(((Attribute) (Object) this).getDescriptionId())) {
            cir.setReturnValue(Double.isNaN(value) ? 1.0 : Mth.clamp(value, 1.0, CAP));
        }
    }
}
