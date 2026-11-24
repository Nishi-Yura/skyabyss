package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 画素の板の絵柄 (UiLook の BRONZE・STONE・DEEP)。枠・欄・升・釦を、字と同じ画素の格子で一画素ずつ塗る。
// 絵は Paper.draw が名前ごとに一度塗って使い回す (名前に絵柄・大きさ・状態・色を入れる)。色は絵柄ごとの Pal、差し色は Accent (職・土地の色)。
// 透かし: fade(g, a) 〜 fadeEnd(g) のあいだに描いた絵・字・塗りは、まとめて a 倍の濃さになる (品と姿は透けないので、半分を超えたら描く)
public final class Plate {
    private Plate() {
    }

    public static final int REST = 0, HOVER = 1, PRESS = 2, FLAT = 3, ON = 4;

    // ---------- 色 ----------
    public static final class Pal {
        // 絵の色 (ARGB)
        public final int outline, slab, slabHi, slabLo, well, wellTL, wellBR, fHi, f, fLo, fDeep, pat, patLo;
        // 暗い地 (板・欄) の上の字 (RGB): 地の文・添え・まだ・見出し・値
        public final int text, soft, dim, head, value;
        // 板の上にじかに書く字 (石板は墨、ほかは暗い地と同じ)
        public final int ink, inkSoft, inkHead;
        // 文の塊の下に暗い欄が要るか (石板)
        public final boolean lightPanel;

        Pal(int outline, int slab, int slabHi, int slabLo, int well, int wellTL, int wellBR, int fHi, int f, int fLo, int fDeep, int pat, int patLo,
                int text, int soft, int dim, int head, int value, int ink, int inkSoft, int inkHead, boolean lightPanel) {
            this.outline = outline;
            this.slab = slab;
            this.slabHi = slabHi;
            this.slabLo = slabLo;
            this.well = well;
            this.wellTL = wellTL;
            this.wellBR = wellBR;
            this.fHi = fHi;
            this.f = f;
            this.fLo = fLo;
            this.fDeep = fDeep;
            this.pat = pat;
            this.patLo = patLo;
            this.text = text;
            this.soft = soft;
            this.dim = dim;
            this.head = head;
            this.value = value;
            this.ink = ink;
            this.inkSoft = inkSoft;
            this.inkHead = inkHead;
            this.lightPanel = lightPanel;
        }
    }

    // 鐘の銅: 深い石の板・銅の縁 (明 → 暗)・緑青・骨色の字・鐘の金の見出し・碑文の青緑の値
    private static final Pal BRONZE = new Pal(0xFF07080C, 0xFF181C28, 0xFF252B3C, 0xFF0F121A, 0xFF10131C, 0xFF08090E, 0xFF2A3044,
            0xFFF2C47C, 0xFFC48A44, 0xFF83502A, 0xFF4C2C1A, 0xFF5FB8A8, 0xFF2F8076,
            0xECE6D6, 0xA9ABB8, 0x6C7084, 0xF2C47C, 0x8FE0D0, 0xECE6D6, 0xA9ABB8, 0xF2C47C, false);
    // 空見の石板: 明るい石・暗い欄・墨の字・青緑の象嵌
    private static final Pal STONE = new Pal(0xFF1A1814, 0xFFCBC4B0, 0xFFF3EEDD, 0xFF8E8875, 0xFF191C26, 0xFF55524A, 0xFFFFFBEB,
            0xFFF3EEDD, 0xFFCBC4B0, 0xFF8E8875, 0xFF5E5A4E, 0xFF3F9E90, 0xFF2A6E66,
            0xECE6D6, 0xA9ABB8, 0x6C7084, 0xF2C47C, 0x8FE0D0, 0x2A2620, 0x5E5A4E, 0x2A2620, true);
    // 淵の石: 縁の細い暗い板。見出しと選んだものは差し色
    private static final Pal DEEP = new Pal(0xFF000000, 0xFF11131B, 0xFF1A1D28, 0xFF0B0C12, 0xFF0B0D13, 0xFF050608, 0xFF262A38,
            0xFF7C8498, 0xFF4A5064, 0xFF343848, 0xFF1E222E, 0xFF5FB8A8, 0xFF2F8076,
            0xECE6D6, 0xA9ABB8, 0x6C7084, 0xECE6D6, 0x8FE0D0, 0xECE6D6, 0xA9ABB8, 0xECE6D6, false);

    public static Pal pal() {
        return switch (UiLook.get()) {
            case STONE -> STONE;
            case DEEP -> DEEP;
            default -> BRONZE;
        };
    }

    // 二つの色を混ぜる (t = 0 で a、1 で b)。不透明の ARGB を返す
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gr = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    // 差し色 (明・中・暗・板に沈めた色)
    public record Accent(int hi, int mid, int lo, int deep) {
        public static Accent of(int rgb) {
            int c = 0xFF000000 | rgb;
            return new Accent(mix(c, 0xFFFFFFFF, 0.42f), c, mix(c, 0xFF000000, 0.48f), mix(c, 0xFF11131B, 0.80f));
        }
    }

    // 職を選ぶ前・職によらない所の差し色 (月の銀)
    public static final Accent NEUTRAL = Accent.of(0x9AA8D0);
    // 職の差し色 (Job の並び): 戦士 = 鐘の金、鍛冶師 = 熾火、魔法使い = 月、弓使い = 苔、暗殺者 = 淵の紅、呪術師 = 碑文の青緑
    private static final Accent[] JOBS = {Accent.of(0xE2A64E), Accent.of(0xE8703A), Accent.of(0x8E9CF2), Accent.of(0x8CC460), Accent.of(0xDA4E6C),
            Accent.of(0x4FC8B6)};

    // 職によらない画面の差し色 (鐘の金)
    public static final Accent GOLD = Accent.of(0xE2A64E);

    public static Accent job(int ordinal) {
        return ordinal < 0 || ordinal >= JOBS.length ? NEUTRAL : JOBS[ordinal];
    }

