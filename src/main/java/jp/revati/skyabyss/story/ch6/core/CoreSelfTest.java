package jp.revati.skyabyss.story.ch6.core;

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
import jp.revati.skyabyss.story.ch6.mob.CoreShard;
import jp.revati.skyabyss.story.ch6.mob.MoonCoreBoss;
import jp.revati.skyabyss.story.ch6.rift.MoonRift;
import jp.revati.skyabyss.story.ch6.rift.RiftPuzzles;
import jp.revati.skyabyss.story.ch6.rift.RiftTicker;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 月の核の自己テスト (docs/design/46 の 4.7)。段: "moon core" (先に "moon core (load)" で塊を読み込む)
//   核の間が流し込まれる (床・四つの台・入口・帰り口) と着く所の核の門 / 頼み main_moon_core (淵のあとに開く・四人と会う・下りる・核・語りで終わる・「完」の札) /
//   月の核 (呼べる・欠片 4・欠片の向きから打つと通らない・段 2 で欠片 2・巻き戻し (削りきれないと戻る・削りきると崩れる)・段 3 で欠片が砕けて四人が立つ・
//   満月落としの無事な円・片付け)
public final class CoreSelfTest {
    private static final List<int[]> FORCED = new ArrayList<>();

    private CoreSelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        if (moon == null) {
            return;
        }
        moon.getChunk(MoonWorld.ARRIVAL);
        MoonWorld.build(moon);
        for (String k : new String[] {"core_gate", "arena_center"}) {
            BlockPos m = MoonCore.mark(k);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    moon.setChunkForced((m.getX() >> 4) + dx, (m.getZ() >> 4) + dz, true);
                    FORCED.add(new int[] {(m.getX() >> 4) + dx, (m.getZ() >> 4) + dz});
                }
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            check(f, QuestManager.quest(CorePuzzles.QUEST) != null, "main_moon_core is not loaded");
            if (moon == null) {
                report(f);
                return;
            }
            check(f, MoonWorld.coreBuilt(server), "the core chamber was not marked as built");
            // 地形と門
            for (String k : new String[] {"arena_center", "arena_entry", "seat_valley", "seat_cliff", "seat_field", "seat_rift", "gate_front"}) {
                check(f, standable(moon, MoonCore.mark(k)), k + " is not standable at " + MoonCore.mark(k));
            }
            check(f, moon.getBlockState(MoonCore.mark("core_gate")).is(ValleyBlocks.STAR_GATE.get()), "the core gate is missing at the landing");
            check(f, moon.getBlockState(MoonCore.mark("arena_back")).is(ValleyBlocks.STAR_GATE.get()), "the chamber return gate is missing");
            check(f, MoonCore.isLandingGate(MoonCore.mark("core_gate")) && MoonCore.isBackGate(MoonCore.mark("arena_back")), "gate lookups are wrong");
            check(f, MoonCore.inChamber(MoonCore.mark("arena_center").getCenter()) && !MoonCore.inChamber(MoonCore.mark("core_gate").getCenter()),
                    "the chamber test is wrong");
            // 頼み: 淵のあとに開く → 四人と会う → 下りる → 核 → 語り
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_core"));
            check(f, !CorePuzzles.open(p), "the core gate is open before the rift");
            String role = TownGuests.ROLE_PREFIX + RiftTicker.PAST_SELF;
            Quests.start(p, RiftPuzzles.QUEST);
            Quests.onTalk(p, role, "ch6_rift_meet3");
            RiftPuzzles.pool(p, MoonRift.mark("star_pool"));
            for (String s : MoonRift.sites()) {
                RiftPuzzles.claim(p, MoonRift.mark("shard_" + s));
            }
            Quests.signal(p, RiftPuzzles.GATE);
            Quests.signal(p, RiftPuzzles.LORD);
            Quests.onTalk(p, role, "ch6_rift_promise3");
            check(f, CorePuzzles.open(p), "the core gate did not open after the rift");
            check(f, !MoonEnd.due(p), "the moon end card came before the core");
            CorePuzzles.gate(p, MoonCore.mark("core_gate"));
            check(f, CorePuzzles.stage(p) == CorePuzzles.stageIndex("descend"), "touching the gate did not gather the four: " + CorePuzzles.stage(p));
            CorePuzzles.gate(p, MoonCore.mark("core_gate"));
            check(f, CorePuzzles.stage(p) == CorePuzzles.stageIndex("core") && p.getTags().contains(CoreArena.TAG),
                    "the second touch did not take the player down: " + CorePuzzles.stage(p));
            Quests.signal(p, CorePuzzles.LORD);
            check(f, CorePuzzles.stage(p) == CorePuzzles.stageIndex("end"), "stopping the core did not move to end: " + CorePuzzles.stage(p));
            jp.revati.skyabyss.story.reel.StoryReels.play(p, CorePuzzles.REEL);
            QuestLog.Entry done = QuestLog.of(p).entry(CorePuzzles.QUEST);
            check(f, done != null && done.done, "the reel did not finish main_moon_core");
            check(f, MoonEnd.due(p), "the moon end card is not due after the core");
            // 月の核
            MoonClock.skipTo(moon, 0);
            CoreArena.clear(moon);
            MoonCoreBoss core = CoreArena.summon(moon);
            spawned.add(core);
            check(f, core != null && core.getMaxHealth() >= MoonCoreBoss.HP - 0.01 && core.getMaxHealth() <= 1024,
                    "the core did not appear with its hp: " + (core == null ? -1 : core.getMaxHealth()));
            if (core != null) {
                core.tick();
                List<CoreShard> shards = moon.getEntitiesOfClass(CoreShard.class, new AABB(core.blockPosition()).inflate(16), Entity::isAlive);
                check(f, shards.size() == 4 && core.shardCount() == 4, "the core has " + shards.size() + " shards");
                double a = Math.toRadians(core.shardAngles().get(0));
                Vec3 behind = core.position().add(Math.cos(a) * 8, 0, Math.sin(a) * 8);
                Vec3 gap = core.position().add(Math.cos(a + Math.PI / 4) * 8, 0, Math.sin(a + Math.PI / 4) * 8);
                check(f, core.shielded(behind) && !core.shielded(gap), "the shard shield is wrong");
                FakePlayer hitter = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_core_hitter"));
                hitter.moveTo(behind.x, behind.y, behind.z);
                float h0 = core.getHealth();
                core.invulnerableTime = 0;
                core.hurt(moon.damageSources().playerAttack(hitter), 40f);
                check(f, core.getHealth() == h0, "a blow from behind a shard went through");
                hitter.moveTo(gap.x, gap.y, gap.z);
                core.invulnerableTime = 0;
                core.hurt(moon.damageSources().playerAttack(hitter), 40f);
                check(f, core.getHealth() < h0, "a blow through the gap did not land");
                // 段 2: 欠片 2・巻き戻し
                core.setHealth(core.getMaxHealth() * 0.6f);
                core.tick();
                check(f, core.phase() == 1 && core.shardCount() == 2, "phase 2 did not start: phase " + core.phase());
                core.rewindForTest();
                float before = core.getHealth();
                core.setHealth(before - core.getMaxHealth() * 0.02f);
                core.tickRewindForTest(moon, 200);
                check(f, Math.abs(core.getHealth() - before) < 0.01, "a light rewind did not restore the core: " + core.getHealth() + " vs " + before);
                core.rewindForTest();
                core.setHealth(before - core.getMaxHealth() * 0.08f);
                core.tickRewindForTest(moon, 1);
                check(f, core.staggered() && !core.rewinding(), "breaking the rewind did not stagger the core");
                // 段 3: 欠片が砕け、四人が立つ。満月落としの無事な円
                core.setHealth(core.getMaxHealth() * 0.3f);
                core.tick();
                List<CoreShard> left = moon.getEntitiesOfClass(CoreShard.class, new AABB(core.blockPosition()).inflate(16), Entity::isAlive);
                check(f, core.phase() == 2 && left.isEmpty(), "phase 3: phase " + core.phase() + ", shards left " + left.size());
                check(f, CoreArena.allyList(moon).size() == 4, "the four did not come: " + CoreArena.allyList(moon).size());
                List<Vec3> safe = CoreArena.safeSpots(moon);
                Vec3 seat = MoonCore.mark("seat_field").getBottomCenter();
                Vec3 open = MoonCore.mark("arena_center").getBottomCenter().add(5, 0, 5);
                check(f, MoonCoreBoss.inSafe(seat, safe) && !MoonCoreBoss.inSafe(open, safe), "the safe circles of the full-moon drop are wrong");
                core.dropForTest(moon);
                check(f, !core.dropping(), "the full-moon drop did not finish");
                CoreArena.summon(moon);
                check(f, CoreArena.lords(moon).size() == 1, "summoning again should keep one core");
            }
            CoreArena.clear(moon);
            check(f, CoreArena.lords(moon).isEmpty() && CoreArena.allyList(moon).isEmpty(), "clearing the chamber left the core or the four");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST moon core crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            spawned.stream().filter(e -> e != null && e.isAlive()).forEach(Entity::discard);
            if (moon != null) {
                CoreArena.clear(moon);
                MoonClock.skipTo(moon, 0);
                for (int[] c : FORCED) {
                    moon.setChunkForced(c[0], c[1], false);
                }
                FORCED.clear();
            }
        }
        report(f);
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon core PASSED (chamber, seats, landing gate, main_moon_core after the rift and the end card, core summon, four shards, "
                    + "shard shield, phase 2 two shards and rewind restore/break, phase 3 shards gone and the four allies, full-moon safe circles, clear)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon core FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
