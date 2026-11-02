package jp.revati.skyabyss.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import jp.revati.skyabyss.util.JpBreak;

// 本文の折り返し (字の幅は measure で一字ずつ測る)。切れ目は util/JpBreak: 文節の切れ目を先に使い、禁則を守る。英語は語の間。
// 改行 (\n) は段落の区切りとしてそのまま使い、空行は空の行として返す
public final class LoreWrap {
    private LoreWrap() {
    }

    public static List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        int[] cps = text.codePoints().toArray();
        float[] w = new float[cps.length];
        for (int i = 0; i < cps.length; i++) {
            w[i] = cps[i] == '\n' ? 0 : measure.applyAsInt(new String(Character.toChars(cps[i])));
        }
        List<String> lines = new ArrayList<>();
        if (cps.length == 0) {
            lines.add("");
            return lines;
        }
        for (int[] r : JpBreak.lines(cps, w, Math.max(1, width))) {
            lines.add(new String(cps, r[0], r[1] - r[0]));
        }
        return lines;
    }
}
