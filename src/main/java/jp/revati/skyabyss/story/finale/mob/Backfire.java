package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.story.finale.Echoes;
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
import net.minecraft.world.phys.Vec3;

// 戻り火 (炉の島。docs/design/50 の 4.2.3・一品一案「火の籠の霊」)。炉から漏れた火の霊。足もとの地面の HOVER 上を漂い、獲物から 6〜9 を保つ。
//   火の玉  獲物の足もとに円 (半径 R、TELL = 0.8 秒) → 火の玉が落ちて燃える。その円は ECHO 後にもう一度燃える (Echoes)
public class Backfire extends CloudMob {
    public static final String ID = "backfire";
    public static final double HP = 40;
    public static final double DMG = 9;
    static final double HOVER = 3.5;
    static final double R = 1.8;
    static final int TELL = 16;
    public static final int ECHO = 50;

    public Backfire(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "throw", "hurt", "death");
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
        goalSelector.addGoal(1, new Throw());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
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
        double wantY = ground + HOVER + Math.sin(tickCount * 0.09) * 0.5;
        v = new Vec3(v.x, (wantY - getY()) * 0.08, v.z);
        if (tg != null && tg.isAlive()) {
            Vec3 to = tg.position().subtract(position());
            double d = to.horizontalDistance();
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            double pull = d > 9 ? 0.05 : d < 6 ? -0.05 : 0.0;
            v = v.add(dir.scale(pull)).add(side.scale(0.02));
            getLookControl().setLookAt(tg, 30f, 30f);
            setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
            yBodyRot = getYRot();
        }
        setDeltaMovement(v);
        hasImpulse = true;
        if (tickCount % 6 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.3, getZ(), 1, 0.15, 0.1, 0.15, 0.01);
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

    final class Throw extends Goal {
        private int t = -1;
        private int cooldown = 50;
        private Vec3 spot = Vec3.ZERO;

        Throw() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && distanceTo(tg) <= 14 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= TELL + 4 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            spot = getTarget().position();
            playAction("throw");
            playSound(CloudMobsSetup.BACKFIRE_TELL.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            if (t < TELL) {
                if (t % 3 == 0) {
                    for (int i = 0; i < 14; i++) {
                        double a = i / 14.0 * Math.PI * 2;
                        sl.sendParticles(ParticleTypes.SMALL_FLAME, spot.x + Math.cos(a) * R, spot.y + 0.15, spot.z + Math.sin(a) * R, 1, 0, 0, 0, 0);
                    }
                }
                return;
            }
            if (t == TELL) {
                playSound(CloudMobsSetup.BACKFIRE_THROW.get(), 1.2f, 1.0f);
                Vec3 from = position().add(0, 0.6, 0);
                for (int i = 0; i <= 10; i++) {
                    Vec3 q = from.lerp(spot, i / 10.0).add(0, Math.sin(i / 10.0 * Math.PI) * 1.5, 0);
                    sl.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0.01);
                }
                sl.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.3, spot.z, 20, R * 0.5, 0.2, R * 0.5, 0.03);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(R + 0.5, 2, R + 0.5),
                        e -> e.isAlive() && !e.isSpectator())) {
                    if (p.position().subtract(spot).horizontalDistance() <= R + 0.3) {
                        strike(p, dmg());
                    }
                }
                Echoes.circle(sl, spot, R, ECHO, dmg(0.8), Backfire.this);
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 70;
        }
    }
}
