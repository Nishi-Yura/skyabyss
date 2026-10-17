package jp.revati.skyabyss.client.opening;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// B0 開幕の暗転と題字 (docs/design/17 の 3.1節): 初めてワールドに入ったときだけ。
// 黒い画面から始まり、鼓動と遠い鐘のあと、まぶたが細く開いて一度まばたきし、ゆっくり視界が戻る。題字「天と淵のあいだ」、その下に一行。
// 時間は tick。画面の効果の強さ (screenEffectStrength) が 0 のときも、暗転と文字は出す (読めるように。足音の薄い暗がりだけを消す)
final class OpeningOverlay {
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
        if (ticks < 0) {
            return;
        }
        float t = ticks + mc.getTimer().getGameTimeDeltaPartialTick(false);
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
        float title = textAlpha(t, 140f, 190f, 300f, 350f);
        if (title > 0.02f) {
            int alpha = Math.max(4, (int) (title * 255f));
            Component text = Component.translatable("opening.skyabyss.title");
            float scale = 2.6f;
            int cx = w / 2;
            int cy = (int) (h * 0.36f);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawCenteredString(font, text, 0, -4, alpha << 24 | 0xF2C14E);
            g.pose().popPose();
            // 題字の左右へ伸びる細い線
            int half = (int) (font.width(text) * scale * 0.5f) + 18;
            int lineAlpha = Math.max(4, (int) (title * 150f));
            int reach = (int) (60 * title);
            g.fill(cx - half - reach, cy + 2, cx - half, cy + 3, lineAlpha << 24 | 0xF2C14E);
            g.fill(cx + half, cy + 2, cx + half + reach, cy + 3, lineAlpha << 24 | 0xF2C14E);
        }
        float line = textAlpha(t, 230f, 280f, 320f, 360f);
        if (line > 0.02f) {
            int alpha = Math.max(4, (int) (line * 255f));
            g.drawCenteredString(font, Component.translatable("opening.skyabyss.line"), w / 2, (int) (h * 0.36f) + 34, alpha << 24 | 0xE9E3D4);
        }
    }
}
