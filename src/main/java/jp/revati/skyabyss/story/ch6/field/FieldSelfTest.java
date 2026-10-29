package jp.revati.skyabyss.story.ch6.field;

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
import jp.revati.skyabyss.story.ch6.mob.FullKeeper;
import jp.revati.skyabyss.story.ch6.mob.LightSwarm;
import jp.revati.skyabyss.story.ch6.mob.MirrorBearer;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.ShadeStrider;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

// 満月の野の自己テスト (docs/design/46 の 4.5)。段: "moon field"
//   野が流し込まれる (野営・池・溝・塔・台) / 印の所の仕掛け (欠片の台 3・門・帰り口・溜まり・帰りの台・満ち引きの石) /
//   満ち引き (満ちは光の石、引きは影の石、移りはどちらも透ける、満月は満ちが長い) / 一瞬止め (石を止めて移りを越す・敵が止まる・倒れても残る) /
//   池の飛び石と柱 (一段跳びで届く) / 塔の段 (一段跳びで届く) / 落ちた人と台の守り / 頼み main_moon_field と「ここまで」の札 /
//   敵の数字 (区画 3 の倍率・満ちと引きの振る舞い) / 望の灯守 (呼べる・光溜めの守り・放ちの予告に一瞬止めで膝をつく)
public final class FieldSelfTest {
    static final double SINGLE_JUMP = 4.69;

