package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.town.RegionFolk;
import net.minecraft.server.MinecraftServer;

// 見どころの一か所ずつの中身 (docs/design/40 の 3.3) の検査。段の名前は "poi places"。
//   数: 道しるべ 6・野盗の野営 4 (+ はじまりの島の一つ)・旅人の野営 3
//   名のある場所: 部品 poi_<種類>__<名> があり、碑の印 tablet がある。名と書付 (4 行) の文が日英にある。名は重ならない
//   道しるべ: 看板の印 sign があり、4 行の文が日英にある。野盗の頭目の兄弟: 4 人の名の文。旅人: 野営の部品に立つ所 folk、町の人の一覧にいる
public final class PoiPlacesSelfTest {
    static final int SIGNPOSTS = 6;
    static final int BANDIT_CAMPS = 5;
    static final int TRAVELER_CAMPS = 3;

    private PoiPlacesSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("poi places", PoiPlacesSelfTest::run));
    }

    static Map<String, String> lang(String path) {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = PoiPlacesSelfTest.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (java.io.IOException ex) {
            SkyAbyss.LOGGER.error("cannot read {}", path, ex);
        }
        return out;
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        Map<String, String> ja = lang("/assets/skyabyss/lang/ja_jp.json");
        Map<String, String> en = lang("/assets/skyabyss/lang/en_us.json");
        Map<String, Integer> count = new HashMap<>();
        Set<String> names = new HashSet<>();
        int chiefs = 0;
        for (ContinentPois.Poi poi : ContinentPois.all()) {
            count.merge(poi.kind(), 1, Integer::sum);
            if (poi.name().isEmpty()) {
                continue;
            }
            if (!names.add(poi.name())) {
                f.add("the place name " + poi.name() + " is used twice");
            }
            Blueprint bp;
            try {
                bp = Blueprint.get(poi.blueprint());
            } catch (RuntimeException ex) {
                f.add(poi.name() + ": no blueprint " + poi.blueprint());
                continue;
            }
            List<String> keys = new ArrayList<>();
            if (poi.kind().equals("signpost")) {
                if (!bp.marks.containsKey("sign")) {
                    f.add(poi.name() + ": the signpost has no sign mark");
                }
                for (int k = 1; k <= 4; k++) {
                    keys.add("sign.skyabyss.waypost." + poi.name() + "." + k);
                }
            } else {
                if (!bp.marks.containsKey("tablet")) {
                    f.add(poi.name() + ": no tablet mark in " + poi.blueprint());
                }
                keys.add(poi.titleKey());
                keys.add(ContinentPois.noteKey(poi) + ".1");
            }
            if (!poi.chief().isEmpty()) {
                chiefs++;
                keys.add("entity.skyabyss.bandit_brother." + poi.chief());
            }
            for (String k : keys) {
                if (!ja.containsKey(k) || ja.get(k).isEmpty() || !en.containsKey(k) || en.get(k).isEmpty()) {
                    f.add(poi.name() + ": missing text " + k);
                }
            }
            if (poi.kind().equals("traveler_camp")) {
                boolean folk = RegionFolk.ALL.stream().anyMatch(rf -> rf.town().equals(RegionFolk.CAMP) && rf.mark().equals(poi.name()));
                if (!folk) {
                    f.add(poi.name() + ": no traveler stands at this camp");
                }
                if (!bp.marks.containsKey("folk")) {
                    f.add(poi.name() + ": no folk mark in " + poi.blueprint());
                }
            }
        }
        if (count.getOrDefault("signpost", 0) != SIGNPOSTS) {
            f.add("signposts: " + count.get("signpost") + " (want " + SIGNPOSTS + ")");
        }
        if (count.getOrDefault("bandit_camp", 0) != BANDIT_CAMPS) {
            f.add("bandit camps: " + count.get("bandit_camp") + " (want " + BANDIT_CAMPS + ")");
        }
        if (count.getOrDefault("traveler_camp", 0) != TRAVELER_CAMPS) {
            f.add("traveler camps: " + count.get("traveler_camp") + " (want " + TRAVELER_CAMPS + ")");
        }
        if (chiefs != 4) {
            f.add("bandit brothers: " + chiefs + " (want 4)");
        }
        for (RegionFolk.Folk rf : RegionFolk.ALL) {
            if (rf.town().equals(RegionFolk.CAMP) && !names.contains(rf.mark())) {
                f.add(rf.id() + ": stands at an unknown camp " + rf.mark());
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST poi places PASSED ({} named places, {} signposts, {} brothers)", names.size(), SIGNPOSTS, chiefs);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST poi places FAILED: {}", s));
        }
    }
}
