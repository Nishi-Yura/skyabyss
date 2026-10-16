package jp.revati.skyabyss.titan.travel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 潮殻の旅の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST tide travel PASSED / FAILED。
// 1. 登録: 巨神の一覧に潮殻 (海の型・呼び貝・言い回し)、呼び貝は大事な品として守られる
// 2. 撃破の記録のフック: 潮殻の心臓の記録 (撃破 1) で呼び貝が一度だけ渡る
// 3. 見積もり・方角
// 4. 本物の海で早回し: 世界の初期スポーンにいちばん近い海の浜で呼び、潮殻が沖から歩いてきて泡の柱を立て (迎え)、
//    陸の奥の行き先を選ぶと、行き先にいちばん近い浜の沖に止まって浜に降ろす (降ろし)。潮殻は一体のまま
public final class TideTravelSelfTest {
    private static final UUID TEST_A = UUID.fromString("7a7e1000-0000-4000-8000-00000000a7b1");
    private static final UUID TEST_D = UUID.fromString("7a7e1000-0000-4000-8000-00000000a7b4");
    private static final int LOAD_RADIUS = 5;

    // 読み込みで見つけた海 (なければ null)
    private static BlockPos sea;

    private TideTravelSelfTest() {
    }

    // 呼ぶのは SelfTestRunner: 読み込み (load) → 待つ → 試す (runSelfTest)
    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        sea = SeaVoyage.seaNear(level, level.getSharedSpawnPos());
        if (sea != null) {
            forceAround(level, sea, true);
            SkyAbyss.LOGGER.info("[selftest] tide travel: nearest sea to the spawn is at {}", sea);
        }
    }

    private static void forceAround(ServerLevel level, BlockPos at, boolean on) {
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, on);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            run(level, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide travel crashed", e);
            failures.add("crashed: " + e);
        }
        if (sea != null) {
            forceAround(level, sea, false);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("[selftest] tide travel: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide travel PASSED (registration, call given once by the heart record, estimate, compass, pickup refusal, "
                    + "pickup/landing flight{})", sea == null ? " skipped: no sea near the spawn" : "");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST tide travel FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(ServerLevel level, List<String> failures, List<String> notes) {
        // ---- 1. 登録
        TitanKind kind = TitanKind.byId("tide_shell");
        check(kind == TitanKind.TIDE_SHELL && kind.style() == TitanKind.Style.SEA, "the tide shell is not a sea travel titan", failures);
        check(TitanKind.TIDE_SHELL.callItem().get() == TravelSetup.TIDE_SHELL_CALL.get(), "the tide shell's call is wrong", failures);
        check(Keepsakes.guardedItems().contains(TravelSetup.TIDE_SHELL_CALL.get()), "the tide conch is not guarded", failures);
        check(TitanKind.TIDE_SHELL.text("message.skyabyss.travel.called").equals("message.skyabyss.travel.tide_shell.called")
                && TitanKind.STONE_WHALE.text("message.skyabyss.travel.called").equals("message.skyabyss.travel.called"), "voiced keys are wrong", failures);
        check(Voyages.newVoyage(TitanKind.TIDE_SHELL, new Dummy(), FakePlayerFactory.get(level, new GameProfile(TEST_D, "[tide_travel_d]")),
                BlockPos.ZERO) instanceof SeaVoyage, "the tide shell does not get a sea voyage", failures);

        // ---- 2. 撃破の記録のフック: 潮殻の心臓を倒した記録で、呼び貝が一度だけ渡る
        FakePlayer d = FakePlayerFactory.get(level, new GameProfile(TEST_D, "[tide_travel_d]"));
        d.getInventory().clearContent();
        TitanMemoryData memory = TitanMemoryData.get(level.getServer());
        TitanMemoryData.Record r = memory.record(TEST_D, "tide_shell");
        TravelState state = Voyages.state(d);
        state.unlocked.clear();
        state.callGiven.clear();
        int clears = r.clears;
        try {
            r.clears = 0;
            check(!Voyages.isUnlocked(d, TitanKind.TIDE_SHELL) && Voyages.giveCalls(d) == 0, "a conch was given without the heart", failures);
            r.clears = 1;
            int given = Voyages.giveCalls(d);
            check(given >= 1 && d.getInventory().countItem(TravelSetup.TIDE_SHELL_CALL.get()) == 1, "stilling the tide heart did not give the conch", failures);
            check(Voyages.giveCalls(d) == 0 && d.getInventory().countItem(TravelSetup.TIDE_SHELL_CALL.get()) == 1, "the conch was given twice", failures);
        } finally {
            r.clears = clears;
            state.unlocked.clear();
            state.callGiven.clear();
            d.getInventory().clearContent();
        }

        // ---- 3. 見積もりと方角
        SeaVoyage probe = new SeaVoyage(TitanKind.TIDE_SHELL, new Dummy(), d, BlockPos.ZERO);
        int near = probe.estimate(200);
        int far = probe.estimate(5000);
        check(near >= 20 && near <= 80 && far >= near && far <= 90, "sea estimates are odd: " + near + " / " + far, failures);
        check(SeaVoyage.compass(BlockPos.ZERO, new BlockPos(0, 0, -100)).equals("n") && SeaVoyage.compass(BlockPos.ZERO, new BlockPos(100, 0, 100)).equals("se"),
                "compass is wrong", failures);
        // 海から離れた (読み込まれていない) 所では呼べない
        FakePlayer a = FakePlayerFactory.get(level, new GameProfile(TEST_A, "[tide_travel_a]"));
        a.moveTo(level.getSharedSpawnPos().getX() + 30000.5, 90, level.getSharedSpawnPos().getZ() + 0.5);
        check(SeaVoyage.findPickup(level, a) == null, "the tide shell could be called far from any known sea", failures);

        // ---- 4. 本物の海で早回し
        if (sea == null) {
            notes.add("no ocean within " + SeaVoyage.SEA_SEARCH + " blocks of the spawn; the flight was skipped");
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        // 浜: 海から世界の初期スポーンへ向かって最初の陸
        BlockPos shore = null;
        Vec3 from = Vec3.atCenterOf(sea);
        Vec3 dir = new Vec3(spawn.getX() - sea.getX(), 0, spawn.getZ() - sea.getZ());
        if (dir.lengthSqr() < 1) {
            dir = new Vec3(1, 0, 0);
        }
        dir = dir.normalize();
        for (double s = 0; s < LOAD_RADIUS * 16 - 8 && shore == null; s += 3) {
            double x = from.x + dir.x * s;
            double z = from.z + dir.z * s;
            if (TideOcean.land(level, x, z)) {
                Vec3 st = TideOcean.standableShore(level, x, z);
                if (st != null) {
                    shore = BlockPos.containing(st);
                }
            }
        }
        if (shore == null) {
            notes.add("no beach between the sea " + sea + " and the spawn within the loaded area; the flight was skipped");
            return;
        }
        a.moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5);
        BlockPos pickup = SeaVoyage.findPickup(level, a);
        if (pickup == null) {
            notes.add("the beach " + shore + " has no shallows the tide shell can rest in; the flight was skipped");
            return;
        }
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        TideShellEntity shell = null;
        try {
            // 沖 150 ブロックから歩いてくる
            Vec3 off = Vec3.atCenterOf(sea).subtract(Vec3.atCenterOf(pickup)).multiply(1, 0, 1);
            off = off.lengthSqr() < 1 ? new Vec3(150, 0, 0) : off.normalize().scale(150);
            shell = TideKeeper.KEEPER.spawn(level, pickup.getX() + off.x, pickup.getZ() + off.z, null);
            shell.setPos(shell.getX(), level.getSeaLevel() - SeaVoyage.TRAVEL_DRAFT, shell.getZ());
            shell.beginVoyage();
            SeaVoyage v = new SeaVoyage(TitanKind.TIDE_SHELL, shell, a, pickup);
            v.test = true;
            // 陸の奥の行き先 (浜から 120 ブロック内陸へ)
            BlockPos inland = BlockPos.containing(Vec3.atCenterOf(shore).add(dir.scale(120)));
            TravelNetwork.Dest dest = new TravelNetwork.Dest("test", "inland", "", inland.getX(), level.getSeaLevel(), inland.getZ(), 2, true, 0);
            EnumSet<Voyage.Phase> seen = EnumSet.noneOf(Voyage.Phase.class);
            StringBuilder timeline = new StringBuilder();
            Vec3 dockAtWaiting = null;
            int ticks = 0;
            for (; ticks < 6000 && !v.finished; ticks++) {
                seen.add(v.phase);
                if (v.phase == Voyage.Phase.WAITING && v.dest == null) {
                    dockAtWaiting = shell.position().add(shell.boardingChainOffset(shell.getYRot()));
                    String refused = v.accept(dest);
                    check(refused == null, "an inland destination near the sea was refused: " + refused, failures);
                    v.chosen(dest);
                    shell.setVoyageChain(Double.NaN);
                }
                Voyage.Phase before = v.phase;
                v.tick();
                if (v.phase != before) {
                    timeline.append(before).append('=').append(ticks).append(' ');
                }
                if (!shell.inVoyage() && !v.finished) {
                    failures.add("the tide shell left the voyage by itself in " + v.phase);
                    break;
                }
            }
            check(v.finished, "the tide voyage did not finish (stuck in " + v.phase + " after " + ticks + " ticks)", failures);
            check(seen.containsAll(EnumSet.of(Voyage.Phase.COMING, Voyage.Phase.WAITING, Voyage.Phase.BOARDING, Voyage.Phase.ASCENT,
                    Voyage.Phase.CRUISE, Voyage.Phase.APPROACH, Voyage.Phase.LOWERING)), "phases skipped: " + seen, failures);
            if (dockAtWaiting != null) {
                double reach = Math.hypot(dockAtWaiting.x - (pickup.getX() + 0.5), dockAtWaiting.z - (pickup.getZ() + 0.5));
                check(reach < 72, "the tide gate stopped " + (int) reach + " blocks from the pickup column", failures);
                notes.add("pickup: the gate stopped " + (int) reach + " blocks from the bubble column at " + pickup);
            }
            if (v.landing != null) {
                boolean onLand = TideOcean.land(level, v.landing.getX() + 0.5, v.landing.getZ() + 0.5);
                check(onLand, "the landing " + v.landing + " is not on land", failures);
                double toDest = Math.hypot(v.landing.getX() - inland.getX(), v.landing.getZ() - inland.getZ());
                check(toDest < 140, "the landing is " + (int) toDest + " blocks from the inland destination", failures);
                double fromShell = Math.hypot(shell.getX() - v.landing.getX(), shell.getZ() - v.landing.getZ());
                check(fromShell < 90, "the tide shell stopped " + (int) fromShell + " blocks from the beach", failures);
                notes.add("landing on the beach " + v.landing + " (" + (int) toDest + " blocks from the destination), shell " + (int) fromShell
                        + " blocks off shore");
            } else {
                failures.add("no beach was chosen");
            }
            check(TideOcean.waterAtSurface(level, shell.getX(), shell.getZ()), "the tide shell finished out of the water", failures);
            check(!shell.inVoyage() && shell.isResting(), "the tide shell did not rest after the voyage", failures);
            check(countShells(level) == 1, countShells(level) + " tide shells after the voyage", failures);
            check(ticks < 20 * 180, "the short tide voyage took too long: " + ticks + " ticks", failures);
            notes.add("short tide voyage took " + ticks + " ticks (" + ticks / 20 + " s), snaps " + v.snaps + ", phases ended at " + timeline);
        } finally {
            Voyages.forceEnd(TitanKind.TIDE_SHELL);
            if (shell != null) {
                shell.discard();
            }
            data.restore(saved, level.registryAccess());
        }
    }

    private static int countShells(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TideShellEntity && !entity.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    // 形だけの巨神 (型を確かめるため)
    private static final class Dummy implements TravelTitan {
        @Override
        public Entity asEntity() {
            return null;
        }

        @Override
        public void beginVoyage() {
        }

        @Override
        public void endVoyage(int restFor) {
        }

        @Override
        public boolean inVoyage() {
            return false;
        }

        @Override
        public void setVoyageChain(double ground) {
        }

        @Override
        public boolean isVoyageChainDown() {
            return false;
        }

        @Override
        public boolean isChainFolded() {
            return true;
        }

        @Override
        public Vec3 boardingChainOffset(float yaw) {
            return Vec3.ZERO;
        }

        @Override
        public List<Vec3> boardingPath() {
            return List.of(Vec3.ZERO);
        }

        @Override
        public int seatCount() {
            return 0;
        }

        @Override
        public Vec3 seatPosition(int index) {
            return Vec3.ZERO;
        }
    }
}
