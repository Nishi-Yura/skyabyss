package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 岩背の大猪 (docs/design/44 の 5): 岩鯨の丘陵の名のある強敵。
//   突進: 地を掻いて (予告 1.2 秒、前の長い帯 11) 駆け抜ける。離れた人へ
//   背を震わせる: 前を持ち上げて岩の背を揺すり (予告 1.0 秒、まわりの円 5) 岩の破片を降らせる。寄った人へ、ときどき
//   牙で薙ぐ: 頭を横へ引いて (予告 0.6 秒、前の扇) 振り抜く。寄った人へ
public class RockBoar extends RegionBoss {
    static final int CHARGE = 1;
    static final int QUAKE = 2;
    static final int GORE = 3;
    public static final int CHARGE_WARN = 24;
    public static final double CHARGE_LENGTH = 11;
    public static final int QUAKE_WARN = 20;
    public static final double QUAKE_RADIUS = 5;
    public static final int GORE_WARN = 12;
    public static final double GORE_RANGE = 3.6;
    private int lastQuake = -200;

    public RockBoar(EntityType<? extends Monster> type, Level level) {
        super(type, level, "rock_boar", MobSounds.ROCK_BOAR_AMBIENT, MobSounds.ROCK_BOAR_HURT, MobSounds.ROCK_BOAR_DEATH, "charge", "quake", "gore", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance >= 5 && distance <= 14) {
            charge(target, CHARGE, "charge", CHARGE_WARN, CHARGE_LENGTH, 1.3, 0.8, MobSounds.ROCK_BOAR_CHARGE);
            return;
        }
        if (distance < QUAKE_RADIUS - 0.5 && tickCount - lastQuake > 160) {
            move = QUAKE;
            lastQuake = tickCount;
            begin(target, "quake");
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), QUAKE_RADIUS))
                    .floor(getY()).warn(QUAKE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.playSound(null, getX(), getY(), getZ(), MobSounds.ROCK_BOAR_QUAKE.get(), SoundSource.HOSTILE, 1.6f, 1f);
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState()), getX(), getY() + 3.0, getZ(),
                                60, QUAKE_RADIUS * 0.4, 0.8, QUAKE_RADIUS * 0.4, 0.1);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.9f))
                    .start();
            return;
        }
        if (distance <= GORE_RANGE) {
            move = GORE;
            begin(target, "gore");
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), GORE_RANGE, Math.toRadians(60)))
                    .floor(getY()).warn(GORE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        player.knockback(1.0, -f.x, -f.z);
                    })
                    .start();
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case CHARGE -> CHARGE_WARN + 22;
            case QUAKE -> QUAKE_WARN + 16;
            default -> GORE_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == CHARGE ? 30 : 18 + random.nextInt(12);
        super.finish();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.ROCK_BOAR_STEP.get(), 0.6f);
    }
}
