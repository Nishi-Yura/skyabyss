package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 鐘負いの大寄居蟹 (docs/design/55 の 4 の 7、00 の 377 = A。潮の洞の中ボス)。
//   挟む    寄った人へ大きな右の鋏 (前の扇、予告 0.8 秒)
//   鐘の波  背の鐘を揺すって鳴らすと、波が広がる (まわりの輪、内側 2.5 は安全、予告 1.2 秒。体力 8 割から)
//   殻ごと突進 鐘にこもって転がり込む (前の長い帯、予告 1.1 秒)。こもっている間は受ける傷が 4 割
//   殻回し  殻にこもって回る (まわりの円 5.5 を二度、予告 0.9 秒から 0.6 秒おき)。こもっている間は受ける傷が 4 割
//   泡吐き  身を起こして泡を三つ吐く (人の足もとと、まわりに二つの円、予告 1.1 秒)
public class BellHermit extends DungeonBoss {
    static final int PINCH = 1;
    static final int RING = 2;
    static final int CHARGE = 3;
    static final int SPIN = 4;
    static final int SPIT = 5;
    public static final int SPIN_WARN = 18;
    public static final int SPIT_WARN = 22;
    private int lastSpin = -160;
    private int lastSpit = -100;
    public static final int PINCH_WARN = 16;
    public static final double PINCH_RANGE = 5;
    public static final int RING_WARN = 24;
    public static final double RING_INNER = 2.5;
    public static final double RING_OUTER = 9;
    public static final int CHARGE_WARN = 22;
    public static final double CHARGE_LENGTH = 14;
    public static final float SHELL_TAKEN = 0.4f;
    private int lastRing = -300;
    private int lastCharge = -200;

    public BellHermit(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bell_hermit", () -> SoundEvents.TURTLE_AMBIENT_LAND, () -> SoundEvents.TURTLE_HURT, () -> SoundEvents.TURTLE_DEATH,
                "pinch", "ring", "charge", "spin", "spit", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 180)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance < 5.5 && tickCount - lastSpin > 180) {
            move = SPIN;
            lastSpin = tickCount;
            spinRings(target, "spin", SPIN_WARN, 5.5, 2, 12, 0.9f);
            return;
        }
        if (distance > 5 && tickCount - lastSpit > 120) {
            move = SPIT;
            lastSpit = tickCount;
            volley(target, "spit", SPIT_WARN, 2, 2.0, 0.9f, net.minecraft.world.level.block.Blocks.PRISMARINE.defaultBlockState());
            return;
        }
        if (hp() < 0.8f && distance < RING_OUTER && tickCount - lastRing > 240) {
            move = RING;
            lastRing = tickCount;
            begin(target, "ring");
            cue("bell_hermit", "ring", RING_WARN);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2f, 0.5f);
            Telegraph.in(server(), TelegraphShape.ring(getX(), getZ(), RING_INNER, RING_OUTER))
                    .floor(getY()).warn(RING_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2f, 0.6f);
                        server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 120, RING_OUTER * 0.5, 0.1, RING_OUTER * 0.5, 0.2);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        Vec3 away = player.position().subtract(position()).multiply(1, 0, 1).normalize();
                        player.knockback(1.3, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance > 7 && distance < CHARGE_LENGTH && tickCount - lastCharge > 160) {
            lastCharge = tickCount;
            cue("bell_hermit", "charge", CHARGE_WARN);
            charge(target, CHARGE, "charge", CHARGE_WARN, CHARGE_LENGTH, 1.4, 0.7, () -> SoundEvents.BELL_BLOCK);
            return;
        }
        if (distance > PINCH_RANGE) {
            return;
        }
        move = PINCH;
        begin(target, "pinch");
        cue("bell_hermit", "pinch", PINCH_WARN);
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), PINCH_RANGE, Math.toRadians(50)))
                .floor(getY()).warn(PINCH_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 1.2f);
                    player.knockback(0.7, -f.x, -f.z);
                })
                .start();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, (move == CHARGE || move == SPIN) && act > 0 ? amount * SHELL_TAKEN : amount);
    }

    @Override
    protected String bossId() {
        return "bell_hermit";
    }

    @Override
    protected int length() {
        return switch (move) {
            case PINCH -> PINCH_WARN + 10;
            case RING -> RING_WARN + 12;
            case SPIN -> SPIN_WARN + 12 + 14;
            case SPIT -> SPIT_WARN + 12;
            default -> CHARGE_WARN + (int) Math.ceil(CHARGE_LENGTH / 0.7) + 10;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == PINCH ? 14 + random.nextInt(12) : 24 + random.nextInt(12);
        super.finish();
    }
}
