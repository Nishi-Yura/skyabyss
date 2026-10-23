package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

// 歩 WR4「胃の縦穴の上り」: 縦穴の底から、四つの踊り場を伝って頂へ上る。
//  - 縦穴の底から胃液が、ゆっくり、一定の速さで上がってくる (泳げる水ではなく、赤い霧の層。層の下にいると傷を受ける。層の面に赤い線の粒が走る)
//  - 踊り場と踊り場のあいだの階段 (「登り」) は、ふだんは引っ込んでいる。骨の突起 (スイッチ) を叩くと階段が生え、しばらくして引っ込む (終わりかけは粒と音で知らせる)
//  - スイッチは二組: 各踊り場の端にある「手元」(次の登りを出す。登りの口から遠い端) と、底の壇の上の「親台」(四つの登りを一つずつ出せる)。
//    一人なら手元で交互に (叩く → 走る → 上る)、人数がいれば一人が壇に残って、登る人の足もとへ階段を出してやると早い
//  - 頂 (四つ目の踊り場) に立った人が一人いれば越える
public final class WhaleShaftRoom extends RaidRoom {
    static final int HX = 7;
    static final int HZ = 7;
    static final int H = 27;
    static final int FLIGHTS = 4;
    static final int RISE = 5;
    static final int GROW_STEP = 3;
    static final int EXTEND_TICKS = 300;
    static final int WARN_TICKS = 60;
    static final int GRACE = 240;
    static final int RISE_TICKS = 110;
    static final float DAMAGE = 3f;
    static final int DAMAGE_EVERY = 10;
    static final int TOP = FLIGHTS * RISE;
    static final String FAIL_KEY = "message.skyabyss.raid.WR4.filled";

    // 登りの始まり (最初の段の中心。原点からの相対 x, z) と上る向き
    private static final int[][] START = {{-6, 2}, {-4, -6}, {6, -4}, {4, 6}};
    private static final int[][] DIR = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    // 踊り場: x0, x1, z0, z1 (原点からの相対)。y = 5 * (番号 + 1)
    private static final int[][] LANDING = {{-7, -5, -7, -3}, {1, 7, -7, -5}, {5, 7, 1, 7}, {-7, -1, 5, 7}};
    // 手元のスイッチ (登り f を出す。f = 0 は床、f ≥ 1 は踊り場 f の端): x, y, z (柱の足もと)
    private static final int[][] LOCAL = {{4, 1, 5}, {-7, 6, -3}, {1, 11, -6}, {7, 16, 1}};
    // 親台の上のスイッチ (登り f)
    private static final int DAIS_Y = 2;
    private static final int[][] MASTER = {{-5, 3, 6}, {-4, 3, 6}, {-3, 3, 6}, {-2, 3, 6}};

