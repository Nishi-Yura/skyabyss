package jp.revati.skyabyss.client.ui;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// 切り絵の画面の部品 (docs/design/56 の「切り絵」。2026-11-24 にユーザーが F を選んだ)。
// 色はボスの切り絵 (黒・桃色・灰の菱形) と同じ。紙は手で決めた点の並び (はさみで切ったまっすぐな辺) を、
// その場で画素に塗った絵 (1 画素 = 論理の 1 画素) にして使い回す。乱数のギザギザは使わない
public final class Paper {
    private Paper() {
    }

    // 色 (ARGB)
    public static final int BLACK = 0xFF181722;
    public static final int BLACK_LO = 0xFF0E0D14;
    public static final int PINK = 0xFFE45A9C;
    public static final int PINK_HI = 0xFFF89CD2;
    public static final int PINK_LO = 0xFF8C2C60;
    public static final int GREY = 0xFFA8A29A;
    public static final int GREY_LO = 0xFF5A5460;
    public static final int CREAM = 0xFFECE4D6;
    public static final int CREAM_LO = 0xFFC9BFAE;
    // 字の色 (生成りの紙の上 / 黒い紙の上)
    public static final int INK = 0x181722;
    public static final int INK_HEAD = 0xB0326E;
    public static final int INK_SOFT = 0x6E6468;
    public static final int ON_BLACK = 0xECE4D6;
    public static final int ON_BLACK_SOFT = 0x9A929C;
    public static final int ON_BLACK_PINK = 0xF89CD2;
    // 黒い紙の上の目立つ字 (夜空の空色の代わり) と、まだ使えないものの字
    public static final int ON_BLACK_HI = 0xFFF6EA;
    public static final int ON_BLACK_DIM = 0x7C7480;
    // 黒い紙の上の細い縁 (升・枠)
    public static final int EDGE = 0xFF3A3644;

    // ---------- 紙の絵 (作った絵を名前で使い回す) ----------
    public interface Painter {
        void paint(Sheet s);
    }

    private record Tex(ResourceLocation id, DynamicTexture tex, int w, int h, String key) {
    }

    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static int serial;

