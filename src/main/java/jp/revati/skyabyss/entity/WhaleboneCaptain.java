package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 鯨骨の番兵長: 東の回廊の最後に待つ中ボス。巨大な鉾を持ち、四つの技を使い分ける。
//  - 薙ぎ払い: 目の前の広い扇形をまとめて斬る (振りかぶりが見えたら下がる)
//  - 跳躍と叩きつけ: 離れた相手へ跳びかかり、着地の衝撃波で周りを吹き飛ばす
//  - 骨の噴出: 足元に白い輪が浮かび、しばらくすると床から骨の槍が突き上がる (輪から出れば避けられる)
//  - 咆哮 (体力が半分を切ったとき一度だけ): 周りを吹き飛ばし、骨の番兵を呼び、速くなる
// 動きはすべてアニメーションの「溜め」と合わせてあり、見てから避けられるようにしている
public class WhaleboneCaptain extends GuardMob {
    private enum Action { NONE, SWEEP, LEAP, ERUPT, ROAR }

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private int actionTicks;
    private int sweepCooldown;
    private int leapCooldown = 80;
    private int eruptCooldown = 120;
    private boolean roared;
    private boolean airborne;
    private final List<Vec3> eruptions = new ArrayList<>();

    public WhaleboneCaptain(EntityType<? extends Monster> type, Level level) {
        super(type, level, "whalebone_captain", "sweep", "leap", "slam", "roar");
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 280)
                .add(Attributes.ATTACK_DAMAGE, 11)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 20 == 0) {
            updateBossBarViewers();
        }
        sweepCooldown = Math.max(0, sweepCooldown - 1);
        leapCooldown = Math.max(0, leapCooldown - 1);
        eruptCooldown = Math.max(0, eruptCooldown - 1);

        LivingEntity target = getTarget();
        if (action == Action.NONE) {
            if (target == null || !target.isAlive()) {
                return;
            }
            getLookControl().setLookAt(target, 30f, 30f);
            double dist = distanceTo(target);
            boolean enraged = getHealth() < getMaxHealth() * 0.5f;
            if (enraged && !roared) {
                begin(Action.ROAR, "roar");
            } else if (eruptCooldown == 0 && dist < 18 && random.nextFloat() < 0.4f) {
                begin(Action.ERUPT, "roar");
            } else if (leapCooldown == 0 && dist > 7 && dist < 18) {
                begin(Action.LEAP, "leap");
            } else if (sweepCooldown == 0 && dist < 4.5) {
                begin(Action.SWEEP, "sweep");
            } else {
                getNavigation().moveTo(target, dist > 6 ? 1.15 : 0.9);
            }
            return;
        }
        actionTicks++;
        switch (action) {
            case SWEEP -> tickSweep();
            case LEAP -> tickLeap(target);
            case ERUPT -> tickErupt(target);
            case ROAR -> tickRoar();
            default -> {
            }
        }
    }

    private void begin(Action next, String animation) {
        action = next;
        actionTicks = 0;
        getNavigation().stop();
        playAction(animation);
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
    }

    private boolean enraged() {
        return getHealth() < getMaxHealth() * 0.5f;
    }

    // 薙ぎ払い: 0.45秒の振りかぶりのあと、前方 140° ・半径5 をまとめて斬る
    private void tickSweep() {
        if (actionTicks == 2) {
            level().playSound(null, getX(), getY(), getZ(), MobSounds.WHALEBONE_CAPTAIN_SWING.get(), SoundSource.HOSTILE, 1.6f, 0.95f + random.nextFloat() * 0.1f);
        }
        if (actionTicks == 12) {
            Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
            for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(5, 2, 5), this::canAttack)) {
                Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                if (to.length() <= 5.2 && look.dot(to.normalize()) > Math.cos(Math.toRadians(70))) {
                    player.hurt(attackSource(), 11f);
                    player.knockback(1.2, -to.x, -to.z);
                }
            }
            level().playSound(null, getX(), getY(), getZ(), MobSounds.WHALEBONE_CAPTAIN_SLAM.get(), SoundSource.HOSTILE, 2f, 0.95f + random.nextFloat() * 0.1f);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 2.5, getY() + 1.5, getZ() + look.z * 2.5, 4, 1.2, 0.2, 1.2, 0);
            }
        }
        if (actionTicks >= 22) {
            sweepCooldown = enraged() ? 25 : 40;
            finish();
        }
    }

    // 跳躍と叩きつけ: 溜めて跳び、着地した瞬間に衝撃波
    private void tickLeap(LivingEntity target) {
        if (actionTicks == 8 && target != null) {
            Vec3 to = target.position().subtract(position());
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            double vy = 0.95;
            double airTicks = 2 * vy / 0.08;
            Vec3 velocity = new Vec3(to.x / airTicks, vy, to.z / airTicks);
            if (horizontal > 0.1) {
                setDeltaMovement(velocity);
                hasImpulse = true;
                airborne = true;
            }
            level().playSound(null, getX(), getY(), getZ(), MobSounds.CAPTAIN_LEAP_ROAR.get(), SoundSource.HOSTILE, 1.8f, 1.0f);
        }
        if (airborne && actionTicks > 12 && onGround()) {
            airborne = false;
            playAction("slam");
            shockwave(enraged() ? 7.5 : 6, 9f);
            leapCooldown = enraged() ? 90 : 140;
            finish();
        } else if (actionTicks > 80) {
            airborne = false;
            leapCooldown = 100;
            finish();
        }
    }

    private void shockwave(double radius, float damage) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        server.playSound(null, getX(), getY(), getZ(), MobSounds.CAPTAIN_SHOCKWAVE.get(), SoundSource.HOSTILE, 2.5f, 1.0f);
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        for (int ring = 1; ring <= (int) radius; ring++) {
            for (int a = 0; a < 360; a += 360 / (ring * 6)) {
                double x = getX() + Math.cos(Math.toRadians(a)) * ring;
                double z = getZ() + Math.sin(Math.toRadians(a)) * ring;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                        x, getY() + 0.1, z, 2, 0.1, 0.05, 0.1, 0.05);
            }
        }
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 2, radius), this::canAttack)) {
            Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
            double d = to.length();
            if (d <= radius) {
                player.hurt(attackSource(), (float) (damage * (1 - d / radius * 0.5)));
                Vec3 push = d > 0.01 ? to.normalize() : new Vec3(1, 0, 0);
                player.setDeltaMovement(player.getDeltaMovement().add(push.x * 1.3, 0.55, push.z * 1.3));
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            }
        }
    }

    // 骨の噴出: 相手の足元と周りに白い輪を描き、1.5秒後に骨の槍が突き上がる
    private void tickErupt(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (actionTicks == 1) {
            eruptions.clear();
            server.playSound(null, getX(), getY(), getZ(), MobSounds.CAPTAIN_ERUPT_CHARGE.get(), SoundSource.HOSTILE, 2f, 1.0f);
            int count = enraged() ? 10 : 6;
            for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(18, 4, 18), this::canAttack)) {
                eruptions.add(player.position());
            }
            while (eruptions.size() < count && target != null) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = 2 + random.nextDouble() * 5;
                eruptions.add(target.position().add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
            }
        }
        if (actionTicks < 30 && actionTicks % 4 == 1) {
            for (Vec3 p : eruptions) {
                for (int a = 0; a < 360; a += 30) {
                    warn(server, ParticleTypes.END_ROD, p.x + Math.cos(Math.toRadians(a)) * 1.2, p.y + 0.1,
                            p.z + Math.sin(Math.toRadians(a)) * 1.2, 1, 0, 0, 0, 0);
                }
            }
        }
        if (actionTicks == 30) {
            for (Vec3 p : eruptions) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        p.x, p.y + 1, p.z, 30, 0.3, 0.9, 0.3, 0.15);
                server.sendParticles(ParticleTypes.CRIT, p.x, p.y + 1.5, p.z, 10, 0.2, 0.8, 0.2, 0.3);
                server.playSound(null, BlockPos.containing(p), MobSounds.CAPTAIN_ERUPT_BURST.get(), SoundSource.HOSTILE, 1.5f, 0.95f + random.nextFloat() * 0.1f);
                for (Player player : server.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(p, p).inflate(1.3, 2, 1.3), this::canAttack)) {
                    player.hurt(attackSource(), 7f);
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.85, 0));
                    player.hurtMarked = true;
                }
            }
        }
        if (actionTicks >= 40) {
            eruptCooldown = enraged() ? 130 : 200;
            finish();
        }
    }

    // 咆哮: 周りを吹き飛ばし、骨の番兵を二体呼び、怒りで速くなる (一度だけ)
    private void tickRoar() {
        if (actionTicks == 6) {
            level().playSound(null, getX(), getY(), getZ(), MobSounds.CAPTAIN_ROAR.get(), SoundSource.HOSTILE, 3f, 1.0f);
        }
        if (actionTicks == 16 && level() instanceof ServerLevel server) {
            roared = true;
            shockwave(6, 4f);
            for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                BlockPos at = BlockPos.containing(getX() + Math.cos(angle) * 3, getY(), getZ() + Math.sin(angle) * 3);
                TitanBackTrials.spawnGuard(server, "sentinel", at, groupId());
            }
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false));
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 0, false, false));
        }
        if (actionTicks >= 30) {
            finish();
        }
    }

    // 近づいてきた相手には、技の合間でもふつうに鉾で殴る
    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("sweep");
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && action == Action.NONE && getTarget() != null && distanceTo(getTarget()) < 2.5 && tickCount % 25 == 0) {
            doHurtTarget(getTarget());
        }
    }

    private void updateBossBarViewers() {
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!player.isAlive() || player.distanceToSqr(this) > 48 * 48 || player.level() != level()) {
                bossBar.removePlayer(player);
            }
        }
        if (level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.getPlayers(p -> p.distanceToSqr(this) < 40 * 40)) {
                bossBar.addPlayer(player);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    getX(), getY() + 1.5, getZ(), 80, 0.8, 1.2, 0.8, 0.2);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.WHALEBONE_CAPTAIN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.WHALEBONE_CAPTAIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.WHALEBONE_CAPTAIN_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.WHALEBONE_CAPTAIN_STEP.get(), 0.8f);
    }

    @Override
    public float getVoicePitch() {
        return 1.0f + (random.nextFloat() - random.nextFloat()) * 0.08f;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    // 目立つよう、少しずつ背の鉾の先から光の粒をこぼす
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextFloat() < 0.15f) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + Mth.nextDouble(random, -0.6, 0.6), getY() + 2.4, getZ() + Mth.nextDouble(random, -0.6, 0.6), 0, 0.02, 0);
        }
    }
}
