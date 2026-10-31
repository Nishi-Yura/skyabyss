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
            check(f, Circuits.runOf(p) == run && run.kind != null && run.spot != null, "a circuit did not start with a task");
            for (int i = 0; i < 6; i++) {
                BlockPos from = run.spot;
                Circuits.nextTask(level, run);
                double d = Math.hypot(run.spot.getX() - from.getX(), run.spot.getZ() - from.getZ());
                check(f, map.region(run.spot.getX(), run.spot.getZ()) == ContinentMap.WHALE, "a circuit task left its region: " + run.spot);
                check(f, d >= Circuits.SPOT_MIN - 1 && d <= Circuits.SPOT_MAX + 1, "a circuit task is too near or far: " + (int) d);
                check(f, !Circuits.taskLine(run).getString().isBlank(), "a circuit task has no line");
                if (run.kind == CircuitRun.Kind.CARRY) {
                    check(f, run.spot2 != null && map.region(run.spot2.getX(), run.spot2.getZ()) == ContinentMap.WHALE, "a carry task has no drop point in the region");
                }
            }
            check(f, run.usedPois.size() >= 3, "circuit tasks do not use the region's sights: " + run.usedPois.size());

            // 守る: 柱のそばにいるあいだだけ進む
            BlockPos here = p.blockPosition();
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

            // 運ぶ: 受け取り、落とし、拾い直して届ける
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

            // 時間切れ: 印なしで次へ
            int tasks = run.tasks;
            run.taskStart = level.getGameTime() - Circuits.TASK_LIMIT - 1;
            Circuits.step(level, run, false);
            check(f, run.marks == 3 && run.tasks == tasks + 1, "a timed-out task was not skipped without a mark");
            discard(level, run);

            // 報い: 印 3 × 25 鐘銭、一日の回数
            int before = Coins.count(p);
            Circuits.finish(server, run);
            check(f, Circuits.runOf(p) == null, "a finished circuit still holds its walker");
            check(f, Coins.count(p) - before == 3 * Circuits.COINS[ContinentMap.WHALE], "circuit coins are wrong: " + (Coins.count(p) - before));
            check(f, Circuits.runsToday(p) == 1, "the circuit reward was not counted for today");
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
            SkyAbyss.LOGGER.info("SELFTEST circuit PASSED (tasks stay in the region at fair distances, guard, hunt, carry with drop, timeout, coins, daily limit, region points)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST circuit FAILED: {}", m));
        }
    }

    static void discard(ServerLevel level, CircuitRun run) {
        for (UUID id : run.mobs) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        run.mobs.clear();
    }
}
