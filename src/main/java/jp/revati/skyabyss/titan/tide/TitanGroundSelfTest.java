package jp.revati.skyabyss.titan.tide;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanGround;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import jp.revati.skyabyss.titan.storm.StormPerch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 巨神が地形にめり込まない決まり (TitanGround) の自己テスト (SKYABYSS_SELFTEST。単独は SKYABYSS_SELFTEST_ONLY="titan ground")。
// どれも「測りの計算」ではなく、体積の柱ごとにブロックを数えて (固いブロックが体の中にあるか) 確かめる。
//  1. 潮殻の休む場所: 世界のいろいろな岸 (丘・木・浅瀬) で、旧い決まり (測りを切る) ではめり込む場所が選ばれ、
//     新しい決まりでは選ばれた場所のどこにもめり込まない (見つからなければ null)
//  2. 潮殻の巡航: 沖から岸へ向かって歩く間、毎 tick めり込まず、持ち上がりはなめらか (1 tick に 0.4 ブロック以下)
//  3. 雷翼の止まり木: 高い峰のまわりで、止まり木の高さなら向きがどちらでも体がめり込まない
//  4. 潮殻の乗り場: 標と輪は体 (core) の外。門の口と輪には入れ、戻る所には入れない
public final class TitanGroundSelfTest {
    private static final long RNG_SEED = 4242L;
    // 岸の候補を探す範囲 (世界のスポーンから)。探す間隔 (ブロック) と、試す岸の数
    private static final int SCAN_RADIUS = 700;
    private static final int SCAN_STEP = 48;
    private static final int SITES = 4;
    private static final int PLAYERS_PER_SHORE = 3;
    private static final int PRELOAD = 17;

