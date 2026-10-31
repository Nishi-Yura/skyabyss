package jp.revati.skyabyss.tower;

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

// あいだの塔の階の地形 (docs/design/54 の 2.4)。tools/gen/world/build_tower.py が彫って data/skyabyss/tower/<階>.json に書く。
// 書式は雲海の島と同じ (palette・runs・marks・rect) で、座標は階の手元 (原点 = 階の床の真ん中、床の上面 y 0)。区画の原点へずらして流し込む
public final class TowerTerrain {
    private static final Map<String, TowerTerrain> CACHE = new HashMap<>();

    public final String name;
    public final Map<String, BlockPos> marks;
    final int x0;
    final int z0;
    final int x1;
    final int z1;
    private final List<BlockState> palette;
    private final List<int[]> runs;

    private TowerTerrain(String name, Map<String, BlockPos> marks, int[] rect, List<BlockState> palette, List<int[]> runs) {
        this.name = name;
        this.marks = marks;
        this.x0 = rect[0];
        this.z0 = rect[1];
        this.x1 = rect[2];
        this.z1 = rect[3];
        this.palette = palette;
        this.runs = runs;
    }

    public static synchronized TowerTerrain of(String name) {
        return CACHE.computeIfAbsent(name, TowerTerrain::load);
    }

    private static TowerTerrain load(String name) {
        String path = "/data/" + SkyAbyss.MODID + "/tower/" + name + ".json";
        JsonObject json;
        try (InputStream in = TowerTerrain.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read " + path, e);
        }
        Map<String, BlockPos> marks = new HashMap<>();
        for (Map.Entry<String, JsonElement> m : json.getAsJsonObject("marks").entrySet()) {
            JsonArray a = m.getValue().getAsJsonArray();
            marks.put(m.getKey(), new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
        }
        List<BlockState> palette = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("palette")) {
            try {
                palette.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), e.getAsString(), false).blockState());
            } catch (Exception ex) {
                throw new IllegalStateException("bad block state in " + name + ".json: " + e.getAsString(), ex);
            }
        }
        List<int[]> runs = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("runs")) {
            JsonArray a = e.getAsJsonArray();
            int[] r = new int[a.size()];
            for (int i = 0; i < r.length; i++) {
                r[i] = a.get(i).getAsInt();
            }
            runs.add(r);
        }
        JsonArray rect = json.getAsJsonArray("rect");
        return new TowerTerrain(name, Map.copyOf(marks), new int[]{rect.get(0).getAsInt(), rect.get(1).getAsInt(), rect.get(2).getAsInt(), rect.get(3).getAsInt()},
                List.copyOf(palette), runs);
    }

    // 手元の印 (階の原点からのずれ)
    public BlockPos mark(String key) {
        BlockPos p = marks.get(key);
        if (p == null) {
            throw new IllegalStateException(name + " mark missing: " + key);
        }
        return p;
    }

    public boolean has(String key) {
        return marks.containsKey(key);
    }

    // 手元の印の名前が prefix で始まるもの
    public List<String> marksStarting(String prefix) {
        List<String> out = new ArrayList<>();
        for (String k : marks.keySet()) {
            if (k.startsWith(prefix)) {
                out.add(k);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    // origin へずらして流し込む (違うブロックだけ置く)。置いた数
    public int pour(ServerLevel level, BlockPos origin) {
        long t0 = System.currentTimeMillis();
        for (int cx = (origin.getX() + x0) >> 4; cx <= (origin.getX() + x1) >> 4; cx++) {
            for (int cz = (origin.getZ() + z0) >> 4; cz <= (origin.getZ() + z1) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int[] r : runs) {
            int y = r[0] + origin.getY();
            int z = r[1] + origin.getZ();
            int x = r[2] + origin.getX();
            for (int i = 3; i + 1 < r.length; i += 2) {
                int idx = r[i];
                int len = r[i + 1];
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
        SkyAbyss.LOGGER.info("[tower] {} poured at {}: {} blocks in {} ms", name, origin.toShortString(), placed, System.currentTimeMillis() - t0);
        return placed;
    }
}
