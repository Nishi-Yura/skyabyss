package jp.revati.skyabyss.guide.nav;

import net.minecraft.util.Mth;

// 道しるべの矢印の計算 (画面に依らない。自己テストが見る)
public final class NavMath {
    // 高さの差がこれを超えたら、矢印の脇に上・下の印を出す (ブロック)
    public static final double VERTICAL_HINT = 12.0;

    private NavMath() {
    }

    // 目印の向き (Minecraft の yaw: 0 = 南 (+Z)、90 = 西 (-X))
    public static double yawTo(double fromX, double fromZ, double toX, double toZ) {
        return Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    // 画面の矢印の角度 (度。時計回りが正。0 = 画面の真上 = いま向いている方向、90 = 右、180 = 真後ろ)。-180 < 値 <= 180
    public static float arrowDegrees(double fromX, double fromZ, double toX, double toZ, float cameraYaw) {
        float rel = Mth.wrapDegrees((float) (yawTo(fromX, fromZ, toX, toZ) - cameraYaw));
        return rel <= -180f ? 180f : rel;
    }

    // 上下の印: +1 = 目印がだいぶ上、-1 = だいぶ下、0 = 出さない
    public static int vertical(double dy) {
        return dy > VERTICAL_HINT ? 1 : dy < -VERTICAL_HINT ? -1 : 0;
    }
}
