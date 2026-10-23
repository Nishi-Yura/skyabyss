package jp.revati.skyabyss.trial.raid.storm;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
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
import net.minecraft.world.phys.Vec3;

// 歩 SR4「雷柱から逃げて鍵集め」: 銅の甲板を、雷の柱がいちばん近い人を追って歩く。甲板に散らばる三つの鍵 (光る台) を集める。
//  - 柱は歩いて近づき、立ち止まって前へ赤い帯 (予告 30 tick) を引き、雷を落とす。帯は壁 (柱・壁) に遮られる。柱にじかに触れても傷
//  - 柱は人が走るより遅い。鍵を取るたびに少し速くなる。鍵は離れた場所にあるので、仲間と手分けすると柱が一人にしか向かず早い
//  - 鍵の場所・飾りの柱の場所は挑戦ごとに少し変わる。全滅・やり直しで鍵・柱は最初へ
public final class ColumnChase extends RaidRoom {
    static final int HX = 15;
    static final int HZ = 12;
    static final int H = 9;
    static final int KEYS = 3;
    static final double PICKUP_R = 1.7;
    // 柱の動き (tick)
    static final int GRACE = 60;
    static final int ROAM = 70;
    static final int WARN = 30;
    static final int REST = 26;
    static final double SPEED = 0.105;
    static final double SPEED_PER_KEY = 0.015;
    static final double BEAM_LENGTH = 9.0;
    static final double BEAM_HALF = 1.4;
    static final float BEAM_DAMAGE = 6f;
    static final float TOUCH_DAMAGE = 4f;
    static final double TOUCH_R = 1.3;

    public enum Phase {
        GRACE, ROAM, CHARGE, REST
    }

    // 一歩ぶん目標へ近づく (純粋な計算)
    static double[] stepToward(double x, double z, double tx, double tz, double speed) {
        double dx = tx - x;
        double dz = tz - z;
        double d = Math.hypot(dx, dz);
        if (d <= speed || d < 1e-6) {
            return new double[] {tx, tz};
        }
        return new double[] {x + dx / d * speed, z + dz / d * speed};
    }

