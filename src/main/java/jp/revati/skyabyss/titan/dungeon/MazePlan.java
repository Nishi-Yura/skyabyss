package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

// 闇の水路 (光海) の「配置の計画」: 迷路の形 (FloodedMaze) から、息継ぎのくぼみ・灯の台・近道・水域の色・流れを決める。
// ブロックは置かない (置くのは MazeBuilder と AnemoneLights)。同じ迷路からは同じ計画になる。docs/design/23 の 11 章。
//
//  - 息継ぎのくぼみ: どのマスからも POCKET_REACH 以内の道のりにある (正しい道は 3 マスごと、枝道にも。入口の小部屋の下のマスは除く)
//  - 灯の台: 正しい道を 4 つに分けた境の 3 つ (くぼみのマスと出口の手前は避ける)
//  - 近道: 迷路の道のりが遠い (7〜24 マス) のに壁一枚で隣り合う 2 マスを、封印の戸で結ぶ。灯が 1・2・3 本ともるごとに一つずつ (その台までに歩く範囲の中のもの)、
//    そのグループにだけ戸が開く (戻るとき・倒れて走り直すとき・遅れてきた仲間のため。出口の戸は迂回させない)
//  - 水域: 正しい道が灯の台を越えるたびに色が変わる (入口の水域・二つ目・三つ目・出口の水域)。枝道は、つながる道の水域の色
public final class MazePlan {
    // どのマスも、この道のり (マスの数) 以内に息継ぎのくぼみがある
    public static final int POCKET_REACH = 3;
    // 正しい道のくぼみの間隔 (マス)
    public static final int PATH_POCKET_EVERY = 3;
    // 近道が結ぶ 2 マスの、迷路の道のり (マス数) の範囲。短すぎると意味がなく、長すぎると迷路が無意味になる
    public static final int CUT_MIN_DISTANCE = 7;
    public static final int CUT_MAX_DISTANCE = 24;
    public static final int ZONES = 4;

    // 近道: マス a と b (壁一枚で隣り合うが、通路ではない) を結ぶ。ka・kb は a・b がつながる正しい道の添え字、gain は結ぶ前の道のり、lit 本の灯で開く
    public record Cut(int[] a, int[] b, int ka, int kb, int gain, int lit) {
    }

    public final FloodedMaze maze;
    public final List<int[]> path;
    private final boolean[][] pocket;
    // 全部のくぼみのマス (正しい道の順のあとに枝道)
    public final List<int[]> pockets = new ArrayList<>();
    // うち正しい道の上のもの (道の順)
    public final List<int[]> pathPockets = new ArrayList<>();
    // 灯の台の、正しい道の上の番号 (path の添え字)
    public final int[] standK;
    public final List<Cut> cuts = new ArrayList<>();
    // 水域 (0〜3) と、そのマスがつながる正しい道の添え字
    private final int[][] zone;
    private final int[][] attach;
    // 下へ引く流れを置く辺 (path[k] → path[k+1] の k)
    public final List<Integer> currents = new ArrayList<>();

    private MazePlan(FloodedMaze maze) {
        this.maze = maze;
        this.path = maze.solution();
        this.pocket = new boolean[maze.width()][maze.height()];
        this.zone = new int[maze.width()][maze.height()];
        this.attach = new int[maze.width()][maze.height()];
        int n = path.size();
        planPockets();
        this.standK = planStands(n);
        planZones();
        planCuts(n);
        for (int k = 2; k <= n - 5; k++) {
            if (k % 6 == 2 && !isPocket(path.get(k)) && !isPocket(path.get(k + 1))) {
                currents.add(k);
            }
        }
    }

    public static MazePlan compute(FloodedMaze maze) {
        return new MazePlan(maze);
    }

    // ---------------------------------------------------------------- 部品

    public boolean isPocket(int[] c) {
        return pocket[c[0]][c[1]];
    }

    public int zoneOf(int i, int j) {
        return zone[i][j];
    }

    // 部屋の下のマスにはくぼみを掘れない (部屋の床が迷路の天井の石と同じ高さ)
    static boolean underRoom(int i, int j) {
        BlockPos c = mazeCell(i, j);
        for (TitanBackLayout.Box room : List.of(MAZE_ENTRY_ROOM, MAZE_ENTRY_ROOM2, MAZE_EXIT_ROOM)) {
            boolean x = c.getX() + MAZE_CELL + 1 >= room.x1() - 1 && c.getX() - 1 <= room.x2() + 1;
            boolean z = c.getZ() + MAZE_CELL + 1 >= room.z1() - 1 && c.getZ() - 1 <= room.z2() + 1;
            if (x && z) {
                return true;
            }
        }
        return false;
    }

