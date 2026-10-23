package jp.revati.skyabyss.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

// 文字を箱の中に収める道具。どの画面も、枠の外に字を描かないためにこれを使う (tools/audit/check_text_fit.py と対)。
//   clip / unclip : 矩形 (画面の寸法) の外を切る scissor。パネルの描画を挟む。入れ子にできる
//   fit           : 1 行を幅に収める。入らなければ「…」で切る (書式は残す)
//   line          : 1 行を描く (fit して)
//   wrapped/lines : 折り返しを maxLines 行まで描く。超えたら最後の行を「…」で終える。描いた行数を返す
public final class TextClip {
    private TextClip() {
    }

    public static void clip(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h));
    }

    // 今の変換 (pose) の中の矩形を切る。矩形は変換の前の座標 (例: UiFit の中で描く盤の単位)。変換を通して画面の座標に直す
    public static void clipPosed(GuiGraphics g, int x, int y, int w, int h) {
        org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector3f a = m.transformPosition(new org.joml.Vector3f(x, y, 0f));
        org.joml.Vector3f b = m.transformPosition(new org.joml.Vector3f(x + Math.max(0, w), y + Math.max(0, h), 0f));
        g.enableScissor(Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y));
    }

    public static void unclip(GuiGraphics g) {
        g.disableScissor();
    }

    public static String fit(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxW - font.width("…"))) + "…";
    }

    // maxW に入るところまでで切り、足りなければ末尾に「…」を付ける。reserve: 「…」の分を空けて切るか
    public static FormattedCharSequence fit(Font font, FormattedCharSequence line, int maxW) {
        return cut(font, line, maxW, false);
    }

    private static FormattedCharSequence cut(Font font, FormattedCharSequence line, int maxW, boolean force) {
        if (!force && font.width(line) <= maxW) {
            return line;
        }
        int ell = font.width("…");
        MutableComponent out = Component.empty();
        float[] used = {0};
        boolean[] stop = {false};
        StringBuilder run = new StringBuilder();
        Style[] runStyle = {null};
        line.accept((i, style, cp) -> {
            if (stop[0]) {
                return false;
            }
            String s = new String(Character.toChars(cp));
            float w = font.width(Component.literal(s).withStyle(style));
            if (used[0] + w + ell > maxW) {
                stop[0] = true;
                return false;
            }
            if (runStyle[0] != null && !runStyle[0].equals(style)) {
                out.append(Component.literal(run.toString()).withStyle(runStyle[0]));
                run.setLength(0);
            }
            runStyle[0] = style;
            run.append(s);
            used[0] += w;
            return true;
        });
        if (run.length() > 0) {
            out.append(Component.literal(run.toString()).withStyle(runStyle[0]));
        }
        out.append(Component.literal("…").withStyle(runStyle[0] == null ? Style.EMPTY : runStyle[0]));
        return out.getVisualOrderText();
    }

    public static void line(GuiGraphics g, Font font, String text, int x, int y, int maxW, int color, boolean shadow) {
        g.drawString(font, fit(font, text, maxW), x, y, color, shadow);
    }

    public static void line(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int color, boolean shadow) {
        g.drawString(font, fit(font, text.getVisualOrderText(), maxW), x, y, color, shadow);
    }

    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int w, int maxLines, int lineH, int color, boolean shadow) {
        return lines(g, font, jp.revati.skyabyss.client.ui.JpWrap.split(font, text, w), x, y, w, maxLines, lineH, color, shadow);
    }

    // 折り返した行を maxLines まで。超えた分は捨て、最後の行を「…」で終える。lineH: 行の高さ
    public static int lines(GuiGraphics g, Font font, List<FormattedCharSequence> all, int x, int y, int w, int maxLines, int lineH, int color,
            boolean shadow) {
        List<FormattedCharSequence> use = new ArrayList<>(all.size() > maxLines ? all.subList(0, Math.max(0, maxLines)) : all);
        if (all.size() > maxLines && !use.isEmpty()) {
            use.set(use.size() - 1, cut(font, use.get(use.size() - 1), w, true));
        }
        int yy = y;
        for (FormattedCharSequence l : use) {
            g.drawString(font, fit(font, l, w), x, yy, color, shadow);
            yy += lineH;
        }
        return use.size();
    }

    // 行の数を maxLines に収めた一覧 (描く側が自分で描くとき用)。超えたら最後の行を「…」で終える
    public static List<FormattedCharSequence> limit(Font font, List<FormattedCharSequence> all, int w, int maxLines) {
        if (all.size() <= maxLines) {
            return all;
        }
        List<FormattedCharSequence> use = new ArrayList<>(all.subList(0, Math.max(0, maxLines)));
        if (!use.isEmpty()) {
            use.set(use.size() - 1, cut(font, use.get(use.size() - 1), w, true));
        }
        return use;
    }
}
