package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 岩殻虫 (docs/design/21 の 3.1): 盾持ちの予習。前の灰青の殻は傷が半分になり、背と腹は通る。
// 技は一つ: 立ち上がり (予告 0.8 秒。床の帯) → 体当たり。近づいて来て、帯の外へ回り込めば当たらず、立ち上がったあとの横腹・背を突ける
public class RockShellBeetle extends GroundFoe {
    public static final int WARN = 16;
    public static final double LINE_LENGTH = 3.8;
    public static final double LINE_HALF_WIDTH = 0.95;
    public static final double START_RANGE = 3.4;
    public static final int LUNGE_TICKS = 5;
    public static final double LUNGE_SPEED = 0.6;
    public static final int END = WARN + 14;

    public RockShellBeetle(EntityType<? extends Monster> type, Level level) {
        super(type, level, "rock_shell_beetle", "charge", "hurt", "death");
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 22)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ApproachGoal(this, 1.1, 2.4));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
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
            if (target != null && target.isAlive() && cooldown == 0 && onGround() && distanceTo(target) <= START_RANGE && hasLineOfSight(target)) {
                startCharge(target);
            }
            return;
        }
        act++;
        holdYaw();
        if (act >= WARN && act < WARN + LUNGE_TICKS) {
            Vec3 f = forward();
            setDeltaMovement(f.x * LUNGE_SPEED, getDeltaMovement().y, f.z * LUNGE_SPEED);
        }
        if (act >= END) {
            act = 0;
            cooldown = 35 + random.nextInt(20);
        }
    }

    // 立ち上がり: 床の帯 (前 3.8・幅 1.9) が 0.8 秒で満ち、満ちた瞬間に前へ体当たりする。当たるのは帯の中の人だけ
    private void startCharge(LivingEntity target) {
        act = 1;
        lockYawToward(target);
        holdYaw();
        playAction("charge");
        Vec3 f = forward();
        double angle = Telegraph.angleOf(f.x, f.z);
        Telegraph.in((ServerLevel) level(), TelegraphShape.line(getX(), getZ(), angle, LINE_LENGTH, LINE_HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this)
                .target(target)
                .startSound(MobSounds.ROCK_SHELL_BEETLE_CHARGE.get(), 1.2f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.POOF, getX() + f.x, getY() + 0.2, getZ() + f.z, 6, 0.4, 0.1, 0.4, 0.02))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                    player.knockback(0.9, -f.x, -f.z);
                })
                .start();
    }

    // 前からの傷は半分 (盾の殻)。背と腹・横は通る
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            if (look.dot(toSource) > 0.5) {
                amount *= 0.5f;
                level().playSound(null, getX(), getY(), getZ(), MobSounds.ROCK_SHELL_BEETLE_HURT.get(), SoundSource.HOSTILE, 1f, 0.7f + random.nextFloat() * 0.2f);
            }
        }
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

    // 岩殻片 60% (1〜2)、骨片 10%
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) {
            return;
        }
        if (random.nextFloat() < 0.6f) {
            spawnAtLocation(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 1 + random.nextInt(2)));
        }
        if (random.nextFloat() < 0.1f) {
            spawnAtLocation(new ItemStack(GuideSetup.BONE_CHIP.get()));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.ROCK_SHELL_BEETLE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.ROCK_SHELL_BEETLE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.ROCK_SHELL_BEETLE_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.ROCK_SHELL_BEETLE_STEP.get(), 0.3f);
    }
}
