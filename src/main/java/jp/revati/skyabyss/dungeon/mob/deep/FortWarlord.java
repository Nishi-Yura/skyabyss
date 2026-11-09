package jp.revati.skyabyss.dungeon.mob.deep;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.dungeon.mob.DungeonBoss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 骨鎧の野盗王 (奥のダンジョン「崩れた見張りの砦」の主。issue #19 の ②、2026-11-09 の頁で A)。岩鯨の骨を打ち付けた鎧と大鉈。
//   大薙ぎ    鉈を引いて前の広い扇を薙ぐ (予告 0.9 秒)
//   跳び叩き  身を沈めて跳び、狙った人の足もとの円へ叩きつける (予告 1.2 秒。着地の場所へ移る)
//   突進      身を低くして前へまっすぐ走る (予告 1.1 秒、前の帯 10)
//   後半 (体力半分から) は角笛で野盗を呼ぶ (DungeonBoss の手下)
public class FortWarlord extends DungeonBoss {
    static final int SWEEP = 1;
    static final int LEAP = 2;
    static final int CHARGE = 3;
    public static final int SWEEP_WARN = 18;
    public static final double SWEEP_RANGE = 6.5;
    public static final int LEAP_WARN = 24;
    public static final double LEAP_RADIUS = 3.2;
    public static final int CHARGE_WARN = 22;
    public static final double CHARGE_LENGTH = 10;
    private int lastLeap = -200;
    private int lastCharge = -200;

    public FortWarlord(EntityType<? extends Monster> type, Level level) {
        super(type, level, "fort_warlord", () -> SoundEvents.VINDICATOR_AMBIENT, () -> SoundEvents.VINDICATOR_HURT, () -> SoundEvents.VINDICATOR_DEATH,
                "sweep", "leap", "charge", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 560)
                .add(Attributes.ATTACK_DAMAGE, 14)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > 6 && distance < 16 && tickCount - lastLeap > 160) {
            move = LEAP;
            lastLeap = tickCount;
            begin(target, "leap");
            cue("fort_warlord", "leap", LEAP_WARN);
            Vec3 at = target.position();
            Telegraph.in(server(), TelegraphShape.circle(at.x, at.z, LEAP_RADIUS))
                    .floor(at.y).warn(LEAP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        teleportTo(at.x, at.y, at.z);
                        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 30, 1.4, 0.2, 1.4, 0.03);
                        server.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.6f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.2f))
                    .start();
            return;
        }
        if (distance < CHARGE_LENGTH && tickCount - lastCharge > 200 && random.nextFloat() < 0.4f) {
            move = CHARGE;
            lastCharge = tickCount;
            begin(target, "charge");
            cue("fort_warlord", "charge", CHARGE_WARN);
            Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            double a = Telegraph.angleOf(f);
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), a, CHARGE_LENGTH, 1.2))
                    .floor(getY()).warn(CHARGE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> setDeltaMovement(f.x * 1.6, 0.05, f.z * 1.6))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        player.knockback(1.4, -f.x, -f.z);
                    })
                    .start();
            return;
        }
        if (distance <= SWEEP_RANGE) {
            move = SWEEP;
            begin(target, "sweep");
            cue("fort_warlord", "sweep", SWEEP_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, Math.toRadians(130)))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 2, getY() + 1.2, getZ() + f.z * 2, 6, 1.5, 0.2, 1.5, 0))
                    .onHit((server, player) -> player.hurt(attackSource(), damage()))
                    .start();
        }
    }

    @Override
    protected String bossId() {
        return "fort_warlord";
    }

    @Override
    protected String minionId() {
        return "skyabyss:bandit";
    }

    @Override
    protected int length() {
        return switch (move) {
            case LEAP -> LEAP_WARN + 12;
            case CHARGE -> CHARGE_WARN + 14;
            default -> SWEEP_WARN + 12;
        };
    }

    @Override
    protected void finish() {
        if (move == LEAP) {
            // 着地のあとは鉈が地に食い込んで隙
            opening();
        }
        cooldown = move == SWEEP ? 14 + random.nextInt(10) : 26;
        super.finish();
    }
}
