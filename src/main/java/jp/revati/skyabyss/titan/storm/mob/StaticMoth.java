package jp.revati.skyabyss.titan.storm.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.storm.back.StormRods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
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

// 雷蛾 (12 の 5.1節): 透けた翅の雷光蛾 (ラフ B)。宙を舞い、4 秒かけて帯電し (翅の模様が明るくなる)、満ちると放電する。
// 放電は 4 ブロック以内の人へ 5。そこから 4 ブロック以内の別の人へ跳ぶ (最大 3 人、跳ぶごとに −20%)。離れて立てば避けられる。
// 帯電の間は 5〜6 ブロック離れて回り、満ちてから寄ってくる。満ちた雷蛾を倒すと半径 2.5 で弾ける (4)。
// 嵐の拍では帯電が速い (2.5 秒)。相手がいないときは、近くの避雷の柱に寄り、触れると柱の雷を 1 回分吸う (自分は半分帯電する)
public class StaticMoth extends StormMob {
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(StaticMoth.class, EntityDataSerializers.FLOAT);

    public static final float HEALTH = 14;
    public static final float ARMOR = 2;
    public static final float DISCHARGE_DAMAGE = 5;
    public static final double RANGE = 4.0;
    public static final double CHAIN_RANGE = 4.0;
    public static final int MAX_CHAIN = 3;
    public static final float FALLOFF = 0.8f;
    public static final double BURST_RADIUS = 2.5;
    public static final float BURST_DAMAGE = 4;
    public static final int CHARGE_TICKS_CALM = 80;
    public static final int CHARGE_TICKS_STORM = 50;
    static final double ORBIT = 5.5;
    static final int AFTER_DISCHARGE = 20;
    static final int DRAIN_COOLDOWN = 200;

    // 静電を帯びない人 (雲間の干し果を食べた人など。装備の担当が差し替える): 放電は受けても次へ跳ばず、跳んだ先にも選ばれない
    public static Predicate<LivingEntity> STATIC_PROOF = e -> false;

    private int rest;
    private int drainCooldown;
    private int rodSearch;
    @Nullable
    private BlockPos rod;
    private double orbitAngle;
    private boolean humming;

