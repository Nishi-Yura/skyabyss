package jp.revati.skyabyss.music;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;

// BGM の自己テスト (SKYABYSS_SELFTEST_ONLY="music"): 曲の一覧ごとに音の名前・ogg・sounds.json (stream) がそろう、
// 地上の場所 (都・町 4・土地 6) がどれも曲に当たる、ボスの帯の印
public final class MusicSelfTest {
    private MusicSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        JsonObject sounds = null;
        try (InputStream in = MusicSelfTest.class.getResourceAsStream("/assets/skyabyss/sounds.json")) {
            sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("sounds.json cannot be read: " + e);
        }
        for (String id : MusicTracks.ALL) {
            if (!BuiltInRegistries.SOUND_EVENT.containsKey(MusicTracks.location(id))) {
                f.add("sound event music." + id + " is not registered");
            }
            if (MusicSelfTest.class.getResource("/assets/skyabyss/sounds/music/" + id + ".ogg") == null) {
                f.add("sounds/music/" + id + ".ogg is missing");
            }
            if (sounds != null) {
                JsonObject e = sounds.getAsJsonObject("music." + id);
                boolean stream = e != null && e.getAsJsonArray("sounds").get(0).getAsJsonObject().has("stream")
                        && e.getAsJsonArray("sounds").get(0).getAsJsonObject().get("stream").getAsBoolean();
                if (!stream) {
                    f.add("sounds.json music." + id + " is missing or not streamed");
                }
            }
        }
        List<String> areas = new ArrayList<>(List.of("capital", "town:whale", "town:tide", "town:storm", "town:ember"));
        for (int r = 0; r < MusicTracks.REGION_TRACKS.length; r++) {
            areas.add("region:" + r);
        }
        for (String a : areas) {
            String t = MusicTracks.forArea(a);
            if (t == null || !MusicTracks.ALL.contains(t)) {
                f.add("area " + a + " has no track");
            }
        }
        if (MusicTracks.forArea("") != null || MusicTracks.forArea("region:99") != null || MusicTracks.forArea("town:nowhere") != null) {
            f.add("unknown areas should have no track");
        }
        ServerBossEvent bar = MusicArea.bossBar(new ServerBossEvent(Component.literal("t"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        if (!bar.shouldPlayBossMusic()) {
            f.add("boss bars are not marked for boss music");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST music PASSED ({} tracks: sound events, ogg, streamed in sounds.json; {} areas mapped; boss bar mark)",
                    MusicTracks.ALL.size(), areas.size());
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST music FAILED: {}", s));
        }
    }
}
