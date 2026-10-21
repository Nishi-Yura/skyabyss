package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// 店の品ぞろえを、データパック (data/<名前空間>/shop/<店>.json) から読む。/reload でも読み直す。店の ID はファイルの名前 (名前空間は省く)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ShopManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, ShopDef> shops = Map.of();
    private static volatile List<String> loadErrors = List.of();

    private ShopManager() {
        super(GSON, "shop");
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ShopManager());
    }

    @Nullable
    public static ShopDef shop(String id) {
        return shops.get(id);
    }

    public static Map<String, ShopDef> all() {
        return shops;
    }

    public static List<String> loadErrors() {
        return loadErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        Map<String, ShopDef> loaded = new LinkedHashMap<>();
        files.forEach((location, json) -> {
            if (!json.isJsonObject()) {
                errors.add(location + ": not an object");
                return;
            }
            try {
                loaded.put(location.getPath(), ShopDef.parse(location.getPath(), json.getAsJsonObject(), errors));
            } catch (RuntimeException e) {
                errors.add(location + ": " + e);
            }
        });
        errors.forEach(e -> SkyAbyss.LOGGER.warn("[shop] {}", e));
        shops = Map.copyOf(loaded);
        loadErrors = List.copyOf(errors);
        SkyAbyss.LOGGER.info("[shop] loaded {} shops", loaded.size());
    }
}
