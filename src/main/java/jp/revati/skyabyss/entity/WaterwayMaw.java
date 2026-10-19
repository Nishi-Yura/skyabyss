package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 水路の顎: 闇の水路を泳ぐ、骨の顎を持つ長い魚。水の中でだけ生き、泳ぐ者に噛みつく
public class WaterwayMaw extends Monster implements GeoEntity {
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.waterway_maw.swim");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.waterway_maw.bite");
    // 来てから襲うまでの猶予（5 秒）と、来た人を数える範囲（16 ブロック。外へ 24 ブロック離れると忘れる）
    // 来てから襲うまでの猶予は 10 秒 (岩棚を渡る間は襲われない)
    public static final int ARRIVAL_GRACE = 200;
    // 噛みつきの強さ (ハート 1 つぶん) と、噛む前の予兆・噛んだあとの間
    public static final float BITE_DAMAGE = 2.0f;
    public static final int WINDUP_TICKS = 16;
    private static final int RETREAT_TICKS = 40;
    private static final int BITE_COOLDOWN = 80;
    // 水から上がった (岩棚・はしご) 相手は、これだけ見えなかったら諦める。諦めたあとは CALM_TICKS の間は誰も狙わない
    public static final int GIVE_UP_TICKS = 20;
    private static final int CALM_TICKS = 120;
    // 近くに同時にいる数の上限
    public static final int MAX_NEAR = 2;
    private static final double NEAR_RADIUS = 24;
    private static final double ARRIVAL_RANGE_SQR = 16 * 16;
    private static final double FORGET_RANGE_SQR = 24 * 24;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // 近くに来た人 → 来た時刻 (tickCount)
    private final Map<UUID, Integer> arrivals = new HashMap<>();
    private int calmUntil;
    private int biteReadyAt;
    private boolean windingUp;

    public WaterwayMaw(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1f, 0.5f, false);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        setPathfindingMalus(PathType.WATER, 0f);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 18)
                .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.77)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new BiteGoal());
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> p.isInWater() && tickCount >= calmUntil && graced(p.getUUID())));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    // 水の中に湧けるように (既定では、液体のある場所には湧かない)
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else {
            super.travel(input);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 10 == 0) {
            updateArrivals();
        }
        // 水の中では息が続き、水から出ると弱っていく
        if (isInWaterOrBubble()) {
            setAirSupply(getMaxAirSupply());
        } else if (!level().isClientSide && tickCount % 20 == 0) {
            hurt(damageSources().dryOut(), 2f);
        }
    }

    // 近くに来た人を覚える。来てから ARRIVAL_GRACE の間は、この人を新しく狙わない
    private void updateArrivals() {
        for (Player p : level().players()) {
            if (p.distanceToSqr(this) <= ARRIVAL_RANGE_SQR) {
                arrivals.putIfAbsent(p.getUUID(), tickCount);
            }
        }
        // 近くに 3 匹目がいたら、あとから来た方は消える (囲まれないように)
        if (getTarget() == null) {
            int rank = 0;
            for (WaterwayMaw other : level().getEntitiesOfClass(WaterwayMaw.class, getBoundingBox().inflate(NEAR_RADIUS), m -> m != this && m.isAlive())) {
                if (other.getId() < getId()) {
                    rank++;
                }
            }
            if (rank >= MAX_NEAR) {
                discard();
                return;
            }
        }
        arrivals.keySet().removeIf(id -> {
            Player p = level().getPlayerByUUID(id);
            return p == null || p.distanceToSqr(this) > FORGET_RANGE_SQR;
        });
    }

    private boolean graced(UUID id) {
        Integer since = arrivals.get(id);
        return since != null && tickCount - since >= ARRIVAL_GRACE;
    }

    // 噛みつき: 小さな傷と、はっきりした弾き飛ばし。続けては噛まない (BiteGoal の間)
    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "bite");
        boolean hit = target.hurt(damageSources().mobAttack(this), BITE_DAMAGE);
        if (hit && target instanceof LivingEntity living) {
            living.knockback(1.1, getX() - living.getX(), getZ() - living.getZ());
            living.hurtMarked = true;
        }
        return hit;
    }

    // 噛む前の合図: 頭のまわりに泡が集まり、低く鳴る
    private void biteTell() {
        if (level() instanceof ServerLevel sl) {
            Vec3 look = getLookAngle();
            sl.sendParticles(ParticleTypes.BUBBLE, getX() + look.x, getY() + 0.4, getZ() + look.z, 8, 0.35, 0.25, 0.35, 0.02);
            sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.3, getZ(), 4, 0.2, 0.1, 0.2, 0.05);
        }
    }

    private class BiteGoal extends Goal {
        private int state; // 0 追う, 1 溜め, 2 退く
        private int timer;
        private int dryTicks;

        BiteGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && t.isInWater();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && dryTicks < GIVE_UP_TICKS;
        }

        @Override
        public void start() {
            state = 0;
            timer = 0;
            dryTicks = 0;
        }

        @Override
        public void stop() {
            windingUp = false;
            getNavigation().stop();
            LivingEntity t = getTarget();
            if (dryTicks >= GIVE_UP_TICKS || t == null || !t.isAlive()) {
                setTarget(null);
                calmUntil = tickCount + CALM_TICKS;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            dryTicks = t.isInWater() ? 0 : dryTicks + 1;
            getLookControl().setLookAt(t, 30f, 30f);
            double dist = distanceTo(t);
            if (state == 2) {
                // 噛んだあとは離れて間を取る
                if (--timer <= 0) {
                    state = 0;
                } else {
                    Vec3 away = position().subtract(t.position()).normalize().scale(6);
                    getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.0);
                }
                return;
            }
            if (state == 1) {
                // 溜め: 止まって泡を出す。この間に離れれば外れる
                getNavigation().stop();
                windingUp = true;
                if (timer % 4 == 0) {
                    biteTell();
                }
                if (--timer <= 0) {
                    windingUp = false;
                    if (dist <= 3.0) {
                        doHurtTarget(t);
                    }
                    state = 2;
                    timer = RETREAT_TICKS;
                    biteReadyAt = tickCount + BITE_COOLDOWN;
                }
                return;
            }
            getNavigation().moveTo(t, 1.2);
            if (dist <= 2.4 && tickCount >= biteReadyAt) {
                state = 1;
                timer = WINDUP_TICKS;
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 0.6f, 1.7f);
                biteTell();
            }
        }
    }

    // 自己テスト用: 噛みつきの合図の最中か
    public boolean isWindingUp() {
        return windingUp;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.WATERWAY_MAW_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.WATERWAY_MAW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.WATERWAY_MAW_DEATH.get();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> state.setAndContinue(SWIM)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP).triggerableAnim("bite", BITE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
