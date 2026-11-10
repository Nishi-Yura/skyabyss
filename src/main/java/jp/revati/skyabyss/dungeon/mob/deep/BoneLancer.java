package jp.revati.skyabyss.dungeon.mob.deep;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.dungeon.mob.DungeonBoss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨馬の槍騎兵 (崩れた見張りの砦の中ボス。2026-11-09 のラフで C)。内郭を骨の馬で駆ける野盗の騎兵。
//   槍の突撃 離れた人へ、前の長い帯を駆け抜ける (予告 1.2 秒)。走り終わりに小さな隙
//   棹立ち   寄った人の足もとへ馬が前脚を振り下ろす (円 3、予告 1.0 秒。外へ輪の衝撃)
//   槍の薙ぎ 寄った人へ槍を横に払う (前の扇、予告 0.7 秒)
public class BoneLancer extends DungeonBoss {
    static final int CHARGE = 1;
    static final int REAR = 2;
    static final int SWEEP = 3;
    public static final int CHARGE_WARN = 24;
    public static final double CHARGE_LENGTH = 13;
    public static final int REAR_WARN = 20;
    public static final int SWEEP_WARN = 14;
    public static final double SWEEP_RANGE = 4.6;
    private int lastRear = -120;

    public BoneLancer(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_lancer", () -> SoundEvents.SKELETON_HORSE_AMBIENT, () -> SoundEvents.SKELETON_HORSE_HURT, () -> SoundEvents.SKELETON_HORSE_DEATH,
                "charge", "rear", "sweep", "intro", "scream", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // 登場の動きと後半の咆哮 (2026-11-11 の声「他のボスたちもこの方向性で」。アニメ intro / scream)
    @Override
    protected String introAnim() {
        return "intro";
    }

    @Override
    protected int introLength() {
        return 76;
    }

    @Override
    protected int introCardAt() {
        return 48;
    }

    @Override
    protected void enterPhase2(LivingEntity target) {
        playAction("scream");
        super.enterPhase2(target);
    }

    @Override
    protected String bossId() {
        return "bone_lancer";
    }

    @Override
    protected double reach() {
        return 5.0;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance >= 6 && distance <= CHARGE_LENGTH + 2) {
            cue(bossId(), "charge", CHARGE_WARN);
            charge(target, CHARGE, "charge", CHARGE_WARN, CHARGE_LENGTH, 1.4, 0.9, () -> SoundEvents.HORSE_GALLOP);
            return;
        }
        if (distance < 6 && tickCount - lastRear > 140) {
            move = REAR;
            lastRear = tickCount;
            leapAt(target, "rear", REAR_WARN, 3.0, 1.0f);
            return;
        }
        if (distance <= SWEEP_RANGE) {
            move = SWEEP;
            begin(target, "sweep");
            cue(bossId(), "sweep", SWEEP_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, Math.toRadians(75)))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 2, getY() + 1.6, getZ() + f.z * 2, 3, 1, 0.2, 1, 0))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage());
                        player.knockback(1.2, -f.x, -f.z);
                    })
                    .start();
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case CHARGE -> CHARGE_WARN + 24;
            case REAR -> REAR_WARN + 16;
            default -> SWEEP_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == CHARGE) {
            opening();
        }
        cooldown = move == CHARGE ? 34 : 16 + random.nextInt(12);
        super.finish();
    }

    @Override
    protected net.minecraft.world.level.block.state.BlockState hazardBlock() {
        return Blocks.BONE_BLOCK.defaultBlockState();
    }
}
