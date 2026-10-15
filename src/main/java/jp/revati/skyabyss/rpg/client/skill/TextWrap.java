package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

// 日本語向けの折り返し。バニラの font.split は空白でしか折り返さないので、日本語の長い行では
// 「。」や「、」が行頭に一つだけ落ちたり、「（6」で行が終わったりする。ここでは:
// - 漢字・かなの間ならどこでも折り返せる
// - 行頭に来てはいけない文字 (。、）」％ など) の前、行末に来てはいけない文字 (（「 など) の後では折り返さない
// - 空白は、両側が英数字のとき (英語の文) だけ折り返す所にする。日本語の「攻撃力の 120%」の空白では切らない
// - どこでも折り返せないときは、はみ出す手前で切る
public final class TextWrap {
    private static final String NO_START = "。、，．・：；？！ー）」』】〕〉》％%…‥ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ.,:;!?)]}";
    private static final String NO_END = "（「『【〔〈《([{";

    private TextWrap() {
    }

    public static List<FormattedCharSequence> split(Font font, Component text, int width) {
        List<Integer> cps = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((style, s) -> {
            s.codePoints().forEach(cp -> {
                cps.add(cp);
                styles.add(style);
            });
            return Optional.empty();
        }, Style.EMPTY);
        int n = cps.size();
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            w[i] = font.width(FormattedText.of(new String(Character.toChars(cps.get(i))), styles.get(i)));
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        int start = 0;
        while (start < n) {
            // 行頭の空白は捨てる
            while (start < n && cps.get(start) == ' ') {
                start++;
            }
            if (start >= n) {
                break;
            }
            float used = 0;
            int i = start;
            int newline = -1;
            for (; i < n; i++) {
                if (cps.get(i) == '\n') {
                    newline = i;
                    break;
                }
                if (used + w[i] > width && i > start) {
                    break;
                }
                used += w[i];
            }
            if (newline >= 0) {
                lines.add(line(cps, styles, start, newline));
                start = newline + 1;
                continue;
            }
            if (i >= n) {
                lines.add(line(cps, styles, start, n));
                break;
            }
            // i の文字が入らない: i から前へ、折り返せる所を探す
            int cut = -1;
            for (int b = i; b > start; b--) {
                if (canBreak(cps, b)) {
                    cut = b;
                    break;
                }
            }
            if (cut < 0) {
                cut = i;
            }
            int end = cut;
            while (end > start && cps.get(end - 1) == ' ') {
                end--;
            }
            lines.add(line(cps, styles, start, end));
            start = cut;
        }
        return lines;
    }

    // b の前 (cps[b-1] と cps[b] の間) で折り返せるか
    private static boolean canBreak(List<Integer> cps, int b) {
        int prev = cps.get(b - 1);
        int next = cps.get(b);
        if (prev == ' ' || next == ' ') {
            // 空白の並びの両外側を見る
            int l = b - 1;
            while (l >= 0 && cps.get(l) == ' ') {
                l--;
            }
            int r = b;
            while (r < cps.size() && cps.get(r) == ' ') {
                r++;
            }
            if (next == ' ' && prev != ' ') {
                // 空白の手前では切らない (空白の後で切る)
                return false;
            }
            if (l < 0 || r >= cps.size()) {
                return true;
            }
            return !cjk(cps.get(l)) && !cjk(cps.get(r)) && NO_START.indexOf(cps.get(r)) < 0;
        }
        if (NO_START.indexOf(next) >= 0 || NO_END.indexOf(prev) >= 0) {
            return false;
        }
        return cjk(prev) || cjk(next);
    }

    private static boolean cjk(int cp) {
        return (cp >= 0x3000 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    private static FormattedCharSequence line(List<Integer> cps, List<Style> styles, int from, int to) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int i = from;
        while (i < to) {
            Style st = styles.get(i);
            StringBuilder sb = new StringBuilder();
            while (i < to && styles.get(i).equals(st)) {
                sb.appendCodePoint(cps.get(i));
                i++;
            }
            parts.add(FormattedCharSequence.forward(sb.toString(), st));
        }
        return parts.isEmpty() ? FormattedCharSequence.EMPTY : FormattedCharSequence.composite(parts);
    }
}
