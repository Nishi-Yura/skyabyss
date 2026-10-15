package jp.revati.skyabyss.titan;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 第一の巨神「岩鯨」。背中に古代の遺跡を乗せ、雲の高さをゆっくり泳ぐ。
// ときどきプレイヤーの近くまで降りてきて、背中の尾の側にある錨石から脇腹ごしに鎖を地上へ垂らす。
// 鎖を右クリックすると、巻き上げられて背中へ引き上げられる。
public class StoneWhaleEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> CHAIN = SynchedEntityData.defineId(StoneWhaleEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.stone_whale.swim");

    // 鎖の付け根まわり (採用案 B のモデルに合わせる。geo の 1 単位は 0.5 ブロック。ゲームでは geo の +X が左)
    // 錨石 (geo の chain 骨、左のわき腹 x 19.5〜22.5、y 37〜41、z 60〜62 単位) の中心は、
    //   後ろ 61 単位 = 30.5 ブロック、左へ 21 単位 = 10.5 ブロック、上へ 39 単位 = 19.5 ブロック
    // 体の左わき腹は z=60 付近で x=19.5 単位 (横 9.75 ブロック)。鎖はその外側 (中心 21 単位) から真下へ垂れる
    // 鎖の先頭 (付け根): 錨石の真下、model の鎖の 3 節 (y 28〜36 単位) の下端 y=28 単位 = 14 ブロック
    private static final double CHAIN_BACK = 30.5;
    private static final double ANCHOR_SIDE = 10.5;
    public static final double ANCHOR_UP = 19.5;
    private static final double CHAIN_SIDE = 10.5;
    public static final double CHAIN_UP = 14.0;
    // 鎖の先は地面の何ブロック上で止まるか (REST の鎖の伸び)
    private static final double CHAIN_END_HEIGHT = 2.5;
    // 鎖の伸び縮みの速さ (ブロック/tick)。地面の高さが変わっても急に跳ねないようにする
    private static final float CHAIN_GROW = 0.6f;
    private static final float CHAIN_SHRINK = 1.2f;
    private static final float CRUISE_SPEED = 0.2f;
    private static final float APPROACH_SPEED = 0.12f;
    private static final float TURN_RATE = 0.5f;
    private static final double CRUISE_CLEARANCE = 75.0;
    private static final double REST_CLEARANCE = 34.0;
    private static final int REST_DURATION = 3600;

    private enum Mode { CRUISE, APPROACH, REST }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Mode mode = Mode.CRUISE;
    private double targetX;
    private double targetZ;
    private boolean hasTarget;
    private int restCooldown = 2400;
    private int restTicks;
    private int songCooldown = 200;
    private double groundY = 64;
    private double surroundingMax = 64;
    @Nullable
    private WhaleChainEntity chainHandle;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public StoneWhaleEntity(EntityType<? extends StoneWhaleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHAIN, 0f);
    }

    // 腹から垂れている鎖の長さ (0 = たたんでいる)
    public float chainLength() {
        return this.entityData.get(CHAIN);
    }

    // 鎖の付け根 (背中の縁) の位置。向きに合わせて回す
    public Vec3 chainTop() {
        return chainTop(getX(), getY(), getZ(), getYRot());
    }

    public static Vec3 chainTop(double x, double y, double z, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(x - fx * CHAIN_BACK + fz * CHAIN_SIDE, y + CHAIN_UP, z - fz * CHAIN_BACK - fx * CHAIN_SIDE);
    }

    // 鎖を背中に留めている所 (描画用)
    public static Vec3 chainAnchor(double x, double y, double z, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(x - fx * CHAIN_BACK + fz * ANCHOR_SIDE, y + ANCHOR_UP, z - fz * CHAIN_BACK - fx * ANCHOR_SIDE);
    }

    public Vec3 chainBottom() {
        Vec3 top = chainTop();
        return new Vec3(top.x, top.y - chainLength(), top.z);
    }

    // 鎖が地面近くまで下りきっているか
    public boolean isChainDown() {
        return mode == Mode.REST && chainLength() > 4f && chainBottom().y <= groundY + CHAIN_END_HEIGHT + 0.5;
    }

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
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 20 == 0) {
            sampleGround(level);
        }
        switch (mode) {
            case CRUISE -> tickCruise(level);
            case APPROACH -> tickApproach(level);
            case REST -> tickRest(level);
        }
        if (--songCooldown <= 0) {
            songCooldown = 1600 + random.nextInt(1600);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 16f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    private void tickCruise(ServerLevel level) {
        if (!hasTarget || horizontalDistanceTo(targetX, targetZ) < 30) {
            pickCruiseTarget(level);
        }
        double altitude = Math.min(level.getMaxBuildHeight() - 40, Math.max(surroundingMax + CRUISE_CLEARANCE, 170));
        steer(targetX, targetZ, altitude, CRUISE_SPEED);
        retractChain();

        if (--restCooldown <= 0) {
            ServerPlayer player = nearestPlayer(level, 320);
            if (player == null) {
                restCooldown = 1200;
                return;
            }
            // プレイヤーの少し横に降り、鎖がすぐ近くに届くようにする
            double angle = random.nextDouble() * Math.PI * 2;
            targetX = player.getX() + Math.cos(angle) * 14;
            targetZ = player.getZ() + Math.sin(angle) * 14;
            hasTarget = true;
            mode = Mode.APPROACH;
        }
    }

    private void tickApproach(ServerLevel level) {
        steer(targetX, targetZ, surroundingMax + REST_CLEARANCE, APPROACH_SPEED);
        retractChain();
        if (horizontalDistanceTo(targetX, targetZ) < 6 && Math.abs(getY() - (surroundingMax + REST_CLEARANCE)) < 2) {
            mode = Mode.REST;
            restTicks = REST_DURATION;
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.8f);
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 16f, 0.85f);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(this) < 200 * 200) {
                    player.sendSystemMessage(ModText.lore("message.skyabyss.titan_descends"));
                }
            }
        }
    }

    private void tickRest(ServerLevel level) {
        // ほとんど止まり、ゆっくり漂う
        setDeltaMovement(getDeltaMovement().scale(0.9));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        float wanted = (float) Math.max(0, chainTop().y - groundY - CHAIN_END_HEIGHT);
        float chain = chainLength();
        float step = chain < wanted ? Math.min(wanted - chain, CHAIN_GROW) : -Math.min(chain - wanted, CHAIN_SHRINK);
        entityData.set(CHAIN, chain + step);
        if (isChainDown()) {
            keepChainHandle(level);
            if (tickCount % 40 == 0) {
                hintNearbyPlayers(level);
            }
        }
        if (--restTicks <= 0) {
            mode = Mode.CRUISE;
            hasTarget = false;
            restCooldown = 6000 + random.nextInt(3000);
            dropChainHandle();
            level.playSound(null, getX(), getY(), getZ(), ModSounds.TITAN_CHAIN.get(), SoundSource.AMBIENT, 6f, 0.7f);
        }
    }

    // 鎖の根元に、右クリックを受け取る見えない当たり判定を置き続ける
    private void keepChainHandle(ServerLevel level) {
        Vec3 bottom = chainBottom();
        if (chainHandle == null || chainHandle.isRemoved()) {
            // 以前の当たり判定が残っていれば消す (右クリックが二重に届かないように)
            for (WhaleChainEntity stray : level.getEntitiesOfClass(WhaleChainEntity.class, getBoundingBox().inflate(120, 120, 120))) {
                stray.discard();
            }
            chainHandle = ModEntities.WHALE_CHAIN.get().create(level);
            if (chainHandle == null) {
                return;
            }
            chainHandle.moveTo(bottom.x, bottom.y, bottom.z);
            level.addFreshEntity(chainHandle);
        } else {
            chainHandle.moveTo(bottom.x, bottom.y, bottom.z);
        }
    }

    private void dropChainHandle() {
        if (chainHandle != null) {
            chainHandle.discard();
            chainHandle = null;
        }
    }

    private void hintNearbyPlayers(ServerLevel level) {
        Vec3 bottom = chainBottom();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(bottom) < 12 * 12) {
                player.displayClientMessage(ModText.hint("message.skyabyss.chain_hint"), true);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        dropChainHandle();
        super.remove(reason);
    }

    private void retractChain() {
        dropChainHandle();
        float chain = chainLength();
        if (chain > 0) {
            entityData.set(CHAIN, Math.max(0f, chain - CHAIN_SHRINK));
        }
    }

    // 目標へ向きを少しずつ変えながら進み、高さを合わせる
    private void steer(double tx, double tz, double ty, float speed) {
        double dx = tx - getX();
        double dz = tz - getZ();
        float desired = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), desired, TURN_RATE);
        setYRot(yaw);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double forward = Math.min(speed, distance * 0.02);
        double vx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * forward;
        double vz = Mth.cos(yaw * Mth.DEG_TO_RAD) * forward;
        double vy = Mth.clamp((ty - getY()) * 0.02, -0.12, 0.12);
        Vec3 motion = getDeltaMovement().lerp(new Vec3(vx, vy, vz), 0.05);
        setDeltaMovement(motion);
        move(net.minecraft.world.entity.MoverType.SELF, motion);
    }

    private void pickCruiseTarget(ServerLevel level) {
        ServerPlayer player = randomPlayer(level, 640);
        double angle = random.nextDouble() * Math.PI * 2;
        if (player != null) {
            // プレイヤーから少し離れた所を横切るように泳ぐ
            double r = 120 + random.nextDouble() * 140;
            targetX = player.getX() + Math.cos(angle) * r;
            targetZ = player.getZ() + Math.sin(angle) * r;
        } else {
            targetX = getX() + Math.cos(angle) * 300;
            targetZ = getZ() + Math.sin(angle) * 300;
        }
        hasTarget = true;
    }

    private void sampleGround(ServerLevel level) {
        int x = Mth.floor(getX());
        int z = Mth.floor(getZ());
        Vec3 top = chainTop();
        groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(top.x), Mth.floor(top.z));
        double max = groundY;
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                max = Math.max(max, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + i * 40, z + k * 40));
            }
        }
        surroundingMax = max;
    }

    private double horizontalDistanceTo(double x, double z) {
        return Math.hypot(x - getX(), z - getZ());
    }

    @Nullable
    private ServerPlayer nearestPlayer(ServerLevel level, double range) {
        ServerPlayer best = null;
        double bestDistance = range;
        for (ServerPlayer player : level.players()) {
            double d = horizontalDistanceTo(player.getX(), player.getZ());
            if (!player.isSpectator() && d < bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    @Nullable
    private ServerPlayer randomPlayer(ServerLevel level, double range) {
        List<ServerPlayer> near = level.players().stream()
                .filter(p -> !p.isSpectator() && horizontalDistanceTo(p.getX(), p.getZ()) < range)
                .toList();
        return near.isEmpty() ? null : near.get(random.nextInt(near.size()));
    }

    // テスト用: すぐにこのプレイヤーのそばへ降りてくる
    public void comeRestNear(ServerPlayer player) {
        targetX = player.getX() + 10;
        targetZ = player.getZ() + 10;
        hasTarget = true;
        mode = Mode.APPROACH;
    }

    // 巨神は傷つかない
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 768.0 * 768.0;
    }

    // 見た目は当たり判定よりずっと大きいので、画面外判定を広げる
    @Override
    public AABB getBoundingBoxForCulling() {
        // モデルは体の底が付け根の上 6.75 ブロック、背の上端が 46.5 ブロック (前後は -66〜81 ブロック)。上はそこまで届くようにする
        return getBoundingBox().inflate(100.0, 50.0, 100.0).expandTowards(0, -Math.max(0, chainLength() - CHAIN_UP), 0);
    }

    // 位置の補間。サーバーは2tickごとに位置を送るので、それに合わせた steps で滑らかにつなぐ
    // (以前は10tickかけて追いかけていたため、描画がサーバーの位置より遅れ、鎖の先が鎖の根元とずれていた)
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = Math.max(1, steps);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        mode = Mode.values()[Mth.clamp(tag.getInt("Mode"), 0, Mode.values().length - 1)];
        targetX = tag.getDouble("TargetX");
        targetZ = tag.getDouble("TargetZ");
        hasTarget = tag.getBoolean("HasTarget");
        restCooldown = tag.getInt("RestCooldown");
        restTicks = tag.getInt("RestTicks");
        entityData.set(CHAIN, tag.getFloat("Chain"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Mode", mode.ordinal());
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetZ", targetZ);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putInt("RestCooldown", restCooldown);
        tag.putInt("RestTicks", restTicks);
        tag.putFloat("Chain", chainLength());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "swim", 0, state -> state.setAndContinue(SWIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
