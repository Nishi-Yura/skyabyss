package jp.revati.skyabyss.client.story;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

// 語りの紙芝居をクライアントで流す入口。中身は assets/skyabyss/story_reels/<id>.json:
//   {"panels": [{"id": "p1", "motion": "still|left|right|up|down", "hold": 秒, "sound": "minecraft:…" (任意)}]}
//   絵は textures/gui/story/<id>/<panel>.png (240x135)、文は lang の story.skyabyss.<id>.<panel>
public final class StoryReelClient {
    private StoryReelClient() {
    }

    public record Panel(String id, ResourceLocation image, String textKey, String motion, float hold, Optional<ResourceLocation> sound) {
    }

    public record Reel(String id, List<Panel> panels) {
    }

    public static Reel load(String id) {
        List<Panel> panels = new ArrayList<>();
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "story_reels/" + id + ".json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) {
            return new Reel(id, panels);
        }
        try (var in = res.get().open()) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("panels")) {
                JsonObject p = e.getAsJsonObject();
                String pid = p.get("id").getAsString();
                panels.add(new Panel(pid, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/story/" + id + "/" + pid + ".png"),
                        "story.skyabyss." + id + "." + pid, p.has("motion") ? p.get("motion").getAsString() : "still",
                        p.has("hold") ? p.get("hold").getAsFloat() : 2.5f,
                        p.has("sound") ? Optional.of(ResourceLocation.parse(p.get("sound").getAsString())) : Optional.empty()));
            }
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("could not read story reel {}", id, ex);
        }
        return new Reel(id, panels);
    }

    public static void play(String id) {
        Minecraft mc = Minecraft.getInstance();
        Reel reel = load(id);
        if (reel.panels().isEmpty()) {
            done(id);
            return;
        }
        mc.setScreen(new StoryReelScreen(reel));
    }

    static void done(String id) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new jp.revati.skyabyss.story.reel.StoryReels.DonePayload(id));
    }
}
