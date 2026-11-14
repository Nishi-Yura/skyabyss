package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 画面の組み方の決まり (docs/design/56_ui_layout.md)。どの画面もここの寸法と描き方を使う:
//   板は横:縦 = 1.618:1 の大 (356x220)・中 (288x178)・小 (233x144)。縁から中身までは 8、見出しの帯は 21、下の段は 21 か 34。
//   中身は主の列 0.618 と脇の列 0.382 に分ける (あいだ 5)。余白は 3・5・8・13・21・34・55 だけ。
//   字は等倍だけ (縮めた字は使わない)。文と一覧の行は 13、釦の高さは 13。強弱は色で (金・空色・淡い灰)。
//   板を大きく描くときは UiFit が字の 1 画素を画面の整数の画素にそろえる。
// 絵柄は A「夜空の硝子」: 藍の半透明の地・金の細い線・角の菱形・見出しの帯の星。地も線もコードで描く (絵のファイルもリソースパックも要らない)
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

        // 左から ww を取ったあと (あいだ GAP) の残り
        public Box afterLeft(int ww) {
            return new Box(x + ww + GAP, y, w - ww - GAP, h);
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

        // 縦に分けたときの上の主 (0.618) と下の脇 (0.382)
        public Box majorTop() {
            return new Box(x, y, w, Golden.major(h));
        }

        public Box minorBottom() {
            int n = Golden.minor(h);
            return new Box(x, y + h - n, w, n);
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

    // 板: 藍の地・外の暗い線・金の細い線・四隅の菱形・見出しの帯の星
    public static void panel(GuiGraphics g, Box b) {
        int x0 = b.x();
        int y0 = b.y();
        int x1 = b.right();
        int y1 = b.bottom();
        g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GLASS_TOP, GLASS_BOTTOM);
        stars(g, x0 + S5, y0 + S5, b.w() - 2 * S5, HEAD);
        outline(g, x0, y0, x1, y1, OUTLINE);
        outline(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, LINE);
        gem(g, x0 + 2, y0 + 2, 2);
        gem(g, x1 - 3, y0 + 2, 2);
        gem(g, x0 + 2, y1 - 3, 2);
        gem(g, x1 - 3, y1 - 3, 2);
    }

    // 見出しの帯の星 (置き場は板の幅だけで決まる。動かない)
    private static void stars(GuiGraphics g, int x, int y, int w, int h) {
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 22; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int sx = x + (int) ((seed >>> 33) % Math.max(1, w));
            int sy = y + (int) ((seed >>> 20) % Math.max(1, h));
            int a = 0x40 + (int) ((seed >>> 12) & 0x7F);
            g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xC8D6FF);
        }
    }

    // 板と見出しの帯の区切りの線 (帯の下、あいだの真ん中) と真ん中の小さな菱形
    public static void headLine(GuiGraphics g, Layout l) {
        int y = l.head().bottom() + 2;
        g.fill(l.inner().x(), y, l.inner().right(), y + 1, LINE_DIM);
        gem(g, l.inner().cx(), y, 2);
    }

    // 板・帯の線・題 (帯の右寄せ。null なら描かない) をまとめて
    public static void frame(GuiGraphics g, Font font, Layout l, Component title) {
        panel(g, l.panel());
        headLine(g, l);
        if (title != null) {
            Box h = l.head();
            FitText.draw(g, font, title, h.right() - S5 - Math.min(font.width(title), h.w() / 2), textY(h.y(), h.h()), h.w() / 2, GOLD, false);
        }
    }

    // 欄 (一段暗い地に細い縁)
    public static void recess(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), RECESS_FILL);
        outline(g, b.x(), b.y(), b.right(), b.bottom(), RECESS_EDGE);
    }

    // 選んでいる行の地 (空色の地と左の金の印)
    public static void selected(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), SELECT);
        g.fill(b.x(), b.y(), b.x() + 2, b.bottom(), 0xFF000000 | GOLD);
    }

    // 一行おきの地 (数の表を横に読みやすく)
    public static void stripe(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x14FFFFFF);
    }

    // 欄の中の区切りの線
    public static void rule(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, RECESS_EDGE);
    }

    // つまみ (見出しの帯の中。選んでいれば金の字と下の線)
    public static void tab(GuiGraphics g, Font font, Box b, Component label, boolean on, boolean hover) {
        if (on) {
            g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x28E9C46A);
            g.fill(b.x(), b.bottom() - 1, b.right(), b.bottom(), 0xFF000000 | GOLD);
        } else if (hover) {
            g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x18FFFFFF);
        }
        int color = on ? GOLD : hover ? TEXT : SOFT;
        int w = font.width(label);
        g.drawString(font, label, b.x() + (b.w() - w) / 2, textY(b.y(), b.h()), color, false);
    }

    // 釦 (高さ 13)。primary は金の地に暗い字、ほかは金の細い縁
    public static void button(GuiGraphics g, Font font, Box b, Component label, boolean primary, boolean hover, boolean enabled) {
        if (primary && enabled) {
            g.fillGradient(b.x(), b.y(), b.right(), b.bottom(), hover ? 0xFFFFE08C : 0xFFF0D07C, hover ? 0xFFD8B25C : 0xFFC6A050);
        } else {
            g.fill(b.x(), b.y(), b.right(), b.bottom(), hover && enabled ? 0x28E9C46A : 0x3004060F);
            outline(g, b.x(), b.y(), b.right(), b.bottom(), enabled ? LINE : RECESS_EDGE);
        }
        int color = !enabled ? DIM : primary ? 0x181420 : hover ? GOLD : TEXT;
        g.drawString(font, label, b.x() + (b.w() - font.width(label)) / 2, textY(b.y(), b.h()), color, false);
    }

    // 品の枠 (size 角。暗い地に細い縁)
    public static void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xAA060814);
        outline(g, x, y, x + size, y + size, 0xFF3E486E);
    }

    // HUD の枠 (藍の半透明の地・外の暗い線・金の細い線)
    public static void hudFrame(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GLASS_HUD);
        outline(g, x0, y0, x1, y1, OUTLINE);
        outline(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, LINE);
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

    // 菱形 (半径 r)。縁は暗い線、中は金
    public static void gem(GuiGraphics g, int cx, int cy, int r) {
        diamond(g, cx, cy, r + 1, OUTLINE);
        diamond(g, cx, cy, r, LINE);
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
