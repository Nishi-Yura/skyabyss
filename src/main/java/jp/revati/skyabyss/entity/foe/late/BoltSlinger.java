package jp.revati.skyabyss.entity.foe.late;

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
import net.minecraft.world.phys.Vec3;

// 雷石投げ (嵐の高地。遠くから撃つ。2026-11-09 の頁の A 岩角の投石手): 8〜16 の距離を保ち、帯電した石を山なりに投げる
// (予告 1.0 秒: 狙った人の足もとに円 2.0 → 落ちて弾ける。傷 + 鈍足 2 秒)。寄られると (3 より近い) 後ろへ跳んで離れる
public class BoltSlinger extends LateFoe {
    public static final int WARN = 20;
    public static final double RADIUS = 2.0;
    public static final int END = 24;
    public static final int HOP_END = 10;
    public static final double MIN_RANGE = 8;
    public static final double MAX_RANGE = 16;
    private boolean hopping;

    public BoltSlinger(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bolt_slinger", MobSounds.SPINE_HARE_AMBIENT, MobSounds.SPINE_HARE_HURT, MobSounds.SPINE_HARE_DEATH, "throw", "hop", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 18)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new KeepDistanceGoal(this, MIN_RANGE, MAX_RANGE));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance < 3) {
            // 跳び退き: 人から離れる向きへ小さく跳ぶ
            hopping = true;
            begin(target, "hop");
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(0.7);
            setDeltaMovement(away.x, 0.42, away.z);
            return;
        }
        if (distance > MAX_RANGE + 2) {
            return;
        }
        hopping = false;
        begin(target, "throw");
        double x = target.getX();
        double z = target.getZ();
        double y = target.getY();
        Telegraph.in(server(), TelegraphShape.circle(x, z, RADIUS))
                .floor(y).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.SPINE_HARE_SHOOT.get(), 1.0f, 0.8f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.3, z, 40, RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.2))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                })
                .start();
    }

    @Override
    protected int length() {
        return hopping ? HOP_END : END;
    }

    @Override
    protected void finish() {
        if (hopping) {
            cooldown = 10;
            retreat = 20;
        } else {
            cooldown = 40 + random.nextInt(25);
        }
        hopping = false;
    }
}
