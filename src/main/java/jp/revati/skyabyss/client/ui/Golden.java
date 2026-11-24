package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 画面の組み方の決まり (docs/design/56_ui_layout.md)。どの画面もここの寸法と描き方を使う:
//   板は横:縦 = 1.618:1 の大 (356x220)・中 (288x178)・小 (233x144)。縁から中身までは 8、見出しの帯は 21、下の段は 21 か 34。
//   中身は主の列 0.618 と脇の列 0.382 に分ける (あいだ 5)。余白は 3・5・8・13・21・34・55 だけ。
//   字は等倍だけ (縮めた字は使わない)。文と一覧の行は 13、釦の高さは 13。強弱は色で (金・空色・淡い灰)。
//   板を大きく描くときは UiFit が字の 1 画素を画面の整数の画素にそろえる。
// 絵柄は切り絵 (2026-11-24 に夜空の硝子から替えた。docs/design/56 の 2.1): 黒い紙・生成りの札・桃色・灰の菱。HUD の枠 (hudFrame) だけは夜空のまま
public final class Golden {
    private Golden() {
    }

    public static final double PHI = 1.618;
    // 余白の段 (フィボナッチ)
    public static final int S3 = 3;
    public static final int S5 = 5;
    public static final int S8 = 8;
    public static final int S13 = 13;
    public static final int S21 = 21;
    public static final int S34 = 34;
    public static final int S55 = 55;
    public static final int S89 = 89;
    // 板の外から中身まで (縁 5 + 余白 3)
    public static final int FRAME = 8;
    // 欄と欄のあいだ
    public static final int GAP = 5;
    // 見出しの帯
    public static final int HEAD = 21;
    // 文と一覧の一行
    public static final int ROW = 13;
    // 釦の高さ
    public static final int BTN = 13;
    // 下の段 (釦一列 / 二列・技の枠)
    public static final int FOOT = 21;
    public static final int FOOT2 = 34;
    // 板の大きさ (横:縦 = 1.618:1)
    public static final int L_W = 356;
    public static final int L_H = 220;
    public static final int M_W = 288;
    public static final int M_H = 178;
    public static final int S_W = 233;
    public static final int S_H = 144;

    // 字の色 (絵柄 A「夜空の硝子」。docs/design/56 の 2 章)
    public static final int TEXT = 0xECE8DC;
    public static final int SOFT = 0xB4BBD6;
    // 手がかり・まだ使えないもの (藍の地の上で読める明るさ)
    public static final int DIM = 0x8892B6;
    public static final int GOLD = 0xE9C46A;
    public static final int PALE = 0x8FD0FF;
    public static final int TEAL = 0x8FD0FF;
    public static final int DENY = 0xE87C9A;
    public static final int MANA = 0x9FB8FF;

    // 板の地 (上から下へ少し明るくなる藍。後ろの景色が少し透ける)
    public static final int GLASS_TOP = 0xEC0C1022;
    public static final int GLASS_BOTTOM = 0xEC161C38;
    // HUD の枠の地 (板より透ける)
    public static final int GLASS_HUD = 0xC80A0E1E;
    public static final int OUTLINE = 0xFF05070F;
    // 金の細い線
    public static final int LINE = 0xFFC9A85C;
    public static final int LINE_DIM = 0x96C9A85C;
    public static final int RECESS_FILL = 0x9604060F;
    public static final int RECESS_EDGE = 0xFF364064;
    static final int SELECT = 0x308FD0FF;

    // 幅 total を主 (0.618) と脇 (0.382) に分けたときの主の幅 (あいだ GAP を引いてから)
    public static int major(int total) {
        return (int) Math.round((total - GAP) * 0.618);
    }

    public static int minor(int total) {
        return total - GAP - major(total);
    }

    // 高さ h の行の中で、字 (高さ 8) を真ん中に置くときの y
    public static int textY(int y, int h) {
        return y + (h - 7) / 2;
    }

