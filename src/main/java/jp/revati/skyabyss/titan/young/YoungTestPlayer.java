package jp.revati.skyabyss.titan.young;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

// 子の自己テストの「人」。NeoForge の FakePlayer は startRiding が常に false なので、乗り物に乗れない。
// 乗り物の欄を直接書いて、本物の人と同じに乗れるようにしたもの (本番では使わない)
final class YoungTestPlayer extends FakePlayer {
    private static final java.lang.reflect.Field VEHICLE;

    static {
        try {
            VEHICLE = Entity.class.getDeclaredField("vehicle");
            VEHICLE.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    YoungTestPlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (!(vehicle instanceof YoungEntity young) || vehicle == getVehicle()) {
            return false;
        }
        try {
            if (isPassenger()) {
                stopRiding();
            }
            setPose(net.minecraft.world.entity.Pose.STANDING);
            VEHICLE.set(this, vehicle);
            young.seatForTest(this);
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        if (level == level()) {
            moveTo(x, y, z, yaw, pitch);
        } else {
            super.teleportTo(level, x, y, z, yaw, pitch);
        }
    }
}
