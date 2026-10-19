package jp.revati.skyabyss.rebirth;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

// 転生の飾りの文 (色つきの Component)。名札・チャット・一覧・墓が同じ作りを使う
public final class RebirthText {
    private RebirthText() {
    }

    private static Style colored(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF));
    }

    // 文字ごとに色を替えた文 (プリズム用)。time は ms、still なら止めて 1 枚の並びにする
    public static MutableComponent rainbow(String text, int count, long timeMs, boolean still) {
        MutableComponent out = Component.empty();
        int n = text.codePointCount(0, text.length());
        int i = 0;
        for (int off = 0; off < text.length(); ) {
            int cp = text.codePointAt(off);
            off += Character.charCount(cp);
            float shift = n <= 1 ? 0f : (float) i / n;
            // 止めるときは時間を 0 に固定して文字ごとの色の差だけ残す (色の差があるので色だけに頼らず読める)
            int rgb = RebirthTier.border(count, still ? 0L : timeMs, false, shift * 0.6f);
            out.append(Component.literal(new String(Character.toChars(cp))).withStyle(colored(rgb)));
            i++;
        }
        return out;
    }

    // チャットの接頭辞 「✦3 」 (段の色。プリズムは止めた色の並び。0 回は空)
    public static MutableComponent chatPrefix(int count) {
        String p = RebirthTier.chatPrefix(count);
        if (p.isEmpty()) {
            return Component.empty();
        }
        if (RebirthTier.of(count) == RebirthTier.Tier.PRISM) {
            return rainbow(p, count, 0L, true).append(Component.literal(" "));
        }
        return Component.literal(p + " ").withStyle(colored(RebirthTier.accent(count, 0L, true)));
    }

    // 記号 (束ねたもの) を段の色で
    public static MutableComponent symbols(int count, long timeMs, boolean still) {
        String s = RebirthTier.symbols(count);
        if (s.isEmpty()) {
            return Component.empty();
        }
        if (RebirthTier.of(count) == RebirthTier.Tier.PRISM) {
            return rainbow(s, count, timeMs, still);
        }
        return Component.literal(s).withStyle(colored(RebirthTier.accent(count, timeMs, still)));
    }

    // 「Lv 50+n」を段の色で (0 回は Lv 50 の白)
    public static MutableComponent level(int level, int count, long timeMs, boolean still) {
        String s = RebirthTier.levelLabel(level, count);
        if (RebirthTier.of(count) == RebirthTier.Tier.PRISM) {
            return rainbow(s, count, timeMs, still);
        }
        return Component.literal(s).withStyle(colored(RebirthTier.color(count, timeMs, still)));
    }
}
