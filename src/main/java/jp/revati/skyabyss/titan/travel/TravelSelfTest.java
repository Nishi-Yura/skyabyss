package jp.revati.skyabyss.titan.travel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 巨神での移動の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST travel PASSED / FAILED。
// 1. 碑の登録 (重ね・上限・忘れる) と保存 (プレイヤーの記録のコーデック、世界の碑の一覧)
// 2. 解放の条件 (心臓の撃破の記録、コマンドの記録)
// 3. 時間の見積もり (距離に対して増える・上限に収まる)
// 4. 降ろす場所 (溶岩・サボテンを避け、空の開けた所)
// 5. 一体だけ・排他 (同時に呼ぶ人: 近くなら一緒に乗る、遠くなら断る。岩鯨は 1 体のまま)
// 6. 旅の流れ (迎え → 出発 → 雲の上 → 降下 → 鎖の垂れ口が降ろす場所の真上) をサーバーの外で早回しする
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TravelSelfTest {
    // 岩鯨の見守りの自己テスト (40 tick 目) の後、岩鯨の背の自己テストがサーバーを止める前に行う
    private static int countdown = -1;
    private static final UUID TEST_A = UUID.fromString("7a7e1000-0000-4000-8000-00000000a7a1");
    private static final UUID TEST_B = UUID.fromString("7a7e1000-0000-4000-8000-00000000a7a2");
    private static final UUID TEST_C = UUID.fromString("7a7e1000-0000-4000-8000-00000000a7a3");
    // 旅の早回しの場所 (世界の初期スポーンからの東西の帯。生成済みのことが多い)
    private static final int STRIP_FROM = -6;
    private static final int STRIP_TO = 18;

    private TravelSelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        for (int cx = STRIP_FROM; cx <= STRIP_TO; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                level.setChunkForced((spawn.getX() >> 4) + cx, (spawn.getZ() >> 4) + cz, true);
            }
        }
        countdown = 70;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (countdown < 0 || --countdown > 0) {
            return;
        }
        countdown = -1;
        ServerLevel level = event.getServer().overworld();
        List<String> failures = new ArrayList<>();
        try {
            run(level, failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST travel crashed", e);
            failures.add("crashed: " + e);
        }
        BlockPos spawn = level.getSharedSpawnPos();
        for (int cx = STRIP_FROM; cx <= STRIP_TO; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                level.setChunkForced((spawn.getX() >> 4) + cx, (spawn.getZ() >> 4) + cz, false);
            }
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST travel PASSED (waymarks, save, unlock, estimate, landing, one whale, join/busy, voyage flight)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST travel FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(ServerLevel level, List<String> failures) {
        // ---- 1. 碑の登録と保存
        TravelState s = new TravelState();
        check(s.addWaymark(new BlockPos(1, 64, 1)) == TravelState.AddResult.ADDED, "first waymark not added", failures);
        check(s.addWaymark(new BlockPos(1, 64, 1)) == TravelState.AddResult.ALREADY, "duplicate waymark not detected", failures);
        for (int i = 0; i < 20; i++) {
            s.addWaymark(new BlockPos(100 + i, 70, -i));
        }
        check(s.waymarks.size() == TravelState.MAX_WAYMARKS, "waymark cap not kept: " + s.waymarks.size(), failures);
        check(s.addWaymark(new BlockPos(999, 1, 999)) == TravelState.AddResult.FULL, "full list accepted a waymark", failures);
        check(s.forgetWaymark(new BlockPos(1, 64, 1)) && s.waymarks.size() == TravelState.MAX_WAYMARKS - 1, "forget failed", failures);
        s.unlocked.add("stone_whale");
        s.callGiven.add("stone_whale");
        s.readyAt = 123456L;
        s.voyages = 3;
        DataResult<Tag> encoded = TravelState.CODEC.encodeStart(NbtOps.INSTANCE, s);
        TravelState back = encoded.flatMap(t -> TravelState.CODEC.parse(NbtOps.INSTANCE, t)).result().orElse(null);
        check(back != null && back.waymarks.equals(s.waymarks) && back.unlocked.equals(s.unlocked) && back.callGiven.equals(s.callGiven)
                && back.readyAt == s.readyAt && back.voyages == 3, "travel state did not survive the codec", failures);
        TravelState empty = TravelState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).result().orElse(null);
        check(empty != null && empty.waymarks.isEmpty() && empty.readyAt == 0, "an empty record did not load", failures);

        TravelWorldData world = new TravelWorldData();
        world.put(new BlockPos(5, 70, -9), new TravelWorldData.Waymark("家", "minecraft:plains"));
        world.put(new BlockPos(-300, 80, 40), new TravelWorldData.Waymark("", "minecraft:taiga"));
        TravelWorldData loaded = TravelWorldData.load(world.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.size() == 2 && "家".equals(loaded.waymark(new BlockPos(5, 70, -9)).name())
                && "minecraft:taiga".equals(loaded.waymark(new BlockPos(-300, 80, 40)).biome()), "waymark world data did not survive save", failures);
        check(loaded.remove(new BlockPos(5, 70, -9)) && loaded.waymark(new BlockPos(5, 70, -9)) == null, "waymark removal failed", failures);

        // ---- 2. 解放の条件
        FakePlayer a = FakePlayerFactory.get(level, new GameProfile(TEST_A, "[travel_test_a]"));
        FakePlayer b = FakePlayerFactory.get(level, new GameProfile(TEST_B, "[travel_test_b]"));
        FakePlayer c = FakePlayerFactory.get(level, new GameProfile(TEST_C, "[travel_test_c]"));
        Voyages.state(a).unlocked.clear();
        TitanMemoryData memory = TitanMemoryData.get(level.getServer());
        TitanMemoryData.Record ra = memory.record(TEST_A, TitanKind.STONE_WHALE.id());
        int oldClears = ra.clears;
        ra.clears = 0;
        check(!Voyages.isUnlocked(a, TitanKind.STONE_WHALE), "unlocked without beating the heart", failures);
        ra.clears = 1;
        check(Voyages.isUnlocked(a, TitanKind.STONE_WHALE), "beating the heart did not unlock travel", failures);
        ra.clears = oldClears;
        Voyages.state(b).unlocked.add(TitanKind.STONE_WHALE.id());
        check(Voyages.isUnlocked(b, TitanKind.STONE_WHALE), "the command unlock was ignored", failures);
        check(TravelRules.check(c, TitanKind.STONE_WHALE, false) == TravelRules.Refusal.NOT_RECOGNIZED, "a stranger was allowed to call", failures);

        // ---- 3. 時間の見積もり
        int prev = 0;
        for (double d : new double[]{64, 300, 600, 601, 2000, 8000, 30000}) {
            int sec = TravelRules.estimateSeconds(d);
            check(sec >= prev - 1, "estimate went down at " + d + ": " + sec, failures);
            check(sec >= 20 && sec <= 90, "estimate out of range at " + d + ": " + sec, failures);
            prev = sec;
        }
        check(TravelRules.cruiseTicks(1e9) == TravelRules.CRUISE_MAX && TravelRules.cruiseTicks(0) == TravelRules.CRUISE_MIN, "cruise ticks not clamped", failures);

        // ---- 4. 降ろす場所 (空高くに小さな台を作って試す)
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos pad = new BlockPos(spawn.getX() + 40, level.getMaxBuildHeight() - 12, spawn.getZ());
        List<BlockPos> placed = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = pad.offset(dx, 0, dz);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                placed.add(p);
            }
        }
        level.setBlock(pad, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        for (int dx = -1; dx <= 1; dx++) {
            BlockPos cactusBase = pad.offset(dx, 0, -1);
            level.setBlock(cactusBase, Blocks.SAND.defaultBlockState(), 3);
            level.setBlock(cactusBase.above(), Blocks.CACTUS.defaultBlockState(), 2);
            placed.add(cactusBase.above());
        }
        BlockPos land = LandingFinder.find(level, pad, 0, 2);
        BlockPos under = land.below();
        check(land.getY() == pad.getY() + 1 && !under.equals(pad) && level.getBlockState(under).is(Blocks.STONE),
                "landing picked an unsafe spot " + land + " on " + level.getBlockState(under), failures);
        for (int i = placed.size() - 1; i >= 0; i--) {
            level.setBlock(placed.get(i), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(pad, Blocks.AIR.defaultBlockState(), 2);

        // ---- 5 と 6. 一体だけ・排他、旅の早回し
        TitanWorldData whaleData = TitanWorldData.get(level);
        CompoundTag saved = whaleData.snapshot(level.registryAccess());
        StoneWhaleEntity whale = null;
        try {
            BlockPos pickup = new BlockPos(spawn.getX() + 8, 0, spawn.getZ());
            pickup = LandingFinder.find(level, pickup.atY(level.getSeaLevel()), 0, 3);
            whale = WhaleKeeper.spawn(level, pickup.getX() + 120, pickup.getZ(), null);
            whale.setPos(whale.getX(), 212, whale.getZ());
            whale.beginVoyage();
            a.moveTo(pickup.getX() + 0.5, pickup.getY(), pickup.getZ() + 0.5);
            b.moveTo(pickup.getX() + 4.5, pickup.getY(), pickup.getZ() + 0.5);
            c.moveTo(pickup.getX() + 400.5, pickup.getY(), pickup.getZ() + 0.5);
            Voyages.state(c).unlocked.add(TitanKind.STONE_WHALE.id());
            Voyage v = new Voyage(TitanKind.STONE_WHALE, whale, a, pickup);
            v.test = true;
            Voyages.put(v);
            check(Voyages.call(a, TitanKind.STONE_WHALE, true) == Voyages.CallResult.REFUSED, "the caller could call twice", failures);
            check(Voyages.call(b, TitanKind.STONE_WHALE, true) == Voyages.CallResult.JOINED, "a nearby player was not told to ride along", failures);
            check(Voyages.call(c, TitanKind.STONE_WHALE, true) == Voyages.CallResult.REFUSED, "a far player could call the busy whale", failures);
            check(countWhales(level) == 1, countWhales(level) + " whales after calls", failures);
            Voyages.forceEnd(TitanKind.STONE_WHALE);
            check(!whale.inVoyage() && Voyages.active(TitanKind.STONE_WHALE) == null, "ending the voyage did not free the whale", failures);

            // 早回し: 迎え → 行き先 (東へ 180) へ。旅の操縦だけを毎 tick 呼ぶ
            whale.beginVoyage();
            whale.setPos(pickup.getX() + 120, 212, pickup.getZ() + 30);
            Voyage f = new Voyage(TitanKind.STONE_WHALE, whale, a, pickup);
            f.test = true;
            BlockPos destPos = new BlockPos(pickup.getX() + 180, level.getSeaLevel(), pickup.getZ() + 6);
            EnumSet<Voyage.Phase> seen = EnumSet.noneOf(Voyage.Phase.class);
            Vec3 chainAtLowering = null;
            int ticks = 0;
            for (; ticks < 6000 && !f.finished; ticks++) {
                seen.add(f.phase);
                if (f.phase == Voyage.Phase.WAITING && f.dest == null) {
                    f.dest = new TravelNetwork.Dest("test", "test", "", destPos.getX(), destPos.getY(), destPos.getZ(), 2, true, 0);
                    f.phase = Voyage.Phase.BOARDING;
                    f.phaseTicks = 0;
                    // 鎖はたたんだことにする (岩鯨自身の tick は回していない)
                    whale.setVoyageChain(Double.NaN);
                }
                seen.add(f.phase);
                f.tick();
                if (f.phase == Voyage.Phase.LOWERING && chainAtLowering == null) {
                    chainAtLowering = whale.position().add(whale.boardingChainOffset(whale.getYRot()));
                }
                if (!whale.inVoyage() && !f.finished) {
                    failures.add("the whale left the voyage by itself in " + f.phase);
                    break;
                }
            }
            check(f.finished, "the voyage did not finish (stuck in " + f.phase + " after " + ticks + " ticks)", failures);
            check(seen.containsAll(EnumSet.of(Voyage.Phase.COMING, Voyage.Phase.WAITING, Voyage.Phase.BOARDING, Voyage.Phase.ASCENT,
                    Voyage.Phase.CRUISE, Voyage.Phase.APPROACH, Voyage.Phase.LOWERING)), "phases skipped: " + seen, failures);
            check(ticks < 20 * 120, "the short voyage took too long: " + ticks + " ticks", failures);
            if (chainAtLowering != null && f.landing != null) {
                double off = Math.hypot(chainAtLowering.x - (f.landing.getX() + 0.5), chainAtLowering.z - (f.landing.getZ() + 0.5));
                check(off < 2.0, "the chain hung " + String.format("%.1f", off) + " blocks away from the landing spot", failures);
                check(chainAtLowering.y > f.landing.getY() + 10, "the chain top was below the landing spot", failures);
            } else {
                failures.add("no landing spot was chosen");
            }
            check(countWhales(level) == 1, countWhales(level) + " whales after the voyage", failures);
            check(!whale.inVoyage(), "the whale stayed in voyage mode after arriving", failures);
            SkyAbyss.LOGGER.info("SELFTEST travel: short voyage took {} ticks ({} s), phases {}", ticks, ticks / 20, seen);
        } finally {
            Voyages.forceEnd(TitanKind.STONE_WHALE);
            if (whale != null) {
                whale.discard();
            }
            whaleData.restore(saved, level.registryAccess());
        }
    }

    private static int countWhales(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof StoneWhaleEntity && !entity.isRemoved()) {
                count++;
            }
        }
        return count;
    }
}
