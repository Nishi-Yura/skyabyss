package jp.revati.skyabyss.trial.raid.storm;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

// 雷翼のレイドの三つの部屋の自己テスト。SKYABYSS_SELFTEST_ONLY="storm raid"。結果は SELFTEST storm raid PASSED / FAILED。
// 登録 (三つそろう)、各部屋を建てて閉じていること (空気がたどり着ける所が部屋の外へ出ない)、
// SR1 = 雷の裁き (外れ・避雷板・護符・直撃)・羽の運び・リセット、SR2 = 300 通りの盤がかならず解ける・叩いて回す・解く・リセット、
// SR3 = 帯電の連鎖・三つの波・精鋭・リセット
public final class StormRaidSelfTest {
    private static final int Y = 150;

    private StormRaidSelfTest() {
    }

    private static final int[] XS = {-5000, -4800, -4600};
    private static int waited;

    // 三つの部屋の場所を強制読み込みし、敵・物を置けるようになる (エンティティが動く) まで待つ
    public static void load(MinecraftServer server) {
        waited = 0;
        for (int x : XS) {
            for (int cx = (x - 24) >> 4; cx <= (x + 24) >> 4; cx++) {
                for (int cz = (5000 - 36) >> 4; cz <= (5000 + 36) >> 4; cz++) {
                    server.overworld().setChunkForced(cx, cz, true);
                }
            }
        }
    }

    public static boolean ready(MinecraftServer server) {
        for (int x : XS) {
            if (!server.overworld().isPositionEntityTicking(new BlockPos(x, Y, 5000))) {
                return ++waited > 800;
            }
        }
        return true;
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            checkRegistered(failures);
            checkOutcomes(failures);
            checkLayouts(failures, notes);
            checkCircuit(level, failures);
            checkFeathers(level, failures);
            checkChain(level, failures, notes);
        } catch (Throwable e) {
            SkyAbyss.LOGGER.error("SELFTEST storm raid crashed", e);
            failures.add("crashed: " + e);
        }
        for (int x : XS) {
            for (int cx = (x - 24) >> 4; cx <= (x + 24) >> 4; cx++) {
                for (int cz = (5000 - 36) >> 4; cz <= (5000 + 36) >> 4; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST storm raid note: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm raid PASSED (registered, sealed cells, strike outcomes, feather haul + reset, 300 solvable circuits + solve + reset, charge chain + waves + elite + reset)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST storm raid FAILED: {}", f));
        }
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }

    private static RaidCtx ctx(ServerLevel level, String id, int x, long seed) throws Exception {
        Constructor<?> k = RaidCtx.class.getDeclaredConstructors()[0];
        k.setAccessible(true);
        return (RaidCtx) k.newInstance(level, UUID.randomUUID(), Titans.THUNDER_WING, id, 1, 1, new BlockPos(x, Y, 5000), seed);
    }

    private static void checkRegistered(List<String> failures) {
        RaidRooms.ensureLoaded();
        check(failures, RaidRooms.active(Titans.THUNDER_WING), "thunder wing has all three raid rooms registered");
        check(failures, "SR1".equals(RaidRooms.idOf(Titans.THUNDER_WING, Trial.Slot.WALK)), "WALK slot = SR1");
        check(failures, "SR2".equals(RaidRooms.idOf(Titans.THUNDER_WING, Trial.Slot.WIT)), "WIT slot = SR2");
        check(failures, "SR3".equals(RaidRooms.idOf(Titans.THUNDER_WING, Trial.Slot.FIGHT)), "FIGHT slot = SR3");
        // 順路の段の枠 (石の key) が三つとも違う (同じだと二つ目の段の「越えた」が無視される)
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (jp.revati.skyabyss.trial.Trial.Def d : jp.revati.skyabyss.trial.TrialCatalog.of(Titans.THUNDER_WING)) {
            if (d.id().startsWith("SR")) {
                keys.add(jp.revati.skyabyss.trial.TrialRegions.keyOfDef(Titans.THUNDER_WING, d, null));
            }
        }
        check(failures, keys.size() == 3, "SR1..SR3 map to three different stone keys " + keys);
        for (String id : new String[] {"SR1", "SR2", "SR3"}) {
            check(failures, RaidRooms.create(id) != null, id + " has a factory");
        }
    }

    private static void checkOutcomes(List<String> failures) {
        List<double[]> none = List.of();
        List<double[]> plate = List.of(new double[] {10, 0});
        List<double[]> holder = List.of(new double[] {1, 1});
        check(failures, FeatherCarry.resolve(0, 0, 9, 0, none, none) == FeatherCarry.Outcome.MISS, "carrier outside the ring: MISS");
        check(failures, FeatherCarry.resolve(0, 0, 1, 0, none, none) == FeatherCarry.Outcome.HIT, "carrier inside, nothing near: HIT");
        check(failures, FeatherCarry.resolve(0, 0, 1, 0, List.of(new double[] {2, 0}), none) == FeatherCarry.Outcome.GROUNDED, "plate near the carrier: GROUNDED");
        check(failures, FeatherCarry.resolve(0, 0, 1, 0, plate, none) == FeatherCarry.Outcome.HIT, "plate far from the carrier: HIT");
        check(failures, FeatherCarry.resolve(0, 0, 1, 0, none, holder) == FeatherCarry.Outcome.REDIRECT, "charm holder next to the carrier: REDIRECT");
        check(failures, FeatherCarry.resolve(0, 0, 1, 0, none, List.of(new double[] {9, 9})) == FeatherCarry.Outcome.HIT, "charm holder far: HIT");
    }

    // 300 通りの盤: かならず解ける・最初は解けていない・ランプは別々・電源は中央
    private static void checkLayouts(List<String> failures, List<String> notes) {
        int unsolvedStart = 0;
        for (long seed = 0; seed < 300; seed++) {
            CircuitRoom.Layout l = CircuitRoom.generate(RandomSource.create(seed));
            int lit = CircuitRoom.litLamps(l, l.solution, null);
            if (lit != CircuitRoom.LAMPS) {
                failures.add("circuit seed " + seed + ": solution lights only " + lit + " lamps");
                return;
            }
            if (CircuitRoom.litLamps(l, l.start, null) < CircuitRoom.LAMPS) {
                unsolvedStart++;
            }
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < CircuitRoom.LAMPS; i++) {
                seen.add(l.lampX[i] * 10 + l.lampZ[i]);
                if (l.lampX[i] == l.srcX && l.lampZ[i] == l.srcZ) {
                    failures.add("circuit seed " + seed + ": lamp on the source");
                    return;
                }
            }
            check(failures, seen.size() == CircuitRoom.LAMPS, "circuit seed " + seed + ": lamps are distinct");
        }
        check(failures, unsolvedStart >= 295, "circuits start unsolved (" + unsolvedStart + "/300)");
        notes.add("circuits start unsolved " + unsolvedStart + "/300");
    }