    private final long[] extUntil = new long[FLIGHTS];
    private final long[] extStart = new long[FLIGHTS];
    private final int[] grown = new int[FLIGHTS];
    private final boolean[] warned = new boolean[FLIGHTS];
    private long startAt = Long.MAX_VALUE;
    private long nextDamage;
    private boolean active;
    private boolean cleared;
    private int tier;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState landing = Blocks.POLISHED_ANDESITE.defaultBlockState();
        // 脚 (肋骨の柱) と踊り場
        int[][] legs = {{-7, -7}, {7, -7}, {7, 7}, {-7, 7}};
        for (int f = 0; f < FLIGHTS; f++) {
            int y = RISE * (f + 1);
            int[] l = LANDING[f];
            RaidKit.fill(level, o(c, l[0], y, l[2]), o(c, l[1], y, l[3]), landing);
            RaidKit.fill(level, o(c, legs[f][0], 1, legs[f][1]), o(c, legs[f][0], y - 1, legs[f][1]), bone);
            RaidKit.set(level, o(c, legs[f][0], y + 1, legs[f][1]), Blocks.LANTERN.defaultBlockState());
            // 踊り場の縁を骨で
            for (int x = l[0]; x <= l[1]; x++) {
                RaidKit.set(level, o(c, x, y, l[2]), bone);
                RaidKit.set(level, o(c, x, y, l[3]), bone);
            }
            for (int z = l[2]; z <= l[3]; z++) {
                RaidKit.set(level, o(c, l[0], y, z), bone);
                RaidKit.set(level, o(c, l[1], y, z), bone);
            }
        }
        // 底の壇 (親台) と、そこへの一段
        RaidKit.fill(level, o(c, -5, 1, 4), o(c, -2, DAIS_Y, 6), Blocks.STONE_BRICKS.defaultBlockState());
        RaidKit.fill(level, o(c, -5, DAIS_Y, 4), o(c, -2, DAIS_Y, 6), bone);
        RaidKit.set(level, o(c, -4, 1, 3), Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        // 天井の肋骨の弧
        for (int z : new int[] {-4, 0, 4}) {
            RaidKit.fill(level, o(c, -HX, H, z), o(c, HX, H, z), bone);
            RaidKit.fill(level, o(c, -HX, H - 3, z), o(c, -HX, H, z), bone);
            RaidKit.fill(level, o(c, HX, H - 3, z), o(c, HX, H, z), bone);
        }
        // スイッチ
        for (int f = 0; f < FLIGHTS; f++) {
            placeSwitch(ctx, o(c, MASTER[f][0], MASTER[f][1], MASTER[f][2]), false);
            placeSwitch(ctx, o(c, LOCAL[f][0], LOCAL[f][1], LOCAL[f][2]), false);
        }
    }

    private static BlockPos o(RaidKit.Cell c, int x, int y, int z) {
        return c.origin().offset(x, y, z);
    }

    private void placeSwitch(RaidCtx ctx, BlockPos pillar, boolean lit) {
        RaidKit.set(ctx.level(), pillar, Blocks.BONE_BLOCK.defaultBlockState());
        RaidKit.set(ctx.level(), pillar.above(), lit ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
    }

    // ---- 階段の位置
    public List<BlockPos> stepCells(RaidCtx ctx, int f, int i) {
        int cx = START[f][0] + (i - 1) * DIR[f][0];
        int cz = START[f][1] + (i - 1) * DIR[f][1];
        int y = RISE * f + i;
        List<BlockPos> out = new ArrayList<>();
        for (int k = -1; k <= 1; k++) {
            out.add(ctx.origin().offset(cx + k * Math.abs(DIR[f][1]), y, cz + k * Math.abs(DIR[f][0])));
        }
        return out;
    }

    private BlockState stair(int f) {
        Direction face = DIR[f][1] < 0 ? Direction.SOUTH : DIR[f][1] > 0 ? Direction.NORTH : DIR[f][0] > 0 ? Direction.WEST : Direction.EAST;
        return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face);
    }

    // ---- 始まり・毎 tick
    @Override
    public void start(RaidCtx ctx) {
        startAt = ctx.now() + GRACE;
        nextDamage = 0;
        active = true;
    }

