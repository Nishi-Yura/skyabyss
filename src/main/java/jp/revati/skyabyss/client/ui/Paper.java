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

    // ---------- 舞台 (論理の 408x228。職を選ぶ画面・スキルの木と同じ。docs/design/56 の 2.1) ----------
    public static final int STAGE_W = 408;
    public static final int STAGE_H = 228;

    // 黒い紙と下の桃色の山を窓の端まで敷く (fit.begin のあと、論理の座標で)。peaks = 山の峰 {論理の x, base 226 からの高さ}、
    // x が 0 より小さい / STAGE_W より大きい峰は窓の端まで伸ばす。dm = 紙の上の菱 {x, y, 大きさ, 桃色なら 1}
    public static void stage(GuiGraphics g, UiFit fit, int width, int height, String name, float[][] peaks, int[][] dm) {
        inkDepth = 0;
        int x0 = (int) Math.floor(fit.x(0));
        int y0 = (int) Math.floor(fit.y(0));
        int sw = (int) Math.ceil(fit.x(width)) - x0;
        int sh = (int) Math.ceil(fit.y(height)) - y0;
        int ox = -x0;
        int oy = -y0;
        draw(g, "stage_" + name, ox + "," + oy + "," + sw + "x" + sh, x0, y0, sw, sh, s -> {
            float[] black = {7, 10, sw * 0.22f, 6, sw * 0.47f, 9, sw * 0.71f, 4, sw - 9, 8, sw - 5, sh * 0.30f, sw - 8, sh * 0.62f, sw - 4, sh - 12,
                    sw * 0.64f, sh - 6, sw * 0.31f, sh - 9, 10, sh - 5, 5, sh * 0.58f, 8, sh * 0.27f};
            s.poly(black, BLACK);
            float base = oy + 226;
            float[] p = new float[peaks.length * 2 + 4];
            for (int i = 0; i < peaks.length; i++) {
                p[i * 2] = peaks[i][0] < 0 ? -20 : peaks[i][0] > STAGE_W ? sw + 20 : ox + peaks[i][0];
                p[i * 2 + 1] = base + peaks[i][1];
            }
            p[peaks.length * 2] = sw + 20;
            p[peaks.length * 2 + 1] = sh + 4;
            p[peaks.length * 2 + 2] = -20;
            p[peaks.length * 2 + 3] = sh + 4;
            s.poly(p, PINK_LO);
            for (int i = 0; i + 1 < peaks.length; i++) {
                float ax = p[i * 2], ay = p[i * 2 + 1], bx = p[i * 2 + 2], by = p[i * 2 + 3];
                int n = (int) Math.max(1, Math.abs(bx - ax));
                for (int k = 0; k <= n; k++) {
                    s.px(Math.round(ax + (bx - ax) * k / n), Math.round(ay + (by - ay) * k / n), PINK);
                }
            }
            for (int[] d : dm) {
                s.diamond(ox + d[0], oy + d[1], d[2], d[3] == 1 ? PINK : GREY, d[3] == 1 ? PINK_HI : 0xFFD8D2CA);
            }
        });
    }

    // 紙一枚 (点の並びは論理の座標)。under が 0 でなければ、その色の影を (ux, uy) ずらして下に。
    // 名前ごとに一枚の絵を使い回し、形か色が変わったときだけ塗り直す (行ごとの紙は名前に行の番号を入れる)
    public static void cut(GuiGraphics g, String name, float[] pts, int body, int under, float ux, float uy) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < pts.length; i += 2) {
            minX = Math.min(minX, pts[i]);
            maxX = Math.max(maxX, pts[i]);
            minY = Math.min(minY, pts[i + 1]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        int x = (int) Math.floor(minX) - 1;
        int y = (int) Math.floor(minY) - 1;
        int w = (int) Math.ceil(maxX) - x + 2 + (int) Math.ceil(Math.abs(ux));
        int h = (int) Math.ceil(maxY) - y + 2 + (int) Math.ceil(Math.abs(uy));
        float[] q = pts.clone();
        for (int i = 0; i < q.length; i += 2) {
            q[i] -= x;
            q[i + 1] -= y;
        }
        StringBuilder key = new StringBuilder(Integer.toHexString(body)).append(':').append(Integer.toHexString(under));
        for (float f : pts) {
            key.append(',').append(Math.round(f * 4));
        }
        draw(g, "cut_" + name, key.toString(), x, y, w, h, s -> {
            if (under != 0) {
                s.poly(q, under, ux, uy);
            }
            s.poly(q, body);
        });
    }

    // 四隅を手でずらした四角の紙 (x, y, w, h と四隅のずれ {左上 y, 右上 y, 右下 x, 左下 x})
    public static float[] quad(float x, float y, float w, float h, float a, float b, float c, float d) {
        return new float[] {x, y + a, x + w, y + b, x + w + c, y + h, x + d, y + h};
    }

    // 菱の留め (r = 中心から角まで)
    public static void pin(GuiGraphics g, int cx, int cy, int r, boolean pink) {
        draw(g, "pin_" + r + "_" + pink, "", cx - r, cy - r, 2 * r + 1, 2 * r + 1, s -> s.diamond(r, r, r, pink ? PINK : GREY, pink ? PINK_HI : 0xFFD8D2CA));
    }

    // ---------- 生成りの紙の上の字 ----------
    // inkBegin と inkEnd のあいだに描く字 (drawString の地の色) は、黒い紙の上の色から生成りの上の墨の色へ写す (mixin/GuiGraphicsMixin)。
    // 黒い紙の上の色で書いてある画面の中身を、そのまま生成りの紙に載せるため。字の色が文の中で決めてあるもの (品の稀さの色など) は写らない
    private static int inkDepth;

    public static void inkBegin() {
        inkDepth++;
    }

    public static void inkEnd() {
        inkDepth = Math.max(0, inkDepth - 1);
    }

    public static boolean inking() {
        return inkDepth > 0;
    }

    public static int inkColor(int argb) {
        return inkDepth <= 0 ? argb : inkOf(argb);
    }

    // 黒い紙の上の字の色 -> 生成りの上の墨の色 (いつでも写す)
    public static int inkOf(int argb) {
        int a = argb & 0xFF000000;
        if (a == 0) {
            a = 0xFF000000;
        }
        int rgb = argb & 0xFFFFFF;
        int out;
        switch (rgb) {
            case 0xF89CD2, 0xE9C46A, 0xE45A9C, 0xF2D78A -> out = INK_HEAD;
            case 0x9A929C, 0xB4BBD6, 0xC4BCC4, 0xA8A29A -> out = INK_SOFT;
            case 0x7C7480, 0x8892B6, 0x707070, 0x808080, 0x5A5460 -> out = 0x8A8086;
            case 0xD9779A, 0xF0607A, 0xE87C9A, 0xFF5555 -> out = 0xB0102C;
            case 0x7BD66B, 0x55FF55, 0x7FFFA0 -> out = 0x2E7A3A;
            default -> {
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // 明るい字 (黒の上の地の文・目立つ字) は墨、ほかの色はそのまま
                out = r * 3 + g * 6 + b > 1800 ? INK : rgb;
            }
        }
        return a | out;
    }

    // 窓の外 (舞台の外) を薄く暗くする地
    public static void dim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, 0xA00E0D14);
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
