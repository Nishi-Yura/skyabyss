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

// 黒石の甲殻 (灰の荒野。守りを固める。A 黒石の蠍): 人が前 6 より近いと鋏を閉じて構える (0.6 秒。構えているあいだ前からの傷は 3 割)。
// 構えたまま尾の針で突く (予告 0.9 秒、前の細い帯 4.5): 傷 + 毒 2 秒。突いたあと 1.5 秒は構えが解ける。突きを避けて、解けた所か横・後ろから叩く
public class BasaltScorpion extends LateFoe {
    public static final int GUARD_WARN = 12;
    public static final int STRIKE_WARN = 18;
    public static final int END = GUARD_WARN + STRIKE_WARN + 6;
    public static final double START_RANGE = 6;
    public static final double LENGTH = 4.5;
    public static final double HALF_WIDTH = 0.6;
    public static final float FRONT_TAKEN = 0.3f;
    public static final int OPEN = 30;

    public BasaltScorpion(EntityType<? extends Monster> type, Level level) {
        super(type, level, "basalt_scorpion", MobSounds.ROCK_SHELL_BEETLE_AMBIENT, MobSounds.ROCK_SHELL_BEETLE_HURT, MobSounds.ROCK_SHELL_BEETLE_DEATH,
                "guard", "strike", "hurt", "death");
        guardTaken = FRONT_TAKEN;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.05, 3.2));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE) {
            return;
        }
        begin(target, "guard");
        guardTicks = GUARD_WARN + STRIKE_WARN;
    }

    @Override
    protected void during(int tick) {
        LivingEntity target = getTarget();
        if (tick != GUARD_WARN || target == null) {
            return;
        }
        playAction("strike");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LENGTH, HALF_WIDTH))
                .floor(getY()).warn(STRIKE_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.ELDER_SENTRY_SWING.get(), 0.9f, 1.3f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.CRIT, getX() + f.x * 3, getY() + 0.8, getZ() + f.z * 3, 8, 0.3, 0.2, 0.3, 0.1))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 40, 0), this);
                })
                .start();
    }

    @Override
    protected void onGuarded() {
        level().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_SOLDIER_BLOCK.get(), SoundSource.HOSTILE, 1f, 0.5f + random.nextFloat() * 0.2f);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        guardTicks = 0;
        cooldown = OPEN + 10 + random.nextInt(20);
    }
}
