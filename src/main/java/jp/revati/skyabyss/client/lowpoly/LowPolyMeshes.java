package jp.revati.skyabyss.client.lowpoly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

// assets/skyabyss/lowpoly/<name>.json を全部読んで焼く (リソースの再読み込みのたびに)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class LowPolyMeshes implements ResourceManagerReloadListener {
    private static volatile Map<String, LowPolyMesh> meshes = Map.of();

    private LowPolyMeshes() {
    }

    @SubscribeEvent
    static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LowPolyMeshes());
    }

    public static LowPolyMesh get(String name) {
        return meshes.get(name);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, LowPolyMesh> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("lowpoly", id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = e.getKey();
            if (!id.getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            String path = id.getPath();
            String name = path.substring("lowpoly/".length(), path.length() - ".json".length());
            try (BufferedReader r = e.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                String tex = LowPolyMesh.textureName(json, name);
                boolean glow = manager.getResource(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + tex + "_glowmask.png")).isPresent();
                LowPolyMesh mesh = LowPolyMesh.parse(name, json, glow);
                loaded.put(name, mesh);
                SkyAbyss.LOGGER.info("[lowpoly] {}: {} bones, {} triangles -> {} quads{}", name, mesh.bones.size(), mesh.triangles, mesh.quads, glow ? ", glowmask" : "");
            } catch (Exception ex) {
                SkyAbyss.LOGGER.error("[lowpoly] failed to load {}", id, ex);
            }
        }
        meshes = loaded;
    }
}
