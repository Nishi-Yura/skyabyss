package jp.revati.skyabyss.titan.young;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 巨神の「子」(docs/design/28)。笛を吹いた人の足もとに現れ、パーティ全員 (最大 6 人) を座席に乗せて、行き先まで運ぶ。
// 呼んだ人 (と同じパーティの人) だけのもの。乗っている間だけ存在し、降ろすと歌に溶ける (保存しない)。
// 状態の流れと動きは YoungTrip (サーバー側)。ここは入れ物: 状態・座席・当たり・アニメ・位置の補間
public class YoungEntity extends Entity implements GeoEntity {
    // 状態 (YoungTrip.Phase の順)
    public static final int APPEAR = 0;
    public static final int WAIT = 1;
    public static final int COUNTDOWN = 2;
    public static final int CARRY = 3;
    public static final int LAND = 4;
    public static final int OFFER = 5;
    public static final int DISSOLVE = 6;

    public static final int APPEAR_TICKS = 32;
    public static final int DISSOLVE_TICKS = 30;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MOVING = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.BOOLEAN);
    // 座席ごとの乗っている者の entity id (-1 = 空き)。降りた人がいても、ほかの人の席は動かない
    private static final EntityDataAccessor<Integer> S0 = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> S1 = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> S2 = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> S3 = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> S4 = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> S5 = SynchedEntityData.defineId(YoungEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<?>[] SEATS = {S0, S1, S2, S3, S4, S5};

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation APPEAR_ANIM = RawAnimation.begin().thenPlay("appear").thenLoop("idle");
    private static final RawAnimation DISSOLVE_ANIM = RawAnimation.begin().thenPlayAndHold("dissolve");

    public final YoungKind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private YoungTrip trip;
    private UUID owner;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public YoungEntity(EntityType<? extends YoungEntity> type, Level level, YoungKind kind) {
        super(type, level);
        this.kind = kind;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, APPEAR);
        builder.define(MOVING, false);
        for (EntityDataAccessor<?> s : SEATS) {
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Integer> seat = (EntityDataAccessor<Integer>) s;
            builder.define(seat, -1);
        }
    }

    // ---------------------------------------------------------------- 持ち主・旅

    public void setOwner(ServerPlayer player) {
        this.owner = player.getUUID();
        this.trip = new YoungTrip(this, player);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    public YoungTrip trip() {
        return trip;
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    void setPhase(int phase) {
        entityData.set(PHASE, phase);
    }

    void setMoving(boolean moving) {
        if (entityData.get(MOVING) != moving) {
            entityData.set(MOVING, moving);
        }
    }

    // ---------------------------------------------------------------- 座席

    @SuppressWarnings("unchecked")
    private int seatId(int slot) {
        return entityData.get((EntityDataAccessor<Integer>) SEATS[slot]);
    }

    @SuppressWarnings("unchecked")
    private void setSeatId(int slot, int id) {
        EntityDataAccessor<Integer> a = (EntityDataAccessor<Integer>) SEATS[slot];
        if (entityData.get(a) != id) {
            entityData.set(a, id);
        }
    }

    // この人の席 (乗っていなければ -1)
    public int slotOf(Entity passenger) {
        for (int i = 0; i < YoungKind.SEATS; i++) {
            if (seatId(i) == passenger.getId()) {
                return i;
            }
        }
        return -1;
    }

    // 席を割り当てる。空き席の若い順 (先頭 = 持ち主の席)。席がなければ false
    boolean assignSeat(Entity passenger) {
        if (slotOf(passenger) >= 0) {
            return true;
        }
        for (int i = 0; i < YoungKind.SEATS; i++) {
            if (seatId(i) < 0) {
                setSeatId(i, passenger.getId());
                return true;
            }
        }
        return false;
    }

    // 乗っていない席の印を消す
    void sweepSeats() {
        for (int i = 0; i < YoungKind.SEATS; i++) {
            int id = seatId(i);
            if (id >= 0 && getPassengers().stream().noneMatch(p -> p.getId() == id)) {
                setSeatId(i, -1);
            }
        }
    }

    public int riderCount() {
        return getPassengers().size();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        int slot = slotOf(passenger);
        if (slot < 0) {
            slot = Math.max(0, getPassengers().indexOf(passenger));
        }
        double[] s = kind.seat(slot);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        // 局所 (横 = 左が +, 前) → 世界
        return new Vec3(fx * s[2] + fz * s[0], s[1], fz * s[2] - fx * s[0]);
    }

    // 自己テスト用: FakePlayer は startRiding を受けないので、乗せる側から呼ぶ
    void seatForTest(Entity passenger) {
        addPassenger(passenger);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        int p = phase();
        return getPassengers().size() < YoungKind.SEATS && (p == APPEAR || p == WAIT || p == COUNTDOWN);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            sweepSeats();
        }
    }

    // 降りる場所は旅の終わりの輪の外 (空中では降りない。YoungTrip が決める)
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return trip != null ? trip.dismountSpot(passenger) : passenger.position();
    }

    // バニラは「プレイヤー一人だけを乗せた乗り物」をプレイヤーと一緒に保存し、ログアウトで取り除く。子は保存しない
    @Override
    public boolean hasExactlyOnePlayerPassenger() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ---------------------------------------------------------------- 当たり

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    // 右クリックで乗る (持ち主・同じパーティの人)
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            YoungCarriers.tryBoard(this, sp);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(6.0, 3.0, 6.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0 * 512.0;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, 0);
                lerpSteps--;
            }
            return;
        }
        if (trip == null || !(level() instanceof ServerLevel)) {
            discard();
            return;
        }
        trip.tick();
    }

    // 位置の補間 (サーバーが updateInterval ごとに送る位置を、その間の tick でつなぐ)
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (distanceToSqr(x, y, z) > 48.0 * 48.0) {
            this.lerpSteps = 0;
            setPos(x, y, z);
            setYRot(yRot % 360f);
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.yRotO = getYRot();
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = Math.max(1, steps);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            YoungCarriers.forget(this);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    public BlockPos feet() {
        return blockPosition();
    }

    // ---------------------------------------------------------------- アニメ

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            int phase = phase();
            if (phase == DISSOLVE) {
                return state.setAndContinue(DISSOLVE_ANIM);
            }
            if (phase == APPEAR) {
                return state.setAndContinue(APPEAR_ANIM);
            }
            return state.setAndContinue(entityData.get(MOVING) ? MOVE : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
