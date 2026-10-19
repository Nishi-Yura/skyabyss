package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.titan.tide.heart.TideHeartNetwork;
import jp.revati.skyabyss.titan.tide.mob.BubbleShot;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 中ボス「潮招き」(08 の 5.2節)。片方のはさみだけが大きな、山のような蟹。干潟の中央の砂から現れる。
// 技: 大鋏の打ち下ろし (前方 幅 3・長さ 7 の帯。打ったあと 1.5 秒はさみが刺さり、その間の傷は 1.5 倍)、
//     招き潮 (30 秒ごと、怒ると 20 秒。干潟だけが 8 秒 2 マス満ち、満ちている間は速さ II、打ち下ろしの帯が水の線になって 2 倍に)、
//     泡の吐息 (泡 3 つ)、砂潜り (18 秒ごと。2 秒は傷が通らない。人の足もとに 1.2 秒の渦、突き上げ)、怒りの咆哮 (体力 50% で一度)。
// 体力は 360 で、挑む人数の倍率 (1 + 0.6 × (人数 - 1)) で受ける傷を割る (心臓と同じ)。
// 傷のある技はすべて Telegraph (combat。docs/design/02 の 5.4) で出す。印と当たりは同じ形で、予告は 0.8 秒以上。接触の傷はない。
// n = 干潟の戦いの人数。人数が増えると、打ち下ろしの帯 (1〜3 本)・泡の吐息の帯 (3〜8 本)・砂の間欠泉 (0〜3 か所)・咆哮の返し波 (1〜2 重) が増える
public class TideBeckoner extends TideMob {
    public static final float HEALTH = 360;
    public static final float ARMOR = 10;
    public static final float TOUGHNESS = 4;
    public static final float SLAM_DAMAGE = 12;
    public static final float BURROW_DAMAGE = 10;
    public static final float ROAR_DAMAGE = 4;
    public static final int SLAM_WINDUP = 20;
    public static final int SLAM_STUCK = 30;
    public static final int SLAM_PULL = 8;
    public static final double SLAM_LENGTH = 7;
    public static final double SLAM_HALF_WIDTH = 1.5;
    public static final int BECKON_WINDUP = 30;
    public static final int BECKON_FLOOD = 160;
    public static final int BECKON_EVERY = 600;
    public static final int BECKON_EVERY_ANGRY = 400;
    public static final int BREATH_WINDUP = 20;
    public static final int BURROW_DIG = 20;
    public static final int BURROW_VORTEX = 24;
    public static final int BURROW_INVULNERABLE = 40;
    public static final int BURROW_EVERY = 360;
    public static final int ROAR_TICKS = 30;
    // 範囲 (ブロック)
    public static final double BREATH_LENGTH = 18;
    public static final double BREATH_HALF_WIDTH = 0.9;
    public static final float BREATH_DAMAGE = 4;
    public static final double BURROW_RADIUS = 2.5;
    public static final double GEYSER_RADIUS = 2.0;
    public static final float GEYSER_DAMAGE = 7;
    public static final float RIPPLE_DAMAGE = 5;
    public static final double ROAR_RADIUS = 6;
    public static final double ROAR_RIPPLE_REACH = 13;
    public static final double BURROW_RIPPLE_REACH = 9;
    private static final double LANE_HALF = Math.toRadians(24);

    // 人数 (n) で増える数。自己テストが同じ式を見る
    public static int slamLines(int n) {
        return n >= 5 ? 3 : n >= 3 ? 2 : 1;
    }

    // 泡の吐息: 狙う人 (最大 4) ごとの扇。一人は 3 本、二人からは一人あたり 2 本
    public static int breathTargets(int n) {
        return Math.min(Math.max(n, 1), 4);
    }

    public static int breathLines(int n) {
        return breathTargets(n) * (n <= 1 ? 3 : 2);
    }

    // 砂潜りで主の人のほかに間欠泉が立つ数 (人数 - 1、最大 3)
    public static int geysers(int n) {
        return Math.min(Math.max(n - 1, 0), 3);
    }

