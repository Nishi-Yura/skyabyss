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
        // 足し書き ("extends": true) は、同じ役の元の会話に混ぜてから読む。元のファイルに手を入れずに、話題や節を足せる
        //   nodes       : 節を足す (同じ名前は不可)
        //   entries     : 入口を、元の最後の入口 (条件なし) の手前に足す
        //   add_options : {"<節>": {"before": "<選択肢>", "options": [...]}} 元の節の選択肢に足す (before を省くと末尾)
        Map<String, com.google.gson.JsonObject> bases = new LinkedHashMap<>();
        Map<String, List<Map.Entry<ResourceLocation, com.google.gson.JsonObject>>> extensions = new LinkedHashMap<>();
        files.forEach((location, json) -> {
            if (!json.isJsonObject()) {
                errors.add(location + ": not an object");
                return;
            }
            com.google.gson.JsonObject o = json.getAsJsonObject();
            String role = net.minecraft.util.GsonHelper.getAsString(o, "npc", location.toString());
            if (net.minecraft.util.GsonHelper.getAsBoolean(o, "extends", false)) {
                extensions.computeIfAbsent(role, k -> new ArrayList<>()).add(Map.entry(location, o));
            } else {
                bases.put(role, o.deepCopy());
            }
        });
        extensions.forEach((role, list) -> {
            com.google.gson.JsonObject base = bases.get(role);
            if (base == null) {
                errors.add("dialogue " + role + ": extension without a base dialogue");
                return;
            }
            list.sort(Map.Entry.comparingByKey());
            list.forEach(e -> merge(base, e.getValue(), e.getKey().toString(), errors));
        });
        bases.forEach((role, o) -> {
            try {
                Dialogue d = Dialogue.parse(role, o, errors);
                loaded.put(d.role(), d);
            } catch (RuntimeException e) {
                errors.add(role + ": " + e);
            }
        });
        errors.forEach(e -> SkyAbyss.LOGGER.warn("[dialogue] {}", e));
        dialogues = Map.copyOf(loaded);
        loadErrors = List.copyOf(errors);
        SkyAbyss.LOGGER.info("[dialogue] loaded {} dialogues", loaded.size());
    }

    static void merge(com.google.gson.JsonObject base, com.google.gson.JsonObject ext, String where, List<String> errors) {
        if (!base.has("nodes")) {
            base.add("nodes", new com.google.gson.JsonObject());
        }
        com.google.gson.JsonObject nodes = base.getAsJsonObject("nodes");
        if (ext.has("nodes")) {
            for (Map.Entry<String, JsonElement> n : ext.getAsJsonObject("nodes").entrySet()) {
                if (nodes.has(n.getKey())) {
                    errors.add(where + ": node " + n.getKey() + " already exists");
                    continue;
                }
                nodes.add(n.getKey(), n.getValue());
            }
        }
        if (ext.has("entries")) {
            com.google.gson.JsonArray old = base.has("entries") ? base.getAsJsonArray("entries") : new com.google.gson.JsonArray();
            com.google.gson.JsonArray merged = new com.google.gson.JsonArray();
            for (int i = 0; i < old.size() - 1; i++) {
                merged.add(old.get(i));
            }
            ext.getAsJsonArray("entries").forEach(merged::add);
            if (!old.isEmpty()) {
                merged.add(old.get(old.size() - 1));
            }
            base.add("entries", merged);
        }
        if (ext.has("add_options")) {
            for (Map.Entry<String, JsonElement> a : ext.getAsJsonObject("add_options").entrySet()) {
                if (!nodes.has(a.getKey())) {
                    errors.add(where + ": add_options to missing node " + a.getKey());
                    continue;
                }
                com.google.gson.JsonObject node = nodes.getAsJsonObject(a.getKey());
                com.google.gson.JsonObject add = a.getValue().getAsJsonObject();
                com.google.gson.JsonArray old = node.has("options") ? node.getAsJsonArray("options") : new com.google.gson.JsonArray();
                String before = add.has("before") ? add.get("before").getAsString() : null;
                com.google.gson.JsonArray merged = new com.google.gson.JsonArray();
                boolean placed = false;
                for (JsonElement op : old) {
                    if (!placed && before != null && before.equals(op.getAsJsonObject().get("id").getAsString())) {
                        add.getAsJsonArray("options").forEach(merged::add);
                        placed = true;
                    }
                    merged.add(op);
                }
                if (!placed) {
                    add.getAsJsonArray("options").forEach(merged::add);
                }
                node.add("options", merged);
            }
        }
    }
}
