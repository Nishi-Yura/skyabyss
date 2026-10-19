package jp.revati.skyabyss.titan.young;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 子の動き (docs/design/28 の 4 章)。巨神ごとの得意で、地形の見方が違う。どれも滑らかに曲がり、地形をなぞって上下する (体はめり込ませない)。
//   SKY    (岩鯨の仔) 前 56 ブロックの地形の最高点 + 20 の高さを保ち、先回りして持ち上がる
//   RIDGE  (雷翼の雛) 同じ見方で、地形の最高点 + 9 まで低く飛ぶ (稜線をなぞる)
//   GROUND (余燼の仔) 足もとの地面の高さに乗る。高い段・深い水は避けて、通れる向きへ曲がる。溶岩の上は渡る
//   SEA    (潮殻の稚貝) 水面に浮かんで滑る。陸は始めと終わりの 100 ブロックだけ転がる。陸を避けて、水のある向きへ曲がる
// 読み込まれていない所は見えない: 速さを落とす (生成が追いつくのを待つ)。見えない所へ高さを下げることはしない
final class YoungMotion {
    enum Result { MOVING, ARRIVED, BLOCKED }

    // 曲がる速さ (度/tick)
    private static final float TURN_AIR = 2.2f;
    private static final float TURN_LAND = 4.0f;
    // 通れる段差
    private static final double STEP = 2.6;
    // 前を見る距離 (地上の子)
    private static final int[] LOOK = {5, 10, 15, 20};
    // 陸を転がってよい距離 (潮殻の稚貝)
    private static final double LAND_BUDGET = 100;

    private final YoungEntity e;
    private final YoungKind kind;
    double speed;
    private float bias = 1f;
    int blocked;
    private double landLeft = LAND_BUDGET;
    private int unknownTicks;

    YoungMotion(YoungEntity e) {
        this.e = e;
        this.kind = e.kind;
    }

    // ---------------------------------------------------------------- 地形

    // 地形の高さ (読み込まれていなければ NaN)。type: 木の葉・水を含むか
    static double height(ServerLevel level, Heightmap.Types type, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        return level.getHeight(type, bx, bz);
    }

    static boolean isWater(ServerLevel level, double x, double z) {
        double h = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return !Double.isNaN(h) && level.getFluidState(net.minecraft.core.BlockPos.containing(x, h - 1, z)).is(FluidTags.WATER);
    }

    private static boolean isLava(ServerLevel level, double x, double z) {
        double h = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return !Double.isNaN(h) && level.getFluidState(net.minecraft.core.BlockPos.containing(x, h - 1, z)).is(FluidTags.LAVA);
    }

    // 水の深さ (水の上でなければ 0)
    private static double depth(ServerLevel level, double x, double z) {
        if (!isWater(level, x, z)) {
            return 0;
        }
        double top = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        double floor = height(level, Heightmap.Types.OCEAN_FLOOR, x, z);
        return Double.isNaN(floor) ? 0 : top - floor;
    }

    // ---------------------------------------------------------------- 一歩

