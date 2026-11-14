package jp.revati.skyabyss.client.lowpoly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

// assets/skyabyss/lowpoly/<name>.json を全部読んで焼く (リソースの再読み込みのたびに)。
// 読んで焼くのは再読み込みの下ごしらえ (裏のスレッド) で、ファイルごとに並列に行う。描画スレッドは出来上がった表を差し替えるだけ。
// 焼く処理 (LowPolyMesh.parse) は JSON と配列だけを扱い、描画の状態には触らない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class LowPolyMeshes extends SimplePreparableReloadListener<Map<String, LowPolyMesh>> {
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
    protected Map<String, LowPolyMesh> prepare(ResourceManager manager, ProfilerFiller profiler) {
        long t0 = System.nanoTime();
        List<Map.Entry<ResourceLocation, Resource>> files = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("lowpoly", id -> id.getPath().endsWith(".json")).entrySet()) {
            if (e.getKey().getNamespace().equals(SkyAbyss.MODID)) {
                files.add(e);
            }
        }
        Map<String, LowPolyMesh> loaded = new ConcurrentHashMap<>();
        files.parallelStream().forEach(e -> {
            ResourceLocation id = e.getKey();
            String path = id.getPath();
            String name = path.substring("lowpoly/".length(), path.length() - ".json".length());
            try (BufferedReader r = e.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                String tex = LowPolyMesh.textureName(json, name);
                boolean glow = manager.getResource(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + tex + "_glow.png")).isPresent();
                LowPolyMesh mesh = LowPolyMesh.parse(name, json, glow);
                loaded.put(name, mesh);
                SkyAbyss.LOGGER.debug("[lowpoly] {}: {} bones, {} triangles -> {} quads{}", name, mesh.bones.size(), mesh.triangles, mesh.quads, glow ? ", glowmask" : "");
            } catch (Exception ex) {
                SkyAbyss.LOGGER.error("[lowpoly] failed to load {}", id, ex);
            }
        });
        SkyAbyss.LOGGER.info("[lowpoly] baked {} meshes in {} ms", loaded.size(), (System.nanoTime() - t0) / 1_000_000);
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<String, LowPolyMesh> loaded, ResourceManager manager, ProfilerFiller profiler) {
        meshes = loaded;
        LowPolyArmor.clearCache();
        LowPolyGlow.clear();
        LowPolyGlowTexture.clear();
        LowPoly.clearCubeCache();
    }
}
