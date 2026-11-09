package jp.revati.skyabyss.dungeon.mob.deep;

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

// 雷の大梟 (吹雪の氷穴の中ボス。2026-11-09 のラフで C)。氷の舞台に降りた、雷を帯びた大きな梟。
//   急降下 舞い上がって人のいる所へ降りる (円 3.2、予告 1.2 秒。外へ輪の衝撃)
//   雷の羽 羽ばたいて前へ雷の風 (前の扇を二枚、予告 0.9 秒)
//   落雷   人の足もとと、まわりに二つの円に雷を落とす (予告 1.2 秒)
public class ThunderOwl extends DungeonBoss {
    static final int DIVE = 1;
    static final int GUST = 2;
    static final int STRIKE = 3;
    public static final int DIVE_WARN = 24;
    public static final int GUST_WARN = 18;
    public static final int STRIKE_WARN = 24;
    private int lastDive = -100;
    private int lastStrike = -80;

    public ThunderOwl(EntityType<? extends Monster> type, Level level) {
        super(type, level, "thunder_owl", () -> SoundEvents.PARROT_AMBIENT, () -> SoundEvents.PARROT_HURT, () -> SoundEvents.PARROT_DEATH,
                "dive", "gust", "strike", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 180)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected String bossId() {
        return "thunder_owl";
    }

    @Override
    protected double reach() {
        return 5.0;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > 6 && tickCount - lastDive > 120) {
            move = DIVE;
            lastDive = tickCount;
            leapAt(target, "dive", DIVE_WARN, 3.2, 1.1f);
            return;
        }
        if (tickCount - lastStrike > 130) {
            move = STRIKE;
            lastStrike = tickCount;
            volley(target, "strike", STRIKE_WARN, 2, 2.2, 1.0f, Blocks.PACKED_ICE.defaultBlockState());
            server().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.4f);
            return;
        }
        if (distance < 8) {
            move = GUST;
            spray(target, "gust", GUST_WARN, 7.5, 70, 10, 0.45f, ParticleTypes.ELECTRIC_SPARK);
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case DIVE -> DIVE_WARN + 18;
            case STRIKE -> STRIKE_WARN + 12;
            default -> GUST_WARN + 10 + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == DIVE) {
            opening();
        }
        cooldown = 16 + random.nextInt(14);
        super.finish();
    }

    @Override
    protected net.minecraft.world.level.block.state.BlockState hazardBlock() {
        return Blocks.PACKED_ICE.defaultBlockState();
    }
}
