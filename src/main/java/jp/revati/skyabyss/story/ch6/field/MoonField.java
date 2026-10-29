package jp.revati.skyabyss.story.ch6.field;

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

// 六章 区画 3「満月の野」(docs/design/46 の 4.5)。
//   地形は tools/gen/world/build_moon_field.py が彫って data/skyabyss/moon/field.json に書く (野の列。空気も含めて)。ここはそれを流し込み、
//   印の所に仕掛け (欠片の台・星の門・月の溜まり) と野営の物を置き、満ち引きの石 (tide の並び) を今の満ち引きで置く (FieldTide)。
//   一度だけ (MoonWorld.build の版で数える)。印の座標は月の世界の座標そのまま (着く所 = (0, 12, 0))
public final class MoonField {
    private static final String PATH = "/data/" + SkyAbyss.MODID + "/moon/field.json";
    // 満月の台 (半径 20) と主の間 (半径 15、床 y 30)
    public static final double MESA_ZONE = 21;
    public static final double ARENA_ZONE = 18;
    public static final int ARENA_FALL_Y = 26;
    public static final double POND_RADIUS = 30;
    public static final int POND_FALL_Y = 4;
    private static Shape shape;

    // 満ち引きの石 (kind 0 = 光の石 / 1 = 影の石)
    public record Stone(BlockPos pos, boolean shade) {
    }

    public record Shape(Map<String, BlockPos> marks, List<Stone> stones, int x0, int z0, int x1, int z1) {
        public BlockPos mark(String key) {
            BlockPos p = marks.get(key);
            if (p == null) {
                throw new IllegalStateException("field mark missing: " + key);
            }
            return p;
        }
    }

    private MoonField() {
    }

    public static synchronized Shape shape() {
        if (shape == null) {
            JsonObject json = read();
            Map<String, BlockPos> marks = new HashMap<>();
            for (Map.Entry<String, JsonElement> m : json.getAsJsonObject("marks").entrySet()) {
                JsonArray a = m.getValue().getAsJsonArray();
                marks.put(m.getKey(), new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            List<Stone> stones = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("tide")) {
                JsonArray a = e.getAsJsonArray();
                stones.add(new Stone(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()), a.get(3).getAsInt() == 1));
            }
            JsonArray r = json.getAsJsonArray("rect");
            shape = new Shape(Map.copyOf(marks), List.copyOf(stones), r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt());
        }
        return shape;
    }

    public static BlockPos mark(String key) {
        return shape().mark(key);
    }

    public static List<Stone> stones() {
        return shape().stones();
    }

    // 野の範囲の中か (水平だけ)
    public static boolean inside(BlockPos p) {
        Shape s = shape();
        return p.getX() >= s.x0 && p.getX() <= s.x1 && p.getZ() >= s.z0 && p.getZ() <= s.z1;
    }

    static double flat(Vec3 p, BlockPos c) {
        return Math.hypot(p.x - (c.getX() + 0.5), p.z - (c.getZ() + 0.5));
    }

    // 満月の台の上 (主の間とまわりの縁)
    public static boolean inArenaZone(Vec3 p) {
        return flat(p, mark("arena_center")) <= ARENA_ZONE;
    }

    // 満月の台の柱の中 (根元から見て)
    public static boolean onMesa(Vec3 p) {
        return flat(p, mark("arena_center")) <= MESA_ZONE;
    }

    // 満ち引きの池の穴の上
    public static boolean inPond(Vec3 p) {
        return flat(p, mark("shard_pond")) <= POND_RADIUS;
    }

    // 降る光の溝の中 (溝の底と覆いの下の台、両側の尾根の上まで。尾根を歩いて越そうとしても光が降る)
    public static boolean inGully(Vec3 p) {
        BlockPos e = mark("gully_east");
        BlockPos w = mark("shard_gully");
        return p.x >= w.getX() - 3 && p.x <= e.getX() + 8 && Math.abs(p.z - (e.getZ() + 0.5)) <= 11 && p.y < e.getY() + 32;
    }

    // 影の塔の中
    public static boolean inTower(Vec3 p) {
        BlockPos t = mark("shard_tower");
        return flat(p, t) <= 8.5;
    }

    private static JsonObject read() {
        try (InputStream in = MoonField.class.getResourceAsStream(PATH)) {
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
        SkyAbyss.LOGGER.info("[moon] full moon field built: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
    }

    private static BlockState parse(String id) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("bad block state in field.json: " + id, e);
        }
    }

    // ---------------------------------------------------------------- 印の所の仕掛けと野営の物
    static void furnish(ServerLevel level) {
        for (String site : sites()) {
            level.setBlock(mark("shard_" + site), ValleyBlocks.STAR_PEDESTAL.get().defaultBlockState(), 3);
        }
        FieldTide.placeAll(level);
        // 門 (台地の東の根元。南北に 3 幅、高さ 4)
        BlockPos gate = mark("field_gate");
        for (int w = -1; w <= 1; w++) {
            for (int y = 0; y < 4; y++) {
                level.setBlock(gate.offset(0, y, w), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, w == 0 && y == 1), 3);
            }
        }
        // 主の間の帰り口 (台の東の縁)
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
        tent(level, camp.offset(-6, 0, -2));
        BlockPos bed = camp.offset(-2, 0, 1);
        level.setBlock(bed, Blocks.WHITE_CARPET.defaultBlockState(), 3);
        level.setBlock(bed.east(), Blocks.WHITE_CARPET.defaultBlockState(), 3);
        level.setBlock(camp.offset(1, 0, -2), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(camp.offset(2, 0, -2), Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
        level.setBlock(camp.offset(2, 1, -2), Blocks.DECORATED_POT.defaultBlockState(), 3);
        // 前の自分の数え書き (書見台と、刻みをつけた石の板)
        level.setBlock(camp.offset(-3, 0, 4), Blocks.LECTERN.defaultBlockState(), 3);
        level.setBlock(camp.offset(-4, 0, 4), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 3);
        level.setBlock(camp.offset(0, 0, 3), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
        for (int[] o : new int[][] {{5, 4}, {-7, 3}, {4, -4}}) {
            BlockPos p = camp.offset(o[0], 0, o[1]);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
    }

    private static void tent(ServerLevel level, BlockPos at) {
        for (int z = 0; z < 4; z++) {
            level.setBlock(at.offset(0, 0, z), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(2, 0, z), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(1, 0, z), z == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(at.offset(1, 1, z), Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
        }
        level.setBlock(at.offset(1, 0, 2), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
    }

    public static List<String> sites() {
        return List.of("pond", "gully", "tower");
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
}
