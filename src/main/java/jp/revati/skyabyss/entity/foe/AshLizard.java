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

// 灰被りの大蜥蜴 (docs/design/44 の 5): 灰の荒野の名のある強敵。火に強い。
//   吐く: 頭を持ち上げて (予告 1.2 秒、前の扇 8) 灰と熾火を吐く。扇の中の人は燃える
//   尾の薙ぎ: 体をひねって (予告 0.8 秒、まわりの輪 1.8〜6.5) 尾で薙ぐ。体のすぐそばは安全
//   噛みつき: 寄った人へ (予告 0.5 秒、前の扇)
public class AshLizard extends RegionBoss {
    static final int BREATH = 1;
    static final int SWEEP = 2;
    static final int BITE = 3;
    public static final int BREATH_WARN = 24;
    public static final double BREATH_RANGE = 8;
    public static final int SWEEP_WARN = 16;
    public static final double SWEEP_IN = 1.8;
    public static final double SWEEP_OUT = 6.5;
    public static final int BITE_WARN = 10;
    private int lastSweep = -120;

    public AshLizard(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ash_lizard", MobSounds.ASH_LIZARD_AMBIENT, MobSounds.ASH_LIZARD_HURT, MobSounds.ASH_LIZARD_DEATH, "breath", "sweep", "bite", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > SWEEP_IN && distance < SWEEP_OUT - 1 && tickCount - lastSweep > 120) {
            move = SWEEP;
            lastSweep = tickCount;
            begin(target, "sweep");
            Telegraph.in(server(), TelegraphShape.ring(getX(), getZ(), SWEEP_IN, SWEEP_OUT))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .startSound(MobSounds.ASH_LIZARD_SWEEP.get(), 1.4f, 1.0f)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 40, SWEEP_OUT * 0.45, 0.1, SWEEP_OUT * 0.45, 0.02))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        Vec3 away = player.position().subtract(position()).multiply(1, 0, 1).normalize();
                        player.knockback(1.4, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance <= 3.2) {
            move = BITE;
            begin(target, "bite");
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), 4.2, Math.toRadians(40)))
                    .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> player.hurt(attackSource(), damage()))
                    .start();
            return;
        }
        if (distance > BREATH_RANGE - 0.5) {
            return;
        }
        move = BREATH;
        begin(target, "breath");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BREATH_RANGE, Math.toRadians(30)))
                .floor(getY()).warn(BREATH_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.ASH_LIZARD_BREATH.get(), 1.4f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> {
                    for (double d = 1.5; d <= BREATH_RANGE; d += 0.8) {
                        server.sendParticles(ParticleTypes.FLAME, getX() + f.x * d, getY() + 0.9, getZ() + f.z * d, 4, d * 0.22, 0.25, d * 0.22, 0.02);
                        server.sendParticles(ParticleTypes.ASH, getX() + f.x * d, getY() + 1.2, getZ() + f.z * d, 6, d * 0.25, 0.4, d * 0.25, 0.02);
                    }
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 0.9f);
                    player.igniteForSeconds(3);
                })
                .start();
    }

    @Override
    protected int length() {
        return switch (move) {
            case BREATH -> BREATH_WARN + 24;
            case SWEEP -> SWEEP_WARN + 16;
            default -> BITE_WARN + 8;
        };
    }

    @Override
    protected void finish() {
        cooldown = 18 + random.nextInt(14);
        super.finish();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
