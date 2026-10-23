package jp.revati.skyabyss.trial.raid.ember;

import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 歩 ER4「落ちる鎖の道」(docs/design/13_ember.md)。熾の海の上に、鎖で吊った板の橋が三本。島を三つ越えて、奥の戸へ行く。
//  - 橋に誰かが乗ると、その橋は手前の板から順に落ちる。板は落ちる 1 秒前に、赤い輪郭 (予告) と熱い色と鎖の音で知らせる。歩いて渡れば先に行ける
//  - 落ちた人は、手前の島へ戻される (傷なし)。手前に人がいれば、落ちた橋は少しして一枚ずつ吊り直される
//  - 全員が橋の向こうの下り段へ着くと越える。一人でも渡れる。手分けするなら、先に渡った人は向こうの島で待て、落ちた仲間は手前から渡り直せる
//  - 全滅・リセットで、橋は全部吊り直される
public final class EmberChainPath extends EmberRoom {
    public static final String ID = "ER4";
    public static final int HX = 9;
    public static final int HZ = 27;
    public static final int PLANK_GAP = 5;
    public static final int PLANK_WARN = 20;
    public static final int REBUILD_WAIT = 60;
    public static final int REBUILD_GAP = 3;
    public static final int DECK = 3;
    private static final BlockState PLANK = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState HOT = Blocks.MAGMA_BLOCK.defaultBlockState();

    // 島 (北 = 小さい z へ並ぶ): {大きい z の端, 小さい z の端}。橋: {手前 (大きい z)、奥 (小さい z)} の板の列
    private static final int[][] ISLANDS = {{16, 12}, {3, -1}, {-10, -14}, {-21, -23}};
    private static final int[][] SPANS = {{11, 4}, {-2, -9}, {-15, -20}};
    // これより奥 (z < -23) の下り段へ着いたら渡り終え
    private static final int EXIT_Z = -23;

    private static final int INTACT = 0;
    private static final int FALLING = 1;
    private static final int GONE = 2;
    private static final int REBUILDING = 3;

