package jp.revati.skyabyss.client.story;

import java.util.Random;

import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.SkyKeyScenePayload;
import jp.revati.skyabyss.story.StorySetup;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

// 天の鍵がそろった場面 (docs/design/14_beta.md の 4.3 節)。ゲームは止めない。
//   0〜4.4 秒 : 画面が暗くなり、四つの欠片が四隅から寄っていく (岩鯨・潮殻・雷翼・余燼の色の光)
//   4.4 秒   : 触れ合って白く光り、一つの鍵になる
//   5〜19 秒 : 空から光の筋が落ちて、画面の下 (足もと) へ沈む。四行の文。足もとの遠い鼓動が二度
//   20 秒〜  : 一章の終わりの札 (ベータ版はここまで)。Esc かクリックで戻る (それまでの Esc は札へ飛ばす)
public final class SkyKeyScene extends Screen {
    private static final ResourceLocation KEY_FORM = ResourceLocation.fromNamespaceAndPath("skyabyss_story", "key_form");
    private static final ResourceLocation DEEP_BEAT = ResourceLocation.fromNamespaceAndPath("skyabyss_story", "deep_beat");
    private static final int[] TITAN_COLORS = {0x40C4EE, 0x46D6B0, 0xEEE278, 0xF08040};
    private static final float MERGE = 4.4f;
    private static final float[] LINE_AT = {5.6f, 8.6f, 11.6f, 14.6f};
    private static final float[] BEAT_AT = {11.4f, 13.6f};
    static final float CARD = 20f;
    private static final float AUTO_CLOSE = 45f;

    private final int days;
    private long startMs;
    private float skipTo = -1;
    private boolean formPlayed;
    private final boolean[] beatPlayed = new boolean[BEAT_AT.length];
    private final float[][] streaks = new float[24][3];

