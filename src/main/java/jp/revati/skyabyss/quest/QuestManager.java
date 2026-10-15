package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.Comparator;
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

// クエストの定義を、データパック (data/<名前空間>/quest/<名前>.json) から読む。/reload でも読み直す。
// ID は skyabyss の名前空間なら "<名前>"、ほかなら "<名前空間>:<名前>"
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class QuestManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, QuestDef> quests = Map.of();
    private static volatile List<QuestDef> ordered = List.of();
    private static volatile List<String> loadErrors = List.of();

    private QuestManager() {
        super(GSON, "quest");
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestManager());
    }

    @Nullable
    public static QuestDef quest(String id) {
        return quests.get(id);
    }

    // メインが先、その中は order の順
    public static List<QuestDef> all() {
        return ordered;
    }

    public static List<String> loadErrors() {
        return loadErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        Map<String, QuestDef> loaded = new LinkedHashMap<>();
        files.forEach((location, json) -> {
            String id = (location.getNamespace().equals(SkyAbyss.MODID) ? "" : location.getNamespace() + ":") + location.getPath();
            if (!json.isJsonObject()) {
                errors.add(location + ": not an object");
                return;
            }
            try {
                loaded.put(id, QuestDef.parse(id, json.getAsJsonObject(), errors));
            } catch (RuntimeException e) {
                errors.add(location + ": " + e.getMessage());
            }
        });
        set(loaded, errors);
        SkyAbyss.LOGGER.info("[quest] loaded {} quests", loaded.size());
    }

    static void set(Map<String, QuestDef> loaded, List<String> errors) {
        errors.forEach(e -> SkyAbyss.LOGGER.warn("[quest] {}", e));
        List<QuestDef> list = new ArrayList<>(loaded.values());
        list.sort(Comparator.comparing((QuestDef q) -> !q.main()).thenComparingInt(QuestDef::order).thenComparing(QuestDef::id));
        quests = Map.copyOf(loaded);
        ordered = List.copyOf(list);
        loadErrors = List.copyOf(errors);
    }
}
