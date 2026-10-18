package jp.revati.skyabyss.titan.storm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import jp.revati.skyabyss.titan.storm.heart.StormHeartArena;
import jp.revati.skyabyss.titan.storm.heart.StormHeartSetup;
import jp.revati.skyabyss.titan.storm.raiju.Raiju;
import jp.revati.skyabyss.titan.storm.raiju.RaijuNest;
import jp.revati.skyabyss.titan.storm.raiju.RaijuSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 雷翼の心臓と雷獣の予告 (Telegraph) の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST storm_telegraph PASSED / FAILED。docs/design/12 の 5.2・5.3節
//  A. 心臓の九つの技: 予告が 0.8 秒 (16 tick) 以上、床に安全な所が残る (傷のある予告が一度も当たらなかった点の割合)、
//     1 人と 6 人で傷のある予告の数が増える、どの技も最後まで回る
//  B. 印と当たりが同じ形: 招雷・雷の鎖・羽根の雨・閃光の扇 (向きで当たる)・風の刃 (しゃがめば当たらない)・雷の網 (跳べば当たらない)・帯電の床 (空中は当たらない)。
//     傷は予告の 16 tick より前に出ない
//  C. 雷獣の爪・飛びかかり・咆哮 (傷は印の中だけ、着地点が印の中心、人数で増える)。
//     雷渡りとその枝分かれ (人数で増える) は RaijuSelfTest が巣で調べる
//  D. 広間・巣に収まる: 心臓の技の長さ・半径が竜骨の間の半径に収まり、雷獣の技の合計が巣の床の 4 割以下
public final class StormTelegraphSelfTest {
    private static final double SX = 8.5;
    private static final double SZ = 8.5;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;
    // 試験の床 (円盤) の半径: 竜骨の間の外の心臓は部屋の半径 12 と見なす。雷獣の巣は半径 13.5 なので、これで測れば厳しめになる
    private static final double DISC = 12;

