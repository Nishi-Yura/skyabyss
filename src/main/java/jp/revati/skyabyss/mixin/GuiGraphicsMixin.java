package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.client.ui.TextBounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

// 画面の文字は、その画面の範囲 (TextBounded) の外へ出ない。drawString (Component・折り返しの行も最後はここを通る) を範囲で切る
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
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
