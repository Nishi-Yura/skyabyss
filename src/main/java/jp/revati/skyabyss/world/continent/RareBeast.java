package jp.revati.skyabyss.world.continent;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 逃げる獣 (2026-11-03 の案「逃げる別の獣」。ドラクエのメタルスライム。docs/design/44 の 7 章)。土地ごとに一種、形の違う一体。
// 出し方・報い・その日の締め出しは RareBeasts。ここは一体のふるまい:
//   落ち着き  見つかるまでは、出た所のまわりをゆっくり歩く。人が NOTICE まで来るか、傷を受けると逃げ出す。CALM_MAX たっても誰も来なければ黙って去る
//   逃げる    一番近い人から離れる向きへ速く走る。逃げ方は形ごと:
//             野兎 = 跳ねて走り、尾の鈴が鳴る / 丸獣 = 丸まって転がり、速い / 海獺 = 近くの水へ向かい、水の中は速く泳ぐ /
//             鼬 = 稲妻のように左右へ折れて走り、尾から火花 / 土竜 = 時々潜って、離れた所から出る (潜っている間は傷つかない)
//   逃げ切る  ESCAPE_TICKS 逃げきるか、人が LOST_RANGE より遠くに離れると、消えて逃げ切る (出した人はその日、この土地では二度と出会わない)
//   硬い     体力は小さいが、一撃で減るのは HIT_CAP まで (何度か当てないと倒れない)
public class RareBeast extends PathfinderMob implements GeoEntity {
    public enum Kind {
        HARE("bell_hare", 0, 300, 1), ROLLER("moss_roller", 2, 450, 2), OTTER("pearl_otter", 3, 600, 3), WEASEL("bolt_weasel", 4, 800, 4),
        MOLE("ember_mole", 5, 1000, 5);

        public final String id;
        // 土地の番号 (RegionSpawner.REGIONS)
        public final int region;
        public final int coins;
        // 封の箱の帯 (稀の箱)
        public final int band;

        Kind(String id, int region, int coins, int band) {
            this.id = id;
            this.region = region;
            this.coins = coins;
            this.band = band;
        }

        @Nullable
        public static Kind ofRegion(int region) {
            for (Kind k : values()) {
                if (k.region == region) {
                    return k;
                }
            }
            return null;
        }

        @Nullable
        public static Kind byId(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            return null;
        }
    }

