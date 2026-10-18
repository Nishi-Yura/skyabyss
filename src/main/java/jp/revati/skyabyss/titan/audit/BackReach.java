package jp.revati.skyabyss.titan.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

// 背中の「詰み」の監査 (docs/design/08 の背中の決まり)。四つの背 (岩鯨・潮殻・雷翼・余燼) を建てたあと、同じ決まりで調べる:
//  (a) 歩ける床の中の一マスの穴 (床の抜け・一マス幅の溝) を探す。落ちた先は (c) で見る
//  (b) はしご・つた (登れるブロック) の縦の列ごとに、上と下に立てる出口があるか、支えが開く戸ではないか
//  (c) 入口から歩く・跳ぶ・登る・泳ぐ・落ちる (3 マスまでは普通、12 マスまでは痛い) で行ける所をたどり、
//      目標 (部屋・休み場・碑・心臓の間) にぜんぶ着くこと、行ける所のどこからも入口か出口へ戻れること (一方通行の落とし穴が無いこと)
// 戸 (封印の扉) は、開けたグループにとって開いているものとして通れる。わざと作った一方通行は Spec.allowTrap に箱で書く (理由は設計書 08 の「詰み監査」)
public final class BackReach {
    private BackReach() {
    }

    // 範囲の箱 (両端を含む)
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        public boolean contains(BlockPos p) {
            return contains(p.getX(), p.getY(), p.getZ());
        }

        public boolean contains(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        @Override
        public String toString() {
            return "[" + x1 + "," + y1 + "," + z1 + " .. " + x2 + "," + y2 + "," + z2 + "]";
        }
    }

    // 調べ方の指定
    public static final class Spec {
        public final String name;
        public final Box bounds;
        // 入口 (到着して立つ所)。ここから歩き始める
        public final List<BlockPos> entrances = new ArrayList<>();
        // 出口 (地上へ戻れる所。入口もふくむ)。行ける所のどこからもここへ戻れること
        public final List<BlockPos> exits = new ArrayList<>();
        // 着くべき所 (立つ位置)。名前 → 位置。上下 1・横 2 ブロック以内に立てる所があればよい
        public final Map<String, BlockPos> goals = new LinkedHashMap<>();
        // 一方通行の落とし穴として許す範囲 (わざとの落下。理由を書く)
        public final List<Box> allowTrap = new ArrayList<>();
        // 穴の検査から外す範囲 (落ちたら休み場へ戻される谷など。理由を書く)
        public final List<Box> allowHole = new ArrayList<>();
        // 穴・はしごを調べる範囲 (空なら bounds 全体)
        public final List<Box> scan = new ArrayList<>();
        // 何マス落ちても平気な所 (滑空・水など。位置で判断)
        public Predicate<BlockPos> glide = p -> false;
        // 歩く・跳ぶ・登る以外の道 (昇り風・泡の柱など)。立つ位置 p から行ける立つ位置を out に足す
        public BiConsumer<Probe, Extra> extra = (probe, out) -> {
        };

        public Spec(String name, Box bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }

    // extra から呼ぶ道の出し先
    public interface Extra {
        void add(BlockPos to);
    }

    // 調べる部品を外へ見せる窓 (extra が使う)
    public interface Probe {
        BlockPos pos();

        boolean valid(BlockPos p);

        boolean passable(BlockPos p);
    }

    public static final class Report {
        public final List<String> failures = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public int nodes;
        public int edges;
        public int roughDrops;
        public int ladders;
        public int holes;
        public int traps;
    }

    // 落ちて痛くない (3 マスまで)、落ちられる (12 マスまで)
    static final int SAFE_DROP = 3;
    static final int MAX_DROP = 12;
    static final int MAX_NODES = 5_000_000;

    private static final byte PASS = 1, SUPPORT = 2, CLIMB = 4, WATER = 8, LAVA = 16, GATE = 32, KNOWN = 64;

    public static Report run(ServerLevel level, Spec spec) {
        return new Audit(level, spec).run();
    }

