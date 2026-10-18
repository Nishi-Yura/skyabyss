package jp.revati.skyabyss.client.ui;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// この mod の文字の大きさと色の決まり。字は絵にせず、いつもバニラのフォントを Component で描く (docs/design/18 の 7 章)。
// 大きい字はポーズの拡大で描き、やわらかい光はフォントを重ねて作る。
//   TITLE   : 題字・章の題字 (2.2 倍、金)
//   HEADING : 場所の名前・見出し・レベルの数字・副題 (1.5 倍)
//   BODY    : 本文・字幕・札 (等倍)
//   CAPTION : 注記・手がかり・案内 (等倍、灰色)
public enum TextStyle {
    TITLE(2.2f, 0xF2E6C8, 0x54D6CE),
    HEADING(1.5f, 0xF2C14E, 0xB8923A),
    BODY(1.0f, 0xEEF4F0, 0x7FD6D0),
    CAPTION(1.0f, 0x8C98A4, 0x4A5560);

    public static final int LINE = 11;

    private final float scale;
    private final int color;
    private final int accent;

    TextStyle(float scale, int color, int accent) {
        this.scale = scale;
        this.color = color;
        this.accent = accent;
    }

    public float scale() {
        return scale;
    }

    // 既定の色 (0xRRGGBB)
    public int color() {
        return color;
    }

    // 線や光に使う色 (0xRRGGBB)
    public int accent() {
        return accent;
    }

    // 一行の高さ (画面の寸法)
    public int lineHeight() {
        return Math.round(LINE * scale);
    }

    // 文字の濃さ 0..1 を ARGB の上の 8 ビットにする。バニラは濃さ 3 以下を不透明として描くので、4 を下限にする
    public static int alpha(float a) {
        return Mth.clamp((int) (a * 255f), 4, 255) << 24;
    }

    // 字の横幅 (画面の寸法)
    public int width(Font font, Component text) {
        return Math.round(font.width(text) * scale);
    }

    // 折り返し (この大きさで maxWidth に収まるように)
    public List<FormattedCharSequence> split(Font font, Component text, int maxWidth) {
        return font.split(text, Math.max(8, (int) (maxWidth / scale)));
    }

    // 中央揃えで一行。x は中心、y は上端。rgb は 0xRRGGBB (濃さは alpha で)
    public void drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, float alpha, int rgb) {
        draw(g, font, text.getVisualOrderText(), cx, y, alpha, rgb, true);
    }

    public void drawCentered(GuiGraphics g, Font font, FormattedCharSequence text, int cx, int y, float alpha, int rgb) {
        draw(g, font, text, cx, y, alpha, rgb, true);
    }

    public void drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, float alpha) {
        drawCentered(g, font, text, cx, y, alpha, color);
    }

    // 左揃えで一行
    public void drawLeft(GuiGraphics g, Font font, Component text, int x, int y, float alpha, int rgb) {
        draw(g, font, text.getVisualOrderText(), x, y, alpha, rgb, false);
    }

    private void draw(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, float alpha, int rgb, boolean centered) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        int ox = centered ? -font.width(text) / 2 : 0;
        g.drawString(font, text, ox, 0, alpha(alpha) | (rgb & 0xFFFFFF), true);
        g.pose().popPose();
    }

    // 光のにじみつきの中央揃え。バニラのフォントを accent の色で少しずつずらして重ね、そのうえに字 (影つき) を置く。
    // glow 0..1 で光の強さ。radius は拡大前の字の寸法で 1〜2 くらい。extraScale は、開幕の題字のように大きくしたいときの倍率
    public void drawGlowCentered(GuiGraphics g, Font font, Component text, float cx, float y, float alpha, float glow, float radius, float extraScale) {
        FormattedCharSequence seq = text.getVisualOrderText();
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale * extraScale, scale * extraScale, 1f);
        int ox = -font.width(seq) / 2;
        int gc = alpha(alpha * glow * 0.32f) | accent;
        int steps = 12;
        for (int ring = 1; ring <= 2; ring++) {
            float r = radius * ring * 0.6f;
            for (int i = 0; i < steps; i++) {
                double ang = Math.PI * 2 * i / steps;
                g.pose().pushPose();
                g.pose().translate((float) Math.cos(ang) * r, (float) Math.sin(ang) * r, 0f);
                g.drawString(font, seq, ox, 0, gc, false);
                g.pose().popPose();
            }
        }
        g.drawString(font, seq, ox, 0, alpha(alpha) | (color & 0xFFFFFF), true);
        g.pose().popPose();
    }
}
