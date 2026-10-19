package jp.revati.skyabyss.guide.nav;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.nav.PathFinder.Cell;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

// 目的の道しるべの自己テスト (docs/design/18 の 8.2 節): 矢印の角度・歩ける道の探索 (段差・穴・壁・水・溶岩・読み込み外)・
// 予算 (1 tick のノード数・1 回の探索の上限)・結果は持ち主にだけ・やり直しの条件・パケットの詰め方
public final class GuideNavSelfTest {
    private GuideNavSelfTest() {
    }

    // 試験用の地形: 床の上面は y = 64 (y <= 63 が固い)。override で一点ずつ書き換える
    private static final class Grid implements PathFinder.Terrain {
        final Map<Long, Cell> over = new HashMap<>();
        BiPredicate<Integer, Integer> unloaded = (x, z) -> false;

        @Override
        public Cell cell(int x, int y, int z) {
            if (unloaded.test(x, z)) {
                return Cell.UNLOADED;
            }
            Cell c = over.get(BlockPos.asLong(x, y, z));
            if (c != null) {
                return c;
            }
            return y <= 63 ? Cell.SOLID : Cell.AIR;
        }

        void set(int x, int y, int z, Cell c) {
            over.put(BlockPos.asLong(x, y, z), c);
        }

        // x0..x1 × z0..z1 の柱を y0..y1 で c にする
        void box(int x0, int x1, int y0, int y1, int z0, int z1, Cell c) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, c);
                    }
                }
            }
        }

        // 床を抜く (深い穴)
        void hole(int x0, int x1, int z0, int z1) {
            box(x0, x1, -64, 63, z0, z1, Cell.AIR);
        }
    }

    private static PathFinder.Result run(Grid g, BlockPos from, BlockPos to) {
        PathFinder f = new PathFinder(g, from, to, GuideScheduler.RADIUS, GuideScheduler.MAX_NODES, GuideScheduler.MAX_PATH);
        while (!f.step(500)) {
            // 続ける
        }
        return f.result();
    }

    private static boolean passes(PathFinder.Result r, int x0, int x1, int z0, int z1) {
        for (BlockPos p : r.nodes()) {
            if (p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1) {
                return true;
            }
        }
        return false;
    }

    private static boolean continuous(PathFinder.Result r) {
        List<BlockPos> n = r.nodes();
        for (int i = 1; i < n.size(); i++) {
            BlockPos a = n.get(i - 1);
            BlockPos b = n.get(i);
            if (Math.abs(a.getX() - b.getX()) > 1 || Math.abs(a.getZ() - b.getZ()) > 1 || b.getY() - a.getY() > 1 || a.getY() - b.getY() > 3) {
                return false;
            }
        }
        return true;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            math(f);
            terrain(f);
            budget(f);
            codec(f, server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST guide nav crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST guide nav PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST guide nav FAILED: {}", m));
        }
    }

    // ---- 矢印の角度 ----
    private static void math(List<String> f) {
        // 北 = -Z、南 = +Z、西 = -X、東 = +X。yaw: 南 0、西 90、北 180 (= -180)、東 -90
        check(f, near(NavMath.arrowDegrees(0, 0, 0, -10, 180f), 0f), "facing north, target north -> straight up");
        check(f, near(NavMath.arrowDegrees(0, 0, 0, -10, 0f), 180f), "facing south, target north -> behind");
        check(f, near(NavMath.arrowDegrees(0, 0, -10, 0, 0f), 90f), "facing south, target west -> right");
        check(f, near(NavMath.arrowDegrees(0, 0, 10, 0, 0f), -90f), "facing south, target east -> left");
        check(f, near(NavMath.arrowDegrees(0, 0, -10, 0, 90f), 0f), "facing west, target west -> straight up");
        check(f, near(NavMath.arrowDegrees(5, 5, 5, 15, 0f), 0f), "facing south, target south of an offset player");
        check(f, near(NavMath.arrowDegrees(0, 0, 10, 10, 0f), -45f), "facing south, target south-east -> up-left");
        check(f, near(NavMath.arrowDegrees(0, 0, 0, -10, -180f), 0f), "yaw -180 facing north");
        check(f, near(NavMath.arrowDegrees(0, 0, 0, -10, 540f), 0f), "yaw 540 (wrapped) facing north");
        // 角度はいつも (-180, 180]
        for (float yaw = -400f; yaw <= 400f; yaw += 17f) {
            float d = NavMath.arrowDegrees(1, 2, -30, 12, yaw);
            check(f, d > -180f && d <= 180f, "arrow degrees out of range at yaw " + yaw + ": " + d);
        }
        check(f, NavMath.vertical(13) == 1 && NavMath.vertical(-13) == -1 && NavMath.vertical(12) == 0 && NavMath.vertical(-12) == 0 && NavMath.vertical(0) == 0,
                "vertical hint thresholds");
    }

    private static boolean near(float a, float b) {
        float d = Math.abs(a - b);
        return d < 0.01f || Math.abs(d - 360f) < 0.01f;
    }

    // ---- 探索 ----
    private static void terrain(List<String> f) {
        BlockPos o = new BlockPos(0, 64, 0);
        // 平ら
        Grid g = new Grid();
        PathFinder.Result r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && r.progress() && !r.nodes().isEmpty(), "flat: reaches the target");
        check(f, r.nodes().size() >= 19 && r.nodes().size() <= 24, "flat: path length " + r.nodes().size());
        check(f, continuous(r), "flat: path is continuous");
        check(f, r.nodes().stream().allMatch(p -> p.getY() == 64), "flat: stays level");
        check(f, r.nodes().get(0).equals(o), "flat: starts at the player's cell");

        // 段差 +1 は上がる
        g = new Grid();
        g.box(10, 10, 64, 64, -40, 40, Cell.SOLID);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && r.nodes().stream().anyMatch(p -> p.getY() == 65), "step of 1: climbs over it");
        check(f, continuous(r), "step of 1: continuous");

        // 2 の壁は越えない (回り込む)
        g = new Grid();
        g.box(10, 10, 64, 65, -5, 5, Cell.SOLID);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && !passes(r, 10, 10, -5, 5), "wall of 2: goes around it");
        check(f, r.reached() && r.nodes().stream().noneMatch(p -> p.getY() > 64), "wall of 2: never climbs");

        // 深い穴 (落差 > 3) は渡らない・回り込む
        g = new Grid();
        g.hole(8, 12, -3, 3);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && !passes(r, 8, 12, -3, 3), "gap: goes around the hole");
        // 落差 3 までは降りる (向こう側の床が 3 低い)。4 は降りない
        g = new Grid();
        g.box(10, 200, 61, 63, -200, 200, Cell.AIR);
        r = run(g, o, new BlockPos(20, 61, 0));
        check(f, r.reached() && r.nodes().stream().anyMatch(p -> p.getY() == 61), "drop of 3: walks down");
        check(f, continuous(r), "drop of 3: continuous");
        g = new Grid();
        g.box(10, 200, 60, 63, -200, 200, Cell.AIR);
        r = run(g, o, new BlockPos(20, 60, 0));
        check(f, !r.reached(), "drop of 4: not taken");

        // 全面の壁: 着けない (近づけた所まで)
        g = new Grid();
        g.box(10, 10, 64, 70, -120, 120, Cell.SOLID);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, !r.reached(), "full wall: does not reach");
        check(f, r.nodes().stream().allMatch(p -> p.getX() < 10), "full wall: partial path stays on this side");

        // 小さな池は避ける・広い湖は渡る
        g = new Grid();
        g.box(8, 12, 63, 63, -2, 2, Cell.WATER);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && !passesWater(g, r), "small pond: avoided");
        g = new Grid();
        g.box(8, 12, 63, 63, -150, 150, Cell.WATER);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && passesWater(g, r), "wide lake: crossed (swimming)");

        // 溶岩は決して通らない
        g = new Grid();
        g.box(8, 12, 63, 63, -2, 2, Cell.HAZARD);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, r.reached() && !passes(r, 8, 12, -2, 2), "lava patch: goes around");
        g = new Grid();
        g.box(8, 12, 63, 64, -150, 150, Cell.HAZARD);
        r = run(g, o, new BlockPos(20, 64, 0));
        check(f, !r.reached() && !passes(r, 8, 12, -150, 150), "lava river: never entered, no route");

        // 読み込まれていない所は通らない。手前までの道 (部分)
        g = new Grid();
        g.unloaded = (x, z) -> x >= 15;
        r = run(g, o, new BlockPos(40, 64, 0));
        check(f, !r.reached() && r.progress() && r.nodes().stream().allMatch(p -> p.getX() < 15), "unloaded: partial path up to the loaded edge");

        // 遠い目的: 半径 96・最大 192 点で切る (部分の道)
        g = new Grid();
        r = run(g, o, new BlockPos(400, 64, 0));
        check(f, !r.reached() && r.progress(), "far target: partial path");
        check(f, r.nodes().size() <= GuideScheduler.MAX_PATH && r.nodes().stream().allMatch(p -> Math.abs(p.getX()) <= GuideScheduler.RADIUS),
                "far target: stays within the search radius");
        check(f, TrailCodec.encodable(r.nodes()), "far target: path is encodable");

        // 空に浮かぶ目印 (巨神): 真下へ向かう
        g = new Grid();
        r = run(g, o, new BlockPos(30, 200, 10));
        check(f, r.reached(), "target in the sky: leads to the point below it");

        // 立てる所がない (空中・埋まり) 出発点は「道なし」
        g = new Grid();
        r = run(g, new BlockPos(0, 120, 0), new BlockPos(20, 64, 0));
        check(f, !r.progress() && r.nodes().isEmpty(), "start in the air: no path");
        // 1 回の探索の上限
        g = new Grid();
        g.box(5, 5, 64, 90, -130, 130, Cell.SOLID);
        g.box(-130, 130, 64, 90, 5, 5, Cell.SOLID);
        g.box(-130, 130, 64, 90, -5, -5, Cell.SOLID);
        g.box(-5, -5, 64, 90, -130, 130, Cell.SOLID);
        PathFinder pf = new PathFinder(g, o, new BlockPos(60, 64, 0), GuideScheduler.RADIUS, GuideScheduler.MAX_NODES, GuideScheduler.MAX_PATH);
        while (!pf.step(1000)) {
            // 続ける
        }
        check(f, pf.expansions() <= GuideScheduler.MAX_NODES, "box: search stops at MAX_NODES (" + pf.expansions() + ")");
        check(f, !pf.result().reached(), "box: sealed-in target is not reached");
    }

    private static boolean passesWater(Grid g, PathFinder.Result r) {
        for (BlockPos p : r.nodes()) {
            if (g.cell(p.getX(), p.getY(), p.getZ()) == Cell.WATER) {
                return true;
            }
        }
        return false;
    }

    // ---- 予算・持ち主 ----
    private static void budget(List<String> f) {
        Grid world = new Grid();
        world.box(10, 10, 64, 66, -20, 20, Cell.SOLID);
        int owners = 12;
        List<UUID> ids = new ArrayList<>();
        Map<UUID, BlockPos> pos = new HashMap<>();
        Map<UUID, BlockPos> goal = new HashMap<>();
        Map<UUID, List<GuideScheduler.Delivery>> got = new HashMap<>();
        List<UUID> bystanders = List.of(UUID.randomUUID());
        GuideScheduler<UUID> sched = new GuideScheduler<>((owner, d) -> got.computeIfAbsent(owner, k -> new ArrayList<>()).add(d));
        for (int i = 0; i < owners; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            pos.put(id, new BlockPos(-50 + i * 4, 64, i * 3 - 15));
            goal.put(id, new BlockPos(60 + i, 64, 20 - i * 2));
            sched.update(id, true, goal.get(id), 0);
        }
        GuideScheduler.Env<UUID> env = owner -> pos.containsKey(owner) ? new GuideScheduler.Where(pos.get(owner), world) : null;
        int budget = 400;
        long now = 0;
        int worst = 0;
        int ticksToAll = -1;
        for (int t = 0; t < 400; t++) {
            int used = sched.tick(now++, env, budget);
            worst = Math.max(worst, used);
            // 生きている間は知らせを続ける (30 秒の途絶えで捨てられない)
            if (now % 100 == 0) {
                beat(sched, ids, goal, now);
            }
            if (ticksToAll < 0 && got.size() == owners) {
                ticksToAll = t;
            }
        }
        check(f, worst <= budget, "budget: one tick used " + worst + " nodes (limit " + budget + ")");
        check(f, ticksToAll >= 0, "budget: every owner eventually gets a path (" + got.size() + "/" + owners + ")");
        check(f, ticksToAll > 1, "budget: the work was spread over several ticks (" + ticksToAll + ")");
        for (UUID id : ids) {
            List<GuideScheduler.Delivery> ds = got.get(id);
            if (ds == null || ds.isEmpty()) {
                continue;
            }
            GuideScheduler.Delivery d = ds.get(0);
            check(f, d.target().equals(goal.get(id)), "owner: delivery carries its own target");
            check(f, !d.nodes().isEmpty() && d.nodes().get(0).distSqr(pos.get(id)) <= 1, "owner: delivery starts at its own position");
        }
        check(f, got.keySet().stream().noneMatch(bystanders::contains) && ids.containsAll(got.keySet()), "owner: nobody else receives a trail");
        // 動かなければやり直さない (同じ結果は送らない)
        int totalBefore = got.values().stream().mapToInt(List::size).sum();
        for (int t = 0; t < 60; t++) {
            sched.tick(now++, env, budget);
            if (now % 100 == 0) {
                beat(sched, ids, goal, now);
            }
        }
        int totalAfter = got.values().stream().mapToInt(List::size).sum();
        check(f, totalAfter == totalBefore, "idle: no repeated deliveries while standing still (" + totalBefore + " -> " + totalAfter + ")");
        // 8 ブロック動いたらやり直す
        UUID mover = ids.get(0);
        int before = got.get(mover).size();
        pos.put(mover, pos.get(mover).offset(9, 0, 0));
        for (int t = 0; t < 40; t++) {
            sched.tick(now++, env, budget);
        }
        check(f, got.get(mover).size() > before, "moved 9 blocks: the path is recomputed");
        // 場所が変わったらやり直す
        int before2 = got.get(ids.get(1)).size();
        sched.update(ids.get(1), true, goal.get(ids.get(1)).offset(0, 0, 30), now);
        for (int t = 0; t < 40; t++) {
            sched.tick(now++, env, budget);
        }
        check(f, got.get(ids.get(1)).size() > before2, "new target: the path is recomputed");
        // 道しるべを止めたら送らない
        UUID quiet = ids.get(2);
        sched.update(quiet, false, null, now);
        int before3 = got.get(quiet).size();
        pos.put(quiet, pos.get(quiet).offset(20, 0, 0));
        for (int t = 0; t < 40; t++) {
            sched.tick(now++, env, budget);
        }
        check(f, got.get(quiet).size() == before3 && !sched.has(quiet), "off: nothing is computed or sent after the client turned it off");
        // 知らせが 30 秒途絶えたら捨てる
        long later = now + GuideScheduler.STALE_TICKS + 5;
        sched.tick(later, env, budget);
        check(f, sched.size() == 0, "stale: entries without a heartbeat are dropped (" + sched.size() + " left)");
        // 持ち主が探索できない (他の次元・オフライン) あいだは探さない
        GuideScheduler<UUID> s2 = new GuideScheduler<>((owner, d) -> f.add("delivery to an owner that cannot be searched"));
        UUID away = UUID.randomUUID();
        s2.update(away, true, new BlockPos(50, 64, 0), 0);
        check(f, s2.tick(1, owner -> null, 500) == 0, "offline owner: no search");
        // 着いたら空の道 (1 回)
        List<GuideScheduler.Delivery> arrived = new ArrayList<>();
        GuideScheduler<UUID> s3 = new GuideScheduler<>((owner, d) -> arrived.add(d));
        UUID near = UUID.randomUUID();
        s3.update(near, true, new BlockPos(2, 64, 1), 0);
        s3.tick(1, owner -> new GuideScheduler.Where(new BlockPos(0, 64, 0), new Grid()), 500);
        s3.tick(2, owner -> new GuideScheduler.Where(new BlockPos(0, 64, 0), new Grid()), 500);
        check(f, arrived.size() == 1 && arrived.get(0).nodes().isEmpty(), "arrived: one empty delivery");
    }

    private static void beat(GuideScheduler<UUID> sched, List<UUID> ids, Map<UUID, BlockPos> goal, long at) {
        for (UUID id : ids) {
            sched.update(id, true, goal.get(id), at);
        }
    }

    // ---- 詰め方 ----
    private static void codec(List<String> f, MinecraftServer server) {
        Grid g = new Grid();
        g.box(10, 10, 64, 64, -40, 40, Cell.SOLID);
        g.box(14, 14, 61, 63, -40, 40, Cell.AIR);
        PathFinder.Result r = run(g, new BlockPos(-3, 64, 2), new BlockPos(30, 64, -9));
        check(f, r.reached() && TrailCodec.encodable(r.nodes()), "codec: sample path is encodable");
        List<BlockPos> back = TrailCodec.decode(r.nodes().get(0), TrailCodec.steps(r.nodes()));
        check(f, back.equals(r.nodes()), "codec: steps round-trip");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        GuideNetwork.GuideTrail t = new GuideNetwork.GuideTrail(new BlockPos(30, 64, -9), true, r.nodes());
        GuideNetwork.GuideTrail.STREAM_CODEC.encode(buf, t);
        int bytes = buf.readableBytes();
        GuideNetwork.GuideTrail t2 = GuideNetwork.GuideTrail.STREAM_CODEC.decode(buf);
        check(f, t2.equals(t), "codec: trail payload round-trips");
        check(f, bytes < 40 + r.nodes().size() * 2, "codec: trail payload is compact (" + bytes + " bytes for " + r.nodes().size() + " nodes)");
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        GuideNetwork.GuideTrail empty = new GuideNetwork.GuideTrail(new BlockPos(1, 2, 3), false, List.of());
        GuideNetwork.GuideTrail.STREAM_CODEC.encode(buf, empty);
        check(f, GuideNetwork.GuideTrail.STREAM_CODEC.decode(buf).equals(empty), "codec: empty trail round-trips");
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        GuideNetwork.GuideTarget gt = new GuideNetwork.GuideTarget(true, new BlockPos(-5, 70, 9000));
        GuideNetwork.GuideTarget.STREAM_CODEC.encode(buf, gt);
        check(f, GuideNetwork.GuideTarget.STREAM_CODEC.decode(buf).equals(gt), "codec: target payload round-trips");
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
