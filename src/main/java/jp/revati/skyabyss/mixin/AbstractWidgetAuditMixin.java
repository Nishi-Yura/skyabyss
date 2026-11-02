package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.revati.skyabyss.client.dev.OverlapAudit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

// 重なりの検査 (client/dev/OverlapAudit) のため、部品を描く間はその部品を「文字の持ち主」にする。検査をしていない間は何もしない
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetAuditMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void skyabyss$auditIn(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (OverlapAudit.capturing()) {
            OverlapAudit.owner((AbstractWidget) (Object) this, g);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void skyabyss$auditOut(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (OverlapAudit.capturing()) {
            OverlapAudit.owner(null, g);
        }
    }
}
