package jp.revati.skyabyss;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

// lang の自己テスト (brushup T-1)。mod の assets/<名前空間>/lang/{ja_jp,en_us}.json すべてについて:
//  (1) 同じキーが二つの名前空間で、違う値のまま定義されていない (どちらが勝つかが読み込み順しだいになる)。
//      同じ名前空間のファイルの中で、同じキーが二度書かれていない
//  (2) ja と en のキーの集合が一致する (名前空間ごと・全体)
//  (3) 同じキーの %s / %d の数が ja と en で一致する (位置指定 %1$s も一つに数える。%% は数えない)
//      と、Java の MessageFormat 形式の {0} が残っていない (Minecraft は %s。{0} は文字のまま画面に出る。first_hour.txt の通しで 2 件見つかった)
//  (4) コード (src/main/java) と データ (src/main/resources/data) の文字列リテラルにある翻訳キーが、ja・en の両方にある
//      (開発環境で、ソースが見えるときだけ。tools/audit/check_lang_keys.py と同じ規則)。
//      クエストの題・要約・完了・各段の日誌・各目的のキーも (これは jar だけでも見える)
public final class LangSelfTest {
    private static final Pattern SPEC = Pattern.compile("%(?:\\d+\\$)?[sd]");
    // (4) の許可リスト (偽陽性)。tools/audit/check_lang_keys.py の ALLOW と同じにする
    private static final java.util.Set<String> ALLOW = java.util.Set.of(
            // StormChapterSelfTest: startsWith の頭 (StormSites.tabletKey(index) が実キーを作る。実キーは lang にある)
            "sign.skyabyss.ch3_wind_",
            // OpeningStorySelfTest: 「この生キーはもう無い」ことを確かめる否定の検査
            "opening.skyabyss.hint_move");
    // <種類>.skyabyss[_名前空間].<名前> の文字列リテラル。続けて + があれば「頭だけのキー」(あとで番号などを足す)
    private static final Pattern CODE_KEY = Pattern.compile("\"([a-z][A-Za-z]*\\.skyabyss(?:_[a-z0-9_]+)?\\.[A-Za-z0-9_.]*[A-Za-z0-9_])\"(\\s*\\+)?");
    private static final Pattern BRACE_ARG = Pattern.compile("\\{\\d+\\}");
    private static final Pattern KEY_LINE = Pattern.compile("^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:");

    private LangSelfTest() {
    }

