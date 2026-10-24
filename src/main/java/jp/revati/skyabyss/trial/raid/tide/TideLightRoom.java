package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 智 TR5「光を返す真珠」: 南の壁の灯台から光の筋が北へ伸びる。広間に並ぶ真珠の台 (鏡) を回して、光を北の壁の水晶へ導く。
//  - 台の真珠は、光を「台の縁の白い印」の向きへ返す (叩く = 反時計回り、使う = 時計回りに 90 度)。光は台の格子 (縦横 5×5、四ブロックおき) の上だけを進む
//  - 光を通さない柱がある。壁・逆戻りの所で光は途切れる。水晶に届いて少し保てば越える
//  - 配置は挑戦ごとに変わる (経路は毎回必ず解ける。経路に関係のない飾りの台と柱が混じる)。台が離れているので、一人が光を見て、もう一人が台を回すと早い
//  - 水・ダメージの仕掛けはない。敵は少しだけ (磯走り)
public final class TideLightRoom extends RaidRoom {
    static final int HX = 11;
    static final int HZ = 13;
    static final int H = 8;
    static final int N = 5;
    static final int[] LATTICE = {-8, -4, 0, 4, 8};
    // 向き: 0 北 (z-) 1 東 (x+) 2 南 (z+) 3 西 (x-)
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DZ = {-1, 0, 1, 0};
    static final int HOLD_TICKS = 30;
    static final double BEAM_Y = 3.5;

    // 格子 (i = x 側、j = z 側。j = 0 が北、4 が南)
    private final int[][] mirror = new int[N][N];
    private final int[][] startFacing = new int[N][N];
    private final int[][] correct = new int[N][N];
    private final boolean[][] pillar = new boolean[N][N];
    private int emitterI;
    private int targetI;
    private int pathMirrors;
    private BlockPos origin;
    private RaidCtx ctxRef;
    private int lit;
    private boolean done;
    private Trace last;
    private long nextSpawn;

    private record Trace(boolean hit, boolean revisit, int reflections, List<int[]> nodes, List<double[]> points) {
    }

