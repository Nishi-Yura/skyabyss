package jp.revati.skyabyss.trial.raid.whale;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// 智 WR5「石の海の床合わせ」: 床の 7×5 の石板 (一枚 3×3) は、骨の筋が通っている。叩くと 90 度回る。
//  - 正しく合わせると、筋がつながって一頭の鯨の骨 (背骨・肋骨・潮吹き・尾びれ) になる。その姿は北の壁の高いところに「見本」として彫ってある
//    (入口の上の見張り台からだと、見本も床の全体も見渡せる)
//  - 鯨の骨の形は挑戦ごとに違う (肋骨の本数と長さ・潮吹きの位置と高さ・尾びれ)。石板の向きはぐちゃぐちゃに回してあり、必ず回して合わせられる
//  - 合っている石板の数は目的の帯に出る (どれかは教えない。見本と見比べる)。一人でも合わせられ、人数がいれば別々の石板を同時に回せて早い。間違っても傷はない
public final class WhaleTilesRoom extends RaidRoom {
    static final int HX = 11;
    static final int HZ = 11;
    static final int H = 22;
    static final int COLS = 7;
    static final int ROWS = 5;
    static final int N = 1;
    static final int E = 2;
    static final int S = 4;
    static final int W = 8;
    // 見本の上端 (床の上の高さ)
    static final int MURAL_TOP = 20;
    static final int BALCONY_Y = 4;

    private final int[][] target = new int[COLS][ROWS];
    private final int[][] initial = new int[COLS][ROWS];
    private final int[][] rot = new int[COLS][ROWS];
    private boolean solved;
    private long solvedAt;
    private int strikes;

    // 時計回りに 90 度回した筋
    static int rotate(int mask, int times) {
        int m = mask & 15;
        for (int i = 0; i < times; i++) {
            m = ((m << 1) | (m >> 3)) & 15;
        }
        return m;
    }

    static boolean arm(int mask, int sx, int sz) {
        if (sx == 1 && sz == 1) {
            return mask != 0;
        }
        return (sx == 1 && sz == 0 && (mask & N) != 0) || (sx == 2 && sz == 1 && (mask & E) != 0) || (sx == 1 && sz == 2 && (mask & S) != 0)
                || (sx == 0 && sz == 1 && (mask & W) != 0);
    }

