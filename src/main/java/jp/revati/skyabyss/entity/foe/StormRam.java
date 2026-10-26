package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 雷角の大羊 (docs/design/44 の 5): 嵐の高地の名のある強敵。
//   角の突進: 頭を下げて (予告 1.2 秒、前の長い帯 12) 駆け抜ける。離れた人へ
//   雷の蹄: 後ろ脚で立ち上がり (予告 1.2 秒、まわりの輪 2.5〜6.5) 蹄を打ち下ろすと、輪に雷が走る (足もとは安全)
//   頭突き: 寄った人へ (予告 0.6 秒、前の扇)
public class StormRam extends RegionBoss {
    static final int CHARGE = 1;
    static final int REAR = 2;
    static final int BUTT = 3;
    public static final int CHARGE_WARN = 24;
    public static final double CHARGE_LENGTH = 12;
    public static final int REAR_WARN = 24;
    public static final double RING_IN = 2.5;
    public static final double RING_OUT = 6.5;
    public static final int BUTT_WARN = 12;
    private int lastRear = -150;

    public StormRam(EntityType<? extends Monster> type, Level level) {
        super(type, level, "storm_ram", MobSounds.STORM_RAM_AMBIENT, MobSounds.STORM_RAM_HURT, MobSounds.STORM_RAM_DEATH, "charge", "rear", "butt", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance >= 6 && distance <= 15) {
            charge(target, CHARGE, "charge", CHARGE_WARN, CHARGE_LENGTH, 1.2, 0.9, MobSounds.STORM_RAM_CHARGE);
            return;
        }
        if (distance > RING_IN && distance < RING_OUT && tickCount - lastRear > 140) {
            move = REAR;
            lastRear = tickCount;
            begin(target, "rear");
            double cx = getX();
            double cz = getZ();
            double y = getY();
            Telegraph.in(server(), TelegraphShape.ring(cx, cz, RING_IN, RING_OUT))
                    .floor(y).warn(REAR_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.playSound(null, cx, y, cz, MobSounds.STORM_RAM_STOMP.get(), SoundSource.HOSTILE, 1.6f, 1f);
                        for (int i = 0; i < 6; i++) {
                            double a = i * Math.PI / 3 + random.nextDouble() * 0.4;
                            double r = (RING_IN + RING_OUT) / 2;
                            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                            if (bolt != null) {
                                bolt.moveTo(cx + Math.cos(a) * r, y, cz + Math.sin(a) * r);
                                bolt.setVisualOnly(true);
                                server.addFreshEntity(bolt);
                            }
                        }
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, y + 0.3, cz, 80, RING_OUT * 0.5, 0.2, RING_OUT * 0.5, 0.3);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.3f))
                    .start();
            return;
        }
        if (distance <= 3.4) {
            move = BUTT;
            begin(target, "butt");
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), 3.6, Math.toRadians(50)))
                    .floor(getY()).warn(BUTT_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        player.knockback(1.5, -f.x, -f.z);
                    })
                    .start();
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case CHARGE -> CHARGE_WARN + 20;
            case REAR -> REAR_WARN + 16;
            default -> BUTT_WARN + 8;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == CHARGE ? 26 : 16 + random.nextInt(12);
        super.finish();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
