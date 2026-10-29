package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.story.ch6.field.FieldTide;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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

// 鏡背負い (満月の野。docs/design/46 の 4.5・一品一案「鏡の皿の亀」)。背に光を集める丸い銀の鏡を負う月の亀。
//   満ち  5〜16 の獲物へ鏡を傾けて光を集め (1 秒の線の予告。向きは予告の頭で決まる)、光の線を放つ。予告のあいだに背から叩くと止まる。
//         近くは噛む (予告つき)
//   引き  頭と脚を殻に引いてこもる (動かない・前からの傷 ×0.3)
public class MirrorBearer extends MoonMob {
    public static final String ID = "mirror_bearer";
    public static final double HP = 52;
    public static final double DMG = 7;
    static final int TELL = 20;
    static final double BEAM = 16;
    private boolean hidden;
    private int aim = -1;
    private Vec3 beamFrom = Vec3.ZERO;
    private Vec3 beamDir = Vec3.ZERO;

    public MirrorBearer(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "aim", "beam", "attack", "hide", "unhide", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Beam());
        goalSelector.addGoal(2, new Tell(this, 0.9, 2.4, 12, 30, "attack", 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean hidden() {
        return hidden;
    }

    public boolean aiming() {
        return aim >= 0;
    }

    // 叩いた者が前 (鏡と頭の側) にいるか
    boolean fromFront(DamageSource source) {
        Entity a = source.getEntity();
        if (a == null) {
            return false;
        }
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        Vec3 to = a.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        return flat.lengthSqr() > 1e-4 && flat.normalize().dot(fwd) > 0.2;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean front = fromFront(source);
        if (aim >= 0 && !front && source.getEntity() != null) {
            // 予告のあいだに背から叩かれた: 光が散る
            aim = -1;
            playSound(MoonMobsSetup.BEARER_BREAK.get(), 1.0f, 1.2f);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.6, getZ(), 16, 0.6, 0.4, 0.6, 0.1);
            }
        }
        return super.hurt(source, hidden && front ? amount * 0.3f : amount);
    }

    // こもっているあいだは噛まない
    @Override
    protected void strike(LivingEntity target, float amount) {
        if (!hidden) {
            super.strike(target, amount);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        boolean lit = FieldTide.lit(sl, blockPosition());
        if (lit == hidden) {
            hidden = !lit;
            aim = -1;
            playAction(hidden ? "hide" : "unhide");
            playSound(MoonMobsSetup.BEARER_HIDE.get(), 0.8f, hidden ? 0.8f : 1.1f);
        }
        if (hidden) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
    }

    // 光の線 (満ちのあいだ、獲物が 5〜16 の内で見えるとき。4 秒に一度)
    final class Beam extends Goal {
        private int cooldown = 40;

        Beam() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return !hidden && tg != null && tg.isAlive() && distanceTo(tg) >= 5 && distanceTo(tg) <= BEAM && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return aim >= 0 && !hidden && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity tg = getTarget();
            aim = 0;
            beamFrom = position().add(0, 1.4, 0);
            Vec3 to = tg == null ? beamFrom.add(0, 0, -1) : tg.position().add(0, 1.0, 0);
            beamDir = to.subtract(beamFrom).normalize();
            getNavigation().stop();
            playAction("aim");
            playSound(MoonMobsSetup.BEARER_AIM.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel sl) || aim < 0) {
                return;
            }
            aim++;
            getNavigation().stop();
            getLookControl().setLookAt(beamFrom.add(beamDir.scale(4)));
            if (aim < TELL && aim % 2 == 0) {
                for (int i = 1; i <= BEAM; i++) {
                    Vec3 p = beamFrom.add(beamDir.scale(i));
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
            if (aim == TELL) {
                playAction("beam");
                playSound(MoonMobsSetup.BEARER_BEAM.get(), 1.4f, 1.0f);
                for (int i = 1; i <= BEAM * 2; i++) {
                    Vec3 p = beamFrom.add(beamDir.scale(i * 0.5));
                    sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
                }
                for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(BEAM + 1), e -> e.isAlive() && !e.isSpectator())) {
                    Vec3 c = p.position().add(0, 0.9, 0).subtract(beamFrom);
                    double along = c.dot(beamDir);
                    if (along < 0 || along > BEAM) {
                        continue;
                    }
                    double off = c.subtract(beamDir.scale(along)).length();
                    if (off <= 0.9) {
                        strike(p, dmg(1.3));
                    }
                }
            }
            if (aim >= TELL + 6) {
                aim = -1;
            }
        }

        @Override
        public void stop() {
            aim = -1;
            cooldown = 80;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.CRESCENT_SHELL.get(), 0.35, 1, 1);
    }
}
