package jp.revati.skyabyss.entity.foe.late;

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

// 峰の甲羅 (嵐の高地。守りを固める。A 峰の甲羅亀): 人が 6 より近いと甲羅に籠る (0.8 秒で首を引く。籠っているあいだ前からの傷は 3 割)。
// 籠って 1.0 秒の予告 (前の帯 5) で前へ体当たり: 傷 + 打ち上げ。正面から叩き続けても削れない。横・後ろへ回り込むか、体当たりのあとを叩く
public class CragTortoise extends LateFoe {
    public static final int GUARD_WARN = 16;
    public static final int RAM_WARN = 20;
    public static final int END = GUARD_WARN + RAM_WARN + 12;
    public static final double START_RANGE = 6;
    public static final double LENGTH = 5;
    public static final double HALF_WIDTH = 0.9;
    public static final float FRONT_TAKEN = 0.3f;

    public CragTortoise(EntityType<? extends Monster> type, Level level) {
        super(type, level, "crag_tortoise", MobSounds.ROCK_SHELL_BEETLE_AMBIENT, MobSounds.ROCK_SHELL_BEETLE_HURT, MobSounds.ROCK_SHELL_BEETLE_DEATH,
                "guard", "ram", "hurt", "death");
        guardTaken = FRONT_TAKEN;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.16)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 3.0));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "guard");
        guardTicks = END;
    }

    @Override
    protected void during(int tick) {
        LivingEntity target = getTarget();
        if (tick != GUARD_WARN || target == null) {
            return;
        }
        playAction("ram");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH))
                .floor(getY()).warn(RAM_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.ROCK_SHELL_BEETLE_CHARGE.get(), 1.0f, 0.8f)
                .victims(this::canHit)
                .onImpact(server -> {
                    setDeltaMovement(f.x * 0.9, 0.1, f.z * 0.9);
                    server.sendParticles(ParticleTypes.CLOUD, getX() + f.x * 2, getY() + 0.4, getZ() + f.z * 2, 20, 0.6, 0.2, 0.6, 0.05);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.setDeltaMovement(player.getDeltaMovement().add(f.x * 0.4, 0.55, f.z * 0.4));
                    player.hurtMarked = true;
                })
                .start();
    }

    @Override
    protected void onGuarded() {
        level().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_SOLDIER_BLOCK.get(), SoundSource.HOSTILE, 1f, 0.6f + random.nextFloat() * 0.2f);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        guardTicks = 0;
        cooldown = 50 + random.nextInt(20);
    }
}