    @Override
    public void tick(RaidCtx ctx) {
        long now = ctx.now();
        ServerLevel level = ctx.level();
        // 階段の生える・引っ込む
        for (int f = 0; f < FLIGHTS; f++) {
            if (now < extUntil[f]) {
                while (grown[f] < RISE && now >= extStart[f] + (long) grown[f] * GROW_STEP) {
                    grown[f]++;
                    for (BlockPos p : stepCells(ctx, f, grown[f])) {
                        RaidKit.set(level, p, stair(f));
                    }
                    BlockPos mid = stepCells(ctx, f, grown[f]).get(1);
                    level.playSound(null, mid, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 0.9f, 0.7f + 0.1f * grown[f]);
                }
                if (extUntil[f] - now <= WARN_TICKS && grown[f] > 0) {
                    if (!warned[f]) {
                        warned[f] = true;
                        level.playSound(null, stepCells(ctx, f, 3).get(1), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.2f, 0.6f);
                    }
                    if (now % 5 == 0) {
                        for (int i = 1; i <= grown[f]; i++) {
                            BlockPos p = stepCells(ctx, f, i).get(1);
                            level.sendParticles(ParticleTypes.CRIT, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 3, 0.6, 0.1, 0.6, 0.02);
                        }
                    }
                }
            } else if (grown[f] > 0) {
                retract(ctx, f);
            } else if (now % 40 == f * 7L % 40) {
                // 引っ込んでいる登りの口のしるし
                BlockPos p = stepCells(ctx, f, 1).get(1);
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 2, 0.5, 0.2, 0.5, 0.01);
            }
        }
        // 胃液の層
        double lv = fluidLevel(ctx);
        lastLevel = lv;
        lastNow = now;
        double surface = ctx.origin().getY() + 1 + lv;
        fluidFx(ctx, surface, now);
        if (now >= startAt && now >= nextDamage) {
            nextDamage = now + DAMAGE_EVERY;
            for (ServerPlayer p : ctx.standing()) {
                if (!p.isCreative() && !p.isSpectator() && p.getY() < surface && ctx.cell().inner().contains(p.position())) {
                    p.invulnerableTime = 0;
                    p.hurt(level.damageSources().magic(), DAMAGE);
                }
            }
        }
        // 上った高さ・頂
        int t = 0;
        for (ServerPlayer p : ctx.standing()) {
            double rel = p.getY() - (ctx.origin().getY() + 1);
            if (ctx.cell().inner().contains(p.position())) {
                t = Math.max(t, Math.min(FLIGHTS, (int) Math.floor((rel + 0.25) / RISE)));
            }
        }
        tier = Math.max(0, t);
        if (!cleared && tier >= FLIGHTS) {
            cleared = true;
        }
        if (!cleared && lv >= TOP + 3) {
            ctx.fail(FAIL_KEY);
        }
    }

    private void fluidFx(RaidCtx ctx, double surface, long now) {
        ServerLevel level = ctx.level();
        BlockPos o = ctx.origin();
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.78f, 0.06f, 0.06f), 1.6f);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.45f, 0.02f, 0.05f), 1.2f);
        if (now % 2 == 0) {
            // 面の上の細い赤い線 (壁ぞい)
            for (int k = -HX; k <= HX; k++) {
                level.sendParticles(red, o.getX() + k + 0.5, surface + 0.1, o.getZ() - HZ + 0.1, 1, 0, 0, 0, 0);
                level.sendParticles(red, o.getX() + k + 0.5, surface + 0.1, o.getZ() + HZ + 0.9, 1, 0, 0, 0, 0);
                level.sendParticles(red, o.getX() - HX + 0.1, surface + 0.1, o.getZ() + k + 0.5, 1, 0, 0, 0, 0);
                level.sendParticles(red, o.getX() + HX + 0.9, surface + 0.1, o.getZ() + k + 0.5, 1, 0, 0, 0, 0);
            }
        }
        // 面ぜんたいの霧
        for (int n = 0; n < 6; n++) {
            double x = o.getX() - HX + ctx.rng().nextDouble() * (2 * HX + 1);
            double z = o.getZ() - HZ + ctx.rng().nextDouble() * (2 * HZ + 1);
            level.sendParticles(dark, x, surface + 0.05, z, 1, 0.3, 0.05, 0.3, 0);
        }
        // 層の下 (胃液の中) のしずく
        if (now % 6 == 0) {
            double x = o.getX() - HX + ctx.rng().nextDouble() * (2 * HX + 1);
            double z = o.getZ() - HZ + ctx.rng().nextDouble() * (2 * HZ + 1);
            level.sendParticles(ParticleTypes.DRIPPING_LAVA, x, surface - 0.2, z, 1, 0, 0, 0, 0);
        }
    }

    private void retract(RaidCtx ctx, int f) {
        for (int i = RISE; i >= 1; i--) {
            for (BlockPos p : stepCells(ctx, f, i)) {
                if (ctx.level().getBlockState(p).getBlock() instanceof StairBlock) {
                    RaidKit.set(ctx.level(), p, Blocks.AIR.defaultBlockState());
                }
            }
        }
        ctx.level().playSound(null, stepCells(ctx, f, 3).get(1), SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
        grown[f] = 0;
        warned[f] = false;
        extUntil[f] = 0;
    }

    // ---- 入力
    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        for (int f = 0; f < FLIGHTS; f++) {
            for (int[] s : new int[][] {MASTER[f], LOCAL[f]}) {
                BlockPos pillar = ctx.origin().offset(s[0], s[1], s[2]);
                if (pos.equals(pillar) || pos.equals(pillar.above())) {
                    strikeFlight(ctx, f);
                    BlockPos pp = pillar;
                    ctx.level().sendParticles(ParticleTypes.CRIT, pp.getX() + 0.5, pp.getY() + 1.5, pp.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                    ctx.level().playSound(null, pp, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0f, 0.5f + 0.15f * f);
                    return true;
                }
            }
        }
        return false;
    }

    // 登り f (0〜3) を出す・延ばす (試験用に公開)
    public void strikeFlight(RaidCtx ctx, int f) {
        long now = ctx.now();
        if (now >= extUntil[f] && grown[f] == 0) {
            extStart[f] = now;
        }
        extUntil[f] = now + (long) RISE * GROW_STEP + EXTEND_TICKS;
        warned[f] = false;
        lightSwitches(ctx, f, true);
    }

    private void lightSwitches(RaidCtx ctx, int f, boolean lit) {
        placeSwitch(ctx, ctx.origin().offset(MASTER[f][0], MASTER[f][1], MASTER[f][2]), lit);
        placeSwitch(ctx, ctx.origin().offset(LOCAL[f][0], LOCAL[f][1], LOCAL[f][2]), lit);
    }

    // 胃液の層の高さ (床の上のブロック数)
    public double fluidLevel(RaidCtx ctx) {
        if (!active || ctx.now() < startAt) {
            return 0;
        }
        return (ctx.now() - startAt) / (double) RISE_TICKS;
    }

    @Override
    public boolean isComplete() {
        return cleared;
    }

    @Override
    public void reset(RaidCtx ctx) {
        for (int f = 0; f < FLIGHTS; f++) {
            retract(ctx, f);
            lightSwitches(ctx, f, false);
        }
        active = false;
        startAt = Long.MAX_VALUE;
        cleared = false;
        tier = 0;
    }

    @Override
    public void end(RaidCtx ctx) {
        active = false;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.WR4.climb";
    }

    @Override
    public int counter() {
        return tier;
    }

    @Override
    public int counterMax() {
        return FLIGHTS;
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.WR4.fluid";
    }

    private double lastLevel;

    @Override
    public int secondary() {
        return (int) Math.min(TOP, Math.floor(lastLevel));
    }

    @Override
    public int secondaryMax() {
        return TOP;
    }

    @Override
    public int timerTicks() {
        return active && startAt != Long.MAX_VALUE ? (int) Math.max(0, startAt + (long) (TOP + 1) * RISE_TICKS - lastNow) : -1;
    }

    private long lastNow;

    // ---- 試験用
    public int grown(int f) {
        return grown[f];
    }

    public int tier() {
        return tier;
    }

    public BlockPos masterSwitch(RaidCtx ctx, int f) {
        return ctx.origin().offset(MASTER[f][0], MASTER[f][1], MASTER[f][2]);
    }

    public BlockPos localSwitch(RaidCtx ctx, int f) {
        return ctx.origin().offset(LOCAL[f][0], LOCAL[f][1], LOCAL[f][2]);
    }

    public BlockPos landingSpot(RaidCtx ctx, int n) {
        int[] l = LANDING[n - 1];
        return ctx.origin().offset((l[0] + l[1]) / 2, RISE * n + 1, (l[2] + l[3]) / 2);
    }
}
