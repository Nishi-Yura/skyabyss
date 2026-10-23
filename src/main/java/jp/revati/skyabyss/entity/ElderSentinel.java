package jp.revati.skyabyss.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.Ch1BackSounds;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 老番兵 (docs/design/21 の 3.3、案 B「石板の守り手」): 骨の番兵の大きな変種。胸と腹に石の板、折れた角、石の盾。HP 220 (人数で増える。
// 番兵長と同じ式 StoneWhaleHeart.scaleFor)、突進 10・足踏み 8。技は三つで、どれも予告 (Telegraph) が先に出る。普通の殴りは持たない。
//  - 盾の突進: 目標へ向く線 (幅 2.4、最長 12) が 1.2 秒かけて満ち、最後の 12 tick で線に沿って突っ込む。傷は走り終えた瞬間に線の中の人へ。
//    走り終えたあと 1.2 秒よろめく (反撃の隙)
//  - 足踏み: 足もとの半径 3.5 の円 (1.0 秒)。近づかれると使う。片脚を高く上げ拳を頭上へ
//  - 骨笛: 笛を口へ当てて鳴らす (1.2 秒)。傷のない灰色の円が射手の湧く足もとに出て、時間が来るとそこから骨棘の射手が現れ、近くの味方は少し速くなる。
//    射手は 2 体 (近くの人が増えるごとに 1 体ずつ、最大 4)。射手が 2 体以上生きているあいだは吹かない
//  - 盾: 正面からの傷は半分 (回り込めば効く)
public class ElderSentinel extends GuardMob {
    private enum Action { NONE, RUSH, STOMP, FLUTE }

    public static final int RUSH_WARN = 24;
    public static final int RUSH_DASH_TICKS = 12;
    public static final double RUSH_HALF_WIDTH = 1.2;
    public static final double RUSH_MAX = 12;
    public static final float RUSH_DAMAGE = 10f;
    public static final int RUSH_RECOVER = 24;
    public static final int STOMP_WARN = 20;
    public static final double STOMP_RADIUS = 3.5;
    public static final float STOMP_DAMAGE = 8f;
    public static final int FLUTE_WARN = 24;
    public static final double FLUTE_SPOT_RADIUS = 1.0;
    public static final int MAX_SUMMONS = 3;   // 2026-10-23: 呼ぶ技は重く (4 → 3)

    private Action action = Action.NONE;
    private int actionTicks;
    private int rushCooldown = 80;
    private int stompCooldown = 40;
    private int fluteCooldown = 300;
    private float lockedYaw;
    private Vec3 dashDir = Vec3.ZERO;
    private Vec3 dashEnd = Vec3.ZERO;
    private final List<BlockPos> fluteSpots = new ArrayList<>();
    private boolean holdAttacks;

    public ElderSentinel(EntityType<? extends Monster> type, Level level) {
        super(type, level, "elder_sentinel", "attack", "block", "rush", "stomp", "flute", "hurt", "death");
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 220)
                .add(Attributes.ATTACK_DAMAGE, 9)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // 試験・コマンド用
    public void holdAttacks() {
        holdAttacks = true;
    }

    public void forceAction(String name) {
        holdAttacks = false;
        begin(Action.valueOf(name.toUpperCase()));
    }

    public boolean isActing() {
        return action != Action.NONE;
    }

    private boolean canHit(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && canAttack(player);
    }

    // 普通の殴りは持たない (傷は予告のある三つの技だけ)
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return false;
    }

    // この老番兵が呼んだ (近くにいる同じグループの) 射手の数
    private int summonsAlive(ServerLevel server) {
        return server.getEntitiesOfClass(RibArcher.class, getBoundingBox().inflate(40, 12, 40),
                a -> a.isAlive() && a.getTags().contains(TitanBackTrials.MOB_TAG) && Objects.equals(a.groupId(), groupId())).size();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        rushCooldown = Math.max(0, rushCooldown - 1);
        stompCooldown = Math.max(0, stompCooldown - 1);
        fluteCooldown = Math.max(0, fluteCooldown - 1);
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
            if (stompCooldown == 0 && dist < STOMP_RADIUS + 0.6) {
                begin(Action.STOMP);
            } else if (rushCooldown == 0 && dist > 5 && dist < 14 && hasLineOfSight(target)) {
                begin(Action.RUSH);
            } else if (fluteCooldown == 0 && dist < 22 && summonsAlive(server) == 0) {
                begin(Action.FLUTE);
            } else {
                getNavigation().moveTo(target, dist > 5 ? 1.1 : 0.8);
            }
            return;
        }
        actionTicks++;
        switch (action) {
            case RUSH -> tickRush(server);
            case STOMP -> tickStomp(server);
            case FLUTE -> tickFlute(server);
            default -> {
            }
        }
    }

    private void begin(Action next) {
        action = next;
        actionTicks = 0;
        if (next.ordinal() != 0) {
            jp.revati.skyabyss.combat.BossCue.send(this, "elder_sentinel", next.name().toLowerCase(java.util.Locale.ROOT), 30);
        }
        getNavigation().stop();
        fluteSpots.clear();
        playAction(switch (next) {
            case RUSH -> "rush";
            case STOMP -> "stomp";
            default -> "flute";
        });
    }

    private void finish() {
        action = Action.NONE;
        actionTicks = 0;
    }

    private void holdYaw() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        getNavigation().stop();
    }

    private void faceTarget(LivingEntity target) {
        if (target != null) {
            lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * 180 / Math.PI) - 90f;
        } else {
            lockedYaw = getYRot();
        }
    }

    private void playTo(ServerLevel server, SoundEvent sound, float volume, float pitch) {
        if (!jp.revati.skyabyss.titan.group.GroupOwned.playToGroup(this, sound, volume, pitch)) {
            server.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
        }
    }

    // ---- 盾の突進 ----
    private void tickRush(ServerLevel server) {
        if (actionTicks == 1) {
            LivingEntity target = getTarget();
            faceTarget(target);
            double rad = Math.toRadians(lockedYaw);
            dashDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            double want = target == null ? RUSH_MAX : Math.hypot(target.getX() - getX(), target.getZ() - getZ()) + 3.0;
            double length = Mth.clamp(want, 8.0, RUSH_MAX);
            dashEnd = new Vec3(getX() + dashDir.x * length, getY(), getZ() + dashDir.z * length);
            Vec3 dir = dashDir;
            Telegraph.in(server, TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(dir.x, dir.z), length, RUSH_HALF_WIDTH))
                    .floor(getY()).warn(RUSH_WARN).danger().owner(this)
                    .target(target)
                    .startSound(Ch1BackSounds.ELDER_RUSH.get(), 1.8f, 1.0f)
                    .victims(this::canHit)
                    .onImpact(s -> s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                            getX(), getY() + 0.3, getZ(), 20, 0.5, 0.2, 0.5, 0.1))
                    .onHit((s, player) -> {
                        player.hurt(attackSource(), RUSH_DAMAGE);
                        player.setDeltaMovement(player.getDeltaMovement().add(dir.x * 1.2, 0.45, dir.z * 1.2));
                        player.hurtMarked = true;
                    })
                    .start();
        }
        holdYaw();
        int dashStart = RUSH_WARN - RUSH_DASH_TICKS;
        if (actionTicks > dashStart && actionTicks <= RUSH_WARN) {
            int remaining = RUSH_WARN - actionTicks + 1;
            Vec3 delta = new Vec3(dashEnd.x - getX(), 0, dashEnd.z - getZ()).scale(1.0 / remaining);
            move(MoverType.SELF, new Vec3(delta.x, -0.1, delta.z));
            if (actionTicks % 3 == 0) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                        getX(), getY() + 0.2, getZ(), 6, 0.4, 0.1, 0.4, 0.05);
            }
        }
        if (actionTicks >= RUSH_WARN + RUSH_RECOVER) {
            rushCooldown = 110;
            finish();
        }
    }

    // ---- 足踏み ----
    private void tickStomp(ServerLevel server) {
        if (actionTicks == 1) {
            faceTarget(getTarget());
            double cx = getX();
            double cz = getZ();
            Telegraph.in(server, TelegraphShape.circle(cx, cz, STOMP_RADIUS))
                    .floor(getY()).warn(STOMP_WARN).danger().owner(this)
                    .target(getTarget())
                    .victims(this::canHit)
                    .onImpact(s -> {
                        playTo(s, Ch1BackSounds.ELDER_STOMP.get(), 2.2f, 0.95f + random.nextFloat() * 0.1f);
                        for (int ring = 1; ring <= 3; ring++) {
                            double r = STOMP_RADIUS * ring / 3.0;
                            int points = 8 + ring * 6;
                            for (int i = 0; i < points; i++) {
                                double a = Math.PI * 2 * i / points;
                                s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                                        cx + Math.cos(a) * r, getY() + 0.1, cz + Math.sin(a) * r, 1, 0.1, 0.05, 0.1, 0.05);
                            }
                        }
                    })
                    .onHit((s, player) -> {
                        player.hurt(attackSource(), STOMP_DAMAGE);
                        Vec3 push = player.position().subtract(cx, player.getY(), cz);
                        push = push.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : push.normalize();
                        player.setDeltaMovement(player.getDeltaMovement().add(push.x * 1.0, 0.4, push.z * 1.0));
                        player.hurtMarked = true;
                    })
                    .start();
        }
        holdYaw();
        if (actionTicks >= STOMP_WARN + 10) {
            stompCooldown = 60;
            finish();
        }
    }

    // ---- 骨笛 ----
    // 射手の数: 1 体 + 近くの人が増えるごとに 1 体 (最大 MAX_SUMMONS)
    public static int summonCount(int nearbyPlayers) {
        return Math.min(MAX_SUMMONS, 1 + Math.max(0, nearbyPlayers - 1));
    }

    private void tickFlute(ServerLevel server) {
        if (actionTicks == 1) {
            faceTarget(getTarget());
            int near = server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24, 8, 24), this::canHit).size();
            int count = Math.min(summonCount(near), MAX_SUMMONS - summonsAlive(server));
            for (int i = 0; i < count; i++) {
                BlockPos spot = freeSpot(server, i, count);
                if (spot != null) {
                    fluteSpots.add(spot);
                }
            }
            playTo(server, Ch1BackSounds.ELDER_FLUTE.get(), 2.4f, 1.0f);
            for (BlockPos spot : fluteSpots) {
                // 傷のない灰色の円。射手の湧く場所 (傷はないので、踏んでも害はない)
                Telegraph.in(server, TelegraphShape.circle(spot.getX() + 0.5, spot.getZ() + 0.5, FLUTE_SPOT_RADIUS))
                        .floor(spot.getY()).warn(FLUTE_WARN).harmless().owner(this)
                        .onImpact(s -> {
                            s.sendParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5, 14, 0.3, 0.6, 0.3, 0.03);
                            TitanBackTrials.spawnSummon(s, "archer", spot, groupId(), this);
                        })
                        .start();
            }
        }
        holdYaw();
        // 笛の音に合わせて、頭のまわりに音符の粒
        if (actionTicks % 4 == 0) {
            server.sendParticles(ParticleTypes.NOTE, getX(), getY() + 3.1, getZ(), 1, 0.4, 0.2, 0.4, 1.0);
        }
        if (actionTicks == FLUTE_WARN) {
            // 近くの味方 (番兵・射手・砲手) が速くなる
            AABB around = getBoundingBox().inflate(14, 6, 14);
            for (GuardMob ally : server.getEntitiesOfClass(GuardMob.class, around,
                    m -> m != this && m.isAlive() && Objects.equals(m.groupId(), groupId()) && !(m instanceof WhaleboneCaptain))) {
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, 0, false, true));
            }
        }
        if (actionTicks >= FLUTE_WARN + 12) {
            fluteCooldown = 720;
            finish();
        }
    }

    // 笛の円を置く足もと: 自分から 3〜6 の輪の上で、床があって頭の上が空いている所
    private BlockPos freeSpot(ServerLevel server, int index, int count) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = Math.PI * 2 * (index + random.nextDouble() * 0.6) / count + random.nextDouble() * 0.4;
            double r = 3 + random.nextDouble() * 3;
            BlockPos p = BlockPos.containing(getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r);
            if (server.getBlockState(p.below()).isSolid() && server.getBlockState(p).isAir() && server.getBlockState(p.above()).isAir()
                    && !fluteSpots.contains(p)) {
                return p;
            }
        }
        return null;
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
                if (action == Action.NONE) {
                    playAction("block");
                }
                level().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_SENTINEL_GUARD.get(), SoundSource.HOSTILE, 1.2f, 0.75f + random.nextFloat() * 0.2f);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        Telegraph.cancelAll(this);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                    getX(), getY() + 1.5, getZ(), 60, 0.7, 1.1, 0.7, 0.15);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch1BackSounds.ELDER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch1BackSounds.ELDER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch1BackSounds.ELDER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(Ch1BackSounds.ELDER_STEP.get(), 0.6f);
    }

    @Override
    protected float getSoundVolume() {
        return 1.5f;
    }

    // 被弾・倒れるアニメ (tools/gen/mobs/gen_guard_hurt_death.py)
    @Override
    protected boolean autoHurtDeath() {
        return true;
    }
}
