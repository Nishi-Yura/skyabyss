package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 智 TR2「貝の音合わせ」: 貝を開く (叩く・使う) と音が鳴る。同じ音の貝を探して、組 (対) にして開けたままにする。
//  - 一つ目を開け、二つ目を開ける。同じ音なら二つとも開いたまま。違えば、すこし見せてから閉じる (傷はなく、また開けられる)
//  - 音は色・粒の形・音の高さと楽器・画面の文字の全部で伝える (音を聞き分けられなくても、色と文字で組が分かる)
//  - 組の数は人数で増える (一人 3 組、二人 4 組、三人以上 5 組)。貝の並びと音は挑戦ごとに変わる
public final class TideShellRoom extends RaidRoom {
    static final int HX = 12;
    static final int HZ = 12;
    static final int H = 8;
    static final int TONES = 5;
    static final int SHOW_TICKS = 36;
    // 貝の置き場 (原点からの相対。縦横四ブロックおき)
    static final int[] GRID_X = {-8, -4, 0, 4, 8};
    static final int[] GRID_Z = {-7, -3, 1, 5};
    // 音ごとの色 (赤は危険の印なので使わない)
    static final BlockState[] TONE_BLOCK = {Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.PINK_CONCRETE.defaultBlockState(),
            Blocks.PURPLE_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState()};
    static final float[][] TONE_RGB = {{0.3f, 0.65f, 1f}, {1f, 0.55f, 0.8f}, {0.65f, 0.3f, 0.95f}, {1f, 0.9f, 0.3f}, {0.5f, 1f, 0.4f}};
    static final float[] TONE_PITCH = {0.7f, 0.9f, 1.1f, 1.35f, 1.7f};

    private enum Shell {
        CLOSED, OPEN, MATCHED
    }

