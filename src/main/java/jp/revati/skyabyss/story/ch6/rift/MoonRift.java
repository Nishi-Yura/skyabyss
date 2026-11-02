package jp.revati.skyabyss.story.ch6.rift;

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
import jp.revati.skyabyss.story.ch6.valley.ShadowBridgeBlock;
import jp.revati.skyabyss.story.ch6.valley.StarGateBlock;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 六章 区画 4「下弦の淵」(docs/design/46 の 4.6)。
//   地形は tools/gen/world/build_moon_rift.py が彫って data/skyabyss/moon/rift.json に書く (淵の列。空気も含めて)。ここはそれを流し込み、
//   印の所に仕掛け (欠片の台・星の門・月の溜まり・下弦の鏡・残像の板・格子の門・影の橋) と野営の物を置く。
//   一度だけ (MoonWorld.build の版で数える)。印の座標は月の世界の座標そのまま
public final class MoonRift {
    private static final String PATH = "/data/" + SkyAbyss.MODID + "/moon/rift.json";
    // 主の間 (柱の上の丸い床、半径 15・床 y 0) と、門を通らずに入れない広さ
    public static final double ARENA_ZONE = 17;
    // 淵へ落ちた (帯の中で縁より 8 低い)
    public static final int FALL_Y = 3;
    private static Shape shape;

    public record Shape(Map<String, BlockPos> marks, List<BlockPos> shadow, int x0, int z0, int x1, int z1, double cx, double cz, double rIn,
                        double rOut, double phiMax) {
        public BlockPos mark(String key) {
            BlockPos p = marks.get(key);
            if (p == null) {
                throw new IllegalStateException("rift mark missing: " + key);
            }
            return p;
        }
    }

    private MoonRift() {
    }

