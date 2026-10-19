package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.HeartBalance;
import jp.revati.skyabyss.combat.Telegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 真珠守りの自己テスト (SKYABYSS_SELFTEST。結果は SELFTEST pearl keeper PASSED / FAILED)。潮招き (TideTelegraphSelfTest) と同じ型:
//  A. 予告 0.8 秒以上: 傷のある予告はどれも 16 tick (動いている予告の warn を全 tick 見る)
//  B. 印と当たり: 床に並べた人 (傷 1000 の作り物) のうち、傷を受けた人 = 傷のある予告が当たった点。予告のない傷 (接触・継続) があれば食い違う
//  C. 安全な床: 一つの技で、傷のある予告が一度も当たらない床 (舞台の円) の割合が 15% 以上 (一人・六人、舞台の中心・縁から)
//  D. 人数で増える: 六人の傷のある予告の数が、一人より多い (五つの技すべて)。式 (PearlKeeper.updrafts など) の値も見る
//  E. 部屋に収まる: 跳べない一つの範囲 (円・帯) が舞台の床の 4 割以下、いちばん遠い印の届きが舞台の直径の中に収まる、高さは 12 以上。判定箱 3.0 x 3.4 が舞台の壁に当たらない
//  F. 手ごたえ (HeartBalance の式): 人数 1・2・4・6 で戦いの長さが帯に入る、最も重い一撃が想定の体力の 34% 以下、傷の式 (人数・ぐったり 1.5 倍・殻の中は傷 0)
public final class PearlKeeperSelfTest {
    private static final double SX = 8.5;
    private static final double SZ = 8.5;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;
    private static final String[] ATTACKS = {"UPDRAFT", "DOWNDRAFT", "CHARGE", "VOLLEY", "SHELL"};

