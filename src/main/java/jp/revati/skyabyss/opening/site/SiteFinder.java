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

// 序盤の構造物の場所探し (docs/design/17 の 4 章)。チャンクを作らずに、地形生成の高さと群系だけで決める
// (ワールドを始める瞬間に重くならない。木や人の建物は、建てる直前に実際のブロックを見て確かめる: SiteJob)。
// 陸の判定: 水面と海底の高さが同じ (水がない)、群系が海・川・沼・ジャングル・暗い森・きのこ島でない、
// 範囲の高さの差が小さい (険しい斜面を避ける)。
// 配置: 野営地 = スポーン地点 (だめなら近い陸)、宿り = 野営地から 140〜220 (東を優先)、積み石 = 道に 40 ブロックおき、
//       足跡 = 道から 30〜60 ブロック外れた所に、一方向へ歩いた 4 歩 (96 ブロックおき、左右の足が交互。大きな足跡 = 長さ 50・幅 45)、
//       骨 = 野営地から 250〜350 の丘 (宿りと違う方角。長さ 100・幅 47 の大きな骨)
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

    // 野営地以外 (宿り・積み石・足跡・骨)。重い (地形生成の高さを何百回も問い合わせる) ので、別のスレッドで呼ぶ
    public static List<Planned> planRest(Probe p, BlockPos camp) {
        long t0 = System.nanoTime();
        List<Planned> out = new ArrayList<>();
        Spot shelter = findShelter(p, camp);
        double pathYaw = 0;
        if (shelter != null) {
            out.add(new Planned(Kind.SHELTER.id, 0, shelter.pos(), encode(shelter.rot(), shelter.mirror())));
            pathYaw = Math.atan2(shelter.pos().getZ() - camp.getZ(), shelter.pos().getX() - camp.getX());
            planCairns(p, out, camp, shelter.pos());
            planFootprints(p, out, camp, shelter.pos());
        } else {
            SkyAbyss.LOGGER.error("Opening sites: no land for the shelter near {}", camp);
            // 宿りがなくても足跡は要る (足跡の頁の目印): 野営地から東 180 ブロックの仮の宿りを道の向きに使う (first_hour_log B-6)
            planFootprints(p, out, camp, camp.offset(180, 0, 0));
        }
        Spot ribs = findRibs(p, camp, shelter == null ? 0 : pathYaw);
        if (ribs != null) {
            out.add(new Planned(Kind.RIBS.id, 0, ribs.pos(), encode(ribs.rot(), ribs.mirror())));
        } else {
            SkyAbyss.LOGGER.error("Opening sites: no hill for the rib bones near {}", camp);
        }
        SkyAbyss.LOGGER.info("Opening sites planned in {} ms ({} height queries, {} ms): camp {} shelter {} ribs {} ({} entries)", (System.nanoTime() - t0) / 1_000_000L,
                p.queries, p.nanos / 1_000_000L, camp, shelter == null ? "-" : shelter.pos(), ribs == null ? "-" : ribs.pos(), out.size());
        return out;
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

    // ---------------------------------------------------------------- 宿り: 野営地から 140〜220 (東を優先)。庭の入口 (南向き) を野営地の方へ向ける
    @Nullable
    static Spot findShelter(Probe p, BlockPos camp) {
        // 都 (docs/design/33 の 2): 一辺 200 の範囲。野営地から 260〜340 (壁の縁が野営地から 150 以上)。段差は整地で吸収するので広めに許す
        int[][] passes = {{260, 340, 20}, {260, 340, 32}, {240, 420, 48}, {240, 480, 80}};
        for (int[] pass : passes) {
            List<Integer> dists = new ArrayList<>();
            for (int d = pass[0]; d <= pass[1]; d += 10) {
                dists.add(d);
            }
            dists.sort(Comparator.comparingInt(d -> Math.abs(d - 300)));
            for (int ai = 0; ai < 24; ai++) {
                // 東から 0, +15, -15, +30, -30, ... 度
                int deg = ((ai + 1) / 2) * 15 * (ai % 2 == 1 ? 1 : -1);
                double a = Math.toRadians(deg);
                for (int d : dists) {
                    int x = camp.getX() + (int) Math.round(Math.cos(a) * d);
                    int z = camp.getZ() + (int) Math.round(Math.sin(a) * d);
                    Area ar = area(p, x, z, 100, 100, pass[2], false, pass[2] < 48);
                    if (ar.ok()) {
                        BlockPos c = new BlockPos(x, ar.median() + 1, z);
                        return new Spot(c, rotToward(camp.getX() - x, camp.getZ() - z, true), false);
                    }
                }
            }
        }
        // 最後の手段: 都は必ず建てる。群系を問わず、水は 1/4 まで許し、段差のいちばん小さい所を選ぶ (整地で吸収する)
        WET_FRACTION = 4;
        try {
            Area best = null;
            int bx = 0;
            int bz = 0;
            for (int ai = 0; ai < 24; ai++) {
                int deg = ((ai + 1) / 2) * 15 * (ai % 2 == 1 ? 1 : -1);
                double a = Math.toRadians(deg);
                for (int d = 260; d <= 420; d += 40) {
                    int x = camp.getX() + (int) Math.round(Math.cos(a) * d);
                    int z = camp.getZ() + (int) Math.round(Math.sin(a) * d);
                    Area ar = area(p, x, z, 100, 100, 512, false, false);
                    if (ar.ok() && (best == null || ar.max() - ar.min() < best.max() - best.min())) {
                        best = ar;
                        bx = x;
                        bz = z;
                    }
                }
            }
            if (best != null) {
                SkyAbyss.LOGGER.warn("Opening sites: the capital takes rough ground near {} {} (height range {})", bx, bz, best.max() - best.min());
                return new Spot(new BlockPos(bx, best.median() + 1, bz), rotToward(camp.getX() - bx, camp.getZ() - bz, true), false);
            }
        } finally {
            WET_FRACTION = 10;
        }
        return null;
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

    // ---------------------------------------------------------------- 積み石: 道に 40 ブロックおき (道の脇 3〜4 ブロック)
    static void planCairns(Probe p, List<Planned> out, BlockPos camp, BlockPos target) {
        double dx = target.getX() - camp.getX();
        double dz = target.getZ() - camp.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len;
        double uz = dz / len;
        int index = 0;
        for (int k = 1; 40 * k <= len - 24; k++) {
            int side = (k % 2 == 0) ? 1 : -1;
            for (int shift = 0; shift <= 24; shift += 4) {
                for (int s = 0; s < (shift == 0 ? 1 : 2); s++) {
                    double along = 40 * k + (s == 0 ? shift : -shift);
                    int x = camp.getX() + (int) Math.round(ux * along - uz * 4 * side);
                    int z = camp.getZ() + (int) Math.round(uz * along + ux * 4 * side);
                    Area ar = area(p, x, z, 1, 1, 3);
                    if (!ar.ok()) {
                        ar = area(p, x, z, 1, 1, 7);
                    }
                    if (ar.ok()) {
                        out.add(new Planned("cairn", index++, new BlockPos(x, ar.median() + 1, z), 0));
                        shift = 999;
                        break;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 足跡: 道から 30〜60 ブロック外れた所に、道と同じ向きへ歩いた 4 歩
    // 足跡は大きい (長さ 50・幅 45)。歩く向きは東西南北のどれか (部品を 90 度ずつ回して置くため)。一歩は 96 ブロックおき、左右の足が道の左右に 14 ずつずれる
    // (地面が合わなければ、少し前後・左右にずらして探す)。野営地から宿りの側へ向かう向きだけを使う (index が大きいほど野営地から遠い。最後の足跡の先に、地へ潜った穴がある)。
    // 必ず建つように、条件 (高さの差・本数・道からの距離) を段階的にゆるめる。ただし道からの距離の上限は 150 (道・積み石・宿り・野営地に重ならない下限もある)
    static final int STRIDE = 96;
    static final int LATERAL = 14;
    static final int PRINTS = 4;
    // 足跡の部品の半分の大きさ (ブロック。向きを揃えた北向きで 横 x 前後)
    static final int FOOT_HALF_X = 27;
    static final int FOOT_HALF_Z = 33;
    // 足跡の中心から、道・宿り・野営地までの下限 (足跡の半分の大きさ + 余白)
    static final int FOOT_ROAD_MIN = 38;
    static final int FOOT_SITE_MIN = 62;
    // 段階: {高さの差の上限, 道からの下限, 上限, 本数, 向きを全部使うか, 乾いていてほしい範囲 (部品の大きさに対する %。小さい池は水の列を残して建てる)、群系を見るか}
    static final int[][] FOOT_STAGES = {{10, 38, 85, 4, 0, 100, 1}, {18, 38, 100, 4, 0, 100, 1}, {30, 38, 120, 4, 0, 100, 1}, {46, 36, 135, 4, 0, 100, 1},
            {70, 36, 140, 4, 0, 100, 1}, {30, 36, 120, 3, 0, 100, 1}, {70, 36, 140, 3, 0, 100, 1}, {60, 34, 140, 3, 1, 100, 1}, {60, 34, 150, 2, 1, 100, 1},
            {90, 32, 150, 1, 1, 100, 1}, {120, 32, 155, 3, 0, 40, 0}, {200, 30, 155, 2, 1, 40, 0}, {400, 30, 155, 1, 1, 15, 0},
            // 最後の手段 (first_hour_log B-6): 中心が乾いていれば、高さの差も群系も問わず 1 つ (建てるときに SiteJob が動かす・強行する)
            {2000, 30, 160, 1, 1, 0, 0}};

    static void planFootprints(Ground p, List<Planned> out, BlockPos camp, BlockPos shelter) {
        double dx = shelter.getX() - camp.getX();
        double dz = shelter.getZ() - camp.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len;
        double uz = dz / len;
        // 歩く向き: 道にいちばん近い東西南北 (道が斜めなら、その次に近い向きも)。最後の段階は全部
        int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<int[]> sorted = new ArrayList<>(List.of(cardinals));
        sorted.sort(Comparator.comparingDouble(c -> -(c[0] * ux + c[1] * uz)));
        List<int[]> along = new ArrayList<>();
        for (int[] c : sorted) {
            if (c[0] * ux + c[1] * uz > 0.2) {
                along.add(c);
            }
        }
        for (int[] st : FOOT_STAGES) {
            List<int[]> dirs = st[4] == 1 ? sorted : along;
            for (double frac : new double[] {0.1, 0.2, 0.0, 0.3, 0.4, 0.5, 0.6}) {
                for (int off : new int[] {46, 40, 54, 62, 72, 84}) {
                    for (int side : new int[] {1, -1}) {
                        for (int[] dir : dirs) {
                            double sx = camp.getX() + ux * len * frac - uz * off * side;
                            double sz = camp.getZ() + uz * len * frac + ux * off * side;
                            List<BlockPos> prints = trail(p, sx, sz, dir, st[0], st[3], camp, shelter, ux, uz, len, st[1], st[2], st[5], st[6] == 1);
                            if (prints != null) {
                                addTrail(out, prints, dir);
                                if (st[3] < PRINTS || st[0] > 30) {
                                    SkyAbyss.LOGGER.info("Opening sites: {} footprints (height range up to {}, road distance {}..{})", prints.size(), st[0], st[1], st[2]);
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
        SkyAbyss.LOGGER.error("Opening sites: no place for the footprints");
    }

    private static void addTrail(List<Planned> out, List<BlockPos> prints, int[] dir) {
        int rot = rotToward(dir[0], dir[1], false);
        for (int i = 0; i < prints.size(); i++) {
            out.add(new Planned(Kind.FOOTPRINT.id, i, prints.get(i), encode(rot, i % 2 == 1)));
        }
    }

    // start から dir へ、count 歩の並びを探す (一歩ごとに少し前後・左右へずらして、乾いた所を探す)。見つからなければ null
    @Nullable
    private static List<BlockPos> trail(Ground p, double sx, double sz, int[] dir, int maxRange, int count, BlockPos camp, BlockPos shelter, double ux,
            double uz, double len, int dpMin, int dpMax, int corePercent, boolean biome) {
        boolean ns = dir[0] == 0;
        int hx = (ns ? FOOT_HALF_X : FOOT_HALF_Z) * corePercent / 100;
        int hz = (ns ? FOOT_HALF_Z : FOOT_HALF_X) * corePercent / 100;
        // 進む向きの右手 (南向きなら西)
        int rx = -dir[1];
        int rz = dir[0];
        List<BlockPos> prints = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean found = false;
            int side = (i % 2 == 0) ? 1 : -1;
            for (int da : new int[] {0, 10, -10, 20, -20}) {
                for (int lat : new int[] {0, 10, -10, 20, -20, 32, -32}) {
                    int x = (int) Math.round(sx + dir[0] * (STRIDE * i + da) + rx * (LATERAL * side + lat));
                    int z = (int) Math.round(sz + dir[1] * (STRIDE * i + da) + rz * (LATERAL * side + lat));
                    double dp = distToPath(x, z, camp, ux, uz, len);
                    if (dp < dpMin || dp > dpMax) {
                        continue;
                    }
                    if (Math.hypot(x - camp.getX(), z - camp.getZ()) < FOOT_SITE_MIN || Math.hypot(x - shelter.getX(), z - shelter.getZ()) < FOOT_SITE_MIN) {
                        continue;
                    }
                    // 前の足跡と近すぎない (一歩の間隔の下限)
                    if (!prints.isEmpty() && Math.hypot(x - prints.get(prints.size() - 1).getX(), z - prints.get(prints.size() - 1).getZ()) < 70) {
                        continue;
                    }
                    Area ar = area(p, x, z, hx, hz, maxRange, false, biome);
                    if (ar.ok()) {
                        prints.add(new BlockPos(x, ar.median() + 1, z));
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            if (!found) {
                return null;
            }
        }
        return prints;
    }

    static double distToPath(int x, int z, BlockPos camp, double ux, double uz, double len) {
        double rx = x - camp.getX();
        double rz = z - camp.getZ();
        double along = Math.max(0, Math.min(len, rx * ux + rz * uz));
        double cx = ux * along;
        double cz = uz * along;
        return Math.sqrt((rx - cx) * (rx - cx) + (rz - cz) * (rz - cz));
    }

    // ---------------------------------------------------------------- 骨: 野営地から 250〜350 の丘。宿りと違う方角 (宿りの向きから 90 度以上離す)
    // 骨は大きい (長さ 100・幅 47)。向きは 東西 か 南北 (部品を 90 度回して置く)。範囲の高さの差が小さい所を選ぶ。
    // 必ず建つように、高さの差の上限を段階的にゆるめ (18 → 30 → 46 → 70 → 120)、最後は距離 200〜380・宿りと 80 度以上離れた乾いた所ならどこでもよい
    static final int RIB_HALF_LONG = 52;
    static final int RIB_HALF_SHORT = 25;
    // 段階: {高さの差の上限, 乾いていてほしい範囲 (骨の大きさに対する %。池は水の列を残して建てる)、群系を見るか}
    static final int[][] RIB_PASSES = {{18, 100, 1}, {30, 100, 1}, {46, 100, 1}, {70, 100, 1}, {120, 100, 1}, {80, 50, 1}, {200, 30, 0}, {1000, 8, 0}};

    @Nullable
    static Spot findRibs(Probe p, BlockPos camp, double shelterAngle) {
        for (int pass = 0; pass < RIB_PASSES.length; pass++) {
            Spot best = null;
            double bestScore = -1e9;
            boolean last = pass >= 5;
            int degFrom = last ? 80 : 90;
            for (int deg = degFrom; deg <= 360 - degFrom; deg += last ? 8 : 10) {
                double a = shelterAngle + Math.toRadians(deg);
                int[] dists = last ? new int[] {300, 260, 340, 280, 320, 240, 360, 226, 374, 232, 368} : new int[] {300, 270, 330, 285, 315, 255, 345, 240, 360};
                for (int d : dists) {
                    int x = camp.getX() + (int) Math.round(Math.cos(a) * d);
                    int z = camp.getZ() + (int) Math.round(Math.sin(a) * d);
                    int rot = Math.abs(x - camp.getX()) > Math.abs(z - camp.getZ()) ? 1 : 0;
                    int hx = (rot == 1 ? RIB_HALF_SHORT : RIB_HALF_LONG) * RIB_PASSES[pass][1] / 100;
                    int hz = (rot == 1 ? RIB_HALF_LONG : RIB_HALF_SHORT) * RIB_PASSES[pass][1] / 100;
                    Area ar = area(p, x, z, hx, hz, RIB_PASSES[pass][0], false, RIB_PASSES[pass][2] == 1);
                    if (!ar.ok()) {
                        continue;
                    }
                    if (pass == 0 && ar.median() < p.seaLevel + 6) {
                        continue;
                    }
                    double score = ar.median() - p.seaLevel - ar.range() * 0.7 - Math.abs(d - 300) * 0.05;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Spot(new BlockPos(x, ar.median() + 1, z), rot, false);
                    }
                }
            }
            if (best != null) {
                if (pass > 0) {
                    SkyAbyss.LOGGER.info("Opening sites: the rib bones need a height range of up to {} (dry core {}%)", RIB_PASSES[pass][0], RIB_PASSES[pass][1]);
                }
                return best;
            }
        }
        return null;
    }
}