    private StormTelegraphSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("storm telegraph (load)", StormTelegraphSelfTest::load),
                SelfTestRunner.Step.once("storm telegraph", 30, StormTelegraphSelfTest::run));
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
            hearts(level, f, notes);
            markers(level, f, notes);
            raiju(level, f, notes);
            rooms(f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm_telegraph crashed", e);
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
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST storm_telegraph note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm_telegraph PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST storm_telegraph FAILED: {}", m));
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
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("storm telegraph " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[st_" + tag + "]")) {
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

    // 向きを決める (視線は頭の向きで決まるので、頭と体も合わせる)
    private static void face(FakePlayer p, float yaw) {
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setYBodyRot(yaw);
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

    private static void tickHeart(ServerLevel level, StormHeart h, int ticks) {
        for (int i = 0; i < ticks; i++) {
            h.tickCount++;
            h.tick();
            Telegraph.tickNow(level);
        }
    }

    private static StormHeart spawnHeart(ServerLevel level) {
        StormHeart h = StormHeartSetup.STORM_HEART.get().create(level);
        h.moveTo(SX, FLOOR + 2, SZ, 0f, 0f);
        h.setPersistenceRequired();
        h.skipIntro();
        h.holdAttacks();
        level.addFreshEntity(h);
        return h;
    }

    private static void clearMoths(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(SX - 30, FLOOR - 3, SZ - 30, SX + 30, FLOOR + 20, SZ + 30))) {
            if (!(e instanceof net.minecraft.world.entity.player.Player) && e.getType().toString().contains("static_moth")) {
                e.discard();
            }
        }
    }

    // ---------------------------------------------------------------- A. 心臓の九つの技

    private static void hearts(ServerLevel level, List<String> f, List<String> notes) {
        Map<StormHeart.Action, int[]> counts = new HashMap<>();
        Map<StormHeart.Action, int[]> safePercent = new HashMap<>();
        List<StormHeart.Action> all = new ArrayList<>();
        for (int phase = 0; phase < 3; phase++) {
            all.addAll(List.of(StormHeart.bagFor(phase)));
        }
        for (int n : new int[] {1, 6}) {
            for (StormHeart.Action attack : all) {
                StormHeart heart = spawnHeart(level);
                List<FakePlayer> ps = new ArrayList<>();
                try {
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n + 0.4;
                        ps.add(fake(level, "h" + n + "_" + i, SX + Math.cos(a) * 8, FLOOR, SZ + Math.sin(a) * 8));
                    }
                    heart.setTarget(ps.get(0));
                    heart.tick();
                    Telegraph.probe(grid(SX, SZ, DISC));
                    Telegraph.resetStats();
                    heart.forceAction(attack);
                    int minWarn = Integer.MAX_VALUE;
                    boolean finished = false;
                    for (int i = 0; i < 400; i++) {
                        tickHeart(level, heart, 1);
                        for (int w : Telegraph.activeWarns(level)) {
                            minWarn = Math.min(minWarn, w);
                        }
                        if (i == 12) {
                            for (FakePlayer p : ps) {
                                check(lost(p) == 0, attack + " (n=" + n + ") hurt a player before the 16-tick warning", f);
                            }
                        }
                        if (i > 20 && heart.actionTickCount() == 0 && Telegraph.activeCount(level) == 0) {
                            finished = true;
                            break;
                        }
                    }
                    check(finished, attack + " (n=" + n + ") did not finish", f);
                    check(minWarn >= Telegraph.MIN_WARN_TICKS, attack + " has a telegraph under 16 ticks (" + minWarn + ")", f);
                    boolean[] hits = Telegraph.probeHits();
                    int percent = Math.round(100f * safeCount(hits) / hits.length);
                    check(percent >= 25, attack + " (n=" + n + ") leaves only " + percent + "% of the floor safe (needs 25%)", f);
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
        for (StormHeart.Action attack : all) {
            int[] c = counts.get(attack);
            int[] s = safePercent.get(attack);
            sb.append(' ').append(attack.key()).append(' ').append(c[0]).append("->").append(c[1]).append('/').append(s[0]).append("%->").append(s[1]).append('%');
            check(c[1] > c[0], attack + ": 6 players made " + c[1] + " areas, 1 player made " + c[0] + " (must grow with the party)", f);
        }
        notes.add(sb.toString());
    }

    // ---------------------------------------------------------------- B. 印と当たりが同じ形

    // 技を一つ出し、2 tick で予告が出てから、印の内と外の人を置く (sink は予告のあとに立てる人)。ticks 進めたあと各人の傷を返す
    private interface Setup {
        List<FakePlayer> placeAfter(ServerLevel level, StormHeart heart);
    }

    private static void markers(ServerLevel level, List<String> f, List<String> notes) {
        // 招雷: 円 (半径 1.5)。中心・1.2 離れた人は傷、2.4 離れた人は無傷
        List<FakePlayer> extra = new ArrayList<>();
        StormHeart h = spawnHeart(level);
        FakePlayer p = fake(level, "sum_p", SX + 8, FLOOR, SZ);
        try {
            h.setTarget(p);
            h.tick();
            h.forceAction(StormHeart.Action.SUMMON);
            tickHeart(level, h, 2);
            FakePlayer in = fake(level, "sum_in", SX + 9.2, FLOOR, SZ);
            FakePlayer out = fake(level, "sum_out", SX + 10.4, FLOOR, SZ);
            extra.addAll(List.of(in, out));
            tickHeart(level, h, 40);
            check(lost(p) >= StormHeart.SUMMON_DAMAGE * 0.7f, "summon: the marked player was not hurt (" + lost(p) + ")", f);
            check(lost(in) > 0, "summon: a player inside the circle was not hurt", f);
            check(lost(out) == 0, "summon: a player outside the circle was hurt", f);
        } finally {
            drop(extra);
            p.discard();
            h.discard();
        }

        // 雷の鎖 (一人: 人へ向かう長さ 20・半幅 1): 線の上は傷、横 2.6 は無傷
        h = spawnHeart(level);
        p = fake(level, "ch_p", SX + 8, FLOOR, SZ);
        try {
            h.setTarget(p);
            h.tick();
            h.forceAction(StormHeart.Action.CHAIN);
            tickHeart(level, h, 2);
            FakePlayer onLine = fake(level, "ch_in", SX + 14, FLOOR, SZ + 0.4);
            FakePlayer beside = fake(level, "ch_out", SX + 14, FLOOR, SZ + 2.8);
            FakePlayer beyond = fake(level, "ch_far", SX + 21, FLOOR, SZ);
            extra.addAll(List.of(onLine, beside, beyond));
            tickHeart(level, h, 40);
            check(lost(onLine) >= StormHeart.CHAIN_DAMAGE * 0.7f, "chain: a player on the line was not hurt (" + lost(onLine) + ")", f);
            check(lost(beside) == 0, "chain: a player beside the line was hurt", f);
            check(lost(beyond) == 0, "chain: a player past the end of the line was hurt", f);
        } finally {
            drop(extra);
            p.discard();
            h.discard();
        }

        // 羽根の雨: 円 (半径 2)。1.8 離れた人は傷、2.6 は無傷
        h = spawnHeart(level);
        p = fake(level, "fe_p", SX + 8, FLOOR, SZ);
        try {
            h.setTarget(p);
            h.tick();
            h.forceAction(StormHeart.Action.FEATHER);
            tickHeart(level, h, 2);
            FakePlayer in = fake(level, "fe_in", SX + 9.7, FLOOR, SZ);
            FakePlayer out = fake(level, "fe_out", SX + 10.7, FLOOR, SZ);
            extra.addAll(List.of(in, out));
            tickHeart(level, h, 40);
            check(lost(in) >= StormHeart.FEATHER_DAMAGE * 0.7f, "feather: a player inside the circle was not hurt", f);
            check(lost(out) == 0, "feather: a player outside the circle was hurt", f);
        } finally {
            drop(extra);
            p.discard();
            h.discard();
        }

        // 閃光: 人へ向く扇 (半角 22 度・射程 13)。扇の中で心臓を見ている人は傷、背を向けた人・扇の外の人は無傷
        h = spawnHeart(level);
        p = fake(level, "fl_p", SX + 8, FLOOR, SZ);
        try {
            face(p, 90f);
            h.setTarget(p);
            h.tick();
            h.forceAction(StormHeart.Action.FLASH);
            tickHeart(level, h, 2);
            FakePlayer facing = fake(level, "fl_in", SX + 6, FLOOR, SZ + 1.2);
            face(facing, 90f);
            FakePlayer away = fake(level, "fl_away", SX + 6, FLOOR, SZ - 1.2);
            face(away, -90f);
            FakePlayer outside = fake(level, "fl_out", SX + 5, FLOOR, SZ + 7);
            face(outside, 135f);
            extra.addAll(List.of(facing, away, outside));
            tickHeart(level, h, 50);
            check(lost(p) >= StormHeart.FLASH_DAMAGE * 0.7f && lost(facing) >= StormHeart.FLASH_DAMAGE * 0.7f, "flash: a player looking at the heart inside the fan was not hurt (target " + lost(p) + ", beside " + lost(facing) + ")", f);
            check(lost(away) == 0, "flash: a player with their back turned was hurt", f);
            check(lost(outside) == 0, "flash: a player outside the fan was hurt", f);
        } finally {
            drop(extra);
            p.discard();
            h.discard();
        }

        // 風の刃: 回る帯。立っている人は傷、しゃがんだ人は無傷 (頭の高さを通る)
        h = spawnHeart(level);
        p = fake(level, "bl_p", SX + 6, FLOOR, SZ);
        try {
            h.setTarget(p);
            h.tick();
            h.forceAction(StormHeart.Action.BLADE);
            tickHeart(level, h, 2);
            FakePlayer crouch = fake(level, "bl_c", SX + 6.4, FLOOR, SZ + 0.4);
            crouch.setPose(Pose.CROUCHING);
            crouch.refreshDimensions();
            extra.add(crouch);
            boolean crouched = crouch.getBoundingBox().getYsize() < 1.6;
            tickHeart(level, h, 90);
            check(lost(p) >= StormHeart.BLADE_DAMAGE * 0.7f, "blade: a standing player on the swept band was not hurt (" + lost(p) + ")", f);
            if (crouched) {
                check(lost(crouch) == 0, "blade: a crouching player was hurt", f);
            } else {
                notes.add("blade crouch check skipped (fake player pose not applied)");
            }
        } finally {
            drop(extra);
            p.discard();
            h.discard();
        }

        // 雷の網: 回る腕 (180 度ぶんを通る)。地面の人は半分近くが傷、跳んでいる人 (床から 1.5) は一人も当たらない
        h = spawnHeart(level);
        List<FakePlayer> ground = new ArrayList<>();
        List<FakePlayer> air = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                ground.add(fake(level, "web_g" + i, SX + Math.cos(a) * 6, FLOOR, SZ + Math.sin(a) * 6));
                air.add(fake(level, "web_a" + i, SX + Math.cos(a) * 9, FLOOR + 1.5, SZ + Math.sin(a) * 9));
            }
            h.setTarget(ground.get(0));
            h.tick();
            h.forceAction(StormHeart.Action.WEB);
            tickHeart(level, h, 140);
            int hurtGround = 0;
            int hurtAir = 0;
            for (FakePlayer q : ground) {
                hurtGround += lost(q) > 0 ? 1 : 0;
            }
            for (FakePlayer q : air) {
                hurtAir += lost(q) > 0 ? 1 : 0;
            }
            check(hurtGround >= 3 && hurtGround <= 9, "web: " + hurtGround + " of 12 players on the floor were hurt (expect about half: 180 degrees swept)", f);
            check(hurtAir == 0, "web: " + hurtAir + " players above the arm (jumping) were hurt", f);
            notes.add("web: " + hurtGround + "/12 on the floor hurt, " + hurtAir + "/12 jumping hurt");
        } finally {
            drop(ground);
            drop(air);
            h.discard();
        }

        // 帯電の床: 円 (半径 3) の中の人は 0.5 秒ごとに何度も、円の中でも空中 (床から 1.5) の人は当たらない
        h = spawnHeart(level);
        p = fake(level, "cg_p", SX + 8, FLOOR, SZ);
        FakePlayer flier = fake(level, "cg_a", SX - 8, FLOOR + 1.5, SZ);
        try {
            h.setTarget(p);
            h.tick();
            h.forceAction(StormHeart.Action.CHARGE);
            tickHeart(level, h, 110);
            check(lost(p) >= StormHeart.CHARGE_DAMAGE * 4, "charge: a player in the circle was hurt " + lost(p) + " (expect about 6 hits of " + StormHeart.CHARGE_DAMAGE + ")", f);
            check(lost(flier) == 0, "charge: a player in the air above a circle was hurt", f);
        } finally {
            flier.discard();
            p.discard();
            h.discard();
        }
        Telegraph.tickNow(level);
        notes.add("markers: summon, chain, feather, flash, blade, web, charge match their areas");
    }

    // ---------------------------------------------------------------- C. 雷獣

    private static Raiju spawnRaiju(ServerLevel level) {
        Raiju r = RaijuSetup.RAIJU.get().create(level);
        r.moveTo(SX, FLOOR, SZ, 0f, 0f);
        r.setPersistenceRequired();
        r.holdAttacks();
        level.addFreshEntity(r);
        return r;
    }

    private static void tickRaiju(ServerLevel level, Raiju r, int ticks) {
        for (int i = 0; i < ticks; i++) {
            r.tickCount++;
            r.tick();
            Telegraph.tickNow(level);
        }
    }

    private static void raiju(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        Raiju r = spawnRaiju(level);
        try {
            // 爪の連打: 前の扇 (射程 3.6)。前 2.5 は傷 (3 回)、後ろ 2.5・前 5.5 は無傷。傷は 16 tick より前に出ない
            FakePlayer front = fake(level, "cl_f", SX, FLOOR, SZ + 2.5);
            ps.add(front);
            tickRaiju(level, r, 2);
            r.forceAction(Raiju.Action.CLAW);
            tickRaiju(level, r, 1);
            // 印が出てから、扇の外の人を置く (置いた人は、印の組み立てには数えない)
            FakePlayer back = fake(level, "cl_b", SX, FLOOR, SZ - 2.5);
            FakePlayer far = fake(level, "cl_far", SX, FLOOR, SZ + 5.5);
            ps.addAll(List.of(back, far));
            int firstHurt = -1;
            for (int i = 2; i <= Raiju.CLAW_TICKS + 4; i++) {
                tickRaiju(level, r, 1);
                if (firstHurt < 0 && front.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            check(firstHurt >= Telegraph.MIN_WARN_TICKS, "raiju claw hurt after only " + firstHurt + " ticks", f);
            check(lost(front) >= Raiju.CLAW_DAMAGE * 2.5f, "raiju claw hurt the front player " + lost(front) + " (expect 3 hits of " + Raiju.CLAW_DAMAGE + ")", f);
            check(lost(back) == 0 && lost(far) == 0, "raiju claw hurt a player outside the cone", f);
            notes.add("claw warns " + firstHurt + " ticks, front hurt " + Math.round(lost(front)));
        } finally {
            drop(ps);
            r.discard();
        }

        // 飛びかかり: 着く所 (狙われた人の足もと) に円 (半径 2.4)。着地点は印の中心。円の中は傷、外は無傷。跳んでいる途中は当たらない
        r = spawnRaiju(level);
        try {
            double lx = SX;
            double lz = SZ + 8;
            FakePlayer center = fake(level, "pn_c", lx, FLOOR, lz);
            ps.add(center);
            r.setTarget(center);
            tickRaiju(level, r, 2);
            Telegraph.resetStats();
            r.forceAction(Raiju.Action.POUNCE);
            tickRaiju(level, r, 1);
            // 印が出てから、道の途中 (跳んでいる間に通る所)・円の縁・円の外に人を置く
            FakePlayer onPath = fake(level, "pn_path", SX, FLOOR, SZ + 4);
            FakePlayer edge = fake(level, "pn_e", lx + 2.0, FLOOR, lz);
            FakePlayer out = fake(level, "pn_o", lx + 3.4, FLOOR, lz);
            ps.addAll(List.of(onPath, edge, out));
            List<Integer> warns = Telegraph.activeWarns(level);
            check(!warns.isEmpty() && warns.stream().allMatch(w -> w >= Telegraph.MIN_WARN_TICKS), "raiju pounce marker warns under 16 ticks " + warns, f);
            tickRaiju(level, r, Raiju.POUNCE_WINDUP + Raiju.POUNCE_FLIGHT + 6);
            check(lost(center) >= Raiju.POUNCE_DAMAGE * 0.7f, "raiju pounce missed the marked player (" + lost(center) + ")", f);
            check(lost(edge) > 0, "raiju pounce missed a player inside the circle", f);
            check(lost(out) == 0, "raiju pounce hurt a player outside the circle", f);
            check(lost(onPath) == 0, "raiju hurt a player on its flight path (only the landing circle is a marker)", f);
            check(Math.abs(r.getX() - lx) < 0.3 && Math.abs(r.getZ() - lz) < 0.3, "raiju did not land on the marked point (" + r.position() + ")", f);
            notes.add("pounce lands on the marker, flight path is safe");
        } finally {
            drop(ps);
            r.discard();
        }

        // 人数で増える: 爪の線 / 飛びかかりの雷の円 / 咆哮の輪 (1 人と 6 人)。雷獣の技の合計が巣の床の 4 割以下
        Map<String, int[]> counts = new HashMap<>();
        Map<String, int[]> union = new HashMap<>();
        for (int n : new int[] {1, 6}) {
            for (Raiju.Action a : new Raiju.Action[] {Raiju.Action.CLAW, Raiju.Action.POUNCE, Raiju.Action.ROAR}) {
                for (boolean angry : new boolean[] {false, true}) {
                    if (a == Raiju.Action.ROAR && angry) {
                        continue;
                    }
                    Raiju rr = spawnRaiju(level);
                    try {
                        for (int i = 0; i < n; i++) {
                            double ang = Math.PI * 2 * i / n + 0.4;
                            ps.add(fake(level, "rj" + n + "_" + i, SX + Math.cos(ang) * 8, FLOOR, SZ + Math.sin(ang) * 8));
                        }
                        if (angry) {
                            rr.makeAngryForTest();
                        }
                        tickRaiju(level, rr, 2);
                        Telegraph.probe(grid(SX, SZ, DISC));
                        Telegraph.resetStats();
                        rr.forceAction(a);
                        int minWarn = Integer.MAX_VALUE;
                        for (int i = 0; i < 160; i++) {
                            tickRaiju(level, rr, 1);
                            for (int w : Telegraph.activeWarns(level)) {
                                minWarn = Math.min(minWarn, w);
                            }
                            if (i > 20 && rr.currentAction() == Raiju.Action.NONE && Telegraph.activeCount(level) == 0) {
                                break;
                            }
                        }
                        check(minWarn >= Telegraph.MIN_WARN_TICKS, "raiju " + a + " has a telegraph under 16 ticks (" + minWarn + ")", f);
                        boolean[] hits = Telegraph.probeHits();
                        int hitPercent = 100 - Math.round(100f * safeCount(hits) / hits.length);
                        String key = a.name() + (angry ? " (angry)" : "");
                        check(hitPercent <= 40, "raiju " + key + " (n=" + n + ") covers " + hitPercent + "% of the floor (limit 40%)", f);
                        counts.computeIfAbsent(key, k -> new int[2])[n == 1 ? 0 : 1] = Telegraph.damagingCreated();
                        union.computeIfAbsent(key, k -> new int[2])[n == 1 ? 0 : 1] = hitPercent;
                    } finally {
                        Telegraph.probe(null);
                        drop(ps);
                        rr.discard();
                        clearMoths(level);
                        for (int i = 0; i < 3; i++) {
                            Telegraph.tickNow(level);
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder("raiju (areas 1p -> 6p / floor covered 1p -> 6p):");
        for (var e : counts.entrySet()) {
            int[] c = e.getValue();
            int[] u = union.get(e.getKey());
            sb.append(' ').append(e.getKey().toLowerCase()).append(' ').append(c[0]).append("->").append(c[1]).append('/').append(u[0]).append("%->").append(u[1]).append('%');
            check(c[1] > c[0], "raiju " + e.getKey() + ": 6 players made " + c[1] + " areas, 1 player made " + c[0] + " (must grow with the party)", f);
        }
        notes.add(sb.toString());
    }

    // ---------------------------------------------------------------- D. 広間・巣に収まる

    private static void rooms(List<String> f, List<String> notes) {
        // 竜骨の間 (内側の半径 15): 回る帯・腕・扇・広がる輪の射程が部屋に収まり、扇・腕の合計が床の 4 割以下 (輪は跳べる・安全な筋あり)
        double radius = StormHeartArena.RADIUS;
        double floor = Math.PI * radius * radius;
        check(StormHeart.BLADE_REACH <= radius - 1 && StormHeart.WEB_REACH <= radius - 1 && StormHeart.FLASH_RANGE <= radius - 1, "a heart reach is beyond the keel chamber", f);
        double halfDisc = 0.5 * Math.PI * StormHeart.BLADE_REACH * StormHeart.BLADE_REACH;
        double flash = 4 * Math.PI * StormHeart.FLASH_RANGE * StormHeart.FLASH_RANGE * (2 * StormHeart.FLASH_HALF_ANGLE / 360);
        check(halfDisc / floor <= 0.4 && flash / floor <= 0.4, "the largest heart sweep / fan covers more than 40% of the keel chamber", f);
        // 雷獣の巣 (内側の半径 13.5): 飛びかかりの輪と円の合計、咆哮の円 12 個
        double nest = Math.PI * RaijuNest.RADIUS * RaijuNest.RADIUS;
        double ring = Math.PI * Raiju.POUNCE_RING_REACH * Raiju.POUNCE_RING_REACH;
        double roar = 12 * Math.PI * 1.5 * 1.5;
        check(Raiju.POUNCE_RING_REACH <= RaijuNest.RADIUS - 2 && ring / nest <= 0.4 && roar / nest <= 0.4, "the raiju areas do not fit the nest", f);
        notes.add(String.format("rooms: keel chamber radius %.0f (half-disc sweep %.0f%%, four fans %.0f%%), nest radius %.1f (pounce ring %.0f%%, twelve roar circles %.0f%%)",
                radius, 100 * halfDisc / floor, 100 * flash / floor, RaijuNest.RADIUS, 100 * ring / nest, 100 * roar / nest));
    }
}
