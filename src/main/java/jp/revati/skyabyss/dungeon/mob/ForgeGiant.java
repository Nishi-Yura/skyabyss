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
//   三連打ち    鎚で前へ三度、だんだん遠くへ打つ (予告 0.9 → 1.3 → 1.7 秒)。横へ抜ける
//   熔けた溝    後半: 鎚で床を裂き、前へ熔けた溝を引く。溝はしばらく燃え続ける (4 秒。立つと 1 秒ごとに焼ける)
//   炉の噴き上げのあとは、背の炉が開いて大きな隙。後半 (体力半分から) は熔岩の蛞蝓を 2 体呼ぶ
//   炉の噴き上げ 体力が 6 割を切ると、前かがみになって背の炉から火を噴き上げ、人ごとの落ちる火の輪 (予告 1.4 秒)
// 作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
//   跳び叩き  深く沈んで鎚を掲げて跳び、人のいた所の円へ叩きつける (予告 1.3 秒)、続けて外への輪の衝撃。離れた人へ。着地のあとに隙
//   火の息    のけぞって下あごを外し (予告 1.0 秒)、首を左から右へ振って前の二枚の扇を 0.8 秒焼く。寄った人へ
//   登場は鎚を握った腕が床を割って突き出し、這い出て片膝をつき、立つと背の炉の扉が弾けて吠える (intro)。後半に入るときも吠える (scream)
public class ForgeGiant extends DungeonBoss {
    static final int HAMMER = 1;
    static final int SWEEP = 2;
    static final int STOMP = 3;
    static final int BLAST = 4;
    static final int COMBO = 5;
    static final int TRENCH = 6;
    static final int LEAP = 7;
    static final int BREATH = 8;
    public static final int COMBO_WARN = 18;
    public static final int TRENCH_WARN = 20;
    public static final double TRENCH_LENGTH = 15;
    public static final int HAMMER_WARN = 20;
    public static final double HAMMER_REACH = 5.5;
    public static final int SWEEP_WARN = 18;
    public static final double SWEEP_RANGE = 7.5;
    public static final int STOMP_WARN = 20;
    public static final double STOMP_OUTER = 9;
    public static final double STOMP_INNER = 3.5;
    public static final int BLAST_WARN = 28;
    public static final int LEAP_WARN = 26;
    public static final int BREATH_WARN = 20;
    public static final int BREATH_ACTIVE = 16;
    public static final double BREATH_RANGE = 9;
    private int lastLeap = -300;
    private int lastBreath = -200;
    private int lastStomp = -200;
    private int lastBlast = -300;
    private int lastCombo = -200;
    private int lastTrench = -300;

    public ForgeGiant(EntityType<? extends Monster> type, Level level) {
        super(type, level, "forge_giant", () -> SoundEvents.IRON_GOLEM_STEP, () -> SoundEvents.IRON_GOLEM_HURT, () -> SoundEvents.IRON_GOLEM_DEATH,
                "hammer", "combo", "sweep", "stomp", "trench", "blast", "leap", "breath", "intro", "scream", "hurt", "death");
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
        if (phase2 && distance > 6 && distance <= TRENCH_LENGTH && tickCount - lastTrench > 260) {
            trench(target);
            return;
        }
        if (distance > 8 && distance < 22 && tickCount - lastLeap > 260) {
            move = LEAP;
            lastLeap = tickCount;
            leapAt(target, "leap", LEAP_WARN, 4.0, 1.4f);
            return;
        }
        if (distance <= BREATH_RANGE - 1 && tickCount - lastBreath > 240 && random.nextFloat() < 0.45f) {
            move = BREATH;
            lastBreath = tickCount;
            spray(target, "breath", BREATH_WARN, BREATH_RANGE, 50, BREATH_ACTIVE, 0.9f, ParticleTypes.FLAME);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2f, 0.4f);
            return;
        }
        if (distance <= 11 && tickCount - lastCombo > 220 && random.nextFloat() < 0.5f) {
            combo(target);
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

    // 三連打ち: 前へ三つの輪、だんだん遠く・遅く
    void combo(LivingEntity target) {
        move = COMBO;
        lastCombo = tickCount;
        begin(target, "combo");
        cue("forge_giant", "combo", COMBO_WARN);
        Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
        for (int k = 0; k < 3; k++) {
            Vec3 c = position().add(f.scale(3.5 + k * 3.5));
            Telegraph.in(server(), TelegraphShape.circle(c.x, c.z, 2.8))
                    .floor(getY()).warn(COMBO_WARN + k * 8).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.FLAME, c.x, getY() + 0.3, c.z, 40, 1.2, 0.2, 1.2, 0.08);
                        server.playSound(null, c.x, getY(), c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.6f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.95f))
                    .start();
        }
    }

    // 熔けた溝: 前へ長い帯。予兆のあと 4 秒燃え続ける
    void trench(LivingEntity target) {
        move = TRENCH;
        lastTrench = tickCount;
        begin(target, "trench");
        cue("forge_giant", "trench", TRENCH_WARN);
        double a = Telegraph.angleOf(target.position().subtract(position()));
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), a, TRENCH_LENGTH, 1.3))
                .floor(getY()).warn(TRENCH_WARN).active(80).repeatEvery(20).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    for (int i = 3; i < TRENCH_LENGTH; i += 3) {
                        jp.revati.skyabyss.combat.prop.AttackProps.rise(server, "lava_pillar", this, position().add(Math.cos(a) * i, 0, Math.sin(a) * i), i * 47f, 0.45f + i * 0.02f, 20);
                    }
                    for (int i = 1; i < TRENCH_LENGTH; i += 2) {
                        server.sendParticles(ParticleTypes.LAVA, getX() + Math.cos(a) * i, getY() + 0.2, getZ() + Math.sin(a) * i, 3, 0.4, 0.1, 0.4, 0.05);
                    }
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_POP, SoundSource.HOSTILE, 2f, 0.6f);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 0.45f);
                    player.igniteForSeconds(2);
                })
                .start();
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
            jp.revati.skyabyss.combat.prop.AttackProps.toss(server(), "fireball", this, position().add(0, 5.5, 0).add(forward().scale(-1.5)), new Vec3(s.x, getY(), s.z), BLAST_WARN, 20, 12f, 1.3f, 12);
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

    // 登場: 腕が床を割り、這い出て、扉が弾けて吠える (アニメ intro 5.0 秒)
    @Override
    protected String introAnim() {
        return "intro";
    }

    @Override
    protected int introLength() {
        return 100;
    }

    @Override
    protected int introCardAt() {
        return 66;
    }

    @Override
    protected void enterPhase2(LivingEntity target) {
        playAction("scream");
        super.enterPhase2(target);
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
    protected String bossId() {
        return "forge_giant";
    }

    @Override
    protected String minionId() {
        return "skyabyss:lava_slug";
    }

    @Override
    protected int length() {
        return switch (move) {
            case HAMMER -> HAMMER_WARN + 14;
            case SWEEP -> SWEEP_WARN + 12;
            case STOMP -> STOMP_WARN + 10;
            case COMBO -> COMBO_WARN + 16 + 12;
            case TRENCH -> TRENCH_WARN + 14;
            case LEAP -> LEAP_WARN + 26;
            case BREATH -> BREATH_WARN + 10 + BREATH_ACTIVE + 12;
            default -> BLAST_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == BLAST) {
            // 噴き上げのあとは背の炉が開く (大きな隙)
            opening();
        }
        cooldown = move == HAMMER || move == BLAST || move == COMBO ? 28 : 16 + random.nextInt(10);
        super.finish();
    }
}
