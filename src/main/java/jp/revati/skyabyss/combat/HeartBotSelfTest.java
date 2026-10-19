package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.domain.HeartDomain;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import jp.revati.skyabyss.titan.ember.heart.EmberHeartSetup;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import jp.revati.skyabyss.titan.storm.heart.StormHeartSetup;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import jp.revati.skyabyss.titan.tide.heart.TideHeartSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 心臓四体の「一方向に回り続けるだけでは倒せない」自己テスト (SKYABYSS_SELFTEST。結果は SELFTEST heart bots PASSED / FAILED)。
// オーバーワールドの空に平らな床を作り、その上に心臓を一体ずつ置いて、人形 (傷を数えるだけの FakePlayer) を動かす:
//   回る人形  : 心臓のまわりを一定の速さ (走る速さ) で回り続ける。読まない
//   読む人形  : 回るのを望みながら、床の予告 (Telegraph.dangerAt) を先読みして、止まる・逆へ回る・内外へ出る・跳ぶで避ける
// 確かめること (どちらも、受けた傷は心臓の技の傷を想定の防具 +0 で通した値。HeartBalance.taken):
//   - 回る人形は 60 秒で想定の体力を超える傷を受ける (倒れる)。回り続けの見張りを切った場合 (修正前の動き) の 2 倍以上
//   - 読む人形は 60 秒で想定の体力の 55% 以内 (回りたいのに、予告を読めば生き残れる = 読める・避けられる)
//   - 領域 (体力が半分を切ったとき一度だけ): 宣言のあいだ傷を受けない・宣言 → 領域 → 終わりの順・すべての傷が予告から (読む人形が 45% 以内)・
//     領域の最中に回る人形は傷を受ける・心臓へ与えた傷で砕ける (予告は消え、1.5 倍・終わりの通信)
//   - 回り続けの見張り (AntiStrafe) の数学: 回れば 1.2 秒で「回っている」・先読みは実際の先へ 0.6 マス以内・止まる・逆へ回る・内外へ走ると外れる
//   - 技の名前 (BossCue) が送られ、日英の語がある
public final class HeartBotSelfTest {
    private static final double CX = 1000.5;
    private static final double CZ = 1000.5;
    private static final int FLOOR = 210;
    private static final double RADIUS = 12;
    private static final double SPRINT = 0.28;
    private static final int FIGHT_TICKS = 1200;

    private HeartBotSelfTest() {
    }

    // 傷を数えるだけの人形
    private static final class Bot extends FakePlayer {
        final List<Float> hits = new ArrayList<>();
        final List<String> where = new ArrayList<>();
        String context = "";

        Bot(ServerLevel level, UUID id, String name) {
            super(level, new GameProfile(id, name));
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            hits.add(amount);
            where.add(context + " " + fmt(amount));
            return true;
        }

        double taken(String stage) {
            double sum = 0;
            for (float h : hits) {
                sum += HeartBalance.taken(stage, h);
            }
            return sum;
        }
    }

    private record Rig(String name, String stage, Mob heart, HeartPursuit pursuit, Supplier<HeartDomain> domain, Consumer<ServerLevel> startDomain,
            DoubleBinaryOperator heightAt, List<double[]> refuges) {
    }

    // 潮殻の部屋の岩の島 (中心から ±6、半径 2.2、高さ 2。TideHeartArena)。島の上は床から 1.9 より高いので満ち潮の輪が届かない
    private static final double ISLAND_OFFSET = 6;
    private static final double ISLAND_RADIUS = 2.2;