    public record Box(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public int cx() {
            return x + w / 2;
        }

        public Box inset(int d) {
            return new Box(x + d, y + d, w - 2 * d, h - 2 * d);
        }

        public Box inset(int dx, int dy) {
            return new Box(x + dx, y + dy, w - 2 * dx, h - 2 * dy);
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public Box left(int ww) {
            return new Box(x, y, ww, h);
        }

        public Box right(int ww) {
            return new Box(x + w - ww, y, ww, h);
        }

        public Box top(int hh) {
            return new Box(x, y, w, hh);
        }

        public Box under(int hh) {
            return new Box(x, y + h - hh, w, hh);
        }


        // 上から hh を取ったあと (あいだ GAP) の残り
        public Box afterTop(int hh) {
            return new Box(x, y + hh + GAP, w, h - hh - GAP);
        }

        // 主の列 (0.618)。left なら左に置く
        public Box major(boolean left) {
            int m = Golden.major(w);
            return left ? new Box(x, y, m, h) : new Box(x + w - m, y, m, h);
        }

        // 脇の列 (0.382)。left なら左に置く
        public Box minor(boolean left) {
            int n = Golden.minor(w);
            return left ? new Box(x, y, n, h) : new Box(x + w - n, y, n, h);
        }



        // i 番目の行 (高さ rowH、上から pad あけて)
        public Box row(int i, int rowH, int pad) {
            return new Box(x, y + pad + i * rowH, w, rowH);
        }
    }

    // 板の区切り: 縁の内側 (inner)、見出しの帯 (head)、中身 (body)、下の段 (foot。無ければ null)
    public record Layout(Box panel, Box inner, Box head, Box body, Box foot) {
        public static Layout of(int x, int y, int w, int h, int footH) {
            Box panel = new Box(x, y, w, h);
            Box inner = panel.inset(FRAME);
            Box head = new Box(inner.x(), inner.y(), inner.w(), HEAD);
            int by = head.bottom() + GAP;
            int bb = footH > 0 ? inner.bottom() - footH - GAP : inner.bottom();
            Box body = new Box(inner.x(), by, inner.w(), bb - by);
            Box foot = footH > 0 ? new Box(inner.x(), inner.bottom() - footH, inner.w(), footH) : null;
            return new Layout(panel, inner, head, body, foot);
        }

        public static Layout large(int footH) {
            return of(0, 0, L_W, L_H, footH);
        }

        public static Layout medium(int footH) {
            return of(0, 0, M_W, M_H, footH);
        }

        public static Layout small(int footH) {
            return of(0, 0, S_W, S_H, footH);
        }

        // 中身の主の列と脇の列を、下の段にもそろえる (下の段の左右の区切りは中身の列の境目)
        public Box footUnder(Box column) {
            return foot == null ? null : new Box(column.x(), foot.y(), column.w(), foot.h());
        }
    }

    // ---------------------------------------------------------------- 描く

    // 板 (切り絵。docs/design/56 の「切り絵」): 画面の大中小の板 (論理の 0,0 に置いた Layout の板) なら、黒い紙と桃色の山を窓の端まで敷く。
    // それ以外の板 (会話の帯・部品の板) は、手で切った黒い紙を一枚 (濃い桃色の影をずらして)
    public static void panel(GuiGraphics g, Box b) {
        if (UiLook.plate()) {
            // 板の絵柄: 板の大きさのまま銅の縁の板を一枚 (画面の大中小の板は開くときに濃くなる)
            if (b.x() == 0 && b.y() == 0) {
                Plate.openFade(g);
            }
            Plate.panel(g, b.x(), b.y(), b.w(), b.h(), 0, Plate.GOLD);
            return;
        }
        if (b.x() == 0 && b.y() == 0 && (b.w() == L_W && b.h() == L_H || b.w() == M_W && b.h() == M_H || b.w() == S_W && b.h() == S_H)) {
            sheet(g, b);
            return;
        }
        int w = b.w();
        int h = b.h();
        // 辺は少しずつ斜めに切る (角の点は手で決めた量だけずらす)
        float[] cut = {-2, 1, w * 0.38f, -1, w + 1, 2, w + 2, h * 0.55f, w, h + 1, w * 0.62f, h + 2, 1, h, -1, h * 0.4f};
        float[] pts = new float[cut.length];
        for (int i = 0; i < cut.length; i += 2) {
            pts[i] = cut[i] + 4;
            pts[i + 1] = cut[i + 1] + 4;
        }
        Paper.draw(g, "golden_panel_" + w + "x" + h, "", b.x() - 4, b.y() - 4, w + 12, h + 12, s -> s.layer(pts, Paper.BLACK, Paper.PINK_LO, 3, 4));
    }

    // 窓の端まで敷く黒い紙と、下の桃色の山 (論理の座標で窓の四隅を求める)。峰は手で置いた
    private static final float[][] PEAKS = {{0.00f, 6}, {0.06f, 1}, {0.15f, 7}, {0.27f, 0}, {0.36f, 5}, {0.49f, 2}, {0.58f, 8}, {0.70f, 1},
            {0.81f, 6}, {0.90f, 0}, {1.00f, 4}};

    public static void sheet(GuiGraphics g, Box b) {
        org.joml.Matrix4f inv = new org.joml.Matrix4f(g.pose().last().pose()).invert();
        org.joml.Vector3f p0 = inv.transformPosition(new org.joml.Vector3f(0, 0, 0));
        org.joml.Vector3f p1 = inv.transformPosition(new org.joml.Vector3f(g.guiWidth(), g.guiHeight(), 0));
        int x0 = (int) Math.floor(p0.x);
        int y0 = (int) Math.floor(p0.y);
        int sw = (int) Math.ceil(p1.x) - x0;
        int sh = (int) Math.ceil(p1.y) - y0;
        int ox = b.x() - x0;
        int oy = b.y() - y0;
        // 山の峰: 板の下の端のすぐ下 (板の中の字にはかからない)
        float base = oy + b.h() + 2;
        Paper.draw(g, "golden_sheet", ox + "," + oy + "," + sw + "x" + sh + "," + b.w() + "x" + b.h(), x0, y0, sw, sh, s -> {
            float[] black = {
                    6, 9, sw * 0.27f, 5, sw * 0.52f, 8, sw * 0.78f, 4, sw - 7, 7,
                    sw - 4, sh * 0.34f, sw - 7, sh * 0.66f, sw - 5, sh - 10,
                    sw * 0.58f, sh - 5, sw * 0.24f, sh - 8, 8, sh - 4,
                    4, sh * 0.62f, 7, sh * 0.31f};
            s.poly(black, Paper.BLACK);
            float[] hill = new float[PEAKS.length * 2 + 4];
            for (int i = 0; i < PEAKS.length; i++) {
                hill[i * 2] = -20 + (sw + 40) * PEAKS[i][0];
                hill[i * 2 + 1] = base + PEAKS[i][1];
            }
            hill[PEAKS.length * 2] = sw + 20;
            hill[PEAKS.length * 2 + 1] = sh + 4;
            hill[PEAKS.length * 2 + 2] = -20;
            hill[PEAKS.length * 2 + 3] = sh + 4;
            s.poly(hill, Paper.PINK_LO);
            for (int i = 0; i + 1 < PEAKS.length; i++) {
                float ax = hill[i * 2];
                float ay = hill[i * 2 + 1];
                float bx = hill[i * 2 + 2];
                float by = hill[i * 2 + 3];
                int n = (int) Math.max(1, Math.abs(bx - ax));
                for (int k = 0; k <= n; k++) {
                    s.px(Math.round(ax + (bx - ax) * k / n), Math.round(ay + (by - ay) * k / n), Paper.PINK);
                }
            }
            // 紙の上の小さな菱 (板の外の余白に、手で置いた)
            int[][] dm = {{-14, 30, 2, 1}, {b.w() + 10, 16, 3, 1}, {b.w() + 12, b.h() / 2 + 20, 1, 0}, {-12, b.h() - 40, 1, 0}, {b.w() / 3, -9, 2, 0}};
            for (int[] q : dm) {
                s.diamond(ox + q[0], oy + q[1], q[2], q[3] == 1 ? Paper.PINK : Paper.GREY_LO, q[3] == 1 ? Paper.PINK_HI : Paper.GREY);
            }
        });
    }

    // 見出しの帯の下の区切り (濃い桃色の細い線と、左寄りの桃色の菱)
    public static void headLine(GuiGraphics g, Layout l) {
        int y = l.head().bottom() + 2;
        g.fill(l.inner().x(), y, l.inner().right(), y + 1, Paper.PINK_LO);
        int cx = l.inner().x() + Math.round(l.inner().w() * 0.31f);
        diamond(g, cx, y, 3, Paper.BLACK);
        diamond(g, cx, y, 2, Paper.PINK);
    }

    // 板・帯の線・題 (帯の右寄せ。null なら描かない) をまとめて
    public static void frame(GuiGraphics g, Font font, Layout l, Component title) {
        panel(g, l.panel());
        headLine(g, l);
        if (title != null) {
            Box h = l.head();
            FitText.draw(g, font, title, h.right() - S5 - Math.min(font.width(title), h.w() / 2), textY(h.y(), h.h()), h.w() / 2, Paper.ON_BLACK_PINK, false);
        }
    }

    // 欄: 黒い紙の上に一段暗い紙を重ねる (右と下に濃い桃色の細い影)
    public static void recess(GuiGraphics g, Box b) {
        if (UiLook.plate()) {
            if (b.w() >= 4 && b.h() >= 4) {
                Plate.well(g, b.x(), b.y(), b.w(), b.h());
            }
            return;
        }
        g.fill(b.x() + 2, b.bottom(), b.right() + 2, b.bottom() + 2, Paper.PINK_LO);
        g.fill(b.right(), b.y() + 2, b.right() + 2, b.bottom(), Paper.PINK_LO);
        g.fill(b.x(), b.y(), b.right(), b.bottom(), Paper.BLACK_LO);
    }

    // 選んでいる行の地 (濃い桃色の地と左の桃色の印)
    public static void selected(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x708C2C60);
        g.fill(b.x(), b.y(), b.x() + 2, b.bottom(), Paper.PINK);
    }