    // 空気が、休み場と広間から部屋の外へ出ないか (戸は閉じている)
    private static boolean sealed(ServerLevel level, RaidKit.Cell c, int maxSteps) {
        AABB b = c.bounds();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(c.rest());
        q.add(c.origin().above());
        q.add(c.exitSpot());
        seen.add(c.rest());
        seen.add(c.origin().above());
        seen.add(c.exitSpot());
        int steps = 0;
        while (!q.isEmpty() && steps++ < maxSteps) {
            BlockPos p = q.poll();
            if (!b.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
                return false;
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) {
                    continue;
                }
                var st = level.getBlockState(n);
                if (st.isAir() || !st.isSolid() && !st.is(Blocks.IRON_BARS)) {
                    if (st.is(Blocks.IRON_BARS)) {
                        continue;
                    }
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        return steps < maxSteps;
    }

    private static void checkCircuit(ServerLevel level, List<String> failures) throws Exception {
        RaidCtx ctx = ctx(level, "SR2", -5000, 77);
        CircuitRoom room = new CircuitRoom();
        room.build(ctx);
        check(failures, ctx.cell() != null, "SR2 built a cell");
        check(failures, sealed(level, ctx.cell(), 200000), "SR2 cell is sealed");
        check(failures, !room.isComplete(), "SR2 not complete before solving");
        CircuitRoom.Layout l = room.layout();
        BlockPos src = room.tileCenter(ctx, l.srcX, l.srcZ);
        check(failures, level.getBlockState(src).is(Blocks.OCHRE_FROGLIGHT), "SR2 source block placed");
        int[][] start = new int[CircuitRoom.N][];
        for (int x = 0; x < CircuitRoom.N; x++) {
            start[x] = room.current()[x].clone();
        }
        room.start(ctx);
        check(failures, !room.rotate(ctx, l.srcX, l.srcZ), "SR2 source does not rotate");
        solve(ctx, room);
        for (int i = 0; i < 4; i++) {
            room.tick(ctx);
        }
        check(failures, room.isComplete() && room.counter() == CircuitRoom.LAMPS, "SR2 solved by script: all lamps lit");
        BlockPos lamp = room.tileCenter(ctx, l.lampX[0], l.lampZ[0]);
        check(failures, level.getBlockState(lamp).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT), "SR2 lamp block is lit");
        // 解いたあとの床に、光る線がある
        boolean glow = false;
        for (int dx = -3; dx <= 3; dx++) {
            glow |= level.getBlockState(src.offset(dx, 0, 0)).is(Blocks.OCHRE_FROGLIGHT) && dx != 0;
        }
        check(failures, glow, "SR2 lit wire visible next to the source");
        room.reset(ctx);
        check(failures, !room.isComplete() && room.counter() < CircuitRoom.LAMPS, "SR2 wipe: not complete again");
        boolean same = true;
        for (int x = 0; x < CircuitRoom.N; x++) {
            same &= java.util.Arrays.equals(start[x], room.current()[x]);
        }
        check(failures, same, "SR2 wipe: tiles back to the starting rotations");
        room.start(ctx);
        solve(ctx, room);
        check(failures, room.isComplete(), "SR2 solvable again after the wipe");
        check(failures, sealed(level, ctx.cell(), 200000), "SR2 cell still sealed");
        room.end(ctx);
    }

    private static void solve(RaidCtx ctx, CircuitRoom room) {
        CircuitRoom.Layout l = room.layout();
        for (int x = 0; x < CircuitRoom.N; x++) {
            for (int z = 0; z < CircuitRoom.N; z++) {
                int guard = 0;
                while (room.current()[x][z] != l.solution[x][z] && guard++ < 4) {
                    room.rotate(ctx, x, z);
                }
            }
        }
    }

    private static void checkFeathers(ServerLevel level, List<String> failures) throws Exception {
        RaidCtx ctx = ctx(level, "SR1", -4800, 55);
        FeatherCarry room = new FeatherCarry();
        room.build(ctx);
        check(failures, ctx.cell() != null, "SR1 built a cell");
        check(failures, sealed(level, ctx.cell(), 400000), "SR1 cell is sealed");
        check(failures, room.plates().size() == FeatherCarry.PLATES, "SR1 has grounding plates");
        room.start(ctx);
        AABB box = ctx.cell().bounds();
        int feathers = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (FeatherCarry.featherIndex(e.getItem()) >= 0) {
                feathers++;
            }
        }
        check(failures, feathers == FeatherCarry.FEATHERS, "SR1 start puts three feathers on the deck (" + feathers + ")");
        for (int i = 0; i < 20; i++) {
            room.tick(ctx);
        }
        check(failures, !room.isComplete() && room.counter() == 0, "SR1 not complete before delivery");
        room.deliverForTest(ctx, 0);
        room.deliverForTest(ctx, 1);
        check(failures, room.counter() == 2 && !room.isComplete(), "SR1 two delivered");
        room.deliverForTest(ctx, 2);
        check(failures, room.counter() == 3 && room.isComplete(), "SR1 three delivered: complete");
        room.reset(ctx);
        check(failures, room.counter() == 0 && !room.isComplete(), "SR1 wipe: count and completion reset");
        int left = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (FeatherCarry.featherIndex(e.getItem()) >= 0) {
                left++;
            }
        }
        check(failures, left == 0, "SR1 wipe: feathers cleared before start (" + left + ")");
        room.start(ctx);
        feathers = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (FeatherCarry.featherIndex(e.getItem()) >= 0) {
                feathers++;
            }
        }
        check(failures, feathers == FeatherCarry.FEATHERS, "SR1 restart puts the feathers back (" + feathers + ")");
        room.end(ctx);
    }

    private static void checkChain(ServerLevel level, List<String> failures, List<String> notes) throws Exception {
        RaidCtx ctx = ctx(level, "SR3", -4600, 99);
        ChargeChain room = new ChargeChain();
        room.build(ctx);
        check(failures, ctx.cell() != null, "SR3 built a cell");
        check(failures, sealed(level, ctx.cell(), 400000), "SR3 cell is sealed");
        room.start(ctx);
        // 連鎖: 光る蛾を倒すと、近くの敵が傷つく (光っていない敵は連鎖しない)
        BlockPos c = ctx.cell().floor(0, 0);
        Mob a = ctx.adopt(StormRaid.spawn(level, StormMobSetup.STATIC_MOTH.get(), ctx.group(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5));
        Mob b = ctx.adopt(StormRaid.spawn(level, StormMobSetup.THUNDER_SUPPLICANT.get(), ctx.group(), c.getX() + 2.5, c.getY(), c.getZ() + 0.5));
        Mob d = ctx.adopt(StormRaid.spawn(level, StormMobSetup.THUNDER_SUPPLICANT.get(), ctx.group(), c.getX() - 2.5, c.getY(), c.getZ() + 1.5));
        Mob far = ctx.adopt(StormRaid.spawn(level, StormMobSetup.THUNDER_SUPPLICANT.get(), ctx.group(), c.getX() + 12.5, c.getY(), c.getZ() + 0.5));
        check(failures, a != null && b != null && d != null && far != null, "SR3 test mobs spawned");
        float hb = b.getHealth();
        float hd = d.getHealth();
        float hf = far.getHealth();
        room.setCharge(a, 10);
        room.setCharge(b, 0);
        room.setCharge(d, 0);
        room.setCharge(far, 0);
        a.kill();
        room.tick(ctx);
        check(failures, b.getHealth() == hb && d.getHealth() == hd, "SR3 an uncharged foe's death does not chain");
        Mob e = ctx.adopt(StormRaid.spawn(level, StormMobSetup.STATIC_MOTH.get(), ctx.group(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5));
        room.setCharge(e, ChargeChain.FULL);
        check(failures, e.isCurrentlyGlowing(), "SR3 a fully charged foe glows");
        e.kill();
        room.tick(ctx);
        check(failures, b.getHealth() < hb && d.getHealth() < hd, "SR3 charged foe's death chains to the nearby foes (" + b.getHealth() + "/" + hb + ", " + d.getHealth() + "/" + hd + ")");
        check(failures, far.getHealth() == hf, "SR3 chain does not reach far foes");
        // 波: 倒しては進む。三つの波のあと精鋭、精鋭を倒すと完了
        ctx.clearMobs();
        room.reset(ctx);
        room.start(ctx);
        int waveSeen = 0;
        boolean eliteSeen = false;
        for (int i = 0; i < 4000 && !room.isComplete(); i++) {
            room.tick(ctx);
            waveSeen = Math.max(waveSeen, room.wave());
            if (room.eliteSpawned()) {
                eliteSeen = true;
            }
            if (i % 4 == 0 && !ctx.aliveMobs().isEmpty()) {
                if (room.wave() > 0 && (!room.eliteSpawned() || i % 40 == 0)) {
                    for (Mob m : new ArrayList<>(ctx.aliveMobs())) {
                        m.kill();
                    }
                }
            }
        }
        check(failures, waveSeen == 3, "SR3 reaches wave 3 (" + waveSeen + ")");
        check(failures, eliteSeen, "SR3 the elite arrives after the waves");
        check(failures, room.isComplete(), "SR3 complete after the elite falls");
        notes.add("SR3 counter at completion " + room.counter() + "/" + room.counterMax());
        // 全滅: 敵は消え、波は最初から
        ctx.clearMobs();
        room.reset(ctx);
        check(failures, !room.isComplete() && room.wave() == 0 && room.counter() == 0 && ctx.aliveMobs().isEmpty(), "SR3 wipe: back to wave 0");
        room.start(ctx);
        for (int i = 0; i < 100; i++) {
            room.tick(ctx);
        }
        check(failures, room.wave() == 1 && !ctx.aliveMobs().isEmpty(), "SR3 wave 1 spawns again after the wipe");
        ctx.clearMobs();
        room.end(ctx);
    }
}
