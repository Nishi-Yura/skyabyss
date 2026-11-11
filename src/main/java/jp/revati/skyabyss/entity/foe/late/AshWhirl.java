package jp.revati.skyabyss.entity.foe.late;

import jp.revati.skyabyss.combat.prop.PropFx;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 灰の渦 (灰の荒野。まわりに効く。C 灰の壺の精): 宙に浮く割れた壺から灰の渦が立つ。人が 5 より近いと回り始め
// (予告 1.2 秒、自分のまわりに円 4.5。予告のあいだ円の中の人を少しずつ中へ引く)、灰を吹き上げる: 傷 + 鈍足 2 秒 + 目くらみ 1 秒。
// 吹いたあと 2 秒は壺が落ちて芯が見え、止まる (受ける傷 +50%)。予告を見て円の外へ出て、止まったところを叩く
public class AshWhirl extends LateFoe {
    public static final int WARN = 24;
    public static final double RADIUS = 4.5;
    public static final double START_RANGE = 5;
    public static final int END = WARN + 6;
    public static final int SOFT = 40;
    public static final float SOFT_TAKEN = 1.5f;
    public static final double PULL = 0.05;

    public AshWhirl(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ash_whirl", MobSounds.STRAY_WISP_AMBIENT, MobSounds.STRAY_WISP_HURT, MobSounds.STRAY_WISP_DEATH, "spin", "burst", "hurt", "death");
        softTaken = SOFT_TAKEN;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.19)
                .add(Attributes.FOLLOW_RANGE, 22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, START_RANGE - 1.5));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance > START_RANGE || soft()) {
            return;
        }
        begin(target, "spin");
        double x = getX();
        double z = getZ();
        Telegraph.in(server(), TelegraphShape.circle(x, z, RADIUS)).prop(PropFx.rise("whirlwind"))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.MOSS_DEAD_SPORES.get(), 1.2f, 0.7f)
                .victims(this::canHit)
                .onImpact(server -> {
                    playAction("burst");
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, x, getY() + 1.0, z, 90, RADIUS * 0.4, 1.0, RADIUS * 0.4, 0.04);
                    server.sendParticles(ParticleTypes.FLAME, x, getY() + 0.8, z, 20, 0.6, 0.6, 0.6, 0.02);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage());
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), this);
                })
                .start();
    }

    // 予告のあいだ、円の中の人を中へ少し引く (外へ走れば抜けられる強さ)
    @Override
    protected void during(int tick) {
        if (tick >= WARN || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        if (tick % 4 == 0) {
            server.sendParticles(ParticleTypes.ASH, getX(), getY() + 1.2, getZ(), 30, RADIUS * 0.45, 0.8, RADIUS * 0.45, 0.02);
        }
        for (ServerPlayer p : server.players()) {
            double dx = getX() - p.getX();
            double dz = getZ() - p.getZ();
            double d = Math.hypot(dx, dz);
            if (canHit(p) && d < RADIUS && d > 0.8 && Math.abs(p.getY() - getY()) < 3) {
                Vec3 pull = new Vec3(dx / d * PULL, 0, dz / d * PULL);
                p.setDeltaMovement(p.getDeltaMovement().add(pull));
                p.hurtMarked = true;
            }
        }
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void finish() {
        softTicks = SOFT;
        cooldown = SOFT + 30;
    }
}
