package jp.revati.skyabyss.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 岩鯨の心臓: 試練の最後に待つ最終ボス。石と骨の殻に包まれ、空中にとどまって動かない。
//  - 鼓動の衝撃波: 床をはう輪が外へ広がる。跳び越えなければ当たる (二度目は怒りのとき)
//  - 骨の雨: 相手の足元に白い輪を描き、天井から骨の棘が落ちる
//  - 血流の奔流: 砲口が振れ、水色の光の帯を薙ぐ
//  - 番兵の召喚: 体力が 75%・25% を切ったとき、骨の番兵と苔這いを一体ずつ呼ぶ
//  - 怒り (体力50%未満): 技の間隔が短くなり、鼓動の衝撃波が二度になる。
//    殻が閉じた「装甲」の間は傷を受けず、呼ばれた番兵を倒すと殻が開く「露出」になる (露出の間だけ傷を受ける)
public class StoneWhaleHeart extends GuardMob {
    private enum Action { NONE, BEAT, RAIN, BEAM, SUMMON }

    // 衝撃波の輪。床をはい、跳び越えない相手に当たる
    private static final class Wave {
        double radius = 1.5;
        final double floorY;
        final Set<UUID> hit = new HashSet<>();

        Wave(double floorY) {
            this.floorY = floorY;
        }
    }

    // 落ちてくる骨の棘。足元の白い輪を見てから避けられるよう、少し間を置いて落ちる
    private record Spike(double x, double y, double z, int impactAt) {
    }

    private static final double BEAM_LENGTH = 24;

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private int actionTicks;
    private int beatCooldown = 40;
    private int rainCooldown = 100;
    private int beamCooldown = 160;
    private boolean pendingSummon;
    private boolean summonedAt75;
    private boolean summonedAt25;
    private boolean cycleStarted;
    private boolean armored;
    private int armorTicks;
    private int exposeTicks;
    private float beamBaseYaw;
    private boolean defeated;
    private final List<Wave> waves = new ArrayList<>();
    private final List<Spike> spikes = new ArrayList<>();
    private final List<UUID> adds = new ArrayList<>();

    public StoneWhaleHeart(EntityType<? extends Monster> type, Level level) {
        super(type, level, "stone_whale_heart", "beat", "rain", "beam", "summon", "expose");
        this.xpReward = 200;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    private boolean phaseTwo() {
        return getHealth() < getMaxHealth() * 0.5f;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 20 == 0) {
            updateBossBarViewers();
        }
        beatCooldown = Math.max(0, beatCooldown - 1);
        rainCooldown = Math.max(0, rainCooldown - 1);
        beamCooldown = Math.max(0, beamCooldown - 1);

        tickWaves(server);
        tickSpikes(server);
        checkThresholds();
        tickCycle(server);

        LivingEntity target = getTarget();
        if (action == Action.NONE) {
            if (target == null || !target.isAlive()) {
                return;
            }
            faceTarget(target);
            if (pendingSummon) {
                pendingSummon = false;
                begin(Action.SUMMON, "summon");
            } else if (beamCooldown == 0 && distanceTo(target) < 30) {
                begin(Action.BEAM, "beam");
            } else if (rainCooldown == 0) {
                begin(Action.RAIN, "rain");
            } else if (beatCooldown == 0) {
                begin(Action.BEAT, "beat");
            }
            return;
        }
        actionTicks++;
        switch (action) {
            case BEAT -> tickBeat(server);
            case RAIN -> tickRain(server);
            case BEAM -> tickBeam(server);
            case SUMMON -> tickSummon(server);
            default -> {
            }
        }
    }

    private void begin(Action next, String animation) {
        action = next;
        actionTicks = 0;
        playAction(animation);
        if (next == Action.BEAM) {
            beamBaseYaw = getYRot();
        }
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
        int jitter = random.nextInt(20);
        boolean p2 = phaseTwo();
        // 次の技までの間隔は、怒りで短くなる
        beatCooldown = (p2 ? 50 : 90) + jitter;
        rainCooldown = (p2 ? 100 : 150) + jitter;
        beamCooldown = (p2 ? 130 : 200) + jitter;
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        setYRot((float) (Mth.atan2(-dx, dz) * 180 / Math.PI));
    }

    // 色つきの粒 (0xRRGGBB と大きさ)
    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    // 向きから水平の前方ベクトル (モデルの正面は -Z = 向きの前)
    private static Vec3 forward(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    // 鼓動の衝撃波: 二度脈打ち、輪を放つ (怒りのときは二つ目の輪も)
    private void tickBeat(ServerLevel server) {
        if (actionTicks == 1) {
            heartbeat(server);
        }
        if (actionTicks == 6) {
            waves.add(new Wave(floorY()));
        }
        if (actionTicks == 12) {
            heartbeat(server);
        }
        if (actionTicks == 18 && phaseTwo()) {
            waves.add(new Wave(floorY()));
        }
        if (actionTicks >= 24) {
            finish();
        }
    }

    private void heartbeat(ServerLevel server) {
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.5f, 0.6f);
    }

