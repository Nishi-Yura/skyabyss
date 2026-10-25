package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
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

    // 技の数値 (印と当たりは同じ形から出る。設計書 02 の 5.2)。tick は 20 で 1 秒
    public static final int SWEEP_WARN = 22;
    public static final double SWEEP_RANGE = 5.2;
    public static final double SWEEP_HALF_ANGLE = 70;
    public static final float SWEEP_DAMAGE = 9f;
    public static final int LEAP_TAKEOFF_TICK = 12;
    public static final int LEAP_LAND_TICK = 27;
    public static final double LEAP_HEIGHT = 3.2;
    public static final double LEAP_MAX = 14;
    public static final double STOMP_RADIUS = 4.0;
    public static final double STOMP_RADIUS_ENRAGED = 5.0;
    public static final int ERUPT_WARN = 30;
    public static final double ERUPT_RADIUS = 1.4;
    public static final float ERUPT_DAMAGE = 7f;
    public static final int ROAR_WARN = 20;
    public static final double ROAR_RADIUS = 5.0;

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Action action = Action.NONE;
    private int actionTicks;
    private int sweepCooldown;
    private int leapCooldown = 80;
    private int eruptCooldown = 120;
    private boolean roared;
    private boolean holdAttacks;
    private Vec3 devAim;
    // 技の最中の向きと、跳躍の出発点・着地点 (印を出した時点で決まる)
    private float lockedYaw;
    private Vec3 leapFrom = Vec3.ZERO;
    private Vec3 leapTo = Vec3.ZERO;

    public WhaleboneCaptain(EntityType<? extends Monster> type, Level level) {
        super(type, level, "whalebone_captain", "sweep", "leap", "slam", "roar", "hurt", "death");
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
            if (holdAttacks) {
                return;
            }
            double dist = distanceTo(target);
            boolean enraged = enraged();
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

    // 試験・コマンド用: 技を一つ出す (SWEEP / LEAP / ERUPT / ROAR)
    public void forceAction(String name) {
        Action a = Action.valueOf(name.toUpperCase());
        begin(a, switch (a) {
            case SWEEP -> "sweep";
            case LEAP -> "leap";
            default -> "roar";
        });
    }

    // 開発用: 狙う人がいないときの跳躍の着地点 (台本のスクショ用)
    public void setDevAim(Vec3 aim) {
        devAim = aim;
    }

    // 試験用: 次の技を始めない
    public void holdAttacks() {
        holdAttacks = true;
    }

    public boolean isActing() {
        return action != Action.NONE;
    }

    private void begin(Action next, String animation) {
        action = next;
        actionTicks = 0;
        if (next.ordinal() != 0) {
            jp.revati.skyabyss.combat.BossCue.send(this, "captain", next.name().toLowerCase(java.util.Locale.ROOT), 30);
        }
        getNavigation().stop();
        playAction(animation);
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
    }

    private boolean enraged() {
        // 闘の部屋の中ボス (段なし。trial/raid/mid/FightBossRoom) は怒らない
        if (getTags().contains(jp.revati.skyabyss.trial.raid.mid.FightBossRoom.SINGLE_STAGE)) {
            return false;
        }
        return getHealth() < getMaxHealth() * 0.5f;
    }

    // ---- 技。どれも Telegraph (combat/Telegraph) で、当たる範囲を床に描いてから、その範囲の中の人だけに当てる ----

    // 技の最中の向きを固定する (予告の印は、始めの向きで決まる)
    private void holdYaw() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        getNavigation().stop();
    }

    private boolean canHit(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && canAttack(player);
    }

    private double floorY() {
        return getY();
    }

    // 薙ぎ払い: 印の扇 (前方 5.2・140°) が 1.1 秒かけて満ちる。後半の 0.55 秒が振りかぶりと振り (アニメ)。
    // 向きは始めに決まり、技の間は動かない。扇の外へ出れば当たらない
    private void tickSweep() {
        if (actionTicks == 1) {
            lockedYaw = getYRot();
            double rad = Math.toRadians(lockedYaw);
            double angle = Telegraph.angleOf(-Math.sin(rad), Math.cos(rad));
            Vec3 look = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            Telegraph.in((ServerLevel) level(), TelegraphShape.cone(getX(), getZ(), angle, SWEEP_RANGE, Math.toRadians(SWEEP_HALF_ANGLE)))
                    .floor(floorY()).warn(SWEEP_WARN).danger().owner(this)
                    .target(getTarget())
                    .startSound(MobSounds.CAPTAIN_ERUPT_CHARGE.get(), 1.6f, 1.5f)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.playSound(null, getX(), getY(), getZ(), MobSounds.WHALEBONE_CAPTAIN_SLAM.get(), SoundSource.HOSTILE, 2f, 0.95f + random.nextFloat() * 0.1f);
                        server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 2.5, getY() + 1.5, getZ() + look.z * 2.5, 4, 1.2, 0.2, 1.2, 0);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), SWEEP_DAMAGE);
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        player.knockback(1.2, -to.x, -to.z);
                    })
                    .start();
        }
        holdYaw();
        // 振りかぶりと振りのアニメは、当たる瞬間 (SWEEP_WARN) の 12 tick 前から (アニメの振りは 0.6 秒)
        if (actionTicks == SWEEP_WARN - 12) {
            playAction("sweep");
            level().playSound(null, getX(), getY(), getZ(), MobSounds.WHALEBONE_CAPTAIN_SWING.get(), SoundSource.HOSTILE, 1.6f, 0.95f + random.nextFloat() * 0.1f);
        }
        if (actionTicks >= SWEEP_WARN + 10) {
            sweepCooldown = enraged() ? 25 : 40;
            finish();
        }
    }

    // 跳躍と叩きつけ (スタンプ): 着地点を始めに決めて円の印を出し (1.4 秒)、しゃがんで跳び、その点へ正確に降りる。
    // 当たるのは印の円の中だけ (中心 8、縁 5。外へ弾かれ、鈍足)。円の外は安全
    private void tickLeap(LivingEntity target) {
        if (actionTicks == 1) {
            lockedYaw = getYRot();
            leapFrom = position();
            Vec3 aim = target != null ? target.position() : devAim != null ? devAim : position();
            Vec3 to = aim.subtract(leapFrom).multiply(1, 0, 1);
            double dist = to.length();
            if (dist > LEAP_MAX) {
                to = to.scale(LEAP_MAX / dist);
            }
            leapTo = new Vec3(leapFrom.x + to.x, leapFrom.y, leapFrom.z + to.z);
            double radius = stompRadius();
            final Vec3 land = leapTo;
            Telegraph.in((ServerLevel) level(), TelegraphShape.circle(land.x, land.z, radius))
                    .floor(land.y).warn(LEAP_LAND_TICK).danger().owner(this)
                    .startSound(MobSounds.CAPTAIN_ERUPT_CHARGE.get(), 2f, 0.8f)
                    .victims(this::canHit)
                    .onImpact(server -> stompEffects(server, land, radius))
                    .onHit((server, player) -> {
                        Vec3 to2 = player.position().subtract(land).multiply(1, 0, 1);
                        double d = to2.length();
                        float damage = (float) (stompDamage() * (1 - d / radius * 0.375));
                        player.hurt(attackSource(), damage);
                        Vec3 push = d > 0.01 ? to2.normalize() : new Vec3(1, 0, 0);
                        player.setDeltaMovement(player.getDeltaMovement().add(push.x * 1.1, 0.5, push.z * 1.1));
                        player.hurtMarked = true;
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    })
                    .start();
        }
        if (actionTicks < LEAP_TAKEOFF_TICK) {
            // 溜め: しゃがむ (アニメ leap の前半)。着地点の方を向いたまま
            holdYaw();
            return;
        }
        if (actionTicks < LEAP_LAND_TICK) {
            if (actionTicks == LEAP_TAKEOFF_TICK) {
                level().playSound(null, getX(), getY(), getZ(), MobSounds.CAPTAIN_LEAP_ROAR.get(), SoundSource.HOSTILE, 1.8f, 1.0f);
                setNoGravity(true);
            }
            // 放物線で、決めた着地点へ (物理に任せず、位置を直接置く)
            double t = (double) (actionTicks - LEAP_TAKEOFF_TICK) / (LEAP_LAND_TICK - LEAP_TAKEOFF_TICK);
            setPos(leapFrom.x + (leapTo.x - leapFrom.x) * t, leapFrom.y + 4 * LEAP_HEIGHT * t * (1 - t), leapFrom.z + (leapTo.z - leapFrom.z) * t);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (actionTicks == LEAP_LAND_TICK) {
            setNoGravity(false);
            setPos(leapTo.x, leapTo.y, leapTo.z);
            setDeltaMovement(Vec3.ZERO);
            playAction("slam");
            leapCooldown = enraged() ? 90 : 140;
        }
        if (actionTicks >= LEAP_LAND_TICK + 10) {
            finish();
        }
    }

    private double stompRadius() {
        return enraged() ? STOMP_RADIUS_ENRAGED : STOMP_RADIUS;
    }

    private float stompDamage() {
        return enraged() ? 9f : 8f;
    }

    private void stompEffects(ServerLevel server, Vec3 at, double radius) {
        server.playSound(null, at.x, at.y, at.z, MobSounds.CAPTAIN_SHOCKWAVE.get(), SoundSource.HOSTILE, 2.5f, 1.0f);
        server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0;
            int points = 8 + ring * 8;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                        at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 2, 0.1, 0.05, 0.1, 0.05);
            }
        }
    }

    // 骨の噴出: 足元に寄った全員と周りに、半径 1.4 の円の印 (1.5 秒)。時間が来ると、その円の中だけに骨の槍が突き上がる
    private void tickErupt(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (actionTicks == 1) {
            List<Vec3> spots = new ArrayList<>();
            int count = enraged() ? 10 : 6;
            for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(18, 4, 18), this::canAttack)) {
                spots.add(player.position());
            }
            while (spots.size() < count && target != null) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = 2 + random.nextDouble() * 5;
                spots.add(target.position().add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
            }
            boolean first = true;
            for (Vec3 p : spots) {
                var builder = Telegraph.in(server, TelegraphShape.circle(p.x, p.z, ERUPT_RADIUS))
                        .floor(p.y).warn(ERUPT_WARN).danger().owner(this)
                        .victims(this::canHit)
                        .onImpact(s -> {
                            s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                                    p.x, p.y + 1, p.z, 30, 0.3, 0.9, 0.3, 0.15);
                            s.sendParticles(ParticleTypes.CRIT, p.x, p.y + 1.5, p.z, 10, 0.2, 0.8, 0.2, 0.3);
                            s.playSound(null, BlockPos.containing(p), MobSounds.CAPTAIN_ERUPT_BURST.get(), SoundSource.HOSTILE, 1.5f, 0.95f + random.nextFloat() * 0.1f);
                        })
                        .onHit((s, player) -> {
                            player.hurt(attackSource(), ERUPT_DAMAGE);
                            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.85, 0));
                            player.hurtMarked = true;
                        });
                if (first) {
                    builder.startSound(MobSounds.CAPTAIN_ERUPT_CHARGE.get(), 2f, 1.0f);
                    first = false;
                }
                builder.start();
            }
        }
        if (actionTicks >= ERUPT_WARN + 10) {
            eruptCooldown = enraged() ? 130 : 200;
            finish();
        }
    }

    // 咆哮: 半径 5 の円の印 (1 秒)。時間が来ると円の中を吹き飛ばし、骨の番兵を一体呼び、怒りで速くなる (一度だけ)
    private void tickRoar() {
        if (actionTicks == 1) {
            roared = true;
            lockedYaw = getYRot();
            Telegraph.in((ServerLevel) level(), TelegraphShape.circle(getX(), getZ(), ROAR_RADIUS))
                    .floor(floorY()).warn(ROAR_WARN).danger().owner(this)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        shockwave(server, ROAR_RADIUS);
                        for (int i = 0; i < 1; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            BlockPos at = BlockPos.containing(getX() + Math.cos(angle) * 3, getY(), getZ() + Math.sin(angle) * 3);
                            TitanBackTrials.spawnSummon(server, "sentinel", at, groupId(), this);
                        }
                        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false));
                        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 0, false, false));
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), 4f);
                        Vec3 to = player.position().subtract(position()).multiply(1, 0, 1);
                        Vec3 push = to.length() > 0.01 ? to.normalize() : new Vec3(1, 0, 0);
                        player.setDeltaMovement(player.getDeltaMovement().add(push.x * 1.2, 0.5, push.z * 1.2));
                        player.hurtMarked = true;
                    })
                    .start();
        }
        holdYaw();
        if (actionTicks == 6) {
            level().playSound(null, getX(), getY(), getZ(), MobSounds.CAPTAIN_ROAR.get(), SoundSource.HOSTILE, 3f, 1.0f);
        }
        if (actionTicks >= ROAR_WARN + 12) {
            finish();
        }
    }

    private void shockwave(ServerLevel server, double radius) {
        stompEffects(server, position(), radius);
    }

    // 近づいてきた相手には、技の合間でもふつうに鉾で殴る
    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("sweep");
        return super.doHurtTarget(target);
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

    // 被弾・倒れるアニメ (tools/gen/mobs/gen_guard_hurt_death.py)
    @Override
    protected boolean autoHurtDeath() {
        return true;
    }
}
