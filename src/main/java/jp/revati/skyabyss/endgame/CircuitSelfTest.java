package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 巡り路 (Circuits) の自己テスト。SKYABYSS_SELFTEST_ONLY="circuit"
//   行き先がその土地の中で、離れすぎず近すぎない / 守る・倒す・運ぶがそれぞれ越えられて印が増える / 時間切れは印なしで次へ /
//   報い (印 × 鐘銭・一日 3 回まで) / 土地の点 (週・名声)
public final class CircuitSelfTest {
    private CircuitSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c1c01"), "circuit_test"));
        List<CircuitRun> made = new ArrayList<>();
        try {
            p.getPersistentData().remove(Circuits.DAY);
            p.getPersistentData().remove(Circuits.RUNS);
            p.getPersistentData().remove(RegionPoints.WEEK);
            p.getPersistentData().remove(RegionPoints.FAME);
            ContinentMap map = ContinentMap.get();
            BlockPos town = new BlockPos(-120, map.surface(-120, -760) + 1, -760);
            CircuitRun run = Circuits.start(server, List.of(p), ContinentMap.WHALE, town);
            made.add(run);
            check(f, Circuits.runOf(p) == run && run.choosing() && run.offers.size() == 2, "a circuit did not start with two pillars to choose");
            for (int i = 0; i < 6; i++) {
                BlockPos from = run.spot == null ? town : run.spot;
                Circuits.nextTask(level, run);
                check(f, run.offers.size() == 2 && run.offers.get(0).kind() != run.offers.get(1).kind()
                        && !(run.offers.get(0).bonus() == CircuitRun.Bonus.NONE && run.offers.get(1).bonus() == CircuitRun.Bonus.NONE),
                        "the two pillars are the same kind or both plain");
                check(f, !Circuits.taskLine(run).getString().isBlank(), "choosing a pillar has no line");
                for (CircuitRun.Offer o : run.offers) {
                    double d = Math.hypot(o.spot().getX() - from.getX(), o.spot().getZ() - from.getZ());
                    check(f, map.region(o.spot().getX(), o.spot().getZ()) == ContinentMap.WHALE, "a circuit pillar left its region: " + o.spot());
                    check(f, d >= Circuits.SPOT_MIN - 1 && d <= Circuits.SPOT_MAX + 1, "a circuit pillar is too near or far: " + (int) d);
                    if (o.kind() == CircuitRun.Kind.CARRY) {
                        check(f, o.spot2() != null && map.region(o.spot2().getX(), o.spot2().getZ()) == ContinentMap.WHALE, "a carry pillar has no drop point in the region");
                    }
                }
                Circuits.adopt(level, run, i % 2);
                check(f, !run.choosing() && run.kind != null && !Circuits.taskLine(run).getString().isBlank(), "adopting a pillar did not start its task");
            }
            // 柱へ歩いて近づくと、その柱の課題になる
            BlockPos here0 = p.blockPosition();
            Circuits.nextTask(level, run);
            CircuitRun.Offer second = run.offers.get(1);
            run.offers.set(1, new CircuitRun.Offer(second.kind(), here0, second.place(), second.spot2(), second.place2(), second.bonus(), second.poi(), second.poi2()));
            Circuits.step(level, run, false);
            check(f, !run.choosing() && run.kind == second.kind() && run.bonus == second.bonus(), "walking to a pillar did not choose it");
            discard(level, run);
            check(f, run.usedPois.size() >= 3, "circuit tasks do not use the region's sights: " + run.usedPois.size());

            // 守る: 柱のそばにいるあいだだけ進む
            BlockPos here = p.blockPosition();
            run.offers.clear();
            run.bonus = CircuitRun.Bonus.NONE;
            run.kind = CircuitRun.Kind.GUARD;
            run.phase = 0;
            run.progress = 0;
            run.spot = here.offset(30, 0, 0);
            run.taskStart = level.getGameTime();
            Circuits.step(level, run, false);
            check(f, run.phase == 0, "guarding started while nobody was near");
            run.spot = here;
            for (int i = 0; i < Circuits.GUARD_NEED / 10 + 2 && run.marks == 0; i++) {
                Circuits.step(level, run, false);
            }
            check(f, run.marks == 1, "guarding next to the pillar did not clear the task");
            discard(level, run);

            // 倒す: 着くと湧き、倒した数で越える。倒さずに消えた敵は越えたことにならず、戻ると湧き直す
            run.offers.clear();
            run.bonus = CircuitRun.Bonus.NONE;
            run.kind = CircuitRun.Kind.HUNT;
            run.phase = 0;
            run.need = 0;
            run.progress = 0;
            run.spot = here;
            run.taskStart = level.getGameTime();
            Circuits.step(level, run, false);
            if (run.marks == 1) {
                int spawned = run.mobs.size();
                discard(level, run);
                Circuits.step(level, run, false);
                check(f, run.marks == 1 && run.phase == 0, "foes that vanished without dying cleared the hunt");
                Circuits.step(level, run, false);
                check(f, spawned == 0 || !run.mobs.isEmpty(), "the hunt did not respawn the missing foes");
                // 倒しきるまで (湧きが足りなければ湧き直す)
                for (int round = 0; round < 6 && run.marks == 1; round++) {
                    for (UUID id : List.copyOf(run.mobs)) {
                        if (level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity le) {
                            le.kill();
                        }
                    }
                    Circuits.step(level, run, false);
                    Circuits.step(level, run, false);
                }
            }
            check(f, run.marks == 2, "defeating the circuit foes did not clear the task");

            // 運ぶ: 受け取り、落とし、拾い直して届ける (早越えの連なりは下の landTasks で確かめるので、ここでは 0 から)
            run.streak = 0;
            run.offers.clear();
            run.bonus = CircuitRun.Bonus.NONE;
            run.kind = CircuitRun.Kind.CARRY;
            run.phase = 0;
            run.need = 0;
            run.dropped = false;
            run.ambushed = false;
            run.spot = here;
            run.spot2 = here.offset(40, 0, 0);
            run.taskStart = level.getGameTime();
            Circuits.step(level, run, false);
            check(f, run.phase == 1 && p.getUUID().equals(run.carrier), "the load was not picked up");
            run.carrier = UUID.randomUUID();
            Circuits.step(level, run, false);
            check(f, run.phase == 0 && run.dropped && Circuits.taskLine(run).getString().equals(
                    net.minecraft.network.chat.Component.translatable("circuit.skyabyss.task.carry_pick").getString()), "a lost carrier did not drop the load");
            run.spot = here;
            Circuits.step(level, run, false);
            p.setPos(run.spot2.getX() + 0.5, p.getY(), run.spot2.getZ() + 0.5);
            Circuits.step(level, run, false);
            p.setPos(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
            check(f, run.marks == 3, "delivering the load did not clear the task");
            discard(level, run);

            // 壊す: 着くと杭が立つ。打つと割れ目が広がり、消えただけでは越えず (戻ると立て直す)、壊すと越える
            run.streak = 0;
            run.offers.clear();
            run.bonus = CircuitRun.Bonus.NONE;
            run.kind = CircuitRun.Kind.BREAK;
            run.phase = 0;
            run.progress = 0;
            run.stake = null;
            run.spot = here;
            run.taskStart = level.getGameTime();
            Circuits.step(level, run, false);
            net.minecraft.world.entity.Entity st = run.stake == null ? null : level.getEntity(run.stake);
            check(f, run.phase == 1 && st instanceof CircuitStake, "arriving did not raise the abyss stake");
            if (st instanceof CircuitStake cs) {
                check(f, Math.abs(cs.getMaxHealth() - Math.min(CircuitStake.MAX_HP, Circuits.stakeHealth(run, 1))) < 1, "the stake health is wrong: " + cs.getMaxHealth());
                cs.hurt(level.damageSources().generic(), cs.getMaxHealth() * 0.5f);
                check(f, cs.stage() == 2, "a half-broken stake did not crack: stage " + cs.stage());
                cs.discard();
                Circuits.step(level, run, false);
                check(f, run.marks == 3 && run.phase == 0, "a stake that vanished cleared the task");
                Circuits.step(level, run, false);
                net.minecraft.world.entity.Entity st2 = run.stake == null ? null : level.getEntity(run.stake);
                check(f, st2 instanceof CircuitStake && st2 != cs, "the stake was not raised again");
                if (st2 instanceof CircuitStake cs2) {
                    cs2.kill();
                    Circuits.step(level, run, false);
                }
            }
            check(f, run.marks == 4, "breaking the stake did not clear the task");
            run.marks = 3;
            discard(level, run);

            // 時間切れ: 印なしで次へ
            Circuits.adopt(level, run, 0);
            run.bonus = CircuitRun.Bonus.NONE;
            int tasks = run.tasks;
            run.taskStart = level.getGameTime() - Circuits.TASK_LIMIT - 1;
            Circuits.step(level, run, false);
            check(f, run.marks == 3 && run.tasks == tasks + 1, "a timed-out task was not skipped without a mark");
            discard(level, run);

            // 柱の色: 金は印が二つ、青は残り時間 +2 分 (どちらも一回ぶん。そのあと数を戻す)
            Circuits.adopt(level, run, 0);
            run.bonus = CircuitRun.Bonus.DOUBLE;
            int marks0 = run.marks;
            long end0 = run.end;
            Circuits.complete(level, run);
            check(f, run.marks == marks0 + 2 && run.end == end0, "a gold pillar did not give two marks");
            Circuits.adopt(level, run, 0);
            run.bonus = CircuitRun.Bonus.TIME;
            Circuits.complete(level, run);
            check(f, run.marks == marks0 + 3 && run.end == end0 + Circuits.TIME_BONUS, "a blue pillar did not add time");
            // 紫の柱: 選ぶと呪いが重なり、敵の体力と攻撃が上がる (巡り路の終わりまで)
            CircuitRun.Offer o0 = run.offers.get(0);
            run.offers.set(0, new CircuitRun.Offer(o0.kind(), o0.spot(), o0.place(), o0.spot2(), o0.place2(), CircuitRun.Bonus.CURSE, o0.poi(), o0.poi2()));
            // 前の柱を選んだときに紫が出ていれば呪いが重なっているので、ここから数える
            run.curses = 0;
            Circuits.adopt(level, run, 0);
            net.minecraft.world.entity.Mob cursed = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            if (cursed != null) {
                double hp0 = cursed.getMaxHealth();
                Circuits.curse(cursed, run);
                check(f, run.curses() == 1 && Math.abs(cursed.getMaxHealth() - hp0 * 1.25) < 0.01 && cursed.getHealth() == cursed.getMaxHealth(),
                        "a purple pillar did not curse the foes (" + run.curses() + ", " + cursed.getMaxHealth() + ")");
                cursed.discard();
            }
            run.curses = 0;
            run.bonus = CircuitRun.Bonus.NONE;
            run.marks = marks0;
            discard(level, run);

            // 土地の課題と早越えの連なり (2026-11-05 のダンジョンの頁 3-1・3-2)
            landTasks(f, level, run, p);
            run.marks = marks0;
            run.streak = 0;
            run.curses = 0;

            // 報い: 印 3 × 25 鐘銭、一日の回数
            int before = Coins.count(p);
            Circuits.finish(server, run);
            check(f, Circuits.runOf(p) == null, "a finished circuit still holds its walker");
            check(f, Coins.count(p) - before == 3 * Circuits.COINS[ContinentMap.WHALE], "circuit coins are wrong: " + (Coins.count(p) - before));
            check(f, Circuits.runsToday(p) == 1, "the circuit reward was not counted for today");
            // 野営地の景品の表から、印 1 つにつき 1 回引く (CircuitCamps)
            check(f, Circuits.LAST_PULLS.getOrDefault(p.getUUID(), -1) == 3, "the reward did not pull once per mark (" + Circuits.LAST_PULLS.get(p.getUUID()) + ")");
            check(f, RegionPoints.week(p, ContinentMap.WHALE) == 3 && RegionPoints.fame(p, ContinentMap.WHALE) == 3, "circuit marks did not count as region points");

            // 一日 3 回まで
            p.getPersistentData().putInt(Circuits.RUNS, Circuits.DAILY);
            CircuitRun again = Circuits.start(server, List.of(p), ContinentMap.WHALE, town);
            made.add(again);
            Circuits.forceComplete(level, again);
            Circuits.forceComplete(level, again);
            discard(level, again);
            before = Coins.count(p);
            Circuits.finish(server, again);
            check(f, Coins.count(p) == before, "a fourth circuit in a day still paid");
            check(f, RegionPoints.fame(p, ContinentMap.WHALE) == 5, "marks past the daily limit did not count as fame");

            // 二人で歩く: 二人とも印の報いを受け、一人が抜けても残りの人で続く
            FakePlayer q = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c1c02"), "circuit_test_b"));
            p.getPersistentData().remove(Circuits.DAY);
            p.getPersistentData().remove(Circuits.RUNS);
            q.getPersistentData().remove(Circuits.DAY);
            q.getPersistentData().remove(Circuits.RUNS);
            CircuitRun duo = Circuits.start(server, List.of(p, q), ContinentMap.WHALE, town);
            made.add(duo);
            check(f, Circuits.runOf(p) == duo && Circuits.runOf(q) == duo && duo.members.size() == 2, "a party circuit did not hold both walkers");
            Circuits.forceComplete(level, duo);
            Circuits.forceComplete(level, duo);
            discard(level, duo);
            int qBefore = Coins.count(q);
            Circuits.leave(q);
            check(f, Circuits.runOf(q) == null && Circuits.runOf(p) == duo && Coins.count(q) - qBefore == 2 * Circuits.COINS[ContinentMap.WHALE],
                    "leaving a party circuit did not pay the leaver or broke the run");
            int pBefore = Coins.count(p);
            Circuits.forceComplete(level, duo);
            discard(level, duo);
            Circuits.finish(server, duo);
            check(f, Coins.count(p) - pBefore == 3 * Circuits.COINS[ContinentMap.WHALE] && Circuits.runsToday(q) == 1,
                    "the walker who stayed was not paid for every mark");
            q.getPersistentData().remove(Circuits.DAY);
            q.getPersistentData().remove(Circuits.RUNS);
            q.getPersistentData().remove(RegionPoints.WEEK);
            q.getPersistentData().remove(RegionPoints.FAME);
        } catch (RuntimeException ex) {
            f.add("circuit test crashed: " + ex);
            SkyAbyss.LOGGER.error("circuit test", ex);
        } finally {
            for (CircuitRun r : made) {
                discard(level, r);
                if (Circuits.runOf(p) == r) {
                    Circuits.finish(server, r);
                }
            }
            p.getPersistentData().remove(Circuits.DAY);
            p.getPersistentData().remove(Circuits.RUNS);
            p.getPersistentData().remove(RegionPoints.WEEK);
            p.getPersistentData().remove(RegionPoints.FAME);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST circuit PASSED (two pillars to choose, tasks stay in the region at fair distances, gold and blue pillars, guard, hunt with respawn, carry with drop, break the stake, timeout, coins, daily limit, region points, party walk and leave)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST circuit FAILED: {}", m));
        }
    }

