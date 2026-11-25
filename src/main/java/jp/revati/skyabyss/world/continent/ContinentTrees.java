package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

// 大陸の木 (docs/design/40 の 6 章)。2026-11-09 の声「マップにある木とかは完全オリジナルにして。今はバニラ感ある。
// 木の幅が詰まりすぎなのと、バニラの木を植えているだけなので、小さい」「砂漠なのに、緑の森あるの変じゃない？」:
// 生物群系の木 (バニラの trees_* の飾り) は抜き (ContinentChunkGenerator.features)、ここで土地ごとの形の木を、間を空けて植える。
//   置き方: 土地ごとの升の幅 (SPACING) の格子に、升ごとに決まった一点と決まった乱数。升の中に一本までなので、木と木は升の幅の半分ほど離れる
//   形: 広葉の大木 (枝が張って葉の房が三つ四つ)・白樺 (細く高い)・針葉 (段の傘。雪の国は雪をかぶる)・平らな傘の木 (潮の土地)・
//       柳 (湿地。垂れる葉)・花の木 (花の森。桜色)・枯れ木 (灰の荒野と砂丘。葉なし)
//   砂丘 (desert) とそのまわり (SAND_NEAR) には緑の木を植えない。砂の上は枯れ木だけ
final class ContinentTrees {
    private ContinentTrees() {
    }

    enum Kind {
        BROAD, OLD_BROAD, BIRCH, CONIFER, SNOW_CONIFER, PINE, FLAT, WILLOW, BLOSSOM, ASH,
        // 岩鯨の土地の木 (WhaleTrees)
        FIR, FIR_BIG, PALE, MOSS_LOW, TEAL,
        // 鐘の野の木 (MeadowTrees。花傘の樹の若木・ふつう・古木)
        BLOSSOM_YOUNG, BLOSSOM_UMBRELLA, BLOSSOM_OLD,
        // 潮の浜の木 (TideTrees。汐傘の樹の若木・ふつう・古木)
        PARASOL_YOUNG, PARASOL, PARASOL_OLD,
        // 嵐の高地の木 (StormTrees。雷晶の螺旋木の若木・ふつう・古木)
        SPIRAL_YOUNG, SPIRAL, SPIRAL_OLD,
        // 灰の荒野の木 (EmberTrees。煤垂れの樹の若木・ふつう・古木)
        SOOT_YOUNG, SOOT, SOOT_OLD,
        // 外れの木 (OutskirtsTrees。雲玉の樫の若木・ふつう・古木)
        CLOUD_YOUNG, CLOUD, CLOUD_OLD,
        // 砂漠 (どの土地でも) と灰の荒野の外の荒野の木 (DesertTrees。砂紐の樹の若木・ふつう・古木)
        CROOK_YOUNG, CROOK, CROOK_OLD
    }

