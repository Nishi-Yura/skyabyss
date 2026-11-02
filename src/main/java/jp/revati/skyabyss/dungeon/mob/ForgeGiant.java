package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 炉負いの巨人 (docs/design/55 の 1 章、熔けた坑道の主。形は 00 の 367 = A 炉負いの巨人)。
//   振り下ろし  鎚を振り上げて前の輪へ叩きつける (予告 1.0 秒)。重い一撃 (47)
//   横薙ぎ      鎚で前の広い扇を薙ぐ (予告 0.9 秒)
//   足踏み      片足を上げて踏み、足もとから外へ広がる熔けた輪 (内は安全な輪。予告 1.0 秒)。寄った人へ、ときどき
//   炉の噴き上げ 体力が 6 割を切ると、前かがみになって背の炉から火を噴き上げ、人ごとの落ちる火の輪 (予告 1.4 秒)
public class ForgeGiant extends DungeonBoss {
    static final int HAMMER = 1;
    static final int SWEEP = 2;
    static final int STOMP = 3;
    static final int BLAST = 4;
    public static final int HAMMER_WARN = 20;
    public static final double HAMMER_REACH = 5.5;
    public static final int SWEEP_WARN = 18;
    public static final double SWEEP_RANGE = 7.5;
    public static final int STOMP_WARN = 20;
    public static final double STOMP_OUTER = 9;
    public static final double STOMP_INNER = 3.5;
    public static final int BLAST_WARN = 28;
    private int lastStomp = -200;
    private int lastBlast = -300;

    public ForgeGiant(EntityType<? extends Monster> type, Level level) {
        super(type, level, "forge_giant", () -> SoundEvents.IRON_GOLEM_STEP, () -> SoundEvents.IRON_GOLEM_HURT, () -> SoundEvents.IRON_GOLEM_DEATH,
                "hammer", "sweep", "stomp", "blast", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 960)
                .add(Attributes.ATTACK_DAMAGE, 22)
                .add(Attributes.ARMOR, 14)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        Vec3 f = forward();
        if (hp() < 0.6f && tickCount - lastBlast > 300) {
            blast(target);
            return;
        }
        if (distance < STOMP_OUTER - 1 && tickCount - lastStomp > 200) {
            move = STOMP;
            lastStomp = tickCount;
            begin(target, "stomp");
            cue("forge_giant", "stomp", STOMP_WARN);
            Telegraph.in(server(), TelegraphShape.ring(getX(), getZ(), STOMP_INNER, STOMP_OUTER))
                    .floor(getY()).warn(STOMP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 40, STOMP_OUTER * 0.4, 0.1, STOMP_OUTER * 0.4, 0.1);
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.8f);
                        player.igniteForSeconds(3);
                    })
                    .start();
            return;
        }
        if (distance <= HAMMER_REACH + 2.5 && random.nextFloat() < 0.6f) {
            move = HAMMER;
            begin(target, "hammer");
            cue("forge_giant", "hammer", HAMMER_WARN);
            Vec3 c = position().add(f.scale(HAMMER_REACH));
            Telegraph.in(server(), TelegraphShape.circle(c.x, c.z, 3.6))
                    .floor(getY()).warn(HAMMER_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.FLAME, c.x, getY() + 0.3, c.z, 50, 1.6, 0.2, 1.6, 0.08);
                        server.playSound(null, c.x, getY(), c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6f, 0.5f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.35f))
                    .start();
            return;
        }
        if (distance <= SWEEP_RANGE) {
            move = SWEEP;
            begin(target, "sweep");
            cue("forge_giant", "sweep", SWEEP_WARN);
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, Math.toRadians(140)))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.95f);
                        Vec3 away = player.position().subtract(position()).normalize();
                        player.knockback(1.6, -away.x, -away.z);
                    })
                    .start();
        }
    }

    void blast(LivingEntity target) {
        move = BLAST;
        lastBlast = tickCount;
        begin(target, "blast");
        cue("forge_giant", "blast", BLAST_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2f, 0.5f);
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(30), this::canHit)) {
            spots.add(p.position());
        }
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            spots.add(position().add(Math.cos(a) * (5 + random.nextDouble() * 9), 0, Math.sin(a) * (5 + random.nextDouble() * 9)));
        }
        for (Vec3 s : spots) {
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 2.8))
                    .floor(getY()).warn(BLAST_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.LAVA, s.x, getY() + 0.5, s.z, 24, 1.0, 0.4, 1.0, 0.1);
                        server.sendParticles(ParticleTypes.LARGE_SMOKE, s.x, getY() + 1, s.z, 12, 0.8, 0.6, 0.8, 0.02);
                        server.playSound(null, s.x, getY(), s.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.7f);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.85f);
                        player.igniteForSeconds(4);
                    })
                    .start();
        }
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move == BLAST && tick % 3 == 0) {
            // 背の炉の煙突から火の粉
            Vec3 back = position().add(forward().scale(-1.6));
            server().sendParticles(ParticleTypes.FLAME, back.x, getY() + 4.6, back.z, 6, 0.3, 0.4, 0.3, 0.05);
            server().sendParticles(ParticleTypes.LARGE_SMOKE, back.x, getY() + 5.0, back.z, 3, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case HAMMER -> HAMMER_WARN + 14;
            case SWEEP -> SWEEP_WARN + 12;
            case STOMP -> STOMP_WARN + 10;
            default -> BLAST_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == HAMMER || move == BLAST ? 28 : 16 + random.nextInt(10);
        super.finish();
    }
}
