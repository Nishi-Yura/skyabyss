package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import jp.revati.skyabyss.client.camera.ShoulderCam;

// この mod の三人称 (肩越し。client/camera/ShoulderCam): 肩越しの間、マウスはカメラの向きを回す (体は照準の先を向く)
@Mixin(MouseHandler.class)
public abstract class MouseHandlerShoulderMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void skyabyss$turn(LocalPlayer player, double dx, double dy) {
        if (!ShoulderCam.turn(player, dx, dy)) {
            player.turn(dx, dy);
        }
    }
}
