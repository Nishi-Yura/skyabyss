package jp.revati.skyabyss.story.ch6.valley;

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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 六章 区画 1「新月の谷」(docs/design/46 の 4.3・4.3.1 の配置 C 輪の谷)。
//   地形は tools/gen/world/build_moon_valley.py が彫って data/skyabyss/moon/valley.json に書く (谷の列だけ。空気も含めて)。ここはそれを流し込み、
//   印の所に仕掛けのブロック (影の橋・鏡の石・欠片の台・星の門・灯りの台) と野営の物を置く。一度だけ (MoonWorld.build の版で数える)。
//   印の座標は月の世界の座標そのまま (着く所 = (0, 12, 0))
public final class MoonValley {
    private static final String PATH = "/data/" + SkyAbyss.MODID + "/moon/valley.json";
    private static Shape shape;

    // 谷の形 (印と範囲)。流し込む並びは build のときだけ読む
    public record Shape(Map<String, BlockPos> marks, int x0, int z0, int x1, int z1) {
        public BlockPos mark(String key) {
            BlockPos p = marks.get(key);
            if (p == null) {
                throw new IllegalStateException("valley mark missing: " + key);
            }
            return p;
        }
    }

    private MoonValley() {
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

    // 谷の範囲の中か (水平だけ)
    public static boolean inside(BlockPos p) {
        Shape s = shape();
        return p.getX() >= s.x0 && p.getX() <= s.x1 && p.getZ() >= s.z0 && p.getZ() <= s.z1;
    }

    private static JsonObject read() {
        try (InputStream in = MoonValley.class.getResourceAsStream(PATH)) {
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
        // 下の段から (砂が支えのある所に乗る)。並びは y の小さい順に書いてある
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
                        // 2 = 見る人へ送る、16 = 隣へ知らせない (砂を落とさない・水を流さない)
                        level.setBlock(m, st, 2 | 16);
                        placed++;
                    }
                }
                x += len;
            }
        }
        furnish(level);
        SkyAbyss.LOGGER.info("[moon] new-moon valley built: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
    }

    private static BlockState parse(String id) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("bad block state in valley.json: " + id, e);
        }
    }

    // ---------------------------------------------------------------- 印の所の仕掛けと野営の物
    static void furnish(ServerLevel level) {
        // 影の橋: 両岸の端から柱の台まで、幅 3
        BlockPos n = mark("bridge_n");
        BlockPos sth = mark("bridge_s");
        BlockPos pillar = mark("shard_bridge").below();
        for (BlockPos end : new BlockPos[] {n, sth}) {
            int dir = Integer.signum(pillar.getZ() - end.getZ());
            for (int z = end.getZ(); z != pillar.getZ(); z += dir) {
                if (Math.abs(z - pillar.getZ()) <= 2) {
                    break;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    BlockPos p = new BlockPos(end.getX() + dx, pillar.getY(), z);
                    if (level.getBlockState(p).isAir()) {
                        // 真ん中の列は新月でなくても足場、両脇は新月のときだけ (位相が合うと広い)
                        level.setBlock(p, ValleyBlocks.SHADOW_BRIDGE.get().defaultBlockState().setValue(ShadowBridgeBlock.WIDE, dx != 0), 2);
                    }
                }
            }
        }
        // 欠片の台 3
        for (String site : new String[] {"bridge", "mirror", "hole"}) {
            level.setBlock(mark("shard_" + site), ValleyBlocks.STAR_PEDESTAL.get().defaultBlockState(), 3);
        }
        // 鏡の石 3 (はじめは窪みの真ん中を向かない)
        BlockPos center = mark("shard_mirror");
        for (int i = 0; i < 3; i++) {
            BlockPos p = mark("mirror_" + i);
            Direction want = MirrorStoneBlock.toward(p, center);
            level.setBlock(p, ValleyBlocks.MIRROR_STONE.get().defaultBlockState().setValue(MirrorStoneBlock.FACING, want.getClockWise()), 3);
        }
        // 星の門 (台地の北の根元)
        BlockPos gate = mark("valley_gate");
        for (int y = 0; y < 4; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                level.setBlock(gate.offset(dx, y, 0), ValleyBlocks.STAR_GATE.get().defaultBlockState()
                        .setValue(StarGateBlock.CORE, dx == 0 && y == 1), 3);
            }
        }
        // 台地の上の帰り口 (主の間の着く所の北。右クリックで門の前へ下りる)
        BlockPos back = mark("arena_entry").offset(0, 0, -2);
        level.setBlock(back, ValleyBlocks.STAR_GATE.get().defaultBlockState(), 3);
        level.setBlock(back.above(), ValleyBlocks.STAR_GATE.get().defaultBlockState().setValue(StarGateBlock.CORE, true), 3);
        // 主の間の灯りの台 6
        for (int i = 0; i < 6; i++) {
            level.setBlock(mark("lamp_" + i), ValleyBlocks.LAMP_POST.get().defaultBlockState(), 3);
        }
        // 野営: 帰りの台・星の溜まりの印・天幕と寝具と荷・灯り
        BlockPos camp = mark("camp");
        level.setBlock(mark("return_stone"), Blocks.LODESTONE.defaultBlockState(), 3);
        // 星の溜まり: 鉢の真ん中の十字 5 つ (紫水晶の上。遠くからも光る水面に見える)
        for (int[] o : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos sp = mark("star_pool").offset(o[0], 0, o[1]);
            if (level.getBlockState(sp).isAir()) {
                level.setBlock(sp, ValleyBlocks.STAR_POOL.get().defaultBlockState(), 3);
            }
        }
        tent(level, camp.offset(-6, 0, -4));
        BlockPos bed = camp.offset(-3, 0, -5);
        level.setBlock(bed, Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
        level.setBlock(bed.east(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), 3);
        level.setBlock(bed.south(), Blocks.WHITE_CARPET.defaultBlockState(), 3);
        level.setBlock(camp.offset(-1, 0, -6), Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(camp.offset(0, 0, -6), Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
        level.setBlock(camp.offset(-1, 1, -6), Blocks.DECORATED_POT.defaultBlockState(), 3);
        level.setBlock(camp.offset(1, 0, 0), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
        for (int[] o : new int[][] {{3, 3}, {-7, 3}, {2, -3}}) {
            BlockPos p = camp.offset(o[0], 0, o[1]);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
        // 門の両脇の灯り (門の場所の目印)
        for (int dx : new int[] {-3, 3}) {
            BlockPos p = gate.offset(dx, 0, -3);
            level.setBlock(p, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
    }

    // 小さな三角の天幕 (南北に長さ 4。両脇の裾は灰の布、棟は明るい布、口は北、奥は閉じる)
    private static void tent(ServerLevel level, BlockPos at) {
        for (int z = 0; z < 4; z++) {
            level.setBlock(at.offset(0, 0, z), Blocks.GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(2, 0, z), Blocks.GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(1, 0, z), z == 3 ? Blocks.GRAY_WOOL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(at.offset(1, 1, z), Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), 3);
            level.setBlock(at.offset(0, 1, z), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(at.offset(2, 1, z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(at.offset(1, 0, 1), Blocks.WHITE_CARPET.defaultBlockState(), 3);
    }

    @Nullable
    public static String siteOf(BlockPos pedestal) {
        for (String site : new String[] {"bridge", "mirror", "hole"}) {
            if (mark("shard_" + site).equals(pedestal)) {
                return site;
            }
        }
        return null;
    }
}
