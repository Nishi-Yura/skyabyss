package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 智 WR2「共鳴の骨」: 四隅の骨が鳴らす順を、同じ順に叩く。
//  - 順は五つの音。ところどころ二本の骨が同時に鳴る (和音)。和音は二本を CHORD_WINDOW の間に叩けばよい (一人なら走って、二人なら同時に叩ける)
//  - 叩く (右でも左でも) と、その骨が鳴る。順を間違えたら、傷はなく、順をもう一度聴かせる (最初から)
//  - 順は挑戦ごとに新しい (和音の位置・骨の並び)
public final class WhaleBonesRoom extends RaidRoom {
    static final int HX = 13;
    static final int HZ = 13;
    static final int H = 9;
    static final int BONES = 4;
    static final int LENGTH = 5;
    // 骨の足元の隅 (原点からの相対。2×2)
    static final int[][] CORNER = {{-9, -9}, {8, -9}, {-9, 8}, {8, 8}};
    static final float[] PITCH = {0.63f, 0.84f, 1.12f, 1.5f};
    static final int NOTE_TICKS = 26;
    static final int LIT_TICKS = 16;
    static final int CHORD_WINDOW = 160;
    static final int LISTEN_DELAY = 50;
    static final int RESUME_DELAY = 50;

    private enum Mode {
        LISTEN, INPUT, DONE
    }

    private final List<int[]> seq = new ArrayList<>();
    private Mode mode = Mode.LISTEN;
    private long modeAt;
    private int progress;
    private final Set<Integer> struck = new HashSet<>();
    private long chordAt;
    private final long[] litUntil = new long[BONES];
    private int lastNoteIdx = -1;
    private int mistakes;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        for (int i = 0; i < BONES; i++) {
            int x = CORNER[i][0];
            int z = CORNER[i][1];
            RaidKit.fill(level, c.origin().offset(x, 1, z), c.origin().offset(x + 1, 1, z + 1), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            RaidKit.fill(level, c.origin().offset(x, 2, z), c.origin().offset(x + 1, 4, z + 1), Blocks.BONE_BLOCK.defaultBlockState());
            setTop(ctx, i, false);
        }
        // 中央の小さな円い台 (飾り)
        RaidKit.fill(level, c.origin().offset(-1, 0, -1), c.origin().offset(1, 0, 1), Blocks.BONE_BLOCK.defaultBlockState());
        // 順 (挑戦ごと): 五つ。最初の一つ以外の中から、二つまで和音
        List<Integer> chords = new ArrayList<>();
        while (chords.size() < 2) {
            int at = 1 + ctx.rng().nextInt(LENGTH - 1);
            if (!chords.contains(at)) {
                chords.add(at);
            }
        }
        int prev = -1;
        for (int i = 0; i < LENGTH; i++) {
            int a;
            do {
                a = ctx.rng().nextInt(BONES);
            } while (a == prev);
            if (chords.contains(i)) {
                int b;
                do {
                    b = ctx.rng().nextInt(BONES);
                } while (b == a);
                seq.add(new int[] {a, b});
            } else {
                seq.add(new int[] {a});
            }
            prev = a;
        }
        // 飾り (docs/design/39 の 4.1): 四隅の骨をパイプオルガンに、床に歌の輪
        WhaleDecor.wr2(ctx, CORNER);
        RaidKit.brightenFloor(ctx, 2);
    }

    @Override
    public void start(RaidCtx ctx) {
        beginListen(ctx, LISTEN_DELAY);
    }

    private void beginListen(RaidCtx ctx, int delay) {
        mode = Mode.LISTEN;
        modeAt = ctx.now() + delay;
        progress = 0;
        struck.clear();
        for (int i = 0; i < BONES; i++) {
            setTop(ctx, i, false);
        }
    }

    @Override
    public void tick(RaidCtx ctx) {
        long now = ctx.now();
        // 消える頃の灯り
        for (int i = 0; i < BONES; i++) {
            if (litUntil[i] != 0 && now >= litUntil[i] && mode != Mode.DONE) {
                litUntil[i] = 0;
                setTop(ctx, i, false);
            }
        }
        if (mode == Mode.LISTEN) {
            long t = now - modeAt;
            if (t < 0) {
                return;
            }
            int step = (int) (t / NOTE_TICKS);
            if (step >= LENGTH) {
                mode = Mode.INPUT;
                modeAt = now;
                lastNoteIdx = -1;
                return;
            }
            if (t % NOTE_TICKS == 0 && step != lastNoteIdx) {
                lastNoteIdx = step;
                for (int b : seq.get(step)) {
                    ring(ctx, b, LIT_TICKS);
                }
            }
        } else if (mode == Mode.INPUT && !struck.isEmpty() && now - chordAt > CHORD_WINDOW) {
            // 和音の片方だけで時間切れ
            wrong(ctx);
        }
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        int bone = boneAt(ctx, pos);
        if (bone < 0) {
            return false;
        }
        if (mode == Mode.INPUT) {
            strike(ctx, bone);
        }
        return true;
    }

