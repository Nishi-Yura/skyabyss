package jp.revati.skyabyss.client.facility;

import net.minecraft.client.gui.GuiGraphics;

// 都の施設の画面 (店・ギルド・取引・厩舎・記録・町の案内) の絵柄: 板と欄は黄金比の板 (client/ui/Golden) と同じ。frame は旅の地図の枠 (半分の画素) として残す
public final class FacilityUi {
    public static final int TEXT = 0xFFECE8DC;
    public static final int DIM = 0xFF8892B6;
    public static final int TEAL = 0xFF8FD0FF;
    public static final int GOLD = 0xFFE9C46A;
    public static final int LACK = 0xFFD9779A;
    public static final int SLATE = 0xF210162E;
    public static final int SLATE_IN = 0xFF0A0E1E;
    public static final int ROW = 0xFF141A34;
    public static final int ROW_HOVER = 0xFF222C52;
    public static final int LINE = 0xFF364064;

    private FacilityUi() {
    }

    // 枠つきの板。2026-11-04 から全画面と同じ黄金比の板の絵柄 (client/ui/Golden。docs/design/56)
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        jp.revati.skyabyss.client.ui.Golden.panel(g, new jp.revati.skyabyss.client.ui.Golden.Box(x, y, w, h));
    }

    // 窓 (板の中の一段暗い欄。一覧の地)。x, y, w, h は中身の範囲 (縁はその 1 外)
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        jp.revati.skyabyss.client.ui.Golden.recess(g, new jp.revati.skyabyss.client.ui.Golden.Box(x - 1, y - 1, w + 2, h + 2));
    }

    // 値段の札 (骨の色の小さな札。中に銭と素材を描く)
    public static void tag(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF2A2620);
        g.renderOutline(x, y, w, h, 0xFF8A7440);
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
        rect2(g, x0, y0, x1, y1, 0xFF05070F);
        for (int i = 1; i <= band; i++) {
            int c = i <= 2 ? 0xFFECE8DC : i <= 4 ? 0xFFC9A85C : 0xFFA88C4C;
            rect2(g, x0 + i, y0 + i, x1 - i, y1 - i, c);
        }
        for (int i = 1; i <= band; i++) {
            int shade = i <= 3 ? 0xFF8A7440 : 0xFFA88C4C;
            g.fill(x1 - i, y0 + i, x1 - i + 1, y1 - i + 1, shade);
            g.fill(x0 + i, y1 - i, x1 - i + 1, y1 - i + 1, shade);
        }
        rect2(g, x0 + band + 1, y0 + band + 1, x1 - band - 1, y1 - band - 1, 0xFF05070F);
        for (int tx = x0 + 24; tx < x1 - 16; tx += 24) {
            g.fill(tx, y0 + 2, tx + 1, y0 + band + 1, 0xFF8A7440);
            g.fill(tx + 1, y0 + 2, tx + 2, y0 + band + 1, 0xFFECE8DC);
            g.fill(tx, y1 - band, tx + 1, y1 - 1, 0xFF8A7440);
        }
        for (int ty = y0 + 24; ty < y1 - 16; ty += 24) {
            g.fill(x0 + 2, ty, x0 + band + 1, ty + 1, 0xFF8A7440);
            g.fill(x1 - band, ty, x1 - 1, ty + 1, 0xFF8A7440);
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
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF05070F);
            if (half >= 1) {
                g.fill(cx - half + 1, cy + dy, cx + half, cy + dy + 1, 0xFF364064);
            }
            if (dy < 0 && half >= 2) {
                g.fill(cx, cy + dy, cx + half - 1, cy + dy + 1, 0xFF8FD0FF);
            }
        }
        g.fill(cx - 1, cy - r + 3, cx, cy - r + 4, 0xFF8FD0FF);
    }

    // 小さなひし形 (GUI の座標で。見出しの印)
    public static void gem(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        gem2(g, x * 2, y * 2, 5);
        g.pose().popPose();
    }
}