    // 土地ごとの {升の幅, 植える割合}。0 なら植えない
    // 場所ごとの {升の幅, 植える割合}。鐘の野 (都のまわり) の平原と草地は花傘の樹を近めに、ゆっくりした揺らぎで林と開けた野に分ける
    static double[] spacingAt(ContinentMap map, int x, int z) {
        int biome = map.biome(x, z);
        if (map.region(x, z) == ContinentMap.MEADOW && (biome == ContinentBiomes.PLAINS || biome == ContinentBiomes.MEADOW)) {
            double grove = ContinentMap.value(x / 70.0, z / 70.0, 0x51A7);
            return new double[] {17, Math.max(0.12, 0.5 + 0.45 * grove)};
        }
        // 鐘の野の林 (森の生物群系): 大きな傘が重なって一面の桜色にならないよう、升を広げて間を空ける
        if (map.region(x, z) == ContinentMap.MEADOW && spacing(biome)[0] > 0 && spacing(biome)[0] < 17) {
            return new double[] {20, 0.6};
        }
        // 潮の浜: サバンナは汐傘の樹をゆっくりした揺らぎで木立と開けた野に分ける。湿地は少し近めに
        if (map.region(x, z) == ContinentMap.TIDE && biome == ContinentBiomes.SAVANNA) {
            double grove = ContinentMap.value(x / 80.0, z / 80.0, 0x71DE);
            return new double[] {22, Math.max(0.15, 0.55 + 0.4 * grove)};
        }
        if (map.region(x, z) == ContinentMap.TIDE && biome == ContinentBiomes.SWAMP) {
            return new double[] {20, 0.45};
        }
        // 嵐の高地の松の森: 螺旋の枝が広いので升を広げる
        if (map.region(x, z) == ContinentMap.STORM && biome == ContinentBiomes.PINE_TAIGA) {
            return new double[] {16, 0.8};
        }
        // 灰の荒野: 荒野はまばら (揺らぎで寄り合う所だけ)、林の荒野は傘が重ならない程度に
        if (map.region(x, z) == ContinentMap.EMBER && biome == ContinentBiomes.BADLANDS) {
            double grove = ContinentMap.value(x / 90.0, z / 90.0, 0x50D7);
            return new double[] {30, Math.max(0.08, 0.2 + 0.35 * grove)};
        }
        if (map.region(x, z) == ContinentMap.EMBER && biome == ContinentBiomes.WOODED_BADLANDS) {
            return new double[] {19, 0.6};
        }
        // 外れ: 森と白樺の森は玉が重ならない程度に升を広げる。草地と平原は揺らぎで木立と開けた野
        if (map.region(x, z) == ContinentMap.OUTSKIRTS && (biome == ContinentBiomes.FOREST || biome == ContinentBiomes.BIRCH_FOREST)) {
            return new double[] {18, 0.75};
        }
        if (map.region(x, z) == ContinentMap.OUTSKIRTS && (biome == ContinentBiomes.MEADOW || biome == ContinentBiomes.PLAINS)) {
            double grove = ContinentMap.value(x / 75.0, z / 75.0, 0x0C1D);
            return new double[] {24, Math.max(0.12, 0.4 + 0.4 * grove)};
        }
        // 砂漠: 砂紐の樹を揺らぎで寄り合わせる (オアシスのまわりのような木立と、何もない砂の広がり)
        if (biome == ContinentBiomes.DESERT) {
            double grove = ContinentMap.value(x / 85.0, z / 85.0, 0x5A2D);
            return new double[] {26, Math.max(0.1, 0.3 + 0.4 * grove)};
        }
        return spacing(biome);
    }

    static double[] spacing(int biome) {
        return switch (biome) {
            case ContinentBiomes.FOREST -> new double[] {15, 0.85};
            case ContinentBiomes.BIRCH_FOREST -> new double[] {13, 0.85};
            case ContinentBiomes.FLOWER_FOREST -> new double[] {16, 0.75};
            case ContinentBiomes.TAIGA -> new double[] {13, 0.85};
            case ContinentBiomes.PINE_TAIGA -> new double[] {12, 0.9};
            case ContinentBiomes.PLAINS -> new double[] {30, 0.4};
            case ContinentBiomes.MEADOW -> new double[] {26, 0.45};
            case ContinentBiomes.SAVANNA -> new double[] {28, 0.45};
            case ContinentBiomes.WINDSWEPT_HILLS -> new double[] {20, 0.5};
            case ContinentBiomes.GRAVELLY_HILLS -> new double[] {26, 0.3};
            case ContinentBiomes.SNOWY_SLOPES -> new double[] {22, 0.35};
            case ContinentBiomes.SWAMP -> new double[] {17, 0.7};
            case ContinentBiomes.WOODED_BADLANDS -> new double[] {16, 0.6};
            case ContinentBiomes.BADLANDS -> new double[] {40, 0.25};
            case ContinentBiomes.DESERT -> new double[] {44, 0.18};
            default -> new double[] {0, 0};
        };
    }

    static Kind kind(int biome, RandomSource r) {
        return switch (biome) {
            case ContinentBiomes.FOREST, ContinentBiomes.MEADOW, ContinentBiomes.PLAINS -> r.nextFloat() < 0.18f ? Kind.OLD_BROAD
                    : biome == ContinentBiomes.FOREST && r.nextFloat() < 0.15f ? Kind.BIRCH : Kind.BROAD;
            case ContinentBiomes.BIRCH_FOREST -> r.nextFloat() < 0.15f ? Kind.BROAD : Kind.BIRCH;
            case ContinentBiomes.FLOWER_FOREST -> r.nextFloat() < 0.3f ? Kind.BROAD : Kind.BLOSSOM;
            case ContinentBiomes.TAIGA, ContinentBiomes.WINDSWEPT_HILLS, ContinentBiomes.GRAVELLY_HILLS -> Kind.CONIFER;
            case ContinentBiomes.PINE_TAIGA -> r.nextFloat() < 0.3f ? Kind.CONIFER : Kind.PINE;
            case ContinentBiomes.SNOWY_SLOPES -> Kind.SNOW_CONIFER;
            case ContinentBiomes.SAVANNA -> Kind.FLAT;
            case ContinentBiomes.SWAMP -> Kind.WILLOW;
            case ContinentBiomes.WOODED_BADLANDS -> Kind.ASH;
            default -> Kind.CROOK;
        };
    }

