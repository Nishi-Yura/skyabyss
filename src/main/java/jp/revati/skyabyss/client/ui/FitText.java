package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

// 一行の枠に文を収めて描く (品の名前・人の名前・見出しなど、折り返せない所)。入ればそのまま、少しはみ出すなら字を縮め (MIN_SCALE まで)、
// それでも入らなければ末尾を「…」にする。長い文が枠の外へ出て隣の字に重なったり、画面の外で見えなくなったりしないように (2026-10-26 の声)
public final class FitText {
    public static final float MIN_SCALE = 0.75f;

    private FitText() {
    }

    // 左寄せ。返すのは描いた幅
    public static int draw(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color, boolean shadow) {
        int w = font.width(text);
        if (w <= maxWidth || maxWidth <= 0) {
            g.drawString(font, text, x, y, color, shadow);
            return w;
        }
        float scale = Math.max(MIN_SCALE, maxWidth / (float) w);
        FormattedCharSequence seq = scale * w <= maxWidth ? text.getVisualOrderText() : ellipsize(font, text, (int) (maxWidth / scale));
        g.pose().pushPose();
        // 縮めた字の高さの分だけ下げ、行の真ん中に置く
        g.pose().translate(x, y + font.lineHeight * (1 - scale) / 2f, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, seq, 0, 0, color, shadow);
        g.pose().popPose();
        return Math.min(maxWidth, Math.round(font.width(seq) * scale));
    }

    // 真ん中寄せ
    public static void drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, int maxWidth, int color, boolean shadow) {
        int w = Math.min(maxWidth, Math.round(font.width(text) * Math.max(MIN_SCALE, Math.min(1f, maxWidth / (float) Math.max(1, font.width(text))))));
        draw(g, font, text, cx - w / 2, y, maxWidth, color, shadow);
    }

    static FormattedCharSequence ellipsize(Font font, Component text, int width) {
        int dots = font.width("…");
        FormattedText head = font.substrByWidth(text, Math.max(0, width - dots));
        return FormattedCharSequence.composite(net.minecraft.locale.Language.getInstance().getVisualOrder(head),
                FormattedCharSequence.forward("…", text.getStyle()));
    }
}
