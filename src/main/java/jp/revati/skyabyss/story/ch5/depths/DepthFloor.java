package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Kind;

// 階の間取り (docs/design/36 の 9)。純粋な計算 (ワールドにも Minecraft の型にも触らない)。
//   3×3 の区画に部屋 (約 85%) を置き、隣の区画どうしを通路でつなぐ (全部が一本の木でつながり、輪が一、二本できる)。
//   入口の部屋から歩いて最も遠い部屋が下り口の部屋。ほかの部屋に、休み場・行商・敵の巣・宝の間・箱・罠を割る。
//   5 階ごとは王の階: 入口の休み場 → 通路 → 広間 (中心に王、奥に下り口。王を倒すまで塞がれている)。
//   灯りは「どの床も 5 マス以内に一つ」になるまで置く。同じ種と階なら、いつも同じ間取り。
public final class DepthFloor {
    public static final int LIGHT_R = 5;

    public enum RoomType {
        NORMAL, REST, SHOP, HOUSE, TREASURE, STAIRS, BOSS
    }

    public enum TrapKind {
        SPIKE, POISON, SUMMON, BLIND, HEAVY
    }

    public record Room(int id, int x0, int z0, int w, int d, RoomType type, boolean entry) {
        public int cx() {
            return x0 + w / 2;
        }

        public int cz() {
            return z0 + d / 2;
        }

        public int area() {
            return w * d;
        }

        public boolean contains(int x, int z) {
            return x >= x0 && x < x0 + w && z >= z0 && z < z0 + d;
        }
    }

    public record Spot(int x, int z) {
        int dist(Spot o) {
            return Math.abs(x - o.x) + Math.abs(z - o.z);
        }
    }

    public record Trap(int x, int z, TrapKind kind) {
    }

    // tier: 0 ふつう・1 宝の間・2 敵の巣・3 王の褒美
    public record Chest(int x, int z, int tier) {
    }

    public record Plan(int floor, long seed, Kind kind, boolean boss, boolean[][] open, boolean[][] path, List<Room> rooms, Spot entry, Spot stairs,
            List<Trap> traps, List<Chest> chests, List<Spot> lights, Spot lantern, Spot chain, Spot merchant, Spot bossSpawn, int houseRoom) {
        public Room roomAt(int x, int z) {
            for (Room r : rooms) {
                if (r.contains(x, z)) {
                    return r;
                }
            }
            return null;
        }

        public Room entryRoom() {
            for (Room r : rooms) {
                if (r.entry()) {
                    return r;
                }
            }
            return null;
        }

        public Room first(RoomType t) {
            for (Room r : rooms) {
                if (r.type() == t) {
                    return r;
                }
            }
            return null;
        }
    }

    private DepthFloor() {
    }

