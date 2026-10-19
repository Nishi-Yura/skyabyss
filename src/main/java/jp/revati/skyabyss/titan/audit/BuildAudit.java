package jp.revati.skyabyss.titan.audit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.VoxelShape;

// 建築の監査 (自己テスト "build audit")。できあがった建物の「階段の向き・段差・埋まり・戸・はしご」を、ブロックの並びだけから調べる。
// 世界 (WorldGrid) でも、ブロックの部品 (MapGrid: 部品の 8 通りの向き) でも同じ決まりで調べる。決まり (docs/design/08 の「建築の監査」):
//  階段 (半分=下): facing は「登っていく向き」(背の高い側)。低い側 = 入る側に床、高い側 = 次の床か次の段。
//    STAIR_REVERSED  低い側が壁で高い側が空いている / 高い側の床が低い側より低い (逆向き)
//    STAIR_DROP      高い側が奈落 (床がない) に面している
//    STAIR_GAP       低い側の床が段の下端より 1 以上下 (最初の一段が高すぎる)
//    STAIR_BURIED    低い側も高い側も同じ高さが丸ごとのブロック (床に埋まっている)
//    STAIR_RUN       登りの列の途中が別の向き / 登りの次の段が逆向き / 並びの隣が逆向き
//    STAIR_UPSIDE    逆さの階段 (上半分) の上に立てる (段として使われている)
//  戸: DOOR_HALF (上下が揃わない)、DOOR_FLOATING (足もとが空)、DOOR_BLOCKED (戸の先が壁)、DOOR_SILL (敷居が床より 1 高い)、DOOR_BURIED (床が敷居より高い)、
//      DOOR_VOID (戸の先が奈落)、DOOR_HINGE (並んだ戸の蝶番が同じ側)
//  はしご: LADDER_SUPPORT (支えが無い)、LADDER_TOP (上に出口が無い)、LADDER_BOTTOM (下に降り口が無い)
public final class BuildAudit {
    private BuildAudit() {
    }

    // ---------------------------------------------------------------- 結果
    public record Finding(String structure, String kind, BlockPos pos, String message) {
        @Override
        public String toString() {
            return structure + " " + kind + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ": " + message;
        }
    }

    // わざと許す例 (理由を書く)。structure は前方一致、kind は "*" なら全部。範囲は箱 (null なら全体)
    public record Allow(String structure, String kind, BackReach.Box box, String reason) {
        boolean matches(Finding f) {
            return f.structure().startsWith(structure) && (kind.equals("*") || kind.equals(f.kind())) && (box == null || box.contains(f.pos()));
        }
    }

    public static final class Report {
        public final List<Finding> findings = new ArrayList<>();
        public final List<String> suppressed = new ArrayList<>();
        public final List<Allow> allow = new ArrayList<>();
        public int scanned;

        public void add(Finding f) {
            for (Allow a : allow) {
                if (a.matches(f)) {
                    suppressed.add(f + " [allowed: " + a.reason() + "]");
                    return;
                }
            }
            findings.add(f);
            jp.revati.skyabyss.SkyAbyss.LOGGER.warn("BUILD AUDIT FINDING {}", f);
        }

        public void addAll(Report other) {
            for (Finding f : other.findings) {
                boolean skipped = false;
                for (Allow a : allow) {
                    if (a.matches(f)) {
                        suppressed.add(f + " [allowed: " + a.reason() + "]");
                        skipped = true;
                        break;
                    }
                }
                if (!skipped) {
                    findings.add(f);
                }
            }
            suppressed.addAll(other.suppressed);
            scanned += other.scanned;
        }

        public Map<String, Integer> counts() {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (Finding f : findings) {
                m.merge(f.structure() + " " + f.kind(), 1, Integer::sum);
            }
            return m;
        }
    }

    // ---------------------------------------------------------------- 格子 (世界でも部品でも)
    public abstract static class Grid {
        public abstract BlockState get(int x, int y, int z);

        // 階段・戸・はしごのある所を box の中から列挙する
        public abstract void candidates(BackReach.Box box, BiConsumer<BlockPos, BlockState> out);

        public final BlockState get(BlockPos p) {
            return get(p.getX(), p.getY(), p.getZ());
        }
    }

