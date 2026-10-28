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

// 殻背の兎 (新月の谷。docs/design/46 の 4.3・選んだ案 B 欠けた三日月の兎)。群れで落ちた星の穴の巣を守る。
//   蹴る     近くでは後ろ足で蹴る (予告つき)
//   押し潰す 少し離れると高く跳び、宙で裏返って三日月の殻を下にし、落ちて押し潰す。落ちる所に影の円が 1.2 秒前から出る (予告。円から出れば当たらない)
public class ShellHare extends MoonMob {
    public static final String ID = "shell_hare";
    public static final double HP = 26;
    public static final double DMG = 7;
    static final int SLAM_UP = 22;
    static final int SLAM_TOTAL = 34;
    static final double SLAM_RADIUS = 2.2;

    public ShellHare(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "slam", "attack", "hurt", "death");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.SAFE_FALL_DISTANCE, 40);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Slam());
        goalSelector.addGoal(2, new Tell(this, 1.1, 1.5, 6, 22, "attack", 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ShellHare.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && onGround() && getDeltaMovement().horizontalDistanceSqr() > 0.003 && tickCount % 14 == 0) {
            playSound(MoonMobsSetup.HARE_HOP.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    // 跳んで押し潰す (3〜10 の距離で、6 秒に一度)
    final class Slam extends Goal {
        private int t = -1;
        private int cooldown = 60;
        @Nullable
        private Vec3 spot;

        Slam() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (tg == null || !tg.isAlive() || !onGround()) {
                return false;
            }
            double d = distanceTo(tg);
            return d >= 3.0 && d <= 10.0;
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= SLAM_TOTAL && isAlive();
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
            playAction("slam");
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl) || spot == null) {
                return;
            }
            if (t == 6) {
                // 跳ぶ (少し的の方へ)
                Vec3 d = spot.subtract(position());
                setDeltaMovement(d.x * 0.12, 1.05, d.z * 0.12);
                hasImpulse = true;
                playSound(MoonMobsSetup.HARE_HOP.get(), 1.0f, 0.7f);
            }
            // 影の円の予告
            if (t >= 10 && t % 2 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i / 16.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SQUID_INK, spot.x + Math.cos(a) * SLAM_RADIUS, spot.y + 0.1, spot.z + Math.sin(a) * SLAM_RADIUS, 1, 0, 0, 0, 0);
                }
            }
            if (t == SLAM_UP) {
                // 円の真上から落ちる
                setPos(spot.x, Math.max(getY(), spot.y + 4), spot.z);
                setDeltaMovement(0, -1.6, 0);
                hasImpulse = true;
            }
            if (t > SLAM_UP && (onGround() || t == SLAM_TOTAL)) {
                land(sl);
                t = SLAM_TOTAL + 1;
            }
        }

        private void land(ServerLevel sl) {
            playSound(MoonMobsSetup.HARE_SLAM.get(), 1.2f, 0.9f);
            sl.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.2, spot.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.2, spot.z, 14, SLAM_RADIUS * 0.5, 0.1, SLAM_RADIUS * 0.5, 0.02);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(spot, spot).inflate(SLAM_RADIUS, 2, SLAM_RADIUS),
                    e -> e instanceof Player && e.isAlive())) {
                if (e.position().distanceTo(spot) <= SLAM_RADIUS + 0.3) {
                    strike(e, dmg(1.5));
                    e.knockback(0.6, spot.x - e.getX(), spot.z - e.getZ());
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            spot = null;
            cooldown = 120;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.CRESCENT_SHELL.get(), 0.35, 1, 1);
    }
}
