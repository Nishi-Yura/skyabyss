package jp.revati.skyabyss.story.ch5.client;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 深淵の圧の表示 (docs/design/36 の A4。サーバーの story/ch5/AbyssPressure から 10 tick ごと)。
//   右上: 「深さ 123」と、淵の声の細い帯 (たまるほど紫から赤へ)。重さが 10% 以上なら「体が重い 40%」
//   画面のふち: 淵の声に合わせて暗くなる (40 から。100 で端がほぼ黒)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AbyssHud {
    private static int voice = -1;
    private static int weight;
    private static int depth;
    private static float shown;

    private AbyssHud() {
    }

    public static void set(int v, int w, int d) {
        voice = v;
        weight = w;
        depth = d;
    }

    @SubscribeEvent
    static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (voice < 0 || mc.player == null || mc.options.hideGui) {
            shown = 0;
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        // ふちの暗さ (なめらかに追う)
        float target = Math.max(0f, (voice - 40) / 60f);
        shown += (target - shown) * 0.08f;
        if (shown > 0.01f) {
            int a = (int) (shown * 200);
            int edge = Math.max(12, (int) (Math.min(w, h) * 0.18f));
            int col = a << 24;
            g.fillGradient(0, 0, w, edge, col, 0);
            g.fillGradient(0, h - edge, w, h, 0, col);
            for (int i = 0; i < edge; i += 2) {
                int ai = (int) (a * (1f - i / (float) edge)) << 24;
                g.fill(i, 0, i + 2, h, ai);
                g.fill(w - i - 2, 0, w - i, h, ai);
            }
        }
        Font font = mc.font;
        Component dep = Component.translatable("hud.skyabyss.abyss.depth", depth);
        int x = w - font.width(dep) - 8;
        g.drawString(font, dep, x, 8, 0xFFC9D3E6, true);
        int bw = 60;
        int bx = w - bw - 8;
        g.fill(bx, 20, bx + bw, 23, 0x88000000);
        int fillW = Math.round(bw * voice / 100f);
        int c = voice < 40 ? 0xFF7A5FB0 : voice < 70 ? 0xFFB05F9A : 0xFFD0453F;
        g.fill(bx, 20, bx + fillW, 23, c);
        if (weight >= 10) {
            Component wt = Component.translatable("hud.skyabyss.abyss.weight", weight);
            g.drawString(font, wt, w - font.width(wt) - 8, 27, 0xFFB8A98A, true);
        }
    }
}
