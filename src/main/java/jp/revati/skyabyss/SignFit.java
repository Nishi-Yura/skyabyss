package jp.revati.skyabyss;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// 看板の各行が、実際に描かれる幅に収まるかを測る。バニラの看板は 1 行 90 px (SignBlockEntity の最大幅) を超えると、はみ出た分が描かれない。
// サーバーには Font がないので、バニラの字の幅の表で測る (自己テスト用)。クライアントでは本物の Font.width でも確かめる (SignFitClient)。
//  - ASCII: バニラの ascii.png の字の幅 + 1 (字の間)。空白は 4
//  - かな・漢字・全角記号: unifont の幅の上書き (left 0, right 15) で、どれも 9
//  - そのほかの字: 9 とみなす (unifont の半角は 9 より狭いので、多めに見積もる)
// 看板の文は lang の "<key>.1" 〜 "<key>.4"。日本語と英語の両方を確かめる
public final class SignFit {
    public static final int MAX_LINE_WIDTH = 90;
    public static final List<String> LANGUAGES = List.of("ja_jp", "en_us");
    // '!' (33) 〜 '~' (126) の幅。バニラの ascii.png (1.21.1) の字を数えた値
    private static final String ASCII_WIDTHS =
            "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    private SignFit() {
    }

    // 字の幅の表で見積もった、描かれる幅 (px)
    public static int approxWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') {
                w += 4;
            } else if (cp > 32 && cp < 127) {
                w += ASCII_WIDTHS.charAt(cp - 33) - '0';
            } else {
                w += 9;
            }
        }
        return w;
    }

    // keys は看板のキー ("sign.skyabyss.xxx"、末尾の .1〜.4 を除く)。はみ出す行と、文のない看板を返す
    public static List<String> check(Collection<String> keys) {
        return check(keys, SignFit::approxWidth);
    }

    public static List<String> check(Collection<String> keys, ToIntFunction<String> width) {
        List<String> failures = new ArrayList<>();
        for (String language : LANGUAGES) {
            Map<String, String> lang = lang(language);
            for (String key : keys) {
                if (!lang.containsKey(key + ".1")) {
                    failures.add("sign " + key + " has no text in " + language);
                    continue;
                }
                for (int i = 1; i <= 4; i++) {
                    String line = lang.getOrDefault(key + "." + i, "");
                    int w = width.applyAsInt(line);
                    if (w > MAX_LINE_WIDTH) {
                        failures.add("sign " + key + "." + i + " (" + language + ") is " + w + " px wide (max " + MAX_LINE_WIDTH + "): " + line);
                    }
                }
            }
        }
        return failures;
    }

    // lang にある看板のキーすべて ("sign.skyabyss." で始まり ".1" で終わるもの)
    public static List<String> allSignKeys() {
        List<String> keys = new ArrayList<>();
        for (String k : lang("en_us").keySet()) {
            if (k.startsWith("sign." + SkyAbyss.MODID + ".") && k.endsWith(".1")) {
                keys.add(k.substring(0, k.length() - 2));
            }
        }
        return keys;
    }

    // Mod の lang ファイルを、言語の設定に関わらずそのまま読む (サーバーでも両方の言語を確かめられるように)
    public static synchronized Map<String, String> lang(String language) {
        return CACHE.computeIfAbsent(language, l -> {
            Map<String, String> map = new HashMap<>();
            String path = "/assets/" + SkyAbyss.MODID + "/lang/" + l + ".json";
            try (InputStream in = SignFit.class.getResourceAsStream(path)) {
                if (in == null) {
                    SkyAbyss.LOGGER.error("Missing lang file {}", path);
                    return map;
                }
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.error("Could not read lang file {}", path, e);
            }
            return map;
        });
    }
}
