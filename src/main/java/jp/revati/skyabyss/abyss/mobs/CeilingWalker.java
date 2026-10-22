package jp.revati.skyabyss.abyss.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;

// 逆さ歩き (深淵だけ・docs/design/36 の 4・4.1 A 逆さの人)。天井を逆さに歩く。
//   見られている間は止まる (人の視線の中の、目に入る向き。見る者の決まりと同じ: 見ていない間だけ動く)。
//   見ていた人が目を離し、近く (5 ブロック) にいると、落ちてくる。落ちる前に 0.8 秒、天井がきしむ音と粒を見せる。
//   その間にまた見られれば落ちない。落ちたあとは地面で殴る。地面で 10 秒、人がいなければ、天井へ戻る。
public class CeilingWalker extends AbyssMobB {
    public static final String ID = "ceiling_walker";
    public static final double HP = 36;
    public static final double DMG = 8;
    public static final double LOOK_DOT = 0.8;
    public static final double LOOK_RANGE = 40;
    public static final double DROP_RANGE = 5;
    public static final int DROP_TELL = 16;
    private static final EntityDataAccessor<Boolean> CEILING = SynchedEntityData.defineId(CeilingWalker.class, EntityDataSerializers.BOOLEAN);
    private static final double SPEED = 0.07;
    private static final int CEILING_SCAN = 6;
    private int dropTell = -1;
    private boolean wasLooked;
    private int groundTicks;

    public CeilingWalker(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "attack", "drop", "hurt", "death");
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CEILING, true);
    }

    // 天井にいるか (描く側は逆さに描く)
    public boolean onCeiling() {
        return entityData.get(CEILING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Tell(this, 1.0, 1.5, 8, 24, "attack", 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        if (onCeiling()) {
            tickCeiling(sl);
        } else {
            tickGround();
        }
    }

    private void tickCeiling(ServerLevel sl) {
        setNoGravity(true);
        setTarget(null);
        BlockPos ceil = ceilingAbove();
        if (ceil == null) {
            drop(sl);
            return;
        }
        boolean looked = anyoneLooking();
        Player near = sl.getNearestPlayer(this, 24);
        // 見られていない間だけ、近い人の方へ少し歩く
        if (!looked && near != null && distanceTo(near) > 1.5) {
            Vec3 flat = new Vec3(near.getX() - getX(), 0, near.getZ() - getZ());
            Vec3 step = flat.normalize().scale(SPEED);
            double nx = getX() + step.x;
            double nz = getZ() + step.z;
            BlockPos next = BlockPos.containing(nx, getY(), nz).above(2);
            if (sl.getBlockState(next).isAir() || sl.getBlockState(next.above()).isAir()) {
                setPos(nx, ceil.getY() - getBbHeight(), nz);
            }
        } else {
            setPos(getX(), ceil.getY() - getBbHeight(), getZ());
        }
        setDeltaMovement(Vec3.ZERO);
        // 見ていた人が目を離した: 近くにいれば、落ちるまでの予告
        if (wasLooked && !looked && near != null && distanceTo(near) <= DROP_RANGE && dropTell < 0) {
            dropTell = DROP_TELL;
            playSound(AbyssMobsBSetup.CEILING_CREAK.get(), 1.0f, 1.0f);
        }
        if (looked && dropTell >= 0) {
            dropTell = -1;
        }
        if (dropTell >= 0) {
            sl.sendParticles(ParticleTypes.DRIPPING_LAVA, getX(), getY() + getBbHeight(), getZ(), 1, 0.2, 0, 0.2, 0);
            if (--dropTell < 0) {
                drop(sl);
            }
        }
        wasLooked = looked;
    }

    private void tickGround() {
        if (getTarget() == null || !getTarget().isAlive()) {
            groundTicks++;
            if (groundTicks > 200 && ceilingAbove() != null) {
                groundTicks = 0;
                entityData.set(CEILING, true);
                setNoGravity(true);
            }
        } else {
            groundTicks = 0;
        }
    }

    // 落ちる: 天井を離れ、地面で殴る
    private void drop(ServerLevel sl) {
        dropTell = -1;
        entityData.set(CEILING, false);
        setNoGravity(false);
        playAction("drop");
        playSound(AbyssMobsBSetup.CEILING_DROP.get(), 1.0f, 1.0f);
        sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 8, 0.4, 0.2, 0.4, 0.02);
        setDeltaMovement(0, -0.3, 0);
    }

    // 天井の面 (体の上の最初の固い所の下面)。なければ null。体の上 2 から CEILING_SCAN まで探す
    BlockPos ceilingAbove() {
        BlockPos base = blockPosition();
        for (int dy = 2; dy <= CEILING_SCAN; dy++) {
            BlockPos p = base.above(dy);
            if (!level().getBlockState(p).isAir() && level().getBlockState(p).isFaceSturdy(level(), p, Direction.DOWN)) {
                return p;
            }
        }
        return null;
    }

    // 見ている人がいるか (目の向きが体の方へ向いていて、間に壁がない)
    boolean anyoneLooking() {
        Vec3 here = position().add(0, getBbHeight() * 0.5, 0);
        for (ServerPlayer p : level().getServer().getPlayerList().getPlayers()) {
            if (p.level() != level() || p.isSpectator() || p.distanceToSqr(this) > LOOK_RANGE * LOOK_RANGE) {
                continue;
            }
            if (lookingAt(p.getEyePosition(), p.getLookAngle(), here) && p.hasLineOfSight(this)) {
                return true;
            }
        }
        return false;
    }

    // 目 (eye) の向き (look) が、対象 (target) の方へ向いているか (約 37 度以内)
    public static boolean lookingAt(Vec3 eye, Vec3 look, Vec3 target) {
        Vec3 to = target.subtract(eye);
        double len = to.length();
        return len > 1e-6 && look.normalize().dot(to.scale(1 / len)) > LOOK_DOT;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.SOOT_CLUMP.get(), 0.4, 1, 1);
    }
}