    public static synchronized Shape shape() {
        if (shape == null) {
            JsonObject json = read();
            Map<String, BlockPos> marks = new HashMap<>();
            for (Map.Entry<String, JsonElement> m : json.getAsJsonObject("marks").entrySet()) {
                JsonArray a = m.getValue().getAsJsonArray();
                marks.put(m.getKey(), new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            List<BlockPos> shadow = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("shadow")) {
                JsonArray a = e.getAsJsonArray();
                shadow.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            JsonArray r = json.getAsJsonArray("rect");
            JsonArray c = json.getAsJsonArray("center");
            JsonArray rad = json.getAsJsonArray("radii");
            shape = new Shape(Map.copyOf(marks), List.copyOf(shadow), r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt(),
                    c.get(0).getAsDouble(), c.get(1).getAsDouble(), rad.get(0).getAsDouble(), rad.get(1).getAsDouble(), json.get("phi_max").getAsDouble());
        }
        return shape;
    }

    public static BlockPos mark(String key) {
        return shape().mark(key);
    }

    public static List<BlockPos> shadow() {
        return shape().shadow();
    }

    // 淵の範囲の中か (水平だけ)
    public static boolean inside(BlockPos p) {
        Shape s = shape();
        return p.getX() >= s.x0 && p.getX() <= s.x1 && p.getZ() >= s.z0 && p.getZ() <= s.z1;
    }

    public static boolean inside(Vec3 p) {
        return inside(BlockPos.containing(p));
    }

    static double flat(Vec3 p, BlockPos c) {
        return Math.hypot(p.x - (c.getX() + 0.5), p.z - (c.getZ() + 0.5));
    }

    // 淵の帯の上 (縁の内と外の間。弧の端の外は帯でない)
    public static boolean overChasm(Vec3 p) {
        Shape s = shape();
        double r = Math.hypot(p.x - s.cx, p.z - s.cz);
        double phi = Math.toDegrees(Math.atan2(p.z - s.cz, p.x - s.cx));
        return r > s.rIn + 0.5 && r < s.rOut - 0.5 && Math.abs(phi) < s.phiMax + 4;
    }

    // 主の間 (柱の上の床とまわり)
    public static boolean inArenaZone(Vec3 p) {
        return flat(p, mark("arena_center")) <= ARENA_ZONE;
    }

    // 灯りの回廊 (淵の中の棚と入口の段の下の半分。棚の下 8 まで)
    public static boolean inHall(Vec3 p) {
        BlockPos f = mark("hall_floor");
        return overChasm(p) && p.y > f.getY() - 8 && p.y < f.getY() + 16 && hallPhi(p);
    }

    private static boolean hallPhi(Vec3 p) {
        Shape s = shape();
        double phi = Math.toDegrees(Math.atan2(p.z - s.cz, p.x - s.cx));
        double r = Math.hypot(p.x - s.cx, p.z - s.cz);
        return phi > 85 && phi < s.phiMax + 2 && r < s.rIn + 8;
    }

    private static JsonObject read() {
        try (InputStream in = MoonRift.class.getResourceAsStream(PATH)) {
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
        SkyAbyss.LOGGER.info("[moon] waning rift built: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
    }

    private static BlockState parse(String id) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("bad block state in rift.json: " + id, e);
        }
    }

    // ---------------------------------------------------------------- 印の所の仕掛けと野営の物
    static void furnish(ServerLevel level) {
        for (String site : sites()) {
            level.setBlock(mark("shard_" + site), ValleyBlocks.STAR_PEDESTAL.get().defaultBlockState(), 3);
        }
        // 下弦の鏡 (野営の練習・板の門・渡り) と残像の板
        for (String k : List.of("camp_mirror", "door_mirror", "leap_mirror")) {
            level.setBlock(mark(k), RiftBlocks.ECHO_MIRROR.get().defaultBlockState(), 3);
        }
        for (String k : List.of("camp_plate", "door_plate_a", "door_plate_b")) {
            level.setBlock(mark(k), RiftBlocks.ECHO_PLATE.get().defaultBlockState(), 3);
        }
        RiftTicker.door(level, false);
        // 影の橋 (谷の黒い硝子。暗がりでだけ足場。RiftTicker が星灯りで決め直す)
        for (BlockPos p : shadow()) {
            level.setBlock(p, ValleyBlocks.SHADOW_BRIDGE.get().defaultBlockState().setValue(ShadowBridgeBlock.SOLID, true), 3);
        }
        // 主の間へ下りる門 (内の縁。南北に 3 幅、高さ 4)
        BlockPos gate = mark("rift_gate");
        for (int w = -1; w <= 1; w++) {
            for (int y = 0; y < 4; y++) {
                level.setBlock(gate.offset(0, y, w), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, w == 0 && y == 1), 3);
            }
        }
        // 主の間の帰り口 (床の南の縁)
        BlockPos back = mark("arena_back");
        level.setBlock(back, ValleyBlocks.STAR_GATE.get().defaultBlockState(), 3);
        level.setBlock(back.above(), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, true), 3);
        // 野営: 帰りの台・月の溜まり・天幕と寝具と荷・灯り
        BlockPos camp = mark("camp");
        level.setBlock(mark("return_stone"), Blocks.LODESTONE.defaultBlockState(), 3);
        for (int[] o : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos sp = mark("star_pool").offset(o[0], 1, o[1]);
            if (level.getBlockState(sp).isAir()) {
                level.setBlock(sp, ValleyBlocks.STAR_POOL.get().defaultBlockState(), 3);
            }
        }
        tent(level, camp.offset(-7, 0, 2));
        BlockPos bed = camp.offset(-3, 0, -3);
        level.setBlock(bed, Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
        level.setBlock(bed.east(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
        level.setBlock(camp.offset(2, 0, 4), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(camp.offset(3, 0, 4), Blocks.DECORATED_POT.defaultBlockState(), 3);
        // 前の自分の写し (割れた鏡を立てかけた書見台と黒石の塀)
        level.setBlock(camp.offset(-4, 0, 3), Blocks.LECTERN.defaultBlockState(), 3);
        level.setBlock(camp.offset(1, 0, -3), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
        for (int[] o : new int[][] {{6, -3}, {-6, -5}, {5, 6}}) {
            BlockPos p = camp.offset(o[0], 0, o[1]);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
    }

    private static void tent(ServerLevel level, BlockPos at) {
        for (int x = 0; x < 4; x++) {
            level.setBlock(at.offset(x, 0, 0), Blocks.GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(x, 0, 2), Blocks.GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(x, 0, 1), x == 0 ? Blocks.GRAY_WOOL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(at.offset(x, 1, 1), Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
        }
        level.setBlock(at.offset(2, 0, 1), Blocks.GRAY_CARPET.defaultBlockState(), 3);
    }

    public static List<String> sites() {
        return List.of("door", "leap", "hall");
    }

    @Nullable
    public static String siteOf(BlockPos pedestal) {
        for (String site : sites()) {
            if (mark("shard_" + site).equals(pedestal)) {
                return site;
            }
        }
        return null;
    }

    // 落ちた人を戻す所: 一番近い場所の入口 (野営・板の門・渡り・回廊・門の前)
    public static BlockPos nearestEntry(Vec3 p) {
        BlockPos best = mark("camp");
        double bd = Double.MAX_VALUE;
        for (String k : List.of("camp", "door_entry", "leap_entry", "hall_entry", "gate_front")) {
            BlockPos e = mark(k);
            double d = flat(p, e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    static Direction faceToward(BlockPos from, BlockPos to) {
        return Direction.getNearest(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
    }
}
