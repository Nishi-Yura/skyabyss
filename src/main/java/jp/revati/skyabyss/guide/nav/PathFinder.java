package jp.revati.skyabyss.guide.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;

// 歩ける道の探索 (A*)。立てる場所 (足と頭が空いていて、足もとが固い。水の中は泳ぐ) の格子の上を、
// 八方向・段差 +1 まで上がる・3 までなら降りる、で歩く。溶岩・火などは通らない。水は避けるが、迂回が長ければ通る。
// 読み込まれていない所 (UNLOADED) は通れない。目印へ着けなければ、いちばん近づけた所までの道を返す (部分の道)。
// 探索は何 tick かに分けられる: step(予算) を呼ぶたびに最大その数のノードを広げる (GuideScheduler)
public final class PathFinder {
    public enum Cell {
        AIR, SOLID, WATER, HAZARD, UNLOADED
    }

    public interface Terrain {
        Cell cell(int x, int y, int z);

        // 立ち位置 (x, y, z) へ歩く手間の上乗せ (0 = なし)。都の案内 (facility/TownGuide) が、狭い路地より大通りの真ん中を選ばせるのに使う
        default float extraCost(int x, int y, int z) {
            return 0f;
        }
    }

    // nodes: 出発点から順の足の位置 (先頭 = 出発点)。reached = 目印のすぐそばまで着いた。progress = 役に立つ前進がある (見つかった扱い)
    public record Result(List<BlockPos> nodes, boolean reached, boolean progress) {
        public static final Result NONE = new Result(List.of(), false, false);
    }

