package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 霞鳥 (鐘の島。docs/design/50 の 4.2.1・一品一案「白い霞の鳥」)。雲から生まれる白い鳥。足もとの地面の HOVER 上を漂う。
//   獲物の上を回り、4 秒ごとに急降下: 獲物の足もとに影の円 (TELL = 0.8 秒) → その円へ突っ込む。円の内にいた人に傷と強い押し戻し
//   (雲の縁から落とされる)。円から出れば当たらない
public class MistBird extends CloudMob {
    public static final String ID = "mist_bird";
    public static final double HP = 46;
    public static final double DMG = 9;
    static final double HOVER = 6.0;
    static final int TELL = 16;
    static final int DIVE = 14;
    static final double CIRCLE = 1.8;
    static final double PUSH = 1.6;
    private static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.1f, 0.1f, 0.14f), 1.4f);
    private int diving;

    public MistBird(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "dive", "hurt", "death");
        this.xpReward = 12;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Dive());
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
        if (level().isClientSide || diving > 0) {
            return;
        }
        // 漂う: 獲物 (いなければ自分) の足もとの地面から HOVER の高さ、獲物のまわり 6 を回る
        LivingEntity tg = getTarget();
        Vec3 v = getDeltaMovement().scale(0.85);
        double ground = groundBelow(tg != null ? tg.blockPosition() : blockPosition());
        double wantY = ground + HOVER + Math.sin(tickCount * 0.07) * 0.8;
        v = new Vec3(v.x, (wantY - getY()) * 0.08, v.z);
        if (tg != null && tg.isAlive()) {
            Vec3 to = tg.position().subtract(position());
            double d = to.horizontalDistance();
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            double pull = d > 7 ? 0.05 : d < 5 ? -0.04 : 0.0;
            v = v.add(dir.scale(pull)).add(side.scale(0.04));
            getLookControl().setLookAt(tg, 30f, 30f);
        }
        if (v.horizontalDistanceSqr() > 1.0e-4) {
            setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
            yBodyRot = getYRot();
        }
        setDeltaMovement(v);
        hasImpulse = true;
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

    @Override
    public void tick() {
        super.tick();
        if (diving > 0) {
            diving--;
        }
    }

    // 急降下 (獲物が 3〜16 の内。4 秒に一度)
    final class Dive extends Goal {
        private int t = -1;
        private int cooldown = 60;
        private Vec3 from = Vec3.ZERO;
        private Vec3 spot = Vec3.ZERO;
        private final Set<Integer> hit = new HashSet<>();

        Dive() {
            setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && tg.onGround() && distanceTo(tg) >= 3 && distanceTo(tg) <= 16 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= TELL + DIVE && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            hit.clear();
            LivingEntity tg = getTarget();
            from = position();
            spot = tg == null ? from : tg.position();
            playAction("dive");
            playSound(CloudMobsSetup.BIRD_TELL.get(), 1.0f, 1.1f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            if (t <= TELL) {
                // 落ちる所に影の円
                if (t % 2 == 0) {
                    for (int i = 0; i < 18; i++) {
                        double a = i / 18.0 * Math.PI * 2;
                        sl.sendParticles(SHADOW, spot.x + Math.cos(a) * CIRCLE, spot.y + 0.1, spot.z + Math.sin(a) * CIRCLE, 1, 0, 0, 0, 0);
                    }
                    sl.sendParticles(SHADOW, spot.x, spot.y + 0.1, spot.z, 4, CIRCLE * 0.4, 0, CIRCLE * 0.4, 0);
                }
                setDeltaMovement(Vec3.ZERO);
                getLookControl().setLookAt(spot.x, spot.y, spot.z);
                return;
            }
            if (t == TELL + 1) {
                playSound(CloudMobsSetup.BIRD_DIVE.get(), 1.2f, 1.0f);
            }
            diving = 2;
            Vec3 to = spot.add(0, 0.4, 0).subtract(position());
            double left = DIVE - (t - TELL - 1);
            setDeltaMovement(to.scale(1.0 / Math.max(1.0, left)));
            hasImpulse = true;
            if (to.length() < 1.2 || t == TELL + DIVE) {
                // 円に着いた: 円の内の人に傷と押し戻し
                sl.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.3, spot.z, 18, CIRCLE * 0.5, 0.2, CIRCLE * 0.5, 0.05);
                for (Player p : sl.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(CIRCLE + 0.3, 1.6, CIRCLE + 0.3),
                        e -> e.isAlive() && !e.isSpectator())) {
                    if (p.position().subtract(spot).horizontalDistance() <= CIRCLE + 0.3 && hit.add(p.getId())) {
                        strike(p, dmg());
                        Vec3 away = p.position().subtract(from);
                        away = new Vec3(away.x, 0, away.z).normalize();
                        p.push(away.x * PUSH, 0.45, away.z * PUSH);
                        p.hurtMarked = true;
                    }
                }
                t = TELL + DIVE + 1;
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 80;
            diving = 0;
            setDeltaMovement(getDeltaMovement().scale(0.2).add(0, 0.4, 0));
        }
    }
}
