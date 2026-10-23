package jp.revati.skyabyss.trial.raid.tide;

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

// 智 TR6「潮見の暦」: 広間の北の壁と南の壁に、六つの月の絵 (壁画) が掛かっている。絵の下の点の数が、暦の何番目かを表す。
// 広間の床には六つの「月の石」の台があり、叩く・使うと満ち欠けの形が変わる (使う = 次の形、叩く = 前の形)。壁画と同じ順に、点の数が同じ番号の台へ同じ月を並べる。
//  - 月の形は六つ: 新月・三日月・上弦・満月・下弦・欠けた月 (欠けの輪)。壁画は北の壁に 1・2・3 番、南の壁に 4・5・6 番 (どちらも見る人から見て左から右)
//  - 暦の順 (どの月から始まり、満ちる向きか欠ける向きか) は挑戦ごとに変わる。壁画を読まないと分からない
//  - 合っている台の数は目的の帯に出る (どの台かは教えない)。一人が壁画を読み、一人が台を回すと早い
//  - 水・敵・ダメージはない
public final class TideMoonRoom extends RaidRoom {
    static final int HX = 13;
    static final int HZ = 11;
    static final int H = 10;
    static final int PHASES = 6;
    static final int DIALS = 6;
    // 月の形: 5 列の明暗 (見る人から見て左から右。1 = 光る)
    static final int[][] MASK = {{0, 0, 0, 0, 0}, {0, 0, 0, 1, 1}, {0, 0, 1, 1, 1}, {1, 1, 1, 1, 1}, {1, 1, 1, 0, 0}, {1, 1, 0, 0, 0}};
    static final int[] CENTER_X = {-8, 0, 8};
    static final int[] DIAL_Z = {-4, 4};
    static final int MURAL_Y = 5;

    static final BlockState LIT = Blocks.SEA_LANTERN.defaultBlockState();
    static final BlockState DARK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

    private BlockPos origin;
    private final int[] start = new int[DIALS];
    private final int[] phase = new int[DIALS];
    private int first;
    private int step;
    private boolean done;

