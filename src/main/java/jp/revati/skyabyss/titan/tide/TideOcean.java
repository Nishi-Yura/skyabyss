package jp.revati.skyabyss.titan.tide;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 潮殻の巨神が地上で使う、海の調べ方と干潮の刻 (08 の 2.1〜2.3節)。
// どれも読み込まれているチャンクだけを調べる (同期の読み込みや地形の生成を起こさない。岩鯨と同じ)
public final class TideOcean {
    // 干潮の刻: 朝 (23000〜2600) と夕 (11000〜14600)。それぞれ 3600 tick。
    // 刻の 1200 tick 前から「寄る相手と休む場所」を選び始める。
    // 時刻を 9800 ずらすと、選び始め (刻の 1200 前) が 0 になり、12000 ごとに繰り返す
    static final int TIDE_SHIFT = 9800;
    static final int TIDE_PERIOD = 12000;
    static final int TIDE_LEAD = 1200;
    static final int TIDE_LENGTH = 3600;

    private TideOcean() {
    }

    // 刻の中での位置 (0 = 選び始め、TIDE_LEAD = 刻の始まり、TIDE_LEAD + TIDE_LENGTH = 刻の終わり)
    static long phase(long dayTime) {
        return Math.floorMod(dayTime - TIDE_SHIFT, TIDE_PERIOD);
    }

    // 刻の番号 (同じ刻で二度選ばないように覚える)
    static long windowId(long dayTime) {
        return Math.floorDiv(dayTime - TIDE_SHIFT, TIDE_PERIOD);
    }

    // 干潮の刻の中か
    public static boolean isLowTide(long dayTime) {
        long p = phase(dayTime);
        return p >= TIDE_LEAD && p < TIDE_LEAD + TIDE_LENGTH;
    }

    // 選び始めから刻の終わりまでか (この間に寄る場所を決め、向かう)
    static boolean isGathering(long dayTime) {
        return phase(dayTime) < TIDE_LEAD + TIDE_LENGTH;
    }

    // 次の干潮の刻の始まりまで (刻の中なら 0)
    public static long ticksUntilLowTide(long dayTime) {
        long p = phase(dayTime);
        if (isLowTide(dayTime)) {
            return 0;
        }
        return p < TIDE_LEAD ? TIDE_LEAD - p : TIDE_PERIOD - p + TIDE_LEAD;
    }

    // 干潮の刻の終わりまで (刻の外なら 0)
    static long ticksUntilHighTide(long dayTime) {
        return isLowTide(dayTime) ? TIDE_LEAD + TIDE_LENGTH - phase(dayTime) : 0;
    }

    // ---- 海の調べ方

    static boolean loaded(ServerLevel level, double x, double z) {
        return level.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    // 海面に水があるか (読み込まれていなければ false)
    static boolean waterAtSurface(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return false;
        }
        BlockPos p = BlockPos.containing(x, level.getSeaLevel() - 1, z);
        return level.getFluidState(p).is(FluidTags.WATER);
    }

