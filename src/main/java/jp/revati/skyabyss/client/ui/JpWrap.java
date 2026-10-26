package jp.revati.skyabyss.client.ui;

import java.util.List;

import jp.revati.skyabyss.rpg.client.skill.TextWrap;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

// 書式つきの文を、日本語の切れ目 (util/JpBreak: 文節の切れ目と禁則) で折り返す (font.split の代わり)。中身は TextWrap と同じ
public final class JpWrap {
    private JpWrap() {
    }

    public static List<FormattedCharSequence> split(Font font, FormattedText text, int width) {
        return TextWrap.split(font, text, width);
    }
}
