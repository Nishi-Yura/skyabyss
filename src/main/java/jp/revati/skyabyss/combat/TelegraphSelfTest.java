package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneSentinel;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 予告 (Telegraph) の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST telegraph PASSED / FAILED。
//  A. 形ごとに、床に並べた人の「当たった / 当たらない」が、形の contains と一致する。当たるのは予告の長さ (16 tick 以上) が過ぎてから
//  B. 短い予告は 16 tick に延びる・持ち主が消えたら傷は出ない・跳び越えられる (jumpClear)
//  C. 番兵長の四つの技 (薙ぎ払い・跳躍と叩きつけ・骨の噴出・咆哮): 印の内側は傷・外側は無傷・予告が 0.8 秒以上・傷の値、跳躍は印の円の中心へ降りる
//  D. 岩鯨の心臓の九つの技: 予告が 0.8 秒以上、安全な床が残る、人数 (1 人と 6 人) で本数が増える。
//     安全な床は、傷のある予告が一度でも当たった点 (Telegraph.probe) を数えて求める
public final class TelegraphSelfTest {
    private static final double SX = 8.5;
    private static final double SZ = 8.5;
    // 床の高さ (石の板の上面)。足場は y = FLOOR - 1 の石
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;

    private TelegraphSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("telegraph (load)", TelegraphSelfTest::load),
                SelfTestRunner.Step.once("telegraph", 30, TelegraphSelfTest::run));
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
            // 敵の傷はむずかしさで変わる (やさしいと約 6 割)。数値はふつうで確かめる
            server.setDifficulty(Difficulty.NORMAL, true);
            platform(level);
            shapes(level, f, notes);
            TelegraphDecalTest.run(server, f, notes);
            TelegraphGroundTest.run(f, notes);
            basics(level, f, notes);
            roomSizes(f, notes);
            captain(level, f, notes);
            heart(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST telegraph crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.probe(null);
            server.setDifficulty(before, true);
            // 強制読み込みは外す (残すと、止めるときのチャンクの書き出しが終わらないことがある)
            for (int cx = -2; cx <= 2; cx++) {
                for (int cz = -2; cz <= 2; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST telegraph note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST telegraph PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST telegraph FAILED: {}", m));
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
                for (int y = FLOOR; y <= FLOOR + 8; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String tag, double x, double y, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("telegraph " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[tg_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawn = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
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

    private static float lost(FakePlayer p) {
        return HEALTH - p.getHealth();
    }

    // ---------------------------------------------------------------- A. 形

    private record Probe(double x, double z) {
    }

    private static void shapes(ServerLevel level, List<String> f, List<String> notes) {
        Map<String, TelegraphShape> shapes = new HashMap<>();
        shapes.put("circle", TelegraphShape.circle(SX, SZ, 3.5));
        shapes.put("ring", TelegraphShape.ring(SX, SZ, 2.0, 4.5));
        shapes.put("line", TelegraphShape.line(SX - 1, SZ - 2, 0.6, 9, 1.4));
        shapes.put("cone", TelegraphShape.cone(SX, SZ, 2.0, 6, Math.toRadians(70)));
        shapes.put("sweep", TelegraphShape.sweep(SX, SZ, 0.3, 1.2, 8, 1.1));
        shapes.put("wave", TelegraphShape.wave(SX, SZ, 2.0, 8, 0.9, new double[] {1.0, 1.0 + Math.PI}, Math.toRadians(22)));
        int total = 0;
        for (var entry : shapes.entrySet()) {
            String name = entry.getKey();
            TelegraphShape shape = entry.getValue();
            boolean moving = name.equals("sweep") || name.equals("wave");
            double[] b = shape.bounds();
            double step = Math.max(b[2] - b[0], b[3] - b[1]) / 6.0;
            List<Probe> points = new ArrayList<>();
            for (double x = b[0] + step / 3; x <= b[2]; x += step) {
                for (double z = b[1] + step / 3; z <= b[3]; z += step) {
                    points.add(new Probe(x, z));
                }
            }
            List<FakePlayer> players = new ArrayList<>();
            int[] firstHit = new int[points.size()];
            java.util.Arrays.fill(firstHit, -1);
            int[] tick = {0};
            boolean[] expectedEver = new boolean[points.size()];
            try {
                for (int i = 0; i < points.size(); i++) {
                    players.add(fake(level, name + i, points.get(i).x(), FLOOR, points.get(i).z()));
                }
                int warn = 18;
                int active = moving ? 20 : 0;
                Telegraph.in(level, shape).floor(FLOOR).warn(warn).active(active)
                        .onHit((lvl, victim) -> {
                            int idx = players.indexOf(victim);
                            if (idx >= 0) {
                                firstHit[idx] = tick[0];
                            }
                        }).start();
                for (int i = 0; i < points.size(); i++) {
                    if (!moving) {
                        expectedEver[i] = shape.at(1.0).contains(points.get(i).x(), points.get(i).z());
                    } else {
                        for (int k = 0; k <= active; k++) {
                            if (shape.at((double) k / active).contains(points.get(i).x(), points.get(i).z())) {
                                expectedEver[i] = true;
                            }
                        }
                    }
                }
                for (int t = 1; t <= warn + active + 3; t++) {
                    tick[0] = t;
                    Telegraph.tickNow(level);
                }
                int mismatches = 0;
                int inside = 0;
                for (int i = 0; i < points.size(); i++) {
                    boolean hit = firstHit[i] >= 0;
                    inside += expectedEver[i] ? 1 : 0;
                    if (hit != expectedEver[i]) {
                        mismatches++;
                    }
                    if (hit && firstHit[i] < Telegraph.MIN_WARN_TICKS) {
                        f.add(name + ": a point was hit after only " + firstHit[i] + " ticks");
                    }
                    if (hit && (lost(players.get(i)) != 0)) {
                        f.add(name + ": a callback-less hit hurt a player");
                    }
                }
                check(mismatches == 0, name + ": " + mismatches + " of " + points.size() + " points disagree between the area and the hit", f);
                check(inside > 0 && inside < points.size(), name + ": the sample has " + inside + "/" + points.size() + " points inside (needs both inside and outside)", f);
                total += points.size();
            } finally {
                drop(players);
            }
        }
        notes.add("shapes: " + shapes.size() + " kinds, " + total + " sample points, area equals hit");
    }

    // ---------------------------------------------------------------- B. 基本

    private static void basics(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> players = new ArrayList<>();
        try {
            FakePlayer p = fake(level, "basic", SX, FLOOR, SZ);
            players.add(p);
            // 短い予告は 16 tick に延びる
            int[] hit = {-1};
            int[] tick = {0};
            Telegraph t = Telegraph.in(level, TelegraphShape.circle(SX, SZ, 2)).floor(FLOOR).warn(3)
                    .onHit((l, v) -> hit[0] = tick[0]).start();
            check(t.warnTicks() == Telegraph.MIN_WARN_TICKS, "a 3-tick warning was not stretched to " + Telegraph.MIN_WARN_TICKS + " (" + t.warnTicks() + ")", f);
            for (int i = 1; i <= 30; i++) {
                tick[0] = i;
                Telegraph.tickNow(level);
            }
            check(hit[0] == Telegraph.MIN_WARN_TICKS, "the stretched warning hit at tick " + hit[0], f);
            // 持ち主が消えたら傷は出ない
            ArmorStand owner = new ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
            owner.moveTo(SX, FLOOR, SZ);
            level.addFreshEntity(owner);
            int[] cancelled = {0};
            Telegraph.in(level, TelegraphShape.circle(SX, SZ, 2)).floor(FLOOR).warn(20).owner(owner)
                    .onHit((l, v) -> cancelled[0]++).start();
            for (int i = 1; i <= 8; i++) {
                Telegraph.tickNow(level);
            }
            owner.discard();
            for (int i = 1; i <= 30; i++) {
                Telegraph.tickNow(level);
            }
            check(cancelled[0] == 0, "a telegraph whose owner vanished still hit", f);
            check(Telegraph.activeCount(level) == 0, "finished telegraphs were not dropped (" + Telegraph.activeCount(level) + ")", f);
            // 跳び越え: 床から 1.3 の人は輪を越える。床の人は当たる
            FakePlayer air = fake(level, "air", SX + 6, FLOOR + 1.3, SZ);
            players.add(air);
            FakePlayer ground = fake(level, "ground", SX + 6, FLOOR, SZ + 0.2);
            players.add(ground);
            int[] hits = {0, 0};
            Telegraph.in(level, TelegraphShape.circle(SX + 6, SZ, 2)).floor(FLOOR).warn(16).jumpClear(0.9)
                    .onHit((l, v) -> {
                        if (v == air) {
                            hits[0]++;
                        } else if (v == ground) {
                            hits[1]++;
                        }
                    }).start();
            for (int i = 1; i <= 20; i++) {
                Telegraph.tickNow(level);
            }
            check(hits[0] == 0 && hits[1] == 1, "jumpClear: air hit " + hits[0] + ", ground hit " + hits[1], f);
            notes.add("basics: warn stretched to 16, owner cancel, jump over");
        } finally {
            drop(players);
        }
    }

    // 番兵長の広間の広さ: いちばん広い技の範囲 (スタンプの円・薙ぎ払いの扇) が床の 4 割以下
    private static void roomSizes(List<String> f, List<String> notes) {
        var e3 = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.E3;
        var e1 = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.E1;
        double floor = (e3.x2() - e3.x1() + 1) * (double) (e3.z2() - e3.z1() + 1);
        double stomp = Math.PI * WhaleboneCaptain.STOMP_RADIUS_ENRAGED * WhaleboneCaptain.STOMP_RADIUS_ENRAGED;
        double sweep = Math.PI * WhaleboneCaptain.SWEEP_RANGE * WhaleboneCaptain.SWEEP_RANGE * (2 * WhaleboneCaptain.SWEEP_HALF_ANGLE / 360);
        double ratio = Math.max(stomp, sweep) / floor;
        check(ratio <= 0.4, "the largest captain area is " + Math.round(ratio * 100) + "% of the captain hall floor (limit 40%)", f);
        double heartFloor = Math.PI * 19 * 19;
        double chain = Math.PI * 14 * 14 / 2;
        check(chain / heartFloor <= 0.4, "the chain sector covers too much of the heart chamber", f);
        notes.add("rooms: captain hall " + (e3.x2() - e3.x1() + 1) + "x" + (e3.z2() - e3.z1() + 1) + " (largest area " + Math.round(ratio * 100) + "%), first hall "
                + (e1.x2() - e1.x1() + 1) + "x" + (e1.z2() - e1.z1() + 1) + ", heart chamber radius 19");
    }

    // ---------------------------------------------------------------- C. 番兵長

    private static List<Vec3> grid(double cx, double cz, double radius) {
        List<Vec3> probes = new ArrayList<>();
        for (double x = cx - radius; x <= cx + radius; x += 1.0) {
            for (double z = cz - radius; z <= cz + radius; z += 1.0) {
                if (Math.hypot(x - cx, z - cz) <= radius) {
                    probes.add(new Vec3(x, FLOOR, z));
                }
            }
        }
        return probes;
    }

    private static int safeCount(boolean[] hits) {
        int safe = 0;
        for (boolean h : hits) {
            safe += h ? 0 : 1;
        }
        return safe;
    }

    private static void runCaptain(ServerLevel level, WhaleboneCaptain c, int ticks) {
        for (int i = 0; i < ticks; i++) {
            c.tickCount++;
            c.tick();
            Telegraph.tickNow(level);
        }
    }

    private static WhaleboneCaptain spawnCaptain(ServerLevel level, float yaw) {
        WhaleboneCaptain c = ModEntities.WHALEBONE_CAPTAIN.get().create(level);
        c.moveTo(SX, FLOOR, SZ, yaw, 0);
        c.setPersistenceRequired();
        c.holdAttacks();
        level.addFreshEntity(c);
        return c;
    }

    private static void captain(ServerLevel level, List<String> f, List<String> notes) {
        // 薙ぎ払い (向き 0 = +z)。前 3 と 5 は扇の中、前 5.8・後ろ 3・横 3 は外
        WhaleboneCaptain c = spawnCaptain(level, 0f);
        List<FakePlayer> ps = new ArrayList<>();
        try {
            FakePlayer a = fake(level, "sw_a", SX, FLOOR, SZ + 3);
            FakePlayer d = fake(level, "sw_d", SX, FLOOR, SZ + 5.0);
            FakePlayer e = fake(level, "sw_e", SX, FLOOR, SZ + 5.8);
            FakePlayer back = fake(level, "sw_b", SX, FLOOR, SZ - 3);
            FakePlayer side = fake(level, "sw_s", SX + 3, FLOOR, SZ + 0.3);
            ps.addAll(List.of(a, d, e, back, side));
            c.setTarget(a);
            Telegraph.probe(grid(SX, SZ, 8));
            c.forceAction("SWEEP");
            int firstHurt = -1;
            for (int i = 1; i <= 40; i++) {
                runCaptain(level, c, 1);
                if (firstHurt < 0 && a.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            check(firstHurt >= Telegraph.MIN_WARN_TICKS, "captain sweep hurt after only " + firstHurt + " ticks", f);
            check(lost(a) >= WhaleboneCaptain.SWEEP_DAMAGE * 0.7f && lost(a) <= WhaleboneCaptain.SWEEP_DAMAGE * 1.1f, "captain sweep damage " + lost(a), f);
            check(lost(d) > 0, "captain sweep missed a player inside the cone at 5.0", f);
            check(lost(e) == 0 && lost(back) == 0 && lost(side) == 0, "captain sweep hurt a player outside the cone", f);
            int safe = safeCount(Telegraph.probeHits());
            check(safe > Telegraph.probeHits().length / 2, "captain sweep leaves little safe floor (" + safe + ")", f);
            notes.add("captain sweep warns " + firstHurt + " ticks (" + Math.round(firstHurt * 5) + " ms), hits " + Math.round(lost(a)));
        } finally {
            Telegraph.probe(null);
            drop(ps);
            c.discard();
        }

        // 跳躍と叩きつけ: 狙われた人の位置 L が着地点。L と、L から 3.5 離れた人は円の中 (傷)、4.6 離れた人は外
        c = spawnCaptain(level, 0f);
        try {
            double lx = SX;
            double lz = SZ + 9;
            FakePlayer center = fake(level, "lp_c", lx, FLOOR, lz);
            FakePlayer edge = fake(level, "lp_e", lx + 3.5, FLOOR, lz);
            FakePlayer out = fake(level, "lp_o", lx + 4.6, FLOOR, lz);
            ps.addAll(List.of(center, edge, out));
            c.setTarget(center);
            Telegraph.probe(grid(lx, lz, 8));
            c.forceAction("LEAP");
            int firstHurt = -1;
            for (int i = 1; i <= 50; i++) {
                runCaptain(level, c, 1);
                if (firstHurt < 0 && center.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            check(firstHurt >= Telegraph.MIN_WARN_TICKS, "captain stomp hurt after only " + firstHurt + " ticks", f);
            check(lost(center) >= 6 && lost(center) <= 9, "captain stomp center damage " + lost(center), f);
            check(lost(edge) >= 3 && lost(edge) <= lost(center), "captain stomp edge damage " + lost(edge) + " (center " + lost(center) + ")", f);
            check(lost(out) == 0, "captain stomp hurt a player outside the circle", f);
            check(Math.abs(c.getX() - lx) < 0.3 && Math.abs(c.getZ() - lz) < 0.3 && Math.abs(c.getY() - FLOOR) < 0.2,
                    "captain did not land on the marked point (" + c.position() + " vs " + lx + ", " + lz + ")", f);
            int safe = safeCount(Telegraph.probeHits());
            check(safe > Telegraph.probeHits().length / 2, "captain stomp leaves little safe floor (" + safe + ")", f);
            notes.add("captain stomp warns " + firstHurt + " ticks, center " + Math.round(lost(center)) + " / edge " + Math.round(lost(edge))
                    + ", radius " + WhaleboneCaptain.STOMP_RADIUS);
        } finally {
            Telegraph.probe(null);
            drop(ps);
            c.discard();
        }

        // 骨の噴出: 立っている人の足元に円。予告 30 tick、傷 7
        c = spawnCaptain(level, 0f);
        try {
            FakePlayer t = fake(level, "er_t", SX, FLOOR, SZ + 6);
            ps.add(t);
            c.setTarget(t);
            Telegraph.resetStats();
            c.forceAction("ERUPT");
            runCaptain(level, c, 2);
            List<Integer> warns = Telegraph.activeWarns(level);
            check(warns.size() >= 6, "captain erupt made " + warns.size() + " circles (expected at least 6)", f);
            check(warns.stream().allMatch(w -> w >= Telegraph.MIN_WARN_TICKS), "a captain erupt circle warns under 16 ticks", f);
            int firstHurt = -1;
            for (int i = 3; i <= 50; i++) {
                runCaptain(level, c, 1);
                if (firstHurt < 0 && t.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            check(firstHurt >= 16 && Math.abs(lost(t) - WhaleboneCaptain.ERUPT_DAMAGE) < 0.8f, "captain erupt hit at " + firstHurt + " for " + lost(t), f);
            notes.add("captain erupt: " + warns.size() + " circles, warns " + WhaleboneCaptain.ERUPT_WARN + " ticks, hits " + Math.round(lost(t)));
        } finally {
            drop(ps);
            c.discard();
        }

        // 咆哮: 半径 5 の円。3 は中、6 は外
        c = spawnCaptain(level, 0f);
        try {
            FakePlayer in = fake(level, "ro_i", SX + 3, FLOOR, SZ);
            FakePlayer out = fake(level, "ro_o", SX + 6, FLOOR, SZ);
            ps.addAll(List.of(in, out));
            c.setTarget(in);
            c.forceAction("ROAR");
            int firstHurt = -1;
            for (int i = 1; i <= 40; i++) {
                runCaptain(level, c, 1);
                if (firstHurt < 0 && in.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            check(firstHurt >= 16, "captain roar hurt after only " + firstHurt + " ticks", f);
            check(lost(in) > 0 && lost(out) == 0, "captain roar: inside lost " + lost(in) + ", outside lost " + lost(out), f);
            notes.add("captain roar warns " + firstHurt + " ticks, radius " + WhaleboneCaptain.ROAR_RADIUS);
        } finally {
            drop(ps);
            for (BoneSentinel s : level.getEntitiesOfClass(BoneSentinel.class, c.getBoundingBox().inflate(20))) {
                s.discard();
            }
            c.discard();
        }
    }

    // ---------------------------------------------------------------- D. 岩鯨の心臓

    private static final String[] ATTACKS = {"THRUST", "SWEEP", "LEAP", "CRACK", "RECLOSE", "VOLLEY", "BEAM", "CHAIN", "SHELL_DROP", "PAIR_0", "PAIR_1", "PAIR_2", "PAIR_3"};

    private static void heart(ServerLevel level, List<String> f, List<String> notes) {
        Map<String, int[]> counts = new HashMap<>();
        Map<String, int[]> safePercent = new HashMap<>();
        for (int n : new int[] {1, 6}) {
            for (String attack : ATTACKS) {
                StoneWhaleHeart heart = ModEntities.STONE_WHALE_HEART.get().create(level);
                heart.moveTo(SX, FLOOR + 2, SZ, 0f, 0f);
                heart.setPersistenceRequired();
                heart.skipIntro();
                heart.holdAttacks();
                level.addFreshEntity(heart);
                List<FakePlayer> ps = new ArrayList<>();
                try {
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n + 0.4;
                        ps.add(fake(level, "h" + n + "_" + i, SX + Math.cos(a) * 9, FLOOR, SZ + Math.sin(a) * 9));
                    }
                    heart.setTarget(ps.get(0));
                    heart.tick();
                    List<Vec3> probes = grid(SX, SZ, 14);
                    Telegraph.probe(probes);
                    Telegraph.resetStats();
                    heart.forceAttack(attack);
                    int maxWarnLow = Integer.MAX_VALUE;
                    int maxAlive = 0;
                    boolean finished = false;
                    for (int i = 0; i < 400; i++) {
                        heart.tickCount++;
                        heart.tick();
                        Telegraph.tickNow(level);
                        for (int w : Telegraph.activeWarns(level)) {
                            maxWarnLow = Math.min(maxWarnLow, w);
                        }
                        maxAlive = Math.max(maxAlive, Telegraph.activeCount(level));
                        if (i > 20 && !finished && heart.actionTickCount() == 0 && Telegraph.activeCount(level) == 0) {
                            finished = true;
                            break;
                        }
                    }
                    check(finished, attack + " (n=" + n + ") did not finish", f);
                    check(maxWarnLow >= Telegraph.MIN_WARN_TICKS, attack + " has a telegraph under 16 ticks (" + maxWarnLow + ")", f);
                    boolean[] hits = Telegraph.probeHits();
                    int safe = safeCount(hits);
                    int percent = Math.round(100f * safe / hits.length);
                    check(attack.equals("RECLOSE") || percent >= (attack.equals("LEAP") ? 15 : attack.equals("PAIR_3") ? 12 : attack.startsWith("PAIR_") ? 15 : 25), attack + " (n=" + n + ") leaves only " + percent + "% of the floor safe", f);
                    counts.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = Telegraph.damagingCreated();
                    safePercent.computeIfAbsent(attack, k -> new int[2])[n == 1 ? 0 : 1] = percent;
                } finally {
                    Telegraph.probe(null);
                    drop(ps);
                    heart.discard();
                    for (int i = 0; i < 3; i++) {
                        Telegraph.tickNow(level);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder("heart (areas 1p -> 6p / safe floor 1p -> 6p):");
        for (String attack : ATTACKS) {
            int[] c = counts.get(attack);
            int[] s = safePercent.get(attack);
            sb.append(' ').append(attack.toLowerCase()).append(' ').append(c[0]).append("->").append(c[1]).append('/').append(s[0]).append("%->").append(s[1]).append('%');
            if (!attack.equals("RECLOSE") && !attack.equals("LEAP") && !attack.equals("PAIR_3")) {
                check(c[1] > c[0], attack + ": 6 players made " + c[1] + " areas, 1 player made " + c[0] + " (must grow with the party)", f);
            }
        }
        // 跳躍の叩きつけは、輪の数 (三人から二重) で数える。傷のある予告 = 円 + 輪
        int[] leap = counts.get("LEAP");
        check(leap[1] > leap[0], "LEAP: 6 players made " + leap[1] + " areas, 1 player made " + leap[0], f);
        notes.add(sb.toString());
        heartFury(level, f, notes);
    }

    // 憤怒: 体力 10% で湧かせると、咆哮を三度 (段階 2・3・憤怒) 経て憤怒に入り、そのあとは技の組だけが出て、予告はどれも 16 tick 以上
    private static void heartFury(ServerLevel level, List<String> f, List<String> notes) {
        StoneWhaleHeart heart = ModEntities.STONE_WHALE_HEART.get().create(level);
        heart.moveTo(SX, FLOOR + 2, SZ, 0f, 0f);
        heart.setPersistenceRequired();
        heart.skipIntro();
        heart.setDomainEnabled(false);
        level.addFreshEntity(heart);
        List<FakePlayer> ps = new ArrayList<>();
        try {
            FakePlayer p = fake(level, "fury", SX + 9, FLOOR, SZ);
            ps.add(p);
            heart.setTarget(p);
            heart.setHealth(heart.getMaxHealth() * 0.10f);
            int minWarn = Integer.MAX_VALUE;
            int attacks = 0;
            boolean wasIdle = true;
            for (int i = 0; i < 900; i++) {
                p.setHealth(p.getMaxHealth());
                heart.tickCount++;
                heart.tick();
                Telegraph.tickNow(level);
                for (int w : Telegraph.activeWarns(level)) {
                    minWarn = Math.min(minWarn, w);
                }
                boolean idle = heart.actionTickCount() == 0;
                if (wasIdle && !idle && heart.isEnraged()) {
                    attacks++;
                }
                wasIdle = idle;
            }
            check(heart.isEnraged(), "the heart did not enter the fury phase at 10% health", f);
            check(heart.getPhase() == StoneWhaleHeart.PHASE_COUNT - 1, "the fury phase should keep the phase 3 model (phase " + heart.getPhase() + ")", f);
            check(minWarn >= Telegraph.MIN_WARN_TICKS, "a fury telegraph is under 16 ticks (" + minWarn + ")", f);
            check(attacks >= 2, "the fury heart attacked only " + attacks + " times in 900 ticks", f);
            notes.add("heart fury: entered, " + attacks + " attacks, shortest warn " + minWarn);
        } finally {
            drop(ps);
            heart.discard();
            for (int i = 0; i < 3; i++) {
                Telegraph.tickNow(level);
            }
        }
    }
}
