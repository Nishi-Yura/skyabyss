package jp.revati.skyabyss.titan.young;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberGround;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.util.ChunkWait;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 呼びの探し (笛・呼び貝・呼び鈴) の時間を測る (SKYABYSS_SELFTEST_ONLY="call perf")。結果は CALLPERF の行 (docs/review/call_perf_2026-10-24.md)。
// 読み込まれた地形 (spawn のまわり) と、まだ読み込まれていない地形 (遠く) で、笛が使う探し (YoungCarriers.spotFor) と
// 成体の探し (TideOcean・EmberGround) と、背中から戻る探し (standableShore・landingAt) の時間を測る。測るだけで、結果は変えない。
public final class CallPerfSelfTest {
    private static final List<String> F = new ArrayList<>();

    private CallPerfSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("call perf", CallPerfSelfTest::run), SelfTestRunner.Step.until("call perf wait", CallPerfSelfTest::waitTick));
    }

    // 待つ探し (ChunkWait.later): まだ読み込まれていない地形の岸を、区画が読み込まれてから探す。
    // 待った結果が、いまの同期の探しと同じか。待ったあとの時間 (同期で読むのは読み込み済みなので一瞬のはず) も出す
    private static boolean waitStarted;
    private static boolean waitDone;
    private static long waitGameStart;
    private static Vec3 waitResult;
    private static double waitMs;
    private static double waitX;
    private static double waitZ;

    private static boolean waitTick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!waitStarted) {
            waitStarted = true;
            BlockPos spawn = level.getSharedSpawnPos();
            waitX = spawn.getX() - 1400.5;
            waitZ = spawn.getZ() - 1400.5;
            waitGameStart = level.getGameTime();
            ChunkWait.later(level, waitX, waitZ, ChunkWait.SHORE_REACH, () -> {
                long t0 = System.nanoTime();
                waitResult = TideOcean.standableShore(level, waitX, waitZ);
                waitMs = (System.nanoTime() - t0) / 1e6;
                waitDone = true;
            });
        }
        if (!waitDone) {
            if (level.getGameTime() - waitGameStart < 1200) {
                return false;
            }
            F.add("delayed shore search never ran");
        }
        Vec3 now = TideOcean.standableShore(level, waitX, waitZ);
        boolean same = java.util.Objects.equals(waitResult, now);
        SkyAbyss.LOGGER.info(String.format(Locale.ROOT, "CALLPERF wait | ticks %d | delayed search run took %.2f ms | same as now: %s | pending %d",
                level.getGameTime() - waitGameStart, waitMs, same, ChunkWait.pending()));
        if (!same) {
            F.add("delayed shore search differs from the same search after loading");
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST call perf wait PASSED");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST call perf wait FAILED: {}", m));
        }
        F.clear();
        return true;
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        Map<String, List<Double>> loaded = new TreeMap<>();
        Map<String, List<Double>> fresh = new TreeMap<>();
        YoungTestPlayer player = new YoungTestPlayer(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-0000000d0f00"), "[callperf]"));
        // 読み込まれた地形: spawn と、そのまわりの 120 ブロックの 8 点
        List<double[]> near = new ArrayList<>();
        near.add(new double[] {spawn.getX() + 0.5, spawn.getZ() + 0.5});
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            near.add(new double[] {spawn.getX() + 0.5 + Math.cos(a) * 120, spawn.getZ() + 0.5 + Math.sin(a) * 120});
        }
        int loadedChunks = 0;
        for (double[] q : near) {
            if (hasChunk(level, q[0], q[1])) {
                loadedChunks++;
            }
        }
        SkyAbyss.LOGGER.info("CALLPERF loaded points: {} of {} are in loaded chunks", loadedChunks, near.size());
        // 二回ずつ (一回目は冷えた状態、二回目は温まった状態)
        for (int pass = 0; pass < 2; pass++) {
            for (double[] q : near) {
                measureNear(level, player, q[0], q[1], loaded);
            }
        }
        // まだ読み込まれていない地形: spawn から遠い 3 点。まず読み込まない探し、次に背中から戻る探し (同期の読み込みで区画を生成する)
        double[][] far = {{spawn.getX() + 1400.5, spawn.getZ() + 1400.5}, {spawn.getX() - 1400.5, spawn.getZ() + 1400.5}, {spawn.getX() + 1400.5, spawn.getZ() - 1400.5}};
        for (double[] q : far) {
            SkyAbyss.LOGGER.info("CALLPERF fresh point ({}, {}) loaded before measuring: {}", q[0], q[1], hasChunk(level, q[0], q[1]));
            measureFar(level, player, q[0], q[1], fresh);
        }
        report("loaded", loaded);
        report("fresh", fresh);
        player.discard();
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST call perf PASSED");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST call perf FAILED: {}", m));
        }
        F.clear();
    }

    private static boolean hasChunk(ServerLevel level, double x, double z) {
        return level.getChunkSource().hasChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    private static void place(ServerLevel level, YoungTestPlayer player, double x, double z) {
        double y = hasChunk(level, x, z) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z)) : 64;
        player.setPos(x, y, z);
    }

    // 読み込まれた地形: 笛の探し (四体)・成体の探し・戻る探し
    private static void measureNear(ServerLevel level, YoungTestPlayer player, double x, double z, Map<String, List<Double>> out) {
        place(level, player, x, z);
        measureItemAndAdult(level, player, x, z, out);
        timed(out, "tide shore", () -> TideOcean.standableShore(level, x, z));
        timed(out, "ember landing", () -> EmberGround.landingAt(level, x, z, 16));
    }

    // まだ読み込まれていない地形: 読み込まない探し (笛・成体) と、戻る探し (区画を生成する)
    private static void measureFar(ServerLevel level, YoungTestPlayer player, double x, double z, Map<String, List<Double>> out) {
        place(level, player, x, z);
        measureItemAndAdult(level, player, x, z, out);
        timed(out, "tide shore (generates)", () -> TideOcean.standableShore(level, x, z));
        timed(out, "ember landing (generates)", () -> EmberGround.landingAt(level, x, z, 16));
    }

    private static void measureItemAndAdult(ServerLevel level, YoungTestPlayer player, double x, double z, Map<String, List<Double>> out) {
        for (YoungKind k : YoungKind.values()) {
            timed(out, "item " + k.name().toLowerCase(Locale.ROOT), () -> YoungCarriers.spotFor(player, k));
        }
        timed(out, "tide rest (adult)", () -> TideOcean.findRestSpot(level, x, z));
        timed(out, "tide call (adult)", () -> TideOcean.findCallSpot(level, x, z));
        timed(out, "ember sleep (adult)", () -> EmberGround.findSleepSpot(level, x, z));
        timed(out, "ember call (adult)", () -> EmberGround.findCallSpot(level, x, z));
    }

    private static void timed(Map<String, List<Double>> out, String label, Supplier<Object> call) {
        long t0 = System.nanoTime();
        try {
            call.get();
        } catch (RuntimeException e) {
            F.add(label + " threw " + e);
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        out.computeIfAbsent(label, k -> new ArrayList<>()).add(ms);
        SkyAbyss.LOGGER.info(String.format(Locale.ROOT, "CALLPERF one | %s | %.2f ms", label, ms));
    }

    private static void report(String scenario, Map<String, List<Double>> m) {
        for (var e : m.entrySet()) {
            double max = 0;
            double sum = 0;
            for (double v : e.getValue()) {
                max = Math.max(max, v);
                sum += v;
            }
            SkyAbyss.LOGGER.info(String.format(Locale.ROOT, "CALLPERF %s | %s | n=%d max=%.2f ms avg=%.2f ms", scenario, e.getKey(), e.getValue().size(), max, sum / e.getValue().size()));
        }
    }
}
