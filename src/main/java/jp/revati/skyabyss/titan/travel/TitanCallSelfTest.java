package jp.revati.skyabyss.titan.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanStay;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.ember.EmberGround;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import jp.revati.skyabyss.titan.end.TitanDormancy;
import jp.revati.skyabyss.titan.storm.GaleColumnEntity;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormPerch;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 巨神の呼びの自己テスト (SKYABYSS_SELFTEST。SKYABYSS_SELFTEST_ONLY="titan call")。結果はログに SELFTEST titan call PASSED / FAILED。
// 四体 (岩鯨・潮殻・雷翼・余燼) を、昼・夜・潮・嵐の刻を変えて呼び (呼び笛の中身 = TitanCalls.call)、
// 1. 呼べる (CALLED) 2. 決まった tick の中で乗れる姿 (TitanCalls.boardable) になる 3. 呼んだ人の近くに来る
// 4. 滞在が 20 分 (TitanStay) で、乗れる窓 (鎖・潮門・昇り風・口の開閉) が開いたままになる、を確かめる。
// 眠っている巨神は応えない・出会っていない人は呼べない・何もしなければ巨神は降りてこない (最初の出会いの時刻は変えない)・
// 出会いの検出 (乗れる姿の近く・背の中) と笛の一度きりの受け渡し、も確かめる
public final class TitanCallSelfTest {
    private static final UUID TEST_A = UUID.fromString("7a7e2000-0000-4000-8000-00000000c411");
    private static final UUID TEST_B = UUID.fromString("7a7e2000-0000-4000-8000-00000000c412");
    private static final int LOAD_RADIUS = 8;
    private static final int LOAD_WAIT_LIMIT = 1200;

    // 呼んでから乗れる姿になるまでの上限 (tick)
    private static final int MAX_WHALE = 4000;
    private static final int MAX_TIDE = 6000;
    private static final int MAX_WING = 3000;
    private static final int MAX_EMBER = 1500;  // 浮かぶのに 600 tick
    // 乗れる姿になってから、見守る tick
    private static final int HOLD_TICKS = 1500;

    private static BlockPos center;
    private static int loadTicks;

    private TitanCallSelfTest() {
    }

