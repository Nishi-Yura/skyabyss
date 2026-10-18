package jp.revati.skyabyss.client.cinematic;

import jp.revati.skyabyss.cinematic.CameraMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

// 場面のカメラ (クライアントだけの見えない物。ワールドには足さない)。Minecraft のカメラの持ち主にして、位置と向きを毎 tick 動かす。
// 目の高さは本人と同じにして、持ち主を入れ替えたときにカメラが上下にすべらないようにする
final class CameraMarker extends Entity {
    private static final EntityDimensions DIMENSIONS = EntityDimensions.fixed(0.2f, 0.2f).withEyeHeight((float) CameraMath.EYE);

    CameraMarker(Level level) {
        super(EntityType.MARKER, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return DIMENSIONS;
    }

    // 目の位置を eye に置く。前の位置を覚えさせて、描画の間をなめらかにつなぐ
    void moveEye(double x, double y, double z, float yaw, float pitch) {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.xo;
        this.yOld = this.yo;
        this.zOld = this.zo;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setPos(x, y - CameraMath.EYE, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    // 位置と向きを前の値ごと一度に置く (始まり)
    void place(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y - CameraMath.EYE, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.xo;
        this.yOld = this.yo;
        this.zOld = this.zo;
        this.yRotO = yaw;
        this.xRotO = pitch;
    }
}
