package jp.revati.skyabyss.client.titan;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BossIntro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// ボスの登場の画面 (combat/BossIntro)。
//   RISE: 画面の縁が赤黒く脈打ち、真ん中のやや上に「〜が坑の底から這い出る」、その下に大きな数え下ろし (3・2・1)。数が変わるたびに大きく出て縮む
//   NAME: 上下から黒い帯が降り、肩書き (「鯨骨の坑の主」) が上に浮かび、名前が真ん中から左右へ開くように現れ、赤い線が左右へ伸びる。最後に薄れて帯が戻る
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BossIntroClient {
    private static final int BAR_IN = 8;
    private static final int FADE = 16;
    private static int phase = -1;
    private static Component name = Component.empty();
    private static String boss = "";
    private static int ticks;
    private static float start;

    private BossIntroClient() {
    }

    private static float now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    public static void receive(BossIntro.Payload p) {
        phase = p.phase();
        name = p.name();
        boss = p.boss();
        ticks = Math.max(1, p.ticks());
        start = now();
    }

    // 試験・撮影用: 今なにを出しているか (-1 = なし)
    public static int showing() {
        return phase;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        phase = -1;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (phase < 0 || mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        float t = now() - start;
        if (t > ticks + (phase == BossIntro.NAME ? 0 : 2) || t < -40) {
            phase = -1;
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        // ほかの札より手前に (帯も文字も)
        g.pose().pushPose();
        g.pose().translate(0, 0, 1100);
        if (phase == BossIntro.RISE) {
            rise(g, mc.font, t);
        } else {
            card(g, mc.font, t);
        }
        g.pose().popPose();
    }

    private static Component riseLine() {
        String own = "gui.skyabyss.boss_intro.rise." + boss;
        return I18n.exists(own) ? Component.translatable(own) : Component.translatable("gui.skyabyss.boss_intro.rise", name);
    }

    private static Component subLine() {
        String own = "gui.skyabyss.boss_intro.sub." + boss;
        return Component.translatable(I18n.exists(own) ? own : "gui.skyabyss.boss_intro.sub");
    }

    private static void rise(GuiGraphics g, Font font, float t) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float in = Mth.clamp(t / 6f, 0f, 1f);
        // 縁の赤黒い脈 (数え下ろしの拍に合わせて)
        float beat = (float) Math.pow(1.0 - ((t % 20f) / 20f), 2.0);
        int edge = (int) ((70 + 80 * beat) * in);
        vignette(g, w, h, edge);
        int cx = w / 2;
        int cy = (int) (h * 0.30f);
        drawScaled(g, font, riseLine(), cx, cy, 1.6f, argb((int) (255 * in), 0xE8C9C0));
        int left = (int) Math.ceil((ticks - t) / 20f);
        if (left >= 1) {
            float u = (t % 20f) / 20f;
            float pop = 1f + 0.9f * (float) Math.pow(1f - u, 3);
            int a = (int) (255 * in * (0.55f + 0.45f * (1f - u)));
            drawScaled(g, font, Component.literal(String.valueOf(left)), cx, cy + 20, 4.2f * pop, argb(a, 0xF2D78A));
        }
    }

    private static void card(GuiGraphics g, Font font, float t) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float out = Mth.clamp((ticks - t) / FADE, 0f, 1f);
        float bar = Mth.clamp(t / BAR_IN, 0f, 1f) * out;
        bar = 1f - (1f - bar) * (1f - bar);
        int bh = (int) (h * 0.11f * bar);
        g.fill(0, 0, w, bh, 0xF0000000);
        g.fill(0, h - bh, w, h, 0xF0000000);
        int cx = w / 2;
        int cy = (int) (h * 0.17f);
        // 肩書き (少し遅れて、上からおりる)
        float sub = Mth.clamp((t - 6) / 10f, 0f, 1f) * out;
        if (sub > 0.01f) {
            drawScaled(g, font, subLine(), cx, (int) (cy - 14 - 6 * (1 - sub)), 1.1f, argb((int) (230 * sub), 0xB8AFA8));
        }
        // 名前: 真ん中から左右へ開く (切り抜きの幅を広げる)
        float open = Mth.clamp((t - 4) / 14f, 0f, 1f);
        open = 1f - (1f - open) * (1f - open) * (1f - open);
        // 名前は画面の幅の 4 割まで (左右の知らせの札に掛からない)
        float scale = Math.min(3.0f, w * 0.40f / Math.max(1, font.width(name)));
        int nw = (int) (font.width(name) * scale);
        int half = (int) (nw / 2f * open) + 2;
        if (open > 0.01f) {
            g.enableScissor(cx - half, cy - 4, cx + half, cy + (int) (font.lineHeight * scale) + 6);
            // 影 (赤く、少しずらして)
            drawScaled(g, font, name, cx + 2, cy + 2, scale, argb((int) (200 * out), 0x6A0E14));
            drawScaled(g, font, name, cx, cy, scale, argb((int) (255 * out), 0xF4EEE6));
            g.disableScissor();
        }
        // 赤い線: 名前の下で左右へ伸びる
        float line = Mth.clamp((t - 10) / 16f, 0f, 1f) * out;
        if (line > 0.01f) {
            int ly = cy + (int) (font.lineHeight * scale) + 5;
            int lw = (int) ((nw / 2f + 40) * line);
            int a = (int) (220 * line);
            g.fill(cx - lw, ly, cx + lw, ly + 1, argb(a, 0xB0262E));
            g.fill(cx - lw / 2, ly + 2, cx + lw / 2, ly + 3, argb(a / 2, 0xB0262E));
        }
    }

    // 画面の縁を暗く (内側ほど薄い帯を重ねる)
    private static void vignette(GuiGraphics g, int w, int h, int alpha) {
        int steps = 10;
        int depth = Math.max(8, Math.min(w, h) / 6);
        for (int i = 0; i < steps; i++) {
            int a = (int) (alpha * (1f - i / (float) steps) / 3f);
            int d = depth * i / steps;
            int d2 = depth * (i + 1) / steps;
            int col = argb(a, 0x2A0004);
            g.fill(0, d, w, d2, col);
            g.fill(0, h - d2, w, h - d, col);
            g.fill(d, d2, d2, h - d2, col);
            g.fill(w - d2, d2, w - d, h - d2, col);
        }
    }

    private static void drawScaled(GuiGraphics g, Font font, Component text, int cx, int y, float scale, int color) {
        if ((color >>> 24) < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(cx, y, 50);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }

    private static int argb(int a, int rgb) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
