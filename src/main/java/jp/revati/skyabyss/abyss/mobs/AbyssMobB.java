package jp.revati.skyabyss.abyss.mobs;

import java.util.EnumSet;

import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 深淵の敵 B 組 (docs/design/36 の 4) の共通部分。GuardMob の決まり (番兵の型・動きの自動切り替え・playAction) を使う。
//   段 (stage) は湧いたときに決めて同期する。数値は tune で入れる (AbyssMobsBStage)。
//   保存しない (深淵は湧き直すので、ディスクに残さない)。遠くへ離れたら消える (湧き場の見張りが数える)。
//   予告つきの攻撃は Tell で作る: 攻撃の動作を先に見せ、予告のあいだに離れれば当たらない。
public abstract class AbyssMobB extends GuardMob {
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(AbyssMobB.class, EntityDataSerializers.INT);
    private double baseDmg;
    private int tunedY;

    protected AbyssMobB(EntityType<? extends Monster> type, Level level, String prefix, String... actions) {
        super(type, level, prefix, actions);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAGE, 0);
    }

    public int stage() {
        return entityData.get(STAGE);
    }

    // 湧いたときに一度だけ。段と HP・傷の基の値 (段と深さの倍率はここで決める)
    public void tune(int stage, double hp, double dmg) {
        tunedY = blockPosition().getY();
        entityData.set(STAGE, Math.max(0, Math.min(AbyssMobsBStage.MAX, stage)));
        double max = AbyssMobsBStage.hp(hp, stage(), tunedY);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
        baseDmg = dmg;
    }

    // 今の段と深さで効く傷 (傷を与える所で使う)
    protected float dmg() {
        return (float) AbyssMobsBStage.dmg(baseDmg, stage(), tunedY);
    }

    protected float dmg(double factor) {
        return (float) (AbyssMobsBStage.dmg(baseDmg, stage(), tunedY) * factor);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > 48;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            playAction("death");
        }
    }

    protected void strike(LivingEntity target, float amount) {
        target.hurt(attackSource(), amount);
    }

    // 予告つきの近接: 近づき、予告の動作 (action) を出し、tell tick のあとにまだ届く距離なら当てる。当てたあと cooldown の間は休む
    protected static final class Tell extends Goal {
        private final AbyssMobB mob;
        private final double speed;
        private final double range;
        private final int cooldownTicks;
        private final int tellTicks;
        private final String action;
        private final double factor;
        private int cooldown;
        private int wind = -1;
        private int repath;

        public Tell(AbyssMobB mob, double speed, double range, int tellTicks, int cooldownTicks, String action, double factor) {
            this.mob = mob;
            this.speed = speed;
            this.range = range;
            this.tellTicks = tellTicks;
            this.cooldownTicks = cooldownTicks;
            this.action = action;
            this.factor = factor;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive()) {
                wind = -1;
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            if (wind >= 0) {
                mob.getNavigation().stop();
                if (--wind < 0 && mob.distanceTo(t) <= range + 0.8) {
                    mob.strike(t, mob.dmg(factor));
                }
                if (wind < 0) {
                    cooldown = cooldownTicks;
                }
                return;
            }
            if (mob.distanceTo(t) <= range && cooldown == 0) {
                mob.getNavigation().stop();
                mob.playAction(action);
                wind = tellTicks;
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                mob.getNavigation().moveTo(t, speed);
            }
        }

        @Override
        public void stop() {
            wind = -1;
            mob.getNavigation().stop();
        }
    }
}
