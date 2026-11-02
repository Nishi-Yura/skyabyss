package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.story.ch2.Ch2Mobs;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 砂潜り (二章の地上): 地面の下の敵。
//   潜っている間は見えず (描かない)、傷も受けない。人が真上 (TRIGGER の近さ) に来ると、砂が盛り上がる (予告 0.8 秒。足もとの円と砂の粒)、
//   満ちた瞬間に飛び出して噛む (4)。出たあとは近づいて飛びかかり (予告 0.8 秒の床の帯)。狙いを失って BURROW_AFTER たつと、また潜る。
//   潮: 浜のひとり歩き (ChapterMobSpawner) の個体は、満潮の間は出てこない (潜ったまま。出ていれば潜る)。
//   場所の湧き台 (沈んだ塩の蔵) の個体と卵の個体は、潮に関係なく出る (クリアをふさがない。状態で遊び方が変わるだけ)。
// 表の予告 0.6 秒は、技の予告の最短 (Telegraph.MIN_WARN_TICKS = 0.8 秒) に合わせた
public class SandBurrower extends GroundFoe {
    public static final int WARN = 16;
    public static final double TRIGGER = 1.5;
    public static final double BULGE_RADIUS = 1.6;
    public static final float BITE_DAMAGE = 4f;
    public static final double LEAP_RANGE = 3.6;
    public static final double LINE_LENGTH = 4.0;
    public static final double LINE_HALF_WIDTH = 0.8;
    public static final int LEAP_TICKS = 5;
    public static final double LEAP_SPEED = 0.62;
    public static final int BITE_END = WARN + 14;
    public static final int EMERGE_END = WARN + 12;
    public static final int BURROW_AFTER = 100;
    public static final int BURROW_TICKS = 16;

    private static final EntityDataAccessor<Boolean> BURIED = SynchedEntityData.defineId(SandBurrower.class, EntityDataSerializers.BOOLEAN);

    private enum Kind { NONE, EMERGE, BITE, BURROW }

    private Kind kind = Kind.NONE;
    private int idle;
    // 試験用: 潮の判定を差し替える (null = ワールドの潮)
    @javax.annotation.Nullable
    private Boolean tideOverride;