    private double floorY() {
        return floorAt(Mth.floor(getX()), Mth.floor(getZ()));
    }

    // 心臓の間は鯨の体内にあり、真上に天井と背の地表がある。高さの地図では天井の上を返すので、
    // 心臓の高さから真下へたどって最初の床を探す
    private int floorAt(int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Mth.floor(getY()), z);
        for (int i = 0; i < 24; i++) {
            pos.move(0, -1, 0);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return pos.getY() + 1;
            }
        }
        return Mth.floor(getY()) - 4;
    }

    // 輪を広げ、跳び越えなかった相手に当てる。輪は一人一度だけ当たる
    private void tickWaves(ServerLevel server) {
        for (Wave wave : waves) {
            wave.radius += 0.8;
            int points = Math.max(8, (int) (2 * Math.PI * wave.radius / 1.2));
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points;
                server.sendParticles(dust(0x7FF4FF, 1.2f),
                        getX() + Math.cos(a) * wave.radius, wave.floorY + 0.6, getZ() + Math.sin(a) * wave.radius, 1, 0, 0, 0, 0);
            }
            double r = wave.radius + 2;
            AABB area = new AABB(getX() - r, wave.floorY - 1, getZ() - r, getX() + r, wave.floorY + 3, getZ() + r);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, p -> p.isAlive() && !p.isSpectator())) {
                Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                double d = to.length();
                // 地に立ったままの人だけに当たる (跳べば輪の下を抜けられる)
                if (Math.abs(d - wave.radius) < 1.0 && player.getY() < wave.floorY + 0.9 && wave.hit.add(player.getUUID())) {
                    player.hurt(damageSources().mobAttack(this), 8f);
                    player.knockback(0.9, -to.x, -to.z);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
            }
        }
        waves.removeIf(w -> w.radius > 22);
    }

    // 骨の雨: 人の足元に白い輪を描き、しばらくすると骨の棘が落ちる
    private void tickRain(ServerLevel server) {
        if (actionTicks == 1) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2f, 1.6f);
            int impact = tickCount + 40;
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28, 6, 28),
                    p -> p.isAlive() && !p.isSpectator())) {
                spikes.add(new Spike(player.getX(), player.getY(), player.getZ(), impact));
                if (phaseTwo()) {
                    double a = random.nextDouble() * Math.PI * 2;
                    spikes.add(new Spike(player.getX() + Math.cos(a) * 2.5, player.getY(), player.getZ() + Math.sin(a) * 2.5, impact));
                }
            }
        }
        if (actionTicks >= 50) {
            finish();
        }
    }

    // 落ちてくる棘: 予告の輪、落下の筋、着弾の順に進む
    private void tickSpikes(ServerLevel server) {
        if (spikes.isEmpty()) {
            return;
        }
        List<Spike> remaining = new ArrayList<>();
        for (Spike s : spikes) {
            if (tickCount >= s.impactAt()) {
                impact(server, s);
                continue;
            }
            if (tickCount % 4 == 0) {
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    server.sendParticles(dust(0xFF5A5A, 1.0f),
                            s.x() + Math.cos(rad) * 1.4, s.y() + 0.1, s.z() + Math.sin(rad) * 1.4, 1, 0, 0, 0, 0);
                }
            }
            if (tickCount >= s.impactAt() - 12) {
                double drop = (s.impactAt() - tickCount) / 12.0;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        s.x(), s.y() + 1 + drop * 9, s.z(), 2, 0.05, 0.1, 0.05, 0);
            }
            remaining.add(s);
        }
        spikes.clear();
        spikes.addAll(remaining);
    }

    private void impact(ServerLevel server, Spike s) {
        server.playSound(null, s.x(), s.y(), s.z(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.6f, 0.6f);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                s.x(), s.y() + 0.5, s.z(), 30, 0.6, 0.4, 0.6, 0.2);
        AABB area = new AABB(s.x() - 1.4, s.y() - 1, s.z() - 1.4, s.x() + 1.4, s.y() + 2.5, s.z() + 1.4);
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, p -> p.isAlive() && !p.isSpectator())) {
            player.hurt(damageSources().mobAttack(this), 9f);
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.4, 0));
            player.hurtMarked = true;
        }
    }

    // 血流の奔流: 砲口が構えてから、前方 ±50° を薙ぐ水色の光の帯。帯の中の相手に当たる
    private void tickBeam(ServerLevel server) {
        Vec3 origin = position().add(0, 2.25, 0).add(forward(getYRot()).scale(0.8));
        if (actionTicks < 30) {
            if (actionTicks % 3 == 0) {
                for (int a = 0; a < 360; a += 45) {
                    double rad = Math.toRadians(a);
                    server.sendParticles(dust(0x7FF4FF, 1.0f),
                            origin.x + Math.cos(rad) * 1.2, origin.y + Math.sin(rad) * 1.2, origin.z, 1, 0, 0, 0, 0);
                }
            }
            if (actionTicks == 1) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2f, 0.5f);
            }
        } else if (actionTicks < 90) {
            double p = (actionTicks - 30) / 60.0;
            double yaw = beamBaseYaw + (-50 + 100 * p);
            Vec3 dir = forward((float) yaw);
            for (double d = 1; d <= BEAM_LENGTH; d += 0.8) {
                server.sendParticles(dust(0x9CFBFF, 1.4f),
                        origin.x + dir.x * d, origin.y, origin.z + dir.z * d, 1, 0.05, 0.05, 0.05, 0);
            }
            if (actionTicks % 8 == 0) {
                AABB area = new AABB(origin, origin.add(dir.scale(BEAM_LENGTH))).inflate(1.5, 2, 1.5);
                for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, p2 -> p2.isAlive() && !p2.isSpectator())) {
                    Vec3 v = player.position().add(0, player.getBbHeight() * 0.6, 0).subtract(origin);
                    double along = v.dot(dir);
                    if (along < 0 || along > BEAM_LENGTH) {
                        continue;
                    }
                    Vec3 perp = v.subtract(dir.scale(along));
                    if (perp.length() < 1.1) {
                        player.hurt(damageSources().mobAttack(this), 12f);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    }
                }
            }
        }
        if (actionTicks >= 96) {
            finish();
        }
    }

    // 番兵の召喚: 骨の番兵と苔這いを一体ずつ、心臓の周りに呼ぶ
    private void tickSummon(ServerLevel server) {
        if (actionTicks == 14) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.5f, 0.7f);
            String[] kinds = {"sentinel", "crawler"};
            for (int i = 0; i < kinds.length; i++) {
                double angle = random.nextDouble() * Math.PI * 2 + i * Math.PI;
                int x = Mth.floor(getX() + Math.cos(angle) * 7);
                int z = Mth.floor(getZ() + Math.sin(angle) * 7);
                BlockPos at = new BlockPos(x, floorAt(x, z), z);
                Mob mob = TitanBackTrials.spawnGuard(server, kinds[i], at);
                if (mob != null) {
                    adds.add(mob.getUUID());
                }
            }
        }
        if (actionTicks >= 32) {
            finish();
        }
    }

    // 体力の節目で番兵を呼ぶ (75%・25%)。50% は怒りの装甲が番兵を呼ぶ
    private void checkThresholds() {
        double frac = getHealth() / getMaxHealth();
        if (!summonedAt75 && frac < 0.75) {
            summonedAt75 = true;
            pendingSummon = true;
        }
        if (!summonedAt25 && frac < 0.25) {
            summonedAt25 = true;
            pendingSummon = true;
        }
    }

    // 怒りの後の装甲と露出の繰り返し
    //  装甲: 殻が閉じて傷を受けない。呼ばれた番兵が全滅するか、15秒たつまで続く
    //  露出: 殻が開き、6秒間だけ傷を受ける。そのあと再び装甲になり、番兵を呼ぶ
    private void tickCycle(ServerLevel server) {
        if (!phaseTwo()) {
            return;
        }
        if (!cycleStarted) {
            cycleStarted = true;
            startArmor();
            return;
        }
        if (armored) {
            armorTicks++;
            adds.removeIf(id -> {
                Entity e = server.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (armorTicks >= 20 && (adds.isEmpty() || armorTicks >= 300)) {
                armored = false;
                exposeTicks = 0;
                playAction("expose");
            }
        } else {
            exposeTicks++;
            if (exposeTicks >= 120) {
                startArmor();
            }
        }
    }

    private void startArmor() {
        armored = true;
        armorTicks = 0;
        pendingSummon = true;
    }

    public boolean isArmored() {
        return armored;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (armored && !level().isClientSide) {
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.5f);
                server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.2, getZ() - 1, 6, 0.8, 0.8, 0.4, 0.1);
            }
            return false;
        }
        return super.hurt(source, amount);
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

    // 倒れたとき: 試練の攻略を知らせる。アイテムは落とさない (戦利品表なし)
    @Override
    public void die(DamageSource source) {
        if (defeated) {
            return;
        }
        defeated = true;
        super.die(source);
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    getX(), getY() + 2.2, getZ(), 120, 1.2, 1.5, 1.2, 0.2);
            server.sendParticles(dust(0x7FF4FF, 1.5f), getX(), getY() + 2.2, getZ(), 80, 1.5, 1.5, 1.5, 0.1);
            TitanBackTrials.onHeartDefeated(server, this);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BONE_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    // 脈打つ光の粒を、核のあたりからこぼす
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextFloat() < 0.2f) {
            level().addParticle(dust(0x7FF4FF, 1.0f),
                    getX() + Mth.nextDouble(random, -0.8, 0.8), getY() + 2.2 + Mth.nextDouble(random, -0.8, 0.8),
                    getZ() + Mth.nextDouble(random, -0.5, 0.5), 0, 0.02, 0);
        }
    }
}
