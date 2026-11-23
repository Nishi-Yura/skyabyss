package jp.revati.skyabyss.client.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

// 店の類の組み方「帳面と台」(UI の 3 周目で店 C をユーザーが選んだ。docs/design/56 の 2.1):
// 左に少し傾けた大きな生成りの帳面 (上の辺につまみの栞)。品は帳面に墨で書いた行 (名前 …… 値段、桃色の点線)、乗せた行は桃色の紙。
// 右の黒い紙の上に、乗せた品を大きく桃色の台に立てて、名前・値段・品の説明を生成りの字で。店の名前は右上、手持ちは山の上の黒い札。
// 舞台は論理の 408x228 (Paper.STAGE_W/H)。字はまっすぐ、行と当たり判定もまっすぐ (紙だけ傾ける)
public final class ShopStage {
    private ShopStage() {
    }

    public static final int W = Paper.STAGE_W;
    public static final int H = Paper.STAGE_H;
    // 帳面の紙と、その中の行の欄
    public static final float[] LEDGER = {2, 20, 236, 16, 240, 206, 6, 212};
    public static final int LIST_X = 8;
    public static final int LIST_Y = 26;
    public static final int LIST_W = 224;
    public static final int ROW_H = 24;
    public static final int ROWS = 7;
    // 手引きの行 (帳面の下の端)
    public static final int HINT_Y = 198;
    // つまみ (帳面の上の辺)
    public static final int TAB_X = 16;
    public static final int TAB_Y = 6;
    public static final int TAB_W = 50;
    public static final int TAB_STEP = 54;
    // 右の列 (黒い紙の上)
    public static final int RIGHT_X = 262;
    public static final int RIGHT_W = 136;
    // 字の色
    public static final int ON_PINK = 0xFFF6EA;
    public static final int LACK = 0xB0102C;
    public static final int LACK_ON_BLACK = 0xF0607A;

    private static final float[][] PEAKS = {{-20, -2}, {60, -6}, {150, -2}, {240, -8}, {262, -24}, {300, -12}, {340, -30}, {372, -14}, {428, -22}};
    private static final int[][] DIAMONDS = {{250, 30, 2, 1}, {398, 112, 3, 0}, {256, 150, 2, 0}};

