package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.Ch1BackSounds;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.titan.group.GroupOwned;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨の砲手 (docs/design/21 の 3.3、案 A「臼砲の籠」): 背中の遠くから骨の塊を落とす砲台。HP 30、砲 5。動きは遅く、骨の盾の後ろにいる。
//  - 骨の塊: 狙う人の足もとに半径 2 の円の印が出て (1.2 秒 = 24 tick)、砲身が上がる。印の上に塊が放物線で落ちる (着弾は印が満ちきる瞬間。
//    印の外へ出れば当たらない)。印と当たりは同じ円。人数が多いほど同時に落とす数が増える (近くの人数、最大 3)
//  - 正面からの傷は半分 (盾)。回り込めば効く
// 動き: 目標が 18 より遠いと近づき、6 より近いと下がる。それ以外は止まって撃つ
public class BoneMortar extends GuardMob {
    // 技の数値 (tick は 20 で 1 秒)
    public static final int WARN = 24;
    // 砲が放たれてから着弾 (= 印が満ちきる) までの飛行時間
    public static final int FLIGHT = 14;
    public static final double RADIUS = 2.0;
    public static final float DAMAGE = 5f;
    public static final double MAX_RANGE = 24;
    public static final double NEAR = 6;
    public static final double FAR = 18;
    public static final int COOLDOWN = 70;
    public static final int MAX_SHELLS = 3;

    private int cooldown = 30;
    private int ticks;
    private float lockedYaw;
    private boolean holdAttacks;
    // 放物線の飛行中の塊 (出発点・着弾点)
    private final List<Vec3[]> shells = new ArrayList<>();

    public BoneMortar(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_mortar", "aim", "fire");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 14f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // 試験用: 次の volley を始めない / 始める
    public void holdAttacks() {
        holdAttacks = true;
    }

    public void forceVolley() {
        holdAttacks = false;
        cooldown = 0;
    }

    public boolean isFiring() {
        return ticks > 0;
    }

    private boolean canHit(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && canAttack(player);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        cooldown = Math.max(0, cooldown - 1);
        LivingEntity target = getTarget();
        if (ticks == 0) {
            if (target == null || !target.isAlive()) {
                return;
            }
            getLookControl().setLookAt(target, 20f, 20f);
            if (holdAttacks) {
                return;
            }
            double dist = distanceTo(target);
            if (cooldown == 0 && dist <= MAX_RANGE) {
                begin(server, target);
                return;
            }
            if (dist > FAR) {
                getNavigation().moveTo(target, 0.8);
            } else if (dist < NEAR) {
                Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                getNavigation().moveTo(getX() + away.x * 6, getY(), getZ() + away.z * 6, 0.8);
            } else {
                getNavigation().stop();
            }
            return;
        }
        ticks++;
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        getNavigation().stop();
        // 放つ: 砲身が上がりきった (aim の 0.5 秒) あと、着弾の FLIGHT tick 前
        if (ticks == WARN - FLIGHT) {
            playAction("fire");
            Vec3 muzzle = muzzle();
            playTo(server, Ch1BackSounds.MORTAR_FIRE.get(), 2f, 0.95f + random.nextFloat() * 0.1f);
            trail(server, ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle, 6, 0.15, 0.1, 0.15, 0.01);
            trail(server, ParticleTypes.POOF, muzzle, 6, 0.2, 0.2, 0.2, 0.05);
            for (Vec3[] shell : shells) {
                shell[0] = muzzle;
            }
        }
        if (ticks > WARN - FLIGHT && ticks <= WARN) {
            double t = (double) (ticks - (WARN - FLIGHT)) / FLIGHT;
            for (Vec3[] shell : shells) {
                double h = 3.0 + shell[0].distanceTo(shell[1]) * 0.18;
                Vec3 p = shell[0].lerp(shell[1], t).add(0, 4 * h * t * (1 - t), 0);
                trail(server, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), p, 5, 0.12, 0.12, 0.12, 0.0);
            }
        }
        if (ticks >= WARN + 8) {
            ticks = 0;
            shells.clear();
            cooldown = COOLDOWN;
        }
    }

