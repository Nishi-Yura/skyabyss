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

    private static final int[] XS = {-5000, -4800, -4600, -4400, -4200, -4000};
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
            checkColumn(level, failures);
            checkBeat(level, failures);
            checkEye(level, failures);
            checkDraw(failures);
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
            SkyAbyss.LOGGER.info("SELFTEST storm raid PASSED (registered, sealed cells, strike outcomes, feather haul + reset, 300 solvable circuits + solve + reset, charge chain + waves + elite + reset, pillar chase + beat tiles + eye advance + draw)");
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
        check(failures, keys.size() == 3, "SR1..SR6 map to three different stone keys " + keys);
        for (String id : new String[] {"SR1", "SR2", "SR3", "SR4", "SR5", "SR6"}) {
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

    // ---- SR4 雷柱から逃げて鍵集め
    private static void checkColumn(ServerLevel level, List<String> failures) throws Exception {
        RaidCtx ctx = ctx(level, "SR4", -4400, 31);
        ColumnChase room = new ColumnChase();
        room.build(ctx);
        check(failures, ctx.cell() != null, "SR4 built a cell");
        check(failures, sealed(level, ctx.cell(), 400000), "SR4 cell is sealed");
        check(failures, room.pillars().size() >= 3, "SR4 has covering pillars (" + room.pillars().size() + ")");
        check(failures, ColumnChase.SPEED + ColumnChase.SPEED_PER_KEY * ColumnChase.KEYS < 0.2, "SR4 the pillar stays slower than a walking player");
        for (int i = 0; i < ColumnChase.KEYS; i++) {
            check(failures, ctx.cell().inArena(room.keyX(ctx, i), room.keyZ(ctx, i)), "SR4 key " + i + " is inside the hall");
            for (int j = i + 1; j < ColumnChase.KEYS; j++) {
                check(failures, Math.hypot(room.keyX(ctx, i) - room.keyX(ctx, j), room.keyZ(ctx, i) - room.keyZ(ctx, j)) >= 7, "SR4 keys " + i + " and " + j + " are far apart");
            }
        }
        room.start(ctx);
        // 柱は最も近い人へ歩き、立ち止まって赤い帯を引く (人は広間の南寄りの仮の目標。鍵から離す)
        double tx = ctx.origin().getX() + 0.5;
        double tz = ctx.origin().getZ() + 8.5;
        List<double[]> target = List.of(new double[] {tx, tz});
        double d0 = Math.hypot(room.colX() - tx, room.colZ() - tz);
        for (int i = 0; i < ColumnChase.GRACE - 2; i++) {
            room.update(ctx, target);
        }
        check(failures, room.phase() == ColumnChase.Phase.GRACE && Math.abs(room.colX() - (ctx.origin().getX() + 0.5)) < 1e-9, "SR4 the pillar waits during the grace");
        int before = jp.revati.skyabyss.combat.Telegraph.activeCount(level);
        boolean beam = false;
        for (int i = 0; i < ColumnChase.GRACE + ColumnChase.ROAM + 4 && !beam; i++) {
            room.update(ctx, target);
            beam = room.phase() == ColumnChase.Phase.CHARGE;
        }
        double d1 = Math.hypot(room.colX() - tx, room.colZ() - tz);
        check(failures, d1 < d0 - 5, "SR4 the pillar walked toward the nearest player (" + d0 + " -> " + d1 + ")");
        check(failures, beam && jp.revati.skyabyss.combat.Telegraph.activeCount(level) > before, "SR4 the pillar stops and shows a red beam");
        // 鍵: 位置に立てば取れる。三つで完了
        int[] order = {1, 2, 0};
        for (int k = 0; k < 3; k++) {
            room.update(ctx, List.of(new double[] {room.keyX(ctx, order[k]), room.keyZ(ctx, order[k])}));
            check(failures, room.counter() == k + 1, "SR4 key " + (k + 1) + " collected (" + room.counter() + ")");
        }
        check(failures, room.isComplete(), "SR4 three keys: complete");
        check(failures, !level.getBlockState(ctx.origin().offset(room.keyOffset(0)[0], 1, room.keyOffset(0)[1])).is(Blocks.VERDANT_FROGLIGHT), "SR4 a taken key's lamp goes dark");
        room.reset(ctx);
        check(failures, !room.isComplete() && room.counter() == 0 && room.phase() == ColumnChase.Phase.GRACE, "SR4 wipe: keys and pillar back to the start");
        check(failures, Math.abs(room.colX() - (ctx.origin().getX() + 0.5)) < 1e-9 && Math.abs(room.colZ() - (ctx.origin().getZ() - ColumnChase.HZ + 3.5)) < 1e-9, "SR4 wipe: the pillar is back at its post");
        check(failures, level.getBlockState(ctx.origin().offset(room.keyOffset(0)[0], 1, room.keyOffset(0)[1])).is(Blocks.VERDANT_FROGLIGHT), "SR4 wipe: the key lamp is lit again");
        room.start(ctx);
        for (int i = 0; i < 3; i++) {
            room.update(ctx, List.of(new double[] {room.keyX(ctx, i), room.keyZ(ctx, i)}));
        }
        check(failures, room.isComplete(), "SR4 solvable again after the wipe");
        check(failures, sealed(level, ctx.cell(), 400000), "SR4 cell still sealed");
        room.end(ctx);
    }

    // ---- SR5 雷の拍子覚え
    private static void checkBeat(ServerLevel level, List<String> failures) throws Exception {
        for (long seed = 0; seed < 300; seed++) {
            int[] s = BeatTiles.generate(RandomSource.create(seed));
            boolean ok = s.length == BeatTiles.MAXLEN;
            for (int i = 1; i < s.length && ok; i++) {
                int dx = Math.abs(s[i] % BeatTiles.N - s[i - 1] % BeatTiles.N);
                int dz = Math.abs(s[i] / BeatTiles.N - s[i - 1] / BeatTiles.N);
                ok = s[i] != s[i - 1] && dx <= 1 && dz <= 1;
            }
            if (!ok) {
                failures.add("SR5 sequence for seed " + seed + " is not walkable (adjacent tiles, no repeats)");
                return;
            }
        }
        RaidCtx ctx = ctx(level, "SR5", -4200, 47);
        BeatTiles room = new BeatTiles();
        room.build(ctx);
        check(failures, ctx.cell() != null, "SR5 built a cell");
        check(failures, sealed(level, ctx.cell(), 400000), "SR5 cell is sealed");
        check(failures, BeatTiles.tileAt(ctx.origin(), ctx.origin().getX() + 5.5, ctx.origin().getZ() - 4.5) == 2, "SR5 tile lookup (east-north tile is 2)");
        check(failures, BeatTiles.tileAt(ctx.origin(), ctx.origin().getX() + 2.5, ctx.origin().getZ() + 0.5) == -1, "SR5 the lanes between tiles are not tiles");
        int[] first = room.seq().clone();
        room.start(ctx);
        check(failures, room.phase() == BeatTiles.Phase.PREP && !room.isComplete(), "SR5 starts with the preparation");
        boolean[] none = new boolean[BeatTiles.N * BeatTiles.N];
        // 見せている間の踏み込みは数えない
        for (int i = 0; i < BeatTiles.PREP + 5; i++) {
            room.advance(ctx, none);
        }
        room.enterTile(ctx, (room.seq()[0] + 1) % 9);
        check(failures, room.phase() == BeatTiles.Phase.SHOW, "SR5 stepping while the sequence plays is ignored");
        check(failures, solveBeat(ctx, room, failures), "SR5 solved by script on the beat");
        check(failures, room.isComplete() && room.counter() == BeatTiles.ROUNDS, "SR5 four rounds: complete (" + room.counter() + ")");
        check(failures, room.tileState(ctx, room.seq()[0]).is(Blocks.VERDANT_FROGLIGHT), "SR5 solved tiles glow green");
        // 失敗: 順にない板 / 拍に遅れる / 早く乗っても数えない
        room.reset(ctx);
        check(failures, !room.isComplete() && room.round() == 0 && room.phase() == BeatTiles.Phase.IDLE && java.util.Arrays.equals(first, room.seq()), "SR5 wipe: round 0, same sequence");
        room.start(ctx);
        toInput(ctx, room);
        boolean[] early = new boolean[9];
        early[room.seq()[1]] = true;
        room.advance(ctx, early);
        check(failures, room.next() == 0 && room.phase() == BeatTiles.Phase.INPUT, "SR5 waiting on a later tile does not count");
        room.enterTile(ctx, (room.seq()[0] + 4) % 9 == room.seq()[0] ? 0 : (room.seq()[0] + 4) % 9);
        check(failures, room.phase() == BeatTiles.Phase.FAIL, "SR5 a wrong tile fails the round");
        int roundBefore = room.round();
        for (int i = 0; i < BeatTiles.PAUSE + 2; i++) {
            room.advance(ctx, none);
        }
        check(failures, room.phase() == BeatTiles.Phase.SHOW && room.round() == roundBefore, "SR5 after a miss the same round is shown again");
        toInput(ctx, room);
        for (int i = 0; i < BeatTiles.BEAT + BeatTiles.GRACE + 3; i++) {
            room.advance(ctx, none);
        }
        check(failures, room.phase() == BeatTiles.Phase.FAIL, "SR5 missing the beat fails the round");
        // 立って待っていた板は拍で数える (手分け)
        for (int i = 0; i < BeatTiles.PAUSE + 2; i++) {
            room.advance(ctx, none);
        }
        toInput(ctx, room);
        boolean[] ready = new boolean[9];
        ready[room.seq()[0]] = true;
        room.advance(ctx, ready);
        check(failures, room.next() == 1, "SR5 a tile waited on counts when its beat comes");
        room.reset(ctx);
        room.start(ctx);
        check(failures, solveBeat(ctx, room, failures) && room.isComplete(), "SR5 solvable again after the wipe");
        room.end(ctx);
    }

    // 入力の入り口まで進める
    private static void toInput(RaidCtx ctx, BeatTiles room) {
        boolean[] none = new boolean[9];
        for (int i = 0; i < 4000 && room.phase() != BeatTiles.Phase.INPUT; i++) {
            room.advance(ctx, none);
        }
    }

    // 台本の人: 次の板が拍に入ってから 2 tick 後に乗る
    private static boolean solveBeat(RaidCtx ctx, BeatTiles room, List<String> failures) {
        boolean[] occ = new boolean[9];
        for (int i = 0; i < 20000 && !room.isComplete(); i++) {
            java.util.Arrays.fill(occ, false);
            if (room.phase() == BeatTiles.Phase.INPUT && room.next() < room.length() && room.phaseAge() >= room.next() * BeatTiles.BEAT + 2) {
                occ[room.seq()[room.next()]] = true;
            }
            room.advance(ctx, occ);
            if (room.phase() == BeatTiles.Phase.FAIL) {
                failures.add("SR5 the scripted player failed a round at round " + room.round());
                return false;
            }
        }
        return room.isComplete();
    }

    // ---- SR6 嵐の目へ
    private static void checkEye(ServerLevel level, List<String> failures) throws Exception {
        RaidCtx ctx = ctx(level, "SR6", -4000, 63);
        EyeAdvance room = new EyeAdvance();
        room.build(ctx);
        check(failures, ctx.cell() != null, "SR6 built a cell");
        check(failures, sealed(level, ctx.cell(), 600000), "SR6 cell is sealed");
        check(failures, room.pillars().size() >= 8, "SR6 has pillars to shelter behind (" + room.pillars().size() + ")");
        // 突風の周期: 始まる前は吹かず、予告のあとに吹き、また凪になる
        long first = EyeAdvance.FIRST_GUST;
        check(failures, !EyeAdvance.blowing(first + EyeAdvance.WARN - 1) && EyeAdvance.blowing(first + EyeAdvance.WARN), "SR6 a gust begins after its warning");
        check(failures, EyeAdvance.blowing(first + EyeAdvance.WARN + EyeAdvance.GUST - 1) && !EyeAdvance.blowing(first + EyeAdvance.WARN + EyeAdvance.GUST), "SR6 a gust ends");
        check(failures, EyeAdvance.blowing(first + EyeAdvance.GUST_PERIOD + EyeAdvance.WARN + 3), "SR6 gusts repeat");
        check(failures, EyeAdvance.untilGust(first + EyeAdvance.WARN + EyeAdvance.GUST + 5) == EyeAdvance.GUST_PERIOD - EyeAdvance.GUST - 5, "SR6 the countdown to the next gust");
        double[] push = EyeAdvance.pushOf(0, 0, 3, 4, 0.13);
        check(failures, Math.abs(push[0] - 0.078) < 1e-9 && Math.abs(push[1] - 0.104) < 1e-9, "SR6 the push points away from the centre");
        // 柱の陰は押されない
        int[] pil = room.pillars().get(0);
        double px = ctx.origin().getX() + pil[0] + 1.0;
        double pz = ctx.origin().getZ() + pil[1] + 1.0;
        double cx = ctx.origin().getX() + 0.5;
        double cz = ctx.origin().getZ() + 0.5;
        double len = Math.hypot(px - cx, pz - cz);
        double bx = px + (px - cx) / len * 2.5;
        double bz = pz + (pz - cz) / len * 2.5;
        double y = ctx.origin().getY() + 1;
        check(failures, room.sheltered(ctx, bx, y, bz), "SR6 behind a pillar is sheltered");
        check(failures, !room.sheltered(ctx, cx, y, ctx.origin().getZ() + 11.5), "SR6 the open lane to the door is not sheltered");
        // 始まり: 守り手が中央に
        room.start(ctx);
        check(failures, room.keeper() != null && room.keeper().isAlive() && ctx.aliveMobs().contains(room.keeper()), "SR6 the keeper stands in the eye");
        check(failures, room.secondaryMax() > 0 && room.secondary() >= room.secondaryMax() - 1, "SR6 keeper health shows on the HUD");
        // 突風の予告で、押しの印と雷蛾
        int tele = jp.revati.skyabyss.combat.Telegraph.activeCount(level);
        room.setAge(EyeAdvance.FIRST_GUST - 1);
        room.tick(ctx);
        check(failures, jp.revati.skyabyss.combat.Telegraph.activeCount(level) > tele, "SR6 the gust shows a warning mark");
        int moths = 0;
        for (Mob m : ctx.aliveMobs()) {
            if (m.getType() == StormMobSetup.STATIC_MOTH.get()) {
                moths++;
            }
        }
        check(failures, moths == 2, "SR6 a gust brings two moths (" + moths + ")");
        check(failures, room.timerTicks() == EyeAdvance.WARN, "SR6 the HUD counts down to the gust");
        check(failures, !room.isComplete(), "SR6 not complete while the keeper lives");
        room.keeper().kill();
        room.tick(ctx);
        check(failures, room.isComplete(), "SR6 defeating the keeper completes");
        // 全滅
        ctx.clearMobs();
        room.reset(ctx);
        check(failures, !room.isComplete() && room.keeper() == null && room.age() == 0 && ctx.aliveMobs().isEmpty(), "SR6 wipe: back to the start with no foes");
        room.start(ctx);
        check(failures, room.keeper() != null && room.keeper().isAlive(), "SR6 the keeper is back after the wipe");
        check(failures, sealed(level, ctx.cell(), 600000), "SR6 cell still sealed");
        ctx.clearMobs();
        room.end(ctx);
    }

    // 枠ごとに二つの部屋があり、周ごとにどちらも引かれる
    private static void checkDraw(List<String> failures) {
        for (Trial.Slot slot : Trial.Slot.values()) {
            List<String> ids = RaidRooms.idsOf(Titans.THUNDER_WING, slot);
            check(failures, ids.size() == 2, slot + " has two storm rooms " + ids);
            Set<String> seen = new HashSet<>();
            for (long seed = 1; seed <= 200; seed++) {
                seen.add(RaidRooms.drawOf(Titans.THUNDER_WING, slot, seed * 7919L));
            }
            check(failures, seen.containsAll(ids) && seen.size() == 2, slot + " draw picks either room across runs " + seen);
        }
    }
}
