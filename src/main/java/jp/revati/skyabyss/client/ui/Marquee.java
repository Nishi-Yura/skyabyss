package jp.revati.skyabyss.client.ui;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 枠に入らない一行を、枠の中でゆっくり横へ流して全部見せる (2026-11-09 の声「UI とかで文の最後が『...』になるときあるけど、
// こちらからしたら何書いてるかわからないので、入る様に書いてほしい」)。前は末尾を「…」で省いていた。
// 頭で PAUSE 秒止まり、SPEED 画素/秒で終わりまで流れ、終わりで PAUSE 秒止まって頭へ戻る。枠の外は切る (今の変換の中の座標で)。
// 入る文はそのまま描く。行の数が決まった欄は、入りきらない行を最後の行へつないで流す (joinRest)
public final class Marquee {
    static final float SPEED = 22f;
    static final float PAUSE = 1.4f;

    private Marquee() {
    }

    // 流す量 (0 〜 overflow 画素)
    static float offset(int overflow) {
        float travel = overflow / SPEED;
        float cycle = 2 * PAUSE + 2 * travel;
        float t = (net.minecraft.Util.getMillis() / 1000f) % cycle;
        if (t < PAUSE) {
            return 0;
        }
        if (t < PAUSE + travel) {
            return (t - PAUSE) * SPEED;
        }
        if (t < 2 * PAUSE + travel) {
            return overflow;
        }
        return overflow - (t - 2 * PAUSE - travel) * SPEED;
    }

    // 描いた幅を返す (入れば文の幅、入らなければ w)
    public static int draw(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int w, int color, boolean shadow) {
        int tw = font.width(text);
        if (tw <= w || w <= 0) {
            g.drawString(font, text, x, y, color, shadow);
            return tw;
        }
        float off = offset(tw - w);
        TextClip.clipPosed(g, x, y - 1, w, font.lineHeight + 2);
        g.pose().pushPose();
        g.pose().translate(-Math.round(off), 0, 0);
        g.drawString(font, text, x, y, color, shadow);
        g.pose().popPose();
        TextClip.unclip(g);
        return w;
    }

    public static int draw(GuiGraphics g, Font font, Component text, int x, int y, int w, int color, boolean shadow) {
        return draw(g, font, text.getVisualOrderText(), x, y, w, color, shadow);
    }

    public static int draw(GuiGraphics g, Font font, String text, int x, int y, int w, int color, boolean shadow) {
        return draw(g, font, Component.literal(text).getVisualOrderText(), x, y, w, color, shadow);
    }

    // maxLines 行に収める: 超えた分は最後の行へつなぐ (描くときは最後の行を流す)
    public static List<FormattedCharSequence> joinRest(List<FormattedCharSequence> all, int maxLines) {
        if (all.size() <= maxLines || maxLines <= 0) {
            return all;
        }
        List<FormattedCharSequence> out = new java.util.ArrayList<>(all.subList(0, maxLines - 1));
        out.add(FormattedCharSequence.composite(all.subList(maxLines - 1, all.size())));
        return out;
    }

    public static List<String> joinRestStrings(List<String> all, int maxLines) {
        if (all.size() <= maxLines || maxLines <= 0) {
            return all;
        }
        List<String> out = new java.util.ArrayList<>(all.subList(0, maxLines - 1));
        out.add(String.join("", all.subList(maxLines - 1, all.size())));
        return out;
    }
}