    // ---------- 透かし ----------
    public static void fade(GuiGraphics g, float a) {
        // 絵 (blit) は混ぜる設定が切れていると透けないので、ここで入れる
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, a)));
    }

    public static void fadeEnd(GuiGraphics g) {
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ---------- 塗りの道具 ----------
    private static void clear(Paper.Sheet s, int x, int y) {
        if (x >= 0 && y >= 0 && x < s.w && y < s.h) {
            s.img.setPixelRGBA(x, y, 0);
        }
    }

    // 角を n 画素落とした四角を塗る
    private static void fillNotched(Paper.Sheet s, int x, int y, int w, int h, int n, int c) {
        s.rect(x, y, w, h, c);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n - j; i++) {
                clear(s, x + i, y + j);
                clear(s, x + w - 1 - i, y + j);
                clear(s, x + i, y + h - 1 - j);
                clear(s, x + w - 1 - i, y + h - 1 - j);
            }
        }
    }

    // 角を n 画素落とした一画素の線
    private static void notchOutline(Paper.Sheet s, int x, int y, int w, int h, int n, int c) {
        s.rect(x + n, y, w - 2 * n, 1, c);
        s.rect(x + n, y + h - 1, w - 2 * n, 1, c);
        s.rect(x, y + n, 1, h - 2 * n, c);
        s.rect(x + w - 1, y + n, 1, h - 2 * n, c);
        for (int i = 1; i < n; i++) {
            s.px(x + n - i, y + i, c);
            s.px(x + w - 1 - n + i, y + i, c);
            s.px(x + n - i, y + h - 1 - i, c);
            s.px(x + w - 1 - n + i, y + h - 1 - i, c);
        }
    }

    // 一画素の輪。上と左は tl、下と右は br
    private static void ring(Paper.Sheet s, int x, int y, int w, int h, int tl, int br) {
        s.rect(x, y, w, 1, tl);
        s.rect(x, y + 1, 1, h - 2, tl);
        s.rect(x, y + h - 1, w, 1, br);
        s.rect(x + w - 1, y + 1, 1, h - 1, br);
    }

    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    // 色の段 ramp を t (0..1) で引く。段のあいだは 4x4 の網で混ぜる
    private static int dither(int[] ramp, float t, int x, int y) {
        float f = Math.max(0f, Math.min(1f, t)) * (ramp.length - 1);
        int i = (int) Math.floor(f);
        if (i >= ramp.length - 1) {
            return ramp[ramp.length - 1];
        }
        return f - i > (BAYER[y & 3][x & 3] + 0.5f) / 16f ? ramp[i + 1] : ramp[i];
    }

    // 切り絵の画面が書く字の色 (黒い紙の上の色・生成りの上の墨の色) を、板の絵柄の色へ写す (mixin/GuiGraphicsMixin が Paper.inkColor を通す)。
    // 板の絵柄では、字はいつも暗い地 (板か欄) の上に書く。文の中で決めた色 (品の稀さなど) は写らない
    public static int textColor(int argb) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof Parchment) {
            return switch (argb & 0xFFFFFF) {
                case 0xB0326E, 0xE45A9C, 0xF89CD2 -> (argb & 0xFF000000) | 0x7A4A1C;
                case 0x8C2C60 -> (argb & 0xFF000000) | 0x83502A;
                default -> argb;
            };
        }
        Pal p = pal();
        int a = argb & 0xFF000000;
        if (a == 0) {
            a = 0xFF000000;
        }
        int out;
        switch (argb & 0xFFFFFF) {
            case 0xF89CD2, 0xE45A9C, 0xB0326E -> out = UiLook.get() == UiLook.DEEP ? 0xF2C47C : p.head;
            case 0x181722 -> out = p.text;
            case 0x9A929C, 0x6E6468, 0xC4BCC4 -> out = p.soft;
            case 0x7C7480, 0x8A8086 -> out = p.dim;
            case 0xB0102C, 0x5A0820 -> out = 0xF0607A;
            case 0x2E7A3A -> out = 0x7BD66B;
            default -> {
                return argb;
            }
        }
        return a | out;
    }

    // 切り絵の画面が塗る色 (紙の絵・g.fill) を、板の絵柄の色へ写す (濃さは保つ)。Paper.Sheet と mixin/GuiGraphicsMixin の fill が通す。
    // 黒い紙 = 板の石、桃色 = 銅、濃い桃色 = 暗い銅、生成りの紙の上の線 = 欄の縁。生成りそのものは写さない (欄にするかは部品の側で決める)
    public static int paint(int argb) {
        int out;
        switch (argb & 0xFFFFFF) {
            case 0x181722 -> out = 0x181C28;
            case 0x0E0D14 -> out = 0x0F121A;
            case 0xE45A9C -> out = 0xC48A44;
            case 0xF89CD2 -> out = 0xF2C47C;
            case 0x8C2C60 -> out = 0x83502A;
            case 0xCC4A8A -> out = 0xA8743A;
            case 0x3A2236 -> out = 0x10131C;
            case 0xC9BFAE, 0x3A3644 -> out = 0x2A3044;
            case 0xDCD2C2, 0xD8D0C4 -> out = 0x0C0E15;
            case 0x3A1A2E -> out = 0x33291C;
            case 0x24202E -> out = 0x283046;
            case 0x2C2834 -> out = 0x2A3044;
            case 0x5A5460 -> out = 0x3A4054;
            default -> {
                return argb;
            }
        }
        return (argb & 0xFF000000) | out;
    }

    // 生成りの紙をじかに塗っている所 (札・枠) の色を、板の色へ写す: 生成り = 浮いた升の地、明るい生成り = 乗せた升、暗い生成り = 縁、桃色 = 灯った升
    public static int card(int argb) {
        if (!UiLook.plate()) {
            return argb;
        }
        return switch (argb & 0xFFFFFF) {
            case 0xECE4D6 -> 0xFF202534;
            case 0xFFF6EA -> 0xFF283046;
            case 0xC9BFAE -> 0xFF2A3044;
            case 0xE45A9C -> 0xFF4A3A2C;
            case 0xF89CD2 -> 0xFF5A4630;
            default -> argb;
        };
    }

    // 紙の絵 (Paper.draw) の中の色。生成りの紙は浮いた升の地へ、その影は暗い縁へ。ほかは paint と同じ
    public static int paintSheet(int argb) {
        int out;
        switch (argb & 0xFFFFFF) {
            case 0xECE4D6 -> out = 0x232A3C;
            case 0xFFF6EA -> out = 0x2E3750;
            case 0xC9BFAE -> out = 0x1B2030;
            case 0xDCD2C2 -> out = 0x10131C;
            case 0xB8AEA4, 0xA89E8E -> out = 0x0A0C12;
            default -> {
                return paint(argb);
            }
        }
        return (argb & 0xFF000000) | out;
    }

    // 羊皮紙の頁に字を書く画面 (伝承の書・記録の帳)。字は墨のまま、桃色の見出しだけ暗い銅へ写す
    public interface Parchment {
    }

    // 画面を開いたときの入り: 舞台ごと 160ms で濃くなる (UiFit.end が戻す)。舞台の地を描く前に呼ぶ
    private static Object fadeOwner;
    private static long fadeAt;

    public static void openFade(GuiGraphics g) {
        Object s = net.minecraft.client.Minecraft.getInstance().screen;
        if (s != fadeOwner) {
            fadeOwner = s;
            fadeAt = net.minecraft.Util.getMillis();
        }
        float t = Math.max(0f, Math.min(1f, (net.minecraft.Util.getMillis() - fadeAt) / 160f));
        fade(g, 1f - (1f - t) * (1f - t) * (1f - t));
    }

    // 切り絵の紙一枚 (Paper.cut) の代わり: 紙の色で部品を決める。生成り = 沈んだ欄、桃色 = 灯った升、黒 = 浮いた升、ほか = 平らな升
    public static void piece(GuiGraphics g, int x, int y, int w, int h, int body) {
        if (w < 4 || h < 4) {
            g.fill(x, y, x + w, y + h, paint(body));
            return;
        }
        switch (body & 0xFFFFFF) {
            case 0xECE4D6, 0xFFF6EA, 0xC9BFAE -> well(g, x, y, w, h);
            case 0xE45A9C, 0xF89CD2, 0xCC4A8A -> tile(g, x, y, w, h, ON, GOLD);
            case 0x181722, 0x0E0D14 -> tile(g, x, y, w, h, REST, GOLD);
            default -> tile(g, x, y, w, h, FLAT, GOLD);
        }
    }

    // ---------- HUD の部品 (docs/design/56 の 5 章) ----------
    // HUD の札・帯・印は、どれもここの部品で描く。大きさが字で変わるので絵 (Paper.draw) にせず g.fill で塗る。
    // 色は Pal からじかに取る (切り絵の色の写し Plate.paint には頼らない)。a = 全体の濃さ (0..1。出入りの透かし)

    // 札の地の濃さ (少し透ける。後ろの景色が分かる)
    public static final int HUD_ALPHA = 0xDC;

    // 色に濃さを掛ける (base = a が 1 のときの濃さ 0..255)
    public static int alpha(int rgb, int base, float a) {
        int v = Math.round(base * Math.max(0f, Math.min(1f, a)));
        return (v << 24) | (rgb & 0xFFFFFF);
    }

    // HUD の札の地: 少し透ける石の板。上の端に銅の線 (左の 3 画素は明るい)、その下に一段明るい石、下の端に暗い縁、右と下に 1 画素の影。角は 1 画素落とす
    public static void hud(GuiGraphics g, int x0, int y0, int x1, int y1) {
        hudCard(g, x0, y0, x1, y1, 0, 1f);
    }

    // accent = 左の端の 2 画素の筋の色 (意味の色。0 = 筋なし)
    public static void hudCard(GuiGraphics g, int x0, int y0, int x1, int y1, int accent, float a) {
        if (a <= 0.02f || x1 - x0 < 4 || y1 - y0 < 4) {
            return;
        }
        Pal p = pal();
        g.fill(x0 + 2, y1, x1 + 1, y1 + 1, alpha(0, 0x70, a));
        g.fill(x1, y0 + 2, x1 + 1, y1, alpha(0, 0x70, a));
        g.fill(x0 + 1, y0, x0 + 4, y0 + 1, alpha(p.fHi, 0xFF, a));
        g.fill(x0 + 4, y0, x1 - 1, y0 + 1, alpha(p.f, 0xFF, a));
        g.fill(x0, y0 + 1, x1, y0 + 2, alpha(p.slabHi, HUD_ALPHA, a));
        g.fill(x0, y0 + 2, x1, y1 - 1, alpha(p.slab, HUD_ALPHA, a));
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, alpha(p.slabLo, 0xF0, a));
        if ((accent & 0xFFFFFF) != 0 || (accent >>> 24) != 0) {
            int ac = paint(0xFF000000 | accent);
            g.fill(x0, y0 + 1, x0 + 2, y1 - 1, alpha(ac, 0xFF, a));
            g.fill(x0, y1 - 2, x0 + 2, y1 - 1, alpha(mix(ac, 0xFF000000, 0.40f), 0xFF, a));
        }
    }

    // 札の中の区切り (暗い溝と、その下の明るい線)
    public static void hudRule(GuiGraphics g, int x, int y, int w, float a) {
        Pal p = pal();
        g.fill(x, y, x + w, y + 1, alpha(p.outline, 0xC0, a));
        g.fill(x, y + 1, x + w, y + 2, alpha(p.slabHi, 0xC0, a));
    }

    // HUD の帯: 溝の中に塗り。塗りは上の一行が光、下の一行が暗い縁、先の一画素が明るい。ghost = 減った分の遅れて縮む影 (frac より大きいときだけ出る)。
    // 高さ 7 以上は銅の縁つき (rim = 縁の色。0 = 銅。打たれた・足りないときに光らせる)、それより細いものは縁なしの溝 (上と左が暗く、下が明るい)
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, float ghost, int color, int rim, float a) {
        Pal p = pal();
        boolean framed = h >= 7;
        int ix = x + 1;
        int iy = y + 1;
        int iw = w - 2;
        int ih = h - 2;
        if (framed) {
            int hi = rim != 0 ? rim : p.f;
            int lo = rim != 0 ? mix(0xFF000000 | rim, 0xFF000000, 0.35f) : p.fLo;
            g.fill(x + 1, y, x + w - 1, y + 1, alpha(hi, 0xFF, a));
            g.fill(x, y + 1, x + 1, y + h - 1, alpha(hi, 0xFF, a));
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, alpha(lo, 0xFF, a));
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, alpha(lo, 0xFF, a));
            g.fill(x + 2, y + h, x + w, y + h + 1, alpha(0, 0x70, a));
            g.fill(ix, iy, ix + iw, iy + 1, alpha(p.wellTL, 0xFF, a));
            g.fill(ix, iy + 1, ix + iw, iy + ih, alpha(p.well, 0xFF, a));
        } else {
            g.fill(x, y, x + w, y + 1, alpha(p.outline, 0xFF, a));
            g.fill(x, y + 1, x + 1, y + h - 1, alpha(p.outline, 0xFF, a));
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, alpha(p.outline, 0xFF, a));
            g.fill(x, y + h - 1, x + w, y + h, alpha(p.wellBR, 0xFF, a));
            g.fill(ix, iy, ix + iw, iy + ih, alpha(p.well, 0xFF, a));
        }
        int fill = Math.round(iw * Math.max(0f, Math.min(1f, frac)));
        int gh = Math.round(iw * Math.max(0f, Math.min(1f, ghost)));
        int c = 0xFF000000 | color;
        if (gh > fill) {
            g.fill(ix + fill, iy, ix + gh, iy + ih, alpha(mix(c, 0xFFFFFFFF, 0.70f), 0xFF, a));
        }
        if (fill > 0) {
            g.fill(ix, iy, ix + fill, iy + ih, alpha(c, 0xFF, a));
            if (ih >= 2) {
                g.fill(ix, iy, ix + fill, iy + 1, alpha(mix(c, 0xFFFFFFFF, 0.40f), 0xFF, a));
            }
            if (ih >= 3) {
                g.fill(ix, iy + ih - 1, ix + fill, iy + ih, alpha(mix(c, 0xFF000000, 0.38f), 0xFF, a));
            }
            if (fill >= 3 && fill < iw && ih >= 3) {
                g.fill(ix + fill - 1, iy + 1, ix + fill, iy + ih - 1, alpha(mix(c, 0xFFFFFFFF, 0.55f), 0xFF, a));
            }
        }
    }

    // 帯の目盛り (n 等分の細い暗い線。帯の内側に引く)
    public static void barTicks(GuiGraphics g, int x, int y, int w, int h, int n, float a) {
        for (int i = 1; i < n; i++) {
            int tx = x + 1 + (w - 2) * i / n;
            g.fill(tx, y + 1, tx + 1, y + h - 1, alpha(0, 0x60, a));
        }
    }

    // 小さな菱の印 (5x5。暗い縁の中に色)。x・y は左上
    public static void pip(GuiGraphics g, int x, int y, int color, float a) {
        int o = alpha(pal().outline, 0xFF, a);
        int c = alpha(paint(0xFF000000 | color), 0xFF, a);
        g.fill(x + 2, y, x + 3, y + 5, o);
        g.fill(x, y + 2, x + 5, y + 3, o);
        g.fill(x + 1, y + 1, x + 4, y + 4, o);
        g.fill(x + 2, y + 1, x + 3, y + 4, c);
        g.fill(x + 1, y + 2, x + 4, y + 3, c);
    }

    // 品や技の升 (size 角。沈んだ欄。edge = 縁の色。0 = 欄の縁)。濃さが変わる所 (出入りする札) で使う。止まった升は Plate.well
    public static void hudSlot(GuiGraphics g, int x, int y, int size, int edge, float a) {
        Pal p = pal();
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, alpha(p.well, 0xF0, a));
        boolean own = edge != 0;
        g.fill(x, y, x + size, y + 1, alpha(own ? edge : p.wellTL, 0xFF, a));
        g.fill(x, y + 1, x + 1, y + size - 1, alpha(own ? edge : p.wellTL, 0xFF, a));
        g.fill(x + size - 1, y + 1, x + size, y + size - 1, alpha(own ? mix(0xFF000000 | edge, 0xFF000000, 0.30f) : p.wellBR, 0xFF, a));
        g.fill(x, y + size - 1, x + size, y + size, alpha(own ? mix(0xFF000000 | edge, 0xFF000000, 0.30f) : p.wellBR, 0xFF, a));
    }

    // HUD の字の色 (RGB に濃さを付ける): 見出し = 鐘の金、地の文 = 骨色、添え = 灰、値 = 碑文の青緑
    public static int hudHead(float a) {
        return alpha(pal().head, 0xFF, Math.max(0.04f, a));
    }

    public static int hudText(float a) {
        return alpha(pal().text, 0xFF, Math.max(0.04f, a));
    }

    public static int hudSoft(float a) {
        return alpha(pal().soft, 0xFF, Math.max(0.04f, a));
    }

    public static int hudValue(float a) {
        return alpha(pal().value, 0xFF, Math.max(0.04f, a));
    }

    // 後ろの景色をぼかす (バニラのメニューと同じぼかし。renderBackground を自分で描く画面から呼ぶ)
    public static void blur() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            mc.gameRenderer.processBlurEffect(mc.getTimer().getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
        }
    }

    // ---------- 地 ----------
    // 窓の全体を暗くする (後ろの景色はバニラのぼかしのまま)。上と下は少し濃く
    public static void backdrop(GuiGraphics g, int width, int height, float a) {
        int al = Math.round(0x58 * Math.max(0f, Math.min(1f, a)));
        g.fill(0, 0, width, height, (al << 24) | 0x05060A);
        int e = Math.round(0x70 * Math.max(0f, Math.min(1f, a)));
        g.fillGradient(0, 0, width, height / 3, (e << 24) | 0x05060A, 0x0005060A);
        g.fillGradient(0, height * 2 / 3, width, height, 0x0005060A, (e << 24) | 0x05060A);
    }

    // ---------- 板 ----------
    // head = 板の上の端から、見出しの下の線まで (0 なら見出しなし)
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int head, Accent ac) {
        UiLook look = UiLook.get();
        Pal p = pal();
        // 硬い影 (右と下に 2 画素)
        g.fill(x + 3, y + h, x + w + 2, y + h + 2, 0x70000000);
        g.fill(x + w, y + 3, x + w + 2, y + h, 0x70000000);
        String name = "plate_panel_" + look + "_" + w + "x" + h + "_" + head + (look == UiLook.DEEP ? "_" + Integer.toHexString(ac.mid()) : "");
        Paper.draw(g, name, "", x, y, w, h, s -> {
            switch (look) {
                case STONE -> paintStone(s, w, h, head, p);
                case DEEP -> paintDeep(s, w, h, head, p, ac);
                default -> paintBronze(s, w, h, head, p);
            }
        });
    }

    private static void paintBronze(Paper.Sheet s, int w, int h, int head, Pal p) {
        fillNotched(s, 0, 0, w, h, 2, p.slab);
        // 銅の縁 (3 画素の棒。光は左上から)
        ring(s, 1, 1, w - 2, h - 2, p.fHi, p.fDeep);
        ring(s, 2, 2, w - 4, h - 4, p.f, p.fLo);
        ring(s, 3, 3, w - 6, h - 6, p.fLo, p.f);
        ring(s, 4, 4, w - 8, h - 8, p.outline, p.outline);
        s.rect(5, 5, w - 10, 1, p.slabHi);
        s.rect(5, h - 6, w - 10, 1, p.slabLo);
        if (head > 0) {
            int hc = mix(p.slab, 0xFF000000, 0.38f);
            s.rect(5, 5, w - 10, head - 7, hc);
            s.rect(5, 5, w - 10, 1, mix(hc, 0xFFFFFFFF, 0.10f));
            s.rect(5, head - 2, w - 10, 1, p.f);
            s.rect(5, head - 1, w - 10, 1, p.fLo);
            s.rect(5, head, w - 10, 1, p.outline);
            s.rect(5, head + 1, w - 10, 1, p.slabHi);
        }
        // 緑青: 下の二つの角から、棒に沿ってにじむ (角ほど濃く、離れるほど途切れる)。上の角からは短く垂れる
        int[][] along = {{0, 13}, {15, 4}, {21, 2}, {26, 1}, {31, 1}};
        int[][] alongLo = {{0, 6}, {8, 2}, {12, 1}};
        int[][] alongIn = {{0, 4}, {6, 1}};
        for (int side = 0; side < 2; side++) {
            for (int[] r : along) {
                for (int i = 0; i < r[1]; i++) {
                    int d = 8 + r[0] + i;
                    s.px(side == 0 ? d : w - 1 - d, h - 3, p.pat);
                    if (r[0] + i < 11) {
                        s.px(side == 0 ? 2 : w - 3, h - 1 - d, p.pat);
                    }
                }
            }
            for (int[] r : alongLo) {
                for (int i = 0; i < r[1]; i++) {
                    int d = 8 + r[0] + i;
                    s.px(side == 0 ? d : w - 1 - d, h - 2, p.patLo);
                    s.px(side == 0 ? 1 : w - 2, h - 1 - d, p.patLo);
                }
            }
            for (int[] r : alongIn) {
                for (int i = 0; i < r[1]; i++) {
                    int d = 8 + r[0] + i;
                    s.px(side == 0 ? d : w - 1 - d, h - 4, p.pat);
                    s.px(side == 0 ? 3 : w - 4, h - 1 - d, p.patLo);
                }
            }
            for (int d = 8; d < 13; d++) {
                s.px(side == 0 ? 2 : w - 3, d, d == 12 ? p.patLo : p.pat);
            }
            s.px(side == 0 ? 3 : w - 4, 8, p.patLo);
        }
        // 角の鋲の板 (6x6)
        int[][] corners = {{1, 1}, {w - 7, 1}, {1, h - 7}, {w - 7, h - 7}};
        for (int i = 0; i < 4; i++) {
            int cx = corners[i][0];
            int cy = corners[i][1];
            boolean low = i >= 2;
            ring(s, cx - 1, cy - 1, 8, 8, p.outline, p.outline);
            s.rect(cx, cy, 6, 6, p.f);
            ring(s, cx, cy, 6, 6, p.fHi, low ? p.fDeep : p.fLo);
            s.px(cx + 2, cy + 2, mix(p.fHi, 0xFFFFFFFF, 0.6f));
            s.px(cx + 3, cy + 2, p.fHi);
            s.px(cx + 2, cy + 3, p.fLo);
            s.px(cx + 3, cy + 3, p.fDeep);
            if (low) {
                s.px(cx + 1, cy + 4, p.pat);
                s.px(cx + 4, cy + 4, p.pat);
                s.px(cx + (i == 2 ? 1 : 4), cy + 3, p.patLo);
                s.px(cx + 4, cy + 1, i == 3 ? p.pat : p.f);
            }
        }
        notchOutline(s, 0, 0, w, h, 2, p.outline);
    }

    private static void paintStone(Paper.Sheet s, int w, int h, int head, Pal p) {
        fillNotched(s, 0, 0, w, h, 2, p.slab);
        // 石の粒 (決まった並び)
        int lo = mix(p.slab, p.slabLo, 0.45f);
        int hi = mix(p.slab, p.slabHi, 0.55f);
        for (int y = 3; y < h - 3; y++) {
            for (int x = 3; x < w - 3; x++) {
                int k = (x * 31 + y * 17 + ((x * y) % 7) * 13) % 101;
                if (k == 0) {
                    s.px(x, y, lo);
                    s.px(x + 1, y, lo);
                } else if (k == 50) {
                    s.px(x, y, hi);
                }
            }
        }
        ring(s, 1, 1, w - 2, h - 2, p.slabHi, p.slabLo);
        ring(s, 2, 2, w - 4, h - 4, p.slabHi, p.slabLo);
        if (head > 0) {
            // 彫った溝と、両の端に青緑の象嵌
            s.rect(7, head - 1, w - 14, 1, p.slabLo);
            s.rect(7, head, w - 14, 1, p.slabHi);
            for (int cx : new int[] {9, w - 10}) {
                s.diamond(cx, head - 1, 3, p.fDeep, 0);
                s.diamond(cx, head - 1, 2, p.pat, 0);
                s.px(cx, head - 2, mix(p.pat, 0xFFFFFFFF, 0.5f));
            }
        }
        notchOutline(s, 0, 0, w, h, 2, p.outline);
    }

    private static void paintDeep(Paper.Sheet s, int w, int h, int head, Pal p, Accent ac) {
        fillNotched(s, 0, 0, w, h, 1, p.slab);
        ring(s, 1, 1, w - 2, h - 2, p.f, p.fLo);
        s.rect(2, 2, w - 4, 1, p.slabHi);
        s.rect(2, h - 3, w - 4, 1, p.slabLo);
        if (head > 0) {
            s.rect(2, head, w - 4, 1, p.wellBR);
            s.rect(2, head + 1, w - 4, 1, p.slabLo);
        }
        // 四隅に差し色の鉤
        for (int i = 0; i < 7; i++) {
            int c = i < 5 ? ac.mid() : ac.lo();
            s.px(1 + i, 1, c);
            s.px(1, 1 + i, c);
            s.px(w - 2 - i, 1, c);
            s.px(w - 2, 1 + i, c);
            s.px(1 + i, h - 2, c);
            s.px(1, h - 2 - i, c);
            s.px(w - 2 - i, h - 2, c);
            s.px(w - 2, h - 2 - i, c);
        }
        notchOutline(s, 0, 0, w, h, 1, p.outline);
    }

    // ---------- 欄 (沈んだ面) ----------
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        UiLook look = UiLook.get();
        Pal p = pal();
        Paper.draw(g, "plate_well_" + look + "_" + w + "x" + h, "", x, y, w, h, s -> {
            s.rect(0, 0, w, h, p.well);
            if (look == UiLook.DEEP) {
                ring(s, 0, 0, w, h, p.fDeep, p.fDeep);
            } else {
                ring(s, 0, 0, w, h, p.wellTL, p.wellBR);
                s.rect(1, 1, w - 2, 1, mix(p.well, 0xFF000000, 0.35f));
            }
        });
    }

    // 文の塊の下の暗い欄 (明るい板のときだけ描く)
    public static void textWell(GuiGraphics g, int x, int y, int w, int h) {
        if (pal().lightPanel) {
            well(g, x, y, w, h);
        }
    }

    // 区切りの溝
    public static void rule(GuiGraphics g, int x, int y, int w) {
        Pal p = pal();
        if (p.lightPanel) {
            g.fill(x, y, x + w, y + 1, 0xFF2A2E3C);
        } else {
            g.fill(x, y, x + w, y + 1, p.slabLo);
            g.fill(x, y + 1, x + w, y + 2, p.slabHi);
        }
    }

    // ---------- 升 (押せる小さな板) ----------
    private static void body(Paper.Sheet s, int w, int h, int body, int top, int lip, int lipRows, int outline) {
        fillNotched(s, 0, 0, w, h, 1, body);
        s.rect(1, 1, w - 2, 1, top);
        s.rect(1, h - 1 - lipRows, w - 2, lipRows, lip);
        notchOutline(s, 0, 0, w, h, 1, outline);
    }

    public static void tile(GuiGraphics g, int x, int y, int w, int h, int state, Accent ac) {
        UiLook look = UiLook.get();
        Pal p = pal();
        String name = "plate_tile_" + look + "_" + w + "x" + h + "_" + state + (state == ON || look == UiLook.DEEP ? "_" + Integer.toHexString(ac.mid()) : "");
        Paper.draw(g, name, "", x, y, w, h, s -> {
            switch (look) {
                case STONE -> {
                    switch (state) {
                        case ON -> body(s, w, h, 0xFFE2B55E, 0xFFF8DD9C, 0xFF9A7030, 2, p.outline);
                        case HOVER -> body(s, w, h, 0xFFD2CCB8, 0xFFFFFBEB, 0xFF8E8875, 2, 0xFFFFFBEB);
                        case FLAT -> body(s, w, h, 0xFFA29C8A, 0xFFA29C8A, 0xFFA29C8A, 0, 0xFF6E6A5C);
                        default -> body(s, w, h, 0xFFB6AF9A, 0xFFE4DECA, 0xFF7E7866, 2, p.outline);
                    }
                    if (state != FLAT) {
                        s.rect(1, 2, 1, h - 5, state == ON ? 0xFFF8DD9C : state == HOVER ? 0xFFFFFBEB : 0xFFE4DECA);
                    }
                }
                case DEEP -> {
                    switch (state) {
                        case ON -> {
                            body(s, w, h, ac.deep(), mix(ac.deep(), ac.mid(), 0.25f), ac.mid(), 2, ac.mid());
                        }
                        case HOVER -> body(s, w, h, 0xFF151822, 0xFF1E2230, 0xFF0B0C12, 1, p.fHi);
                        case FLAT -> body(s, w, h, p.slabLo, p.slabLo, p.slabLo, 0, 0xFF1A1D28);
                        default -> body(s, w, h, p.well, 0xFF12141C, 0xFF07080C, 1, p.wellBR);
                    }
                }
                default -> {
                    switch (state) {
                        case ON -> {
                            int b = mix(0xFF202534, ac.mid(), 0.20f);
                            body(s, w, h, b, mix(b, 0xFFFFFFFF, 0.22f), mix(b, 0xFF000000, 0.50f), 2, p.fHi);
                            // 金の角を一画素ずつ太らせる
                            s.px(1, 1, p.fHi);
                            s.px(w - 2, 1, p.fHi);
                            s.px(1, h - 2, p.f);
                            s.px(w - 2, h - 2, p.f);
                        }
                        case HOVER -> body(s, w, h, 0xFF283046, 0xFF3C4662, 0xFF12151E, 2, p.f);
                        case FLAT -> body(s, w, h, p.well, p.well, p.well, 0, 0xFF1C202C);
                        default -> body(s, w, h, 0xFF202534, 0xFF2E3548, 0xFF12151E, 2, p.outline);
                    }
                }
            }
        });
    }

    // 升の上の字の色 (RGB)
    public static int tileInk(int state, Accent ac) {
        Pal p = pal();
        return switch (UiLook.get()) {
            case STONE -> state == FLAT ? 0x6E6A5C : state == ON ? 0x2A1A04 : 0x2A2620;
            case DEEP -> state == ON ? (ac.hi() & 0xFFFFFF) : state == HOVER ? p.text : p.soft;
            default -> state == ON ? 0xFFF6EA : state == HOVER ? p.text : p.soft;
        };
    }

    // ---------- 釦 ----------
    // PRESS は 1 画素沈む (下の縁が 1 画素になる)。字の色を返す
    public static int button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean primary, int state, Accent ac) {
        UiLook look = UiLook.get();
        Pal p = pal();
        int[] c = buttonColors(look, p, primary, state, ac);
        boolean press = state == PRESS;
        int yy = y + (press ? 1 : 0);
        int hh = h - (press ? 1 : 0);
        int lipRows = state == FLAT ? 0 : press ? 1 : 2;
        String name = "plate_btn_" + look + "_" + w + "x" + hh + "_" + (primary ? "p" : "s") + state + (look == UiLook.DEEP ? "_" + Integer.toHexString(ac.mid()) : "");
        Paper.draw(g, name, "", x, yy, w, hh, s -> body(s, w, hh, c[0], press ? mix(c[0], 0xFF000000, 0.25f) : c[1], c[2], lipRows, c[3]));
        if (label != null) {
            int tw = font.width(label);
            g.drawString(font, label, x + (w - tw) / 2, yy + (hh - lipRows - 7) / 2 + (press ? 1 : 0), 0xFF000000 | c[4], false);
        }
        return c[4];
    }

    // {地, 上の光, 下の縁, 外の線, 字 (RGB)}
    private static int[] buttonColors(UiLook look, Pal p, boolean primary, int state, Accent ac) {
        if (state == FLAT) {
            return look == UiLook.STONE ? new int[] {0xFFA29C8A, 0xFFA29C8A, 0xFFA29C8A, 0xFF6E6A5C, 0x6E6A5C}
                    : new int[] {p.well, p.well, p.well, look == UiLook.DEEP ? 0xFF1A1D28 : 0xFF1C202C, p.dim};
        }
        boolean hot = state == HOVER;
        switch (look) {
            case STONE:
                if (primary) {
                    return new int[] {hot ? 0xFF52B8A8 : 0xFF3F9E90, hot ? 0xFFA8F0E0 : 0xFF86DCCB, 0xFF22605A, hot ? 0xFFFFFBEB : p.outline, 0xF8FFFA};
                }
                return new int[] {hot ? 0xFFD2CCB8 : 0xFFB6AF9A, hot ? 0xFFFFFBEB : 0xFFE4DECA, 0xFF7E7866, hot ? 0xFFFFFBEB : p.outline, 0x2A2620};
            case DEEP:
                if (primary) {
                    return new int[] {hot ? mix(ac.mid(), ac.hi(), 0.45f) : ac.mid(), ac.hi(), ac.lo(), hot ? 0xFFFFFFFF : p.outline, 0x0B0C12};
                }
                return new int[] {hot ? 0xFF1C2030 : 0xFF151822, hot ? 0xFF2A3044 : 0xFF202432, 0xFF0B0C12, hot ? ac.mid() : p.f, p.text};
            default:
                if (primary) {
                    int b = mix(p.f, p.fHi, hot ? 0.62f : 0.28f);
                    return new int[] {b, mix(p.fHi, 0xFFFFFFFF, hot ? 0.55f : 0.30f), p.fLo, hot ? 0xFFFFF2D0 : p.outline, 0x2A1808};
                }
                return new int[] {hot ? 0xFF343C56 : 0xFF2A3044, hot ? 0xFF4A5674 : 0xFF3E4864, 0xFF151924, hot ? p.fHi : p.outline, p.text};
        }
    }

    // つまみ (選んだものは灯った升、ほかは沈んだ升)
    public static void tab(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean on, boolean hover) {
        int state = on ? ON : hover ? HOVER : REST;
        tile(g, x, y - (on ? 1 : 0), w, h + (on ? 1 : 0), state, GOLD);
        int rgb = tileInk(state, GOLD);
        int tw = Math.min(font.width(label), w - 4);
        FitText.draw(g, font, label, x + (w - tw) / 2, y + (h - 9) / 2, w - 4, 0xFF000000 | rgb, false);
    }

    // 品を立てる窓 (店の右上): 欄の中に淡い光と石の台
    public static void plinth(GuiGraphics g, int x, int y, int w, int h, Accent ac) {
        UiLook look = UiLook.get();
        Pal p = pal();
        Paper.draw(g, "plate_plinth_" + look + "_" + w + "x" + h + "_" + Integer.toHexString(ac.mid()), "", x, y, w, h, s -> {
            int[] ramp = {p.well, mix(p.well, ac.mid(), 0.07f), mix(p.well, ac.mid(), 0.14f), mix(p.well, ac.mid(), 0.22f), mix(p.well, ac.mid(), 0.31f)};
            float cx = w / 2f;
            float cy = h * 0.46f;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    float dx = (i - cx) / (w * 0.55f);
                    float dy = (j - cy) / (h * 0.62f);
                    s.px(i, j, dither(ramp, Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy)), i, j));
                }
            }
            // 石の台 (二段)
            int top = h - 19;
            int stone = mix(p.well, 0xFFB8B2A0, 0.42f);
            int hi = mix(stone, 0xFFFFFFFF, 0.30f);
            int lo = mix(stone, 0xFF000000, 0.45f);
            int w1 = Math.min(w - 24, 84);
            int x1 = (w - w1) / 2;
            s.rect(x1, top, w1, 6, stone);
            s.rect(x1, top, w1, 1, hi);
            s.rect(x1, top + 5, w1, 1, lo);
            s.rect(x1 - 6, top + 6, w1 + 12, 8, mix(stone, 0xFF000000, 0.18f));
            s.rect(x1 - 6, top + 6, w1 + 12, 1, stone);
            s.rect(x1 - 6, top + 13, w1 + 12, 1, lo);
            for (int i = x1 - 6 + 9; i < x1 + w1 + 6; i += 14) {
                s.rect(i, top + 7, 1, 6, lo);
            }
            s.rect(0, top + 14, w, h - top - 14, mix(p.well, 0xFF000000, 0.35f));
            s.rect(0, top + 14, w, 1, mix(p.well, ac.mid(), 0.25f));
            if (look == UiLook.DEEP) {
                ring(s, 0, 0, w, h, p.fDeep, p.fDeep);
            } else {
                ring(s, 0, 0, w, h, p.wellTL, p.wellBR);
            }
        });
    }

    // 釦の上を斜めに渡る光 (t = 0..1 で左から右へ。釦の地の中だけ)
    public static void shine(GuiGraphics g, int x, int y, int w, int h, float t) {
        if (t <= 0f || t >= 1f) {
            return;
        }
        int lead = x + Math.round(t * (w + h + 8)) - 4;
        for (int j = 1; j < h - 3; j++) {
            int x0 = Math.max(x + 1, lead - j - 5);
            int x1 = Math.min(x + w - 1, lead - j);
            if (x1 > x0) {
                g.fill(x0, y + j, x1, y + j + 1, 0x50FFFFFF);
            }
        }
    }

    // 画面に置く釦 (Button.builder の代わり)
    public static class Btn extends net.minecraft.client.gui.components.Button {
        private final boolean primary;

        public Btn(int x, int y, int w, int h, Component text, OnPress onPress, boolean primary) {
            super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int state = !active ? FLAT : isHovered() ? HOVER : REST;
            button(g, net.minecraft.client.Minecraft.getInstance().font, getX(), getY(), getWidth(), getHeight(), getMessage(), primary, state, NEUTRAL);
        }
    }

    // ---------- 小さな札・目盛り ----------
    // 字の入る小さな札 (高さ 11)。幅を返す
    public static int chip(GuiGraphics g, Font font, int x, int y, Component label, int fill, int textRgb) {
        int w = font.width(label) + 7;
        g.fill(x + 1, y, x + w - 1, y + 11, fill);
        g.fill(x, y + 1, x + 1, y + 10, fill);
        g.fill(x + w - 1, y + 1, x + w, y + 10, fill);
        g.fill(x + 1, y + 10, x + w - 1, y + 11, mix(fill, 0xFF000000, 0.35f));
        g.fill(x + 1, y, x + w - 1, y + 1, mix(fill, 0xFFFFFFFF, 0.25f));
        g.drawString(font, label, x + 4, y + 2, 0xFF000000 | textRgb, false);
        return w;
    }

    // 目盛りの一区切り (on = 差し色、off = 沈んだ升)
    public static void seg(GuiGraphics g, int x, int y, int w, int h, boolean on, int color) {
        Pal p = pal();
        if (on) {
            g.fill(x, y, x + w, y + h, color);
            g.fill(x, y, x + w, y + 1, mix(color, 0xFFFFFFFF, 0.45f));
            g.fill(x, y + h - 1, x + w, y + h, mix(color, 0xFF000000, 0.40f));
        } else {
            g.fill(x, y, x + w, y + h, p.lightPanel ? 0xFF2A2E3C : mix(p.slab, 0xFF000000, 0.45f));
            g.fill(x, y + h - 1, x + w, y + h, p.lightPanel ? 0xFF3A4054 : p.slabHi);
        }
    }

    // 縁を走る光 (板の縁から inset 画素入った輪の上を、u = 0..1 で一周。尾は len 画素)
    public static void runLight(GuiGraphics g, int x, int y, int w, int h, int inset, float u, int len, int rgb) {
        int rw = w - inset * 2;
        int rh = h - inset * 2;
        int total = 2 * (rw - 1) + 2 * (rh - 1);
        int head = Math.round(u * total);
        for (int i = 0; i < len; i++) {
            int d = ((head - i) % total + total) % total;
            int px;
            int py;
            if (d < rw - 1) {
                px = d;
                py = 0;
            } else if (d < rw - 1 + rh - 1) {
                px = rw - 1;
                py = d - (rw - 1);
            } else if (d < 2 * (rw - 1) + rh - 1) {
                px = rw - 1 - (d - (rw - 1) - (rh - 1));
                py = rh - 1;
            } else {
                px = 0;
                py = rh - 1 - (d - 2 * (rw - 1) - (rh - 1));
            }
            int a = Math.round(255 * (1f - i / (float) len));
            g.fill(x + inset + px, y + inset + py, x + inset + px + 1, y + inset + py + 1, (a << 24) | (i < 3 ? 0xFFFFFF : rgb));
        }
    }

    // ---------- 小さな印 ----------
    private static final String[] BELL = {"....#....", "...###...", "..#####..", "..#####..", "..#####..", ".#######.", ".#######.", "#########", "...##...."};

    // 鐘の印 (9x9)。color = 本体、下の一段は暗く
    public static void bell(GuiGraphics g, int x, int y, int color) {
        Paper.draw(g, "plate_bell_" + Integer.toHexString(color), "", x, y, 9, 9, s -> {
            for (int j = 0; j < 9; j++) {
                for (int i = 0; i < 9; i++) {
                    if (BELL[j].charAt(i) == '#') {
                        s.px(i, j, j == 7 ? mix(color, 0xFF000000, 0.35f) : i <= 3 && j > 1 && j < 7 ? mix(color, 0xFFFFFFFF, 0.35f) : color);
                    }
                }
            }
        });
    }

    // 小さな菱 (最初の技・いまの職の印)
    public static void gem(GuiGraphics g, int x, int y, int r, int color) {
        Paper.draw(g, "plate_gem_" + r + "_" + Integer.toHexString(color), "", x - r - 1, y - r - 1, r * 2 + 3, r * 2 + 3, s -> {
            s.diamond(r + 1, r + 1, r + 1, 0xFF07080C, 0);
            s.diamond(r + 1, r + 1, r, color, mix(color, 0xFFFFFFFF, 0.5f));
        });
    }

    // ---------- 見せ絵 (止まった月と、巨神の背と、鐘楼) ----------
    // 窓の中の絵。月のまわりが差し色に明るく、左の端は板の色へ溶ける (bleed)
    public static void showcase(GuiGraphics g, int x, int y, int w, int h, Accent ac, boolean bleed) {
        UiLook look = UiLook.get();
        Pal p = pal();
        Paper.draw(g, "plate_show_" + look + "_" + w + "x" + h + "_" + Integer.toHexString(ac.mid()) + (bleed ? "b" : ""), "", x, y, w, h, s -> {
            int base = bleed ? p.slab : p.well;
            int[] ramp = {base, mix(base, ac.mid(), 0.07f), mix(base, ac.mid(), 0.14f), mix(base, ac.mid(), 0.22f), mix(base, ac.mid(), 0.31f), mix(base, ac.mid(), 0.41f),
                    mix(base, ac.mid(), 0.52f)};
            float mx = w * 0.30f;
            float my = h * 0.24f;
            int ground = h - 13;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    float dx = (i - mx) / (w * 0.95f);
                    float dy = (j - my) / (h * 0.80f);
                    float v = (float) Math.pow(Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy)), 1.35f);
                    float hz = Math.max(0f, 1f - Math.abs(j - (ground - 6)) / 30f) * 0.42f;
                    v = Math.max(v * 0.92f, hz);
                    if (bleed) {
                        v *= Math.min(1f, i / 34f);
                    }
                    s.px(i, j, dither(ramp, v, i, j));
                }
            }
            // 星
            int[][] stars = {{62, 9}, {81, 21}, {104, 12}, {96, 38}, {112, 55}, {70, 44}, {8, 58}, {20, 76}, {88, 70}, {108, 84}, {54, 62}, {14, 12}, {74, 88}};
            for (int k = 0; k < stars.length; k++) {
                int sx = stars[k][0] * w / 120;
                int sy = stars[k][1] * h / 147;
                int c = k % 3 == 0 ? ac.hi() : 0xFFECE6D6;
                s.px(sx, sy, c);
                if (k % 4 == 1) {
                    int c2 = mix(c, base, 0.45f);
                    s.px(sx - 1, sy, c2);
                    s.px(sx + 1, sy, c2);
                    s.px(sx, sy - 1, c2);
                    s.px(sx, sy + 1, c2);
                }
            }
            // 月
            float r = Math.min(w, h) * 0.225f;
            int moon = mix(0xFFE8ECF4, ac.hi(), 0.22f);
            int moonLo = mix(moon, ac.lo(), 0.42f);
            int moonRim = mix(moon, 0xFFFFFFFF, 0.6f);
            for (int j = (int) (my - r - 1); j <= (int) (my + r + 1); j++) {
                for (int i = (int) (mx - r - 1); i <= (int) (mx + r + 1); i++) {
                    float dx = i + 0.5f - mx;
                    float dy = j + 0.5f - my;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > r) {
                        continue;
                    }
                    float sh = (dx + dy) / (r * 1.4f);
                    int c = sh > 0.20f + (BAYER[j & 3][i & 3] / 16f) * 0.30f ? moonLo : moon;
                    if (d > r - 1.2f && dx + dy < 0) {
                        c = moonRim;
                    }
                    s.px(i, j, c);
                }
            }
            float[][] craters = {{-0.38f, -0.30f, 0.16f}, {0.22f, -0.46f, 0.10f}, {-0.10f, 0.30f, 0.20f}, {0.42f, 0.12f, 0.11f}, {-0.52f, 0.20f, 0.08f}};
            int crater = mix(moon, ac.lo(), 0.30f);
            for (float[] c : craters) {
                s.disc(mx + c[0] * r, my + c[1] * r, Math.max(1.2f, c[2] * r), crater);
            }
            // 遠くの巨神の背 (背に古い街の小さな角)
            int far = mix(base, ac.lo(), 0.50f);
            for (int i = 0; i < w; i++) {
                float t = (i - w * 0.18f) / (w * 0.95f);
                if (t <= 0f || t >= 1f) {
                    continue;
                }
                int top = ground - Math.round((float) Math.sin(Math.PI * Math.pow(t, 0.72)) * 15f);
                if ((i * 7) % 23 < 3 && t > 0.2f && t < 0.75f) {
                    top -= 2 + (i % 3);
                }
                s.rect(i, top, 1, ground - top, far);
            }
            // 鐘楼 (右の端): 尖った屋根・鐘の間 (灯りと鐘)・細い窓
            int tx = bleed ? 24 : 9;
            int dark = mix(base, 0xFF000000, 0.55f);
            int top = ground - 44;
            s.rect(tx, top, 7, 44, dark);
            s.rect(tx - 1, top - 1, 9, 1, dark);
            s.rect(tx, top - 3, 7, 2, dark);
            s.rect(tx + 1, top - 5, 5, 2, dark);
            s.rect(tx + 2, top - 7, 3, 2, dark);
            s.rect(tx + 3, top - 11, 1, 4, dark);
            s.rect(tx + 2, top + 3, 3, 5, mix(base, ac.mid(), 0.42f));
            s.px(tx + 3, top + 2, mix(base, ac.mid(), 0.42f));
            s.px(tx + 3, top + 4, ac.hi());
            s.px(tx + 3, top + 5, ac.mid());
            s.rect(tx + 3, top + 16, 1, 2, mix(dark, ac.mid(), 0.45f));
            s.rect(tx + 3, top + 28, 1, 2, mix(dark, ac.mid(), 0.30f));
            s.rect(tx - 1, ground - 2, 9, 2, dark);
            // 足もとの石の段
            int stone = mix(base, 0xFF000000, 0.40f);
            s.rect(0, ground, w, h - ground, stone);
            s.rect(0, ground, w, 1, mix(stone, ac.mid(), 0.55f));
            s.rect(0, ground + 1, w, 1, mix(stone, ac.mid(), 0.22f));
            for (int j = ground + 2; j < h; j += 5) {
                s.rect(0, j + 4, w, 1, mix(stone, 0xFF000000, 0.45f));
                for (int i = ((j / 5) % 2) * 7; i < w; i += 14) {
                    s.rect(i, j, 1, 4, mix(stone, 0xFF000000, 0.45f));
                }
            }
            if (bleed) {
                // 左の端は板の色へ (石の段も)
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < 20; i++) {
                        if (i / 20f < (BAYER[j & 3][i & 3] + 0.5f) / 16f) {
                            s.px(i, j, p.slab);
                        }
                    }
                }
            }
        });
    }
}
