package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 潮招きの大蟹 (docs/design/44 の 5): 潮の浜の名のある強敵。横歩きで寄る。
//   大ばさみの叩きつけ: 振り上げて (予告 1.0 秒、前の帯 6・幅 2.4) 叩きつける
//   泡: 体を前へ傾けて (予告 1.2 秒、前の扇 7) 泡を吹く。扇の中の人は押し流され、3 秒鈍る
public class TideCrab extends RegionBoss {
    static final int SLAM = 1;
    static final int SPRAY = 2;
    public static final int SLAM_WARN = 20;
    public static final double SLAM_LENGTH = 6;
    public static final int SPRAY_WARN = 24;
    public static final double SPRAY_RANGE = 7;
    private int lastSpray = -100;

    public TideCrab(EntityType<? extends Monster> type, Level level) {
        super(type, level, "tide_crab", MobSounds.TIDE_CRAB_AMBIENT, MobSounds.TIDE_CRAB_HURT, MobSounds.TIDE_CRAB_DEATH, "slam", "spray", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160)
                .add(Attributes.ATTACK_DAMAGE, 10)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance <= SPRAY_RANGE - 0.5 && distance > 3 && tickCount - lastSpray > 140) {
            move = SPRAY;
            lastSpray = tickCount;
            begin(target, "spray");
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SPRAY_RANGE, Math.toRadians(35)))
                    .floor(getY()).warn(SPRAY_WARN).danger().owner(this).target(target)
                    .startSound(MobSounds.TIDE_CRAB_SPRAY.get(), 1.4f, 1.0f)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        for (double d = 1; d <= SPRAY_RANGE; d += 1) {
                            server.sendParticles(ParticleTypes.BUBBLE_POP, getX() + f.x * d, getY() + 0.8, getZ() + f.z * d, 6, d * 0.25, 0.3, d * 0.25, 0.05);
                        }
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.5f);
                        player.knockback(1.6, -f.x, -f.z);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                    })
                    .start();
            return;
        }
        if (distance > SLAM_LENGTH - 0.5) {
            return;
        }
        move = SLAM;
        begin(target, "slam");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), SLAM_LENGTH, 1.2))
                .floor(getY()).warn(SLAM_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, getX() + f.x * 4, getY(), getZ() + f.z * 4, MobSounds.TIDE_CRAB_SLAM.get(), SoundSource.HOSTILE, 1.5f, 1f);
                    server.sendParticles(ParticleTypes.SPLASH, getX() + f.x * 4, getY() + 0.3, getZ() + f.z * 4, 40, 1.0, 0.2, 1.0, 0.1);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 1.2f);
                    player.knockback(0.8, -f.x, -f.z);
                })
                .start();
    }

    @Override
    protected int length() {
        return move == SPRAY ? SPRAY_WARN + 20 : SLAM_WARN + 20;
    }

    @Override
    protected void finish() {
        cooldown = 20 + random.nextInt(15);
        super.finish();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