    private boolean allowedPocket(int[] c, int n) {
        int k = pathIndex(c);
        if (k == 0 || (k >= 0 && k >= n - 3)) {
            return false;
        }
        return maze.links(c[0], c[1]) >= 2 && !underRoom(c[0], c[1]) && !(c[0] == MAZE_EXIT_I && c[1] == MAZE_EXIT_J);
    }

    // 正しい道の添え字 (道の上でなければ -1)
    public int pathIndex(int[] c) {
        for (int k = 0; k < path.size(); k++) {
            if (path.get(k)[0] == c[0] && path.get(k)[1] == c[1]) {
                return k;
            }
        }
        return -1;
    }

    private void planPockets() {
        int n = path.size();
        int last = 0;
        for (int k = 1; k < n - 3; k++) {
            int[] c = path.get(k);
            if (k - last >= PATH_POCKET_EVERY && allowedPocket(c, n)) {
                pocket[c[0]][c[1]] = true;
                pockets.add(c);
                pathPockets.add(c);
                last = k;
            }
        }
        // 枝道: どのマスも POCKET_REACH 以内にくぼみがあるように、入口から広げて足す
        int w = maze.width();
        int h = maze.height();
        int[][] dist = new int[w][h];
        for (int[] row : dist) {
            java.util.Arrays.fill(row, Integer.MAX_VALUE / 2);
        }
        List<int[]> sources = new ArrayList<>(pathPockets);
        sources.add(path.get(0));
        for (int[] s : sources) {
            relax(dist, s, 0);
        }
        int[][][] parent = new int[w][h][];
        List<int[]> order = bfsOrder(parent);
        for (int[] c : order) {
            if (dist[c[0]][c[1]] <= POCKET_REACH) {
                continue;
            }
            int[] pick = c;
            while (pick != null && !allowedPocket(pick, n)) {
                pick = parent[pick[0]][pick[1]];
            }
            if (pick == null || pocket[pick[0]][pick[1]] || dist[pick[0]][pick[1]] < 2) {
                continue;
            }
            pocket[pick[0]][pick[1]] = true;
            pockets.add(pick);
            relax(dist, pick, 0);
        }
    }

