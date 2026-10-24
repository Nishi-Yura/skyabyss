package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;

// 大陸の見取り図の座標 (data/skyabyss/continent/layout.json。tools/gen/world/gen_continent.py が書く)。
// 都・はじまりの場所・土地ごとの町・章の跡地・道・渡し船・土地の名前と目安のレベル。高さは持たない (Y は 0。使う側で地面の高さを足す)
public final class ContinentLayout {
    private static ContinentLayout instance;

    private final BlockPos capital;
    private final BlockPos start;
    private final Map<String, BlockPos> towns = new HashMap<>();
    private final Map<String, BlockPos> sites = new HashMap<>();
    private final List<List<BlockPos>> roads = new ArrayList<>();
    private final List<List<BlockPos>> ferries = new ArrayList<>();
    private final Map<String, BlockPos> names = new java.util.LinkedHashMap<>();
    private final Map<String, Level> levels = new java.util.LinkedHashMap<>();

    // 地図に書く目安のレベル ("4-12") とその場所
    public record Level(String text, BlockPos pos) {
    }

    private ContinentLayout(JsonObject o) {
        capital = pos(o.getAsJsonArray("capital"));
        start = pos(o.getAsJsonArray("start"));
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("towns").entrySet()) {
            towns.put(e.getKey(), pos(e.getValue().getAsJsonArray()));
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("sites").entrySet()) {
            sites.put(e.getKey(), pos(e.getValue().getAsJsonArray()));
        }
        lines(o.getAsJsonArray("roads"), roads);
        lines(o.getAsJsonArray("ferries"), ferries);
        if (o.has("names")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("names").entrySet()) {
                names.put(e.getKey(), pos(e.getValue().getAsJsonArray()));
            }
        }
        if (o.has("levels")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("levels").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                levels.put(e.getKey(), new Level(a.get(0).getAsString(), pos(a.get(1).getAsJsonArray())));
            }
        }
    }

    private static BlockPos pos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), 0, a.get(1).getAsInt());
    }

    private static void lines(JsonArray a, List<List<BlockPos>> out) {
        for (JsonElement line : a) {
            List<BlockPos> pts = new ArrayList<>();
            for (JsonElement p : line.getAsJsonArray()) {
                pts.add(pos(p.getAsJsonArray()));
            }
            out.add(List.copyOf(pts));
        }
    }

    public static synchronized ContinentLayout get() {
        if (instance == null) {
            String path = "/data/skyabyss/continent/layout.json";
            try (InputStream in = ContinentLayout.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + path);
                }
                instance = new ContinentLayout(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot read " + path, e);
            }
        }
        return instance;
    }

    public BlockPos capital() {
        return capital;
    }

    public BlockPos start() {
        return start;
    }

    // 章の id ("titan_whale" など。Chapters.id()) か、"whale" などの短い名前
    @Nullable
    public BlockPos site(String titan) {
        return sites.get(titan.startsWith("titan_") ? titan.substring(6) : titan);
    }

    @Nullable
    public BlockPos town(String titan) {
        return towns.get(titan.startsWith("titan_") ? titan.substring(6) : titan);
    }

    public List<List<BlockPos>> roads() {
        return roads;
    }

    public List<List<BlockPos>> ferries() {
        return ferries;
    }

    // 土地の名前 (lang の map.skyabyss.continent.<id>) の場所
    public Map<String, BlockPos> names() {
        return names;
    }

    // 土地ごとの目安のレベル (capital・whale・tide・storm・ember)
    public Map<String, Level> levels() {
        return levels;
    }
}