    // 状態 (手元の動きもこれで選ぶ)。CALM 落ち着き・FLEE 逃げる・SPECIAL 逃げ方の動き (転がる・泳ぐ・潜る)・EMERGE 土竜が出る
    public static final int CALM = 0;
    public static final int FLEE = 1;
    public static final int SPECIAL = 2;
    public static final int EMERGE = 3;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RareBeast.class, EntityDataSerializers.INT);

    static final double NOTICE = 12;
    static final int CALM_MAX = 20 * 45;
    static final int ESCAPE_TICKS = 20 * 26;
    static final double LOST_RANGE = 42;
    static final float HIT_CAP = 2.5f;
    static final int DIG_TICKS = 18;
    static final int EMERGE_TICKS = 14;
    static final int DIG_EVERY = 20 * 5;

    public final Kind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation run;
    private final RawAnimation special;
    private final RawAnimation emerge;
    private final RawAnimation hurt;
    private final RawAnimation death;
    @Nullable
    UUID owner;
    int calmTicks;
    int fleeTicks;
    int lostTicks;
    int retarget;
    int digTicks;
    boolean escaped;

    public RareBeast(EntityType<? extends PathfinderMob> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        String a = "animation." + kind.id + ".";
        idle = RawAnimation.begin().thenLoop(a + "idle");
        walk = RawAnimation.begin().thenLoop(a + "walk");
        run = RawAnimation.begin().thenLoop(a + "run");
        special = switch (kind) {
            case ROLLER -> RawAnimation.begin().thenLoop(a + "roll");
            case OTTER -> RawAnimation.begin().thenLoop(a + "swim");
            case MOLE -> RawAnimation.begin().thenPlayAndHold(a + "dig");
            default -> run;
        };
        emerge = RawAnimation.begin().thenPlay(a + "emerge");
        hurt = RawAnimation.begin().thenPlay(a + "hurt");
        death = RawAnimation.begin().thenPlayAndHold(a + "death");
        setPathfindingMalus(PathType.WATER, kind == Kind.OTTER ? 0f : 8f);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, CALM);
    }

    public int state() {
        return entityData.get(STATE);
    }

    void setState(int s) {
        entityData.set(STATE, s);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.45, 40) {
            @Override
            public boolean canUse() {
                return state() == CALM && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
    }

    // 逃げ出す (人が近づいた・傷を受けた)
    public void startle() {
        if (state() == CALM) {
            setState(FLEE);
            fleeTicks = 0;
            retarget = 0;
            digTicks = DIG_EVERY / 2;
            playSound(SoundEvents.RABBIT_JUMP, 0.8f, 1.4f);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Player near = level.getNearestPlayer(this, LOST_RANGE);
        if (near != null && near.isSpectator()) {
            near = null;
        }
        if (state() == CALM) {
            calmTicks++;
            double notice = near == null ? 0 : near.isSprinting() || near.isPassenger() ? NOTICE * 1.5 : NOTICE;
            if (near != null && distanceTo(near) < notice) {
                startle();
            } else if (calmTicks > CALM_MAX) {
                leave(level, false);
            }
            return;
        }
        fleeTicks++;
        lostTicks = near == null ? lostTicks + 1 : 0;
        if (fleeTicks > ESCAPE_TICKS || lostTicks > 60) {
            leave(level, true);
            return;
        }
        if (near == null) {
            return;
        }
        switch (kind) {
            case HARE -> fleeHare(level, near);
            case ROLLER -> fleeRoller(near);
            case OTTER -> fleeOtter(level, near);
            case WEASEL -> fleeWeasel(level, near);
            case MOLE -> fleeMole(level, near);
        }
    }

    // 人から離れる向き (水平)。turn = 左右へ振る角 (度)
    Vec3 away(Player p, double turn) {
        Vec3 d = new Vec3(getX() - p.getX(), 0, getZ() - p.getZ());
        if (d.lengthSqr() < 1e-4) {
            d = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        return d.normalize().yRot((float) Math.toRadians(turn));
    }

    // 離れる向きの dist ブロック先へ走る (地面の高さへ)
    void runTo(Vec3 dir, double dist, double speed) {
        Vec3 g = position().add(dir.scale(dist));
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(g.x), Mth.floor(g.z));
        getNavigation().moveTo(g.x, y, g.z, speed);
    }

    private void fleeHare(ServerLevel level, Player p) {
        if (retarget-- <= 0 || getNavigation().isDone()) {
            runTo(away(p, (random.nextDouble() - 0.5) * 50), 12, 1.6);
            retarget = 16;
        }
        if (onGround() && fleeTicks % 9 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.01) {
            jumpFromGround();
        }
        if (fleeTicks % 7 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 0.7f, 1.9f + random.nextFloat() * 0.1f);
        }
    }

    private void fleeRoller(Player p) {
        setState(SPECIAL);
        if (retarget-- <= 0 || getNavigation().isDone()) {
            runTo(away(p, (random.nextDouble() - 0.5) * 30), 14, 1.85);
            retarget = 20;
        }
        if (fleeTicks % 6 == 0 && onGround()) {
            playSound(SoundEvents.STONE_STEP, 0.6f, 0.7f);
        }
    }

    private void fleeOtter(ServerLevel level, Player p) {
        if (isInWater()) {
            setState(SPECIAL);
            Vec3 d = away(p, 0).scale(0.22);
            setDeltaMovement(d.x, Math.max(getDeltaMovement().y, 0.0) * 0.5 + (isUnderWater() ? 0.02 : -0.01), d.z);
            setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            yBodyRot = getYRot();
            if (fleeTicks % 5 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.3, getZ(), 3, 0.2, 0.1, 0.2, 0.02);
            }
            return;
        }
        setState(FLEE);
        if (retarget-- <= 0 || getNavigation().isDone()) {
            BlockPos water = nearWater(level, p);
            if (water != null) {
                getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.6);
            } else {
                runTo(away(p, (random.nextDouble() - 0.5) * 40), 12, 1.5);
            }
            retarget = 20;
        }
    }

    // 人と反対の側にある、近くの水 (12 ブロックまで)
    @Nullable
    private BlockPos nearWater(ServerLevel level, Player p) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos at = blockPosition();
        for (int dx = -12; dx <= 12; dx += 2) {
            for (int dz = -12; dz <= 12; dz += 2) {
                BlockPos c = at.offset(dx, 0, dz);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ()) - 1;
                BlockPos w = new BlockPos(c.getX(), y, c.getZ());
                if (!level.getFluidState(w).isSource()) {
                    continue;
                }
                double toMe = w.distSqr(at);
                double toThem = w.distSqr(p.blockPosition());
                if (toThem > toMe && toMe < bestD) {
                    best = w;
                    bestD = toMe;
                }
            }
        }
        return best;
    }

    private void fleeWeasel(ServerLevel level, Player p) {
        if (retarget-- <= 0 || getNavigation().isDone()) {
            // 稲妻のように左右へ折れる
            double side = (fleeTicks / 12) % 2 == 0 ? 55 : -55;
            runTo(away(p, side + (random.nextDouble() - 0.5) * 20), 9, 1.75);
            retarget = 12;
        }
        if (fleeTicks % 3 == 0) {
            Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.7);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + back.x, getY() + 0.7, getZ() + back.z, 2, 0.15, 0.2, 0.15, 0.05);
        }
    }

    private void fleeMole(ServerLevel level, Player p) {
        if (state() == SPECIAL) {
            // 潜っている: 掻いて沈み、DIG_TICKS で離れた所へ移って出る
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (++digTicks % 4 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 4, 0.3, 0.1, 0.3, 0.01);
            }
            if (digTicks >= DIG_TICKS) {
                Vec3 g = position().add(away(p, (random.nextDouble() - 0.5) * 70).scale(10 + random.nextInt(5)));
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(g.x), Mth.floor(g.z));
                if (!level.getFluidState(new BlockPos(Mth.floor(g.x), y - 1, Mth.floor(g.z))).isEmpty()) {
                    g = position();
                    y = Mth.floor(getY());
                }
                teleportTo(g.x, y, g.z);
                setState(EMERGE);
                digTicks = 0;
                level.sendParticles(ParticleTypes.LAVA, g.x, y + 0.2, g.z, 6, 0.4, 0.1, 0.4, 0.01);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, g.x, y + 0.2, g.z, 10, 0.4, 0.2, 0.4, 0.02);
                level.playSound(null, g.x, y, g.z, SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 1f, 0.7f);
            }
            return;
        }
        if (state() == EMERGE) {
            if (++digTicks >= EMERGE_TICKS) {
                setState(FLEE);
                digTicks = 0;
            }
            return;
        }
        if (++digTicks >= DIG_EVERY && onGround()) {
            setState(SPECIAL);
            digTicks = 0;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
            return;
        }
        if (retarget-- <= 0 || getNavigation().isDone()) {
            runTo(away(p, (random.nextDouble() - 0.5) * 40), 10, 1.4);
            retarget = 18;
        }
    }

    // 消える。escape = 逃げ切った (出した人の締め出し)。黙って去るときは締め出さない
    void leave(ServerLevel level, boolean escape) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.4, getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        if (escape) {
            escaped = true;
            RareBeasts.onEscaped(this);
        }
        discard();
    }

    // 一撃で減るのは HIT_CAP まで。潜っている土竜は傷つかない。傷を受けたら逃げ出す
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (kind == Kind.MOLE && state() == SPECIAL && digTicks > 6 || source.getEntity() == null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hit = super.hurt(source, Math.min(amount, HIT_CAP));
        if (hit && !level().isClientSide) {
            startle();
            triggerAnim("hurt", "hurt");
        }
        return hit;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return state() == CALM;
    }

    // ---------------------------------------------------------------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 3, s -> {
            if (isDeadOrDying()) {
                return s.setAndContinue(death);
            }
            int st = state();
            if (st == EMERGE) {
                return s.setAndContinue(emerge);
            }
            if (st == SPECIAL) {
                return s.setAndContinue(special);
            }
            double v = getDeltaMovement().horizontalDistanceSqr();
            if (v > 0.012 || st == FLEE && v > 0.002) {
                return s.setAndContinue(st == FLEE ? run : walk);
            }
            return s.setAndContinue(v > 0.0006 ? walk : idle);
        }));
        controllers.add(new AnimationController<>(this, "hurt", 0, s -> PlayState.STOP).triggerableAnim("hurt", hurt));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    static String nameOf(Kind k) {
        return SkyAbyss.MODID + ":" + k.id;
    }
}
