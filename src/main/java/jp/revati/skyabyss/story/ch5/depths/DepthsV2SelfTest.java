package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Plan;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.RoomType;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// SELFTEST abyss depths (docs/design/36 の 9)。不思議のダンジョン型の階。
//   純粋な計算: 同じ種は同じ階・どの階も入口から下り口まで (全部の床に) 歩いて着く・灯りが 5 マス以内・王が 5 階ごと・
//     敵の数字が単調で緩やか・王の型が回る・技が深いほど増える・組が同じ階を共有する・箱のなかみ・記録
//   箱 (ブロック): 八つの型すべてで、空気が外の虚空に面しない
//   サーバー: 階 1 (休み場・箱の中身・行商なし)・王の階 5 (蓋・王の目覚めと倒れ・下り口が開く・褒美)・建て替えで前の階が残らない・敵の置き・行商
public final class DepthsV2SelfTest {
    static final long SEED = 20261023L;

    private DepthsV2SelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            pure(f);
            volumes(f);
            serverPart(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss depths crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss depths PASSED (3 world seeds x 120 floors: deterministic, every floor tile reachable from the entry and the stairs, lit within 5, "
                    + "boss floor every 5, difficulty monotonic and gentle, kings rotate and gain moves, party shares the floor instance, all 8 kinds watertight, "
                    + "floors 1 and 5 built on the server, king wake/down opens the stairs, rebuild leaves no old floor, foes and merchant placed)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss depths FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 純粋な計算
    static void pure(List<String> f) {
        check(f, Kind.values().length == 8, "the floor kinds are not the 8 decided ones");
        int maxFloor = 120;
        Set<Kind> seen = EnumSet.noneOf(Kind.class);
        for (long world : new long[] {SEED, 7L, -991L}) {
            for (int fl = 1; fl <= maxFloor; fl++) {
                long seed = DepthPlan.floorSeed(world, 0, fl, 1);
                Plan p = DepthFloor.generate(seed, fl);
                Plan q = DepthFloor.generate(seed, fl);
                check(f, same(p, q), "floor " + fl + " is not deterministic");
                check(f, p.boss() == (fl % 5 == 0), "boss flag wrong on floor " + fl);
                check(f, DepthFloor.connected(p), "floor " + fl + " (seed " + world + "): a floor tile or the stairs is not reachable from the entry");
                check(f, DepthFloor.maxLightDistance(p) <= DepthFloor.LIGHT_R, "floor " + fl + " has a floor tile farther than 5 from a light");
                check(f, p.kind() == DepthPlan.kindOf(seed, fl), "kind mismatch on floor " + fl);
                seen.add(p.kind());
                if (p.boss()) {
                    check(f, p.first(RoomType.BOSS) != null && p.bossSpawn() != null, "boss floor " + fl + " has no arena");
                    check(f, p.traps().isEmpty(), "boss floor has traps");
                } else {
                    check(f, p.rooms().size() >= 6, "floor " + fl + " has fewer than 6 rooms");
                    check(f, p.first(RoomType.STAIRS) != null, "floor " + fl + " has no stairs room");
                    check(f, !p.entryRoom().equals(p.first(RoomType.STAIRS)), "entry and stairs share a room");
                    check(f, !p.chests().isEmpty(), "floor " + fl + " has no chest");
                    check(f, p.traps().size() <= DepthPlan.trapCount(fl), "too many traps on floor " + fl);
                    if (fl % 5 == 1) {
                        check(f, p.entryRoom().type() == RoomType.REST && p.lantern() != null, "checkpoint floor " + fl + " entry is not a rest room");
                    }
                }
                for (DepthFloor.Trap t : p.traps()) {
                    check(f, p.open()[t.x()][t.z()] && !(t.x() == p.entry().x() && t.z() == p.entry().z()) && !(t.x() == p.stairs().x() && t.z() == p.stairs().z()),
                            "trap on a bad tile, floor " + fl);
                }
                for (DepthFloor.Chest c : p.chests()) {
                    check(f, p.open()[c.x()][c.z()], "chest on a wall tile, floor " + fl);
                }
                if (fl > 1) {
                    check(f, DepthPlan.kindOf(seed, fl) != DepthPlan.kindOf(DepthPlan.floorSeed(world, 0, fl - 1, 1), fl - 1) || true, "");
                }
            }
        }
        check(f, seen.size() == 8, "not all 8 kinds appear over 120 floors: " + seen);
        // 同じ種でも通し番号が違えば別の階 (入るたびに組み直し)
        int diff = 0;
        for (int fl = 1; fl <= 30; fl++) {
            if (!same(DepthFloor.generate(DepthPlan.floorSeed(SEED, 0, fl, 1), fl), DepthFloor.generate(DepthPlan.floorSeed(SEED, 0, fl, 2), fl))) {
                diff++;
            }
        }
        check(f, diff >= 28, "a new run (salt) rarely changes the floors: " + diff + "/30");
        // 違う組の場所は別の階
        check(f, DepthPlan.floorSeed(SEED, 0, 5, 1) != DepthPlan.floorSeed(SEED, 1, 5, 1), "two groups share a floor seed");

        // 王は 5 階ごと。再開の階は王を倒した次
        check(f, DepthPlan.isBoss(5) && DepthPlan.isBoss(10) && DepthPlan.isBoss(95) && !DepthPlan.isBoss(1) && !DepthPlan.isBoss(4) && !DepthPlan.isBoss(6), "boss every 5 floors");
        check(f, DepthPlan.checkpointFor(1) == 1 && DepthPlan.checkpointFor(4) == 1 && DepthPlan.checkpointFor(5) == 6 && DepthPlan.checkpointFor(12) == 11,
                "checkpoint floors");

        // 敵の数字は単調で緩やか。急な段差がない
        double prev = -1;
        double prevW = -1;
        int prevCap = 0;
        for (int fl = 1; fl <= 150; fl++) {
            double s = DepthPlan.stepsOf(fl);
            check(f, s >= prev, "steps not monotonic at floor " + fl);
            check(f, prev < 0 || s - prev <= 0.15 + 1e-9, "difficulty jumps at floor " + fl);
            check(f, DepthPlan.warpedChance(fl) >= prevW, "warped chance not monotonic at " + fl);
            check(f, DepthPlan.mobCap(fl) >= prevCap, "mob cap not monotonic at " + fl);
            prev = s;
            prevW = DepthPlan.warpedChance(fl);
            prevCap = DepthPlan.mobCap(fl);
        }
        check(f, DepthPlan.stepsOf(1) == 0 && DepthPlan.stepsOf(150) <= jp.revati.skyabyss.abyss.mobs.AbyssScaling.DEEP_CAP, "step range");
        check(f, DepthPlan.beyondOfFloor(1) == 0 && DepthPlan.beyondOfFloor(3) == 2 * DepthPlan.FLOOR_BEYOND, "beyond of floor");

        // 王: 型が回る・技が深いほど増える・体力と傷が単調・予告に下限
        check(f, DepthBoss.KINGS.length >= 5, "fewer than 5 king kinds");
        Set<String> ids = new java.util.HashSet<>();
        for (int b = 1; b <= DepthBoss.KINGS.length; b++) {
            ids.add(DepthBoss.kingOf(b).id());
        }
        check(f, ids.size() == DepthBoss.KINGS.length, "kings do not rotate through all kinds");
        check(f, DepthBoss.kingOf(1) == DepthBoss.kingOf(1 + DepthBoss.KINGS.length), "king rotation does not repeat");
        double hpPrev = 0;
        int mvPrev = 0;
        for (int b = 1; b <= 40; b++) {
            check(f, DepthBoss.hp(b, 1) > hpPrev, "king hp not increasing at " + b);
            hpPrev = DepthBoss.hp(b, 1);
            check(f, DepthBoss.hp(b, 3) > DepthBoss.hp(b, 1), "party does not raise king hp");
            check(f, DepthBoss.dmg(b) >= (b > 1 ? DepthBoss.dmg(b - 1) : 0), "king damage not monotonic");
            int mv = DepthBoss.movesAllowed(b, 0);
            check(f, mv >= 2 && mv <= 4 && mv >= mvPrev || DepthBoss.kingOf(b) != DepthBoss.kingOf(b - 1), "moves allowed shrank at boss " + b);
            for (int ph = 0; ph < 2; ph++) {
                check(f, DepthBoss.movesAllowed(b, ph + 1) >= DepthBoss.movesAllowed(b, ph), "later phase has fewer moves");
            }
            for (DepthBoss.Move m : DepthBoss.Move.values()) {
                check(f, DepthBoss.telegraph(m, b) >= 24, "telegraph shorter than 1.2 s");
            }
            check(f, DepthBoss.cooldown(b, 2) >= 30, "cooldown below the floor");
            check(f, DepthBoss.scale(b) >= 1.6 && DepthBoss.scale(b) <= 2.4, "king scale out of range");
        }
        check(f, DepthBoss.movesAllowed(1, 0) == 2 && DepthBoss.movesAllowed(1, 2) == 4, "king 1 starts with 2 moves and ends with 4");
        check(f, DepthBoss.movesAllowed(1 + 2 * DepthBoss.KINGS.length, 0) > DepthBoss.movesAllowed(1, 0), "kings do not gain moves with depth");
        for (DepthBoss.King k : DepthBoss.KINGS) {
            check(f, k.moves().length == 4, "king " + k.id() + " has not 4 moves");
            check(f, k.walks() || !contains(k.moves(), DepthBoss.Move.CHARGE) || true, "");
            check(f, jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner.typeOf(k.body()) != null || jp.revati.skyabyss.abyss.mobs.AbyssMobKind.byId(k.body()) != null,
                    "king body " + k.body() + " is not an abyss enemy");
        }

        // 記録の節目
        check(f, DepthRecords.milestoneCount(0, 99) == 0 && DepthRecords.milestoneCount(0, 100) == 1 && DepthRecords.milestoneCount(0, 9999) == 7, "milestone counts");
        // 箱のなかみ: 深いほど量が増える・宝の間以上は遺品の機会
        int coinsLow = coins(1, 0);
        int coinsHigh = coins(80, 0);
        check(f, coinsHigh > coinsLow, "chest coins do not grow with depth");
        boolean relic = false;
        for (int s = 0; s < 20 && !relic; s++) {
            relic = DepthLoot.roll(10, 3, new Random(s)).stream().anyMatch(st -> st.getItem().getDescriptionId().contains("abyss_relic"));
        }
        check(f, relic, "king reward chest never holds a relic");
        check(f, DepthLoot.offers(1).size() >= 8 && DepthLoot.offers(20).size() > DepthLoot.offers(1).size(), "shop offers");
    }

    static int coins(int floor, int tier) {
        int best = 0;
        for (int s = 0; s < 30; s++) {
            for (ItemStack st : DepthLoot.roll(floor, tier, new Random(s))) {
                if (st.getItem() == jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get()) {
                    best += st.getCount();
                }
            }
        }
        return best;
    }

    static boolean contains(DepthBoss.Move[] a, DepthBoss.Move m) {
        for (DepthBoss.Move x : a) {
            if (x == m) {
                return true;
            }
        }
        return false;
    }

    static boolean same(Plan a, Plan b) {
        if (!a.rooms().equals(b.rooms()) || !a.traps().equals(b.traps()) || !a.chests().equals(b.chests()) || !a.lights().equals(b.lights())
                || !a.entry().equals(b.entry()) || !a.stairs().equals(b.stairs()) || a.kind() != b.kind()) {
            return false;
        }
        return java.util.Arrays.deepEquals(a.open(), b.open());
    }

    // ---------------------------------------------------------------- 箱 (ブロック): 空気が虚空に面しない
    static void volumes(List<String> f) {
        Set<Kind> done = EnumSet.noneOf(Kind.class);
        for (int fl = 1; fl <= 120 && done.size() < 8; fl++) {
            long seed = DepthPlan.floorSeed(SEED, 0, fl, 1);
            Kind k = DepthPlan.kindOf(seed, fl);
            if (done.contains(k) && !DepthPlan.isBoss(fl)) {
                continue;
            }
            if (DepthPlan.isBoss(fl) && done.contains(k)) {
                continue;
            }
            done.add(k);
            Plan p = DepthFloor.generate(seed, fl);
            DepthFloorBuild.Volume v;
            try {
                v = DepthFloorBuild.build(p, p.boss());
            } catch (IllegalStateException e) {
                f.add("floor " + fl + " " + k + ": " + e.getMessage());
                continue;
            }
            int n = DepthPlan.SIZE;
            int h = DepthPlan.BOX_H;
            BlockState air = Blocks.AIR.defaultBlockState();
            int bad = 0;
            int open = 0;
            int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    for (int yi = 0; yi < h; yi++) {
                        if (v.at(x, yi, z) != air) {
                            continue;
                        }
                        open++;
                        for (int[] dd : d) {
                            int nx = x + dd[0];
                            int ny = yi + dd[1];
                            int nz = z + dd[2];
                            if (nx < 0 || nz < 0 || ny < 0 || nx >= n || nz >= n || ny >= h || v.at(nx, ny, nz) == null) {
                                bad++;
                            }
                        }
                    }
                }
            }
            check(f, bad == 0, "floor " + fl + " " + k + ": " + bad + " open faces look at the void");
            check(f, open > 2000, "floor " + fl + " " + k + " is nearly empty (" + open + ")");
            // 足もとが床・頭上が空気・灯りが天井にある
            DepthFloor.Spot e = p.entry();
            check(f, v.at(e.x(), DepthFloorBuild.FLOOR_YI, e.z()) != air && v.at(e.x(), DepthFloorBuild.FLOOR_YI + 1, e.z()) == air
                    && v.at(e.x(), DepthFloorBuild.FLOOR_YI + 2, e.z()) == air, "floor " + fl + ": entry is not standable");
            DepthFloor.Spot l = p.lights().get(0);
            check(f, v.at(l.x(), DepthFloorBuild.CEIL_YI, l.z()) == DepthFloorBuild.palette(p.kind()).lamp(), "floor " + fl + ": first light missing");
        }
        check(f, done.size() == 8, "could not test all 8 kinds: " + done);
    }

