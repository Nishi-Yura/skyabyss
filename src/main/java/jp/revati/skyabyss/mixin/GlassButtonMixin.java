package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;

// mod の画面に置いたバニラの釦 (Button.builder で作ったもの) を、板と同じ絵柄の釦で描く (docs/design/56 の 2 章)。
// 絵柄のパックに頼らない。バニラの画面 (ポーズ・設定など) の釦と、自分で描く釦 (つまみなど renderWidget を持つもの) はそのまま
@Mixin(AbstractButton.class)
public abstract class GlassButtonMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void skyabyss$glass(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Object self = this;
        Minecraft mc = Minecraft.getInstance();
        if (!(self instanceof Button b) || mc.screen == null || !mc.screen.getClass().getName().startsWith("jp.revati.skyabyss.")) {
            return;
        }
        Golden.button(g, mc.font, new Golden.Box(b.getX(), b.getY(), b.getWidth(), b.getHeight()), b.getMessage(), false, b.isHoveredOrFocused(), b.active);
        ci.cancel();
    }
}