    // target へ一歩進める。arriveRadius の内に入れば ARRIVED。landY が NaN でなければ、その高さへ降りながら進む
    Result step(ServerLevel level, double tx, double tz, double padDistance, double arriveRadius, double landY) {
        Vec3 p = e.position();
        double dx = tx - p.x;
        double dz = tz - p.z;
        double dist = Math.hypot(dx, dz);
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        boolean landing = !Double.isNaN(landY);
        if (dist <= arriveRadius && (!landing || Math.abs(p.y - landY) < 0.35)) {
            return Result.ARRIVED;
        }
        float heading = want;
        boolean groundGait = kind.gait == YoungKind.Gait.GROUND || kind.gait == YoungKind.Gait.SEA;
        if (groundGait && dist > arriveRadius) {
            heading = steer(level, p, want, padDistance);
            if (Float.isNaN(heading)) {
                blocked++;
                speed = Math.max(0, speed - 0.06);
                return blocked > 50 ? Result.BLOCKED : Result.MOVING;
            }
            blocked = 0;
        }
        float yaw = Mth.approachDegrees(e.getYRot(), heading, groundGait ? TURN_LAND : TURN_AIR);
        // 速さ: 近づくほど落とす。見えない先がある間は半分
        double wanted = Math.min(kind.speed, Math.max(0.1, dist * 0.04));
        boolean unknown = false;
        double yr = yaw * Mth.DEG_TO_RAD;
        double fx = -Math.sin(yr);
        double fz = Math.cos(yr);
        double ahead = kind.flies() ? 40 : 14;
        if (Double.isNaN(height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.x + fx * ahead, p.z + fz * ahead))) {
            unknown = true;
            wanted *= 0.45;
            unknownTicks++;
        } else {
            unknownTicks = 0;
        }
        if (dist <= arriveRadius) {
            wanted = 0;
        }
        speed += Mth.clamp(wanted - speed, -0.05, 0.02);
        double nx = p.x + fx * speed;
        double nz = p.z + fz * speed;
        double ny = p.y;
        if (landing) {
            ny = p.y + Mth.clamp((landY - p.y) * 0.1, -0.4, 0.4);
            if (Math.abs(landY - ny) < 0.05) {
                ny = landY;
            }
        } else if (kind.flies()) {
            double target = flightTarget(level, nx, nz, fx, fz, unknown);
            ny = p.y + Mth.clamp(target - p.y, -0.22, 0.5);
        } else {
            ny = surfaceY(level, nx, nz, p.y);
            if (!Double.isNaN(ny)) {
                ny = p.y + Mth.clamp(ny - p.y, -0.5, 0.35);
            } else {
                ny = p.y;
            }
        }
        if (kind.gait == YoungKind.Gait.SEA && !isWater(level, nx, nz)) {
            landLeft -= speed;
        }
        e.setYRot(yaw);
        e.setPos(nx, ny, nz);
        e.setDeltaMovement(nx - p.x, ny - p.y, nz - p.z);
        return Result.MOVING;
    }

    // 地形の上の高さ (飛ぶ子)。前 56 ブロックの最高点 + 余裕。見えない先は余裕を足す
    private double flightTarget(ServerLevel level, double x, double z, double fx, double fz, boolean unknown) {
        double max = level.getSeaLevel();
        double lx = fz;
        double lz = -fx;
        for (int d = 0; d <= 56; d += 8) {
            for (int lat : d <= 24 ? new int[] {-6, 0, 6} : new int[] {0}) {
                double h = height(level, Heightmap.Types.WORLD_SURFACE, x + fx * d + lx * lat, z + fz * d + lz * lat);
                if (!Double.isNaN(h)) {
                    max = Math.max(max, h);
                } else {
                    unknown = true;
                }
            }
        }
        double target = max + kind.clearance + (unknown ? 6 : 0);
        if (kind.gait == YoungKind.Gait.SKY) {
            target = Math.max(target, level.getSeaLevel() + 26);
        }
        return Math.min(target, level.getMaxBuildHeight() - 12);
    }

    // 地上の子が乗る高さ (NaN = まだ見えない)
    private double surfaceY(ServerLevel level, double x, double z, double fallback) {
        double h = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Double.isNaN(h)) {
            return Double.NaN;
        }
        if (kind.gait == YoungKind.Gait.SEA && isWater(level, x, z)) {
            return h - 0.35;
        }
        if (kind.gait == YoungKind.Gait.GROUND) {
            if (isLava(level, x, z)) {
                return h - 0.5;
            }
            if (isWater(level, x, z)) {
                // 浅い水は底を歩く
                double floor = height(level, Heightmap.Types.OCEAN_FLOOR, x, z);
                return Double.isNaN(floor) ? h : floor;
            }
        }
        return h;
    }

    // ---------------------------------------------------------------- 曲がる向き (地上の子)

    // 行きたい向き want のまわりで、前が通れる向きを探す (どれも通れなければ NaN)。前回選んだ側を先に見る
    private float steer(ServerLevel level, Vec3 p, float want, double padDistance) {
        boolean landOk = kind.gait != YoungKind.Gait.SEA || landLeft > 0 || padDistance < LAND_BUDGET + 10;
        float current = e.getYRot();
        for (int off = 0; off <= 160; off += 20) {
            for (int s = 0; s < (off == 0 ? 1 : 2); s++) {
                float side = s == 0 ? bias : -bias;
                float heading = want + side * off;
                if (passable(level, p, heading, landOk)) {
                    if (off != 0) {
                        bias = side;
                    }
                    return heading;
                }
            }
        }
        // 向きを変えず、その場で待つ
        return Float.isNaN(current) ? want : Float.NaN;
    }

    private boolean passable(ServerLevel level, Vec3 p, float heading, boolean landOk) {
        double yr = heading * Mth.DEG_TO_RAD;
        double fx = -Math.sin(yr);
        double fz = Math.cos(yr);
        double prev = p.y;
        for (int d : LOOK) {
            double x = p.x + fx * d;
            double z = p.z + fz * d;
            double h = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Double.isNaN(h)) {
                // 見えない所は通れる扱い (速さを落として待つ)
                continue;
            }
            boolean water = isWater(level, x, z);
            if (kind.gait == YoungKind.Gait.GROUND) {
                if (water && depth(level, x, z) > 2) {
                    return false;
                }
            } else if (kind.gait == YoungKind.Gait.SEA && !water) {
                if (!landOk) {
                    return false;
                }
            }
            if (!water || kind.gait == YoungKind.Gait.GROUND) {
                if (Math.abs(h - prev) > STEP) {
                    return false;
                }
                prev = h;
            }
        }
        return true;
    }
}
