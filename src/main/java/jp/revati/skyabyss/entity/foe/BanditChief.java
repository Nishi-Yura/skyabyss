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

// 野盗の頭目 (docs/design/44): 峠と野盗の野営の頭。両手の大きな手斧で前を広く薙ぐ (予告 0.8 秒、前の広い扇)。
// ときどき斧を掲げて吠え (1.8 秒、隙)、まわり 12 ブロックの野盗と弓手を 6 秒速くする
public class BanditChief extends ContinentFoe {
    public static final int SWEEP_WARN = 16;
    public static final double SWEEP_RANGE = 3.7;
    public static final double SWEEP_HALF = Math.toRadians(75);
    public static final int SWEEP_END = SWEEP_WARN + 18;
    public static final int SHOUT_AT = 10;
    public static final int SHOUT_END = 36;
    public static final double SHOUT_RADIUS = 12;
    public static final int SHOUT_EVERY = 240;
    private boolean shouting;
    private int lastShout = -SHOUT_EVERY / 2;

    public BanditChief(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bandit_chief", MobSounds.BANDIT_CHIEF_AMBIENT, MobSounds.BANDIT_CHIEF_HURT, MobSounds.BANDIT_CHIEF_DEATH, "sweep", "shout", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 2.6));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (tickCount - lastShout > SHOUT_EVERY && distance < 10 && hasCrew()) {
            shouting = true;
            lastShout = tickCount;
            begin(target, "shout");
            server().playSound(null, getX(), getY(), getZ(), MobSounds.BANDIT_CHIEF_SHOUT.get(), SoundSource.HOSTILE, 1.6f, 1f);
            return;
        }
        if (distance > SWEEP_RANGE) {
            return;
        }
        shouting = false;
        begin(target, "sweep");
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, SWEEP_HALF))
                .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.BANDIT_SWING.get(), 1.2f, 0.7f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 1.8, getY() + 1.1, getZ() + f.z * 1.8, 3, 0.8, 0.1, 0.8, 0))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.knockback(0.8, -f.x, -f.z);
                })
                .start();
    }

    private boolean hasCrew() {
        return !level().getEntitiesOfClass(ContinentFoe.class, getBoundingBox().inflate(SHOUT_RADIUS), e -> e != this && crew(e)).isEmpty();
    }

    private static boolean crew(LivingEntity e) {
        return e.isAlive() && (e instanceof Bandit || e instanceof BanditArcher);
    }

    @Override
    protected void during(int tick) {
        if (shouting && tick == SHOUT_AT) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SHOUT_RADIUS), BanditChief::crew)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1), this);
                server().sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 2, 0.2, 0.1, 0.2, 0);
            }
            server().sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 16, 1.2, 0.05, 1.2, 0.04);
        }
    }

    @Override
    protected int length() {
        return shouting ? SHOUT_END : SWEEP_END;
    }

    @Override
    protected void finish() {
        cooldown = shouting ? 10 : 30 + random.nextInt(20);
        shouting = false;
    }
}
