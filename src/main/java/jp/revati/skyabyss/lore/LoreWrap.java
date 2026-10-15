package jp.revati.skyabyss.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

// 本文の折り返し。バニラの折り返しは日本語で行頭に「。」や「」」が来てしまうので、
// 行頭に来てはいけない字 (句読点・閉じかっこ・小さい仮名など) は、前の行の最後の一字と一緒に次の行へ送る (追い出し)。
// 英語は単語の切れ目で折り返す。改行 (\n) は段落の区切りとしてそのまま使い、空行は空の行として返す
public final class LoreWrap {
    private static final String NO_START = "、。，．,.・：；:;？！?!」』）)】〉》ー〜…‥ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ々";
    private static final String NO_END = "「『（(【〈《";

    private LoreWrap() {
    }

    public static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            wrapParagraph(paragraph, width, measure, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, int width, ToIntFunction<String> measure, List<String> out) {
        List<String> tokens = tokens(paragraph);
        StringBuilder line = new StringBuilder();
        for (String token : tokens) {
            String candidate = line + token;
            if (measure.applyAsInt(candidate.stripTrailing()) <= width) {
                line.append(token);
                continue;
            }
            if (line.length() == 0) {
                // 一語で幅を超える: 字で割る
                for (char c : token.toCharArray()) {
                    if (line.length() > 0 && measure.applyAsInt(line.toString() + c) > width) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(c);
                }
                continue;
            }
            String carry = token.stripLeading();
            // 行頭禁則: 次の行の頭が句読点などなら、この行の最後の字も一緒に送る。
            // 行末禁則: この行の最後が開きかっこなら、それも送る (どちらも 3 字まで)
            for (int k = 0; k < 3 && line.length() > 1; k++) {
                char last = line.charAt(line.length() - 1);
                boolean push = startsWithAny(carry, NO_START) || NO_END.indexOf(last) >= 0;
                if (!push || Character.isWhitespace(last)) {
                    break;
                }
                carry = last + carry;
                line.setLength(line.length() - 1);
            }
            out.add(line.toString().stripTrailing());
            line.setLength(0);
            line.append(carry);
        }
        if (line.length() > 0) {
            out.add(line.toString().stripTrailing());
        }
    }

    // 英数字の語 (後ろの空白を含む) はひとかたまり、それ以外 (かな・漢字・記号) は一字ずつ
    private static List<String> tokens(String s) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (isWordChar(c)) {
                int j = i;
                while (j < s.length() && isWordChar(s.charAt(j))) {
                    j++;
                }
                while (j < s.length() && s.charAt(j) == ' ') {
                    j++;
                }
                tokens.add(s.substring(i, j));
                i = j;
            } else {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) == ' ') {
                    j++;
                }
                tokens.add(s.substring(i, j));
                i = j;
            }
        }
        return tokens;
    }

    private static boolean isWordChar(char c) {
        return c < 0x2000 && c != ' ' && c != '\n';
    }

    private static boolean startsWithAny(String s, String chars) {
        return !s.isEmpty() && chars.indexOf(s.charAt(0)) >= 0;
    }
}
