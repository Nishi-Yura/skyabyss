package jp.revati.skyabyss.abyss.mobs;

import java.util.EnumSet;

import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 燠の番人 (灰の層・docs/design/36 の 4・4.1 C 灰の僧)。熱の気配で人に近づき、胸の燠が先に見える。
//   熱の輪: 近くに人がいると、足もとに赤い輪 (半径 HEAT_RADIUS) を 16 tick 見せてから熱を出す。輪の中の人は傷と火 (3 秒)。
//   輪の外へ出れば当たらない。離れすぎた人には近づき、近すぎれば下がる (距離を保つ)。
public class EmberKeeper extends AbyssMobB {
    public static final String ID = "ember_keeper";
    public static final double HP = 40;
    public static final double DMG = 6;
    public static final double HEAT_RADIUS = 4.0;
    private static final int TELL_TICKS = 16;
    private static final int COOLDOWN_TICKS = 70;
    private static final int FIRE_TICKS = 60;

    public EmberKeeper(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "telegraph", "heat", "hurt", "death");
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new HeatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && random.nextInt(3) == 0) {
            // 胸の燠の粒 (近くの人に見える)
            ((ServerLevel) level()).sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 1.3, getZ(), 2, 0.15, 0.2, 0.15, 0.01);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.LAMP_ASH.get(), 0.5, 1, 2);
    }

    // 熱の輪: 予告 (赤い輪の粒) → 熱 (輪の中の人に傷と火)。距離を保つ
    static final class HeatGoal extends Goal {
        private final EmberKeeper mob;
        private int wind = -1;
        private int cooldown;
        private int repath;

        HeatGoal(EmberKeeper mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            wind = -1;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player t = mob.getTarget() instanceof Player p ? p : null;
            if (t == null || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            if (wind >= 0) {
                mob.getNavigation().stop();
                ringParticles(level);
                if (--wind < 0) {
                    heat(level);
                    cooldown = COOLDOWN_TICKS;
                }
                return;
            }
            double d = mob.distanceTo(t);
            if (d <= 12 && cooldown == 0 && mob.hasLineOfSight(t)) {
                mob.playAction("telegraph");
                mob.playSound(AbyssMobsBSetup.EMBER_KEEPER_TELEGRAPH.get(), 0.9f, 1.0f);
                wind = TELL_TICKS;
                return;
            }
            if (--repath > 0) {
                return;
            }
            repath = 10;
            if (d < 5.5) {
                Vec3 away = jp.revati.skyabyss.entity.GuardMob.awayPoint(mob, t.position(), 6);
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 0.9);
                }
            } else if (d > 9) {
                mob.getNavigation().moveTo(t, 1.0);
            } else {
                mob.getNavigation().stop();
            }
        }

        private void ringParticles(ServerLevel level) {
            for (int i = 0; i < 8; i++) {
                double a = (i / 8.0) * Math.PI * 2;
                level.sendParticles(ParticleTypes.FLAME, mob.getX() + Math.cos(a) * HEAT_RADIUS, mob.getY() + 0.1,
                        mob.getZ() + Math.sin(a) * HEAT_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        private void heat(ServerLevel level) {
            mob.playAction("heat");
            mob.playSound(AbyssMobsBSetup.EMBER_KEEPER_HEAT.get(), 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.LAVA, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, HEAT_RADIUS / 2, 0.3, HEAT_RADIUS / 2, 0.0);
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator()) {
                    continue;
                }
                double dx = p.getX() - mob.getX();
                double dz = p.getZ() - mob.getZ();
                if (dx * dx + dz * dz <= HEAT_RADIUS * HEAT_RADIUS && Math.abs(p.getY() - mob.getY()) < 3) {
                    p.hurt(mob.attackSource(), mob.dmg());
                    p.setRemainingFireTicks(FIRE_TICKS);
                }
            }
        }
    }
}