    // name ごとに一枚。key (大きさや状態) が変わったときだけ塗り直す。(x, y) は絵の左上 (論理の座標)
    public static void draw(GuiGraphics g, String name, String key, int x, int y, int w, int h, Painter p) {
        Tex t = CACHE.get(name);
        if (t == null || !t.key.equals(key) || t.w != w || t.h != h) {
            if (t != null) {
                Minecraft.getInstance().getTextureManager().release(t.id);
            }
            Sheet s = new Sheet(w, h);
            p.paint(s);
            DynamicTexture dt = new DynamicTexture(s.img);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "paper/" + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + "_" + (serial++));
            Minecraft.getInstance().getTextureManager().register(id, dt);
            t = new Tex(id, dt, w, h, key);
            CACHE.put(name, t);
        }
        g.blit(t.id, x, y, 0, 0, w, h, w, h);
    }

    // 塗る面 (NativeImage は ABGR)
    public static final class Sheet {
        final NativeImage img;
        public final int w;
        public final int h;

        Sheet(int w, int h) {
            this.w = w;
            this.h = h;
            img = new NativeImage(w, h, true);
            img.fillRect(0, 0, w, h, 0);
        }

        public void px(int x, int y, int argb) {
            if (x < 0 || y < 0 || x >= w || y >= h) {
                return;
            }
            int a = argb >>> 24;
            if (a == 0) {
                return;
            }
            img.setPixelRGBA(x, y, (a << 24) | ((argb & 0xFF) << 16) | (argb & 0xFF00) | ((argb >> 16) & 0xFF));
        }

        public void rect(int x, int y, int rw, int rh, int argb) {
            for (int j = 0; j < rh; j++) {
                for (int i = 0; i < rw; i++) {
                    px(x + i, y + j, argb);
                }
            }
        }

        // 多角形を塗る (点の並び {x0, y0, x1, y1, ...}。画素の真ん中が中にあれば塗る)。dx, dy だけずらして
        public void poly(float[] p, int argb, float dx, float dy) {
            int n = p.length / 2;
            float[] xs = new float[n];
            for (int y = 0; y < h; y++) {
                float cy = y + 0.5f;
                int k = 0;
                for (int i = 0; i < n; i++) {
                    float x0 = p[i * 2] + dx;
                    float y0 = p[i * 2 + 1] + dy;
                    float x1 = p[((i + 1) % n) * 2] + dx;
                    float y1 = p[((i + 1) % n) * 2 + 1] + dy;
                    if ((y0 <= cy && y1 > cy) || (y1 <= cy && y0 > cy)) {
                        xs[k++] = x0 + (cy - y0) / (y1 - y0) * (x1 - x0);
                    }
                }
                java.util.Arrays.sort(xs, 0, k);
                for (int i = 0; i + 1 < k; i += 2) {
                    int a = (int) Math.ceil(xs[i] - 0.5f);
                    int b = (int) Math.floor(xs[i + 1] - 0.5f);
                    for (int x = a; x <= b; x++) {
                        px(x, y, argb);
                    }
                }
            }
        }

        public void poly(float[] p, int argb) {
            poly(p, argb, 0, 0);
        }

        // 紙を一枚: 下に影の色の紙 (ずらして)、上に本体
        public void layer(float[] p, int argb, int under, float ux, float uy) {
            poly(p, under, ux, uy);
            poly(p, argb, 0, 0);
        }

        // 円 (真ん中 cx, cy・半径 r。画素の真ん中が中にあれば塗る)
        public void disc(float cx, float cy, float r, int argb) {
            for (int y = (int) Math.floor(cy - r); y <= (int) Math.ceil(cy + r); y++) {
                for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
                    float dx = x + 0.5f - cx;
                    float dy = y + 0.5f - cy;
                    if (dx * dx + dy * dy <= r * r) {
                        px(x, y, argb);
                    }
                }
            }
        }

        // 菱形 (r = 中心から角まで)。hi があれば右上の半分を明るく
        public void diamond(int cx, int cy, int r, int argb, int hi) {
            for (int j = -r; j <= r; j++) {
                int span = r - Math.abs(j);
                for (int i = -span; i <= span; i++) {
                    boolean upperRight = hi != 0 && j < 0 && i >= 0;
                    px(cx + i, cy + j, upperRight ? hi : argb);
                }
            }
        }
    }

    // ---------- 形 ----------
    // 回した四角 (中心・幅・高さ・度)
    public static float[] rotRect(float cx, float cy, float w, float h, float deg) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float[] q = {-w / 2, -h / 2, w / 2, -h / 2, w / 2, h / 2, -w / 2, h / 2};
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            out[i * 2] = cx + q[i * 2] * c - q[i * 2 + 1] * s;
            out[i * 2 + 1] = cy + q[i * 2] * s + q[i * 2 + 1] * c;
        }
        return out;
    }

    // ---------- 釦の紙 (A 斜めの短冊。2026-11-24 にユーザーが 3 案から選んだ。docs/review/paper_ui/buttons/) ----------
    // 右へ傾けた短冊の紙。左の端に別の色の斜めの筋を一本 (主の釦は生成り、ほかは桃色)。下に黒い紙の硬い影。
    // 乗せると紙が浮いて影が伸び、押している間は影が消えて紙が沈む。押せないときは灰の紙を平らに (筋なし)
    public static final int REST = 0, HOVER = 1, PRESS = 2, FLAT = 3, LOW = 4;
    public static final int CREAM_HI = 0xFFFFF6EA;
    public static final int TEXT_OFF = 0xC4BCC4;

    // 状態。hot = 乗せている か 手元 (キー) で選んでいる、over = マウスが上にある (押している = over で左の釦を押さえている)
    public static int state(boolean enabled, boolean hot, boolean over) {
        if (!enabled) {
            return FLAT;
        }
        if (!hot) {
            return REST;
        }
        return over && Minecraft.getInstance().mouseHandler.isLeftPressed() ? PRESS : HOVER;
    }

    public static int state(boolean enabled, boolean hover) {
        return state(enabled, hover, hover);
    }

    // 斜めの切り口の幅
    static int slant(int h) {
        return Math.max(2, Math.round(h * 0.23f));
    }

    // 筋を入れても字が筋に触れないか (入らなければ筋を省く)
    public static boolean roomy(Font font, Component label, int w, int h) {
        return font.width(label) <= w - slant(h) - 10;
    }

    // 紙を描いて、字をずらす量 {dx, dy} を返す。stripe = 0 なら筋なし
    public static int[] slip(GuiGraphics g, int x, int y, int w, int h, int body, int stripe, int state) {
        int sl = slant(h);
        int dx = state == PRESS ? 1 : 0;
        int dy = state == HOVER ? -1 : state == PRESS ? 1 : 0;
        int o = 4;
        String key = Integer.toHexString(body) + "_" + Integer.toHexString(stripe) + "_" + state + "_" + w + "x" + h;
        draw(g, "slip_" + key, "", x - o, y - o, w + 2 * o + 2, h + 2 * o + 2, s -> {
            float l = o + dx;
            float t = o + dy;
            float[] p = {l + sl, t, l + w, t, l + w - sl, t + h, l, t + h};
            if (state == REST) {
                s.poly(p, BLACK_LO, 1, 2);
            } else if (state == HOVER) {
                s.poly(p, BLACK_LO, 2, 3);
            } else if (state == LOW) {
                s.poly(p, BLACK_LO, 1, 1);
            }
            s.poly(p, body);
            if (stripe != 0 && state != FLAT) {
                s.poly(new float[] {l + sl + 3, t, l + sl + 5, t, l + 5, t + h, l + 3, t + h}, stripe);
            }
        });
        return new int[] {dx, dy};
    }

    // 釦 (主は桃色に生成りの筋、ほかは生成りに桃色の筋)
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean primary, int state) {
        int body = state == FLAT ? GREY_LO : primary ? (state == HOVER ? PINK_HI : state == PRESS ? 0xFFCC4A8A : PINK)
                : (state == HOVER ? CREAM_HI : state == PRESS ? CREAM_LO : CREAM);
        boolean roomy = roomy(font, label, w, h);
        int[] d = slip(g, x, y, w, h, body, roomy ? (primary ? CREAM : PINK) : 0, state);
        label(g, font, label, x + d[0], y + d[1], w, h, roomy, state == FLAT ? TEXT_OFF : INK);
    }

    // つまみ (選んでいれば桃色に生成りの筋、ほかは一段沈んだ生成りに桃色の筋。乗せると浮く)
    public static void tab(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean on, boolean hover) {
        int state = on ? REST : hover ? HOVER : LOW;
        int body = on ? PINK : hover ? CREAM : CREAM_LO;
        boolean roomy = roomy(font, label, w, h);
        int[] d = slip(g, x, y, w, h, body, roomy ? (on ? CREAM : PINK) : 0, state);
        label(g, font, label, x + d[0], y + d[1], w, h, roomy, INK);
    }

    // 札の字: 筋があれば筋の右から、なければ斜めの切り口の内側で真ん中に。入らなければ横へ流す (FitText)
    private static void label(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean stripe, int color) {
        int sl = slant(h);
        int left = stripe ? sl / 2 + 8 : (sl + 1) / 2 + 1;
        int right = w - (sl + 1) / 2 - 1;
        int room = right - left;
        int tw = Math.min(font.width(label), room);
        FitText.draw(g, font, label, x + left + (room - tw) / 2, y + (h - 7) / 2, room, color, false);
    }

    // 枠 (細い縁の右と下に濃い桃色の影。夜空の Golden.outline の代わり)。x1, y1 は右下の外
    public static void edge(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 1, y1, x1 + 1, y1 + 1, PINK_LO);
        g.fill(x1, y0 + 1, x1 + 1, y1, PINK_LO);
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    // Button.builder の代わり (同じ書き方で Tag を作る)。主の釦は .primary()
    public static TagBuilder tag(Component text, net.minecraft.client.gui.components.Button.OnPress onPress) {
        return new TagBuilder(text, onPress);
    }

    public static final class TagBuilder {
        private final Component text;
        private final net.minecraft.client.gui.components.Button.OnPress onPress;
        private int x;
        private int y;
        private int w = 150;
        private int h = 20;
        private boolean primary;
        private net.minecraft.client.gui.components.Tooltip tooltip;

        TagBuilder(Component text, net.minecraft.client.gui.components.Button.OnPress onPress) {
            this.text = text;
            this.onPress = onPress;
        }

        public TagBuilder bounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public TagBuilder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public TagBuilder size(int w, int h) {
            this.w = w;
            this.h = h;
            return this;
        }

        public TagBuilder width(int w) {
            this.w = w;
            return this;
        }

        public TagBuilder tooltip(net.minecraft.client.gui.components.Tooltip t) {
            this.tooltip = t;
            return this;
        }

        public TagBuilder primary() {
            this.primary = true;
            return this;
        }

        public Tag build() {
            Tag t = new Tag(x, y, w, h, text, onPress, primary);
            t.setTooltip(tooltip);
            return t;
        }
    }

    // 切り絵の札の釦 (Button の代わりに使う)。主の釦は桃色、ほかは生成り。押せないときは灰
    public static class Tag extends net.minecraft.client.gui.components.Button {
        private final boolean primary;

        public Tag(int x, int y, int w, int h, Component text, OnPress onPress, boolean primary) {
            super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            button(g, Minecraft.getInstance().font, getX(), getY(), width, height, getMessage(), primary, state(active, isHoveredOrFocused(), isHovered()));
        }
    }
}
