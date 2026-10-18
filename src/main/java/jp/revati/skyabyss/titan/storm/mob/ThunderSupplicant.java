package jp.revati.skyabyss.titan.storm.mob;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.EnumSet;

import jp.revati.skyabyss.titan.storm.back.StormStrike;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 雷乞い (12 の 5.1節): 今も雷を乞い続ける風の民の人影。鳥籠の頭の中に稲妻が閉じこめられている (ラフ C)。杖は別モデル (socket_main)。
// 8〜14 ブロックの間合いを保ち、杖を頭上へ掲げて (予告 0.35 秒)、人の足もとに招雷の輪を呼ぶ (StormStrike: 輪 1.2 秒、半径 1.5、9 と 0.5 秒の鈍足)。
// 輪が避雷の柱から 3 ブロック以内なら、雷は柱へ落ちる (東の試練で柱を満たせる。判定は StormStrike / StormRods)。
// 近づかれると杖で突く (4)。凪・嵐を問わず同じ
public class ThunderSupplicant extends StormMob {
    public static final float HEALTH = 32;
    public static final float DAMAGE = 4;
    public static final float ARMOR = 4;
    public static final float CALL_DAMAGE = 9;
    public static final int RING_TICKS = 24;
    public static final double KEEP_MIN = 8;
    public static final double KEEP_MAX = 14;
    static final int CAST_WINDUP = 7;
    static final int CAST_HOLD = 18;
    static final int CAST_COOLDOWN = 70;
    static final int JAB_WINDUP = 6;
    static final int JAB_COOLDOWN = 25;
    static final double JAB_RANGE = 2.4;

    // 自己テスト用: 最後に輪を呼んだ所
    Vec3 lastCall;

    public ThunderSupplicant(EntityType<? extends Monster> type, Level level) {
        super(type, level, "thunder_supplicant", "cast", "attack");
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CallGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 14f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 招雷: 相手の今の足もとに輪を呼ぶ (サーバー)
    void callAt(LivingEntity target) {
        if (level() instanceof ServerLevel server) {
            lastCall = target.position();
            StormStrike.call(server, lastCall, RING_TICKS, CALL_DAMAGE, this);
        }
    }

    private static final class CallGoal extends Goal {
        private final ThunderSupplicant mob;
        private int castWindup = -1;
        private int castHold;
        private int castCooldown = 30;
        private int jabWindup = -1;
        private int jabCooldown;
        private int repath;

        CallGoal(ThunderSupplicant mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return fair(mob.getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || castWindup >= 0 || castHold > 0;
        }

        @Override
        public void stop() {
            castWindup = -1;
            jabWindup = -1;
            castHold = 0;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (castCooldown > 0) {
                castCooldown--;
            }
            if (jabCooldown > 0) {
                jabCooldown--;
            }
            if (t != null) {
                mob.getLookControl().setLookAt(t, 30f, 30f);
            }
            // 杖を掲げている間 (予告): 止まって、杖の先に火花
            if (castWindup >= 0) {
                mob.getNavigation().stop();
                if (t != null) {
                    mob.face(t, 20f);
                }
                if (mob.level() instanceof ServerLevel server) {
                    mob.warn(server, ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + 3.0, mob.getZ(), 3, 0.15, 0.15, 0.15, 0.05);
                }
                if (--castWindup <= 0) {
                    castWindup = -1;
                    castHold = CAST_HOLD;
                    if (t != null && fair(t)) {
                        mob.callAt(t);
                    }
                }
                return;
            }
            if (castHold > 0) {
                castHold--;
                mob.getNavigation().stop();
                return;
            }
            if (jabWindup >= 0) {
                mob.getNavigation().stop();
                if (t != null) {
                    mob.face(t, 20f);
                }
                if (--jabWindup <= 0) {
                    jabWindup = -1;
                    jabCooldown = JAB_COOLDOWN;
                    if (t != null && fair(t) && mob.distanceTo(t) <= JAB_RANGE + 0.6 && mob.inFront(t, 45)) {
                        mob.doHurtTarget(t);
                    }
                }
                return;
            }
            if (t == null) {
                return;
            }
            double d = mob.distanceTo(t);
            boolean see = mob.hasLineOfSight(t);
            if (d <= JAB_RANGE && jabCooldown == 0) {
                jabWindup = JAB_WINDUP;
                mob.playAction("attack");
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), StormMobSetup.SUPPLICANT_SWING.get(), SoundSource.HOSTILE, 1f, 1f);
                return;
            }
            if (castCooldown == 0 && d <= KEEP_MAX + 4 && see) {
                castWindup = CAST_WINDUP;
                castCooldown = CAST_COOLDOWN + mob.getRandom().nextInt(30);
                mob.playAction("cast");
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), StormMobSetup.SUPPLICANT_RAISE.get(), SoundSource.HOSTILE, 1.2f, 1f);
                return;
            }
            // 間合い: 近ければ離れ、遠ければ寄る。間合いの中なら止まる
            if (--repath > 0) {
                return;
            }
            repath = 10;
            if (d < KEEP_MIN) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 12, 5, t.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
                }
            } else if (d > KEEP_MAX || !see) {
                mob.getNavigation().moveTo(t, 1.0);
            } else {
                mob.getNavigation().stop();
                mob.face(t, 30f);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return StormMobSetup.SUPPLICANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return StormMobSetup.SUPPLICANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return StormMobSetup.SUPPLICANT_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.THUNDER_SUPPLICANT_STEP.get(), 0.2f);
    }
}
