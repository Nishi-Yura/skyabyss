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
// 岩・倒木・茂み・岩の柱・崩れた石垣を置く。町・跡地・見どころ・道・はじまりの島の野営地と船着き場には置かない
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
            // はじまりの島の野営地と船着き場 (島のほかの所には飾りを置く)
            out.add(new int[] {lay.start().getX(), lay.start().getZ(), 48});
            out.add(new int[] {lay.dock().getX(), lay.dock().getZ(), 48});
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

    // はじまりの島の道 (野営地から船着き場) から 90 以内: 道の脇に足跡が並ぶので飾りを置かない
    static boolean nearIslandRoad(int x, int z) {
        ContinentLayout lay = ContinentLayout.get();
        for (List<BlockPos> line : lay.roads()) {
            if (line.isEmpty() || line.get(0).distSqr(lay.start()) > 40 * 40) {
                continue;
            }
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double len2 = dx * dx + dz * dz;
                double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / len2));
                if (Math.hypot(x - (a.getX() + u * dx), z - (a.getZ() + u * dz)) < 90) {
                    return true;
                }
            }
        }
        return false;
    }

    static void decorate(WorldGenLevel level, ChunkAccess chunk) {
        ContinentMap map = ContinentMap.get();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        clearRoad(level, ContinentChunkGenerator.roadSegments(x0, z0), x0, z0);
        clearLair(level, x0, z0);
        if (kept(x0 + 8, z0 + 8) || nearIslandRoad(x0 + 8, z0 + 8)) {
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
                // 木立 (平原に木のかたまり。野がのっぺりしないように)
                if (r.nextFloat() < (biome == ContinentBiomes.SAVANNA ? 0.06f : 0.12f)) {
                    grove(level, r, roads, x0, z0, biome == ContinentBiomes.SAVANNA);
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

    // 道の上を片付ける (2026-10-27 の声「道の上に人工物」「急に途切れる」): 雪の国で道に積もった雪 (バニラの凍り) と、道に生えた草花・木 (幹と葉ごと) を取る
    // 巨神の居場所: 地面 (cap 以下) より上に生えた木・草を払う (体にめり込まない)。海面より下は水に戻す
    static void clearLair(WorldGenLevel level, int x0, int z0) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int cap = ContinentLairs.cap(x, z);
                if (cap == Integer.MAX_VALUE && ContinentLairs.onPad(x, z)) {
                    // 祠の広場: 地面より上の木・草を払う
                    cap = ContinentMap.get().surface(x, z);
                }
                if (cap == Integer.MAX_VALUE) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = cap + 1; y <= top; y++) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir() || s.is(Blocks.WATER)) {
                        continue;
                    }
                    level.setBlock(m, y < ContinentChunkGenerator.SEA_LEVEL ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    static void clearRoad(WorldGenLevel level, List<int[]> roads, int x0, int z0) {
        if (roads.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (ContinentChunkGenerator.road(roads, x, z) == null) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                // 上から道の面 (土の道・荒れた土・砂利) を探し、その上の雪・草花・木を取る
                for (int y = top; y > top - 24; y--) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.GRAVEL)) {
                        BlockState a = level.getBlockState(m.set(x, y + 1, z));
                        if (a.is(net.minecraft.tags.BlockTags.LOGS)) {
                            removeTree(level, m.immutable());
                        } else if (!a.isAir() && a.getFluidState().isEmpty() && (a.canBeReplaced() || a.is(Blocks.SNOW))) {
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                            if (level.getBlockState(m.move(Direction.UP)).canBeReplaced() && level.getBlockState(m).getFluidState().isEmpty()) {
                                level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                        break;
                    }
                    if (!s.isAir() && !s.is(Blocks.SNOW) && !s.canBeReplaced() && !s.is(net.minecraft.tags.BlockTags.LOGS) && !s.is(net.minecraft.tags.BlockTags.LEAVES)) {
                        break;
                    }
                }
            }
        }
    }

    // 道の上に生えた木を、つながった幹と葉ごと取る (幹の根から横 6・上 14 まで、多くて 400 個)
    static void removeTree(WorldGenLevel level, BlockPos root) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(root);
        seen.add(root.asLong());
        int n = 0;
        while (!q.isEmpty() && n < 400) {
            BlockPos p = q.poll();
            BlockState s = level.getBlockState(p);
            if (!s.is(net.minecraft.tags.BlockTags.LOGS) && !s.is(net.minecraft.tags.BlockTags.LEAVES)) {
                continue;
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            n++;
            for (Direction d : Direction.values()) {
                BlockPos o = p.relative(d);
                if (Math.abs(o.getX() - root.getX()) > 6 || Math.abs(o.getZ() - root.getZ()) > 6 || o.getY() < root.getY() || o.getY() > root.getY() + 14) {
                    continue;
                }
                if (seen.add(o.asLong())) {
                    q.add(o);
                }
            }
        }
    }

    // 置き場 (チャンクの内側。端から 3 あける)。草か土の上で、道の縁から離れた所。だめなら null
    static BlockPos spot(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, int pad) {
        int x = x0 + pad + r.nextInt(16 - 2 * pad);
        int z = z0 + pad + r.nextInt(16 - 2 * pad);
        // 置き物の広がり (pad) と道の縁 (ROAD_EDGE + 揺らぎ) のあいだに 2 あける
        if (ContinentChunkGenerator.roadDistance(roads, x, z) < ContinentChunkGenerator.ROAD_EDGE + 1.0 + pad + 2) {
            return null;
        }
        // 巨神の居場所 (体の下となじませの幅) には置かない
        if (ContinentLairs.inside(x, z)) {
            return null;
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

    // 木立: 3〜6 本の小さな木 (幹 4〜6 と丸い葉。サバンナはアカシア)。白樺をまぜる
    static void grove(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, boolean savanna) {
        int n = 3 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            BlockPos c = spot(level, r, roads, x0, z0, 2);
            if (c == null || !level.getBlockState(c).is(Blocks.GRASS_BLOCK)) {
                continue;
            }
            boolean birch = !savanna && r.nextFloat() < 0.3f;
            Block log = savanna ? Blocks.ACACIA_LOG : birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG;
            Block leaves = savanna ? Blocks.ACACIA_LEAVES : birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES;
            tree(level, r, c.above(), log, leaves, 4 + r.nextInt(3));
        }
    }

    static void tree(WorldGenLevel level, RandomSource r, BlockPos base, Block log, Block leaves, int h) {
        for (int y = 0; y < h; y++) {
            if (!free(level.getBlockState(base.above(y)))) {
                return;
            }
        }
        BlockState leaf = leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = h - 3; dy <= h; dy++) {
                    int d = dx * dx + dz * dz + (dy - h + 1) * (dy - h + 1) * 2;
                    if (d > 5 || d == 5 && r.nextBoolean()) {
                        continue;
                    }
                    BlockPos p = base.offset(dx, dy, dz);
                    if (free(level.getBlockState(p))) {
                        put(level, p, leaf);
                    }
                }
            }
        }
        for (int y = 0; y < h; y++) {
            put(level, base.above(y), log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
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
