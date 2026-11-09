package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;

// 牛頭の鍛冶番 (docs/design/55 の 4 の 7、00 の 383 = B。熔けた坑道の中ボス)。
//   掴み投げ 大きな火ばさみで前を掴み、後ろへ放り投げる (前の帯、予告 0.8 秒。当たると燃える)
//   盾打ち   焼けた鉄の板の盾で前を打つ (前の扇、予告 0.8 秒、強く飛ばす)
//   叩き伏せ 体力が 6 割を切ると、盾と火ばさみを振り上げて叩きつける (まわりの輪、予告 1.05 秒。燃える)
//   盾: 前 (向きから ±60 度) からの傷は 0.35 倍。横と後ろは通る
public class OxSmith extends DungeonBoss {
    static final int GRAB = 1;
    static final int BASH = 2;
    static final int SLAM = 3;
    static final int HURL = 4;
    static final int BREATH = 5;
    public static final int HURL_WARN = 22;
    public static final int BREATH_WARN = 18;
    private int lastHurl = -120;
    private int lastBreath = -160;
    public static final int GRAB_WARN = 16;
    public static final double GRAB_LENGTH = 6;
    public static final int BASH_WARN = 16;
    public static final double BASH_RANGE = 4.2;
    public static final int SLAM_WARN = 21;
    public static final double SLAM_RADIUS = 6;
    public static final float FRONT_TAKEN = 0.35f;
    private int lastSlam = -300;
    private int lastBash = -100;

    public OxSmith(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ox_smith", () -> SoundEvents.RAVAGER_AMBIENT, () -> SoundEvents.RAVAGER_HURT, () -> SoundEvents.RAVAGER_DEATH,
                "grab", "bash", "slam", "hurl", "breath", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 190)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        // 鉄塊投げ (2026-11-08 の作り込み): 焼けた鉄を投げる (人の足もとの大きな円と、まわりに一つ)
        if (distance > 5 && tickCount - lastHurl > 140) {
            move = HURL;
            lastHurl = tickCount;
            volley(target, "hurl", HURL_WARN, 1, 3.0, 1.2f, net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState());
            return;
        }
        // 火の息 (2026-11-08 の作り込み): 首を振って前へ火を吹く (前の扇を二枚、少しずらして 1.5 秒焼く)
        if (distance < 8 && tickCount - lastBreath > 200) {
            move = BREATH;
            lastBreath = tickCount;
            spray(target, "breath", BREATH_WARN, 8, 50, 30, 0.35f, ParticleTypes.FLAME);
            return;
        }
        if (hp() < 0.6f && distance < SLAM_RADIUS && tickCount - lastSlam > 220) {
            move = SLAM;
            lastSlam = tickCount;
            begin(target, "slam");
            cue("ox_smith", "slam", SLAM_WARN);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), SLAM_RADIUS))
                    .floor(getY()).warn(SLAM_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 30, SLAM_RADIUS * 0.4, 0.1, SLAM_RADIUS * 0.4, 0.1);
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.5f);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 1.2f);
                        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 60));
                    })
                    .start();
            return;
        }
        Vec3 f = forward();
        if (distance < BASH_RANGE && tickCount - lastBash > 60) {
            move = BASH;
            lastBash = tickCount;
            begin(target, "bash");
            cue("ox_smith", "bash", BASH_WARN);
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BASH_RANGE, Math.toRadians(60)))
                    .floor(getY()).warn(BASH_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.9f);
                        player.knockback(1.8, -f.x, -f.z);
                    })
                    .start();
            return;
        }
        if (distance > GRAB_LENGTH + 0.5) {
            return;
        }
        move = GRAB;
        begin(target, "grab");
        cue("ox_smith", "grab", GRAB_WARN);
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), GRAB_LENGTH, 0.9))
                .floor(getY()).warn(GRAB_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 40));
                    // 頭の上を越えて後ろへ放る
                    player.setDeltaMovement(f.x * 0.9, 0.9, f.z * 0.9);
                    player.hurtMarked = true;
                })
                .start();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (!level().isClientSide && from != null && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            if (look.dot(toSource) > 0.5) {
                amount *= FRONT_TAKEN;
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.6f, 1.4f);
                ((net.minecraft.server.level.ServerLevel) level()).sendParticles(ParticleTypes.LAVA, getX() + look.x * 1.4, getY() + 1.6, getZ() + look.z * 1.4, 4,
                        0.3, 0.3, 0.3, 0.02);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected String bossId() {
        return "ox_smith";
    }

    @Override
    protected int length() {
        return switch (move) {
            case GRAB -> GRAB_WARN + 22;
            case BASH -> BASH_WARN + 10;
            case HURL -> HURL_WARN + 12;
            case BREATH -> BREATH_WARN + 10 + 30;
            default -> SLAM_WARN + 16;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == SLAM ? 30 + random.nextInt(14) : 16 + random.nextInt(12);
        super.finish();
    }
}
