package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.prop.PropFx;
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

// 雷角の兜虫 (docs/design/55 の 4 の 7、00 の 378 = B。雷晶の空洞の道中)。
// 頭を上げて二本の角の間に雷を溜め (角が光る)、前へ放つ (前の帯、予告 1.2 秒)。当たると痺れて足が重くなる
public class HornBeetle extends ContinentFoe {
    public static final int WARN = 24;
    public static final double LENGTH = 10;
    public static final double HALF_WIDTH = 0.8;
    public static final double START_RANGE = 9;

    public HornBeetle(EntityType<? extends Monster> type, Level level) {
        super(type, level, "horn_beetle", () -> SoundEvents.SILVERFISH_AMBIENT, () -> SoundEvents.SHULKER_HURT, () -> SoundEvents.SHULKER_DEATH,
                "bolt", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 22);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 5.0));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "bolt");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH)).prop(PropFx.along("lightning").size(0.55f))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f)
                .victims(this::canHit)
                .onImpact(server -> {
                    for (int i = 1; i <= LENGTH; i++) {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + f.x * i, getY() + 0.7, getZ() + f.z * i, 3, 0.1, 0.2, 0.1, 0.05);
                    }
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 1.2f);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), this);
                })
                .start();
    }

    @Override
    protected void during(int tick) {
        if (tick < WARN && tick % 3 == 0) {
            Vec3 f = forward();
            server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + f.x * 1.1, getY() + 0.9, getZ() + f.z * 1.1, 2, 0.1, 0.15, 0.1, 0.02);
        }
    }

    @Override
    protected int length() {
        return WARN + 8;
    }

    @Override
    protected void finish() {
        cooldown = 40 + random.nextInt(20);
    }
}
