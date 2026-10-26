package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 穂かじり鼠 (docs/design/44): 島の草地と都のまわりの平原に群れで出る。走り寄り、身を縮めて (予告 0.3 秒、前の短い帯)
// 跳びかかって足もとを噛み、すぐ離れる。弱いが速く、数で囲む
public class GrainRat extends ContinentFoe {
    public static final int WARN = 6;
    public static final double LENGTH = 2.8;
    public static final double HALF_WIDTH = 0.55;
    public static final double START_RANGE = 2.6;
    public static final int END = WARN + 12;

    public GrainRat(EntityType<? extends Monster> type, Level level) {
        super(type, level, "grain_rat", MobSounds.GRAIN_RAT_AMBIENT, MobSounds.GRAIN_RAT_HURT, MobSounds.GRAIN_RAT_DEATH, "bite", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.2, 1.4));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "bite");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.GRAIN_RAT_AMBIENT.get(), 0.8f, 1.4f)
                .victims(this::canHit)
                .onImpact(server -> {
                    setDeltaMovement(f.x * 0.55, 0.28, f.z * 0.55);
                    hasImpulse = true;
                    playSound(MobSounds.GRAIN_RAT_BITE.get(), 0.9f, 1f);
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
        retreat = 16;
        cooldown = 26 + random.nextInt(24);
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