    // 読み込み (毎 tick、読み込み終わるまで)。海のそば (なければ初期スポーン) の周りを、札だけで読み込む
    public static boolean load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (loadTicks++ == 0) {
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos sea = SeaVoyage.seaNear(level, spawn);
            center = sea != null ? sea : spawn;
            force(level, true);
            SkyAbyss.LOGGER.info("[selftest] titan call: test area around {}", center);
            return false;
        }
        int missing = 0;
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                if (level.getChunkSource().getChunkNow((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz) == null) {
                    missing++;
                }
            }
        }
        if (missing == 0 || loadTicks > LOAD_WAIT_LIMIT) {
            SkyAbyss.LOGGER.info("[selftest] titan call: chunks ready after {} ticks ({} missing)", loadTicks, missing);
            return true;
        }
        return false;
    }

    private static void force(ServerLevel level, boolean on) {
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                level.getChunkSource().updateChunkForced(new ChunkPos((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz), on);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        long dayTime = level.getDayTime();
        try {
            run(level, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan call crashed", e);
            failures.add("crashed: " + e);
        } finally {
            for (String id : Titans.all()) {
                TitanDormancy.clear(server, id);
                discardAll(level, id);
            }
            WhaleKeeper.keeper().releaseTicket(level);
            TideKeeper.KEEPER.releaseTicket(level);
            StormKeeper.KEEPER.releaseTicket(level);
            EmberKeeper.KEEPER.releaseTicket(level);
            data.restore(saved, level.registryAccess());
            level.setDayTime(dayTime);
            force(level, false);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("[selftest] titan call: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan call PASSED (four titans called at day/night/any tide/no storm, stay 20 min with the boarding window held open, "
                    + "sleeping titan refuses, unmet player refused, no call = no descent, meeting detection, whistle given once)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST titan call FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void discardAll(ServerLevel level, String id) {
        Entity e = switch (id) {
            case Titans.STONE_WHALE -> WhaleKeeper.current(level);
            case Titans.TIDE_SHELL -> TideKeeper.KEEPER.current(level);
            case Titans.THUNDER_WING -> StormKeeper.KEEPER.current(level);
            default -> EmberKeeper.KEEPER.current(level);
        };
        if (e != null) {
            e.discard();
        }
        for (GaleColumnEntity col : level.getEntitiesOfClass(GaleColumnEntity.class, new AABB(center).inflate(2000))) {
            col.discard();
        }
    }

    // ---------------------------------------------------------------- 場所

    // 読み込まれた範囲を中心から外へ、立てる陸の点で条件を満たす最初のもの
    private static Vec3 findSpot(ServerLevel level, Predicate<Vec3> ok) {
        for (int s = 0; s <= LOAD_RADIUS * 16 - 12; s += 4) {
            for (int a = 0; a < (s == 0 ? 1 : 24); a++) {
                double angle = Math.PI * 2 * a / 24;
                double x = center.getX() + Math.cos(angle) * s;
                double z = center.getZ() + Math.sin(angle) * s;
                if (!TideOcean.land(level, x, z)) {
                    continue;
                }
                Vec3 stand = TideOcean.standableShore(level, x, z);
                if (stand != null && ok.test(stand)) {
                    return stand;
                }
            }
        }
        return null;
    }

    private static FakePlayer player(ServerLevel level, UUID id, String name, Vec3 at) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(id, name));
        p.getInventory().clearContent();
        p.moveTo(at.x, at.y, at.z);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    // ---------------------------------------------------------------- 本体

    private static void run(ServerLevel level, List<String> failures, List<String> notes) {
        MinecraftServer server = level.getServer();
        // 登録: 四体の笛、守られる品、旅のない巨神の笛は旅の型を持たない
        check(TitanCalls.callItem(Titans.THUNDER_WING) == TravelSetup.THUNDER_WING_CALL.get()
                && TitanCalls.callItem(Titans.EMBER) == TravelSetup.EMBER_CALL.get(), "the call items are wrong", failures);
        check(TravelSetup.THUNDER_WING_CALL.get() instanceof TitanCallItem w && w.kind() == null && w.titanId().equals(Titans.THUNDER_WING)
                && TravelSetup.EMBER_CALL.get() instanceof TitanCallItem e && e.kind() == null && e.titanId().equals(Titans.EMBER),
                "the thunder wing / ember calls must be titan call items without a travel kind", failures);
        check(TravelSetup.STONE_WHALE_CALL.get() instanceof TitanCallItem sw && sw.kind() == TitanKind.STONE_WHALE
                && TravelSetup.TIDE_SHELL_CALL.get() instanceof TitanCallItem ts && ts.kind() == TitanKind.TIDE_SHELL, "the travel calls lost their kind", failures);
        check(jp.revati.skyabyss.record.Keepsakes.guardedItems().contains(TravelSetup.THUNDER_WING_CALL.get())
                && jp.revati.skyabyss.record.Keepsakes.guardedItems().contains(TravelSetup.EMBER_CALL.get()), "the new calls are not guarded", failures);
        check(jp.revati.skyabyss.pouch.PouchEntries.any(TravelSetup.THUNDER_WING_CALL.get()) != null
                && jp.revati.skyabyss.pouch.PouchEntries.any(TravelSetup.EMBER_CALL.get()) != null, "the new calls are not pouch tools", failures);

        Vec3 landSpot = findSpot(level, v -> StormPerch.find(level, v.x, v.z, 24, 96) != null);
        Vec3 tideSpot = findSpot(level, v -> TideOcean.findCallSpot(level, v.x, v.z) != null);
        Vec3 emberSpot = findSpot(level, v -> EmberGround.findSleepSpot(level, v.x, v.z) != null);
        notes.add("spots: land=" + landSpot + " tide=" + tideSpot + " ember=" + emberSpot);

        long[] times = {6000, 18000, 1000};
        long lowTide = -1;
        long highTide = -1;
        for (long t = 0; t < 24000 && (lowTide < 0 || highTide < 0); t += 250) {
            if (TideOcean.isLowTide(t)) {
                lowTide = lowTide < 0 ? t : lowTide;
            } else {
                highTide = highTide < 0 ? t : highTide;
            }
        }

        TitanDormancy.clear(server, Titans.STONE_WHALE);
        for (String id : Titans.all()) {
            TitanDormancy.clear(server, id);
        }
        if (landSpot != null) {
            for (long t : times) {
                callCase(level, Titans.STONE_WHALE, landSpot, t, MAX_WHALE, failures, notes);
                callCase(level, Titans.THUNDER_WING, landSpot, t, MAX_WING, failures, notes);
            }
        }
        if (tideSpot != null) {
            // 満ち潮 (高潮) と引き潮 (干潮)、昼と夜
            callCase(level, Titans.TIDE_SHELL, tideSpot, highTide >= 0 ? highTide : 6000, MAX_TIDE, failures, notes);
            callCase(level, Titans.TIDE_SHELL, tideSpot, lowTide >= 0 ? lowTide : 18000, MAX_TIDE, failures, notes);
        } else {
            notes.add("no shore with rest water near the test area: the tide shell call was skipped");
        }
        // 余燼は眠る場所の陸の検査を、自己テストの置き場所 (TitanCalls.emberSpotOverride) に替えて通す (長い平らな陸が地形にないため)
        if (landSpot != null) {
            for (long t : times) {
                callCase(level, Titans.EMBER, landSpot, t, MAX_EMBER, failures, notes);
            }
        }

        if (landSpot != null) {
            // 巨神が読み込まれていない (いまはいない) ときの呼び: 現れ直して応える
            for (String id : List.of(Titans.STONE_WHALE, Titans.THUNDER_WING)) {
                discardAll(level, id);
                TitanWorldData.Slot slot = TitanWorldData.get(level).slot(id);
                slot.spawned = true;
                slot.id = null;
                FakePlayer p = player(level, TEST_A, "[titan_call_a]", landSpot);
                Voyages.state(p).met.add(id);
                check(TitanCalls.call(p, id, false) == TitanCalls.Result.CALLED && slot.id != null, id + ": a call with no titan loaded did not bring one", failures);
            }
            refusals(level, landSpot, failures);
            meetings(level, landSpot, tideSpot, failures);
        }
    }

    // 一体を一つの時刻で呼ぶ
    private static void callCase(ServerLevel level, String id, Vec3 spot, long dayTime, int limit, List<String> failures, List<String> notes) {
        String label = id + "@" + dayTime;
        MinecraftServer server = level.getServer();
        level.setDayTime(dayTime);
        // 一体だけ、遠くから: 前の個体を片づけ、見守りの記録を「現れたことがある・いまはいない」にする
        discardAll(level, id);
        TitanWorldData.Slot slot = TitanWorldData.get(level).slot(id);
        slot.spawned = true;
        slot.id = null;
        slot.hasLast = false;
        FakePlayer p = player(level, TEST_A, "[titan_call_a]", spot);
        Voyages.state(p).met.add(id);
        Voyages.state(p).unlocked.remove(id);
        // 巨神は、読み込まれた範囲の少し離れた所にいる (潮殻だけは、いない所から呼ぶ道 = 現れ直しも通す)
        switch (id) {
            case Titans.STONE_WHALE -> WhaleKeeper.spawn(level, spot.x + 90, spot.z + 20, null).setPos(spot.x + 90, spot.y + 120, spot.z + 20);
            case Titans.THUNDER_WING -> StormKeeper.KEEPER.spawn(level, spot.x + 70, spot.z + 40, null).setPos(spot.x + 70, spot.y + 90, spot.z + 40);
            case Titans.EMBER -> {
                EmberKeeper.KEEPER.spawn(level, spot.x + 40, spot.z, null);
                double ground = EmberGround.surfaceY(level, spot.x, spot.z);
                TitanCalls.emberSpotOverride = who -> {
                    // 鼻先は人の 10 ブロック南、口は北 (人の方) へ。体 (中心) は読み込んだ範囲の中に収まる
                    float yaw = EmberGround.yawOf(0, -1);
                    double noseZ = who.getZ() + 10;
                    return new EmberGround.Spot(who.getX() - EmberGround.forwardX(yaw) * EmberTitanEntity.SNOUT_FORWARD,
                            noseZ - EmberGround.forwardZ(yaw) * EmberTitanEntity.SNOUT_FORWARD, yaw, Double.isNaN(ground) ? who.getY() : ground);
                };
            }
            default -> {
            }
        }
        // 何もしなければ降りてこない (最初の出会いの時刻・場面は呼びでは動かない): 巨神を遠くに置いて数 tick
        TitanCalls.Result r = TitanCalls.call(p, id, false);
        check(r == TitanCalls.Result.CALLED, label + ": the call was not answered (" + r + ")", failures);
        if (r != TitanCalls.Result.CALLED) {
            return;
        }
        Entity titan = current(level, id);
        check(titan != null && !titan.isRemoved(), label + ": no titan after the call", failures);
        if (titan == null) {
            return;
        }
        int arrivedAt = -1;
        for (int tick = 0; tick < limit; tick++) {
            tickTitan(titan);
            if (TitanCalls.boardable(level, id) != null) {
                arrivedAt = tick;
                break;
            }
        }
        check(arrivedAt >= 0, label + ": did not become boardable within " + limit + " ticks", failures);
        if (arrivedAt < 0) {
            return;
        }
        Vec3 at = titan.position();
        double dist = Math.hypot(at.x - p.getX(), at.z - p.getZ());
        double reach = id.equals(Titans.TIDE_SHELL) ? 160 : TitanCalls.HERE_RANGE;
        check(dist <= reach, label + ": arrived " + (int) dist + " blocks from the caller (limit " + (int) reach + ")", failures);
        // 20 分の滞在と、乗れる窓が開いたまま
        int stay = stayLeft(titan);
        check(stay > TitanStay.TICKS - 2400, label + ": the stay was " + stay + " ticks, not about " + TitanStay.TICKS, failures);
        boolean windowSeen = false;
        for (int tick = 0; tick < HOLD_TICKS; tick++) {
            tickTitan(titan);
            if (TitanCalls.boardable(level, id) == null) {
                failures.add(label + ": stopped being boardable " + tick + " ticks after arriving");
                break;
            }
            if (tick % 20 == 0 && windowOpen(level, titan)) {
                windowSeen = true;
            }
        }
        check(windowSeen, label + ": the boarding window (chain / gate / updraft / open mouth) was never open during the stay", failures);
        check(stayLeft(titan) > TitanStay.TICKS - 2400 - HOLD_TICKS - 100, label + ": the stay ran down too fast: " + stayLeft(titan), failures);
        notes.add(label + ": CALLED, boardable after " + arrivedAt + " ticks (" + arrivedAt / 20 + " s), " + (int) dist + " blocks from the caller, stay " + stayLeft(titan));
        // 眠った巨神は応えない (記録だけ眠らせる)。巨神は動かない
        Vec3 before = titan.position();
        TitanDormancy.begin(server, id, 100000);
        FakePlayer q = player(level, TEST_B, "[titan_call_b]", spot);
        Voyages.state(q).met.add(id);
        check(TitanCalls.call(q, id, false) == TitanCalls.Result.REFUSED && titan.position().equals(before), label + ": a sleeping titan answered the call", failures);
        TitanDormancy.clear(server, id);
        TitanCalls.emberSpotOverride = null;
        discardAll(level, id);
    }

    private static Entity current(ServerLevel level, String id) {
        return switch (id) {
            case Titans.STONE_WHALE -> WhaleKeeper.current(level);
            case Titans.TIDE_SHELL -> TideKeeper.KEEPER.current(level);
            case Titans.THUNDER_WING -> StormKeeper.KEEPER.current(level);
            default -> EmberKeeper.KEEPER.current(level);
        };
    }

    private static void tickTitan(Entity titan) {
        titan.tick();
    }

    private static int stayLeft(Entity titan) {
        if (titan instanceof StoneWhaleEntity w) {
            return w.stayLeft();
        }
        if (titan instanceof TideShellEntity s) {
            return s.stayLeft();
        }
        if (titan instanceof StormWingEntity w) {
            return w.stayLeft();
        }
        return titan instanceof EmberTitanEntity e ? e.forcedTicks() : 0;
    }

    // 乗れる窓が開いているか: 岩鯨は鎖が地面まで下りている、潮殻は休んでいる (潮門が開く)、雷翼は昇り風が立っている、余燼は口が開いた (息の吸い)
    private static boolean windowOpen(ServerLevel level, Entity titan) {
        if (titan instanceof StoneWhaleEntity w) {
            return w.isChainDown();
        }
        if (titan instanceof TideShellEntity s) {
            return s.isResting();
        }
        if (titan instanceof StormWingEntity w) {
            return !level.getEntitiesOfClass(GaleColumnEntity.class, w.getBoundingBox().inflate(80, 120, 80)).isEmpty();
        }
        return titan instanceof EmberTitanEntity e && e.mouthOpen();
    }

    // ---------------------------------------------------------------- 断り・出会い

    private static void refusals(ServerLevel level, Vec3 spot, List<String> failures) {
        level.setDayTime(6000);
        for (String id : Titans.all()) {
            TitanWorldData.Slot slot = TitanWorldData.get(level).slot(id);
            slot.spawned = true;
            FakePlayer stranger = player(level, TEST_B, "[titan_call_b]", spot);
            Voyages.state(stranger).met.remove(id);
            Voyages.state(stranger).unlocked.remove(id);
            check(!TitanCalls.met(stranger, id), id + ": a stranger counts as met", failures);
            check(TitanCalls.call(stranger, id, false) == TitanCalls.Result.REFUSED, id + ": a player who never met it could call it", failures);
        }
        // 眠りの文: 目覚めの時刻が決まっていれば分を添える (どちらも断る)
        FakePlayer a = player(level, TEST_A, "[titan_call_a]", spot);
        Voyages.state(a).met.add(Titans.THUNDER_WING);
        TitanDormancy.Rest rest = TitanDormancy.begin(level.getServer(), Titans.THUNDER_WING, 0);
        rest.wakeAt = level.getGameTime() + 6000;
        check(TitanCalls.call(a, Titans.THUNDER_WING, false) == TitanCalls.Result.REFUSED, "a sleeping thunder wing answered", failures);
        rest.wakeAt = Long.MAX_VALUE;
        check(TitanCalls.call(a, Titans.THUNDER_WING, false) == TitanCalls.Result.REFUSED, "a sleeping thunder wing (no wake time) answered", failures);
        TitanDormancy.clear(level.getServer(), Titans.THUNDER_WING);
        // 別の巨神が近く (NEAR_TITAN_RANGE 以内) にいるあいだは呼べない。離れれば呼べる
        discardAll(level, Titans.STONE_WHALE);
        discardAll(level, Titans.EMBER);
        TitanWorldData.Slot wslot = TitanWorldData.get(level).slot(Titans.STONE_WHALE);
        wslot.spawned = true;
        wslot.id = null;
        Voyages.state(a).met.add(Titans.STONE_WHALE);
        Voyages.state(a).met.add(Titans.EMBER);
        StoneWhaleEntity whale = (StoneWhaleEntity) jp.revati.skyabyss.titan.travel.TitanKind.STONE_WHALE.respawn().spawn(level, spot.x + 60, spot.y + 40, spot.z);
        TitanWorldData.Slot eslot = TitanWorldData.get(level).slot(Titans.EMBER);
        eslot.spawned = true;
        check(TitanCalls.nearTitan(level, a, Titans.EMBER) != null, "a whale 60 blocks away was not counted as near", failures);
        check(TitanCalls.call(a, Titans.EMBER, false) == TitanCalls.Result.REFUSED, "an ember call near the whale was not refused", failures);
        whale.setPos(spot.x + TitanCalls.NEAR_TITAN_RANGE + 40, spot.y + 40, spot.z);
        check(TitanCalls.nearTitan(level, a, Titans.EMBER) == null, "a whale beyond the near range was counted as near", failures);
        whale.discard();
    }

    private static void meetings(ServerLevel level, Vec3 spot, Vec3 tideSpot, List<String> failures) {
        level.setDayTime(6000);
        // 降りて止まった雷翼の近くにいると出会い、笛が一度だけ渡る。巡航中 (止まっていない) の近くでは出会わない
        discardAll(level, Titans.THUNDER_WING);
        TitanWorldData.Slot slot = TitanWorldData.get(level).slot(Titans.THUNDER_WING);
        slot.spawned = true;
        slot.id = null;
        StormWingEntity wing = StormKeeper.KEEPER.spawn(level, spot.x + 30, spot.z + 30, null);
        FakePlayer p = player(level, TEST_A, "[titan_call_a]", spot);
        TravelState s = Voyages.state(p);
        s.met.clear();
        s.callGiven.clear();
        s.unlocked.clear();
        jp.revati.skyabyss.loot.TitanMemoryData.get(level.getServer()).record(TEST_A, Titans.THUNDER_WING).clears = 0;
        wing.setPos(spot.x + 30, spot.y + 90, spot.z + 30);
        TitanCalls.meetAndGive(p);
        check(!wing.isPerched() && !s.met.contains(Titans.THUNDER_WING) && !s.callGiven.contains(Titans.THUNDER_WING),
                "met the thunder wing while it was cruising", failures);
        wing.restHere(TitanStay.TICKS);
        check(wing.isPerched(), "restHere did not perch", failures);
        int given = TitanCalls.meetAndGive(p);
        check(given >= 1 && s.met.contains(Titans.THUNDER_WING) && s.callGiven.contains(Titans.THUNDER_WING)
                && jp.revati.skyabyss.pouch.Pouch.countAll(p, TravelSetup.THUNDER_WING_CALL.get())
                        + p.getInventory().countItem(TravelSetup.THUNDER_WING_CALL.get()) == 1, "the first meeting did not give the call exactly once (" + given + ")", failures);
        TitanCalls.meetAndGive(p);
        check(jp.revati.skyabyss.pouch.Pouch.countAll(p, TravelSetup.THUNDER_WING_CALL.get()) + p.getInventory().countItem(TravelSetup.THUNDER_WING_CALL.get()) == 1,
                "the call was given twice", failures);
        // 眠っている巨神は、出会いに数えない
        s.met.clear();
        s.callGiven.clear();
        TitanDormancy.begin(level.getServer(), Titans.THUNDER_WING, 0);
        TitanCalls.meetAndGive(p);
        check(!s.met.contains(Titans.THUNDER_WING), "met a sleeping titan", failures);
        TitanDormancy.clear(level.getServer(), Titans.THUNDER_WING);
        wing.discard();
    }
}