    private Vec3 muzzle() {
        double rad = Math.toRadians(lockedYaw);
        return new Vec3(getX() - Math.sin(rad) * 0.8, getY() + 1.9, getZ() + Math.cos(rad) * 0.8);
    }

    // 一度に落とす塊の数と狙う場所: 狙われた人の足もと、それから近くの人 (最大 MAX_SHELLS)
    public List<Vec3> aimSpots(ServerLevel server, LivingEntity target) {
        List<ServerPlayer> near = new ArrayList<>(server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(MAX_RANGE, 8, MAX_RANGE), this::canHit));
        near.sort(Comparator.comparingDouble(p -> p == target ? -1.0 : p.distanceToSqr(target)));
        List<Vec3> spots = new ArrayList<>();
        spots.add(target.position());
        for (ServerPlayer p : near) {
            if (spots.size() >= MAX_SHELLS) {
                break;
            }
            if (p != target) {
                spots.add(p.position());
            }
        }
        return spots;
    }

    private void begin(ServerLevel server, LivingEntity target) {
        ticks = 1;
        shells.clear();
        List<Vec3> spots = aimSpots(server, target);
        Vec3 first = spots.get(0);
        lockedYaw = (float) (Mth.atan2(first.z - getZ(), first.x - getX()) * 180 / Math.PI) - 90f;
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        playAction("aim");
        boolean firstSpot = true;
        for (Vec3 spot : spots) {
            Vec3 land = spot;
            shells.add(new Vec3[] {position(), land});
            var builder = Telegraph.in(server, TelegraphShape.circle(land.x, land.z, RADIUS))
                    .floor(land.y).warn(WARN).danger().owner(this)
                    .victims(this::canHit)
                    .onImpact(s -> impact(s, land))
                    .onHit((s, player) -> {
                        player.hurt(attackSource(), DAMAGE);
                        Vec3 push = player.position().subtract(land).multiply(1, 0, 1);
                        push = push.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : push.normalize();
                        player.setDeltaMovement(player.getDeltaMovement().add(push.x * 0.5, 0.35, push.z * 0.5));
                        player.hurtMarked = true;
                    });
            if (firstSpot) {
                builder.target(target).startSound(Ch1BackSounds.MORTAR_CHARGE.get(), 1.4f, 0.9f);
                firstSpot = false;
            }
            builder.start();
        }
    }

    private void impact(ServerLevel server, Vec3 at) {
        server.playSound(null, at.x, at.y, at.z, Ch1BackSounds.MORTAR_LAND.get(), SoundSource.HOSTILE, 2f, 0.95f + random.nextFloat() * 0.1f);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y + 0.4, at.z, 40, 0.8, 0.3, 0.8, 0.2);
        server.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 10, 0.8, 0.1, 0.8, 0.04);
    }

    // 飛行の粒は同じグループの人にだけ
    private void trail(ServerLevel server, ParticleOptions particle, Vec3 at, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : Players.snapshot(server.players())) {
            if (player.distanceToSqr(at) < 64 * 64 && GroupOwned.visibleTo(this, player)) {
                server.sendParticles(player, particle, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
            }
        }
    }

    private void playTo(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        if (!GroupOwned.playToGroup(this, sound, volume, pitch)) {
            server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
        }
    }

    // 骨の盾: 正面からの傷は半分 (回り込めば効く)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
            Vec3 toSource = from.subtract(position()).multiply(1, 0, 1).normalize();
            if (look.dot(toSource) > 0.5) {
                amount *= 0.5f;
                level().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_SENTINEL_GUARD.get(), SoundSource.HOSTILE, 1f, 0.8f + random.nextFloat() * 0.2f);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        Telegraph.cancelAll(this);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 1, getZ(), 40, 0.7, 0.6, 0.7, 0.15);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch1BackSounds.MORTAR_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch1BackSounds.MORTAR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch1BackSounds.MORTAR_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(Ch1BackSounds.MORTAR_STEP.get(), 0.4f);
    }
}
