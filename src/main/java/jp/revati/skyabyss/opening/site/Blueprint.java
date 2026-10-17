package jp.revati.skyabyss.opening.site;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

// 構造物の部品 (data/skyabyss/opening/<名前>.json)。書くのは tools/rough/opening_sites.py (ラフと同じブロックの配置)。
// 座標は構造物の中心が (0, 0)、y=0 が最初の段 (地面の上面は y=-1)。向きを変えて置ける (回す・左右を反転)
public final class Blueprint {
    // 一つのブロック (部品の座標)
    public record Cell(int x, int y, int z, BlockState state) {
    }

    private static final Map<String, Blueprint> CACHE = new HashMap<>();

    public final String name;
    public final List<Cell> cells = new ArrayList<>();
    public final Map<String, int[]> marks = new HashMap<>();
    // 地形を整える水平の範囲 (部品の座標。両端を含む)
    public int rx0, rz0, rx1, rz1;
    public boolean level;
    public boolean anchor;
    public boolean water;
    public int ring = 3;
    public int minY;
    public int maxY;
    // 地面に沿って掘る・盛る列 (大きな足跡): {x, z, kind, a, b, c}。kind 0 = 掘る [深さ, 底のブロック (palette の番号), 雨水か]、
    // 1 = 盛る [高さ, 上のブロック, 中のブロック]、2 = 物を立てる [高さ, ブロック]
    public final List<int[]> relief = new ArrayList<>();
    public final List<BlockState> palette = new ArrayList<>();
    // 骨など: 部品のブロックの近く (この距離まで) の木と草だけを片付ける。0 なら範囲の全部 (relief のときは relief の列の 1 まわり)
    public int clearNear;

    private Blueprint(String name) {
        this.name = name;
    }

    public static synchronized Blueprint get(String name) {
        return CACHE.computeIfAbsent(name, Blueprint::load);
    }

    private static Blueprint load(String name) {
        String path = "/data/" + SkyAbyss.MODID + "/opening/" + name + ".json";
        try (InputStream in = Blueprint.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing blueprint " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Blueprint bp = new Blueprint(name);
            List<BlockState> palette = bp.palette;
            for (JsonElement e : json.getAsJsonArray("palette")) {
                palette.add(parse(e.getAsString()));
            }
            bp.minY = Integer.MAX_VALUE;
            bp.maxY = Integer.MIN_VALUE;
            for (JsonElement e : json.getAsJsonArray("blocks")) {
                JsonArray a = e.getAsJsonArray();
                int y = a.get(1).getAsInt();
                bp.cells.add(new Cell(a.get(0).getAsInt(), y, a.get(2).getAsInt(), palette.get(a.get(3).getAsInt())));
                bp.minY = Math.min(bp.minY, y);
                bp.maxY = Math.max(bp.maxY, y);
            }
            if (bp.cells.isEmpty()) {
                bp.minY = 0;
                bp.maxY = 0;
            }
            if (json.has("relief")) {
                for (JsonElement e : json.getAsJsonArray("relief")) {
                    JsonArray a = e.getAsJsonArray();
                    bp.relief.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt()});
                }
            }
            // 低い段から (支えになる物が先)
            bp.cells.sort((p, q) -> Integer.compare(p.y(), q.y()));
            for (Map.Entry<String, JsonElement> m : json.getAsJsonObject("marks").entrySet()) {
                JsonArray a = m.getValue().getAsJsonArray();
                bp.marks.put(m.getKey(), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
            }
            JsonObject meta = json.getAsJsonObject("meta");
            JsonArray r = meta.getAsJsonArray("rect");
            bp.rx0 = r.get(0).getAsInt();
            bp.rz0 = r.get(1).getAsInt();
            bp.rx1 = r.get(2).getAsInt();
            bp.rz1 = r.get(3).getAsInt();
            bp.level = meta.has("level") && meta.get("level").getAsBoolean();
            bp.anchor = meta.has("anchor") && meta.get("anchor").getAsBoolean();
            bp.water = meta.has("water") && meta.get("water").getAsBoolean();
            if (meta.has("height")) {
                bp.maxY = Math.max(bp.maxY, meta.get("height").getAsInt());
            }
            if (meta.has("clearNear")) {
                bp.clearNear = meta.get("clearNear").getAsInt();
            }
            if (meta.has("ring")) {
                bp.ring = meta.get("ring").getAsInt();
            }
            return bp;
        } catch (Exception e) {
            throw new IllegalStateException("could not read blueprint " + name, e);
        }
    }

    private static BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("bad block state in a blueprint: " + s, e);
        }
    }

    // ---------------------------------------------------------------- 向き
    // 左右を反転 (x → -x) してから、時計回りに rot 回 (90 度ずつ) 回す
    public static BlockPos transform(int x, int y, int z, int rot, boolean mirror, BlockPos origin) {
        if (mirror) {
            x = -x;
        }
        int rx = x;
        int rz = z;
        for (int i = 0; i < (rot & 3); i++) {
            int nx = -rz;
            int nz = rx;
            rx = nx;
            rz = nz;
        }
        return origin.offset(rx, y, rz);
    }

    public static BlockState transform(BlockState state, int rot, boolean mirror) {
        if (mirror) {
            state = state.mirror(Mirror.FRONT_BACK);
        }
        return state.rotate(switch (rot & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        });
    }

    @Nullable
    public BlockPos mark(String key, int rot, boolean mirror, BlockPos origin) {
        int[] m = marks.get(key);
        return m == null ? null : transform(m[0], m[1], m[2], rot, mirror, origin);
    }

    // 地形を整える範囲の、向きを変えたあとの外接の長方形 (世界の座標、両端を含む) = {x0, z0, x1, z1}
    public int[] worldRect(int rot, boolean mirror, BlockPos origin) {
        BlockPos a = transform(rx0, 0, rz0, rot, mirror, origin);
        BlockPos b = transform(rx1, 0, rz1, rot, mirror, origin);
        return new int[] {Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ())};
    }
}
