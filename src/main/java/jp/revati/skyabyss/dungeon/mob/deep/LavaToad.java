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

// 熔岩の大蛙 (熔岩の管の坑の中ボス。2026-11-09 のラフで B)。背に熔岩の噴き口を負う大きな蛙。
//   跳びかかり 人のいる所へ跳んで降りる (円 3.4、予告 1.1 秒。外へ輪の衝撃。燃える)
//   噴き口     背の噴き口から熔岩を打ち上げ、人の足もとと、まわりに三つの円へ落とす (予告 1.2 秒)
//   舌         前へ長い舌を伸ばし、当たった人を引き寄せる (前の帯 8、予告 0.8 秒)
public class LavaToad extends DungeonBoss {
    static final int HOP = 1;
    static final int ERUPT = 2;
    static final int TONGUE = 3;
    public static final int HOP_WARN = 22;
    public static final int ERUPT_WARN = 24;
    public static final int TONGUE_WARN = 16;
    public static final double TONGUE_LENGTH = 8;
    private int lastHop = -200;
    private int lastErupt = -80;

    public LavaToad(EntityType<? extends Monster> type, Level level) {
        super(type, level, "lava_toad", () -> SoundEvents.FROG_AMBIENT, () -> SoundEvents.FROG_HURT, () -> SoundEvents.FROG_DEATH,
                "hop", "erupt", "tongue", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected String bossId() {
        return "lava_toad";
    }

    @Override
    protected double reach() {
        return 5.0;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > 6 && tickCount - lastHop > 110) {
            move = HOP;
            lastHop = tickCount;
            leapAt(target, "hop", HOP_WARN, 3.4, 1.1f);
            return;
        }
        if (tickCount - lastErupt > 140) {
            move = ERUPT;
            lastErupt = tickCount;
            volley(target, "erupt", ERUPT_WARN, 3, 2.4, 0.9f, Blocks.MAGMA_BLOCK.defaultBlockState());
            return;
        }
        if (distance <= TONGUE_LENGTH) {
            move = TONGUE;
            begin(target, "tongue");
            cue(bossId(), "tongue", TONGUE_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), TONGUE_LENGTH, 0.9))
                    .floor(getY()).warn(TONGUE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.FLAME, getX() + f.x * 3, getY() + 1.0, getZ() + f.z * 3, 12, 1.2, 0.2, 1.2, 0.02))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.8f);
                        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 40));
                        // 引き寄せる
                        player.setDeltaMovement(-f.x * 1.1, 0.35, -f.z * 1.1);
                        player.hurtMarked = true;
                    })
                    .start();
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case HOP -> HOP_WARN + 18;
            case ERUPT -> ERUPT_WARN + 12;
            default -> TONGUE_WARN + 14;
        };
    }

    @Override
    protected void finish() {
        if (move == HOP) {
            opening();
        }
        cooldown = 16 + random.nextInt(14);
        super.finish();
    }

    @Override
    protected net.minecraft.world.level.block.state.BlockState hazardBlock() {
        return Blocks.MAGMA_BLOCK.defaultBlockState();
    }
}
