package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 本の形の画面 (伝承の書・控え帳) の部品。鐘の銅の板 (docs/design/56 の 2.1) に生成りの頁を載せる。
// 傾けない・丸めない・字と同じ画素の刻み。表紙は Plate.panel、しおりは Plate.tile。頁の上の字は墨の色
public final class BookPlate {
    // 頁の生成り (地・上の光・下の厚み・のどの折り目)
    public static final int CREAM = 0xFFECE4D6;
    public static final int CREAM_HI = 0xFFFFF6EA;
    public static final int CREAM_LO = 0xFFC9BFAE;
    public static final int CREAM_FOLD = 0xFFDDD3C2;
    // 頁の上の字 (墨・薄い墨・暗い銅・見出しの銅)
    public static final int INK = 0xFF181722;
    public static final int FADED = 0xFF6E6468;
    public static final int RUST = 0xFF83502A;
    public static final int TITLE = 0xFF7A4A1C;

    // 印の種類
    public static final int MARK_KNOWN = 0, MARK_NEW = 1, MARK_LOCKED = 2, MARK_LOST = 3, MARK_CHANGED = 4;

    private BookPlate() {
    }

    // 表紙 (石の板に銅の縁)
    public static void cover(GuiGraphics g, int x, int y, int w, int h) {
        Plate.panel(g, x, y, w, h, 0, Plate.GOLD);
    }

