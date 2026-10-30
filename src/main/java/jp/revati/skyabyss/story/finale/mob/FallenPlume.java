package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 落ち羽 (約束の島。docs/design/50 の 4.2.4・一品一案「浮かぶ大きな羽」)。巡礼者の翼から抜け落ちた羽。足もとの地面の HOVER 上を漂い、獲物から 7〜10 を保つ。
//   羽根の矢  獲物へ向けて地面に光の筋 (長さ LEN・幅 HALF×2、TELL = 1 秒) → 筋に沿って羽根の矢が走る。筋から横へ出れば当たらない
public class FallenPlume extends CloudMob {
    public static final String ID = "fallen_plume";
    public static final double HP = 44;
    public static final double DMG = 9;
    static final double HOVER = 2.6;
    static final double LEN = 12;
    static final double HALF = 0.8;
    static final int TELL = 20;

    public FallenPlume(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "volley", "hurt", "death");
        this.xpReward = 11;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 26)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Volley());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        LivingEntity tg = getTarget();
        Vec3 v = getDeltaMovement().scale(0.85);
        double ground = groundBelow(tg != null ? tg.blockPosition() : blockPosition());
        double wantY = ground + HOVER + Math.sin(tickCount * 0.07) * 0.4;
        v = new Vec3(v.x, (wantY - getY()) * 0.08, v.z);
        if (tg != null && tg.isAlive()) {
            Vec3 to = tg.position().subtract(position());
            double d = to.horizontalDistance();
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            double pull = d > 10 ? 0.05 : d < 7 ? -0.05 : 0.0;
            v = v.add(dir.scale(pull)).add(side.scale(0.015));
            getLookControl().setLookAt(tg, 30f, 30f);
            setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
            yBodyRot = getYRot();
        }
        setDeltaMovement(v);
        hasImpulse = true;
        if (tickCount % 8 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.4, getZ(), 1, 0.1, 0.4, 0.1, 0.005);
        }
    }

    private double groundBelow(BlockPos from) {
        BlockPos.MutableBlockPos m = from.mutable();
        for (int i = 0; i < 16; i++) {
            if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) {
                return m.getY() + 1;
            }
            m.move(0, -1, 0);
        }
        return getY() - HOVER;
    }

    // 点 p が筋 (from から dir へ LEN、幅 HALF) の内か
    public static boolean onLine(Vec3 from, Vec3 dir, Vec3 p) {
        Vec3 to = new Vec3(p.x - from.x, 0, p.z - from.z);
        double along = to.dot(dir);
        if (along < 0 || along > LEN) {
            return false;
        }
        return to.subtract(dir.scale(along)).length() <= HALF + 0.3 && Math.abs(p.y - from.y) < 2.5;
    }

    final class Volley extends Goal {
        private int t = -1;
        private int cooldown = 40;
        private Vec3 from = Vec3.ZERO;
        private Vec3 dir = new Vec3(0, 0, 1);

        Volley() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && distanceTo(tg) <= LEN && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= TELL + 6 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            LivingEntity tg = getTarget();
            Vec3 to = new Vec3(tg.getX() - getX(), 0, tg.getZ() - getZ());
            dir = to.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : to.normalize();
            // 筋は獲物の足の高さ、羽の真下から
            from = new Vec3(getX(), tg.getY(), getZ());
            playAction("volley");
            playSound(CloudMobsSetup.PLUME_TELL.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
            if (t < TELL) {
                if (t % 3 == 0) {
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    for (double d = 0.5; d <= LEN; d += 0.9) {
                        for (int s = -1; s <= 1; s += 2) {
                            Vec3 q = from.add(dir.scale(d)).add(side.scale(s * HALF));
                            sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y + 0.12, q.z, 1, 0, 0, 0, 0);
                        }
                    }
                }
                return;
            }
            if (t == TELL) {
                playSound(CloudMobsSetup.PLUME_VOLLEY.get(), 1.2f, 1.0f);
                Vec3 top = position().add(0, 1.2, 0);
                for (double d = 0; d <= LEN; d += 0.5) {
                    Vec3 q = top.lerp(from.add(dir.scale(LEN)), d / LEN);
                    sl.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.01);
                    sl.sendParticles(ParticleTypes.CRIT, from.x + dir.x * d, from.y + 0.4, from.z + dir.z * d, 1, 0.1, 0.1, 0.1, 0.02);
                }
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(from, from.add(dir.scale(LEN))).inflate(1.5, 3, 1.5),
                        e -> e.isAlive() && !e.isSpectator())) {
                    if (onLine(from, dir, p.position())) {
                        strike(p, dmg());
                    }
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 60;
        }
    }
}
