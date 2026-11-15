package jp.revati.skyabyss.client.splat;

import java.io.InputStream;
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

// assets/skyabyss/splat/<名前>.bin を全部読む (リソースの再読み込みのたび)。名前は低ポリのメッシュの名前と同じ (LowPolyGeoRenderer が引く)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SplatClouds extends SimplePreparableReloadListener<Map<String, SplatCloud>> {
    // 点の模型を使うか。false = 今までの低ポリメッシュ。台本の `splat on|off`
    public static volatile boolean enabled = true;

    private static volatile Map<String, SplatCloud> clouds = Map.of();

    private SplatClouds() {
    }

    @SubscribeEvent
    static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SplatClouds());
    }

    public static SplatCloud get(String name) {
        return enabled ? clouds.get(name) : null;
    }

    @Override
    protected Map<String, SplatCloud> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, SplatCloud> loaded = new ConcurrentHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("splat", id -> id.getPath().endsWith(".bin")).entrySet()) {
            if (!e.getKey().getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            String path = e.getKey().getPath();
            String name = path.substring("splat/".length(), path.length() - ".bin".length());
            try (InputStream in = e.getValue().open()) {
                SplatCloud c = SplatCloud.read(name, in);
                loaded.put(name, c);
                SkyAbyss.LOGGER.info("[splat] {}: {} splats ({} additive), {} bones", name, c.total, c.additive.n, c.bones.length);
            } catch (Exception ex) {
                SkyAbyss.LOGGER.error("[splat] failed to load {}", e.getKey(), ex);
            }
        }
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<String, SplatCloud> loaded, ResourceManager manager, ProfilerFiller profiler) {
        clouds = loaded;
        SplatGpu.clear();
    }
}
