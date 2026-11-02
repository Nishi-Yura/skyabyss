package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 晶の目玉 (docs/design/55 の 4 の 7、00 の 379 = B。雷晶の空洞の道中)。
// 人から 5〜9 離れて浮き、見つめた所 (人の足もと) に雷を落とす (円、予告 1.2 秒。歩けば外れる)
public class CrystalEye extends DungeonFloater {
    public static final int WARN = 24;
    public static final double RADIUS = 2.2;
    public static final double MAX_RANGE = 14;

    public CrystalEye(EntityType<? extends Monster> type, Level level) {
        super(type, level, "crystal_eye", () -> SoundEvents.AMETHYST_BLOCK_CHIME, () -> SoundEvents.AMETHYST_BLOCK_HIT, () -> SoundEvents.AMETHYST_BLOCK_BREAK,
                2.2, 0.06, 5.0, 9.0, "gaze", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 22);
    }

    @Override
    protected void tryAct(LivingEntity target, double distance) {
        if (distance > MAX_RANGE) {
            return;
        }
        begin(target, "gaze");
        Vec3 at = target.position();
        Telegraph.in(server(), TelegraphShape.circle(at.x, at.z, RADIUS))
                .floor(at.y).warn(WARN).danger().owner(this).target(target)
                .startSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.4f)
                .victims(this::canHit)
                .onImpact(server -> {
                    for (int i = 0; i < 10; i++) {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + i * 0.6, at.z, 4, 0.15, 0.2, 0.15, 0.02);
                    }
                    server.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.8f, 1.4f);
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.2f))
                .start();
    }

    @Override
    protected int length() {
        return WARN + 8;
    }

    @Override
    protected void finish() {
        cooldown = 50 + random.nextInt(30);
    }
}
