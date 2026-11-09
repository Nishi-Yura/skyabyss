package jp.revati.skyabyss.world.continent;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

// 「天と淵の大陸」の地形 (docs/design/40 の 3 章)。手で描いた大陸の図 (ContinentMap) のとおりに地面を積み、
// 上の数段を土地ごとの土 (草・砂・石・雪・赤い粘土・玄武岩) にし、海の高さまで水を張る。
// layout.json の道を土の道にする。洞窟は掘らない。マインクラフトの構造物 (村・砦など) は出さない。木・草・鉱石は生物群系のとおりに生える (地上の溶岩の池は抜く)
public final class ContinentChunkGenerator extends ChunkGenerator {
    // 地形や道を変えたら、前の版の世界は地上を作り直す (upgrade/ContinentRegen の VERSION を上げる)
    public static final MapCodec<ContinentChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, i.stable(ContinentChunkGenerator::new)));

    public static final int SEA_LEVEL = 63;
    static final int MIN_Y = -64;
    static final int HEIGHT = 384;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState BRIDGE = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState[] BANDS = {
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
        Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
    };

    public ContinentChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource, net.minecraft.Util.memoize(ContinentChunkGenerator::features));
    }

    // 生物群系の飾り (木・草・鉱石など) から、地上の溶岩の池・溶岩の湧き口・水の湧き口を抜く (RPG の野にいきなり溶岩は置かない。崖から水が垂れない。2026-10-26)
    static final List<String> DROPPED = List.of("lake_lava", "spring_lava", "spring_water");

    static net.minecraft.world.level.biome.BiomeGenerationSettings features(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        net.minecraft.world.level.biome.BiomeGenerationSettings src = biome.value().getGenerationSettings();
        List<net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature>> steps = new java.util.ArrayList<>();
        for (net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature> step : src.features()) {
            List<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> keep = new java.util.ArrayList<>();
            for (net.minecraft.core.Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature> f : step) {
                String id = f.unwrapKey().map(k -> k.location().getPath()).orElse("");
                if (DROPPED.stream().noneMatch(id::startsWith)) {
                    keep.add(f);
                }
            }
            steps.add(net.minecraft.core.HolderSet.direct(keep));
        }
        return new net.minecraft.world.level.biome.BiomeGenerationSettings(java.util.Map.of(), steps);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // 構造物の置き場を一つも持たない
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, Stream.empty());
    }

    // ---------------------------------------------------------------- 柱の中身
    // 上面の Y・まわりとの段差・土地の番号から、y の所の石を決める (水と空気はここでは決めない)
    static BlockState solid(int x, int y, int z, int top, int slope, int biome) {
        if (y <= MIN_Y) {
            return BEDROCK;
        }
        int depth = top - y;
        boolean underwater = top < SEA_LEVEL - 1;
        boolean steep = slope >= 3;
        double n = ContinentMap.value(x / 9.0, z / 9.0, 0x2B07);
        switch (biome) {
            case ContinentBiomes.DEEP_OCEAN, ContinentBiomes.OCEAN, ContinentBiomes.INLAND_SEA, ContinentBiomes.RIVER -> {
                if (depth < 3) {
                    if (biome == ContinentBiomes.INLAND_SEA || top >= 48) {
                        return n > 0.55 ? Blocks.CLAY.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    }
                    return n > 0.1 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                }
            }
            case ContinentBiomes.BEACH, ContinentBiomes.DESERT -> {
                if (depth < 4) {
                    return Blocks.SAND.defaultBlockState();
                }
                if (depth < 7) {
                    return Blocks.SANDSTONE.defaultBlockState();
                }
            }
            case ContinentBiomes.STONY_PEAKS -> {
                if (depth < 2 && n > 0.45) {
                    return Blocks.ANDESITE.defaultBlockState();
                }
                if (depth < 2 && n < -0.55) {
                    return Blocks.CALCITE.defaultBlockState();
                }
            }
            case ContinentBiomes.SNOWY_SLOPES -> {
                if (depth < 3 && !steep) {
                    return Blocks.SNOW_BLOCK.defaultBlockState();
                }
            }
            case ContinentBiomes.BADLANDS, ContinentBiomes.WOODED_BADLANDS -> {
                if (depth == 0 && !steep) {
                    return Blocks.RED_SAND.defaultBlockState();
                }
                if (depth < 14) {
                    return BANDS[Math.floorMod(y, BANDS.length)];
                }
            }
            case ContinentBiomes.ERODED_BADLANDS -> {
                if (depth < 4) {
                    if (top > 185 && depth == 0 && n > 0.75) {
                        return Blocks.MAGMA_BLOCK.defaultBlockState();
                    }
                    return n > 0 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                }
            }
            case ContinentBiomes.GRAVELLY_HILLS -> {
                if (depth < 3 && !steep && n > 0.15) {
                    return Blocks.GRAVEL.defaultBlockState();
                }
                if (depth < 4 && !steep) {
                    return depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                }
            }
            default -> {
                // 草の土地 (野・林・谷・丘陵・針葉樹・浜の奥)
                boolean rocky = steep || biome == ContinentBiomes.WINDSWEPT_HILLS && slope >= 2 && n > 0.2;
                // 中くらいの坂の、岩の出た筋 (2026-11-03 の声「地形に作り込み」。丘の肌を草一色にしない)
                boolean scree = !rocky && !underwater && slope == 2 && ContinentMap.value(x / 5.0, z / 5.0, 0x63E1) > 0.5;
                if (depth < 4 && !rocky && !scree) {
                    if (underwater) {
                        return depth < 2 ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    }
                    if (depth == 0) {
                        return grassTop(x, z, biome, n);
                    }
                    return Blocks.DIRT.defaultBlockState();
                }
                if (scree && depth < 2) {
                    double h = ContinentMap.hash(x, y, z ^ 0x2D93);
                    return depth == 0 && h > 0.35 ? Blocks.GRAVEL.defaultBlockState() : h < -0.4 ? Blocks.ANDESITE.defaultBlockState() : STONE;
                }
                // 崖の面 (2026-10-26): 石一色にせず、林の崖には苔のまだら。2026-11-03 から地層: 3 段ごとの横の層 (石・安山岩・凝灰岩・閃緑岩)。
                // 建物の置き場の見分け (SiteJob.naturalGround) が自然と見る石だけを使う (丸石を混ぜると、一章の骨の置き場が「人が建てた所」と見て動き続け、建たなかった)。
                // 層の高さは場所でゆっくりずれ、崖の上の縁 (depth 0) は苔の丸石か草の根の土
                if (rocky && depth < 3) {
                    double h = ContinentMap.hash(x, y, z ^ 0x51C7);
                    if ((biome == ContinentBiomes.FOREST || biome == ContinentBiomes.BIRCH_FOREST || biome == ContinentBiomes.FLOWER_FOREST) && h > 0.55 && depth == 0) {
                        return Blocks.MOSS_BLOCK.defaultBlockState();
                    }
                    int layer = Math.floorDiv(y + (int) Math.floor(3.0 * ContinentMap.value(x / 23.0, z / 23.0, 0x5A11)), 3);
                    switch (Math.floorMod(layer * 7 + 3, 6)) {
                        case 0, 3 -> {
                            return h > 0.8 ? Blocks.ANDESITE.defaultBlockState() : STONE;
                        }
                        case 1 -> {
                            return Blocks.ANDESITE.defaultBlockState();
                        }
                        case 2 -> {
                            return h > 0.5 ? Blocks.STONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                        }
                        case 4 -> {
                            return depth == 0 && h > 0.2 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : STONE;
                        }
                        default -> {
                            return h > 0.3 ? Blocks.ANDESITE.defaultBlockState() : Blocks.DIORITE.defaultBlockState();
                        }
                    }
                }
            }
        }
        return y < 0 ? DEEPSLATE : STONE;
    }

    // 草の土地の上面: 草を土台に、土地ごとのまだら (2026-11-03 の声「地形に作り込み」)。
    // 林は落ち葉の土 (ポドゾル) と苔、針葉樹は落ち葉の土と荒れた土、サバンナと平原は乾いた荒れ土。野 (花の丘) は草のまま
    static BlockState grassTop(int x, int z, int biome, double n) {
        double p = ContinentMap.value(x / 14.0, z / 14.0, 0x7B3D);
        double q = ContinentMap.hash(x, z, 0x1F2B);
        switch (biome) {
            case ContinentBiomes.FOREST, ContinentBiomes.BIRCH_FOREST, ContinentBiomes.FLOWER_FOREST -> {
                if (p > 0.5 && q > -0.3) {
                    return Blocks.PODZOL.defaultBlockState();
                }
                if (p < -0.62 && q > 0.1) {
                    return Blocks.MOSS_BLOCK.defaultBlockState();
                }
            }
            case ContinentBiomes.TAIGA, ContinentBiomes.PINE_TAIGA -> {
                if (n > 0.35 || p > 0.55 && q > 0) {
                    return Blocks.PODZOL.defaultBlockState();
                }
                if (p < -0.6 && q > 0.2) {
                    return Blocks.COARSE_DIRT.defaultBlockState();
                }
            }
            case ContinentBiomes.SAVANNA -> {
                if (p > 0.35 && q > -0.2) {
                    return Blocks.COARSE_DIRT.defaultBlockState();
                }
            }
            case ContinentBiomes.PLAINS, ContinentBiomes.WINDSWEPT_HILLS -> {
                if (p > 0.68 && q > 0.1) {
                    return Blocks.COARSE_DIRT.defaultBlockState();
                }
            }
            default -> {
            }
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    // 上面の Y を (x0-1..x0+16, z0-1..z0+16) の 18 x 18 で
    static int[] tops(ContinentMap map, int x0, int z0) {
        int[] t = new int[18 * 18];
        for (int dz = 0; dz < 18; dz++) {
            for (int dx = 0; dx < 18; dx++) {
                t[dz * 18 + dx] = map.surface(x0 + dx - 1, z0 + dz - 1);
            }
        }
        return t;
    }

    static int slope(int[] t, int dx, int dz) {
        int c = t[(dz + 1) * 18 + dx + 1];
        int s = 0;
        s = Math.max(s, Math.abs(c - t[(dz + 1) * 18 + dx]));
        s = Math.max(s, Math.abs(c - t[(dz + 1) * 18 + dx + 2]));
        s = Math.max(s, Math.abs(c - t[dz * 18 + dx + 1]));
        s = Math.max(s, Math.abs(c - t[(dz + 2) * 18 + dx + 1]));
        return s;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        ContinentMap map = ContinentMap.get();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        int[] t = tops(map, x0, z0);
        List<int[]> roads = roadSegments(x0, z0);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection s : sections) {
            s.acquire();
        }
        try {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    int x = x0 + dx;
                    int z = z0 + dz;
                    int top = Math.min(t[(dz + 1) * 18 + dx + 1], maxY - 1);
                    int sl = slope(t, dx, dz);
                    // 段丘の土地の段差 2 と、道の切り通し (道の縁から 6 以内の段差 2) は、土の壁ではなく岩の面にする (2026-11-03)
                    if (sl == 2 && (map.terraceAt(x, z) > 0.25 || !roads.isEmpty() && roadDistance(roads, x, z) < ROAD_EDGE + 6)) {
                        sl = 3;
                    }
                    int biome = map.biome(x, z);
                    int upto = Math.max(top, SEA_LEVEL - 1);
                    BlockState road = road(roads, x, z);
                    // 道が川を渡る所は、水面に板の橋
                    boolean bridge = road != null && top < SEA_LEVEL - 1 && biome == ContinentBiomes.RIVER;
                    // 外れの帯の見せ場 (FrontierTerrain): 上の数段の土と、置く物 (肋骨・浮き岩・岩礁)。道の上は変えない
                    int region = map.region(x, z);
                    double far = road == null ? FrontierTerrain.weight(region, x, z) : 0;
                    for (int y = minY; y <= upto; y++) {
                        BlockState st;
                        if (y <= top) {
                            if (y == top && road != null && !bridge) {
                                st = road;
                            } else {
                                BlockState f = far > 0 ? FrontierTerrain.ground(region, far, x, y, z, top, top - y, sl) : null;
                                st = f != null ? f : solid(x, y, z, top, sl, biome);
                            }
                        } else {
                            st = bridge && y == SEA_LEVEL - 1 ? BRIDGE : WATER;
                        }
                        sections[chunk.getSectionIndex(y)].setBlockState(dx, y & 15, dz, st, false);
                    }
                    if (far > 0.9) {
                        final int fdx = dx;
                        final int fdz = dz;
                        FrontierTerrain.column(map, x, z, top, (y0, y1, state) -> {
                            for (int y = Math.max(minY + 1, y0); y <= Math.min(maxY - 1, y1); y++) {
                                sections[chunk.getSectionIndex(y)].setBlockState(fdx, y & 15, fdz, state, false);
                            }
                        });
                    }
                }
            }
        } finally {
            for (LevelChunkSection s : sections) {
                s.release();
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    // ---------------------------------------------------------------- 道 (layout.json の道。幅 5 の土の道、縁は荒れた土と砂利)
    // 道は島も街道も幅 8 (土の道 8、縁 1 の荒れた土と砂利。2026-10-28 の声「基本は道の幅 8 マス」)。WIDE は前の街道の上乗せ (今は 0)
    static final double ROAD_HALF = 4.0;
    static final double ROAD_EDGE = 4.9;
    static final double WIDE = 0.0;

    // 道の芯から縁までの幅 (置き場所を道から離す仕組みが使う。RoadClearance・opening/site/ChapterSites)
    public static double roadEdge() {
        return ROAD_EDGE;
    }

    // このチャンク (と縁 4) にかかる道の線分 {x0, z0, x1, z1}
    static List<int[]> roadSegments(int x0, int z0) {
        List<int[]> out = new java.util.ArrayList<>();
        List<List<BlockPos>> all = ContinentLayout.get().roads();
        for (int li = 0; li < all.size(); li++) {
            List<BlockPos> line = all.get(li);
            // 島の道 (はじまりの島の中) は細いまま
            int wide = line.isEmpty() || ContinentMap.get().region(line.get(0).getX(), line.get(0).getZ()) == ContinentMap.OUTSKIRTS ? 0 : 1;
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                int pad = 8;
                if (Math.max(a.getX(), b.getX()) < x0 - pad || Math.min(a.getX(), b.getX()) > x0 + 15 + pad
                        || Math.max(a.getZ(), b.getZ()) < z0 - pad || Math.min(a.getZ(), b.getZ()) > z0 + 15 + pad) {
                    continue;
                }
                out.add(new int[] {a.getX(), a.getZ(), b.getX(), b.getZ(), wide, li});
            }
        }
        return out;
    }

    // その場所の道の上面 (道でなければ null)。建物の地均し (opening/site/SiteJob) が、道の上を草で塗らないために使う
    public static BlockState roadAt(int x, int z) {
        return road(roadSegments(x & ~15, z & ~15), x, z);
    }

    // 道の芯からの距離 (道が無ければ大きな値)。置き物を道から離すのに使う
    static double roadDistance(List<int[]> segs, int x, int z) {
        double best = 1e9;
        for (int[] g : segs) {
            double dx = g[2] - g[0];
            double dz = g[3] - g[1];
            double len2 = dx * dx + dz * dz;
            double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - g[0]) * dx + (z - g[1]) * dz) / len2));
            best = Math.min(best, Math.hypot(x - (g[0] + u * dx), z - (g[1] + u * dz)) - (g.length > 4 && g[4] == 1 ? WIDE : 0));
        }
        return best;
    }

    // 道どうしが出会う所の丸み (2026-11-08 の声「道が上手く曲がってない」。斜めに合わさる二本の道のあいだに、草の楔がぎざぎざに残っていた)。
    // 同じ道の中はふつうの近さ、別の道どうしは滑らかな最小 (半径 JOIN_ROUND) で合わせ、内側の角を埋める
    static final double JOIN_ROUND = 8.0;

    static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    // 道の上面の石 (道でなければ null)。幅を少し揺らす
    static BlockState road(List<int[]> segs, int x, int z) {
        if (segs.isEmpty()) {
            return null;
        }
        // 道ごとの近さ (同じ道の線分は最小)
        java.util.Map<Integer, Double> perLine = new java.util.HashMap<>();
        for (int[] g : segs) {
            double dx = g[2] - g[0];
            double dz = g[3] - g[1];
            double len2 = dx * dx + dz * dz;
            double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - g[0]) * dx + (z - g[1]) * dz) / len2));
            double d = Math.hypot(x - (g[0] + u * dx), z - (g[1] + u * dz)) - (g.length > 4 && g[4] == 1 ? WIDE : 0);
            perLine.merge(g.length > 5 ? g[5] : -1, d, Math::min);
        }
        double best = 1e9;
        for (double d : perLine.values()) {
            best = best >= 1e8 ? d : smoothMin(best, d, JOIN_ROUND);
        }
        // 縁は少しだけ揺らす (幅 8 がほぼ保たれる)
        double d = best - ContinentMap.value(x / 23.0, z / 23.0, 0x0AD5) * 0.4;
        if (d < ROAD_HALF) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        if (d < ROAD_EDGE) {
            return ContinentMap.hash(x, z, 0x3E11) > 0.2 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        }
        return null;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int top = ContinentMap.get().surface(x, z);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) {
            return top + 1;
        }
        return Math.max(top, SEA_LEVEL - 1) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        ContinentMap map = ContinentMap.get();
        int[] t = tops(map, x, z);
        int top = t[18 + 1];
        int sl = slope(t, 0, 0);
        int biome = map.biome(x, z);
        int minY = height.getMinBuildHeight();
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            states[i] = y <= top ? solid(x, y, z, top, sl, biome) : y < SEA_LEVEL ? WATER : AIR;
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        ContinentMap map = ContinentMap.get();
        info.add("Continent: surface " + map.surface(pos.getX(), pos.getZ()) + " land " + map.biome(pos.getX(), pos.getZ()));
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager,
            ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    // 生物群系の木や草のあとに、野の小さな飾り (岩・倒木・茂みなど)
    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        ContinentScatter.decorate(level, chunk);
    }

    // 生まれたときの動物 (羊・牛など。生物群系のとおり。羊を連れ戻す頼みなどに要る)
    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        net.minecraft.world.level.ChunkPos chunkpos = level.getCenter();
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = level.getBiome(chunkpos.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        net.minecraft.world.level.levelgen.WorldgenRandom random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), chunkpos.getMinBlockX(), chunkpos.getMinBlockZ());
        net.minecraft.world.level.NaturalSpawner.spawnMobsForChunkGeneration(level, holder, chunkpos, random);
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }
}
