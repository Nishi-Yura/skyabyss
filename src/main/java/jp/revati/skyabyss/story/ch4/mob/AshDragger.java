package jp.revati.skyabyss.story.ch4.mob;

import java.util.EnumSet;

import javax.annotation.Nullable;

import jp.revati.skyabyss.story.ch4.Ch4Ground;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import jp.revati.skyabyss.story.ch4.Ch4Setup;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 灰ひきずり (docs/design/21 の 6.1): 追いかけっこの相手 (敵意なし)。HP 20。灰を引きずって歩き、跡に灰の粒を残す。
//   人が 6 ブロックまで来ると丸まり (0.3 秒)、煤の窯の跡 (Ch4Ground.kilnNear。なければ人から離れる向き) へ転がって逃げる (速さ 1.6 倍)。
//   人が 14 より離れて 3 秒で、丸まりをほどいて歩く。窯の跡の 4 ブロックまで転がり着くと、窯の口へ潜って消える (近くの人に一行。巣)。
//   燠虫 (背中) の転がりの小さな予習。叩くと灰麻 50%・煤鉄 30%
public class AshDragger extends EmberMob {
    private static final EntityDataAccessor<Boolean> BALL = SynchedEntityData.defineId(AshDragger.class, EntityDataSerializers.BOOLEAN);
    public static final float HEALTH = 20;
    public static final double NOTICE = 6;
    public static final double CALM = 14;
    public static final double NEST = 4;
    public static final double KILN_RANGE = 220;
    private int calm;

    public AshDragger(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ash_dragger", "curl", "uncurl");
        this.xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BALL, false);
    }

    public boolean isBall() {
        return entityData.get(BALL);
    }

    @Override
    protected String state() {
        if (!isBall()) {
            return null;
        }
        return getDeltaMovement().horizontalDistanceSqr() > 1e-4 ? "rolling" : "curled";
    }

    @Override
    protected boolean frozen() {
        return isBall();
    }

    // 敵意はない
    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Nullable
    Player nearestChaser(double range) {
        Player p = level().getNearestPlayer(this, range);
        return p != null && !p.isSpectator() && !p.isCreative() ? p : null;
    }

    public void curl(boolean on) {
        if (isBall() == on) {
            return;
        }
        entityData.set(BALL, on);
        playAction(on ? "curl" : "uncurl");
        if (on) {
            playSound(Ch4Setup.DRAGGER_CURL.get(), 0.8f, 1.0f);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            // 引きずる灰の跡
            if (getDeltaMovement().horizontalDistanceSqr() > 1e-4 && random.nextInt(3) == 0) {
                level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()),
                        getX() - getDeltaMovement().x * 4, getY() + 0.1, getZ() - getDeltaMovement().z * 4, 0, 0, 0);
            }
            return;
        }
        if (isBall() && tickCount % 10 == 0 && getDeltaMovement().horizontalDistanceSqr() > 1e-3) {
            playSound(Ch4Setup.DRAGGER_ROLL.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    // 窯の跡に転がり着いた: 近くの人に一行 (人ごとに一度。灰ひきずりの頼みの道しるべ)、窯の口へ潜って消える
    void reachNest(ServerLevel level, BlockPos kiln) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()), getX(), getY() + 0.3, getZ(),
                24, 0.4, 0.3, 0.4, 0.05);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 32 * 32 && jp.revati.skyabyss.rpg.Growth.discover(p, "ch4_dragger_nest")) {
                jp.revati.skyabyss.notice.Notice.narrate(p, "message.skyabyss.ch4g.kiln.dragger");
            }
        }
        level.playSound(null, getX(), getY(), getZ(), Ch4Setup.DRAGGER_ROLL.get(), SoundSource.NEUTRAL, 0.8f, 0.7f);
        discard();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, Ch4Setup.ASH_HEMP.get(), 0.5, 1, 2);
        Ch4Mobs.drop(level, this, source, Ch4Setup.SOOT_IRON.get(), 0.3, 1, 1);
    }

    // ---------------------------------------------------------------- 逃げる
    static final class FleeGoal extends Goal {
        private final AshDragger mob;
        private int repath;

        FleeGoal(AshDragger mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.isBall() || mob.nearestChaser(NOTICE) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.isBall();
        }

        @Override
        public void start() {
            mob.curl(true);
            mob.calm = 0;
            repath = 6;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(mob.level() instanceof ServerLevel level)) {
                return;
            }
            Player chaser = mob.nearestChaser(CALM);
            if (chaser == null) {
                if (++mob.calm > 60) {
                    mob.curl(false);
                }
                mob.getNavigation().stop();
                return;
            }
            mob.calm = 0;
            BlockPos kiln = Ch4Ground.kilnNear(level, mob.blockPosition(), KILN_RANGE);
            if (kiln != null && mob.distanceToSqr(Vec3.atBottomCenterOf(kiln)) < NEST * NEST) {
                mob.reachNest(level, kiln);
                return;
            }
            if (--repath > 0) {
                return;
            }
            repath = 10;
            Vec3 to;
            if (kiln != null) {
                // 窯へ。ただし人の真ん前へは行かない (窯の向きと、人から離れる向きを混ぜる)
                Vec3 k = Vec3.atBottomCenterOf(kiln).subtract(mob.position()).multiply(1, 0, 1).normalize();
                Vec3 away = mob.position().subtract(chaser.position()).multiply(1, 0, 1).normalize();
                Vec3 dir = k.scale(0.75).add(away.scale(0.25)).normalize();
                to = mob.position().add(dir.scale(Math.min(10, Math.sqrt(mob.distanceToSqr(Vec3.atBottomCenterOf(kiln))))));
            } else {
                to = jp.revati.skyabyss.entity.GuardMob.awayPoint(mob, chaser.position(), 10);
            }
            if (to != null) {
                mob.getNavigation().moveTo(to.x, to.y, to.z, 1.6);
            }
        }
    }
}