    // 鯨の骨の形 (木。つながりは必ず合っている)
    private void generate(RandomSource rng) {
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                target[c][r] = 0;
            }
        }
        for (int c = 0; c < COLS; c++) {
            target[c][2] |= (c > 0 ? W : 0) | (c < COLS - 1 ? E : 0);
        }
        // 尾びれ
        int tail = rng.nextInt(3);
        if (tail != 1) {
            target[0][2] |= N;
            target[0][1] = S;
        }
        if (tail != 0) {
            target[0][2] |= S;
            target[0][3] = N;
        }
        // 肋骨 (背骨の下へ) 二〜三本
        java.util.List<Integer> cols = new java.util.ArrayList<>(java.util.List.of(1, 2, 3, 4));
        int ribs = 2 + rng.nextInt(2);
        for (int i = 0; i < ribs; i++) {
            int c = cols.remove(rng.nextInt(cols.size()));
            target[c][2] |= S;
            if (rng.nextBoolean()) {
                target[c][3] = N;
            } else {
                target[c][3] = N | S;
                target[c][4] = N;
            }
        }
        // 潮吹き (背骨の上へ)
        int sc = 4 + rng.nextInt(2);
        target[sc][2] |= N;
        if (rng.nextBoolean()) {
            target[sc][1] = S;
        } else {
            target[sc][1] = N | S;
            target[sc][0] = S;
        }
        // 向きをばらす。回さないと合わない石板が過半数になるまで
        for (int tries = 0; tries < 50; tries++) {
            int sensitive = 0;
            int off = 0;
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    initial[c][r] = rng.nextInt(4);
                    if (rotate(target[c][r], 1) != target[c][r]) {
                        sensitive++;
                        if (rotate(target[c][r], initial[c][r]) != target[c][r]) {
                            off++;
                        }
                    }
                }
            }
            if (off * 10 >= sensitive * 7) {
                break;
            }
        }
    }

    // ---- 位置 (原点からの相対)
    static int tileX(int c) {
        return (c - 3) * 3;
    }

    static int tileZ(int r) {
        return (r - 2) * 3;
    }

    public BlockPos tileCenter(RaidCtx ctx, int c, int r) {
        return ctx.origin().offset(tileX(c), 0, tileZ(r));
    }

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell cell = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        generate(ctx.rng());
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState dark = Blocks.DEEPSLATE_TILES.defaultBlockState();
        // 床の石板
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                rot[c][r] = initial[c][r];
                drawTile(ctx, c, r);
            }
        }
        // 見本 (北の壁。見る側 = 南から見て、列は西→東、行は上→下で床と同じ向き)
        int wallZ = ctx.origin().getZ() - HZ - 1;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                for (int sz = 0; sz < 3; sz++) {
                    for (int sx = 0; sx < 3; sx++) {
                        BlockPos p = new BlockPos(ctx.origin().getX() + tileX(c) - 1 + sx, ctx.origin().getY() + MURAL_TOP - (3 * r + sz), wallZ);
                        RaidKit.set(level, p, arm(target[c][r], sx, sz) ? bone : dark);
                    }
                }
            }
        }
        // 見本の額 (骨の縁)
        int mx0 = ctx.origin().getX() + tileX(0) - 2;
        int mx1 = ctx.origin().getX() + tileX(COLS - 1) + 2;
        int my0 = ctx.origin().getY() + MURAL_TOP - 3 * ROWS;
        int my1 = ctx.origin().getY() + MURAL_TOP + 1;
        for (int x = mx0; x <= mx1; x++) {
            RaidKit.set(level, new BlockPos(x, my0, wallZ), Blocks.POLISHED_ANDESITE.defaultBlockState());
            RaidKit.set(level, new BlockPos(x, my1, wallZ), Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        for (int y = my0; y <= my1; y++) {
            RaidKit.set(level, new BlockPos(mx0, y, wallZ), Blocks.POLISHED_ANDESITE.defaultBlockState());
            RaidKit.set(level, new BlockPos(mx1, y, wallZ), Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        // 見張り台 (入口の上。高さ 4)。一段ずつ上る階段は床の最南の行の東端から
        RaidKit.fill(level, cell.origin().offset(-HX + 1, BALCONY_Y, 9), cell.origin().offset(HX - 1, BALCONY_Y, HZ), Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int x = -HX + 1; x <= HX - 1; x++) {
            RaidKit.set(level, cell.origin().offset(x, BALCONY_Y, 9), bone);
            if (x != HX - 1) {
                RaidKit.set(level, cell.origin().offset(x, BALCONY_Y + 1, 9), Blocks.ANDESITE_WALL.defaultBlockState());
            }
        }
        for (int i = 1; i <= BALCONY_Y; i++) {
            RaidKit.set(level, cell.origin().offset(HX - 5 + i, i, 8), Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        }
        // 見張り台の下の柱と灯り
        for (int x : new int[] {-HX + 1, 0, HX - 1}) {
            RaidKit.fill(level, cell.origin().offset(x, 1, 10), cell.origin().offset(x, BALCONY_Y - 1, 10), bone);
        }
        RaidKit.set(level, cell.origin().offset(-HX + 1, BALCONY_Y + 1, HZ), Blocks.LANTERN.defaultBlockState());
        RaidKit.set(level, cell.origin().offset(HX - 1, BALCONY_Y + 1, HZ), Blocks.LANTERN.defaultBlockState());
        // 天井の肋骨
        for (int z : new int[] {-8, -2, 4}) {
            RaidKit.fill(level, cell.origin().offset(-HX, H, z), cell.origin().offset(HX, H, z), bone);
            RaidKit.fill(level, cell.origin().offset(-HX, H - 3, z), cell.origin().offset(-HX, H, z), bone);
            RaidKit.fill(level, cell.origin().offset(HX, H - 3, z), cell.origin().offset(HX, H, z), bone);
        }
        // 飾り (docs/design/39 の 4.1): 見本を化石の鯨の額に。床の石板・見本・見張り台からの眺めは空ける
        WhaleDecor.wr5(ctx);
    }

    private void drawTile(RaidCtx ctx, int c, int r) {
        int mask = rotate(target[c][r], rot[c][r]);
        BlockPos center = tileCenter(ctx, c, r);
        for (int sz = 0; sz < 3; sz++) {
            for (int sx = 0; sx < 3; sx++) {
                BlockState st = arm(mask, sx, sz) ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                RaidKit.set(ctx.level(), center.offset(sx - 1, 0, sz - 1), st);
            }
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        solved = false;
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (solved && ctx.now() % 10 == 0 && ctx.now() - solvedAt < 100) {
            // 越えた: 筋が光る
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    if (target[c][r] != 0) {
                        BlockPos p = tileCenter(ctx, c, r);
                        ctx.level().sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.01);
                    }
                }
            }
        }
    }

    // 床の石板を叩いた/使った
    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        if (solved || pos.getY() != ctx.origin().getY()) {
            return false;
        }
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                BlockPos t = tileCenter(ctx, c, r);
                if (Math.abs(pos.getX() - t.getX()) <= 1 && Math.abs(pos.getZ() - t.getZ()) <= 1) {
                    turn(ctx, c, r);
                    return true;
                }
            }
        }
        return false;
    }

    // 石板 (c, r) を時計回りに一回 (試験用に公開)
    public void turn(RaidCtx ctx, int c, int r) {
        rot[c][r] = (rot[c][r] + 1) % 4;
        strikes++;
        drawTile(ctx, c, r);
        BlockPos p = tileCenter(ctx, c, r);
        ctx.level().playSound(null, p, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f + 0.1f * ((c + r) % 4));
        ctx.level().sendParticles(ParticleTypes.CRIT, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 4, 0.5, 0.05, 0.5, 0.02);
        if (allSolved()) {
            solved = true;
            solvedAt = ctx.now();
            WhaleDecor.exitLamps(ctx, true);
            ctx.level().playSound(null, ctx.origin(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2.0f, 0.5f);
        }
    }

    private boolean allSolved() {
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (rotate(target[c][r], rot[c][r]) != target[c][r]) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isComplete() {
        return solved;
    }

    @Override
    public void reset(RaidCtx ctx) {
        solved = false;
        WhaleDecor.exitLamps(ctx, false);
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                rot[c][r] = initial[c][r];
                drawTile(ctx, c, r);
            }
        }
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.WR5.align";
    }

    // 合っている石板の数 (目的の帯に「21/35」。どの石板かは教えない)
    @Override
    public int counter() {
        int n = 0;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (rotate(target[c][r], rot[c][r]) == target[c][r]) {
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public int counterMax() {
        return COLS * ROWS;
    }

    // ---- 試験用
    public int targetMask(int c, int r) {
        return target[c][r];
    }

    public int currentMask(int c, int r) {
        return rotate(target[c][r], rot[c][r]);
    }

    public int turnsNeeded(int c, int r) {
        for (int k = 0; k < 4; k++) {
            if (rotate(target[c][r], (rot[c][r] + k) % 4) == target[c][r]) {
                return k;
            }
        }
        return -1;
    }

    public int strikes() {
        return strikes;
    }

    // 見本の (c, r, sx, sz) のブロックの位置
    public BlockPos muralPos(RaidCtx ctx, int c, int r, int sx, int sz) {
        return new BlockPos(ctx.origin().getX() + tileX(c) - 1 + sx, ctx.origin().getY() + MURAL_TOP - (3 * r + sz), ctx.origin().getZ() - HZ - 1);
    }

    public BlockPos balconySpot(RaidCtx ctx) {
        return ctx.origin().offset(0, BALCONY_Y + 1, 10);
    }
}
