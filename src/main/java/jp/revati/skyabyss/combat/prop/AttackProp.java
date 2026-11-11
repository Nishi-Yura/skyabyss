package jp.revati.skyabyss.combat.prop;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 技の形 (2026-11-11 の声「遠距離攻撃とかはきちんと円の予告とエフェクトだけじゃなくて、そこに攻撃専用のモデルが出てきたり」)。
// 予告の円の所に、技そのものの姿 (床から突き出る棘・落ちてくる岩・飛んでくる弾など) を出す見た目だけの物。当たり判定は Telegraph が持つ。
// 動き方 (mode):
//   RISE  床から一気に突き出て (RISE_IN tick、少し行き過ぎる)、留まり、最後に沈んで消える
//   FALL  delay tick は見えず、そのあと空 (height ブロック上) から落ちて、ちょうど予告の打つ瞬間に着く。着いたら留まって消える
//   TOSS  from から to へ、arc の高さの弧を描いて flight tick で飛ぶ。着いたら留まって消える (飛ぶ間は進む向きを向く)
// 位置は両側で同じ式で決める (送るのは最初の値だけ)。保存しない。見えるのは持ち主と同じグループの人だけ (GroupOwned)
public class AttackProp extends Entity implements GeoEntity, jp.revati.skyabyss.titan.group.GroupOwned {
    public static final int RISE = 0;
    public static final int FALL = 1;
    public static final int TOSS = 2;
    public static final int RISE_IN = 4;
    public static final int SINK = 6;

    static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> TRAVEL = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> LINGER = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<org.joml.Vector3f> FROM = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.VECTOR3);
    static final EntityDataAccessor<org.joml.Vector3f> TO = SynchedEntityData.defineId(AttackProp.class, EntityDataSerializers.VECTOR3);

    private final String kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID groupId;

    public AttackProp(EntityType<? extends AttackProp> type, Level level, String kind) {
        super(type, level);
        this.kind = kind;
        this.noPhysics = true;
        setNoGravity(true);
    }

    public String kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(MODE, RISE);
        b.define(DELAY, 0);
        b.define(TRAVEL, 0);
        b.define(LINGER, 20);
        b.define(SIZE, 1f);
        b.define(HEIGHT, 0f);
        b.define(FROM, new org.joml.Vector3f());
        b.define(TO, new org.joml.Vector3f());
    }

    void setup(int mode, Vec3 from, Vec3 to, int delay, int travel, int linger, float size, float height, float yaw) {
        entityData.set(MODE, mode);
        entityData.set(FROM, from.toVector3f());
        entityData.set(TO, to.toVector3f());
        entityData.set(DELAY, delay);
        entityData.set(TRAVEL, Math.max(1, travel));
        entityData.set(LINGER, linger);
        entityData.set(SIZE, size);
        entityData.set(HEIGHT, height);
        setYRot(yaw);
        yRotO = yaw;
        Vec3 p = at(0);
        moveTo(p.x, p.y, p.z, yaw, 0f);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public int delay() {
        return entityData.get(DELAY);
    }

    public int life() {
        int m = mode();
        return entityData.get(DELAY) + (m == RISE ? 0 : entityData.get(TRAVEL)) + entityData.get(LINGER) + (m == RISE ? SINK : 0);
    }

    // 生まれてから age tick の位置
    Vec3 at(float age) {
        Vec3 from = new Vec3(entityData.get(FROM));
        Vec3 to = new Vec3(entityData.get(TO));
        int m = mode();
        float t = age - entityData.get(DELAY);
        int travel = entityData.get(TRAVEL);
        float h = entityData.get(HEIGHT);
        if (m == FALL) {
            float u = Mth.clamp(t / travel, 0f, 1f);
            return to.add(0, h * (1 - u * u), 0);
        }
        if (m == TOSS) {
            float u = Mth.clamp(t / travel, 0f, 1f);
            Vec3 p = from.lerp(to, u);
            return p.add(0, h * 4 * u * (1 - u), 0);
        }
        return to;
    }

    // RISE の床からの出方 (0 = 床の下に隠れる、1 = 出きり)。描く側が掛ける
    public float rise(float partial) {
        if (mode() != RISE) {
            return 1f;
        }
        float t = tickCount + partial - entityData.get(DELAY);
        if (t < 0) {
            return 0f;
        }
        int linger = entityData.get(LINGER);
        if (t < RISE_IN) {
            float u = t / RISE_IN;
            // 一気に出て少し行き過ぎる
            return 1.12f * (1 - (1 - u) * (1 - u) * (1 - u));
        }
        if (t < RISE_IN + 2) {
            return 1.12f - 0.12f * (t - RISE_IN) / 2f;
        }
        if (t < linger) {
            return 1f;
        }
        return Mth.clamp(1f - (t - linger) / SINK, 0f, 1f);
    }

    // まだ姿を出さない (FALL の待ち)
    public boolean hidden(float partial) {
        return tickCount + partial < entityData.get(DELAY);
    }

    // TOSS の飛ぶ向き (度)。描く側が頭を進む向きへ傾ける
    public float pitch(float partial) {
        if (mode() == FALL) {
            return 90f;
        }
        if (mode() != TOSS) {
            return 0f;
        }
        Vec3 a = at(tickCount + partial - 0.5f);
        Vec3 b = at(tickCount + partial + 0.5f);
        Vec3 d = b.subtract(a);
        double hz = Math.sqrt(d.x * d.x + d.z * d.z);
        return hz < 1e-4 && Math.abs(d.y) < 1e-4 ? 0f : (float) Math.toDegrees(Math.atan2(-d.y, hz));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 p = at(tickCount);
        setPos(p.x, p.y, p.z);
        if (!level().isClientSide && tickCount > life() + 2) {
            discard();
        }
    }

    @Override
    @Nullable
    public UUID groupId() {
        return groupId;
    }

    @Override
    public void setGroupId(@Nullable UUID group) {
        this.groupId = group;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return super.broadcastToPlayer(player) && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, player);
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = RawAnimation.begin().thenLoop("animation.prop_" + kind + ".idle");
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
