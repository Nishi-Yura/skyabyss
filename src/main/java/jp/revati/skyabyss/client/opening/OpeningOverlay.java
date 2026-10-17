package jp.revati.skyabyss.client.opening;

import com.mojang.blaze3d.systems.RenderSystem;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// B0 開幕の暗転と題字 (docs/design/17 の 3.1節): 初めてワールドに入ったときだけ。
// 黒い画面から始まり、鼓動と遠い鐘のあと、まぶたが細く開いて一度まばたきし、ゆっくり視界が戻る。題字「天と淵のあいだ」、その下に一行。
// 時間は tick。画面の効果の強さ (screenEffectStrength) が 0 のときも、暗転と文字は出す (読めるように。足音の薄い暗がりだけを消す)
final class OpeningOverlay {
    // 全体の長さ (18 秒)
    static final int TOTAL = 360;
    // 題字の絵 (1536 x 384)
    private static final int TEX_W = 1536;
    private static final int TEX_H = 384;
    private static final ResourceLocation TITLE_JA = tex("title_ja");
    private static final ResourceLocation TITLE_EN = tex("title_en");
    private static final ResourceLocation TITLE_JA_GLOW = tex("title_ja_glow");
    private static final ResourceLocation TITLE_EN_GLOW = tex("title_en_glow");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/opening/" + name + ".png");
    }

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
        // 題字: 明朝体で描いた絵 (tools/gen_opening_title.py)。ゆっくり浮かび上がり、青緑のかすかな光が呼吸する。言語が日本語以外なら英語の題字
        float title = textAlpha(t, 130f, 230f, 300f, 350f);
        if (title > 0.02f) {
            boolean ja = mc.options.languageCode.startsWith("ja");
            ResourceLocation face = ja ? TITLE_JA : TITLE_EN;
            ResourceLocation glow = ja ? TITLE_JA_GLOW : TITLE_EN_GLOW;
            int tw = Math.min((int) (w * 0.7f), 4 * (int) (h * 0.5f));
            int th = tw / 4;
            // 浮かび上がる: 少し下から上がり、わずかに大きさが落ち着く
            float rise = 1f - smooth((t - 130f) / 120f);
            float scale = 1f + 0.03f * rise;
            int x = (w - tw) / 2;
            int y = (int) (h * 0.30f - th * 0.5f + rise * 6f);
            // 明るい空でも読めるよう、題字のまわりをうっすら暗くする
            int shade = (int) (title * 70f);
            int sy = y + th / 2;
            g.fillGradient(0, sy - th, w, sy, 0x00000000, shade << 24);
            g.fillGradient(0, sy, w, sy + th, shade << 24, 0x00000000);
            g.pose().pushPose();
            g.pose().translate(w / 2f, y + th / 2f, 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-w / 2f, -(y + th / 2f), 0);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float breath = 0.6f + 0.4f * Mth.sin(t * 0.05f);
            g.setColor(1f, 1f, 1f, title * breath);
            g.blit(glow, x, y, tw, th, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
            g.setColor(1f, 1f, 1f, title);
            g.blit(face, x, y, tw, th, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
            g.pose().popPose();
        }
        // 開幕の一行: 字幕のように、画面の下に静かに
        float line = textAlpha(t, 230f, 280f, 320f, 360f);
        if (line > 0.02f) {
            int alpha = Math.max(4, (int) (line * 255f));
            int y = (int) (h * 0.80f);
            int band = (int) (line * 110f);
            g.fillGradient(0, y - 22, w, y - 4, 0x00000000, band << 24);
            g.fill(0, y - 4, w, y + 16, band << 24);
            g.fillGradient(0, y + 16, w, y + 34, band << 24, 0x00000000);
            g.drawCenteredString(font, Component.translatable("opening.skyabyss.line"), w / 2, y, alpha << 24 | 0xEEF4F0);
        }
    }
}
