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
public final class LangSelfTest {
    private static final Pattern SPEC = Pattern.compile("%(?:\\d+\\$)?[sd]");
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
