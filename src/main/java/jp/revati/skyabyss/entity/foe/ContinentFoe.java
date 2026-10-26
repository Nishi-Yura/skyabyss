package jp.revati.skyabyss.entity.foe;

import java.util.EnumSet;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.entity.GroundFoe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 大陸の新しい敵 (docs/design/44、tools/gen/foes/gen_foes_one.py) の共通部分。
// 体力・攻撃の素の値は岩鯨の土地 (ContinentDanger の 1.0) に合わせ、湧いた土地の強さが掛かる (島 0.45・草原 0.55 …)。
// 被弾・倒れの動きは GuardMob が流す (構えの途中は崩さない)。技は act (1 から数える tick) と Telegraph で書く
public abstract class ContinentFoe extends GroundFoe {
    private final Supplier<SoundEvent> ambient;
    private final Supplier<SoundEvent> hurtSound;
    private final Supplier<SoundEvent> deathSound;
    // 技を終えてから離れる残りの tick (離れる間は busy、近づかない)
    protected int retreat;

    protected ContinentFoe(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient,
            Supplier<SoundEvent> hurt, Supplier<SoundEvent> death, String... actions) {
        super(type, level, animPrefix, actions);
        this.ambient = ambient;
        this.hurtSound = hurt;
        this.deathSound = death;
        this.xpReward = 0;
    }

    @Override
    protected boolean autoHurtDeath() {
        return true;
    }

    @Override
    public boolean busy() {
        return super.busy() || retreat > 0;
    }

    // ふつうの組み立て: 近づく (止まる距離) か距離をとる goal を呼ぶ側で足し、さまよう・見る・狙うは共通
    protected void baseGoals(Goal move) {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, move);
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (cooldown > 0) {
            cooldown--;
        }
        if (retreat > 0) {
            retreat--;
            LivingEntity t = getTarget();
            if (t != null && retreat % 10 == 9) {
                Vec3 away = awayPoint(this, t.position(), 6);
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                }
            }
            if (retreat == 0) {
                getNavigation().stop();
            }
        }
        if (act == 0) {
            LivingEntity target = getTarget();
            if (retreat == 0 && target != null && target.isAlive() && cooldown == 0 && onGround() && hasLineOfSight(target)) {
                choose(target, distanceTo(target));
            }
            return;
        }
        act++;
        holdYaw();
        during(act);
        if (act >= length()) {
            act = 0;
            finish();
        }
    }

    // 技を選んで始める (act = 1 にする)。始めないなら何もしない
    protected abstract void choose(LivingEntity target, double distance);

    // 技の最中の毎 tick (向きは固定済み)
    protected void during(int tick) {
    }

    // いま動いている技の長さ (tick)
    protected abstract int length();

    // 技が終わったとき (cooldown・retreat を決める)
    protected abstract void finish();

    protected float damage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    protected void begin(LivingEntity target, String action) {
        act = 1;
        lockYawToward(target);
        holdYaw();
        playAction(action);
    }

    // 撃つ点から人まで壁がない (遮蔽の裏には通らない)
    protected boolean clearShot(ServerPlayer player) {
        if (!canHit(player)) {
            return false;
        }
        Vec3 from = new Vec3(getX(), getEyeY(), getZ());
        Vec3 to = new Vec3(player.getX(), player.getY() + 1.0, player.getZ());
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    protected ServerLevel server() {
        return (ServerLevel) level();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
        }
    }

    // 何も落とさない (経験値は GrowthRewards、鐘銭は野営の頭だけ)
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ambient.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return hurtSound.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return deathSound.get();
    }

    // 距離をとる: 近すぎれば離れ、遠ければ寄る。技の最中・離れる間は動かない (弓手・弓兵)
    public static final class KeepDistanceGoal extends Goal {
        private final ContinentFoe mob;
        private final double min;
        private final double max;
        private int recalc;

        public KeepDistanceGoal(ContinentFoe mob, double min, double max) {
            this.mob = mob;
            this.min = min;
            this.max = max;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.busy();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            if (--recalc > 0) {
                return;
            }
            recalc = 6;
            double d = mob.distanceTo(t);
            if (d < min) {
                Vec3 away = awayPoint(mob, t.position(), 7);
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
                }
            } else if (d > max || !mob.hasLineOfSight(t)) {
                mob.getNavigation().moveTo(t, 1.0);
            } else {
                mob.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }
}
