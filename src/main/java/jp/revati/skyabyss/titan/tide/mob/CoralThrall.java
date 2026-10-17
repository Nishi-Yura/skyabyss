package jp.revati.skyabyss.titan.tide.mob;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.EnumSet;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 珊瑚憑き (08 の 5.1節): 潮の民の骸に珊瑚が根を張ったもの。珊瑚の銛で 2.5 ブロック先まで突く。
// 潮が満ちると珊瑚の像になる (動かず、傷を受けない)。干潮で目覚め、いちばん近い人のほうへ向き直る。
// 突きは 0.3 秒の構え (後ろへ引く) を見せてから。構えを見て下がれば避けられる
public class CoralThrall extends TideMob {
    private static final EntityDataAccessor<Boolean> STATUE = SynchedEntityData.defineId(CoralThrall.class, EntityDataSerializers.BOOLEAN);

    public static final float HEALTH = 40;
    public static final float DAMAGE = 8;
    public static final float ARMOR = 8;
    // 突きが届く距離 (体の前の縁から)。銛の長さ 1.3 と突き出し 0.35 に、少しの余り
    public static final double REACH = 2.5;
    // 構えから突きまで (tick)、突きの後の間
    static final int WINDUP = 8;
    static final int COOLDOWN = 22;
    // 目覚めてから動き出すまで
    static final int WAKE_TICKS = 20;

    private int wakeLeft;

    public CoralThrall(EntityType<? extends Monster> type, Level level) {
        super(type, level, "coral_thrall", "attack", "wake");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATUE, false);
    }

    public boolean isStatue() {
        return entityData.get(STATUE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ThrustGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected String state() {
        return isStatue() ? "statue" : null;
    }

    @Override
    protected boolean frozen() {
        return isStatue();
    }

    // ---------------------------------------------------------------- 潮で像になる・目覚める

    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            boolean wet = MobTide.phase(level()).wet();
            if (wet && !isStatue()) {
                petrify();
            } else if (!wet && isStatue()) {
                wake();
            }
        }
        if (isStatue()) {
            // 像は動かない (押されても、水に流されても)
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            getNavigation().stop();
        }
        if (wakeLeft > 0) {
            wakeLeft--;
            getNavigation().stop();
        }
        super.aiStep();
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isStatue() || wakeLeft > 0;
    }

    void petrify() {
        entityData.set(STATUE, true);
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BRAIN_CORAL_BLOCK.defaultBlockState()),
                    getX(), getY() + 1.2, getZ(), 24, 0.35, 0.8, 0.35, 0.05);
            server.playSound(null, getX(), getY(), getZ(), TideMobSetup.THRALL_PETRIFY.get(), SoundSource.HOSTILE, 1f, 1f);
        }
    }

    void wake() {
        entityData.set(STATUE, false);
        wakeLeft = WAKE_TICKS;
        // いちばん近い人のほうへ向き直る (08 の 5.1節)
        Player near = level().getNearestPlayer(this, 24);
        if (near != null && !near.isSpectator() && !near.isCreative()) {
            float yaw = (float) (Mth.atan2(near.getZ() - getZ(), near.getX() - getX()) * (180 / Math.PI)) - 90f;
            setYRot(yaw);
            setYHeadRot(yaw);
            setYBodyRot(yaw);
            yRotO = yaw;
            yBodyRotO = yaw;
            setTarget(near);
        }
        playAction("wake");
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BRAIN_CORAL_BLOCK.defaultBlockState()),
                    getX(), getY() + 1.2, getZ(), 30, 0.4, 0.9, 0.4, 0.08);
            server.playSound(null, getX(), getY(), getZ(), TideMobSetup.THRALL_WAKE.get(), SoundSource.HOSTILE, 1.2f, 1f);
        }
    }

    // 像は傷を受けない (/kill と奈落は通す)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isStatue() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (!level().isClientSide && source.getEntity() instanceof Player) {
                level().playSound(null, getX(), getY(), getZ(), TideMobSetup.THRALL_STONE_TAP.get(), SoundSource.HOSTILE, 0.8f,
                        0.9f + random.nextFloat() * 0.2f);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return !isStatue() && super.isPushable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Statue", isStatue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STATUE, tag.getBoolean("Statue"));
    }

    // ---------------------------------------------------------------- 突き

    // 突きが当たるか: 体の前の縁から REACH 以内で、前方 (左右 50 度) にいる
    boolean inThrustReach(LivingEntity target) {
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        double dist = to.length() - getBbWidth() / 2 - target.getBbWidth() / 2;
        if (dist > REACH || Math.abs(target.getY() - getY()) > 2.2) {
            return false;
        }
        if (to.lengthSqr() < 1e-4) {
            return true;
        }
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        return look.dot(to.normalize()) > Math.cos(Math.toRadians(50));
    }

    private static final class ThrustGoal extends Goal {
        private final CoralThrall mob;
        private int windup = -1;
        private int cooldown;
        private int repath;

        ThrustGoal(CoralThrall mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.isStatue();
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
            if (windup >= 0) {
                // 構えの間は止まって、相手のほうを向き続ける (突きの瞬間の向きで当たりを決める)
                mob.getNavigation().stop();
                if (t != null) {
                    mob.getLookControl().setLookAt(t, 20f, 20f);
                    face(t, 12f);
                }
                if (--windup == 0) {
                    windup = -1;
                    cooldown = COOLDOWN;
                    if (t != null && t.isAlive() && mob.inThrustReach(t)) {
                        mob.doHurtTarget(t);
                    }
                    mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), TideMobSetup.THRALL_THRUST.get(), SoundSource.HOSTILE, 1f,
                            0.9f + mob.getRandom().nextFloat() * 0.2f);
                }
                return;
            }
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double d = mob.distanceTo(t) - mob.getBbWidth() / 2 - t.getBbWidth() / 2;
            if (d <= REACH - 0.4 && mob.hasLineOfSight(t)) {
                mob.getNavigation().stop();
                face(t, 20f);
                if (cooldown == 0 && mob.inThrustReach(t)) {
                    windup = WINDUP;
                    mob.playAction("attack");
                }
            } else if (--repath <= 0) {
                repath = 8;
                mob.getNavigation().moveTo(t, 1.05);
            }
        }

        private void face(LivingEntity t, float max) {
            float want = (float) (Mth.atan2(t.getZ() - mob.getZ(), t.getX() - mob.getX()) * (180 / Math.PI)) - 90f;
            float yaw = Mth.approachDegrees(mob.getYRot(), want, max);
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
        }
    }

    // ---------------------------------------------------------------- 音

    @Override
    protected SoundEvent getAmbientSound() {
        return isStatue() ? null : TideMobSetup.THRALL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TideMobSetup.THRALL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TideMobSetup.THRALL_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.CORAL_THRALL_STEP.get(), 0.3f);
    }
}
