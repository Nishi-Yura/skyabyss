package jp.revati.skyabyss.mixin;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.client.hud.FoeStatusClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

// 燃えている敵に、バニラの体を包む炎の板を描かない (2026-11-12 の声「せっかくモデル作り込んでるのに火が邪魔している」)。
// 代わりの見せ方 (熱の色・火の粉・アイコン) は client/hud/FoeStatusClient
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityFlameMixin {
    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true)
    private void skyabyss$noFoeFlame(PoseStack poseStack, MultiBufferSource buffer, Entity entity, Quaternionf quaternion, CallbackInfo ci) {
        if (FoeStatusClient.isFoe(entity)) {
            ci.cancel();
        }
    }
}
