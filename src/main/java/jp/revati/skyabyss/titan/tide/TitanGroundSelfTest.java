package jp.revati.skyabyss.titan.tide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanGround;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import jp.revati.skyabyss.titan.storm.StormPerch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 巨神が地形にめり込まない決まり (TitanGround) の自己テスト (SKYABYSS_SELFTEST。単独は SKYABYSS_SELFTEST_ONLY="titan ground")。
// どれも「測りの計算」ではなく、体積の柱ごとにブロックを数えて (固いブロックが体の中にあるか) 確かめる。
//  1. 潮殻の休む場所: 世界のいろいろな岸 (丘・木・浅瀬) で、旧い決まり (測りを切る) ではめり込む場所が選ばれ、
//     新しい決まりでは選ばれた場所のどこにもめり込まない (見つからなければ null)
//  2. 潮殻の巡航: 沖から岸へ向かって歩く間、毎 tick めり込まず、持ち上がりはなめらか (1 tick に 0.4 ブロック以下)
//  3. 雷翼の止まり木: 高い峰のまわりで、止まり木の高さなら向きがどちらでも体がめり込まない
//  4. 潮殻の乗り場: 標と輪は体 (core) の外。門の口と輪には入れ、戻る所には入れない
//
// 段は一つの系統 (titan ground) だが、中身は小さな「作業」に分けて、一 tick に一つずつ流す (SelfTestRunner は until の段を許す)。
// 地形の区画は同期に生成しない: 必要な区画を読み込みに固定し、読み込みが済むまで次の作業へ進まない (背景で生成される)。
// 作業の中身 (測り・判定の式) は前のままで、読み込まれた地形は同じなので、結果は前と同じ。
public final class TitanGroundSelfTest {
    private static final long RNG_SEED = 4242L;
    // 岸の候補を探す範囲 (世界のスポーンから)。探す間隔 (ブロック) と、試す岸の数
    private static final int SCAN_RADIUS = 700;
    private static final int SCAN_STEP = 48;
    private static final int SITES = 4;
    private static final int PLAYERS_PER_SHORE = 3;
    private static final int PRELOAD = 17;
    // 巡航: 1 tick に進める回数 (3600 回ぶんを 1 tick で回すと重い)
    private static final int CRUISE_TICKS = 3600;
    private static final int CRUISE_PER_TICK = 25;
    // 読み込みに固定して、まだ読み込み中の区画の数の上限 (多すぎると背景の生成と光の計算でメモリが足りなくなる)
    private static final int MAX_PENDING = 128;

    private TitanGroundSelfTest() {
    }

    // 岸の場所: 水際の (水の点 water, 陸の点 land)
    private record Shore(Vec3 water, Vec3 land) {
    }

    // 一つの作業。true を返したら済んだ (次へ)。false なら次の tick にもう一度
    private interface Body {
        boolean run(Run r);
    }

    private static final class Unit {
        final String phase;
        final Body body;

        Unit(String phase, Body body) {
            this.phase = phase;
            this.body = body;
        }
    }

    // 段の間の状態 (作業をまたいで持つもの)
    private static final class Run {
        final List<String> failures = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final Deque<Unit> units = new ArrayDeque<>();
        final Set<Long> forced = new HashSet<>();
        final Set<Long> pending = new HashSet<>();
        final Map<String, long[]> phases = new LinkedHashMap<>();
        MinecraftServer server;
        ServerLevel level;
        TitanVolume shell;
        TitanVolume wing;
        boolean was;
        // 読み込みが足りなかった作業は、結果を捨てて次の tick にやり直す
        boolean missing;
        List<Shore> shores = new ArrayList<>();
        List<Vec3> cands;
        int candIdx;
        // 休む場所
        int tried;
        int oldFound;
        int oldBad;
        int newFound;
        int newRaised;
        double maxRaise;
        double minSunk = Double.POSITIVE_INFINITY;
        double worstOld;
        final List<Double> looseRaise = new ArrayList<>();
        // 巡航
        Cruise cruise;
        // 雷翼の止まり木
        List<int[]> highs = new ArrayList<>();
        int perchChecked;
        int perchRises;
        double perchMaxRise;
        // 余燼の眠る場所
        TitanVolume ember;
        Random emberRng;
        int emberTries;
        int emberSites;
        int emberOldFound;
        int emberOldBad;
        int emberNewFound;
        boolean emberPending;
        int emberX;
        int emberZ;
    }

    private static final class Cruise {
        final TideShellEntity titan;
        final double sx;
        final double sz;
        final Shore shore;
        final boolean found;
        int t;
        double worstOverlap = Double.NEGATIVE_INFINITY;
        double maxStep;
        double minMargin = Double.MAX_VALUE;
        double lastY;
        String maxStepInfo = "";

