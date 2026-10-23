package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 歩 TR4「伸びる珊瑚の道」: 広間を三枚の珊瑚の壁が横切る。壁には四つの戸 (幅 3) があり、戸は珊瑚の枝が伸びて閉じ、引いて開く。周期は戸ごとにずれていて、
// どの壁でも、いつも少なくとも一つは開いている。渡るには、開いている戸を読んで通る。
//  - 予告: 閉じる少し前に、床に桃色の絨毯が戸の端から順に敷かれる (伸びる向きが分かる)。そのあと同じ順に枝が伸びて塞ぐ。引くときは逆の順
//  - 戸の中に人が立っていると、その柱は伸びない (閉じるのが遅れる)。仲間が戸に立って押さえれば、ほかの人が通れる
//  - 水・落とし穴・ダメージはない。渡った壁の数が目的の数。全員が最後の壁を越えれば越える
public final class TideCoralRoadRoom extends RaidRoom {
    static final int HX = 13;
    static final int HZ = 15;
    static final int H = 9;
    static final int ROWS = 3;
    // 壁の位置 (原点からの z。北ほど小さい。厚さ 2: z と z+1)
    static final int[] ROW_Z = {8, 0, -8};
    static final int[] LANE_X = {-9, -3, 3, 9};
    static final int LANES = LANE_X.length;
    static final int CYCLE = 240;
    static final int WARN_AT = 64;
    static final int CLOSE_AT = 96;
    static final int OPEN_AT = 200;
    static final int STEP = 8;
    static final int GATE_H = 4;

    static final BlockState GROWTH_A = Blocks.PINK_CONCRETE.defaultBlockState();
    static final BlockState GROWTH_B = Blocks.MAGENTA_CONCRETE.defaultBlockState();
    static final BlockState SIGN = Blocks.PINK_CARPET.defaultBlockState();

    private RaidCtx ctxRef;
    private BlockPos origin;
    private final int[][] offset = new int[ROWS][LANES];
    private final int[][] dirSign = new int[ROWS][LANES];
    private final int[][] shown = new int[ROWS][LANES];
    private final int[][] carpets = new int[ROWS][LANES];
    private long t0;
    private boolean done;