    // 一行おきの地 (数の表を横に読みやすく)
    public static void stripe(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x10ECE4D6);
    }

    // 欄の中の区切りの線
    public static void rule(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, 0xFF3A3644);
    }

    // つまみ (切り絵の札。Paper.tab: 選んでいれば桃色、ほかは一段沈んだ生成り)
    public static void tab(GuiGraphics g, Font font, Box b, Component label, boolean on, boolean hover) {
        Paper.tab(g, font, b.x(), b.y(), b.w(), b.h(), label, on, hover);
    }

    // 釦 (高さ 13。切り絵の札。Paper.button: primary は桃色、ほかは生成り、押せないときは灰。押している間は沈む)
    public static void button(GuiGraphics g, Font font, Box b, Component label, boolean primary, boolean hover, boolean enabled) {
        Paper.button(g, font, b.x(), b.y(), b.w(), b.h(), label, primary, Paper.state(enabled, hover));
    }

    // 品の枠 (size 角。一段暗い紙の升に、灰の細い縁)
    public static void slot(GuiGraphics g, int x, int y, int size) {
        if (UiLook.plate()) {
            Plate.well(g, x, y, size, size);
            return;
        }
        g.fill(x, y, x + size, y + size, Paper.BLACK_LO);
        outline(g, x, y, x + size, y + size, 0xFF3A3644);
    }

    // HUD の枠 (藍の半透明の地・外の暗い線・金の細い線)
    // HUD の地 (切り絵: 黒い紙に右下の濃い桃色の影。2026-11-24 に HUD も夜空から替えた。docs/design/56 の 5)
    public static void hudFrame(GuiGraphics g, int x0, int y0, int x1, int y1) {
        if (UiLook.plate()) {
            Plate.hud(g, x0, y0, x1, y1);
            return;
        }
        g.fill(x0 + 1, y0 + 1, x1 + 1, y1 + 1, Paper.PINK_LO);
        g.fill(x0, y0, x1, y1, Paper.BLACK);
        g.fill(x0, y0, x1, y0 + 1, Paper.EDGE);
    }

    // 空の欄の一行 (何をすると埋まるか)。欄の真ん中に淡い字で、幅で折り返す
    public static void empty(GuiGraphics g, Font font, Box b, Component text) {
        var lines = font.split(text, Math.max(S55, b.w() - 2 * S13));
        int y = b.y() + (b.h() - lines.size() * ROW) / 2;
        for (var line : lines) {
            g.drawString(font, line, b.cx() - font.width(line) / 2, y + 3, SOFT, false);
            y += ROW;
        }
    }

    // 菱形 (半径 r)。切り絵の桃色の菱 (黒い縁)
    public static void gem(GuiGraphics g, int cx, int cy, int r) {
        diamond(g, cx, cy, r + 1, Paper.BLACK_LO);
        diamond(g, cx, cy, r, Paper.PINK);
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    public static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }
}