        Cruise(TideShellEntity titan, double sx, double sz, Shore shore, boolean found) {
            this.titan = titan;
            this.sx = sx;
            this.sz = sz;
            this.shore = shore;
            this.found = found;
            this.lastY = titan.getY();
        }
    }

    private static Run current;

    // SelfTestRunner の段 (until) から毎 tick 呼ばれる。全部済んだら true
    public static boolean tick(MinecraftServer server) {
        if (current == null) {
            current = new Run();
            current.server = server;
            current.units.add(new Unit("setup", TitanGroundSelfTest::setup));
            current.units.add(new Unit("shores", TitanGroundSelfTest::shores));
        }
        Run r = current;
        Unit u = r.units.peekFirst();
        if (u == null) {
            current = null;
            return true;
        }
        long t0 = System.nanoTime();
        boolean done;
        try {
            done = u.body.run(r);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan ground crashed", e);
            r.failures.add("crashed: " + e);
            r.units.clear();
            r.units.add(new Unit("finish", TitanGroundSelfTest::finish));
            done = false;
        }
        long took = System.nanoTime() - t0;
        long[] acc = r.phases.computeIfAbsent(u.phase, k -> new long[2]);
        acc[0] += took;
        acc[1]++;
        if (done) {
            r.units.removeFirstOccurrence(u);
            // 段が済んだら読み込みの固定を外す (巡航の途中は、道筋の区画を固定したまま)。必要な区画は次の段でまた固定する
            if (r.cruise == null) {
                releaseAll(r);
            }
        }
        if (r.units.isEmpty()) {
            current = null;
            return true;
        }
        return false;
    }

    // 作業を、いま実行中の作業の次に入れる (順番どおりに)
    private static void pushFront(Run r, List<Unit> next) {
        for (int i = next.size() - 1; i >= 0; i--) {
            r.units.addFirst(next.get(i));
        }
    }

    // ---------------------------------------------------------------- 準備

    private static boolean setup(Run r) {
        jp.revati.skyabyss.titan.end.TitanEndSelfTest.resetAll(r.server);
        r.was = TitanGround.enabled;
        TitanGround.enabled = true;
        r.level = r.server.overworld();
        r.shell = TitanVolume.get("tide_shell");
        r.wing = TitanVolume.get("thunder_wing");
        if (r.shell == null || r.wing == null) {
            r.failures.add("a titan volume is missing");
            r.units.clear();
            r.units.add(new Unit("finish", TitanGroundSelfTest::finish));
        }
        return true;
    }

    // 岸を探して、残りの作業を並べる
    private static boolean shores(Run r) {
        if (!findShores(r)) {
            return false;
        }
        r.notes.add("shores found: " + r.shores.size());
        List<Unit> next = new ArrayList<>();
        if (r.shores.isEmpty()) {
            r.notes.add("no coast within " + SCAN_RADIUS + " of the spawn: rest spot and cruise checks skipped");
        } else {
            for (Shore s : r.shores) {
                for (int k = 0; k < PLAYERS_PER_SHORE; k++) {
                    final Shore sh = s;
                    final int kk = k;
                    next.add(new Unit("rest spots", x -> restSpot(x, sh, kk)));
                }
                next.add(new Unit("release", TitanGroundSelfTest::release));
            }
            next.add(new Unit("rest spots", TitanGroundSelfTest::restSummary));
            next.add(new Unit("cruise", TitanGroundSelfTest::cruiseSetup));
            next.add(new Unit("cruise", TitanGroundSelfTest::cruiseRun));
            next.add(new Unit("cruise", TitanGroundSelfTest::cruiseFinish));
            next.add(new Unit("release", TitanGroundSelfTest::release));
        }
        next.add(new Unit("perches", TitanGroundSelfTest::perchSample));
        next.add(new Unit("ember", TitanGroundSelfTest::ember));
        next.add(new Unit("release", TitanGroundSelfTest::release));
        next.add(new Unit("dock", TitanGroundSelfTest::dock));
        next.add(new Unit("finish", TitanGroundSelfTest::finish));
        pushFront(r, next);
        return true;
    }

    // ---------------------------------------------------------------- 1. 休む場所

