package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.foe.ContinentFoe;
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
import net.minecraft.world.phys.Vec3;

// 沈み船の水夫 (docs/design/55 の 4 の 7、00 の 375 = A。潮の洞の道中)。
// 離れて立ち、銛を投げる (人へ向く細い帯、予告 0.8 秒)。当たると銛の綱で少し引き寄せられ、足が重くなる
public class DrownedSailor extends ContinentFoe {
    public static final int WARN = 16;
    public static final double HALF_WIDTH = 0.6;
    public static final double MAX_RANGE = 15;

    public DrownedSailor(EntityType<? extends Monster> type, Level level) {
        super(type, level, "drowned_sailor", () -> SoundEvents.DROWNED_AMBIENT, () -> SoundEvents.DROWNED_HURT, () -> SoundEvents.DROWNED_DEATH,
                "throw", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 22)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.ARMOR, 1)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 26);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new KeepDistanceGoal(this, 5, 10));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance < 2.5 || distance > MAX_RANGE) {
            return;
        }
        begin(target, "throw");
        Vec3 f = forward();
        double length = Math.min(MAX_RANGE + 1, distance + 3);
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), length, HALF_WIDTH))
                .floor(target.getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7f, 1.2f)
                .victims(this::clearShot)
                .onImpact(server -> {
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1f, 0.9f);
                    for (int i = 1; i < length; i += 2) {
                        server.sendParticles(ParticleTypes.BUBBLE_POP, getX() + f.x * i, getY() + 1.2, getZ() + f.z * i, 2, 0.05, 0.05, 0.05, 0.01);
                    }
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    Vec3 pull = position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(0.55);
                    player.push(pull.x, 0.1, pull.z);
                    player.hurtMarked = true;
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), this);
                })
                .start();
    }

    @Override
    protected int length() {
        return WARN + 10;
    }

    @Override
    protected void finish() {
        cooldown = 34 + random.nextInt(20);
    }
}
