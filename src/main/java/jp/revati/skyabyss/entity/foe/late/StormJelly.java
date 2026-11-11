package jp.revati.skyabyss.entity.foe.late;

import jp.revati.skyabyss.combat.prop.PropFx;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 雷雲くらげ (嵐の高地。まわりに効く。A 雲のくらげ): 地面から浮いた傘 (形の上で 2.5 ブロック) が人に寄る。
// 4 より近いと縮んで光が強まり (予告 1.2 秒、自分の足もとに円 5)、放電: 円の中の人に傷 + 0.6 秒の足止め。
// 放電のあと 3 秒は光が消えて柔らかい (受ける傷 +30%)
public class StormJelly extends LateFoe {
    public static final int WARN = 24;
    public static final double RADIUS = 5;
    public static final double START_RANGE = 4;
    public static final int END = WARN + 12;
    public static final int SOFT = 60;
    public static final float SOFT_TAKEN = 1.3f;

    public StormJelly(EntityType<? extends Monster> type, Level level) {
        super(type, level, "storm_jelly", MobSounds.STRAY_WISP_AMBIENT, MobSounds.STRAY_WISP_HURT, MobSounds.STRAY_WISP_DEATH, "charge", "burst", "hurt", "death");
        softTaken = SOFT_TAKEN;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, START_RANGE - 1));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "charge");
        double x = getX();
        double z = getZ();
        Telegraph.in(server(), TelegraphShape.circle(x, z, RADIUS)).prop(PropFx.ring("lightning", 6).size(0.6f))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.STRAY_WISP_PULSE.get(), 1.2f, 0.6f)
                .victims(this::canHit)
                .onImpact(server -> {
                    playAction("burst");
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, getY() + 2.5, z, 80, RADIUS * 0.4, 1.2, RADIUS * 0.4, 0.3);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 9), this);
                })
                .start();
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        softTicks = SOFT;
        cooldown = SOFT + 20;
    }
}