    // 岸の一人の位置 (k 番目) を一つの作業で。人の立つ区画が読み込めたら試す
    private static boolean restSpot(Run r, Shore s, int k) {
        if (!ensure(r, s.land().x, s.land().z, PRELOAD)) {
            return false;
        }
        ServerLevel level = r.level;
        TitanVolume shell = r.shell;
        double restY = level.getSeaLevel() - TideShellEntity.REST_DRAFT;
        double ang = k * Math.PI * 2 / PLAYERS_PER_SHORE;
        double px = s.land().x + Math.cos(ang) * 16;
        double pz = s.land().z + Math.sin(ang) * 16;
        r.tried++;
        TitanGround.enabled = false;
        Vec3 old = TideOcean.findRestSpot(level, px, pz);
        TitanGround.enabled = true;
        if (old != null) {
            r.oldFound++;
            int bad = solidColumns(level, shell, old.x, restY, old.z, (float) old.y);
            if (bad > 0) {
                r.oldBad++;
                r.worstOld = Math.max(r.worstOld, bad);
                if (r.oldBad <= 6) {
                    r.notes.add(String.format("  old rule clips: shell at %d %d yaw %.0f (%d body columns in blocks), player at %d %d", (int) old.x, (int) old.z, old.y, bad,
                            (int) px, (int) pz));
                }
            }
        }
        Vec3 loose = TideOcean.findRestSpot(level, px, pz, 40.0);
        if (loose != null) {
            r.looseRaise.add(Math.max(0, TideOcean.restRaise(level, loose.x, loose.z, (float) loose.y)));
        }
        Vec3 now = TideOcean.findRestSpot(level, px, pz);
        if (now != null) {
            r.newFound++;
            double raise = Math.max(0, TideOcean.restRaise(level, now.x, now.z, (float) now.y));
            if (raise > 0.05) {
                r.newRaised++;
            }
            r.maxRaise = Math.max(r.maxRaise, raise);
            if (r.newFound <= 4) {
                r.notes.add(String.format("  new rule spot: shell at %d %d yaw %.0f raise %.1f for the player at %d %d", (int) now.x, (int) now.z, now.y, raise, (int) px, (int) pz));
            }
            // 体がその高さにあるときの、体の中の固いブロック (ブロックを直接数える)
            // 休むときは足・ひれを除いた腹を REST_BURY だけ海底へ沈めてよい。その分だけ持ち上げた体が地形に触れないことを見る
            int bad = solidColumns(level, shell, now.x, restY + raise + TideShellEntity.REST_BURY, now.z, (float) now.y, TideShellEntity.REST_LEG_SKIP, TideShellEntity.REST_SKIRT_HI);
            if (bad > 0) {
                r.failures.add("the tide shell rest spot (" + (int) now.x + ", " + (int) now.z + ") has " + bad + " body columns inside blocks");
            }
            // 体の底 (甲羅の腹。足・ひれと低い裾を除く) が、水面より 6 ブロック以上下にあること = 腹が水に浸かって、甲羅が海に浮いて見えない
            double belly = bellyBottom(shell);
            double hullBottom = restY + raise + belly;
            double sunk = level.getSeaLevel() - hullBottom;
            r.minSunk = Math.min(r.minSunk, sunk);
            if (sunk < 6.0 - 0.05) {
                r.failures.add("the tide shell hull floats: its belly bottom is only " + sunk + " blocks under the water at (" + (int) now.x + ", " + (int) now.z + ") raise " + raise);
            }
            if (raise > TideOcean.MAX_RAISE + 0.05) {
                r.failures.add("a chosen rest spot needs a lift above the limit: " + raise);
            }
        }
        return true;
    }

    private static boolean restSummary(Run r) {
        r.notes.add("hull belly under the water at the rest spots: at least " + String.format("%.1f", r.minSunk) + " blocks");
        r.notes.add("rest spots: " + r.tried + " player positions, old rule found " + r.oldFound + " (" + r.oldBad + " clip into terrain, worst " + (int) r.worstOld
                + " columns), new rule found " + r.newFound + " (" + r.newRaised + " need the shell lifted, max " + String.format("%.1f", r.maxRaise) + " blocks), clipping 0 required");
        r.notes.add("lowest lift needed over the " + r.tried + " positions with the limit raised to 40: "
                + r.looseRaise.stream().map(v -> String.format("%.1f", v)).toList());
        if (r.newFound == 0 && r.oldFound > 0) {
            r.failures.add("the new rule never finds a rest spot where the old one did (" + r.oldFound + ")");
        }
        return true;
    }

    // 体積の底の高さ: 足・ひれ・低い裾 (頂が REST_SKIRT_HI に届かない柱) を除いた柱の底の最小 (甲羅の腹の底)
    private static double bellyBottom(TitanVolume shell) {
        double lo = Double.POSITIVE_INFINITY;
        for (TitanVolume.Column c : shell.coreColumns()) {
            if (c.hi() > TideShellEntity.REST_SKIRT_HI && c.lo() >= TideShellEntity.REST_LEG_SKIP) {
                lo = Math.min(lo, c.lo());
            }
        }
        return Double.isFinite(lo) ? lo : 0.0;
    }

