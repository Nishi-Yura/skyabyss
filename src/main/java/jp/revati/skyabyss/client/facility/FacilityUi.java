package jp.revati.skyabyss.client.facility;

import net.minecraft.client.gui.GuiGraphics;

// 都の施設の画面 (店・ギルド) の絵柄: 暗い石板の地、骨の枠、角の青緑のひし形 (旅の地図の枠・装飾の画面と同じ作り。TravelMapScreen.frame を写した)
public final class FacilityUi {
    public static final int TEXT = 0xFFE2D9C2;
    public static final int DIM = 0xFF8E8A7C;
    public static final int TEAL = 0xFF7FD6D0;
    public static final int GOLD = 0xFFF2C14E;
    public static final int LACK = 0xFFD9779A;
    public static final int SLATE = 0xF2181D25;
    public static final int SLATE_IN = 0xFF10141A;
    public static final int ROW = 0xFF1C222C;
    public static final int ROW_HOVER = 0xFF243440;
    public static final int LINE = 0xFF2E3844;

    private FacilityUi() {
    }

    // 枠つきの板 (地を塗ってから枠)
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, SLATE);
        frame(g, x, y, w, h);
    }

    // 窓 (板の中の一段暗い四角。一覧の地)
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLATE_IN);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, LINE);
    }

    // 値段の札 (骨の色の小さな札。中に銭と素材を描く)
    public static void tag(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF2A2620);
        g.renderOutline(x, y, w, h, 0xFF6E6654);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF4A4436);
    }

    // 骨の枠 (半分の画素で描く)
    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        int x0 = x * 2;
        int y0 = y * 2;
        int x1 = x0 + w * 2 - 1;
        int y1 = y0 + h * 2 - 1;
        int band = 6;
        rect2(g, x0, y0, x1, y1, 0xFF0C0E12);
        for (int i = 1; i <= band; i++) {
            int c = i <= 2 ? 0xFFE2D9C2 : i <= 4 ? 0xFFC4BAA0 : 0xFFA0967C;
            rect2(g, x0 + i, y0 + i, x1 - i, y1 - i, c);
        }
        for (int i = 1; i <= band; i++) {
            int shade = i <= 3 ? 0xFF6E6654 : 0xFFA0967C;
            g.fill(x1 - i, y0 + i, x1 - i + 1, y1 - i + 1, shade);
            g.fill(x0 + i, y1 - i, x1 - i + 1, y1 - i + 1, shade);
        }
        rect2(g, x0 + band + 1, y0 + band + 1, x1 - band - 1, y1 - band - 1, 0xFF0C0E12);
        for (int tx = x0 + 24; tx < x1 - 16; tx += 24) {
            g.fill(tx, y0 + 2, tx + 1, y0 + band + 1, 0xFF6E6654);
            g.fill(tx + 1, y0 + 2, tx + 2, y0 + band + 1, 0xFFE2D9C2);
            g.fill(tx, y1 - band, tx + 1, y1 - 1, 0xFF6E6654);
        }
        for (int ty = y0 + 24; ty < y1 - 16; ty += 24) {
            g.fill(x0 + 2, ty, x0 + band + 1, ty + 1, 0xFF6E6654);
            g.fill(x1 - band, ty, x1 - 1, ty + 1, 0xFF6E6654);
        }
        for (int[] c : new int[][]{{x0 + 7, y0 + 7}, {x1 - 7, y0 + 7}, {x0 + 7, y1 - 7}, {x1 - 7, y1 - 7}}) {
            gem2(g, c[0], c[1], 6);
        }
        g.pose().popPose();
    }

    private static void rect2(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1 + 1, y0 + 1, c);
        g.fill(x0, y1, x1 + 1, y1 + 1, c);
        g.fill(x0, y0, x0 + 1, y1 + 1, c);
        g.fill(x1, y0, x1 + 1, y1 + 1, c);
    }

    // 青緑のひし形 (半分の画素の座標で)
    private static void gem2(GuiGraphics g, int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF0C0E12);
            if (half >= 1) {
                g.fill(cx - half + 1, cy + dy, cx + half, cy + dy + 1, 0xFF2A5E6A);
            }
            if (dy < 0 && half >= 2) {
                g.fill(cx, cy + dy, cx + half - 1, cy + dy + 1, 0xFF7FD6D0);
            }
        }
        g.fill(cx - 1, cy - r + 3, cx, cy - r + 4, 0xFF9FE8FF);
    }

    // 小さなひし形 (GUI の座標で。見出しの印)
    public static void gem(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        gem2(g, x * 2, y * 2, 5);
        g.pose().popPose();
    }
}