    @Override
    public void build(RaidCtx ctx) {
        ctxRef = ctx;
        ServerLevel level = ctx.level();
        origin = ctx.origin();
        generate(ctx);
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, TideBuild.PALETTE));
        BlockState brick = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
        // 灯台 (南の壁) と水晶の枠 (北の壁)
        int ex = LATTICE[emitterI];
        RaidKit.fill(level, origin.offset(ex - 1, 1, HZ), origin.offset(ex + 1, 1, HZ), brick);
        RaidKit.fill(level, origin.offset(ex, 2, HZ), origin.offset(ex, 3, HZ), Blocks.SEA_LANTERN.defaultBlockState());
        int tx = LATTICE[targetI];
        RaidKit.fill(level, origin.offset(tx - 1, 1, -HZ), origin.offset(tx + 1, 1, -HZ), brick);
        RaidKit.fill(level, origin.offset(tx - 1, 2, -HZ), origin.offset(tx - 1, 4, -HZ), dark);
        RaidKit.fill(level, origin.offset(tx + 1, 2, -HZ), origin.offset(tx + 1, 4, -HZ), dark);
        RaidKit.fill(level, origin.offset(tx - 1, 5, -HZ), origin.offset(tx + 1, 5, -HZ), dark);
        setCrystal(ctx, false);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (mirror[i][j] >= 0) {
                    drawMirror(ctx, i, j);
                } else if (pillar[i][j]) {
                    int x = LATTICE[i];
                    int z = LATTICE[j];
                    RaidKit.fill(level, origin.offset(x, 1, z), origin.offset(x, 5, z), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
                    RaidKit.set(level, origin.offset(x, 6, z), dark);
                }
            }
        }
        BlockState[] dead = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState()};
        for (int k = 0; k < 10; k++) {
            int side = ctx.rng().nextBoolean() ? -1 : 1;
            int z = ctx.rng().nextInt(2 * HZ - 7) - HZ + 3;
            RaidKit.fill(level, origin.offset(side * (HX - 1), 1, z), origin.offset(side * (HX - 1), 1 + ctx.rng().nextInt(3), z), dead[ctx.rng().nextInt(dead.length)]);
        }
        dress(ctx);
    }

    // 飾り (docs/design/39 の 4.2「解く 光」): 北の水晶を真珠層の光の輪で囲む。光を水晶に保つあいだ、輪の八つの目が下から順に灯る。
    // 南の灯台の上に縞の塔。南東の隅に見渡し台 (光の筋の高さより上から、台の格子の全体が見える)。台の格子と光の通る高さには何も置かない
    private void dress(RaidCtx ctx) {
        TideBuild d = new TideBuild(ctx);
        d.keep(-10, 1, -12, 10, 6, 12);
        int ex = LATTICE[emitterI];
        int tx = LATTICE[targetI];
        d.keep(ex - 1, 1, HZ - 1, ex + 1, 4, HZ);
        d.keep(tx - 1, 1, -HZ, tx + 1, 5, -HZ + 1);
        d.keep(9, 1, 9, HX, 6, HZ);
        d.keep(tx - 5, 1, -HZ, tx + 5, H, -HZ);
        d.keep(ex - 1, 1, HZ, ex + 1, H, HZ);
        d.shell(2);
        // 水晶の光の輪 (北の殻の面)
        haloEyes.clear();
        haloEyes.addAll(d.halo(true, tx, 3.5, 3.2, HALO_EYES));
        // 灯台の縞の塔 (南の殻の面、灯台の上)
        for (int y = 1; y <= H; y++) {
            for (int x = ex - 1; x <= ex + 1; x++) {
                if (Math.abs(x) <= 3 && y <= 5) {
                    continue;
                }
                BlockPos w = origin.offset(x, y, HZ + 1);
                if (!ctx.level().getBlockState(w).is(Blocks.IRON_BARS)) {
                    RaidKit.set(ctx.level(), w, y % 2 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.CHERRY_PLANKS.defaultBlockState());
                }
            }
        }
        // 見渡し台 (南東の隅、天板の高さ 3)
        BlockState top = Blocks.CUT_SANDSTONE.defaultBlockState();
        // 北から南へ上る (段の向き FACING は高い側 = 南)
        BlockState step = Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.SOUTH);
        for (int x = HX - 1; x <= HX; x++) {
            RaidKit.set(ctx.level(), origin.offset(x, 1, HZ - 3), step);
            RaidKit.set(ctx.level(), origin.offset(x, 1, HZ - 2), top);
            RaidKit.set(ctx.level(), origin.offset(x, 2, HZ - 2), step);
            for (int z = HZ - 1; z <= HZ; z++) {
                RaidKit.set(ctx.level(), origin.offset(x, 1, z), top);
                RaidKit.set(ctx.level(), origin.offset(x, 2, z), z == HZ ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : top);
            }
        }
        // 台座の縁 (光の格子の位置を床に): 格子の線を切った砂岩で
        for (int a : LATTICE) {
            d.floorLine(a, -HZ + 1, HZ - 1, Blocks.CUT_SANDSTONE.defaultBlockState());
        }
        d.hiddenLights(11, 4, false).ceilingCoral(8, 1, H - 1, 2).wallBranches(8, 5, 0).shards(3);
        d.finish();
        showHalo(ctx);
    }

    static final int HALO_EYES = 8;
    private final List<BlockPos> haloEyes = new ArrayList<>();
    private int haloShown = -1;

    // 光の輪: 光を保った長さだけ目が灯る (越えたら全部)
    private void showHalo(RaidCtx ctx) {
        int n = done ? haloEyes.size() : Math.min(haloEyes.size(), lit * haloEyes.size() / HOLD_TICKS);
        if (n != haloShown) {
            haloShown = n;
            TideBuild.lamps(ctx.level(), haloEyes, n);
        }
    }

    // 経路を作る: 灯台から北へ。角ごとに台を置き、最後に北へ曲げて水晶へ。経路の台は正解の向きで作り、最後にばらす
    private void generate(RaidCtx ctx) {
        var rng = ctx.rng();
        for (int attempt = 0; attempt < 2000; attempt++) {
            for (int a = 0; a < N; a++) {
                Arrays.fill(mirror[a], -1);
                Arrays.fill(correct[a], -1);
                Arrays.fill(pillar[a], false);
            }
            int corners = rng.nextInt(10) < 7 ? 4 : 2;
            int i = rng.nextInt(N);
            int j = N - 1;
            int dir = 0;
            List<int[]> path = new ArrayList<>();
            boolean ok = true;
            emitterI = i;
            if (emitterI == 2) {
                continue;
            }
            for (int c = 0; c < corners && ok; c++) {
                int max = switch (dir) {
                    case 0 -> j;
                    case 1 -> N - 1 - i;
                    case 2 -> N - 1 - j;
                    default -> i;
                };
                int d = c == 0 ? rng.nextInt(Math.min(3, max) + 1) : 1 + rng.nextInt(Math.max(1, max));
                if (d > max || (c > 0 && d == 0)) {
                    ok = false;
                    break;
                }
                i += DX[dir] * d;
                j += DZ[dir] * d;
                int turn;
                if (c == corners - 1) {
                    turn = 0;
                } else if (dir == 0 || dir == 2) {
                    turn = rng.nextBoolean() ? 1 : 3;
                } else {
                    turn = rng.nextBoolean() ? 0 : 2;
                }
                if (mirror[i][j] >= 0) {
                    ok = false;
                    break;
                }
                mirror[i][j] = turn;
                correct[i][j] = turn;
                path.add(new int[] {i, j});
                dir = turn;
            }
            if (!ok || dir != 0 || path.isEmpty()) {
                continue;
            }
            targetI = path.get(path.size() - 1)[0];
            if (targetI == 2) {
                continue;
            }
            Trace t = trace();
            if (!t.hit() || t.revisit()) {
                continue;
            }
            pathMirrors = path.size();
            // 飾りの台と柱: 光の通る格子点でない所に
            Set<Integer> used = new HashSet<>();
            for (int[] n : t.nodes()) {
                used.add(n[0] * N + n[1]);
            }
            List<Integer> free = new ArrayList<>();
            for (int a = 0; a < N * N; a++) {
                if (!used.contains(a)) {
                    free.add(a);
                }
            }
            java.util.Collections.shuffle(free, new java.util.Random(ctx.seed() ^ 0xC0FFEEL));
            int decoys = 2 + rng.nextInt(2);
            for (int k = 0; k < free.size(); k++) {
                int a = free.get(k);
                if (k < decoys) {
                    mirror[a / N][a % N] = rng.nextInt(4);
                } else if (k < decoys + 2) {
                    pillar[a / N][a % N] = true;
                }
            }
            // 経路の台を、どれも正解でない向きにばらす。ばらしたままで光が届く配置は避ける
            boolean scrambledOk = false;
            for (int tries = 0; tries < 20 && !scrambledOk; tries++) {
                for (int[] n : path) {
                    mirror[n[0]][n[1]] = (correct[n[0]][n[1]] + 1 + rng.nextInt(3)) % 4;
                }
                scrambledOk = !trace().hit();
            }
            if (!scrambledOk) {
                continue;
            }
            for (int a = 0; a < N; a++) {
                startFacing[a] = mirror[a].clone();
            }
            return;
        }
        throw new IllegalStateException("tide light room could not generate a route");
    }

    // 光を追う (いまの台の向きで)。点の座標は原点からの (x, z)
    private Trace trace() {
        List<int[]> nodes = new ArrayList<>();
        List<double[]> pts = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        boolean revisit = false;
        int dir = 0;
        int i = emitterI;
        int j = N - 1;
        int refl = 0;
        pts.add(new double[] {LATTICE[i], HZ - 0.5});
        for (int step = 0; step < 40; step++) {
            pts.add(new double[] {LATTICE[i], LATTICE[j]});
            if (!seen.add(i * N + j)) {
                revisit = true;
                break;
            }
            nodes.add(new int[] {i, j});
            if (pillar[i][j]) {
                return new Trace(false, revisit, refl, nodes, pts);
            }
            if (mirror[i][j] >= 0) {
                int out = mirror[i][j];
                if (out == (dir + 2) % 4) {
                    return new Trace(false, revisit, refl, nodes, pts);
                }
                dir = out;
                refl++;
            }
            int ni = i + DX[dir];
            int nj = j + DZ[dir];
            if (ni < 0 || ni >= N || nj < 0 || nj >= N) {
                double wx = dir == 1 ? HX - 0.5 : dir == 3 ? -HX + 0.5 : LATTICE[i];
                double wz = dir == 0 ? -HZ + 0.5 : dir == 2 ? HZ - 0.5 : LATTICE[j];
                pts.add(new double[] {wx, wz});
                return new Trace(dir == 0 && i == targetI, revisit, refl, nodes, pts);
            }
            i = ni;
            j = nj;
        }
        return new Trace(false, revisit, refl, nodes, pts);
    }

    private void drawMirror(RaidCtx ctx, int i, int j) {
        ServerLevel level = ctx.level();
        int x = LATTICE[i];
        int z = LATTICE[j];
        BlockState brick = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        RaidKit.fill(level, origin.offset(x - 1, 1, z - 1), origin.offset(x + 1, 1, z + 1), brick);
        RaidKit.set(level, origin.offset(x, 2, z), Blocks.DARK_PRISMARINE.defaultBlockState());
        RaidKit.set(level, origin.offset(x, 3, z), Blocks.SEA_LANTERN.defaultBlockState());
        int f = mirror[i][j];
        RaidKit.set(level, origin.offset(x + DX[f], 1, z + DZ[f]), Blocks.WHITE_CONCRETE.defaultBlockState());
    }

    private void setCrystal(RaidCtx ctx, boolean on) {
        int tx = LATTICE[targetI];
        BlockState st = on ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.TINTED_GLASS.defaultBlockState();
        RaidKit.fill(ctx.level(), origin.offset(tx, 2, -HZ), origin.offset(tx, 3, -HZ), st);
    }

    private void restore(RaidCtx ctx) {
        lit = 0;
        done = false;
        setCrystal(ctx, false);
        showHalo(ctx);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (mirror[i][j] >= 0 && mirror[i][j] != startFacing[i][j]) {
                    mirror[i][j] = startFacing[i][j];
                    drawMirror(ctx, i, j);
                }
            }
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        ctxRef = ctx;
        restore(ctx);
        nextSpawn = ctx.now() + 160;
        spawnFoe(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        ctx.clearMobs();
        restore(ctx);
    }

    private void spawnFoe(RaidCtx ctx) {
        for (int k = 0; k < 20; k++) {
            BlockPos at = RaidKit.randomSpot(ctx, 3);
            if (ctx.level().getBlockState(at).isAir() && ctx.level().getBlockState(at.above()).isAir() && !ctx.level().getBlockState(at.below()).isAir()) {
                TideRaid.spawn(ctx, TideMobSetup.REEF_SCUTTLER.get(), at);
                return;
            }
        }
    }

    @Override
    public void tick(RaidCtx ctx) {
        last = trace();
        long now = ctx.now();
        if (!done) {
            if (last.hit()) {
                lit++;
                setCrystal(ctx, true);
                if (lit >= HOLD_TICKS) {
                    done = true;
                    ctx.level().playSound(null, origin.above(2), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6f, 1.3f);
                    ctx.say(Component.translatable("message.skyabyss.raid.TR5.lit"));
                }
            } else {
                if (lit > 0) {
                    setCrystal(ctx, false);
                }
                lit = 0;
            }
        }
        showHalo(ctx);
        // 光の筋 (3 tick おき)
        if (now % 3 == 0) {
            ServerLevel level = ctx.level();
            List<double[]> pts = last.points();
            for (int k = 0; k + 1 < pts.size(); k++) {
                double[] a = pts.get(k);
                double[] b = pts.get(k + 1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                int n = Math.max(1, (int) (len / 0.8));
                for (int s = 0; s < n; s++) {
                    double u = (double) s / n;
                    level.sendParticles(TideRaid.dust(0.75f, 1f, 1f, 0.9f), origin.getX() + 0.5 + a[0] + (b[0] - a[0]) * u, origin.getY() + BEAM_Y,
                            origin.getZ() + 0.5 + a[1] + (b[1] - a[1]) * u, 1, 0.02, 0.02, 0.02, 0);
                }
            }
            double[] end = pts.get(pts.size() - 1);
            level.sendParticles(last.hit() ? ParticleTypes.END_ROD : ParticleTypes.SMOKE, origin.getX() + 0.5 + end[0], origin.getY() + BEAM_Y, origin.getZ() + 0.5 + end[1], 2, 0.1, 0.1, 0.1, 0.01);
        }
        if (!done && now >= nextSpawn) {
            nextSpawn = now + 300;
            if (ctx.aliveMobs().size() < 1 + ctx.scale()) {
                spawnFoe(ctx);
            }
        }
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        int[] n = mirrorAt(pos);
        if (n == null) {
            return false;
        }
        if (!done) {
            rotate(ctx, n[0], n[1], attack ? 3 : 1);
        }
        return true;
    }

    private int[] mirrorAt(BlockPos pos) {
        int lx = pos.getX() - origin.getX();
        int lz = pos.getZ() - origin.getZ();
        int ly = pos.getY() - origin.getY();
        if (ly < 1 || ly > 3) {
            return null;
        }
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (mirror[i][j] >= 0 && Math.abs(lx - LATTICE[i]) <= 1 && Math.abs(lz - LATTICE[j]) <= 1) {
                    return new int[] {i, j};
                }
            }
        }
        return null;
    }

    private void rotate(RaidCtx ctx, int i, int j, int by) {
        int x = LATTICE[i];
        int z = LATTICE[j];
        int old = mirror[i][j];
        RaidKit.set(ctx.level(), origin.offset(x + DX[old], 1, z + DZ[old]), Blocks.PRISMARINE_BRICKS.defaultBlockState());
        mirror[i][j] = (old + by) % 4;
        drawMirror(ctx, i, j);
        ctx.level().playSound(null, origin.offset(x, 2, z), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 0.8f + 0.2f * mirror[i][j]);
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.TR5.guide";
    }

    // 光が曲がった数 (水晶に届けば最後の一つ)
    @Override
    public int counter() {
        if (done) {
            return pathMirrors + 1;
        }
        Trace t = last == null ? trace() : last;
        return Math.min(pathMirrors, t.reflections()) + (t.hit() ? 1 : 0);
    }

    @Override
    public int counterMax() {
        return pathMirrors + 1;
    }

    // ---- 試験用
    public int pathMirrors() {
        return pathMirrors;
    }

    public boolean lit() {
        return last != null && last.hit();
    }

    public int litTicks() {
        return lit;
    }

    // 経路の台の一覧 (格子の添字 {i, j})
    public List<int[]> pathNodes() {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (correct[i][j] >= 0) {
                    out.add(new int[] {i, j});
                }
            }
        }
        return out;
    }

    public int facing(int i, int j) {
        return mirror[i][j];
    }

    public int correctFacing(int i, int j) {
        return correct[i][j];
    }

    public int startFacing(int i, int j) {
        return startFacing[i][j];
    }

    public int mirrorsTotal() {
        int n = 0;
        for (int[] row : mirror) {
            for (int f : row) {
                if (f >= 0) {
                    n++;
                }
            }
        }
        return n;
    }

    public BlockPos mirrorPos(int i, int j) {
        return origin.offset(LATTICE[i], 2, LATTICE[j]);
    }

    public int targetColumn() {
        return targetI;
    }

    public int emitterColumn() {
        return emitterI;
    }
}
