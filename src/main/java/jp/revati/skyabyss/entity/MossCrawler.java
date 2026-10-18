package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 苔這い (苔芋虫): 骨の輪と苔に覆われた、五つ節の長い芋虫。低く素早く這い寄って、前の節を起こして飛びかかり、噛まれると足が重くなる
public class MossCrawler extends GuardMob {
    public MossCrawler(EntityType<? extends Monster> type, Level level) {
        super(type, level, "moss_crawler", "pounce", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // 飛びかかりの踏み切りで、前の節を起こして飛び込む動き (pounce) を流す
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45f) {
            @Override
            public void start() {
                super.start();
                pounceAnim();
            }
        });
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 飛び込みの動きの最中 (0.9 秒) に、噛みつきで流し直して途切れさせない
    private long lastPounce = -100;

    private void pounceAnim() {
        if (level().getGameTime() - lastPounce >= 12) {
            lastPounce = level().getGameTime();
            playAction("pounce");
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        pounceAnim();
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
        }
        return hit;
    }

    // 被弾の身震い (死ぬ一撃では、倒れる動きを流すのでここでは流さない)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
        }
        return hit;
    }

    // 倒れる: 渦を巻いて丸まり (death)、そのまま消えるまで止まる
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            playAction("death");
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.MOSS_CRAWLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.MOSS_CRAWLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.MOSS_CRAWLER_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.MOSS_CRAWLER_STEP.get(), 0.2f);
    }

    @Override
    public float getVoicePitch() {
        return 1.0f + (random.nextFloat() - random.nextFloat()) * 0.08f;
    }
}
