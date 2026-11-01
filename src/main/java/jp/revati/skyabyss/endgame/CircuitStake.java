package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.music.MusicArea;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 淵の杭 (巡り路の「壊す」課題。docs/design/54 の 3.1.1。形は tools/rough/lowpoly/cloud/circuit_stake.py の一品一案)。
//   土地の敵が打ち込んだ杭。動かず、押されない。打つと割れ目が広がる (体力 2/3・1/3 で姿の段 2・3)。
//   脈: PULSE ごとに結晶の房が持ち上がって震え (予告 TELL)、叩きつけて足もと (PULSE_R) の人に傷と、外へ押す力。予告のあいだに離れれば当たらない
//   体力と傷は湧かせる所 (Circuits.stepBreak) が人数と柱の色で決める
public class CircuitStake extends CloudMob {
    public static final String ID = "circuit_stake";
    static final int PULSE = 140;
    static final int TELL = 16;
    static final double PULSE_R = 3.5;
    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(0.75f, 0.6f, 1.0f), 1.4f);
    private int pulseAt = PULSE / 2;
    // 体力の帯 (近くの人に見せる。ほかのボスと同じ型)
    private final ServerBossEvent bar = MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.circuit_stake"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));

    public CircuitStake(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "pulse", "hurt", "death");
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            float f = getHealth() / getMaxHealth();
            setStage(f > 2f / 3f ? 1 : f > 1f / 3f ? 2 : 3);
            playSound(CloudMobsSetup.STAKE_HIT.get(), 1.0f, 0.8f + random.nextFloat() * 0.3f);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
        if (level() instanceof ServerLevel sl) {
            playSound(CloudMobsSetup.STAKE_BREAK.get(), 1.4f, 0.7f);
            sl.sendParticles(VOID, getX(), getY() + 1.4, getZ(), 40, 0.5, 1.0, 0.5, 0.05);
        }
    }

    // 帯: 体力の割合と、近く (40 ブロック) の人への出し入れ
    private void updateBar(ServerLevel sl) {
        bar.setProgress(Math.max(0f, getHealth() / getMaxHealth()));
        for (ServerPlayer p : sl.players()) {
            boolean near = p.distanceToSqr(this) < 40 * 40;
            if (near && !bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            } else if (!near && bar.getPlayers().contains(p)) {
                bar.removePlayer(p);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
        if (!(level() instanceof ServerLevel sl) || !isAlive()) {
            return;
        }
        updateBar(sl);
        // 割れ目から漏れる淵の粒 (崩れるほど多く)
        if (tickCount % 6 == 0) {
            sl.sendParticles(VOID, getX(), getY() + 1.0 + random.nextDouble() * 1.4, getZ(), stage(), 0.35, 0.4, 0.35, 0.01);
        }
        pulseAt--;
        if (pulseAt == TELL) {
            playAction("pulse");
            BossCue.send(this, "circuit_stake", "pulse", TELL);
            playSound(CloudMobsSetup.STAKE_PULSE.get(), 1.2f, 0.8f);
            // 予告: 当たる輪を地面に見せる
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                sl.sendParticles(VOID, getX() + Math.cos(a) * PULSE_R, getY() + 0.2, getZ() + Math.sin(a) * PULSE_R, 1, 0, 0, 0, 0);
            }
        } else if (pulseAt <= 0) {
            pulseAt = PULSE;
            pulse(sl);
            // 脈のあとが隙 (47 の 2.1。構えは ×0.3、隙は ×1.6)
            jp.revati.skyabyss.combat.BossOpenings.moveEnded(this);
        }
    }

    // 脈: 足もとの人に傷と、外へ押す力
    void pulse(ServerLevel sl) {
        sl.sendParticles(VOID, getX(), getY() + 0.3, getZ(), 30, PULSE_R * 0.5, 0.1, PULSE_R * 0.5, 0.02);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(PULSE_R, 2, PULSE_R))) {
            if (p.isSpectator() || p.isCreative() || p.distanceToSqr(this) > PULSE_R * PULSE_R + 1) {
                continue;
            }
            strike(p, dmg());
            Vec3 out = p.position().subtract(position()).multiply(1, 0, 1);
            if (out.lengthSqr() > 1e-4) {
                out = out.normalize().scale(0.9);
                p.push(out.x, 0.35, out.z);
                p.hurtMarked = true;
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > 96;
    }
}
