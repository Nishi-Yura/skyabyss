package jp.revati.skyabyss.titan.tide.mob;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

// 磯走り (08 の 5.1節): 瓦の殻を背負った小さな蟹。3〜5 体の群れで速く走る。
// 一体が人を見つける・叩かれると、近くの仲間も同じ人へ向かう。
// 跳びながらの会心の一撃を受けると裏返り、3 秒動けず、その間は傷が 2 倍。水に入ると鈍る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public class ReefScuttler extends TideMob {
    private static final EntityDataAccessor<Integer> FLIPPED = SynchedEntityData.defineId(ReefScuttler.class, EntityDataSerializers.INT);
    private static final ResourceLocation WADING = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "scuttler_wading");

    public static final float HEALTH = 10;
    public static final float DAMAGE = 3;
    public static final float ARMOR = 4;
    public static final double SPEED = 0.35;
    public static final int FLIP_TICKS = 60;
    public static final float FLIPPED_DAMAGE = 2f;
    // 水の中の速さ (掛け算)
    static final double WATER_SLOW = -0.5;
    static final double PACK_RADIUS = 10;

    public ReefScuttler(EntityType<? extends Monster> type, Level level) {
        super(type, level, "reef_scuttler", "attack");
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLIPPED, 0);
    }

    public boolean isFlipped() {
        return entityData.get(FLIPPED) > 0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected String state() {
        return isFlipped() ? "flipped" : null;
    }

    @Override
    protected boolean frozen() {
        return isFlipped();
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isFlipped();
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            int f = entityData.get(FLIPPED);
            if (f > 0) {
                entityData.set(FLIPPED, f - 1);
                getNavigation().stop();
            }
            // 水の中では鈍る
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            boolean wet = isInWater();
            if (speed != null && wet != speed.hasModifier(WADING)) {
                if (wet) {
                    speed.addTransientModifier(new AttributeModifier(WADING, WATER_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                } else {
                    speed.removeModifier(WADING);
                }
            }
            // 群れ: 人を追っている間、近くの手のすいた仲間にも同じ人を追わせる
            if (tickCount % 20 == 0 && getTarget() instanceof Player target && target.isAlive()) {
                List<ReefScuttler> pack = level().getEntitiesOfClass(ReefScuttler.class, getBoundingBox().inflate(PACK_RADIUS),
                        s -> s != this && s.getTarget() == null && s.isAlive());
                for (ReefScuttler s : pack) {
                    s.setTarget(target);
                }
            }
        }
        super.aiStep();
    }

    // 裏返す (会心の一撃を受けたとき)
    public void flip() {
        if (isFlipped() || !isAlive()) {
            return;
        }
        entityData.set(FLIPPED, FLIP_TICKS);
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().x * 0.3, 0.3, getDeltaMovement().z * 0.3);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), TideMobSetup.SCUTTLER_FLIP.get(), SoundSource.HOSTILE, 1f, 1f);
            server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.4, getZ(), 10, 0.3, 0.2, 0.3, 0.2);
        }
    }

    // 裏返った背 (腹) への傷は 2 倍
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFlipped()) {
            amount *= FLIPPED_DAMAGE;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("attack");
        return super.doHurtTarget(target);
    }

    // 跳びながらの会心の一撃 (バニラの会心の条件) で裏返る。傷の 2 倍は、裏返ったあとの次の一撃から
    @SubscribeEvent
    static void onCritical(CriticalHitEvent event) {
        if (event.isCriticalHit() && event.getTarget() instanceof ReefScuttler s && !s.level().isClientSide) {
            s.flipNext = true;
        }
    }

    private boolean flipNext;

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (flipNext) {
            flipNext = false;
            flip();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 会心の判定のあと、傷が通らなかった (無敵時間など) ときは印を消す
        flipNext = false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TideMobSetup.SCUTTLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TideMobSetup.SCUTTLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TideMobSetup.SCUTTLER_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(TideMobSetup.SCUTTLER_STEP.get(), 0.25f, 1f + random.nextFloat() * 0.3f);
    }
}