    private TitanGroundSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean was = TitanGround.enabled;
        try {
            // 前の段 (巨神の眠り) の「眠っている」記録を消す (眠った潮殻は沈むだけで、測りが効かない)
            jp.revati.skyabyss.titan.end.TitanEndSelfTest.resetAll(server);
            TitanGround.enabled = true;
            run(level, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan ground crashed", e);
            failures.add("crashed: " + e);
        } finally {
            TitanGround.enabled = was;
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("[selftest] titan ground: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan ground PASSED (tide shell rest spots, cruise, thunder wing perches, dock outside the body)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST titan ground FAILED: {}", f));
        }
    }

    // 岸の場所: 水際の (水の点 water, 陸の点 land)
    private record Shore(Vec3 water, Vec3 land) {
    }

    private static void run(ServerLevel level, List<String> failures, List<String> notes) {
        TitanVolume shell = TitanVolume.get("tide_shell");
        TitanVolume wing = TitanVolume.get("thunder_wing");
        if (shell == null || wing == null) {
            failures.add("a titan volume is missing");
            return;
        }
        List<Shore> shores = findShores(level);
        notes.add("shores found: " + shores.size());
        if (shores.isEmpty()) {
            notes.add("no coast within " + SCAN_RADIUS + " of the spawn: rest spot and cruise checks skipped");
        } else {
            checkRestSpots(level, shell, shores, failures, notes);
            checkCruise(level, shell, shores, failures, notes);
        }
        checkPerches(level, wing, failures, notes);
        checkEmber(level, failures, notes);
        checkDock(level, shell, failures);
    }

    // ---------------------------------------------------------------- 1. 休む場所

    private static void checkRestSpots(ServerLevel level, TitanVolume shell, List<Shore> shores, List<String> failures, List<String> notes) {
        double restY = level.getSeaLevel() - TideShellEntity.REST_DRAFT;
        int tried = 0;
        int oldFound = 0;
        int oldBad = 0;
        int newFound = 0;
        int newRaised = 0;
        double maxRaise = 0;
        double minSunk = Double.POSITIVE_INFINITY;
        double worstOld = 0;
        List<Double> looseRaise = new ArrayList<>();
        for (Shore s : shores) {
            preload(level, s.land().x, s.land().z, PRELOAD);
            // 同じ岸でも、人の立つ場所を少しずつ変えて試す
            for (int k = 0; k < PLAYERS_PER_SHORE; k++) {
                double ang = k * Math.PI * 2 / PLAYERS_PER_SHORE;
                double px = s.land().x + Math.cos(ang) * 16;
                double pz = s.land().z + Math.sin(ang) * 16;
                tried++;
                TitanGround.enabled = false;
                Vec3 old = TideOcean.findRestSpot(level, px, pz);
                TitanGround.enabled = true;
                if (old != null) {
                    oldFound++;
                    int bad = solidColumns(level, shell, old.x, restY, old.z, (float) old.y);
                    if (bad > 0) {
                        oldBad++;
                        worstOld = Math.max(worstOld, bad);
                        if (oldBad <= 6) {
                            notes.add(String.format("  old rule clips: shell at %d %d yaw %.0f (%d body columns in blocks), player at %d %d", (int) old.x, (int) old.z, old.y, bad,
                                    (int) px, (int) pz));
                        }
                    }
                }
                Vec3 loose = TideOcean.findRestSpot(level, px, pz, 40.0);
                if (loose != null) {
                    looseRaise.add(Math.max(0, TideOcean.restRaise(level, loose.x, loose.z, (float) loose.y)));
                }
                Vec3 now = TideOcean.findRestSpot(level, px, pz);
                if (now != null) {
                    newFound++;
                    double raise = Math.max(0, TideOcean.restRaise(level, now.x, now.z, (float) now.y));
                    if (raise > 0.05) {
                        newRaised++;
                    }
                    maxRaise = Math.max(maxRaise, raise);
                    if (newFound <= 4) {
                        notes.add(String.format("  new rule spot: shell at %d %d yaw %.0f raise %.1f for the player at %d %d", (int) now.x, (int) now.z, now.y, raise, (int) px, (int) pz));
                    }
                    // 体がその高さにあるときの、体の中の固いブロック (ブロックを直接数える)
                    // 休むときは足・ひれを除いた腹を REST_BURY だけ海底へ沈めてよい。その分だけ持ち上げた体が地形に触れないことを見る
                    int bad = solidColumns(level, shell, now.x, restY + raise + TideShellEntity.REST_BURY, now.z, (float) now.y, TideShellEntity.REST_LEG_SKIP, TideShellEntity.REST_SKIRT_HI);
                    if (bad > 0) {
                        failures.add("the tide shell rest spot (" + (int) now.x + ", " + (int) now.z + ") has " + bad + " body columns inside blocks");
                    }
                    // 体の底 (甲羅の腹。足・ひれと低い裾を除く) が、水面より 6 ブロック以上下にあること = 腹が水に浸かって、甲羅が海に浮いて見えない
                    double belly = bellyBottom(shell);
                    double hullBottom = restY + raise + belly;
                    double sunk = level.getSeaLevel() - hullBottom;
                    minSunk = Math.min(minSunk, sunk);
                    if (sunk < 6.0 - 0.05) {
                        failures.add("the tide shell hull floats: its belly bottom is only " + sunk + " blocks under the water at (" + (int) now.x + ", " + (int) now.z + ") raise " + raise);
                    }
                    if (raise > TideOcean.MAX_RAISE + 0.05) {
                        failures.add("a chosen rest spot needs a lift above the limit: " + raise);
                    }
                }
            }
        }
        notes.add("hull belly under the water at the rest spots: at least " + String.format("%.1f", minSunk) + " blocks");
        notes.add("rest spots: " + tried + " player positions, old rule found " + oldFound + " (" + oldBad + " clip into terrain, worst " + (int) worstOld
                + " columns), new rule found " + newFound + " (" + newRaised + " need the shell lifted, max " + String.format("%.1f", maxRaise) + " blocks), clipping 0 required");
        notes.add("lowest lift needed over the " + tried + " positions with the limit raised to 40: " + looseRaise.stream().map(v -> String.format("%.1f", v)).toList());
        if (newFound == 0 && oldFound > 0) {
            failures.add("the new rule never finds a rest spot where the old one did (" + oldFound + ")");
        }
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

    private static void checkCruise(ServerLevel level, TitanVolume shell, List<Shore> shores, List<String> failures, List<String> notes) {
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
                preload(level, x, z, 6);
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
        for (int r = 0; r < 1; r++) {
            preload(level, (sx + s.land().x) / 2, (sz + s.land().z) / 2, PRELOAD + 4);
            TideShellEntity titan = TideSetup.TIDE_SHELL.get().create(level);
            if (titan == null) {
                failures.add("the tide shell could not be created");
                return;
            }
            // 世界へは入れない (一体だけの決まりに触れない)。tick は手で回す
            float yaw = (float) (Mth.atan2(s.land().z - sz, s.land().x - sx) * Mth.RAD_TO_DEG) - 90f;
            // 出だしがすでに地形にめり込んでいては比べられないので、出だしの高さは下限と標準の巡航の高さの高い方
            double need0 = TitanGround.requiredY(level, TitanGround.Surface.FLOOR, shell, sx, sz, yaw);
            titan.moveTo(sx, Math.max(level.getSeaLevel() - TideShellEntity.CRUISE_DRAFT_MAX, Double.isFinite(need0) ? need0 : Double.NEGATIVE_INFINITY), sz, yaw, 0f);
            titan.testCruiseTo(s.land().x, s.land().z);
            double worstOverlap = Double.NEGATIVE_INFINITY;
            double maxStep = 0;
            double minMargin = Double.MAX_VALUE;
            double lastY = titan.getY();
            double endGap = 0;
            int ticks = 3600;
            for (int t = 0; t < ticks; t++) {
                titan.tick();
                double overlap = TitanGround.overlap(level, TitanGround.Surface.FLOOR, shell, titan.getX(), titan.getY(), titan.getZ(), titan.getYRot());
                worstOverlap = Math.max(worstOverlap, overlap);
                minMargin = Math.min(minMargin, -overlap);
                if (t >= 10) {
                    maxStep = Math.max(maxStep, Math.abs(titan.getY() - lastY));
                }
                lastY = titan.getY();
            }
            endGap = Math.hypot(titan.getX() - s.land().x, titan.getZ() - s.land().z);
            if (worstOverlap > 1e-6) {
                failures.add("the cruising tide shell clipped into terrain by " + worstOverlap + " blocks on shore " + (int) s.land().x + ", " + (int) s.land().z);
            }
            if (maxStep > 1.05) {
                failures.add("the tide shell jerked vertically by " + maxStep + " blocks in one tick");
            }
            notes.add("cruise from " + (int) sx + ", " + (int) sz + (found ? " (open water start)" : " (rough start)") + " toward the shore at " + (int) s.land().x + ", " + (int) s.land().z + ": worst overlap " + String.format("%.2f", worstOverlap) + ", min clearance "
                    + String.format("%.2f", minMargin) + ", max step " + String.format("%.2f", maxStep) + ", ended " + (int) endGap + " from the shore at y " + String.format("%.1f", titan.getY()));
            titan.discard();
        }
    }

    // ---------------------------------------------------------------- 3. 雷翼の止まり木

    private static void checkPerches(ServerLevel level, TitanVolume wing, List<String> failures, List<String> notes) {
        // 高い所のまわり: 候補の点をいくつか高さで選ぶ
        Random rng = new Random(RNG_SEED);
        BlockPos spawn = level.getSharedSpawnPos();
        List<int[]> highs = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            int x = spawn.getX() + rng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            int z = spawn.getZ() + rng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            level.getChunk(x >> 4, z >> 4);
            highs.add(new int[] {x, z, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)});
        }
        highs.sort((a, b) -> Integer.compare(b[2], a[2]));
        int checked = 0;
        int rises = 0;
        double maxRise = 0;
        for (int i = 0; i < Math.min(2, highs.size()); i++) {
            int[] h = highs.get(i);
            preload(level, h[0], h[1], 15);
            StormPerch.Peak p = StormPerch.find(level, h[0], h[1], 24, 96);
            if (p == null) {
                continue;
            }
            checked++;
            double ground = StormPerch.surfaceAt(level, p.x(), p.z());
            double rise = p.y() - ground;
            if (rise > 0.05) {
                rises++;
            }
            maxRise = Math.max(maxRise, rise);
            for (int k = 0; k < 8; k++) {
                int bad = solidColumns(level, wing, p.x(), p.y(), p.z(), k * 45f);
                if (bad > 0) {
                    failures.add("the thunder wing perch (" + (int) p.x() + ", " + (int) p.z() + ") at yaw " + k * 45 + " has " + bad + " body columns inside blocks");
                }
            }
        }
        notes.add("thunder wing perches near " + highs.size() + " random points' 3 highest: " + checked + " perches checked, " + rises + " lifted above the ground (max "
                + String.format("%.1f", maxRise) + " blocks), 8 yaws each");
    }

    // ---------------------------------------------------------------- 3b. 余燼の眠る場所

    // 地面に伏せる余燼 (腹は土に伏せてよい) の、地面より上の体 (底が口の前の地面より上の柱) が丘・木にめり込まない
    private static void checkEmber(ServerLevel level, List<String> failures, List<String> notes) {
        TitanVolume ember = TitanVolume.get("ember_titan");
        if (ember == null) {
            failures.add("the ember volume is missing");
            return;
        }
        Random rng = new Random(RNG_SEED + 1);
        BlockPos spawn = level.getSharedSpawnPos();
        int sites = 0;
        int oldFound = 0;
        int oldBad = 0;
        int newFound = 0;
        for (int tries = 0; tries < 80 && sites < 2; tries++) {
            int x = spawn.getX() + rng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            int z = spawn.getZ() + rng.nextInt(2 * SCAN_RADIUS) - SCAN_RADIUS;
            level.getChunk(x >> 4, z >> 4);
            if (!jp.revati.skyabyss.titan.ember.EmberGround.isLand(level, x, z)) {
                continue;
            }
            // 先に小さく読み込んで、旧い決まりで眠る場所が見つかる所だけを、体の大きさまで広く読み込んで確かめる
            preload(level, x, z, 5);
            TitanGround.enabled = false;
            boolean anyOld = jp.revati.skyabyss.titan.ember.EmberGround.findSleepSpot(level, x, z) != null;
            TitanGround.enabled = true;
            if (!anyOld) {
                continue;
            }
            sites++;
            preload(level, x, z, 13);
            for (int k = 0; k < 4; k++) {
                double px = x + Math.cos(k * Math.PI / 2) * 20;
                double pz = z + Math.sin(k * Math.PI / 2) * 20;
                TitanGround.enabled = false;
                var old = jp.revati.skyabyss.titan.ember.EmberGround.findSleepSpot(level, px, pz);
                TitanGround.enabled = true;
                if (old != null) {
                    oldFound++;
                    if (aboveGroundSolid(level, ember, old.x(), old.groundY() - jp.revati.skyabyss.titan.ember.EmberTitanEntity.TONGUE_UP, old.z(), old.yaw()) > 0) {
                        oldBad++;
                    }
                }
                var now = jp.revati.skyabyss.titan.ember.EmberGround.findSleepSpot(level, px, pz);
                if (now != null) {
                    newFound++;
                    int bad = aboveGroundSolid(level, ember, now.x(), now.groundY() - jp.revati.skyabyss.titan.ember.EmberTitanEntity.TONGUE_UP, now.z(), now.yaw());
                    if (bad > 0) {
                        failures.add("the ember sleeping spot (" + (int) now.x() + ", " + (int) now.z() + ") has " + bad + " above-ground body columns inside blocks");
                    }
                }
            }
        }
        notes.add("ember sleeping spots: " + sites * 4 + " player positions, old rule found " + oldFound + " (" + oldBad + " clip hills/trees), new rule found " + newFound);
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

    private static void checkDock(ServerLevel level, TitanVolume shell, List<String> failures) {
        double surfaceLocal = TideShellEntity.REST_DRAFT;
        double fwd = -TideShellEntity.GATE_BACK;
        // 輪の縁の点と真ん中、水面から上 14 ブロックまで (光の柱の根もと〜輪の上) が体 (core) の外
        for (double r = 0; r <= TideShellEntity.DOCK_RADIUS + 0.01; r += TideShellEntity.DOCK_RADIUS / 2) {
            for (int a = 0; a < (r == 0 ? 1 : 12); a++) {
                double side = TideShellEntity.DOCK_SIDE + Math.cos(Math.PI / 6 * a) * r;
                double f = fwd + Math.sin(Math.PI / 6 * a) * r;
                for (double up = surfaceLocal - 1; up <= surfaceLocal + 14; up += 1.0) {
                    if (shell.inCore(side, up, f)) {
                        failures.add("the dock ring overlaps the shell body at side " + side + ", up " + up + ", back " + -f);
                        return;
                    }
                }
            }
        }
        // 輪の中心の柱 (光の柱) は体の中を通らない: 水面から 46 ブロックまで
        for (double up = surfaceLocal; up <= surfaceLocal + 46; up += 0.5) {
            if (shell.inCore(TideShellEntity.DOCK_SIDE, up, fwd)) {
                failures.add("the light beam passes through the shell body at up " + up);
                return;
            }
        }
        // 判定: 輪の真ん中と門の口は乗れる所、戻る所と体の中心は乗れない所 (向きを変えても)
        Entity probe = TideSetup.TIDE_SHELL.get().create(level);
        if (!(probe instanceof TideShellEntity t)) {
            failures.add("the tide shell could not be created for the dock check");
            return;
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
    }

    private static boolean mouth(TideShellEntity t, Vec3 p) {
        Entity e = new net.minecraft.world.entity.item.ItemEntity(t.level(), p.x, p.y, p.z, net.minecraft.world.item.ItemStack.EMPTY);
        return t.inGateMouth(e);
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

    // (x, z) のまわり radius チャンクを作る (同期)
    private static void preload(ServerLevel level, double x, double z, int radius) {
        int cx = Mth.floor(x) >> 4;
        int cz = Mth.floor(z) >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.getChunk(cx + dx, cz + dz);
            }
        }
    }

    // 世界のスポーンのまわりの岸 (深さ 6〜14 の水が、陸の 40 ブロック以内にある所) を、決まった乱数で SITES か所。
    // 水の点と、そこから見ていちばん近い陸の点
    private static List<Shore> findShores(ServerLevel level) {
        Random rng = new Random(RNG_SEED);
        BlockPos spawn = level.getSharedSpawnPos();
        List<Shore> found = new ArrayList<>();
        List<Vec3> candidates = new ArrayList<>();
        for (int x = spawn.getX() - SCAN_RADIUS; x <= spawn.getX() + SCAN_RADIUS; x += SCAN_STEP) {
            for (int z = spawn.getZ() - SCAN_RADIUS; z <= spawn.getZ() + SCAN_RADIUS; z += SCAN_STEP) {
                candidates.add(new Vec3(x + rng.nextInt(SCAN_STEP), 0, z + rng.nextInt(SCAN_STEP)));
            }
        }
        java.util.Collections.shuffle(candidates, rng);
        for (Vec3 c : candidates) {
            if (found.size() >= SITES) {
                break;
            }
            // 遠い候補は、広く作らず一本のチャンクだけ見る
            level.getChunk(Mth.floor(c.x) >> 4, Mth.floor(c.z) >> 4);
            if (!TideOcean.waterAtSurface(level, c.x, c.z)) {
                continue;
            }
            int d = TideOcean.depth(level, c.x, c.z);
            if (d < 6 || d > 14) {
                continue;
            }
            Vec3 land = nearestLand(level, c.x, c.z, 40);
            if (land != null) {
                found.add(new Shore(new Vec3(c.x, level.getSeaLevel(), c.z), land));
            }
        }
        return found;
    }

    private static Vec3 nearestLand(ServerLevel level, double x, double z, int range) {
        for (int s = 4; s <= range; s += 4) {
            for (int dir = 0; dir < 16; dir++) {
                double px = x + Math.cos(Math.PI / 8 * dir) * s;
                double pz = z + Math.sin(Math.PI / 8 * dir) * s;
                level.getChunk(Mth.floor(px) >> 4, Mth.floor(pz) >> 4);
                if (TideOcean.land(level, px, pz)) {
                    return new Vec3(px, level.getSeaLevel(), pz);
                }
            }
        }
        return null;
    }
}
