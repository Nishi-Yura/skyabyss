package jp.revati.skyabyss.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import jp.revati.skyabyss.lore.LoreWrap;

// 書式つきの文を、日本語の禁則を守って折り返す (font.split の代わり)。行の切れ目は LoreWrap で決め、字ごとの書式 (色・字体) を付け直す。
// 幅は文の頭の字の書式で測る (一つの文の中で字体や太字が混ざることはほぼ無い)。行と元の文が合わなければバニラの折り返しに戻す
public final class JpWrap {
    private JpWrap() {
    }

    public static List<FormattedCharSequence> split(Font font, FormattedText text, int width) {
        StringBuilder plain = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        text.visit((style, s) -> {
            plain.append(s);
            for (int i = 0; i < s.length(); i++) {
                styles.add(style);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (plain.length() == 0) {
            return font.split(text, width);
        }
        Style base = styles.get(0);
        List<String> lines = LoreWrap.wrap(plain.toString(), Math.max(1, width), s -> font.width(Component.literal(s).withStyle(base)));
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        String all = plain.toString();
        int at = 0;
        for (String line : lines) {
            if (line.isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
                continue;
            }
            int pos = all.indexOf(line, at);
            if (pos < 0) {
                return font.split(text, width);
            }
            MutableComponent row = Component.empty();
            int i = pos;
            int end = pos + line.length();
            while (i < end) {
                Style st = styles.get(i);
                int j = i + 1;
                while (j < end && styles.get(j).equals(st)) {
                    j++;
                }
                row.append(Component.literal(all.substring(i, j)).withStyle(st));
                i = j;
            }
            out.add(row.getVisualOrderText());
            at = end;
        }
        return out;
    }
}
