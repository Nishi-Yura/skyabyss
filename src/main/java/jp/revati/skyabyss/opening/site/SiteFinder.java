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
//       足跡 = 道から 30〜60 ブロック外れた所に 3 つ、骨 = 野営地から 250〜350 の丘 (宿りと違う方角)
public final class SiteFinder {
    // 一つの場所の候補 (中心の高さは地面の上面 + 1)
    public record Spot(BlockPos pos, int rot, boolean mirror) {
    }

    // 「地形の高さ」を問い合わせる道具 (チャンクを作らない)
    public static final class Probe {
        private final ServerLevel level;
        private final ChunkGenerator gen;
        private final RandomState random;
        private final Climate.Sampler sampler;
        public final int seaLevel;
        private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();

        public Probe(ServerLevel level) {
            this.level = level;
            this.gen = level.getChunkSource().getGenerator();
            this.random = level.getChunkSource().randomState();
            this.sampler = random.sampler();
            this.seaLevel = gen.getSeaLevel();
            tops.defaultReturnValue(Integer.MIN_VALUE);
        }

        // 地面の一番上のブロックの高さ (海底を含む。木は数えない)
        public int top(int x, int z) {
            long key = BlockPos.asLong(x, 0, z);
            int v = tops.get(key);
            if (v == Integer.MIN_VALUE) {
                v = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
                tops.put(key, v);
            }
            return v;
        }

        // 水の下か (地面が海面より低い)。地形生成の高さの問い合わせは 1 回 数十 ms かかるので、1 地点 1 回だけにしている
        public boolean wet(int x, int z) {
            return top(x, z) < seaLevel;
        }

        public Holder<Biome> biome(int x, int z) {
            return gen.getBiomeSource().getNoiseBiome(x >> 2, top(x, z) >> 2, z >> 2, sampler);
        }

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
    public static Area area(Probe p, int cx, int cz, int halfX, int halfZ, int maxRange) {
        return area(p, cx, cz, halfX, halfZ, maxRange, false);
    }

    // fine = true なら 5x5 (野営地のように、一度しか探さない所向け)
    public static Area area(Probe p, int cx, int cz, int halfX, int halfZ, int maxRange, boolean fine) {
        List<Integer> hs = new ArrayList<>();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        // 3x3 (広い範囲は横に 5) の格子。四隅と辺の中央と中心
        int nx = fine ? 5 : halfX >= 16 ? 5 : 3;
        int nz = fine ? 5 : 3;
        for (int i = 0; i < nx; i++) {
            int xx = -halfX + (2 * halfX * i) / (nx - 1);
            for (int j = 0; j < nz; j++) {
                int zz = -halfZ + (2 * halfZ * j) / (nz - 1);
                int wx = cx + xx;
                int wz = cz + zz;
                if (p.wet(wx, wz)) {
                    return new Area(false, 0, 0, 0, 0);
                }
                int t = p.top(wx, wz);
                hs.add(t);
                min = Math.min(min, t);
                max = Math.max(max, t);
                if (max - min > maxRange) {
                    return new Area(false, min, max, 0, hs.size());
                }
            }
        }
        if (!p.biomeOk(cx, cz) || !p.biomeOk(cx - halfX, cz - halfZ) || !p.biomeOk(cx + halfX, cz + halfZ)) {
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
        }
        Spot ribs = findRibs(p, camp, shelter == null ? 0 : pathYaw);
        if (ribs != null) {
            out.add(new Planned(Kind.RIBS.id, 0, ribs.pos(), encode(ribs.rot(), ribs.mirror())));
        } else {
            SkyAbyss.LOGGER.error("Opening sites: no hill for the rib bones near {}", camp);
        }
        SkyAbyss.LOGGER.info("Opening sites planned in {} ms: camp {} shelter {} ribs {} ({} entries)", (System.nanoTime() - t0) / 1_000_000L, camp,
                shelter == null ? "-" : shelter.pos(), ribs == null ? "-" : ribs.pos(), out.size());
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
        return null;
    }

