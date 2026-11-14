package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.prop.PropFx;
import java.util.ArrayDeque;
import java.util.Deque;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.foe.ContinentFoe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 熔岩の蛞蝓 (docs/design/55 の 4 の 7、00 の 382 = A。熔けた坑道の道中)。
//   噛む  寄った人へ前の扇 (予告 0.8 秒)
//   火の跡 這ったあとに小さな火の輪が残る (予告 0.8 秒のあと 4 秒、立っていると 0.5 秒ごとに少し焼ける)
public class LavaSlug extends ContinentFoe {
    public static final int WARN = 16;
    public static final double BITE_RANGE = 2.8;
    public static final double TRAIL_RADIUS = 1.1;
    public static final int TRAIL_TICKS = 80;
    public static final int TRAIL_EVERY = 24;
    private final Deque<long[]> trail = new ArrayDeque<>();
    private Vec3 lastTrail;

    public LavaSlug(EntityType<? extends Monster> type, Level level) {
        super(type, level, "lava_slug", () -> SoundEvents.MAGMA_CUBE_SQUISH_SMALL, () -> SoundEvents.MAGMA_CUBE_HURT_SMALL, () -> SoundEvents.MAGMA_CUBE_DEATH_SMALL,
                "bite", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 1.8));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % TRAIL_EVERY == 0 && getTarget() != null && (lastTrail == null || lastTrail.distanceTo(position()) > 1.6)) {
            lastTrail = position();
            Vec3 at = lastTrail;
            trail.add(new long[] {Double.doubleToLongBits(at.x), Double.doubleToLongBits(at.y), Double.doubleToLongBits(at.z), tickCount + WARN + TRAIL_TICKS});
            Telegraph.in(server(), TelegraphShape.circle(at.x, at.z, TRAIL_RADIUS)).prop(PropFx.rise("lava_pillar").size(0.4f).linger(10))
                    .floor(at.y).warn(WARN).active(TRAIL_TICKS).repeatEvery(10).danger().owner(this)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.3f);
                        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 30));
                    })
                    .start();
        }
        // 残っている火の跡の炎
        while (!trail.isEmpty() && trail.peekFirst()[3] < tickCount) {
            trail.pollFirst();
        }
        if (tickCount % 4 == 0) {
            for (long[] t : trail) {
                server().sendParticles(ParticleTypes.FLAME, Double.longBitsToDouble(t[0]), Double.longBitsToDouble(t[1]) + 0.1, Double.longBitsToDouble(t[2]),
                        2, TRAIL_RADIUS * 0.5, 0.05, TRAIL_RADIUS * 0.5, 0.01);
            }
        }
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > BITE_RANGE) {
            return;
        }
        begin(target, "bite");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BITE_RANGE + 0.6, Math.toRadians(50)))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.LAVA, getX() + f.x * 1.6, getY() + 0.4, getZ() + f.z * 1.6, 6, 0.3, 0.1, 0.3, 0.02))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 40));
                })
                .start();
    }

    @Override
    protected int length() {
        return WARN + 8;
    }

    @Override
    protected void finish() {
        cooldown = 24 + random.nextInt(16);
    }
}
