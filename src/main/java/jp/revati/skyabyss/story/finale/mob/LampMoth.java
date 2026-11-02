package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.finale.LampIsle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;

// 灯蛾 (灯の島。docs/design/50 の 4.2.2・一品一案「目玉の紋の白金の蛾」)。灯の光から生まれる蛾。ふわふわと獲物の胸の高さへ寄る。
//   灯す  近く (2.5) に来ると強く光り (TELL = 1 秒。翅を震わせる)、そこから突っ込む。触れられた人は LIT のあいだ「灯される」
//         (陰に入っても光の筋に見つかる。LampIsle.light) と小さな傷
public class LampMoth extends CloudMob {
    public static final String ID = "lamp_moth";
    public static final double HP = 34;
    public static final double DMG = 6;
    static final int TELL = 20;
    public static final int LIT = 120;
    private int flaring;

    public LampMoth(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "flare", "hurt", "death");
        this.xpReward = 10;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Flare());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    // 灯された (自己テストからも)
    public static void mark(ServerPlayer p) {
        LampIsle.light(p, LIT);
        p.serverLevel().playSound(null, p.blockPosition(), CloudMobsSetup.MOTH_MARK.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.2f);
        if (jp.revati.skyabyss.story.finale.FinaleHints.once(p, "lamp_marked")) {
            Notice.narrate(p, "message.skyabyss.isle2.marked");
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || flaring > 0) {
            return;
        }
        // 漂う: 獲物の胸の高さへ、ゆらゆらと寄る (いなければ その場で上下)
        LivingEntity tg = getTarget();
        Vec3 v = getDeltaMovement().scale(0.8);
        if (tg != null && tg.isAlive()) {
            Vec3 want = tg.position().add(Math.sin(tickCount * 0.11) * 1.5, 1.4 + Math.sin(tickCount * 0.17) * 0.5, Math.cos(tickCount * 0.11) * 1.5);
            Vec3 to = want.subtract(position());
            double d = to.length();
            v = v.add(to.normalize().scale(Math.min(0.045, d * 0.02)));
            getLookControl().setLookAt(tg, 30f, 30f);
        } else {
            v = new Vec3(v.x, Math.sin(tickCount * 0.1) * 0.03, v.z);
        }
        if (v.horizontalDistanceSqr() > 1.0e-4) {
            setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
            yBodyRot = getYRot();
        }
        setDeltaMovement(v);
        hasImpulse = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (flaring > 0) {
            flaring--;
        }
    }

    final class Flare extends Goal {
        private int t = -1;
        private int cooldown = 40;
        private boolean done;

        Flare() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg instanceof ServerPlayer && tg.isAlive() && distanceTo(tg) <= 2.8;
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
            done = false;
            playAction("flare");
            playSound(CloudMobsSetup.MOTH_FLARE.get(), 1.0f, 1.2f);
        }

        @Override
        public void tick() {
            t++;
            flaring = 2;
            if (!(level() instanceof ServerLevel sl) || !(getTarget() instanceof ServerPlayer tg)) {
                return;
            }
            getLookControl().setLookAt(tg, 30f, 30f);
            if (t < TELL) {
                setDeltaMovement(getDeltaMovement().scale(0.5));
                if (t % 2 == 0) {
                    sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 3, 0.4, 0.3, 0.4, 0.02);
                }
                return;
            }
            Vec3 to = tg.getEyePosition().add(0, -0.4, 0).subtract(position());
            setDeltaMovement(to.normalize().scale(0.6));
            hasImpulse = true;
            if (!done && distanceTo(tg) <= 1.6) {
                done = true;
                strike(tg, dmg(0.5));
                mark(tg);
                sl.sendParticles(ParticleTypes.FLASH, tg.getX(), tg.getY() + 1.2, tg.getZ(), 1, 0, 0, 0, 0);
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 80;
            setDeltaMovement(getDeltaMovement().scale(-0.5).add(0, 0.2, 0));
        }
    }
}