    private int[] cx;
    private int[] cz;
    private int[] tone;
    private Shell[] state;
    private int pairs;
    private int first = -1;
    private long closeAt;
    private int mistakes;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        pairs = Math.min(TONES, 2 + ctx.scale());
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.TIDE));
        // 飾り: 壁ぎわの死んだ珊瑚のこぶ (灰色。色は貝だけのもの)
        BlockState[] dead = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
                Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};
        for (int i = 0; i < 14; i++) {
            int side = ctx.rng().nextInt(2) == 0 ? -1 : 1;
            int z = ctx.rng().nextInt(2 * HZ - 5) - HZ + 3;
            int h = 1 + ctx.rng().nextInt(3);
            RaidKit.fill(level, c.origin().offset(side * (HX - 1), 1, z), c.origin().offset(side * (HX - 1), h, z + 1), dead[ctx.rng().nextInt(dead.length)]);
        }
        // 貝の置き場から対の数だけ選ぶ
        List<int[]> spots = new ArrayList<>();
        for (int x : GRID_X) {
            for (int z : GRID_Z) {
                spots.add(new int[] {x, z});
            }
        }
        Collections.shuffle(spots, new java.util.Random(ctx.seed()));
        int n = pairs * 2;
        cx = new int[n];
        cz = new int[n];
        tone = new int[n];
        state = new Shell[n];
        List<Integer> tones = new ArrayList<>();
        for (int t = 0; t < pairs; t++) {
            tones.add(t);
            tones.add(t);
        }
        Collections.shuffle(tones, new java.util.Random(ctx.seed() ^ 0x5EEDL));
        for (int i = 0; i < n; i++) {
            cx[i] = spots.get(i)[0];
            cz[i] = spots.get(i)[1];
            tone[i] = tones.get(i);
            // 台 (暗い海のレンガ) と、貝の座
            RaidKit.fill(level, c.origin().offset(cx[i] - 1, 1, cz[i] - 1), c.origin().offset(cx[i] + 1, 1, cz[i] + 1), Blocks.PRISMARINE_BRICKS.defaultBlockState());
            RaidKit.set(level, c.origin().offset(cx[i], 2, cz[i]), Blocks.DARK_PRISMARINE.defaultBlockState());
            setTop(ctx, i, Shell.CLOSED);
        }
        // 中央の通り道の飾り: 巻貝の模様の円
        RaidKit.fill(level, c.origin().offset(-1, 0, 9), c.origin().offset(1, 0, 9), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    @Override
    public void start(RaidCtx ctx) {
        closeAll(ctx);
    }

    @Override
    public void tick(RaidCtx ctx) {
        long now = ctx.now();
        if (first >= 0 && closeAt != 0 && now >= closeAt) {
            // 違う音だった二つを閉じる
            for (int i = 0; i < state.length; i++) {
                if (state[i] == Shell.OPEN) {
                    setTop(ctx, i, Shell.CLOSED);
                    state[i] = Shell.CLOSED;
                    ctx.level().playSound(null, ctx.origin().offset(cx[i], 2, cz[i]), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
                }
            }
            first = -1;
            closeAt = 0;
        }
        // 開いている貝の小さなきらめき (組になった貝は消えない)
        if (now % 8 == 0) {
            for (int i = 0; i < state.length; i++) {
                if (state[i] != Shell.CLOSED) {
                    float[] rgb = TONE_RGB[tone[i]];
                    ctx.level().sendParticles(TideRaid.dust(rgb[0], rgb[1], rgb[2], 1f), ctx.origin().getX() + cx[i] + 0.5, ctx.origin().getY() + 3.4,
                            ctx.origin().getZ() + cz[i] + 0.5, 2, 0.25, 0.15, 0.25, 0);
                }
            }
        }
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        int i = shellAt(ctx, pos);
        if (i < 0) {
            return false;
        }
        open(ctx, i);
        return true;
    }

    // 貝 i を開ける (試験からも呼ぶ)。開いた・閉じる待ちの間・組になった貝は何もしない
    public void open(RaidCtx ctx, int i) {
        if (state[i] != Shell.CLOSED || closeAt != 0) {
            return;
        }
        state[i] = Shell.OPEN;
        setTop(ctx, i, Shell.OPEN);
        sound(ctx, i);
        if (first < 0) {
            first = i;
            return;
        }
        if (tone[first] == tone[i]) {
            state[first] = Shell.MATCHED;
            state[i] = Shell.MATCHED;
            first = -1;
            ServerLevel level = ctx.level();
            level.playSound(null, ctx.origin().above(2), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, TONE_PITCH[tone[i]]);
            for (int k = 0; k < 2; k++) {
                int s = k == 0 ? i : -1;
                if (s >= 0) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ctx.origin().getX() + cx[s] + 0.5, ctx.origin().getY() + 3.5, ctx.origin().getZ() + cz[s] + 0.5, 6, 0.3, 0.2, 0.3, 0);
                }
            }
            ctx.say(Component.translatable("message.skyabyss.raid.TR2.match", Component.translatable("raid.skyabyss.TR2.tone." + tone[i])));
        } else {
            mistakes++;
            closeAt = ctx.now() + SHOW_TICKS;
            ctx.say(Component.translatable("message.skyabyss.raid.TR2.wrong"));
        }
    }

    // 音と見た目: 色の粒・粒の形 (音 t なら t+3 点の輪)・楽器と高さ・画面の文字
    private void sound(RaidCtx ctx, int i) {
        ServerLevel level = ctx.level();
        int t = tone[i];
        double x = ctx.origin().getX() + cx[i] + 0.5;
        double y = ctx.origin().getY() + 3.2;
        double z = ctx.origin().getZ() + cz[i] + 0.5;
        SoundEvent inst = switch (t) {
            case 0 -> SoundEvents.NOTE_BLOCK_BELL.value();
            case 1 -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case 2 -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case 3 -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            default -> SoundEvents.NOTE_BLOCK_HARP.value();
        };
        level.playSound(null, x, y, z, inst, SoundSource.PLAYERS, 2.0f, TONE_PITCH[t]);
        float[] rgb = TONE_RGB[t];
        int points = t + 3;
        for (int k = 0; k < points; k++) {
            double a = Math.PI * 2 * k / points;
            level.sendParticles(TideRaid.dust(rgb[0], rgb[1], rgb[2], 1.6f), x + Math.cos(a) * 0.9, y + 0.2, z + Math.sin(a) * 0.9, 2, 0.04, 0.04, 0.04, 0);
        }
        level.sendParticles(ParticleTypes.NOTE, x, y + 0.8, z, 1, 0.1, 0.1, 0.1, 1.0);
        ctx.say(Component.translatable("message.skyabyss.raid.TR2.tone", Component.translatable("raid.skyabyss.TR2.tone." + t)));
    }

    private void setTop(RaidCtx ctx, int i, Shell s) {
        BlockPos p = ctx.origin().offset(cx[i], 3, cz[i]);
        BlockState top = s == Shell.CLOSED ? Blocks.CALCITE.defaultBlockState() : TONE_BLOCK[tone[i]];
        RaidKit.set(ctx.level(), p, top);
        // 開いた貝の上に、色の板 (くぼんだ口)
        RaidKit.set(ctx.level(), p.above(), s == Shell.CLOSED ? Blocks.AIR.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState());
    }

    private int shellAt(RaidCtx ctx, BlockPos pos) {
        int lx = pos.getX() - ctx.origin().getX();
        int lz = pos.getZ() - ctx.origin().getZ();
        int ly = pos.getY() - ctx.origin().getY();
        if (ly < 1 || ly > 4) {
            return -1;
        }
        for (int i = 0; i < cx.length; i++) {
            if (Math.abs(lx - cx[i]) <= 1 && Math.abs(lz - cz[i]) <= 1) {
                return i;
            }
        }
        return -1;
    }

    private void closeAll(RaidCtx ctx) {
        for (int i = 0; i < state.length; i++) {
            state[i] = Shell.CLOSED;
            setTop(ctx, i, Shell.CLOSED);
        }
        first = -1;
        closeAt = 0;
    }

    @Override
    public boolean isComplete() {
        if (state == null) {
            return false;
        }
        for (Shell s : state) {
            if (s != Shell.MATCHED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void reset(RaidCtx ctx) {
        closeAll(ctx);
    }

    @Override
    public String objectiveKey() {
        return first >= 0 ? "raid.skyabyss.TR2.second" : "raid.skyabyss.TR2.first";
    }

    @Override
    public int counter() {
        int n = 0;
        for (Shell s : state) {
            if (s == Shell.MATCHED) {
                n++;
            }
        }
        return n / 2;
    }

    @Override
    public int counterMax() {
        return pairs;
    }

    // 試験用
    public int shells() {
        return tone.length;
    }

    public int toneOf(int i) {
        return tone[i];
    }

    public boolean isMatched(int i) {
        return state[i] == Shell.MATCHED;
    }

    public boolean isOpen(int i) {
        return state[i] != Shell.CLOSED;
    }

    public BlockPos shellPos(RaidCtx ctx, int i) {
        return ctx.origin().offset(cx[i], 3, cz[i]);
    }

    public int mistakes() {
        return mistakes;
    }

    public int pairs() {
        return pairs;
    }
}
