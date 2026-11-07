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
                if (p.kind().equals(ContinentPois.GONE)) {
                    continue;
                }
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
        // 地形の見どころ (2026-11-03 の声「もっと地形に作り込みが欲しい。移動の楽しみが減る」): 岩の露頭・岩のアーチ・池・花のかたまり。
        // 道の縁から離して置く (spot)。下の switch の小物 (岩・茂み・倒木) より先に置く
        landmarks(level, r, roads, x0, z0, biome);
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
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.TUFF) || s.is(Blocks.DIORITE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
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
        // 坂の下側で根が宙に浮かないよう、根の下を地面まで埋める (浮いた根の下に 1〜2 段の、くぐれないすき間ができていた)
        int ri = (int) Math.ceil(2.8f);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                if (dx * dx + dz * dz > 2.8f * 2.8f) {
                    continue;
                }
                for (int dy = -3; dy > -16; dy--) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (!free(level.getBlockState(p))) {
                        break;
                    }
                    put(level, p, main.defaultBlockState());
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

    // ---------------------------------------------------------------- 地形の見どころ (2026-11-03)
    // 岩の材: 層の三つ (下から順に繰り返す)・上の面・根元
    record Rock(Block a, Block b, Block c, Block top, Block base) {
    }

    static final Rock HILL_ROCK = new Rock(Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.GRASS_BLOCK, Blocks.MOSSY_COBBLESTONE);
    static final Rock WOOD_ROCK = new Rock(Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.MOSS_BLOCK, Blocks.MOSSY_COBBLESTONE);
    static final Rock SAVANNA_ROCK = new Rock(Blocks.GRANITE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.COARSE_DIRT, Blocks.ANDESITE);
    static final Rock MESA_ROCK = new Rock(Blocks.ORANGE_TERRACOTTA, Blocks.TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.RED_SAND, Blocks.TERRACOTTA);
    static final Rock SAND_ROCK = new Rock(Blocks.SANDSTONE, Blocks.TERRACOTTA, Blocks.SANDSTONE, Blocks.SAND, Blocks.SANDSTONE);
    static final Rock ASH_ROCK = new Rock(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.BLACKSTONE, Blocks.BASALT);
    static final Rock PEAK_ROCK = new Rock(Blocks.STONE, Blocks.ANDESITE, Blocks.CALCITE, Blocks.STONE, Blocks.ANDESITE);

    // 土地ごとの見どころの出やすさ (チャンクごと)。岩の露頭はどこでも、アーチは岩がちな土地、池は湿った野、花のかたまりは野と花の林
    static void landmarks(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, int biome) {
        switch (biome) {
            case ContinentBiomes.PLAINS, ContinentBiomes.MEADOW -> {
                chance(r, 0.05f, () -> crag(level, r, roads, x0, z0, HILL_ROCK));
                chance(r, 0.04f, () -> pond(level, r, roads, x0, z0, false));
                chance(r, biome == ContinentBiomes.MEADOW ? 0.32f : 0.18f, () -> flowers(level, r, roads, x0, z0));
                chance(r, 0.005f, () -> arch(level, r, roads, x0, z0, HILL_ROCK));
            }
            case ContinentBiomes.SAVANNA -> {
                chance(r, 0.06f, () -> crag(level, r, roads, x0, z0, SAVANNA_ROCK));
                chance(r, 0.01f, () -> arch(level, r, roads, x0, z0, SAVANNA_ROCK));
            }
            case ContinentBiomes.FOREST, ContinentBiomes.BIRCH_FOREST, ContinentBiomes.FLOWER_FOREST -> {
                chance(r, 0.07f, () -> crag(level, r, roads, x0, z0, WOOD_ROCK));
                chance(r, 0.04f, () -> pond(level, r, roads, x0, z0, false));
                if (biome == ContinentBiomes.FLOWER_FOREST) {
                    chance(r, 0.25f, () -> flowers(level, r, roads, x0, z0));
                }
            }
            case ContinentBiomes.TAIGA, ContinentBiomes.PINE_TAIGA -> {
                chance(r, 0.08f, () -> crag(level, r, roads, x0, z0, WOOD_ROCK));
                chance(r, 0.025f, () -> pond(level, r, roads, x0, z0, false));
            }
            case ContinentBiomes.WINDSWEPT_HILLS, ContinentBiomes.GRAVELLY_HILLS -> {
                chance(r, 0.12f, () -> crag(level, r, roads, x0, z0, HILL_ROCK));
                chance(r, 0.015f, () -> arch(level, r, roads, x0, z0, HILL_ROCK));
            }
            case ContinentBiomes.STONY_PEAKS, ContinentBiomes.SNOWY_SLOPES -> chance(r, 0.08f, () -> crag(level, r, roads, x0, z0, PEAK_ROCK));
            case ContinentBiomes.BADLANDS, ContinentBiomes.WOODED_BADLANDS -> {
                chance(r, 0.06f, () -> crag(level, r, roads, x0, z0, MESA_ROCK));
                chance(r, 0.03f, () -> arch(level, r, roads, x0, z0, MESA_ROCK));
            }
            case ContinentBiomes.ERODED_BADLANDS -> {
                chance(r, 0.07f, () -> crag(level, r, roads, x0, z0, ASH_ROCK));
                chance(r, 0.02f, () -> arch(level, r, roads, x0, z0, ASH_ROCK));
            }
            case ContinentBiomes.DESERT -> {
                chance(r, 0.04f, () -> crag(level, r, roads, x0, z0, SAND_ROCK));
                chance(r, 0.02f, () -> arch(level, r, roads, x0, z0, SAND_ROCK));
            }
            case ContinentBiomes.SWAMP -> chance(r, 0.15f, () -> pond(level, r, roads, x0, z0, true));
            default -> {
            }
        }
    }

    // 乱数は必ず一つ引く (出る出ないで後の飾りの並びが変わらないように)
    private static void chance(RandomSource r, float p, Runnable place) {
        if (r.nextFloat() < p) {
            place.run();
        }
    }

    // 層の材: 2〜3 段ごとに a・b・c を繰り返す (場所で層の高さが少しずれる)
    static Block layer(Rock rock, int x, int y, int z) {
        int k = Math.floorDiv(y + (int) Math.floor(2.0 * ContinentMap.value(x / 9.0, z / 9.0, 0x4C2F)), 2 + Math.floorMod(x * 31 + z * 17, 2));
        return switch (Math.floorMod(k, 3)) {
            case 0 -> rock.a();
            case 1 -> rock.b();
            default -> rock.c();
        };
    }

    // 形 (相対の箱の中で、置く所に true) を、層の材で置く。てっぺん (形の一番上から 2 段以内) の上が空く面は上の面の材 (草・苔・砂)、
    // それより下の段の上面は岩のまま、ところどころ苔の敷物 (横の段に草の塊が点々と付くと継ぎはぎに見えた)。地面のすぐ上の縁は根元の材。
    // 地面より上は空き (草花・雪) だけを置き換え、地面より下 (dy < 0) は土を置き換えて根を埋める
    static void build(WorldGenLevel level, RandomSource r, BlockPos c, boolean[][][] shape, int ox, int oy, int oz, Rock rock) {
        int sx = shape.length;
        int sy = shape[0].length;
        int sz = shape[0][0].length;
        int peak = 0;
        for (int i = 0; i < sx; i++) {
            for (int k = 0; k < sz; k++) {
                for (int j = sy - 1; j > peak; j--) {
                    if (shape[i][j][k]) {
                        peak = j;
                        break;
                    }
                }
            }
        }
        boolean mossy = rock.top() == Blocks.GRASS_BLOCK || rock.top() == Blocks.MOSS_BLOCK;
        for (int i = 0; i < sx; i++) {
            for (int k = 0; k < sz; k++) {
                for (int j = 0; j < sy; j++) {
                    if (!shape[i][j][k]) {
                        continue;
                    }
                    int dy = j - oy;
                    BlockPos p = c.offset(i - ox, dy, k - oz);
                    BlockState here = level.getBlockState(p);
                    if (dy > 0 && !free(here)) {
                        continue;
                    }
                    boolean openAbove = j + 1 >= sy || !shape[i][j + 1][k];
                    Block b;
                    if (openAbove && dy >= 1 && j >= peak - 2) {
                        b = r.nextFloat() < 0.85f ? rock.top() : rock.base();
                    } else if (dy >= 0 && dy <= 1 && r.nextFloat() < 0.6f) {
                        b = rock.base();
                    } else {
                        b = layer(rock, p.getX(), p.getY(), p.getZ());
                    }
                    put(level, p, b.defaultBlockState());
                    BlockPos up = p.above();
                    if (!openAbove || dy < 1 || !level.getBlockState(up).isAir()) {
                        continue;
                    }
                    // てっぺんに草やシダ、下の段の岩の上面に苔の敷物 (草と苔の土地の岩だけ)
                    if ((b == Blocks.GRASS_BLOCK || b == Blocks.MOSS_BLOCK) && r.nextFloat() < 0.35f) {
                        put(level, up, (r.nextFloat() < 0.4f ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
                    } else if (mossy && j < peak - 2 && r.nextFloat() < 0.3f) {
                        put(level, up, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                }
            }
        }
    }

    // 岩の露頭: 平たい楕円の塊 2〜3 個を、ずらしながら積んだ幅 7〜10・高さ 4〜8 の岩のかたまり (細く高い柱は塔のように見えた)。
    // 上の塊が下からはみ出して軒ができる。置き場から ±5 に収める (チャンクの外へ出さない)。根は 2 段埋める
    static void crag(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, Rock rock) {
        BlockPos c = spot(level, r, roads, x0, z0, 5);
        if (c == null) {
            return;
        }
        int n = 2 + r.nextInt(2);
        double[][] blobs = new double[n][];
        double bx = 0;
        double by = 0.5;
        double bz = 0;
        double rx = 3.6 + r.nextDouble() * 1.2;
        double rz = 3.0 + r.nextDouble() * 1.2;
        double ry = 1.8 + r.nextDouble() * 1.2;
        double peak = 0;
        for (int i = 0; i < n; i++) {
            blobs[i] = new double[] {bx, by, bz, rx, ry, rz};
            peak = Math.max(peak, by + ry * 1.11);
            bx = Math.max(-1.5, Math.min(1.5, bx + (r.nextDouble() - 0.5) * 2.4));
            bz = Math.max(-1.5, Math.min(1.5, bz + (r.nextDouble() - 0.5) * 2.4));
            by += ry * (0.75 + r.nextDouble() * 0.4);
            rx *= 0.68 + r.nextDouble() * 0.2;
            rz *= 0.68 + r.nextDouble() * 0.2;
            ry *= 0.85 + r.nextDouble() * 0.2;
        }
        int top = (int) Math.ceil(peak);
        int ox = 5;
        // 根は 4 段 (急な坂の下側でも、根の下が宙に浮かない)
        int oy = 4;
        boolean[][][] shape = new boolean[11][top + oy + 1][11];
        int salt = r.nextInt();
        for (int i = 0; i < 11; i++) {
            for (int k = 0; k < 11; k++) {
                for (int j = 0; j < shape[0].length; j++) {
                    double dx = i - ox;
                    double dy = j - oy;
                    double dz = k - ox;
                    double rough = 0.22 * ContinentMap.hash(c.getX() + i, c.getY() + j, (c.getZ() + k) ^ salt);
                    for (double[] b : blobs) {
                        double ex = (dx - b[0]) / b[3];
                        double ey = (dy - b[1]) / b[4];
                        double ez = (dz - b[2]) / b[5];
                        // 根 (地面より下) は一番下の塊の広さのまま埋める
                        if (dy < 0) {
                            ey = 0;
                        }
                        if (ex * ex + ey * ey + ez * ez <= 1.0 + rough) {
                            shape[i][j][k] = true;
                            break;
                        }
                    }
                }
            }
        }
        lowGaps(level, c, shape, ox, oy, ox, true);
        build(level, r, c, shape, ox, oy, ox, rock);
    }

    // 岩のアーチ: 半ばの高さ 5〜8、脚の間 8〜11 の弧。脚は地面へ 3 段埋める。両脚の地面の高さが置き場と 2 以上違えば置かない
    static void arch(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, Rock rock) {
        BlockPos c = spot(level, r, roads, x0, z0, 6);
        if (c == null) {
            return;
        }
        boolean alongX = r.nextBoolean();
        double span = 3.5 + r.nextDouble() * 0.7;
        double rise = 5.0 + r.nextDouble() * 3.0;
        double thick = 1.1 + r.nextDouble() * 0.3;
        for (int s = -1; s <= 1; s += 2) {
            int lx = c.getX() + (alongX ? (int) Math.round(s * span) : 0);
            int lz = c.getZ() + (alongX ? 0 : (int) Math.round(s * span));
            int ly = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz) - 1;
            if (Math.abs(ly - c.getY()) > 2) {
                return;
            }
        }
        int ox = 6;
        int oy = 3;
        int height = (int) Math.ceil(rise + thick) + 1;
        boolean[][][] shape = new boolean[13][height + oy + 1][13];
        for (int i = 0; i < 13; i++) {
            for (int k = 0; k < 13; k++) {
                double along = alongX ? i - ox : k - ox;
                double across = alongX ? k - ox : i - ox;
                if (Math.abs(across) > thick + 0.6) {
                    continue;
                }
                for (int j = 0; j < shape[0].length; j++) {
                    double dy = j - oy;
                    boolean in = false;
                    // 弧: 半楕円の線から thick 以内
                    for (int a = 0; a <= 48 && !in; a++) {
                        double t = Math.PI * a / 48.0;
                        double px = -Math.cos(t) * span;
                        double py = Math.sin(t) * rise;
                        double w = thick * (1.0 + 0.35 * (1.0 - Math.sin(t)));
                        if ((along - px) * (along - px) + (dy - py) * (dy - py) + across * across * 1.3 <= w * w) {
                            in = true;
                        }
                    }
                    // 脚の根 (地面より下)
                    if (!in && dy < 0.5 && Math.abs(Math.abs(along) - span) <= thick + 0.4 && Math.abs(across) <= thick + 0.4) {
                        in = true;
                    }
                    shape[i][j][k] = in;
                }
            }
        }
        // 斜面で弧の下の地面が高く、くぐれない (すき間 1〜2) アーチは置かない (2026-11-08 の声「火のバイオームのところ、高さが無く通れない場所」)
        if (lowGaps(level, c, shape, ox, oy, ox, false)) {
            return;
        }
        build(level, r, c, shape, ox, oy, ox, rock);
    }

    // 岩の張り出しの下で、地面とのすき間が 1〜2 段 (人がくぐれず、這うしかない) の所があるか。fill なら、その張り出しの下を地面まで岩で埋める
    static boolean lowGaps(WorldGenLevel level, BlockPos c, boolean[][][] shape, int ox, int oy, int oz, boolean fill) {
        boolean low = false;
        for (int i = 0; i < shape.length; i++) {
            for (int k = 0; k < shape[0][0].length; k++) {
                for (int j = 1; j < shape[0].length; j++) {
                    if (!shape[i][j][k] || shape[i][j - 1][k]) {
                        continue;
                    }
                    // 張り出しの下の空き (形の外の、置ける所) を数える
                    int gap = 0;
                    while (gap < 3) {
                        int jj = j - 1 - gap;
                        if (jj >= 0 && shape[i][jj][k]) {
                            break;
                        }
                        BlockPos at = c.offset(i - ox, jj - oy, k - oz);
                        if (!free(level.getBlockState(at))) {
                            break;
                        }
                        gap++;
                    }
                    if (gap >= 1 && gap <= 2) {
                        low = true;
                        if (!fill) {
                            return true;
                        }
                        for (int g = 1; g <= gap; g++) {
                            if (j - g >= 0) {
                                shape[i][j - g][k] = true;
                            }
                        }
                    }
                }
                // 上向き: この岩の上の面と、先に置かれた岩 (隣の岩山やアーチ) の張り出しのあいだが 1〜2 段
                for (int j = oy; j < shape[0].length; j++) {
                    if (!shape[i][j][k] || j + 1 < shape[0].length && shape[i][j + 1][k]) {
                        continue;
                    }
                    int gap = 0;
                    while (gap < 3) {
                        BlockPos at = c.offset(i - ox, j + 1 + gap - oy, k - oz);
                        if (!free(level.getBlockState(at))) {
                            break;
                        }
                        gap++;
                    }
                    BlockPos lid = c.offset(i - ox, j + 1 + gap - oy, k - oz);
                    if (gap >= 1 && gap <= 2 && level.getBlockState(lid).blocksMotion() && !level.getBlockState(lid).is(net.minecraft.tags.BlockTags.LEAVES)) {
                        low = true;
                        if (!fill) {
                            return true;
                        }
                        // 岩山は下を削る: この柱の、すき間の真下の一段を外す (上の張り出しとのあいだを 3 段以上にする)
                        for (int g = 0; g < 3 - gap && j - g >= 0; g++) {
                            shape[i][j - g][k] = false;
                        }
                    }
                }
            }
        }
        return low;
    }

    // 池: 楕円 (半径 2.5〜5)、深さ 1〜2。縁より一段下に水面。底は砂・粘土・砂利。縁にサトウキビ、水に睡蓮の葉 (沼は多め・縁は泥)。
    // 縁がまわりより低い (水があふれる) 所・木の根元には作らない
    static void pond(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0, boolean swamp) {
        BlockPos c = spot(level, r, roads, x0, z0, 6);
        if (c == null) {
            return;
        }
        BlockState ground = level.getBlockState(c);
        if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MOSS_BLOCK) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.DIRT))) {
            return;
        }
        double rx = 2.5 + r.nextDouble() * 2.5;
        double rz = 2.0 + r.nextDouble() * 2.5;
        int y = c.getY();
        if (y <= ContinentChunkGenerator.SEA_LEVEL + 1) {
            return;
        }
        // 平らか (水の所は地面が y、そのまわり 2 升ほどは y 以上)
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = pondShape(c, dx, dz, rx, rz);
                if (d > 2.4) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, c.getX() + dx, c.getZ() + dz) - 1;
                if (d <= 1.0 ? top != y : top < y) {
                    return;
                }
            }
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = pondShape(c, dx, dz, rx, rz);
                BlockPos at = c.offset(dx, 0, dz);
                if (d <= 1.0) {
                    int depth = d < 0.35 ? 2 : 1;
                    for (int k = 0; k < depth; k++) {
                        put(level, at.below(k), water);
                    }
                    double h = ContinentMap.hash(at.getX(), at.getZ(), 0x3A9);
                    put(level, at.below(depth), (swamp ? Blocks.MUD : h > 0.3 ? Blocks.CLAY : h < -0.4 ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState());
                    for (int up = 1; up <= 2; up++) {
                        BlockState s = level.getBlockState(at.above(up));
                        if (!s.isAir() && s.canBeReplaced() && s.getFluidState().isEmpty()) {
                            put(level, at.above(up), Blocks.AIR.defaultBlockState());
                        }
                    }
                    if (level.getBlockState(at.above()).isAir() && r.nextFloat() < (swamp ? 0.3f : 0.1f)) {
                        put(level, at.above(), Blocks.LILY_PAD.defaultBlockState());
                    }
                } else if (d <= 1.45 && level.getBlockState(at).is(Blocks.GRASS_BLOCK)) {
                    // 岸: 土の縁 (沼は泥) と、水に接する所にサトウキビ
                    if (r.nextFloat() < 0.35f) {
                        put(level, at, (swamp ? Blocks.MUD : r.nextBoolean() ? Blocks.SAND : Blocks.COARSE_DIRT).defaultBlockState());
                    }
                    boolean wet = false;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        if (level.getBlockState(at.relative(dir)).is(Blocks.WATER)) {
                            wet = true;
                        }
                    }
                    if (wet && !swamp && r.nextFloat() < 0.3f && free(level.getBlockState(at.above()))) {
                        if (level.getBlockState(at).is(Blocks.COARSE_DIRT)) {
                            put(level, at, Blocks.SAND.defaultBlockState());
                        }
                        int h = 1 + r.nextInt(3);
                        for (int k = 1; k <= h && free(level.getBlockState(at.above(k))); k++) {
                            put(level, at.above(k), Blocks.SUGAR_CANE.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    // 池の形 (1 以下が水)。縁を少し揺らす
    private static double pondShape(BlockPos c, int dx, int dz, double rx, double rz) {
        return (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) + 0.15 * ContinentMap.hash(c.getX() + dx, c.getZ() + dz, 0x60D);
    }

    // 花のかたまり: 一種類 (ときどき二種類) の花を、半径 3〜4 に 14〜28 本
    static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET,
            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.LILY_OF_THE_VALLEY};

    static void flowers(WorldGenLevel level, RandomSource r, List<int[]> roads, int x0, int z0) {
        BlockPos c = spot(level, r, roads, x0, z0, 4);
        if (c == null || !level.getBlockState(c).is(Blocks.GRASS_BLOCK)) {
            return;
        }
        Block main = FLOWERS[r.nextInt(FLOWERS.length)];
        Block second = r.nextFloat() < 0.3f ? FLOWERS[r.nextInt(FLOWERS.length)] : main;
        int n = 14 + r.nextInt(15);
        for (int i = 0; i < n; i++) {
            int x = c.getX() + Math.max(-4, Math.min(4, (int) Math.round(r.nextGaussian() * 1.7)));
            int z = c.getZ() + Math.max(-4, Math.min(4, (int) Math.round(r.nextGaussian() * 1.7)));
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK)) {
                // 草が生えていたら、その草と置き換える
                if (level.getBlockState(p.below()).is(Blocks.SHORT_GRASS) && level.getBlockState(p.below(2)).is(Blocks.GRASS_BLOCK)) {
                    p = p.below();
                } else {
                    continue;
                }
            }
            put(level, p, (r.nextFloat() < 0.75f ? main : second).defaultBlockState());
        }
    }
}
