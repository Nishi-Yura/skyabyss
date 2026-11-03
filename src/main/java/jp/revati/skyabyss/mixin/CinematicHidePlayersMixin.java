package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.client.cinematic.CinematicClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

// 場面 (シネマティック) の間は、人 (本人もほかの人も) を描かない (2026-11-03 の声「シネマティック入る時、プレイヤー非表示にして」)。
// カメラが本人の目から離れるとき・戻るときに頭の中が映り、道筋の途中では立ったままの体が絵に入っていた。
// 描くかどうかの入口で止めるので、体・持ち物・影・名札がまとめて消える
@Mixin(EntityRenderDispatcher.class)
public abstract class CinematicHidePlayersMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void skyabyss$hidePlayers(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && CinematicClient.hidesPlayers()) {
            cir.setReturnValue(false);
        }
    }
}
