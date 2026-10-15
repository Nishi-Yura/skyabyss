package jp.revati.skyabyss.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationController;

// 骨の番兵: 鯨骨の鎧をまとった剣士。正面からの攻撃は骨の盾で受け、半分しか通らない (回り込めば効く)
public class BoneSentinel extends GuardMob {
    public BoneSentinel(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_sentinel", "attack", "block");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("attack");
        return super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            if (look.dot(toSource) > 0.5) {
                amount *= 0.5f;
                playAction("block");
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1f, 0.7f);
            }
        }
        return super.hurt(source, amount);
    }

    // クライアント用: 盾で受ける動作 (action の block) が流れている間か。盾のモデル (GearLayer) の震えに使う
    public boolean isBlockingAnim() {
        AnimationController<?> action = getAnimatableInstanceCache().getManagerForId(getId()).getAnimationControllers().get("action");
        if (action == null || !action.isPlayingTriggeredAnimation() || action.getCurrentAnimation() == null) {
            return false;
        }
        return action.getCurrentAnimation().animation().name().endsWith(".block");
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
        return 0.6f;
    }
}
