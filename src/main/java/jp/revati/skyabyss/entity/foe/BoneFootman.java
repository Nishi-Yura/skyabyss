package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 骸の歩兵 (docs/design/44): 都のまわりの夜と亡者の野営。前からの傷は丸い木の盾が受けて 3 割に減る (横と後ろは通る)。
// 盾を構えたまま剣を引いて (予告 0.6 秒、前の短い帯) 突く
public class BoneFootman extends BoneSoldier {
    public static final int WARN = 12;
    public static final double LENGTH = 3.2;
    public static final double HALF_WIDTH = 0.6;
    public static final double START_RANGE = 3.0;
    public static final int END = WARN + 14;
    public static final float FRONT_TAKEN = 0.3f;

    public BoneFootman(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_footman", "thrust", "block", "rise", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.05, 2.2));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "thrust");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.BANDIT_SWING.get(), 0.7f, 1.4f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.CRIT, getX() + f.x * 2, getY() + 1.1, getZ() + f.z * 2, 5, 0.2, 0.1, 0.2, 0.05))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.knockback(0.5, -f.x, -f.z);
                })
                .start();
    }

    // 前の盾: 前 (向きから ±60 度) からの傷は 3 割。盾が鳴る
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (!level().isClientSide && from != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            if (look.dot(toSource) > 0.5) {
                amount *= FRONT_TAKEN;
                level().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_SOLDIER_BLOCK.get(), SoundSource.HOSTILE, 1f, 0.9f + random.nextFloat() * 0.2f);
                if (act == 0) {
                    playAction("block");
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        cooldown = 25 + random.nextInt(20);
    }
}
