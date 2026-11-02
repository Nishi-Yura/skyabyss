package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 迷い灯 (docs/design/44): 島と草原の夜に浮く小物。地面から 1 ブロックほどの高さを漂い、人に寄って来る。
// 近づくと芯が膨らんで明滅し (予告 0.6 秒、まわりの円)、円の中の人の視界を 3.5 秒暗くする。弱く、二、三回で消える
public class StrayWisp extends ContinentFoe {
    public static final int WARN = 12;
    public static final double RADIUS = 3.2;
    public static final double START_RANGE = 3.0;
    public static final int END = WARN + 8;
    public static final double HOVER = 0.9;
    public static final double SPEED = 0.07;
    private Vec3 wander;
    private int wanderFor;

    public StrayWisp(EntityType<? extends Monster> type, Level level) {
        super(type, level, "stray_wisp", MobSounds.STRAY_WISP_AMBIENT, MobSounds.STRAY_WISP_HURT, MobSounds.STRAY_WISP_DEATH, "pulse", "hurt", "death");
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 18);
    }

    // 歩かない (customServerAiStep で漂う)。狙うだけ
    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (act > 0 || isDeadOrDying()) {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            return;
        }
        LivingEntity t = getTarget();
        Vec3 goal;
        if (t != null && t.isAlive()) {
            goal = distanceTo(t) > START_RANGE - 0.8 ? t.position() : null;
            getLookControl().setLookAt(t, 30f, 30f);
        } else {
            if (--wanderFor <= 0 || wander == null) {
                wanderFor = 60 + random.nextInt(80);
                wander = position().add((random.nextDouble() - 0.5) * 12, 0, (random.nextDouble() - 0.5) * 12);
            }
            goal = wander;
        }
        // 高さ: 足もとの地面 + HOVER に、ゆっくり上下する
        BlockPos column = blockPosition();
        double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        double wantY = Math.max(ground, (t != null ? t.getY() : ground)) + HOVER + Math.sin(tickCount * 0.08) * 0.25;
        double vx = 0;
        double vz = 0;
        if (goal != null) {
            Vec3 d = goal.subtract(position()).multiply(1, 0, 1);
            double len = d.length();
            if (len > 0.3) {
                vx = d.x / len * SPEED;
                vz = d.z / len * SPEED;
            }
        }
        double vy = Math.max(-0.08, Math.min(0.08, (wantY - getY()) * 0.1));
        setDeltaMovement(vx, vy, vz);
        if (vx * vx + vz * vz > 1.0E-4) {
            float yaw = (float) (Math.atan2(vz, vx) * 57.29577951308232) - 90f;
            setYRot(yaw);
            setYBodyRot(yaw);
        }
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
    }

    // 地面に立たないので onGround の条件を外し、ここで技を選ぶ
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && act == 0 && cooldown == 0) {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive() && distanceTo(t) <= START_RANGE && hasLineOfSight(t)) {
                begin(t, "pulse");
                double floor = t.getY();
                Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), RADIUS))
                        .floor(floor).warn(WARN).danger().owner(this).target(t)
                        .startSound(MobSounds.STRAY_WISP_PULSE.get(), 0.9f, 1f)
                        .victims(this::canHit)
                        .onImpact(server -> {
                            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.1, getZ(), 24, 0.6, 0.6, 0.6, 0.06);
                            server.playSound(null, getX(), getY(), getZ(), MobSounds.STRAY_WISP_PULSE.get(), SoundSource.HOSTILE, 1f, 0.6f);
                        })
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage());
                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0), this);
                        })
                        .start();
            }
        }
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        cooldown = 60 + random.nextInt(40);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    // 夜の灯: 暗いところでも明るく見える
    @Override
    public float getLightLevelDependentMagicValue() {
        return 1f;
    }
}