    // namespace -> (locale -> (key -> value))
    private static Map<String, Map<String, Map<String, String>>> load(List<String> f, Map<String, List<String>> inFileDuplicates) throws IOException {
        Map<String, Map<String, Map<String, String>>> all = new TreeMap<>();
        Path assets = ModList.get().getModFileById(SkyAbyss.MODID).getFile().findResource("assets");
        List<Path> namespaces;
        try (Stream<Path> s = Files.list(assets)) {
            namespaces = s.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList();
        }
        for (Path ns : namespaces) {
            String name = ns.getFileName().toString().replace("/", "");
            if (all.containsKey(name)) {
                continue;
            }
            for (String locale : List.of("ja_jp", "en_us")) {
                Path file = ns.resolve("lang").resolve(locale + ".json");
                if (!Files.exists(file)) {
                    continue;
                }
                Map<String, String> map = new TreeMap<>();
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement e = JsonParser.parseReader(r);
                    for (var en : e.getAsJsonObject().entrySet()) {
                        map.put(en.getKey(), en.getValue().getAsString());
                    }
                } catch (RuntimeException ex) {
                    f.add(name + "/" + locale + " does not parse: " + ex);
                    continue;
                }
                // 同じファイルの中の二度書き (パース後は消えるので、行で数える)
                List<String> seen = new ArrayList<>();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Matcher m = KEY_LINE.matcher(line);
                    if (m.find()) {
                        if (seen.contains(m.group(1))) {
                            inFileDuplicates.computeIfAbsent(name + "/" + locale, k -> new ArrayList<>()).add(m.group(1));
                        } else {
                            seen.add(m.group(1));
                        }
                    }
                }
                all.computeIfAbsent(name, k -> new TreeMap<>()).put(locale, map);
            }
        }
        return all;
    }

    // 全名前空間をまとめた ja_jp / en_us の表 (他の自己テストが文を引くのに使う)
    public static Map<String, String> table(String locale) throws IOException {
        Map<String, Map<String, Map<String, String>>> all = load(new ArrayList<>(), new TreeMap<>());
        Map<String, String> out = new TreeMap<>();
        all.values().forEach(m -> {
            if (m.get(locale) != null) {
                out.putAll(m.get(locale));
            }
        });
        return out;
    }

    // ソースの木 (開発環境の run/ から上へ辿る)。見えなければ null (jar だけで動かしているとき)
    static Path sourceRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && p != null; i++, p = p.getParent()) {
            if (Files.isDirectory(p.resolve("src/main/java/jp/revati/skyabyss"))) {
                return p;
            }
        }
        return null;
    }

    private static boolean exists(String key, boolean prefix, java.util.Collection<String> names) {
        if (prefix) {
            return names.stream().anyMatch(n -> n.startsWith(key));
        }
        return names.contains(key) || names.contains(key + ".1"); // 看板は key.1 ... key.4
    }

    // (4) コード・データの中のキー。見つからなければ failures へ。数を返す (ソースが見えなければ -1)
    static int checkCodeKeys(Map<String, String> ja, Map<String, String> en, List<String> f) throws IOException {
        Path root = sourceRoot();
        if (root == null) {
            return -1;
        }
        Map<String, String> found = new TreeMap<>(); // "キー|prefix" -> 最初の場所
        for (String dir : List.of("src/main/java", "src/main/resources/data")) {
            boolean java = dir.endsWith("java");
            try (Stream<Path> w = Files.walk(root.resolve(dir))) {
                for (Path file : w.filter(x -> x.toString().endsWith(java ? ".java" : ".json")).toList()) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String t = lines.get(i).strip();
                        if (java && (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))) {
                            continue;
                        }
                        Matcher m = CODE_KEY.matcher(lines.get(i));
                        while (m.find()) {
                            found.putIfAbsent(m.group(1) + "|" + (m.group(2) != null && java), root.relativize(file) + ":" + (i + 1));
                        }
                    }
                }
            }
        }
        for (var e : found.entrySet()) {
            String key = e.getKey().substring(0, e.getKey().indexOf('|'));
            boolean prefix = e.getKey().endsWith("|true");
            if (ALLOW.contains(key)) {
                continue;
            }
            for (var loc : List.of(Map.entry("ja_jp", ja), Map.entry("en_us", en))) {
                if (!exists(key, prefix, loc.getValue().keySet())) {
                    f.add("the key " + key + (prefix ? "..." : "") + " used at " + e.getValue() + " is missing from " + loc.getKey());
                }
            }
        }
        return found.size();
    }

    static int specs(String s) {
        Matcher m = SPEC.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            Map<String, List<String>> inFile = new TreeMap<>();
            Map<String, Map<String, Map<String, String>>> all = load(f, inFile);
            inFile.forEach((file, keys) -> f.add("duplicated key inside " + file + ": " + keys));
            if (all.size() < 10) {
                f.add("only " + all.size() + " lang namespaces were found (the test cannot see the assets)");
            }
            for (String locale : List.of("ja_jp", "en_us")) {
                // (1) 名前空間をまたいだ重複
                Map<String, Map<String, String>> byKey = new TreeMap<>();
                all.forEach((ns, perLocale) -> {
                    Map<String, String> m = perLocale.get(locale);
                    if (m != null) {
                        m.forEach((k, v) -> byKey.computeIfAbsent(k, x -> new TreeMap<>()).put(ns, v));
                    }
                });
                byKey.forEach((k, perNs) -> {
                    if (perNs.size() > 1 && perNs.values().stream().distinct().count() > 1) {
                        f.add("the key " + k + " is defined in several namespaces with different " + locale + " values: " + perNs);
                    }
                });
            }
            // (2) (3) ja / en の一致
            for (var e : all.entrySet()) {
                Map<String, String> ja = e.getValue().get("ja_jp");
                Map<String, String> en = e.getValue().get("en_us");
                if (ja == null || en == null) {
                    f.add("namespace " + e.getKey() + " has only one of ja_jp / en_us");
                    continue;
                }
                for (var loc : List.of(Map.entry("ja_jp", ja), Map.entry("en_us", en))) {
                    loc.getValue().forEach((k, v) -> {
                        if (BRACE_ARG.matcher(v).find()) {
                            f.add(e.getKey() + ": " + k + " has a {0}-style placeholder in " + loc.getKey() + " (use %s): " + v);
                        }
                    });
                }
                for (String k : ja.keySet()) {
                    if (!en.containsKey(k)) {
                        f.add(e.getKey() + ": " + k + " is only in ja_jp");
                    } else if (specs(ja.get(k)) != specs(en.get(k))) {
                        f.add(e.getKey() + ": " + k + " has " + specs(ja.get(k)) + " placeholders in ja_jp and " + specs(en.get(k)) + " in en_us");
                    }
                }
                for (String k : en.keySet()) {
                    if (!ja.containsKey(k)) {
                        f.add(e.getKey() + ": " + k + " is only in en_us");
                    }
                }
            }
            // 全体でも (名前空間をまたいで、片方にしか無いキー)
            java.util.Set<String> jaAll = new java.util.TreeSet<>();
            java.util.Set<String> enAll = new java.util.TreeSet<>();
            all.values().forEach(m -> {
                if (m.get("ja_jp") != null) {
                    jaAll.addAll(m.get("ja_jp").keySet());
                }
                if (m.get("en_us") != null) {
                    enAll.addAll(m.get("en_us").keySet());
                }
            });
            if (!jaAll.equals(enAll)) {
                f.add("the ja_jp and en_us key sets differ overall (" + jaAll.size() + " vs " + enAll.size() + ")");
            }
            // (4) コード中のキー、クエストのキー
            Map<String, String> jaT = new TreeMap<>();
            Map<String, String> enT = new TreeMap<>();
            all.values().forEach(m -> {
                if (m.get("ja_jp") != null) {
                    jaT.putAll(m.get("ja_jp"));
                }
                if (m.get("en_us") != null) {
                    enT.putAll(m.get("en_us"));
                }
            });
            int scanned = checkCodeKeys(jaT, enT, f);
            if (scanned >= 0 && scanned < 300) {
                f.add("only " + scanned + " translation keys were found in the source (the scan is broken)");
            }
            SkyAbyss.LOGGER.info("SELFTEST lang code keys scanned: {}", scanned);
            int questKeys = 0;
            for (jp.revati.skyabyss.quest.QuestDef def : jp.revati.skyabyss.quest.QuestManager.all()) {
                List<String> keys = new ArrayList<>(List.of(def.titleKey(), def.summaryKey(), def.doneKey()));
                for (var st : def.stages()) {
                    keys.add(def.journalKey(st));
                    st.objectives().forEach(ob -> keys.add(def.objectiveKey(st, ob)));
                }
                for (String k : keys) {
                    questKeys++;
                    if (!jaT.containsKey(k) || !enT.containsKey(k)) {
                        f.add("quest key " + k + " is missing from " + (jaT.containsKey(k) ? "en_us" : enT.containsKey(k) ? "ja_jp" : "both"));
                    }
                }
            }
            if (questKeys < 50) {
                f.add("only " + questKeys + " quest keys were checked (no quests loaded?)");
            }
            int dialogueKeys = 0;
            for (jp.revati.skyabyss.npc.Dialogue d : jp.revati.skyabyss.npc.DialogueManager.all().values()) {
                for (var node : d.nodes().values()) {
                    List<String> keys = new ArrayList<>();
                    if (node.textKey() != null) {
                        keys.add(node.textKey());
                    }
                    node.options().forEach(o -> keys.add(o.textKey()));
                    for (String k : keys) {
                        dialogueKeys++;
                        if (k != null && (!jaT.containsKey(k) || !enT.containsKey(k))) {
                            f.add("dialogue key " + k + " is missing from " + (jaT.containsKey(k) ? "en_us" : enT.containsKey(k) ? "ja_jp" : "both"));
                        }
                    }
                }
            }
            if (dialogueKeys < 100) {
                f.add("only " + dialogueKeys + " dialogue keys were checked (no dialogues loaded?)");
            }
            // 検査の部品そのものが動くこと
            if (specs("%s と %1$s と %% と %d") != 3) {
                f.add("the placeholder counter is wrong");
            }
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST lang crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST lang PASSED");
        } else {
            f.stream().limit(40).forEach(m -> SkyAbyss.LOGGER.error("SELFTEST lang FAILED: {}", m));
        }
    }
}
