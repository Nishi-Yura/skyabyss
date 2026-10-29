package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 月跳ね (上弦の崖。docs/design/46 の 4.4・選んだ案 A 岩の跳び虫)。長い後ろ脚で崖から崖へ跳ぶ。
//   跳びかかる 4〜14 の距離で、高く跳んで獲物の上から落ちる。落ちる所に影の円が 1 秒前から出る (予告。円から出れば当たらない)
//   斬る       近くでは短い鎌の前腕で斬る (予告つき)
public class MoonLeaper extends MoonMob {
    public static final String ID = "moon_leaper";
    public static final double HP = 30;
    public static final double DMG = 7;
    static final int POUNCE_DOWN = 20;
    static final int POUNCE_TOTAL = 40;
    static final double POUNCE_RADIUS = 2.0;

    public MoonLeaper(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "leap", "attack", "hurt", "death");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.SAFE_FALL_DISTANCE, 60);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Pounce());
        goalSelector.addGoal(2, new Tell(this, 1.15, 1.8, 7, 24, "attack", 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    // 跳びかかる (4〜14 の距離で、5 秒に一度)
    final class Pounce extends Goal {
        private int t = -1;
        private int cooldown = 40;
        @Nullable
        private Vec3 spot;

        Pounce() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            if (tg == null || !tg.isAlive() || !onGround()) {
                return false;
            }
            double d = distanceTo(tg);
            return d >= 4.0 && d <= 14.0 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= POUNCE_TOTAL && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            LivingEntity tg = getTarget();
            spot = tg == null ? position() : tg.position();
            getNavigation().stop();
            playAction("leap");
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl) || spot == null) {
                return;
            }
            if (t == 5) {
                Vec3 d = spot.subtract(position());
                setDeltaMovement(d.x * 0.1, 1.15, d.z * 0.1);
                hasImpulse = true;
                playSound(MoonMobsSetup.LEAPER_JUMP.get(), 1.0f, 0.8f);
            }
            if (t <= POUNCE_DOWN && t % 2 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i / 16.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SQUID_INK, spot.x + Math.cos(a) * POUNCE_RADIUS, spot.y + 0.1, spot.z + Math.sin(a) * POUNCE_RADIUS, 1, 0, 0, 0, 0);
                }
            }
            if (t == POUNCE_DOWN) {
                setPos(spot.x, Math.max(getY(), spot.y + 5), spot.z);
                setDeltaMovement(0, -1.8, 0);
                hasImpulse = true;
            }
            if (t > POUNCE_DOWN && (onGround() || t == POUNCE_TOTAL)) {
                land(sl);
                t = POUNCE_TOTAL + 1;
            }
        }

        private void land(ServerLevel sl) {
            playSound(MoonMobsSetup.LEAPER_LAND.get(), 1.2f, 0.9f);
            sl.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.2, spot.z, 12, POUNCE_RADIUS * 0.5, 0.1, POUNCE_RADIUS * 0.5, 0.02);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(spot, spot).inflate(POUNCE_RADIUS, 2, POUNCE_RADIUS),
                    e -> e instanceof Player && e.isAlive())) {
                if (e.position().distanceTo(spot) <= POUNCE_RADIUS + 0.3) {
                    strike(e, dmg(1.4));
                    e.knockback(0.5, spot.x - e.getX(), spot.z - e.getZ());
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            spot = null;
            cooldown = 100;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.MOON_DUST.get(), 0.35, 1, 1);
    }
}
