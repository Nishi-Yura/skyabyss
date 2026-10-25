package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

// 野の小さな飾り (docs/design/40 の 6 章)。生物群系の木や草のあとに、チャンクごとに決まった乱数で
// 岩・倒木・茂み・岩の柱・崩れた石垣を置く。町・跡地・見どころ・道・はじまりの島には置かない
final class ContinentScatter {
    private ContinentScatter() {
    }

    private static List<int[]> keepOut;

    // 置かない円 {x, z, 半径}
    static synchronized List<int[]> keepOut() {
        if (keepOut == null) {
            ContinentLayout lay = ContinentLayout.get();
            List<int[]> out = new ArrayList<>();
            out.add(new int[] {lay.capital().getX(), lay.capital().getZ(), 300});
            for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
                BlockPos town = lay.town(t);
                if (town != null) {
                    out.add(new int[] {town.getX(), town.getZ(), 120});
                }
                BlockPos site = lay.site(t);
                if (site != null) {
                    out.add(new int[] {site.getX(), site.getZ(), 180});
                }
            }
            for (List<BlockPos> f : lay.ferryDocks()) {
                for (BlockPos p : f) {
                    out.add(new int[] {p.getX(), p.getZ(), 60});
                }
            }
            for (ContinentPois.Poi p : ContinentPois.all()) {
                out.add(new int[] {p.x(), p.z(), 36});
            }
            keepOut = out;
        }
        return keepOut;
    }

    static boolean kept(int x, int z) {
        for (int[] k : keepOut()) {
            long dx = x - k[0];
            long dz = z - k[1];
            if (dx * dx + dz * dz < (long) k[2] * k[2]) {
                return true;
            }
        }
        return false;
    }

    static void decorate(WorldGenLevel level, ChunkAccess chunk) {
        ContinentMap map = ContinentMap.get();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        int region = map.region(x0 + 8, z0 + 8);
        if (region == ContinentMap.OUTSKIRTS || kept(x0 + 8, z0 + 8)) {
            return;
        }
        RandomSource r = RandomSource.create(chunk.getPos().toLong() * 0x9E3779B97F4A7C15L + 0x5CA77E2L);
        List<int[]> roads = ContinentChunkGenerator.roadSegments(x0, z0);
        int biome = map.biome(x0 + 8, z0 + 8);
        switch (biome) {
            case ContinentBiomes.PLAINS, ContinentBiomes.MEADOW, ContinentBiomes.SAVANNA -> {
                if (r.nextFloat() < 0.16f) {
                    boulder(level, r, roads, x0, z0, biome == ContinentBiomes.SAVANNA ? Blocks.GRANITE : Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE);
                }
                int bushes = r.nextFloat() < 0.4f ? 1 + r.nextInt(3) : 0;
                for (int i = 0; i < bushes; i++) {
                    bush(level, r, roads, x0, z0, biome == ContinentBiomes.SAVANNA ? Blocks.ACACIA_LEAVES : Blocks.OAK_LEAVES);
                }
                if (r.nextFloat() < 0.035f) {
                    wall(level, r, roads, x0, z0);
                }
            }
            case ContinentBiomes.FOREST, ContinentBiomes.BIRCH_FOREST, ContinentBiomes.FLOWER_FOREST, ContinentBiomes.TAIGA, ContinentBiomes.PINE_TAIGA -> {
                Block wood = biome == ContinentBiomes.BIRCH_FOREST ? Blocks.BIRCH_LOG
                        : biome == ContinentBiomes.TAIGA || biome == ContinentBiomes.PINE_TAIGA ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG;
                if (r.nextFloat() < 0.3f) {
                    log(level, r, roads, x0, z0, wood);
                }
                if (r.nextFloat() < 0.12f) {
                    boulder(level, r, roads, x0, z0, Blocks.MOSSY_COBBLESTONE, Blocks.STONE);
                }
            }
            case ContinentBiomes.WINDSWEPT_HILLS, ContinentBiomes.GRAVELLY_HILLS -> {
                if (r.nextFloat() < 0.22f) {
                    boulder(level, r, roads, x0, z0, Blocks.STONE, Blocks.ANDESITE);
                }
                if (r.nextFloat() < 0.06f) {
                    spire(level, r, roads, x0, z0, Blocks.STONE, Blocks.ANDESITE);
                }
            }
            case ContinentBiomes.BADLANDS, ContinentBiomes.WOODED_BADLANDS -> {
                if (r.nextFloat() < 0.1f) {
                    spire(level, r, roads, x0, z0, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA);
                }
            }
            case ContinentBiomes.ERODED_BADLANDS -> {
                if (r.nextFloat() < 0.1f) {
                    spire(level, r, roads, x0, z0, Blocks.BASALT, Blocks.BLACKSTONE);
                }
            }
            case ContinentBiomes.DESERT -> {
                if (r.nextFloat() < 0.08f) {
                    boulder(level, r, roads, x0, z0, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE);
                }
            }
            case ContinentBiomes.SWAMP -> {
                if (r.nextFloat() < 0.25f) {
                    log(level, r, roads, x0, z0, Blocks.MANGROVE_LOG);
                }
            }
            default -> {
            }
        }
    }

    // 置き場 (チャンクの内側。端から 3 あける)。草か土の上で、道から離れた所。だめなら null
    static BlockPos spot(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, int pad) {
        int x = x0 + pad + r.nextInt(16 - 2 * pad);
        int z = z0 + pad + r.nextInt(16 - 2 * pad);
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dz = -4; dz <= 4; dz += 4) {
                if (ContinentChunkGenerator.road(roads, x + dx, z + dz) != null) {
                    return null;
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        BlockState top = level.getBlockState(new BlockPos(x, y, z));
        if (y < ContinentChunkGenerator.SEA_LEVEL || !top.getFluidState().isEmpty() || top.is(Blocks.DIRT_PATH)) {
            return null;
        }
        // 木の葉の上に乗ったら、地面まで下りる
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 24 && !solidGround(level.getBlockState(m)); i++) {
            m.move(Direction.DOWN);
        }
        return solidGround(level.getBlockState(m)) ? m.immutable() : null;
    }

    static boolean solidGround(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.STONE) || s.is(Blocks.ANDESITE)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.MUD)
                || s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CALCITE)
                || s.getBlock().getDescriptionId().endsWith("terracotta");
    }

    static boolean free(BlockState s) {
        return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    static void put(WorldGenLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }

    // 丸い岩。半分ほど土に埋める。苔の石をまぜる
    static void boulder(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, Block main, Block alt) {
        BlockPos c = spot(level, r, roads, x0, z0, 4);
        if (c == null) {
            return;
        }
        float rad = 1.4f + r.nextFloat() * 1.6f;
        int ri = (int) Math.ceil(rad);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dy = -1; dy <= ri; dy++) {
                    float d = (dx * dx + dz * dz) / (rad * rad) + (dy * dy) / (rad * rad * 0.7f);
                    if (d > 1f - r.nextFloat() * 0.25f) {
                        continue;
                    }
                    BlockPos p = c.offset(dx, dy, dz);
                    if (dy > 0 && !free(level.getBlockState(p))) {
                        continue;
                    }
                    put(level, p, (r.nextFloat() < 0.3f ? alt : main).defaultBlockState());
                }
            }
        }
    }

    // 倒れた木。4〜7 本の丸太を横に寝かせ、上に苔やきのこ
    static void log(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, Block wood) {
        BlockPos c = spot(level, r, roads, x0, z0, 4);
        if (c == null) {
            return;
        }
        boolean alongX = r.nextBoolean();
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        int len = 4 + r.nextInt(4);
        for (int i = 0; i < len; i++) {
            BlockPos p = alongX ? c.offset(i - len / 2, 1, 0) : c.offset(0, 1, i - len / 2);
            if (!free(level.getBlockState(p))) {
                return;
            }
            BlockPos below = p.below();
            if (free(level.getBlockState(below))) {
                put(level, below, wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
                continue;
            }
            put(level, p, wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
            if (r.nextFloat() < 0.25f && free(level.getBlockState(p.above()))) {
                put(level, p.above(), (r.nextBoolean() ? Blocks.MOSS_CARPET : r.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
            }
        }
    }

    // 茂み (葉の小さなかたまり。散らない葉)
    static void bush(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, Block leaves) {
        BlockPos c = spot(level, r, roads, x0, z0, 2);
        if (c == null || !level.getBlockState(c).is(Blocks.GRASS_BLOCK)) {
            return;
        }
        BlockState leaf = leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        int w = 1 + r.nextInt(2);
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -w; dz <= w; dz++) {
                for (int dy = 1; dy <= 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + (dy - 1) * 2 > w + 1 || r.nextFloat() < 0.15f) {
                        continue;
                    }
                    BlockPos p = c.offset(dx, dy, dz);
                    if (free(level.getBlockState(p))) {
                        put(level, p, leaf);
                    }
                }
            }
        }
    }

    // 岩の柱 (細くとがる。根元は太い)
    static void spire(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, Block main, Block alt) {
        BlockPos c = spot(level, r, roads, x0, z0, 4);
        if (c == null) {
            return;
        }
        int h = 6 + r.nextInt(8);
        for (int dy = -2; dy < h; dy++) {
            float rad = 2.4f * (1f - (float) Math.max(0, dy) / (h + 1)) + 0.4f;
            int ri = (int) Math.ceil(rad);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > rad * rad) {
                        continue;
                    }
                    BlockPos p = c.offset(dx, dy, dz);
                    if (dy > 0 && !free(level.getBlockState(p))) {
                        continue;
                    }
                    put(level, p, ((dy / 2 + dx + dz) % 3 == 0 ? alt : main).defaultBlockState());
                }
            }
        }
    }

    // 崩れた石垣 (昔の畑の境。苔むした石レンガが途切れ途切れに)
    static void wall(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0) {
        BlockPos c = spot(level, r, roads, x0, z0, 3);
        if (c == null) {
            return;
        }
        boolean alongX = r.nextBoolean();
        int len = 5 + r.nextInt(5);
        for (int i = 0; i < len; i++) {
            if (r.nextFloat() < 0.2f) {
                continue;
            }
            int gx = alongX ? c.getX() - len / 2 + i : c.getX();
            int gz = alongX ? c.getZ() : c.getZ() - len / 2 + i;
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, gx, gz);
            if (Math.abs(y - c.getY() - 1) > 2) {
                continue;
            }
            int h = 1 + r.nextInt(2);
            for (int dy = 0; dy < h; dy++) {
                BlockPos p = new BlockPos(gx, y + dy, gz);
                if (!free(level.getBlockState(p))) {
                    break;
                }
                put(level, p, (r.nextFloat() < 0.5f ? Blocks.MOSSY_STONE_BRICKS : r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState());
            }
        }
    }
}
