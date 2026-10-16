package jp.revati.skyabyss.prologue;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

// 見る者の立ち位置を選ぶ。
// 見通しの良い地面の上 (目の高さから頭と胸が見える)、暗い所、空の下。
// できれば少し高い所で、背後が空になる (稜線に影が浮かぶ) 場所を選ぶ。
// 気づく前に視界の真ん中へ急に現れないよう、ふだんは今見ている向きの外 (左右と後ろ) から選ぶ
public record WatcherSpot(Vec3 feet, float yaw, double distance, boolean skyline) {
    // 何か所試すか
    static final int TRIES = 48;
    // 見ている向きから何度より外を選ぶか
    static final float VIEW_HALF_ANGLE = 75f;
    // 見る者の頭 (光る目) と胸の高さ (足もとから)。背丈は 2.1 ブロック
    public static final double HEAD = 1.95;
    static final double CHEST = 1.2;
    // 立ち位置の明るさ (ブロックの光) の上限。松明の光の中には立たない
    static final int MAX_BLOCK_LIGHT = 5;

    public static Optional<WatcherSpot> find(ServerLevel level, Vec3 eye, float viewYaw, int minDistance, int maxDistance,
                                             RandomSource random, boolean avoidView) {
        WatcherSpot best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < TRIES; i++) {
            float rel = avoidView
                    ? VIEW_HALF_ANGLE + random.nextFloat() * (360f - 2f * VIEW_HALF_ANGLE)
                    : random.nextFloat() * 360f;
            float yaw = viewYaw + rel;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
            double dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
            int x = Mth.floor(eye.x + dx * distance);
            int z = Mth.floor(eye.z + dz * distance);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!standable(level, feet)) {
                continue;
            }
            double rise = y - eye.y;
            if (rise < -12 || rise > 32) {
                continue;
            }
            Vec3 base = Vec3.atBottomCenterOf(feet);
            Vec3 head = base.add(0, HEAD, 0);
            if (!clear(level, eye, head) || !clear(level, eye, base.add(0, CHEST, 0))) {
                continue;
            }
            // 背後が空か: 目から頭へ向かう線をそのまま 32 ブロック伸ばして、何にも当たらないか
            Vec3 away = head.subtract(eye).normalize();
            boolean skyline = clear(level, head, head.add(away.scale(32)));
            double score = (skyline ? 6.0 : 0.0) + Mth.clamp(rise, -4.0, 12.0) * 0.5 + random.nextDouble() * 2.0;
            if (score > bestScore) {
                bestScore = score;
                float face = (float) (Mth.atan2(eye.z - base.z, eye.x - base.x) * Mth.RAD_TO_DEG) - 90f;
                best = new WatcherSpot(base, Mth.wrapDegrees(face), Math.sqrt(base.distanceToSqr(eye.x, base.y, eye.z)), skyline);
            }
        }
        return Optional.ofNullable(best);
    }

    // 足もとが固い地面 (葉や液体ではない) で、体の入る 3 ブロックが空いていて、暗く、空の下
    static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(BlockTags.LEAVES) || !ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        for (int dy = 0; dy < 3; dy++) {
            BlockPos p = feet.above(dy);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) {
                return false;
            }
        }
        return level.getBrightness(LightLayer.BLOCK, feet.above()) <= MAX_BLOCK_LIGHT && level.canSeeSky(feet.above(2));
    }

    // 2 点のあいだに、目に見えるブロックがないか (ガラス越しは見える)
    static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }
}
