package jp.revati.skyabyss.mount;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 乗り笛で来る乗り物 (docs/design/38)。馬の操縦 (鞍つき・自分で歩かせる・跳びの溜め) をそのまま使い、
// 飼いならし・繁殖・持ち物は持たない。持ち主の人だけが乗れる。世界に保存しない (笛で帰す・持ち主が去る・遠く離れる・倒れると消える)。
// 見た目 (MountLook) と性能 (MountStats の段) は持ち主の MountData から MountService が与える。落下で傷つかない
public class MountEntity extends AbstractHorse implements GeoEntity {
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(MountEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mount.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mount.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.mount.run");
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold("animation.mount.jump");
    // 歩き・駆けの境 (ブロック/tick)。乗って前へ進むと 0.3〜0.55 になる
    private static final double WALK_FROM = 0.012;
    private static final double RUN_FROM = 0.2;
    // 飛ぶ乗り物 (38 の 6 章): 速さの段の値 × FLY_SCALE ブロック/tick (段 8 の 0.44 で 1 秒に 21 ブロックほど。地上の 1.2 倍)、
    // 地面 (動きを止める面) から FLY_CEILING ブロックより上へは上がらない。羽ばたき (跳びのキー) は上へ FLAP + 跳びの段の分
    static final double FLY_SCALE = 2.4;
    static final int FLY_CEILING = 40;
    static final double FLAP = 0.32;
    // 相乗り (2026-11-03 の案「相乗りの道」。38 の 7 章): 持ち主の後ろに同じパーティの人を一人。後ろの席は胴の幅 × TANDEM_BACK だけ後ろ
    static final double TANDEM_BACK = 0.42;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    // 描画側だけで数える: 歩いた距離 (ブロック。脚の運びを地面の速さに合わせる molang の variable.mount_dist)・滑らかにした速さ・宙にいる tick 数
    private double travel;
    private double travelO;
    private double gaitSpeed;
    private int airTicks;

    public MountEntity(EntityType<? extends MountEntity> type, Level level) {
        super(type, level);
        setFlag(2, true); // 飼いならし済み
        setFlag(4, true); // 鞍つき
    }

    public static AttributeSupplier.Builder attributes() {
        return AbstractHorse.createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, MountStats.speed(0))
                .add(Attributes.JUMP_STRENGTH, MountStats.jump(0))
                .add(Attributes.SAFE_FALL_DISTANCE, 1024)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LOOK.equals(key)) {
            refreshDimensions();
        }
    }

    public MountLook look() {
        return MountLook.byOrdinal(entityData.get(LOOK));
    }

    public void setLook(MountLook look) {
        entityData.set(LOOK, look.ordinal());
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(ServerPlayer player) {
        this.owner = player.getUUID();
    }

    // 持ち主の持ち物の見た目と段を当てる。healFull = 体力を満たす (呼んだとき)
    public void apply(MountData data, boolean healFull) {
        setLook(data.chosen());
        double oldMax = getMaxHealth();
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(MountStats.speed(data.tier(MountStats.SPEED)));
        getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(MountStats.jump(data.tier(MountStats.JUMP)));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MountStats.FIXED_HEALTH);
        if (healFull) {
            setHealth(getMaxHealth());
        } else if (getMaxHealth() > oldMax) {
            setHealth((float) Math.min(getMaxHealth(), getHealth() + (getMaxHealth() - oldMax)));
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        MountLook l = look();
        return EntityDimensions.scalable(l.width, l.height);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        Vec3 seat = new Vec3(0, look().seat * scale, 0);
        if (getPassengers().indexOf(passenger) == 1) {
            return seat.add(new Vec3(0, 0.04, -look().width * TANDEM_BACK * scale).yRot(-yBodyRot * Mth.DEG_TO_RAD));
        }
        return seat;
    }

    // 席は二つ (持ち主と、後ろに一人)
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    // 操るのは前の席の持ち主だけ (持ち主はサーバーだけが知っている。手元では前の席の人)
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        LivingEntity driver = super.getControllingPassenger();
        if (driver != null && !level().isClientSide && owner != null && !owner.equals(driver.getUUID())) {
            return null;
        }
        return driver;
    }

    // 後ろに乗れるか: 持ち主が前に乗っていて、席が空いていて、同じパーティの人
    boolean canRideBehind(ServerPlayer rider) {
        return getPassengers().size() == 1 && getFirstPassenger() instanceof ServerPlayer driver && owner != null && owner.equals(driver.getUUID())
                && driver != rider && jp.revati.skyabyss.party.Parties.sameParty(driver, rider);
    }

    // 前の席に持ち主がいなければ、後ろの人も降ろす (持ち主が降りた・抜けた)
    void tidyRiders() {
        if (isVehicle() && owner != null && !(getFirstPassenger() instanceof Player first && owner.equals(first.getUUID()))) {
            ejectPassengers();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tidyRiders();
        }
        if (level().isClientSide) {
            double dx = getX() - xo;
            double dz = getZ() - zo;
            double v = Math.min(1.5, Math.sqrt(dx * dx + dz * dz));
            travelO = travel;
            travel += v;
            gaitSpeed = gaitSpeed * 0.6 + v * 0.4;
            airTicks = onGround() || isInWater() || isInLava() ? 0 : airTicks + 1;
        }
        // 飛ぶ乗り物は、人が乗っているあいだ落ちない (降りると地面へ下りる。落下では傷つかない)
        boolean fly = flying();
        if (isNoGravity() != fly) {
            setNoGravity(fly);
        }
    }

    // 飛んでいるか (飛ぶ見た目で、持ち主が乗っている)
    public boolean flying() {
        return look().flies() && getControllingPassenger() instanceof Player;
    }

    // 飛ぶ乗り物: 見ている向きへ飛ぶ (上を向けば上がる・下を向けば下がる)。前へ押さなければ宙に止まる。少しずつ速さを合わせる (惰性)
    @Override
    public void travel(Vec3 input) {
        if (!flying() || !(getControllingPassenger() instanceof Player p)) {
            super.travel(input);
            return;
        }
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED) * FLY_SCALE;
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 want = look.scale(input.z).add(left.scale(input.x));
        if (want.lengthSqr() > 1) {
            want = want.normalize();
        }
        Vec3 v = getDeltaMovement().scale(0.88).add(want.scale(speed * 0.12));
        // 高さの上限: 地面から FLY_CEILING より上へは上がらない (越えたら少しずつ下げる)
        int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        double over = getY() - (ground + FLY_CEILING);
        if (over > 0 && v.y > 0) {
            v = new Vec3(v.x, 0, v.z);
        }
        if (over > 1) {
            v = new Vec3(v.x, -0.08, v.z);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    // 跳びのキー: 飛ぶ乗り物は宙でも羽ばたいて上がる (溜めの強さと跳びの段で大きく)
    @Override
    public void onPlayerJump(int power) {
        if (!look().flies() || !isVehicle()) {
            super.onPlayerJump(power);
            return;
        }
        double k = Mth.clamp(power, 0, 100) / 100.0;
        int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        if (getY() < ground + FLY_CEILING) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Math.max(v.y, 0) + FLAP * (0.5 + 0.5 * k) * (0.6 + getAttributeValue(Attributes.JUMP_STRENGTH)), v.z);
        }
        playSound(SoundEvents.PHANTOM_FLAP, 0.5f, 1.2f);
    }

    // 描画用: 歩いた距離 (部分 tick で補間)
    public double travel(float partialTick) {
        return travelO + (travel - travelO) * partialTick;
    }

    // ---------------------------------------------------------------- ふるまい

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || player.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        if (isVehicle()) {
            // 相乗り: 持ち主が乗っていれば、同じパーティの人が後ろに乗る
            if (level().isClientSide) {
                return getPassengers().size() == 1 ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer sp && canRideBehind(sp)) {
                sp.startRiding(this);
                if (getFirstPassenger() instanceof ServerPlayer driver) {
                    jp.revati.skyabyss.notice.Notice.hint(driver, "message.skyabyss.mount.tandem_on", sp.getDisplayName());
                }
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer sp) {
                jp.revati.skyabyss.notice.Notice.hint(sp, "message.skyabyss.mount.tandem_party");
            }
            return InteractionResult.PASS;
        }
        if (player.getUUID().equals(owner)) {
            if (!level().isClientSide) {
                doPlayerRide(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
    }

    @Override
    public boolean isSaddled() {
        return true;
    }

    @Override
    public boolean canParent() {
        return false;
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || super.isInvulnerableTo(source);
    }

    // 他の人は傷つけられない (持ち主は笛で帰せる)。敵・火・溶岩からは傷つく
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            return false;
        }
        return super.hurt(source, amount);
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
    public boolean canBeLeashed() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.GOAT_STEP, 0.15f, 1.0f);
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 宙 (跳び・落ち) → 跳び、速い → 駆け、動いている → 歩き、止まる → 待機。脚の運びは歩いた距離で進む (アニメの式の variable.mount_dist)
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            // 飛ぶ乗り物は宙にいるのがふつうなので、跳びの姿にはしない (待機・歩き・駆け = ゆっくり・ふつう・強い羽ばたき)
            if (airTicks > 2 && !look().flies()) {
                return state.setAndContinue(JUMP);
            }
            if (gaitSpeed > RUN_FROM) {
                return state.setAndContinue(RUN);
            }
            return state.setAndContinue(gaitSpeed > WALK_FROM ? WALK : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // 自己テスト用: FakePlayer は startRiding できないので、席に直接座らせる
    void seatForTest(Entity rider) {
        addPassenger(rider);
    }
}
