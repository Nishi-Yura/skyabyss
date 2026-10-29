package jp.revati.skyabyss.story.ch6.core;

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
import jp.revati.skyabyss.story.ch6.valley.StarGateBlock;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 六章「月の核」(docs/design/46 の 4.7)。
//   核の間は tools/gen/world/build_moon_core.py が彫って data/skyabyss/moon/core.json に書く (月の奥の球の空洞と浮く床)。ここはそれを流し込み、
//   着く所の北に核の門 (黒石の台と星の門) を置き、床の縁に帰り口を置く。一度だけ (MoonWorld.build の版で数える)
public final class MoonCore {
    private static final String PATH = "/data/" + SkyAbyss.MODID + "/moon/core.json";
    // 床の真ん中からの、核の間の広さ
    public static final double ARENA_ZONE = 18;
    private static Shape shape;

    public record Shape(Map<String, BlockPos> marks, int x0, int z0, int x1, int z1, Vec3 center, double radius, int floorY, int floorR) {
        public BlockPos mark(String key) {
            BlockPos p = marks.get(key);
            if (p == null) {
                throw new IllegalStateException("core mark missing: " + key);
            }
            return p;
        }
    }

    private MoonCore() {
    }

    public static synchronized Shape shape() {
        if (shape == null) {
            JsonObject json = read();
            Map<String, BlockPos> marks = new HashMap<>();
            for (Map.Entry<String, JsonElement> m : json.getAsJsonObject("marks").entrySet()) {
                JsonArray a = m.getValue().getAsJsonArray();
                marks.put(m.getKey(), new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            JsonArray r = json.getAsJsonArray("rect");
            JsonArray c = json.getAsJsonArray("center");
            JsonArray f = json.getAsJsonArray("floor");
            shape = new Shape(Map.copyOf(marks), r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt(),
                    new Vec3(c.get(0).getAsDouble(), c.get(1).getAsDouble(), c.get(2).getAsDouble()), json.get("radius").getAsDouble(),
                    f.get(0).getAsInt(), f.get(1).getAsInt());
        }
        return shape;
    }

    public static BlockPos mark(String key) {
        return shape().mark(key);
    }

    // 核の間の球の内 (着く所の地面の下)
    public static boolean inChamber(Vec3 p) {
        Shape s = shape();
        return p.distanceTo(s.center()) <= s.radius() + 1 && p.y < 2;
    }

    public static boolean inArenaZone(Vec3 p) {
        BlockPos c = mark("arena_center");
        return Math.hypot(p.x - (c.getX() + 0.5), p.z - (c.getZ() + 0.5)) <= ARENA_ZONE && inChamber(p);
    }

    // 核の門のそば (着く所の北)
    public static boolean nearGate(BlockPos p) {
        return p.closerThan(mark("core_gate"), 6);
    }

    private static JsonObject read() {
        try (InputStream in = MoonCore.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException("missing " + PATH);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not read " + PATH, e);
        }
    }

    // ---------------------------------------------------------------- 流し込む (一度だけ)
    public static void build(ServerLevel level) {
        long t0 = System.currentTimeMillis();
        JsonObject json = read();
        List<BlockState> palette = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("palette")) {
            palette.add(parse(e.getAsString()));
        }
        Shape s = shape();
        for (int cx = s.x0 >> 4; cx <= s.x1 >> 4; cx++) {
            for (int cz = s.z0 >> 4; cz <= s.z1 >> 4; cz++) {
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
                BlockState st = palette.get(a.get(i).getAsInt());
                int len = a.get(i + 1).getAsInt();
                for (int k = 0; k < len; k++) {
                    m.set(x + k, y, z);
                    if (level.getBlockState(m) != st) {
                        level.setBlock(m, st, 2 | 16);
                        placed++;
                    }
                }
                x += len;
            }
        }
        furnish(level);
        SkyAbyss.LOGGER.info("[moon] core chamber built: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
    }

    private static BlockState parse(String id) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("bad block state in core.json: " + id, e);
        }
    }

    // ---------------------------------------------------------------- 核の門 (着く所の北) と帰り口
    static void furnish(ServerLevel level) {
        BlockPos g = mark("core_gate");
        // 黒石の台 (門の前後 6 × 9)。台の上は空ける
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 3; z++) {
                BlockPos f = g.offset(x, -1, z);
                level.setBlock(f, (x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                level.setBlock(f.below(), Blocks.TUFF.defaultBlockState(), 3);
                for (int y = 0; y < 6; y++) {
                    level.setBlock(g.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 枠 (左右の柱と上の梁) と星の門 3 × 4
        for (int y = 0; y < 5; y++) {
            level.setBlock(g.offset(-2, y, 0), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
            level.setBlock(g.offset(2, y, 0), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
        }
        for (int x = -2; x <= 2; x++) {
            level.setBlock(g.offset(x, 4, 0), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 3);
        }
        for (int w = -1; w <= 1; w++) {
            for (int y = 0; y < 4; y++) {
                level.setBlock(g.offset(w, y, 0), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, w == 0 && y == 1), 3);
            }
        }
        for (int[] o : new int[][] {{-4, 3}, {4, 3}}) {
            BlockPos p = g.offset(o[0], 0, o[1]);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
        // 床の縁の帰り口
        BlockPos back = mark("arena_back");
        level.setBlock(back, ValleyBlocks.STAR_GATE.get().defaultBlockState(), 3);
        level.setBlock(back.above(), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, true), 3);
    }

    // 着く所の門のブロックか (核の門の 3 × 4)
    public static boolean isLandingGate(BlockPos p) {
        BlockPos g = mark("core_gate");
        return p.getZ() == g.getZ() && Math.abs(p.getX() - g.getX()) <= 1 && p.getY() >= g.getY() && p.getY() < g.getY() + 4;
    }

    public static boolean isBackGate(BlockPos p) {
        BlockPos b = mark("arena_back");
        return p.getX() == b.getX() && p.getZ() == b.getZ() && (p.getY() == b.getY() || p.getY() == b.getY() + 1);
    }
}