    // c から d 歩のところまで、道のりの最小を更新する
    private void relax(int[][] dist, int[] c, int d) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[c[0]][c[1]] = Math.min(dist[c[0]][c[1]], d);
        q.add(new int[]{c[0], c[1], d});
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[2] >= 6) {
                continue;
            }
            for (int[] dir : FloodedMaze.DIRS) {
                if (maze.linked(cur[0], cur[1], dir[0], dir[1])) {
                    int ni = cur[0] + dir[0];
                    int nj = cur[1] + dir[1];
                    if (cur[2] + 1 < dist[ni][nj]) {
                        dist[ni][nj] = cur[2] + 1;
                        q.add(new int[]{ni, nj, cur[2] + 1});
                    }
                }
            }
        }
    }

    // 入口からの幅優先の順。parent に、入口へ向かう一つ手前のマスを入れる
    private List<int[]> bfsOrder(int[][][] parent) {
        List<int[]> order = new ArrayList<>();
        boolean[][] seen = new boolean[maze.width()][maze.height()];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int[] s = path.get(0);
        q.add(s);
        seen[s[0]][s[1]] = true;
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            order.add(cur);
            for (int[] dir : FloodedMaze.DIRS) {
                if (maze.linked(cur[0], cur[1], dir[0], dir[1]) && !seen[cur[0] + dir[0]][cur[1] + dir[1]]) {
                    int[] nx = {cur[0] + dir[0], cur[1] + dir[1]};
                    seen[nx[0]][nx[1]] = true;
                    parent[nx[0]][nx[1]] = cur;
                    q.add(nx);
                }
            }
        }
        return order;
    }

    private int[] planStands(int n) {
        int count = AnemoneLights.STANDS;
        int[] result = new int[count];
        int prev = 0;
        for (int i = 0; i < count; i++) {
            int target = Math.round((n - 1) * (i + 1f) / (count + 1f));
            int best = -1;
            for (int off = 0; off < n && best < 0; off++) {
                for (int sign : new int[]{1, -1}) {
                    int k = target + off * sign;
                    if (k > prev && k >= 1 && k <= n - 4 && !isPocket(path.get(k))) {
                        best = k;
                        break;
                    }
                }
            }
            if (best < 0) {
                best = prev + 1;
            }
            result[i] = best;
            prev = best;
        }
        return result;
    }

    private void planZones() {
        // 正しい道から枝へ: つながる道の添え字を伝える
        int w = maze.width();
        int h = maze.height();
        for (int[] row : attach) {
            java.util.Arrays.fill(row, -1);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int k = 0; k < path.size(); k++) {
            int[] c = path.get(k);
            attach[c[0]][c[1]] = k;
            q.add(c);
        }
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int[] dir : FloodedMaze.DIRS) {
                int ni = cur[0] + dir[0];
                int nj = cur[1] + dir[1];
                if (maze.linked(cur[0], cur[1], dir[0], dir[1]) && attach[ni][nj] < 0) {
                    attach[ni][nj] = attach[cur[0]][cur[1]];
                    q.add(new int[]{ni, nj});
                }
            }
        }
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int z = 0;
                for (int k : standK) {
                    if (k < attach[i][j]) {
                        z++;
                    }
                }
                zone[i][j] = z;
            }
        }
    }

    // 近道: 壁一枚で隣り合う 2 マスで、迷路の道のりが CUT_MIN〜CUT_MAX マス離れているもの (戻る・走り直すときの近道)。
    // 灯が L 本ともるときに開く近道は、「L 番目の台までに歩く範囲」(正しい道の添え字が台 L-1 の先から台 L まで。L = 3 は出口の戸の手前まで) の中のもの。
    // 出口のマスと、出口の手前 3 マス、入口のマス、くぼみのマスには掛けない (出口の戸を迂回させない)
    private void planCuts(int n) {
        Set<Integer> bad = new HashSet<>();
        for (int k = Math.max(0, n - 3); k < n; k++) {
            bad.add(path.get(k)[0] * maze.height() + path.get(k)[1]);
        }
        bad.add(path.get(0)[0] * maze.height() + path.get(0)[1]);
        record Cand(int[] a, int[] b, int gain, int far) {
        }
        List<Cand> cands = new ArrayList<>();
        for (int i = 0; i < maze.width(); i++) {
            for (int j = 0; j < maze.height(); j++) {
                int[] a = {i, j};
                if (isPocket(a) || bad.contains(i * maze.height() + j)) {
                    continue;
                }
                int[][] da = null;
                for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                    int[] b = {i + d[0], j + d[1]};
                    if (b[0] >= maze.width() || b[1] >= maze.height() || isPocket(b) || bad.contains(b[0] * maze.height() + b[1])
                            || maze.linked(i, j, d[0], d[1])) {
                        continue;
                    }
                    if (da == null) {
                        da = bfs(a);
                    }
                    int g = da[b[0]][b[1]];
                    if (g >= CUT_MIN_DISTANCE && g <= CUT_MAX_DISTANCE) {
                        cands.add(new Cand(a, b, g, Math.max(attach[a[0]][a[1]], attach[b[0]][b[1]])));
                    }
                }
            }
        }
        Set<Integer> used = new HashSet<>();
        for (int lit = 1; lit <= standK.length; lit++) {
            int hi = lit < standK.length ? standK[lit - 1] : n - 4;
            int lo = lit > 1 ? standK[lit - 2] : -1;
            Cand best = null;
            for (Cand c : cands) {
                if (used.contains(c.a()[0] * maze.height() + c.a()[1]) || used.contains(c.b()[0] * maze.height() + c.b()[1])) {
                    continue;
                }
                if (c.far() <= hi && c.far() > lo && (best == null || c.gain() > best.gain())) {
                    best = c;
                }
            }
            if (best != null) {
                used.add(best.a()[0] * maze.height() + best.a()[1]);
                used.add(best.b()[0] * maze.height() + best.b()[1]);
                cuts.add(new Cut(best.a(), best.b(), attach[best.a()[0]][best.a()[1]], attach[best.b()[0]][best.b()[1]], best.gain(), lit));
            }
        }
    }

    // ---------------------------------------------------------------- 場所

    // マスの真ん中、水の一段目
    public static BlockPos center(int[] c) {
        return mazeCell(c[0], c[1]).offset(MAZE_CELL / 2, 0, MAZE_CELL / 2);
    }

    // a から b (隣のマス) へ向かう通路 (壁の切れ目) の 1×3 の戸の場所。y は水の三段
    public static List<BlockPos> passage(int[] a, int[] b) {
        BlockPos ca = mazeCell(a[0], a[1]);
        int dx = b[0] - a[0];
        int dz = b[1] - a[1];
        List<BlockPos> list = new ArrayList<>();
        for (int t = MAZE_CELL / 2; t <= MAZE_CELL / 2; t++) {
            int x = dx != 0 ? ca.getX() + (dx > 0 ? MAZE_CELL : -1) : ca.getX() + t;
            int z = dz != 0 ? ca.getZ() + (dz > 0 ? MAZE_CELL : -1) : ca.getZ() + t;
            for (int y = ca.getY(); y <= ca.getY() + 2; y++) {
                list.add(new BlockPos(x, y, z));
            }
        }
        return list;
    }

    // 通路の床の真ん中 (壁の切れ目の足もと)
    public static BlockPos passageFloor(int[] a, int[] b) {
        BlockPos ca = mazeCell(a[0], a[1]);
        int dx = b[0] - a[0];
        int dz = b[1] - a[1];
        int x = dx != 0 ? ca.getX() + (dx > 0 ? MAZE_CELL : -1) : ca.getX() + MAZE_CELL / 2;
        int z = dz != 0 ? ca.getZ() + (dz > 0 ? MAZE_CELL : -1) : ca.getZ() + MAZE_CELL / 2;
        return new BlockPos(x, MAZE_FLOOR, z);
    }

    public List<BlockPos> cutBlocks(Cut cut) {
        return passage(cut.a(), cut.b());
    }

    // 位置 (x, z) のマス (壁の上なら近いほう)
    public static int[] cellAt(double x, double z) {
        int i = Math.floorDiv((int) Math.floor(x) - (MAZE_X0 + 1), MAZE_CELL + 1);
        int j = Math.floorDiv((int) Math.floor(z) - (MAZE_Z0 + 1), MAZE_CELL + 1);
        return new int[]{Math.max(0, Math.min(MAZE_W - 1, i)), Math.max(0, Math.min(MAZE_H - 1, j))};
    }

    // from から to へ、迷路の通路を通る道のり (マス数)。つながっていなければ -1
    public int distance(int[] from, int[] to) {
        int[][] d = bfs(to);
        return d[from[0]][from[1]];
    }

    // from から to へ向かう次のマス (同じマスなら null)
    public int[] nextStep(int[] from, int[] to) {
        if (from[0] == to[0] && from[1] == to[1]) {
            return null;
        }
        int[][] d = bfs(to);
        int[] best = null;
        for (int[] dir : FloodedMaze.DIRS) {
            if (maze.linked(from[0], from[1], dir[0], dir[1])) {
                int ni = from[0] + dir[0];
                int nj = from[1] + dir[1];
                if (d[ni][nj] >= 0 && d[ni][nj] < d[from[0]][from[1]] && (best == null || d[ni][nj] < d[best[0]][best[1]])) {
                    best = new int[]{ni, nj};
                }
            }
        }
        return best;
    }

    private int[][] bfs(int[] to) {
        return distanceMap(List.of(to));
    }

    // 複数のマスからの、迷路の通路を通る道のり (マス数) の最小。どのマスからも届かなければ -1
    public int[][] distanceMap(List<int[]> sources) {
        int[][] d = new int[maze.width()][maze.height()];
        for (int[] row : d) {
            java.util.Arrays.fill(row, -1);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int[] to : sources) {
            d[to[0]][to[1]] = 0;
            q.add(to);
        }
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int[] dir : FloodedMaze.DIRS) {
                int ni = cur[0] + dir[0];
                int nj = cur[1] + dir[1];
                if (maze.linked(cur[0], cur[1], dir[0], dir[1]) && d[ni][nj] < 0) {
                    d[ni][nj] = d[cur[0]][cur[1]] + 1;
                    q.add(new int[]{ni, nj});
                }
            }
        }
        return d;
    }
}
