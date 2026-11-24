package jp.revati.skyabyss.rpg.client.skill;

import jp.revati.skyabyss.client.ui.Paper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// スキルの木 v2 の部品 (docs/design/56 の「切り絵」。JobChoiceScreen と同じ言葉)。
// SkillScreen は状態と押したときの処理、ここは描くだけ。字の色は ARGB (alpha 0 は Font が不透明に扱うので 6 以上)
public final class SkillStage {
    private SkillStage() {
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    // 減速して止まる
    public static float out(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    // 少し行き過ぎて戻る
    public static float back(float t) {
        t = clamp01(t);
        float c = 1.9f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    // 色に濃さを掛ける (字用)
    public static int fade(int rgb, float a) {
        int al = Math.max(6, Math.min(255, Math.round(255 * clamp01(a))));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    // 2 倍の字 (入らなければ 1 倍)。描いた高さを返す
    public static int big(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int color) {
        int sc = font.width(text) * 2 <= maxW ? 2 : 1;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
        return font.lineHeight * sc;
    }

    // 判子: 少し傾けた桃色の紙に字。press = 0..1 (0 で大きく浮き、1 で押されて止まる)。描いた幅を返す
    public static int stamp(GuiGraphics g, Font font, Component text, int x, int y, float press, boolean pink) {
        int tw = font.width(text) + 10;
        if (press <= 0.05f) {
            return tw;
        }
        float st = back(press);
        g.pose().pushPose();
        g.pose().translate(x + tw / 2f, y + 6, 0);
        float sc = 1f + (1f - st) * 0.5f;
        g.pose().scale(sc, sc, 1f);
        Paper.cut(g, "skst_" + (pink ? "p" : "g") + tw, Paper.quad(-tw / 2f, -6, tw, 12, 1, -1, 1, -1),
                pink ? Paper.PINK : Paper.GREY_LO, pink ? Paper.PINK_LO : Paper.BLACK_LO, 2, 2);
        g.drawString(font, text, -tw / 2 + 5, -4, 0xFF000000 | Paper.ON_BLACK_HI, false);
        g.pose().popPose();
        return tw;
    }

    // 札 (生成りの地の上の小さな札)。dark なら黒い札に生成りの字。描いた幅を返す
    public static int chip(GuiGraphics g, Font font, Component text, int x, int y, boolean dark) {
        int w = font.width(text) + 8;
        int bg = dark ? Paper.BLACK : Paper.CREAM_LO;
        g.fill(x, y, x + w, y + 11, bg);
        g.fill(x + 1, y + 11, x + w + 1, y + 12, dark ? Paper.PINK_LO : 0xFFA89E8E);
        g.drawString(font, text, x + 4, y + 2, dark ? 0xFF000000 | Paper.ON_BLACK : 0xFF000000 | Paper.INK, false);
        return w + 1;
    }

    // 菱の列 (filled 個が桃色)。大きさ r の菱を step ごとに。描いた幅を返す
    public static int pips(GuiGraphics g, int x, int y, int count, int filled, int r, boolean onBlack) {
        int step = 2 * r + 3;
        for (int i = 0; i < count; i++) {
            boolean on = i < filled;
            int off = onBlack ? Paper.EDGE : 0xFF1B2030;
            int offHi = onBlack ? Paper.GREY_LO : 0xFFB3A898;
            Paper.draw(g, "skpip_" + r + (on ? "1" : onBlack ? "b" : "c"), "", x + i * step, y, 2 * r + 1, 2 * r + 1,
                    p -> p.diamond(r, r, r, on ? Paper.PINK : off, on ? Paper.PINK_HI : offHi));
        }
        return count * step;
    }

    // 生成りの紙の札 (説明の欄)。shape は舞台の座標の点。dx だけ右へずらして描く
    // lift だけ下の辺を持ち上げる (中身が短い札)。下の辺の点 (y が 110 より下) を上へずらす
    public static void card(GuiGraphics g, float[] shape, int x, int y, int w, int h, int dx, int lift) {
        // 板の絵柄: 説明の札はまっすぐな浮いた升 (紙の形の外接の四角)
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < shape.length; i += 2) {
            x0 = Math.min(x0, shape[i]);
            x1 = Math.max(x1, shape[i]);
            y0 = Math.min(y0, shape[i + 1]);
            y1 = Math.max(y1, shape[i + 1]);
        }
        int bx = Math.round(x0) + 2 + dx;
        int by = Math.round(y0) + 2;
        jp.revati.skyabyss.client.ui.Plate.tile(g, bx, by, Math.min(Math.round(x1) - 2, 401) + dx - bx, Math.round(y1) - 4 - lift - by,
                jp.revati.skyabyss.client.ui.Plate.REST, jp.revati.skyabyss.client.ui.Plate.GOLD);
    }
}
