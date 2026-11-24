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
    public static final int BLACK = 0xFF181C28;
    public static final int BLACK_LO = 0xFF0F121A;
    public static final int PINK = 0xFFC48A44;
    public static final int PINK_HI = 0xFFF2C47C;
    public static final int PINK_LO = 0xFF83502A;
    public static final int GREY = 0xFFA8A29A;
    public static final int GREY_LO = 0xFF3A4054;
    public static final int CREAM = 0xFFECE4D6;
    public static final int CREAM_LO = 0xFF2A3044;
    // 字の色 (生成りの紙の上 / 黒い紙の上)
    public static final int INK = 0xECE6D6;
    public static final int INK_HEAD = 0xF2C47C;
    public static final int INK_SOFT = 0xA9ABB8;
    public static final int ON_BLACK = 0xECE4D6;
    public static final int ON_BLACK_SOFT = 0xA9ABB8;
    public static final int ON_BLACK_PINK = 0xF2C47C;
    // 黒い紙の上の目立つ字 (夜空の空色の代わり) と、まだ使えないものの字
    public static final int ON_BLACK_HI = 0xFFF6EA;
    public static final int ON_BLACK_DIM = 0x6C7084;
    // 黒い紙の上の細い縁 (升・枠)
    public static final int EDGE = 0xFF2A3044;

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
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
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
    public static final int TEXT_OFF = 0xA9ABB8;

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
        boolean pink = (body & 0xFFFFFF) == 0xE45A9C || (body & 0xFFFFFF) == 0xF89CD2 || (body & 0xFFFFFF) == 0xF2C47C || (body & 0xFFFFFF) == 0xC48A44 || (body & 0xFFFFFF) == 0xA8743A || (body & 0xFFFFFF) == 0xCC4A8A;
        Plate.tile(g, x, y, w, h, state == FLAT ? Plate.FLAT : pink ? Plate.ON : state == HOVER ? Plate.HOVER : Plate.REST, Plate.GOLD);
        return new int[] {0, 0};
    }

    // 釦 (主は桃色に生成りの筋、ほかは生成りに桃色の筋)
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean primary, int state) {
        Plate.button(g, font, x, y, w, h, label, primary, state == LOW ? Plate.REST : state, Plate.GOLD);
    }

    // つまみ (選んでいれば桃色に生成りの筋、ほかは一段沈んだ生成りに桃色の筋。乗せると浮く)
    public static void tab(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean on, boolean hover) {
        Plate.tab(g, font, x, y, w, h, label, on, hover);
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
        // 舞台の大きさの板を一枚 (窓の端までは敷かない。後ろの景色はぼかして見せる)。peaks・dm は切り絵のときの引数で、使わない
        Plate.openFade(g);
        Plate.panel(g, 0, 0, STAGE_W, STAGE_H, 0, Plate.GOLD);
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
        // 板の絵柄: 紙の外接の四角を、紙の色に合う部品で (傾きは持たない)
        int bx = Math.round(minX);
        int by = Math.round(minY);
        Plate.piece(g, bx, by, Math.round(maxX) - bx, Math.round(maxY) - by, body);
    }

    // 四隅を手でずらした四角の紙 (x, y, w, h と四隅のずれ {左上 y, 右上 y, 右下 x, 左下 x})
    public static float[] quad(float x, float y, float w, float h, float a, float b, float c, float d) {
        return new float[] {x, y + a, x + w, y + b, x + w + c, y + h, x + d, y + h};
    }

    // 菱の留め (r = 中心から角まで)
    public static void pin(GuiGraphics g, int cx, int cy, int r, boolean pink) {
        Plate.gem(g, cx, cy, Math.max(1, r - 1), pink ? 0xFFF2C47C : 0xFF8A90A4);
    }

    // ---------- 生成りの紙の上の字 ----------
    // 窓の外 (舞台の外) を薄く暗くする地
    public static void dim(GuiGraphics g, int width, int height) {
        Plate.blur();
        Plate.backdrop(g, width, height, 1f);
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
