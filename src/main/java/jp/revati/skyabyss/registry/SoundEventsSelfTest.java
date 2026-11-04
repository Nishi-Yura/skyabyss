package jp.revati.skyabyss.registry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

// 登録した音の出来事が、どれも自分の名前空間の sounds.json に行を持つか。SKYABYSS_SELFTEST_ONLY="sound events"
//   行が無い音は鳴らない (終章の敵 16 種の声が、sounds.json を書き直したときに消えて鳴らなくなっていた。2026-11-05 に戻した)
public final class SoundEventsSelfTest {
    private SoundEventsSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        Map<String, JsonObject> files = new HashMap<>();
        int checked = 0;
        try {
            // 鳴らす名前は音の出来事が持つ名前 (登録名と違うことがある。例 skyabyss:storm_heart_beat → skyabyss_storm_heart:beat)
            for (net.minecraft.sounds.SoundEvent ev : BuiltInRegistries.SOUND_EVENT) {
                ResourceLocation key = ev.getLocation();
                String ns = key.getNamespace();
                if (!ns.equals(SkyAbyss.MODID) && !ns.startsWith(SkyAbyss.MODID + "_")) {
                    continue;
                }
                checked++;
                JsonObject o = files.computeIfAbsent(ns, SoundEventsSelfTest::load);
                if (o == null) {
                    f.add(ns + " has sound events but no sounds.json");
                    continue;
                }
                if (!o.has(key.getPath())) {
                    f.add(key.toString());
                }
            }
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("SELFTEST sound events crashed", e);
            f.add("crashed: " + e);
        }
        if (checked == 0) {
            f.add("no mod sound events were found");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST sound events PASSED ({} mod sound events, each has a sounds.json row)", checked);
        } else {
            SkyAbyss.LOGGER.error("SELFTEST sound events FAILED ({} without a sounds.json row): {}", f.size(), f.size() > 20 ? f.subList(0, 20) + " ..." : f);
        }
    }

    private static JsonObject load(String ns) {
        try (InputStream in = SoundEventsSelfTest.class.getResourceAsStream("/assets/" + ns + "/sounds.json")) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