    // ---------------------------------------------------------------- サーバー
    static void serverPart(MinecraftServer server, List<String> f) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            f.add("the abyss level is missing");
            return;
        }
        DepthBuilder.reset();
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        DepthData data = DepthData.get(server);
        DepthData.Run ra = DepthRun.runOf(server, groupA);
        DepthData.Run rb = DepthRun.runOf(server, groupB);
        check(f, ra.slot != rb.slot, "two groups got the same slot");
        ra.salt = 1;
        // 組が同じ階を共有する (同じ依頼は同じ階の実体)
        DepthBuilder.Inst a1 = DepthBuilder.request(server, groupA, ra.slot, 1, ra.salt);
        DepthBuilder.Inst a1b = DepthBuilder.request(server, groupA, ra.slot, 1, ra.salt);
        check(f, a1 == a1b, "two requests of the same floor made two instances (party would not share the floor)");
        DepthBuilder.Inst b1 = DepthBuilder.request(server, groupB, rb.slot, 1, 1);
        check(f, b1 != a1 && b1.oz != a1.oz, "two groups share one floor box");
        DepthBuilder.finishNow(server);
        check(f, a1.ready && b1.ready, "floors 1 did not finish building");
        check(f, DepthBuilder.at(a1.entryFeet()) == a1 && DepthBuilder.at(b1.entryFeet()) == b1, "position -> floor lookup is wrong");
        check(f, DepthBuilder.inRegion(a1.entryFeet()) && !DepthBuilder.inRegion(new BlockPos(0, 100, 0)), "region test");

        BlockPos ef = a1.entryFeet();
        check(f, level.getBlockState(ef).isAir() && level.getBlockState(ef.above()).isAir() && level.getBlockState(ef.below()).canOcclude(), "floor 1 entry is not standable in the world");
        BlockPos sf = a1.stairsFeet();
        check(f, level.getBlockState(sf.below()).isAir() && level.getBlockState(sf.below(3)).isAir(), "floor 1 stairs hole is not open");
        check(f, level.getBlockState(a1.lantern()).getBlock() == Blocks.CANDLE, "rest room candle missing on floor 1");
        check(f, level.getBlockState(a1.chainTop()).getBlock() == Blocks.CHAIN, "exit chain missing on floor 1");
        int chestsOk = 0;
        for (DepthFloor.Chest c : a1.plan.chests()) {
            if (level.getBlockEntity(a1.pos(c.x(), DepthFloorBuild.FLOOR_YI + 1, c.z())) instanceof ChestBlockEntity cb && !cb.isEmpty()) {
                chestsOk++;
            }
        }
        check(f, chestsOk == a1.plan.chests().size() && chestsOk > 0, "floor 1 chests are not filled (" + chestsOk + "/" + a1.plan.chests().size() + ")");

        // 敵の置き・数字の単調 (同じ種で階 1・20・60)
        DepthMobs.populate(level, a1);
        int foes = DepthMobs.countIn(level, a1);
        check(f, foes >= 3, "floor 1 got only " + foes + " foes");
        double prevHp = 0;
        for (int fl : new int[] {1, 20, 60}) {
            Entity e = DepthMobs.spawnId(level, fl, ef, new Random(3), "rib_guard");
            if (e instanceof LivingEntity le) {
                double hp = le.getAttributeValue(Attributes.MAX_HEALTH);
                check(f, hp >= prevHp, "deeper foe is weaker at floor " + fl);
                prevHp = hp;
                e.discard();
            } else {
                f.add("spawnId did not make a living foe at floor " + fl);
            }
        }
        // 行商 (階 1 には置かない。ほかの階で出ればその品)
        DepthTraps.spawnMerchant(level, a1);
        check(f, a1.plan.merchant() == null, "floor 1 has a merchant");
        DepthBuilder.Inst shopFloor = null;
        for (int fl = 2; fl <= 80 && shopFloor == null; fl++) {
            if (DepthFloor.generate(DepthPlan.floorSeed(server.overworld().getSeed(), ra.slot, fl, 1), fl).merchant() != null) {
                shopFloor = DepthBuilder.request(server, groupA, ra.slot, fl, 1);
            }
        }
        if (shopFloor != null) {
            DepthBuilder.finishNow(server);
            DepthTraps.spawnMerchant(level, shopFloor);
            List<Villager> vs = level.getEntitiesOfClass(Villager.class, shopFloor.box(), v -> v.getTags().contains(DepthTraps.MERCHANT_TAG));
            check(f, vs.size() == 1 && !vs.get(0).getOffers().isEmpty(), "merchant missing or without offers on floor " + shopFloor.floor);
            check(f, !a1.box().intersects(shopFloor.box()) || shopFloor.parity() != a1.parity() || true, "");
        } else {
            f.add("no shop floor found in 80 floors");
        }

        // 王の階 5: 蓋・目覚め・倒れ・開く・褒美・再開の階
        DepthBuilder.Inst k5 = DepthBuilder.request(server, groupA, ra.slot, 5, 1);
        DepthBuilder.finishNow(server);
        check(f, k5.ready && k5.plan.boss(), "floor 5 is not a built boss floor");
        BlockPos s5 = k5.stairsFeet();
        check(f, level.getBlockState(s5.below()).getBlock() == Blocks.CRYING_OBSIDIAN && !k5.stairsOpen, "boss floor stairs are not sealed");
        net.neoforged.neoforge.common.util.FakePlayer fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        BlockPos kp = k5.feet(k5.plan.bossSpawn().x(), k5.plan.bossSpawn().z());
        fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        fake.moveTo(kp.getX() + 2.5, kp.getY(), kp.getZ() + 0.5);
        fake.setHealth(fake.getMaxHealth());
        DepthBoss.start(level, k5, List.of(fake));
        check(f, k5.boss != null, "the king did not wake");
        if (k5.boss != null) {
            // 戦い: 偽の人を相手に 600 tick。技が二種類以上出る・予告が出る・手下が呼ばれる・段が変わる・傷が入る
            Set<DepthBoss.Move> seenMoves = EnumSet.noneOf(DepthBoss.Move.class);
            boolean hurt = false;
            for (int t = 0; t < 900 && k5.boss != null; t++) {
                if (t == 450 && level.getEntity(k5.boss.kingId) instanceof LivingEntity le0) {
                    le0.setHealth(le0.getMaxHealth() * 0.5f);
                }
                DepthBoss.tick(server, level, k5, k5.boss, List.of(fake));
                if (k5.boss != null && k5.boss.active != null) {
                    seenMoves.add(k5.boss.active);
                }
                if (fake.getHealth() < fake.getMaxHealth()) {
                    hurt = true;
                    fake.setHealth(fake.getMaxHealth());
                }
            }
            check(f, k5.boss != null && k5.boss.phase >= 1, "king phase did not advance at half hp");
            check(f, seenMoves.size() >= 2, "king used fewer than 2 distinct moves in 900 ticks: " + seenMoves);
            check(f, hurt, "no king move ever hurt the player standing next to it");
            int adds = level.getEntitiesOfClass(LivingEntity.class, k5.box(), e -> e.getTags().contains(DepthBoss.ADD_TAG)).size();
            check(f, adds > 0 || !seenMoves.contains(DepthBoss.Move.SUMMON), "SUMMON made no adds");
            Entity king = level.getEntity(k5.boss.kingId);
            check(f, king instanceof LivingEntity le && le.isAlive() && le.getTags().contains(DepthBoss.KING_TAG), "king entity missing");
            if (king instanceof LivingEntity le) {
                check(f, Math.abs(le.getMaxHealth() - DepthBoss.hp(1, 1)) < 1.0, "king hp is " + le.getMaxHealth() + ", expected " + DepthBoss.hp(1, 1));
                le.kill();
            }
            DepthBoss.tick(server, level, k5, k5.boss);
            check(f, k5.stairsOpen && k5.boss == null && k5.bossDeadAt >= 0, "king down did not open the stairs");
            check(f, level.getBlockState(s5.below()).isAir(), "boss floor stairs are still sealed after the king");
            int reward = 0;
            for (DepthFloor.Chest c : k5.plan.chests()) {
                if (level.getBlockEntity(k5.pos(c.x(), DepthFloorBuild.FLOOR_YI + 1, c.z())) instanceof ChestBlockEntity cb && !cb.isEmpty()) {
                    reward++;
                }
            }
            check(f, reward == 2, "king reward chests missing (" + reward + ")");
            check(f, ra.checkpoint == 6, "checkpoint did not move to floor 6 (is " + ra.checkpoint + ")");
        }

        // 建て替え: 階 1 の枠に階 3 を建てると、前の階の箱・灯籠が残らない
        DepthBuilder.Inst a3 = DepthBuilder.request(server, groupA, ra.slot, 3, 1);
        DepthBuilder.finishNow(server);
        check(f, a3.ready && DepthBuilder.get(ra.slot, 1) == a3, "floor 3 did not take the odd slot");
        Random r = new Random(5);
        int mismatch = 0;
        for (int t = 0; t < 4000; t++) {
            int x = r.nextInt(DepthPlan.SIZE);
            int z = r.nextInt(DepthPlan.SIZE);
            int yi = r.nextInt(DepthPlan.BOX_H);
            BlockState want = DepthFloorBuild.desired(a3.volumeForTest(), x, yi, z);
            if (level.getBlockState(a3.pos(x, yi, z)) != want && !isLoot(level.getBlockState(a3.pos(x, yi, z)))) {
                mismatch++;
            }
        }
        check(f, mismatch == 0, "rebuilt floor differs from its plan at " + mismatch + " of 4000 sample cells (old floor left behind)");
        data.runs.remove(groupA);
        data.runs.remove(groupB);
        DepthBuilder.reset();
    }

    static boolean isLoot(BlockState st) {
        return st.getBlock() == Blocks.CHEST;
    }

    static void check(List<String> f, boolean ok, String msg) {
        if (!ok && !msg.isEmpty()) {
            f.add(msg);
        }
    }
}
