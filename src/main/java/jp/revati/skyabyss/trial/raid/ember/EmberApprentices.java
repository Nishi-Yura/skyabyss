package jp.revati.skyabyss.trial.raid.ember;

import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrials;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

// 闘 ER6「鍛え手の弟子たち」(docs/design/13_ember.md)。灰の鍛え手 (AshSmith) に鍛えられた弟子が、一人ずつ出てくる。四人。
//  - 弟子は灰被りの体 (殻つき) に、鍛え手の技を一つずつ借りて覚えている。後の弟子ほど体が強く、技が増える (前の技も使う):
//      一 鎚打ち (前方の帯・赤い予告 1 秒)   二 + 火花散らし (足もとへ円が三つ)   三 + 鞴の息 (前方の扇)   四 + 焼き入れ (自分のまわりの大きな円)
//  - 傷のある技は、すべて赤い予告のあとに当たる。人数が増えると、弟子の体力と火花の数が増える。一人でも、印を見れば傷なしで避けられる
//  - 一人が倒れると、少し間があって次が出る。四人とも倒せば越える。全員が倒れると部屋は最初から (弟子は消える)
public final class EmberApprentices extends EmberRoom {
    public static final String ID = "ER6";
    public static final int COUNT = 4;
    public static final int HX = 11;
    public static final int HZ = 11;
    public static final int FIRST_DELAY = 60;
    public static final int GAP = 70;
    public static final int MOVE_WARN = 22;
    public static final float SLAM_DAMAGE = 8;
    public static final float SPARK_DAMAGE = 6;
    public static final float BREATH_DAMAGE = 6;
    public static final float QUENCH_DAMAGE = 7;
    private static final float[] HEALTH_MULT = {0.7f, 1.0f, 1.4f, 1.9f};
    private static final int[] COOLDOWN = {64, 56, 50, 44};

    public static final int SLAM = 0;
    public static final int SPARKS = 1;
    public static final int BREATH = 2;
    public static final int QUENCH = 3;

