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

// 大海月の巫女 (沈んだ神殿の中ボス。2026-11-09 のラフで C)。珊瑚の中庭に漂う大きな海月と、その傘の下に吊られた巫女。
//   触手の鞭 前へ触手を払う (前の扇を二枚、予告 0.9 秒。少し焼け続ける)
//   毒の雨   人の足もとと、まわりに三つの円に、傘から毒の滴を降らせる (予告 1.1 秒)
//   脈打ち   傘を縮めて、まわりの輪を二度打つ (予告 1.0 秒、間 0.6 秒)
// 傘は高く、触手が床まで届く。ゆっくり漂う
public class JellyPriestess extends DungeonBoss {
    static final int LASH = 1;
    static final int RAIN = 2;
    static final int PULSE = 3;
    public static final int LASH_WARN = 18;
    public static final int RAIN_WARN = 22;
    public static final int PULSE_WARN = 20;
    public static final double PULSE_RADIUS = 5.0;
    private int lastRain = -100;
    private int lastPulse = -140;

    public JellyPriestess(EntityType<? extends Monster> type, Level level) {
        super(type, level, "jelly_priestess", () -> SoundEvents.CONDUIT_AMBIENT, () -> SoundEvents.SLIME_HURT, () -> SoundEvents.CONDUIT_DEACTIVATE,
                "lash", "rain", "pulse", "intro", "scream", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 170)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
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
        return 72;
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
        return "jelly_priestess";
    }

    @Override
    protected double reach() {
        return 5.0;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > 5 && tickCount - lastRain > 110) {
            move = RAIN;
            lastRain = tickCount;
            volley(target, "rain", RAIN_WARN, 3, 2.4, 0.9f, Blocks.PRISMARINE.defaultBlockState());
            return;
        }
        if (distance < PULSE_RADIUS && tickCount - lastPulse > 150) {
            move = PULSE;
            lastPulse = tickCount;
            spinRings(target, "pulse", PULSE_WARN, PULSE_RADIUS, 2, 12, 0.8f);
            return;
        }
        if (distance < 7) {
            move = LASH;
            spray(target, "lash", LASH_WARN, 6.5, 60, 14, 0.4f, ParticleTypes.GLOW_SQUID_INK);
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case RAIN -> RAIN_WARN + 12;
            case PULSE -> PULSE_WARN + 12 + 14;
            default -> LASH_WARN + 10 + 14;
        };
    }

    @Override
    protected void finish() {
        if (move == PULSE) {
            opening();
        }
        cooldown = 18 + random.nextInt(14);
        super.finish();
    }

    @Override
    protected net.minecraft.world.level.block.state.BlockState hazardBlock() {
        return Blocks.PRISMARINE.defaultBlockState();
    }
}
