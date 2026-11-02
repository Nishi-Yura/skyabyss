package jp.revati.skyabyss.util;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;

// 日本語の折り返しの切れ目 (JpBreak) の検査。SKYABYSS_SELFTEST_ONLY="text wrap"。字の幅は全角 9・半角 5 として、
// 実際の文で「言葉の途中で切れない」「行頭に句読点が来ない」「英語は語の間」を見る (2026-10-26 の声「改行の位置が気持ち悪い」)
public final class JpBreakSelfTest {
    private JpBreakSelfTest() {
    }

    static List<String> wrap(String s, int width) {
        int[] cps = s.codePoints().toArray();
        float[] w = new float[cps.length];
        for (int i = 0; i < cps.length; i++) {
            w[i] = cps[i] < 0x2000 ? 5 : 9;
        }
        List<String> out = new ArrayList<>();
        for (int[] r : JpBreak.lines(cps, w, width)) {
            out.add(new String(cps, r[0], r[1] - r[0]));
        }
        return out;
    }

    static void expect(List<String> f, String s, int width, String... lines) {
        List<String> got = wrap(s, width);
        if (!got.equals(List.of(lines))) {
            f.add("\"" + s + "\" at " + width + " -> " + got + " (want " + List.of(lines) + ")");
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        // 文節で切る (前の版は「訪ね｜よう」)
        expect(f, "大鐘楼の語り部を訪ねよう", 9 * 10, "大鐘楼の語り部を", "訪ねよう");
        // 送りがなの中 (「語り｜部」) では切らない
        expect(f, "大鐘楼の語り部を訪ねよう", 9 * 6, "大鐘楼の", "語り部を", "訪ねよう");
        // 句読点は行頭に来ない
        expect(f, "敵の狙いを引き、仲間の盾になる。壁の役目。", 9 * 8, "敵の狙いを引き、", "仲間の盾になる。", "壁の役目。");
        // 数と単位はひとかたまり。カタカナ語と漢字の境 (「ブロック｜以内」) で切る
        expect(f, "見ている先 5 ブロック以内の敵へ攻撃力の 140% で斬る", 9 * 12, "見ている先 5 ブロック", "以内の敵へ攻撃力の 140%", "で斬る");
        // 英語は語の間
        expect(f, "Close in on a foe you look at within 5 blocks", 5 * 20, "Close in on a foe", "you look at within 5", "blocks");
        // 改行はそのまま
        expect(f, "一行目\n二行目", 9 * 20, "一行目", "二行目");
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST text wrap PASSED (phrase breaks, okurigana kept, no punctuation at line start, numbers and units together, English by words, newlines)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST text wrap FAILED: {}", x));
        }
    }
}
