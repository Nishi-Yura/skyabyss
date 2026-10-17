package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.ModText;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 群れの巣 (reef_nest、08 の 4.3節・5.1節)。干潟の砂の上の珊瑚の塚 (見た目は島のブロック)。このエンティティは当たり判定と体力だけを持つ。
// 干潮で口を開け、磯走りを出す。満潮では殻を閉じて傷が通らない。湧き方・壊れたあとは TideFlats が決める。保存しない (TideFlats が置き直す)
public class ReefNest extends Mob {
    public static final float BASE_HEALTH = 60;
    public static final float ARMOR = 6;

    int site = -1;
    private long lastClosedNotice;

    public ReefNest(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    public int site() {
        return site;
    }

    // 口を開けているか (干潮のあいだ)
    public boolean open() {
        return level() instanceof ServerLevel server && TideFlats.nestsOpen(server);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isDeadOrDying()) {
            return false;
        }
        if (source.getEntity() == null && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            // 溺れ・落下などは受けない
            return false;
        }
        if (!open() && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            if (source.getEntity() instanceof ServerPlayer player && level().getGameTime() - lastClosedNotice > 20) {
                lastClosedNotice = level().getGameTime();
                player.displayClientMessage(ModText.deny("message.skyabyss.tide.nest_closed"), true);
            }
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1f, 0.6f);
            return false;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1.5, getZ(), 12, 0.8, 0.4, 0.8, 0.05);
            if (source.getEntity() instanceof ServerPlayer player && isAlive()) {
                player.displayClientMessage(ModText.battle("message.skyabyss.tide.nest_hp", (int) Math.ceil(getHealth()), (int) getMaxHealth()), true);
            }
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            TideFlats.onNestBroken(server, this);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    protected void tickDeath() {
        // 倒れたら、すぐに消える (塚のブロックが崩れる)
        if (!level().isClientSide) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    // 攻撃を受けても向きを変えない
    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(net.minecraft.world.entity.Entity attacker) {
        return false;
    }

    static boolean isPlayerAttack(DamageSource source) {
        return source.getEntity() instanceof Player;
    }
}
