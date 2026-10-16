package jp.revati.skyabyss.titan.storm.mob;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.RawAnimation;

// 羽守 (12 の 5.1節): 外套を広げたまま空洞になった風の民の番人。銅の兜と鎧羽 (ラフ B)、鉤槍は別モデル (socket_main)。
// 凪: 持ち場のまわりを歩いて番をし、近づいた人を鉤槍で薙ぐ (届く 3 ブロック、前方 60 度)。
// 嵐: 1.0 秒かがんで外套を広げ (予告)、上へ跳ぶ。跳ぶと同時に地面に長さ 14 の光る帯が出て、0.8 秒後にその上を一直線に突っ込む (12)。
//     着地のあと 1.5 秒動けず、その間に受ける傷は 1.5 倍。突撃の向きは帯が出たときに決まる (帯の外へ出れば当たらない)
public class FeatherWarden extends StormMob {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(FeatherWarden.class, EntityDataSerializers.INT);

    public static final float HEALTH = 52;
    public static final float DAMAGE = 10;
    public static final float ARMOR = 9;
    public static final float DIVE_DAMAGE = 12;
    public static final float STUN_MULTIPLIER = 1.5f;
    // 鉤槍の届く距離 (体の前の縁から) と、前方の角度 (左右 30 度ずつで 60 度)
    public static final double REACH = 3.0;
    public static final double HALF_ANGLE = 30;
    static final int SWING_WINDUP = 10;
    static final int SWING_COOLDOWN = 24;
    // 突撃: かがみ 1.0 秒 → 跳ぶ 0.4 秒 → 宙で狙う 0.4 秒 (帯は跳んだときから。突っ込むまで 0.8 秒) → 突っ込む → 動けない 1.5 秒
    public static final int CROUCH_TICKS = 20;
    public static final int RISE_TICKS = 8;
    public static final int AIM_TICKS = 8;
    public static final int LINE_LEAD = RISE_TICKS + AIM_TICKS;
    public static final int STUN_TICKS = 30;
    public static final double LINE_LENGTH = 14;
    static final double DIVE_SPEED = 1.25;
    static final int DIVE_MAX_TICKS = 18;
    static final int DIVE_COOLDOWN = 90;
    static final double DIVE_MIN = 5;
    static final double DIVE_MAX = 13;
    static final double RISE_SPEED = 0.38;

    private static final DustParticleOptions LINE = new DustParticleOptions(new Vector3f(0.68f, 0.84f, 1.0f), 1.1f);
    private static final DustParticleOptions LINE_EDGE = new DustParticleOptions(new Vector3f(0.95f, 0.97f, 1.0f), 0.8f);

    public enum State { IDLE, CROUCH, RISE, AIM, DIVE, STUNNED }

    private int stateTicks;
    private int diveCooldown = 40;
    private Vec3 lineStart = Vec3.ZERO;
    private Vec3 lineEnd = Vec3.ZERO;
    private double lineY;
    private final Set<UUID> diveHit = new HashSet<>();
    // 自己テスト用: 帯が出た時と突っ込み始めた時 (この個体の突撃の時計)
    long clock;
    long lineShownAt = -1;
    long diveStartedAt = -1;

