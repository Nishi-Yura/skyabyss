package jp.revati.skyabyss.story.ch5.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
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

    // 深さの表示の置き場は HudLayout に頼む (右上の効き目の絵・誓いの板と重ならない)。高さは深さの行 + 帯 (+ 重みの行)
    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (voice < 0 || mc.player == null || mc.options.hideGui) {
            return;
        }
        int w = Math.max(60, mc.font.width(Component.translatable("hud.skyabyss.abyss.depth", depth)));
        if (weight >= 10) {
            w = Math.max(w, mc.font.width(Component.translatable("hud.skyabyss.abyss.weight", weight)));
        }
        out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.ABYSS_DEPTH,
                jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT, (w + 3 + 3) / 4 * 4, weight >= 10 ? 28 : 16));
    }

    // 横に濃淡のつく四角 (左の色 → 右の色)
    private static void quad(VertexConsumer vc, Matrix4f m, int x0, int x1, int h, int left, int right) {
        vc.addVertex(m, x0, 0, 0).setColor(left);
        vc.addVertex(m, x0, h, 0).setColor(left);
        vc.addVertex(m, x1, h, 0).setColor(right);
        vc.addVertex(m, x1, 0, 0).setColor(right);
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
            // 左右は横向きの濃淡を四角一つずつで (細い帯を何十本も重ねない)
            Matrix4f m = g.pose().last().pose();
            VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
            quad(vc, m, 0, edge, h, col, 0);
            quad(vc, m, w - edge, w, h, 0, col);
        }
        Font font = mc.font;
        Component dep = Component.translatable("hud.skyabyss.abyss.depth", depth);
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.ABYSS_DEPTH);
        if (r == null) {
            return;
        }
        int right = r.right() - 2;
        int top = r.y();
        g.drawString(font, dep, right - font.width(dep), top, 0xFFC9D3E6, true);
        int bw = 60;
        int bx = right - bw;
        g.fill(bx, top + 12, bx + bw, top + 15, 0x88000000);
        int fillW = Math.round(bw * voice / 100f);
        int c = voice < 40 ? 0xFF7A5FB0 : voice < 70 ? 0xFFB05F9A : 0xFFD0453F;
        g.fill(bx, top + 12, bx + fillW, top + 15, c);
        if (weight >= 10) {
            Component wt = Component.translatable("hud.skyabyss.abyss.weight", weight);
            g.drawString(font, wt, right - font.width(wt), top + 19, 0xFFB8A98A, true);
        }
    }
}
