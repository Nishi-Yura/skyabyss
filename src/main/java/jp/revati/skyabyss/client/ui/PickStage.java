package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 選ぶ類の組み方「三枚を扇に」(UI の 3 周目で選ぶ A をユーザーが選んだ。docs/design/56 の 2.1):
// 職を選ぶ画面と同じ手つき。選ぶものは背の高い札を左寄りに扇で広げ、乗せた札は桃色で持ち上がる。
// 左上に題 (桃色の菱と字) と添えの一行、右の黒い紙の上に記録・手引き・釦。下の山の上に手引きの一行。舞台は論理の 408x228
public final class PickStage {
    private PickStage() {
    }

    public static final int W = Paper.STAGE_W;
    public static final int H = Paper.STAGE_H;
    public static final int CARD_W = 108;
    public static final int CARD_H = 156;
    // 右の列 (黒い紙の上)
    public static final int RIGHT_X = 280;
    public static final int RIGHT_W = 118;
    public static final int ON_PINK = 0xFFF6EA;

    private static final float[][] PEAKS = {{-20, 0}, {18, -12}, {60, -4}, {104, -20}, {150, -8}, {200, -16}, {246, -2}, {290, -22}, {336, -8}, {376, -18}, {428, -6}};
    private static final int[][] DIAMONDS = {{396, 18, 3, 1}, {266, 120, 2, 0}, {12, 150, 2, 1}};
    private static final float[][] TILT = {{0}, {-5, 4}, {-6, 0, 5}};

    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height) {
        Paper.stage(g, fit, width, height, "pick", PEAKS, DIAMONDS);
    }

    // 題 (左上) と添えの一行
    public static void title(GuiGraphics g, Font font, Component title, Component sub) {
        Paper.pin(g, 14, 13, 3, true);
        FitText.draw(g, font, title, 22, 9, 250, Paper.ON_BLACK_PINK, false);
        if (sub != null) {
            FitText.draw(g, font, sub, 22, 21, 250, Paper.ON_BLACK_SOFT, false);
        }
    }

    // n 枚のうち i 枚目の札の真ん中 {x, y}。lift なら持ち上げる
    public static int[] center(int i, int n, boolean lift) {
        int step = n <= 2 ? 128 : 82;
        int x0 = n <= 2 ? 76 : 60;
        int cy = 122 + (n == 3 && i == 1 ? -4 : 2) - (lift ? 10 : 0);
        return new int[] {x0 + i * step, cy};
    }

    public static float tilt(int i, int n) {
        float[] t = TILT[Math.max(0, Math.min(2, n - 1))];
        return t[Math.min(i, t.length - 1)];
    }

    // 札を一枚 (pink = 乗せている・選んでいる)
    public static void card(GuiGraphics g, int i, int n, boolean lift, boolean pink) {
        int[] c = center(i, n, lift);
        Paper.cut(g, "pick_card" + i, Paper.rotRect(c[0], c[1], CARD_W, CARD_H, tilt(i, n)), pink ? Paper.PINK : Paper.CREAM,
                pink ? Paper.BLACK_LO : Paper.PINK_LO, 2, 4);
    }

    // 指の下の札 (右の札が上に重なるので、右から調べる。なければ -1)
    public static int at(double mx, double my, int n) {
        for (int i = n - 1; i >= 0; i--) {
            int[] c = center(i, n, false);
            if (Math.abs(mx - c[0]) <= CARD_W / 2f && my >= c[1] - CARD_H / 2f - 10 && my <= c[1] + CARD_H / 2f) {
                return i;
            }
        }
        return -1;
    }

    // 札の中の字の左上 {x, y} と幅
    public static int[] inner(int i, int n, boolean lift) {
        int[] c = center(i, n, lift);
        return new int[] {c[0] - CARD_W / 2 + 9, c[1] - CARD_H / 2 + 8, CARD_W - 18};
    }

    // 下の山の上の手引き
    public static void hint(GuiGraphics g, Font font, Component text) {
        FitText.draw(g, font, text, 14, 211, 250, ON_PINK, false);
    }
}
