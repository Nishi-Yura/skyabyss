package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 骸の弓兵 (docs/design/44): 長弓で弧を描く矢を射る。狙った人の足もとに円が出て (予告 1.0 秒)、満ちると矢が降る (壁の裏にも届く)。
// 寄られると弓で横に払って (予告 0.5 秒、前の扇) 下がる
public class BoneBowman extends BoneSoldier {
    public static final int SHOOT_WARN = 20;
    public static final double SHOOT_RADIUS = 1.7;
    public static final int SHOOT_END = SHOOT_WARN + 8;
    public static final int BASH_WARN = 10;
    public static final double BASH_RANGE = 2.6;
    public static final double BASH_HALF = Math.toRadians(60);
    public static final int BASH_END = BASH_WARN + 12;
    public static final double MIN_RANGE = 6;
    public static final double MAX_RANGE = 16;
    private boolean bashing;

    public BoneBowman(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_bowman", "shoot", "bash", "rise", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16)
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
        if (distance <= BASH_RANGE + 0.3) {
            bashing = true;
            begin(target, "bash");
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BASH_RANGE, BASH_HALF))
                    .floor(getY()).warn(BASH_WARN).danger().owner(this).target(target)
                    .startSound(MobSounds.BANDIT_SWING.get(), 0.7f, 1.2f)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.7f);
                        player.knockback(1.0, -f.x, -f.z);
                    })
                    .start();
            return;
        }
        if (distance < 4 || distance > MAX_RANGE + 2) {
            return;
        }
        bashing = false;
        begin(target, "shoot");
        double tx = target.getX();
        double tz = target.getZ();
        double ty = target.getY();
        Telegraph.in(server(), TelegraphShape.circle(tx, tz, SHOOT_RADIUS))
                .floor(ty).warn(SHOOT_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.BANDIT_ARCHER_DRAW.get(), 0.8f, 0.7f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, getX(), getY(), getZ(), MobSounds.BONE_BOWMAN_SHOOT.get(), SoundSource.HOSTILE, 1f, 0.9f + random.nextFloat() * 0.2f);
                    server.sendParticles(ParticleTypes.CRIT, tx, ty + 2.5, tz, 14, 0.6, 1.2, 0.6, 0.02);
                    server.sendParticles(ParticleTypes.POOF, tx, ty + 0.1, tz, 6, 0.6, 0.05, 0.6, 0.01);
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage()))
                .start();
    }

    @Override
    protected int length() {
        return bashing ? BASH_END : SHOOT_END;
    }

    @Override
    protected void finish() {
        if (bashing) {
            retreat = 26;
            cooldown = 30;
        } else {
            cooldown = 45 + random.nextInt(25);
        }
        bashing = false;
    }
}
