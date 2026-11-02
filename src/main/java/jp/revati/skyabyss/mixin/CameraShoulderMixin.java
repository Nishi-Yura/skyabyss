package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import jp.revati.skyabyss.client.camera.ShoulderCam;

// この mod の三人称 (肩越し。client/camera/ShoulderCam): バニラがカメラを体の後ろへ置いたあとで、向きと置き場を肩越しのものに替える
@Mixin(Camera.class)
public abstract class CameraShoulderMixin implements ShoulderCam.CameraAccess {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void skyabyss$shoulder(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (detached && !thirdPersonReverse && ShoulderCam.active()) {
            ShoulderCam.place((Camera) (Object) this, this, entity, partialTick);
        }
    }

    @Override
    public void skyabyss$setRotation(float yaw, float pitch) {
        setRotation(yaw, pitch);
    }

    @Override
    public void skyabyss$setPosition(Vec3 pos) {
        setPosition(pos);
    }
}
