package jp.revati.skyabyss.client.ui;

import java.util.Locale;

import net.minecraft.network.chat.Style;

// 斜体の扱い (撮影で三案を見比べるための切り替え。環境変数 SKYABYSS_ITALIC_STYLE を起動時に一度読む)
//   未設定 (既定) : 現在のまま。斜体・帯なし
//   a             : mod の字のパックが入っているとき斜体をやめる (パックが外れていれば斜体のまま)。帯なし
//   b             : 斜体のまま。場所の副題の後ろに暗い帯
//   c             : 斜体をやめる + 場所の副題の後ろに暗い帯
// 斜体は FontItalicMixin が一字ずつ描く直前に外す (Style の斜体を落とす)。字の幅は変わらない
public enum ItalicStyle {
    CURRENT(true, false),
    A(false, false),
    B(true, true),
    C(false, true);

    private static final ItalicStyle VARIANT = parse(System.getenv("SKYABYSS_ITALIC_STYLE"));

    private final boolean keepItalic;
    private final boolean band;

    ItalicStyle(boolean keepItalic, boolean band) {
        this.keepItalic = keepItalic;
        this.band = band;
    }

    private static ItalicStyle parse(String env) {
        if (env == null) {
            return CURRENT;
        }
        return switch (env.trim().toLowerCase(Locale.ROOT)) {
            case "a" -> A;
            case "b" -> B;
            case "c" -> C;
            default -> CURRENT;
        };
    }

    public static ItalicStyle current() {
        return VARIANT;
    }

    // 斜体の文を描くとき、斜体を外すか。mod の字のパックが入っているときだけ外す
    public static Style apply(Style style) {
        if (VARIANT.keepItalic || !style.isItalic() || !ModFont.active()) {
            return style;
        }
        return style.withItalic(false);
    }

    // 場所の副題の後ろに暗い帯を敷くか
    public static boolean band() {
        return VARIANT.band;
    }
}
