package jp.revati.skyabyss.story.ch6.cliff;

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

// 六章 区画 2「上弦の崖」(docs/design/46 の 4.4・4.4.1 の配置 A 弧の登り)。
//   地形は tools/gen/world/build_moon_cliff.py が彫って data/skyabyss/moon/cliff.json に書く (崖の列だけ。空気も含めて)。ここはそれを流し込み、
//   印の所に仕掛け (欠片の台・崩れる棚・錘の山・星の門) と野営の物を置き、天秤の皿を組む (CliffBalance.reset)。一度だけ (MoonWorld.build の版で数える)。
//   印の座標は月の世界の座標そのまま (着く所 = (0, 12, 0))。弧の中心 (160, 50)・半径 100
public final class MoonCliff {
    private static final String PATH = "/data/" + SkyAbyss.MODID + "/moon/cliff.json";
    public static final double ARC_X = 160;
    public static final double ARC_Z = 50;
    // 皿 (半径 14) と堀 (18 まで)。その外の頂の輪は含めない
    public static final double ARENA_ZONE = 18;
    public static final int ARENA_FALL_Y = 74;
    private static Shape shape;

    public record Shape(Map<String, BlockPos> marks, int x0, int z0, int x1, int z1) {
        public BlockPos mark(String key) {
            BlockPos p = marks.get(key);
            if (p == null) {
                throw new IllegalStateException("cliff mark missing: " + key);
            }
            return p;
        }
    }

    private MoonCliff() {
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
            shape = new Shape(Map.copyOf(marks), r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt());
        }
        return shape;
    }

    public static BlockPos mark(String key) {
        return shape().mark(key);
    }

    // 崖の範囲の中か (水平だけ)
    public static boolean inside(BlockPos p) {
        Shape s = shape();
        return p.getX() >= s.x0 && p.getX() <= s.x1 && p.getZ() >= s.z0 && p.getZ() <= s.z1;
    }

    // 主の間のまわり (皿と堀。水平の距離)
    public static boolean inArenaZone(Vec3 p) {
        BlockPos c = mark("arena_center");
        return Math.hypot(p.x - (c.getX() + 0.5), p.z - (c.getZ() + 0.5)) <= ARENA_ZONE;
    }

    private static JsonObject read() {
        try (InputStream in = MoonCliff.class.getResourceAsStream(PATH)) {
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
        SkyAbyss.LOGGER.info("[moon] waxing cliffs built: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
    }

    private static BlockState parse(String id) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("bad block state in cliff.json: " + id, e);
        }
    }

    // ---------------------------------------------------------------- 印の所の仕掛けと野営の物
    static void furnish(ServerLevel level) {
        for (String site : sites()) {
            level.setBlock(mark("shard_" + site), ValleyBlocks.STAR_PEDESTAL.get().defaultBlockState(), 3);
        }
        for (BlockPos p : ledgeBlocks()) {
            level.setBlock(p, CliffBlocks.CRUMBLING_LEDGE.get().defaultBlockState(), 3);
        }
        level.setBlock(mark("weight_pile"), CliffBlocks.WEIGHT_PILE.get().defaultBlockState(), 3);
        CliffBalance.reset(level);
        // 頂の門 (道を横切る向き。弧の半径の向きに 3 幅、高さ 4)
        BlockPos gate = mark("cliff_gate");
        double rx = gate.getX() - ARC_X;
        double rz = gate.getZ() - ARC_Z;
        double rl = Math.hypot(rx, rz);
        for (int w = -1; w <= 1; w++) {
            for (int y = 0; y < 4; y++) {
                BlockPos p = gate.offset((int) Math.round(rx / rl * w), y, (int) Math.round(rz / rl * w));
                level.setBlock(p, ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, w == 0 && y == 1), 3);
            }
        }
        // 主の間の帰り口 (皿の縁。右クリックで門の前へ下りる)
        BlockPos back = mark("arena_back");
        level.setBlock(back, ValleyBlocks.STAR_GATE.get().defaultBlockState(), 3);
        level.setBlock(back.above(), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, true), 3);
        // 野営: 帰りの台・月の溜まり (星の溜まりと同じ)・天幕と寝具と荷・灯り
        BlockPos camp = mark("camp");
        level.setBlock(mark("return_stone"), Blocks.LODESTONE.defaultBlockState(), 3);
        for (int[] o : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos sp = mark("star_pool").offset(o[0], 1, o[1]);
            if (level.getBlockState(sp).isAir()) {
                level.setBlock(sp, ValleyBlocks.STAR_POOL.get().defaultBlockState(), 3);
            }
        }
        tent(level, camp.offset(-6, 0, -4));
        BlockPos bed = camp.offset(-3, 0, 1);
        level.setBlock(bed, Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
        level.setBlock(bed.east(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
        level.setBlock(camp.offset(-2, 0, -3), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(camp.offset(-1, 0, -3), Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
        level.setBlock(camp.offset(-2, 1, -3), Blocks.DECORATED_POT.defaultBlockState(), 3);
        // 登りの綱の束 (前の自分の荷。鎖を巻いた杭)
        level.setBlock(camp.offset(2, 0, -3), Blocks.CHAIN.defaultBlockState(), 3);
        level.setBlock(camp.offset(2, 1, -3), Blocks.CHAIN.defaultBlockState(), 3);
        level.setBlock(camp.offset(0, 0, 2), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
        for (int[] o : new int[][] {{4, 3}, {-6, 2}, {3, -2}}) {
            BlockPos p = camp.offset(o[0], 0, o[1]);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
    }

    private static void tent(ServerLevel level, BlockPos at) {
        for (int z = 0; z < 4; z++) {
            level.setBlock(at.offset(0, 0, z), Blocks.GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(2, 0, z), Blocks.GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(1, 0, z), z == 0 ? Blocks.GRAY_WOOL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(at.offset(1, 1, z), Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
        }
        level.setBlock(at.offset(1, 0, 2), Blocks.WHITE_CARPET.defaultBlockState(), 3);
    }

    // 崩れる棚の並び (印 ledge_a から ledge_b へ、同じ高さ。一つずつ順に)
    public static List<BlockPos> ledgeBlocks() {
        BlockPos a = mark("ledge_a");
        BlockPos b = mark("ledge_b");
        List<BlockPos> out = new ArrayList<>();
        int n = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ()));
        BlockPos last = null;
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            BlockPos p = new BlockPos((int) Math.round(a.getX() + (b.getX() - a.getX()) * t), a.getY(), (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
            if (!p.equals(last)) {
                // 斜めに飛ぶ所は角を足して、足場が途切れないように
                if (last != null && last.getX() != p.getX() && last.getZ() != p.getZ()) {
                    out.add(new BlockPos(p.getX(), p.getY(), last.getZ()));
                }
                out.add(p);
                last = p;
            }
        }
        return out;
    }

    public static List<String> sites() {
        return List.of("tower", "ledge", "balance");
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
