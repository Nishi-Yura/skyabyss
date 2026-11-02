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

// 野盗の弓手 (docs/design/44): 距離をとって射る。弓を引いて (予告 0.8 秒、床の細い帯) 放ち、後ろへ跳ぶ。
// 帯の外へ一歩出れば当たらない。寄られると跳んで離れる
public class BanditArcher extends ContinentFoe {
    public static final int WARN = 16;
    public static final double LENGTH = 15;
    public static final double HALF_WIDTH = 0.5;
    public static final double MIN_RANGE = 5;
    public static final double MAX_RANGE = 13;
    public static final int END = WARN + 14;

    public BanditArcher(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bandit_archer", MobSounds.BANDIT_AMBIENT, MobSounds.BANDIT_HURT, MobSounds.BANDIT_DEATH, "shoot", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 26);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new KeepDistanceGoal(this, MIN_RANGE, MAX_RANGE));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance < 2.5 || distance > MAX_RANGE + 2) {
            return;
        }
        begin(target, "shoot");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.BANDIT_ARCHER_DRAW.get(), 1.0f, 1.0f)
                .victims(this::clearShot)
                .onImpact(server -> {
                    server.playSound(null, getX(), getY(), getZ(), MobSounds.BANDIT_ARCHER_SHOOT.get(), SoundSource.HOSTILE, 1f, 0.9f + random.nextFloat() * 0.2f);
                    for (double d = 1; d <= LENGTH; d += 0.8) {
                        server.sendParticles(ParticleTypes.CRIT, getX() + f.x * d, getY() + 1.3, getZ() + f.z * d, 1, 0.02, 0.02, 0.02, 0);
                    }
                    setDeltaMovement(-f.x * 0.5, 0.38, -f.z * 0.5);
                    hasImpulse = true;
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage()))
                .start();
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        cooldown = 40 + random.nextInt(25);
    }
}
