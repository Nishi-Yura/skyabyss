package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.finale.Echoes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 灰の打ち手 (炉の島。docs/design/50 の 4.2.3・一品一案「大槌の鍛冶の抜け殻」)。前の灰炉の鍛冶の抜け殻。
//   振り下ろし  近く (REACH) に来ると、獲物の足もとに残り火の円 (半径 R) を置いて大槌を振りかぶり (TELL = 0.8 秒) → 叩きつける。
//               その円は ECHO 後にもう一度打たれる (Echoes)。打たれた所から離れれば当たらない
public class AshStriker extends CloudMob {
    public static final String ID = "ash_striker";
    public static final double HP = 90;
    public static final double DMG = 13;
    static final double REACH = 3.6;
    static final double R = 2.0;
    static final int TELL = 16;
    public static final int ECHO = 50;

    public AshStriker(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "slam", "hurt", "death");
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Slam());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    final class Slam extends Goal {
        private int t = -1;
        private int cooldown = 20;
        private int repath;
        private Vec3 spot = Vec3.ZERO;

        Slam() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity tg = getTarget();
            if (tg == null || !(level() instanceof ServerLevel sl)) {
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            if (t < 0) {
                getLookControl().setLookAt(tg, 30f, 30f);
                if (distanceTo(tg) <= REACH && cooldown == 0) {
                    t = 0;
                    spot = tg.position();
                    getNavigation().stop();
                    playAction("slam");
                    playSound(CloudMobsSetup.STRIKER_TELL.get(), 1.0f, 1.0f);
                } else if (--repath <= 0) {
                    repath = 8;
                    getNavigation().moveTo(tg, 0.95);
                }
                return;
            }
            t++;
            getNavigation().stop();
            getLookControl().setLookAt(spot.x, spot.y, spot.z);
            if (t < TELL && t % 3 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i / 16.0 * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.SMALL_FLAME, spot.x + Math.cos(a) * R, spot.y + 0.15, spot.z + Math.sin(a) * R, 1, 0, 0, 0, 0);
                }
            }
            if (t == TELL + 2) {
                playSound(CloudMobsSetup.STRIKER_SLAM.get(), 1.4f, 0.9f);
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, spot.x, spot.y + 0.2, spot.z, 6, R * 0.4, 0.1, R * 0.4, 0.01);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(REACH + R), e -> e.isAlive() && !e.isSpectator())) {
                    if (p.position().subtract(spot).horizontalDistance() <= R + 0.3 && Math.abs(p.getY() - spot.y) < 2) {
                        strike(p, dmg());
                    }
                }
                Echoes.circle(sl, spot, R, ECHO, dmg(0.8), AshStriker.this);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(10), e -> e.isAlive())) {
                    if (jp.revati.skyabyss.story.finale.FinaleHints.once(p, "echo_first")) {
                        Notice.narrate(p, "message.skyabyss.isle3.echo_first");
                    }
                }
            }
            if (t >= TELL + 10) {
                t = -1;
                cooldown = 50;
            }
        }

        @Override
        public void stop() {
            t = -1;
            getNavigation().stop();
        }
    }
}
