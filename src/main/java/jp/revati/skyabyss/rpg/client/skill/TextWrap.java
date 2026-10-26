package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jp.revati.skyabyss.util.JpBreak;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

// 日本語向けの折り返し (字ごとの書式を保つ)。切れ目は util/JpBreak: 文節の切れ目 (「大鐘楼の｜語り部を｜訪ねよう」) を先に使い、
// 無いときだけ字の間 (禁則は守る)。英語は語の間。バニラの font.split は空白でしか折り返さないので使わない
public final class TextWrap {
    private TextWrap() {
    }

    public static List<FormattedCharSequence> split(Font font, Component text, int width) {
        return split(font, (FormattedText) text, width);
    }

    public static List<FormattedCharSequence> split(Font font, FormattedText text, int width) {
        List<Integer> cpl = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((style, s) -> {
            s.codePoints().forEach(cp -> {
                cpl.add(cp);
                styles.add(style);
            });
            return Optional.empty();
        }, Style.EMPTY);
        int n = cpl.size();
        int[] cps = new int[n];
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            cps[i] = cpl.get(i);
            w[i] = font.width(FormattedText.of(new String(Character.toChars(cps[i])), styles.get(i)));
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int[] r : JpBreak.lines(cps, w, Math.max(1, width))) {
            lines.add(line(cps, styles, r[0], r[1]));
        }
        return lines;
    }

    private static FormattedCharSequence line(int[] cps, List<Style> styles, int from, int to) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int i = from;
        while (i < to) {
            Style st = styles.get(i);
            StringBuilder sb = new StringBuilder();
            while (i < to && styles.get(i).equals(st)) {
                sb.appendCodePoint(cps[i]);
                i++;
            }
            parts.add(FormattedCharSequence.forward(sb.toString(), st));
        }
        return parts.isEmpty() ? FormattedCharSequence.EMPTY : FormattedCharSequence.composite(parts);
    }
}