    private static final double GOAL = 2.0;
    private static final float WEIGHT = 1.4f;
    private static final float WATER_COST = 3f;
    private static final float DEEP_COST = 2f;
    // これだけ近づけなければ「道なし」
    private static final double MIN_PROGRESS = 6.0;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final class Node {
        final int x;
        final int y;
        final int z;
        float g;
        float h;
        float f;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Terrain terrain;
    private final int goalX;
    private final int goalZ;
    private final int sx;
    private final int sz;
    private final int radius;
    private final int maxNodes;
    private final int maxPath;
    private final HashMap<Long, Node> nodes = new HashMap<>();
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
    private Node start;
    private Node best;
    private Node reachedNode;
    private int expansions;
    private boolean done;
    private Result result;

    public PathFinder(Terrain terrain, BlockPos from, BlockPos goal, int radius, int maxNodes, int maxPath) {
        this.terrain = terrain;
        this.goalX = goal.getX();
        this.goalZ = goal.getZ();
        this.sx = from.getX();
        this.sz = from.getZ();
        this.radius = radius;
        this.maxNodes = maxNodes;
        this.maxPath = maxPath;
        int y = snap(terrain, from.getX(), from.getY(), from.getZ());
        if (y == Integer.MIN_VALUE) {
            done = true;
            result = Result.NONE;
            return;
        }
        start = node(sx, y, sz);
        start.g = 0f;
        start.h = heuristic(sx, sz);
        start.f = WEIGHT * start.h;
        best = start;
        open.add(start);
    }

    // 足もとの立てる高さ (本人の高さから、下へ 4・上へ 1 まで探す)。なければ MIN_VALUE
    public static int snap(Terrain t, int x, int y, int z) {
        for (int dy = 0; dy >= -4; dy--) {
            if (standable(t, x, y + dy, z)) {
                return y + dy;
            }
        }
        return standable(t, x, y + 1, z) ? y + 1 : Integer.MIN_VALUE;
    }

    private static boolean passable(Cell c) {
        return c == Cell.AIR || c == Cell.WATER;
    }

    private static boolean standable(Terrain t, int x, int y, int z) {
        Cell feet = t.cell(x, y, z);
        Cell head = t.cell(x, y + 1, z);
        if (!passable(feet) || !passable(head)) {
            return false;
        }
        return feet == Cell.WATER || t.cell(x, y - 1, z) == Cell.SOLID;
    }

    private Node node(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Node n = nodes.get(key);
        if (n == null) {
            n = new Node(x, y, z);
            n.g = Float.MAX_VALUE;
            nodes.put(key, n);
        }
        return n;
    }

    private float heuristic(int x, int z) {
        double dx = Math.abs(x - goalX);
        double dz = Math.abs(z - goalZ);
        return (float) (Math.max(dx, dz) + 0.414 * Math.min(dx, dz));
    }

    public int expansions() {
        return expansions;
    }

    public boolean done() {
        return done;
    }

    // 最大 budget 個のノードを広げる。終わったら true
    public boolean step(int budget) {
        while (!done && budget-- > 0) {
            Node n = open.poll();
            if (n == null) {
                finish();
                break;
            }
            if (n.closed) {
                continue;
            }
            n.closed = true;
            expansions++;
            double dx = n.x - goalX;
            double dz = n.z - goalZ;
            if (dx * dx + dz * dz <= GOAL * GOAL) {
                reachedNode = n;
                finish();
                break;
            }
            expand(n);
            if (expansions >= maxNodes) {
                finish();
            }
        }
        return done;
    }

    private void expand(Node n) {
        for (int[] d : DIRS) {
            int nx = n.x + d[0];
            int nz = n.z + d[1];
            if (Math.max(Math.abs(nx - sx), Math.abs(nz - sz)) > radius) {
                continue;
            }
            boolean diagonal = d[0] != 0 && d[1] != 0;
            if (diagonal && !(clear(n.x + d[0], n.y, n.z) && clear(n.x, n.y, n.z + d[1]))) {
                continue;
            }
            int ny = landing(n, nx, nz);
            if (ny == Integer.MIN_VALUE) {
                continue;
            }
            float cost = diagonal ? 1.414f : 1f;
            if (ny > n.y) {
                cost += 0.6f;
            } else if (ny < n.y) {
                cost += 0.25f * (n.y - ny);
            }
            cost += terrain.extraCost(nx, ny, nz);
            if (terrain.cell(nx, ny, nz) == Cell.WATER) {
                cost += WATER_COST + (terrain.cell(nx, ny + 1, nz) == Cell.WATER ? DEEP_COST : 0f);
            }
            Node m = node(nx, ny, nz);
            if (m.closed) {
                continue;
            }
            float g = n.g + cost;
            if (g < m.g) {
                m.g = g;
                m.h = heuristic(nx, nz);
                m.f = g + WEIGHT * m.h;
                m.parent = n;
                open.add(m);
                if (m.h < best.h || (m.h == best.h && m.g < best.g)) {
                    best = m;
                }
            }
        }
    }

    // 足と頭の高さが空いている (斜めの角抜けの判定)
    private boolean clear(int x, int y, int z) {
        return passable(terrain.cell(x, y, z)) && passable(terrain.cell(x, y + 1, z));
    }

    // (nx, nz) の列で、n から歩いて着く立ち位置の高さ。なければ MIN_VALUE
    private int landing(Node n, int nx, int nz) {
        int y = n.y;
        Cell feet = terrain.cell(nx, y, nz);
        if (!passable(feet)) {
            // 段差を上る: 隣の足もとが固く (hazard は不可)、その上と、いまの列の頭の上が空いている
            if (feet == Cell.SOLID && passable(terrain.cell(n.x, y + 2, n.z)) && standable(terrain, nx, y + 1, nz)) {
                return y + 1;
            }
            return Integer.MIN_VALUE;
        }
        if (!passable(terrain.cell(nx, y + 1, nz))) {
            return Integer.MIN_VALUE;
        }
        if (standable(terrain, nx, y, nz)) {
            return y;
        }
        // 降りる (3 まで)。途中に塞ぎ・危険があれば不可
        for (int ny = y - 1; ny >= y - 3; ny--) {
            Cell c = terrain.cell(nx, ny, nz);
            if (!passable(c)) {
                return Integer.MIN_VALUE;
            }
            if (standable(terrain, nx, ny, nz)) {
                return ny;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void finish() {
        done = true;
        Node end = reachedNode != null ? reachedNode : best;
        List<BlockPos> list = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            list.add(new BlockPos(n.x, n.y, n.z));
        }
        Collections.reverse(list);
        boolean reached = reachedNode != null;
        boolean progress = reached || start.h - end.h >= MIN_PROGRESS;
        if (list.size() > maxPath) {
            list = new ArrayList<>(list.subList(0, maxPath));
        }
        result = progress ? new Result(List.copyOf(list), reached, true) : Result.NONE;
    }

    public Result result() {
        return result;
    }
}