    private final int[] spanState = new int[SPANS.length];
    private final long[] spanT = new long[SPANS.length];
    private boolean done;
    private long t;
    private int falls;
    private int crossed;
    private int expected = 1;

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, 10, RaidKit.EMBER));
        var lv = ctx.level();
        // 熾の海 (床をところどころマグマと玄武岩に。ctx.rng() で毎回少し変わる)
        int salt = ctx.rng().nextInt(16);
        for (int x = -HX + 1; x < HX; x++) {
            for (int z = -HZ + 1; z < HZ; z++) {
                int h = (x * 7 + z * 13 + salt) & 15;
                if (h < 4) {
                    set(ctx, ctx.local(x, 0, z), b(Blocks.MAGMA_BLOCK));
                } else if (h < 9) {
                    set(ctx, ctx.local(x, 0, z), b(Blocks.SMOOTH_BASALT));
                }
            }
        }
        // 上り段 (手前)・島・下り段
        for (int i = 0; i < 3; i++) {
            RaidKit.fill(lv, ctx.local(-4, 1, 19 - i), ctx.local(4, i + 1, 19 - i), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        }
        for (int[] isl : ISLANDS) {
            RaidKit.fill(lv, ctx.local(-4, 1, isl[1]), ctx.local(4, DECK - 1, isl[0]), b(Blocks.BASALT));
            RaidKit.fill(lv, ctx.local(-4, DECK, isl[1]), ctx.local(4, DECK, isl[0]), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            for (int[] c : new int[][] {{-4, isl[0]}, {4, isl[0]}, {-4, isl[1]}, {4, isl[1]}}) {
                RaidKit.fill(lv, ctx.local(c[0], DECK + 1, c[1]), ctx.local(c[0], DECK + 2, c[1]), b(Blocks.POLISHED_BASALT));
                set(ctx, ctx.local(c[0], DECK + 3, c[1]), b(Blocks.SHROOMLIGHT));
            }
        }
        RaidKit.fill(lv, ctx.local(-4, 1, -24), ctx.local(4, 2, -24), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        RaidKit.fill(lv, ctx.local(-4, 1, -25), ctx.local(4, 1, -25), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        // 橋の手すり (鉄格子) と吊り鎖 (天井から)
        for (int[] sp : SPANS) {
            for (int z = sp[1]; z <= sp[0]; z++) {
                for (int x : new int[] {-2, 2}) {
                    set(ctx, ctx.local(x, DECK + 1, z), b(Blocks.IRON_BARS));
                }
            }
            for (int z = sp[1]; z <= sp[0]; z += 3) {
                for (int x : new int[] {-2, 2}) {
                    RaidKit.fill(lv, ctx.local(x, DECK + 2, z), ctx.local(x, 10, z), b(Blocks.CHAIN));
                }
            }
        }
        resetState(ctx);
    }

    private void setRow(RaidCtx ctx, int z, BlockState st) {
        for (int x = -1; x <= 1; x++) {
            set(ctx, ctx.local(x, DECK, z), st);
        }
    }

    private void resetState(RaidCtx ctx) {
        for (int s = 0; s < SPANS.length; s++) {
            spanState[s] = INTACT;
            spanT[s] = 0;
            for (int z = SPANS[s][1]; z <= SPANS[s][0]; z++) {
                setRow(ctx, z, PLANK);
            }
        }
        done = false;
        t = 0;
        falls = 0;
        crossed = 0;
    }

    @Override
    public void start(RaidCtx ctx) {
        resetState(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        failRequested = false;
        resetState(ctx);
    }

    private int spanLen(int s) {
        return SPANS[s][0] - SPANS[s][1] + 1;
    }

    private static double lzb(RaidCtx ctx, ServerPlayer p) {
        return p.getZ() - ctx.origin().getZ();
    }

    private boolean onSpan(RaidCtx ctx, ServerPlayer p, int s) {
        double lx = p.getX() - (ctx.origin().getX() + 0.5);
        double lz = lzb(ctx, p);
        return Math.abs(lx) <= 2.2 && lz >= SPANS[s][1] && lz < SPANS[s][0] + 1 && Math.abs(p.getY() - (ctx.origin().getY() + DECK + 1)) < 1.2;
    }

    // 手前から数えて j 枚目の板の「落ちるぞ」の印
    private void warnRow(RaidCtx ctx, int s, int j) {
        int z = SPANS[s][0] - j;
        setRow(ctx, z, HOT);
        double zc = ctx.origin().getZ() + z + 0.5;
        Telegraph.in(ctx.level(), new TelegraphShape.Line(ctx.origin().getX() - 1, zc, Telegraph.angleOf(1, 0), 3, 0.5)).floor(ctx.origin().getY() + DECK + 1)
                .warn(PLANK_WARN).danger().onHit((lvl, p) -> {
                }).start();
        ctx.level().playSound(null, ctx.local(0, DECK, z), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.7f, 0.7f);
        particles(ctx, ParticleTypes.SMOKE, ctx.origin().getX() + 0.5, ctx.origin().getY() + DECK + 1.2, zc, 6, 0.8, 0.02);
    }

    private void dropRow(RaidCtx ctx, int s, int j) {
        int z = SPANS[s][0] - j;
        setRow(ctx, z, AIR);
        particles(ctx, ParticleTypes.LAVA, ctx.origin().getX() + 0.5, ctx.origin().getY() + DECK + 0.5, ctx.origin().getZ() + z + 0.5, 4, 0.8, 0.1);
        ctx.level().playSound(null, ctx.local(0, DECK, z), SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 0.8f, 0.8f);
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done) {
            return;
        }
        t++;
        List<ServerPlayer> ps = who(ctx);
        double fy = ctx.origin().getY();
        // 橋に乗ったら、その橋は落ち始める
        for (int s = 0; s < SPANS.length; s++) {
            if (spanState[s] == INTACT) {
                for (ServerPlayer p : ps) {
                    if (onSpan(ctx, p, s)) {
                        spanState[s] = FALLING;
                        spanT[s] = t;
                        break;
                    }
                }
            }
        }
        for (int s = 0; s < SPANS.length; s++) {
            int len = spanLen(s);
            long dt = t - spanT[s];
            if (spanState[s] == FALLING) {
                for (int j = 0; j < len; j++) {
                    if (dt == (long) j * PLANK_GAP) {
                        warnRow(ctx, s, j);
                    }
                    if (dt == (long) j * PLANK_GAP + PLANK_WARN) {
                        dropRow(ctx, s, j);
                    }
                }
                if (dt >= (long) (len - 1) * PLANK_GAP + PLANK_WARN) {
                    spanState[s] = GONE;
                    spanT[s] = t;
                }
            } else if (spanState[s] == GONE) {
                boolean waiting = false;
                for (ServerPlayer p : ps) {
                    double lz = lzb(ctx, p);
                    if (lz >= SPANS[s][0] + 1 && lz < HZ) {
                        waiting = true;
                    }
                }
                if (!waiting) {
                    spanT[s] = t;
                } else if (dt >= REBUILD_WAIT) {
                    spanState[s] = REBUILDING;
                    spanT[s] = t;
                }
            } else if (spanState[s] == REBUILDING) {
                for (int j = 0; j < len; j++) {
                    if (dt - 1 == (long) j * REBUILD_GAP) {
                        setRow(ctx, SPANS[s][0] - j, PLANK);
                        ctx.level().playSound(null, ctx.local(0, DECK, SPANS[s][0] - j), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.1f);
                    }
                }
                if (dt - 1 >= (long) (len - 1) * REBUILD_GAP) {
                    spanState[s] = INTACT;
                }
            }
        }
        // 落ちた人は、手前の島へ (傷なし)
        for (ServerPlayer p : ps) {
            double lz = lzb(ctx, p);
            if (lz >= EXIT_Z && lz < 18 && p.getY() < fy + DECK + 0.2 && p.getY() > fy - 4) {
                int pick = 0;
                for (int i = 0; i < ISLANDS.length; i++) {
                    if (ISLANDS[i][1] >= lz) {
                        pick = i;
                    }
                }
                falls++;
                moveTo(p, ctx.origin().getX() + 0.5, fy + DECK + 1, ctx.origin().getZ() + ISLANDS[pick][0] - 0.5);
                ctx.level().playSound(null, p.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.8f);
            }
        }
        // 渡り終えた人数
        expected = Math.max(1, scale(ctx));
        int n = 0;
        for (ServerPlayer p : ps) {
            if (lzb(ctx, p) < EXIT_Z) {
                n++;
            }
        }
        crossed = n;
        if (!ps.isEmpty() && n == ps.size()) {
            done = true;
            particles(ctx, ParticleTypes.LAVA, ctx.origin().getX() + 0.5, fy + 3, ctx.origin().getZ() - HZ + 2, 20, 1.5, 0.2);
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.ER4.cross";
    }

    @Override
    public int counter() {
        return crossed;
    }

    @Override
    public int counterMax() {
        return expected;
    }

    // 自己テスト用の窓
    int spanState(int s) {
        return spanState[s];
    }

    int falls() {
        return falls;
    }

    int spans() {
        return SPANS.length;
    }

    int planks(int s) {
        return spanLen(s);
    }

    boolean plankAt(RaidCtx ctx, int s, int j) {
        return !ctx.level().getBlockState(ctx.local(0, DECK, SPANS[s][0] - j)).isAir();
    }

    // 橋 s の手前の端の板の上 / 奥の端の次 (島) / 島 i の真ん中
    BlockPos spanHead(RaidCtx ctx, int s) {
        return ctx.local(0, DECK + 1, SPANS[s][0]);
    }

    BlockPos islandAt(RaidCtx ctx, int i) {
        return ctx.local(0, DECK + 1, ISLANDS[i][0] - 2);
    }

    BlockPos exitStep(RaidCtx ctx) {
        return ctx.local(0, 3, -24);
    }

    // 行き先の印: 奥の下り段 (戸の前)
    @Override
    public BlockPos guideTarget(RaidCtx ctx) {
        return done ? null : ctx.local(0, 2, -25);
    }
}
