package jp.revati.skyabyss.story.finale;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

// 雲海の島の地形 (docs/design/50 の 4)。tools/gen/world/build_cloud_*.py が彫って data/skyabyss/finale/<名前>.json に書く。
// 書式は月の区画と同じ: palette・runs ([y, z, x0, (色, 横の数)...]。色 -1 は飛ばす)・marks (名前 → [x, y, z])・rect [x0, z0, x1, z1]
public final class FinaleTerrain {
    private static final Map<String, FinaleTerrain> CACHE = new HashMap<>();

    public final String name;
    public final Map<String, BlockPos> marks;
    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;

    private FinaleTerrain(String name, Map<String, BlockPos> marks, int x0, int z0, int x1, int z1) {
        this.name = name;
        this.marks = marks;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
    }

    public static synchronized FinaleTerrain of(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            JsonObject json = read(n);
            Map<String, BlockPos> marks = new HashMap<>();
            for (Map.Entry<String, JsonElement> m : json.getAsJsonObject("marks").entrySet()) {
                JsonArray a = m.getValue().getAsJsonArray();
                marks.put(m.getKey(), new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            JsonArray r = json.getAsJsonArray("rect");
            return new FinaleTerrain(n, Map.copyOf(marks), r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt());
        });
    }

    public BlockPos mark(String key) {
        BlockPos p = marks.get(key);
        if (p == null) {
            throw new IllegalStateException(name + " mark missing: " + key);
        }
        return p;
    }

    public boolean inside(BlockPos p) {
        return p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1;
    }

    static JsonObject read(String name) {
        String path = "/data/" + SkyAbyss.MODID + "/finale/" + name + ".json";
        try (InputStream in = FinaleTerrain.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }

    // ある種類のブロック (palette の文字列が prefix で始まるもの) の位置 (仕掛けのブロックの一覧に使う)
    public List<BlockPos> positionsOf(String prefix) {
        JsonObject json = read(name);
        JsonArray pal = json.getAsJsonArray("palette");
        java.util.Set<Integer> want = new java.util.HashSet<>();
        for (int i = 0; i < pal.size(); i++) {
            if (pal.get(i).getAsString().startsWith(prefix)) {
                want.add(i);
            }
        }
        List<BlockPos> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("runs")) {
            JsonArray a = e.getAsJsonArray();
            int y = a.get(0).getAsInt();
            int z = a.get(1).getAsInt();
            int x = a.get(2).getAsInt();
            for (int i = 3; i + 1 < a.size(); i += 2) {
                int idx = a.get(i).getAsInt();
                int len = a.get(i + 1).getAsInt();
                if (want.contains(idx)) {
                    for (int k = 0; k < len; k++) {
                        out.add(new BlockPos(x + k, y, z));
                    }
                }
                x += len;
            }
        }
        return out;
    }

    // 流し込む (下の段から)。置いた数
    public int pour(ServerLevel level) {
        long t0 = System.currentTimeMillis();
        JsonObject json = read(name);
        List<BlockState> palette = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("palette")) {
            try {
                palette.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), e.getAsString(), false).blockState());
            } catch (Exception ex) {
                throw new IllegalStateException("bad block state in " + name + ".json: " + e.getAsString(), ex);
            }
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (JsonElement e : json.getAsJsonArray("runs")) {
            JsonArray a = e.getAsJsonArray();
            int y = a.get(0).getAsInt();
            int z = a.get(1).getAsInt();
            int x = a.get(2).getAsInt();
            for (int i = 3; i + 1 < a.size(); i += 2) {
                int idx = a.get(i).getAsInt();
                int len = a.get(i + 1).getAsInt();
                if (idx >= 0) {
                    BlockState st = palette.get(idx);
                    for (int k = 0; k < len; k++) {
                        m.set(x + k, y, z);
                        if (level.getBlockState(m) != st) {
                            level.setBlock(m, st, 2 | 16);
                            placed++;
                        }
                    }
                }
                x += len;
            }
        }
        SkyAbyss.LOGGER.info("[cloudsea] {} poured: {} blocks in {} ms", name, placed, System.currentTimeMillis() - t0);
        return placed;
    }
}