    public StaticMoth(EntityType<? extends Monster> type, Level level) {
        super(type, level, "static_moth", "attack");
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 4;
        setNoGravity(true);
        orbitAngle = random.nextDouble() * Math.PI * 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DISCHARGE_DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.5)
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
        builder.define(CHARGE, 0f);
    }

    // 帯電の割合 (0〜1)。描画は光の層をこの割合で足す
    public float getCharge() {
        return entityData.get(CHARGE);
    }

    void setCharge(float c) {
        entityData.set(CHARGE, Math.max(0f, Math.min(1f, c)));
    }

    public boolean isFull() {
        return getCharge() >= 0.999f;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new HoverGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.7));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------------------------------------------------------------- 帯電と放電

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (getCharge() > 0.5f && random.nextFloat() < getCharge() * 0.4f) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0, 0);
            }
            return;
        }
        if (drainCooldown > 0) {
            drainCooldown--;
        }
        LivingEntity t = getTarget();
        boolean hunting = fair(t) && distanceTo(t) < 16;
        if (rest > 0) {
            rest--;
        } else if (hunting && !isFull()) {
            int full = MobStorm.phase(level()).stormy() ? CHARGE_TICKS_STORM : CHARGE_TICKS_CALM;
            if (!humming) {
                humming = true;
                level().playSound(null, getX(), getY(), getZ(), StormMobSetup.MOTH_CHARGE.get(), SoundSource.HOSTILE, 0.9f,
                        full == CHARGE_TICKS_STORM ? 1.3f : 1f);
            }
            setCharge(getCharge() + 1f / full);
        } else if (!hunting && !isFull()) {
            setCharge(getCharge() - 0.004f);
        }
        if (isFull() && rest == 0) {
            List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(CHAIN_RANGE * 3),
                    e -> e instanceof Player && fair(e));
            List<Hit> plan = plan(position().add(0, getBbHeight() / 2, 0), near, e -> hasLineOfSight(e));
            if (!plan.isEmpty()) {
                discharge(plan);
            }
        }
        if (!hunting) {
            seekRod();
        }
    }

    public record Hit(LivingEntity target, float damage) {
    }

    // 放電の跳び方: origin から RANGE 以内でいちばん近い人へ、そこから CHAIN_RANGE 以内でまだ当たっていない、いちばん近い人へ。
    // 最大 MAX_CHAIN 人、跳ぶごとに FALLOFF 倍。静電を帯びない人は、最初に当たってもそこで止まり、跳んだ先には選ばれない
    public static List<Hit> plan(Vec3 origin, List<? extends LivingEntity> candidates, Predicate<LivingEntity> canSee) {
        List<Hit> hits = new ArrayList<>();
        List<LivingEntity> left = new ArrayList<>(candidates);
        Vec3 from = origin;
        float damage = DISCHARGE_DAMAGE;
        double range = RANGE;
        while (hits.size() < MAX_CHAIN) {
            LivingEntity best = null;
            double bestD = range * range;
            for (LivingEntity e : left) {
                Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
                double d = c.distanceToSqr(from);
                if (d <= bestD && (hits.isEmpty() ? canSee.test(e) : !STATIC_PROOF.test(e))) {
                    bestD = d;
                    best = e;
                }
            }
            if (best == null) {
                break;
            }
            hits.add(new Hit(best, damage));
            left.remove(best);
            if (STATIC_PROOF.test(best)) {
                break;
            }
            from = best.position().add(0, best.getBbHeight() / 2, 0);
            damage *= FALLOFF;
            range = CHAIN_RANGE;
        }
        return hits;
    }

    private void discharge(List<Hit> plan) {
        playAction("attack");
        level().playSound(null, getX(), getY(), getZ(), StormMobSetup.MOTH_DISCHARGE.get(), SoundSource.HOSTILE, 1.2f, 1f);
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        for (Hit h : plan) {
            Vec3 to = h.target().position().add(0, h.target().getBbHeight() / 2, 0);
            arc(from, to);
            h.target().hurt(damageSources().mobAttack(this), h.damage());
            from = to;
        }
        setCharge(0);
        humming = false;
        rest = AFTER_DISCHARGE;
    }

    private void arc(Vec3 a, Vec3 b) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 d = b.subtract(a);
        int n = Math.max(4, (int) (d.length() * 4));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double j = (i % 2 == 0 ? 1 : -1) * 0.15 * Math.sin(t * Math.PI);
            Vec3 p = a.add(d.scale(t)).add(j, j * 0.5, -j);
            warn(server, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        warn(server, ParticleTypes.FLASH, b.x, b.y, b.z, 1, 0, 0, 0, 0);
    }

    // 相手のいないとき: 近くの避雷の柱に寄り、触れると柱の雷を 1 回分吸う
    private void seekRod() {
        if (!(level() instanceof ServerLevel server) || drainCooldown > 0) {
            return;
        }
        if (--rodSearch <= 0) {
            rodSearch = 40;
            rod = StormRods.find(server, position(), 12);
        }
        if (rod == null) {
            return;
        }
        Vec3 top = Vec3.atBottomCenterOf(rod).add(0, 1.2, 0);
        if (position().distanceToSqr(top) < 1.6 * 1.6) {
            StormRods.charge(server, rod, -1);
            setCharge(getCharge() + 0.5f);
            drainCooldown = DRAIN_COOLDOWN;
            level().playSound(null, getX(), getY(), getZ(), StormMobSetup.MOTH_CHARGE.get(), SoundSource.HOSTILE, 0.8f, 1.5f);
            rod = null;
        } else if (getNavigation().isDone()) {
            getNavigation().moveTo(top.x, top.y, top.z, 1.0);
        }
    }

    // 満ちた雷蛾を倒すと弾ける
    @Override
    public void die(DamageSource source) {
        boolean burst = getCharge() >= 0.95f;
        super.die(source);
        if (burst && level() instanceof ServerLevel server) {
            Vec3 c = position().add(0, getBbHeight() / 2, 0);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BURST_RADIUS),
                    e -> e instanceof Player && fair(e) && e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(c) <= BURST_RADIUS + 0.5)) {
                e.hurt(damageSources().mobAttack(this), BURST_DAMAGE);
            }
            warn(server, ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 1.0, 1.0, 1.0, 0.3);
            warn(server, ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level().playSound(null, getX(), getY(), getZ(), StormMobSetup.MOTH_BURST.get(), SoundSource.HOSTILE, 1.3f, 1f);
            setCharge(0);
        }
    }

    // 帯電の間は ORBIT 離れて回り、満ちたら寄る (人の胸の高さへ)
    private static final class HoverGoal extends Goal {
        private final StaticMoth mob;
        private int repath;

        HoverGoal(StaticMoth mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return fair(mob.getTarget());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            mob.face(t, 12f);
            if (--repath > 0) {
                return;
            }
            repath = 6;
            Vec3 goal;
            if (mob.isFull()) {
                Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : away.normalize();
                goal = t.position().add(away.scale(2.0)).add(0, 1.2, 0);
            } else {
                mob.orbitAngle += 0.35;
                goal = t.position().add(Math.cos(mob.orbitAngle) * ORBIT, 1.6 + Math.sin(mob.orbitAngle * 1.7) * 0.6, Math.sin(mob.orbitAngle) * ORBIT);
            }
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, mob.isFull() ? 1.3 : 1.0);
        }
    }

    // ---------------------------------------------------------------- 浮く者の決まり、音

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return StormMobSetup.MOTH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return StormMobSetup.MOTH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return StormMobSetup.MOTH_DEATH.get();
    }
}
