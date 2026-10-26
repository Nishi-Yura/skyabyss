package jp.revati.skyabyss.util;

// 日本語の折り返しの切れ目 (TextWrap・LoreWrap が使う)。どこで切ってよいかを、字と字の間ごとに点数で返す:
//   GOOD  = 文節の切れ目 (「大鐘楼の｜語り部を｜訪ねよう」)。助詞・送りがなの後で、次が漢字・カタカナ・英数字・開きかっこ。句読点の後
//   OK    = 字の間 (禁則は守る)。GOOD が行の中に無いときだけ使う
//   NEVER = 切らない (行頭禁則の字の前、行末禁則の字の後、英単語・数・カタカナ語・漢字の熟語の中、空白の手前)
// 前の版は漢字・かなの間ならどこでも切ったので「訪ね｜よう」「攻撃｜力」のような気持ちの悪い改行が出ていた (2026-10-26 の声)
public final class JpBreak {
    public static final int NEVER = 0;
    public static final int OK = 1;
    public static final int GOOD = 2;

    static final String NO_START = "、。，．,.・：；:;？！?!」』）)】〕〉》ー〜～…‥％%ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ々゛゜";
    static final String NO_END = "「『（(【〔〈《［[";
    // 一字で文節を閉じる助詞 (「敵の｜狙い」「石を｜抱く」)。それ以外の一字のひらがな (送りがな「語り部」の「り」) の後では切らない
    static final String PARTICLES = "のをにはがでとへもや";
    // 文の区切り (この後はいつでもよい切れ目)
    static final String CLOSERS = "、。，．！？!?」』）)】〕〉》…";

    private JpBreak() {
    }

    enum Kind {
        KANJI, HIRA, KATA, WORD, SPACE, OPEN, CLOSE, OTHER
    }

    static Kind kind(int cp) {
        if (cp == ' ' || cp == '　') {
            return Kind.SPACE;
        }
        if (NO_END.indexOf(cp) >= 0) {
            return Kind.OPEN;
        }
        if (CLOSERS.indexOf(cp) >= 0 || NO_START.indexOf(cp) >= 0 && cp != 'ー' && !(cp >= 0x3041 && cp <= 0x30FF)) {
            return Kind.CLOSE;
        }
        if (cp == 'ー' || cp >= 0x30A0 && cp <= 0x30FF || cp >= 0xFF66 && cp <= 0xFF9F) {
            return Kind.KATA;
        }
        if (cp >= 0x3040 && cp <= 0x309F) {
            return Kind.HIRA;
        }
        if (cp == '々' || cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0x3400 && cp <= 0x4DBF || cp >= 0xF900 && cp <= 0xFAFF) {
            return Kind.KANJI;
        }
        if (cp < 0x2000 || cp >= 0xFF10 && cp <= 0xFF5A) {
            return Kind.WORD;
        }
        return Kind.OTHER;
    }

    static boolean cjk(Kind k) {
        return k == Kind.KANJI || k == Kind.HIRA || k == Kind.KATA || k == Kind.OTHER;
    }