    private FieldSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<int[]> forced = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            check(f, QuestManager.quest(FieldPuzzles.QUEST) != null, "main_moon_field is not loaded");
            if (moon == null) {
                report(f);
                return;
            }
            long t0 = System.currentTimeMillis();
            moon.getChunk(MoonWorld.ARRIVAL);
            MoonWorld.build(moon);
            check(f, MoonWorld.fieldBuilt(server), "the field was not marked as built");
            SkyAbyss.LOGGER.info("SELFTEST moon field: build took {} ms", System.currentTimeMillis() - t0);
            for (String k : new String[] {"camp", "shard_pond", "pond_entry", "gully_east", "shard_gully", "shard_tower", "arena_center", "gate_front"}) {
                BlockPos m = MoonField.mark(k);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        moon.setChunkForced((m.getX() >> 4) + dx, (m.getZ() >> 4) + dz, true);
                        forced.add(new int[] {(m.getX() >> 4) + dx, (m.getZ() >> 4) + dz});
                    }
                }
            }
            // 地形: 立てる所
            for (String k : new String[] {"camp", "arena_center", "arena_entry", "pond_entry", "gully_entry", "gully_east", "tower_entry", "gate_front"}) {
                check(f, standable(moon, MoonField.mark(k)), k + " is not standable at " + MoonField.mark(k));
            }
            // 仕掛け
            for (String s : MoonField.sites()) {
                check(f, moon.getBlockState(MoonField.mark("shard_" + s)).is(ValleyBlocks.STAR_PEDESTAL.get()), "pedestal missing at " + s);
            }
            check(f, moon.getBlockState(MoonField.mark("field_gate")).is(ValleyBlocks.STAR_GATE.get()), "full moon gate missing");
            check(f, moon.getBlockState(MoonField.mark("arena_back")).is(ValleyBlocks.STAR_GATE.get()), "arena return gate missing");
            check(f, moon.getBlockState(MoonField.mark("return_stone")).is(Blocks.LODESTONE), "camp return stone missing");
            check(f, moon.getBlockState(MoonField.mark("star_pool").above()).is(ValleyBlocks.STAR_POOL.get()), "moon pool missing");
            int stones = MoonField.stones().size();
            long tideBlocks = MoonField.stones().stream().filter(s -> moon.getBlockState(s.pos()).getBlock() instanceof TideStoneBlock).count();
            check(f, stones > 200 && tideBlocks == stones, "tide stones: " + tideBlocks + " of " + stones);
            // 満ち引き
            MoonClock.skipTo(moon, 0);
            MoonField.Stone light = MoonField.stones().stream().filter(s -> !s.shade()).findFirst().orElseThrow();
            MoonField.Stone shade = MoonField.stones().stream().filter(MoonField.Stone::shade).findFirst().orElseThrow();
            FieldTide.setTo(moon, FieldTide.Tide.BRIGHT, 10);
            check(f, solid(moon, light.pos()) && !solid(moon, shade.pos()), "at swell the light stone should stand and the shade stone fade");
            FieldTide.setTo(moon, FieldTide.Tide.DIM, 10);
            check(f, !solid(moon, light.pos()) && solid(moon, shade.pos()), "at ebb the shade stone should stand and the light stone fade");
            FieldTide.setTo(moon, FieldTide.Tide.TO_DIM, 5);
            check(f, !solid(moon, light.pos()) && !solid(moon, shade.pos()), "at the turn both stones should fade");
            FieldTide.setTo(moon, FieldTide.Tide.BRIGHT, 230);
            check(f, FieldTide.tide(moon) == FieldTide.Tide.TO_DIM, "a new-moon swell should be over after 10 seconds");
            MoonClock.skipTo(moon, 2);
            FieldTide.setTo(moon, FieldTide.Tide.BRIGHT, 230);
            check(f, FieldTide.tide(moon) == FieldTide.Tide.BRIGHT, "a full-moon swell should last 14 seconds");
            MoonClock.skipTo(moon, 0);
            // 一瞬止め: 満ちの終わりに止めた光の石は、移りに入っても立っている。止めていない石は透ける
            FieldTide.setTo(moon, FieldTide.Tide.BRIGHT, 195);
            MoonField.Stone other = MoonField.stones().stream().filter(s -> !s.shade() && !s.pos().equals(light.pos())).findFirst().orElseThrow();
            check(f, FieldTide.hold(moon, light.pos(), moon.getGameTime() + MomentStop.DURATION), "could not hold a light stone");
            for (int i = 0; i < 12; i++) {
                FieldTide.tick(moon);
            }
            FieldTide.refresh(moon, true);
            check(f, FieldTide.tide(moon) == FieldTide.Tide.TO_DIM, "the tide did not reach the turn: " + FieldTide.tide(moon) + " " + FieldTide.pos());
            check(f, solid(moon, light.pos()) && !solid(moon, other.pos()), "a held light stone should stand through the turn while others fade");
            // 池の飛び石: 一つずつ 2 上がり、間は 10 まで。最後の石から柱の頂は一段跳びで届く
            double lastTop = MoonField.mark("pond_entry").getY();
            BlockPos lastAt = MoonField.mark("pond_entry");
            for (int i = 0; i < 6; i++) {
                BlockPos s = MoonField.mark("pond_stone_" + i);
                double rise = s.getY() - lastTop;
                double gap = Math.hypot(s.getX() - lastAt.getX(), s.getZ() - lastAt.getZ());
                check(f, rise <= SINGLE_JUMP && gap <= 10.5, "pond stone " + i + ": rise " + rise + ", gap " + gap);
                lastTop = s.getY();
                lastAt = s;
            }
            double pillar = MoonField.mark("shard_pond").getY() - lastTop;
            check(f, pillar <= SINGLE_JUMP && pillar > 0, "the pond pillar top is not a jump from the last stone: " + pillar);
            check(f, MoonField.mark("shard_pond").getY() - MoonField.mark("pond_stone_1").getY() > 9.37, "the pond pillar can be reached from the second stone");
            // 塔の段: 一段ずつ 3 上がる。頂の台まで一段跳びで届く
            for (int i = 1; i < 14; i++) {
                double rise = MoonField.mark("tower_step_" + i).getY() - MoonField.mark("tower_step_" + (i - 1)).getY();
                check(f, rise > 0 && rise <= SINGLE_JUMP, "tower step " + i + " rise " + rise);
            }
            double top = MoonField.mark("shard_tower").getY() - MoonField.mark("tower_step_13").getY();
            check(f, top > 0 && top <= SINGLE_JUMP, "the tower top is not a jump from the last step: " + top);
            // 落ちた人と台の守り
            FakePlayer faller = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_field_faller"));
            BlockPos pond = MoonField.mark("shard_pond");
            faller.moveTo(pond.getX() + 5.5, 0, pond.getZ() + 0.5);
            check(f, FieldTicker.fell(faller) && faller.blockPosition().closerThan(MoonField.mark("pond_entry"), 2), "a pond fall did not return to the pond ledge");
            FakePlayer sneak = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_field_sneak"));
            BlockPos entry = MoonField.mark("arena_entry");
            sneak.moveTo(entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5);
            check(f, FieldTicker.guardMesa(sneak) && !MoonField.inArenaZone(sneak.position()), "a player without the shards stayed on the mesa: " + sneak.blockPosition());
            // 一瞬止めの印と頼みの通し: 会う → 月の溜まり → 欠片 3 → 門 → 主 → 約束
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_field"));
            check(f, !MomentStop.learned(p), "a new player already knows moment stop");
            check(f, jp.revati.skyabyss.util.LifeKeep.KEYS.contains(MomentStop.TAG), "moment stop is lost on death (LifeKeep)");
            check(f, !FieldPuzzles.mayCross(p), "a player without shards may cross the full moon gate");
            String role = TownGuests.ROLE_PREFIX + FieldTicker.PAST_SELF;
            check(f, Quests.start(p, FieldPuzzles.QUEST), "main_moon_field would not start");
            Quests.onTalk(p, role, "ch6_field_meet3");
            check(f, FieldPuzzles.stage(p) == FieldPuzzles.stageIndex("pool"), "talking at the camp did not move to pool: " + FieldPuzzles.stage(p));
            FieldPuzzles.pool(p, MoonField.mark("star_pool").above());
            check(f, MomentStop.learned(p) && MoonHourglassItem.carries(p), "the moon pool did not teach moment stop and give the hourglass");
            check(f, FieldPuzzles.stage(p) == FieldPuzzles.stageIndex("shards") && FieldPuzzles.shards(p) == 0, "the moon pool did not move to shards");
            FieldPuzzles.claim(p, MoonField.mark("shard_pond"));
            FieldPuzzles.claim(p, MoonField.mark("shard_gully"));
            check(f, FieldPuzzles.shards(p) == 2 && !FieldPuzzles.mayCross(p), "two shards: count " + FieldPuzzles.shards(p) + ", may cross " + FieldPuzzles.mayCross(p));
            FieldPuzzles.claim(p, MoonField.mark("shard_tower"));
            check(f, FieldPuzzles.mayCross(p), "three shards did not open the full moon gate");
            Quests.signal(p, FieldPuzzles.GATE);
            check(f, !MoonEnd.due(p), "the moon end card came before the field was done");
            Quests.signal(p, FieldPuzzles.LORD);
            check(f, FieldPuzzles.stage(p) == FieldPuzzles.stageIndex("promise"), "the lord signal did not move to promise: " + FieldPuzzles.stage(p));
            Quests.onTalk(p, role, "ch6_field_promise3");
            QuestLog.Entry done = QuestLog.of(p).entry(FieldPuzzles.QUEST);
            check(f, done != null && done.done, "the promise did not finish main_moon_field");
            check(f, !MoonEnd.due(p), "the moon end card came after the field (it waits for the rift)");
            // 敵の数字 (満月は合った位相) と満ち引きの振る舞い
            MoonClock.skipTo(moon, 2);
            BlockPos near = MoonField.mark("camp").offset(-24, 0, 0);
            LightSwarm swarm = (LightSwarm) FieldTicker.put(moon, MoonMobsSetup.LIGHT_SWARM.get(), standableNear(moon, near));
            spawned.add(swarm);
            double want = LightSwarm.HP * MoonMob.SECTION_HP[2] * MoonMob.MATCHED;
            check(f, swarm != null && Math.abs(swarm.getMaxHealth() - want) < 0.01 && swarm.stage() == 2 && swarm.isNoGravity(),
                    "light swarm hp " + (swarm == null ? -1 : swarm.getMaxHealth()) + " != " + want + " or not the second form / not floating");
            ShadeStrider strider = (ShadeStrider) FieldTicker.put(moon, MoonMobsSetup.SHADE_STRIDER.get(), standableNear(moon, near.offset(4, 0, 0)));
            spawned.add(strider);
            MirrorBearer bearer = (MirrorBearer) FieldTicker.put(moon, MoonMobsSetup.MIRROR_BEARER.get(), standableNear(moon, near.offset(-4, 0, 0)));
            spawned.add(bearer);
            FieldTide.setTo(moon, FieldTide.Tide.BRIGHT, 10);
            strider.aiStep();
            bearer.aiStep();
            swarm.aiStep();
            check(f, strider.statue() && !bearer.hidden() && !swarm.scattered(), "at swell: strider statue " + strider.statue() + ", bearer hidden " + bearer.hidden()
                    + ", swarm scattered " + swarm.scattered());
            FieldTide.setTo(moon, FieldTide.Tide.DIM, 10);
            strider.aiStep();
            bearer.aiStep();
            swarm.aiStep();
            check(f, !strider.statue() && bearer.hidden() && swarm.scattered(), "at ebb: strider statue " + strider.statue() + ", bearer hidden " + bearer.hidden()
                    + ", swarm scattered " + swarm.scattered());
            // 一瞬止め: まわりの敵が止まる
            FakePlayer caster = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_field_caster"));
            caster.moveTo(swarm.getX() + 2, swarm.getY(), swarm.getZ());
            int n = MomentStop.cast(caster);
            check(f, n >= 1 && MomentStop.frozen(swarm), "moment stop did not freeze the swarm: " + n);
            MoonClock.skipTo(moon, 0);
            // 望の灯守
            FullKeeper lord = FieldArena.summon(moon);
            spawned.add(lord);
            check(f, lord != null && lord.getMaxHealth() >= FullKeeper.HP - 0.01 && lord.getMaxHealth() <= 1024,
                    "the keeper did not appear with its hp: " + (lord == null ? -1 : lord.getMaxHealth()));
            if (lord != null) {
                FieldTide.setTo(moon, FieldTide.Tide.BRIGHT, 10);
                lord.chargeForTest(40);
                float h0 = lord.getHealth();
                lord.invulnerableTime = 0;
                lord.hurt(moon.damageSources().generic(), 50f);
                float d1 = h0 - lord.getHealth();
                check(f, d1 <= 18f, "the charging keeper took full damage: " + d1);
                lord.chargeForTest(FullKeeper.HP > 0 ? 160 : 0);
                FieldTide.setTo(moon, FieldTide.Tide.TO_DIM, 1);
                lord.tick();
                check(f, lord.releasing(), "a full keeper did not start its release at the turn");
                MomentStop.freeze(lord, moon.getGameTime() + MomentStop.DURATION);
                check(f, lord.kneeling() && !lord.releasing(), "moment stop during the release did not make the keeper kneel");
                float h1 = lord.getHealth();
                lord.invulnerableTime = 0;
                lord.hurt(moon.damageSources().generic(), 50f);
                float d2 = h1 - lord.getHealth();
                check(f, d2 >= 70f, "the kneeling keeper did not take extra damage: " + d2);
                FieldArena.summon(moon);
                int k = FieldArena.lords(moon).size();
                check(f, k == 1, "summoning again should keep one keeper, found " + k);
            }
            FieldArena.clear(moon);
            check(f, FieldArena.lords(moon).isEmpty() && !FieldTide.fast(), "clearing the mesa left the keeper or the fast tide");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST moon field crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            spawned.stream().filter(e -> e != null && e.isAlive()).forEach(Entity::discard);
            if (moon != null) {
                MoonClock.skipTo(moon, 0);
                for (int[] c : forced) {
                    moon.setChunkForced(c[0], c[1], false);
                }
            }
        }
        report(f);
    }

    static boolean solid(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.getBlock() instanceof TideStoneBlock && TideStoneBlock.solid(st);
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    static BlockPos standableNear(ServerLevel level, BlockPos at) {
        for (int dy = 12; dy >= -12; dy--) {
            if (standable(level, at.above(dy))) {
                return at.above(dy);
            }
        }
        return at;
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon field PASSED (field terrain, camp, pond, gully, tower, mesa, puzzle blocks, tide swell/ebb/turn and full moon, "
                    + "moment stop hold and freeze, pond and tower reach, falls and mesa guard, main_moon_field (no end card yet), mob numbers and tide behaviour, "
                    + "keeper summon, charge guard and kneel)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon field FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
