package jp.revati.skyabyss.titan.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// 闇の水路の迷路の形 (マス目の上での、つながりだけ)。ブロックへの置き換えは MazeBuilder が行う。
// 「成長する木」の方法で掘る: 最後に掘ったマスから伸ばす (長い通路) か、途中のマスから枝分かれする (分かれ道) かを
// 半々で選ぶので、長い道と分かれ道がほどよく混ざる。どの2マスのあいだも道は一本だけ。同じ種からは同じ形になる
public final class FloodedMaze {
    public static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final int w;
    private final int h;
    // east[i][j]: (i, j) と (i+1, j) がつながっている / south[i][j]: (i, j) と (i, j+1)
    private final boolean[][] east;
    private final boolean[][] south;
    private final int[] start;
    private final int[] exit;

    private FloodedMaze(int w, int h, int[] start, int[] exit) {
        this.w = w;
        this.h = h;
        this.east = new boolean[w][h];
        this.south = new boolean[w][h];
        this.start = start;
        this.exit = exit;
    }

    public static FloodedMaze generate(int w, int h, int startI, int startJ, int exitI, int exitJ, long seed) {
        FloodedMaze maze = new FloodedMaze(w, h, new int[]{startI, startJ}, new int[]{exitI, exitJ});
        Random random = new Random(seed);
        boolean[][] visited = new boolean[w][h];
        List<int[]> active = new ArrayList<>();
        active.add(new int[]{startI, startJ});
        visited[startI][startJ] = true;
        while (!active.isEmpty()) {
            int index = random.nextBoolean() ? active.size() - 1 : random.nextInt(active.size());
            int[] cur = active.get(index);
            List<int[]> next = new ArrayList<>();
            for (int[] d : DIRS) {
                int ni = cur[0] + d[0];
                int nj = cur[1] + d[1];
                if (ni >= 0 && nj >= 0 && ni < w && nj < h && !visited[ni][nj]) {
                    next.add(new int[]{ni, nj});
                }
            }
            if (next.isEmpty()) {
                active.remove(index);
                continue;
            }
            int[] n = next.get(random.nextInt(next.size()));
            maze.link(cur[0], cur[1], n[0], n[1]);
            visited[n[0]][n[1]] = true;
            active.add(n);
        }
        return maze;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    private void link(int i, int j, int ni, int nj) {
        if (ni == i + 1) {
            east[i][j] = true;
        } else if (ni == i - 1) {
            east[ni][nj] = true;
        } else if (nj == j + 1) {
            south[i][j] = true;
        } else {
            south[ni][nj] = true;
        }
    }

    // (i, j) から (i+di, j+dj) へ道がつながっているか
    public boolean linked(int i, int j, int di, int dj) {
        int ni = i + di;
        int nj = j + dj;
        if (ni < 0 || nj < 0 || ni >= w || nj >= h) {
            return false;
        }
        if (di == 1) {
            return east[i][j];
        }
        if (di == -1) {
            return east[ni][nj];
        }
        if (dj == 1) {
            return south[i][j];
        }
        return south[ni][nj];
    }

    public int links(int i, int j) {
        int n = 0;
        for (int[] d : DIRS) {
            if (linked(i, j, d[0], d[1])) {
                n++;
            }
        }
        return n;
    }

    // 入口から出口までの正しい道 (マスの並び)
    public List<int[]> solution() {
        int[][][] prev = new int[w][h][];
        boolean[][] seen = new boolean[w][h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start[0]][start[1]] = true;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] d : DIRS) {
                if (linked(cur[0], cur[1], d[0], d[1]) && !seen[cur[0] + d[0]][cur[1] + d[1]]) {
                    int[] n = {cur[0] + d[0], cur[1] + d[1]};
                    seen[n[0]][n[1]] = true;
                    prev[n[0]][n[1]] = cur;
                    queue.add(n);
                }
            }
        }
        List<int[]> path = new ArrayList<>();
        for (int[] c = exit; c != null; c = prev[c[0]][c[1]]) {
            path.add(c);
        }
        Collections.reverse(path);
        return path;
    }

    // 行き止まりのマス (入口と出口を除く)
    public List<int[]> deadEnds() {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                boolean special = (i == start[0] && j == start[1]) || (i == exit[0] && j == exit[1]);
                if (!special && links(i, j) == 1) {
                    result.add(new int[]{i, j});
                }
            }
        }
        return result;
    }
}
