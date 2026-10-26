package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 苔の亡者 (docs/design/44): 岩鯨の丘陵の夜。遅いが硬い。太い右腕を振りかぶって (予告 1.0 秒) 前の円へ叩きつける。
// ときどき背の胞子の袋が膨らんで (予告 1.2 秒、まわりの円) 胞子を撒く: 円の中の人は毒と鈍足 3 秒
public class MossDead extends ContinentFoe {
    public static final int SLAM_WARN = 20;
    public static final double SLAM_REACH = 2.0;
    public static final double SLAM_RADIUS = 1.9;
    public static final double SLAM_START = 3.4;
    public static final int SLAM_END = SLAM_WARN + 16;
    public static final int SPORE_WARN = 24;
    public static final double SPORE_RADIUS = 4.2;
    public static final int SPORE_END = SPORE_WARN + 18;
    public static final int SPORE_EVERY = 200;
    private boolean spores;
    private int lastSpores = -SPORE_EVERY / 2;

    public MossDead(EntityType<? extends Monster> type, Level level) {
        super(type, level, "moss_dead", MobSounds.MOSS_DEAD_AMBIENT, MobSounds.MOSS_DEAD_HURT, MobSounds.MOSS_DEAD_DEATH, "slam", "spores", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.19)
                .add(Attributes.FOLLOW_RANGE, 22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 2.4));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (tickCount - lastSpores > SPORE_EVERY && distance < SPORE_RADIUS) {
            spores = true;
            lastSpores = tickCount;
            begin(target, "spores");
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), SPORE_RADIUS))
                    .floor(getY()).warn(SPORE_WARN).danger().owner(this).target(target)
                    .startSound(MobSounds.MOSS_DEAD_SPORES.get(), 1.0f, 1.0f)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 1.0, getZ(), 60, SPORE_RADIUS * 0.45, 0.6, SPORE_RADIUS * 0.45, 0.01))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.3f);
                        player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                    })
                    .start();
            return;
        }
        if (distance > SLAM_START) {
            return;
        }
        spores = false;
        begin(target, "slam");
        Vec3 f = forward();
        double cx = getX() + f.x * SLAM_REACH;
        double cz = getZ() + f.z * SLAM_REACH;
        Telegraph.in(server(), TelegraphShape.circle(cx, cz, SLAM_RADIUS))
                .floor(getY()).warn(SLAM_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, cx, getY(), cz, MobSounds.MOSS_DEAD_SLAM.get(), SoundSource.HOSTILE, 1.2f, 1f);
                    server.sendParticles(ParticleTypes.POOF, cx, getY() + 0.2, cz, 10, 0.6, 0.05, 0.6, 0.02);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.knockback(0.8, -f.x, -f.z);
                })
                .start();
    }

    @Override
    protected int length() {
        return spores ? SPORE_END : SLAM_END;
    }

    @Override
    protected void finish() {
        cooldown = spores ? 20 : 30 + random.nextInt(20);
        spores = false;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.MOSS_DEAD_STEP.get(), 0.5f);
    }
}
