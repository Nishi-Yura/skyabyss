package jp.revati.skyabyss.titan.tide.mob;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

// 泡吹き (08 の 5.1節): 宙に浮く棘のある魚。離れた所から泡を吐く (当たると真上へ浮く。BubbleShot)。
// 近づかれるとふくらみ、棘に触れた人に 3 の傷。ふくらんでいる間は泡を吐かない。
// ふくらみは別のモデルを持たず、体の骨の拡大と棘の骨の出し入れのアニメーションで表す (08 の 14.3節)
public class BubblePuffer extends TideMob {
    private static final EntityDataAccessor<Boolean> INFLATED = SynchedEntityData.defineId(BubblePuffer.class, EntityDataSerializers.BOOLEAN);

    public static final float HEALTH = 22;
    public static final float ARMOR = 2;
    public static final float SPINE_DAMAGE = 3;
    // ふくらむ距離、しぼむ距離 (中心から)、棘が届く距離
    static final double INFLATE_RANGE = 2.8;
    static final double DEFLATE_RANGE = 4.5;
    static final double SPINE_REACH = 1.25;
    static final int DEFLATE_DELAY = 40;
    static final int SPINE_COOLDOWN = 20;
    // 泡を吐く間 (tick)、構えてから吐くまで、届く距離
    static final int SHOT_INTERVAL = 50;
    static final int SHOT_WINDUP = 9;
    static final double SHOT_RANGE = 14;

    private int calm;
    private final Map<UUID, Integer> spineHit = new HashMap<>();

    public BubblePuffer(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bubble_puffer", "attack", "deflate");
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.xpReward = 6;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SPINE_DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(INFLATED, false);
    }

    public boolean isInflated() {
        return entityData.get(INFLATED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new ShootGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected String state() {
        return isInflated() ? "inflated" : null;
    }

    @Override
    protected RawAnimation stateAnimation(String name) {
        return sequence("inflate", "inflated");
    }

    // ---------------------------------------------------------------- ふくらみと棘

    @Override
    public void aiStep() {
        super.aiStep();
        // 水の中でも息ができる (魚)
        setAirSupply(getMaxAirSupply());
        if (level().isClientSide) {
            return;
        }
        Player near = null;
        double best = Double.MAX_VALUE;
        for (Player p : level().players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
                continue;
            }
            double d = p.distanceToSqr(getX(), getY() + getBbHeight() / 2, getZ());
            if (d < best) {
                best = d;
                near = p;
            }
        }
        double dist = Math.sqrt(best);
        if (!isInflated()) {
            if (near != null && dist < INFLATE_RANGE) {
                setInflated(true);
            }
        } else {
            calm = dist > DEFLATE_RANGE ? calm + 1 : 0;
            if (calm >= DEFLATE_DELAY) {
                setInflated(false);
            } else {
                prick();
            }
        }
        spineHit.replaceAll((k, v) -> v - 1);
        spineHit.values().removeIf(v -> v <= 0);
    }

    void setInflated(boolean on) {
        entityData.set(INFLATED, on);
        calm = 0;
        level().playSound(null, getX(), getY(), getZ(), on ? TideMobSetup.PUFFER_INFLATE.get() : TideMobSetup.PUFFER_DEFLATE.get(),
                SoundSource.HOSTILE, 1f, 1f);
        if (!on) {
            playAction("deflate");
        }
    }

    // 棘に触れた人へ 3 の傷 (一人ごとに 1 秒に一度)
    private void prick() {
        Vec3 c = position().add(0, getBbHeight() / 2, 0);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SPINE_REACH),
                e -> e instanceof Player p && !p.isSpectator() && !p.isCreative() && e.isAlive())) {
            if (spineHit.containsKey(e.getUUID())) {
                continue;
            }
            Vec3 closest = new Vec3(Mth.clamp(c.x, e.getBoundingBox().minX, e.getBoundingBox().maxX),
                    Mth.clamp(c.y, e.getBoundingBox().minY, e.getBoundingBox().maxY),
                    Mth.clamp(c.z, e.getBoundingBox().minZ, e.getBoundingBox().maxZ));
            if (closest.distanceTo(c) <= SPINE_REACH) {
                if (e.hurt(damageSources().thorns(this), SPINE_DAMAGE)) {
                    spineHit.put(e.getUUID(), SPINE_COOLDOWN);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 泡を吐く

    private static final class ShootGoal extends Goal {
        private final BubblePuffer mob;
        private int cooldown = 20;
        private int windup = -1;
        private int repath;

        ShootGoal(BubblePuffer mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            windup = -1;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            face(t);
            double d = mob.distanceTo(t);
            // 離れた所 (5〜9 ブロック) で、相手より 2〜3 ブロック上に浮く
            if (--repath <= 0) {
                repath = 15;
                Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1e-3) {
                    away = new Vec3(1, 0, 0);
                }
                double want = d < 5 ? 7 : d > 9 ? 7 : d;
                Vec3 goal = t.position().add(away.normalize().scale(want)).add(0, 2.5, 0);
                if (mob.isInflated()) {
                    goal = mob.position().add(away.normalize().scale(1.5)).add(0, 0.5, 0); // ふくらんだら少し離れる
                }
                mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
            }
            if (cooldown > 0) {
                cooldown--;
            }
            if (windup >= 0) {
                if (--windup == 0) {
                    windup = -1;
                    if (!mob.isInflated()) {
                        Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.45, 0);
                        Vec3 to = t.position().add(0, t.getBbHeight() * 0.55, 0);
                        mob.level().addFreshEntity(new BubbleShot(mob.level(), mob, to.subtract(from)));
                        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), TideMobSetup.PUFFER_SHOOT.get(), SoundSource.HOSTILE, 1f,
                                0.9f + mob.getRandom().nextFloat() * 0.2f);
                    }
                }
                return;
            }
            if (cooldown == 0 && !mob.isInflated() && d < SHOT_RANGE && mob.hasLineOfSight(t)) {
                cooldown = SHOT_INTERVAL + mob.getRandom().nextInt(20);
                windup = SHOT_WINDUP;
                mob.playAction("attack");
            }
        }

        private void face(LivingEntity t) {
            float want = (float) (Mth.atan2(t.getZ() - mob.getZ(), t.getX() - mob.getX()) * (180 / Math.PI)) - 90f;
            float yaw = Mth.approachDegrees(mob.getYRot(), want, 15f);
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
        }
    }

    // ---------------------------------------------------------------- 浮く者の決まり

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }


    @Override
    protected SoundEvent getAmbientSound() {
        return TideMobSetup.PUFFER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TideMobSetup.PUFFER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TideMobSetup.PUFFER_DEATH.get();
    }
}
