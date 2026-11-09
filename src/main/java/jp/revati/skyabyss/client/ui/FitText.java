package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

// 一行の枠に文を収めて描く (品の名前・人の名前・見出しなど、折り返せない所)。入ればそのまま、少しはみ出すなら字を縮め (MIN_SCALE まで)、
// それでも入らなければ枠の中で横へ流す (Marquee。前は末尾を「…」にしていた)。長い文が枠の外へ出て隣の字に重なったり、画面の外で見えなくなったりしないように (2026-10-26 の声)。
// 2026-11-04 の画面の決まり (docs/design/56) で字は等倍だけにしたので、縮めずに「…」にする (縮めた字は画素がつぶれて読めない)
public final class FitText {
    public static final float MIN_SCALE = 1f;

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
        if (scale * w > maxWidth) {
            // 入らない字は省かずに、枠の中で横へ流す (Marquee。2026-11-09 の声「『...』だと何書いてるかわからない」)
            return Marquee.draw(g, font, text, x, y, maxWidth, color, shadow);
        }
        FormattedCharSequence seq = text.getVisualOrderText();
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

}
