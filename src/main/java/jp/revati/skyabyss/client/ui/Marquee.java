package jp.revati.skyabyss.client.ui;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 枠に入らない一行を、枠の中に縮めて全部見せる (2026-11-09 の声「文の最後が『...』になると、何書いてるかわからない」→ 流していたが、
// 2026-11-10 の声「動いてるけど気持ち悪い」で縮める形に)。入る文はそのまま描く。行の数が決まった欄は、入りきらない行を最後の行へつなぐ (joinRest)
public final class Marquee {

    private Marquee() {
    }

    // 入らない文は、流さずに縮めて枠に収める (2026-11-10 の声「はいりきらない文うごいてるけど、気持ち悪い。動かなくても入るように」)。
    // まず全体を MIN_SCALE まで小さくし (縦は枠の真ん中に)、それでも入らなければ横だけ詰める。
    // 横を SQUASH_WARN より詰めた文は、開発の起動でログに出す (文を短くする手がかり)
    static final float MIN_SCALE = 0.7f;
    static final float SQUASH_WARN = 0.8f;
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    // 描いた幅を返す (入れば文の幅、入らなければ w)
    public static int draw(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int w, int color, boolean shadow) {
        int tw = font.width(text);
        if (tw <= w || w <= 0) {
            g.drawString(font, text, x, y, color, shadow);
            return tw;
        }
        float s = Math.max(MIN_SCALE, (float) w / tw);
        float sx = Math.min(1f, w / (tw * s));
        if (sx < SQUASH_WARN && !net.neoforged.fml.loading.FMLEnvironment.production) {
            StringBuilder sb = new StringBuilder();
            text.accept((i, st, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            if (WARNED.add(sb.toString())) {
                jp.revati.skyabyss.SkyAbyss.LOGGER.info("[marquee] squashed to {}% to fit {} px: {}", Math.round(s * sx * 100), w, sb);
            }
        }
        g.pose().pushPose();
        g.pose().translate(x, y + font.lineHeight * (1 - s) / 2f, 0);
        g.pose().scale(s * sx, s, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
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