    // 生成りの頁を一枚。fold = のどの側 (-1 = 左の端、1 = 右の端、0 = なし) に一筋の折り目
    public static void page(GuiGraphics g, int x, int y, int w, int h, int fold) {
        int o = Plate.pal().outline;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, o);
        g.fill(x, y, x + w, y + h, CREAM);
        g.fill(x, y, x + w, y + 1, CREAM_HI);
        // 下の端に 2 画素の紙の厚み
        g.fill(x, y + h - 2, x + w, y + h - 1, CREAM_FOLD);
        g.fill(x, y + h - 1, x + w, y + h, CREAM_LO);
        if (fold < 0) {
            g.fill(x, y + 1, x + 1, y + h - 2, CREAM_LO);
            g.fill(x + 1, y + 1, x + 2, y + h - 2, CREAM_FOLD);
        } else if (fold > 0) {
            g.fill(x + w - 1, y + 1, x + w, y + h - 2, CREAM_LO);
            g.fill(x + w - 2, y + 1, x + w - 1, y + h - 2, CREAM_FOLD);
        }
    }

    // 見開きの のど (二枚の頁のあいだの石に、真ん中の暗い線)
    public static void spine(GuiGraphics g, int x, int y, int w, int h) {
        Plate.Pal p = Plate.pal();
        g.fill(x, y, x + w, y + h, p.slabLo);
        g.fill(x + w / 2 - 1, y, x + w / 2 + 1, y + h, p.outline);
    }

    // 見出しの下の線: 暗い銅の線と、左寄り (0.31) の菱
    public static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, RUST);
        Plate.pip(g, x + Math.round(w * 0.31f) - 2, y - 2, Plate.pal().f, 1f);
    }

    // 一枚の頁を縦に分ける線 (上の端に銅の菱、下の端に薄い菱)
    public static void divider(GuiGraphics g, int x, int y0, int y1) {
        g.fill(x, y0, x + 1, y1, RUST);
        Plate.pip(g, x - 2, y0 - 2, Plate.pal().f, 1f);
        Plate.pip(g, x - 2, y1 - 3, 0xA9A29A, 1f);
    }

    // しおり (つまみ)。選んだものは灯った升、ほかは升。locked = まだ何もない (字を暗く)
    public static void tab(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean on, boolean hover, boolean locked) {
        int state = on ? Plate.ON : hover ? Plate.HOVER : Plate.REST;
        Plate.tile(g, x, y - (on ? 1 : 0), w, h + (on ? 1 : 0), state, Plate.GOLD);
        int rgb = locked && !on ? Plate.pal().dim : Plate.tileInk(state, Plate.GOLD);
        int tw = Math.min(font.width(label), w - 4);
        FitText.draw(g, font, label, x + (w - tw) / 2, y + (h - 9) / 2 + 1, w - 4, 0xFF000000 | rgb, false);
    }

    // 選んだ行 (淡い銅の帯と、左の 2 画素の銅の筋)。alpha = 乗せただけのときは薄く
    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
        g.fill(x, y, x + w, y + h, selected ? 0x58C48A44 : 0x28C48A44);
        g.fill(x, y, x + 2, y + h, selected ? 0xFFC48A44 : 0x80C48A44);
    }

    // 菱の印 (8 角の枠の中、芯は x + 3, y + 4)
    public static void mark(GuiGraphics g, int x, int y, int kind) {
        Plate.Pal p = Plate.pal();
        int px = x + 1;
        int py = y + 2;
        switch (kind) {
            case MARK_LOCKED -> {
                int c = 0xFF8A8494;
                g.fill(px + 2, py, px + 3, py + 1, c);
                g.fill(px + 1, py + 1, px + 2, py + 2, c);
                g.fill(px + 3, py + 1, px + 4, py + 2, c);
                g.fill(px, py + 2, px + 1, py + 3, c);
                g.fill(px + 4, py + 2, px + 5, py + 3, c);
                g.fill(px + 1, py + 3, px + 2, py + 4, c);
                g.fill(px + 3, py + 3, px + 4, py + 4, c);
                g.fill(px + 2, py + 4, px + 3, py + 5, c);
            }
            case MARK_NEW -> {
                Plate.pip(g, px, py, p.fHi, 1f);
                g.fill(px + 2, py + 1, px + 3, py + 2, 0xFFFFF6EA);
            }
            case MARK_LOST -> {
                Plate.pip(g, px, py, p.slab, 1f);
                g.fill(px + 2, py + 2, px + 3, py + 3, CREAM);
            }
            case MARK_CHANGED -> {
                Plate.pip(g, px, py, p.f, 1f);
                g.fill(px + 2, py + 2, px + 3, py + 3, 0xFFFFF6EA);
            }
            default -> Plate.pip(g, px, py, p.fLo, 1f);
        }
    }

    // 送りの矢印 (8 角の枠の中、幅 5 の三角)
    public static void arrow(GuiGraphics g, int x, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? y + 2 + i : y + 4 - i;
            g.fill(x + 2 - i + 2, row, x + 3 + i + 2, row + 1, color);
        }
    }

    // 伏せ字の墨の帯 (石の色のまっすぐな帯)
    public static void inkBar(GuiGraphics g, int x, int y, int w) {
        Plate.Pal p = Plate.pal();
        g.fill(x, y, x + w, y + 5, p.slab);
        g.fill(x, y, x + w, y + 1, p.slabHi);
    }

    // 頁の上の帯 (暗い銅の地に、上の端の銅の線。字は生成り)
    public static void band(GuiGraphics g, int x, int y, int w, int h) {
        Plate.Pal p = Plate.pal();
        g.fill(x, y, x + w, y + h, p.fLo);
        g.fill(x, y, x + w, y + 1, p.f);
        g.fill(x, y + h - 1, x + w, y + h, p.fDeep);
    }

    // 頁の上の細い帯 (高さ 5。石の溝に銅の塗り、塗りの上の一行が光)
    public static void bar(GuiGraphics g, int x, int y, int w, float frac) {
        Plate.Pal p = Plate.pal();
        g.fill(x, y, x + w, y + 5, p.slab);
        g.fill(x + 1, y + 1, x + w - 1, y + 4, p.well);
        int fill = Math.round((w - 2) * frac);
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + 4, p.f);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, p.fHi);
        }
    }

    // しおり紐 (頁の下から垂れる銅の紐。下の端は燕尾)
    public static void ribbon(GuiGraphics g, int x, int y0, int y1) {
        Plate.Pal p = Plate.pal();
        g.fill(x, y0, x + 5, y1 - 2, p.f);
        g.fill(x, y0, x + 1, y1 - 2, p.fHi);
        g.fill(x + 4, y0, x + 5, y1 - 2, p.fLo);
        g.fill(x, y1 - 2, x + 2, y1, p.f);
        g.fill(x + 3, y1 - 2, x + 5, y1, p.fLo);
        g.fill(x, y1 - 2, x + 1, y1, p.fHi);
    }
}
