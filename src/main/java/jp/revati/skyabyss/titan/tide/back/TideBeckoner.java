package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

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
// 体力は 360 で、挑む人数の倍率 (1 + 0.6 × (人数 - 1)) で受ける傷を割る (心臓と同じ)。予告は粒 (制限を無視) と技ごとの音と目盛り
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
    public static final int BREATH_WINDUP = 16;
    public static final int BURROW_DIG = 20;
    public static final int BURROW_VORTEX = 24;
    public static final int BURROW_INVULNERABLE = 40;
    public static final int BURROW_EVERY = 360;
    public static final int ROAR_TICKS = 30;

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
        if (!roared && getHealth() < getMaxHealth() * 0.5f && action == Action.NONE) {
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

    @Nullable
    private ServerPlayer pickTarget(ServerLevel server) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : TideFlats.fighters(server)) {
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
            }
            case BECKON -> {
                playAction("beckon");
                sound(server, TideBackSetup.BECKONER_BECKON.get(), 3f, 0.8f);
                TideFlats.warnBeckon(server, BECKON_WINDUP);
            }
            case BREATH -> {
                playAction("breath");
                sound(server, TideBackSetup.BECKONER_BREATH.get(), 2f, 1f);
            }
            case BURROW -> {
                playAction("burrow");
                sound(server, TideBackSetup.BECKONER_BURROW.get(), 3f, 1f);
                invulnerable = BURROW_INVULNERABLE;
            }
            case ROAR -> {
                playAction("roar");
                sound(server, TideBackSetup.BECKONER_ROAR.get(), 4f, 0.8f);
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
                if (actionTick % 4 == 0) {
                    Vec3 m = mouth();
                    warn(server, ParticleTypes.BUBBLE_POP, m.x, m.y, m.z, 6, 0.3, 0.2, 0.3, 0.02);
                }
                if (actionTick == BREATH_WINDUP) {
                    if (target != null) {
                        Vec3 m = mouth();
                        Vec3 dir = target.getEyePosition().subtract(m);
                        for (int i = -1; i <= 1; i++) {
                            Vec3 d = dir.yRot(i * 0.22f);
                            BubbleShot shot = new BubbleShot(server, this, d);
                            shot.setPos(m.x, m.y, m.z);
                            server.addFreshEntity(shot);
                        }
                    }
                    breathCooldown = 70 + random.nextInt(40);
                    finish();
                }
            }
            case BURROW -> tickBurrow(server);
            case ROAR -> {
                if (actionTick % 5 == 0) {
                    warn(server, new DustParticleOptions(new Vector3f(0.75f, 0.25f, 0.25f), 2f), getX(), getY() + 0.2, getZ(), 30, 3, 0.1, 3, 0);
                }
                if (actionTick == ROAR_TICKS) {
                    for (ServerPlayer p : TideFlats.fighters(server)) {
                        if (p.distanceToSqr(this) < 36) {
                            p.hurt(attackSource(), ROAR_DAMAGE);
                            Vec3 away = p.position().subtract(position()).normalize().scale(1.2);
                            TideHeartNetwork.nudge(p, away.x, 0.45, away.z);
                        }
                    }
                    server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 4, 2, 0.2, 2, 0);
                    TideFlats.roarSpawn(server, this);
                    finish();
                }
            }
            default -> finish();
        }
    }

    private void tickSlam(ServerLevel server) {
        double length = TideFlats.flatsFlooded(server) ? SLAM_LENGTH * 2 : SLAM_LENGTH;
        if (actionTick < SLAM_WINDUP) {
            if (actionTick % 4 == 0) {
                // 予告: 前方の帯に赤い粒 (水があるときは水の線の先まで)
                for (double f = 1; f <= length; f += 1.2) {
                    for (double s = -SLAM_HALF_WIDTH; s <= SLAM_HALF_WIDTH; s += 1.5) {
                        Vec3 p = bandPoint(f, s);
                        warn(server, new DustParticleOptions(new Vector3f(0.9f, 0.15f, 0.1f), 1.6f), p.x, groundY(p) + 0.15, p.z, 1, 0.1, 0, 0.1, 0);
                    }
                }
            }
            return;
        }
        if (actionTick == SLAM_WINDUP) {
            playAction("slam_strike");
            sound(server, TideBackSetup.BECKONER_SLAM.get(), 3f, 0.9f);
            boolean water = length > SLAM_LENGTH;
            for (ServerPlayer p : TideFlats.fighters(server)) {
                Vec3 rel = p.position().subtract(position());
                double f = rel.x * slamDir.x + rel.z * slamDir.z;
                double s = -rel.x * slamDir.z + rel.z * slamDir.x;
                boolean onRock = p.getY() >= TideBackLayout.ROCK_TOP + 0.9;
                double reach = water && !onRock ? length : SLAM_LENGTH;
                if (f >= 0 && f <= reach + 0.5 && Math.abs(s) <= SLAM_HALF_WIDTH + 0.4 && Math.abs(rel.y) < 3) {
                    p.hurt(attackSource(), SLAM_DAMAGE);
                }
            }
            for (double f = 1; f <= length; f += 0.8) {
                Vec3 p = bandPoint(f, 0);
                server.sendParticles(water ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, p.x, groundY(p) + 0.3, p.z, water ? 10 : 3, 0.6, 0.1, 0.6, 0.1);
            }
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

    private void tickBurrow(ServerLevel server) {
        if (actionTick == BURROW_DIG) {
            setMode(MODE_BURROWED);
            setInvisible(true);
            ServerPlayer t = target;
            burrowAt = t != null ? t.position() : position();
        }
        if (actionTick > BURROW_DIG && actionTick < BURROW_DIG + BURROW_VORTEX) {
            ServerPlayer t = target;
            if (t != null && actionTick < BURROW_DIG + 8) {
                // 渦は人の足もとへ付いていく (はじめの 0.4 秒だけ。あとは止まって、逃げられる)
                burrowAt = t.position();
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
            for (ServerPlayer p : TideFlats.fighters(server)) {
                if (p.distanceToSqr(burrowAt) < 2.5 * 2.5 + 1) {
                    p.hurt(attackSource(), BURROW_DAMAGE);
                    TideHeartNetwork.nudge(p, 0, 0.9, 0);
                }
            }
            return;
        }
        if (actionTick >= BURROW_DIG + BURROW_VORTEX + 16) {
            burrowCooldown = BURROW_EVERY;
            finish();
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

    private void sound(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
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

    private void updateBossBar(ServerLevel server) {
        List<ServerPlayer> keep = new ArrayList<>();
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(this) < 48 * 48 && isAlive()) {
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
