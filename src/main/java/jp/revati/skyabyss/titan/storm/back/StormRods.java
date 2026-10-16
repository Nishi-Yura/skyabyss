package jp.revati.skyabyss.titan.storm.back;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 【空の実装 (敵の担当が置いた)】避雷の柱 (12 の 4.2節、11.1 の API)。統合で背中の担当 (担当 1) の本物に置き換わる。
// 本物は柱のブロックを持つ。ここでは、試すためにバニラの避雷針のブロックを柱とみなし、満ちる段と接地を覚えるだけ
public final class StormRods {
    public static final int FULL = 3;

    private static final Map<BlockPos, Integer> CHARGE = new HashMap<>();
    private static final Map<BlockPos, Long> GROUNDED_UNTIL = new HashMap<>();

    private StormRods() {
    }

    // pos から radius ブロック以内で、いちばん近い柱 (無ければ null)
    @Nullable
    public static BlockPos find(ServerLevel level, Vec3 pos, double radius) {
        BlockPos best = null;
        double bestD = radius * radius;
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(pos);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (level.getBlockState(p).is(Blocks.LIGHTNING_ROD)) {
                double dx = p.getX() + 0.5 - pos.x;
                double dz = p.getZ() + 0.5 - pos.z;
                double d = dx * dx + dz * dz;
                if (d <= bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    // 柱を amount 段満たす (負なら減らす。雷蛾が吸う)。満ちたら true
    public static boolean charge(ServerLevel level, BlockPos rod, int amount) {
        int c = Math.max(0, Math.min(FULL, CHARGE.getOrDefault(rod, 0) + amount));
        CHARGE.put(rod.immutable(), c);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, rod.getX() + 0.5, rod.getY() + 1.0, rod.getZ() + 0.5, 12 * Math.max(1, c), 0.2, 0.4, 0.2, 0.2);
        return c >= FULL;
    }

    public static int chargeOf(BlockPos rod) {
        return CHARGE.getOrDefault(rod, 0);
    }

    public static boolean isGrounded(BlockPos rod) {
        return GROUNDED_UNTIL.getOrDefault(rod, Long.MIN_VALUE) > StormBackClock.now();
    }

    public static void ground(BlockPos rod, int ticks) {
        GROUNDED_UNTIL.put(rod.immutable(), StormBackClock.now() + ticks);
    }

    public static void clear() {
        CHARGE.clear();
        GROUNDED_UNTIL.clear();
    }
}
