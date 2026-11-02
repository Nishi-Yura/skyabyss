package jp.revati.skyabyss.client.opening;

import jp.revati.skyabyss.client.ui.TextStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// B0 開幕の暗転と題字: 初めてワールドに入ったときだけ。
// 黒い画面から始まり、鼓動と遠い鐘のあと、まぶたが細く開いて一度まばたきし、ゆっくり視界が戻る。題字「天と淵のあいだ」、その下に一行。
// 時間は tick。画面の効果の強さ (screenEffectStrength) が 0 のときも、暗転と文字は出す (読めるように。足音の薄い暗がりだけを消す)
public final class OpeningOverlay {
    // 全体の長さ (18 秒)
    static final int TOTAL = 360;
    private OpeningOverlay() {
    }

    static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    // まぶたの開き (0 = 閉じている、1 = 全部開いた)。60 tick で細く開き、100 で一度閉じ、120 から 80 tick かけて開く
    static float eyelid(float t) {
        if (t < 60f) {
            return 0f;
        }
        if (t < 100f) {
            return 0.12f * smooth((t - 60f) / 40f);
        }
        if (t < 120f) {
            return 0.12f * (1f - smooth((t - 100f) / 20f));
        }
        return smooth((t - 120f) / 80f);
    }

    // 題字・一行の濃さ。fadeIn の始めから入り、hold のあいだ保ち、fadeOut で消える
    static float textAlpha(float t, float in0, float in1, float out0, float out1) {
        if (t < in0 || t > out1) {
            return 0f;
        }
        if (t < in1) {
            return smooth((t - in0) / (in1 - in0));
        }
        if (t <= out0) {
            return 1f;
        }
        return 1f - smooth((t - out0) / (out1 - out0));
    }

    static void render(GuiGraphics g, float silenceEffect) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        // 足音の間のかすかな暗がり (縁を暗く沈める。強さの設定で消える)
        if (silenceEffect > 0.01f) {
            int a = (int) (Mth.clamp(silenceEffect, 0f, 1.5f) * 0.1f * 255f);
            g.fill(0, 0, w, h, a << 24 | 0x03030A);
        }
        int ticks = OpeningClient.awakeTicks();
        // カメラの場面 (opening) が開幕を受け持つときは、場面の画面が renderIntro を呼ぶ
        if (ticks < 0 || jp.revati.skyabyss.client.cinematic.CinematicClient.takesOverOpening()) {
            return;
        }
        renderIntro(g, ticks + mc.getTimer().getGameTimeDeltaPartialTick(false), true);
    }

    // 開幕の暗転・まぶた・題字 (一行は withLine のときだけ。場面では台詞の欄が出す)。t = 開幕からの tick
    public static void renderIntro(GuiGraphics g, float t, boolean withLine) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float open = eyelid(t);
        if (open < 1f) {
            if (t < 60f) {
                g.fill(0, 0, w, h, 0xFF000000);
            } else {
                int bar = (int) (h * 0.5f * (1f - open));
                // まぶたの縁はやわらかく
                g.fill(0, 0, w, bar, 0xFF000000);
                g.fill(0, h - bar, w, h, 0xFF000000);
                int soft = Math.max(4, h / 14);
                g.fillGradient(0, bar, w, bar + soft, 0xFF000000, 0x00000000);
                g.fillGradient(0, h - bar - soft, w, h - bar, 0x00000000, 0xFF000000);
                // 開ききるまで、視界全体が暗くにじむ
                g.fill(0, 0, w, h, ((int) ((1f - open) * (1f - open) * 0.55f * 255f)) << 24 | 0x000000);
            }
        }
        Font font = mc.font;
        // 題字: 題字の字 (ModFont) を大きく描き、青緑のかすかな光が呼吸する (lang の opening.skyabyss.title / title_sub。言語ごとに文が変わる)
        float title = textAlpha(t, 130f, 230f, 300f, 350f);
        if (title > 0.02f) {
            // 浮かび上がる: 少し下から上がる
            float rise = 1f - smooth((t - 130f) / 120f);
            float big = Mth.clamp(h / 270f, 1f, 2f);
            // 題字の字 (mod の字のパックが外れているときはバニラの字)
            Component main = jp.revati.skyabyss.client.ui.ModFont.title(Component.translatable("opening.skyabyss.title"), TextStyle.TITLE.scale() * big);
            Component sub = jp.revati.skyabyss.client.ui.ModFont.title(Component.translatable("opening.skyabyss.title_sub"), TextStyle.HEADING.scale() * big * 0.8f);
            float mainH = TextStyle.TITLE.lineHeight() * big;
            float y = h * 0.30f - mainH * 0.5f + rise * 6f;
            // 明るい空でも読めるよう、題字のまわりをうっすら暗くする
            int shade = (int) (title * 70f);
            int sy = (int) (y + mainH * 0.5f);
            int bandH = (int) (mainH * 1.4f);
            g.fillGradient(0, sy - bandH, w, sy, 0x00000000, shade << 24);
            g.fillGradient(0, sy, w, sy + bandH, shade << 24, 0x00000000);
            float breath = 0.6f + 0.4f * Mth.sin(t * 0.05f);
            TextStyle.TITLE.drawGlowCentered(g, font, main, w / 2f, y, title, breath, 1.4f, big);
            TextStyle.HEADING.drawGlowCentered(g, font, sub, w / 2f, y + mainH + 6f * big, title * 0.85f, breath, 1.0f, big * 0.8f);
        }
        // 開幕の一行: 字幕のように、画面の下に静かに
        float line = withLine ? textAlpha(t, 230f, 280f, 320f, 360f) : 0f;
        if (line > 0.02f) {
            int alpha = Math.max(4, (int) (line * 255f));
            int y = (int) (h * 0.80f);
            int band = (int) (line * 110f);
            g.fillGradient(0, y - 22, w, y - 4, 0x00000000, band << 24);
            g.fill(0, y - 4, w, y + 16, band << 24);
            g.fillGradient(0, y + 16, w, y + 34, band << 24, 0x00000000);
            TextStyle.BODY.drawCentered(g, font, Component.translatable("opening.skyabyss.line"), w / 2, y, line);
        }
    }
}
