package jp.revati.skyabyss.story.ch6.cliff;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch6.MoonClock;
import jp.revati.skyabyss.story.ch6.MoonEnd;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.CliffCreeper;
import jp.revati.skyabyss.story.ch6.mob.MoonLeaper;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.MoonScale;
import jp.revati.skyabyss.story.ch6.mob.PushStone;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

// 上弦の崖の自己テスト (docs/design/46 の 4.4)。段: "moon cliff"
//   崖が流し込まれる (野営・主の皿) / 道は弧に沿って跳ばずに登れる (段差 0.6 まで) / 印の所の仕掛け (欠片の台 3・崩れる棚・錘の山・皿・門・溜まり・帰りの台) /
//   跳び石 (一段跳びでは届かず、軽身なら届く段差) / 天秤 (錘二つで東の皿が上がりきり、高い柱まで軽身で届く。水平からは届かない) /
//   崩れる棚 (震えて落ちて戻る) / 軽身の印 / 頼み main_moon_cliff と「ここまで」の札 / 敵の数字 (区画 2 の倍率) / 月秤 (呼べる・釣り合いの守り・崩れる)
public final class CliffSelfTest {
    // 月の跳び (MoonGravity) で上がる高さ: 一段 4.69、軽身でもう一段 9.37 (着く面の高さの目安)
    static final double SINGLE_JUMP = 4.69;
    static final double DOUBLE_JUMP = 9.37;

