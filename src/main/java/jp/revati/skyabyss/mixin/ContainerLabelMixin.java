package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import jp.revati.skyabyss.client.ui.UiSkin;

// 箱の画面 (持ち物・チェスト・かまど…) の見出しの文字は、バニラでは濃い灰色 (0x404040) で明るい板の上に描く。
// 絵柄のパック (UiSkin) が入っているときは、暗い板でも読める色にする (文字を描く最後の口 2 つで)
@Mixin(GuiGraphics.class)
public abstract class ContainerLabelMixin {
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skyabyss$labelString(int color) {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> ? UiSkin.label(color) : color;
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skyabyss$labelSequence(int color) {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> ? UiSkin.label(color) : color;
    }
}