    private static final class Audit {
        final ServerLevel level;
        final Spec spec;
        final Report rep = new Report();
        final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();
        final LongOpenHashSet forward = new LongOpenHashSet();
        final LongArrayList edgeFrom = new LongArrayList();
        final LongArrayList edgeTo = new LongArrayList();
        final LongOpenHashSet rough = new LongOpenHashSet();
        final LongArrayList roughFrom = new LongArrayList();
        final LongArrayList roughTo = new LongArrayList();
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        Audit(ServerLevel level, Spec spec) {
            this.level = level;
            this.spec = spec;
        }

        // ---------------------------------------------------------------- マスの性質
        byte info(BlockPos p) {
            long k = p.asLong();
            byte v = cache.get(k);
            if ((v & KNOWN) != 0) {
                return v;
            }
            v = compute(p);
            cache.put(k, v);
            return v;
        }

        byte compute(BlockPos p) {
            if (!spec.bounds.contains(p)) {
                return KNOWN;
            }
            BlockState s = level.getBlockState(p);
            FluidState f = s.getFluidState();
            boolean lava = f.is(FluidTags.LAVA);
            boolean water = f.is(FluidTags.WATER);
            boolean climb = s.is(BlockTags.CLIMBABLE);
            boolean gate = s.is(ModBlocks.SEALED_GATE.get());
            boolean empty;
            double top = 0;
            if (gate) {
                empty = true;
            } else {
                VoxelShape sh = s.getCollisionShape(level, p);
                empty = sh.isEmpty();
                if (!empty) {
                    top = sh.max(net.minecraft.core.Direction.Axis.Y);
                }
            }
            byte v = KNOWN;
            // 戸は開いているものとして通る。低い物 (じゅうたん・たき火・ろうそく・半ブロック) は段差として越える
            boolean door = s.is(BlockTags.DOORS);
            if (!lava && (empty || climb || door || top <= 0.6)) {
                v |= PASS;
            }
            if (!gate && !empty && !climb && !door && top >= 0.4 && top <= 1.0 && !water && !lava) {
                v |= SUPPORT;
            }
            if (climb) {
                v |= CLIMB;
            }
            if (water) {
                v |= WATER;
            }
            if (lava) {
                v |= LAVA;
            }
            if (gate) {
                v |= GATE;
            }
            return v;
        }

        boolean passable(BlockPos p) {
            return (info(p) & PASS) != 0;
        }

        boolean support(BlockPos below) {
            return (info(below) & SUPPORT) != 0;
        }

        boolean climb(BlockPos p) {
            return (info(p) & CLIMB) != 0;
        }

        boolean water(BlockPos p) {
            return (info(p) & WATER) != 0;
        }

        // 立てる (足のマスと頭のマスが通れて、足もとが固い・登れる・水)
        boolean valid(BlockPos p) {
            if (!spec.bounds.contains(p)) {
                return false;
            }
            byte i = info(p);
            if ((i & PASS) == 0 || !passable(p.above())) {
                return false;
            }
            if ((i & (CLIMB | WATER)) != 0) {
                return true;
            }
            return support(p.below());
        }

        boolean floorNode(BlockPos p) {
            return valid(p) && !climb(p) && !water(p);
        }

        // ---------------------------------------------------------------- 道
        void expand(BlockPos p, List<BlockPos> out, List<BlockPos> roughOut) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (valid(n)) {
                    out.add(n);
                    continue;
                }
                // 一段上る (跳ぶ)。頭の上も空いていること
                BlockPos up = n.above();
                if (valid(up) && passable(p.above(2))) {
                    out.add(up);
                    continue;
                }
                // 落ちる
                if (passable(n) && passable(n.above())) {
                    drop(p, n, 1, out, roughOut);
                }
            }
            byte i = info(p);
            if ((i & WATER) != 0) {
                BlockPos up = p.above();
                if (valid(up)) {
                    out.add(up);
                }
                BlockPos down = p.below();
                if (valid(down)) {
                    out.add(down);
                }
            }
            if ((i & CLIMB) != 0) {
                BlockPos up = p.above();
                if (valid(up)) {
                    out.add(up);
                }
                BlockPos down = p.below();
                if (valid(down)) {
                    out.add(down);
                } else if (passable(down)) {
                    drop(p, p, 1, out, roughOut);
                }
            }
            if (floorNode(p) || (i & CLIMB) != 0) {
                jumps(p, out);
            }
            spec.extra.accept(new ProbeImpl(p), to -> {
                if (valid(to)) {
                    out.add(to);
                }
            });
        }