    @Override
    public void build(RaidCtx ctx) {
        ctxRef = ctx;
        ServerLevel level = ctx.level();
        origin = ctx.origin();
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.TIDE));
        BlockState[] dead = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
                Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};
        BlockState lintel = Blocks.DARK_PRISMARINE.defaultBlockState();
        for (int r = 0; r < ROWS; r++) {
            int z = ROW_Z[r];
            // 壁: 端から端まで、高さ 6。戸の所だけ y=1〜GATE_H を空け、その上は梁
            for (int x = -HX; x <= HX; x++) {
                BlockState st = dead[ctx.rng().nextInt(dead.length)];
                RaidKit.fill(level, origin.offset(x, 1, z), origin.offset(x, 6, z + 1), st);
            }
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(order, new java.util.Random(ctx.seed() ^ (r * 0x9E3779B97F4A7C15L)));
            int jitter = ctx.rng().nextInt(60);
            for (int l = 0; l < LANES; l++) {
                offset[r][l] = (order.get(l) * (CYCLE / LANES) + jitter) % CYCLE;
                dirSign[r][l] = ctx.rng().nextBoolean() ? 1 : -1;
                int cx = LANE_X[l];
                RaidKit.fill(level, origin.offset(cx - 1, 1, z), origin.offset(cx + 1, GATE_H, z + 1), Blocks.AIR.defaultBlockState());
                RaidKit.fill(level, origin.offset(cx - 1, GATE_H + 1, z), origin.offset(cx + 1, GATE_H + 2, z + 1), lintel);
                // 戸の両脇の柱は暗い海晶
                for (int s = -1; s <= 1; s += 2) {
                    RaidKit.fill(level, origin.offset(cx + s * 2, 1, z), origin.offset(cx + s * 2, GATE_H, z + 1), lintel);
                }
            }
        }
        // 飾り: 壁ぎわの死んだ珊瑚のこぶ (壁と壁のあいだの端)
        for (int i = 0; i < 12; i++) {
            int side = ctx.rng().nextBoolean() ? -1 : 1;
            int z = ctx.rng().nextInt(2 * HZ - 7) - HZ + 3;
            if (nearRow(z)) {
                continue;
            }
            int h = 1 + ctx.rng().nextInt(3);
            RaidKit.fill(level, origin.offset(side * (HX - 1), 1, z), origin.offset(side * (HX - 1), h, z), dead[ctx.rng().nextInt(dead.length)]);
        }
        // 出口の前の印 (越えた先)
        RaidKit.fill(level, origin.offset(-2, 0, -HZ + 1), origin.offset(2, 0, -HZ + 1), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    private static boolean nearRow(int z) {
        for (int rz : ROW_Z) {
            if (z >= rz - 1 && z <= rz + 2) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void start(RaidCtx ctx) {
        ctxRef = ctx;
        t0 = ctx.now();
        done = false;
        clearAllGates(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        done = false;
        clearAllGates(ctx);
    }

    private void clearAllGates(RaidCtx ctx) {
        for (int r = 0; r < ROWS; r++) {
            for (int l = 0; l < LANES; l++) {
                for (int k = 0; k < 3; k++) {
                    setColumn(ctx, r, l, k, Blocks.AIR.defaultBlockState());
                }
                shown[r][l] = 0;
                carpets[r][l] = 0;
            }
        }
    }

    // 戸 (r, l) の、伸びる順 k 番目の柱の x。dirSign=+1 なら西→東、-1 なら東→西
    private int columnX(int r, int l, int k) {
        return LANE_X[l] + dirSign[r][l] * (k - 1);
    }

    private void setColumn(RaidCtx ctx, int r, int l, int k, BlockState st) {
        int x = columnX(r, l, k);
        int z = ROW_Z[r];
        ServerLevel level = ctx.level();
        if (st == SIGN) {
            RaidKit.set(level, origin.offset(x, 1, z), st);
            RaidKit.set(level, origin.offset(x, 1, z + 1), st);
            return;
        }
        for (int y = 1; y <= GATE_H; y++) {
            BlockState put = st.isAir() ? st : (y % 2 == 0 ? GROWTH_A : GROWTH_B);
            RaidKit.set(level, origin.offset(x, y, z), put);
            RaidKit.set(level, origin.offset(x, y, z + 1), put);
        }
    }

    private int phaseOf(int r, int l, long elapsed) {
        return (int) ((elapsed + offset[r][l]) % CYCLE);
    }

    // その位相で塞がっているべき柱の数
    static int targetColumns(int p) {
        if (p < CLOSE_AT) {
            return 0;
        }
        if (p < OPEN_AT) {
            return Math.min(3, (p - CLOSE_AT) / STEP + 1);
        }
        int open = Math.min(3, (p - OPEN_AT) / STEP + 1);
        return Math.max(0, 3 - open);
    }

    // 予告の絨毯の数
    static int warnColumns(int p) {
        if (p < WARN_AT || p >= OPEN_AT) {
            return 0;
        }
        return Math.min(3, (p - WARN_AT) / STEP + 1);
    }

    private boolean occupied(int r, int l, int k, List<ServerPlayer> players) {
        int x = columnX(r, l, k);
        AABB box = new AABB(origin.getX() + x, origin.getY() + 1, origin.getZ() + ROW_Z[r], origin.getX() + x + 1, origin.getY() + GATE_H + 1,
                origin.getZ() + ROW_Z[r] + 2).inflate(0.3, 0, 0.3);
        for (ServerPlayer p : players) {
            if (p.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(RaidCtx ctx) {
        long el = ctx.now() - t0;
        List<ServerPlayer> players = ctx.players();
        ServerLevel level = ctx.level();
        for (int r = 0; r < ROWS; r++) {
            for (int l = 0; l < LANES; l++) {
                int p = phaseOf(r, l, el);
                int target = targetColumns(p);
                int before = shown[r][l];
                // 伸びる (人が立つ柱は待つ)・引く
                while (shown[r][l] < target && !occupied(r, l, shown[r][l], players)) {
                    setColumn(ctx, r, l, shown[r][l], GROWTH_A);
                    shown[r][l]++;
                }
                while (shown[r][l] > target) {
                    shown[r][l]--;
                    setColumn(ctx, r, l, shown[r][l], Blocks.AIR.defaultBlockState());
                }
                if (shown[r][l] != before) {
                    boolean grew = shown[r][l] > before;
                    int x = columnX(r, l, Math.max(shown[r][l], before) - 1);
                    level.playSound(null, origin.offset(x, 2, ROW_Z[r]), grew ? SoundEvents.AMETHYST_BLOCK_PLACE : SoundEvents.AMETHYST_BLOCK_BREAK,
                            SoundSource.BLOCKS, 1.0f, grew ? 0.8f : 1.2f);
                }
                // 予告の絨毯: 伸びる順に、まだ塞がっていない柱に
                int want = warnColumns(p);
                for (int k = 0; k < 3; k++) {
                    boolean carpet = k >= shown[r][l] && k < want;
                    boolean had = (carpets[r][l] & (1 << k)) != 0;
                    if (carpet && !had) {
                        setColumn(ctx, r, l, k, SIGN);
                        carpets[r][l] |= 1 << k;
                    } else if (!carpet && had) {
                        if (k >= shown[r][l]) {
                            setColumn(ctx, r, l, k, Blocks.AIR.defaultBlockState());
                        }
                        carpets[r][l] &= ~(1 << k);
                    }
                }
                if (ctx.now() % 8 == 0 && want > 0 && shown[r][l] < 3) {
                    int x = columnX(r, l, Math.min(2, Math.max(shown[r][l], want - 1)));
                    level.sendParticles(TideRaid.dust(1f, 0.6f, 0.85f, 1.1f), origin.getX() + x + 0.5, origin.getY() + 2.2, origin.getZ() + ROW_Z[r] + 1.0, 3, 0.25, 0.8, 0.5, 0);
                }
            }
        }
        if (!done) {
            List<ServerPlayer> standing = ctx.standing();
            if (!standing.isEmpty()) {
                boolean all = true;
                for (ServerPlayer p : standing) {
                    if (p.getZ() >= origin.getZ() + ROW_Z[ROWS - 1]) {
                        all = false;
                    }
                }
                done = all;
            }
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.TR4.cross";
    }

    @Override
    public int counter() {
        if (done) {
            return ROWS;
        }
        int best = 0;
        if (ctxRef != null) {
            for (ServerPlayer p : ctxRef.standing()) {
                best = Math.max(best, passedBy(p));
            }
        }
        return best;
    }

    @Override
    public int counterMax() {
        return ROWS;
    }

    // 試験用・目的の数: その人が越えた壁の数
    public int passedBy(ServerPlayer p) {
        int n = 0;
        for (int r = 0; r < ROWS; r++) {
            if (p.getZ() < origin.getZ() + ROW_Z[r]) {
                n = r + 1;
            }
        }
        return n;
    }

    public int shownColumns(int row, int lane) {
        return shown[row][lane];
    }

    public boolean carpetAt(RaidCtx ctx, int row, int lane, int k) {
        return ctx.level().getBlockState(origin.offset(columnX(row, lane, k), 1, ROW_Z[row])).is(SIGN.getBlock());
    }

    public boolean solidAt(RaidCtx ctx, int row, int lane, int k) {
        return !ctx.level().getBlockState(origin.offset(columnX(row, lane, k), 2, ROW_Z[row])).isAir();
    }

    public int phase(RaidCtx ctx, int row, int lane) {
        return phaseOf(row, lane, ctx.now() - t0);
    }

    // この戸が完全に開いているか (柱が一本も無い)
    public boolean fullyOpen(int row, int lane) {
        return shown[row][lane] == 0;
    }

    // 戸の中央の立つ位置
    public BlockPos gateSpot(int row, int lane) {
        return origin.offset(LANE_X[lane], 1, ROW_Z[row]);
    }

    // 戸の次に伸びる柱の位置 (押さえの試験用)
    public BlockPos nextColumn(int row, int lane) {
        return origin.offset(columnX(row, lane, Math.min(2, shown[row][lane])), 1, ROW_Z[row]);
    }

    public int rowZ(int row) {
        return ROW_Z[row];
    }
}
