package jp.revati.skyabyss.trial.raid.storm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 智 SR5「雷の拍子覚え」: 3×3 の銅の板 (一枚 3×3 ブロック) が九枚。拍に合わせて光る順を見て、同じ順に、拍に合わせて踏む。
//  - 順は挑戦ごとの乱数 (長さ 6 の一本。一周目は 3、二周目は 4、三周目は 5、四周目は 6 まで)。次の板は、前の板の隣 (斜めを含む) なので、一人でも間に合う
//  - 一拍 = BEAT tick。i 番目の板は、i 拍目の始まりから終わりまでの間に (あらかじめ乗って待っていてもよい) 踏まれていること。拍のかなり前には数えない
//  - 順にない板へ踏み込む・拍に間に合わない = 失敗 (音が鳴り、同じ周を見せ直す。傷はない)。広間の板の間の筋は歩ける
//  - 仲間と手分けするなら、見せている間に次の板へそれぞれ立って待つ。全滅・やり直しは最初の周から (順は変わらない)
public final class BeatTiles extends RaidRoom {
    static final int HX = 8;
    static final int HZ = 8;
    static final int H = 8;
    static final int N = 3;
    static final int ROUNDS = 4;
    static final int MAXLEN = ROUNDS + 2;
    static final int BEAT = 30;
    static final int GRACE = 8;
    static final int PREP = 50;
    static final int GAP = 30;
    static final int PAUSE = 44;
    static final int STEP = 5;

    public enum Phase {
        IDLE, PREP, SHOW, GAP, INPUT, PASS, FAIL, DONE
    }

    // 順を作る (長さ MAXLEN。前の板の隣で、同じ板は続かない)。板の番号 = z * N + x (0..8)
    static int[] generate(RandomSource rng) {
        int[] seq = new int[MAXLEN];
        seq[0] = rng.nextInt(N * N);
        for (int i = 1; i < MAXLEN; i++) {
            int px = seq[i - 1] % N;
            int pz = seq[i - 1] / N;
            int pick;
            do {
                int nx = px + rng.nextInt(3) - 1;
                int nz = pz + rng.nextInt(3) - 1;
                pick = nx < 0 || nx >= N || nz < 0 || nz >= N || nx == px && nz == pz ? -1 : nz * N + nx;
            } while (pick < 0);
            seq[i] = pick;
        }
        return seq;
    }

    private int[] seq = new int[MAXLEN];
    private Phase phase = Phase.IDLE;
    private int phaseAge;
    private int round;
    private int next;
    private int generation;
    private final Map<UUID, Integer> on = new HashMap<>();
    private boolean started;

    // 板の中心の相対座標 (x, z)
    static int tileC(int i) {
        return (i - 1) * STEP;
    }