    private SkyKeyScene(int days) {
        super(Component.translatable("scene.skyabyss.sky_key.title"));
        this.days = days;
        Random r = new Random(4);
        for (float[] s : streaks) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat() * 3.5f;
            s[2] = 0.5f + r.nextFloat();
        }
    }

    public static void open(SkyKeyScenePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new SkyKeyScene(payload.days())));
    }

    // 場面の間は HUD (チャット・追跡表示・技の枠) を隠す。閉じたら元に戻す
    private Boolean hudHiddenBefore;

    @Override
    protected void init() {
        if (startMs == 0) {
            startMs = Util.getMillis();
        }
        if (hudHiddenBefore == null && minecraft != null) {
            hudHiddenBefore = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
        }
    }

    @Override
    public void removed() {
        if (hudHiddenBefore != null && minecraft != null) {
            minecraft.options.hideGui = hudHiddenBefore;
            hudHiddenBefore = null;
        }
        super.removed();
    }

    // 経った秒数 (Esc で札へ飛ばしたぶんを足す)
    float time() {
        float t = (Util.getMillis() - startMs) / 1000f;
        return skipTo >= 0 ? Math.max(t, skipTo) : t;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        float t = time();
        if (!formPlayed && t >= MERGE - 1.45f) {
            formPlayed = true;
            play(KEY_FORM, 1.0f, 0.9f);
        }
        for (int i = 0; i < BEAT_AT.length; i++) {
            if (!beatPlayed[i] && t >= BEAT_AT[i] && t < CARD) {
                beatPlayed[i] = true;
                play(DEEP_BEAT, 1.0f, 0.8f);
            }
        }
        if (t > AUTO_CLOSE) {
            onClose();
        }
    }

    private static void play(ResourceLocation id, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), pitch, volume));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Esc
            if (time() < CARD) {
                skip();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (time() >= CARD + 1.5f) {
            onClose();
            return true;
        }
        return false;
    }

    private void skip() {
        skipTo = CARD;
        formPlayed = true;
        java.util.Arrays.fill(beatPlayed, true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景のぼかしは使わない (景色がうっすら見えるように、自分で暗くする)
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = time();
        int w = width;
        int h = height;
        // 暗がり: 上ほど濃く (空が暗くなる)
        float dark = Mth.clamp(t / 1.2f, 0f, 1f);
        if (t > CARD - 1f) {
            dark = Math.max(dark, 1f);
        }
        g.fillGradient(0, 0, w, h, argb(0.94f * dark, 0x05070C), argb(0.84f * dark, 0x0A0D14));

        float cx = w / 2f;
        float cy = h * 0.40f;
        if (t < CARD) {
            if (t < MERGE) {
                fragments(g, t, cx, cy);
            } else {
                streaks(g, t, cx, cy, w, h);
                key(g, t, cx, cy);
                flash(g, t, w, h);
                lines(g, t, cx, cy);
            }
        } else {
            card(g, t, cx, h);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void fragments(GuiGraphics g, float t, float cx, float cy) {
        float appear = Mth.clamp((t - 0.6f) / 0.8f, 0f, 1f);
        float k = Mth.clamp((t - 0.6f) / (MERGE - 0.6f), 0f, 1f);
        float ease = k * k * (3 - 2 * k);
        float r0 = Math.min(width, height) * 0.32f;
        float r = Mth.lerp(ease, r0, 10f);
        ItemStack frag = new ItemStack(ModItems.SKY_KEY_FRAGMENT.get());
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2 + ease * 0.9;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            float pulse = 0.55f + 0.25f * Mth.sin(t * 5f + i);
            glow(g, x, y, 16 + 10 * ease, TITAN_COLORS[i], appear * pulse * 0.5f);
            item(g, frag, x, y, 2.5f, appear);
        }
    }

    private void key(GuiGraphics g, float t, float cx, float cy) {
        float k = Mth.clamp((t - MERGE) / 0.6f, 0f, 1f);
        float bob = Mth.sin((t - MERGE) * 1.6f) * 2f;
        glow(g, cx, cy + bob, 34, 0xE8F6FF, 0.35f * k);
        item(g, new ItemStack(StorySetup.SKY_KEY.get()), cx, cy + bob, 4f, 1f);
    }

    // 空から落ちる光の筋。鍵の下、画面の下の端 (足もと) へ沈んで消える
    private void streaks(GuiGraphics g, float t, float cx, float cy, int w, int h) {
        float since = t - MERGE;
        float strength = Mth.clamp(since / 1.5f, 0f, 1f) * Mth.clamp((CARD - 1f - t) / 1.5f, 0f, 1f);
        for (float[] s : streaks) {
            float period = 3.5f / s[2];
            float phase = ((since + s[1]) % period) / period;
            float x = cx + (s[0] - 0.5f) * w * 0.7f * (1 - phase * 0.85f);
            float y = -20 + phase * (h + 40);
            float len = 14 + 18 * s[2];
            float fade = strength * (phase < 0.85f ? 1f : (1 - phase) / 0.15f);
            g.fillGradient((int) x, (int) (y - len), (int) x + 1, (int) y, argb(0f, 0xDFF4FF), argb(0.55f * fade, 0xDFF4FF));
        }
        // 鍵から真下へ、細い光 (鍵が足もとを指す)
        float beam = Mth.clamp((since - 1f) / 2f, 0f, 1f) * strength;
        g.fillGradient((int) cx, (int) cy + 20, (int) cx + 1, h, argb(0.5f * beam, 0xE8F6FF), argb(0.05f * beam, 0xE8F6FF));
    }

    private void flash(GuiGraphics g, float t, int w, int h) {
        float f = 1f - Mth.clamp((t - MERGE) / 0.5f, 0f, 1f);
        if (f > 0) {
            g.fill(0, 0, w, h, argb(0.8f * f, 0xF4FBFF));
        }
    }

    private void lines(GuiGraphics g, float t, float cx, float cy) {
        float out = Mth.clamp((CARD - 1f - t) / 1f, 0f, 1f);
        int y = (int) (cy + 44);
        for (int i = 0; i < LINE_AT.length; i++) {
            float a = Mth.clamp((t - LINE_AT[i]) / 0.9f, 0f, 1f) * out;
            if (a > 0.02f) {
                g.drawCenteredString(font, Component.translatable("scene.skyabyss.sky_key.line" + (i + 1)), (int) cx, y, argb(a, 0xDDE8F0));
            }
            y += 16;
        }
    }

    private void card(GuiGraphics g, float t, float cx, int h) {
        float s = t - CARD;
        int y = (int) (h * 0.36f);
        float a1 = Mth.clamp(s / 1.2f, 0f, 1f);
        float a2 = Mth.clamp((s - 1.2f) / 1.2f, 0f, 1f);
        float a3 = Mth.clamp((s - 2.6f) / 1.2f, 0f, 1f);
        float a4 = Mth.clamp((s - 4f) / 1f, 0f, 1f);
        if (a1 > 0.02f) {
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, Component.translatable("scene.skyabyss.sky_key.end_title"), 0, 0, argb(a1, 0xF2E6C8));
            g.pose().popPose();
        }
        if (a2 > 0.02f) {
            g.drawCenteredString(font, Component.translatable("scene.skyabyss.sky_key.end_sub"), (int) cx, y + 30, argb(a2, 0xB8C4D0));
        }
        if (a3 > 0.02f) {
            g.drawCenteredString(font, Component.translatable("scene.skyabyss.sky_key.end_thanks"), (int) cx, y + 52, argb(a3, 0xDDE8F0));
            g.drawCenteredString(font, Component.translatable("scene.skyabyss.sky_key.end_days", days), (int) cx, y + 66, argb(a3 * 0.8f, 0x8C98A4));
        }
        if (a4 > 0.02f) {
            g.drawCenteredString(font, Component.translatable("scene.skyabyss.sky_key.end_hint"), (int) cx, h - 24, argb(a4 * 0.7f, 0x8C98A4));
        }
    }

    private static void item(GuiGraphics g, ItemStack stack, float x, float y, float scale, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 100);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    private static void glow(GuiGraphics g, float x, float y, float r, int rgb, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        // 重ねた四角でぼんやりした光 (外ほど薄く、角が目立たないように段を細かく)
        for (int i = 10; i >= 1; i--) {
            float rr = r * i / 10f;
            g.fill((int) (x - rr), (int) (y - rr * 0.8f), (int) (x + rr), (int) (y + rr * 0.8f), argb(alpha * 0.07f, rgb));
            g.fill((int) (x - rr * 0.8f), (int) (y - rr), (int) (x + rr * 0.8f), (int) (y + rr), argb(alpha * 0.05f, rgb));
        }
    }

    // 透明度 a (0〜1) と色。文字はアルファが 4 未満だと不透明に描かれるので、下を切る
    static int argb(float a, int rgb) {
        int alpha = Mth.clamp((int) (a * 255), 0, 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
