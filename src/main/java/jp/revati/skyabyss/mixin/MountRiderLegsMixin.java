package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.revati.skyabyss.mount.MountEntity;
import jp.revati.skyabyss.mount.MountLook;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

// 乗り物 (docs/design/38) に乗った人の脚: 馬の「前へ投げ出す」脚のままだと、胴の太い乗り物では脚が体に入る。
// 見た目ごとの向き (MountLook.legPitch / legSpread。生成の道具がメッシュから選ぶ) で脚を置く。
// 防具・ズボンの層は setupAnim のあとに脚から写すので、一緒に付いてくる
@Mixin(HumanoidModel.class)
public abstract class MountRiderLegsMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void skyabyss$mountLegs(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                    CallbackInfo ci) {
        if (!(entity.getVehicle() instanceof MountEntity mount)) {
            return;
        }
        MountLook look = mount.look();
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float pitch = look.legPitch * ((float) Math.PI / 180f);
        float spread = look.legSpread * ((float) Math.PI / 180f);
        model.rightLeg.xRot = pitch;
        model.rightLeg.yRot = 0f;
        model.rightLeg.zRot = spread;
        model.leftLeg.xRot = pitch;
        model.leftLeg.yRot = 0f;
        model.leftLeg.zRot = -spread;
    }
}