    // 歩いて着く最短の歩数 (4 近傍。from から全部の床まで。着かない所は -1)
    public static int[][] distances(boolean[][] open, Spot from) {
        int n = open.length;
        int[][] dist = new int[n][n];
        for (int[] row : dist) {
            Arrays.fill(row, -1);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[from.x][from.z] = 0;
        q.add(new int[] {from.x, from.z});
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int i = 0; i < 4; i++) {
                int x = c[0] + dx[i];
                int z = c[1] + dz[i];
                if (x >= 0 && z >= 0 && x < n && z < n && open[x][z] && dist[x][z] < 0) {
                    dist[x][z] = dist[c[0]][c[1]] + 1;
                    q.add(new int[] {x, z});
                }
            }
        }
        return dist;
    }

    // 入口から全部の床 (と下り口) に歩いて着くか
    public static boolean connected(Plan p) {
        int[][] d = distances(p.open(), p.entry());
        for (int x = 0; x < d.length; x++) {
            for (int z = 0; z < d.length; z++) {
                if (p.open()[x][z] && d[x][z] < 0) {
                    return false;
                }
            }
        }
        return d[p.stairs().x][p.stairs().z] >= 0;
    }

    // どの床も、灯りから (|dx|+|dz|) で何マスか。最大を返す
    public static int maxLightDistance(Plan p) {
        int worst = 0;
        for (int x = 0; x < p.open().length; x++) {
            for (int z = 0; z < p.open().length; z++) {
                if (!p.open()[x][z]) {
                    continue;
                }
                int best = Integer.MAX_VALUE;
                for (Spot l : p.lights()) {
                    best = Math.min(best, Math.abs(l.x - x) + Math.abs(l.z - z));
                }
                worst = Math.max(worst, best);
            }
        }
        return worst;
    }

    public static Plan generate(long seed, int floor) {
        Random r = new Random(seed ^ DepthPlan.SALT ^ 0x7A3);
        Kind kind = DepthPlan.kindOf(seed, floor);
        return DepthPlan.isBoss(floor) ? boss(r, seed, floor, kind) : normal(r, seed, floor, kind);
    }

    // ---------------------------------------------------------------- 王の階
    private static Plan boss(Random r, long seed, int floor, Kind kind) {
        int n = DepthPlan.SIZE;
        boolean[][] open = new boolean[n][n];
        boolean[][] path = new boolean[n][n];
        List<Room> rooms = new ArrayList<>();
        Room entry = new Room(0, 3, 34, 9, 9, RoomType.REST, true);
        Room arena = new Room(1, 22, 23, 31, 31, RoomType.BOSS, false);
        rooms.add(entry);
        rooms.add(arena);
        carve(open, entry);
        carve(open, arena);
        for (int x = entry.x0 + entry.w; x < arena.x0 + 2; x++) {
            for (int z = 37; z <= 39; z++) {
                open[x][z] = true;
            }
        }
        for (int x = entry.x0 + entry.w - 1; x < arena.x0 + 3; x++) {
            for (int z = 36; z <= 40; z++) {
                path[x][z] = true;
            }
        }
        // 広間の四つの柱 (2×2)
        int ax = arena.cx();
        int az = arena.cz();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        open[ax + sx * 8 + dx][az + sz * 8 + dz] = false;
                    }
                }
            }
        }
        Spot stairs = new Spot(ax + 12, az);
        Spot spawn = new Spot(ax - 3, az);
        Spot lantern = new Spot(entry.cx(), entry.cz() - 2);
        Spot chain = new Spot(entry.x0 + 1, entry.z0 + 1);
        List<Chest> chests = List.of(new Chest(ax + 12, az - 5, 3), new Chest(ax + 12, az + 5, 3));
        return new Plan(floor, seed, kind, true, open, path, List.copyOf(rooms), new Spot(entry.cx(), entry.cz() + 2), stairs, List.of(), chests,
                lights(open), lantern, chain, null, spawn, -1);
    }

    // ---------------------------------------------------------------- ふつうの階
    private static Plan normal(Random r, long seed, int floor, Kind kind) {
        int g = DepthPlan.GRID;
        int cell = DepthPlan.CELL;
        int n = DepthPlan.SIZE;
        boolean[][] open = new boolean[n][n];
        boolean[][] path = new boolean[n][n];
        Room[][] cellRoom = new Room[g][g];
        List<Room> rooms = new ArrayList<>();
        int made = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int cx = 0; cx < g; cx++) {
                for (int cz = 0; cz < g; cz++) {
                    if (cellRoom[cx][cz] != null) {
                        continue;
                    }
                    boolean want = pass == 0 ? r.nextInt(100) < 85 : made < 6;
                    if (!want) {
                        continue;
                    }
                    int w = 7 + r.nextInt(9);
                    int d = 7 + r.nextInt(9);
                    int x0 = cx * cell + 2 + r.nextInt(cell - 4 - w + 1);
                    int z0 = cz * cell + 2 + r.nextInt(cell - 4 - d + 1);
                    Room room = new Room(rooms.size(), x0, z0, w, d, RoomType.NORMAL, false);
                    cellRoom[cx][cz] = room;
                    rooms.add(room);
                    carve(open, room);
                    made++;
                }
            }
        }
        // 区画の中心 (部屋の中心か、部屋のない区画の交差点)
        int[][][] node = new int[g][g][2];
        for (int cx = 0; cx < g; cx++) {
            for (int cz = 0; cz < g; cz++) {
                Room room = cellRoom[cx][cz];
                node[cx][cz][0] = room != null ? room.cx() : cx * cell + cell / 2;
                node[cx][cz][1] = room != null ? room.cz() : cz * cell + cell / 2;
                if (room == null) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            open[node[cx][cz][0] + dx][node[cx][cz][1] + dz] = true;
                            path[node[cx][cz][0] + dx][node[cx][cz][1] + dz] = true;
                        }
                    }
                }
            }
        }
        // 木 (ランダムな Prim) + 余りの辺。edge[cx][cz][0] = 右 (x+) とつなぐ、[1] = 下 (z+) とつなぐ
        boolean[][] in = new boolean[g][g];
        boolean[][][] edge = new boolean[g][g][2];
        in[r.nextInt(g)][r.nextInt(g)] = true;
        for (int step = 1; step < g * g; step++) {
            List<int[]> cand = new ArrayList<>();
            for (int cx = 0; cx < g; cx++) {
                for (int cz = 0; cz < g; cz++) {
                    if (!in[cx][cz]) {
                        continue;
                    }
                    if (cx + 1 < g && !in[cx + 1][cz]) {
                        cand.add(new int[] {cx, cz, 0});
                    }
                    if (cx > 0 && !in[cx - 1][cz]) {
                        cand.add(new int[] {cx - 1, cz, 0});
                    }
                    if (cz + 1 < g && !in[cx][cz + 1]) {
                        cand.add(new int[] {cx, cz, 1});
                    }
                    if (cz > 0 && !in[cx][cz - 1]) {
                        cand.add(new int[] {cx, cz - 1, 1});
                    }
                }
            }
            int[] e = cand.get(r.nextInt(cand.size()));
            edge[e[0]][e[1]][e[2]] = true;
            in[e[0]][e[1]] = true;
            in[e[0] + (e[2] == 0 ? 1 : 0)][e[1] + (e[2] == 1 ? 1 : 0)] = true;
        }
        int extra = 1 + r.nextInt(2);
        for (int tries = 0; tries < 20 && extra > 0; tries++) {
            int cx = r.nextInt(g);
            int cz = r.nextInt(g);
            int dir = r.nextInt(2);
            if ((dir == 0 && cx + 1 >= g) || (dir == 1 && cz + 1 >= g) || edge[cx][cz][dir]) {
                continue;
            }
            edge[cx][cz][dir] = true;
            extra--;
        }
        for (int cx = 0; cx < g; cx++) {
            for (int cz = 0; cz < g; cz++) {
                for (int dir = 0; dir < 2; dir++) {
                    if (edge[cx][cz][dir]) {
                        int[] a = node[cx][cz];
                        int[] b = node[cx + (dir == 0 ? 1 : 0)][cz + (dir == 1 ? 1 : 0)];
                        corridor(open, path, a[0], a[1], b[0], b[1], r.nextBoolean());
                    }
                }
            }
        }

        // 入口と下り口の部屋 (下り口は、入口から歩いて最も遠い部屋)
        Room entry = rooms.get(r.nextInt(rooms.size()));
        int[][] dist = distances(open, new Spot(entry.cx(), entry.cz()));
        Room far = null;
        int farD = -1;
        for (Room room : rooms) {
            int d = dist[room.cx()][room.cz()];
            if (room != entry && d > farD) {
                farD = d;
                far = room;
            }
        }
        List<Room> others = new ArrayList<>(rooms);
        others.remove(entry);
        others.remove(far);
        Collections.shuffle(others, r);
        RoomType[] types = new RoomType[rooms.size()];
        Arrays.fill(types, RoomType.NORMAL);
        types[far.id()] = RoomType.STAIRS;
        boolean checkpointFloor = floor % DepthPlan.BOSS_EVERY == 1;
        if (checkpointFloor) {
            types[entry.id()] = RoomType.REST;
        } else if (r.nextInt(100) < 22 && !others.isEmpty()) {
            types[others.remove(0).id()] = RoomType.REST;
        }
        if (floor >= 2 && r.nextInt(100) < 28 && !others.isEmpty()) {
            types[others.remove(0).id()] = RoomType.SHOP;
        }
        int houseRoom = -1;
        if (floor >= 3 && r.nextInt(100) < Math.min(40, 15 + floor) && !others.isEmpty()) {
            Room h = others.stream().filter(o -> o.area() >= 80).findFirst().orElse(null);
            if (h != null) {
                others.remove(h);
                types[h.id()] = RoomType.HOUSE;
                houseRoom = h.id();
            }
        }
        if (r.nextInt(100) < 35 && !others.isEmpty()) {
            types[others.remove(0).id()] = RoomType.TREASURE;
        }
        List<Room> typed = new ArrayList<>();
        for (Room room : rooms) {
            typed.add(new Room(room.id(), room.x0(), room.z0(), room.w(), room.d(), types[room.id()], room == entry));
        }
        Room entryT = typed.get(entry.id());
        Room stairsT = typed.get(far.id());

        Spot entrySpot = near(path, entryT, entryT.cx(), entryT.cz(), 2);
        Spot stairs = new Spot(stairsT.cx(), stairsT.cz());
        List<Spot> taken = new ArrayList<>(List.of(entrySpot, stairs));

        // 休み場の灯籠と鎖・行商の位置
        Spot lantern = null;
        Spot chain = null;
        Spot merchant = null;
        for (Room room : typed) {
            if (room.type() == RoomType.REST && lantern == null) {
                lantern = near(path, room, room.cx(), room.cz(), 1);
                chain = new Spot(room.x0() + 1, room.z0() + 1);
                taken.add(lantern);
            }
            if (room.type() == RoomType.SHOP && merchant == null) {
                merchant = near(path, room, room.cx(), room.cz(), 1);
                taken.add(merchant);
            }
        }

        // 箱
        List<Chest> chests = new ArrayList<>();
        for (Room room : typed) {
            int count = switch (room.type()) {
                case NORMAL -> r.nextInt(100) < 30 && !room.entry() ? 1 : 0;
                case TREASURE, HOUSE -> 2;
                default -> 0;
            };
            int tier = room.type() == RoomType.TREASURE ? 1 : room.type() == RoomType.HOUSE ? 2 : 0;
            for (int i = 0; i < count; i++) {
                Spot s = freeOff(path, room, r, taken, 2);
                if (s != null) {
                    chests.add(new Chest(s.x, s.z, tier));
                    taken.add(s);
                }
            }
        }
        if (chests.isEmpty()) {
            for (Room room : typed) {
                if (room.type() == RoomType.NORMAL && !room.entry()) {
                    Spot s = freeOff(path, room, r, taken, 2);
                    if (s != null) {
                        chests.add(new Chest(s.x, s.z, 0));
                        taken.add(s);
                        break;
                    }
                }
            }
        }

        // 罠 (休み場・行商・入口・下り口の部屋の外。互いに 4 マス以上離す)
        List<Trap> traps = new ArrayList<>();
        List<Spot> pool = new ArrayList<>();
        for (int x = 2; x < n - 2; x++) {
            for (int z = 2; z < n - 2; z++) {
                if (!open[x][z]) {
                    continue;
                }
                Room in2 = null;
                for (Room room : typed) {
                    if (room.contains(x, z)) {
                        in2 = room;
                    }
                }
                if (in2 != null && (in2.entry() || in2.type() == RoomType.REST || in2.type() == RoomType.SHOP || in2.type() == RoomType.STAIRS)) {
                    continue;
                }
                pool.add(new Spot(x, z));
            }
        }
        Collections.shuffle(pool, r);
        int want = DepthPlan.trapCount(floor);
        TrapKind[] tk = TrapKind.values();
        for (Spot s : pool) {
            if (traps.size() >= want) {
                break;
            }
            boolean ok = true;
            for (Spot t : taken) {
                if (s.dist(t) < 4) {
                    ok = false;
                    break;
                }
            }
            for (Trap t : traps) {
                if (Math.abs(t.x() - s.x) + Math.abs(t.z() - s.z) < 4) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                traps.add(new Trap(s.x, s.z, tk[r.nextInt(tk.length)]));
            }
        }
        return new Plan(floor, seed, kind, false, open, path, List.copyOf(typed), entrySpot, stairs, List.copyOf(traps), List.copyOf(chests), lights(open),
                lantern, chain, merchant, null, houseRoom);
    }

    // ---------------------------------------------------------------- 部品
    private static void carve(boolean[][] open, Room r) {
        for (int x = r.x0; x < r.x0 + r.w; x++) {
            for (int z = r.z0; z < r.z0 + r.d; z++) {
                open[x][z] = true;
            }
        }
    }

    // 幅 3 の L 字の通路 (a から b へ。horizontalFirst なら先に x)
    private static void corridor(boolean[][] open, boolean[][] path, int ax, int az, int bx, int bz, boolean horizontalFirst) {
        int mx = horizontalFirst ? bx : ax;
        int mz = horizontalFirst ? az : bz;
        line(open, path, ax, az, mx, mz);
        line(open, path, mx, mz, bx, bz);
    }

    private static void line(boolean[][] open, boolean[][] path, int ax, int az, int bx, int bz) {
        int dx = Integer.signum(bx - ax);
        int dz = Integer.signum(bz - az);
        int x = ax;
        int z = az;
        while (true) {
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if ((dx != 0 && i != 0) || (dz != 0 && j != 0)) {
                        continue;
                    }
                    int ex = x + i;
                    int ez = z + j;
                    if (ex < 0 || ez < 0 || ex >= open.length || ez >= open.length) {
                        continue;
                    }
                    path[ex][ez] = true;
                    if (Math.abs(i) <= 1 && Math.abs(j) <= 1) {
                        open[ex][ez] = true;
                    }
                }
            }
            if (x == bx && z == bz) {
                break;
            }
            x += dx;
            z += dz;
        }
    }

    // 部屋の中の、通路の線 (path) に載らない床のうち (nx, nz) にいちばん近い所。なければ (nx, nz)
    private static Spot near(boolean[][] path, Room room, int nx, int nz, int margin) {
        Spot best = null;
        int bd = Integer.MAX_VALUE;
        for (int x = room.x0 + margin; x < room.x0 + room.w - margin; x++) {
            for (int z = room.z0 + margin; z < room.z0 + room.d - margin; z++) {
                if (path[x][z]) {
                    continue;
                }
                int d = Math.abs(x - nx) + Math.abs(z - nz);
                if (d < bd) {
                    bd = d;
                    best = new Spot(x, z);
                }
            }
        }
        return best != null ? best : new Spot(nx, nz);
    }

    // 部屋の中のランダムな床 (通路の線に載らず、taken から 3 マス以上離れる)。なければ null
    private static Spot freeOff(boolean[][] path, Room room, Random r, List<Spot> taken, int margin) {
        List<Spot> c = new ArrayList<>();
        for (int x = room.x0 + margin; x < room.x0 + room.w - margin; x++) {
            for (int z = room.z0 + margin; z < room.z0 + room.d - margin; z++) {
                if (path[x][z]) {
                    continue;
                }
                Spot s = new Spot(x, z);
                boolean ok = true;
                for (Spot t : taken) {
                    if (s.dist(t) < 3) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    c.add(s);
                }
            }
        }
        return c.isEmpty() ? null : c.get(r.nextInt(c.size()));
    }

    // 灯りの置き方: 床を順に見て、LIGHT_R マス (|dx|+|dz|) 以内に灯りがなければ、その床の天井に一つ
    static List<Spot> lights(boolean[][] open) {
        int n = open.length;
        List<Spot> out = new ArrayList<>();
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                if (!open[x][z]) {
                    continue;
                }
                boolean lit = false;
                for (Spot l : out) {
                    if (Math.abs(l.x - x) + Math.abs(l.z - z) <= LIGHT_R) {
                        lit = true;
                        break;
                    }
                }
                if (!lit) {
                    int sx = x + 1;
                    int sz = z + 1;
                    out.add(sx < n && sz < n && open[sx][sz] ? new Spot(sx, sz) : new Spot(x, z));
                }
            }
        }
        return out;
    }
}
