package jp.revati.skyabyss.titan.storm.chapter.mob;

import java.util.EnumSet;

import javax.annotation.Nullable;

import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 雷拾い (三章の地上): 追いかけっこ (敵意なし)。銅の脚の長い鳥。HP 12。攻撃はしない。
//   ついばむ  ふだんは歩いて、ときどき焼けた荷の跡をついばむ (アニメ peck)
//   逃げる    人が SCARE の近さに来ると羽ばたいて跳び、走って逃げる。逃げる先はいちばん近い雷拾いの原 (LEAD_RANGE 以内。なければ人から離れる向き)。
//             追う人を原へ導く。人が CALM より離れると歩きに戻る
//   荷を落とす はじめて驚いたとき、背の荷を落とす (銅線 1、焦げ羽 50%)。一羽に一度だけ (dropped。保存する)。荷のない鳥は背の荷が消える (アニメ packless)
//   落とす物 (倒したとき) 銅線 50%、焦げ羽 50%
public class LightningGleaner extends GuardMob {
    public static final float HEALTH = 12;
    public static final double SCARE = 6.0;
    public static final double CALM = 10.0;
    public static final double LEAD_RANGE = 400;
    public static final double FLEE_STEP = 10;

    private static final EntityDataAccessor<Boolean> PACK = SynchedEntityData.defineId(LightningGleaner.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLEEING = SynchedEntityData.defineId(LightningGleaner.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation A_PACKLESS = RawAnimation.begin().thenLoop("animation.lightning_gleaner.packless");
    private static final RawAnimation A_FLEE = RawAnimation.begin().thenLoop("animation.lightning_gleaner.flee");

    @Nullable
    private BlockPos home;
    private int peck = 60;

    public LightningGleaner(EntityType<? extends Monster> type, Level level) {
        super(type, level, "lightning_gleaner", "peck", "flap", "flee", "packless", "hurt", "death");
        this.xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PACK, true);
        builder.define(FLEEING, false);
    }

    public boolean hasPack() {
        return entityData.get(PACK);
    }

    public boolean isFleeing() {
        return entityData.get(FLEEING);
    }

    // 雷拾いの原の鳥: 原のまわりにとどまる
    public void setHome(@Nullable BlockPos home) {
        this.home = home;
        if (home != null) {
            restrictTo(home, 12);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FleeGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<>(this, "flee", 3, s -> isFleeing() && !isDeadOrDying() ? s.setAndContinue(A_FLEE) : PlayState.STOP));
        controllers.add(new AnimationController<>(this, "pack", 0, s -> hasPack() ? PlayState.STOP : s.setAndContinue(A_PACKLESS)));
    }

    // 敵意なし
    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boolean near = nearest(isFleeing() ? CALM : SCARE) != null;
        if (near && !isFleeing()) {
            entityData.set(FLEEING, true);
            playAction("flap");
            playSound(Ch3Mobs.GLEANER_FLAP.get(), 0.9f, 0.9f + random.nextFloat() * 0.2f);
            // 羽ばたいて小さく跳ぶ
            setDeltaMovement(getDeltaMovement().add(0, 0.45, 0));
            dropPack();
        } else if (!near && isFleeing()) {
            entityData.set(FLEEING, false);
        }
        if (!isFleeing() && --peck <= 0) {
            peck = 80 + random.nextInt(120);
            if (getNavigation().isDone()) {
                playAction("peck");
            }
        }
    }

    @Nullable
    ServerPlayer nearest(double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : Players.snapshot(((ServerLevel) level()).players())) {
            double d = p.distanceToSqr(this);
            if (!p.isSpectator() && p.isAlive() && d <= bd && GroupOwned.visibleTo(this, p)) {
                best = p;
                bd = d;
            }
        }
        return best;
    }

    // 背の荷を落とす (銅線 1、焦げ羽 50%)。一羽に一度。落としたら true
    public boolean dropPack() {
        if (!hasPack()) {
            return false;
        }
        entityData.set(PACK, false);
        playSound(Ch3Mobs.GLEANER_DROP.get(), 0.9f, 1.0f);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.1, getZ(), 6, 0.2, 0.1, 0.2, 0.05);
            Item wire = Ch3Mobs.item(Ch3Mobs.COPPER_WIRE);
            Item feather = Ch3Mobs.item(Ch3Mobs.SCORCHED_FEATHER);
            if (wire != null) {
                toss(server, new ItemStack(wire));
            }
            if (feather != null && random.nextFloat() < 0.5f) {
                toss(server, new ItemStack(feather));
            }
        }
        return true;
    }

    private void toss(ServerLevel server, ItemStack stack) {
        ItemEntity drop = new ItemEntity(server, getX(), getY() + 1.0, getZ(), stack);
        drop.setDeltaMovement((random.nextDouble() - 0.5) * 0.2, 0.2, (random.nextDouble() - 0.5) * 0.2);
        drop.setDefaultPickUpDelay();
        server.addFreshEntity(drop);
    }

    // 逃げる先: いちばん近い雷拾いの原 (LEAD_RANGE 以内) へ FLEE_STEP 進んだ所。原にいるなら、人から離れる向き
    @Nullable
    public Vec3 fleeGoal(Vec3 from) {
        BlockPos field = home != null ? home : Ch3Mobs.nearestField(level().getServer(), getX(), getZ(), LEAD_RANGE);
        if (field != null) {
            Vec3 c = Vec3.atBottomCenterOf(field);
            Vec3 to = c.subtract(position()).multiply(1, 0, 1);
            if (to.length() > 6) {
                Vec3 step = position().add(to.normalize().scale(Math.min(FLEE_STEP, to.length())));
                return new Vec3(step.x, getY(), step.z);
            }
        }
        return awayPoint(this, from, 6);
    }

    static final class FleeGoal extends Goal {
        private final LightningGleaner mob;
        private int repath;

        FleeGoal(LightningGleaner mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.isFleeing();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (--repath > 0) {
                return;
            }
            repath = 15;
            ServerPlayer p = mob.nearest(CALM);
            Vec3 goal = mob.fleeGoal(p != null ? p.position() : mob.position());
            if (goal != null) {
                mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.6);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
            dropPack();
            entityData.set(FLEEING, true);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            playAction("death");
        }
    }

    // 銅線 50%、焦げ羽 50%
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) {
            return;
        }
        Item wire = Ch3Mobs.item(Ch3Mobs.COPPER_WIRE);
        Item feather = Ch3Mobs.item(Ch3Mobs.SCORCHED_FEATHER);
        if (wire != null && random.nextFloat() < 0.5f) {
            spawnAtLocation(new ItemStack(wire));
        }
        if (feather != null && random.nextFloat() < 0.5f) {
            spawnAtLocation(new ItemStack(feather));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Pack", hasPack());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Pack")) {
            entityData.set(PACK, tag.getBoolean("Pack"));
        }
    }

    // 鳥なので落ちても平気
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch3Mobs.GLEANER_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 220;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch3Mobs.GLEANER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch3Mobs.GLEANER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        modStep(Ch3Mobs.GLEANER_STEP.get(), 0.25f);
    }
}
