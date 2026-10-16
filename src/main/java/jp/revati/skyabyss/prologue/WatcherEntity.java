package jp.revati.skyabyss.prologue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 「見る者」。遠くに立って、こちらを見ているだけの人影。
// サーバーには存在しない: 見せる相手のクライアントが自分の世界にだけ置き、動きも消え方もそこで決める
// (client/prologue/WatcherClient)。触れない、押せない、攻撃できない、保存されない。
public class WatcherEntity extends Entity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.watcher.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 以下はクライアントの WatcherClient が毎 tick 書き換える、描画用の値
    // 姿の濃さ (0 = 見えない, 1 = そのまま)
    public float alpha = 1f;
    public float prevAlpha = 1f;
    // 目の光 (0〜1)
    public float eyeGlow = 0.6f;
    public float prevEyeGlow = 0.6f;
    // 体の向きに対する頭の向き (度)、首のかしげ (度)
    public float headYaw;
    public float prevHeadYaw;
    public float headPitch;
    public float prevHeadPitch;
    public float tilt;
    public float prevTilt;

    public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        // サーバーに紛れ込んだら (コマンドなどで) すぐ消す
        if (!level().isClientSide) {
            discard();
            return;
        }
        prevAlpha = alpha;
        prevEyeGlow = eyeGlow;
        prevHeadYaw = headYaw;
        prevHeadPitch = headPitch;
        prevTilt = tilt;
        super.tick();
    }

    public float alpha(float partialTick) {
        return Mth.lerp(partialTick, prevAlpha, alpha);
    }

    public float eyeGlow(float partialTick) {
        return Mth.lerp(partialTick, prevEyeGlow, eyeGlow);
    }

    public float headYaw(float partialTick) {
        return Mth.rotLerp(partialTick, prevHeadYaw, headYaw);
    }

    public float headPitch(float partialTick) {
        return Mth.lerp(partialTick, prevHeadPitch, headPitch);
    }

    public float tilt(float partialTick) {
        return Mth.lerp(partialTick, prevTilt, tilt);
    }

    // 遠くからでも描く (既定では大きさから決まる距離より遠いと描かれない)
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192.0 * 192.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
