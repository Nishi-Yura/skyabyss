package jp.revati.skyabyss.dungeon.mob;

import java.util.function.Supplier;

import jp.revati.skyabyss.entity.foe.ContinentFoe;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// ダンジョンの宙に浮く敵 (洞の水母・晶の目玉) の共通: 歩かずに漂う (迷い火 StrayWisp と同じ作り)。
// 足もとの床 + hover の高さで、人との間を [near, far] に保つ。地面に立たないので、技は aiStep で選ぶ (tryAct)
public abstract class DungeonFloater extends ContinentFoe {
    private final double hover;
    private final double speed;
    private final double near;
    private final double far;
    private Vec3 wander;
    private int wanderFor;

    protected DungeonFloater(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, double hover, double speed, double near, double far, String... actions) {
        super(type, level, animPrefix, ambient, hurt, death, actions);
        this.hover = hover;
        this.speed = speed;
        this.near = near;
        this.far = far;
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (act > 0 || isDeadOrDying()) {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            return;
        }
        LivingEntity t = getTarget();
        Vec3 goal = null;
        if (t != null && t.isAlive()) {
            double d = distanceTo(t);
            if (d > far) {
                goal = t.position();
            } else if (d < near) {
                goal = position().add(position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(3));
            }
            getLookControl().setLookAt(t, 30f, 30f);
        } else {
            if (--wanderFor <= 0 || wander == null) {
                wanderFor = 60 + random.nextInt(80);
                wander = position().add((random.nextDouble() - 0.5) * 10, 0, (random.nextDouble() - 0.5) * 10);
            }
            goal = wander;
        }
        double ground = floorBelow();
        double wantY = ground + hover + Math.sin(tickCount * 0.07) * 0.25;
        double vx = 0;
        double vz = 0;
        if (goal != null) {
            Vec3 d = goal.subtract(position()).multiply(1, 0, 1);
            double len = d.length();
            if (len > 0.3) {
                vx = d.x / len * speed;
                vz = d.z / len * speed;
            }
        }
        double vy = Math.max(-0.08, Math.min(0.08, (wantY - getY()) * 0.1));
        setDeltaMovement(vx, vy, vz);
        if (t != null) {
            float yaw = (float) (Math.atan2(t.getZ() - getZ(), t.getX() - getX()) * 57.29577951308232) - 90f;
            setYRot(yaw);
            setYBodyRot(yaw);
        } else if (vx * vx + vz * vz > 1.0E-4) {
            float yaw = (float) (Math.atan2(vz, vx) * 57.29577951308232) - 90f;
            setYRot(yaw);
            setYBodyRot(yaw);
        }
    }

    // 真下の床の高さ (洞窟の中なので空の高さの地図は使わず、下へ 8 まで探す)
    private double floorBelow() {
        BlockPos.MutableBlockPos p = blockPosition().mutable();
        for (int i = 0; i < 8; i++) {
            if (!level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty()) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return getY() - hover;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
    }

    // 技を始めるなら act を 1 にする (begin)
    protected abstract void tryAct(LivingEntity target, double distance);

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && act == 0 && cooldown == 0) {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive() && hasLineOfSight(t)) {
                tryAct(t, distanceTo(t));
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }
}
