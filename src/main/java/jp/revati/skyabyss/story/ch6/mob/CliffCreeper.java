package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 崖這い (上弦の崖。docs/design/46 の 4.4・選んだ案 B 岩背負いの蟹)。崖の面に張り付いて登り降りし、背の岩を外して落とす。
//   岩落とし 獲物が 12 の内にいれば、獲物の足もとに影の円 (1.2 秒の予告) → 円の真上から岩が落ちる。岩は 6 秒で背に戻る (それまでは落とせない)
//   挟む     近くでは鋏で挟む (予告つき)
public class CliffCreeper extends MoonMob {
    public static final String ID = "cliff_creeper";
    public static final double HP = 38;
    public static final double DMG = 7;
    private static final EntityDataAccessor<Boolean> ROCKLESS = SynchedEntityData.defineId(CliffCreeper.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(CliffCreeper.class, EntityDataSerializers.BOOLEAN);
    static final int DROP_TELL = 24;
    static final int REGROW = 120;
    static final double DROP_RADIUS = 1.8;
    private int regrow;

    public CliffCreeper(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "throw", "attack", "hurt", "death");
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROCKLESS, false);
        builder.define(CLIMBING, false);
    }

    public boolean rockless() {
        return entityData.get(ROCKLESS);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        entityData.set(CLIMBING, horizontalCollision);
        if (regrow > 0 && --regrow == 0) {
            entityData.set(ROCKLESS, false);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Drop());
        goalSelector.addGoal(2, new Tell(this, 1.0, 1.7, 8, 26, "attack", 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 岩落とし (背に岩があり、獲物が 3〜12 の内で見えるとき)
    final class Drop extends Goal {
        private int t = -1;
        @Nullable
        private Vec3 spot;

        Drop() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            if (rockless() || tg == null || !tg.isAlive()) {
                return false;
            }
            double d = distanceTo(tg);
            return d >= 3.0 && d <= 12.0 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= DROP_TELL + 4 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            LivingEntity tg = getTarget();
            spot = tg == null ? null : tg.position();
            getNavigation().stop();
            playAction("throw");
            playSound(MoonMobsSetup.CREEPER_LIFT.get(), 1.0f, 0.8f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl) || spot == null) {
                return;
            }
            LivingEntity tg = getTarget();
            if (tg != null) {
                getLookControl().setLookAt(tg, 30f, 30f);
            }
            if (t <= DROP_TELL && t % 2 == 0) {
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SQUID_INK, spot.x + Math.cos(a) * DROP_RADIUS, spot.y + 0.1, spot.z + Math.sin(a) * DROP_RADIUS, 1, 0, 0, 0, 0);
                }
                // 落ちてくる岩の影 (上から下りる粒)
                double h = 8.0 * (1.0 - t / (double) DROP_TELL);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), spot.x, spot.y + 1 + h, spot.z, 3, 0.3, 0.2, 0.3, 0);
            }
            if (t == 8) {
                entityData.set(ROCKLESS, true);
                regrow = REGROW;
            }
            if (t == DROP_TELL) {
                playSound(MoonMobsSetup.CREEPER_ROCK.get(), 1.4f, 0.8f);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), spot.x, spot.y + 0.5, spot.z, 40, 0.8, 0.4, 0.8, 0.1);
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(spot, spot).inflate(DROP_RADIUS, 2.5, DROP_RADIUS), e -> e.isAlive())) {
                    if (p.position().subtract(spot).horizontalDistance() <= DROP_RADIUS + 0.3) {
                        strike(p, dmg(1.3));
                    }
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            spot = null;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.CRESCENT_SHELL.get(), 0.3, 1, 1);
    }
}
