package jp.revati.skyabyss.titan.ember;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 余燼の巨神が地上で使う、陸の調べ方と夜の刻 (13 の 2.1〜2.3節。潮殻の TideOcean の陸版)。
// どれも読み込まれているチャンクだけを調べる (同期の読み込みや地形の生成を起こさない。岩鯨・潮殻と同じ)。
// 例外は landingAt (背中から戻す直前に一度だけ、行き先のチャンクを読み込む)
public final class EmberGround {
    // ---- 夜の刻 (1 日 24000 tick の中の時刻)
    // 眠る場所を選び始める (眠りの始まりの 1 分前)。温石で頼めるのもここから
    public static final int GATHER = 11300;
    // 地上へ浮かび始める (30 秒で浮かぶ)
    public static final int RISE = 12500;
    // 地の中へ沈み始める (10 秒で沈む)
    public static final int WAKE = 23000;

    // ---- 灰の降る範囲 (2.3節。近いほど多い三段)
    public static final double ASH_NEAR = 64;
    public static final double ASH_MID = 160;
    public static final double ASH_FAR = 256;

    // 眠る場所の口の前の平らさ (16 ブロック、高さの差 3 以内)
    static final int FLAT_LENGTH = 16;
    static final int FLAT_STEP = 3;

    private EmberGround() {
    }

    // ---------------------------------------------------------------- 時刻

    public static long timeOfDay(long dayTime) {
        return Math.floorMod(dayTime, 24000L);
    }

    // 夜の番号 (同じ夜で二度寄らない・温石の頼みは一夜に一人)。選び始め (11300) で次の番号になる
    public static long nightId(long dayTime) {
        return Math.floorDiv(dayTime - GATHER, 24000L);
    }

    // 眠る場所を選び、向かってよい時刻か (11300〜23000)
    public static boolean isGathering(long dayTime) {
        long t = timeOfDay(dayTime);
        return t >= GATHER && t < WAKE;
    }

    // 眠っていてよい時刻か (12500〜23000)
    public static boolean isSleepTime(long dayTime) {
        long t = timeOfDay(dayTime);
        return t >= RISE && t < WAKE;
    }

    // 温石で「そばで眠って」と頼める時刻か (日没の 1 分前から眠りの始まりまで)
    public static boolean isRequestTime(long dayTime) {
        long t = timeOfDay(dayTime);
        return t >= GATHER && t < RISE;
    }

    // 次の眠りの始まりまで (眠っていてよい時刻なら 0)
    public static long ticksUntilSleep(long dayTime) {
        long t = timeOfDay(dayTime);
        if (t >= RISE && t < WAKE) {
            return 0;
        }
        return t < RISE ? RISE - t : 24000L - t + RISE;
    }

    // 灰の降り方の段 (0 降らない、1 遠い、2 中、3 近い)
    public static int ashTier(double distance) {
        if (distance < ASH_NEAR) {
            return 3;
        }
        if (distance < ASH_MID) {
            return 2;
        }
        return distance < ASH_FAR ? 1 : 0;
    }

    // ---------------------------------------------------------------- 陸の調べ方

    public static boolean loaded(ServerLevel level, double x, double z) {
        return level.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    // 地表の高さ (いちばん上の、空気でないブロックの一つ上)。読み込まれていなければ NaN
    public static double surfaceY(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return Double.NaN;
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
    }

    // 地表に水 (や溶岩) があるか。読み込まれていなければ false
    public static boolean fluidAtSurface(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
        return !level.getFluidState(new BlockPos(Mth.floor(x), top - 1, Mth.floor(z))).isEmpty();
    }

    // 海面の高さ。Level.getSeaLevel() は 1.21.1 ではいつも 63 なので、地形の生成の海面を使う (平らな世界は -63)
    public static int seaLevel(ServerLevel level) {
        return level.getChunkSource().getGenerator().getSeaLevel();
    }

    // 巨神が出てよい陸か: 読み込まれていて、地表が海面より 2 以上高く、地表が水でなく、海・川のバイオームでない (2.1節)
    public static boolean isLand(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return false;
        }
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        if (top < seaLevel(level) + 2) {
            return false;
        }
        if (!level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty()) {
            return false;
        }
        var biome = level.getBiome(new BlockPos(bx, top, bz));
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
    }

