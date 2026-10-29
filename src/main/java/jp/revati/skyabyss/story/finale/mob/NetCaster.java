package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

// 網打ち (灯の島。docs/design/50 の 4.2.2・一品一案「網を垂らす漁師の抜け殻」)。前の浜の漁師の抜け殻。
//   打つ   近くでは網の縁で殴る (予告 0.35 秒)
//   投げる 獲物が 3〜7 にいて見えていれば、網を頭上で振り回し (TELL = 0.8 秒) → 前の扇 (CONE 度・RANGE まで) へ投げる。
//          当たった人は ROOT のあいだその場から動けない (光の筋の中なら、そのまま焼かれる)
public class NetCaster extends CloudMob {
    public static final String ID = "net_caster";
    public static final double HP = 82;
    public static final double DMG = 12;
    static final int TELL = 16;
    static final double RANGE = 6.5;
    static final double CONE = 70;
    public static final int ROOT = 30;
    private static boolean told;

    public NetCaster(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "cast", "strike", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Cast());
        goalSelector.addGoal(2, new Tell(this, 0.9, 2.2, 7, 30, "strike", 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 網を投げられた (自己テストからも)
    public static void root(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT, 7, false, true, true));
        if (!told) {
            told = true;
            Notice.narrate(p, "message.skyabyss.isle2.netted");
        }
    }

    final class Cast extends Goal {
        private int t = -1;
        private int cooldown = 60;
        private Vec3 fwd = Vec3.ZERO;

        Cast() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && distanceTo(tg) >= 3 && distanceTo(tg) <= RANGE + 0.5 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= TELL + 8 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            playAction("cast");
            playSound(CloudMobsSetup.CASTER_TELL.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            getNavigation().stop();
            LivingEntity tg = getTarget();
            if (t < TELL && tg != null) {
                getLookControl().setLookAt(tg, 30f, 30f);
                fwd = new Vec3(tg.getX() - getX(), 0, tg.getZ() - getZ()).normalize();
            }
            if (t < TELL && t % 3 == 0) {
                // 投げる扇の縁に濡れた光の粒 (予告)
                for (int i = -3; i <= 3; i++) {
                    Vec3 d = fwd.yRot((float) Math.toRadians(i * CONE / 6.0));
                    Vec3 p = position().add(d.scale(RANGE));
                    sl.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y + 0.3, p.z, 1, 0, 0, 0, 0);
                }
            }
            if (t == TELL + 2) {
                playSound(CloudMobsSetup.CASTER_CAST.get(), 1.2f, 1.0f);
                for (double d = 1; d <= RANGE; d += 1) {
                    Vec3 p = position().add(fwd.scale(d));
                    sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.6, p.z, 6, d * 0.25, 0.2, d * 0.25, 0.1);
                }
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(RANGE + 0.5), e -> e.isAlive() && !e.isSpectator())) {
                    Vec3 to = p.position().subtract(position());
                    Vec3 flat = new Vec3(to.x, 0, to.z);
                    if (flat.length() <= RANGE + 0.3 && flat.normalize().dot(fwd) > Math.cos(Math.toRadians(CONE / 2))) {
                        strike(p, dmg(0.6));
                        root(p);
                    }
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 100;
        }
    }
}