    // 点 (x, z) に最も近い目標の番号 (なければ -1)
    static int nearest(double x, double z, List<double[]> targets) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < targets.size(); i++) {
            double d = Math.hypot(targets.get(i)[0] - x, targets.get(i)[1] - z);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private final int[][] keyRel = new int[KEYS][];
    private final boolean[] taken = new boolean[KEYS];
    private final List<int[]> pillars = new ArrayList<>();
    private int count;
    private boolean started;
    private long age;
    private Phase phase = Phase.GRACE;
    private int phaseAge;
    private double colX;
    private double colZ;
    private double aim;
    private int touchCd;
    private int generation;
    private final List<Telegraph> beams = new ArrayList<>();

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.STORM));
        BlockPos o = c.origin();
        BlockState cut = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        // 鍵の場所: 西・東・南寄りの三つ (西と東は挑戦ごとに入れ替わる)
        boolean flip = ctx.rng().nextBoolean();
        keyRel[0] = new int[] {(flip ? 1 : -1) * (10 + ctx.rng().nextInt(3)), -6 + ctx.rng().nextInt(5)};
        keyRel[1] = new int[] {(flip ? -1 : 1) * (10 + ctx.rng().nextInt(3)), -6 + ctx.rng().nextInt(5)};
        keyRel[2] = new int[] {(ctx.rng().nextBoolean() ? 1 : -1) * (3 + ctx.rng().nextInt(3)), 3 + ctx.rng().nextInt(4)};
        for (int i = 0; i < KEYS; i++) {
            int x = keyRel[i][0];
            int z = keyRel[i][1];
            RaidKit.fill(level, o.offset(x - 1, 0, z - 1), o.offset(x + 1, 0, z + 1), Blocks.WAXED_COPPER_BLOCK.defaultBlockState());
            setKey(ctx, i, true);
        }
        // 飾りの柱: 帯を遮る (鍵・柱の出発点・戸の前から離す)
        int tries = 0;
        while (pillars.size() < 5 && tries++ < 120) {
            int x = ctx.rng().nextInt(2 * HX - 5) - HX + 2;
            int z = ctx.rng().nextInt(2 * HZ - 5) - HZ + 2;
            if (pillarOk(x, z)) {
                pillars.add(new int[] {x, z});
                RaidKit.fill(level, o.offset(x, 1, z), o.offset(x + 1, H - 3, z + 1), cut);
                RaidKit.fill(level, o.offset(x, H - 2, z), o.offset(x + 1, H - 2, z + 1), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                RaidKit.set(level, o.offset(x, H - 1, z), Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
        // 柱の出発点 (北の中央): 焦げた銅の円
        RaidKit.fill(level, o.offset(-1, 0, -HZ + 2), o.offset(1, 0, -HZ + 4), Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState());
        colX = o.getX() + 0.5;
        colZ = o.getZ() - HZ + 3 + 0.5;
        // 建築 (docs/design/39 の 4.3): 鍵の台の縁と天井の雲の切れ目・羽の段の壁・雲の天井・雲の窓
        StormRoomDeco.dressSR4(ctx, keyRel);
    }

    private boolean pillarOk(int x, int z) {
        for (int[] k : keyRel) {
            if (Math.abs(k[0] - x) < 4 && Math.abs(k[1] - z) < 4) {
                return false;
            }
        }
        for (int[] p : pillars) {
            if (Math.abs(p[0] - x) < 5 && Math.abs(p[1] - z) < 5) {
                return false;
            }
        }
        // 北の出発点・南の戸の前の通り道
        return !(Math.abs(x) < 4 && (z < -HZ + 7 || z > HZ - 8));
    }

    private void setKey(RaidCtx ctx, int i, boolean on) {
        BlockPos at = ctx.origin().offset(keyRel[i][0], 1, keyRel[i][1]);
        ServerLevel level = ctx.level();
        if (on) {
            RaidKit.set(level, at, Blocks.VERDANT_FROGLIGHT.defaultBlockState());
            RaidKit.set(level, at.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        } else {
            RaidKit.set(level, at, Blocks.WAXED_COPPER_BULB.defaultBlockState());
            RaidKit.set(level, at.above(), Blocks.AIR.defaultBlockState());
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        started = true;
        age = 0;
        enter(Phase.GRACE);
    }

    private void enter(Phase p) {
        phase = p;
        phaseAge = 0;
    }

    @Override
    public void tick(RaidCtx ctx) {
        List<double[]> pos = new ArrayList<>();
        List<ServerPlayer> standing = ctx.standing();
        for (ServerPlayer p : standing) {
            if (ctx.cell().inArena(p.getX(), p.getZ())) {
                pos.add(new double[] {p.getX(), p.getZ()});
            }
        }
        update(ctx, pos);
        // 柱に触れた人は傷 (柱は見えているので、触れないようにできる)
        if (touchCd > 0) {
            touchCd--;
        } else if (started && phase != Phase.GRACE) {
            for (ServerPlayer p : standing) {
                if (Math.hypot(p.getX() - colX, p.getZ() - colZ) <= TOUCH_R) {
                    p.hurt(ctx.level().damageSources().lightningBolt(), TOUCH_DAMAGE);
                    Vec3 away = new Vec3(p.getX() - colX, 0, p.getZ() - colZ);
                    if (away.lengthSqr() > 1e-4) {
                        away = away.normalize();
                        p.setDeltaMovement(away.x * 0.7, 0.35, away.z * 0.7);
                        p.hurtMarked = true;
                    }
                    touchCd = 20;
                    break;
                }
            }
        }
    }

    // 位置 (x, z) の一覧だけを見て進める (試験でも使う)。鍵の取得・柱の動き・予告
    void update(RaidCtx ctx, List<double[]> pos) {
        ServerLevel level = ctx.level();
        age++;
        phaseAge++;
        for (double[] p : pos) {
            for (int i = 0; i < KEYS; i++) {
                if (!taken[i] && Math.hypot(p[0] - keyX(ctx, i), p[1] - keyZ(ctx, i)) <= PICKUP_R) {
                    take(ctx, i);
                }
            }
        }
        if (isComplete()) {
            return;
        }
        effects(level, ctx);
        switch (phase) {
            case GRACE -> {
                if (phaseAge >= GRACE && started) {
                    enter(Phase.ROAM);
                }
            }
            case ROAM -> {
                int t = nearest(colX, colZ, pos);
                if (t >= 0) {
                    double[] n = stepToward(colX, colZ, pos.get(t)[0], pos.get(t)[1], SPEED + SPEED_PER_KEY * count);
                    colX = n[0];
                    colZ = n[1];
                    aim = Telegraph.angleOf(pos.get(t)[0] - colX, pos.get(t)[1] - colZ);
                    if (phaseAge >= ROAM) {
                        beam(ctx);
                        enter(Phase.CHARGE);
                    }
                }
            }
            case CHARGE -> {
                if (phaseAge >= WARN + 4) {
                    enter(Phase.REST);
                }
            }
            case REST -> {
                if (phaseAge >= REST) {
                    enter(Phase.ROAM);
                }
            }
        }
    }

    private void take(RaidCtx ctx, int i) {
        taken[i] = true;
        count++;
        setKey(ctx, i, false);
        if (isComplete()) {
            StormRoomDeco.exitLit(ctx, true);
        }
        BlockPos at = ctx.origin().offset(keyRel[i][0], 2, keyRel[i][1]);
        ctx.level().playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.3f, 0.6f + 0.25f * count);
        ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.08);
        if (!isComplete()) {
            ctx.say(Component.translatable("message.skyabyss.raid.SR4.key", count, KEYS));
        }
    }

    // 立ち止まって前へ赤い帯。落ちるとき帯の中の人へ傷
    private void beam(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        double ax = colX;
        double az = colZ;
        double ang = aim;
        int gen = generation;
        Telegraph t = Telegraph.in(level, TelegraphShape.line(ax, az, ang, BEAM_LENGTH, BEAM_HALF))
                .floor(ctx.origin().getY() + 1).warn(WARN).danger()
                .startSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.7f)
                .onImpact(lvl -> {
                    if (gen != generation) {
                        return;
                    }
                    for (int k = 1; k <= 3; k++) {
                        double d = BEAM_LENGTH * k / 3.0;
                        Vec3 at = new Vec3(ax + Math.cos(ang) * d, ctx.origin().getY() + 1, az + Math.sin(ang) * d);
                        StormRaid.bolt(lvl, at);
                        StormRaid.sparks(lvl, at.add(0, 0.5, 0), 20, 0.5);
                    }
                    StormRaid.zap(lvl, BlockPos.containing(ax, ctx.origin().getY() + 1, az), 1.2f, 1.3f);
                })
                .onHit((lvl, p) -> p.hurt(lvl.damageSources().lightningBolt(), BEAM_DAMAGE))
                .start();
        beams.add(t);
        beams.removeIf(Telegraph::isDone);
    }

    // 柱の見た目: 床から天井近くまでの光の粒と、足もとの雲
    private void effects(ServerLevel level, RaidCtx ctx) {
        double y0 = ctx.origin().getY() + 1;
        if (age % 2 == 0) {
            for (int k = 0; k < 7; k++) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, colX + (level.random.nextDouble() - 0.5) * 0.5, y0 + k * 1.1, colZ + (level.random.nextDouble() - 0.5) * 0.5, 1, 0.1, 0.1, 0.1, 0.02);
            }
            level.sendParticles(ParticleTypes.CLOUD, colX, y0 + 0.2, colZ, 2, 0.4, 0.1, 0.4, 0.01);
        }
        if (age % 12 == 0) {
            level.playSound(null, BlockPos.containing(colX, y0, colZ), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.25f, 2.0f);
        }
        if (phase == Phase.CHARGE && age % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, colX, y0 + 1.5, colZ, 8, 0.6, 1.2, 0.6, 0.12);
        }
    }

    @Override
    public boolean isComplete() {
        return count >= KEYS;
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        for (Telegraph t : beams) {
            t.cancel();
        }
        beams.clear();
        for (int i = 0; i < KEYS; i++) {
            taken[i] = false;
            setKey(ctx, i, true);
        }
        count = 0;
        started = false;
        touchCd = 0;
        StormRoomDeco.exitLit(ctx, false);
        colX = ctx.origin().getX() + 0.5;
        colZ = ctx.origin().getZ() - HZ + 3 + 0.5;
        enter(Phase.GRACE);
    }

    @Override
    public void end(RaidCtx ctx) {
        generation++;
        for (Telegraph t : beams) {
            t.cancel();
        }
        beams.clear();
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.SR4.keys";
    }

    @Override
    public int counter() {
        return count;
    }

    @Override
    public int counterMax() {
        return KEYS;
    }

    // ---- 試験用
    public Phase phase() {
        return phase;
    }

    public double colX() {
        return colX;
    }

    public double colZ() {
        return colZ;
    }

    public double keyX(RaidCtx ctx, int i) {
        return ctx.origin().getX() + keyRel[i][0] + 0.5;
    }

    public double keyZ(RaidCtx ctx, int i) {
        return ctx.origin().getZ() + keyRel[i][1] + 0.5;
    }

    public int[] keyOffset(int i) {
        return keyRel[i];
    }

    public List<int[]> pillars() {
        return pillars;
    }

    // 行き先の印: まだ取っていない鍵 (いちばん若い番号)
    @Override
    @javax.annotation.Nullable
    public BlockPos guideTarget(RaidCtx ctx) {
        for (int i = 0; i < KEYS; i++) {
            if (!taken[i]) {
                return ctx.origin().offset(keyRel[i][0], 1, keyRel[i][1]);
            }
        }
        return null;
    }

    public boolean taken(int i) {
        return taken[i];
    }
}
