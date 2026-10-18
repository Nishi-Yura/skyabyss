package jp.revati.skyabyss.titan.tide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackSetup;
import jp.revati.skyabyss.titan.tide.back.TideBeckoner;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import jp.revati.skyabyss.titan.tide.heart.TideHeartArena;
import jp.revati.skyabyss.titan.tide.heart.TideHeartSetup;
import jp.revati.skyabyss.titan.tide.heart.TidePearl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 潮殻の心臓と中ボス潮招きの予告 (Telegraph) の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST tide_telegraph PASSED / FAILED。
// 床に平らな足場を作り、心臓 (島も水もない。部屋に縛らない) と潮招きの技を一つずつ出す。
//  A. 傷のある予告はどれも 16 tick (0.8 秒) 以上 (動いている予告の warn を全 tick 見る)
//  B. 印と当たり: 床に並べた人 (傷 1000 の作り物) のうち、傷を受けた人 = 傷のある予告が当たった点 (Telegraph.probe)。
//     予告のない傷 (接触・泡の継続・追う真珠) があれば、受けた人が印の外にいて食い違う
//  C. 安全な床: 一つの技で、傷のある予告が一度も当たらない床の割合が 15% 以上 (一人と六人)
//  D. 人数で増える: 六人の傷のある予告の数が、一人より多い (心臓の 9 技、潮招きの 3 技 + 咆哮)。式 (TideHeart.xxx(n)) の値も見る
//  E. 部屋に収まる: いちばん広い一つの範囲が床の 4 割以下 (心臓の部屋は半径 14 の円、干潟は 49 x 33)。心臓の大きさ (判定箱 4.5 x 6.3) が部屋と島に収まる
public final class TideTelegraphSelfTest {
    private static final double SX = 8.5;
    private static final double SZ = 8.5;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;
    private static final String[] HEART_ATTACKS = {"BREATH", "SHELL_STRIKE", "WHIRL", "FLOOD", "BUBBLE", "PEARL", "EBB", "MOON_WAVE", "PEARL_RAIN"};
    private static final String[] BOSS_ATTACKS = {"SLAM", "BREATH", "BURROW", "ROAR"};

