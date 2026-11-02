package jp.revati.skyabyss.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 画面の文字と部品の重なりの検査 (開発用。2026-11-02 の声「UI がたまにおかしいものある。文字や要素が重なったり」)。
// 撮影の台本の「overlap <名前>」で、次に描く一枚の画面について、描いた文字 (GuiGraphics.drawString) と釦などの部品の四角を集めて調べ、
// 撮影のフォルダの overlap.txt に書く。見るもの:
//   text/text     二つの文字の四角が重なる (同じ文をほぼ同じ所に描く縁取りは除く)
//   text/widget   画面が描いた文字が釦などの部品にかかる
//   label         部品が描いた文字が、その部品の外へはみ出す
//   widget        二つの部品が一部だけ重なる (片方がもう片方を丸ごと含むのは除く)
//   cut           画面の範囲 (TextBounds) で文字が切られた
// ツールチップ (奥行き 400 より手前) は見ない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class OverlapAudit {
    private record Rect(float x, float y, float w, float h) {
        boolean overlaps(Rect o, float min) {
            float ix = Math.min(x + w, o.x + o.w) - Math.max(x, o.x);
            float iy = Math.min(y + h, o.y + o.h) - Math.max(y, o.y);
            return ix > min && iy > min;
        }

        boolean contains(Rect o, float slack) {
            return o.x >= x - slack && o.y >= y - slack && o.x + o.w <= x + w + slack && o.y + o.h <= y + h + slack;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "(%.0f,%.0f %.0fx%.0f)", x, y, w, h);
        }
    }

    private record Text(String s, Rect r, Rect owner, boolean cut) {
    }

    private record Widget(String name, Rect r) {
    }

    private static String armed;
    private static Path file;
    private static boolean capturing;
    private static final List<Text> TEXTS = new ArrayList<>();
    private static final List<Widget> WIDGETS = new ArrayList<>();
    private static Rect owner;
    public static int found;

    private OverlapAudit() {
    }

    // 次の一枚を調べる
    static void arm(String label, Path out) {
        armed = label;
        file = out;
    }

    static boolean done() {
        return armed == null;
    }

    public static boolean capturing() {
        return capturing;
    }

    // 部品を描く間 (AbstractWidget.render)。その間に描いた文字はその部品のもの。四角は描いたときの姿勢 (拡大・ずらし) で写す
    public static void owner(AbstractWidget w, GuiGraphics g) {
        if (w == null) {
            owner = null;
            return;
        }
        Matrix4f m = g.pose().last().pose();
        if (!w.visible || w.getWidth() <= 0 || w.getHeight() <= 0 || m.m32() >= 300f) {
            owner = null;
            return;
        }
        owner = new Rect(m.m00() * w.getX() + m.m30(), m.m11() * w.getY() + m.m31(), m.m00() * w.getWidth(), m.m11() * w.getHeight());
        // 巻物の外 (切り抜きの外) に出た部品は見えないので数えない
        if (g.containsPointInScissor((int) (owner.x + owner.w / 2), (int) (owner.y + owner.h / 2))) {
            WIDGETS.add(new Widget(plain(w.getMessage().getVisualOrderText()), owner));
        }
    }

    public static void text(GuiGraphics g, Font font, Object text, int x, int y, int width, boolean cut) {
        Matrix4f m = g.pose().last().pose();
        if (m.m32() >= 300f) {
            return;
        }
        String s = text instanceof String str ? str : text instanceof FormattedCharSequence seq ? plain(seq) : "";
        if (s.isBlank()) {
            return;
        }
        // 切り抜き (巻物の枠) の外に真ん中がある字は見えないので数えない
        // 切り抜きは姿勢を掛けた座標で持っている
        if (!g.containsPointInScissor((int) (m.m00() * (x + width / 2f) + m.m30()), (int) (m.m11() * (y + font.lineHeight / 2f) + m.m31()))) {
            return;
        }
        // 切られた字: 範囲の外にまるごと出た字 (巻物の外) は見えないだけなので数えない。一部だけ出た字を「切れた」とする
        int[] b = jp.revati.skyabyss.client.ui.TextBounds.current();
        if (cut && b != null) {
            boolean outside = x + width <= b[0] || x >= b[0] + b[2] || y + font.lineHeight <= b[1] || y >= b[1] + b[3];
            if (outside) {
                return;
            }
        }
        float sx = m.m00();
        float sy = m.m11();
        Rect r = new Rect(sx * x + m.m30(), sy * y + m.m31(), sx * width, sy * (font.lineHeight - 1));
        // 本当に切れたか: 両端が今の切り抜きの中なら切れていない (範囲の決まりは拡大した字の座標で外れと見ることがある)
        if (cut) {
            int cy = (int) (r.y + r.h / 2);
            cut = !(g.containsPointInScissor((int) r.x + 1, cy) && g.containsPointInScissor((int) (r.x + r.w) - 1, cy));
        }
        TEXTS.add(new Text(s, r, owner, cut));
    }

    static String plain(FormattedCharSequence seq) {
        StringBuilder b = new StringBuilder();
        seq.accept((i, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        return b.toString();
    }

    @SubscribeEvent
    static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (armed != null) {
            TEXTS.clear();
            WIDGETS.clear();
            owner = null;
            capturing = true;
        }
    }

    @SubscribeEvent
    static void onRenderPost(ScreenEvent.Render.Post event) {
        if (armed == null || !capturing) {
            return;
        }
        capturing = false;
        Screen screen = event.getScreen();
        List<Rect> widgets = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Widget w : WIDGETS) {
            widgets.add(w.r);
            names.add(w.name);
        }
        List<String> out = new ArrayList<>();
        // 同じ文をほぼ同じ所 (2 px 以内) に描いたもの (縁取り・影の手描き) は一つと数える
        List<Text> texts = new ArrayList<>();
        for (Text t : TEXTS) {
            boolean dup = false;
            for (Text u : texts) {
                if (u.s.equals(t.s) && Math.abs(u.r.x - t.r.x) <= 2 && Math.abs(u.r.y - t.r.y) <= 2) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                texts.add(t);
            }
        }
        for (int i = 0; i < texts.size(); i++) {
            Text a = texts.get(i);
            if (a.cut) {
                out.add("cut         '" + clip(a.s) + "' " + a.r);
            }
            if (a.owner != null) {
                Rect own = a.owner;
                if (!own.contains(a.r, 1.5f)) {
                    out.add("label       '" + clip(a.s) + "' " + a.r + " outside its widget " + own);
                }
            } else {
                for (int k = 0; k < widgets.size(); k++) {
                    if (widgets.get(k).overlaps(a.r, 1.5f)) {
                        out.add("text/widget '" + clip(a.s) + "' " + a.r + " on [" + clip(names.get(k)) + "] " + widgets.get(k));
                    }
                }
            }
            for (int j = i + 1; j < texts.size(); j++) {
                Text b = texts.get(j);
                if (a.r.overlaps(b.r, 1.5f)) {
                    out.add("text/text   '" + clip(a.s) + "' " + a.r + " x '" + clip(b.s) + "' " + b.r);
                }
            }
        }
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                Rect a = widgets.get(i);
                Rect b = widgets.get(j);
                if (a.overlaps(b, 1.5f) && !a.contains(b, 0) && !b.contains(a, 0)) {
                    out.add("widget      [" + clip(names.get(i)) + "] " + a + " x [" + clip(names.get(j)) + "] " + b);
                }
            }
        }
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("== ").append(armed).append("  ").append(screen.getClass().getSimpleName()).append("  gui ")
                .append(mc.getWindow().getGuiScaledWidth()).append('x').append(mc.getWindow().getGuiScaledHeight())
                .append(" (scale ").append((int) mc.getWindow().getGuiScale()).append(")  texts ").append(texts.size())
                .append("  widgets ").append(widgets.size()).append("  issues ").append(out.size()).append('\n');
        for (String o : out) {
            sb.append("  ").append(o).append('\n');
        }
        found += out.size();
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SkyAbyss.LOGGER.warn("[overlap] could not write {}", file, e);
        }
        SkyAbyss.LOGGER.info("[overlap] {}: {} issue(s)", armed, out.size());
        armed = null;
        TEXTS.clear();
        WIDGETS.clear();
    }

    private static String clip(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
