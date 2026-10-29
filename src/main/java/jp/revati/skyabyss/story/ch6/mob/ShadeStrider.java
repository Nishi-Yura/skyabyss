package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.story.ch6.field.FieldTide;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 影の竹馬 (満月の野。docs/design/46 の 4.5・一品一案「竹馬の影」)。小さな影の胴を四本の長い黒硝子の脚が持ち上げる歩き手 (高さ 3)。
//   引き  歩いて寄り、高い所から前脚で踏みつける (獲物の足もとに円 1 秒の予告、半径 1.8)
//   満ち  胴を地面まで下ろし、脚を胴のまわりに縦に揃えて影の石の像になる (動かない・傷 ×0.2)。像のあいだに裏へ回って、引きを待つ
public class ShadeStrider extends MoonMob {
    public static final String ID = "shade_strider";
    public static final double HP = 44;
    public static final double DMG = 8;
    static final int TELL = 20;
    static final double STOMP_R = 1.8;
    private boolean statue;

    public ShadeStrider(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "stomp", "fold", "unfold", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Stomp());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean statue() {
        return statue;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, statue ? amount * 0.2f : amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        boolean lit = FieldTide.lit(sl, blockPosition());
        if (lit != statue) {
            statue = lit;
            playAction(statue ? "fold" : "unfold");
            playSound(MoonMobsSetup.STRIDER_FOLD.get(), 1.0f, statue ? 0.7f : 1.1f);
            getNavigation().stop();
        }
        if (statue) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
    }

    // 踏みつけ (引きのあいだ。獲物が 4.5 の内。円の予告 1 秒)
    final class Stomp extends Goal {
        private int t = -1;
        private int cooldown;
        private int repath;
        private Vec3 at = Vec3.ZERO;

        Stomp() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            return !statue && tg != null && tg.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || t >= 0 && !statue;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity tg = getTarget();
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            if (t >= 0) {
                t++;
                getNavigation().stop();
                if (t % 2 == 0) {
                    for (int i = 0; i < 14; i++) {
                        double a = i / 14.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.SQUID_INK, at.x + Math.cos(a) * STOMP_R, at.y + 0.1, at.z + Math.sin(a) * STOMP_R, 1, 0, 0, 0, 0);
                    }
                }
                if (t == TELL) {
                    playSound(MoonMobsSetup.STRIDER_STOMP.get(), 1.4f, 0.8f);
                    sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 14, 0.8, 0.1, 0.8, 0.05);
                    for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(STOMP_R + 0.5, 2.5, STOMP_R + 0.5), e -> e.isAlive())) {
                        if (p.position().subtract(at).horizontalDistance() <= STOMP_R + 0.3) {
                            strike(p, dmg(1.3));
                        }
                    }
                }
                if (t >= TELL + 8) {
                    t = -1;
                    cooldown = 50;
                }
                return;
            }
            if (tg == null) {
                return;
            }
            getLookControl().setLookAt(tg, 30f, 30f);
            if (distanceTo(tg) <= 4.5 && cooldown == 0) {
                t = 0;
                at = tg.position();
                playAction("stomp");
                getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(tg, 1.0);
            }
        }

        @Override
        public void stop() {
            t = -1;
            getNavigation().stop();
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.SHADOW_THREAD.get(), 0.35, 1, 1);
    }
}
