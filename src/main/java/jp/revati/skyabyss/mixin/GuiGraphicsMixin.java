package jp.revati.skyabyss.mixin;

import java.util.List;

import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.client.ui.TextBounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.util.FormattedCharSequence;

// 画面の文字は、その画面の範囲 (TextBounded) の外へ出ない。drawString (Component・折り返しの行も最後はここを通る) を範囲で切る。
// ツールチップは切らず、画面からはみ出すときだけ縮めて、画面の中に収める (幅・高さのどちらでも。端では左右・上下へずらす)
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Shadow
    private void renderTooltipInternal(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY, ClientTooltipPositioner positioner) {
    }

    @Shadow
    public abstract int guiWidth();

    @Shadow
    public abstract int guiHeight();

    private boolean skyabyss$fitting;

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void skyabyss$fitTooltip(Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY, ClientTooltipPositioner positioner,
            CallbackInfo ci) {
        if (skyabyss$fitting || components.isEmpty()) {
            return;
        }
        int w = 0;
        int h = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent c : components) {
            w = Math.max(w, c.getWidth(font));
            h += c.getHeight();
        }
        int sw = guiWidth();
        int sh = guiHeight();
        float s = Math.min(1.0f, Math.min((sw - 8.0f) / (w + 8.0f), (sh - 8.0f) / (h + 8.0f)));
        if (s >= 0.999f) {
            return;
        }
        float scale = Math.max(0.45f, s);
        final int tw = w;
        final int th = h;
        final float inv = 1.0f / scale;
        ClientTooltipPositioner fit = (screenW, screenH, mx, my, ttw, tth) -> {
            double pw = screenW * inv;
            double ph = screenH * inv;
            int x = mx + 12;
            if (x + tw + 6 > pw) {
                x = mx - 16 - tw;
            }
            x = (int) Math.max(6, Math.min(x, pw - tw - 6));
            int y = (int) Math.max(6, Math.min(my - 12, ph - th - 6));
            return new Vector2i(x, y);
        };
        GuiGraphics self = (GuiGraphics) (Object) this;
        skyabyss$fitting = true;
        self.pose().pushPose();
        self.pose().scale(scale, scale, 1.0f);
        try {
            // 位置決めは縮めた座標で行う (画面の寸法・マウスの位置も縮尺の逆数で渡る)
            renderTooltipInternal(font, components, Math.round(mouseX * inv), Math.round(mouseY * inv), fit);
        } finally {
            self.pose().popPose();
            skyabyss$fitting = false;
        }
        ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"))
    private void skyabyss$in1(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        skyabyss$begin();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("RETURN"))
    private void skyabyss$out1(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        skyabyss$end();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"))
    private void skyabyss$in2(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        skyabyss$begin();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("RETURN"))
    private void skyabyss$out2(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        skyabyss$end();
    }

    private boolean skyabyss$on;

    private void skyabyss$begin() {
        int[] b = TextBounds.current();
        skyabyss$on = b != null;
        if (skyabyss$on) {
            ((GuiGraphics) (Object) this).enableScissor(b[0], b[1], b[0] + b[2], b[1] + b[3]);
        }
    }

    private void skyabyss$end() {
        if (skyabyss$on) {
            skyabyss$on = false;
            ((GuiGraphics) (Object) this).disableScissor();
        }
    }
}
