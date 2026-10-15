package jp.revati.skyabyss.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// 伝承の章と項目を、データパック (data/<名前空間>/lore/) から読む。/reload でも読み直す。
//   data/skyabyss/lore/chapter/<章>.json        → 章
//   data/skyabyss/lore/entry/<章>/<名前>.json  → 項目 (ID は "<章>/<名前>"。skyabyss 以外の名前空間なら "<名前空間>:<章>/<名前>")
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LoreManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile LoreCatalog catalog = LoreCatalog.EMPTY;
    // 読み込みで弾いたファイル (自己テストで失敗にする)
    private static volatile List<String> loadErrors = List.of();

    private LoreManager() {
        super(GSON, "lore");
    }

    public static LoreCatalog catalog() {
        return catalog;
    }

    public static List<String> loadErrors() {
        return loadErrors;
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new LoreManager());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<LoreChapter> chapters = new ArrayList<>();
        List<LoreEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        files.forEach((location, json) -> {
            String path = location.getPath();
            String prefix = location.getNamespace().equals(SkyAbyss.MODID) ? "" : location.getNamespace() + ":";
            if (path.startsWith("chapter/")) {
                String id = prefix + path.substring("chapter/".length());
                LoreChapter.Def.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(e -> errors.add(location + ": " + e))
                        .ifPresent(def -> chapters.add(new LoreChapter(id, def.order())));
            } else if (path.startsWith("entry/")) {
                String id = prefix + path.substring("entry/".length());
                LoreEntry.Def.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(e -> errors.add(location + ": " + e))
                        .ifPresent(def -> entries.add(def.withId(id)));
            } else {
                errors.add(location + ": not under lore/chapter or lore/entry");
            }
        });
        LoreCatalog loaded = new LoreCatalog(chapters, entries);
        errors.addAll(loaded.problems());
        errors.forEach(e -> SkyAbyss.LOGGER.warn("[lore] {}", e));
        catalog = loaded;
        loadErrors = List.copyOf(errors);
        SkyAbyss.LOGGER.info("[lore] loaded {} chapters and {} entries", loaded.chapters().size(), loaded.entries().size());
    }
}
