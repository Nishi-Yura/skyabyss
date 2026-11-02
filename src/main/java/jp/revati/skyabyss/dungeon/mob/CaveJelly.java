package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 洞の水母 (docs/design/55 の 4 の 7、00 の 376 = C。潮の洞の道中)。
// ゆっくり漂って寄り、傘をすぼめて光る糸を広げる (まわりの輪、予告 0.8 秒)。当たると痺れて足が重くなる
public class CaveJelly extends DungeonFloater {
    public static final int WARN = 16;
    public static final double RADIUS = 3.4;
    public static final double START_RANGE = 3.0;

    public CaveJelly(EntityType<? extends Monster> type, Level level) {
        super(type, level, "cave_jelly", () -> SoundEvents.SQUID_AMBIENT, () -> SoundEvents.SQUID_HURT, () -> SoundEvents.SQUID_DEATH,
                0.6, 0.05, 0.0, START_RANGE - 0.8, "pulse", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 18);
    }

    @Override
    protected void tryAct(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "pulse");
        double floor = target.getY();
        Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), RADIUS))
                .floor(floor).warn(WARN).danger().owner(this).target(target)
                .startSound(SoundEvents.SQUID_SQUIRT, 0.8f, 1.4f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.GLOW, getX(), getY() + 0.8, getZ(), 24, 1.2, 0.8, 1.2, 0.02))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                })
                .start();
    }

    @Override
    protected int length() {
        return WARN + 6;
    }

    @Override
    protected void finish() {
        cooldown = 50 + random.nextInt(30);
    }
}
