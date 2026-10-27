package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

// 序盤の構造物の場所 (docs/design/40 の 3 章)。天と淵の大陸の図 (ContinentLayout) に描いた場所に、地形生成の高さだけで合わせる
// (チャンクを作らない。木や人の建物は、建てる直前に実際のブロックを見て確かめる: SiteJob)。
// 陸の判定: 水面と海底の高さが同じ (水がない)、群系が海・川・沼などでない、範囲の高さの差が小さい。
// 野営地 = はじまりの島の谷のはじまりの場所のそば、宿り = 鐘の都、足跡 = 島の道の脇の決まった所、骨 = 一章の跡地の中、
// ほかに土地ごとの町・外れの休み場と祠・渡し船の船着き場
public final class SiteFinder {
    // 一つの場所の候補 (中心の高さは地面の上面 + 1)
    public record Spot(BlockPos pos, int rot, boolean mirror) {
    }

    // 「地形の高さ」の問い合わせ先 (Probe = 本物の地形生成、自己テストでは合成の地形)
    public interface Ground {
        int top(int x, int z);

        int topCoarse(int x, int z);

        boolean wet(int x, int z);

        boolean wetCoarse(int x, int z);

        boolean biomeOk(int x, int z);

        int sea();

        // 乾いた群系か (荒野・サバンナ・砂漠・暗い森。四章の灰の地)
        default boolean dryBiome(int x, int z) {
            return false;
        }

    }

    // 「地形の高さ」を問い合わせる道具 (チャンクを作らない)
    public static final class Probe implements Ground {
        private final ServerLevel level;
        private final ChunkGenerator gen;
        private final RandomState random;
        private final Climate.Sampler sampler;
        public final int seaLevel;
        private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
        // 地形生成の高さの問い合わせの回数と時間 (場所探しの重さの記録)
        public int queries;
        public long nanos;

        public Probe(ServerLevel level) {
            this.level = level;
            this.gen = level.getChunkSource().getGenerator();
            this.random = level.getChunkSource().randomState();
            this.sampler = random.sampler();
            this.seaLevel = gen.getSeaLevel();
            tops.defaultReturnValue(Integer.MIN_VALUE);
        }

        // 地面の一番上のブロックの高さ (海底を含む。木は数えない)
        @Override
        public int top(int x, int z) {
            long key = BlockPos.asLong(x, 0, z);
            int v = tops.get(key);
            if (v == Integer.MIN_VALUE) {
                long t0 = System.nanoTime();
                v = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
                nanos += System.nanoTime() - t0;
                queries++;
                tops.put(key, v);
            }
            return v;
        }

        // 大きな範囲 (足跡・骨) を調べる用: 8 ブロックの格子に丸めて問い合わせる (候補どうしで同じ点を使い回せる = 速い)
        @Override
        public int topCoarse(int x, int z) {
            return top(Math.floorDiv(x + 4, 8) * 8, Math.floorDiv(z + 4, 8) * 8);
        }

        // 水の下か (地面が海面より低い)。地形生成の高さの問い合わせは 1 回 数十 ms かかるので、1 地点 1 回だけにしている
        @Override
        public boolean wet(int x, int z) {
            return top(x, z) < seaLevel;
        }

        @Override
        public boolean wetCoarse(int x, int z) {
            return topCoarse(x, z) < seaLevel;
        }

        @Override
        public int sea() {
            return seaLevel;
        }

        public Holder<Biome> biome(int x, int z) {
            return gen.getBiomeSource().getNoiseBiome(x >> 2, top(x, z) >> 2, z >> 2, sampler);
        }

        @Override
        public boolean dryBiome(int x, int z) {
            Holder<Biome> b = biome(x, z);
            return b.is(BiomeTags.IS_BADLANDS) || b.is(BiomeTags.IS_SAVANNA) || b.is(Biomes.DESERT) || b.is(Biomes.DARK_FOREST);
        }