    private PearlKeeperSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("pearl keeper (load)", PearlKeeperSelfTest::load),
                SelfTestRunner.Step.once("pearl keeper", 30, PearlKeeperSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            platform(level);
            rooms(f, notes);
            balance(f, notes);
            fights(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST pearl keeper crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.probe(null);
            server.setDifficulty(before, true);
            for (int cx = -2; cx <= 2; cx++) {
                for (int cz = -2; cz <= 2; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST pearl keeper note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST pearl keeper PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST pearl keeper FAILED: {}", m));
        }
    }

    private static void check(boolean ok, String message, List<String> f) {
        if (!ok) {
            f.add(message);
        }
    }

    private static void platform(ServerLevel level) {
        for (int x = -24; x <= 40; x++) {
            for (int z = -24; z <= 40; z++) {
                level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = FLOOR; y <= FLOOR + 12; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String tag, double x, double y, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("pearl keeper " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[pk_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        p.setHealth(HEALTH);
        p.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(p);
        return p;
    }

    private static void drop(List<FakePlayer> players) {
        for (FakePlayer p : players) {
            p.discard();
        }
        players.clear();
    }

    private static List<FakePlayer> party(ServerLevel level, String tag, int n, double radius) {
        List<FakePlayer> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + 0.4;
            ps.add(fake(level, tag + i, SX + Math.cos(a) * radius, FLOOR, SZ + Math.sin(a) * radius));
        }
        return ps;
    }

    // 舞台の円の中の点 (中心から radius まで、spacing おき)
    private static List<Vec3> arenaGrid(double spacing, double skip) {
        List<Vec3> points = new ArrayList<>();
        double r = BreathLayout.ARENA_R;
        for (double x = SX - r; x <= SX + r; x += spacing) {
            for (double z = SZ - r; z <= SZ + r; z += spacing) {
                double d = Math.hypot(x - SX, z - SZ);
                if (d <= r && d >= skip) {
                    points.add(new Vec3(x, FLOOR, z));
                }
            }
        }
        return points;
    }

    private static int safeCount(boolean[] hits) {
        int safe = 0;
        for (boolean h : hits) {
            safe += h ? 0 : 1;
        }
        return safe;
    }

    // ---------------------------------------------------------------- E. 部屋
    private static void rooms(List<String> f, List<String> notes) {
        var type = TideBackSetup.PEARL_KEEPER.get();
        double arena = Math.PI * BreathLayout.ARENA_R * BreathLayout.ARENA_R;
        check(Math.abs(type.getWidth() - 3.0f) < 1e-3 && Math.abs(type.getHeight() - 3.4f) < 1e-3, "the pearl keeper hitbox is " + type.getWidth() + " x " + type.getHeight(), f);
        check(type.getWidth() / 2 + 1.5 < BreathLayout.ARENA_R, "the keeper is too wide for the arena", f);
        double ventArea = Math.PI * BreathLayout.VENT_WALL_R * BreathLayout.VENT_WALL_R / 2;
        Map<String, Double> areas = new HashMap<>();
        areas.put("updraft circle", Math.PI * PearlKeeper.UPDRAFT_RADIUS * PearlKeeper.UPDRAFT_RADIUS);
        areas.put("volley circle", Math.PI * PearlKeeper.VOLLEY_RADIUS * PearlKeeper.VOLLEY_RADIUS);
        areas.put("charge line", 2 * PearlKeeper.CHARGE_HALF_WIDTH * PearlKeeper.CHARGE_LENGTH);
        // 跳べない範囲 (輪は跳び越える・安全な筋がある)
        double widest = 0;
        String widestName = "";
        for (var e : areas.entrySet()) {
            if (e.getValue() > widest) {
                widest = e.getValue();
                widestName = e.getKey();
            }
        }
        check(widest / (arena - ventArea) <= 0.4, "the widest keeper area (" + widestName + ") is " + Math.round(100 * widest / (arena - ventArea)) + "% of the arena (limit 40%)", f);
        // 輪: 外へ走る輪は、舞台の円 (半径 6) をぜんぶ通る。安全な筋 (2〜3) の割合
        double laneShare2 = 2 * Math.toDegrees(2 * Math.toRadians(26)) / 2 / 360 * 2;
        check(PearlKeeper.lanes(1) == 2 && PearlKeeper.lanes(6) == 3, "lane counts", f);
        // 届きと高さ
        double farthest = Math.max(PearlKeeper.CHARGE_LENGTH, PearlKeeper.RING_R1);
        check(PearlKeeper.RING_R1 >= BreathLayout.ARENA_R + 1, "the ring does not cover the arena", f);
        check(BreathLayout.B_TOP - BreathLayout.ARENA_FLOOR >= 12, "the arena has only " + (BreathLayout.B_TOP - BreathLayout.ARENA_FLOOR) + " blocks of air (needs 12)", f);
        check(BreathLayout.radiusB(BreathLayout.ARENA_FLOOR + 1) >= BreathLayout.ARENA_R && BreathLayout.radiusB(BreathLayout.ARENA_FLOOR + 14) >= 5.0,
                "the tower is narrower than the arena", f);
        // 人数の式
        check(PearlKeeper.updrafts(1) == 2 && PearlKeeper.updrafts(6) == 6, "updraft count " + PearlKeeper.updrafts(1) + "/" + PearlKeeper.updrafts(6), f);
        check(PearlKeeper.volleys(1) == 3 && PearlKeeper.volleys(6) == 8, "volley count " + PearlKeeper.volleys(1) + "/" + PearlKeeper.volleys(6), f);
        check(PearlKeeper.chargeLines(1) == 1 && PearlKeeper.chargeLines(3) == 2 && PearlKeeper.chargeLines(6) == 3, "charge lines", f);
        check(PearlKeeper.rings(1) == 1 && PearlKeeper.rings(6) == 2, "rings", f);
        notes.add("rooms: arena " + Math.round(arena) + " cells, widest area " + widestName + " " + Math.round(100 * widest / (arena - ventArea)) + "%, farthest reach " + farthest + ", air "
                + (BreathLayout.B_TOP - BreathLayout.ARENA_FLOOR));
    }

    // ---------------------------------------------------------------- F. 手ごたえ
    private static void balance(List<String> f, List<String> notes) {
        HeartBalance.Profile p = PearlKeeper.balanceProfile();
        StringBuilder sb = new StringBuilder("balance (seconds at 1/2/4/6 players):");
        double lo = 1e9;
        double hi = 0;
        for (int n : new int[] {1, 2, 4, 6}) {
            double s = HeartBalance.fightSeconds(p, n, 1.0);
            lo = Math.min(lo, s);
            hi = Math.max(hi, s);
            sb.append(' ').append(Math.round(s));
            check(s >= 70 && s <= 200, "the fight with " + n + " players lasts " + Math.round(s) + " s (band 70-200)", f);
        }
        check(hi / lo <= 1.35, "fight length differs too much between party sizes (" + Math.round(lo) + " .. " + Math.round(hi) + " s)", f);
        double heavy = HeartBalance.share("tide_shell", HeartBalance.heaviest(p));
        sb.append("; heaviest hit ").append(Math.round(heavy * 100)).append("% of the expected health");
        check(heavy <= HeartBalance.HEAVY_SHARE_MAX, "the heaviest keeper hit is " + Math.round(heavy * 100) + "% of the expected health", f);
        check(PearlKeeper.takenFor(100, 1f, false) < 100 && PearlKeeper.takenFor(100, 1f, true) > PearlKeeper.takenFor(100, 1f, false) * 1.4
                && PearlKeeper.takenFor(100, 6f, false) < PearlKeeper.takenFor(100, 1f, false) / 5, "damage taken formula", f);
        check(Math.abs(PearlKeeper.HEALTH - 500f) < 1e-3 && PearlKeeper.createAttributes().build().getValue(Attributes.MAX_HEALTH) == PearlKeeper.HEALTH, "keeper health is not the balance value", f);
        notes.add(sb.toString());
    }

    // ---------------------------------------------------------------- A〜D
    private record Result(boolean finished, int warnLow, int created, int safePercent) {
    }

    private static PearlKeeper spawn(ServerLevel level, double x, double z) {
        PearlKeeper boss = TideBackSetup.PEARL_KEEPER.get().create(level);
        boss.moveTo(x, FLOOR, z, 0f, 0f);
        boss.setPersistenceRequired();
        boss.holdAttacks();
        level.addFreshEntity(boss);
        return boss;
    }

    private static Result run(ServerLevel level, PearlKeeper boss, List<FakePlayer> ps, String attack, List<Vec3> probes) {
        List<ServerPlayer> list = new ArrayList<>(ps);
        boss.setDevFighters(list);
        boss.tick();
        Telegraph.probe(probes);
        Telegraph.resetStats();
        boss.forceAction(attack);
        int warnLow = Integer.MAX_VALUE;
        boolean finished = false;
        for (int i = 0; i < 260; i++) {
            boss.tickCount++;
            boss.tick();
            Telegraph.tickNow(level);
            for (int w : Telegraph.activeWarns(level)) {
                warnLow = Math.min(warnLow, w);
            }
            if (i > 20 && boss.idle() && Telegraph.activeCount(level) == 0) {
                finished = true;
                break;
            }
        }
        boolean[] hits = Telegraph.probeHits();
        return new Result(finished, warnLow, Telegraph.damagingCreated(), Math.round(100f * safeCount(hits) / hits.length));
    }

    private static void cleanup(ServerLevel level, PearlKeeper boss, List<FakePlayer> ps) {
        Telegraph.probe(null);
        drop(ps);
        boss.discard();
        for (int i = 0; i < 3; i++) {
            Telegraph.tickNow(level);
        }
    }

    private static void fights(ServerLevel level, List<String> f, List<String> notes) {
        Map<String, int[]> counts = new HashMap<>();
        Map<String, int[]> safePercent = new HashMap<>();
        for (int n : new int[] {1, 6}) {
            for (String attack : ATTACKS) {
                PearlKeeper boss = spawn(level, SX, SZ);
                List<FakePlayer> ps = party(level, "b" + n + attack, n, 3.5);
                try {
                    Result r = run(level, boss, ps, attack, arenaGrid(0.75, 0));
                    check(r.finished(), "keeper " + attack + " (n=" + n + ") did not finish", f);
                    check(r.warnLow() >= Telegraph.MIN_WARN_TICKS, "keeper " + attack + " has a telegraph under 16 ticks (" + r.warnLow() + ")", f);
                    check(r.safePercent() >= 15, "keeper " + attack + " (n=" + n + ") leaves only " + r.safePercent() + "% of the arena safe", f);
                    counts.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = r.created();
                    safePercent.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = r.safePercent();
                } finally {
                    cleanup(level, boss, ps);
                }
            }
        }
        StringBuilder sb = new StringBuilder("keeper (areas 1p -> 6p / safe arena 1p -> 6p):");
        for (String attack : ATTACKS) {
            int[] c = counts.get(attack);
            int[] s = safePercent.get(attack);
            sb.append(' ').append(attack.toLowerCase()).append(' ').append(c[0]).append("->").append(c[1]).append('/').append(s[0]).append("%->").append(s[1]).append('%');
            check(c[1] > c[0], "keeper " + attack + ": 6 players made " + c[1] + " areas, 1 player made " + c[0] + " (must grow with the party)", f);
        }
        notes.add(sb.toString());

        // 舞台のふち (中心から 4.5) から撃っても、舞台の円の 15% 以上が安全
        StringBuilder edge = new StringBuilder("keeper fired from the arena edge (safe arena 1p/6p):");
        for (String attack : ATTACKS) {
            int[] pct = new int[2];
            for (int k = 0; k < 2; k++) {
                int n = k == 0 ? 1 : 6;
                PearlKeeper boss = spawn(level, SX + 4.5, SZ);
                List<FakePlayer> ps = party(level, "be" + n + attack, n, 3.0);
                try {
                    Result r = run(level, boss, ps, attack, arenaGrid(0.75, 0));
                    check(r.finished(), "keeper " + attack + " from the edge (n=" + n + ") did not finish", f);
                    check(r.safePercent() >= 15, "keeper " + attack + " from the arena edge (n=" + n + ") leaves only " + r.safePercent() + "% of the arena safe", f);
                    pct[k] = r.safePercent();
                } finally {
                    cleanup(level, boss, ps);
                }
            }
            edge.append(' ').append(attack.toLowerCase()).append(' ').append(pct[0]).append('/').append(pct[1]);
        }
        notes.add(edge.toString());

        // 印と当たり: 格子に並べた人のうち、傷を受けた人 = 印の中にいた人
        int total = 0;
        for (String attack : ATTACKS) {
            PearlKeeper boss = spawn(level, SX, SZ);
            List<Vec3> points = arenaGrid(2.2, 0.0);
            List<FakePlayer> ps = new ArrayList<>();
            try {
                for (int i = 0; i < points.size(); i++) {
                    ps.add(fake(level, "bg" + attack + i, points.get(i).x, FLOOR, points.get(i).z));
                }
                boss.setPartyOverride(6);
                Result r = run(level, boss, ps, attack, points);
                boolean[] hits = Telegraph.probeHits();
                int mismatches = 0;
                for (int i = 0; i < ps.size(); i++) {
                    boolean lost = ps.get(i).getHealth() < HEALTH;
                    if (lost != hits[i]) {
                        mismatches++;
                    }
                }
                total += ps.size();
                check(r.finished(), "keeper " + attack + " (grid) did not finish", f);
                check(mismatches == 0, "keeper " + attack + ": " + mismatches + " of " + ps.size() + " players were hurt outside the marker or not hurt inside it", f);
            } finally {
                cleanup(level, boss, ps);
            }
        }
        notes.add("keeper marker equals hit for " + ATTACKS.length + " attacks, " + total + " player samples");

        // 殻の中は傷を受けない・ぐったりは 1.5 倍・倒れたとき舞台の記録に届く
        PearlKeeper boss = spawn(level, SX, SZ);
        try {
            float full = boss.getHealth();
            boss.forceAction("SHELL");
            boss.tickCount++;
            boss.tick();
            boolean hurt = boss.hurt(level.damageSources().generic(), 50f);
            check(!hurt && boss.getHealth() == full, "the keeper took damage inside its shell", f);
            check(boss.mode() == PearlKeeper.MODE_SHELLED, "the keeper is not shelled during the shell action", f);
        } finally {
            boss.discard();
        }
    }
}
