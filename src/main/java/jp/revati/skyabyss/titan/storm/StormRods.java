package jp.revati.skyabyss.titan.storm;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// [仮の実装 / STUB] 避雷の柱 (12 の 10.3節)。本物は背中の担当 (担当 1) が作る (柱のブロック、満ちる段、接地の栓)。
// 11.1節の名前と引数だけそろえた空の実装 (柱は見つからない)。統合で置き換える。
// 心臓の竜骨の間の 4 本の柱は、心臓に固有の決まり (3 段で満ちる・4 本で翼が裂ける・満ちた柱は受けない・段階 3 では満ちない) が
// あるので、心臓の側 (titan.storm.heart.StormHeartArena) が自分で持っている
public final class StormRods {
    private StormRods() {
    }

    // pos から radius 以内の避雷の柱 (なければ null)
    @Nullable
    public static BlockPos find(Level level, Vec3 pos, double radius) {
        return null;
    }

    // 柱を amount 段満たす。満ちた段を返す
    public static int charge(Level level, BlockPos rod, int amount) {
        return 0;
    }

    public static boolean isGrounded(BlockPos rod) {
        return false;
    }

    public static void ground(BlockPos rod, int ticks) {
    }
}
