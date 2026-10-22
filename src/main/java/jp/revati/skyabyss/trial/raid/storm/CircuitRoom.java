package jp.revati.skyabyss.trial.raid.storm;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// 智 SR2「銅の回路つなぎ」: 床に 7×7 の銅線のタイル (一枚 3×3 ブロック)。叩く (左右どちらのクリックでも) とそのタイルが 90 度回る。
// 中央の電源 (金の蛙灯の柱) から、四隅あたりの 4 つのランプへ線をつなぐ。つながった線は光り、光の粒が流れて見える。
//  - 盤は挑戦ごとに作る (ctx.rng())。必ず解ける: まず電源から全タイルへ木を張り、ランプへ要らない枝を刈り、残った線をばらばらに回す。
//    線に使わないタイルは、飾りの線 (つなげてもよい) をつけた
//  - 敵・傷はない。手分けして別々の枝を回すと早い
public final class CircuitRoom extends RaidRoom {
    public static final int N = 7;
    static final int T = 3;
    static final int HX = 10;
    static final int HZ = 10;
    static final int H = 8;
    static final int LAMPS = 4;
    // 方角: 0 = 北 (-z)、1 = 東 (+x)、2 = 南 (+z)、3 = 西 (-x)。タイルの形は 4 ビットの腕
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DZ = {-1, 0, 1, 0};
    static final int CROSS = 15;

    // 盤の設計 (世界に依存しない。試験もここだけで解ける)
    public static final class Layout {
        public final int[][] solution = new int[N][N];
        public final int[][] start = new int[N][N];
        public final int srcX = N / 2;
        public final int srcZ = N / 2;
        public final int[] lampX = new int[LAMPS];
        public final int[] lampZ = new int[LAMPS];

        public boolean isFixed(int x, int z) {
            if (x == srcX && z == srcZ) {
                return true;
            }
            return lampIndex(x, z) >= 0;
        }

