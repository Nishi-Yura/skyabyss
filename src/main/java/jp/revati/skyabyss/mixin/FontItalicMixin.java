package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import jp.revati.skyabyss.client.ui.ItalicStyle;
import net.minecraft.network.chat.Style;

// 字を一字ずつ描く所 (Font.StringRenderOutput.accept) で、mod の字のパックが入っているときは斜体を外す (ItalicStyle の案 a・c)
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontItalicMixin {
    @ModifyVariable(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"), argsOnly = true)
    private Style skyabyss$noItalic(Style style) {
        return ItalicStyle.apply(style);
    }
}
