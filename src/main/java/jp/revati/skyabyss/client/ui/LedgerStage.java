package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 帳の類の組み方「黒に書いて、目録は帯に」(UI の 3 周目で帳 C をユーザーが選んだ。docs/design/56 の 2.1):
// 読む中身は左の黒い紙にじかに生成りの字で (題は桃色の菱を添えて、その下に濃い桃色の線)。
// 一覧 (目録) は右に縦長の生成りの帯 (少し傾けて)、選んだ行は帯から左へはみ出す桃色の付箋。下の段 (報酬・釦・手引き) は左下の山の上。
// 舞台は論理の 408x228 (Paper.STAGE_W/H)
public final class LedgerStage {
    private LedgerStage() {
    }

    public static final int W = Paper.STAGE_W;
    public static final int H = Paper.STAGE_H;
    // 目録の帯と、その中の一覧の欄・見出し
    public static final float[] INDEX = {270, 4, 404, 8, 400, 222, 274, 218};
    public static final int INDEX_X = 282;
    public static final int INDEX_W = 112;
    public static final int INDEX_HEAD_Y = 14;
    public static final int LIST_Y = 30;
    public static final int LIST_H = 184;
    // 読む頁 (黒い紙の上)
    public static final int PAGE_X = 18;
    public static final int PAGE_Y = 10;
    public static final int PAGE_W = 236;
    // 題の線の下から中身
    public static final int BODY_Y = 42;
    public static final int BODY_H = 140;
    // 下の段 (山の上)
    public static final int FOOT_Y = 196;
    // 字の色 (桃色の付箋の上)
    public static final int ON_PINK = 0xFFF6EA;

    private static final float[][] PEAKS = {{-20, -14}, {40, -26}, {90, -10}, {140, -30}, {196, -12}, {240, -20}, {300, -4}, {428, -2}};
    private static final int[][] DIAMONDS = {{258, 14, 2, 0}, {262, 120, 3, 1}};

    // K の頁 (Growth・Journey・Profile・Soul): 記録・スキル・旅のつまみはスキルの木と同じ所 (SkillScreen.HEAD)。頁の題はつまみの右、中身は K_BODY_Y から
    public static final Golden.Box K_HEAD = new Golden.Box(10, 5, 252, 21);
    public static final int K_BODY_Y = 32;

    // K の頁の題 (つまみの右。x = つまみの右の端)
    public static void kTitle(GuiGraphics g, Font font, Component title, int x) {
        Paper.pin(g, x + 4, 15, 3, true);
        FitText.draw(g, font, title, x + 12, 11, 262 - x - 12, Paper.ON_BLACK_PINK, false);
        g.fill(PAGE_X, 26, PAGE_X + PAGE_W, 27, Paper.PINK_LO);
    }

    // 黒い紙・山・目録の帯 (fit.begin のあと)
    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height) {
        Paper.stage(g, fit, width, height, "ledger", PEAKS, DIAMONDS);
        Paper.cut(g, "ledger_index", INDEX, Paper.CREAM, Paper.BLACK_LO, -3, 3);
    }

    // 目録の見出し (帯の上の端)
    public static void indexHead(GuiGraphics g, Font font, Component title) {
        FitText.draw(g, font, Component.literal(title.getString()), INDEX_X + 2, INDEX_HEAD_Y, INDEX_W - 2, Paper.INK_HEAD, false);
        g.fill(INDEX_X + 2, 25, INDEX_X + INDEX_W, 26, Paper.CREAM_LO);
    }

    // 選んだ行の付箋 (帯から左へはみ出す)。y は行の上、h は行の高さ
    public static void flag(GuiGraphics g, int y, int h) {
        Paper.cut(g, "ledger_flag", new float[] {252, y - 1, INDEX_X + INDEX_W + 2, y - 1, INDEX_X + INDEX_W + 4, y + h, 256, y + h + 1}, Paper.PINK, Paper.BLACK_LO, 1, 2);
        Paper.pin(g, 260, y + h / 2, 2, false);
    }

    // 乗せた行の地 (帯の上)
    public static void hover(GuiGraphics g, int y, int h) {
        g.fill(INDEX_X - 2, y, INDEX_X + INDEX_W, y + h, 0x30E45A9C);
    }

    // 見出しの行 (目録の中の区切り: 字と、右へ伸びる線)
    public static void section(GuiGraphics g, Font font, Component label, int x, int y, int h, int color) {
        Component c = Component.literal(label.getString());
        int tw = Math.min(font.width(c), INDEX_X + INDEX_W - x - 6);
        FitText.draw(g, font, c, x, y + (h - 7) / 2, tw, color, false);
        if (x + tw + 4 < INDEX_X + INDEX_W) {
            g.fill(x + tw + 4, y + h / 2, INDEX_X + INDEX_W, y + h / 2 + 1, Paper.CREAM_LO);
        }
    }

    // 読む頁の題 (桃色の菱・題・添え)
    public static void pageTitle(GuiGraphics g, Font font, Component title, Component sub) {
        Paper.pin(g, PAGE_X + 2, PAGE_Y + 5, 3, true);
        FitText.draw(g, font, title, PAGE_X + 10, PAGE_Y + 1, PAGE_W - 10, Paper.ON_BLACK_PINK, false);
        if (sub != null) {
            FitText.draw(g, font, sub, PAGE_X + 10, PAGE_Y + 13, PAGE_W - 10, Paper.ON_BLACK_SOFT, false);
        }
        g.fill(PAGE_X, PAGE_Y + 26, PAGE_X + PAGE_W, PAGE_Y + 27, Paper.PINK_LO);
    }

    // 下の段の手引き (山の上の字)
    public static void footHint(GuiGraphics g, Font font, Component text, int x, int w) {
        FitText.draw(g, font, text, x, FOOT_Y + 4, w, ON_PINK, false);
    }

    // 黒い紙の上の字の色を、生成りの帯の上の墨の色へ写す (目録の行の色)
    public static int ink(int rgb) {
        return Paper.inkOf(rgb) & 0xFFFFFF;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