    // 海面から海底までの深さ (ブロック)。読み込まれていなければ -1
    static int depth(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return -1;
        }
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, Mth.floor(x), Mth.floor(z));
        return level.getSeaLevel() - floor;
    }

    // 海底の高さ (読み込まれていなければ NaN)
    static double floorY(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return Double.NaN;
        }
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, Mth.floor(x), Mth.floor(z));
    }

    // 歩ける海か: 海面に水があり、深さが minDepth 以上
    static boolean walkable(ServerLevel level, double x, double z, int minDepth) {
        return waterAtSurface(level, x, z) && depth(level, x, z) >= minDepth;
    }

    // 海のバイオームか (海・深海・暖かい海など。川は含まない)
    static boolean oceanBiome(ServerLevel level, double x, double z) {
        return level.getBiome(BlockPos.containing(x, level.getSeaLevel() - 1, z)).is(BiomeTags.IS_OCEAN);
    }

    // 陸か (海面より上に、水でないブロックがある)
    static boolean land(ServerLevel level, double x, double z) {
        if (!loaded(level, x, z)) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));
        if (top < level.getSeaLevel()) {
            return false;
        }
        return !level.getFluidState(new BlockPos(Mth.floor(x), top - 1, Mth.floor(z))).is(FluidTags.WATER);
    }

    // (cx, cz) から rMin〜rMax 離れた、海のバイオームで深さ minDepth 以上の点をいくつか試して探す。なければ null
    @Nullable
    static Vec3 findOceanPoint(ServerLevel level, RandomSource random, double cx, double cz, double rMin, double rMax, int minDepth, int tries) {
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = rMin + random.nextDouble() * (rMax - rMin);
            double x = cx + Math.cos(angle) * r;
            double z = cz + Math.sin(angle) * r;
            if (walkable(level, x, z, minDepth) && oceanBiome(level, x, z)) {
                return new Vec3(x, level.getSeaLevel(), z);
            }
        }
        return null;
    }

    // 休む場所: (px, pz) の人から 64 ブロック以内の海で、深さ 6〜14、岸から 40 ブロック以内 (08 の 2.3節)。
    // 巨神の左 (潮門の側) をその人へ向けたとき、潮門の前が水であること。見つからなければ null。
    // 戻り値は (x, 向き, z)。近すぎると甲羅が人に重なるので、中心は人から 40〜64 ブロック、だめなら 24〜36 ブロックを試す
    @Nullable
    static Vec3 findRestSpot(ServerLevel level, double px, double pz) {
        int[] rings = {44, 52, 60, 64, 40, 36, 30, 24};
        for (int r : rings) {
            for (int a = 0; a < 16; a++) {
                double angle = Math.PI * 2 * (a + (r % 8) / 8.0) / 16;
                double x = px + Math.cos(angle) * r;
                double z = pz + Math.sin(angle) * r;
                if (!waterAtSurface(level, x, z)) {
                    continue;
                }
                int d = depth(level, x, z);
                if (d < 6 || d > 14 || !nearShore(level, x, z, 40)) {
                    continue;
                }
                // 巨神の左を人へ向ける: 左の向き (cos yaw, sin yaw) = 人への向き
                float yaw = (float) (Mth.atan2(pz - z, px - x) * Mth.RAD_TO_DEG);
                Vec3 gate = new Vec3(x, 0, z).add(TideShellEntity.offset(yaw, TideShellEntity.GATE_SIDE, 0, -TideShellEntity.GATE_BACK));
                Vec3 front = new Vec3(x, 0, z).add(TideShellEntity.offset(yaw, TideShellEntity.EXIT_SIDE, 0, -TideShellEntity.GATE_BACK));
                if (!waterAtSurface(level, gate.x, gate.z) || !waterAtSurface(level, front.x, front.z) || depth(level, front.x, front.z) < 2) {
                    continue;
                }
                return new Vec3(x, yaw, z);
            }
        }
        return null;
    }

    // 岸 (陸のブロック) が range ブロック以内にあるか (8 方向を 4 ブロックごとに見る)
    static boolean nearShore(ServerLevel level, double x, double z, int range) {
        for (int dir = 0; dir < 8; dir++) {
            double ax = Math.cos(Math.PI / 4 * dir);
            double az = Math.sin(Math.PI / 4 * dir);
            for (int s = 4; s <= range; s += 4) {
                if (land(level, x + ax * s, z + az * s)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 岸の近くの立てる場所 (背中から、休んでいない巨神のもとへ戻るとき)。(x, z) から 48 ブロック以内で探す。
    // チャンクを読み込んでよい (テレポートの直前に一度だけ呼ぶ)。なければ null
    @Nullable
    static Vec3 standableShore(ServerLevel level, double x, double z) {
        for (int s = 0; s <= 48; s += 4) {
            for (int dir = 0; dir < (s == 0 ? 1 : 16); dir++) {
                double ax = x + Math.cos(Math.PI / 8 * dir) * s;
                double az = z + Math.sin(Math.PI / 8 * dir) * s;
                int bx = Mth.floor(ax);
                int bz = Mth.floor(az);
                level.getChunk(bx >> 4, bz >> 4);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                BlockPos below = new BlockPos(bx, top - 1, bz);
                if (top >= level.getSeaLevel() && !level.getFluidState(below).is(FluidTags.WATER)
                        && level.getBlockState(below).isSolidRender(level, below)) {
                    return new Vec3(bx + 0.5, top, bz + 0.5);
                }
            }
        }
        return null;
    }
}
