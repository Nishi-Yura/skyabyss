package jp.revati.skyabyss.entity;

import java.util.EnumSet;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 棘うさぎ (docs/design/21 の 3.1): 射手の予習。夜の草原に出る。距離をとり、背の骨の棘を立てて (予告 0.8 秒。立った棘と床の帯が予告)、
// 直線に 1 本飛ばす。当たると鈍足 I 1.5 秒。撃ったあとは後ろ脚で跳んで離れる。遮蔽 (壁・石柱) の裏には通らない
public class SpineHare extends GroundFoe {
    public static final int WARN = 16;
    public static final double LINE_LENGTH = 14;
    public static final double LINE_HALF_WIDTH = 0.55;
    public static final float DAMAGE = 3f;
    public static final int SLOW_TICKS = 30;
    public static final int END = WARN + 12;
    public static final double MIN_RANGE = 4.5;
    public static final double MAX_RANGE = 13;

    public SpineHare(EntityType<? extends Monster> type, Level level) {
        super(type, level, "spine_hare", "shoot", "hurt", "death");
        this.xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new KeepDistanceGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (cooldown > 0) {
            cooldown--;
        }
        if (act == 0) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && cooldown == 0 && onGround()) {
                double d = distanceTo(target);
                if (d >= 3 && d <= MAX_RANGE + 2 && hasLineOfSight(target)) {
                    startShot(target);
                }
            }
            return;
        }
        act++;
        holdYaw();
        if (act >= END) {
            act = 0;
            cooldown = 50 + random.nextInt(30);
        }
    }

    // 棘を立てる: 狙った方へ向きを固定し、床の帯 (前 14・幅 1.1) が 0.8 秒で満ちる。満ちた瞬間に棘が飛ぶ (帯の中の人にだけ当たる) → 跳んで離れる
    private void startShot(LivingEntity target) {
        act = 1;
        lockYawToward(target);
        holdYaw();
        playAction("shoot");
        Vec3 f = forward();
        double angle = Telegraph.angleOf(f.x, f.z);
        Telegraph.in((ServerLevel) level(), TelegraphShape.line(getX(), getZ(), angle, LINE_LENGTH, LINE_HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this)
                .target(target)
                .startSound(MobSounds.SPINE_HARE_RAISE.get(), 1.0f, 1.0f)
                .victims(this::clearShot)
                .onImpact(server -> {
                    server.playSound(null, getX(), getY(), getZ(), MobSounds.SPINE_HARE_SHOOT.get(), SoundSource.HOSTILE, 1f, 0.9f + random.nextFloat() * 0.2f);
                    for (double d = 0.8; d <= LINE_LENGTH; d += 0.7) {
                        server.sendParticles(ParticleTypes.CRIT, getX() + f.x * d, getY() + 0.7, getZ() + f.z * d, 1, 0.02, 0.02, 0.02, 0);
                    }
                    setDeltaMovement(-f.x * 0.45, 0.42, -f.z * 0.45);
                    hasImpulse = true;
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), DAMAGE);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0), this);
                })
                .start();
    }

    // 撃つ点から人まで壁がない (遮蔽の裏には通らない)
    private boolean clearShot(ServerPlayer player) {
        if (!canHit(player)) {
            return false;
        }
        Vec3 from = new Vec3(getX(), getY() + 0.7, getZ());
        Vec3 to = new Vec3(player.getX(), player.getY() + 1.0, player.getZ());
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && act == 0) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            playAction("death");
        }
    }

    // 棘針 40% (1〜2)、骨片 10%
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) {
            return;
        }
        if (random.nextFloat() < 0.4f) {
            spawnAtLocation(new ItemStack(GuideSetup.HARE_QUILL.get(), 1 + random.nextInt(2)));
        }
        if (random.nextFloat() < 0.1f) {
            spawnAtLocation(new ItemStack(GuideSetup.BONE_CHIP.get()));
        }
    }

    // 距離をとる: 近すぎれば跳んで離れ、遠ければ寄る。技の最中は動かない
    static final class KeepDistanceGoal extends Goal {
        private final SpineHare mob;
        private int recalc;

        KeepDistanceGoal(SpineHare mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.busy();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            if (--recalc > 0) {
                return;
            }
            recalc = 6;
            double d = mob.distanceTo(t);
            if (d < MIN_RANGE) {
                Vec3 away = awayPoint(mob, t.position(), 8);
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.35);
                }
            } else if (d > MAX_RANGE) {
                mob.getNavigation().moveTo(t, 1.0);
            } else {
                mob.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.SPINE_HARE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.SPINE_HARE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.SPINE_HARE_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.SPINE_HARE_STEP.get(), 0.25f);
    }
}