    // score[b] = cps[b-1] と cps[b] の間の点数 (b = 1..n-1。0 と n は使わない)
    public static int[] scores(int[] cps) {
        int n = cps.length;
        int[] score = new int[n + 1];
        Kind[] k = new Kind[n];
        for (int i = 0; i < n; i++) {
            k[i] = kind(cps[i]);
        }
        for (int b = 1; b < n; b++) {
            int prev = cps[b - 1];
            int next = cps[b];
            Kind pk = k[b - 1];
            Kind nk = k[b];
            if (NO_START.indexOf(next) >= 0 || NO_END.indexOf(prev) >= 0) {
                score[b] = NEVER;
                continue;
            }
            if (pk == Kind.SPACE || nk == Kind.SPACE) {
                // 空白は後ろで切る。日本語の「攻撃力の 120%」の空白では切らず、英語の語の間 (両側が英数字) だけ
                if (nk == Kind.SPACE) {
                    score[b] = NEVER;
                    continue;
                }
                int l = b - 1;
                while (l >= 0 && k[l] == Kind.SPACE) {
                    l--;
                }
                if (l < 0) {
                    score[b] = NEVER;
                } else if (k[l] == Kind.WORD && nk == Kind.WORD) {
                    score[b] = GOOD;
                } else if (k[l] == Kind.HIRA || k[l] == Kind.CLOSE) {
                    // 「Lv 15 まで の」などの、ひらがな・句読点の後の空白
                    score[b] = GOOD;
                } else {
                    score[b] = OK;
                }
                continue;
            }
            if (pk == Kind.WORD && nk == Kind.WORD) {
                score[b] = NEVER;
                continue;
            }
            if (pk == Kind.CLOSE) {
                score[b] = GOOD;
                continue;
            }
            if (nk == Kind.OPEN) {
                score[b] = pk == Kind.HIRA || pk == Kind.CLOSE ? GOOD : OK;
                continue;
            }
            if (pk == Kind.HIRA && (nk == Kind.KANJI || nk == Kind.KATA || nk == Kind.WORD || nk == Kind.OTHER)) {
                // ひらがなの塊の後: 塊が一字なら助詞のときだけ (送りがなの「語り｜部」では切らない)
                int run = 1;
                while (b - 1 - run >= 0 && k[b - 1 - run] == Kind.HIRA) {
                    run++;
                }
                boolean afterKanji = b - 1 - run >= 0 && k[b - 1 - run] == Kind.KANJI;
                if (run == 1 && afterKanji && PARTICLES.indexOf(prev) < 0) {
                    score[b] = OK;
                } else {
                    score[b] = GOOD;
                }
                continue;
            }
            if (pk == Kind.KATA && nk == Kind.KANJI || pk == Kind.KANJI && nk == Kind.KATA) {
                // カタカナ語と漢字の境 (「ブロック｜以内」「炎の｜マナ」)
                score[b] = GOOD;
                continue;
            }
            if (cjk(pk) || cjk(nk)) {
                // 漢字の熟語・カタカナ語・ひらがなの中・漢字とかなの境 (送りがな) は、行に GOOD が無いときだけ
                score[b] = OK;
                continue;
            }
            score[b] = OK;
        }
        return score;
    }

    // 行に分ける。返すのは [始め, 終わり) の組 (行頭の空白は捨てる・行末の空白は落とす)。\n は段落の区切り (空行も一行)
    public static java.util.List<int[]> lines(int[] cps, float[] widths, float width) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int n = cps.length;
        if (n == 0) {
            return out;
        }
        int[] score = scores(cps);
        int start = 0;
        while (start <= n) {
            int para = start;
            while (para < n && cps[para] != '\n') {
                para++;
            }
            // start..para が一つの段落
            int s = start;
            if (s == para) {
                out.add(new int[] {s, s});
            }
            while (s < para) {
                while (s < para && kind(cps[s]) == Kind.SPACE) {
                    s++;
                }
                if (s >= para) {
                    break;
                }
                float used = 0;
                int i = s;
                while (i < para && (used + widths[i] <= width || i == s)) {
                    used += widths[i];
                    i++;
                }
                // 入らない最初の字が空白なら、そこで切ればよい (ちょうど埋まった行)
                int cut = i >= para ? para : kind(cps[i]) == Kind.SPACE ? i : choose(score, widths, s, i, width);
                int e = cut;
                while (e > s && kind(cps[e - 1]) == Kind.SPACE) {
                    e--;
                }
                out.add(new int[] {s, e});
                s = cut;
            }
            if (para >= n) {
                break;
            }
            start = para + 1;
        }
        return out;
    }

    // 幅に入る最後の字 (end。cps[start..end) が入る) から、切る所を選ぶ。行の幅の minFill 以上が埋まる GOOD を優先し、無ければ OK、
    // それも無ければ end (字の途中で切る)
    public static int choose(int[] score, float[] widths, int start, int end, float width) {
        float minFill = width * 0.45f;
        float used = 0;
        float[] at = new float[end - start + 1];
        for (int i = start; i < end; i++) {
            used += widths[i];
            at[i - start + 1] = used;
        }
        for (int b = end; b > start; b--) {
            if (score[b] == GOOD && at[b - start] >= minFill) {
                return b;
            }
        }
        for (int b = end; b > start; b--) {
            if (score[b] >= OK) {
                return b;
            }
        }
        return end;
    }
}
