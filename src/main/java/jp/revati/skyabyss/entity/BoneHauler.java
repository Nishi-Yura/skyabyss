package jp.revati.skyabyss.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 骨負い (docs/design/21 の 3.1): 追いかけっこ (敵意なし)。骨の籠を背負った四足の苔の獣。夕方〜夜に骨の集め場のまわりを歩き、人を見ると逃げる。
// 逃げるとき、集め場が人の背ごしでなければ、集め場へ向かって走る (追えば導かれる)。攻撃はしない。倒すと籠 (骨片 1〜3) を落とす
public class BoneHauler extends GuardMob {
    // 逃げ始める人との近さ、逃げ続ける近さ
    public static final double SCARE = 9;
    public static final double CALM = 13;
    // 集め場に着いたとみなす近さ、集め場のまわりを歩く広さ
    public static final double AT_HOME = 4;
    public static final double HOME_ROAM = 7;

    @Nullable
    private BlockPos home;

    public BoneHauler(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_hauler", "hurt", "death");
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    // 帰る先 (骨の集め場の中心)。湧かせる側が入れる
    public void setHome(@Nullable BlockPos home) {
        this.home = home;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal(this));
        goalSelector.addGoal(4, new RoamHomeGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
    }

    // 逃げる先: 集め場が人の背ごしでなければ集め場 (着いたら、集め場のまわりで人から離れる)。そうでなければ人から離れる方へ
    @Nullable
    Vec3 fleeDestination(Player scary) {
        Vec3 away = position().subtract(scary.position()).multiply(1, 0, 1);
        if (home != null) {
            Vec3 toHome = Vec3.atCenterOf(home).subtract(position()).multiply(1, 0, 1);
            double homeDist = toHome.length();
            if (homeDist > AT_HOME && away.lengthSqr() > 1.0E-4 && toHome.normalize().dot(away.normalize()) > -0.2) {
                return Vec3.atBottomCenterOf(home);
            }
            if (homeDist <= AT_HOME + 3) {
                Vec3 p = awayPoint(this, scary.position(), 7);
                if (p != null && p.distanceToSqr(Vec3.atCenterOf(home)) <= (HOME_ROAM + 3) * (HOME_ROAM + 3)) {
                    return p;
                }
                return Vec3.atBottomCenterOf(home);
            }
        }
        return awayPoint(this, scary.position(), 12);
    }

    static final class FleeGoal extends Goal {
        private final BoneHauler mob;
        private Player scary;
        private int recalc;

        FleeGoal(BoneHauler mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            scary = mob.level().getNearestPlayer(mob, SCARE);
            return scary != null && !scary.isSpectator() && !scary.isCreative();
        }

        @Override
        public boolean canContinueToUse() {
            return scary != null && scary.isAlive() && !scary.isSpectator() && mob.distanceTo(scary) < CALM;
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            if (--recalc > 0 && !mob.getNavigation().isDone()) {
                return;
            }
            recalc = 8;
            Vec3 to = mob.fleeDestination(scary);
            if (to != null) {
                mob.getNavigation().moveTo(to.x, to.y, to.z, 1.6);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    // 落ち着いているあいだ: 集め場のまわりをうろうろする
    static final class RoamHomeGoal extends Goal {
        private final BoneHauler mob;

        RoamHomeGoal(BoneHauler mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.home != null && mob.getRandom().nextInt(60) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos h = mob.home;
            double a = mob.getRandom().nextDouble() * Math.PI * 2;
            double r = 2 + mob.getRandom().nextDouble() * (HOME_ROAM - 2);
            mob.getNavigation().moveTo(h.getX() + 0.5 + Math.cos(a) * r, h.getY(), h.getZ() + 0.5 + Math.sin(a) * r, 0.7);
        }
    }

    // 集め場から離れてしまった個体は、ふつうの敵と同じように遠くで消える (番兵と違って試練の一部ではない)
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
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

    // 籠を落とす: 骨片 1〜3
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(new ItemStack(GuideSetup.BONE_CHIP.get(), 1 + random.nextInt(3)));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.BONE_HAULER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.BONE_HAULER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.BONE_HAULER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.BONE_HAULER_STEP.get(), 0.25f);
    }
}