        @Override
        public boolean biomeOk(int x, int z) {
            Holder<Biome> b = biome(x, z);
            return !(b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_JUNGLE) || b.is(BiomeTags.IS_BEACH)
                    || b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP) || b.is(Biomes.DARK_FOREST) || b.is(Biomes.MUSHROOM_FIELDS)
                    || b.is(Biomes.DEEP_DARK) || b.is(Biomes.LUSH_CAVES) || b.is(Biomes.DRIPSTONE_CAVES) || b.is(Biomes.BAMBOO_JUNGLE));
        }
    }

    // 範囲を調べた結果
    public record Area(boolean ok, int min, int max, int median, int count) {
        public int range() {
            return max - min;
        }
    }

    private SiteFinder() {
    }

    // (cx, cz) を中心に、半径 half の正方形 (4 ブロック間隔の格子と四隅) を調べる。水・不適な群系があれば ok = false。maxRange より段差が大きくても false
    public static Area area(Ground p, int cx, int cz, int halfX, int halfZ, int maxRange) {
        return area(p, cx, cz, halfX, halfZ, maxRange, false);
    }

    // fine = true なら 5x5 (野営地のように、一度しか探さない所向け)
    public static Area area(Ground p, int cx, int cz, int halfX, int halfZ, int maxRange, boolean fine) {
        return area(p, cx, cz, halfX, halfZ, maxRange, fine, true);
    }

    // biome = false なら群系を問わない (大きな構造物の最後の手段)
    // 大きな範囲で許す水の割合 (標本の 1/この数)。都の最後の手段では 1/4
    static int WET_FRACTION = 10;

    public static Area area(Ground p, int cx, int cz, int halfX, int halfZ, int maxRange, boolean fine, boolean biome) {
        int wet = 0;
        List<Integer> hs = new ArrayList<>();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        // 3x3 (広い範囲は横に 5) の格子。四隅と辺の中央と中心
        int nx = fine ? 5 : halfX >= 16 ? 5 : 3;
        int nz = fine || halfZ >= 16 ? 5 : 3;
        for (int i = 0; i < nx; i++) {
            int xx = -halfX + (2 * halfX * i) / (nx - 1);
            for (int j = 0; j < nz; j++) {
                int zz = -halfZ + (2 * halfZ * j) / (nz - 1);
                int wx = cx + xx;
                int wz = cz + zz;
                boolean coarse = halfX >= 16 || halfZ >= 16;
                if (coarse ? p.wetCoarse(wx, wz) : p.wet(wx, wz)) {
                    // 都のような大きな範囲は、池や川が少し (1 割まで) あってもよい (整地で埋める。fillWater)
                    if (halfX >= 60 && ++wet <= nx * nz / WET_FRACTION) {
                        continue;
                    }
                    return new Area(false, 0, 0, 0, 0);
                }
                int t = coarse ? p.topCoarse(wx, wz) : p.top(wx, wz);
                hs.add(t);
                min = Math.min(min, t);
                max = Math.max(max, t);
                if (max - min > maxRange) {
                    return new Area(false, min, max, 0, hs.size());
                }
            }
        }
        if (biome && (!p.biomeOk(cx, cz) || !p.biomeOk(cx - halfX, cz - halfZ) || !p.biomeOk(cx + halfX, cz + halfZ))) {
            return new Area(false, min, max, 0, hs.size());
        }
        hs.sort(Comparator.naturalOrder());
        return new Area(true, min, max, hs.get(hs.size() / 2), hs.size());
    }

    // ---------------------------------------------------------------- 計画
    // 決めた場所 (まだ SiteData には書かない。重い探索を別のスレッドでやるため)
    public record Planned(String kind, int index, BlockPos pos, int yaw) {
    }

    // 野営地 (スポーン地点か近い陸)。軽いので、サーバーが始まった瞬間にやる
    @Nullable
    public static BlockPos planCamp(Probe p, BlockPos spawn) {
        return findCamp(p, spawn);
    }

    // 野営地以外 (宿り・足跡・骨) と、章の跡地。重い (地形生成の高さを何百回も問い合わせる) ので、別のスレッドで呼ぶ
    public record Rest(List<Planned> planned, List<ChapterGrounds.Found> grounds) {
    }

    public static List<Planned> planRest(Probe p, BlockPos camp) {
        return planRestAll(p, camp).planned();
    }

    public static Rest planRestAll(Probe p, BlockPos camp) {
        long t0 = System.nanoTime();
        List<Planned> out = new ArrayList<>();
        List<ChapterGrounds.Found> grounds = new ArrayList<>();
        Spot shelter = findShelter(p, camp);
        if (shelter != null) {
            out.add(new Planned(Kind.SHELTER.id, 0, shelter.pos(), encode(shelter.rot(), shelter.mirror())));
            // はじまりの島: 足跡は道の脇の窪地の決まった所 (道にかからない。2026-10-26)。向きは並びの向き
            List<BlockPos> fixed = jp.revati.skyabyss.world.continent.ContinentLayout.get().islandPrints();
            if (!fixed.isEmpty()) {
                planFixedPrints(p, out, fixed);
            }
        }
        // 章の跡地 (都から方位と距離で)。野営地と足跡は避ける
        if (shelter != null) {
            List<ChapterGrounds.Obstacle> avoid = new ArrayList<>();
            avoid.add(new ChapterGrounds.Obstacle(camp, ChapterGrounds.CAMP_GAP));
            for (Planned pl : out) {
                if (pl.kind().equals(Kind.FOOTPRINT.id)) {
                    avoid.add(new ChapterGrounds.Obstacle(pl.pos(), ChapterGrounds.PRINT_GAP));
                }
            }
            grounds.addAll(ChapterGrounds.planAll(p, shelter.pos(), avoid));
        }
        ChapterGrounds.Found whale = grounds.stream().filter(f -> f.chapter() == jp.revati.skyabyss.story.Chapters.WHALE).findFirst().orElse(null);
        Spot ribs = null;
        if (whale != null) {
            // 一章の骨は一章の跡地 (北の丘) の中。整える丘があれば、その形を重ねた地形で探す
            Ground g = ChapterGrounds.overlay(p, List.of(whale));
            ribs = findRibsNear(g, whale.center());
            if (ribs != null) {
                // 祠は骨の脇 (骨の半分の長さ + 余白より外)
                BlockPos shrine = ChapterGrounds.shrineNear(g, ribs.pos(), RIB_HALF_LONG + 8, List.of(new ChapterGrounds.Obstacle(ribs.pos(), RIB_HALF_LONG + 6)));
                grounds.set(grounds.indexOf(whale), new ChapterGrounds.Found(whale.chapter(), whale.center(), whale.shape(), whale.shapeCenter(), whale.baseY(),
                        whale.sea(), whale.restYaw(), whale.fixedRest(), shrine, whale.road(), whale.dist(), whale.deg()));
            }
        }
        if (ribs != null) {
            out.add(new Planned(Kind.RIBS.id, 0, ribs.pos(), encode(ribs.rot(), ribs.mirror())));
        } else {
            SkyAbyss.LOGGER.error("Opening sites: no hill for the rib bones near {}", camp);
        }
        // 土地ごとの町 (図に描いた場所。docs/design/40 の 3 章)
        {
            for (int i = 0; i < jp.revati.skyabyss.world.continent.ContinentTowns.REGIONS.length; i++) {
                BlockPos t = jp.revati.skyabyss.world.continent.ContinentLayout.get().town(jp.revati.skyabyss.world.continent.ContinentTowns.REGIONS[i]);
                if (t != null) {
                    out.add(new Planned(jp.revati.skyabyss.world.continent.ContinentTowns.KIND, i, new BlockPos(t.getX(), p.top(t.getX(), t.getZ()) + 1, t.getZ()), 0));
                }
            }
            // 外れの一本道の休み場と祠
            List<BlockPos> prints = new ArrayList<>();
            for (Planned pl : out) {
                if (pl.kind().equals(Kind.FOOTPRINT.id)) {
                    prints.add(pl.pos());
                }
            }
            List<int[]> stops = jp.revati.skyabyss.world.continent.ContinentOutskirts.spots(prints);
            for (int i = 0; i < stops.size(); i++) {
                int[] q = stops.get(i);
                out.add(new Planned(jp.revati.skyabyss.world.continent.ContinentOutskirts.KIND, i, new BlockPos(q[0], p.top(q[0], q[1]) + 1, q[1]), 0));
            }
            // 内海の渡し船の両岸の船着き場 (向こう岸は向きを返す)
            List<List<BlockPos>> fd = jp.revati.skyabyss.world.continent.ContinentLayout.get().ferryDocks();
            for (int i = 0; i < fd.size(); i++) {
                for (int j = 0; j < 2 && j < fd.get(i).size(); j++) {
                    BlockPos q = fd.get(i).get(j);
                    out.add(new Planned(jp.revati.skyabyss.world.continent.ContinentFerries.KIND, i * 2 + j,
                            new BlockPos(q.getX(), p.top(q.getX(), q.getZ()) + 1, q.getZ()), encode(j == 0 ? 0 : 2, false)));
                }
            }
            // 船着き場 (島の道の終わり。ここから鐘の都へ渡る)
            BlockPos dock = jp.revati.skyabyss.world.continent.ContinentLayout.get().dock();
            out.add(new Planned(jp.revati.skyabyss.world.continent.ContinentOutskirts.KIND, jp.revati.skyabyss.world.continent.ContinentOutskirts.DOCK,
                    new BlockPos(dock.getX(), p.top(dock.getX(), dock.getZ()) + 1, dock.getZ()), 0));
        }
        SkyAbyss.LOGGER.info("Opening sites planned in {} ms ({} height queries, {} ms): camp {} shelter {} ribs {} ({} entries)", (System.nanoTime() - t0) / 1_000_000L,
                p.queries, p.nanos / 1_000_000L, camp, shelter == null ? "-" : shelter.pos(), ribs == null ? "-" : ribs.pos(), out.size());
        return new Rest(out, grounds);
    }

    // 一章の跡地の中の骨 (中心から 0〜96)。高い所・平らな所を選ぶ。段階は RIB_PASSES と同じ。見つからなければ null
    @Nullable
    static Spot findRibsNear(Ground p, BlockPos center) {
        for (int pass = 0; pass < RIB_PASSES.length; pass++) {
            Spot best = null;
            double bestScore = -1e9;
            for (int r = 0; r <= 96; r += 16) {
                int n = r == 0 ? 1 : Math.max(6, (int) (Math.PI * 2 * r / 24));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    int x = center.getX() + (int) Math.round(Math.cos(a) * r);
                    int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
                    for (int rot = 0; rot <= 1; rot++) {
                        int hx = (rot == 1 ? RIB_HALF_SHORT : RIB_HALF_LONG) * RIB_PASSES[pass][1] / 100;
                        int hz = (rot == 1 ? RIB_HALF_LONG : RIB_HALF_SHORT) * RIB_PASSES[pass][1] / 100;
                        Area ar = area(p, x, z, hx, hz, RIB_PASSES[pass][0], false, RIB_PASSES[pass][2] == 1);
                        if (!ar.ok()) {
                            continue;
                        }
                        double score = ar.median() - p.sea() - ar.range() * 0.7 - r * 0.08;
                        if (score > bestScore) {
                            bestScore = score;
                            best = new Spot(new BlockPos(x, ar.median() + 1, z), rot, false);
                        }
                    }
                }
            }
            if (best != null) {
                if (pass > 0) {
                    SkyAbyss.LOGGER.info("Opening sites: the rib bones on the north hills need a height range of up to {} (dry core {}%)", RIB_PASSES[pass][0],
                            RIB_PASSES[pass][1]);
                }
                return best;
            }
        }
        return null;
    }

    public static int encode(int rot, boolean mirror) {
        return (rot & 3) | (mirror ? 4 : 0);
    }

    public static int rot(int yaw) {
        return yaw & 3;
    }

    public static boolean mirror(int yaw) {
        return (yaw & 4) != 0;
    }

    // ---------------------------------------------------------------- 野営地: スポーン地点。だめなら近い陸 (半径 160 まで)
    @Nullable
    static BlockPos findCamp(Probe p, BlockPos spawn) {
        // 外れの谷の、図に描いたはじまりの場所のそば
        spawn = jp.revati.skyabyss.world.continent.ContinentLayout.get().start();
        for (int range : new int[] {3, 6, 12}) {
            for (int r = 0; r <= 160; r += (r == 0 ? 6 : 6)) {
                int n = r == 0 ? 1 : Math.max(8, (int) (Math.PI * 2 * r / 7));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    int x = spawn.getX() + (int) Math.round(Math.cos(a) * r);
                    int z = spawn.getZ() + (int) Math.round(Math.sin(a) * r);
                    Area ar = area(p, x, z, 7, 7, range, true);
                    if (ar.ok()) {
                        return new BlockPos(x, ar.median() + 1, z);
                    }
                }
            }
        }
        // 近くに陸がない (島・海のスポーン): 半径 480 まで、小さな範囲・ゆるい高さの差で探す (必ず見つける)。新しいワールドならスポーン地点を動かす
        for (int[] pass : new int[][] {{5, 16}, {4, 28}, {3, 60}}) {
            for (int r = 168; r <= 480; r += 28) {
                int n = Math.max(8, (int) (Math.PI * 2 * r / 24));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    int x = spawn.getX() + (int) Math.round(Math.cos(a) * r);
                    int z = spawn.getZ() + (int) Math.round(Math.sin(a) * r);
                    Area ar = area(p, x, z, pass[0], pass[0], pass[1], true);
                    if (ar.ok()) {
                        SkyAbyss.LOGGER.info("Opening sites: the camp is {} blocks from the spawn (no land nearer)", r);
                        return new BlockPos(x, ar.median() + 1, z);
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 宿り: 鐘の都 (図の場所)。庭の入口 (南向き) を野営地の方へ向ける
    static Spot findShelter(Probe p, BlockPos camp) {
        BlockPos c = jp.revati.skyabyss.world.continent.ContinentLayout.get().capital();
        return new Spot(new BlockPos(c.getX(), p.top(c.getX(), c.getZ()) + 1, c.getZ()), rotToward(camp.getX() - c.getX(), camp.getZ() - c.getZ(), true), false);
    }

    // dx, dz の向きを向く回転 (部品は「南向き」(toSouthFacing = true) か「北向き」(false) が基準)。0..3
    static int rotToward(double dx, double dz, boolean southBase) {
        // 部品の基準の向きから時計回りに: 南 → 西 → 北 → 東 / 北 → 東 → 南 → 西
        int dirIndex; // 0 = 南、1 = 西、2 = 北、3 = 東
        if (Math.abs(dx) >= Math.abs(dz)) {
            dirIndex = dx < 0 ? 1 : 3;
        } else {
            dirIndex = dz > 0 ? 0 : 2;
        }
        return southBase ? dirIndex : (dirIndex + 2) & 3;
    }

    // ---------------------------------------------------------------- 足跡: 島の道の脇の決まった所 (図)。左右の足が交互
    // 都の外で旅の跡を見張る範囲を広げるか (SKYABYSS_SMALL_SHELTER で小さな宿りの試し)
    public static boolean capital() {
        return System.getenv("SKYABYSS_SMALL_SHELTER") == null;
    }

    static void planFixedPrints(Probe p, List<Planned> out, List<BlockPos> at) {
        BlockPos a = at.get(0);
        BlockPos b = at.get(at.size() - 1);
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        int[] dir = Math.abs(dx) >= Math.abs(dz) ? new int[] {Integer.signum(dx), 0} : new int[] {0, Integer.signum(dz)};
        List<BlockPos> prints = new ArrayList<>();
        for (BlockPos q : at) {
            prints.add(new BlockPos(q.getX(), p.top(q.getX(), q.getZ()) + 1, q.getZ()));
        }
        addTrail(out, prints, dir);
    }

    private static void addTrail(List<Planned> out, List<BlockPos> prints, int[] dir) {
        int rot = rotToward(dir[0], dir[1], false);
        for (int i = 0; i < prints.size(); i++) {
            out.add(new Planned(Kind.FOOTPRINT.id, i, prints.get(i), encode(rot, i % 2 == 1)));
        }
    }

    // ---------------------------------------------------------------- 骨: 一章の跡地の中 (findRibsNear)。骨は大きい (長さ 100・幅 47)。向きは 東西 か 南北
    static final int RIB_HALF_LONG = 52;
    static final int RIB_HALF_SHORT = 25;
    // 段階: {高さの差の上限, 乾いていてほしい範囲 (骨の大きさに対する %。池は水の列を残して建てる)、群系を見るか}
    static final int[][] RIB_PASSES = {{18, 100, 1}, {30, 100, 1}, {46, 100, 1}, {70, 100, 1}, {120, 100, 1}, {80, 50, 1}, {200, 30, 0}, {1000, 8, 0}};
}
