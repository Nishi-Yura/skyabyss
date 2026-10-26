package jp.revati.skyabyss.entity.foe;

import java.util.function.Supplier;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 名のある強敵の大物 (岩背の大猪・潮招きの大蟹・雷角の大羊・灰被りの大蜥蜴) の共通: いま出している技 (move) と、突進の手当て。
// 突進: 床の長い帯が満ちたら、帯の向きへ dash tick 走る (走る間に帯の人に当たる = 帯の当たりそのもの)。走り終わりに少し止まる
public abstract class RegionBoss extends ContinentFoe {
    protected int move;
    private int dashFrom;
    private int dashTicks;
    private double dashSpeed;

    protected RegionBoss(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, String... actions) {
        super(type, level, animPrefix, ambient, hurt, death, actions);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 3.0));
    }

    // 前の長い帯の突進。length = 帯の長さ (走る距離)
    protected void charge(LivingEntity target, int m, String action, int warn, double length, double halfWidth, double speed, Supplier<SoundEvent> sound) {
        move = m;
        begin(target, action);
        Vec3 f = forward();
        dashFrom = warn;
        dashSpeed = speed;
        dashTicks = (int) Math.ceil(length / speed);
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), length, halfWidth))
                .floor(getY()).warn(warn).danger().owner(this).target(target)
                .startSound(sound.get(), 1.4f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 12, 0.6, 0.1, 0.6, 0.02))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 1.2f);
                    player.knockback(1.4, -f.x, -f.z);
                })
                .start();
    }

    protected boolean dashing() {
        return dashTicks > 0 && act >= dashFrom && act < dashFrom + dashTicks;
    }

    @Override
    protected void during(int tick) {
        if (dashing()) {
            Vec3 f = forward();
            setDeltaMovement(f.x * dashSpeed, getDeltaMovement().y, f.z * dashSpeed);
            if (tick % 3 == 0) {
                server().sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 3, 0.5, 0.05, 0.5, 0.02);
            }
        }
    }

    @Override
    protected void finish() {
        dashTicks = 0;
        move = 0;
    }
}
