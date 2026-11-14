package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.item.BowItem;

// 弓を引き切るまでを combat/BowDraw.FULL_TICKS に (バニラの 20 tick の半分)。すべての弓 (mod の弓はみな BowItem) に効く
@Mixin(BowItem.class)
public abstract class BowPowerMixin {
    @ModifyVariable(method = "getPowerForTime", at = @At("HEAD"), argsOnly = true)
    private static int skyabyss$fasterDraw(int charge) {
        return jp.revati.skyabyss.combat.BowDraw.vanillaTicks(charge);
    }
}
