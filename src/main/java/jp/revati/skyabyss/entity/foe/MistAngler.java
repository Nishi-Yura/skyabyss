package jp.revati.skyabyss.entity.foe;

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

// 霧の漁り火 (docs/design/44): 潮の浜と沼の夜。距離をとって漂い、網を投げる (予告 1.0 秒、狙った人の足もとの円): 円の中の人は 3 秒ひどく鈍る。
// ときどき竿の灯を大きく揺らして霧を呼ぶ (予告 0.8 秒、まわり 7 の円): 円の中の人は 2.5 秒目がくらむ (傷はない)
public class MistAngler extends ContinentFoe {
    public static final int NET_WARN = 20;
    public static final double NET_RADIUS = 2.3;
    public static final int NET_END = NET_WARN + 16;
    public static final int SWAY_WARN = 16;
    public static final double SWAY_RADIUS = 7;
    public static final int SWAY_END = SWAY_WARN + 12;
    public static final int SWAY_EVERY = 260;
    public static final double MIN_RANGE = 6;
    public static final double MAX_RANGE = 14;
    private boolean swaying;
    private int lastSway = -SWAY_EVERY / 2;

    public MistAngler(EntityType<? extends Monster> type, Level level) {
        super(type, level, "mist_angler", MobSounds.MIST_ANGLER_AMBIENT, MobSounds.MIST_ANGLER_HURT, MobSounds.MIST_ANGLER_DEATH, "net", "sway", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 26);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new KeepDistanceGoal(this, MIN_RANGE, MAX_RANGE));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (tickCount - lastSway > SWAY_EVERY && distance < SWAY_RADIUS) {
            swaying = true;
            lastSway = tickCount;
            begin(target, "sway");
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), SWAY_RADIUS))
                    .floor(getY()).warn(SWAY_WARN).danger().owner(this).target(target)
                    .startSound(MobSounds.MIST_ANGLER_SWAY.get(), 1.2f, 1.0f)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.6, getZ(), 70, SWAY_RADIUS * 0.4, 0.5, SWAY_RADIUS * 0.4, 0.01))
                    .onHit((server, player) -> player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), this))
                    .start();
            return;
        }
        if (distance < 3 || distance > MAX_RANGE + 2) {
            return;
        }
        swaying = false;
        begin(target, "net");
        double x = target.getX();
        double z = target.getZ();
        double y = target.getY();
        Telegraph.in(server(), TelegraphShape.circle(x, z, NET_RADIUS))
                .floor(y).warn(NET_WARN).danger().owner(this).target(target)
                .startSound(MobSounds.MIST_ANGLER_NET.get(), 1.0f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.SPLASH, x, y + 0.3, z, 40, NET_RADIUS * 0.4, 0.2, NET_RADIUS * 0.4, 0.05))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 0.6f);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
                })
                .start();
    }

    @Override
    protected int length() {
        return swaying ? SWAY_END : NET_END;
    }

    @Override
    protected void finish() {
        cooldown = swaying ? 20 : 45 + random.nextInt(25);
        swaying = false;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
