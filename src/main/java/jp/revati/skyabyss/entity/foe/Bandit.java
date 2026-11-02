package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 野盗 (docs/design/44): 島の峠・野盗の野営の近接。鉈を振りかぶって (予告 0.6 秒、前の扇) 斜めに斬り下ろす。
// 体力が半分を切ると、斬ったあと少し後ずさる (追う間合いを作る)
public class Bandit extends ContinentFoe {
    public static final int WARN = 12;
    public static final double RANGE = 2.9;
    public static final double HALF_ANGLE = Math.toRadians(50);
    public static final double START_RANGE = 2.7;
    public static final int END = WARN + 16;

    public Bandit(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bandit", MobSounds.BANDIT_AMBIENT, MobSounds.BANDIT_HURT, MobSounds.BANDIT_DEATH, "swing", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.1, 1.9));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "swing");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), RANGE, HALF_ANGLE))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.BANDIT_SWING.get(), 0.9f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 1.4, getY() + 1.0, getZ() + f.z * 1.4, 1, 0, 0, 0, 0))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.knockback(0.4, -f.x, -f.z);
                })
                .start();
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        cooldown = 22 + random.nextInt(20);
        if (getHealth() < getMaxHealth() * 0.5f) {
            retreat = 24;
        }
    }
}