    static boolean candidate(BlockState s) {
        Block b = s.getBlock();
        return b instanceof StairBlock || b instanceof DoorBlock || b instanceof LadderBlock;
    }

    // 本物の世界。チャンクの区画ごとに、階段・戸・はしごを含む区画だけ調べる
    public static final class WorldGrid extends Grid {
        private final ServerLevel level;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        public WorldGrid(ServerLevel level) {
            this.level = level;
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return level.getBlockState(m.set(x, y, z));
        }

        @Override
        public void candidates(BackReach.Box box, BiConsumer<BlockPos, BlockState> out) {
            int minY = Math.max(box.y1(), level.getMinBuildHeight());
            int maxY = Math.min(box.y2(), level.getMaxBuildHeight() - 1);
            for (int cx = box.x1() >> 4; cx <= box.x2() >> 4; cx++) {
                for (int cz = box.z1() >> 4; cz <= box.z2() >> 4; cz++) {
                    ChunkAccess chunk = level.getChunk(cx, cz);
                    LevelChunkSection[] sections = chunk.getSections();
                    for (int i = 0; i < sections.length; i++) {
                        LevelChunkSection sec = sections[i];
                        int sy = level.getSectionYFromSectionIndex(i) << 4;
                        if (sec == null || sec.hasOnlyAir() || sy + 15 < minY || sy > maxY || !sec.maybeHas(BuildAudit::candidate)) {
                            continue;
                        }
                        for (int lx = 0; lx < 16; lx++) {
                            for (int ly = 0; ly < 16; ly++) {
                                for (int lz = 0; lz < 16; lz++) {
                                    BlockState s = sec.getBlockState(lx, ly, lz);
                                    if (!candidate(s)) {
                                        continue;
                                    }
                                    int x = (cx << 4) + lx;
                                    int y = sy + ly;
                                    int z = (cz << 4) + lz;
                                    if (box.contains(x, y, z)) {
                                        out.accept(new BlockPos(x, y, z), s);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 部品を置いた形 (y <= -1 は地面。部品の座標)
    public static final class MapGrid extends Grid {
        private final Map<Long, BlockState> cells = new HashMap<>();
        private final BlockState ground = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        private final BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        public void put(int x, int y, int z, BlockState s) {
            cells.put(BlockPos.asLong(x, y, z), s);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            BlockState s = cells.get(BlockPos.asLong(x, y, z));
            if (s != null) {
                return s;
            }
            return y <= -1 ? ground : air;
        }

        @Override
        public void candidates(BackReach.Box box, BiConsumer<BlockPos, BlockState> out) {
            for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
                if (candidate(e.getValue())) {
                    BlockPos p = BlockPos.of(e.getKey());
                    if (box.contains(p)) {
                        out.accept(p, e.getValue());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 形の道具
    private static final Map<BlockState, Double> TOP = new HashMap<>();

    // 当たりの上面の高さ (0 = 通り抜けられる)。溶岩は「通れない」とみなす
    public static double topOf(BlockState s) {
        return top(s);
    }

    static double top(BlockState s) {
        if (s.isAir()) {
            return 0;
        }
        Double c = TOP.get(s);
        if (c == null) {
            double t;
            try {
                VoxelShape sh = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                t = sh.isEmpty() ? 0 : sh.max(net.minecraft.core.Direction.Axis.Y);
            } catch (RuntimeException e) {
                t = 1;
            }
            if (t == 0 && s.getFluidState().is(FluidTags.LAVA)) {
                t = 1;
            }
            c = t;
            TOP.put(s, c);
        }
        return c;
    }

    private static boolean clear(Grid g, int x, int y, int z) {
        return top(g.get(x, y, z)) == 0;
    }

    private static boolean clear(Grid g, BlockPos p) {
        return clear(g, p.getX(), p.getY(), p.getZ());
    }

    // その柱の、立てる床の高さ (上に 2 マスの空きがある、いちばん上の当たり)。y + 1 から y - 4 まで探す。無ければ NaN
    static double surf(Grid g, int x, int y, int z) {
        for (int yy = y + 1; yy >= y - 4; yy--) {
            double t = top(g.get(x, yy, z));
            if (t > 0 && clear(g, x, yy + 1, z) && clear(g, x, yy + 2, z)) {
                return yy + t;
            }
        }
        return Double.NaN;
    }

    private static double surf(Grid g, BlockPos p) {
        return surf(g, p.getX(), p.getY(), p.getZ());
    }

    private static boolean isStair(BlockState s) {
        return s.getBlock() instanceof StairBlock;
    }

    // ---------------------------------------------------------------- 調べる
    public static void scan(Grid g, BackReach.Box box, String structure, Report out) {
        g.candidates(box, (p, s) -> {
            out.scanned++;
            Block b = s.getBlock();
            if (b instanceof StairBlock) {
                stair(g, p, s, structure, out);
            } else if (b instanceof DoorBlock) {
                door(g, p, s, structure, out);
            } else if (b instanceof LadderBlock) {
                ladder(g, p, s, structure, out);
            }
        });
    }

    private static void flag(Report out, String structure, String kind, BlockPos p, String msg) {
        out.add(new Finding(structure, kind, p.immutable(), msg));
    }

    private static String h(double v) {
        return Double.isNaN(v) ? "none" : String.valueOf(v);
    }

    // 階段の向きの約束が変わっていないか (facing = 登る向き = 背の高い側)
    public static boolean stairConventionHolds() {
        BlockState s = net.minecraft.world.level.block.Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        VoxelShape sh = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        // 東の高い側 (x 0.9..1, y 0.6..0.9) には当たりがあり、西の高さ (x 0..0.1, y 0.6..0.9) には無い
        return net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(sh, net.minecraft.world.phys.shapes.Shapes.box(0.9, 0.6, 0.1, 1, 0.9, 0.9),
                net.minecraft.world.phys.shapes.BooleanOp.AND)
                && !net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(sh, net.minecraft.world.phys.shapes.Shapes.box(0, 0.6, 0.1, 0.1, 0.9, 0.9),
                        net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    private static void stair(Grid g, BlockPos p, BlockState s, String st, Report out) {
        Half half = s.getValue(StairBlock.HALF);
        Direction f = s.getValue(StairBlock.FACING);
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        if (half == Half.TOP) {
            // 逆さの階段の上に立てるか (足もとが固く、上が空いている)
            if (top(g.get(p.below())) > 0 && clear(g, p.above()) && clear(g, p.above(2))) {
                flag(out, st, "STAIR_UPSIDE", p, "upside-down stair with open space above (stood on like a step): " + s.getBlock().getDescriptionId());
            }
            return;
        }
        if (s.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT) {
            return;
        }
        // 並びの隣が逆向き (横)
        for (Direction perp : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
            BlockState n = g.get(p.relative(perp));
            if (isStair(n) && n.getValue(StairBlock.HALF) == Half.BOTTOM && n.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT
                    && n.getValue(StairBlock.FACING) == f.getOpposite()) {
                flag(out, st, "STAIR_RUN", p, "neighbour stair at " + p.relative(perp).toShortString() + " in the same row faces the opposite way (" + f + " vs " + f.getOpposite() + ")");
            }
        }
        // 登りの次の段 (四方のどれかの 1 つ上の階段)。あれば、その向きを向いているはず (らせん階段の角も、この約束で足りる)
        List<Direction> upN = new ArrayList<>();
        List<Direction> downN = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState u = g.get(p.relative(d).above());
            if (isStair(u) && u.getValue(StairBlock.HALF) == Half.BOTTOM) {
                upN.add(d);
            }
            BlockState dn = g.get(p.relative(d).below());
            if (isStair(dn) && dn.getValue(StairBlock.HALF) == Half.BOTTOM) {
                downN.add(d);
            }
        }
        if (!upN.isEmpty()) {
            // 角: 来た向きにまっすぐ (うしろが 1 つ下の階段) なら、次の段が横へ折れていてもよい
            double behind = surf(g, p.relative(f.getOpposite()));
            boolean arrivesStraight = downN.contains(f.getOpposite()) || (clear(g, p.relative(f.getOpposite())) && !Double.isNaN(behind) && Math.abs(behind - y) <= 0.5);
            if (!upN.contains(f) && !arrivesStraight) {
                flag(out, st, "STAIR_RUN", p, "stair faces " + f + " but the next step up is toward " + upN + ", steps below at " + downN + " (" + s.getBlock().getDescriptionId() + ")");
            }
            return;
        }
        if (!downN.isEmpty() && downN.contains(f) && !downN.contains(f.getOpposite())) {
            flag(out, st, "STAIR_RUN", p, "stair faces " + f + ", toward the step below it (the run descends the way it faces)");
            return;
        }
        // 頭の上が空いている段だけ (屋根の飾りなどは調べない)
        if (!clear(g, p.above()) || !clear(g, p.above(2))) {
            return;
        }
        BlockPos lo = p.relative(f.getOpposite());
        BlockPos hi = p.relative(f);
        boolean loWall = top(g.get(lo)) >= 1 || top(g.get(lo.above())) >= 1;
        boolean hiOpen = clear(g, hi) && clear(g, hi.above());
        double sLo = surf(g, lo);
        double sHi = surf(g, hi);
        if (top(g.get(lo)) >= 1 && top(g.get(hi)) >= 1) {
            flag(out, st, "STAIR_BURIED", p, "stair is embedded in a floor: full blocks on both its low and high side (" + s.getBlock().getDescriptionId() + " facing " + f + "; low " + g.get(lo).getBlock().getDescriptionId() + ", high " + g.get(hi).getBlock().getDescriptionId() + ")");
            return;
        }
        if (loWall && hiOpen) {
            flag(out, st, "STAIR_REVERSED", p, "low side is a wall but the high side is open: facing " + f + " rises into open air");
            return;
        }
        // 登りの最後の段は、横へ降りる床 (段の背の高い側と同じ高さの丸ごとのブロック) へ出てよい
        boolean sideExit = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = p.relative(d);
            if (d != f.getOpposite() && top(g.get(n)) >= 1 && clear(g, n.above()) && clear(g, n.above(2))) {
                sideExit = true;
            }
        }
        if (sideExit && !loWall) {
            return;
        }
        if (!Double.isNaN(sLo) && !Double.isNaN(sHi) && sHi + 0.4 < sLo) {
            flag(out, st, "STAIR_REVERSED", p, "high side floor (" + h(sHi) + ") is lower than the low side floor (" + h(sLo) + "), facing " + f + nb(g, lo, hi));
            return;
        }
        if (hiOpen && Double.isNaN(sHi) && !Double.isNaN(sLo)) {
            flag(out, st, "STAIR_DROP", p, "high side (facing " + f + ") hangs over a drop, low side floor " + h(sLo) + nb(g, lo, hi));
            return;
        }
        if (!Double.isNaN(sHi) && sHi >= y + 1.75 && downN.contains(f.getOpposite())) {
            flag(out, st, "STAIR_GAP", p, "top of the run: the floor beyond the high side (" + h(sHi) + ") is a whole block above the stair's high edge (" + (y + 1) + "), facing " + f + nb(g, lo, hi));
            return;
        }
        if (clear(g, lo) && !Double.isNaN(sLo) && sLo <= y - 0.75) {
            flag(out, st, "STAIR_GAP", p, "low side floor " + h(sLo) + " is " + (y - sLo) + " below the stair (first step too high), facing " + f + nb(g, lo, hi));
        }
    }

    private static String nb(Grid g, BlockPos lo, BlockPos hi) {
        return " [lo " + g.get(lo).getBlock().getDescriptionId() + "/" + g.get(lo.below()).getBlock().getDescriptionId() + " hi " + g.get(hi).getBlock().getDescriptionId() + "/"
                + g.get(hi.below()).getBlock().getDescriptionId() + " hi+1 " + g.get(hi.above()).getBlock().getDescriptionId() + "]";
    }

    private static void door(Grid g, BlockPos p, BlockState s, String st, Report out) {
        if (s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
            // 上半分の下に下半分があるか
            BlockState below = g.get(p.below());
            if (!(below.getBlock() == s.getBlock() && below.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
                flag(out, st, "DOOR_HALF", p, "upper door half without a lower half below it");
            }
            return;
        }
        BlockState up = g.get(p.above());
        if (!(up.getBlock() == s.getBlock() && up.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER && up.getValue(DoorBlock.FACING) == s.getValue(DoorBlock.FACING)
                && up.getValue(DoorBlock.HINGE) == s.getValue(DoorBlock.HINGE) && up.getValue(DoorBlock.OPEN) == s.getValue(DoorBlock.OPEN))) {
            flag(out, st, "DOOR_HALF", p, "lower door half whose upper half is missing or different (hinge/facing/open): " + up);
        }
        if (!g.get(p.below()).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)) {
            flag(out, st, "DOOR_FLOATING", p, "nothing sturdy under the door: " + g.get(p.below()));
        }
        Direction f = s.getValue(DoorBlock.FACING);
        int y = p.getY();
        for (Direction side : new Direction[] {f, f.getOpposite()}) {
            BlockPos n = p.relative(side);
            if (!clear(g, n) || !clear(g, n.above())) {
                flag(out, st, "DOOR_BLOCKED", p, "the " + side + " side of the door (passage axis) is blocked at " + n.toShortString() + ": " + g.get(n) + " / " + g.get(n.above()));
                continue;
            }
            double sf = surf(g, n);
            if (Double.isNaN(sf)) {
                flag(out, st, "DOOR_VOID", p, "the " + side + " side of the door has no floor within 4 blocks");
            } else if (sf <= y - 0.75) {
                flag(out, st, "DOOR_SILL", p, "door sill is above the " + side + " floor (" + h(sf) + ") by " + (y - sf));
            } else if (sf >= y + 0.75) {
                flag(out, st, "DOOR_BURIED", p, "the " + side + " floor (" + h(sf) + ") is higher than the door sill (" + y + ")");
            }
        }
        // 並んだ戸 (同じ向き・同じ高さ) の蝶番は左右が逆
        for (Direction perp : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
            BlockState n = g.get(p.relative(perp));
            if (n.getBlock() instanceof DoorBlock && n.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && n.getValue(DoorBlock.FACING) == f
                    && n.getValue(DoorBlock.HINGE) == s.getValue(DoorBlock.HINGE) && perp == f.getClockWise()) {
                DoorHingeSide hs = s.getValue(DoorBlock.HINGE);
                flag(out, st, "DOOR_HINGE", p, "double door at " + p.relative(perp).toShortString() + ": both doors have the " + hs + " hinge");
            }
        }
    }

    private static void ladder(Grid g, BlockPos p, BlockState s, String st, Report out) {
        Direction f = s.getValue(LadderBlock.FACING);
        BlockPos support = p.relative(f.getOpposite());
        if (!g.get(support).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, f)) {
            flag(out, st, "LADDER_SUPPORT", p, "ladder facing " + f + " has nothing sturdy behind it at " + support.toShortString() + ": " + g.get(support));
        }
        // 列の上端: 上が同じはしごでなければ、出口 (横に床と 2 マスの空き) があること
        if (!(g.get(p.above()).getBlock() instanceof LadderBlock)) {
            boolean exit = g.get(p.above()).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (top(g.get(n)) >= 0.9 && clear(g, n.above()) && clear(g, n.above(2))) {
                    exit = true;
                }
            }
            // 上へ抜ける穴 (はしごの真上が 2 マス空いて、その横に床) も出口
            if (!exit && clear(g, p.above())) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.above().relative(d);
                    if (top(g.get(n)) >= 0.9 && clear(g, n.above()) && clear(g, n.above(2))) {
                        exit = true;
                    }
                }
            }
            if (!exit) {
                flag(out, st, "LADDER_TOP", p, "top of the ladder has no exit (no floor beside it with 2 blocks of room)");
            }
        }
        // 列の下端: 下が同じはしごでなければ、足もとが固いか水か、横に降りられる床があること
        if (!(g.get(p.below()).getBlock() instanceof LadderBlock)) {
            BlockState below = g.get(p.below());
            boolean ok = top(below) > 0 || !below.getFluidState().isEmpty() || below.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (clear(g, n) && clear(g, n.above()) && top(g.get(n.below())) >= 0.9) {
                    ok = true;
                }
            }
            if (!ok) {
                flag(out, st, "LADDER_BOTTOM", p, "bottom of the ladder floats: nothing solid below and no floor beside it");
            }
        }
    }
}
