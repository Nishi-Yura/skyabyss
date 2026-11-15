package jp.revati.skyabyss.client.splat;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

// 点の模型の置き場。名前は低ポリのメッシュの名前 (lowpoly/<名前>.json) と同じ。
// 手で彫った assets/skyabyss/splat/<名前>.bin があればそれ、無ければメッシュからその場で作る (SplatGen、裏のスレッドで。出来るまでは描かない)。
// しばらく (UNUSED_TICKS) 使われなかった模型は捨てる (GPU の分も)。リソースの再読み込みで全部作り直す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SplatClouds extends SimplePreparableReloadListener<Map<String, SplatCloud>> {
    private static final int UNUSED_TICKS = 20 * 60;

    private static volatile Map<String, SplatCloud> handMade = Map.of();
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static int tick;

    private static final class Entry {
        final CompletableFuture<SplatCloud> future;
        volatile int lastUsed;

        Entry(CompletableFuture<SplatCloud> f) {
            this.future = f;
        }
    }

    private SplatClouds() {
    }

    @SubscribeEvent
    static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SplatClouds());
    }

    // 出来ていればその模型、まだなら作り始めて null。メッシュも .bin も無い名前も null
    public static SplatCloud get(String name) {
        if (name == null) {
            return null;
        }
        SplatCloud hand = handMade.get(name);
        if (hand != null) {
            return hand;
        }
        return get(LowPolyMeshes.get(name), null);
    }

    // メッシュを絵 tex (null = メッシュの絵) で塗った模型。武器の強化の段 (+5・+10) は絵だけが替わる
    public static SplatCloud get(LowPolyMesh mesh, ResourceLocation tex) {
        if (mesh == null) {
            return null;
        }
        boolean own = tex == null || tex.equals(mesh.texture);
        if (own) {
            SplatCloud hand = handMade.get(mesh.name);
            if (hand != null) {
                return hand;
            }
        }
        String key = own ? mesh.name : mesh.name + "@" + tex;
        Entry e = CACHE.get(key);
        if (e == null) {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            ResourceLocation paint = own ? mesh.texture : tex;
            Entry made = new Entry(CompletableFuture.supplyAsync(() -> {
                long t0 = System.nanoTime();
                SplatCloud c = SplatGen.generate(mesh, paint, rm);
                if (c != null) {
                    SkyAbyss.LOGGER.debug("[splat] generated {}: {} splats in {} ms", key, c.total, (System.nanoTime() - t0) / 1_000_000);
                }
                return c;
            }, Util.backgroundExecutor()).exceptionally(ex -> {
                SkyAbyss.LOGGER.error("[splat] failed to generate {}", key, ex);
                return null;
            }));
            Entry prev = CACHE.putIfAbsent(key, made);
            e = prev == null ? made : prev;
        }
        e.lastUsed = tick;
        return e.future.getNow(null);
    }

    // 低ポリのメッシュに対応する模型 (無ければ null)
    public static SplatCloud of(LowPolyMesh mesh) {
        return get(mesh, null);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        tick++;
        if (tick % 100 != 0) {
            return;
        }
        CACHE.entrySet().removeIf(en -> {
            if (tick - en.getValue().lastUsed > UNUSED_TICKS && en.getValue().future.isDone()) {
                SplatCloud c = en.getValue().future.getNow(null);
                if (c != null) {
                    SplatGpu.release(c);
                }
                return true;
            }
            return false;
        });
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
        handMade = loaded;
        CACHE.clear();
        SplatGpu.clear();
    }
}
