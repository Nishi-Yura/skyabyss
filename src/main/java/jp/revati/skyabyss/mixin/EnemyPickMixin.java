package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.combat.EnemyPick;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;

// こちらから殴るときの当たりを広げる (2026-10-23 の試遊)。敵 (Enemy。mod の敵もバニラの敵も) の「狙いの余白」(getPickRadius) を四方に足す。
// 体の箱 (動き・ぶつかり・通れる隙間) は変えず、照準が当たるかの判定にだけ効く。武器の当たる瞬間の確かめ (weapon/WeaponStrikes) も同じ余白で見る
@Mixin(Entity.class)
public abstract class EnemyPickMixin {
    @Inject(method = "getPickRadius", at = @At("HEAD"), cancellable = true)
    private void skyabyss$enemyPick(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Enemy) {
            cir.setReturnValue(EnemyPick.MARGIN);
        }
    }
}