        public int lampIndex(int x, int z) {
            for (int i = 0; i < LAMPS; i++) {
                if (lampX[i] == x && lampZ[i] == z) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static int rotateMask(int m) {
        return ((m << 1) | (m >> 3)) & 15;
    }

    // 盤を作る
    public static Layout generate(RandomSource rng) {
        Layout l = new Layout();
        // ランプ: 外周のうち、互いに離れた 4 つ
        List<int[]> ring = new ArrayList<>();
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                if (x == 0 || z == 0 || x == N - 1 || z == N - 1) {
                    ring.add(new int[] {x, z});
                }
            }
        }
        boolean ok = false;
        for (int attempt = 0; attempt < 200 && !ok; attempt++) {
            java.util.Collections.shuffle(ring, new java.util.Random(rng.nextLong()));
            int n = 0;
            for (int[] c : ring) {
                boolean far = true;
                for (int i = 0; i < n; i++) {
                    if (Math.abs(l.lampX[i] - c[0]) + Math.abs(l.lampZ[i] - c[1]) < 5) {
                        far = false;
                    }
                }
                if (far) {
                    l.lampX[n] = c[0];
                    l.lampZ[n] = c[1];
                    if (++n == LAMPS) {
                        break;
                    }
                }
            }
            ok = n == LAMPS;
        }
        if (!ok) {
            int[][] corners = {{0, 0}, {N - 1, 0}, {0, N - 1}, {N - 1, N - 1}};
            for (int i = 0; i < LAMPS; i++) {
                l.lampX[i] = corners[i][0];
                l.lampZ[i] = corners[i][1];
            }
        }
        // 電源から全タイルへ、ランダムな深さ優先の木
        int[][] adj = new int[N][N];
        boolean[][] seen = new boolean[N][N];
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[] {l.srcX, l.srcZ});
        seen[l.srcX][l.srcZ] = true;
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<Integer> opts = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DX[d];
                int nz = c[1] + DZ[d];
                if (nx >= 0 && nz >= 0 && nx < N && nz < N && !seen[nx][nz]) {
                    opts.add(d);
                }
            }
            if (opts.isEmpty()) {
                stack.pop();
                continue;
            }
            int d = opts.get(rng.nextInt(opts.size()));
            int nx = c[0] + DX[d];
            int nz = c[1] + DZ[d];
            adj[c[0]][c[1]] |= 1 << d;
            adj[nx][nz] |= 1 << ((d + 2) & 3);
            seen[nx][nz] = true;
            stack.push(new int[] {nx, nz});
        }
        // ランプへ要らない枝 (葉) を刈る
        boolean pruned = true;
        while (pruned) {
            pruned = false;
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    if (adj[x][z] != 0 && Integer.bitCount(adj[x][z]) == 1 && !l.isFixed(x, z)) {
                        int d = Integer.numberOfTrailingZeros(adj[x][z]);
                        int nx = x + DX[d];
                        int nz = z + DZ[d];
                        adj[x][z] = 0;
                        adj[nx][nz] &= ~(1 << ((d + 2) & 3));
                        pruned = true;
                    }
                }
            }
        }
        // 線に使わないタイルの飾り (腕 1〜2 本)
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                if (l.isFixed(x, z)) {
                    l.solution[x][z] = CROSS;
                } else if (adj[x][z] != 0) {
                    l.solution[x][z] = adj[x][z];
                } else {
                    int a = rng.nextInt(4);
                    int m = 1 << a;
                    if (rng.nextBoolean()) {
                        m |= 1 << ((a + (rng.nextBoolean() ? 1 : 2)) & 3);
                    }
                    l.solution[x][z] = m;
                }
            }
        }
        // ばらばらに回す。偶然解けていたらやり直す
        for (int attempt = 0; attempt < 50; attempt++) {
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    int m = l.solution[x][z];
                    if (!l.isFixed(x, z)) {
                        for (int r = rng.nextInt(4); r > 0; r--) {
                            m = rotateMask(m);
                        }
                    }
                    l.start[x][z] = m;
                }
            }
            if (litLamps(l, l.start, null) < LAMPS) {
                break;
            }
        }
        return l;
    }

    // いまの腕の並びで、つながって光るタイル。ランプの数を返す。lit に非 null を渡すと、光るタイルを (電源から近い順に) 並べて入れる
    public static int litLamps(Layout l, int[][] cur, boolean[][] litOut) {
        boolean[][] lit = new boolean[N][N];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[] {l.srcX, l.srcZ});
        lit[l.srcX][l.srcZ] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DX[d];
                int nz = c[1] + DZ[d];
                if (nx < 0 || nz < 0 || nx >= N || nz >= N || lit[nx][nz]) {
                    continue;
                }
                if ((cur[c[0]][c[1]] & (1 << d)) != 0 && (cur[nx][nz] & (1 << ((d + 2) & 3))) != 0) {
                    lit[nx][nz] = true;
                    q.add(new int[] {nx, nz});
                }
            }
        }
        int n = 0;
        for (int i = 0; i < LAMPS; i++) {
            if (lit[l.lampX[i]][l.lampZ[i]]) {
                n++;
            }
        }
        if (litOut != null) {
            for (int x = 0; x < N; x++) {
                System.arraycopy(lit[x], 0, litOut[x], 0, N);
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 部屋
    private Layout layout;
    private final int[][] cur = new int[N][N];
    private final boolean[][] lit = new boolean[N][N];
    private final int[][] drawn = new int[N][N];
    private final boolean[][] drawnLit = new boolean[N][N];
    private final boolean[] lampOn = new boolean[LAMPS];
    private int lampsLit;
    private long age;

    public Layout layout() {
        return layout;
    }

    public int[][] current() {
        return cur;
    }

    @Override
    public void build(RaidCtx ctx) {
        layout = generate(ctx.rng());
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.STORM));
        ServerLevel level = ctx.level();
        // 四隅の柱 (避雷針つき) と、天井の雲 (方解石)
        BlockState post = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                RaidKit.fill(level, c.origin().offset(sx * HX, 1, sz * HZ), c.origin().offset(sx * HX, H - 2, sz * HZ), post);
                RaidKit.set(level, c.origin().offset(sx * HX, H - 1, sz * HZ), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                RaidKit.set(level, c.origin().offset(sx * HX, H, sz * HZ), Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
        for (int i = 0; i < 9; i++) {
            int x = ctx.rng().nextInt(2 * HX - 3) - HX + 1;
            int z = ctx.rng().nextInt(2 * HZ - 3) - HZ + 1;
            if (nearLight(x) && nearLight(z)) {
                continue;
            }
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    RaidKit.set(level, c.origin().offset(x + dx, H, z + dz), Blocks.CALCITE.defaultBlockState());
                }
            }
        }
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                cur[x][z] = layout.start[x][z];
                drawn[x][z] = -1;
            }
        }
        refresh(ctx, true);
    }

    // 天井の灯り (x・z = -7, 1) の下は雲を置かない
    private static boolean nearLight(int v) {
        return (v >= -9 && v <= -6) || (v >= -1 && v <= 2);
    }

    @Override
    public void start(RaidCtx ctx) {
        age = 0;
    }

    @Override
    public void tick(RaidCtx ctx) {
        age++;
        if (age % 3 == 0) {
            List<int[]> order = new ArrayList<>();
            for (int x = 0; x < N; x++) {
                for (int z = 0; z < N; z++) {
                    if (lit[x][z]) {
                        order.add(new int[] {x, z});
                    }
                }
            }
            if (!order.isEmpty()) {
                // 流れ: 電源から遠い順の位置に粒が一つずつ進む
                order.sort((a, b) -> Integer.compare(dist(a), dist(b)));
                int[] t = order.get((int) ((age / 3) % order.size()));
                BlockPos at = tileCenter(ctx, t[0], t[1]);
                ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 2, 0.4, 0.1, 0.4, 0.02);
            }
        }
    }

    private int dist(int[] c) {
        return Math.abs(c[0] - layout.srcX) + Math.abs(c[1] - layout.srcZ);
    }

    @Override
    public boolean isComplete() {
        return lampsLit >= LAMPS;
    }

    @Override
    public void reset(RaidCtx ctx) {
        for (int x = 0; x < N; x++) {
            System.arraycopy(layout.start[x], 0, cur[x], 0, N);
        }
        age = 0;
        refresh(ctx, false);
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.SR2.connect";
    }

    @Override
    public int counter() {
        return lampsLit;
    }

    @Override
    public int counterMax() {
        return LAMPS;
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        int lx = pos.getX() - ctx.origin().getX() + HX;
        int lz = pos.getZ() - ctx.origin().getZ() + HZ;
        int dy = pos.getY() - ctx.origin().getY();
        if (lx < 0 || lz < 0 || lx >= N * T || lz >= N * T || dy < 0 || dy > 2) {
            return false;
        }
        return rotate(ctx, lx / T, lz / T);
    }

    // タイル (gx, gz) を 90 度回す。回せたら true (電源・ランプは回らない)
    public boolean rotate(RaidCtx ctx, int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= N || gz >= N || layout.isFixed(gx, gz)) {
            return false;
        }
        cur[gx][gz] = rotateMask(cur[gx][gz]);
        BlockPos at = tileCenter(ctx, gx, gz);
        ServerLevel level = ctx.level();
        level.playSound(null, at, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 6, 0.5, 0.1, 0.5, 0.05);
        refresh(ctx, false);
        return true;
    }

    public BlockPos tileCenter(RaidCtx ctx, int gx, int gz) {
        return ctx.origin().offset((gx - N / 2) * T, 0, (gz - N / 2) * T);
    }

    // つながりを数え直し、変わったタイルだけ描き直す
    private void refresh(RaidCtx ctx, boolean all) {
        lampsLit = litLamps(layout, cur, lit);
        ServerLevel level = ctx.level();
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                if (all || drawn[x][z] != cur[x][z] || drawnLit[x][z] != lit[x][z]) {
                    draw(ctx, x, z);
                }
            }
        }
        for (int i = 0; i < LAMPS; i++) {
            boolean on = lit[layout.lampX[i]][layout.lampZ[i]];
            if (on && !lampOn[i] && !all) {
                BlockPos at = tileCenter(ctx, layout.lampX[i], layout.lampZ[i]);
                level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 0.7f + 0.2f * lampsLit);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 2.2, at.getZ() + 0.5, 24, 0.3, 0.5, 0.3, 0.1);
            }
            lampOn[i] = on;
        }
    }

    private static final BlockState LIT_WIRE = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState DEAD_WIRE = Blocks.WAXED_EXPOSED_COPPER.defaultBlockState();

    private void draw(RaidCtx ctx, int gx, int gz) {
        ServerLevel level = ctx.level();
        BlockPos c = tileCenter(ctx, gx, gz);
        boolean on = lit[gx][gz];
        int m = cur[gx][gz];
        BlockState base = ((gx + gz) & 1) == 0 ? Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState() : Blocks.WAXED_WEATHERED_COPPER.defaultBlockState();
        RaidKit.fill(level, c.offset(-1, 0, -1), c.offset(1, 0, 1), base);
        BlockState wire = on ? LIT_WIRE : DEAD_WIRE;
        RaidKit.set(level, c, wire);
        for (int d = 0; d < 4; d++) {
            if ((m & (1 << d)) != 0) {
                RaidKit.set(level, c.offset(DX[d], 0, DZ[d]), wire);
            }
        }
        if (gx == layout.srcX && gz == layout.srcZ) {
            RaidKit.set(level, c, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            RaidKit.set(level, c.above(), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
            RaidKit.set(level, c.above(2), Blocks.LIGHTNING_ROD.defaultBlockState());
            RaidKit.set(level, c.above(3), Blocks.LIGHTNING_ROD.defaultBlockState());
        } else if (layout.lampIndex(gx, gz) >= 0) {
            BlockState bulb = Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, on);
            RaidKit.set(level, c, bulb);
            RaidKit.set(level, c.above(), bulb);
            RaidKit.set(level, c.above(2), Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        drawn[gx][gz] = m;
        drawnLit[gx][gz] = on;
    }
}
