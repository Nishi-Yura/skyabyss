package jp.revati.skyabyss.cinematic;

import net.minecraft.world.phys.Vec3;

// カメラの見る点。FRAME = 場面の座標 (右, 上, 前。原点は始まりの足もと、前は向き)、TARGET = 狙う物 (岩鯨・心臓) からのずれ (世界の x, y, z)
public record Look(Kind kind, Vec3 v) {
    public enum Kind {
        FRAME,
        TARGET
    }

    public static Look frame(double right, double up, double forward) {
        return new Look(Kind.FRAME, new Vec3(right, up, forward));
    }

    public static Look target(double dx, double dy, double dz) {
        return new Look(Kind.TARGET, new Vec3(dx, dy, dz));
    }
}
