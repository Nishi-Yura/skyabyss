package jp.revati.skyabyss.titan.storm.chapter.mob;

import java.util.EnumSet;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.GroundFoe;
import jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 峰の雛 (docs/design/21 の 5.1。三章の地上): 急降下の予習 (羽守の突撃の縮小版)。HP 14、急降下 5。
//   巣     湧いた所 (岩棚・梁) が巣。ふだんは巣のまわりを歩く
//   急降下 狙いが DIVE_MIN〜DIVE_MAX にいて見えると、羽ばたいて RISE 上がり (RISE_TICKS)、地面に狙いへの光る線 (Telegraph の帯。WARN = 0.8 秒) を出す。
//          線が満ちた瞬間、線の上の人に 5。雛は線の先まで一直線に突っ込む。誰にも当たらなかったら、岩に当たってよろめく (STAGGER_TICKS。受ける傷 1.5 倍)
//   落とす物 焦げ羽 40%
public class PeakFledgling extends GroundFoe {
    public static final float HEALTH = 14;
    public static final float DIVE_DAMAGE = 5;
    public static final int WARN = 16;
    public static final int RISE_TICKS = 10;
    public static final double RISE = 2.6;
    public static final int DASH_TICKS = 6;
    public static final int STAGGER_TICKS = 40;
    public static final float STAGGER_TAKEN = 1.5f;
    public static final double LINE_LENGTH = 11;
    public static final double LINE_HALF = 0.7;
    public static final double DIVE_MIN = 3;
    public static final double DIVE_MAX = 10;
    public static final int COOLDOWN = 70;

    public static final int IDLE = 0;
    public static final int RISING = 1;
    public static final int AIMING = 2;
    public static final int DIVING = 3;
    public static final int STAGGER = 4;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(PeakFledgling.class, EntityDataSerializers.INT);

    private int phase;
    private Vec3 lineStart = Vec3.ZERO;
    private Vec3 lineEnd = Vec3.ZERO;
    private Vec3 diveFrom = Vec3.ZERO;
    private boolean landedHit;
    @Nullable
    private Telegraph pending;