    // 骨 bone を叩く (入力の時だけ効く。試験からも呼ぶ)
    public void strike(RaidCtx ctx, int bone) {
        if (mode != Mode.INPUT) {
            return;
        }
        ring(ctx, bone, LIT_TICKS);
        int[] want = seq.get(progress);
        boolean in = false;
        for (int w : want) {
            in |= w == bone;
        }
        if (!in || struck.contains(bone)) {
            wrong(ctx);
            return;
        }
        if (struck.isEmpty()) {
            chordAt = ctx.now();
        }
        struck.add(bone);
        if (struck.size() >= want.length) {
            struck.clear();
            progress++;
            if (progress >= LENGTH) {
                mode = Mode.DONE;
                WhaleDecor.exitLamps(ctx, true);
                for (int i = 0; i < BONES; i++) {
                    setTop(ctx, i, true);
                    litUntil[i] = 0;
                }
                ServerLevel level = ctx.level();
                level.playSound(null, ctx.origin().above(2), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.8f);
            }
        }
    }

    private void wrong(RaidCtx ctx) {
        mistakes++;
        ServerLevel level = ctx.level();
        level.playSound(null, ctx.origin().above(2), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
        ctx.say(net.minecraft.network.chat.Component.translatable("message.skyabyss.raid.WR2.wrong"));
        beginListen(ctx, RESUME_DELAY);
    }

    private void ring(RaidCtx ctx, int bone, int ticks) {
        setTop(ctx, bone, true);
        litUntil[bone] = ctx.now() + ticks;
        BlockPos top = ctx.origin().offset(CORNER[bone][0], 5, CORNER[bone][1]);
        ServerLevel level = ctx.level();
        level.playSound(null, top, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.6f, PITCH[bone]);
        level.sendParticles(ParticleTypes.NOTE, top.getX() + 1.0, top.getY() + 0.8, top.getZ() + 1.0, 3, 0.3, 0.2, 0.3, 0.5);
    }

    // 骨の頭: 鳴っている間は灯る
    private void setTop(RaidCtx ctx, int bone, boolean lit) {
        BlockState st = lit ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState();
        int x = CORNER[bone][0];
        int z = CORNER[bone][1];
        RaidKit.fill(ctx.level(), ctx.origin().offset(x, 5, z), ctx.origin().offset(x + 1, 5, z + 1), st);
        WhaleDecor.organGlow(ctx, CORNER[bone], lit);
    }

    private int boneAt(RaidCtx ctx, BlockPos pos) {
        int lx = pos.getX() - ctx.origin().getX();
        int lz = pos.getZ() - ctx.origin().getZ();
        int ly = pos.getY() - ctx.origin().getY();
        if (ly < 1 || ly > 5) {
            return -1;
        }
        for (int i = 0; i < BONES; i++) {
            if (lx >= CORNER[i][0] && lx <= CORNER[i][0] + 1 && lz >= CORNER[i][1] && lz <= CORNER[i][1] + 1) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isComplete() {
        return mode == Mode.DONE;
    }

    @Override
    public void reset(RaidCtx ctx) {
        beginListen(ctx, LISTEN_DELAY);
        mode = Mode.LISTEN;
        WhaleDecor.exitLamps(ctx, false);
    }

    @Override
    public String objectiveKey() {
        return mode == Mode.INPUT ? "raid.skyabyss.WR2.strike" : "raid.skyabyss.WR2.listen";
    }

    @Override
    public int counter() {
        return progress;
    }

    @Override
    public int counterMax() {
        return LENGTH;
    }

    // 試験用
    public List<int[]> sequence() {
        return seq;
    }

    public boolean listening() {
        return mode == Mode.LISTEN;
    }

    // 和音のうち、もう叩いた本数
    int struckCount() {
        return struck.size();
    }

    boolean inputting() {
        return mode == Mode.INPUT;
    }

    public int mistakes() {
        return mistakes;
    }
}