    private static double islandHeight(double x, double z) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (Math.hypot(x - (CX + sx * ISLAND_OFFSET), z - (CZ + sz * ISLAND_OFFSET)) < ISLAND_RADIUS) {
                    return 2;
                }
            }
        }
        return 0;
    }

    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = 58; cx <= 66; cx++) {
            for (int cz = 58; cz <= 66; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            if (before == Difficulty.PEACEFUL) {
                server.setDifficulty(Difficulty.NORMAL, true);
            }
            buildFloor(level);
            checkMath(level, f, notes);
            List<Supplier<Rig>> rigs = List.of(() -> whale(level), () -> tide(level), () -> storm(level), () -> ember(level));
            for (Supplier<Rig> maker : rigs) {
                checkHeart(level, maker, f, notes);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST heart bots crashed", e);
            f.add("crashed: " + e);
        } finally {
            server.setDifficulty(before, true);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST heart bots note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart bots PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST heart bots FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ---------------------------------------------------------------- 場
    private static void buildFloor(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = 22;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = FLOOR; y <= FLOOR + 16; y++) {
                    p.set((int) Math.floor(CX) + dx, y, (int) Math.floor(CZ) + dz);
                    boolean island = y <= FLOOR + 2 && islandHeight(p.getX() + 0.5, p.getZ() + 0.5) > 0;
                    level.setBlock(p, y == FLOOR || island ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static <T extends Mob> T place(T heart, ServerLevel level, double yOffset) {
        heart.moveTo(CX, FLOOR + 1 + yOffset, CZ, 0f, 0f);
        heart.setPersistenceRequired();
        level.addFreshEntity(heart);
        return heart;
    }

    private static Rig whale(ServerLevel level) {
        StoneWhaleHeart h = place(ModEntities.STONE_WHALE_HEART.get().create(level), level, 0);
        h.skipIntro();
        return new Rig("whale", "stone_whale", h, h.pursuit(), h::domain, h::startDomain, (x, z) -> 0, List.of());
    }

    private static Rig tide(ServerLevel level) {
        TideHeart h = place(TideHeartSetup.TIDE_HEART.get().create(level), level, 0.5);
        h.skipIntro();
        return new Rig("tide", "tide_shell", h, h.pursuit(), h::domain, h::startDomain, HeartBotSelfTest::islandHeight, islandCenters());
    }

    private static Rig storm(ServerLevel level) {
        StormHeart h = place(StormHeartSetup.STORM_HEART.get().create(level), level, 0.5);
        h.skipIntro();
        return new Rig("storm", "thunder_wing", h, h.pursuit(), h::domain, h::startDomain, (x, z) -> 0, List.of());
    }

    private static Rig ember(ServerLevel level) {
        EmberHeart h = place(EmberHeartSetup.EMBER_HEART.get().create(level), level, 0.5);
        h.skipIntro();
        return new Rig("ember", "ember", h, h.pursuit(), h::domain, h::startDomain, (x, z) -> 0, List.of());
    }

    private static List<double[]> islandCenters() {
        List<double[]> out = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                out.add(new double[] {CX + sx * ISLAND_OFFSET, CZ + sz * ISLAND_OFFSET});
            }
        }
        return out;
    }

    private static Bot spawnBot(ServerLevel level, String name, double radius) {
        Bot bot = new Bot(level, UUID.nameUUIDFromBytes(("heart_bot_" + name).getBytes()), "[" + name + "]");
        bot.moveTo(CX + radius, FLOOR + 1, CZ, 0f, 0f);
        level.addNewPlayer(bot);
        return bot;
    }

    private static void despawn(Rig rig, Bot bot) {
        rig.heart().discard();
        if (!bot.isRemoved()) {
            ((ServerLevel) bot.level()).removePlayerImmediately(bot, Entity.RemovalReason.DISCARDED);
        }
    }

    // ---------------------------------------------------------------- 見張りの数学
    private static void checkMath(ServerLevel level, List<String> f, List<String> notes) {
        AntiStrafe a = new AntiStrafe();
        Bot bot = spawnBot(level, "math", 8);
        try {
            // 回る (反時計): 1.2 秒で回っている。先読みは実際の先へ
            double angle = 0;
            double step = SPRINT / 8;
            for (int i = 0; i < 40; i++) {
                angle += step;
                bot.moveTo(CX + Math.cos(angle) * 8, FLOOR + 1, CZ + Math.sin(angle) * 8);
                a.update(CX, CZ, List.of(bot));
            }
            check(f, a.isStrafing(bot) && a.direction(bot) == 1, "a circling bot is not seen as strafing (streak " + a.streak(bot) + ")");
            Vec3 lead = a.predict(bot, 26);
            double future = angle + step * 26;
            double err = lead.distanceTo(new Vec3(CX + Math.cos(future) * 8, FLOOR + 1, CZ + Math.sin(future) * 8));
            check(f, err < 0.6, "lead is " + fmt(err) + " blocks away from the real position 26 ticks later");
            // 逆へ回る: やり直し
            for (int i = 0; i < 4; i++) {
                angle -= step;
                bot.moveTo(CX + Math.cos(angle) * 8, FLOOR + 1, CZ + Math.sin(angle) * 8);
                a.update(CX, CZ, List.of(bot));
            }
            check(f, !a.isStrafing(bot), "reversing did not reset the streak (" + a.streak(bot) + ")");
            // 止まる: 減って外れる
            for (int i = 0; i < 40; i++) {
                a.update(CX, CZ, List.of(bot));
            }
            check(f, a.streak(bot) == 0, "standing still did not clear the streak");
            // 内外へ走る: 回りではない
            for (int i = 0; i < 40; i++) {
                bot.moveTo(CX + 4 + (i % 20) * 0.3, FLOOR + 1, CZ);
                a.update(CX, CZ, List.of(bot));
            }
            check(f, !a.isStrafing(bot), "running straight in and out counted as strafing");
            // 見張りを切ると、回っていても外れる
            a.setEnabled(false);
            angle = 0;
            for (int i = 0; i < 60; i++) {
                angle += step;
                bot.moveTo(CX + Math.cos(angle) * 8, FLOOR + 1, CZ + Math.sin(angle) * 8);
                a.update(CX, CZ, List.of(bot));
            }
            check(f, !a.isStrafing(bot) && a.predict(bot, 26).equals(bot.position()), "a disabled tracker still leads");
            notes.add("anti-strafe math ok (lead error " + fmt(err) + ")");
        } finally {
            level.removePlayerImmediately(bot, Entity.RemovalReason.DISCARDED);
        }
    }

    // ---------------------------------------------------------------- 人形の動き
    private interface Driver {
        // 1 tick ぶん、人形を動かす (位置を書き換える)
        void move(Bot bot, ServerLevel level, int tick);
    }

    // 回る人形: 反時計に、半径 8 を走る速さで
    private static Driver circler() {
        double[] angle = {0};
        return (bot, level, tick) -> {
            angle[0] += SPRINT / 8;
            bot.moveTo(CX + Math.cos(angle[0]) * 8, FLOOR + 1, CZ + Math.sin(angle[0]) * 8);
        };
    }

    private static double arc(int i) {
        return i >= 1 && i <= 13 ? 1.25 * Math.sin(Math.PI * i / 14.0) : 0;
    }

    // 読む人形: 回りたいが、予告 (30 tick 先まで) に当たるなら、16 方向・止まる・跳ぶから、当たらない動きを選ぶ
    private static Driver reader(DoubleBinaryOperator heightAt, List<double[]> refuges) {
        double[] want = {0};
        int[] jump = {0};
        double[] dirs = new double[16];
        for (int i = 0; i < 16; i++) {
            dirs[i] = Math.PI * 2 * i / 16;
        }
        return (bot, level, tick) -> {
            want[0] += SPRINT / 8;
            Vec3 pos = bot.position();
            double dx = CX + Math.cos(want[0]) * 8 - pos.x;
            double dz = CZ + Math.sin(want[0]) * 8 - pos.z;
            double dl = Math.hypot(dx, dz);
            Vec3 desired = pos.add(dl < 1e-6 ? Vec3.ZERO : new Vec3(dx / dl * Math.min(SPRINT, dl), 0, dz / dl * Math.min(SPRINT, dl)));
            double bestCost = Double.MAX_VALUE;
            double bestX = pos.x;
            double bestZ = pos.z;
            boolean bestJump = false;
            // 候補 {1 tick の動き x, z, 最大の歩数}: 16 方向、止まる、回りたい向き、避難所 (岩の島) へ向かう道
            List<double[]> cands = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                cands.add(new double[] {Math.cos(dirs[i]) * SPRINT, Math.sin(dirs[i]) * SPRINT, 999});
            }
            cands.add(new double[] {0, 0, 0});
            cands.add(new double[] {desired.x - pos.x, desired.z - pos.z, 999});
            for (double[] r : refuges) {
                double d = Math.hypot(r[0] - pos.x, r[1] - pos.z);
                if (d > 0.3) {
                    cands.add(new double[] {(r[0] - pos.x) / d * SPRINT, (r[1] - pos.z) / d * SPRINT, d / SPRINT});
                }
            }
            // それぞれ、跳ぶ / 跳ばない (すでに跳んでいる間は跳ぶ・跳ばないを選ばない)
            for (int jmp = 0; jmp < (jump[0] > 0 ? 1 : 2); jmp++) {
                for (double[] c : cands) {
                    double ux = c[0];
                    double uz = c[1];
                    int hits = 0;
                    for (int k = 1; k <= 36; k++) {
                        double kk = Math.min(k, c[2]);
                        double x = pos.x + ux * kk;
                        double z = pos.z + uz * kk;
                        double rr = Math.hypot(x - CX, z - CZ);
                        if (rr > RADIUS - 1) {
                            x = CX + (x - CX) * (RADIUS - 1) / rr;
                            z = CZ + (z - CZ) * (RADIUS - 1) / rr;
                        }
                        int idx = jump[0] > 0 ? jump[0] + k : (jmp == 1 ? k : -100);
                        double y = FLOOR + 1 + heightAt.applyAsDouble(x, z) + arc(idx);
                        if (Telegraph.dangerAt(level, x, y, z, k)) {
                            hits++;
                        }
                    }
                    double cost = hits * 100 + Math.hypot(pos.x + ux - desired.x, pos.z + uz - desired.z) * 5 + (jmp == 1 ? 3 : 0);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestX = pos.x + ux;
                        bestZ = pos.z + uz;
                        bestJump = jmp == 1;
                    }
                }
            }
            if (jump[0] == 0 && bestJump) {
                jump[0] = 1;
            } else if (jump[0] > 0) {
                jump[0]++;
                if (jump[0] > 14) {
                    jump[0] = 0;
                }
            }
            double rr = Math.hypot(bestX - CX, bestZ - CZ);
            if (rr > RADIUS - 1) {
                bestX = CX + (bestX - CX) * (RADIUS - 1) / rr;
                bestZ = CZ + (bestZ - CZ) * (RADIUS - 1) / rr;
            }
            bot.moveTo(bestX, FLOOR + 1 + heightAt.applyAsDouble(bestX, bestZ) + arc(jump[0]), bestZ);
        };
    }

    // ---------------------------------------------------------------- 戦い
    private static void step(Rig rig, Bot bot, Driver driver, ServerLevel level, int tick) {
        HeartDomain dom = rig.domain().get();
        bot.context = (dom == null ? "-" : dom.phase() + "@" + dom.ticks()) + " t" + tick;
        driver.move(bot, level, tick);
        rig.heart().setTarget(bot);
        rig.heart().tickCount++;
        rig.heart().tick();
        Telegraph.tickNow(level);
    }

    // ticks の間戦って、その間に受けた傷 (想定の体力に対する割合)
    private static double fight(Rig rig, Bot bot, Driver driver, ServerLevel level, int ticks) {
        double before = bot.taken(rig.stage());
        for (int t = 0; t < ticks; t++) {
            step(rig, bot, driver, level, t);
        }
        return (bot.taken(rig.stage()) - before) / HeartBalance.expectedHealth(rig.stage());
    }

    private static void checkHeart(ServerLevel level, Supplier<Rig> maker, List<String> f, List<String> notes) {
        // 修正前の動き (見張りを切る): 回る人形
        Rig rig = maker.get();
        Bot bot = spawnBot(level, "base", 8);
        double base;
        try {
            rig.pursuit().setDisabled(true);
            base = fight(rig, bot, circler(), level, FIGHT_TICKS);
        } finally {
            despawn(rig, bot);
        }
        // 回る人形
        rig = maker.get();
        bot = spawnBot(level, "circle", 8);
        double circle;
        int fired;
        try {
            circle = fight(rig, bot, circler(), level, FIGHT_TICKS);
            fired = rig.pursuit().fired();
        } finally {
            despawn(rig, bot);
        }
        String name = rig.name();
        check(f, circle >= 1.0, name + ": a constant circler took only " + fmt(circle) + " of the expected health in 60 s (needs >= 1.0)");
        check(f, circle >= 2 * Math.max(base, 0.05), name + ": the fix barely changes a circler (" + fmt(circle) + " vs " + fmt(base) + " before)");
        check(f, fired >= 5, name + ": only " + fired + " pursuit moves in 60 s of circling");
        // 読む人形
        rig = maker.get();
        bot = spawnBot(level, "reader", 8);
        double read;
        String readHits;
        try {
            read = fight(rig, bot, reader(rig.heightAt(), rig.refuges()), level, FIGHT_TICKS);
            readHits = bot.where.toString();
        } finally {
            despawn(rig, bot);
        }
        check(f, read <= 0.55, name + ": a telegraph-reading bot took " + fmt(read) + " of the expected health in 60 s (needs <= 0.55): " + readHits);
        notes.add(String.format(Locale.ROOT, "%s: circler %.2f (was %.2f without the fix, %d pursuit moves), reader %.2f", name, circle, base, fired, read));
        checkDomain(level, maker, f, notes);
    }

    private static void checkDomain(ServerLevel level, Supplier<Rig> maker, List<String> f, List<String> notes) {
        // 読む人形で領域を通す: 宣言 (傷なし) → 領域 → 終わり
        Rig rig = maker.get();
        String name = rig.name();
        Bot bot = spawnBot(level, "domain", 8);
        try {
            Driver driver = reader(rig.heightAt(), rig.refuges());
            rig.heart().setHealth(rig.heart().getMaxHealth() * 0.45f);
            boolean sawDeclare = false;
            boolean sawActive = false;
            boolean sawDone = false;
            float healthInDeclare = -1;
            float healthAfterDeclare = -1;
            double taken = 0;
            int cues = BossCue.sentCount();
            for (int t = 0; t < 60 + HeartDomain.DECLARE_TICKS + HeartDomain.ACTIVE_TICKS + 200 && !sawDone; t++) {
                step(rig, bot, driver, level, t);
                HeartDomain d = rig.domain().get();
                if (d != null) {
                    if (d.phase() == HeartDomain.Phase.DECLARE) {
                        if (!sawDeclare) {
                            sawDeclare = true;
                            // 宣言のあいだは傷を受けない
                            rig.heart().invulnerableTime = 0;
                            healthInDeclare = rig.heart().getHealth();
                            rig.heart().hurt(rig.heart().damageSources().generic(), 40f);
                            healthAfterDeclare = rig.heart().getHealth();
                        }
                    } else if (d.phase() == HeartDomain.Phase.ACTIVE) {
                        sawActive = true;
                    } else if (d.phase() == HeartDomain.Phase.DONE) {
                        sawDone = true;
                    }
                }
            }
            taken = bot.taken(rig.stage()) / HeartBalance.expectedHealth(rig.stage());
            check(f, sawDeclare && sawActive && sawDone, name + ": the domain did not run declare -> active -> done (" + sawDeclare + sawActive + sawDone + ")");
            check(f, healthInDeclare > 0 && healthInDeclare == healthAfterDeclare, name + ": the heart took damage while declaring its domain");
            check(f, rig.domain().get() != null && !rig.domain().get().broken(), name + ": an undisturbed domain reports broken");
            check(f, BossCue.sentCount() > cues, name + ": no attack names were sent during the domain");
            check(f, taken <= 0.45, name + ": a telegraph-reading bot took " + fmt(taken) + " of the expected health through the domain (needs <= 0.45): " + bot.where);
            check(f, jp.revati.skyabyss.titan.domain.HeartDomain.lastPayload() != null
                    && jp.revati.skyabyss.titan.domain.HeartDomain.lastPayload().state() == jp.revati.skyabyss.titan.domain.DomainPayload.END,
                    name + ": no end packet after the domain");
            notes.add(String.format(Locale.ROOT, "%s domain: reader %.2f", name, taken));
        } finally {
            despawn(rig, bot);
        }
        // 回る人形は領域のあいだ傷を受ける (領域の決まりは回り続けを許さない)
        rig = maker.get();
        bot = spawnBot(level, "domain_circle", 8);
        try {
            Driver driver = circler();
            rig.heart().setHealth(rig.heart().getMaxHealth() * 0.45f);
            int guard = 0;
            while ((rig.domain().get() == null || rig.domain().get().phase() != HeartDomain.Phase.ACTIVE) && guard++ < 400) {
                step(rig, bot, driver, level, guard);
            }
            double before = bot.taken(rig.stage());
            for (int t = 0; t < 400; t++) {
                step(rig, bot, driver, level, t);
            }
            double taken = (bot.taken(rig.stage()) - before) / HeartBalance.expectedHealth(rig.stage());
            check(f, taken >= 0.35, name + ": a constant circler took only " + fmt(taken) + " in 20 s of the domain (needs >= 0.35)");
            notes.add(String.format(Locale.ROOT, "%s domain: circler %.2f in 20 s", name, taken));
        } finally {
            despawn(rig, bot);
        }
        // 砕く: 領域のあいだに心臓へ与えた傷で砕け、予告は消え、1.5 倍・終わりの通信
        rig = maker.get();
        bot = spawnBot(level, "domain_break", 8);
        try {
            Driver driver = reader(rig.heightAt(), rig.refuges());
            rig.heart().setHealth(rig.heart().getMaxHealth() * 0.45f);
            int guard = 0;
            while ((rig.domain().get() == null || rig.domain().get().phase() != HeartDomain.Phase.ACTIVE) && guard++ < 400) {
                step(rig, bot, driver, level, guard);
            }
            HeartDomain d = rig.domain().get();
            check(f, d != null && d.phase() == HeartDomain.Phase.ACTIVE, name + ": the domain never became active");
            if (d != null) {
                for (int t = 0; t < 40; t++) {
                    step(rig, bot, driver, level, t);
                }
                check(f, Telegraph.activeCount(level) > 0, name + ": the domain shows no telegraphs");
                float start = rig.heart().getHealth();
                int hits = 0;
                while (d.phase() == HeartDomain.Phase.ACTIVE && hits++ < 80) {
                    rig.heart().invulnerableTime = 0;
                    rig.heart().hurt(rig.heart().damageSources().generic(), 40f);
                }
                check(f, d.phase() == HeartDomain.Phase.DONE && d.broken(), name + ": the domain did not break after " + hits + " hits (" + d.percent() + "%)");
                float lost = start - rig.heart().getHealth();
                check(f, lost >= d.breakNeeded() && lost < d.breakNeeded() + 60, name + ": lost " + fmt(lost) + " health, break needs " + fmt(d.breakNeeded()));
                check(f, d.takenMultiplier() > 1.4f, name + ": no 1.5x window after the shatter");
                Telegraph.tickNow(level);
                check(f, Telegraph.activeCount(level) == 0, name + ": telegraphs remain after the shatter (" + Telegraph.activeCount(level) + ")");
                jp.revati.skyabyss.titan.domain.DomainPayload p = HeartDomain.lastPayload();
                check(f, p != null && p.state() == jp.revati.skyabyss.titan.domain.DomainPayload.END && p.a() == 1, name + ": no shatter packet");
                // 砕けたあと、心臓は技へ戻る
                for (int t = 0; t < 200; t++) {
                    step(rig, bot, driver, level, t);
                }
                check(f, !d.running(), name + ": the domain is still running after it broke");
                notes.add(name + " domain break ok (" + fmt(lost) + " health)");
            }
        } finally {
            despawn(rig, bot);
        }
    }
}
