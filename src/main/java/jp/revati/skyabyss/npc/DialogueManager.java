package jp.revati.skyabyss.npc;

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

// NPC の会話を、データパック (data/<名前空間>/dialogue/<役>.json) から読む。/reload でも読み直す。
// 役の ID はエンティティの ID と同じ ("skyabyss:storyteller")
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DialogueManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, Dialogue> dialogues = Map.of();
    private static volatile List<String> loadErrors = List.of();

    private DialogueManager() {
        super(GSON, "dialogue");
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DialogueManager());
    }

    @Nullable
    public static Dialogue dialogue(String role) {
        return dialogues.get(role);
    }

    public static Map<String, Dialogue> all() {
        return dialogues;
    }

    public static List<String> loadErrors() {
        return loadErrors;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<String> errors = new ArrayList<>();
        Map<String, Dialogue> loaded = new LinkedHashMap<>();
        files.forEach((location, json) -> {
            if (!json.isJsonObject()) {
                errors.add(location + ": not an object");
                return;
            }
            try {
                Dialogue d = Dialogue.parse(location.toString(), json.getAsJsonObject(), errors);
                loaded.put(d.role(), d);
            } catch (RuntimeException e) {
                errors.add(location + ": " + e);
            }
        });
        errors.forEach(e -> SkyAbyss.LOGGER.warn("[dialogue] {}", e));
        dialogues = Map.copyOf(loaded);
        loadErrors = List.copyOf(errors);
        SkyAbyss.LOGGER.info("[dialogue] loaded {} dialogues", loaded.size());
    }
}