    static void landTasks(List<String> f, ServerLevel level, CircuitRun run, FakePlayer p) {
        check(f, Circuits.landKind(ContinentMap.WHALE) == CircuitRun.Kind.DIG && Circuits.landKind(ContinentMap.TIDE) == CircuitRun.Kind.TIDE
                && Circuits.landKind(ContinentMap.STORM) == CircuitRun.Kind.CHARGE && Circuits.landKind(ContinentMap.EMBER) == CircuitRun.Kind.FIRE,
                "each land does not have its own circuit task");
        // 潮待ち: はじめ満ち 10 秒、引き 20 秒・満ち 12 秒
        run.cycleStart = 0;
        check(f, !CircuitLand.ebb(run, 100) && CircuitLand.ebb(run, 250) && !CircuitLand.ebb(run, 700) && CircuitLand.ebb(run, 860), "the tide cycle is wrong");
        // 早越え: 三つ続くと +1、五つで +2 (そこで数え直し)
        int m0 = run.marks;
        run.streak = 0;
        for (int i = 0; i < 5; i++) {
            if (run.choosing()) {
                Circuits.adopt(level, run, 0);
            }
            run.bonus = CircuitRun.Bonus.NONE;
            run.taskStart = level.getGameTime();
            Circuits.complete(level, run);
        }
        check(f, run.marks == m0 + 5 + 1 + 2 && run.streak == 0, "quick clears did not add streak marks (" + (run.marks - m0) + ")");
        run.taskStart = level.getGameTime() - Circuits.TASK_LIMIT;
        if (run.choosing()) {
            Circuits.adopt(level, run, 0);
        }
        run.bonus = CircuitRun.Bonus.NONE;
        run.taskStart = level.getGameTime() - Circuits.TASK_LIMIT;
        Circuits.complete(level, run);
        check(f, run.streak == 0, "a slow clear did not reset the streak");
        discard(level, run);
        // 掘り出す: 着くと光る所が三つ、しゃがみ続けると骨、三つで越える
        if (run.choosing()) {
            Circuits.adopt(level, run, 0);
        }
        run.bonus = CircuitRun.Bonus.NONE;
        run.kind = CircuitRun.Kind.DIG;
        run.phase = 0;
        run.progress = 0;
        run.need = 0;
        run.points.clear();
        run.pointProgress.clear();
        run.spot = p.blockPosition();
        int m1 = run.marks;
        long now = level.getGameTime();
        CircuitLand.stepDig(level, run, List.of(p), now);
        check(f, run.phase == 1 && run.points.size() == CircuitLand.DIG_POINTS, "arriving did not mark three dig spots");
        p.setShiftKeyDown(true);
        for (int i = 0; i < CircuitLand.DIG_POINTS && run.kind == CircuitRun.Kind.DIG; i++) {
            BlockPos at = run.points.get(i);
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            for (int k = 0; k < CircuitLand.DIG_STEPS && run.kind == CircuitRun.Kind.DIG; k++) {
                CircuitLand.stepDig(level, run, List.of(p), now);
            }
        }
        p.setShiftKeyDown(false);
        check(f, run.marks == m1 + 1, "digging three bones did not clear the task");
        discard(level, run);
        // 雷を集める: 結晶が三つ立ち、三回ずつ当てると満ちて越える。終わると片づく
        if (run.choosing()) {
            Circuits.adopt(level, run, 0);
        }
        run.bonus = CircuitRun.Bonus.NONE;
        run.kind = CircuitRun.Kind.CHARGE;
        run.phase = 0;
        run.progress = 0;
        run.spot = p.blockPosition();
        int m2 = run.marks;
        CircuitLand.stepCharge(level, run, List.of(p), now);
        List<BlockPos> crystals = new ArrayList<>(run.placed);
        check(f, run.phase == 1 && crystals.size() == CircuitLand.CRYSTALS && crystals.stream().allMatch(c -> level.getBlockState(c).is(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK)),
                "arriving did not raise three crystals (" + crystals.size() + ")");
        for (BlockPos c : crystals) {
            for (int k = 0; k < CircuitLand.CRYSTAL_HITS; k++) {
                CircuitLand.strike(level, run, c);
            }
        }
        CircuitLand.stepCharge(level, run, List.of(p), now);
        check(f, run.marks == m2 + 1, "filling the crystals did not clear the task");
        check(f, crystals.stream().allMatch(c -> level.getBlockState(c).isAir()), "the crystals were not cleared away");
        discard(level, run);
    }

    static void discard(ServerLevel level, CircuitRun run) {
        for (UUID id : run.mobs) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        run.mobs.clear();
        Circuits.removeStake(level, run);
    }
}
