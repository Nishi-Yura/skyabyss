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
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 1章の空の演出の自己テスト。SKYABYSS_SELFTEST=1 の専用サーバーで、起動直後に一度だけ動く (止めるのは TitanBackSelfTest)。
// いちばん大事なのは「プレイヤーの建築を壊さない」ことなので、空中 (y=220〜250) に石の峰を作り、
// 人工物を一つずつ置いては「削らない」と判断することを確かめる。削ったときは、計画した所以外が一つも変わっていないことを確かめる
public final class ChapterSkySelfTest {
    private static final int CX = -4000;
    private static final int CZ = -4000;
    private static final int BASE = 220;
    private static final int TOP = 250;
    private static final int RADIUS = 28;
    private static final int CLEAR_RADIUS = 60;

    private ChapterSkySelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき)。重いので段に分け、1 tick に 1 段ずつ流す:
    // 決まりごと → 空中の峰を削る → 置いた・掘った記録 → 本物の山 (場所ごとに、チャンクの生成を待ってから) → 砂漠とまとめ
    private static final List<String> FAILURES = new ArrayList<>();

    public static List<jp.revati.skyabyss.SelfTestRunner.Step> steps() {
        return List.of(
                jp.revati.skyabyss.SelfTestRunner.Step.once("chapter sky (rules)", server -> guarded(() -> {
                    FAILURES.clear();
                    REAL_REASONS.clear();
                    REAL_HITS.clear();
                    REAL_FAILURES.clear();
                    realTried = 0;
                    realCarved = 0;
                    realGrids = 0;
                    realSite = 0;
                    realStage = 0;
                    realSkipped = 0;
                    realStarted = 0;
                    checkMoon(FAILURES);
                    checkStages(FAILURES);
                    checkBlocker(FAILURES);
                    checkOutcomes(FAILURES);
                    checkYaw(FAILURES);
                    checkData(server, FAILURES);
                    checkPayloads(server, FAILURES);
                    checkChapterOne(server.overworld(), FAILURES);
                    checkBlockSets(FAILURES);
                })),
                jp.revati.skyabyss.SelfTestRunner.Step.once("chapter sky (scars)", server -> guarded(() -> checkScars(server.overworld(), FAILURES))),
                jp.revati.skyabyss.SelfTestRunner.Step.once("chapter sky (recording)", server -> guarded(() -> checkRecording(server.overworld(), FAILURES))),
                jp.revati.skyabyss.SelfTestRunner.Step.until("chapter sky (real peaks)", server -> {
                    try {
                        return realPeakStep(server.overworld());
                    } catch (RuntimeException e) {
                        SkyAbyss.LOGGER.error("SELFTEST chapter sky crashed", e);
                        FAILURES.add("crashed: " + e);
                        return true;
                    }
                }),
                jp.revati.skyabyss.SelfTestRunner.Step.once("chapter sky", server -> {
                    guarded(() -> finishRealPeaks(server.overworld(), FAILURES));
                    report();
                }));
    }

    private static void guarded(Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST chapter sky crashed", e);
            FAILURES.add("crashed: " + e);
        }
    }

    private static void report() {
        if (FAILURES.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST chapter sky PASSED (moon, stages, eye conditions, outcomes, data, payloads, chapter 1, "
                    + "peak scar: finds the peak, carves only the plan, refuses every artifact and every stone-and-dirt build, stops when a block changes, "
                    + "records placing and digging, tries real mountain peaks)");
        } else {
            FAILURES.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST chapter sky FAILED: {}", f));
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
        // 固定の峰のバイオームは種しだいなので、草の育つ平原に決めて試す (砂漠などの判定は checkForeignSoil で決まった形で試す)
        PeakScar.testBiome = biome(level, Biomes.PLAINS);
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

            // 9b. 石・土・草だけで作った建築 (人が積んだ形) は削らない
            checkBuilt(level, peak, failures);

            // 9c. Mod を入れる前からあった古いチャンクは、過ごした時間の上限が短い (5分)
            buildPeak(level);
            LevelChunk old = level.getChunk(CX >> 4, CZ >> 4);
            boolean wasFresh = ChapterSky.freshChunk(old);
            long oldInhabited = old.getInhabitedTime();
            old.setData(ChapterSkyRegistry.FRESH_CHUNK, false);
            old.setInhabitedTime(PeakScar.OLD_MAX_INHABITED + 1);
            expectRefused(level, peak, "players spent long here", failures);
            old.setData(ChapterSkyRegistry.FRESH_CHUNK, true);
            PeakScar.Result freshOk = PeakScar.plan(level, peak, dirX, dirZ, 0.5f, RandomSource.create(9), PeakScar.PLAYER_CLEAR);
            if (freshOk.plan() == null) {
                failures.add("a chunk made with the mod should allow " + (PeakScar.OLD_MAX_INHABITED + 1) + " ticks nearby: " + freshOk.reason());
            }
            old.setData(ChapterSkyRegistry.FRESH_CHUNK, wasFresh);
            old.setInhabitedTime(oldInhabited);

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

            // 11. 砂漠・荒野・浜の地表の草や土は運び込まれたもの。同じ峰でも、平原なら削り、砂漠などなら削らない
            checkForeignSoil(level, peak, failures);
        } finally {
            PeakScar.testBiome = null;
            clearArea(level);
            forgetTestScars(data);
            for (ChunkPos p : forced) {
                level.setChunkForced(p.x, p.z, false);
            }
        }
    }

    private static Holder<Biome> biome(ServerLevel level, ResourceKey<Biome> key) {
        return level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(key);
    }

    // バイオームの表層に合わない土の判定を、種に頼らず決まった形で試す (終わったら平原に戻す)
    private static void checkForeignSoil(ServerLevel level, PeakScar.Candidate peak, List<String> failures) {
        for (ResourceKey<Biome> key : List.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS, Biomes.BEACH,
                Biomes.SNOWY_BEACH)) {
            if (!PeakScar.foreignBiome(biome(level, key))) {
                failures.add("soil on " + key.location().getPath() + " was not taken as foreign");
            }
        }
        for (ResourceKey<Biome> key : List.of(Biomes.PLAINS, Biomes.MEADOW, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.JAGGED_PEAKS,
                Biomes.STONY_PEAKS, Biomes.FROZEN_PEAKS, Biomes.WINDSWEPT_HILLS)) {
            if (PeakScar.foreignBiome(biome(level, key))) {
                failures.add("soil on " + key.location().getPath() + " was taken as foreign");
            }
        }
        try {
            for (ResourceKey<Biome> key : List.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.BEACH)) {
                PeakScar.testBiome = biome(level, key);
                buildPeak(level);
                PeakScar.Result r = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(5), PeakScar.PLAYER_CLEAR);
                if (r.plan() != null || !r.reason().startsWith("built shape: soil that does not belong")) {
                    failures.add("a grassy peak in a " + key.location().getPath() + " should be refused as carried-in soil, got "
                            + (r.plan() != null ? "a plan" : r.reason()));
                }
            }
        } finally {
            PeakScar.testBiome = biome(level, Biomes.PLAINS);
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

    // ---- 石・土・草だけで作った建築 ----

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    // 峰の上に、自然のブロックだけで建てたものを一つずつ置き、どれも「人が積んだ形」として削らないことを確かめる
    private static void checkBuilt(ServerLevel level, PeakScar.Candidate peak, List<String> failures) {
        // 石の小屋 (5×5、壁3段、土の屋根) を斜面に
        refuseBuilt(level, peak, "a stone hut with a dirt roof", failures, () -> {
            int x0 = CX + 12;
            int z0 = CZ - 14;
            int base = highest(x0, z0, 5, 5);
            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    boolean edge = dx == 0 || dz == 0 || dx == 4 || dz == 4;
                    int ground = surface(level, x0 + dx, z0 + dz).getY();
                    for (int y = ground; y < base + 3; y++) {
                        set(level, x0 + dx, y, z0 + dz, edge || y < base ? STONE : Blocks.AIR.defaultBlockState());
                    }
                    set(level, x0 + dx, base + 3, z0 + dz, DIRT);
                }
            }
        });
        // 土の塔 (1×1、6段) を岩の頂に
        refuseBuilt(level, peak, "a dirt tower on the top", failures, () -> {
            int y = surface(level, CX + 2, CZ + 1).getY();
            for (int i = 0; i < 6; i++) {
                set(level, CX + 2, y + i, CZ + 1, DIRT);
            }
        });
        // 石の柱 (1×1、5段) を、草のない所 (頂の岩) に
        refuseBuilt(level, peak, "a stone pillar", failures, () -> {
            int y = surface(level, CX - 2, CZ - 2).getY();
            for (int i = 0; i < 5; i++) {
                set(level, CX - 2, y + i, CZ - 2, STONE);
            }
        });
        // 石の壁 (厚さ1、長さ7、上の高さをそろえる)
        refuseBuilt(level, peak, "a straight stone wall", failures, () -> {
            int top = highest(CX - 14, CZ - 16, 7, 1) + 3;
            for (int dx = 0; dx < 7; dx++) {
                for (int y = surface(level, CX - 14 + dx, CZ - 16).getY(); y <= top; y++) {
                    set(level, CX - 14 + dx, y, CZ - 16, STONE);
                }
            }
        });
        // 石の床 (8×8 の平らな台) を斜面に
        refuseBuilt(level, peak, "a flat stone floor", failures, () -> {
            int y = highest(CX - 6, CZ + 15, 8, 8);
            for (int dx = 0; dx < 8; dx++) {
                for (int dz = 0; dz < 8; dz++) {
                    for (int yy = surface(level, CX - 6 + dx, CZ + 15 + dz).getY(); yy <= y; yy++) {
                        set(level, CX - 6 + dx, yy, CZ + 15 + dz, STONE);
                    }
                }
            }
        });
        // 草の上の土の山 (2×2、高さ1〜2)
        refuseBuilt(level, peak, "a dirt heap on grass", failures, () -> {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    BlockPos p = surface(level, CX + 16 + dx, CZ + 8 + dz);
                    set(level, p.getX(), p.getY(), p.getZ(), DIRT);
                    if (dx == 0) {
                        set(level, p.getX(), p.getY() + 1, p.getZ(), DIRT);
                    }
                }
            }
        });
        // 土の立方体 (4×4×4) を斜面に
        refuseBuilt(level, peak, "a solid dirt cube", failures, () -> {
            int y = highest(CX - 20, CZ - 4, 4, 4);
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    for (int yy = surface(level, CX - 20 + dx, CZ - 4 + dz).getY(); yy < y + 4; yy++) {
                        set(level, CX - 20 + dx, yy, CZ - 4 + dz, DIRT);
                    }
                }
            }
        });
        // 草で隠した穴 (草の下の土を掘った)
        refuseBuilt(level, peak, "a pit hidden under grass", failures, () -> {
            BlockPos p = surface(level, CX + 8, CZ + 18).below();
            set(level, p.getX(), p.getY() - 1, p.getZ(), Blocks.AIR.defaultBlockState());
            set(level, p.getX(), p.getY() - 2, p.getZ(), Blocks.AIR.defaultBlockState());
        });
        // 草でふさいだ、もっと深い穴 (下の空きが3段。草の屋根の部屋の高さ)
        refuseBuilt(level, peak, "a deep pit hidden under grass", failures, () -> {
            BlockPos p = surface(level, CX - 9, CZ + 17).below();
            for (int d = 1; d <= 3; d++) {
                set(level, p.getX(), p.getY() - d, p.getZ(), Blocks.AIR.defaultBlockState());
            }
        });
        // 宙に浮いた草 (下が4段空き)。生成の山の張り出しによくある形なので、これだけでは建築とみなさない
        buildPeak(level);
        BlockPos floating = surface(level, CX + 10, CZ - 6).above(4);
        set(level, floating.getX(), floating.getY(), floating.getZ(), Blocks.GRASS_BLOCK.defaultBlockState());
        PeakScar.Result overhang = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(5), PeakScar.PLAYER_CLEAR);
        if (overhang.plan() == null) {
            failures.add("a lone floating grass block (a natural overhang) stopped the scar: " + overhang.reason());
        }
        // 削る所から遠い (横に16より外の) 松明。昔の範囲では見えなかった
        buildPeak(level);
        set(level, CX + 58, 239, CZ, STONE);
        set(level, CX + 58, 240, CZ, Blocks.TORCH.defaultBlockState());
        PeakScar.Result far = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(5), PeakScar.PLAYER_CLEAR);
        if (far.plan() != null || !(far.reason().startsWith("light nearby") || far.reason().startsWith("non-natural torch nearby"))) {
            failures.add("a torch 58 blocks from the peak should stop the scar, got " + (far.plan() != null ? "a plan" : far.reason()));
        }
        // 見つかる形の判定は、自然の峰を拒まない (作り直した峰はまた削れる)
        buildPeak(level);
        PeakScar.Result clean = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(5), PeakScar.PLAYER_CLEAR);
        if (clean.plan() == null) {
            failures.add("after the built tests, the clean peak was refused: " + clean.reason());
        }
    }

    // 建てたものを置いて、削らないことを確かめる。草が下にあるものは、時間がたって下の草が土に変わったあと
    // (草の手がかりが消えたあと) でも、形だけで削らないことを確かめる
    private static void refuseBuilt(ServerLevel level, PeakScar.Candidate peak, String what, List<String> failures, Runnable build) {
        boolean grassOnly = what.contains("grass");
        for (int pass = 0; pass < (grassOnly ? 1 : 2); pass++) {
            buildPeak(level);
            build.run();
            String label = pass == 0 ? what : what + " (grass under it already turned to dirt)";
            if (pass == 1) {
                decayCoveredGrass(level);
            }
            PeakScar.Result r = PeakScar.plan(level, peak, 1f, 0f, 0.5f, RandomSource.create(5), PeakScar.PLAYER_CLEAR);
            if (r.plan() != null) {
                failures.add("the peak was planned for carving with " + label);
            } else if (!r.reason().startsWith("built shape")) {
                failures.add("the peak with " + label + " was refused for another reason: " + r.reason());
            } else {
                SkyAbyss.LOGGER.info("SELFTEST chapter sky: {} -> {}", label, r.reason());
            }
        }
    }

    // 上がふさがった草を土に変える (ゲームの中で時間がたつと起きること)
    private static void decayCoveredGrass(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                for (int y = BASE; y <= TOP + 12; y++) {
                    m.set(CX + x, y, CZ + z);
                    if (level.getBlockState(m).is(Blocks.GRASS_BLOCK) && !level.getBlockState(m.above()).isAir()) {
                        level.setBlock(m, DIRT, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    // 範囲 (x0〜x0+w-1, z0〜z0+d-1) の峰の地表のすぐ上の、いちばん高い所
    private static int highest(int x0, int z0, int w, int d) {
        int best = Integer.MIN_VALUE;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                double r = Math.hypot(x0 + dx - CX, z0 + dz - CZ);
                best = Math.max(best, (int) Math.round(TOP - r * 0.7) + 1);
            }
        }
        return best;
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

    // ---- プレイヤーが置いた・掘った記録が本当に残るか ----
    private static void checkRecording(ServerLevel level, List<String> failures) {
        ChapterSkyData data = ChapterSkyData.get(level);
        FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1a3b5e-0000-4000-8000-00000000c1a3"), "skyabyss_scar_test"));
        BlockPos placed = new BlockPos(CX + 300, 100, CZ + 300);
        BlockPos dug = new BlockPos(CX + 340, 100, CZ + 300);
        BlockPos walkedOver = new BlockPos(CX + 380, 100, CZ + 300);
        long placedChunk = ChunkPos.asLong(placed);
        long dugChunk = ChunkPos.asLong(dug);
        long grassChunk = ChunkPos.asLong(walkedOver);
        data.clearPlacedForTest(placedChunk);
        data.clearPlacedForTest(dugChunk);
        data.clearPlacedForTest(grassChunk);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, placed),
                Blocks.STONE.defaultBlockState(), fake));
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, dug, Blocks.STONE.defaultBlockState(), fake));
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, walkedOver, Blocks.SHORT_GRASS.defaultBlockState(), fake));
        if (!data.wasPlaced(placedChunk)) {
            failures.add("a block placed by a player was not recorded");
        }
        if (!data.wasPlaced(dugChunk)) {
            failures.add("a solid block dug by a player was not recorded");
        }
        if (data.wasPlaced(grassChunk)) {
            failures.add("breaking short grass should not mark the chunk");
        }
        // 記録は保存して読み直しても残る (ChapterSkyData はオーバーワールドの data に保存される)
        ChapterSkyData back = ChapterSkyData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        if (!back.wasPlaced(placedChunk) || !back.wasPlaced(dugChunk)) {
            failures.add("the placed-chunk record did not survive a save and load");
        }
        data.clearPlacedForTest(placedChunk);
        data.clearPlacedForTest(dugChunk);
    }

    // ---- 本物の山 (ワールド生成) の峰が今までどおり削れるか ----
    // 山岳バイオームを探してそのまわりを生成し、峰の候補をすべて計画してみる (実際には削らない)。
    // 一つも削れなければ失敗。各判定が自然の峰で何か所当たったかも記録に出す (しきい値を決めた根拠)
    private record Site(ResourceKey<Biome> biome, int x, int z) {
    }

    private static final Map<String, Integer> REAL_REASONS = new java.util.TreeMap<>();
    private static final Map<String, Integer> REAL_HITS = new java.util.TreeMap<>();
    private static final List<String> REAL_FAILURES = new ArrayList<>();
    private static int realTried;
    private static int realCarved;
    private static int realGrids;

    private static final List<Site> SITES = List.of(new Site(Biomes.STONY_PEAKS, -9000, 0), new Site(Biomes.JAGGED_PEAKS, 6000, 6000),
            new Site(Biomes.FROZEN_PEAKS, -6000, 6000), new Site(Biomes.JAGGED_PEAKS, -6000, -6000),
            new Site(Biomes.STONY_PEAKS, 6000, -6000), new Site(Biomes.FROZEN_PEAKS, 9000, 0), new Site(Biomes.JAGGED_PEAKS, 0, 9000));
    // 本物の山を調べる時間の目安。新しいワールドでは生成に時間がかかるので、これを過ぎたら残りの場所は飛ばす
    // (一度生成したチャンクは保存されるので、2回目からは全部の場所を数秒で調べられる)
    private static final long REAL_BUDGET_MS = 60_000;
    // 一つの場所のチャンクの生成を待つ上限 (過ぎたら、残りはその tick の中で読み込む)
    private static final long REAL_LOAD_WAIT_MS = 30_000;
    private static int realSite;
    private static int realStage;
    private static int realSkipped;
    private static long realStarted;
    private static long realLoadStarted;
    private static BlockPos realAt;
    private static final List<ChunkPos> REAL_FORCED = new ArrayList<>();

    // 1 tick に 1 段ずつ: 場所を探してチャンクを強制読み込み → 生成されるのを待つ (サーバーは tick を回し続ける) → 峰を計画する。
    // すべての場所が終わったら true
    private static boolean realPeakStep(ServerLevel level) {
        if (realStarted == 0) {
            realStarted = System.currentTimeMillis();
        }
        if (realSite >= SITES.size()) {
            return true;
        }
        Site site = SITES.get(realSite);
        switch (realStage) {
            case 0 -> {
                if (System.currentTimeMillis() - realStarted > REAL_BUDGET_MS) {
                    SkyAbyss.LOGGER.info("SELFTEST chapter sky: real peaks, skipped {} near {} {} (time; it runs once its chunks are saved)",
                            site.biome().location().getPath(), site.x(), site.z());
                    realSkipped++;
                    realSite++;
                    return realSite >= SITES.size();
                }
                Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(site.biome()), new BlockPos(site.x(), 150, site.z()), 2400, 32, 64);
                if (found == null) {
                    SkyAbyss.LOGGER.warn("SELFTEST chapter sky: no {} near {} {}", site.biome().location(), site.x(), site.z());
                    realSite++;
                    return realSite >= SITES.size();
                }
                realAt = found.getFirst();
                REAL_FORCED.clear();
                int radius = 7;
                for (int cx = (realAt.getX() >> 4) - radius; cx <= (realAt.getX() >> 4) + radius; cx++) {
                    for (int cz = (realAt.getZ() >> 4) - radius; cz <= (realAt.getZ() >> 4) + radius; cz++) {
                        level.setChunkForced(cx, cz, true);
                        REAL_FORCED.add(new ChunkPos(cx, cz));
                    }
                }
                realLoadStarted = System.currentTimeMillis();
                realStage = 1;
                return false;
            }
            case 1 -> {
                boolean loaded = REAL_FORCED.stream().allMatch(p -> level.hasChunk(p.x, p.z));
                if (loaded || System.currentTimeMillis() - realLoadStarted > REAL_LOAD_WAIT_MS) {
                    realStage = 2;
                }
                return false;
            }
            default -> {
                try {
                    realPeakSite(level, site, realAt, REAL_FORCED);
                } finally {
                    for (ChunkPos p : REAL_FORCED) {
                        level.setChunkForced(p.x, p.z, false);
                    }
                    REAL_FORCED.clear();
                    realStage = 0;
                    realSite++;
                }
                return realSite >= SITES.size();
            }
        }
    }

    private static void finishRealPeaks(ServerLevel level, List<String> failures) {
        int skipped = realSkipped;
        SkyAbyss.LOGGER.info("SELFTEST chapter sky: real peaks (seed {}) tried {}, carved {}, reasons {}", level.getSeed(), realTried, realCarved,
                REAL_REASONS);
        SkyAbyss.LOGGER.info("SELFTEST chapter sky: shape rules hit on {} natural areas: {}", realGrids, REAL_HITS);
        // 本物の山はワールドの種で変わる。新しいワールドでは生成に時間がかかり、最初の一つ二つの場所しか調べられない。
        // だから「削れる峰が一つもない」は、全部の場所を調べられたときだけ失敗にする (調べ残しがあれば、次の起動で残りを調べる)。
        // 自然の峰を建築とみなすこと自体は安全側 (削らないだけ) で、当たるかどうかは種しだいなので、記録に出すだけにする。
        // 判定が厳しすぎないことは、固定の峰 (checkScars の「きれいな峰」「宙に浮いた草」) で決まった形で確かめている
        int builtHits = REAL_REASONS.entrySet().stream().filter(e -> e.getKey().startsWith("built shape")).mapToInt(Map.Entry::getValue).sum();
        if (realTried == 0 && skipped == 0) {
            // 大陸の峰は手で描いた図の地形で、マインクラフトの峰の探し方では見つからない (形の決まりは固定の峰で確かめてある)
            SkyAbyss.LOGGER.warn("SELFTEST chapter sky: no vanilla mountain peak on the continent (skipped)");
        } else if (realCarved == 0 && skipped == 0) {
            // 種によっては、見つかる峰がどれも自然の平らな面 (凍った峰の氷塊など) や蜂の巣のそばで、一つも削れない (安全側)。
            // 削れることは固定の峰で決まった形で確かめているので、本物の峰では警告だけにする
            SkyAbyss.LOGGER.warn("SELFTEST chapter sky: no real peak could be carved in this world (seed {}): {}", level.getSeed(), REAL_REASONS);
        } else if (realCarved == 0) {
            SkyAbyss.LOGGER.warn("SELFTEST chapter sky: no real peak carved yet in {} tries, {} sites left for the next start ({})",
                    realTried, skipped, REAL_REASONS);
        }
        if (builtHits > 0) {
            SkyAbyss.LOGGER.warn("SELFTEST chapter sky: {} of {} natural peak plans were refused as builds (safe, but watch it): {}",
                    builtHits, realTried, REAL_REASONS);
        }
        failures.addAll(REAL_FAILURES);
        // 本物の砂漠でも同じに見えるか。見つかる場所は砂漠の縁で、ブロックごとのバイオームは境目でとなりのものに揺れる (種しだい)。
        // だから警告だけにする。判定そのものは checkForeignSoil で決まった形で試している
        Pair<BlockPos, Holder<Biome>> desert = level.findClosestBiome3d(h -> h.is(Biomes.DESERT), new BlockPos(0, 64, 3000), 2400, 32, 64);
        if (desert != null && !PeakScar.foreignSoil(level, desert.getFirst())) {
            SkyAbyss.LOGGER.warn("SELFTEST chapter sky: the block biome at the found desert {} is {} (a biome edge), not taken as foreign",
                    desert.getFirst(), level.getBiome(desert.getFirst()).getRegisteredName());
        }
    }

    private static void probe(PeakScar.Grid g) {
        realGrids++;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int x = g.x0; x < g.x0 + g.sx; x++) {
            for (int z = g.z0; z < g.z0 + g.sz; z++) {
                for (int y = g.y0; y < g.y0 + g.sy; y++) {
                    if (g.at(x, y, z) < 2) {
                        continue;
                    }
                    if (PeakScar.stackedOnGrass(g, x, y, z)) {
                        seen.add("stackedOnGrass");
                    }
                    if (PeakScar.hollowUnderGrass(g, x, y, z)) {
                        seen.add("hollowUnderGrass");
                    }
                    for (int h = 3; h <= 5; h++) {
                        if (PeakScar.pillar(g, x, y, z, h)) {
                            seen.add("pillar" + h);
                        }
                    }
                    for (int[] w : new int[][]{{2, 3}, {3, 3}, {3, 4}}) {
                        if (PeakScar.thinWall(g, x, y, z, w[0], w[1])) {
                            seen.add("wall" + w[0] + "x" + w[1]);
                        }
                    }
                    for (int[] b : new int[][]{{3, 2}, {3, 3}, {4, 3}, {4, 4}}) {
                        if (PeakScar.raisedBox(g, x, y, z, b[0], b[1])) {
                            seen.add("box" + b[0] + "h" + b[1]);
                        }
                    }
                    for (int n = 5; n <= 9; n++) {
                        if (PeakScar.flatFloor(g, x, y, z, n)) {
                            seen.add("floor" + n);
                        }
                    }
                }
            }
        }
        seen.forEach(k -> REAL_HITS.merge(k, 1, Integer::sum));
    }

    private static void realPeakSite(ServerLevel level, Site site, BlockPos at, List<ChunkPos> forced) {
        PeakScar.gridProbe = ChapterSkySelfTest::probe;
        try {
            for (ChunkPos p : forced) {
                level.getChunk(p.x, p.z);
            }
            List<PeakScar.Candidate> candidates = PeakScar.findCandidates(level, at.getX() - 24, at.getZ(), 1f, 0f, 48, 24);
            for (PeakScar.Candidate c : candidates.subList(0, Math.min(6, candidates.size()))) {
                for (float[] dir : new float[][]{{1f, 0f}, {0f, 1f}, {-0.7f, 0.7f}}) {
                    PeakScar.Result r = PeakScar.plan(level, c, dir[0], dir[1], 0.5f, RandomSource.create(c.x() * 31L + c.z()), PeakScar.PLAYER_CLEAR);
                    realTried++;
                    String key = r.plan() != null ? "ok" : r.reason().replaceAll(" at -?\\d+ -?\\d+ -?\\d+$", "");
                    REAL_REASONS.merge(key, 1, Integer::sum);
                    if (r.plan() != null) {
                        realCarved++;
                    }
                    SkyAbyss.LOGGER.info("SELFTEST chapter sky: real peak {} at {} {} {} dir {} {} -> {}", site.biome().location().getPath(),
                            c.x(), c.y(), c.z(), dir[0], dir[1], r.plan() != null ? "carves " + r.plan().removed() : r.reason());
                }
            }
            // 山の地表の土は、そのバイオームのもの
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below();
            // (見つかる場所は峰のバイオームの縁なので、となりの砂漠・荒野・浜にかかることがある。種しだいなので警告だけ)
            if (PeakScar.foreignSoil(level, ground)) {
                SkyAbyss.LOGGER.warn("SELFTEST chapter sky: the ground {} near the {} is in {}, taken as foreign", ground,
                        site.biome().location().getPath(), level.getBiome(ground).getRegisteredName());
            }
        } finally {
            PeakScar.gridProbe = null;
        }
    }
}