    static boolean green(Kind k) {
        return k != Kind.ASH && k != Kind.CROOK_YOUNG && k != Kind.CROOK && k != Kind.CROOK_OLD;
    }

    // 砂丘のまわりのこの幅には緑の木を植えない
    static final int SAND_NEAR = 24;

    static void plant(WorldGenLevel level, ChunkAccess chunk) {
        ContinentMap map = ContinentMap.get();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        List<int[]> roads = ContinentChunkGenerator.roadSegments(x0, z0);
        // このチャンクにある升の幅 (土地ごと)
        List<Integer> sizes = new ArrayList<>();
        for (int dx = 0; dx <= 16; dx += 8) {
            for (int dz = 0; dz <= 16; dz += 8) {
                int s = (int) spacingAt(map, x0 + Math.min(15, dx), z0 + Math.min(15, dz))[0];
                if (s > 0 && !sizes.contains(s)) {
                    sizes.add(s);
                }
            }
        }
        for (int s : sizes) {
            for (int gx = Math.floorDiv(x0, s); gx <= Math.floorDiv(x0 + 15, s); gx++) {
                for (int gz = Math.floorDiv(z0, s); gz <= Math.floorDiv(z0 + 15, s); gz++) {
                    RandomSource r = RandomSource.create(gx * 0x2F0B3A5L ^ gz * 0x6C8E9CF5L ^ s * 0x1F1F1F1FL ^ 0x7EE5L);
                    int margin = Math.max(1, s / 5);
                    int x = gx * s + margin + r.nextInt(Math.max(1, s - 2 * margin));
                    int z = gz * s + margin + r.nextInt(Math.max(1, s - 2 * margin));
                    if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) {
                        continue;
                    }
                    int biome = map.biome(x, z);
                    double[] sp = spacingAt(map, x, z);
                    if ((int) sp[0] != s || r.nextFloat() >= sp[1]) {
                        continue;
                    }
                    Kind k = kind(biome, r);
                    int region = map.region(x, z);
                    if (biome == ContinentBiomes.DESERT || k == Kind.CROOK && region != ContinentMap.EMBER && region != ContinentMap.WHALE
                            && region != ContinentMap.MEADOW) {
                        // 砂漠はどの土地でも砂紐の樹 (若木が多め)。灰の荒野の外の荒野も
                        float roll = r.nextFloat();
                        k = roll < 0.15f ? Kind.CROOK_OLD : roll < 0.5f ? Kind.CROOK_YOUNG : Kind.CROOK;
                    } else if (map.region(x, z) == ContinentMap.WHALE) {
                        k = whaleKind(biome, r, x, z);
                    } else if (map.region(x, z) == ContinentMap.MEADOW) {
                        float roll = r.nextFloat();
                        k = roll < 0.15f ? Kind.BLOSSOM_OLD : roll < 0.4f ? Kind.BLOSSOM_YOUNG : Kind.BLOSSOM_UMBRELLA;
                    } else if (map.region(x, z) == ContinentMap.TIDE && (biome == ContinentBiomes.SAVANNA || biome == ContinentBiomes.SWAMP)) {
                        float roll = r.nextFloat();
                        boolean swamp = biome == ContinentBiomes.SWAMP;
                        k = roll < (swamp ? 0.08f : 0.15f) ? Kind.PARASOL_OLD : roll < (swamp ? 0.55f : 0.4f) ? Kind.PARASOL_YOUNG : Kind.PARASOL;
                    } else if (map.region(x, z) == ContinentMap.STORM && green(k)) {
                        // 嵐の高地: 森は古木まじり、吹きさらしと雪の斜面は若木が多い
                        float roll = r.nextFloat();
                        boolean forest = biome == ContinentBiomes.PINE_TAIGA;
                        k = roll < (forest ? 0.2f : 0.08f) ? Kind.SPIRAL_OLD : roll < (forest ? 0.45f : 0.6f) ? Kind.SPIRAL_YOUNG : Kind.SPIRAL;
                    } else if (map.region(x, z) == ContinentMap.EMBER && (biome == ContinentBiomes.BADLANDS || biome == ContinentBiomes.WOODED_BADLANDS)) {
                        // 灰の荒野: 林は古木まじり、開けた荒野は若木が多い
                        float roll = r.nextFloat();
                        boolean wood = biome == ContinentBiomes.WOODED_BADLANDS;
                        k = roll < (wood ? 0.2f : 0.1f) ? Kind.SOOT_OLD : roll < (wood ? 0.45f : 0.6f) ? Kind.SOOT_YOUNG : Kind.SOOT;
                    } else if (map.region(x, z) == ContinentMap.OUTSKIRTS && green(k)) {
                        // 外れ: 森は古木まじり、草地は若木が多い
                        float roll = r.nextFloat();
                        boolean wood = biome == ContinentBiomes.FOREST || biome == ContinentBiomes.BIRCH_FOREST;
                        k = roll < (wood ? 0.2f : 0.1f) ? Kind.CLOUD_OLD : roll < (wood ? 0.45f : 0.55f) ? Kind.CLOUD_YOUNG : Kind.CLOUD;
                    }
                    if (green(k) && nearSand(map, x, z)) {
                        continue;
                    }
                    BlockPos ground = ground(level, roads, x, z, reach(k));
                    if (ground == null) {
                        continue;
                    }
                    grow(level, r, ground.above(), k);
                }
            }
        }
    }

    // 木の横の広がり (道・置かない円からこれだけ離す)
    static int reach(Kind k) {
        return switch (k) {
            case OLD_BROAD, FLAT -> 6;
            case BROAD, WILLOW, BLOSSOM -> 5;
            case CONIFER, SNOW_CONIFER, ASH -> 4;
            case BIRCH, PINE -> 3;
            case FIR, PALE -> 4;
            case FIR_BIG -> 8;
            case MOSS_LOW -> 7;
            case TEAL -> 12;
            case BLOSSOM_YOUNG -> 7;
            case BLOSSOM_UMBRELLA -> 11;
            case BLOSSOM_OLD -> 14;
            case PARASOL_YOUNG -> 7;
            case PARASOL -> 10;
            case PARASOL_OLD -> 14;
            case SPIRAL_YOUNG -> 8;
            case SPIRAL -> 11;
            case SPIRAL_OLD -> 14;
            case SOOT_YOUNG -> 8;
            case SOOT -> 10;
            case SOOT_OLD -> 13;
            case CLOUD_YOUNG -> 9;
            case CLOUD -> 12;
            case CLOUD_OLD -> 15;
            case CROOK_YOUNG -> 10;
            case CROOK -> 12;
            case CROOK_OLD -> 15;
        };
    }

    // 岩鯨の土地の木の選び方 (2026-11-25 の試作で決めた森): 森の土地は垂れ枝の樅 (大木まじり)・骨白の細木、
    // 森の中の揺らぎの島だけ灯り葉の樹の林。開けた土地は苔の低木・骨白の細木・ときどき樅
    static Kind whaleKind(int biome, RandomSource r, int x, int z) {
        boolean forest = switch (biome) {
            case ContinentBiomes.TAIGA, ContinentBiomes.PINE_TAIGA, ContinentBiomes.FOREST, ContinentBiomes.BIRCH_FOREST,
                    ContinentBiomes.WINDSWEPT_HILLS, ContinentBiomes.GRAVELLY_HILLS, ContinentBiomes.SNOWY_SLOPES -> true;
            default -> false;
        };
        float roll = r.nextFloat();
        if (forest) {
            if (ContinentMap.value(x / 55.0, z / 55.0, 0x7EA1) > 0.4 && roll < 0.55f) {
                return Kind.TEAL;
            }
            return roll < 0.2f ? Kind.FIR_BIG : roll < 0.32f ? Kind.PALE : Kind.FIR;
        }
        return roll < 0.5f ? Kind.MOSS_LOW : roll < 0.75f ? Kind.PALE : Kind.FIR;
    }

    static boolean nearSand(ContinentMap map, int x, int z) {
        for (int dx = -SAND_NEAR; dx <= SAND_NEAR; dx += SAND_NEAR / 2) {
            for (int dz = -SAND_NEAR; dz <= SAND_NEAR; dz += SAND_NEAR / 2) {
                if (map.biome(x + dx, z + dz) == ContinentBiomes.DESERT) {
                    return true;
                }
            }
        }
        return false;
    }

    // 植える地面 (草・土・雪・砂・粘土)。道・町や見どころの円・巨神の居場所・祠の広場・水の上・急な斜面は null
    static BlockPos ground(WorldGenLevel level, List<int[]> roads, int x, int z, int reach) {
        if (ContinentChunkGenerator.roadDistance(roads, x, z) < ContinentChunkGenerator.ROAD_EDGE + reach + 3) {
            return null;
        }
        if (ContinentScatter.kept(x, z) || ContinentScatter.nearIslandRoad(x, z) || ContinentLairs.inside(x, z) || ContinentLairs.onPad(x, z)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 12 && !ContinentScatter.solidGround(level.getBlockState(m)); i++) {
            if (!level.getBlockState(m).getFluidState().isEmpty()) {
                return null;
            }
            m.move(Direction.DOWN);
        }
        BlockState top = level.getBlockState(m);
        if (!ContinentScatter.solidGround(top) || m.getY() < ContinentChunkGenerator.SEA_LEVEL || top.is(Blocks.DIRT_PATH)
                || top.is(Blocks.COBBLESTONE) || top.is(Blocks.MOSSY_COBBLESTONE)) {
            return null;
        }
        // 急な斜面には植えない (まわり 2 の高さの差)
        int h = m.getY();
        for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            int hh = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + d[0], z + d[1]) - 1;
            if (Math.abs(hh - h) > 3) {
                return null;
            }
        }
        // 幹の通る所が空いている
        for (int dy = 1; dy <= 4; dy++) {
            if (!ContinentScatter.free(level.getBlockState(m.above(dy)))) {
                return null;
            }
        }
        return m.immutable();
    }

    // ---------------------------------------------------------------- 形
    static void grow(WorldGenLevel level, RandomSource r, BlockPos base, Kind k) {
        Grower g = new Grower(level, r);
        switch (k) {
            case BROAD -> broad(g, base, r, false);
            case OLD_BROAD -> broad(g, base, r, true);
            case BIRCH -> birch(g, base, r);
            case CONIFER -> conifer(g, base, r, false, false);
            case SNOW_CONIFER -> conifer(g, base, r, true, false);
            case PINE -> conifer(g, base, r, false, true);
            case FLAT -> flat(g, base, r);
            case WILLOW -> willow(g, base, r);
            case BLOSSOM -> blossom(g, base, r);
            case ASH -> dead(g, base, r, Blocks.DARK_OAK_LOG, true);
            case FIR -> WhaleTrees.fir(g, base, r, false);
            case FIR_BIG -> WhaleTrees.fir(g, base, r, true);
            case PALE -> WhaleTrees.pale(g, base, r);
            case MOSS_LOW -> WhaleTrees.mossLow(g, base, r);
            case TEAL -> WhaleTrees.teal(g, base, r);
            case BLOSSOM_YOUNG -> MeadowTrees.blossomUmbrella(g, base, r, 0.65);
            case BLOSSOM_UMBRELLA -> MeadowTrees.blossomUmbrella(g, base, r, 1.0);
            case BLOSSOM_OLD -> MeadowTrees.blossomUmbrella(g, base, r, 1.35);
            case PARASOL_YOUNG -> TideTrees.parasol(g, base, r, 0.7);
            case PARASOL -> TideTrees.parasol(g, base, r, 1.0);
            case PARASOL_OLD -> TideTrees.parasol(g, base, r, 1.3);
            case SPIRAL_YOUNG -> StormTrees.spiral(g, base, r, 0.7);
            case SPIRAL -> StormTrees.spiral(g, base, r, 1.0);
            case SPIRAL_OLD -> StormTrees.spiral(g, base, r, 1.3);
            case SOOT_YOUNG -> EmberTrees.sootWeeper(g, base, r, 0.75);
            case SOOT -> EmberTrees.sootWeeper(g, base, r, 1.0);
            case SOOT_OLD -> EmberTrees.sootWeeper(g, base, r, 1.3);
            case CLOUD_YOUNG -> OutskirtsTrees.cloudOak(g, base, r, 0.75);
            case CLOUD -> OutskirtsTrees.cloudOak(g, base, r, 1.0);
            case CLOUD_OLD -> OutskirtsTrees.cloudOak(g, base, r, 1.3);
            case CROOK_YOUNG -> DesertTrees.crook(g, base, r, 0.75);
            case CROOK -> DesertTrees.crook(g, base, r, 1.0);
            case CROOK_OLD -> DesertTrees.crook(g, base, r, 1.3);
        }
        g.finish();
    }

    // 広葉の木: 根の張り出し・少し曲がる幹・斜めに伸びる枝・枝の先と頂の葉の房。大木は幹の下が 2×2 で枝が多い
    static void broad(Grower g, BlockPos base, RandomSource r, boolean old) {
        Block log = Blocks.OAK_LOG;
        BlockState leaf = mix(r, Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES, 0.18f);
        g.alt(Blocks.AZALEA_LEAVES, 0.18f);
        int h = old ? 11 + r.nextInt(4) : 7 + r.nextInt(3);
        roots(g, base, r, log, old ? 4 : 2 + r.nextInt(2), old ? 3 : 2);
        if (old) {
            for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}}) {
                g.line(base.offset(d[0], 0, d[1]), base.offset(d[0], h / 2, d[1]), log);
            }
        }
        BlockPos top = trunk(g, base, r, log, h, 0.25f);
        int branches = old ? 4 + r.nextInt(2) : 2 + r.nextInt(2);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < branches; i++) {
            double a = a0 + i * Math.PI * 2 / branches + (r.nextDouble() - 0.5) * 0.6;
            int from = (int) Math.round(h * (0.5 + 0.3 * r.nextDouble()));
            int len = old ? 4 + r.nextInt(3) : 3 + r.nextInt(2);
            BlockPos s = base.above(from);
            BlockPos e = s.offset((int) Math.round(Math.cos(a) * len), 1 + r.nextInt(3), (int) Math.round(Math.sin(a) * len));
            g.line(s, e, log);
            g.blob(e.above(), old ? 3.2 : 2.6, 2.0, leaf);
        }
        g.blob(top.above(), old ? 3.8 : 3.0, old ? 2.6 : 2.3, leaf);
    }

    // 白樺: 細く高い幹、短い小枝、縦に長い房を二つ重ねる
    static void birch(Grower g, BlockPos base, RandomSource r) {
        BlockState leaf = mix(r, Blocks.BIRCH_LEAVES, Blocks.BIRCH_LEAVES, 0f);
        int h = 10 + r.nextInt(4);
        BlockPos top = trunk(g, base, r, Blocks.BIRCH_LOG, h, 0.1f);
        for (int i = 0; i < 2; i++) {
            Direction d = Direction.from2DDataValue(r.nextInt(4));
            BlockPos s = base.above(h / 2 + r.nextInt(3));
            BlockPos e = s.relative(d, 2).above();
            g.line(s, e, Blocks.BIRCH_LOG);
            g.blob(e, 1.6, 1.4, leaf);
        }
        g.blob(top.below(2), 2.3, 3.6, leaf);
        g.blob(top.above(1), 1.6, 1.6, leaf);
    }

    // 針葉樹: 下の幹を見せ、上へ細る段の傘 (一段おきに広い裾)。雪の国は葉の上に雪。松は下半分が裸で上に平たい段
    static void conifer(Grower g, BlockPos base, RandomSource r, boolean snow, boolean pine) {
        Block log = Blocks.SPRUCE_LOG;
        BlockState leaf = mix(r, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_LEAVES, 0f);
        int h = pine ? 15 + r.nextInt(5) : 11 + r.nextInt(5);
        roots(g, base, r, log, 2, 1);
        g.line(base, base.above(h), log);
        int start = pine ? (int) (h * 0.55) : 3;
        double maxR = pine ? 2.8 : 3.4 + r.nextDouble() * 0.6;
        for (int y = start; y <= h; y++) {
            double t = (y - start) / (double) Math.max(1, h - start);
            double rad = maxR * Math.pow(1 - t, pine ? 0.6 : 0.9) + 0.6;
            if (((y - start) & 1) == 1) {
                rad -= 1.1;
            }
            g.disc(base.above(y), rad, leaf);
        }
        g.leaf(base.above(h + 1), leaf);
        g.leaf(base.above(h + 2), leaf);
        if (snow) {
            g.snowOnTop = true;
        }
    }

    // 平らな傘の木: 斜めに曲がって上がる幹から、広く薄い傘。小さな二つ目の傘を枝の先に
    static void flat(Grower g, BlockPos base, RandomSource r) {
        Block log = Blocks.ACACIA_LOG;
        BlockState leaf = mix(r, Blocks.ACACIA_LEAVES, Blocks.ACACIA_LEAVES, 0f);
        int h = 6 + r.nextInt(3);
        Direction lean = Direction.from2DDataValue(r.nextInt(4));
        BlockPos p = base;
        for (int y = 0; y < h; y++) {
            g.log(p, log, Direction.Axis.Y);
            if (y >= h / 2 && y < h - 1 && r.nextFloat() < 0.6f) {
                p = p.relative(lean);
                g.log(p, log, Direction.Axis.Y);
            }
            p = p.above();
        }
        g.flat(p, 4.6 + r.nextDouble(), leaf);
        g.flat(p.above(), 2.6, leaf);
        // 二つ目の傘
        Direction other = lean.getOpposite();
        BlockPos s = base.above(h / 2 + 1);
        BlockPos e = s.relative(other, 3).above(2);
        g.line(s, e, log);
        g.flat(e.above(), 2.4, leaf);
    }

    // 柳: 太く短い幹、丸い傘、縁から垂れる葉の簾
    static void willow(Grower g, BlockPos base, RandomSource r) {
        Block log = Blocks.MANGROVE_LOG;
        BlockState leaf = mix(r, Blocks.MANGROVE_LEAVES, Blocks.OAK_LEAVES, 0.3f);
        g.alt(Blocks.OAK_LEAVES, 0.3f);
        int h = 6 + r.nextInt(3);
        roots(g, base, r, log, 3, 2);
        BlockPos top = trunk(g, base, r, log, h, 0.3f);
        g.blob(top, 4.0, 2.2, leaf);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + r.nextDouble() * 0.2;
            int x = (int) Math.round(Math.cos(a) * 3.6);
            int z = (int) Math.round(Math.sin(a) * 3.6);
            int len = 2 + r.nextInt(4);
            for (int d = 0; d < len; d++) {
                g.leaf(top.offset(x, -1 - d, z), leaf);
            }
        }
    }

    // 花の木: 低く曲がった幹から三方へ上がる枝、丸い桜色の房
    static void blossom(Grower g, BlockPos base, RandomSource r) {
        Block log = Blocks.CHERRY_LOG;
        BlockState leaf = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int h = 4 + r.nextInt(2);
        BlockPos top = trunk(g, base, r, log, h, 0.2f);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double a = a0 + i * Math.PI * 2 / 3;
            int len = 3 + r.nextInt(2);
            BlockPos e = top.offset((int) Math.round(Math.cos(a) * len), 2 + r.nextInt(2), (int) Math.round(Math.sin(a) * len));
            g.line(top, e, log);
            g.blob(e.above(), 2.6, 1.8, leaf);
        }
        g.blob(top.above(3), 2.4, 1.6, leaf);
    }

    // 枯れ木: ねじれた幹と、上へ折れる裸の枝。灰の木は枝が多く、根もとに焦げた石
    static void dead(Grower g, BlockPos base, RandomSource r, Block log, boolean ash) {
        int h = (ash ? 6 : 4) + r.nextInt(4);
        BlockPos top = trunk(g, base, r, log, h, 0.4f);
        int branches = ash ? 3 + r.nextInt(2) : 1 + r.nextInt(2);
        for (int i = 0; i < branches; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            BlockPos s = base.above(h / 2 + r.nextInt(Math.max(1, h / 2)));
            int len = 2 + r.nextInt(2);
            BlockPos e = s.offset((int) Math.round(Math.cos(a) * len), 1 + r.nextInt(2), (int) Math.round(Math.sin(a) * len));
            g.line(s, e, log);
            // 折れて上へ
            g.line(e, e.above(1 + r.nextInt(2)), log);
        }
        g.line(top, top.above(1), log);
        if (ash) {
            g.ground(base.below(), Blocks.COARSE_DIRT.defaultBlockState(), 2, r);
        }
    }

    // 根の張り出し (幹のまわりに横向きの丸太)
    static void roots(Grower g, BlockPos base, RandomSource r, Block log, int n, int len) {
        int start = r.nextInt(4);
        for (int i = 0; i < n; i++) {
            Direction d = Direction.from2DDataValue((start + i) % 4);
            for (int l = 1; l <= len - (r.nextFloat() < 0.4f ? 1 : 0); l++) {
                BlockPos p = base.relative(d, l);
                if (g.free(p)) {
                    g.log(p, log, d.getAxis());
                }
            }
        }
    }

    // 幹 (少しずれながら上がる)。てっぺんを返す
    static BlockPos trunk(Grower g, BlockPos base, RandomSource r, Block log, int h, float wobble) {
        BlockPos p = base;
        Direction drift = Direction.from2DDataValue(r.nextInt(4));
        for (int y = 0; y < h; y++) {
            g.log(p, log, Direction.Axis.Y);
            if (y > 2 && y < h - 1 && r.nextFloat() < wobble) {
                p = p.relative(drift);
                g.log(p, log, Direction.Axis.Y);
            }
            p = p.above();
        }
        return p.below();
    }

    // 主の葉 (混ぜる葉は Grower.alt)
    static BlockState mix(RandomSource r, Block main, Block alt, float altShare) {
        return main.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    // 置く役 (空いた所にだけ置く。葉は幹を上書きしない。最後に雪)
    static final class Grower {
        final WorldGenLevel level;
        final RandomSource r;
        final List<BlockPos> leaves = new ArrayList<>();
        boolean snowOnTop;
        BlockState alt;
        float altShare;

        Grower(WorldGenLevel level, RandomSource r) {
            this.level = level;
            this.r = r;
        }

        // 葉に混ぜるもう一つの葉 (色の深み)
        void alt(Block leaves, float share) {
            alt = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            altShare = share;
        }

        boolean free(BlockPos p) {
            return ContinentScatter.free(level.getBlockState(p));
        }

        void log(BlockPos p, Block log, Direction.Axis axis) {
            BlockState s = level.getBlockState(p);
            if (free(p) || s.getBlock() instanceof LeavesBlock) {
                level.setBlock(p, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), 2);
            }
        }

        void leaf(BlockPos p, BlockState leaf) {
            if (free(p)) {
                level.setBlock(p, alt != null && r.nextFloat() < altShare ? alt : leaf, 2);
                leaves.add(p);
            }
        }

        // a から b へ丸太の線 (向きは長い軸)
        void line(BlockPos a, BlockPos b, Block log) {
            int dx = b.getX() - a.getX();
            int dy = b.getY() - a.getY();
            int dz = b.getZ() - a.getZ();
            int n = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            Direction.Axis axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? Direction.Axis.Y
                    : Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
            for (int i = 0; i <= n; i++) {
                double t = n == 0 ? 0 : i / (double) n;
                log(new BlockPos(a.getX() + (int) Math.round(dx * t), a.getY() + (int) Math.round(dy * t), a.getZ() + (int) Math.round(dz * t)), log, axis);
            }
        }

        // 葉の房 (横の半径 rh、縦の半径 rv)。縁は少し欠ける
        void blob(BlockPos c, double rh, double rv, BlockState leaf) {
            int ih = (int) Math.ceil(rh);
            int iv = (int) Math.ceil(rv);
            for (int dy = -iv; dy <= iv; dy++) {
                for (int dx = -ih; dx <= ih; dx++) {
                    for (int dz = -ih; dz <= ih; dz++) {
                        double d = (dx * dx + dz * dz) / (rh * rh) + dy * dy / (rv * rv);
                        if (d > 1 || d > 0.7 && r.nextFloat() < 0.35f) {
                            continue;
                        }
                        leaf(c.offset(dx, dy, dz), leaf);
                    }
                }
            }
        }

        // 一段の円 (針葉の段)
        void disc(BlockPos c, double rad, BlockState leaf) {
            int ir = (int) Math.ceil(rad);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rad || d > rad - 0.8 && r.nextFloat() < 0.4f) {
                        continue;
                    }
                    leaf(c.offset(dx, 0, dz), leaf);
                }
            }
        }

        // 平らな傘 (縁は欠ける)
        void flat(BlockPos c, double rad, BlockState leaf) {
            disc(c, rad, leaf);
        }

        // 根もとの土をまだらに
        void ground(BlockPos c, BlockState s, int rad, RandomSource r) {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos p = c.offset(dx, 0, dz);
                    if (dx * dx + dz * dz <= rad * rad && r.nextFloat() < 0.6f && ContinentScatter.solidGround(level.getBlockState(p))
                            && free(p.above())) {
                        level.setBlock(p, s, 2);
                    }
                }
            }
        }

        void finish() {
            if (!snowOnTop) {
                return;
            }
            for (BlockPos p : leaves) {
                BlockPos up = p.above();
                if (level.getBlockState(up).isAir() && r.nextFloat() < 0.75f) {
                    level.setBlock(up, Blocks.SNOW.defaultBlockState(), 2);
                }
            }
        }
    }
}
