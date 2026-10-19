package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.guide.SiteSpawner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 一章の地上の敵 (岩殻虫・棘うさぎ・老いた歩哨) の共通部分。技は Telegraph (combat/Telegraph) で当たる範囲を床に描いてから当てる。
// act = 動いている技の tick (1 から数える。0 = 技の最中でない)。技の最中は向きを固定し、歩かない
public abstract class GroundFoe extends GuardMob {
    protected int act;
    protected float lockedYaw;
    protected int cooldown;

    protected GroundFoe(EntityType<? extends Monster> type, Level level, String animPrefix, String... actions) {
        super(type, level, animPrefix, actions);
    }

    public boolean busy() {
        return act > 0;
    }

    // 場所の湧き台・ひとり歩きで湧いた個体か (保存されない)
    public boolean isSiteMob() {
        return getTags().contains(SiteSpawner.TAG);
    }

    protected void lockYawToward(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        lockedYaw = (float) (Mth.atan2(dz, dx) * 57.2957763671875) - 90f;
    }

    protected void holdYaw() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        getNavigation().stop();
    }

    // 固定した向きの前 (x, z)
    protected Vec3 forward() {
        double rad = Math.toRadians(lockedYaw);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    protected boolean canHit(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && canAttack(player);
    }

    // 狙いへ近づく (止まる距離まで)。技の最中は動かない
    public static class ApproachGoal extends Goal {
        private final GroundFoe mob;
        private final double speed;
        private final double stopDistance;

        public ApproachGoal(GroundFoe mob, double speed, double stopDistance) {
            this.mob = mob;
            this.speed = speed;
            this.stopDistance = stopDistance;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
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
            if (mob.distanceTo(t) > stopDistance) {
                mob.getNavigation().moveTo(t, speed);
            } else {
                mob.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }
}