    // ---------------------------------------------------------------- 2. 巡航

    private static boolean cruiseSetup(Run r) {
        ServerLevel level = r.level;
        TitanVolume shell = r.shell;
        List<Shore> shores = r.shores;
        // 沖が開けた (標準の巡航の高さで体が地形に触れない) 出だしを探す: 岸から沖へ 90・150・210 ブロック。なければ最初の岸の 90
        Shore s = shores.get(0);
        double sx = 0;
        double sz = 0;
        boolean found = false;
        for (Shore cand : shores) {
            Vec3 out = cand.water().subtract(cand.land()).multiply(1, 0, 1).normalize();
            for (double off : new double[] {90, 150, 210}) {
                double x = cand.water().x + out.x * off;
                double z = cand.water().z + out.z * off;
                if (!ensure(r, x, z, 6)) {
                    return false;
                }
                float yaw0 = (float) (Mth.atan2(cand.land().z - z, cand.land().x - x) * Mth.RAD_TO_DEG) - 90f;
                double n = TitanGround.requiredY(level, TitanGround.Surface.FLOOR, shell, x, z, yaw0);
                if (TideOcean.waterAtSurface(level, x, z) && n <= level.getSeaLevel() - TideShellEntity.CRUISE_DRAFT_MIN - 2) {
                    s = cand;
                    sx = x;
                    sz = z;
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        if (!found) {
            Vec3 out = s.water().subtract(s.land()).multiply(1, 0, 1).normalize();
            sx = s.water().x + out.x * 90;
            sz = s.water().z + out.z * 90;
        }
        // 巡航のあいだ、通る区画を読み込んだままにする (読み込みの外れた区画は、戻ると地形が変わって潮殻がとびはねる)
        double mx = (sx + s.land().x) / 2;
        double mz = (sz + s.land().z) / 2;
        if (!ensure(r, mx, mz, PRELOAD + 4)) {
            return false;
        }
        forceCorridor(r, sx, sz, s.land().x, s.land().z, true);
        TideShellEntity titan = TideSetup.TIDE_SHELL.get().create(level);
        if (titan == null) {
            r.failures.add("the tide shell could not be created");
            return true;
        }
        // 世界へは入れない (一体だけの決まりに触れない)。tick は手で回す
        float yaw = (float) (Mth.atan2(s.land().z - sz, s.land().x - sx) * Mth.RAD_TO_DEG) - 90f;
        // 出だしがすでに地形にめり込んでいては比べられないので、出だしの高さは下限と標準の巡航の高さの高い方
        double need0 = TitanGround.requiredY(level, TitanGround.Surface.FLOOR, shell, sx, sz, yaw);
        titan.moveTo(sx, Math.max(level.getSeaLevel() - TideShellEntity.CRUISE_DRAFT_MAX, Double.isFinite(need0) ? need0 : Double.NEGATIVE_INFINITY), sz, yaw, 0f);
        titan.testCruiseTo(s.land().x, s.land().z);
        r.cruise = new Cruise(titan, sx, sz, s, found);
        return true;
    }

    // 巡航を CRUISE_PER_TICK 回ずつ進める。終わるまで false
    private static boolean cruiseRun(Run r) {
        Cruise c = r.cruise;
        if (c == null) {
            return true;
        }
        for (int i = 0; i < CRUISE_PER_TICK && c.t < CRUISE_TICKS; i++, c.t++) {
            c.titan.tick();
            double overlap = TitanGround.overlap(r.level, TitanGround.Surface.FLOOR, r.shell, c.titan.getX(), c.titan.getY(), c.titan.getZ(), c.titan.getYRot());
            c.worstOverlap = Math.max(c.worstOverlap, overlap);
            c.minMargin = Math.min(c.minMargin, -overlap);
            if (c.t >= 10) {
                double st = Math.abs(c.titan.getY() - c.lastY);
                if (st > c.maxStep) {
                    c.maxStepInfo = "t=" + c.t + " step=" + String.format("%.2f", st) + " " + c.titan.debugGround();
                }
                c.maxStep = Math.max(c.maxStep, st);
            }
            c.lastY = c.titan.getY();
        }
        return c.t >= CRUISE_TICKS;
    }

    private static boolean cruiseFinish(Run r) {
        Cruise c = r.cruise;
        if (c == null) {
            return true;
        }
        TideShellEntity titan = c.titan;
        Shore s = c.shore;
        double endGap = Math.hypot(titan.getX() - s.land().x, titan.getZ() - s.land().z);
        if (c.worstOverlap > 1e-6) {
            r.failures.add("the cruising tide shell clipped into terrain by " + c.worstOverlap + " blocks on shore " + (int) s.land().x + ", " + (int) s.land().z);
        }
        if (c.maxStep > 1.05) {
            r.failures.add("the tide shell jerked vertically by " + c.maxStep + " blocks in one tick (" + c.maxStepInfo + ")");
        }
        r.notes.add("cruise from " + (int) c.sx + ", " + (int) c.sz + (c.found ? " (open water start)" : " (rough start)") + " toward the shore at " + (int) s.land().x + ", " + (int) s.land().z
                + ": worst overlap " + String.format("%.2f", c.worstOverlap) + ", min clearance " + String.format("%.2f", c.minMargin) + ", max step "
                + String.format("%.2f", c.maxStep) + ", ended " + (int) endGap + " from the shore at y " + String.format("%.1f", titan.getY()));
        titan.discard();
        forceCorridor(r, c.sx, c.sz, s.land().x, s.land().z, false);
        r.cruise = null;
        return true;
    }

    // ---------------------------------------------------------------- 3. 雷翼の止まり木

    // 高い所のまわり: 候補の点をいくつか高さで選ぶ (区画の読み込みが済んでから高さを読む)
    private static boolean perchSample(Run r) {
        ServerLevel level = r.level;
        Random rng = new Random(RNG_SEED);
        BlockPos spawn = level.getSharedSpawnPos();
        List<int[]> points = new ArrayList<>();
        r.missing = false;
        for (int i = 0; i < 90; i++) {
            int x = spawn.getX() + rng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            int z = spawn.getZ() + rng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            if (!ensureChunk(r, x >> 4, z >> 4)) {
                r.missing = true;
            }
            points.add(new int[] {x, z});
        }
        if (r.missing) {
            return false;
        }
        List<int[]> highs = new ArrayList<>();
        for (int[] p : points) {
            highs.add(new int[] {p[0], p[1], level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1])});
        }
        highs.sort((a, b) -> Integer.compare(b[2], a[2]));
        r.highs = highs;
        List<Unit> next = new ArrayList<>();
        for (int i = 0; i < Math.min(2, highs.size()); i++) {
            final int[] h = highs.get(i);
            next.add(new Unit("perches", x -> perch(x, h)));
        }
        next.add(new Unit("perches", TitanGroundSelfTest::perchSummary));
        pushFront(r, next);
        return true;
    }

    private static boolean perch(Run r, int[] h) {
        if (!ensure(r, h[0], h[1], 15)) {
            return false;
        }
        StormPerch.Peak p = StormPerch.find(r.level, h[0], h[1], 24, 96);
        if (p == null) {
            return true;
        }
        r.perchChecked++;
        double ground = StormPerch.surfaceAt(r.level, p.x(), p.z());
        double rise = p.y() - ground;
        if (rise > 0.05) {
            r.perchRises++;
        }
        r.perchMaxRise = Math.max(r.perchMaxRise, rise);
        for (int k = 0; k < 8; k++) {
            int bad = solidColumns(r.level, r.wing, p.x(), p.y(), p.z(), k * 45f);
            if (bad > 0) {
                r.failures.add("the thunder wing perch (" + (int) p.x() + ", " + (int) p.z() + ") at yaw " + k * 45 + " has " + bad + " body columns inside blocks");
            }
        }
        return true;
    }

    private static boolean perchSummary(Run r) {
        r.notes.add("thunder wing perches near " + r.highs.size() + " random points' 3 highest: " + r.perchChecked + " perches checked, " + r.perchRises
                + " lifted above the ground (max " + String.format("%.1f", r.perchMaxRise) + " blocks), 8 yaws each");
        return true;
    }

    // ---------------------------------------------------------------- 3b. 余燼の眠る場所

    // 地面に伏せる余燼 (腹は土に伏せてよい) の、地面より上の体 (底が口の前の地面より上の柱) が丘・木にめり込まない。
    // 候補の場所を一つずつ作業に分ける (区画が読み込めた候補だけ試す)
    private static boolean ember(Run r) {
        if (r.emberRng == null) {
            r.ember = TitanVolume.get("ember_titan");
            if (r.ember == null) {
                r.failures.add("the ember volume is missing");
                return true;
            }
            r.emberRng = new Random(RNG_SEED + 1);
        }
        if (r.emberSites >= 2 || r.emberTries >= 80) {
            r.notes.add("ember sleeping spots: " + r.emberSites * 4 + " player positions, old rule found " + r.emberOldFound + " (" + r.emberOldBad
                    + " clip hills/trees), new rule found " + r.emberNewFound);
            return true;
        }
        ServerLevel level = r.level;
        BlockPos spawn = level.getSharedSpawnPos();
        if (!r.emberPending) {
            r.emberX = spawn.getX() + r.emberRng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            r.emberZ = spawn.getZ() + r.emberRng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            r.emberPending = true;
        }
        int x = r.emberX;
        int z = r.emberZ;
        if (!ensureChunk(r, x >> 4, z >> 4)) {
            return false;
        }
        if (!jp.revati.skyabyss.titan.ember.EmberGround.isLand(level, x, z)) {
            return next(r);
        }
        // 先に小さく読み込んで、旧い決まりで眠る場所が見つかる所だけを、体の大きさまで広く読み込んで確かめる
        if (!ensure(r, x, z, 5)) {
            return false;
        }
        TitanGround.enabled = false;
        boolean anyOld = jp.revati.skyabyss.titan.ember.EmberGround.findSleepSpot(level, x, z) != null;
        TitanGround.enabled = true;
        if (!anyOld) {
            return next(r);
        }
        if (!ensure(r, x, z, 13)) {
            return false;
        }
        r.emberSites++;
        TitanVolume ember = r.ember;
        for (int k = 0; k < 4; k++) {
            double px = x + Math.cos(k * Math.PI / 2) * 20;
            double pz = z + Math.sin(k * Math.PI / 2) * 20;
            TitanGround.enabled = false;
            var old = jp.revati.skyabyss.titan.ember.EmberGround.findSleepSpot(level, px, pz);
            TitanGround.enabled = true;
            if (old != null) {
                r.emberOldFound++;
                if (aboveGroundSolid(level, ember, old.x(), old.groundY() - jp.revati.skyabyss.titan.ember.EmberTitanEntity.TONGUE_UP, old.z(), old.yaw()) > 0) {
                    r.emberOldBad++;
                }
            }
            var now = jp.revati.skyabyss.titan.ember.EmberGround.findSleepSpot(level, px, pz);
            if (now != null) {
                r.emberNewFound++;
                int bad = aboveGroundSolid(level, ember, now.x(), now.groundY() - jp.revati.skyabyss.titan.ember.EmberTitanEntity.TONGUE_UP, now.z(), now.yaw());
                if (bad > 0) {
                    r.failures.add("the ember sleeping spot (" + (int) now.x() + ", " + (int) now.z() + ") has " + bad + " above-ground body columns inside blocks");
                }
            }
        }
        return next(r);
    }

    // 次の候補へ (数えてから)
    private static boolean next(Run r) {
        r.emberPending = false;
        r.emberTries++;
        return false;
    }

    // 底が地面 (体の底 y + TONGUE_UP) より上の柱のうち、固いブロックが体の中にある数
    private static int aboveGroundSolid(ServerLevel level, TitanVolume volume, double x, double y, double z, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int bad = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (TitanVolume.Column c : volume.coreColumns()) {
            if (c.lo() < jp.revati.skyabyss.titan.ember.EmberTitanEntity.TONGUE_UP) {
                continue;
            }
            int wx = Mth.floor(x + fx * c.fwd() + fz * c.side());
            int wz = Mth.floor(z + fz * c.fwd() - fx * c.side());
            for (int by = Mth.floor(y + c.lo()); by <= Mth.floor(y + c.hi()); by++) {
                if (by >= level.getMinBuildHeight() && by < level.getMaxBuildHeight() && level.getBlockState(pos.set(wx, by, wz)).blocksMotion()) {
                    bad++;
                    break;
                }
            }
        }
        return bad;
    }

    // ---------------------------------------------------------------- 4. 乗り場

    private static boolean dock(Run r) {
        ServerLevel level = r.level;
        TitanVolume shell = r.shell;
        List<String> failures = r.failures;
        double surfaceLocal = TideShellEntity.REST_DRAFT;
        double fwd = -TideShellEntity.GATE_BACK;
        // 輪の縁の点と真ん中、水面から上 14 ブロックまで (光の柱の根もと〜輪の上) が体 (core) の外
        for (double rr = 0; rr <= TideShellEntity.DOCK_RADIUS + 0.01; rr += TideShellEntity.DOCK_RADIUS / 2) {
            for (int a = 0; a < (rr == 0 ? 1 : 12); a++) {
                double side = TideShellEntity.DOCK_SIDE + Math.cos(Math.PI / 6 * a) * rr;
                double f = fwd + Math.sin(Math.PI / 6 * a) * rr;
                for (double up = surfaceLocal - 1; up <= surfaceLocal + 14; up += 1.0) {
                    if (shell.inCore(side, up, f)) {
                        failures.add("the dock ring overlaps the shell body at side " + side + ", up " + up + ", back " + -f);
                        return true;
                    }
                }
            }
        }
        // 輪の中心の柱 (光の柱) は体の中を通らない: 水面から 46 ブロックまで
        for (double up = surfaceLocal; up <= surfaceLocal + 46; up += 0.5) {
            if (shell.inCore(TideShellEntity.DOCK_SIDE, up, fwd)) {
                failures.add("the light beam passes through the shell body at up " + up);
                return true;
            }
        }
        // 判定: 輪の真ん中と門の口は乗れる所、戻る所と体の中心は乗れない所 (向きを変えても)
        Entity probe = TideSetup.TIDE_SHELL.get().create(level);
        if (!(probe instanceof TideShellEntity t)) {
            failures.add("the tide shell could not be created for the dock check");
            return true;
        }
        for (float yaw : new float[] {0f, 37f, 90f, 200f, -135f}) {
            t.moveTo(0, level.getSeaLevel() - TideShellEntity.REST_DRAFT, 0, yaw, 0f);
            Vec3 dock = t.dockCenter();
            if (!mouth(t, new Vec3(dock.x, t.getY() + surfaceLocal, dock.z))) {
                failures.add("the dock ring center is not a boarding place at yaw " + yaw);
            }
            Vec3 exit = t.gateExit();
            if (mouth(t, new Vec3(exit.x, t.getY() + surfaceLocal, exit.z))) {
                failures.add("the exit point is inside the boarding place at yaw " + yaw + " (a returning player would cross again)");
            }
            if (mouth(t, t.position().add(0, 4, 0))) {
                failures.add("the body center is a boarding place at yaw " + yaw);
            }
        }
        probe.discard();
        return true;
    }

    private static boolean mouth(TideShellEntity t, Vec3 p) {
        Entity e = new net.minecraft.world.entity.item.ItemEntity(t.level(), p.x, p.y, p.z, net.minecraft.world.item.ItemStack.EMPTY);
        return t.inGateMouth(e);
    }

    // ---------------------------------------------------------------- 終わり

    private static boolean finish(Run r) {
        TitanGround.enabled = r.was;
        releaseAll(r);
        r.notes.forEach(n -> SkyAbyss.LOGGER.info("[selftest] titan ground: {}", n));
        r.phases.forEach((name, acc) -> SkyAbyss.LOGGER.info("[selftest] titan ground timing: {} {} ms over {} ticks", name, acc[0] / 1_000_000L, acc[1]));
        if (r.failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan ground PASSED (tide shell rest spots, cruise, thunder wing perches, dock outside the body)");
        } else {
            r.failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST titan ground FAILED: {}", f));
        }
        return true;
    }

    // 作業の段の区切りで、読み込みに固定した区画を外す (次の段では必要な分だけ読み直す)
    private static boolean release(Run r) {
        releaseAll(r);
        return true;
    }

    private static void releaseAll(Run r) {
        if (r.level != null) {
            for (long key : r.forced) {
                r.level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
            }
        }
        r.forced.clear();
        r.pending.clear();
    }

    // ---------------------------------------------------------------- 道具

    // 位置 (x, y, z)・向き yaw の体で、固いブロックが体の中にある柱の数 (ブロックを直接数える。測りの計算は使わない)
    static int solidColumns(ServerLevel level, TitanVolume volume, double x, double y, double z, float yaw) {
        return solidColumns(level, volume, x, y, z, yaw, Double.NEGATIVE_INFINITY);
    }

    // loFrom より下に底のある柱 (足・ひれ) は数えない版
    static int solidColumns(ServerLevel level, TitanVolume volume, double x, double y, double z, float yaw, double loFrom) {
        return solidColumns(level, volume, x, y, z, yaw, loFrom, Double.NEGATIVE_INFINITY);
    }

    static int solidColumns(ServerLevel level, TitanVolume volume, double x, double y, double z, float yaw, double loFrom, double hiFrom) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int bad = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (TitanVolume.Column c : volume.coreColumns()) {
            if (c.lo() < loFrom || c.hi() < hiFrom) {
                continue;
            }
            int wx = Mth.floor(x + fx * c.fwd() + fz * c.side());
            int wz = Mth.floor(z + fz * c.fwd() - fx * c.side());
            int from = Mth.floor(y + c.lo());
            int to = Mth.floor(y + c.hi());
            for (int by = from; by <= to; by++) {
                if (by < level.getMinBuildHeight() || by >= level.getMaxBuildHeight()) {
                    continue;
                }
                if (level.getBlockState(pos.set(wx, by, wz)).blocksMotion()) {
                    bad++;
                    break;
                }
            }
        }
        return bad;
    }