    private CliffSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<int[]> forced = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            check(f, QuestManager.quest(CliffPuzzles.QUEST) != null, "main_moon_cliff is not loaded");
            if (moon == null) {
                report(f);
                return;
            }
            long t0 = System.currentTimeMillis();
            moon.getChunk(MoonWorld.ARRIVAL);
            MoonWorld.build(moon);
            check(f, MoonWorld.cliffBuilt(server), "the cliff was not marked as built");
            SkyAbyss.LOGGER.info("SELFTEST moon cliff: build took {} ms", System.currentTimeMillis() - t0);
            for (String k : new String[] {"camp", "plate_w", "arena_center", "shard_tower", "ledge_a"}) {
                BlockPos m = MoonCliff.mark(k);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        moon.setChunkForced((m.getX() >> 4) + dx, (m.getZ() >> 4) + dz, true);
                        forced.add(new int[] {(m.getX() >> 4) + dx, (m.getZ() >> 4) + dz});
                    }
                }
            }
            // 地形: 野営と主の皿に立てる
            BlockPos camp = MoonCliff.mark("camp");
            check(f, standable(moon, camp), "the camp is not standable");
            check(f, standable(moon, MoonCliff.mark("arena_center")), "the scale's plate is not standable");
            check(f, standable(moon, MoonCliff.mark("arena_entry")), "the plate entry is not standable");
            // 道: 弧の内 4 を 1 ずつ見て、段差が 0.6 を超えない (跳ばずに登れる)
            double worst = 0;
            String worstAt = "";
            double prev = Double.NaN;
            double r = 96.0;
            BlockPos gate = MoonCliff.mark("cliff_gate");
            double aEnd = Math.atan2(gate.getZ() - MoonCliff.ARC_Z, gate.getX() - MoonCliff.ARC_X);
            double aStart = Math.toRadians(203.0) - Math.PI * 2;
            for (double a = aStart; a <= aEnd - 2.5 / r; a += 1.0 / r) {
                int x = (int) Math.floor(MoonCliff.ARC_X + Math.cos(a) * r);
                int z = (int) Math.floor(MoonCliff.ARC_Z + Math.sin(a) * r);
                double top = floorTop(moon, x, z);
                if (!Double.isNaN(prev) && top - prev > worst) {
                    worst = top - prev;
                    worstAt = x + "," + z + " (" + prev + " -> " + top + ")";
                }
                prev = top;
            }
            check(f, worst <= 0.6, "the path has a step a walker cannot take: " + worst + " at " + worstAt);
            // 門を通らずに皿へ入った人は、門の前の道へ戻される (月の跳びで岩山や門の枠から跳び移っても)
            FakePlayer sneak = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_cliff_sneak"));
            BlockPos entry = MoonCliff.mark("arena_entry");
            sneak.moveTo(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5);
            check(f, CliffTicker.guardPlate(sneak) && !MoonCliff.inArenaZone(sneak.position()), "a player without the shards stayed on the plate: " + sneak.blockPosition());
            // 仕掛け
            for (String s : MoonCliff.sites()) {
                check(f, moon.getBlockState(MoonCliff.mark("shard_" + s)).is(ValleyBlocks.STAR_PEDESTAL.get()), "pedestal missing at " + s);
            }
            List<BlockPos> ledge = MoonCliff.ledgeBlocks();
            check(f, ledge.size() >= 24 && ledge.stream().allMatch(p -> moon.getBlockState(p).getBlock() instanceof CrumblingLedgeBlock),
                    "the crumbling ledge is not whole: " + ledge.size());
            check(f, moon.getBlockState(MoonCliff.mark("weight_pile")).is(CliffBlocks.WEIGHT_PILE.get()), "weight pile missing");
            check(f, moon.getBlockState(MoonCliff.mark("cliff_gate")).is(ValleyBlocks.STAR_GATE.get()), "summit gate missing");
            check(f, moon.getBlockState(MoonCliff.mark("arena_back")).is(ValleyBlocks.STAR_GATE.get()), "plate return gate missing");
            check(f, moon.getBlockState(MoonCliff.mark("return_stone")).is(Blocks.LODESTONE), "camp return stone missing");
            check(f, moon.getBlockState(MoonCliff.mark("star_pool").above()).is(ValleyBlocks.STAR_POOL.get()), "moon pool missing");
            // 跳び石: 道から一つ目、石から石、最後の石から頂の台。一段跳びでは届かず、軽身なら届く
            double pathTop = floorTop(moon, MoonCliff.mark("tower_stone_0").getX(), MoonCliff.mark("tower_stone_0").getZ() + 0);
            double lastTop = MoonCliff.mark("tower_stone_0").getY() - 5 + 1;
            for (int k = 0; k < 6; k++) {
                double top = MoonCliff.mark("tower_stone_" + k).getY() + 1;
                double rise = top - lastTop;
                check(f, rise > SINGLE_JUMP && rise < DOUBLE_JUMP, "tower stone " + k + " rise " + rise + " is not a light-body jump");
                lastTop = top;
            }
            double toTop = MoonCliff.mark("shard_tower").getY() - lastTop;
            check(f, toTop > SINGLE_JUMP && toTop < DOUBLE_JUMP, "the spire top rise " + toTop + " is not a light-body jump");
            check(f, !Double.isNaN(pathTop), "no floor under the first stone");
            // 天秤
            CliffBalance.reset(moon);
            check(f, CliffBalance.lift() == 0 && CliffBalance.weights(moon) == 0, "the balance does not start level and empty");
            check(f, moon.getBlockState(CliffBalance.plateTop(false).below()).getBlock() instanceof BalancePlateBlock, "west plate missing");
            double highFromLevel = MoonCliff.mark("shard_balance").getY() - CliffBalance.plateTop(true).getY();
            check(f, highFromLevel > DOUBLE_JUMP, "the tall pillar can be reached from the level plate: " + highFromLevel);
            CliffBalance.putWeightForTest(moon, 0);
            CliffBalance.putWeightForTest(moon, 1);
            check(f, CliffBalance.weights(moon) == 2 && CliffBalance.target(moon) == CliffBalance.MAX_LIFT,
                    "two weights should lift the east plate all the way: weights " + CliffBalance.weights(moon) + " target " + CliffBalance.target(moon));
            for (int i = 0; i < CliffBalance.MAX_LIFT; i++) {
                CliffBalance.step(moon, 1);
            }
            double highFromUp = MoonCliff.mark("shard_balance").getY() - CliffBalance.plateTop(true).getY();
            check(f, highFromUp > SINGLE_JUMP && highFromUp < DOUBLE_JUMP, "from the raised plate the pillar is not a light-body jump: " + highFromUp);
            check(f, CliffBalance.weights(moon) == 2, "the weights did not ride down with the west plate");
            check(f, moon.getBlockState(CliffBalance.plateTop(true).below()).getBlock() instanceof BalancePlateBlock, "east plate did not move up");
            CliffBalance.reset(moon);
            check(f, CliffBalance.lift() == 0 && CliffBalance.weights(moon) == 0, "reset did not level the plates and clear the weights");
            // 崩れる棚
            BlockPos lp = ledge.get(ledge.size() / 2);
            CrumblingLedgeBlock.shake(moon, lp, moon.getBlockState(lp));
            check(f, moon.getBlockState(lp).getValue(CrumblingLedgeBlock.SHAKING), "the ledge did not start shaking");
            moon.getBlockState(lp).tick(moon, lp, moon.random);
            BlockState fallen = moon.getBlockState(lp);
            check(f, fallen.getValue(CrumblingLedgeBlock.FALLEN), "the ledge did not fall");
            check(f, fallen.getCollisionShape(moon, lp).isEmpty(), "a fallen ledge still has a floor");
            fallen.tick(moon, lp, moon.random);
            check(f, !moon.getBlockState(lp).getValue(CrumblingLedgeBlock.FALLEN), "the ledge did not come back");
            CrumblingLedgeBlock.restore(moon, lp);
            // 軽身の印と、門
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_cliff"));
            check(f, !LightBody.learned(p), "a new player already knows light body");
            LightBody.learn(p);
            check(f, LightBody.learned(p), "learning light body did not stick");
            check(f, jp.revati.skyabyss.util.LifeKeep.KEYS.contains(LightBody.TAG), "light body is lost on death (LifeKeep)");
            LightBody.forget(p);
            check(f, !CliffPuzzles.mayCross(p), "a player without shards may cross the summit gate");
            // 頼みの通し: 会う → 月の溜まり → 欠片 3 → 門 → 主 → 約束
            String role = TownGuests.ROLE_PREFIX + CliffTicker.PAST_SELF;
            check(f, Quests.start(p, CliffPuzzles.QUEST), "main_moon_cliff would not start");
            Quests.onTalk(p, role, "ch6_cliff_meet3");
            check(f, CliffPuzzles.stage(p) == CliffPuzzles.stageIndex("pool"), "talking at the camp did not move to pool: " + CliffPuzzles.stage(p));
            CliffPuzzles.pool(p, MoonCliff.mark("star_pool").above());
            check(f, LightBody.learned(p), "the moon pool did not teach light body");
            check(f, CliffPuzzles.stage(p) == CliffPuzzles.stageIndex("shards") && CliffPuzzles.shards(p) == 0, "the moon pool did not move to shards");
            CliffPuzzles.claim(p, MoonCliff.mark("shard_tower"));
            CliffPuzzles.claim(p, MoonCliff.mark("shard_ledge"));
            check(f, CliffPuzzles.shards(p) == 2 && !CliffPuzzles.mayCross(p), "two shards: count " + CliffPuzzles.shards(p) + ", may cross " + CliffPuzzles.mayCross(p));
            CliffPuzzles.claim(p, MoonCliff.mark("shard_balance"));
            check(f, CliffPuzzles.mayCross(p), "three shards did not open the summit gate");
            Quests.signal(p, CliffPuzzles.GATE);
            check(f, !MoonEnd.due(p), "the moon end card came before the cliff was done");
            Quests.signal(p, CliffPuzzles.LORD);
            check(f, CliffPuzzles.stage(p) == CliffPuzzles.stageIndex("promise"), "the lord signal did not move to promise: " + CliffPuzzles.stage(p));
            Quests.onTalk(p, role, "ch6_cliff_promise3");
            check(f, QuestLog.of(p).entry(CliffPuzzles.QUEST) != null && QuestLog.of(p).entry(CliffPuzzles.QUEST).done, "the promise did not finish main_moon_cliff");
            // 「ここまで」の札は満月の野を終えた人に出る (崖を終えただけでは出ない)
            check(f, !MoonEnd.due(p), "the moon end card came after the cliff, before the full moon field");
            // 敵の数字 (上弦は合った位相)
            MoonClock.skipTo(moon, 1);
            BlockPos near = MoonCliff.mark("camp").offset(0, 0, -22);
            MoonLeaper leaper = (MoonLeaper) CliffTicker.put(moon, MoonMobsSetup.MOON_LEAPER.get(), standableNear(moon, near));
            spawned.add(leaper);
            double want = MoonLeaper.HP * MoonMob.SECTION_HP[1] * MoonMob.MATCHED;
            check(f, leaper != null && Math.abs(leaper.getMaxHealth() - want) < 0.01 && leaper.stage() == 2,
                    "moon leaper hp " + (leaper == null ? -1 : leaper.getMaxHealth()) + " != " + want + " or not the second form");
            PushStone stone = (PushStone) CliffTicker.put(moon, MoonMobsSetup.PUSH_STONE.get(), standableNear(moon, near.offset(3, 0, 0)));
            spawned.add(stone);
            check(f, stone != null && stone.isNoGravity(), "the push stone does not float");
            CliffCreeper crab = (CliffCreeper) CliffTicker.put(moon, MoonMobsSetup.CLIFF_CREEPER.get(), standableNear(moon, near.offset(-3, 0, 0)));
            spawned.add(crab);
            check(f, crab != null && !crab.rockless(), "the cliff creeper does not carry its rock");
            MoonClock.skipTo(moon, 0);
            // 月秤
            MoonScale lord = CliffArena.summon(moon);
            spawned.add(lord);
            check(f, lord != null && lord.getMaxHealth() >= MoonScale.HP - 0.01, "the moon scale did not appear with its hp: " + (lord == null ? -1 : lord.getMaxHealth()));
            if (lord != null) {
                float h0 = lord.getHealth();
                lord.invulnerableTime = 0;
                lord.hurt(moon.damageSources().generic(), 50f);
                float d1 = h0 - lord.getHealth();
                check(f, d1 <= 18f, "the balanced scale took full damage: " + d1);
                lord.topple(moon);
                check(f, lord.toppled(), "the scale did not topple");
                float h1 = lord.getHealth();
                lord.invulnerableTime = 0;
                lord.hurt(moon.damageSources().generic(), 50f);
                float d2 = h1 - lord.getHealth();
                check(f, d2 >= 70f, "the toppled scale did not take extra damage: " + d2);
                CliffArena.summon(moon);
                int n = CliffArena.lords(moon).size();
                check(f, n == 1, "summoning again should keep one lord, found " + n + " (lord at " + lord.blockPosition() + ", alive " + lord.isAlive() + ")");
            }
            CliffArena.clear(moon);
            check(f, CliffArena.lords(moon).isEmpty(), "clearing the plate left the lord");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST moon cliff crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            spawned.stream().filter(e -> e != null && e.isAlive()).forEach(Entity::discard);
            if (moon != null) {
                CliffBalance.reset(moon);
                for (int[] c : forced) {
                    moon.setChunkForced(c[0], c[1], false);
                }
            }
        }
        report(f);
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir();
    }

    static BlockPos standableNear(ServerLevel level, BlockPos at) {
        for (int dy = 12; dy >= -12; dy--) {
            if (standable(level, at.above(dy))) {
                return at.above(dy);
            }
        }
        return at;
    }

    // 列のいちばん上の当たりの面の高さ (下付きの板は半分)
    static double floorTop(ServerLevel level, int x, int z) {
        for (int y = 100; y > 0; y--) {
            BlockPos p = new BlockPos(x, y, z);
            VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
            if (!s.isEmpty()) {
                return y + s.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon cliff PASSED (cliff terrain, camp, plate, walkable path, puzzle blocks, leaping stones, balance lift and reach, crumbling ledge, light body, main_moon_cliff and no end card yet, mob numbers, scale summon, balance guard and topple)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon cliff FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