    public FeatherWarden(EntityType<? extends Monster> type, Level level) {
        super(type, level, "feather_warden", "attack");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, 0);
    }

    public State state3() {
        int i = entityData.get(STATE);
        return State.values()[Mth.clamp(i, 0, State.values().length - 1)];
    }

    private void setState(State s, int ticks) {
        entityData.set(STATE, s.ordinal());
        stateTicks = ticks;
    }

    public Vec3 lineStart() {
        return lineStart;
    }

    public Vec3 lineEnd() {
        return lineEnd;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FightGoal(this));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected String state() {
        return switch (state3()) {
            case CROUCH -> "crouch";
            case RISE, AIM, DIVE -> "dive";
            case STUNNED -> "stunned";
            default -> null;
        };
    }

    @Override
    protected RawAnimation stateAnimation(String name) {
        return name.equals("crouch") ? hold(name) : super.stateAnimation(name);
    }

    @Override
    protected boolean frozen() {
        return state3() != State.IDLE;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || state3() != State.IDLE;
    }

    // ---------------------------------------------------------------- 突撃

    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            if (!hasRestriction()) {
                restrictTo(blockPosition(), 10);   // 持ち場 (湧いた所) のまわりで番をする
            }
            if (diveCooldown > 0) {
                diveCooldown--;
            }
            tickDive();
        }
        super.aiStep();
    }

    public boolean canDive() {
        return state3() == State.IDLE && diveCooldown == 0 && onGround();
    }

    // かがみを始める (サーバー)。向きはかがんでいる間も相手を追い、帯が出るときに決まる
    public void startCrouch() {
        setState(State.CROUCH, CROUCH_TICKS);
        getNavigation().stop();
        level().playSound(null, getX(), getY(), getZ(), StormMobSetup.WARDEN_CROUCH.get(), SoundSource.HOSTILE, 1.2f, 1f);
        if (level() instanceof ServerLevel server) {
            warn(server, ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 10, 0.5, 0.05, 0.5, 0.02);
        }
    }

    void tickDive() {
        State s = state3();
        if (s == State.IDLE) {
            return;
        }
        clock++;
        LivingEntity t = getTarget();
        switch (s) {
            case CROUCH -> {
                getNavigation().stop();
                if (t != null) {
                    face(t, 20f);
                }
                if (--stateTicks <= 0) {
                    beginLine(t);
                }
            }
            case RISE -> {
                move(MoverType.SELF, new Vec3(0, RISE_SPEED * (0.5 + stateTicks / (double) RISE_TICKS * 0.7), 0));
                setDeltaMovement(Vec3.ZERO);
                showLine();
                if (--stateTicks <= 0) {
                    setState(State.AIM, AIM_TICKS);
                }
            }
            case AIM -> {
                setDeltaMovement(Vec3.ZERO);
                showLine();
                if (--stateTicks <= 0) {
                    diveStartedAt = clock;
                    diveHit.clear();
                    setState(State.DIVE, DIVE_MAX_TICKS);
                    level().playSound(null, getX(), getY(), getZ(), StormMobSetup.WARDEN_DIVE.get(), SoundSource.HOSTILE, 1.4f, 1f);
                }
            }
            case DIVE -> tickDash();
            case STUNNED -> {
                getNavigation().stop();
                setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                if (--stateTicks <= 0) {
                    setState(State.IDLE, 0);
                    diveCooldown = DIVE_COOLDOWN + random.nextInt(40);
                }
            }
            default -> {
            }
        }
    }

    // 地面に帯を引く: 今の足もとから相手の向きへ 14 ブロック (壁で止まる)
    private void beginLine(LivingEntity t) {
        Vec3 dir = t != null ? t.position().subtract(position()).multiply(1, 0, 1) : Vec3.directionFromRotation(0, getYRot());
        if (dir.lengthSqr() < 1e-4) {
            dir = Vec3.directionFromRotation(0, getYRot());
        }
        dir = dir.normalize();
        lineY = getY();
        lineStart = new Vec3(getX(), lineY, getZ());
        Vec3 end = lineStart.add(dir.scale(LINE_LENGTH));
        HitResult hit = level().clip(new ClipContext(lineStart.add(0, 0.6, 0), end.add(0, 0.6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 h = hit.getLocation();
            end = new Vec3(h.x, lineY, h.z).subtract(dir.scale(0.6));
        }
        lineEnd = end;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90f;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setNoGravity(true);
        lineShownAt = clock;
        setState(State.RISE, RISE_TICKS);
        showLine();
    }

    private void showLine() {
        if (!(level() instanceof ServerLevel server) || clock % 2 != 0 && clock != lineShownAt) {
            return;
        }
        Vec3 d = lineEnd.subtract(lineStart);
        double len = d.length();
        if (len < 1e-3) {
            return;
        }
        Vec3 dir = d.scale(1 / len);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (double s = 0; s <= len; s += 0.5) {
            Vec3 p = lineStart.add(dir.scale(s));
            warn(server, LINE, p.x, lineY + 0.08, p.z, 1, 0, 0, 0, 0);
            warn(server, LINE_EDGE, p.x + side.x * 0.6, lineY + 0.08, p.z + side.z * 0.6, 1, 0, 0, 0, 0);
            warn(server, LINE_EDGE, p.x - side.x * 0.6, lineY + 0.08, p.z - side.z * 0.6, 1, 0, 0, 0, 0);
        }
        warn(server, ParticleTypes.ELECTRIC_SPARK, lineEnd.x, lineY + 0.3, lineEnd.z, 4, 0.2, 0.2, 0.2, 0.05);
    }

    // 宙から帯の先へ一直線に突っ込む (壁に当たればそこで落ちる。すり抜けない)
    private void tickDash() {
        Vec3 to = new Vec3(lineEnd.x, lineY, lineEnd.z).subtract(position());
        double dist = to.length();
        Vec3 step = dist <= DIVE_SPEED ? to : to.scale(DIVE_SPEED / dist);
        Vec3 before = position();
        move(MoverType.SELF, step);
        setDeltaMovement(Vec3.ZERO);
        Vec3 dir = new Vec3(step.x, 0, step.z);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(before.subtract(position())).inflate(0.45),
                e -> e instanceof Player && fair(e) && !diveHit.contains(e.getUUID()))) {
            diveHit.add(e.getUUID());
            if (e.hurt(attackSource(), DIVE_DAMAGE) && dir.lengthSqr() > 1e-4) {
                Vec3 k = dir.normalize();
                e.knockback(0.6, -k.x, -k.z);
            }
        }
        if (level() instanceof ServerLevel server) {
            warn(server, ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 3, 0.3, 0.5, 0.3, 0.02);
        }
        boolean blocked = horizontalCollision || position().distanceToSqr(before) < 0.01 && dist > 0.2;
        if (dist <= DIVE_SPEED + 1e-3 || blocked || --stateTicks <= 0 || (verticalCollision && onGround() && dist < 3)) {
            land();
        }
    }

    private void land() {
        setNoGravity(false);
        setDeltaMovement(Vec3.ZERO);
        setState(State.STUNNED, STUN_TICKS);
        level().playSound(null, getX(), getY(), getZ(), StormMobSetup.WARDEN_LAND.get(), SoundSource.HOSTILE, 1.3f, 1f);
        if (level() instanceof ServerLevel server) {
            warn(server, ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 18, 0.6, 0.05, 0.6, 0.05);
        }
    }

    public boolean isStunned() {
        return state3() == State.STUNNED;
    }

    // 着地のあと動けない間は、受ける傷 1.5 倍
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, isStunned() ? amount * STUN_MULTIPLIER : amount);
    }

    // 突撃の途中は押されても動きを変えない
    @Override
    public void knockback(double strength, double x, double z) {
        State s = state3();
        if (s == State.IDLE || s == State.STUNNED) {
            super.knockback(strength, x, z);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // 鉤槍が当たるか: 体の前の縁から REACH 以内で、前方 (左右 30 度)
    boolean inSwingReach(LivingEntity target) {
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        double dist = to.length() - getBbWidth() / 2 - target.getBbWidth() / 2;
        return dist <= REACH && Math.abs(target.getY() - getY()) < 2.5 && inFront(target, HALF_ANGLE);
    }

    private static final class FightGoal extends Goal {
        private final FeatherWarden mob;
        private int windup = -1;
        private int cooldown;
        private int repath;

        FightGoal(FeatherWarden mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return fair(mob.getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || windup >= 0;
        }

        @Override
        public void stop() {
            windup = -1;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (cooldown > 0) {
                cooldown--;
            }
            if (mob.state3() != State.IDLE) {
                windup = -1;
                return;
            }
            if (windup >= 0) {
                mob.getNavigation().stop();
                if (t != null) {
                    mob.face(t, 14f);
                }
                if (--windup == 0) {
                    windup = -1;
                    cooldown = SWING_COOLDOWN;
                    if (t != null && fair(t) && mob.inSwingReach(t)) {
                        mob.doHurtTarget(t);
                    }
                }
                return;
            }
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double flat = Math.sqrt(t.distanceToSqr(mob.getX(), t.getY(), mob.getZ()));
            double d = flat - mob.getBbWidth() / 2 - t.getBbWidth() / 2;
            if (MobStorm.phase(mob.level()).stormy() && mob.canDive() && flat >= DIVE_MIN && flat <= DIVE_MAX
                    && Math.abs(t.getY() - mob.getY()) < 3 && mob.hasLineOfSight(t)) {
                mob.startCrouch();
                return;
            }
            if (d <= REACH - 0.6 && mob.hasLineOfSight(t)) {
                mob.getNavigation().stop();
                mob.face(t, 20f);
                if (cooldown == 0 && mob.inSwingReach(t)) {
                    windup = SWING_WINDUP;
                    mob.playAction("attack");
                    mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), StormMobSetup.WARDEN_SWING.get(), SoundSource.HOSTILE, 1f,
                            0.9f + mob.getRandom().nextFloat() * 0.2f);
                }
            } else if (--repath <= 0) {
                repath = 8;
                mob.getNavigation().moveTo(t, 1.05);
            }
        }
    }

    // ---------------------------------------------------------------- 音

    @Override
    protected SoundEvent getAmbientSound() {
        return StormMobSetup.WARDEN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return StormMobSetup.WARDEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return StormMobSetup.WARDEN_DEATH.get();
    }
}