    public SandBurrower(EntityType<? extends Monster> type, Level level) {
        super(type, level, "sand_burrower", "leap", "bite", "burrow", "hurt", "death");
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16)
                .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 14)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURIED, true);
    }

    public boolean isBuried() {
        return entityData.get(BURIED);
    }

    private void setBuried(boolean b) {
        entityData.set(BURIED, b);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ApproachGoal(this, 1.2, 2.6) {
            @Override
            public boolean canUse() {
                return !isBuried() && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f) {
            @Override
            public boolean canUse() {
                return !isBuried() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return !isBuried() && super.canUse();
            }
        });
    }

    // 潮に縛られる個体か (浜のひとり歩きだけ)
    public boolean tideBound() {
        return ChapterMobSpawner.isLive(getUUID());
    }

    // いま出てこられるか (潮に縛られない個体はいつでも)
    public boolean mayEmerge() {
        return !bound() || lowTide();
    }

    private boolean lowTide() {
        return tideOverride != null ? tideOverride : TideOcean.isLowTide(level().getDayTime());
    }

    // 試験用: 潮を決める (null で元に戻す)、潮に縛られる個体として扱う
    public void testTide(@javax.annotation.Nullable Boolean low) {
        tideOverride = low;
    }

    private boolean testBound;

    public void testTideBound(boolean bound) {
        testBound = bound;
    }

    private boolean bound() {
        return testBound || tideBound();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (cooldown > 0) {
            cooldown--;
        }
        boolean emergeOk = !bound() || lowTide();
        if (kind == Kind.NONE) {
            if (isBuried()) {
                getNavigation().stop();
                setTarget(null);
                if (emergeOk) {
                    ServerPlayer above = above();
                    if (above != null) {
                        begin(Kind.EMERGE, above);
                    } else if (tickCount % 60 == 7 && random.nextFloat() < 0.4f) {
                        // 潜んでいる印: ときどき砂の粒が小さく跳ねる
                        sand(3, 0.2);
                    }
                }
                return;
            }
            LivingEntity target = getTarget();
            boolean has = target != null && target.isAlive();
            idle = has ? 0 : idle + 1;
            if (!emergeOk || idle >= BURROW_AFTER) {
                begin(Kind.BURROW, null);
            } else if (has && cooldown == 0 && onGround() && distanceTo(target) <= LEAP_RANGE && hasLineOfSight(target)) {
                begin(Kind.BITE, target);
            }
            return;
        }
        act++;
        switch (kind) {
            case EMERGE -> {
                getNavigation().stop();
                if (act < WARN) {
                    if (act % 3 == 0) {
                        sand(4 + act / 3, 0.35);
                    }
                } else if (act == WARN) {
                    setBuried(false);
                    playAction("leap");
                    setDeltaMovement(getDeltaMovement().x, 0.42, getDeltaMovement().z);
                }
                if (act >= EMERGE_END) {
                    finish(30);
                }
            }
            case BITE -> {
                holdYaw();
                if (act >= WARN && act < WARN + LEAP_TICKS) {
                    Vec3 f = forward();
                    setDeltaMovement(f.x * LEAP_SPEED, act == WARN ? 0.25 : getDeltaMovement().y, f.z * LEAP_SPEED);
                }
                if (act >= BITE_END) {
                    finish(40 + random.nextInt(20));
                }
            }
            case BURROW -> {
                getNavigation().stop();
                if (act % 4 == 0) {
                    sand(4, 0.3);
                }
                if (act >= BURROW_TICKS) {
                    setBuried(true);
                    setTarget(null);
                    finish(20);
                }
            }
            default -> {
            }
        }
    }

    // 真上にいる人 (足もとの近さ。高さの差 2 以内)
    @javax.annotation.Nullable
    private ServerPlayer above() {
        for (ServerPlayer p : Players.snapshot(((ServerLevel) level()).players())) {
            double dx = p.getX() - getX();
            double dz = p.getZ() - getZ();
            if (dx * dx + dz * dz <= TRIGGER * TRIGGER && Math.abs(p.getY() - getY()) < 2.0 && canHit(p)) {
                return p;
            }
        }
        return null;
    }

    private void sand(int count, double spread) {
        if (level() instanceof ServerLevel server) {
            BlockPos below = blockPosition().below();
            var state = server.getBlockState(below);
            if (state.isAir()) {
                state = Blocks.SAND.defaultBlockState();
            }
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.1, getZ(), count, spread, 0.05, spread, 0.08);
        }
    }

    private void begin(Kind next, @javax.annotation.Nullable LivingEntity target) {
        kind = next;
        act = 1;
        idle = 0;
        switch (next) {
            case EMERGE -> startEmerge(target);
            case BITE -> {
                lockYawToward(target);
                holdYaw();
                playAction("bite");
                startBite(target);
            }
            case BURROW -> {
                playAction("burrow");
                Telegraph.cancelAll(this);
            }
            default -> {
            }
        }
    }

    private void finish(int cool) {
        kind = Kind.NONE;
        act = 0;
        cooldown = cool;
    }

    // 試験用: いまの動き (NONE / EMERGE / BITE / BURROW)
    public String actionName() {
        return kind.name();
    }

    // 砂の盛り上がり (予告 0.8 秒): 足もとの円が満ち、満ちた瞬間に飛び出して円の中の人を噛む
    private void startEmerge(LivingEntity target) {
        double cx = getX();
        double cz = getZ();
        Telegraph.in((ServerLevel) level(), TelegraphShape.circle(cx, cz, BULGE_RADIUS))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.SAND_BURROWER_BULGE.get(), 1.0f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, cx, getY(), cz, MobSounds.SAND_BURROWER_EMERGE.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 1.0f);
                    sand(18, 0.5);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), BITE_DAMAGE);
                    player.push(0, 0.25, 0);
                })
                .start();
    }

    // 飛びかかり (予告 0.8 秒の床の帯): 満ちた瞬間に前へ跳ねる。当たるのは帯の中の人だけ
    private void startBite(LivingEntity target) {
        Vec3 f = forward();
        double angle = Telegraph.angleOf(f.x, f.z);
        Telegraph.in((ServerLevel) level(), TelegraphShape.line(getX(), getZ(), angle, LINE_LENGTH, LINE_HALF_WIDTH))
                .floor(getY()).warn(WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> server.playSound(null, getX(), getY(), getZ(), MobSounds.SAND_BURROWER_BITE.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), BITE_DAMAGE);
                    player.knockback(0.6, -f.x, -f.z);
                })
                .start();
    }

    // 潜っている間は傷を受けない・触れない・押されない
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return (isBuried() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) || super.isInvulnerableTo(source);
    }

    // 潜っている間は描かない (同期した BURIED から。湧いた最初の tick から見えない)
    @Override
    public boolean isInvisible() {
        return isBuried() || super.isInvisible();
    }

    @Override
    public boolean isPickable() {
        return !isBuried() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !isBuried() && super.isPushable();
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && kind == Kind.NONE) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            setBuried(false);
            playAction("death");
        }
    }

    // 塩結晶 30%
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Item salt = Ch2Mobs.item(Ch2Mobs.SALT_CRYSTAL);
        if (recentlyHit && salt != null && random.nextFloat() < 0.3f) {
            spawnAtLocation(new ItemStack(salt));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Buried", isBuried());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Buried")) {
            setBuried(tag.getBoolean("Buried"));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isBuried() ? null : MobSounds.SAND_BURROWER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.SAND_BURROWER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.SAND_BURROWER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!isBuried()) {
            modStep(MobSounds.SAND_BURROWER_STEP.get(), 0.3f);
        }
    }
}