    @Nullable
    private Mob current;
    private int spawned;
    private int defeated;
    private long t;
    private long nextSpawn;
    private long nextMove;
    private int moveIndex;
    private boolean done;
    private int lastMove = -1;

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, 9, RaidKit.EMBER));
        var lv = ctx.level();
        // 中央の金床の輪 (弟子が出てくる所)
        RaidKit.fill(lv, ctx.local(-2, 0, -2), ctx.local(2, 0, 2), b(Blocks.POLISHED_BASALT));
        RaidKit.fill(lv, ctx.local(-1, 0, -1), ctx.local(1, 0, 1), b(Blocks.SMOOTH_BASALT));
        set(ctx, ctx.local(0, 1, 0), b(Blocks.ANVIL));
        // 四隅の柱と、壁際の道具棚 (西と東: 炉と砥石)
        for (int[] d : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            RaidKit.fill(lv, ctx.local(d[0], 1, d[1]), ctx.local(d[0], 3, d[1]), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            set(ctx, ctx.local(d[0], 4, d[1]), b(Blocks.SHROOMLIGHT));
        }
        for (int z = -4; z <= 4; z += 4) {
            set(ctx, ctx.local(-HX, 1, z), b(Blocks.BLAST_FURNACE));
            set(ctx, ctx.local(HX, 1, z), b(Blocks.GRINDSTONE));
        }
        // 北の壁の四つの灯 (倒した弟子の数だけ灯る。消えているときは黒い石)
        for (int i = 0; i < COUNT; i++) {
            RaidKit.fill(lv, ctx.local(lampX(i) - 1, 3, -HZ), ctx.local(lampX(i) + 1, 5, -HZ), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        }
        resetState(ctx);
    }

    private static int lampX(int i) {
        return -7 + i * 14 / 3 - (i == 3 ? 0 : 0);
    }

    private void lamps(RaidCtx ctx) {
        for (int i = 0; i < COUNT; i++) {
            set(ctx, ctx.local(lampX(i), 4, -HZ), i < defeated ? b(Blocks.SHROOMLIGHT) : b(Blocks.BLACKSTONE));
        }
    }

    private void resetState(RaidCtx ctx) {
        current = null;
        spawned = 0;
        defeated = 0;
        t = 0;
        nextSpawn = FIRST_DELAY;
        nextMove = 0;
        moveIndex = 0;
        lastMove = -1;
        done = false;
        lamps(ctx);
    }

    @Override
    public void start(RaidCtx ctx) {
        resetState(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        failRequested = false;
        ctx.clearMobs();
        resetState(ctx);
    }

    private void spawnApprentice(RaidCtx ctx) {
        int i = spawned;
        Mob m = spawn(ctx, EmberMobSetup.ASH_THRALL.get(), ctx.local(0, 2, 0));
        if (m == null) {
            nextSpawn = t + 20;
            return;
        }
        float hp = 60f * HEALTH_MULT[i] * (1f + 0.6f * (scale(ctx) - 1));
        var attr = m.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
        }
        m.setHealth(hp);
        m.setCustomName(Component.translatable("raid.skyabyss.ER6.apprentice", i + 1));
        m.setCustomNameVisible(true);
        current = m;
        spawned++;
        nextMove = t + 50;
        moveIndex = 0;
        particles(ctx, ParticleTypes.LAVA, m.getX(), m.getY() + 1, m.getZ(), 20, 0.6, 0.2);
        ctx.level().playSound(null, m.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2f, 0.5f);
        bar(ctx, Component.translatable("raid.skyabyss.ER6.enter", i + 1, COUNT));
    }

    @Nullable
    private ServerPlayer nearest(List<ServerPlayer> ps, Mob m) {
        ServerPlayer best = null;
        double bd = 1e9;
        for (ServerPlayer p : ps) {
            double d = p.distanceToSqr(m);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void hurt(RaidCtx ctx, ServerPlayer p, Mob by, float amount) {
        if (p.isCreative() || p.isSpectator()) {
            return;
        }
        p.invulnerableTime = 0;
        p.hurt(EmberTrials.damage(ctx.level(), EmberIds.HEAT_BURST, by), amount);
    }

    // 技 (弟子 i が使える技の中から、順に)。傷は赤い予告のあと。リセットをまたいだ予告は捨てる
    void doMove(RaidCtx ctx, Mob m, int move, List<ServerPlayer> ps) {
        final int gen = generation;
        ServerPlayer target = nearest(ps, m);
        if (target == null) {
            return;
        }
        lastMove = move;
        double fy = ctx.origin().getY() + 1;
        Vec3 d = new Vec3(target.getX() - m.getX(), 0, target.getZ() - m.getZ());
        if (d.lengthSqr() < 1e-4) {
            d = new Vec3(0, 0, 1);
        }
        d = d.normalize();
        switch (move) {
            case SLAM -> {
                Vec3 o = m.position();
                Telegraph.in(ctx.level(), new TelegraphShape.Line(o.x, o.z, Telegraph.angleOf(d.x, d.z), 7, 1.5)).floor(fy).warn(MOVE_WARN).danger()
                        .onImpact(l -> l.playSound(null, m.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.7f))
                        .onHit((l, p) -> {
                            if (gen == generation) {
                                hurt(ctx, p, m, SLAM_DAMAGE);
                            }
                        }).start();
            }
            case SPARKS -> {
                int n = Math.min(5, 3 + (ps.size() - 1) / 2);
                for (int k = 0; k < n; k++) {
                    ServerPlayer p0 = ps.get(k % ps.size());
                    double x = p0.getX();
                    double z = p0.getZ();
                    Telegraph.in(ctx.level(), new TelegraphShape.Circle(x, z, 1.8)).floor(fy).warn(MOVE_WARN + 4 * k).danger()
                            .onImpact(l -> l.sendParticles(ParticleTypes.LAVA, x, fy + 0.2, z, 8, 0.6, 0.1, 0.6, 0.1))
                            .onHit((l, p) -> {
                                if (gen == generation) {
                                    hurt(ctx, p, m, SPARK_DAMAGE);
                                }
                            }).start();
                }
            }
            case BREATH -> {
                Telegraph.in(ctx.level(), new TelegraphShape.Cone(m.getX(), m.getZ(), Telegraph.angleOf(d.x, d.z), 9, Math.toRadians(32))).floor(fy)
                        .warn(MOVE_WARN + 8).danger()
                        .onImpact(l -> l.playSound(null, m.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 1.2f, 0.6f))
                        .onHit((l, p) -> {
                            if (gen == generation) {
                                hurt(ctx, p, m, BREATH_DAMAGE);
                                p.knockback(0.8, -d0x(m, p), -d0z(m, p));
                            }
                        }).start();
            }
            default -> {
                double x = m.getX();
                double z = m.getZ();
                Telegraph.in(ctx.level(), new TelegraphShape.Circle(x, z, 4.5)).floor(fy).warn(MOVE_WARN + 4).danger()
                        .onImpact(l -> {
                            l.playSound(null, m.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.4f, 0.6f);
                            l.sendParticles(ParticleTypes.CLOUD, x, fy + 0.3, z, 30, 2.0, 0.1, 2.0, 0.05);
                        })
                        .onHit((l, p) -> {
                            if (gen == generation) {
                                hurt(ctx, p, m, QUENCH_DAMAGE);
                            }
                        }).start();
            }
        }
    }

    private static double d0x(Mob m, ServerPlayer p) {
        double dx = p.getX() - m.getX();
        double dz = p.getZ() - m.getZ();
        double l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        return dx / l;
    }

    private static double d0z(Mob m, ServerPlayer p) {
        double dx = p.getX() - m.getX();
        double dz = p.getZ() - m.getZ();
        double l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        return dz / l;
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done) {
            return;
        }
        t++;
        List<ServerPlayer> ps = who(ctx);
        if (current != null && (!current.isAlive() || current.isRemoved())) {
            current = null;
            defeated++;
            lamps(ctx);
            ctx.level().playSound(null, ctx.local(0, 2, 0), SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 1f, 0.7f);
            if (defeated >= COUNT) {
                done = true;
                particles(ctx, ParticleTypes.LAVA, ctx.origin().getX() + 0.5, ctx.origin().getY() + 3, ctx.origin().getZ() + 0.5, 30, 1.5, 0.2);
                return;
            }
            nextSpawn = t + GAP;
        }
        if (current == null && spawned < COUNT && t >= nextSpawn) {
            spawnApprentice(ctx);
        }
        if (current != null && !ps.isEmpty() && t >= nextMove) {
            int i = spawned - 1;
            Mob m = current;
            ServerPlayer near = nearest(ps, m);
            if (near != null && near.distanceTo(m) < 14) {
                int avail = i + 1;
                int move = moveIndex % avail;
                // 鎚は近いときだけ、輪は近いときだけ。遠いなら火花か扇に回す
                if ((move == SLAM || move == QUENCH) && near.distanceTo(m) > 8 && avail > 1) {
                    move = (move + 1) % avail;
                }
                moveIndex++;
                doMove(ctx, m, move, ps);
                nextMove = t + COOLDOWN[i] + MOVE_WARN;
            }
        }
        // 弟子は部屋の外へ出ない
        if (current != null && ctx.cell() != null && !ctx.cell().inArena(current.getX(), current.getZ())) {
            current.moveTo(ctx.origin().getX() + 0.5, ctx.origin().getY() + 1, ctx.origin().getZ() + 0.5);
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.ER6.fight";
    }

    @Override
    public int counter() {
        return defeated;
    }

    @Override
    public int counterMax() {
        return COUNT;
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.ER6.apprentice_hp";
    }

    @Override
    public int secondary() {
        return current == null ? 0 : (int) Math.ceil(100 * current.getHealth() / Math.max(1f, current.getMaxHealth()));
    }

    @Override
    public int secondaryMax() {
        return current == null ? 0 : 100;
    }

    // 自己テスト用の窓
    int spawned() {
        return spawned;
    }

    int defeated() {
        return defeated;
    }

    @Nullable
    Mob current() {
        return current;
    }

    int lastMove() {
        return lastMove;
    }

    BlockPos lampPos(RaidCtx ctx, int i) {
        return ctx.local(lampX(i), 4, -HZ);
    }
}