    // 黒い紙・山・帳面 (fit.begin のあと)
    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height) {
        Paper.stage(g, fit, width, height, "shop", PEAKS, DIAMONDS);
        Paper.cut(g, "shop_ledger", LEDGER, Paper.CREAM, Paper.PINK_LO, 4, 4);
    }

    public static int tabX(int i) {
        return TAB_X + i * TAB_STEP;
    }

    public static void tab(GuiGraphics g, Font font, int i, Component label, boolean on, int mx, int my) {
        Paper.tab(g, font, tabX(i), TAB_Y, TAB_W, 13, label, on, in(mx, my, tabX(i), TAB_Y, TAB_W, 13));
    }

    public static int tabAt(double mx, double my, int n) {
        for (int i = 0; i < n; i++) {
            if (in(mx, my, tabX(i), TAB_Y, TAB_W, 13)) {
                return i;
            }
        }
        return -1;
    }

    public static int rowY(int i) {
        return LIST_Y + i * ROW_H;
    }

    // 行の番号 (見えている行の中で。外なら -1)
    public static int rowAt(double mx, double my) {
        if (mx < LIST_X - 2 || mx >= LIST_X + LIST_W || my < LIST_Y || my >= LIST_Y + ROWS * ROW_H) {
            return -1;
        }
        return (int) ((my - LIST_Y) / ROW_H);
    }

    // 乗せた行の桃色の紙
    public static void hot(GuiGraphics g, int i) {
        int y = rowY(i);
        Paper.cut(g, "shop_hot", new float[] {LIST_X - 2, y - 1, LIST_X + LIST_W + 2, y - 2, LIST_X + LIST_W + 3, y + ROW_H - 2, LIST_X - 1, y + ROW_H - 1}, Paper.PINK, 0, 0, 0);
    }

    // 名前から値段までの点線
    public static void leader(GuiGraphics g, int x0, int x1, int y, boolean hot) {
        for (int d = x0; d < x1; d += 3) {
            g.fill(d, y, d + 1, y + 1, hot ? 0xFFFFF6EA : Paper.PINK);
        }
    }

    // 送りの糸 (帳面の右の辺)
    public static void scroll(GuiGraphics g, int scroll, int n) {
        if (n <= ROWS) {
            return;
        }
        int top = LIST_Y + 4;
        int len = ROWS * ROW_H - 8;
        int bar = Math.max(12, len * ROWS / n);
        int by = top + (len - bar) * scroll / Math.max(1, n - ROWS);
        g.fill(235, top, 236, top + len, Paper.CREAM_LO);
        g.fill(234, by, 237, by + bar, Paper.PINK);
    }

    public static void empty(GuiGraphics g, Font font, Component text) {
        FitText.drawCentered(g, font, text, LIST_X + LIST_W / 2, LIST_Y + ROWS * ROW_H / 2 - 4, LIST_W - 16, Paper.INK_SOFT, false);
    }

    public static void hint(GuiGraphics g, Font font, Component text) {
        // 長い手引きは縮めずに横へ流す
        Marquee.draw(g, font, text.getVisualOrderText(), LIST_X + 4, HINT_Y, LIST_W - 8, Paper.INK_SOFT, false);
    }

    // 店の名前 (右上)
    public static void title(GuiGraphics g, Font font, Component title) {
        FitText.draw(g, font, title, RIGHT_X, 10, RIGHT_W, Paper.ON_BLACK_PINK, false);
        g.fill(RIGHT_X, 21, RIGHT_X + RIGHT_W, 22, Paper.PINK_LO);
    }

    // 台の上の品 (大きく)。品の上に指があるか を返す (詳しい札を出すため)
    public static boolean plinth(GuiGraphics g, ItemStack stack, int mx, int my) {
        Paper.cut(g, "shop_plinth", new float[] {276, 92, 376, 88, 366, 106, 288, 108}, Paper.PINK, Paper.BLACK_LO, 2, 3);
        if (stack.isEmpty()) {
            return false;
        }
        g.pose().pushPose();
        g.pose().translate(294, 26, 0);
        g.pose().scale(4f, 4f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        return in(mx, my, 290, 22, 72, 70);
    }

    // 台の下の名前 (y = 114) と、説明の行 (from から、bottom まで)。説明は品の札の 2 行目から
    public static void describe(GuiGraphics g, Font font, ItemStack stack, Component name, int from, int bottom) {
        FitText.draw(g, font, Component.literal(name.getString()), RIGHT_X, 114, RIGHT_W, 0xFFF6EA, false);
        if (stack.isEmpty()) {
            return;
        }
        List<Component> tip = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        int y = from;
        for (int i = 1; i < tip.size() && y + 9 <= bottom; i++) {
            if (tip.get(i).getString().isBlank()) {
                continue;
            }
            for (FormattedCharSequence l : font.split(tip.get(i), RIGHT_W)) {
                if (y + 9 > bottom) {
                    break;
                }
                g.drawString(font, l, RIGHT_X, y, Paper.ON_BLACK_SOFT, false);
                y += 11;
            }
        }
    }

    // 手持ち (山の上の黒い札)
    public static void purse(GuiGraphics g, Font font, ItemStack coin, int coins) {
        String s = Integer.toString(coins);
        int w = 26 + font.width(s);
        int x = 392 - w;
        Paper.cut(g, "shop_purse", new float[] {x, 204, x + w + 2, 202, x + w, 222, x + 2, 224}, Paper.BLACK, Paper.PINK, 2, 2);
        g.renderItem(coin, x + 4, 205);
        g.drawString(font, s, x + 21, 210, Paper.ON_BLACK_HI, false);
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
