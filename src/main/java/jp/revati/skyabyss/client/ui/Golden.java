package jp.revati.skyabyss.client.ui;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// 画面の組み方の決まり (docs/design/56_ui_layout.md)。どの画面もここの寸法と描き方を使う:
//   板は横:縦 = 1.618:1 の大 (356x220)・中 (288x178)・小 (233x144)。縁から中身までは 8、見出しの帯は 21、下の段は 21 か 34。
//   中身は主の列 0.618 と脇の列 0.382 に分ける (あいだ 5)。余白は 3・5・8・13・21・34・55 だけ。
//   字は等倍だけ (縮めた字は使わない)。文と一覧の行は 13、釦の高さは 13。強弱は色で (金・水色・淡い灰)。
//   板を大きく描くときは UiFit が字の 1 画素を画面の整数の画素にそろえる。
// 縁・玉・線はコードで 1 画素ずつ描き (どの大きさの板も同じ太さ)、地は 64x64 の石のテクスチャを並べる (tools/gen/ui/gen_golden_assets.py)
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

    // 字の色
    public static final int TEXT = 0xE6E2D6;
    public static final int SOFT = 0xA9AEBC;
    // 手がかり・まだ使えないもの (前の 0x5E6472 は地との対比が足りず読めなかった)
    public static final int DIM = 0x7A8292;
    public static final int GOLD = 0xF2C14E;
    public static final int PALE = 0x9FE8FF;
    public static final int TEAL = 0x7FD6D0;
    public static final int DENY = 0xD9779A;
    public static final int MANA = 0x9FB8FF;

    static final int OUTLINE = 0xFF0C0E12;
    static final int BONE_LIGHT = 0xFFE2D9C2;
    static final int BONE = 0xFFC4BAA0;
    static final int BONE_MID = 0xFFA0967C;
    static final int BONE_DARK = 0xFF6E6654;
    static final int TEAL_DIM = 0xFF2A5E6A;
    static final int TEAL_DEEP = 0xFF1E3C46;
    static final int TEAL_LIGHT = 0xFF7FD6D0;
    static final int RECESS_EDGE = 0xFF343A48;
    static final int SELECT = 0xFF1F4B54;
    static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/golden/stone.png");
    static final ResourceLocation RECESS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/golden/recess.png");

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

    // 板: 外の線・骨の縁 (3 画素)・内の線・石の地・刻み・四隅の玉
    public static void panel(GuiGraphics g, Box b) {
        int x0 = b.x();
        int y0 = b.y();
        int x1 = b.right();
        int y1 = b.bottom();
        g.blit(STONE, x0 + 5, y0 + 5, 0, 0, b.w() - 10, b.h() - 10, 64, 64);
        outline(g, x0, y0, x1, y1, OUTLINE);
        for (int i = 1; i <= 3; i++) {
            int lit = i == 1 ? BONE_LIGHT : i == 2 ? BONE : BONE_MID;
            int shade = i == 1 ? BONE_DARK : BONE_MID;
            g.fill(x0 + i, y0 + i, x1 - i, y0 + i + 1, lit);
            g.fill(x0 + i, y0 + i, x0 + i + 1, y1 - i, lit);
            g.fill(x0 + i, y1 - i - 1, x1 - i, y1 - i, shade);
            g.fill(x1 - i - 1, y0 + i, x1 - i, y1 - i, shade);
        }
        outline(g, x0 + 4, y0 + 4, x1 - 4, y1 - 4, OUTLINE);
        for (int x = x0 + S21; x < x1 - S13; x += S21) {
            g.fill(x, y0 + 1, x + 1, y0 + 4, BONE_DARK);
            g.fill(x, y1 - 4, x + 1, y1 - 1, BONE_DARK);
        }
        for (int y = y0 + S21; y < y1 - S13; y += S21) {
            g.fill(x0 + 1, y, x0 + 4, y + 1, BONE_DARK);
            g.fill(x1 - 4, y, x1 - 1, y + 1, BONE_DARK);
        }
        gem(g, x0 + 3, y0 + 3, 3);
        gem(g, x1 - 4, y0 + 3, 3);
        gem(g, x0 + 3, y1 - 4, 3);
        gem(g, x1 - 4, y1 - 4, 3);
    }

    // 板と見出しの帯の区切りの線 (帯の下、あいだの真ん中) と真ん中の小さな玉
    public static void headLine(GuiGraphics g, Layout l) {
        int y = l.head().bottom() + 2;
        g.fill(l.inner().x(), y, l.inner().right(), y + 1, TEAL_DIM);
        g.fill(l.inner().x() + S21, y + 1, l.inner().right() - S21, y + 2, TEAL_DEEP);
        gem(g, l.inner().cx(), y, 2);
    }

    // 板・帯の線・題 (帯の右寄せ。null なら描かない) をまとめて
    public static void frame(GuiGraphics g, Font font, Layout l, Component title) {
        panel(g, l.panel());
        headLine(g, l);
        if (title != null) {
            Box h = l.head();
            FitText.draw(g, font, title, h.right() - S5 - Math.min(font.width(title), h.w() / 2), textY(h.y(), h.h()), h.w() / 2, PALE, false);
        }
    }

    // 欄 (一段くぼんだ地)
    public static void recess(GuiGraphics g, Box b) {
        g.blit(RECESS, b.x(), b.y(), 0, 0, b.w(), b.h(), 64, 64);
        g.fill(b.x(), b.y(), b.right(), b.y() + 1, OUTLINE);
        g.fill(b.x(), b.y(), b.x() + 1, b.bottom(), OUTLINE);
        g.fill(b.x() + 1, b.bottom() - 1, b.right(), b.bottom(), RECESS_EDGE);
        g.fill(b.right() - 1, b.y() + 1, b.right(), b.bottom(), RECESS_EDGE);
    }

    // 選んでいる行の地
    public static void selected(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), SELECT);
    }

    // 一行おきの地 (数の表を横に読みやすく)
    public static void stripe(GuiGraphics g, Box b) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), 0x14FFFFFF);
    }

    // 欄の中の区切りの線
    public static void rule(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, RECESS_EDGE);
    }

    // つまみ (見出しの帯の中。選んでいれば金の縁)
    public static void tab(GuiGraphics g, Font font, Box b, Component label, boolean on, boolean hover) {
        g.fill(b.x(), b.y(), b.right(), b.bottom(), on ? 0xFF2A2A22 : 0xFF12151C);
        outline(g, b.x(), b.y(), b.right(), b.bottom(), on ? 0xFF000000 | GOLD : hover ? 0xFF59606F : RECESS_EDGE);
        int color = on ? GOLD : hover ? TEXT : SOFT;
        int w = font.width(label);
        g.drawString(font, label, b.x() + (b.w() - w) / 2, textY(b.y(), b.h()), color, false);
    }

    // 菱形の玉 (半径 r)。縁は暗い線、左上を明るく
    public static void gem(GuiGraphics g, int cx, int cy, int r) {
        diamond(g, cx, cy, r + 1, OUTLINE);
        diamond(g, cx, cy, r, TEAL_DIM);
        for (int dy = -r + 1; dy <= 0; dy++) {
            int half = r - Math.abs(dy) - 1;
            if (half >= 0) {
                g.fill(cx - half, cy + dy, cx + 1, cy + dy + 1, TEAL_LIGHT);
            }
        }
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