    // 巡航の道筋 (出だしから岸まで) の区画を読み込みに固定する (on) / 外す (!on)。道筋の 8 ブロックおきに、まわり 2 区画。
    // 四角く広く固定すると区画が多すぎて (千を超える)、メモリが足りなくなる
    private static void forceCorridor(Run r, double ax, double az, double bx, double bz, boolean on) {
        double len = Math.hypot(bx - ax, bz - az);
        int steps = Math.max(1, (int) (len / 8));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int cx = Mth.floor(ax + (bx - ax) * t) >> 4;
            int cz = Mth.floor(az + (bz - az) * t) >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    r.level.setChunkForced(cx + dx, cz + dz, on);
                }
            }
        }
    }

    // (x, z) のまわり radius チャンクを読み込みに固定して、すべて読み込めたか。同期の生成はしない (背景で生成が進み、後の tick で読み込み済みになる)
    private static boolean ensure(Run r, double x, double z, int radius) {
        int cx = Mth.floor(x) >> 4;
        int cz = Mth.floor(z) >> 4;
        boolean ready = true;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ready &= ensureChunk(r, cx + dx, cz + dz);
            }
        }
        return ready;
    }

    private static boolean ensureChunk(Run r, int cx, int cz) {
        if (r.level.getChunkSource().hasChunk(cx, cz)) {
            return true;
        }
        long key = ChunkPos.asLong(cx, cz);
        if (r.forced.contains(key)) {
            return false;
        }
        if (r.pending.size() >= MAX_PENDING) {
            r.pending.removeIf(k -> r.level.getChunkSource().hasChunk(ChunkPos.getX(k), ChunkPos.getZ(k)));
            if (r.pending.size() >= MAX_PENDING) {
                return false;
            }
        }
        r.forced.add(key);
        r.pending.add(key);
        r.level.setChunkForced(cx, cz, true);
        return false;
    }

    // 世界のスポーンのまわりの岸 (深さ 6〜14 の水が、陸の 40 ブロック以内にある所) を、決まった乱数で SITES か所。
    // 候補の区画をまず読み込んでから、一 tick に SHORE_PER_TICK 候補ずつ調べる (進みは r に持つ)。済んだら true
    private static final int SHORE_PER_TICK = 40;

    private static boolean findShores(Run r) {
        ServerLevel level = r.level;
        if (r.cands == null) {
            Random rng = new Random(RNG_SEED);
            BlockPos spawn = level.getSharedSpawnPos();
            List<Vec3> candidates = new ArrayList<>();
            for (int x = spawn.getX() - SCAN_RADIUS; x <= spawn.getX() + SCAN_RADIUS; x += SCAN_STEP) {
                for (int z = spawn.getZ() - SCAN_RADIUS; z <= spawn.getZ() + SCAN_RADIUS; z += SCAN_STEP) {
                    candidates.add(new Vec3(x + rng.nextInt(SCAN_STEP), 0, z + rng.nextInt(SCAN_STEP)));
                }
            }
            java.util.Collections.shuffle(candidates, rng);
            r.cands = candidates;
        }
        // 候補の区画を全部読み込む (読み込み中は待つ)
        boolean ready = true;
        for (Vec3 c : r.cands) {
            ready &= ensureChunk(r, Mth.floor(c.x) >> 4, Mth.floor(c.z) >> 4);
        }
        if (!ready) {
            return false;
        }
        for (int n = 0; n < SHORE_PER_TICK && r.candIdx < r.cands.size() && r.shores.size() < SITES; n++) {
            Vec3 c = r.cands.get(r.candIdx);
            if (!TideOcean.waterAtSurface(level, c.x, c.z)) {
                r.candIdx++;
                continue;
            }
            int d = TideOcean.depth(level, c.x, c.z);
            if (d < 6 || d > 14) {
                r.candIdx++;
                continue;
            }
            r.missing = false;
            Vec3 land = nearestLand(r, c.x, c.z, 40);
            if (r.missing) {
                // 陸を探す区画がまだ読み込み中: この候補は次の tick にもう一度
                return false;
            }
            if (land != null) {
                r.shores.add(new Shore(new Vec3(c.x, level.getSeaLevel(), c.z), land));
            }
            r.candIdx++;
        }
        return r.candIdx >= r.cands.size() || r.shores.size() >= SITES;
    }

    private static Vec3 nearestLand(Run r, double x, double z, int range) {
        ServerLevel level = r.level;
        for (int s = 4; s <= range; s += 4) {
            for (int dir = 0; dir < 16; dir++) {
                double px = x + Math.cos(Math.PI / 8 * dir) * s;
                double pz = z + Math.sin(Math.PI / 8 * dir) * s;
                if (!ensureChunk(r, Mth.floor(px) >> 4, Mth.floor(pz) >> 4)) {
                    r.missing = true;
                    continue;
                }
                if (TideOcean.land(level, px, pz)) {
                    return new Vec3(px, level.getSeaLevel(), pz);
                }
            }
        }
        return null;
    }
}
