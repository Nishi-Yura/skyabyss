package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;

// mod の画面に置いたバニラの釦 (Button.builder で作ったもの) を、切り絵の釦 (Paper.button の斜めの短冊) で描く (docs/design/56 の 2.1)。
// バニラの画面 (ポーズ・設定など) の釦と切り替えの釦も、切り絵の絵柄のパック (A) が入っていれば同じに描く。
// 自分で描く釦 (つまみなど renderWidget を持つもの) はここを通らない
@Mixin(AbstractButton.class)
public abstract class GlassButtonMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void skyabyss$glass(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Object self = this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            return;
        }
        boolean ours = mc.screen.getClass().getName().startsWith("jp.revati.skyabyss.");
        if (ours ? !(self instanceof Button) : !jp.revati.skyabyss.client.ui.UiSkin.paper()) {
            return;
        }
        AbstractButton b = (AbstractButton) self;
        jp.revati.skyabyss.client.ui.Paper.button(g, mc.font, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.getMessage(), false,
                jp.revati.skyabyss.client.ui.Paper.state(b.active, b.isHoveredOrFocused(), b.isHovered()));
        ci.cancel();
    }
}
