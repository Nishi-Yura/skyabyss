package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 晶洞の傀儡 (docs/design/55 の 4 の 7、00 の 380 = A。雷晶の空洞の中ボス)。
//   拳      右の岩の拳で前を突く (前の太い帯、予告 0.8 秒)
//   叩きつけ 両の拳を振り上げて地を叩く (まわりの輪、予告 1.05 秒。当たると浮く)
//   晶の雨  体力が 6 割を切ると、胸を開いて結晶を降らせる (人ごとの円と、ほかに三つ。予告 1.4 秒)
//   胸の結晶が弱点: 前 (向きから ±50 度) からの傷は 1.5 倍、後ろの岩からは 0.6 倍
public class GeodeGolem extends DungeonBoss {
    static final int PUNCH = 1;
    static final int POUND = 2;
    static final int SHARDS = 3;
    public static final int PUNCH_WARN = 16;
    public static final double PUNCH_LENGTH = 6;
    public static final int POUND_WARN = 21;
    public static final double POUND_RADIUS = 6.5;
    public static final int SHARDS_WARN = 28;
    public static final float FRONT_TAKEN = 1.5f;
    public static final float BACK_TAKEN = 0.6f;
    private int lastPound = -200;
    private int lastShards = -400;

    public GeodeGolem(EntityType<? extends Monster> type, Level level) {
        super(type, level, "geode_golem", () -> SoundEvents.AMETHYST_BLOCK_CHIME, () -> SoundEvents.IRON_GOLEM_HURT, () -> SoundEvents.IRON_GOLEM_DEATH,
                "punch", "pound", "shards", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 190)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (hp() < 0.6f && tickCount - lastShards > 260) {
            shards(target);
            return;
        }
        if (distance < POUND_RADIUS - 1 && tickCount - lastPound > 140) {
            move = POUND;
            lastPound = tickCount;
            begin(target, "pound");
            cue("geode_golem", "pound", POUND_WARN);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), POUND_RADIUS))
                    .floor(getY()).warn(POUND_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), getX(), getY() + 0.2, getZ(),
                                60, POUND_RADIUS * 0.4, 0.1, POUND_RADIUS * 0.4, 0.15);
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 0.7f);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 1.1f);
                        player.setDeltaMovement(player.getDeltaMovement().add(0, 0.7, 0));
                        player.hurtMarked = true;
                    })
                    .start();
            return;
        }
        if (distance > PUNCH_LENGTH + 0.5) {
            return;
        }
        move = PUNCH;
        begin(target, "punch");
        cue("geode_golem", "punch", PUNCH_WARN);
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), PUNCH_LENGTH, 1.4))
                .floor(getY()).warn(PUNCH_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 1.2f);
                    player.knockback(1.2, -f.x, -f.z);
                })
                .start();
    }

    // 胸を開いて結晶を降らせる: 人ごとに円を一つ、ほかに三つ
    void shards(LivingEntity target) {
        move = SHARDS;
        lastShards = tickCount;
        begin(target, "shards");
        cue("geode_golem", "shards", SHARDS_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2f, 0.6f);
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24), this::canHit)) {
            spots.add(p.position());
        }
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            spots.add(position().add(Math.cos(a) * (5 + random.nextDouble() * 7), 0, Math.sin(a) * (5 + random.nextDouble() * 7)));
        }
        for (Vec3 s : spots) {
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 2.4))
                    .floor(getY()).warn(SHARDS_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()), s.x, getY() + 4, s.z,
                                30, 0.8, 1.4, 0.8, 0.1);
                        server.playSound(null, s.x, getY(), s.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.4f, 0.7f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.9f))
                    .start();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (!level().isClientSide && from != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            double dot = look.dot(toSource);
            if (dot > 0.64) {
                amount *= FRONT_TAKEN;
                server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + look.x * 1.2, getY() + 2.1, getZ() + look.z * 1.2, 8, 0.3, 0.3, 0.3, 0.1);
            } else if (dot < -0.5) {
                amount *= BACK_TAKEN;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected int length() {
        return switch (move) {
            case PUNCH -> PUNCH_WARN + 10;
            case POUND -> POUND_WARN + 16;
            default -> SHARDS_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == PUNCH ? 16 + random.nextInt(12) : 26 + random.nextInt(14);
        super.finish();
    }
}
