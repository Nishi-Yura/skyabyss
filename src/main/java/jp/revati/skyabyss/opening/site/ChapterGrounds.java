package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites.Kind;
import jp.revati.skyabyss.opening.site.SiteFinder.Area;
import jp.revati.skyabyss.opening.site.SiteFinder.Ground;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 章の跡地 (docs/design/33 の 3 章。W2)。はじまりの都から決まった方位と距離の帯に、章ごとに世界で一つ。
//   一章 岩鯨 = 北 300〜600 の丘、二章 潮殻 = 東 800〜1,400 の海辺、三章 雷翼 = 西 1,000〜1,800 の峰 (y 150 以上)、四章 余燼 = 南 1,200〜2,000 の乾いた低地。
//   探し方: 方位の扇 (±25 度) の中で、帯の内側から外側へ 32 ブロック刻み (扇は 5 度刻み、まんなかから)。合う地形があればそこ。
//     バニラの村・構造物の出るチャンクが 192 以内なら、その候補を飛ばす (= 少しずらす。小要素の広がりのぶん 128 より広め。道しるべの点は 48)。一章は野営地・足跡からも離す。
//     無ければ帯のまんなかのそばの、いちばん乾いて平らな所に「整える形」を作る (丘 96・入り江 160・峰 128 で +60 まで・灰の盆地 160。ShapeJob)。
//   時機: 四つとも、都が決まったときに場所を決める (地形生成の高さだけを見る。別のスレッド)。一章の形は目覚めの前に、二章以降の形は章が開いたとき
//     (その章の人が出たとき = 前の章の心臓を止めたとき。ChapterSites が prepare を呼ぶ) に建てる。
//   中身: 章の小要素のうち「跡地に置く」もの (ChapterSites.Builder.atGround) は、跡地の中心を拠点にして世界で一つ置く。
//     「道に置く」もの (atRoad) と道しるべの石標は、都から跡地への道 (40 ブロックごと。水の上・崖は飛ばす) に置く。
//     一章の巨神の骨 (序盤の RIBS) も一章の跡地の中に置く。
//   祠 (32 の 3.1): 跡地の中心のそばの平らな所を、場所を決めたときに取っておく。**祠を建てる側はここだけを見る: shrineSpot(server, titan)**。
//   潮殻: 干潮の刻と潮見の貝で寄るのは二章の跡地の浜だけ (restSpot。掘った入り江なら決まった場所と向き)。
// 読む窓口は center / shrineSpot / restSpot / road / ready。保存は SiteData の grounds。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterGrounds {
    public enum Shape {
        NONE, HILL, COVE, PEAK, BASIN
    }

    // 章ごとの決まり (33 の 3 章の表)。dir = 都から見た向き (x, z。北 = -z)、min〜max = 距離の帯、side = 整える形の一辺
    public record Spec(Chapters chapter, int dirX, int dirZ, int min, int max, Shape shape, int side) {
        double angle() {
            return Math.atan2(dirZ, dirX);
        }
    }

    public static final List<Spec> SPECS = List.of(
            new Spec(Chapters.WHALE, 0, -1, 300, 600, Shape.HILL, 96),
            new Spec(Chapters.TIDE, 1, 0, 800, 1400, Shape.COVE, 160),
            new Spec(Chapters.STORM, -1, 0, 1000, 1800, Shape.PEAK, 128),
            new Spec(Chapters.EMBER, 0, 1, 1200, 2000, Shape.BASIN, 160));

    // 扇の片側の開き (度) と刻み、距離の刻み
    public static final int SECTOR = 25;
    static final int ANGLE_STEP = 5;
    static final int DIST_STEP = 32;
    // バニラの構造物を避ける距離 (跡地の中心から。小要素は中心から 150 まで広がるので、33 の「128 以内」より広めに)、道しるべの点から
    static final int STRUCTURE_GAP = 192;
    static final int ROAD_STRUCTURE_GAP = 48;
    // 一章の跡地と野営地・足跡の間 (跡地の小要素は中心から 150 まで広がる)
    static final int CAMP_GAP = 190;
    static final int PRINT_GAP = 150;
    // 合う地形: 丘 = 海面 + 14 以上で、半径 40 の輪より平均 6 以上高い。峰 = y 150 以上。
    // 海辺 = 低い陸 (海面 + 10 まで) で、40 以内に水、その向きの 110 先に巨神の体が入る深さ 8 以上の海。灰の地 = 乾いた群系の低地 (海面 + 1〜40)
    static final int HILL_ABOVE_SEA = 14;
    static final int HILL_PROMINENCE = 6;
    static final int HILL_RING = 40;
    public static final int PEAK_Y = 150;
    static final int COAST_LOW = 10;
    static final int SHORE_REACH = 40;
    static final int BODY_OUT = 110;
    static final int BODY_HALF = 50;
    static final int BODY_DEPTH = 8;
    static final int BASIN_HIGH = 40;
    // 整える形の大きさ
    public static final int HILL_HALF = 48;
    public static final int HILL_RISE = 24;
    public static final int PEAK_HALF = 64;
    public static final int PEAK_RISE = 60;
    public static final int BASIN_HALF = 80;
    public static final int BASIN_DEPTH = 8;
    public static final int COVE_HALF = 80;
    // 入り江: 水 (深い所 74・岸の坂 78)・浜 80。浜の中心 (跡地の中心) は入り江の中心から 90 (都の側)
    public static final int COVE_DEEP = 74;
    public static final int COVE_WATER = 78;
    public static final int COVE_FLOOR = 15;
    public static final int COVE_BEACH = 90;
    // 道しるべ: 都の中心から 150 で始め、40 ごと。跡地の中心の手前 56 で止める。一章は石標 7 つが入るよう間を詰める (12 まで)
    public static final int ROAD_START = 150;
    public static final int ROAD_STEP = 40;
    public static final int ROAD_END_GAP = 56;
    public static final int WHALE_CAIRNS = 7;

    // 決めた跡地 (まだ SiteData には書かない。別のスレッドで探すため)
    public record Found(Chapters chapter, BlockPos center, Shape shape, BlockPos shapeCenter, int baseY, @Nullable BlockPos sea, float restYaw,
            boolean fixedRest, @Nullable BlockPos shrine, List<BlockPos> road, int dist, int deg) {
    }

    public record Obstacle(BlockPos pos, int radius) {
    }

    private ChapterGrounds() {
    }

    public static Spec spec(Chapters chapter) {
        for (Spec s : SPECS) {
            if (s.chapter() == chapter) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public static Chapters chapterOfTitan(String titan) {
        for (Spec s : SPECS) {
            if (s.chapter().titan().equals(titan)) {
                return s.chapter();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 道しるべの石標 (章ごとの道の残りの点。鯨道の石標と同じ部品)
    public static final String ROAD_PREFIX = "w2_road_";
    public static final String ROAD_BLUEPRINT = "ch1_road_cairn";
    // 跡地を「見た」とみなす距離 (地図の印)
    public static final int SEEN_NEAR = 96;
    private static boolean roadsRegistered;

    public static String roadId(Chapters chapter) {
        return ROAD_PREFIX + chapter.id();
    }

    // 道しるべの石標を章ごとに登録する (一度だけ。サーバーが始まるときに SiteBuilds が呼ぶ)
    public static synchronized void registerRoads() {
        if (roadsRegistered) {
            return;
        }
        roadsRegistered = true;
        for (Spec s : SPECS) {
            ChapterSites.element(roadId(s.chapter()), s.chapter(), ROAD_BLUEPRINT).fillRoad().arriveRadius(6).onArrive((p, site) -> {
                // 着いた記録だけ (地図の道しるべの点)
            }).register();
        }
    }

    // 人が跡地の近くにいれば「見た」に (ChapterSites の見張りから 1 秒おき)
    static void markSeen(SiteData data, java.util.UUID player, BlockPos pos) {
        for (SiteData.Ground g : data.grounds()) {
            if (!g.seen.contains(player) && ChapterSites.horizontal(g.center, pos) <= SEEN_NEAR) {
                g.seen.add(player);
                data.setDirty();
            }
        }
    }

    // ---------------------------------------------------------------- 読む
    @Nullable
    public static SiteData.Ground ground(MinecraftServer server, Chapters chapter) {
        return SiteData.get(server.overworld()).ground(chapter.id());
    }

    // 跡地の中心 (立つ場所)。まだ決まっていなければ空
    public static Optional<BlockPos> center(MinecraftServer server, Chapters chapter) {
        SiteData.Ground g = ground(server, chapter);
        return g == null ? Optional.empty() : Optional.of(g.center);
    }

    // **祠の置き場** (32 の 3.1。台座 9×9 の中心の立つ場所)。titan は Titans の名前 (stone_whale など)。
    // 跡地が決まっていれば、跡地の中心のそばの平らな所 (決めたときに取っておいた所。小要素はここを避ける)。決まっていなければ空
    public static Optional<BlockPos> shrineSpot(MinecraftServer server, String titan) {
        Chapters ch = chapterOfTitan(titan);
        if (ch == null) {
            return Optional.empty();
        }
        SiteData.Ground g = ground(server, ch);
        return g == null ? Optional.empty() : Optional.ofNullable(g.shrine != null ? g.shrine : g.center);
    }

    // 跡地が使えるか (決まっていて、整える形があるなら整え終わった)
    public static boolean ready(MinecraftServer server, Chapters chapter) {
        SiteData.Ground g = ground(server, chapter);
        return g != null && g.shaped;
    }

    // 都から跡地への道しるべの点 (都に近い順)
    public static List<BlockPos> road(MinecraftServer server, Chapters chapter) {
        SiteData.Ground g = ground(server, chapter);
        return g == null ? List.of() : List.copyOf(g.road);
    }

    // pos がどれかの跡地の中心から r 以内なら、その跡地
    @Nullable
    public static SiteData.Ground groundAt(SiteData data, BlockPos pos, double r) {
        for (SiteData.Ground g : data.grounds()) {
            if (ChapterSites.horizontal(g.center, pos) <= r) {
                return g;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 探す (純粋。地形は Ground の問い合わせだけ)
    // 都 capital から、四つの跡地を決める。avoid = 避ける場所 (野営地・足跡)。ribsOut に一章の骨の場所を返す (一章の跡地の中)
    public static List<Found> planAll(Ground g, BlockPos capital, List<Obstacle> avoid) {
        long t0 = System.nanoTime();
        List<Found> out = new ArrayList<>();
        List<Obstacle> obs = new ArrayList<>(avoid);
        for (Spec s : SPECS) {
            Found f = find(g, capital, s, obs);
            out.add(f);
            obs.add(new Obstacle(f.center(), 200));
        }
        SkyAbyss.LOGGER.info("Chapter grounds planned in {} ms: {}", (System.nanoTime() - t0) / 1_000_000L,
                out.stream().map(f -> f.chapter().id() + " " + f.center().toShortString() + " " + f.shape() + " d" + f.dist() + " " + f.deg() + "deg").toList());
        return out;
    }

    static Found find(Ground g, BlockPos capital, Spec s, List<Obstacle> obs) {
        for (int d = s.min(); d <= s.max(); d += DIST_STEP) {
            for (int off : offsets()) {
                double a = s.angle() + Math.toRadians(off);
                int x = capital.getX() + (int) Math.round(Math.cos(a) * d);
                int z = capital.getZ() + (int) Math.round(Math.sin(a) * d);
                if (blocked(x, z, obs)) {
                    continue;
                }
                Found f = fit(g, capital, s, x, z, d, off);
                if (f != null && !g.structureNear(x, z, STRUCTURE_GAP)) {
                    return finish(g, capital, f);
                }
            }
        }
        return finish(g, capital, shapeAt(g, capital, s, obs));
    }

    // 0, +5, -5, +10, -10, ... ±25
    static int[] offsets() {
        int n = SECTOR / ANGLE_STEP;
        int[] out = new int[n * 2 + 1];
        for (int i = 1; i <= n; i++) {
            out[i * 2 - 1] = i * ANGLE_STEP;
            out[i * 2] = -i * ANGLE_STEP;
        }
        return out;
    }

    private static boolean blocked(int x, int z, List<Obstacle> obs) {
        for (Obstacle o : obs) {
            if (Math.hypot(x - o.pos().getX(), z - o.pos().getZ()) < o.radius()) {
                return true;
            }
        }
        return false;
    }

    // 合う地形か (合えば形なしの跡地)
    @Nullable
    static Found fit(Ground g, BlockPos capital, Spec s, int x, int z, int d, int off) {
        int sea = g.sea();
        int t = g.topCoarse(x, z);
        switch (s.chapter()) {
            case WHALE -> {
                if (t < sea + HILL_ABOVE_SEA || !g.biomeOk(x, z)) {
                    return null;
                }
                int sum = 0;
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    int h = g.topCoarse(x + (int) Math.round(Math.cos(a) * HILL_RING), z + (int) Math.round(Math.sin(a) * HILL_RING));
                    if (h < sea || Math.abs(h - t) > 40) {
                        return null;
                    }
                    sum += h;
                }
                if (t - sum / 8.0 < HILL_PROMINENCE) {
                    return null;
                }
                return new Found(s.chapter(), new BlockPos(x, t + 1, z), Shape.NONE, BlockPos.ZERO, t, null, 0, false, null, List.of(), d, off);
            }
            case TIDE -> {
                if (t < sea || t > sea + COAST_LOW) {
                    return null;
                }
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    double ux = Math.cos(a);
                    double uz = Math.sin(a);
                    if (!g.wetCoarse(x + (int) Math.round(ux * 24), z + (int) Math.round(uz * 24))
                            && !g.wetCoarse(x + (int) Math.round(ux * SHORE_REACH), z + (int) Math.round(uz * SHORE_REACH))) {
                        continue;
                    }
                    int bx = x + (int) Math.round(ux * BODY_OUT);
                    int bz = z + (int) Math.round(uz * BODY_OUT);
                    boolean deep = true;
                    for (int[] p : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (g.topCoarse(bx + p[0] * BODY_HALF, bz + p[1] * BODY_HALF) > sea - BODY_DEPTH) {
                            deep = false;
                            break;
                        }
                    }
                    if (deep) {
                        return new Found(s.chapter(), new BlockPos(x, t + 1, z), Shape.NONE, BlockPos.ZERO, t, new BlockPos(bx, sea, bz), 0, false, null, List.of(), d, off);
                    }
                }
                return null;
            }
            case STORM -> {
                if (t < PEAK_Y) {
                    return null;
                }
                // 頂へ寄せる (16 の格子で、いちばん高い所)
                int bx = x;
                int bz = z;
                int bt = t;
                for (int dx = -16; dx <= 16; dx += 16) {
                    for (int dz = -16; dz <= 16; dz += 16) {
                        int h = g.topCoarse(x + dx, z + dz);
                        if (h > bt) {
                            bt = h;
                            bx = x + dx;
                            bz = z + dz;
                        }
                    }
                }
                return new Found(s.chapter(), new BlockPos(bx, bt + 1, bz), Shape.NONE, BlockPos.ZERO, bt, null, 0, false, null, List.of(), d, off);
            }
            case EMBER -> {
                if (t <= sea || t > sea + BASIN_HIGH || !g.dryBiome(x, z)) {
                    return null;
                }
                int lo = t;
                int hi = t;
                for (int[] p : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int h = g.topCoarse(x + p[0] * 48, z + p[1] * 48);
                    if (h < sea) {
                        return null;
                    }
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
                if (hi - lo > 24) {
                    return null;
                }
                return new Found(s.chapter(), new BlockPos(x, t + 1, z), Shape.NONE, BlockPos.ZERO, t, null, 0, false, null, List.of(), d, off);
            }
            default -> {
                return null;
            }
        }
    }

    // 合う地形がない: 帯の中の候補のうち、いちばん乾いて平らな所 (まんなかに近いほど少しよい) に形を作る
    static Found shapeAt(Ground g, BlockPos capital, Spec s, List<Obstacle> obs) {
        int mid = (s.min() + s.max()) / 2;
        int half = s.side() / 2;
        double bestScore = -1e18;
        int bx = 0;
        int bz = 0;
        int bd = mid;
        int boff = 0;
        int bBase = g.sea();
        // 帯の全体を 64 刻みで (まんなかに近いほど少しよい)。形の中 (5×5) が乾いていて平らで、まわり 128 の輪 (小要素の広がる所) も乾いた陸の所
        for (int d = s.min(); d <= s.max(); d += 2 * DIST_STEP) {
            for (int off : offsets()) {
                double a = s.angle() + Math.toRadians(off);
                int x = capital.getX() + (int) Math.round(Math.cos(a) * d);
                int z = capital.getZ() + (int) Math.round(Math.sin(a) * d);
                if (blocked(x, z, obs)) {
                    continue;
                }
                int wet = 0;
                List<Integer> hs = new ArrayList<>();
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        int h = g.topCoarse(x + i * half / 2, z + j * half / 2);
                        if (h < g.sea()) {
                            wet++;
                        }
                        hs.add(h);
                    }
                }
                int outerWet = 0;
                int low = 0;
                for (int i = 0; i < 12; i++) {
                    double b = Math.PI * 2 * i / 12;
                    int h = g.topCoarse(x + (int) Math.round(Math.cos(b) * 128), z + (int) Math.round(Math.sin(b) * 128));
                    if (h < g.sea()) {
                        outerWet++;
                    } else if (h <= g.sea() + 1) {
                        low++;
                    }
                }
                hs.sort(Integer::compare);
                int range = hs.get(hs.size() - 1) - hs.get(0);
                for (int h : hs) {
                    if (h >= g.sea() && h <= g.sea() + 1) {
                        low++;
                    }
                }
                double score = -wet * 100.0 - outerWet * 40.0 - low * 10.0 - range - Math.abs(d - mid) * 0.05 - Math.abs(off) * 0.3;
                if (score > bestScore && !g.structureNear(x, z, STRUCTURE_GAP)) {
                    bestScore = score;
                    bx = x;
                    bz = z;
                    bd = d;
                    boff = off;
                    bBase = Math.max(g.sea(), hs.get(hs.size() / 2));
                }
            }
        }
        if (bestScore <= -1e18) {
            // 全部ふさがっている (ありえないが): 帯のまんなかの正面
            bx = capital.getX() + s.dirX() * mid;
            bz = capital.getZ() + s.dirZ() * mid;
            bBase = Math.max(g.sea(), g.topCoarse(bx, bz));
        }
        BlockPos sc = new BlockPos(bx, bBase, bz);
        return switch (s.shape()) {
            case HILL -> new Found(s.chapter(), new BlockPos(bx, bBase + HILL_RISE + 1, bz), Shape.HILL, sc, bBase, null, 0, false, null, List.of(), bd, boff);
            case PEAK -> new Found(s.chapter(), new BlockPos(bx, bBase + PEAK_RISE + 1, bz), Shape.PEAK, sc, bBase, null, 0, false, null, List.of(), bd, boff);
            case BASIN -> {
                int floor = basinFloor(bBase, g.sea());
                yield new Found(s.chapter(), new BlockPos(bx, floor + 1, bz), Shape.BASIN, sc, bBase, null, 0, false, null, List.of(), bd, boff);
            }
            case COVE -> {
                // 浜 (跡地の中心) は入り江の都の側。入り江の中心は形の中心。巨神は左 (潮門) を浜へ向けて、入り江のまんなかで休む
                double a = Math.atan2(bz - capital.getZ(), bx - capital.getX());
                int ux = (int) Math.round(Math.cos(a) * COVE_BEACH);
                int uz = (int) Math.round(Math.sin(a) * COVE_BEACH);
                // 軸に沿わせる (入り江は軸に沿った四角なので、体も軸に沿わせる)
                int[] axis = Math.abs(ux) >= Math.abs(uz) ? new int[] {Integer.signum(ux), 0} : new int[] {0, Integer.signum(uz)};
                BlockPos beach = new BlockPos(bx - axis[0] * COVE_BEACH, Math.max(g.sea(), bBase) + 1, bz - axis[1] * COVE_BEACH);
                // 左 (cos yaw, sin yaw) = 浜の向き (-axis)
                float yaw = (float) Math.toDegrees(Math.atan2(-axis[1], -axis[0]));
                double fx = -Math.sin(Math.toRadians(yaw));
                double fz = Math.cos(Math.toRadians(yaw));
                // 体の箱 (前 87・後ろ 59) のまんなかを入り江のまんなかに: 巨神の原点は前へ 14 ずらした分だけ後ろ
                BlockPos rest = new BlockPos(bx - (int) Math.round(fx * 14), g.sea(), bz - (int) Math.round(fz * 14));
                yield new Found(s.chapter(), beach, Shape.COVE, sc, bBase, rest, yaw, true, null, List.of(), bd, boff);
            }
            default -> new Found(s.chapter(), new BlockPos(bx, bBase + 1, bz), Shape.NONE, sc, bBase, null, 0, false, null, List.of(), bd, boff);
        };
    }

    static int basinFloor(int base, int sea) {
        return Math.max(sea + 1, base - BASIN_DEPTH);
    }

    // 祠と道しるべを決める (形の重ねた地形で)
    static Found finish(Ground raw, BlockPos capital, Found f) {
        Ground g = overlay(raw, List.of(f));
        BlockPos shrine = shrineNear(g, f.center(), f.chapter() == Chapters.WHALE ? 56 : 16, List.of());
        List<BlockPos> road = road(g, capital, f.center(), f.chapter() == Chapters.WHALE ? WHALE_CAIRNS : 0);
        return new Found(f.chapter(), f.center(), f.shape(), f.shapeCenter(), f.baseY(), f.sea(), f.restYaw(), f.fixedRest(), shrine, road, f.dist(), f.deg());
    }

    // 祠の台座 (9×9) が平らに乗る所。base のまわり rMin〜rMin+56 の輪。avoid の中は選ばない。なければ base
    static BlockPos shrineNear(Ground g, BlockPos base, int rMin, List<Obstacle> avoid) {
        for (int[] pass : new int[][] {{3, 1}, {6, 1}, {10, 0}}) {
            for (int r = rMin; r <= rMin + 56; r += 8) {
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + r * 0.1;
                    int x = base.getX() + (int) Math.round(Math.cos(a) * r);
                    int z = base.getZ() + (int) Math.round(Math.sin(a) * r);
                    if (blocked(x, z, avoid)) {
                        continue;
                    }
                    Area ar = SiteFinder.area(g, x, z, 4, 4, pass[0], false, pass[1] == 1);
                    if (ar.ok()) {
                        return new BlockPos(x, ar.median() + 1, z);
                    }
                }
            }
        }
        // 平らな所がない: 輪の内側の乾いた所 (台座は祠を建てる側が地面まで埋める)。それもなければ base から rMin + 4 の東
        for (int r = rMin; r <= rMin + 56; r += 8) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                int x = base.getX() + (int) Math.round(Math.cos(a) * r);
                int z = base.getZ() + (int) Math.round(Math.sin(a) * r);
                if (!blocked(x, z, avoid) && !g.wet(x, z)) {
                    return new BlockPos(x, g.top(x, z) + 1, z);
                }
            }
        }
        return new BlockPos(base.getX() + rMin + 4, g.top(base.getX() + rMin + 4, base.getZ()) + 1, base.getZ());
    }

    // 都から跡地への道しるべ: 中心から ROAD_START で始め、ROAD_STEP ごと、跡地の手前 ROAD_END_GAP で止める。
    // atLeast > 0 なら、その数が入るように間を詰める (12 まで)。水の上・崖・海面の高さの平地の点は、まわり (前後 12・左右 20) に置けなければ
    // 最後の手段 (roadFallback: 低い平地、浅い水なら石の台) に回し、それもだめな点 (深い水・構造物のそば) だけ飛ばす
    static List<BlockPos> road(Ground g, BlockPos capital, BlockPos center, int atLeast) {
        // 足りなければ間を詰めて探し直す (置けない点を飛ばすと数が減るので)
        List<BlockPos> out = roadOnce(g, capital, center, atLeast, ROAD_STEP);
        for (double step = ROAD_STEP * 0.75; atLeast > 0 && out.size() < atLeast && step >= 12; step *= 0.75) {
            out = roadOnce(g, capital, center, atLeast, step);
        }
        return out;
    }

    private static List<BlockPos> roadOnce(Ground g, BlockPos capital, BlockPos center, int atLeast, double baseStep) {
        double dx = center.getX() - capital.getX();
        double dz = center.getZ() - capital.getZ();
        double len = Math.hypot(dx, dz);
        double ux = dx / len;
        double uz = dz / len;
        double span = len - ROAD_START - ROAD_END_GAP;
        List<BlockPos> out = new ArrayList<>();
        if (span <= 0) {
            return out;
        }
        double step = baseStep;
        if (atLeast > 0 && span / step + 1 < atLeast) {
            step = Math.max(12, span / (atLeast - 1));
        }
        int n = (int) Math.floor(span / step) + 1;
        for (int k = 0; k < n; k++) {
            double along = ROAD_START + span - (n - 1 - k) * step;
            BlockPos at = null;
            search:
            for (int shift : new int[] {0, 4, -4, 8, -8, 12, -12}) {
                for (int side : new int[] {0, 6, -6, 12, -12, 20, -20}) {
                    int x = capital.getX() + (int) Math.round(ux * (along + shift) - uz * side);
                    int z = capital.getZ() + (int) Math.round(uz * (along + shift) + ux * side);
                    Area ar = SiteFinder.area(g, x, z, 1, 1, 4, false, false);
                    // 海面の高さの平地 (沼・浜・川べり) は水たまりが多いので避ける
                    if (ar.ok() && ar.min() >= g.sea() + 1 && !g.structureNear(x, z, ROAD_STRUCTURE_GAP)) {
                        at = new BlockPos(x, ar.median() + 1, z);
                        break search;
                    }
                }
            }
            if (at == null) {
                at = roadFallback(g, capital, ux, uz, along);
            }
            if (at != null) {
                out.add(at);
            }
        }
        return out;
    }

    // 道しるべの点の最後の手段 (乾いた平らな所が見つからない): まず海面の高さの低い平地 (沼・川べり) も許して探し、
    // それもなければ線の上の浅い水 (深さ BRIDGE_DEPTH まで) に置く。水の上の点は、建てるときに石の台を水底から立てる (SiteJob の pillar)
    public static final int BRIDGE_DEPTH = 10;

    @Nullable
    static BlockPos roadFallback(Ground g, BlockPos capital, double ux, double uz, double along) {
        for (int shift : new int[] {0, 4, -4, 8, -8, 12, -12}) {
            for (int side : new int[] {0, 6, -6, 12, -12}) {
                int x = capital.getX() + (int) Math.round(ux * (along + shift) - uz * side);
                int z = capital.getZ() + (int) Math.round(uz * (along + shift) + ux * side);
                Area ar = SiteFinder.area(g, x, z, 1, 1, 4, false, false);
                if (ar.ok() && !g.structureNear(x, z, ROAD_STRUCTURE_GAP)) {
                    return new BlockPos(x, ar.median() + 1, z);
                }
            }
        }
        for (int shift : new int[] {0, 4, -4, 8, -8}) {
            int x = capital.getX() + (int) Math.round(ux * (along + shift));
            int z = capital.getZ() + (int) Math.round(uz * (along + shift));
            int top = g.top(x, z);
            if (g.wet(x, z) && g.sea() - top <= BRIDGE_DEPTH && !g.structureNear(x, z, ROAD_STRUCTURE_GAP)) {
                return new BlockPos(x, g.sea() + 1, z);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 形を重ねた地形 (場所探しが、整えたあとの地形を見る)
    public static Ground overlay(Ground raw, List<Found> grounds) {
        List<SiteData.Ground> list = new ArrayList<>();
        for (Found f : grounds) {
            if (f.shape() != Shape.NONE) {
                SiteData.Ground sg = SiteData.newGround(f.chapter().id(), f.center());
                sg.shape = f.shape().name();
                sg.shapeCenter = f.shapeCenter();
                sg.baseY = f.baseY();
                list.add(sg);
            }
        }
        return overlayData(raw, list);
    }

    static Ground overlayData(Ground raw, List<SiteData.Ground> grounds) {
        List<SiteData.Ground> shaped = new ArrayList<>();
        for (SiteData.Ground g : grounds) {
            if (!g.shape.equals(Shape.NONE.name())) {
                shaped.add(g);
            }
        }
        if (shaped.isEmpty()) {
            return raw;
        }
        return new Ground() {
            private int adjust(int x, int z, int t) {
                for (SiteData.Ground g : shaped) {
                    int v = shapeTop(g, x, z, t, raw.sea());
                    if (v != Integer.MIN_VALUE) {
                        return v;
                    }
                }
                return t;
            }

            @Override
            public int top(int x, int z) {
                return adjust(x, z, raw.top(x, z));
            }

            @Override
            public int topCoarse(int x, int z) {
                return adjust(x, z, raw.topCoarse(x, z));
            }

            @Override
            public boolean wet(int x, int z) {
                return top(x, z) < raw.sea();
            }

            @Override
            public boolean wetCoarse(int x, int z) {
                return topCoarse(x, z) < raw.sea();
            }

            @Override
            public boolean biomeOk(int x, int z) {
                for (SiteData.Ground g : shaped) {
                    if (inShape(g, x, z)) {
                        return true;
                    }
                }
                return raw.biomeOk(x, z);
            }

            @Override
            public int sea() {
                return raw.sea();
            }

            @Override
            public boolean dryBiome(int x, int z) {
                return raw.dryBiome(x, z);
            }

            @Override
            public boolean structureNear(int x, int z, int r) {
                return raw.structureNear(x, z, r);
            }
        };
    }

    public static Shape shapeOf(SiteData.Ground g) {
        try {
            return Shape.valueOf(g.shape);
        } catch (IllegalArgumentException e) {
            return Shape.NONE;
        }
    }

    public static int half(Shape s) {
        return switch (s) {
            case HILL -> HILL_HALF;
            case PEAK -> PEAK_HALF;
            case BASIN -> BASIN_HALF;
            case COVE -> COVE_HALF;
            default -> 0;
        };
    }

    static boolean inShape(SiteData.Ground g, int x, int z) {
        int h = half(shapeOf(g));
        return h > 0 && Math.abs(x - g.shapeCenter.getX()) <= h && Math.abs(z - g.shapeCenter.getZ()) <= h;
    }

    // 整えたあとの地面の高さ (いちばん上の固いブロック)。形の外なら Integer.MIN_VALUE。natural = 今の地面の高さ。
    // 入り江の水の所は「海底」の高さ (海面より下 = 水)
    public static int shapeTop(SiteData.Ground g, int x, int z, int natural, int sea) {
        Shape s = shapeOf(g);
        int h = half(s);
        int dx = x - g.shapeCenter.getX();
        int dz = z - g.shapeCenter.getZ();
        if (h == 0 || Math.abs(dx) > h || Math.abs(dz) > h) {
            return Integer.MIN_VALUE;
        }
        double r = Math.hypot(dx, dz);
        switch (s) {
            case HILL -> {
                if (r >= HILL_HALF) {
                    return Integer.MIN_VALUE;
                }
                double u = r / HILL_HALF;
                int target = g.baseY + (int) Math.round(HILL_RISE * (Math.cos(Math.PI * u) + 1) / 2);
                return Math.max(natural, target);
            }
            case PEAK -> {
                if (r >= PEAK_HALF) {
                    return Integer.MIN_VALUE;
                }
                double u = r / PEAK_HALF;
                // 尾根の筋 (向きで少し波打つ) をつけた円錐
                double ridge = 1 + 0.12 * Math.sin(Math.atan2(dz, dx) * 5 + g.shapeCenter.getX() * 0.01);
                double p = Math.pow(Math.max(0, 1 - u * ridge * 0.95), 1.5);
                int target = g.baseY + (int) Math.round(PEAK_RISE * Math.min(1, p));
                return Math.max(natural, target);
            }
            case BASIN -> {
                if (r >= BASIN_HALF) {
                    return Integer.MIN_VALUE;
                }
                int floor = basinFloor(g.baseY, sea);
                double u = r / BASIN_HALF;
                if (u < 0.7) {
                    return floor;
                }
                double w = (u - 0.7) / 0.3;
                w = w * w * (3 - 2 * w);
                return (int) Math.round(floor * (1 - w) + natural * w);
            }
            case COVE -> {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m <= COVE_DEEP) {
                    return sea - COVE_FLOOR;
                }
                if (m <= COVE_WATER) {
                    double w = (m - COVE_DEEP) / (double) (COVE_WATER - COVE_DEEP);
                    return (int) Math.round((sea - COVE_FLOOR) * (1 - w) + (sea - 3) * w);
                }
                // 浜: 海面の高さの砂 (高い地面は残す)
                return Math.max(sea, Math.min(natural, sea + 2));
            }
            default -> {
                return Integer.MIN_VALUE;
            }
        }
    }

    // ---------------------------------------------------------------- 書く・整える (サーバー)
    private static volatile CompletableFuture<List<Found>> pending;
    private static final List<ShapeJob> JOBS = new ArrayList<>();

    public static boolean planning() {
        return pending != null;
    }

    public static synchronized boolean busy() {
        return pending != null || !JOBS.isEmpty();
    }

    // 古い世界 (序盤の場所は決まっているのに、跡地がまだ): 都のまわりで跡地だけを決める。骨は今の場所のまま
    public static boolean ensurePlanned(MinecraftServer server) {
        ServerLevel level = server.overworld();
        SiteData data = SiteData.get(level);
        if (data.groundsPlanned() || pending != null || !data.planned()) {
            return false;
        }
        SiteData.Entry shelter = data.first(Kind.SHELTER.id);
        if (shelter == null) {
            return false;
        }
        SiteFinder.Probe probe = new SiteFinder.Probe(level);
        List<Obstacle> avoid = avoidList(data);
        BlockPos capital = shelter.pos;
        pending = CompletableFuture.supplyAsync(() -> planAll(probe, capital, avoid), Util.backgroundExecutor());
        return true;
    }

    static List<Obstacle> avoidList(SiteData data) {
        List<Obstacle> avoid = new ArrayList<>();
        for (SiteData.Entry e : data.entries()) {
            if (e.kind.equals(Kind.CAMP.id)) {
                avoid.add(new Obstacle(e.pos, CAMP_GAP));
            } else if (e.kind.equals(Kind.FOOTPRINT.id)) {
                avoid.add(new Obstacle(e.pos, PRINT_GAP));
            }
        }
        return avoid;
    }

    // 決めた跡地を書く (tick の中)。一章の形があれば、すぐ整え始める
    static void write(MinecraftServer server, List<Found> found) {
        SiteData data = SiteData.get(server.overworld());
        List<SiteData.Ground> list = new ArrayList<>();
        for (Found f : found) {
            SiteData.Ground g = SiteData.newGround(f.chapter().id(), f.center());
            g.shape = f.shape().name();
            g.shapeCenter = f.shapeCenter();
            g.baseY = f.baseY();
            g.shaped = f.shape() == Shape.NONE;
            g.shrine = f.shrine();
            g.sea = f.sea();
            g.restYaw = f.restYaw();
            g.fixedRest = f.fixedRest();
            g.road.addAll(f.road());
            list.add(g);
        }
        data.setGrounds(list);
        prepare(server, Chapters.WHALE);
    }

    // その章の跡地を整え始める (まだなら)。何度呼んでもよい。戻り値 = 使えるか (整え終わっている)
    public static synchronized boolean prepare(MinecraftServer server, Chapters chapter) {
        SiteData data = SiteData.get(server.overworld());
        SiteData.Ground g = data.ground(chapter.id());
        if (g == null) {
            return false;
        }
        if (g.shaped) {
            return true;
        }
        for (ShapeJob j : JOBS) {
            if (j.ground == g) {
                return false;
            }
        }
        JOBS.add(new ShapeJob(server.overworld(), data, g));
        SkyAbyss.LOGGER.info("Chapter grounds: shaping the {} ground ({} at {})", g.chapter, g.shape, g.shapeCenter.toShortString());
        return false;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CompletableFuture<List<Found>> f = pending;
        if (f != null && f.isDone()) {
            pending = null;
            try {
                write(server, f.join());
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("Chapter grounds: the search failed", e);
            }
        }
        ShapeJob job;
        synchronized (ChapterGrounds.class) {
            job = JOBS.isEmpty() ? null : JOBS.get(0);
        }
        if (job == null) {
            return;
        }
        long budget = server.getPlayerCount() > 0 ? SiteBuilds.BUDGET_PLAYERS_MS : SiteBuilds.BUDGET_IDLE_MS;
        if (job.step(System.nanoTime() + budget * 1_000_000L)) {
            synchronized (ChapterGrounds.class) {
                JOBS.remove(job);
            }
            done(server, job);
        }
    }

    private static void done(MinecraftServer server, ShapeJob job) {
        job.ground.shaped = true;
        job.data.setDirty();
        SkyAbyss.LOGGER.info("Chapter grounds: shaped the {} ground: {} blocks in {} ticks ({} ms of work)", job.ground.chapter, job.placed, job.ticks,
                job.workNanos / 1_000_000L);
        SiteBuilds.enqueueMissing(server);
    }

    // 自己テスト・開発コマンド用: 探しと整えを最後まで (チャンクをその場で読む)
    public static void finishNow(MinecraftServer server) {
        CompletableFuture<List<Found>> f = pending;
        if (f != null) {
            f.join();
            pending = null;
            write(server, f.join());
        }
        while (true) {
            ShapeJob job;
            synchronized (ChapterGrounds.class) {
                job = JOBS.isEmpty() ? null : JOBS.get(0);
            }
            if (job == null) {
                return;
            }
            job.blocking = true;
            while (!job.step(System.nanoTime() + 200_000_000L)) {
                // 次の周回
            }
            synchronized (ChapterGrounds.class) {
                JOBS.remove(job);
            }
            done(server, job);
        }
    }

    // 序盤の計画の中で決めた跡地を書く (SiteBuilds の applyPlan から)
    static void accept(MinecraftServer server, @Nullable List<Found> found) {
        if (found != null && !found.isEmpty()) {
            write(server, found);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        pending = null;
        synchronized (ChapterGrounds.class) {
            JOBS.forEach(ShapeJob::release);
            JOBS.clear();
        }
    }
}