    // この位置 (ワールドの x, z) が乗っている板の番号 (なければ -1)。原点 = 部屋の床の中心のブロック
    static int tileAt(BlockPos origin, double x, double z) {
        for (int tz = 0; tz < N; tz++) {
            for (int tx = 0; tx < N; tx++) {
                double x0 = origin.getX() + tileC(tx) - 1;
                double z0 = origin.getZ() + tileC(tz) - 1;
                if (x >= x0 && x < x0 + 3 && z >= z0 && z < z0 + 3) {
                    return tz * N + tx;
                }
            }
        }
        return -1;
    }

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.STORM));
        BlockPos o = c.origin();
        seq = generate(ctx.rng());
        for (int i = 0; i < N * N; i++) {
            paint(ctx, i, DIM);
        }
        // 四隅の飾りの柱 (板に重ならない)
        BlockState cut = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * (HX - 1);
                int z = sz * (HZ - 1);
                RaidKit.fill(level, o.offset(x, 1, z), o.offset(x, H - 3, z), cut);
                RaidKit.set(level, o.offset(x, H - 2, z), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                RaidKit.set(level, o.offset(x, H - 1, z), Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
    }

    private static final BlockState DIM = Blocks.WAXED_WEATHERED_COPPER.defaultBlockState();
    private static final BlockState SHOWN = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState HIT = Blocks.VERDANT_FROGLIGHT.defaultBlockState();

    private void paint(RaidCtx ctx, int i, BlockState st) {
        BlockPos o = ctx.origin();
        int cx = tileC(i % N);
        int cz = tileC(i / N);
        RaidKit.fill(ctx.level(), o.offset(cx - 1, 0, cz - 1), o.offset(cx + 1, 0, cz + 1), st);
    }

    private void paintAll(RaidCtx ctx, BlockState st) {
        for (int i = 0; i < N * N; i++) {
            paint(ctx, i, st);
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        started = true;
        round = 0;
        enter(Phase.PREP);
        paintAll(ctx, DIM);
    }

    private void enter(Phase p) {
        phase = p;
        phaseAge = 0;
    }

    private int len() {
        return Math.min(MAXLEN, 3 + round);
    }

    @Override
    public void tick(RaidCtx ctx) {
        // 板への踏み込みを見る (乗っていた板が変わった = 踏み込み)
        Map<UUID, Integer> was = new HashMap<>(on);
        on.clear();
        for (ServerPlayer p : ctx.standing()) {
            int now = ctx.cell().inArena(p.getX(), p.getZ()) && Math.abs(p.getY() - (ctx.origin().getY() + 1)) < 1.6 ? tileAt(ctx.origin(), p.getX(), p.getZ()) : -1;
            on.put(p.getUUID(), now);
            if (now >= 0 && now != was.getOrDefault(p.getUUID(), -1)) {
                enterTile(ctx, now);
            }
        }
        advance(ctx, occupied(ctx));
    }

    private boolean[] occupied(RaidCtx ctx) {
        boolean[] occ = new boolean[N * N];
        for (int t : on.values()) {
            if (t >= 0) {
                occ[t] = true;
            }
        }
        return occ;
    }

    // 時間を一つ進める。occ = 乗っている板 (試験では自分で渡す)
    void advance(RaidCtx ctx, boolean[] occ) {
        if (!started) {
            return;
        }
        ServerLevel level = ctx.level();
        phaseAge++;
        switch (phase) {
            case PREP -> {
                if (phaseAge >= PREP) {
                    ctx.say(Component.translatable("message.skyabyss.raid.SR5.round", round + 1, ROUNDS));
                    enter(Phase.SHOW);
                }
            }
            case SHOW -> {
                int slot = (phaseAge - 1) / BEAT;
                int at = (phaseAge - 1) % BEAT;
                if (slot < len()) {
                    if (at == 0) {
                        paintAll(ctx, DIM);
                        paint(ctx, seq[slot], SHOWN);
                        note(level, ctx, seq[slot], 1.0f);
                    } else if (at == BEAT * 2 / 3) {
                        paint(ctx, seq[slot], DIM);
                    }
                } else {
                    paintAll(ctx, DIM);
                    enter(Phase.GAP);
                }
            }
            case GAP -> {
                if (phaseAge >= GAP) {
                    next = 0;
                    ctx.say(Component.translatable("message.skyabyss.raid.SR5.go"));
                    enter(Phase.INPUT);
                }
            }
            case INPUT -> {
                int t = phaseAge - 1;
                if (t % BEAT == 0 && t / BEAT < len() + 1) {
                    level.playSound(null, ctx.origin().above(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.7f, 1.2f);
                }
                // 乗って待っていた板は、拍が来れば数える
                if (next < len() && t >= next * BEAT - GRACE && occ[seq[next]]) {
                    hitTile(ctx);
                }
                if (phase == Phase.INPUT && next < len() && t > (next + 1) * BEAT + GRACE) {
                    fail(ctx);
                }
            }
            case PASS -> {
                if (phaseAge >= PAUSE) {
                    round++;
                    if (round >= ROUNDS) {
                        enter(Phase.DONE);
                    } else {
                        paintAll(ctx, DIM);
                        enter(Phase.SHOW);
                    }
                }
            }
            case FAIL -> {
                if (phaseAge >= PAUSE) {
                    paintAll(ctx, DIM);
                    enter(Phase.SHOW);
                }
            }
            default -> {
            }
        }
    }

    // 板へ踏み込んだ (入力の間だけ意味がある)。順にない板なら失敗
    void enterTile(RaidCtx ctx, int tile) {
        if (phase != Phase.INPUT || next >= len()) {
            return;
        }
        if (tile != seq[next]) {
            fail(ctx);
        }
    }

    // 順どおりの板が拍の中で踏まれた
    private void hitTile(RaidCtx ctx) {
        paint(ctx, seq[next], HIT);
        note(ctx.level(), ctx, seq[next], 1.0f);
        next++;
        if (next >= len()) {
            paintAll(ctx, HIT);
            ctx.level().playSound(null, ctx.origin().above(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 1.0f);
            ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, ctx.origin().getX() + 0.5, ctx.origin().getY() + 2, ctx.origin().getZ() + 0.5, 60, 4.0, 1.0, 4.0, 0.1);
            enter(Phase.PASS);
        }
    }

    private void fail(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        level.playSound(null, ctx.origin().above(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.4f, 0.5f);
        level.sendParticles(ParticleTypes.SMOKE, ctx.origin().getX() + 0.5, ctx.origin().getY() + 1.5, ctx.origin().getZ() + 0.5, 40, 4.0, 0.5, 4.0, 0.02);
        paintAll(ctx, DIM);
        ctx.say(Component.translatable("message.skyabyss.raid.SR5.miss"));
        enter(Phase.FAIL);
    }

    private void note(ServerLevel level, RaidCtx ctx, int tile, float vol) {
        int cx = tileC(tile % N);
        int cz = tileC(tile / N);
        BlockPos at = ctx.origin().offset(cx, 1, cz);
        float pitch = 0.6f + 0.15f * tile;
        level.playSound(null, at, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, vol * 1.3f, pitch);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 14, 0.8, 0.2, 0.8, 0.05);
    }

    @Override
    public boolean isComplete() {
        return phase == Phase.DONE;
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        round = 0;
        next = 0;
        started = false;
        on.clear();
        phase = Phase.IDLE;
        phaseAge = 0;
        paintAll(ctx, DIM);
    }

    @Override
    public String objectiveKey() {
        return phase == Phase.INPUT || phase == Phase.GAP ? "raid.skyabyss.SR5.step" : "raid.skyabyss.SR5.watch";
    }

    @Override
    public int counter() {
        return Math.min(round, ROUNDS);
    }

    @Override
    public int counterMax() {
        return ROUNDS;
    }

    // ---- 試験用
    public Phase phase() {
        return phase;
    }

    public int phaseAge() {
        return phaseAge;
    }

    public int round() {
        return round;
    }

    public int next() {
        return next;
    }

    public int length() {
        return len();
    }

    public int[] seq() {
        return seq;
    }

    public int generation() {
        return generation;
    }

    public BlockState tileState(RaidCtx ctx, int i) {
        return ctx.level().getBlockState(ctx.origin().offset(tileC(i % N), 0, tileC(i / N)));
    }
}