    public PeakFledgling(EntityType<? extends Monster> type, Level level) {
        super(type, level, "peak_fledgling", "fly", "dive", "stagger", "hurt", "death");
        this.xpReward = 3;
        this.cooldown = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DIVE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, IDLE);
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int s) {
        entityData.set(STATE, s);
        phase = 0;
    }

    public Vec3 lineStart() {
        return lineStart;
    }

    public Vec3 lineEnd() {
        return lineEnd;
    }

    @Override
    public boolean busy() {
        return state() != IDLE;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new DiveGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        software.bernie.geckolib.animation.RawAnimation fly = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.peak_fledgling.fly");
        software.bernie.geckolib.animation.RawAnimation dive = software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.peak_fledgling.dive");
        software.bernie.geckolib.animation.RawAnimation stagger = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.peak_fledgling.stagger");
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "state", 2, s -> switch (state()) {
            case RISING, AIMING -> s.setAndContinue(fly);
            case DIVING -> s.setAndContinue(dive);
            case STAGGER -> s.setAndContinue(stagger);
            default -> software.bernie.geckolib.animation.PlayState.STOP;
        }));
    }

    public boolean canDive() {
        return state() == IDLE && cooldown == 0 && onGround();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!hasRestriction()) {
            restrictTo(blockPosition(), 8);   // 巣 (湧いた所) のまわり
        }
        if (cooldown > 0) {
            cooldown--;
        }
        phase++;
        LivingEntity t = getTarget();
        switch (state()) {
            case RISING -> {
                getNavigation().stop();
                setNoGravity(true);
                move(MoverType.SELF, new Vec3(0, RISE / RISE_TICKS, 0));
                setDeltaMovement(Vec3.ZERO);
                if (t != null) {
                    lockYawToward(t);
                    holdYaw();
                }
                if (phase >= RISE_TICKS) {
                    beginLine(t);
                }
            }
            case AIMING -> {
                setDeltaMovement(Vec3.ZERO);
                holdYaw();
                if (phase >= WARN) {
                    startDive();
                }
            }
            case DIVING -> {
                double k = Math.min(1.0, phase / (double) DASH_TICKS);
                Vec3 to = diveFrom.lerp(lineEnd, k);
                move(MoverType.SELF, to.subtract(position()));
                setDeltaMovement(Vec3.ZERO);
                holdYaw();
                if (level() instanceof ServerLevel server) {
                    warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4, getZ(), 2, 0.2, 0.2, 0.2, 0.02);
                }
                if (phase >= DASH_TICKS || horizontalCollision) {
                    land();
                }
            }
            case STAGGER -> {
                getNavigation().stop();
                if (phase >= STAGGER_TICKS) {
                    setState(IDLE);
                    cooldown = COOLDOWN + random.nextInt(30);
                }
            }
            default -> {
            }
        }
    }

    // 羽ばたいて上がる (サーバー)
    public void startRise(LivingEntity target) {
        lockYawToward(target);
        setState(RISING);
        getNavigation().stop();
        playSound(Ch3Mobs.FLEDGLING_CRY.get(), 1.0f, 1.0f + random.nextFloat() * 0.2f);
    }

    // 地面の線: 足もとの真下から狙いへ LINE_LENGTH (壁で止まる)。Telegraph の帯が WARN で満ち、満ちた瞬間に線の上の人に 5
    private void beginLine(@Nullable LivingEntity t) {
        double floor = Math.floor(getY() - RISE + 0.01);
        Vec3 from = new Vec3(getX(), floor, getZ());
        Vec3 dir = t != null ? t.position().subtract(from).multiply(1, 0, 1) : Vec3.directionFromRotation(0, getYRot());
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(0, getYRot());
        }
        dir = dir.normalize();
        Vec3 end = from.add(dir.scale(LINE_LENGTH));
        HitResult hit = level().clip(new ClipContext(from.add(0, 0.5, 0), end.add(0, 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 h = hit.getLocation();
            end = new Vec3(h.x, floor, h.z).subtract(dir.scale(0.5));
        }
        lineStart = from;
        lineEnd = end;
        lockedYaw = (float) (Mth.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90f;
        holdYaw();
        setState(AIMING);
        landedHit = false;
        double len = Math.max(1.0, end.subtract(from).length());
        double angle = Telegraph.angleOf(dir);
        pending = Telegraph.in((ServerLevel) level(), TelegraphShape.line(from.x, from.z, angle, len, LINE_HALF))
                .floor(floor).warn(WARN).danger().owner(this)
                .victims(this::canHit)
                .onHit((server, player) -> {
                    landedHit = true;
                    player.hurt(attackSource(), DIVE_DAMAGE);
                })
                .start();
    }

    private void startDive() {
        pending = null;
        diveFrom = position();
        setState(DIVING);
        playSound(Ch3Mobs.FLEDGLING_DIVE.get(), 1.1f, 1.0f);
    }

    private void land() {
        setNoGravity(false);
        setDeltaMovement(Vec3.ZERO);
        if (landedHit) {
            setState(IDLE);
            cooldown = COOLDOWN;
        } else {
            // 外した: 岩に当たってよろめく
            setState(STAGGER);
            level().playSound(null, getX(), getY(), getZ(), Ch3Mobs.FLEDGLING_THUD.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
            if (level() instanceof ServerLevel server) {
                warn(server, ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 8, 0.3, 0.05, 0.3, 0.02);
            }
        }
    }

    public boolean isStaggered() {
        return state() == STAGGER;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, isStaggered() ? amount * STAGGER_TAKEN : amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            setNoGravity(false);
            playAction("death");
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (state() == IDLE || state() == STAGGER) {
            super.knockback(strength, x, z);
        }
    }

    // 焦げ羽 40%
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Item feather = Ch3Mobs.item(Ch3Mobs.SCORCHED_FEATHER);
        if (recentlyHit && feather != null && random.nextFloat() < 0.4f) {
            spawnAtLocation(new ItemStack(feather));
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // 狙いが急降下の間合いにいて見えれば上がる。近すぎれば離れる (巣のまわりを歩く)
    static final class DiveGoal extends Goal {
        private final PeakFledgling mob;

        DiveGoal(PeakFledgling mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t instanceof ServerPlayer p && mob.canHit(p) && !p.isCreative();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null || mob.busy()) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double d = Math.sqrt(t.distanceToSqr(mob.getX(), t.getY(), mob.getZ()));
            if (mob.canDive() && d >= DIVE_MIN && d <= DIVE_MAX && Math.abs(t.getY() - mob.getY()) < 4 && mob.hasLineOfSight(t)) {
                mob.startRise(t);
            } else if (d < DIVE_MIN) {
                Vec3 away = awayPoint(mob, t.position(), 4);
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
                }
            } else if (d > DIVE_MAX && mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(t, 0.9);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch3Mobs.FLEDGLING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch3Mobs.FLEDGLING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch3Mobs.FLEDGLING_DEATH.get();
    }
}