    @Override
    public void build(RaidCtx ctx) {
        origin = ctx.origin();
        ServerLevel level = ctx.level();
        first = ctx.rng().nextInt(PHASES);
        step = ctx.rng().nextBoolean() ? 1 : PHASES - 1;
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, TideBuild.PALETTE));
        // 壁画: 北の壁に 1〜3、南の壁に 4〜6 (どちらも見る人から見て左から右)
        for (int k = 0; k < DIALS; k++) {
            boolean north = k < 3;
            int cx = north ? CENTER_X[k] : -CENTER_X[k - 3];
            int wz = north ? -HZ : HZ;
            drawDisc(level, cx, wz, correct(k), north);
            // 点: 番号の数だけ、円の下に並べる
            int n = k + 1;
            int x0 = cx - (n - 1) / 2;
            for (int i = 0; i < n; i++) {
                RaidKit.set(level, origin.offset(x0 + i, MURAL_Y - 1, wz), Blocks.CALCITE.defaultBlockState());
            }
        }
        // 月の石の台
        BlockState brick = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        for (int k = 0; k < DIALS; k++) {
            int cx = dialX(k);
            int cz = dialZ(k);
            RaidKit.fill(level, origin.offset(cx - 3, 1, cz - 1), origin.offset(cx + 3, 1, cz + 1), brick);
            int n = k + 1;
            int x0 = cx - (n - 1) / 2;
            for (int i = 0; i < n; i++) {
                RaidKit.set(level, origin.offset(x0 + i, 1, cz + 1), Blocks.CALCITE.defaultBlockState());
            }
        }
        // 初めの形: どれも正解でない形
        for (int k = 0; k < DIALS; k++) {
            int c = correct(k);
            start[k] = (c + 1 + ctx.rng().nextInt(PHASES - 1)) % PHASES;
            phase[k] = start[k];
            drawDial(ctx, k);
        }
        // 飾り: 壁ぎわの死んだ珊瑚のこぶ (壁画の前を避ける)
        BlockState[] dead = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};
        for (int i = 0; i < 12; i++) {
            int side = ctx.rng().nextBoolean() ? -1 : 1;
            int z = ctx.rng().nextInt(2 * HZ - 5) - HZ + 3;
            RaidKit.fill(level, origin.offset(side * (HX - 1), 1, z), origin.offset(side * (HX - 1), 1 + ctx.rng().nextInt(3), z), dead[ctx.rng().nextInt(dead.length)]);
        }
        dress(ctx);
    }

    // 飾り (docs/design/39 の 4.2「解く 月・潮見の暦」): 六つの壁画を貝殻の額 (石英の枠と桃の縁) に入れ、天井の真ん中に月の円盤。
    // 壁画の面と月の石の台のまわりには何も置かない (見分けを邪魔しない)
    private void dress(RaidCtx ctx) {
        TideBuild d = new TideBuild(ctx);
        for (int k = 0; k < DIALS; k++) {
            boolean north = k < 3;
            int cx = north ? CENTER_X[k] : -CENTER_X[k - 3];
            int wz = north ? -HZ : HZ;
            d.keep(cx - 4, MURAL_Y - 2, wz - 1, cx + 4, H, wz + 1);
            d.keep(dialX(k) - 4, 1, dialZ(k) - 2, dialX(k) + 4, 4, dialZ(k) + 2);
        }
        d.shell(2);
        // 壁画の額: 左右の柱 (方解石と石英)、上の庇 (石英の板)、北の壁画には下の棚
        for (int k = 0; k < DIALS; k++) {
            boolean north = k < 3;
            int cx = north ? CENTER_X[k] : -CENTER_X[k - 3];
            int wz = north ? -HZ : HZ;
            for (int y = MURAL_Y - 1; y <= MURAL_Y + 4; y++) {
                BlockState st = y == MURAL_Y + 2 ? Blocks.CHERRY_PLANKS.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
                d.mark(cx - 3, y, wz, st);
                d.mark(cx + 3, y, wz, st);
            }
            for (int x = cx - 3; x <= cx + 3; x++) {
                d.mark(x, MURAL_Y + 5, wz, x == cx ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState());
                if (north) {
                    d.mark(x, MURAL_Y - 2, wz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
                }
            }
        }
        // 天井の月の円盤 (天井の板を塗る。真ん中だけ真珠の光)
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double r = Math.hypot(x, z);
                if (r > 4.3) {
                    continue;
                }
                BlockState st = r < 0.5 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : r > 3.4 ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                        : (x * 3 + z * 5) % 4 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                d.paint(x, H + 1, z, st);
            }
        }
        // 月の円盤の縁から垂れる六つの珊瑚 (六つの顔の数)
        for (int i = 0; i < PHASES; i++) {
            double a = Math.PI * 2 * i / PHASES;
            int x = (int) Math.round(Math.cos(a) * 5);
            int z = (int) Math.round(Math.sin(a) * 5);
            d.deco(x, H, z, Blocks.CHERRY_PLANKS.defaultBlockState());
            d.deco(x, H - 1, z, i % 2 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.MAGENTA_TERRACOTTA.defaultBlockState());
        }
        // 床: 真ん中の渦と、月の石の台の列の筋
        d.floorSpiral(0, 0, 3, 1.5, Blocks.CUT_SANDSTONE.defaultBlockState());
        for (int z : DIAL_Z) {
            for (int x = -HX + 2; x <= HX - 2; x++) {
                d.floorPut(x, z + 2 * Integer.signum(z), Blocks.CUT_SANDSTONE.defaultBlockState());
            }
        }
        d.hiddenLights(11, 4, false).ceilingCoral(8, 2, H - 1, 2).wallBranches(10, 6, 2).pools(2).shards(4);
        d.finish();
    }

    // 番号 k (0 始まり) の台に並べる月
    int correct(int k) {
        return Math.floorMod(first + step * k, PHASES);
    }

    // 台の位置: 1〜3 番は北の列、4〜6 番は南の列 (どちらも南から見て左から右)
    static int dialX(int k) {
        return CENTER_X[k % 3];
    }

    static int dialZ(int k) {
        return DIAL_Z[k / 3];
    }

    // 壁画の円: 5×5 の角を落とした形。見る人から見て左から右の列で明暗を決める。north = 北の壁 (北を向いて見る人の右 = +x)
    private void drawDisc(ServerLevel level, int cx, int wz, int ph, boolean north) {
        BlockState rim = Blocks.PRISMARINE.defaultBlockState();
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r < 5; r++) {
                boolean corner = (c == 0 || c == 4) && (r == 0 || r == 4);
                int wx = north ? cx + (c - 2) : cx - (c - 2);
                BlockState st = corner ? rim : MASK[ph][c] == 1 ? LIT : DARK;
                RaidKit.set(level, origin.offset(wx, MURAL_Y + r, wz), st);
            }
        }
    }

    private void drawDial(RaidCtx ctx, int k) {
        int cx = dialX(k);
        int cz = dialZ(k);
        for (int c = 0; c < 5; c++) {
            BlockState st = MASK[phase[k]][c] == 1 ? LIT : DARK;
            RaidKit.set(ctx.level(), origin.offset(cx + (c - 2), 2, cz), st);
            RaidKit.set(ctx.level(), origin.offset(cx + (c - 2), 3, cz), st);
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        restore(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        restore(ctx);
    }

    private void restore(RaidCtx ctx) {
        done = false;
        for (int k = 0; k < DIALS; k++) {
            if (phase[k] != start[k]) {
                phase[k] = start[k];
                drawDial(ctx, k);
            }
        }
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done && ctx.now() % 10 == 0) {
            for (int k = 0; k < DIALS; k++) {
                ctx.level().sendParticles(ParticleTypes.END_ROD, origin.getX() + dialX(k) + 0.5, origin.getY() + 4.5, origin.getZ() + dialZ(k) + 0.5, 2, 0.5, 0.2, 0.1, 0.01);
            }
        }
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        int k = dialAt(pos);
        if (k < 0) {
            return false;
        }
        if (!done) {
            turn(ctx, k, attack ? PHASES - 1 : 1);
        }
        return true;
    }

    private int dialAt(BlockPos pos) {
        int lx = pos.getX() - origin.getX();
        int lz = pos.getZ() - origin.getZ();
        int ly = pos.getY() - origin.getY();
        if (ly < 1 || ly > 3) {
            return -1;
        }
        for (int k = 0; k < DIALS; k++) {
            if (Math.abs(lx - dialX(k)) <= 3 && Math.abs(lz - dialZ(k)) <= 1) {
                return k;
            }
        }
        return -1;
    }

    private void turn(RaidCtx ctx, int k, int by) {
        phase[k] = (phase[k] + by) % PHASES;
        drawDial(ctx, k);
        ctx.level().playSound(null, origin.offset(dialX(k), 2, dialZ(k)), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.4f, 0.6f + 0.2f * phase[k]);
        for (int i = 0; i < DIALS; i++) {
            if (phase[i] != correct(i)) {
                return;
            }
        }
        done = true;
        ctx.level().playSound(null, origin.above(2), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6f, 1.0f);
        ctx.say(Component.translatable("message.skyabyss.raid.TR6.aligned"));
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.TR6.arrange";
    }

    // 合っている月の石の数 (目的の帯に「4/6」。docs/design/32 の 9 章: 謎の部屋は進みを帯に出す)
    @Override
    public int counter() {
        if (done) {
            return DIALS;
        }
        int n = 0;
        for (int i = 0; i < DIALS; i++) {
            if (phase[i] == correct(i)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int counterMax() {
        return DIALS;
    }

    // ---- 試験用
    public int phaseOf(int k) {
        return phase[k];
    }

    public int correctOf(int k) {
        return correct(k);
    }

    public int startOf(int k) {
        return start[k];
    }

    public BlockPos dialPos(int k) {
        return origin.offset(dialX(k), 2, dialZ(k));
    }

    // 壁画の円の形 (壁画 k の見る人から見て 5 列の明暗を、世界の座標から読む)
    public int[] muralMask(RaidCtx ctx, int k) {
        boolean north = k < 3;
        int cx = north ? CENTER_X[k] : -CENTER_X[k - 3];
        int wz = north ? -HZ : HZ;
        int[] out = new int[5];
        for (int c = 0; c < 5; c++) {
            int wx = north ? cx + (c - 2) : cx - (c - 2);
            out[c] = ctx.level().getBlockState(origin.offset(wx, MURAL_Y + 2, wz)).is(LIT.getBlock()) ? 1 : 0;
        }
        return out;
    }

    public int[] dialMask(RaidCtx ctx, int k) {
        int[] out = new int[5];
        for (int c = 0; c < 5; c++) {
            out[c] = ctx.level().getBlockState(origin.offset(dialX(k) + (c - 2), 2, dialZ(k))).is(LIT.getBlock()) ? 1 : 0;
        }
        return out;
    }

    public int step() {
        return step;
    }

    public int first() {
        return first;
    }
}