        // n の真下へ、k マス目から落ちる。最初に立てるマスが落ち先
        void drop(BlockPos p, BlockPos n, int startK, List<BlockPos> out, List<BlockPos> roughOut) {
            for (int k = startK; k <= MAX_DROP + 1; k++) {
                BlockPos mm = n.below(k);
                if (!spec.bounds.contains(mm)) {
                    return;
                }
                if (valid(mm)) {
                    int depth = k;
                    boolean land = water(mm);
                    if (land || depth <= SAFE_DROP || spec.glide.test(p) || spec.glide.test(mm)) {
                        out.add(mm);
                    } else if (depth <= MAX_DROP) {
                        out.add(mm);
                        roughOut.add(mm);
                    }
                    return;
                }
                if (!passable(mm)) {
                    return;
                }
            }
        }

        // 跳んで越える (一マスや二マスの溝)。足場から足場へ。CourseRules と同じ距離
        void jumps(BlockPos p, List<BlockPos> out) {
            boolean gap = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (passable(n) && passable(n.above()) && !support(n.below()) && !valid(n)) {
                    gap = true;
                    break;
                }
            }
            if (!gap) {
                return;
            }
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double dist = Math.hypot(dx, dz);
                    if (dist < 2) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        double limit = dy == 1 ? 3.2 : dy == 0 ? 4.0 : 4.3;
                        if (dist > limit) {
                            continue;
                        }
                        BlockPos dest = p.offset(dx, dy, dz);
                        if (!valid(dest) || !floorNode(dest)) {
                            continue;
                        }
                        if (clearArc(p, dx, dz, dy)) {
                            out.add(dest);
                        }
                    }
                }
            }
        }

        boolean clearArc(BlockPos p, int dx, int dz, int dy) {
            int steps = (int) Math.ceil(Math.hypot(dx, dz) * 4);
            int lastX = p.getX();
            int lastZ = p.getZ();
            for (int s = 1; s < steps; s++) {
                double t = s / (double) steps;
                int x = (int) Math.floor(p.getX() + 0.5 + dx * t);
                int z = (int) Math.floor(p.getZ() + 0.5 + dz * t);
                if ((x == lastX && z == lastZ) || (x == p.getX() && z == p.getZ()) || (x == p.getX() + dx && z == p.getZ() + dz)) {
                    continue;
                }
                lastX = x;
                lastZ = z;
                int y = p.getY() + (dy > 0 && t > 0.4 ? dy : 0);
                BlockPos c = new BlockPos(x, y, z);
                if (!passable(c) || !passable(c.above()) || (dy > 0 && !passable(c.above(2)))) {
                    return false;
                }
            }
            return passable(p.above(2));
        }

        final class ProbeImpl implements Probe {
            final BlockPos p;

            ProbeImpl(BlockPos p) {
                this.p = p;
            }

            @Override
            public BlockPos pos() {
                return p;
            }

            @Override
            public boolean valid(BlockPos q) {
                return Audit.this.valid(q);
            }

            @Override
            public boolean passable(BlockPos q) {
                return Audit.this.passable(q);
            }
        }

        // ---------------------------------------------------------------- 本体
        Report run() {
            for (BlockPos e : spec.entrances) {
                if (!valid(e)) {
                    rep.failures.add(spec.name + ": entrance " + e + " is not a standable cell");
                    continue;
                }
                forward.add(e.asLong());
            }
            ArrayDeque<Long> queue = new ArrayDeque<>();
            forward.forEach(k -> queue.add(k));
            List<BlockPos> next = new ArrayList<>();
            List<BlockPos> roughNext = new ArrayList<>();
            while (!queue.isEmpty()) {
                if (forward.size() > MAX_NODES) {
                    rep.failures.add(spec.name + ": walk graph exceeded " + MAX_NODES + " cells (leak outside the layout?)");
                    break;
                }
                long k = queue.poll();
                BlockPos p = BlockPos.of(k);
                next.clear();
                roughNext.clear();
                expand(p, next, roughNext);
                for (BlockPos q : next) {
                    edgeFrom.add(k);
                    edgeTo.add(q.asLong());
                    if (forward.add(q.asLong())) {
                        queue.add(q.asLong());
                    }
                }
                for (BlockPos q : roughNext) {
                    roughFrom.add(k);
                    roughTo.add(q.asLong());
                }
            }
            rep.nodes = forward.size();
            rep.edges = edgeFrom.size();
            rep.roughDrops = roughFrom.size();
            checkGoals();
            checkReturn();
            checkLadders();
            checkHoles();
            rep.notes.add(spec.name + " reach: " + rep.nodes + " cells, " + rep.edges + " moves, " + rep.roughDrops + " long drops, "
                    + rep.ladders + " ladder columns");
            return rep;
        }

        // ---------------------------------------------------------------- 目標
        void checkGoals() {
            for (Map.Entry<String, BlockPos> e : spec.goals.entrySet()) {
                BlockPos g = e.getValue();
                boolean ok = false;
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (int dy = -1; dy <= 1 && !ok; dy++) {
                    for (int dx = -2; dx <= 2 && !ok; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (forward.contains(g.offset(dx, dy, dz).asLong())) {
                                ok = true;
                                break;
                            }
                        }
                    }
                }
                if (!ok) {
                    var it = forward.iterator();
                    while (it.hasNext()) {
                        BlockPos q = BlockPos.of(it.nextLong());
                        double dd = q.distSqr(g);
                        if (dd < bd) {
                            bd = dd;
                            best = q;
                        }
                    }
                    rep.failures.add(spec.name + ": goal '" + e.getKey() + "' " + g + " cannot be reached (standable " + valid(g)
                            + ", closest reached " + best + ")");
                }
            }
        }

        // ---------------------------------------------------------------- 戻れるか (一方通行の落とし穴)
        void checkReturn() {
            Long2ObjectOpenHashMap<LongArrayList> rev = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < edgeFrom.size(); i++) {
                rev.computeIfAbsent(edgeTo.getLong(i), kk -> new LongArrayList()).add(edgeFrom.getLong(i));
            }
            LongOpenHashSet back = new LongOpenHashSet();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            for (BlockPos e : spec.exits) {
                if (!forward.contains(e.asLong())) {
                    rep.failures.add(spec.name + ": exit " + e + " is not reachable from the entrance");
                    continue;
                }
                if (back.add(e.asLong())) {
                    queue.add(e.asLong());
                }
            }
            while (!queue.isEmpty()) {
                long k = queue.poll();
                LongArrayList from = rev.get(k);
                if (from == null) {
                    continue;
                }
                for (int i = 0; i < from.size(); i++) {
                    if (back.add(from.getLong(i))) {
                        queue.add(from.getLong(i));
                    }
                }
            }
            LongOpenHashSet trapped = new LongOpenHashSet();
            var it = forward.iterator();
            while (it.hasNext()) {
                long k = it.nextLong();
                if (!back.contains(k)) {
                    BlockPos p = BlockPos.of(k);
                    boolean allowed = false;
                    for (Box b : spec.allowTrap) {
                        if (b.contains(p)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        trapped.add(k);
                    }
                }
            }
            if (trapped.isEmpty()) {
                return;
            }
            // かたまりに分けて報告する (行き来できる隣どうし)
            Long2ObjectOpenHashMap<LongArrayList> adj = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < edgeFrom.size(); i++) {
                long a = edgeFrom.getLong(i);
                long b = edgeTo.getLong(i);
                if (trapped.contains(a) && trapped.contains(b)) {
                    adj.computeIfAbsent(a, kk -> new LongArrayList()).add(b);
                    adj.computeIfAbsent(b, kk -> new LongArrayList()).add(a);
                }
            }
            LongOpenHashSet seen = new LongOpenHashSet();
            int comps = 0;
            var it2 = trapped.iterator();
            while (it2.hasNext()) {
                long s = it2.nextLong();
                if (seen.contains(s)) {
                    continue;
                }
                comps++;
                int size = 0;
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                LongOpenHashSet comp = new LongOpenHashSet();
                ArrayDeque<Long> q = new ArrayDeque<>();
                q.add(s);
                seen.add(s);
                comp.add(s);
                while (!q.isEmpty()) {
                    long k = q.poll();
                    BlockPos p = BlockPos.of(k);
                    size++;
                    minX = Math.min(minX, p.getX());
                    minY = Math.min(minY, p.getY());
                    minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX());
                    maxY = Math.max(maxY, p.getY());
                    maxZ = Math.max(maxZ, p.getZ());
                    LongArrayList ns = adj.get(k);
                    if (ns != null) {
                        for (int i = 0; i < ns.size(); i++) {
                            if (seen.add(ns.getLong(i))) {
                                comp.add(ns.getLong(i));
                                q.add(ns.getLong(i));
                            }
                        }
                    }
                }
                // 入ってきた落ち口 (外から入る道)
                String entry = "";
                for (int i = 0; i < edgeFrom.size() && entry.isEmpty(); i++) {
                    if (comp.contains(edgeTo.getLong(i)) && !comp.contains(edgeFrom.getLong(i))) {
                        BlockPos a = BlockPos.of(edgeFrom.getLong(i));
                        BlockPos b = BlockPos.of(edgeTo.getLong(i));
                        entry = " entered from " + fmt(a) + " -> " + fmt(b);
                    }
                }
                if (comps <= 12) {
                    rep.failures.add(spec.name + ": one-way trap area of " + size + " cells " + new Box(minX, minY, minZ, maxX, maxY, maxZ) + entry);
                }
            }
            rep.traps = comps;
            if (comps > 12) {
                rep.failures.add(spec.name + ": ... and " + (comps - 12) + " more one-way trap areas");
            }
        }

        static String fmt(BlockPos p) {
            return p.getX() + "," + p.getY() + "," + p.getZ();
        }

        // ---------------------------------------------------------------- はしご・つた
        void checkLadders() {
            // 調べる範囲の登れるブロックを集める (歩いてたどり着けないはしごも見つける)
            LongOpenHashSet ladders = new LongOpenHashSet();
            List<Box> boxes = spec.scan.isEmpty() ? List.of(spec.bounds) : spec.scan;
            for (Box b : boxes) {
                for (int x = b.x1(); x <= b.x2(); x++) {
                    for (int z = b.z1(); z <= b.z2(); z++) {
                        for (int y = b.y1(); y <= b.y2(); y++) {
                            m.set(x, y, z);
                            if (level.getBlockState(m).is(BlockTags.CLIMBABLE)) {
                                ladders.add(m.asLong());
                            }
                        }
                    }
                }
            }
            var it = ladders.iterator();
            List<BlockPos> next = new ArrayList<>();
            List<BlockPos> roughNext = new ArrayList<>();
            while (it.hasNext()) {
                BlockPos p = BlockPos.of(it.nextLong());
                // 列の頂上と底だけ調べる
                boolean isTop = !ladders.contains(p.above().asLong());
                boolean isBottom = !ladders.contains(p.below().asLong());
                BlockState s = level.getBlockState(p);
                // 支え: 戸・崩れる骨・開く物の側面に付いていないこと
                Direction face = s.hasProperty(net.minecraft.world.level.block.LadderBlock.FACING) ? s.getValue(net.minecraft.world.level.block.LadderBlock.FACING) : null;
                if (face != null) {
                    BlockPos back = p.relative(face.getOpposite());
                    BlockState bs = level.getBlockState(back);
                    if (bs.is(ModBlocks.SEALED_GATE.get()) || bs.is(ModBlocks.CRUMBLING_BONE.get()) || !s.canSurvive(level, p)) {
                        rep.failures.add(spec.name + ": ladder at " + fmt(p) + " has no solid fixed backing (" + bs.getBlock().getDescriptionId() + " at " + fmt(back) + ")");
                    }
                }
                if (isTop) {
                    rep.ladders++;
                    next.clear();
                    roughNext.clear();
                    expand(p, next, roughNext);
                    boolean exit = false;
                    for (BlockPos q : next) {
                        if (!climb(q) && !water(q) && q.getY() >= p.getY()) {
                            exit = true;
                        }
                    }
                    if (!exit) {
                        rep.failures.add(spec.name + ": ladder top at " + fmt(p) + " has no standable exit (needs a 2-high space next to the top rung)");
                    }
                }
                if (isBottom) {
                    boolean exit = floorNode(p);
                    if (!exit) {
                        next.clear();
                        roughNext.clear();
                        expand(p, next, roughNext);
                        for (BlockPos q : next) {
                            if (!climb(q) && q.getY() <= p.getY() + 1 && q.getY() >= p.getY() - SAFE_DROP) {
                                exit = true;
                            }
                        }
                    }
                    if (!exit) {
                        rep.failures.add(spec.name + ": ladder bottom at " + fmt(p) + " has no floor or exit");
                    }
                }
            }
        }

        // ---------------------------------------------------------------- 一マスの穴
        void checkHoles() {
            List<Box> boxes = spec.scan.isEmpty() ? List.of(spec.bounds) : spec.scan;
            LongOpenHashSet reported = new LongOpenHashSet();
            var it = forward.iterator();
            int shown = 0;
            while (it.hasNext()) {
                BlockPos p = BlockPos.of(it.nextLong());
                if (!floorNode(p)) {
                    continue;
                }
                boolean inScan = false;
                for (Box b : boxes) {
                    if (b.contains(p)) {
                        inScan = true;
                        break;
                    }
                }
                if (!inScan) {
                    continue;
                }
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos c = p.relative(d);
                    if (!passable(c) || !passable(c.above()) || support(c.below()) || (info(c) & (WATER | CLIMB | LAVA)) != 0 || valid(c)) {
                        continue;
                    }
                    // c は足の高さの空きで、足もとが無い。まわりの床の数
                    int floors = 0;
                    boolean opposite = false;
                    for (Direction e : Direction.Plane.HORIZONTAL) {
                        if (floorNode(c.relative(e)) || (support(c.relative(e).below()) && passable(c.relative(e)))) {
                            floors++;
                        }
                    }
                    BlockPos far = c.relative(d);
                    opposite = floorNode(far) || valid(far.above());
                    boolean tile = floors >= 3;
                    boolean trench = !tile && opposite;
                    if (!tile && !trench) {
                        continue;
                    }
                    if (!reported.add(c.asLong())) {
                        continue;
                    }
                    boolean allowed = false;
                    for (Box b : spec.allowHole) {
                        if (b.contains(c)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (allowed) {
                        continue;
                    }
                    // 落ちた先
                    String landing = "bottomless/lethal";
                    for (int k = 1; k <= 40; k++) {
                        BlockPos mm = c.below(k);
                        if (!spec.bounds.contains(mm)) {
                            break;
                        }
                        if (valid(mm)) {
                            landing = "lands " + k + " below at " + fmt(mm) + (forward.contains(mm.asLong()) ? "" : " (cell not reachable otherwise)");
                            break;
                        }
                        if (!passable(mm)) {
                            landing = "hits " + level.getBlockState(mm).getBlock().getDescriptionId() + " at " + fmt(mm) + " (not standable)";
                            break;
                        }
                    }
                    rep.holes++;
                    if (shown++ < 40) {
                        rep.failures.add(spec.name + ": " + (tile ? "MISSING FLOOR TILE" : "1-wide gap") + " at " + fmt(c) + " beside " + fmt(p) + ", " + landing);
                    }
                }
            }
        }
    }
}