    // 咆哮の返し波 (跳び越える輪): 三人から二重
    public static int roarRipples(int n) {
        return n >= 3 ? 2 : 1;
    }

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(TideBeckoner.class, EntityDataSerializers.INT);
    public static final int MODE_NORMAL = 0;
    public static final int MODE_STUCK = 1;
    public static final int MODE_BURROWED = 2;

    enum Action {
        NONE, SLAM, BECKON, BREATH, BURROW, ROAR
    }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private int actionTick;
    private int slamCooldown = 40;
    private int breathCooldown = 80;
    private int beckonCooldown = 200;
    private int burrowCooldown = BURROW_EVERY;
    private int invulnerable;
    private boolean roared;
    private boolean angry;
    private float multiplier = 1f;
    // 打ち下ろしの帯の向き (振り上げたときに決める)
    private Vec3 slamDir = new Vec3(0, 0, 1);
    @Nullable
    private ServerPlayer target;
    private Vec3 burrowAt = Vec3.ZERO;
    // 試験・開発用: 戦いの人を決める (null なら干潟の戦いの人)、人数を多いことにする、技を自分で始めない
    @Nullable
    private List<ServerPlayer> devFighters;
    private int partyOverride;
    private boolean holdAttacks;

    public TideBeckoner(EntityType<? extends Monster> type, Level level) {
        super(type, level, "tide_beckoner", "slam_raise", "slam_strike", "slam_stuck", "slam_pull", "beckon", "breath", "burrow", "emerge", "roar");
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, SLAM_DAMAGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, MODE_NORMAL);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    private void setMode(int m) {
        entityData.set(MODE, m);
    }

    @Override
    protected boolean frozen() {
        return mode() != MODE_NORMAL || action != Action.NONE;
    }

    @Override
    protected void registerGoals() {
        // 動きは aiStep の中で決める (技の間は止まる)
    }

    public void setChallengers(int n) {
        multiplier = 1f + 0.6f * (Math.max(1, n) - 1);
    }

    public float multiplier() {
        return multiplier;
    }

