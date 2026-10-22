package jp.revati.skyabyss.client.guide;

import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;

// ドット絵の矢印 (帯・追跡中のクエストの札が使う。目的の標 ObjectiveMarker と同じ金色)。11×11 の「上向き」の形を、向きの角度だけ回して描く。
// 色だけに頼らない: 形 (矢じり + 軸) と暗い縁取りで読む
public final class GuideArrow {
    public static final int SIZE = 11;
    // 目的の標 (ObjectiveMarker) と同じ明るい金
    private static final int GOLD = 0xFFD866;
    // 上向き (11 列 × 11 行)。# = 塗る
    private static final String[] SHAPE = {
            ".....#.....",
            "....###....",
            "...#####...",
            "..#######..",
            ".#########.",
            "....###....",
            "....###....",
            "....###....",
            "....###....",
            "....###....",
            "....###....",
    };

    private GuideArrow() {
    }

    // 中心 (cx, cy)、deg = 時計回りの角度 (0 = 真上)。alpha 0..1
    public static void draw(GuiGraphics g, float cx, float cy, float deg, float alpha) {
        // 塗りだけの描きを一度に送る (fill ごとに描き出さない。出る絵は同じ)
        g.drawManaged(() -> {
            int a = (int) (255 * alpha);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
            int half = SIZE / 2;
            // 縁取り (暗い影を 1 ドットずらして)
            int shadow = ((int) (a * 0.75f)) << 24 | 0x05060A;
            paint(g, -half, -half, 1, 1, shadow);
            paint(g, -half, -half, -1, -1, shadow);
            paint(g, -half, -half, 1, -1, shadow);
            paint(g, -half, -half, -1, 1, shadow);
            paint(g, -half, -half, 0, 0, a << 24 | GOLD);
            g.pose().popPose();
        });
    }

    // SHAPE の '#' の横の連なり {行, 始まり, 終わり (含まない)}。一度だけ求め、1 回の paint は連なりごとの fill にする (一回の中では重ならないので出る絵は同じ)
    private static final int[] RUNS = runs();

    private static int[] runs() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int row = 0; row < SHAPE.length; row++) {
            String line = SHAPE[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) == '#') {
                    int start = col;
                    while (col < line.length() && line.charAt(col) == '#') {
                        col++;
                    }
                    out.add(row);
                    out.add(start);
                    out.add(col);
                } else {
                    col++;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void paint(GuiGraphics g, int ox, int oy, int dx, int dy, int argb) {
        for (int i = 0; i < RUNS.length; i += 3) {
            int y = oy + RUNS[i] + dy;
            g.fill(ox + RUNS[i + 1] + dx, y, ox + RUNS[i + 2] + dx, y + 1, argb);
        }
    }

    // 上下の印 (小さな三角。5×3)。(cx, cy) が中心
    public static void drawVertical(GuiGraphics g, int cx, int cy, boolean up, float alpha) {
        // 塗りだけの描きを一度に送る (fill ごとに描き出さない。出る絵は同じ)
        g.drawManaged(() -> {
            int a = (int) (255 * alpha);
            int color = a << 24 | GOLD;
            int shadow = ((int) (a * 0.75f)) << 24 | 0x05060A;
            for (int r = 0; r < 3; r++) {
                int width = up ? 1 + r * 2 : 5 - r * 2;
                int y = cy - 1 + r;
                int x = cx - width / 2;
                g.fill(x - 1, y - 1, x + width + 1, y + 2, shadow);
            }
            for (int r = 0; r < 3; r++) {
                int width = up ? 1 + r * 2 : 5 - r * 2;
                int y = cy - 1 + r;
                g.fill(cx - width / 2, y, cx - width / 2 + width, y + 1, color);
            }
        });
    }
}
