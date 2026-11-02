package jp.revati.skyabyss.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.util.JpBreak;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

// バニラの折り返し (Font.split・ツールチップ・看板・本・ボタンの複数行) は空白でしか切らず、日本語は幅いっぱいの字の途中で切る。
// 日本語 (かな・漢字) を含む文だけ、切れ目を util/JpBreak (文節の切れ目と禁則) で決め直す。英語だけの文はバニラのまま
@Mixin(StringSplitter.class)
public abstract class StringSplitterMixin {
    @Shadow
    @Final
    StringSplitter.WidthProvider widthProvider;

    @Inject(method = "splitLines(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/network/chat/Style;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void skyabyss$jpSplit(FormattedText text, int width, Style base, CallbackInfoReturnable<List<FormattedText>> cir) {
        List<FormattedText> lines = skyabyss$lines(text, width, base);
        if (lines != null) {
            cir.setReturnValue(lines);
        }
    }

    // チャット (ComponentRenderUtils.wrapComponents) が使う形。二行目からは続きの行
    @Inject(method = "splitLines(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/network/chat/Style;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void skyabyss$jpSplitChat(FormattedText text, int width, Style base, java.util.function.BiConsumer<FormattedText, Boolean> sink,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        List<FormattedText> lines = skyabyss$lines(text, width, base);
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                sink.accept(lines.get(i), i > 0);
            }
            ci.cancel();
        }
    }

    // 日本語を含まなければ null (バニラに任せる)
    private List<FormattedText> skyabyss$lines(FormattedText text, int width, Style base) {
        List<Integer> cpl = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        boolean[] jp = {false};
        text.visit((style, s) -> {
            s.codePoints().forEach(cp -> {
                cpl.add(cp);
                styles.add(style);
                if (cp >= 0x3040 && cp <= 0x9FFF) {
                    jp[0] = true;
                }
            });
            return Optional.empty();
        }, base);
        if (!jp[0]) {
            return null;
        }
        int n = cpl.size();
        int[] cps = new int[n];
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            cps[i] = cpl.get(i);
            w[i] = cps[i] == '\n' ? 0 : widthProvider.getWidth(cps[i], styles.get(i));
        }
        List<FormattedText> out = new ArrayList<>();
        for (int[] r : JpBreak.lines(cps, w, Math.max(1, width))) {
            List<FormattedText> parts = new ArrayList<>();
            int i = r[0];
            while (i < r[1]) {
                Style st = styles.get(i);
                StringBuilder sb = new StringBuilder();
                while (i < r[1] && styles.get(i).equals(st)) {
                    sb.appendCodePoint(cps[i]);
                    i++;
                }
                parts.add(FormattedText.of(sb.toString(), st));
            }
            out.add(parts.isEmpty() ? FormattedText.EMPTY : FormattedText.composite(parts));
        }
        return out;
    }
}
