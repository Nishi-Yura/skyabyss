package jp.revati.skyabyss.titan.tide.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 泡吹きの吐く泡 (08 の 5.1節)。まっすぐ、ゆっくり飛ぶ。当たると 4 の傷と、
// 1 秒だけ真上へ 1.5 ブロック浮かせ、ゆっくり降ろす (低速落下 2 秒)。横へは押さない (台から押し落とさない)
public class BubbleShot extends Projectile {
    public static final float DAMAGE = 4;
    // 真上へ浮かせる速さ (重力と空気の抵抗で、約 1.5 ブロック上がる)
    public static final double LIFT = 0.48;
    public static final int SLOW_FALL_TICKS = 40;
    static final double SPEED = 0.42;
    static final int LIFETIME = 70;

    public BubbleShot(EntityType<? extends BubbleShot> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public BubbleShot(Level level, LivingEntity owner, Vec3 dir) {
        this(TideMobSetup.BUBBLE_SHOT.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.45, owner.getZ());
        setDeltaMovement(dir.normalize().scale(SPEED));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            hitTargetOrDeflectSelf(hit);
            if (isRemoved()) {
                return;
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5f);
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.BUBBLE_POP, getX(), getY(), getZ(), 0, 0.01, 0);
            }
        } else if (tickCount > LIFETIME) {
            pop();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target instanceof LivingEntity living) {
            Vec3 before = living.getDeltaMovement();
            boolean hurt = living.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity o ? o : null), DAMAGE);
            if (hurt) {
                lift(living, before);
            }
        }
        pop();
    }

    // 真上へ浮かせる。傷のノックバックで付いた横の速さは、当たる前の速さへ戻す
    public static void lift(LivingEntity living, Vec3 before) {
        living.setDeltaMovement(before.x, LIFT, before.z);
        living.hurtMarked = true;
        living.hasImpulse = true;
        living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        pop();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof BubblePuffer);
    }

    private void pop() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY(), getZ(), 12, 0.25, 0.25, 0.25, 0.05);
            server.playSound(null, getX(), getY(), getZ(), TideMobSetup.BUBBLE_POP.get(), SoundSource.HOSTILE, 0.9f,
                    0.9f + random.nextFloat() * 0.3f);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }
}