    // ---------------------------------------------------------------- 宿り: 野営地から 140〜220 (東を優先)。庭の入口 (南向き) を野営地の方へ向ける
    @Nullable
    static Spot findShelter(Probe p, BlockPos camp) {
        int[][] passes = {{140, 220, 4}, {140, 220, 7}, {100, 320, 8}};
        for (int[] pass : passes) {
            List<Integer> dists = new ArrayList<>();
            for (int d = pass[0]; d <= pass[1]; d += 10) {
                dists.add(d);
            }
            dists.sort(Comparator.comparingInt(d -> Math.abs(d - 180)));
            for (int ai = 0; ai < 24; ai++) {
                // 東から 0, +15, -15, +30, -30, ... 度
                int deg = ((ai + 1) / 2) * 15 * (ai % 2 == 1 ? 1 : -1);
                double a = Math.toRadians(deg);
                for (int d : dists) {
                    int x = camp.getX() + (int) Math.round(Math.cos(a) * d);
                    int z = camp.getZ() + (int) Math.round(Math.sin(a) * d);
                    Area ar = area(p, x, z, 11, 11, pass[2]);
                    if (ar.ok()) {
                        BlockPos c = new BlockPos(x, ar.median() + 1, z);
                        return new Spot(c, rotToward(camp.getX() - x, camp.getZ() - z, true), false);
                    }
                }
            }
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
            for (int shift = 0; shift <= 12; shift += 4) {
                for (int s = 0; s < (shift == 0 ? 1 : 2); s++) {
                    double along = 40 * k + (s == 0 ? shift : -shift);
                    int x = camp.getX() + (int) Math.round(ux * along - uz * 4 * side);
                    int z = camp.getZ() + (int) Math.round(uz * along + ux * 4 * side);
                    Area ar = area(p, x, z, 1, 1, 3);
                    if (ar.ok()) {
                        out.add(new Planned("cairn", index++, new BlockPos(x, ar.median() + 1, z), 0));
                        shift = 99;
                        break;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 足跡: 道から 30〜60 ブロック外れた所に、40 ブロックおきに 3 つ (進む向きは道に対して斜め)
    static void planFootprints(Probe p, List<Planned> out, BlockPos camp, BlockPos shelter) {
        double dx = shelter.getX() - camp.getX();
        double dz = shelter.getZ() - camp.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len;
        double uz = dz / len;
        int[][] passes = {{4}, {7}, {11}, {16}};
        for (int pi = 0; pi < passes.length; pi++) {
            int[] pass = passes[pi];
            for (double frac : new double[] {0.35, 0.25, 0.45, 0.55, 0.15, 0.65}) {
                for (int off : new int[] {40, 35, 45, 30, 50, 55, 60}) {
                    for (int side : new int[] {1, -1}) {
                        double px = camp.getX() + ux * len * frac - uz * off * side;
                        double pz = camp.getZ() + uz * len * frac + ux * off * side;
                        // 進む向き: 道とほぼ平行 (少し斜め)。3 つとも道から 30〜60 ブロックの帯に収める
                        for (int turn : new int[] {20, 0, -20}) {
                            double ang = Math.atan2(uz, ux) + Math.toRadians(turn * side);
                            double mx = Math.cos(ang);
                            double mz = Math.sin(ang);
                            List<BlockPos> prints = new ArrayList<>();
                            for (int i = 0; i < 3; i++) {
                                int x = (int) Math.round(px + mx * 40 * (i - 1));
                                int z = (int) Math.round(pz + mz * 40 * (i - 1));
                                Area ar = area(p, x, z, 6, 7, pass[0]);
                                double dp = distToPath(x, z, camp, ux, uz, len);
                                if (!ar.ok() || dp < (pi >= 3 ? 20 : 28) || dp > (pi >= 3 ? 90 : 62)) {
                                    break;
                                }
                                prints.add(new BlockPos(x, ar.median() + 1, z));
                            }
                            if (prints.size() == 3) {
                                int rot = rotToward(mx, mz, false);
                                prints.sort(Comparator.comparingDouble(b -> b.distSqr(camp)));
                                for (int i = 0; i < 3; i++) {
                                    out.add(new Planned(Kind.FOOTPRINT.id, i, prints.get(i), encode(rot, i % 2 == 1)));
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
        // どうしても道の脇に見つからない: 野営地のまわりの輪のどこでもよいので、乾いた所に 3 つ並べる
        for (int r = 50; r <= 150; r += 16) {
            for (int deg = 0; deg < 360; deg += 20) {
                double a = Math.toRadians(deg);
                double px = camp.getX() + Math.cos(a) * r;
                double pz = camp.getZ() + Math.sin(a) * r;
                for (int turn : new int[] {90, 0}) {
                    double ang = a + Math.toRadians(turn);
                    double mx = Math.cos(ang);
                    double mz = Math.sin(ang);
                    List<BlockPos> prints = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        int x = (int) Math.round(px + mx * 40 * (i - 1));
                        int z = (int) Math.round(pz + mz * 40 * (i - 1));
                        Area ar = area(p, x, z, 6, 7, 24);
                        if (!ar.ok()) {
                            break;
                        }
                        prints.add(new BlockPos(x, ar.median() + 1, z));
                    }
                    if (prints.size() == 3) {
                        int rot = rotToward(mx, mz, false);
                        prints.sort(Comparator.comparingDouble(b -> b.distSqr(camp)));
                        for (int i = 0; i < 3; i++) {
                            out.add(new Planned(Kind.FOOTPRINT.id, i, prints.get(i), encode(rot, i % 2 == 1)));
                        }
                        SkyAbyss.LOGGER.info("Opening sites: footprints placed away from the road (no good land beside it)");
                        return;
                    }
                }
            }
        }
        SkyAbyss.LOGGER.error("Opening sites: no place for the three footprints");
    }

    static double distToPath(int x, int z, BlockPos camp, double ux, double uz, double len) {
        double rx = x - camp.getX();
        double rz = z - camp.getZ();
        double along = Math.max(0, Math.min(len, rx * ux + rz * uz));
        double cx = ux * along;
        double cz = uz * along;
        return Math.sqrt((rx - cx) * (rx - cx) + (rz - cz) * (rz - cz));
    }

    // ---------------------------------------------------------------- 骨: 野営地から 250〜350 の丘。宿りと違う方角 (宿りの向きから 90 度以上)
    @Nullable
    static Spot findRibs(Probe p, BlockPos camp, double shelterAngle) {
        Spot best = null;
        double bestScore = -1e9;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (int deg = 90; deg <= 270; deg += 15) {
                double a = shelterAngle + Math.toRadians(deg);
                for (int d : new int[] {300, 270, 330, 285, 315, 255, 345}) {
                    int x = camp.getX() + (int) Math.round(Math.cos(a) * d);
                    int z = camp.getZ() + (int) Math.round(Math.sin(a) * d);
                    Area ar = area(p, x, z, 24, 14, pass == 0 ? 14 : 24);
                    if (!ar.ok()) {
                        continue;
                    }
                    double score = ar.median() - p.seaLevel - ar.range() * 0.7 - Math.abs(d - 300) * 0.05;
                    if (pass == 0 && ar.median() < p.seaLevel + 6) {
                        continue;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        int rot = Math.abs(x - camp.getX()) > Math.abs(z - camp.getZ()) ? 1 : 0;
                        best = new Spot(new BlockPos(x, ar.median() + 1, z), rot, false);
                    }
                }
            }
        }
        return best;
    }
}