    private TideTelegraphSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("tide telegraph (load)", TideTelegraphSelfTest::load),
                SelfTestRunner.Step.once("tide telegraph", 30, TideTelegraphSelfTest::run));
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
            heart(level, f, notes);
            boss(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide_telegraph crashed", e);
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
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST tide_telegraph note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide_telegraph PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST tide_telegraph FAILED: {}", m));
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
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("tide telegraph " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[tt_" + tag + "]")) {
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

    // 床に並べる点 (中心から radius まで、spacing おき)
    private static List<Vec3> grid(double cx, double cz, double radius, double spacing, double skip) {
        List<Vec3> points = new ArrayList<>();
        for (double x = cx - radius; x <= cx + radius; x += spacing) {
            for (double z = cz - radius; z <= cz + radius; z += spacing) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= radius && d >= skip) {
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
        // 心臓の判定箱と、部屋・島に収まるか
        var type = TideHeartSetup.TIDE_HEART.get();
        check(Math.abs(type.getWidth() - 3.0f * TideHeart.BODY_SCALE) < 1e-3 && Math.abs(type.getHeight() - 4.2f * TideHeart.BODY_SCALE) < 1e-3,
                "the tide heart hitbox is " + type.getWidth() + " x " + type.getHeight() + " (expected 1.5x of 3.0 x 4.2)", f);
        double halfBody = type.getWidth() / 2;
        double islandCorner = Math.hypot(TideHeartArena.ISLAND_OFFSET - 1, TideHeartArena.ISLAND_OFFSET - 1);
        check(halfBody + 2.0 < islandCorner, "the tide heart body reaches the rock islands (half width " + halfBody + ", nearest island corner " + islandCorner + ")", f);
        double top = TideHeartArena.heartHome().y + type.getHeight();
        check(top + 1 < TideHeartArena.CEILING, "the tide heart hits the chamber ceiling (top " + top + ")", f);
        // 心臓の部屋: 床は半径 14 の円
        double floor = Math.PI * TideHeartArena.RADIUS * TideHeartArena.RADIUS;
        double reach = Math.min(TideHeart.SHELL_REACH, TideHeartArena.RADIUS);
        Map<String, Double> areas = new HashMap<>();
        areas.put("breath", 2 * TideHeart.BREATH_HALF_WIDTH * TideHeart.BREATH_LENGTH);
        areas.put("shell core", Math.PI * TideHeart.SHELL_CORE * TideHeart.SHELL_CORE);
        areas.put("shell ring (widest instant)", 2 * Math.PI * reach * 1.8);
        areas.put("whirl blast", Math.PI * TideHeart.WHIRL_BLAST * TideHeart.WHIRL_BLAST);
        areas.put("whirl pool", Math.PI * TideHeart.WHIRL_POOL * TideHeart.WHIRL_POOL);
        areas.put("ebb blast", Math.PI * TideHeart.EBB_BLAST * TideHeart.EBB_BLAST);
        areas.put("moon wave fan (one bar)", 0.5 * Math.min(TideHeart.MOON_LENGTH, TideHeartArena.RADIUS) * Math.min(TideHeart.MOON_LENGTH, TideHeartArena.RADIUS)
                * Math.toRadians(TideHeart.moonSpanDegrees(1)));
        areas.put("roar push", Math.PI * TideHeart.ROAR_PUSH_RADIUS * TideHeart.ROAR_PUSH_RADIUS);
        areas.put("rain circle", Math.PI * TideHeart.RAIN_RADIUS * TideHeart.RAIN_RADIUS);
        String widest = "";
        double widestArea = 0;
        for (var e : areas.entrySet()) {
            if (e.getValue() > widestArea) {
                widestArea = e.getValue();
                widest = e.getKey();
            }
        }
        check(widestArea / floor <= 0.4, "the widest tide heart area (" + widest + ") is " + Math.round(100 * widestArea / floor) + "% of the chamber floor (limit 40%)", f);
        // 干潟 (潮招きの部屋)
        var flats = TideBackLayout.FLATS;
        double flatsFloor = (flats.x2() - flats.x1() + 1) * (double) (flats.z2() - flats.z1() + 1);
        double slam = 2 * TideBeckoner.SLAM_HALF_WIDTH * TideBeckoner.SLAM_LENGTH * 2;
        double roar = Math.PI * TideBeckoner.ROAR_RADIUS * TideBeckoner.ROAR_RADIUS;
        double band = 2 * Math.PI * TideBeckoner.ROAR_RIPPLE_REACH * 1.8;
        double breath = 2 * TideBeckoner.BREATH_HALF_WIDTH * TideBeckoner.BREATH_LENGTH;
        double bossWidest = Math.max(Math.max(slam, roar), Math.max(band, breath));
        check(bossWidest / flatsFloor <= 0.4, "the widest beckoner area is " + Math.round(100 * bossWidest / flatsFloor) + "% of the flats floor (limit 40%)", f);
        check(TideBeckoner.ROAR_RIPPLE_REACH * 2 + 2 <= Math.min(flats.x2() - flats.x1() + 1, flats.z2() - flats.z1() + 1) + 4,
                "the beckoner ripple does not fit the flats", f);
        notes.add("rooms: heart chamber widest " + widest + " " + Math.round(100 * widestArea / floor) + "%, flats widest " + Math.round(100 * bossWidest / flatsFloor) + "% of "
                + (flats.x2() - flats.x1() + 1) + "x" + (flats.z2() - flats.z1() + 1));
        // 部屋の使える広さ: 使える床の半径 >= 0.8 x いちばん遠い印の届き。天井の高さ >= 12 (ボス部屋)
        double heartFar = Math.max(Math.max(TideHeart.BREATH_LENGTH, TideHeart.SHELL_REACH), Math.max(TideHeart.MOON_LENGTH, TideHeart.ROAR_PUSH_RADIUS));
        check(TideHeartArena.RADIUS >= 0.8 * heartFar, "the heart chamber radius " + TideHeartArena.RADIUS + " is under 0.8 x the farthest telegraph reach " + heartFar, f);
        check(TideHeartArena.CEILING - TideHeartArena.FLOOR >= 12, "the heart chamber has only " + (TideHeartArena.CEILING - TideHeartArena.FLOOR) + " blocks of air (needs 12)", f);
        double flatsRadius = Math.min(flats.x2() - flats.x1() + 1, flats.z2() - flats.z1() + 1) / 2.0;
        double bossFar = Math.max(Math.max(TideBeckoner.SLAM_LENGTH * 2, TideBeckoner.BREATH_LENGTH), Math.max(TideBeckoner.ROAR_RIPPLE_REACH, TideBeckoner.BURROW_RIPPLE_REACH));
        check(flatsRadius >= 0.8 * bossFar, "the flats half width " + flatsRadius + " is under 0.8 x the farthest beckoner reach " + bossFar, f);
        check(flats.height() >= 12, "the flats have only " + flats.height() + " blocks of air (needs 12)", f);
        notes.add("room reach: heart radius " + TideHeartArena.RADIUS + " vs farthest " + heartFar + ", air " + (TideHeartArena.CEILING - TideHeartArena.FLOOR)
                + "; flats half width " + flatsRadius + " vs farthest " + bossFar + ", air " + flats.height());
        // 人数の式
        check(TideHeart.breathLines(1) == 1 && TideHeart.breathLines(6) > TideHeart.breathLines(1), "breath lines do not grow with the party", f);
        check(TideHeart.pearlCount(1) == 3 && TideHeart.pearlCount(6) == 8, "pearl count: " + TideHeart.pearlCount(1) + " / " + TideHeart.pearlCount(6), f);
        check(TideHeart.moonBars(6) == 3 && TideHeart.moonBars(3) == 2 && TideHeart.moonBars(2) == 1, "moon bars do not follow the party", f);
        check(TideHeart.moonBars(6) * TideHeart.moonSpanDegrees(TideHeart.moonBars(6)) <= 220, "moon bars cover too much of the floor", f);
        check(TideBeckoner.slamLines(1) == 1 && TideBeckoner.slamLines(6) == 3 && TideBeckoner.breathLines(1) == 3 && TideBeckoner.breathLines(6) == 8
                && TideBeckoner.geysers(1) == 0 && TideBeckoner.geysers(6) == 3 && TideBeckoner.roarRipples(1) == 1 && TideBeckoner.roarRipples(6) == 2,
                "beckoner party formulas are wrong", f);
    }

    // ---------------------------------------------------------------- 心臓

    private static TideHeart spawnHeart(ServerLevel level) {
        TideHeart heart = TideHeartSetup.TIDE_HEART.get().create(level);
        heart.moveTo(SX, FLOOR + 1.5, SZ, 0f, 0f);
        heart.setPersistenceRequired();
        heart.skipIntro();
        heart.holdAttacks();
        level.addFreshEntity(heart);
        return heart;
    }

    private static void tickPearls(ServerLevel level, TideHeart heart) {
        for (TidePearl p : level.getEntitiesOfClass(TidePearl.class, heart.getBoundingBox().inflate(40, 20, 40))) {
            p.tickCount++;
            p.tick();
        }
    }

    private static int pearlsAlive(ServerLevel level, TideHeart heart) {
        return level.getEntitiesOfClass(TidePearl.class, heart.getBoundingBox().inflate(40, 20, 40)).size();
    }

    private record Result(boolean finished, int warnLow, int created, int safePercent, int[] hitIndexes) {
    }

    // 技を一つ最後まで回す。probes は床の点 (傷のある予告が一度でも当たった点を数える)
    private static Result runHeart(ServerLevel level, TideHeart heart, String attack, List<Vec3> probes) {
        heart.tick();
        Telegraph.probe(probes);
        Telegraph.resetStats();
        heart.forceAction(TideHeart.Action.valueOf(attack));
        int warnLow = Integer.MAX_VALUE;
        boolean finished = false;
        for (int i = 0; i < 600; i++) {
            heart.tickCount++;
            heart.tick();
            tickPearls(level, heart);
            Telegraph.tickNow(level);
            for (int w : Telegraph.activeWarns(level)) {
                warnLow = Math.min(warnLow, w);
            }
            if (i > 20 && heart.actionTickCount() == 0 && Telegraph.activeCount(level) == 0 && pearlsAlive(level, heart) == 0) {
                finished = true;
                break;
            }
        }
        boolean[] hits = Telegraph.probeHits();
        int safe = safeCount(hits);
        return new Result(finished, warnLow, Telegraph.damagingCreated(), Math.round(100f * safe / hits.length), null);
    }

    private static void clear(ServerLevel level, TideHeart heart) {
        for (TidePearl p : level.getEntitiesOfClass(TidePearl.class, heart.getBoundingBox().inflate(40, 20, 40))) {
            p.discard();
        }
        for (jp.revati.skyabyss.titan.tide.heart.TideBubble b : level.getEntitiesOfClass(jp.revati.skyabyss.titan.tide.heart.TideBubble.class, heart.getBoundingBox().inflate(40, 20, 40))) {
            b.discard();
        }
        for (int i = 0; i < 3; i++) {
            Telegraph.tickNow(level);
        }
    }

    private static void heart(ServerLevel level, List<String> f, List<String> notes) {
        Map<String, int[]> counts = new HashMap<>();
        Map<String, int[]> safePercent = new HashMap<>();
        for (int n : new int[] {1, 6}) {
            for (String attack : HEART_ATTACKS) {
                TideHeart heart = spawnHeart(level);
                List<FakePlayer> ps = party(level, "h" + n + attack, n, 9);
                try {
                    heart.setTarget(ps.get(0));
                    Result r = runHeart(level, heart, attack, grid(SX, SZ, 14, 1.0, 0));
                    check(r.finished(), attack + " (n=" + n + ") did not finish", f);
                    check(r.warnLow() >= Telegraph.MIN_WARN_TICKS, attack + " has a telegraph under 16 ticks (" + r.warnLow() + ")", f);
                    check(r.safePercent() >= 15, attack + " (n=" + n + ") leaves only " + r.safePercent() + "% of the floor safe", f);
                    counts.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = r.created();
                    safePercent.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = r.safePercent();
                } finally {
                    Telegraph.probe(null);
                    drop(ps);
                    clear(level, heart);
                    heart.discard();
                }
            }
        }
        StringBuilder sb = new StringBuilder("heart (areas 1p -> 6p / safe floor 1p -> 6p):");
        for (String attack : HEART_ATTACKS) {
            int[] c = counts.get(attack);
            int[] s = safePercent.get(attack);
            sb.append(' ').append(attack.toLowerCase()).append(' ').append(c[0]).append("->").append(c[1]).append('/').append(s[0]).append("%->").append(s[1]).append('%');
            check(c[1] > c[0], attack + ": 6 players made " + c[1] + " areas, 1 player made " + c[0] + " (must grow with the party)", f);
        }
        notes.add(sb.toString());

        // 部屋のふちから撃つ: 心臓を床の円のふち (中心から 11) に置き、どの技でも部屋の床の 15% 以上が安全
        StringBuilder edge = new StringBuilder("heart fired from the room edge (safe floor 1p/6p):");
        for (String attack : HEART_ATTACKS) {
            int[] pct = new int[2];
            for (int k = 0; k < 2; k++) {
                int n = k == 0 ? 1 : 6;
                TideHeart heart = spawnHeart(level);
                heart.moveTo(SX + 11, FLOOR + 1.5, SZ, 0f, 0f);
                List<FakePlayer> ps = party(level, "e" + n + attack, n, 9);
                try {
                    heart.setTarget(ps.get(0));
                    Result r = runHeart(level, heart, attack, grid(SX, SZ, 14, 1.0, 0));
                    check(r.finished(), attack + " from the edge (n=" + n + ") did not finish", f);
                    check(r.safePercent() >= 15, attack + " from the room edge (n=" + n + ") leaves only " + r.safePercent() + "% of the room safe", f);
                    pct[k] = r.safePercent();
                } finally {
                    Telegraph.probe(null);
                    drop(ps);
                    clear(level, heart);
                    heart.discard();
                }
            }
            edge.append(' ').append(attack.toLowerCase()).append(' ').append(pct[0]).append('/').append(pct[1]);
        }
        notes.add(edge.toString());

        // 咆哮: 傷なし (押し返すだけ)。予告は 16 tick 以上
        TideHeart roar = spawnHeart(level);
        List<FakePlayer> rp = party(level, "roar", 3, 5);
        try {
            roar.setTarget(rp.get(0));
            Result r = runHeart(level, roar, "ROAR", grid(SX, SZ, 14, 1.0, 0));
            check(r.finished() && r.warnLow() >= Telegraph.MIN_WARN_TICKS, "roar did not finish or warns under 16 ticks (" + r.warnLow() + ")", f);
            for (FakePlayer p : rp) {
                check(p.getHealth() == HEALTH, "the roar hurt a player", f);
            }
        } finally {
            Telegraph.probe(null);
            drop(rp);
            clear(level, roar);
            roar.discard();
        }

        // 印と当たり: 床に並べた人 (37 人ほど) のうち、傷を受けた人 = 印の中 (傷のある予告が当たった点)
        int total = 0;
        for (String attack : HEART_ATTACKS) {
            TideHeart heart = spawnHeart(level);
            List<Vec3> points = grid(SX, SZ, 13, 3.0, 3.5);
            List<FakePlayer> ps = new ArrayList<>();
            try {
                for (int i = 0; i < points.size(); i++) {
                    ps.add(fake(level, "g" + attack + i, points.get(i).x, FLOOR, points.get(i).z));
                }
                heart.setTarget(ps.get(0));
                heart.setPartyOverride(6);
                Result r = runHeart(level, heart, attack, points);
                boolean[] hits = Telegraph.probeHits();
                int mismatches = 0;
                int hurt = 0;
                for (int i = 0; i < ps.size(); i++) {
                    // 泡の柱は傷を直接与えず、泡に閉じこめる (傷はそのあとの泡の中の継続)。閉じこめられた人も「当たった」と数える
                    boolean lost = ps.get(i).getHealth() < HEALTH || jp.revati.skyabyss.titan.tide.heart.TideBubble.isTrapped(level, ps.get(i));
                    hurt += lost ? 1 : 0;
                    if (lost != hits[i]) {
                        mismatches++;
                    }
                }
                total += ps.size();
                check(r.finished(), attack + " (grid) did not finish", f);
                check(mismatches == 0, attack + ": " + mismatches + " of " + ps.size() + " players were hurt outside the marker or not hurt inside it (hurt " + hurt + ")", f);
            } finally {
                Telegraph.probe(null);
                drop(ps);
                clear(level, heart);
                heart.discard();
            }
        }
        notes.add("heart marker equals hit for " + HEART_ATTACKS.length + " attacks, " + total + " player samples");
    }

    // ---------------------------------------------------------------- 潮招き

    private static TideBeckoner spawnBoss(ServerLevel level) {
        TideBeckoner boss = TideBackSetup.TIDE_BECKONER.get().create(level);
        boss.moveTo(SX, FLOOR, SZ, 0f, 0f);
        boss.setPersistenceRequired();
        boss.holdAttacks();
        level.addFreshEntity(boss);
        return boss;
    }

    private static Result runBoss(ServerLevel level, TideBeckoner boss, List<FakePlayer> ps, String attack, List<Vec3> probes) {
        List<ServerPlayer> list = new ArrayList<>(ps);
        boss.setDevFighters(list);
        boss.tick();
        Telegraph.probe(probes);
        Telegraph.resetStats();
        boss.forceAction(attack);
        int warnLow = Integer.MAX_VALUE;
        boolean finished = false;
        for (int i = 0; i < 200; i++) {
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
        return new Result(finished, warnLow, Telegraph.damagingCreated(), Math.round(100f * safeCount(hits) / hits.length), null);
    }

    private static void boss(ServerLevel level, List<String> f, List<String> notes) {
        Map<String, int[]> counts = new HashMap<>();
        Map<String, int[]> safePercent = new HashMap<>();
        for (int n : new int[] {1, 6}) {
            for (String attack : BOSS_ATTACKS) {
                TideBeckoner boss = spawnBoss(level);
                List<FakePlayer> ps = party(level, "b" + n + attack, n, 7);
                try {
                    Result r = runBoss(level, boss, ps, attack, grid(SX, SZ, 14, 1.0, 0));
                    check(r.finished(), "beckoner " + attack + " (n=" + n + ") did not finish", f);
                    check(r.warnLow() >= Telegraph.MIN_WARN_TICKS, "beckoner " + attack + " has a telegraph under 16 ticks (" + r.warnLow() + ")", f);
                    check(r.safePercent() >= 15, "beckoner " + attack + " (n=" + n + ") leaves only " + r.safePercent() + "% of the floor safe", f);
                    counts.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = r.created();
                    safePercent.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = r.safePercent();
                } finally {
                    Telegraph.probe(null);
                    drop(ps);
                    boss.discard();
                    for (int i = 0; i < 3; i++) {
                        Telegraph.tickNow(level);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder("beckoner (areas 1p -> 6p / safe floor 1p -> 6p):");
        for (String attack : BOSS_ATTACKS) {
            int[] c = counts.get(attack);
            int[] s = safePercent.get(attack);
            sb.append(' ').append(attack.toLowerCase()).append(' ').append(c[0]).append("->").append(c[1]).append('/').append(s[0]).append("%->").append(s[1]).append('%');
            check(c[1] > c[0], "beckoner " + attack + ": 6 players made " + c[1] + " areas, 1 player made " + c[0] + " (must grow with the party)", f);
        }
        notes.add(sb.toString());

        // 部屋のふちから: 潮招きを干潟の東のふち (中心から 20) に置き、干潟の床 (49 x 33) の 15% 以上が安全
        StringBuilder edge = new StringBuilder("beckoner fired from the room edge (safe floor 1p/6p):");
        List<Vec3> rect = new ArrayList<>();
        for (double x = SX - 24; x <= SX + 24; x += 1.5) {
            for (double z = SZ - 16; z <= SZ + 16; z += 1.5) {
                rect.add(new Vec3(x, FLOOR, z));
            }
        }
        for (String attack : BOSS_ATTACKS) {
            int[] pct = new int[2];
            for (int k = 0; k < 2; k++) {
                int n = k == 0 ? 1 : 6;
                TideBeckoner boss = spawnBoss(level);
                boss.moveTo(SX + 20, FLOOR, SZ, 90f, 0f);
                List<FakePlayer> ps = party(level, "be" + n + attack, n, 7);
                try {
                    Result r = runBoss(level, boss, ps, attack, rect);
                    check(r.finished(), "beckoner " + attack + " from the edge (n=" + n + ") did not finish", f);
                    check(r.safePercent() >= 15, "beckoner " + attack + " from the room edge (n=" + n + ") leaves only " + r.safePercent() + "% of the flats safe", f);
                    pct[k] = r.safePercent();
                } finally {
                    Telegraph.probe(null);
                    drop(ps);
                    boss.discard();
                    for (int i = 0; i < 3; i++) {
                        Telegraph.tickNow(level);
                    }
                }
            }
            edge.append(' ').append(attack.toLowerCase()).append(' ').append(pct[0]).append('/').append(pct[1]);
        }
        notes.add(edge.toString());

        // 印と当たり
        int total = 0;
        for (String attack : BOSS_ATTACKS) {
            TideBeckoner boss = spawnBoss(level);
            List<Vec3> points = grid(SX, SZ, 13, 3.0, 3.0);
            List<FakePlayer> ps = new ArrayList<>();
            try {
                for (int i = 0; i < points.size(); i++) {
                    ps.add(fake(level, "bg" + attack + i, points.get(i).x, FLOOR, points.get(i).z));
                }
                boss.setPartyOverride(6);
                Result r = runBoss(level, boss, ps, attack, points);
                boolean[] hits = Telegraph.probeHits();
                int mismatches = 0;
                for (int i = 0; i < ps.size(); i++) {
                    boolean lost = ps.get(i).getHealth() < HEALTH;
                    if (lost != hits[i]) {
                        mismatches++;
                    }
                }
                total += ps.size();
                check(r.finished(), "beckoner " + attack + " (grid) did not finish", f);
                check(mismatches == 0, "beckoner " + attack + ": " + mismatches + " of " + ps.size() + " players were hurt outside the marker or not hurt inside it", f);
            } finally {
                Telegraph.probe(null);
                drop(ps);
                boss.discard();
                for (int i = 0; i < 3; i++) {
                    Telegraph.tickNow(level);
                }
            }
        }
        notes.add("beckoner marker equals hit for " + BOSS_ATTACKS.length + " attacks, " + total + " player samples");
    }
}