    // (cx, cz) から rMin〜rMax 離れた陸の点を、tries 回ためして探す。なければ null (y は使わない)
    @Nullable
    public static Vec3 findLandPoint(ServerLevel level, RandomSource random, double cx, double cz, double rMin, double rMax, int tries) {
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = rMin + random.nextDouble() * (rMax - rMin);
            double x = cx + Math.cos(angle) * r;
            double z = cz + Math.sin(angle) * r;
            if (isLand(level, x, z)) {
                return new Vec3(x, 0, z);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 眠る場所

    // 眠る場所: 巨神の中心 (x, z)、向き yaw、口の前の地面の高さ groundY (舌の上面をこの高さに合わせる)
    public record Spot(double x, double z, float yaw, double groundY) {
    }

    // 向き yaw の前の単位ベクトル (x, z)。巨神と同じ回し方 (yaw 0 は南、+Z)
    public static double forwardX(float yaw) {
        return -Mth.sin(yaw * Mth.DEG_TO_RAD);
    }

    public static double forwardZ(float yaw) {
        return Mth.cos(yaw * Mth.DEG_TO_RAD);
    }

    // (dx, dz) の向きの yaw
    public static float yawOf(double dx, double dz) {
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
    }

    // 眠る場所 (2.3節): (px, pz) の人から 24〜64 ブロックの陸に鼻先を置き、口をその人へ向ける。
    // 口の前 16 ブロックが平ら (高さの差 3 以内) で、鼻先・火山の背・腰・尾の下が陸 (水の上に伏せない) なこと。見つからなければ null
    @Nullable
    public static Spot findSleepSpot(ServerLevel level, double px, double pz) {
        int[] rings = {36, 44, 28, 52, 24, 60, 64};
        for (int r : rings) {
            for (int a = 0; a < 16; a++) {
                double angle = Math.PI * 2 * (a + (r % 7) / 7.0) / 16;
                double sx = px + Math.cos(angle) * r;
                double sz = pz + Math.sin(angle) * r;
                Spot spot = sleepSpotAt(level, sx, sz, yawOf(px - sx, pz - sz));
                if (spot != null) {
                    return spot;
                }
            }
        }
        return null;
    }

    // 呼び鈴で呼ぶときの眠る場所: まず人の近く (findSleepSpot)。そこに平らな陸がなければ、輪を 160 ブロックまで広げて探す
    // (口をその人へ向ける)。読み込まれている範囲だけを見る。見つからなければ null
    @Nullable
    public static Spot findCallSpot(ServerLevel level, double px, double pz) {
        Spot near = findSleepSpot(level, px, pz);
        if (near != null) {
            return near;
        }
        for (int r = 72; r <= 160; r += 16) {
            for (int a = 0; a < 24; a++) {
                double angle = Math.PI * 2 * (a + (r % 7) / 7.0) / 24;
                double sx = px + Math.cos(angle) * r;
                double sz = pz + Math.sin(angle) * r;
                Spot spot = sleepSpotAt(level, sx, sz, yawOf(px - sx, pz - sz));
                if (spot != null) {
                    return spot;
                }
            }
        }
        return null;
    }

    // 鼻先を (sx, sz) に置き、向き yaw で伏せられるか。伏せられれば眠る場所
    @Nullable
    public static Spot sleepSpotAt(ServerLevel level, double sx, double sz, float yaw) {
        if (!isLand(level, sx, sz)) {
            return null;
        }
        double fx = forwardX(yaw);
        double fz = forwardZ(yaw);
        double ground = surfaceY(level, sx, sz);
        // 口の前 16 ブロック (鼻先から前へ) が平ら。横にも 3 ブロックずつ見る
        for (int d = 0; d <= FLAT_LENGTH; d += 2) {
            for (int s = -3; s <= 3; s += 3) {
                double x = sx + fx * d + fz * s;
                double z = sz + fz * d - fx * s;
                double y = surfaceY(level, x, z);
                if (Double.isNaN(y) || Math.abs(y - ground) > FLAT_STEP || fluidAtSurface(level, x, z)) {
                    return null;
                }
            }
        }
        // 体の下 (鼻先から後ろへ) も陸 (中心・火山・腰・尾)
        for (double back : new double[] {EmberTitanEntity.SNOUT_FORWARD, EmberTitanEntity.SNOUT_FORWARD - EmberTitanEntity.RIDGE_FORWARD,
                EmberTitanEntity.SNOUT_FORWARD + 60, EmberTitanEntity.SNOUT_FORWARD + 110}) {
            if (!isLand(level, sx - fx * back, sz - fz * back)) {
                return null;
            }
        }
        double cx = sx - fx * EmberTitanEntity.SNOUT_FORWARD;
        double cz = sz - fz * EmberTitanEntity.SNOUT_FORWARD;
        return new Spot(cx, cz, yaw, ground);
    }

    // ---------------------------------------------------------------- 背中から戻すとき

    // (x, z) のまわりで立てる地面 (読み込んでよい)。radius ブロック以内を中心から外へ探す。なければ null
    @Nullable
    public static Vec3 landingAt(ServerLevel level, double x, double z, int radius) {
        for (int s = 0; s <= radius; s += 2) {
            for (int dir = 0; dir < (s == 0 ? 1 : 16); dir++) {
                int bx = Mth.floor(x + Math.cos(Math.PI / 8 * dir) * s);
                int bz = Mth.floor(z + Math.sin(Math.PI / 8 * dir) * s);
                level.getChunk(bx >> 4, bz >> 4);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                BlockPos below = new BlockPos(bx, top - 1, bz);
                if (level.getFluidState(below).isEmpty() && level.getBlockState(below).isSolidRender(level, below)
                        && level.getBlockState(below.above()).isAir() && level.getBlockState(below.above(2)).isAir()) {
                    return new Vec3(bx + 0.5, top, bz + 0.5);
                }
            }
        }
        return null;
    }
}
