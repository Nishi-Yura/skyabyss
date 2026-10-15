package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

// 1章の空の演出の自己テスト。SKYABYSS_SELFTEST=1 の専用サーバーで、起動直後に一度だけ動く (止めるのは TitanBackSelfTest)。
// いちばん大事なのは「プレイヤーの建築を壊さない」ことなので、空中 (y=220〜250) に石の峰を作り、
// 人工物を一つずつ置いては「削らない」と判断することを確かめる。削ったときは、計画した所以外が一つも変わっていないことを確かめる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterSkySelfTest {
    private static final int CX = -4000;
    private static final int CZ = -4000;
    private static final int BASE = 220;
    private static final int TOP = 250;
    private static final int RADIUS = 28;
    private static final int CLEAR_RADIUS = 60;

    private ChapterSkySelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        try {
            checkMoon(failures);
            checkStages(failures);
            checkBlocker(failures);
            checkOutcomes(failures);
            checkYaw(failures);
            checkData(server, failures);
            checkPayloads(server, failures);
            checkChapterOne(level, failures);
            checkBlockSets(failures);
            checkScars(level, failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sky crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST chapter sky PASSED (moon, stages, eye conditions, outcomes, data, payloads, chapter 1, "
                    + "peak scar: finds the peak, carves only the plan, refuses every artifact, stops when a block changes)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST chapter sky FAILED: {}", f));
        }
    }

    private static void checkMoon(List<String> failures) {
        float[] expected = {1 / 7f, 2 / 7f, 3 / 7f, 4 / 7f, 5 / 7f, 6 / 7f, 1f, 1 / 7f};
        for (int day = 0; day < expected.length; day++) {
            float m = ChapterSky.provisionalMoon(day * 24000L + 18000L, 7);
            if (Math.abs(m - expected[day]) > 1e-4) {
                failures.add("provisional moon on day " + day + " is " + m);
            }
        }
        // 4日目の夜 (index 3) から半分を超える
        if (ChapterSky.provisionalMoon(2 * 24000L + 18000L, 7) >= CloudEyeEvents.MOON_MIN
                || ChapterSky.provisionalMoon(3 * 24000L + 18000L, 7) < CloudEyeEvents.MOON_MIN) {
            failures.add("the moon should pass half on the 4th night");
        }
        if (ChapterSky.provisionalMoon(-1, 7) <= 0f) {
            failures.add("negative day time breaks the provisional moon");
        }
    }

    private static void checkStages(List<String> failures) {
        CloudEyeStage prev = null;
        for (int i = 0; i < CloudEyeStage.STAGES.length; i++) {
            CloudEyeStage s = CloudEyeStage.byIndex(i);
            if (s.index() != i || s.width() > 64f || s.offsetMin() < 30f || s.elevationMin() < 30f) {
                failures.add("cloud eye stage " + i + " is out of the safe range (too big, or too central)");
            }
            if (prev != null && (s.width() <= prev.width() || s.holdTicks() <= prev.holdTicks())) {
                failures.add("cloud eye stage " + i + " does not grow");
            }
            prev = s;
        }
        if (CloudEyeStage.of(0, 0.5f).index() != 0 || CloudEyeStage.of(1, 0.5f).index() != 1 || CloudEyeStage.of(3, 0.5f).index() != 2
                || CloudEyeStage.of(0, 0.9f).index() != 1 || CloudEyeStage.of(50, 1f).index() != 2) {
            failures.add("cloud eye stage selection is wrong");
        }
        if (CloudEyeStage.MAX_TICKS < CloudEyeStage.HUSH_TICKS + CloudEyeStage.OPEN_TICKS + CloudEyeStage.byIndex(2).holdTicks()
                + CloudEyeStage.CLOSE_TICKS + CloudEyeStage.CLEAR_TICKS) {
            failures.add("cloud eye MAX_TICKS is shorter than the longest eye");
        }
    }

    private static void checkBlocker(List<String> failures) {
        long night = 18000L;
        long day = 6000L;
        expect(failures, "eye ok", CloudEyeEvents.blocker(1, true, 0.6f, night, false, true, true, 100, 50, false), null);
        expect(failures, "eye frequency 0", CloudEyeEvents.blocker(0, true, 0.6f, night, false, true, true, 100, 50, false), "disabled");
        expect(failures, "eye prologue", CloudEyeEvents.blocker(1, false, 0.6f, night, false, true, true, 100, 50, false), "not in chapter 1");
        expect(failures, "eye moon far", CloudEyeEvents.blocker(1, true, 0.49f, night, false, true, true, 100, 50, false), "moon not near");
        expect(failures, "eye day", CloudEyeEvents.blocker(1, true, 0.6f, day, false, true, true, 100, 50, false), "not night");
        expect(failures, "eye dusk", CloudEyeEvents.blocker(1, true, 0.6f, 13500L, false, true, true, 100, 50, false), "not night");
        expect(failures, "eye thunder", CloudEyeEvents.blocker(1, true, 0.6f, night, true, true, true, 100, 50, false), "thunderstorm");
        expect(failures, "eye indoors", CloudEyeEvents.blocker(1, true, 0.6f, night, false, false, true, 100, 50, false), "not outdoors");
        expect(failures, "eye asleep", CloudEyeEvents.blocker(1, true, 0.6f, night, false, true, false, 100, 50, false), "not awake");
        expect(failures, "eye too soon", CloudEyeEvents.blocker(1, true, 0.6f, night, false, true, true, 100, 150, false), "too soon");
        expect(failures, "eye unscheduled", CloudEyeEvents.blocker(1, true, 0.6f, night, false, true, true, 100, -1, false), "too soon");
        expect(failures, "eye twice", CloudEyeEvents.blocker(1, true, 0.6f, night, false, true, true, 100, 50, true), "already open");
        expect(failures, "eye later night", CloudEyeEvents.blocker(1, true, 1f, 24000L * 9 + 21000L, false, true, true, 100, 50, false), null);
    }

    private static void expect(List<String> failures, String what, String actual, String wanted) {
        if (wanted == null ? actual != null : !wanted.equals(actual)) {
            failures.add(what + ": expected " + wanted + " but got " + actual);
        }
    }

    private static void checkOutcomes(List<String> failures) {
        ChapterSkyData.Entry e = new ChapterSkyData.Entry();
        e.nextEye = 10_000;
        CloudEyeEvents.applyOutcome(e, CloudEyeOutcome.MISSED, 5_000, 1_000);
        if (e.nextEye != 5_000 || e.eyeMisses != 1) {
            failures.add("a missed eye should come back once, early");
        }
        e.nextEye = 10_000;
        CloudEyeEvents.applyOutcome(e, CloudEyeOutcome.MISSED, 5_000, 1_000);
        if (e.nextEye != 10_000) {
            failures.add("a second missed eye should not come back early");
        }
        CloudEyeEvents.applyOutcome(e, CloudEyeOutcome.MET, 5_000, 1_000);
        if (e.eyeSightings != 1 || e.eyeMisses != 0 || e.nextEye != 10_000) {
            failures.add("meeting the eye should count a sighting and keep the gap");
        }
        CloudEyeEvents.applyOutcome(e, CloudEyeOutcome.DISABLED, 5_000, 1_000);
        if (e.eyeSightings != 1 || e.nextEye != 10_000) {
            failures.add("a disabled eye should change nothing");
        }
        CloudEyeEvents.applyOutcome(e, CloudEyeOutcome.CANCELLED, 5_000, 1_000);
        if (e.nextEye != 1_000 || e.eyeSightings != 1) {
            failures.add("a cancelled eye should be allowed again soon, without counting");
        }
        if (CloudEyeOutcome.byId(99) != CloudEyeOutcome.CANCELLED) {
            failures.add("an unknown outcome should be treated as cancelled");
        }
    }

    private static void checkYaw(List<String> failures) {
        RandomSource r = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            float look = r.nextFloat() * 360f - 180f;
            float offset = Mth.lerp(r.nextFloat(), 35f, 110f);
            float yaw = CloudEyeEvents.pickYaw(look, offset, r.nextBoolean());
            float diff = Math.abs(Mth.wrapDegrees(yaw - look));
            if (Math.abs(diff - offset) > 0.01f) {
                failures.add("the eye opened " + diff + " degrees from the view instead of " + offset);
                return;
            }
        }
        // 南を向いているとき (東西の月の通り道から最も遠い向き) 左右の外し方が 90 度なら、東西を避けられないので片側を選ぶだけ
        float yaw = CloudEyeEvents.pickYaw(0f, 40f, true);
        if (Math.abs(Mth.wrapDegrees(yaw)) != 40f) {
            failures.add("pickYaw broke for the south");
        }
    }

    private static void checkData(MinecraftServer server, List<String> failures) {
        ChapterSkyData data = ChapterSkyData.load(new CompoundTag(), server.registryAccess());
        UUID id = UUID.randomUUID();
        ChapterSkyData.Entry e = data.entry(id);
        e.boarded = true;
        e.nextEye = 12345L;
        e.eyeSightings = 3;
        e.eyeMisses = 1;
        e.home = new BlockPos(10, 70, -20);
        data.markPlaced(ChunkPos.asLong(5, -7));
        data.addScar(new ChapterSkyData.Scar(new BlockPos(1, 2, 3), 99L, 120));
        data.setMoonOverride(0.75f);
        ChapterSkyData back = ChapterSkyData.load(data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        ChapterSkyData.Entry b = back.entry(id);
        if (!b.boarded || b.nextEye != 12345L || b.eyeSightings != 3 || b.eyeMisses != 1 || !new BlockPos(10, 70, -20).equals(b.home)
                || !back.wasPlaced(ChunkPos.asLong(5, -7)) || back.wasPlaced(ChunkPos.asLong(5, -6)) || back.scars().size() != 1
                || back.scars().get(0).removed() != 120 || Math.abs(back.moonOverride() - 0.75f) > 1e-6) {
            failures.add("chapter sky data did not survive a save and load");
        }
        ChapterSkyData empty = ChapterSkyData.load(new CompoundTag(), server.registryAccess());
        if (empty.moonOverride() >= 0 || empty.entry(id).nextEye != -1) {
            failures.add("empty chapter sky data has wrong defaults");
        }
        for (int i = 0; i < ChapterSkyData.MAX_SCARS + 5; i++) {
            empty.addScar(new ChapterSkyData.Scar(BlockPos.ZERO, i, 1));
        }
        if (empty.scars().size() != ChapterSkyData.MAX_SCARS || empty.scars().get(0).gameTime() != 5) {
            failures.add("scar list should keep only the newest " + ChapterSkyData.MAX_SCARS);
        }
    }

    private static void checkPayloads(MinecraftServer server, List<String> failures) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        CloudEyePayload p = new CloudEyePayload(42, -135.5f, 47.25f, 2, -123456);
        CloudEyePayload.STREAM_CODEC.encode(buf, p);
        CloudEyePayload q = CloudEyePayload.STREAM_CODEC.decode(buf);
        if (!p.equals(q)) {
            failures.add("cloud eye payload round trip changed it: " + q);
        }
        buf.clear();
        CloudEyeEndPayload end = new CloudEyeEndPayload(42, CloudEyeOutcome.MET.ordinal());
        CloudEyeEndPayload.STREAM_CODEC.encode(buf, end);
        if (!end.equals(CloudEyeEndPayload.STREAM_CODEC.decode(buf))) {
            failures.add("cloud eye end payload round trip changed it");
        }
        buf.release();
    }

    private static void checkChapterOne(ServerLevel level, List<String> failures) {
        FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1a3b5e-0000-4000-8000-00000000c1a1"), "skyabyss_ch1_test"));
        ChapterSkyData data = ChapterSkyData.get(level);
        ChapterSkyData.Entry entry = data.entry(fake.getUUID());
        entry.boarded = false;
        Lore.lock(fake, ChapterSky.CHAPTER_ONE_LORE);
        if (ChapterSky.inChapterOne(fake)) {
            failures.add("a new player should be in the prologue");
        }
        ChapterSky.markBoarded(fake);
        if (!ChapterSky.inChapterOne(fake)) {
            failures.add("boarding the titan should start chapter 1");
        }
        entry.boarded = false;
        if (!Lore.unlock(fake, ChapterSky.CHAPTER_ONE_LORE, false) || !ChapterSky.inChapterOne(fake)) {
            failures.add("the sky ruins lore page should also mean chapter 1");
        }
        Lore.lock(fake, ChapterSky.CHAPTER_ONE_LORE);
        data.entries().remove(fake.getUUID());
    }

    private static void checkBlockSets(List<String> failures) {
        // 人の手を思わせるブロックは、どれも「自然」に入らない
        Block[] artifacts = {Blocks.TORCH, Blocks.WALL_TORCH, Blocks.LANTERN, Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CHEST,
                Blocks.RED_BED, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.GLASS, Blocks.OAK_FENCE, Blocks.OAK_DOOR,
                Blocks.OAK_LOG, Blocks.SMOOTH_STONE, Blocks.STONE_SLAB, Blocks.LADDER, Blocks.WHITE_WOOL, Blocks.BRICKS, Blocks.SANDSTONE,
                Blocks.POLISHED_ANDESITE, Blocks.CAMPFIRE, Blocks.SCAFFOLDING, Blocks.WHITE_CONCRETE, Blocks.TERRACOTTA, Blocks.OAK_SIGN,
                Blocks.RAIL, Blocks.REDSTONE_WIRE, Blocks.FLOWER_POT, Blocks.HAY_BLOCK, Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.GLOWSTONE,
                Blocks.SEA_LANTERN, Blocks.MOSSY_COBBLESTONE, Blocks.SPRUCE_LOG, Blocks.CHISELED_STONE_BRICKS, Blocks.IRON_BLOCK};
        for (Block b : artifacts) {
            if (PeakScar.natural(b.defaultBlockState()) || PeakScar.removable(b.defaultBlockState())) {
                failures.add(b + " counts as natural terrain");
            }
        }
        BlockState placedLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState wildLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        if (PeakScar.natural(placedLeaves) || !PeakScar.natural(wildLeaves) || PeakScar.removable(wildLeaves)) {
            failures.add("leaves: placed ones must not count as natural, wild ones are natural but not removed");
        }
        for (Block b : new Block[]{Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.SNOW_BLOCK, Blocks.CALCITE, Blocks.EMERALD_ORE}) {
            if (!PeakScar.removable(b.defaultBlockState())) {
                failures.add(b + " should be removable");
            }
        }
    }

    // ---- 削られた山頂 ----

    private static void checkScars(ServerLevel level, List<String> failures) {
        ChapterSkyData data = ChapterSkyData.get(level);
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = (CX - CLEAR_RADIUS - 16) >> 4; cx <= (CX + CLEAR_RADIUS + 16) >> 4; cx++) {
            for (int cz = (CZ - CLEAR_RADIUS - 16) >> 4; cz <= (CZ + CLEAR_RADIUS + 16) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new ChunkPos(cx, cz));
            }
        }
        try {
            forgetTestScars(data);
            float dirX = 1f;
            float dirZ = 0f;

            // 1. 経路の下の峰を見つける
            buildPeak(level);
            List<PeakScar.Candidate> found = PeakScar.findCandidates(level, CX - 110, CZ + 10, dirX, dirZ, 220, 40);
            if (found.isEmpty() || Math.abs(found.get(0).x() - CX) > 2 || Math.abs(found.get(0).z() - CZ) > 2 || found.get(0).y() != TOP) {
                failures.add("the peak under the pass was not found: " + found);
                return;
            }
            PeakScar.Candidate peak = found.get(0);

            // 2. 何もなければ削れる。計画した所だけが変わる
            PeakScar.Result r = PeakScar.plan(level, peak, dirX, dirZ, 0.5f, RandomSource.create(1), PeakScar.PLAYER_CLEAR);
            if (r.plan() == null) {
                failures.add("a clean natural peak was refused: " + r.reason());
                return;
            }
            PeakScar.Plan plan = r.plan();
            if (plan.removed() < PeakScar.MIN_REMOVED || plan.removed() > PeakScar.MAX_REMOVED) {
                failures.add("scar size out of range: " + plan.removed());
            }
            Map<BlockPos, BlockState> planned = new HashMap<>();
            boolean calcite = false;
            boolean debris = false;
            for (PeakScar.Change c : plan.changes()) {
                planned.put(c.pos(), c.to());
                boolean removal = c.to().isAir();
                if (removal && !PeakScar.removable(c.expect())) {
                    failures.add("the plan removes " + c.expect() + " at " + c.pos());
                }
                if (!removal && !c.expect().isAir() && !PeakScar.vegetation(c.expect()) && !PeakScar.WHITENS.contains(c.expect().getBlock())
                        && !PeakScar.snow(c.expect())) {
                    failures.add("the plan replaces " + c.expect() + " at " + c.pos());
                }
                calcite |= c.to().is(Blocks.CALCITE) && PeakScar.WHITENS.contains(c.expect().getBlock());
                debris |= c.expect().isAir() && !c.to().isAir();
                if (Math.hypot(c.pos().getX() - CX, c.pos().getZ() - CZ) > RADIUS + 2 || c.pos().getY() < BASE || c.pos().getY() > TOP + 1) {
                    failures.add("the plan reaches outside the peak at " + c.pos());
                }
            }
            if (!calcite || !debris) {
                failures.add("the scar should leave pale cut rock and scattered debris (calcite " + calcite + ", debris " + debris + ")");
            }
            Map<BlockPos, BlockState> before = snapshot(level);
            if (!PeakScarEvents.runNow(level, plan)) {
                failures.add("carving a clean plan stopped part way");
            }
            int changedOutside = 0;
            for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                BlockState now = level.getBlockState(e.getKey());
                BlockState want = planned.getOrDefault(e.getKey(), e.getValue());
                if (now != want && changedOutside++ < 3) {
                    failures.add("after carving, " + e.getKey() + " is " + now + " (expected " + want + ")");
                }
            }
            if (data.scars().stream().noneMatch(s -> s.pos().equals(plan.center()))) {
                failures.add("the scar was not recorded");
            }
            // 同じ所はもう削らない
            buildPeak(level);
            expectRefused(level, peak, "near another scar", failures);
            forgetTestScars(data);

            // 雪の峰では、切り口を白くせず、雪の底を灰色の岩にする (雪に紛れさせない)
            buildPeak(level, true);
            PeakScar.Result snowyPlan = PeakScar.plan(level, peak, dirX, dirZ, 0.5f, RandomSource.create(8), PeakScar.PLAYER_CLEAR);
            if (snowyPlan.plan() == null) {
                failures.add("a snowy peak was refused: " + snowyPlan.reason());
            } else {
                boolean anyCalcite = snowyPlan.plan().changes().stream().anyMatch(c -> c.to().is(Blocks.CALCITE) && !c.expect().isAir());
                boolean bareRock = snowyPlan.plan().changes().stream().anyMatch(c -> PeakScar.snow(c.expect()) && !c.to().isAir());
                if (anyCalcite || !bareRock) {
                    failures.add("a snowy peak should be cut to grey rock, not calcite (calcite " + anyCalcite + ", rock " + bareRock + ")");
                }
            }

            // 3. 強さ (月の近さ) で深く長くなる
            PeakScar.Result weak = PeakScar.plan(level, peak, dirX, dirZ, 0f, RandomSource.create(2), PeakScar.PLAYER_CLEAR);
            PeakScar.Result strong = PeakScar.plan(level, peak, dirX, dirZ, 1f, RandomSource.create(2), PeakScar.PLAYER_CLEAR);
            if (weak.plan() == null || strong.plan() == null || strong.plan().removed() <= weak.plan().removed()) {
                failures.add("a nearer moon should scar deeper (" + weak.reason() + " / " + strong.reason() + ")");
            }

            // 4. 人工物があれば削らない (一つずつ)
            BlockPos topPos = new BlockPos(CX, TOP + 1, CZ);
            refuseWith(level, peak, surface(level, CX + 14, CZ), Blocks.TORCH.defaultBlockState(), "torch 14 blocks away", failures);
            refuseWith(level, peak, surface(level, CX - 3, CZ + 12), Blocks.CRAFTING_TABLE.defaultBlockState(), "crafting table on the slope", failures);
            refuseWith(level, peak, new BlockPos(CX + 2, TOP - 10, CZ), Blocks.RED_BED.defaultBlockState(), "bed buried under the cut", failures);
            refuseWith(level, peak, new BlockPos(CX, TOP, CZ), Blocks.OAK_PLANKS.defaultBlockState(), "planks on the very top", failures);
            refuseWith(level, peak, new BlockPos(CX - 1, TOP - 1, CZ), Blocks.COBBLESTONE.defaultBlockState(), "cobblestone in the cut", failures);
            refuseWith(level, peak, surface(level, CX + 9, CZ - 9), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                    "player-placed leaves", failures);
            refuseWith(level, peak, new BlockPos(CX + 6, TOP - 6, CZ + 3), Blocks.CHEST.defaultBlockState(), "chest inside the peak", failures);
            refuseWith(level, peak, topPos, Blocks.WATER.defaultBlockState(), "water on the top", failures);
            refuseWith(level, peak, new BlockPos(CX + 20, TOP + 8, CZ), Blocks.GLASS.defaultBlockState(), "glass floating above", failures);
            // 自然に生えた葉が少し離れてあるだけなら削れる
            buildPeak(level);
            level.setBlock(surface(level, CX + 13, CZ + 13), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), Block.UPDATE_CLIENTS);
            PeakScar.Result wild = PeakScar.plan(level, peak, dirX, dirZ, 0.5f, RandomSource.create(3), PeakScar.PLAYER_CLEAR);
            if (wild.plan() == null) {
                failures.add("wild leaves at the edge should not stop the scar: " + wild.reason());
            }

            // 自然の木 (幹の上に自然の葉) が斜面にあっても削れる。葉のない原木の柱、皮をはいだ原木は削らない
            buildPeak(level);
            BlockPos trunk = surface(level, CX - 12, CZ - 10);
            for (int i = 0; i < 5; i++) {
                level.setBlock(trunk.above(i), Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(trunk.offset(dx, 5, dz), Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), Block.UPDATE_CLIENTS);
                }
            }
            PeakScar.Result tree = PeakScar.plan(level, peak, dirX, dirZ, 0.5f, RandomSource.create(7), PeakScar.PLAYER_CLEAR);
            if (tree.plan() == null) {
                failures.add("a wild spruce on the slope should not stop the scar: " + tree.reason());
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(trunk.offset(dx, 5, dz), Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
                }
            }
            expectRefused(level, peak, "non-natural", failures);
            refuseWith(level, peak, surface(level, CX + 11, CZ + 6), Blocks.OAK_LOG.defaultBlockState(), "a bare log", failures);
            refuseWith(level, peak, surface(level, CX + 11, CZ + 6), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), "a stripped log", failures);

            // 5. 防具立て (人の持ち物) があれば削らない
            buildPeak(level);
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand != null) {
                BlockPos at = surface(level, CX + 10, CZ + 4);
                stand.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.addFreshEntity(stand);
                expectRefused(level, peak, "owned entity", failures);
                stand.discard();
            }

            // 6. プレイヤーがブロックを置いたチャンクは削らない
            buildPeak(level);
            long chunk = ChunkPos.asLong((CX + 12) >> 4, CZ >> 4);
            data.markPlaced(chunk);
            expectRefused(level, peak, "player placed blocks here", failures);
            data.clearPlacedForTest(chunk);

            // 7. プレイヤーが長く過ごしたチャンクは削らない
            LevelChunk lc = level.getChunk(CX >> 4, CZ >> 4);
            long inhabited = lc.getInhabitedTime();
            lc.setInhabitedTime(PeakScar.MAX_INHABITED + 1);
            expectRefused(level, peak, "players spent long here", failures);
            lc.setInhabitedTime(inhabited);

            // 8. だれかの復活地点 (ログアウト中の人の分も) の近くは削らない
            UUID someone = UUID.fromString("5c1a3b5e-0000-4000-8000-00000000c1a2");
            data.entry(someone).home = new BlockPos(CX + 100, 64, CZ + 40);
            expectRefused(level, peak, "near a home", failures);
            data.entries().remove(someone);

            // 9. 計画のあとで、だれかがブロックを置いた (または掘った) ら、そこで止まる
            buildPeak(level);
            PeakScar.Result again = PeakScar.plan(level, peak, dirX, dirZ, 0.5f, RandomSource.create(4), PeakScar.PLAYER_CLEAR);
            if (again.plan() == null) {
                failures.add("could not plan for the stop test: " + again.reason());
            } else {
                List<PeakScar.Change> changes = again.plan().changes();
                int mid = changes.size() / 2;
                BlockPos changed = changes.get(mid).pos();
                level.setBlock(changed, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                if (PeakScarEvents.runNow(level, again.plan())) {
                    failures.add("carving did not stop at a block that changed after planning");
                }
                if (!level.getBlockState(changed).is(Blocks.GLASS)) {
                    failures.add("the block that changed after planning was carved");
                }
                for (int i = mid + 1; i < changes.size(); i++) {
                    if (level.getBlockState(changes.get(i).pos()) != changes.get(i).expect()) {
                        failures.add("carving went on after it should have stopped, at " + changes.get(i).pos());
                        break;
                    }
                }
                if (data.scars().stream().anyMatch(s -> s.pos().equals(again.plan().center()))) {
                    failures.add("a stopped scar was recorded");
                }
            }

            // 10. 低い所・平らな所は峰とみなさない
            clearArea(level);
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    level.setBlock(new BlockPos(CX + x, TOP, CZ + z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            List<PeakScar.Candidate> flat = PeakScar.findCandidates(level, CX - 110, CZ, dirX, dirZ, 220, 40);
            if (!flat.isEmpty() && flat.stream().anyMatch(c -> Math.abs(c.x() - CX) < RADIUS && Math.abs(c.z() - CZ) < RADIUS)) {
                failures.add("a flat plateau was taken for a peak");
            }
        } finally {
            clearArea(level);
            forgetTestScars(data);
            for (ChunkPos p : forced) {
                level.setChunkForced(p.x, p.z, false);
            }
        }
    }

    private static void refuseWith(ServerLevel level, PeakScar.Candidate peak, BlockPos pos, BlockState state, String what,
                                   List<String> failures) {
        buildPeak(level);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        PeakScar.Result r = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(5), PeakScar.PLAYER_CLEAR);
        if (r.plan() != null) {
            failures.add("the peak was planned for carving with " + what + " at " + pos);
        } else if (!r.reason().startsWith("non-natural") && !r.reason().startsWith("cannot remove")) {
            failures.add("the peak with " + what + " was refused for another reason: " + r.reason());
        }
    }

    private static void expectRefused(ServerLevel level, PeakScar.Candidate peak, String reason, List<String> failures) {
        PeakScar.Result r = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(6), PeakScar.PLAYER_CLEAR);
        if (r.plan() != null || !r.reason().startsWith(reason)) {
            failures.add("expected the scar to be refused for '" + reason + "' but got " + (r.plan() != null ? "a plan" : r.reason()));
        }
    }

    private static void buildPeak(ServerLevel level) {
        buildPeak(level, false);
    }

    // 空中に円錐の峰 (石。上の二段は土と草、snowy なら雪) を作る。前の結果は消してから
    private static void buildPeak(ServerLevel level, boolean snowy) {
        clearArea(level);
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double r = Math.hypot(x, z);
                if (r > RADIUS) {
                    continue;
                }
                int top = (int) Math.round(TOP - r * 0.7);
                for (int y = BASE; y <= top; y++) {
                    BlockState s = y == top ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : y >= top - 2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    if (r < 4 && y >= top - 2) {
                        // 頂は岩がむき出し
                        s = Blocks.STONE.defaultBlockState();
                    }
                    if (snowy && y >= top - 2) {
                        // 雪の峰: 上の三段が雪 (頂も)
                        s = Blocks.SNOW_BLOCK.defaultBlockState();
                    }
                    level.setBlock(new BlockPos(CX + x, y, CZ + z), s, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void clearArea(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                for (int y = BASE - 2; y <= TOP + 14; y++) {
                    m.set(CX + x, y, CZ + z);
                    if (!level.getBlockState(m).isAir()) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                for (int y = BASE - 2; y <= TOP + 14; y++) {
                    BlockPos p = new BlockPos(CX + x, y, CZ + z);
                    map.put(p, level.getBlockState(p));
                }
            }
        }
        return map;
    }

    // 峰の地表のすぐ上 (置き物を置く所)
    private static BlockPos surface(ServerLevel level, int x, int z) {
        double r = Math.hypot(x - CX, z - CZ);
        return new BlockPos(x, (int) Math.round(TOP - r * 0.7) + 1, z);
    }

    private static void forgetTestScars(ChapterSkyData data) {
        data.scars().removeIf(s -> Math.abs(s.pos().getX() - CX) < 200 && Math.abs(s.pos().getZ() - CZ) < 200);
    }
}
