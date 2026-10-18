package jp.revati.skyabyss.cinematic;

import net.minecraft.world.phys.Vec3;

// カメラの道筋の計算 (サーバー・クライアント共通。画面を使わないので自己テストでも確かめる)。
// 場面の座標は (右, 上, 前): 原点は始まりの足もと、前は yaw の向き (Minecraft の向き。0 = 南 = +z、90 = 西)
public final class CameraMath {
    // 目の高さ。最後のカメラをここに置けば、操作へ切れ目なく戻る
    public static final double EYE = 1.62;

    // origin: 場面の原点 (足もと)、yaw: 前の向き (度)、target: 狙う物の今の位置 (なければ原点)
    public record Ctx(Vec3 origin, float yaw, Vec3 target) {
    }

    public record Pose(Vec3 pos, Vec3 look, float fov) {
    }

    private CameraMath() {
    }

    public static Vec3 toWorld(Ctx c, Vec3 f) {
        double rad = Math.toRadians(c.yaw());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        // 前 = (-sin, 0, cos)、右 = (-cos, 0, -sin)
        return new Vec3(c.origin().x - cos * f.x - sin * f.z, c.origin().y + f.y, c.origin().z - sin * f.x + cos * f.z);
    }

    public static Vec3 resolve(Ctx c, Look look) {
        return switch (look.kind()) {
            case FRAME -> toWorld(c, look.v());
            case TARGET -> c.target().add(look.v());
        };
    }

    // 場面の t 秒のカメラ。範囲外は両端
    public static Pose sample(Scene scene, float t, Ctx c) {
        float start = 0f;
        Shot last = scene.shots().get(scene.shots().size() - 1);
        for (Shot s : scene.shots()) {
            if (t < start + s.seconds() || s == last) {
                return shot(s, Math.min(Math.max((t - start) / s.seconds(), 0f), 1f), t, c);
            }
            start += s.seconds();
        }
        throw new IllegalStateException("scene without shots: " + scene.id());
    }

    private static Pose shot(Shot s, float x, float t, Ctx c) {
        double k = s.ease().apply(x);
        Vec3 pos = toWorld(c, s.posFrom().lerp(s.posTo(), k));
        Vec3 look = resolve(c, s.lookFrom()).lerp(resolve(c, s.lookTo()), k);
        if (s.sway() > 0f) {
            double a = Math.max(2.0, look.distanceTo(pos)) * s.sway() * 0.05;
            look = look.add(Math.sin(t * 1.1) * a, Math.sin(t * 0.8 + 1.0) * a * 0.6, Math.sin(t * 0.9 + 2.0) * a);
        }
        float fov = s.fovFrom() <= 0f || s.fovTo() <= 0f ? 0f : s.fovFrom() + (s.fovTo() - s.fovFrom()) * (float) k;
        return new Pose(pos, look, fov);
    }

    // 向きの度 (Minecraft の yaw / pitch。pitch は上が負)
    public static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    public static float pitchOf(Vec3 dir) {
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return (float) -Math.toDegrees(Math.atan2(dir.y, horizontal));
    }
}
