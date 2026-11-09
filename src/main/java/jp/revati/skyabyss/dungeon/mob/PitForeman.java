package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨鯨の兜の坑頭 (docs/design/55 の 4 の 7、00 の 374 = A。鯨骨の坑の中ボス)。
//   地割り  担いだ骨の槌を振り下ろし、割れ目が前へ走る (前の長い帯、予告 1.0 秒)
//   薙ぎ    寄った人へ槌を横に薙ぐ (前の広い扇、予告 0.8 秒)
//   足踏み  体力が 6 割を切ると、まわりを踏み鳴らす (まわりの輪、予告 0.9 秒。当たると浮く)
//   跳び割り 離れた人のいる所へ跳び込む (円 4.5、予告 1.15 秒。続けて外へ輪の衝撃)
//   骨投げ  背の骨塊を投げる (人の足もとと、まわりに二つの円、予告 1.0 秒)
public class PitForeman extends DungeonBoss {
    static final int SLAM = 1;
    static final int SWEEP = 2;
    static final int STOMP = 3;
    static final int LEAP = 4;
    static final int THROW = 5;
    public static final int LEAP_WARN = 23;
    public static final int THROW_WARN = 20;
    private int lastLeap = -200;
    private int lastThrow = -120;
    public static final int SLAM_WARN = 20;
    public static final double SLAM_LENGTH = 12;
    public static final int SWEEP_WARN = 16;
    public static final double SWEEP_RANGE = 6;
    public static final int STOMP_WARN = 18;
    public static final double STOMP_RADIUS = 6.5;
    private int lastSweep = -200;
    private int lastStomp = -400;

    public PitForeman(EntityType<? extends Monster> type, Level level) {
        super(type, level, "pit_foreman", () -> SoundEvents.RAVAGER_AMBIENT, () -> SoundEvents.RAVAGER_HURT, () -> SoundEvents.RAVAGER_DEATH,
                "slam", "sweep", "stomp", "leap", "throw", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 170)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > 7 && distance < 22 && tickCount - lastLeap > 200) {
            move = LEAP;
            lastLeap = tickCount;
            leapAt(target, "leap", LEAP_WARN, 4.5, 1.3f);
            return;
        }
        if (distance > 5 && tickCount - lastThrow > 140) {
            move = THROW;
            lastThrow = tickCount;
            volley(target, "throw", THROW_WARN, 2, 2.2, 1.0f, Blocks.BONE_BLOCK.defaultBlockState());
            return;
        }
        if (hp() < 0.6f && distance < STOMP_RADIUS && tickCount - lastStomp > 200) {
            move = STOMP;
            lastStomp = tickCount;
            begin(target, "stomp");
            cue("pit_foreman", "stomp", STOMP_WARN);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), STOMP_RADIUS))
                    .floor(getY()).warn(STOMP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 0.2, getZ(),
                                60, STOMP_RADIUS * 0.4, 0.1, STOMP_RADIUS * 0.4, 0.15);
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 0.6f);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        player.setDeltaMovement(player.getDeltaMovement().add(0, 0.7, 0));
                        player.hurtMarked = true;
                    })
                    .start();
            return;
        }
        Vec3 f = forward();
        if (distance < SWEEP_RANGE - 0.5 && tickCount - lastSweep > 100) {
            move = SWEEP;
            lastSweep = tickCount;
            begin(target, "sweep");
            cue("pit_foreman", "sweep", SWEEP_WARN);
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, Math.toRadians(70)))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 3, getY() + 1.2, getZ() + f.z * 3, 6, 1.4, 0.2, 1.4, 0))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.9f);
                        player.knockback(1.0, -f.x, -f.z);
                    })
                    .start();
            return;
        }
        if (distance > SLAM_LENGTH) {
            return;
        }
        move = SLAM;
        begin(target, "slam");
        cue("pit_foreman", "slam", SLAM_WARN);
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), SLAM_LENGTH, 1.3))
                .floor(getY()).warn(SLAM_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    for (int i = 1; i <= SLAM_LENGTH; i += 2) {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DRIPSTONE_BLOCK.defaultBlockState()),
                                getX() + f.x * i, getY() + 0.2, getZ() + f.z * i, 8, 0.4, 0.1, 0.4, 0.1);
                    }
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.5f);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 1.3f);
                    player.knockback(0.8, -f.x, -f.z);
                })
                .start();
    }

    @Override
    protected String bossId() {
        return "pit_foreman";
    }

    @Override
    protected int length() {
        return switch (move) {
            case SLAM -> SLAM_WARN + 16;
            case SWEEP -> SWEEP_WARN + 12;
            case LEAP -> LEAP_WARN + 18;
            case THROW -> THROW_WARN + 12;
            default -> STOMP_WARN + 12;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == SLAM ? 24 + random.nextInt(12) : 16 + random.nextInt(12);
        super.finish();
    }
}
