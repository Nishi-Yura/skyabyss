package jp.revati.skyabyss.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimationController;

// 骨棘の射手: 肋骨の弓で、骨の棘を放つ。当たると動きが鈍る
public class RibArcher extends GuardMob implements RangedAttackMob {
    public RibArcher(EntityType<? extends Monster> type, Level level) {
        super(type, level, "rib_archer", "shoot");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 28, 46, 18f));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 弦を放つまでの時間 (shoot アニメーションの 0.50 秒 = 10 tick)。構えて引いてから棘が飛ぶ
    private static final int RELEASE_TICKS = 10;
    private LivingEntity pendingTarget;
    private int releaseIn;

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        playAction("shoot");
        pendingTarget = target;
        releaseIn = RELEASE_TICKS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && releaseIn > 0 && --releaseIn == 0) {
            LivingEntity target = pendingTarget;
            pendingTarget = null;
            if (target != null && target.isAlive() && !isDeadOrDying()) {
                fireSpike(target);
            }
        }
    }

    private void fireSpike(LivingEntity target) {
        BoneSpike spike = new BoneSpike(level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.33) - spike.getY();
        double dz = target.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        spike.shoot(dx, dy + d * 0.18, dz, 1.7f, 4f);
        level().addFreshEntity(spike);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1f, 0.6f);
    }

    // 描画側 (弓の GearLayer) が使う: shoot の動作アニメーションが流れている間は弓の弦を引く (draw)
    public boolean isDrawingBow() {
        AnimationController<?> action = getAnimatableInstanceCache().getManagerForId(getId()).getAnimationControllers().get("action");
        return action != null && action.isPlayingTriggeredAnimation();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BONE_BLOCK_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 0.8f;
    }
}
