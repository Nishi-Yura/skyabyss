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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 呼び笛で来る乗り物 (docs/design/38)。馬の操縦 (鞍つき・自分で歩かせる・跳びの溜め) をそのまま使い、
// 飼いならし・繁殖・持ち物は持たない。持ち主の人だけが乗れる。世界に保存しない (笛で帰す・持ち主が去る・遠く離れる・倒れると消える)。
// 見た目 (MountLook) と性能 (MountStats の段) は持ち主の MountData から MountService が与える。落下で傷つかない
public class MountEntity extends AbstractHorse implements GeoEntity {
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(MountEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mount.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mount.walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;

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
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MountStats.health(data.tier(MountStats.HEALTH)));
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
        return new Vec3(0, look().seat * scale, 0);
    }

    // ---------------------------------------------------------------- ふるまい

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) {
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
    public boolean isTamed() {
        return true;
    }

    @Override
    public boolean canMate() {
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
        controllers.add(new AnimationController<>(this, "move", 4, state -> state.setAndContinue(
                state.isMoving() ? WALK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
