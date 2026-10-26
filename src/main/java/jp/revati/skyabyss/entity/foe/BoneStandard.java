package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 骸の旗手 (docs/design/44): 亡者の野営の頭、草原の名のある強敵。長い片手剣で前を薙ぐ (予告 0.7 秒、前の広い扇)。
// ときどき背の軍旗を地に突き立てる (予告 1.2 秒): 足もとの円に打ち込み、旗の届く 8 ブロックの骸 (歩兵・弓兵) に旗の守りを付ける
// (倒れるはずの一撃で一度だけ起き上がる)。先に旗手を倒すか、守りの付いた骸を二度倒す
public class BoneStandard extends BoneSoldier {
    public static final int SWEEP_WARN = 14;
    public static final double SWEEP_RANGE = 3.9;
    public static final double SWEEP_HALF = Math.toRadians(75);
    public static final int SWEEP_END = SWEEP_WARN + 18;
    public static final int PLANT_WARN = 24;
    public static final double PLANT_RADIUS = 2.4;
    public static final double WARD_RADIUS = 8;
    public static final int PLANT_END = 46;
    public static final int PLANT_EVERY = 300;
    private boolean planting;
    private int lastPlant = -PLANT_EVERY / 2;

    public BoneStandard(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_standard", "sweep", "plant", "rise", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 26)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 2.8));
    }

    // 旗手自身は旗の守りを受けない (守る側)
    @Override
    public void ward() {
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (tickCount - lastPlant > PLANT_EVERY && distance < 12 && hasSoldiers()) {
            planting = true;
            lastPlant = tickCount;
            begin(target, "plant");
            server().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_STANDARD_HORN.get(), SoundSource.HOSTILE, 1.4f, 1f);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), WARD_RADIUS))
                    .floor(getY()).warn(PLANT_WARN).harmless().owner(this).start();
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), PLANT_RADIUS))
                    .floor(getY()).warn(PLANT_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.playSound(null, getX(), getY(), getZ(), MobSounds.BONE_STANDARD_PLANT.get(), SoundSource.HOSTILE, 1.4f, 1f);
                        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.1, getZ(), 30, WARD_RADIUS * 0.4, 0.05, WARD_RADIUS * 0.4, 0.01);
                        for (BoneSoldier s : level().getEntitiesOfClass(BoneSoldier.class, getBoundingBox().inflate(WARD_RADIUS), e -> e != this && e.isAlive())) {
                            s.ward();
                            server.sendParticles(ParticleTypes.SOUL, s.getX(), s.getY() + 1.0, s.getZ(), 6, 0.25, 0.5, 0.25, 0.02);
                        }
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.6f);
                        Vec3 away = player.position().subtract(position()).multiply(1, 0, 1).normalize();
                        player.knockback(0.9, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance > SWEEP_RANGE) {
            return;
        }
        planting = false;
        begin(target, "sweep");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, SWEEP_HALF))
                .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.BANDIT_SWING.get(), 1.2f, 0.6f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 1.9, getY() + 1.2, getZ() + f.z * 1.9, 3, 0.8, 0.1, 0.8, 0))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.knockback(0.7, -f.x, -f.z);
                })
                .start();
    }

    private boolean hasSoldiers() {
        return !level().getEntitiesOfClass(BoneSoldier.class, getBoundingBox().inflate(WARD_RADIUS), e -> e != this && e.isAlive() && !e.warded()).isEmpty();
    }

    @Override
    protected int length() {
        return planting ? PLANT_END : SWEEP_END;
    }

    @Override
    protected void finish() {
        cooldown = planting ? 15 : 30 + random.nextInt(20);
        planting = false;
    }
}
