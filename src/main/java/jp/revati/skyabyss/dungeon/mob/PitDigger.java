package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.foe.ContinentFoe;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨の坑夫 (docs/design/55 の 4 の 7、00 の 372 = A。鯨骨の坑の道中)。
// 担いだ鶴嘴を振り下ろす (前の短い帯、予告 0.8 秒)
public class PitDigger extends ContinentFoe {
    public static final int WARN = 16;
    public static final double LENGTH = 3.4;
    public static final double HALF_WIDTH = 0.8;
    public static final double START_RANGE = 3.0;

    public PitDigger(EntityType<? extends Monster> type, Level level) {
        super(type, level, "pit_digger", () -> SoundEvents.SKELETON_AMBIENT, () -> SoundEvents.SKELETON_HURT, () -> SoundEvents.SKELETON_DEATH,
                "swing", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.05, 2.4));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "swing");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(SoundEvents.SKELETON_STEP, 0.8f, 0.6f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        getX() + f.x * 2.2, getY() + 0.2, getZ() + f.z * 2.2, 14, 0.4, 0.1, 0.4, 0.1))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.knockback(0.5, -f.x, -f.z);
                })
                .start();
    }

    @Override
    protected int length() {
        return WARN + 10;
    }

    @Override
    protected void finish() {
        cooldown = 20 + random.nextInt(16);
    }
}
