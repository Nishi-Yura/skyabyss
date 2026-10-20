package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.revati.skyabyss.client.hud.VanillaShift;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;

// 札 (トースト) は描画層ではないので、mod の部品と重なるときのずらし (HudLayout の V_TOASTS) をここで描く前後に当てる
@Mixin(ToastComponent.class)
public abstract class ToastShiftMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void skyabyss$begin(GuiGraphics g, CallbackInfo ci) {
        VanillaShift.beginToasts(g);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void skyabyss$end(GuiGraphics g, CallbackInfo ci) {
        VanillaShift.endToasts(g);
    }
}