    // 現れる (砂の中から)
    public void emergeAt(ServerLevel level, Vec3 at) {
        moveTo(at.x, at.y, at.z, getYRot(), 0);
        playAction("emerge");
        playSound(TideBackSetup.BECKONER_EMERGE.get(), 3f, 1f);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()),
                at.x, at.y + 0.5, at.z, 120, 2.0, 0.6, 2.0, 0.2);
        invulnerable = 16;
    }

    // ---------------------------------------------------------------- 毎 tick
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) {
            updateBossBar(server);
        }
        if (invulnerable > 0) {
            invulnerable--;
        }
        if (isDeadOrDying()) {
            return;
        }
        slamCooldown--;
        breathCooldown--;
        beckonCooldown--;
        burrowCooldown--;
        target = pickTarget(server);
        if (!roared && !holdAttacks && getHealth() < getMaxHealth() * 0.5f && action == Action.NONE) {
            roared = true;
            angry = true;
            start(server, Action.ROAR);
        }
        if (action != Action.NONE) {
            getNavigation().stop();
            tickAction(server);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 20f, 20f);
        if (holdAttacks) {
            return;
        }
        double dist = Math.sqrt(distanceToSqr(target));
        if (beckonCooldown <= 0 && !TideFlats.beckoning()) {
            start(server, Action.BECKON);
        } else if (burrowCooldown <= 0) {
            start(server, Action.BURROW);
        } else if (dist < 6 && slamCooldown <= 0) {
            start(server, Action.SLAM);
        } else if (dist < 18 && dist > 5 && breathCooldown <= 0) {
            start(server, Action.BREATH);
        } else if (dist > 3.5) {
            double speed = TideFlats.beckoning() ? 1.45 : 1.0;
            getNavigation().moveTo(target, speed);
        } else {
            getNavigation().stop();
        }
    }

    // 狙える人 (干潟の戦いの人。試験・開発では指定した人)
    private List<ServerPlayer> fighters(ServerLevel server) {
        return devFighters != null ? devFighters : TideFlats.fighters(server);
    }

    private boolean isFighter(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && level() instanceof ServerLevel server && fighters(server).contains(p);
    }

    // 戦いの人数 (開発用の上書きがあれば、多いほう)
    private int present(ServerLevel server) {
        return Math.max(partyOverride, Math.max(1, fighters(server).size()));
    }

    // 試験・開発用
    public void holdAttacks() {
        holdAttacks = true;
    }

    public void setDevFighters(@Nullable List<ServerPlayer> players) {
        devFighters = players;
    }

    public void setPartyOverride(int count) {
        partyOverride = count;
    }

    public int actionTickCount() {
        return actionTick;
    }

    public boolean idle() {
        return action == Action.NONE;
    }

    // 試験・開発用: 技を一つ始める (SLAM / BECKON / BREATH / BURROW / ROAR)
    public void forceAction(String name) {
        if (level() instanceof ServerLevel server) {
            target = pickTarget(server);
            start(server, Action.valueOf(name.toUpperCase(java.util.Locale.ROOT)));
        }
    }

    // 予告の組み立て (持ち主は潮招き、当たるのは戦いの人)
    // 印の種類 (Telegraph の preset): D = 傷が出る (danger)、S = 安全な筋 (safe)、H = 押すだけ・見た目だけ (harmless)
    private static final int D = 0;
    private static final int S = 1;
    private static final int H = 2;

    private static Telegraph.Builder preset(Telegraph.Builder b, int style) {
        return switch (style) {
            case S -> b.safe();
            case H -> b.harmless();
            default -> b.danger();
        };
    }

    private Telegraph.Builder tg(ServerLevel server, TelegraphShape shape, double floor, int warn, int style) {
        return preset(Telegraph.in(server, shape).floor(floor).warn(warn).owner(this).victims(this::isFighter).reach(3.5), style);
    }

    private static double[] lanes(double base, int count) {
        double[] gaps = new double[count];
        for (int i = 0; i < count; i++) {
            gaps[i] = base + Math.PI * 2 * i / count;
        }
        return gaps;
    }

    // 床を走る輪 (跳べば当たらない。安全な筋は何もない)
    private void ripple(ServerLevel server, double cx, double cz, double floor, double r0, double r1, int warn, double[] gaps, float damage) {
        int active = (int) Math.ceil((r1 - r0) / 0.55);
        tg(server, TelegraphShape.wave(cx, cz, r0, r1, 0.9, gaps, LANE_HALF), floor, warn, D).active(active).jumpClear(0.9)
                .onHit((s, p) -> {
                    p.hurt(attackSource(), damage);
                    Vec3 away = p.position().subtract(cx, floor, cz).multiply(1, 0, 1);
                    if (away.lengthSqr() > 1e-4) {
                        p.knockback(0.6, -away.x, -away.z);
                        p.hurtMarked = true;
                    }
                })
                .start();
        // 安全な筋 (見た目だけ。緑の扇)
        for (double gap : gaps) {
            tg(server, TelegraphShape.cone(cx, cz, gap, r1, LANE_HALF), floor, warn, S).start();
        }
    }

    @Nullable
    private ServerPlayer pickTarget(ServerLevel server) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : fighters(server)) {
            double d = distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void start(ServerLevel server, Action a) {
        action = a;
        actionTick = 0;
        switch (a) {
            case SLAM -> {
                Vec3 to = target != null ? target.position().subtract(position()) : getLookAngle();
                slamDir = new Vec3(to.x, 0, to.z).normalize();
                if (slamDir.lengthSqr() < 1e-4) {
                    slamDir = new Vec3(0, 0, 1);
                }
                setYRot((float) (Mth.atan2(slamDir.z, slamDir.x) * Mth.RAD_TO_DEG) - 90f);
                yBodyRot = getYRot();
                yHeadRot = getYRot();
                playAction("slam_raise");
                sound(server, TideBackSetup.BECKONER_RAISE.get(), 2.5f, 1f);
                telegraphSlam(server);
            }
            case BECKON -> {
                playAction("beckon");
                sound(server, TideBackSetup.BECKONER_BECKON.get(), 3f, 0.8f);
                TideFlats.warnBeckon(server, BECKON_WINDUP);
            }
            case BREATH -> {
                playAction("breath");
                sound(server, TideBackSetup.BECKONER_BREATH.get(), 2f, 1f);
                telegraphBreath(server);
            }
            case BURROW -> {
                playAction("burrow");
                sound(server, TideBackSetup.BECKONER_BURROW.get(), 3f, 1f);
                invulnerable = BURROW_INVULNERABLE;
            }
            case ROAR -> {
                playAction("roar");
                sound(server, TideBackSetup.BECKONER_ROAR.get(), 4f, 0.8f);
                telegraphRoar(server);
            }
            default -> {
            }
        }
    }

    private void tickAction(ServerLevel server) {
        actionTick++;
        switch (action) {
            case SLAM -> tickSlam(server);
            case BECKON -> {
                if (actionTick == BECKON_WINDUP) {
                    TideFlats.beckon(server, BECKON_FLOOD);
                    beckonCooldown = angry ? BECKON_EVERY_ANGRY : BECKON_EVERY;
                    finish();
                }
            }
            case BREATH -> {
                if (actionTick % 4 == 0 && actionTick < BREATH_WINDUP) {
                    Vec3 m = mouth();
                    warn(server, ParticleTypes.BUBBLE_POP, m.x, m.y, m.z, 6, 0.3, 0.2, 0.3, 0.02);
                }
                if (actionTick >= BREATH_WINDUP + 4) {
                    breathCooldown = 70 + random.nextInt(40);
                    finish();
                }
            }
            case BURROW -> tickBurrow(server);
            case ROAR -> {
                if (actionTick % 5 == 0 && actionTick < ROAR_TICKS) {
                    warn(server, new DustParticleOptions(new Vector3f(0.75f, 0.25f, 0.25f), 2f), getX(), getY() + 0.2, getZ(), 30, 3, 0.1, 3, 0);
                }
                if (actionTick == ROAR_TICKS) {
                    server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 4, 2, 0.2, 2, 0);
                    TideFlats.roarSpawn(server, this);
                    finish();
                }
            }
            default -> finish();
        }
    }

    // 打ち下ろしの帯 (幅 3・長さ 7、水が満ちていれば 2 倍の 14 で、水の線は岩の上に立つ人には当たらない)。
    // 三人から、別の人へ向けた帯が増える (3〜4 人は 2 本、5 人から 3 本)。向きは振り上げたときに決まる
    private void telegraphSlam(ServerLevel server) {
        int n = present(server);
        double[] angles = new double[slamLines(n)];
        angles[0] = Telegraph.angleOf(slamDir.x, slamDir.z);
        List<ServerPlayer> others = new ArrayList<>(fighters(server));
        others.remove(target);
        java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
        for (int i = 1; i < angles.length; i++) {
            if (i - 1 < others.size()) {
                ServerPlayer o = others.get(i - 1);
                angles[i] = Telegraph.angleOf(o.getX() - getX(), o.getZ() - getZ());
            } else {
                // 人が足りないとき (開発用の人数の上書き): 主の帯の両側へ広げる
                angles[i] = angles[0] + (i % 2 == 1 ? 1 : -1) * Math.toRadians(40) * ((i + 1) / 2);
            }
        }
        boolean flooded = TideFlats.flatsFlooded(server);
        double ground = groundY(position());
        for (int i = 0; i < angles.length; i++) {
            double angle = angles[i];
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            boolean first = i == 0;
            tg(server, TelegraphShape.line(getX(), getZ(), angle, SLAM_LENGTH, SLAM_HALF_WIDTH), ground, SLAM_WINDUP, D).target(target)
                    .onImpact(s -> slamImpact(s, dx, dz, SLAM_LENGTH, 0, flooded, first))
                    .onHit((s, p) -> p.hurt(attackSource(), SLAM_DAMAGE))
                    .start();
            if (flooded) {
                // 水の線 (後ろの半分)。岩の上 (島) に立つ人には当たらない
                tg(server, TelegraphShape.line(getX() + dx * SLAM_LENGTH, getZ() + dz * SLAM_LENGTH, angle, SLAM_LENGTH, SLAM_HALF_WIDTH), ground, SLAM_WINDUP, D)
                        .reach(2.5)
                        .onImpact(s -> slamImpact(s, dx, dz, SLAM_LENGTH, SLAM_LENGTH, true, false))
                        .onHit((s, p) -> p.hurt(attackSource(), SLAM_DAMAGE))
                        .start();
            }
        }
    }

    private void slamImpact(ServerLevel server, double dx, double dz, double length, double from, boolean water, boolean first) {
        for (double f = from + 1; f <= from + length; f += 0.8) {
            Vec3 p = position().add(dx * f, 0, dz * f);
            server.sendParticles(water ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, p.x, groundY(p) + 0.3, p.z, water ? 10 : 3, 0.6, 0.1, 0.6, 0.1);
        }
        if (first) {
            sound(server, TideBackSetup.BECKONER_SLAM.get(), 3f, 0.9f);
        }
    }

    private void tickSlam(ServerLevel server) {
        if (actionTick < SLAM_WINDUP) {
            return;
        }
        if (actionTick == SLAM_WINDUP) {
            playAction("slam_strike");
            playAction("slam_stuck");
            setMode(MODE_STUCK);
            return;
        }
        if (actionTick == SLAM_WINDUP + SLAM_STUCK) {
            setMode(MODE_NORMAL);
            playAction("slam_pull");
            return;
        }
        if (actionTick >= SLAM_WINDUP + SLAM_STUCK + SLAM_PULL) {
            slamCooldown = 50 + random.nextInt(30);
            finish();
        }
    }

    // 泡の吐息の帯 (幅 1.8・長さ 18、予告 1 秒): 口から、狙う人ごとの扇 (一人は 3 本、二人からは一人あたり 2 本。最大 4 人)。
    // 当たると傷 4 と、真上へ浮く (泡吹きの泡と同じ)。向きは吐く前に決まる
    private void telegraphBreath(ServerLevel server) {
        int n = present(server);
        List<ServerPlayer> all = new ArrayList<>(fighters(server));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        if (target != null && all.remove(target)) {
            all.add(0, target);
        }
        Vec3 m = mouth();
        List<Double> centers = new ArrayList<>();
        List<ServerPlayer> aimed = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (centers.size() < breathTargets(n)) {
                centers.add(Telegraph.angleOf(p.getX() - m.x, p.getZ() - m.z));
                aimed.add(p);
            }
        }
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        double fallback = Telegraph.angleOf(look.x, look.z);
        while (centers.size() < breathTargets(n)) {
            // 人が足りないとき (開発用の人数の上書き): 前方のまわりへ散らす
            int k = centers.size();
            centers.add(fallback + Math.toRadians(70) * (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2));
        }
        double ground = groundY(position());
        double fan = n <= 1 ? 0.22 : 0.12;
        int perTarget = n <= 1 ? 3 : 2;
        boolean first = true;
        for (int c = 0; c < centers.size(); c++) {
            double center = centers.get(c);
            ServerPlayer aim = c < aimed.size() ? aimed.get(c) : null;
            for (int k = 0; k < perTarget; k++) {
                double angle = center + (perTarget == 3 ? (k - 1) * fan : (k == 0 ? -fan : fan));
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                boolean sounds = first;
                first = false;
                tg(server, TelegraphShape.line(m.x, m.z, angle, BREATH_LENGTH, BREATH_HALF_WIDTH), ground, BREATH_WINDUP, D).target(aim)
                        .onImpact(s -> {
                            if (sounds) {
                                s.playSound(null, m.x, m.y, m.z, jp.revati.skyabyss.titan.tide.mob.TideMobSetup.BUBBLE_POP.get(), SoundSource.HOSTILE, 1.2f, 1f);
                            }
                            for (double d = 2; d <= BREATH_LENGTH; d += 2.0) {
                                s.sendParticles(ParticleTypes.BUBBLE_POP, m.x + dx * d, ground + 1.0, m.z + dz * d, 4, 0.4, 0.4, 0.4, 0.05);
                            }
                        })
                        .onHit((s, p) -> {
                            Vec3 before = p.getDeltaMovement();
                            if (p.hurt(attackSource(), BREATH_DAMAGE)) {
                                BubbleShot.lift(p, before);
                            }
                        })
                        .start();
            }
        }
    }

    // 咆哮 (1.5 秒の予告): 足もとの半径 6 の円が衝撃 4 と押し返し、そのあと円の外へ跳び越える輪が広がる (三人から二重)。輪の安全な筋は緑
    private void telegraphRoar(ServerLevel server) {
        double ground = groundY(position());
        double cx = getX();
        double cz = getZ();
        tg(server, TelegraphShape.circle(cx, cz, ROAR_RADIUS), ground, ROAR_TICKS, D)
                .onHit((s, p) -> {
                    p.hurt(attackSource(), ROAR_DAMAGE);
                    Vec3 away = p.position().subtract(position()).multiply(1, 0, 1);
                    if (away.lengthSqr() > 1e-4) {
                        away = away.normalize().scale(1.2);
                        TideHeartNetwork.nudge(p, away.x, 0.45, away.z);
                    }
                })
                .start();
        double[] gaps = lanes(random.nextDouble() * Math.PI * 2, 2);
        int ripples = roarRipples(present(server));
        for (int i = 0; i < ripples; i++) {
            ripple(server, cx, cz, ground, ROAR_RADIUS + 1.0, ROAR_RIPPLE_REACH, ROAR_TICKS + i * 14, gaps, ROAR_DAMAGE);
        }
    }

    // 砂潜り: 20 tick 掘って消え、人の足もとへ付いていく渦 (はじめの 0.2 秒だけ)。そのあと渦は止まり、予告の円 (半径 2.5、1 秒) が出て、
    // 突き上げて現れる (円の中は傷 10 と跳ね上げ。現れたあと、円の外へ跳び越える輪が広がる)。
    // 人数が増えると、別の人の足もとにも砂の間欠泉 (半径 2、傷 7) が同時に立つ (人数 - 1、最大 3)
    private void tickBurrow(ServerLevel server) {
        int lockAt = BURROW_DIG + 4;
        if (actionTick == BURROW_DIG) {
            setMode(MODE_BURROWED);
            setInvisible(true);
            ServerPlayer t = target;
            burrowAt = t != null ? t.position() : position();
        }
        if (actionTick > BURROW_DIG && actionTick < BURROW_DIG + BURROW_VORTEX) {
            ServerPlayer t = target;
            if (t != null && actionTick < lockAt) {
                // 渦は人の足もとへ付いていく (はじめの 0.2 秒だけ。あとは止まって、逃げられる)
                burrowAt = t.position();
            }
            if (actionTick == lockAt) {
                telegraphBurrow(server);
            }
            if (actionTick % 2 == 0) {
                for (int i = 0; i < 10; i++) {
                    double a = (actionTick * 0.5 + i * Math.PI / 5);
                    double r = 0.6 + (i % 3) * 0.7;
                    warn(server, new DustParticleOptions(new Vector3f(0.85f, 0.75f, 0.45f), 1.5f), burrowAt.x + Math.cos(a) * r,
                            burrowAt.y + 0.1, burrowAt.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (actionTick % 6 == 0) {
                    server.playSound(null, burrowAt.x, burrowAt.y, burrowAt.z, net.minecraft.sounds.SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
                }
            }
            return;
        }
        if (actionTick == BURROW_DIG + BURROW_VORTEX) {
            setInvisible(false);
            setMode(MODE_NORMAL);
            emergeAt(server, new Vec3(burrowAt.x, burrowAt.y, burrowAt.z));
            return;
        }
        if (actionTick >= BURROW_DIG + BURROW_VORTEX + 16) {
            burrowCooldown = BURROW_EVERY;
            finish();
        }
    }

    private void telegraphBurrow(ServerLevel server) {
        int warn = BURROW_VORTEX - 4;
        double bx = burrowAt.x;
        double bz = burrowAt.z;
        double by = burrowAt.y;
        tg(server, TelegraphShape.circle(bx, bz, BURROW_RADIUS), by, warn, D)
                .onHit((s, p) -> {
                    p.hurt(attackSource(), BURROW_DAMAGE);
                    TideHeartNetwork.nudge(p, 0, 0.9, 0);
                })
                .start();
        ripple(server, bx, bz, by, BURROW_RADIUS + 0.9, BURROW_RIPPLE_REACH, warn, lanes(random.nextDouble() * Math.PI * 2, 2), RIPPLE_DAMAGE);
        int n = present(server);
        List<ServerPlayer> others = new ArrayList<>(fighters(server));
        others.remove(target);
        java.util.Collections.shuffle(others, new java.util.Random(random.nextLong()));
        for (int i = 0; i < geysers(n); i++) {
            double gx;
            double gz;
            double gy;
            if (i < others.size()) {
                gx = others.get(i).getX();
                gz = others.get(i).getZ();
                gy = others.get(i).getY();
            } else {
                // 人が足りないとき (開発用の人数の上書き): 主の円のまわり
                double a = random.nextDouble() * Math.PI * 2;
                double r = 6 + random.nextDouble() * 3;
                gx = bx + Math.cos(a) * r;
                gz = bz + Math.sin(a) * r;
                gy = by;
            }
            double fx = gx;
            double fz = gz;
            double fy = gy;
            tg(server, TelegraphShape.circle(gx, gz, GEYSER_RADIUS), gy, warn, D)
                    .onImpact(s -> {
                        s.playSound(null, fx, fy, fz, net.minecraft.sounds.SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.6f, 0.7f);
                        s.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()),
                                fx, fy + 0.5, fz, 40, 0.8, 0.6, 0.8, 0.2);
                    })
                    .onHit((s, p) -> {
                        p.hurt(attackSource(), GEYSER_DAMAGE);
                        TideHeartNetwork.nudge(p, 0, 0.6, 0);
                    })
                    .start();
        }
    }

    private void finish() {
        action = Action.NONE;
        actionTick = 0;
    }

    private Vec3 bandPoint(double forward, double side) {
        return position().add(slamDir.x * forward - slamDir.z * side, 0, slamDir.z * forward + slamDir.x * side);
    }

    private double groundY(Vec3 p) {
        BlockPos b = BlockPos.containing(p.x, getY() + 2, p.z);
        for (int i = 0; i < 6; i++) {
            if (!level().getBlockState(b).getCollisionShape(level(), b).isEmpty()) {
                return b.getY() + 1;
            }
            b = b.below();
        }
        return getY();
    }

    private Vec3 mouth() {
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(look.x * 1.6, 1.4, look.z * 1.6);
    }

    // 技の音は、潮招きのグループの人にだけ (GuardMob.playSound → GroupOwned.playToGroup)
    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        playSound(sound, volume, pitch);
    }

    // ---------------------------------------------------------------- 傷
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            return super.hurt(source, amount);
        }
        if (invulnerable > 0 || mode() == MODE_BURROWED || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return false;
        }
        float scaled = amount / multiplier;
        if (mode() == MODE_STUCK) {
            scaled *= 1.5f;
        }
        boolean hit = super.hurt(source, scaled);
        if (hit) {
            playSound(TideBackSetup.BECKONER_HURT.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        playSound(TideBackSetup.BECKONER_DEATH.get(), 4f, 1f);
        setMode(MODE_NORMAL);
        setInvisible(false);
        if (level() instanceof ServerLevel server) {
            TideFlats.onBeckonerDefeated(server, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // ボスバーは、近くにいる潮招きのグループの人にだけ
    private void updateBossBar(ServerLevel server) {
        List<ServerPlayer> keep = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(server.players())) {
            if (p.distanceToSqr(this) < 48 * 48 && isAlive() && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, p)) {
                keep.add(p);
            }
        }
        for (ServerPlayer p : new ArrayList<>(bossBar.getPlayers())) {
            if (!keep.contains(p)) {
                bossBar.removePlayer(p);
            }
        }
        for (ServerPlayer p : keep) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TideBackSetup.BECKONER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TideBackSetup.BECKONER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TideBackSetup.BECKONER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    // 戦いの範囲 (干潟) の外へ出たら、真ん中へ戻す (TideFlats が見る)
    public AABB arena() {
        return TideBackLayout.FLATS.aabb().inflate(0, 4, 0);
    }
}
