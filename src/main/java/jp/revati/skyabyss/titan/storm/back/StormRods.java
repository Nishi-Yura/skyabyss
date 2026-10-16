package jp.revati.skyabyss.titan.storm.back;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 【仮の実装】避雷の柱 (12 の 10.3節・11.1節の約束の API)。本物は背中の担当 (担当 1) が作る。統合で本物に置き換える。
// ここでは柱 = 避雷針のブロック (柱の先) の位置。find はまわりの避雷針のブロックを探すだけ、満ちる段と接地はメモリの表に持つ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormRods {
    public static final int FULL = 3;
    private static final Map<BlockPos, Integer> CHARGE = new HashMap<>();
    private static final Map<BlockPos, Long> GROUNDED_UNTIL = new HashMap<>();
    private static long now;

    private StormRods() {
    }

    // pos から水平に radius 以内の柱 (いちばん近いもの)。なければ null
    @Nullable
    public static BlockPos find(Level level, Vec3 pos, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos base = BlockPos.containing(pos);
        BlockPos best = null;
        double bestD = radius * radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 9; dy++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(Blocks.LIGHTNING_ROD)) {
                        continue;
                    }
                    double hx = p.getX() + 0.5 - pos.x;
                    double hz = p.getZ() + 0.5 - pos.z;
                    double d = hx * hx + hz * hz;
                    if (d <= bestD) {
                        bestD = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    // 柱に雷を amount 段足す。満ちた段 (0〜FULL) を返す
    public static int charge(ServerLevel level, BlockPos rod, int amount) {
        int c = Math.min(FULL, CHARGE.getOrDefault(rod, 0) + amount);
        CHARGE.put(rod.immutable(), c);
        return c;
    }

    public static boolean isGrounded(BlockPos rod) {
        Long until = GROUNDED_UNTIL.get(rod);
        return until != null && until > now;
    }

    // 柱を ticks のあいだ接地させる (先の光が消え、雷を受けない)
    public static void ground(BlockPos rod, int ticks) {
        GROUNDED_UNTIL.put(rod.immutable(), now + ticks);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        now++;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CHARGE.clear();
        GROUNDED_UNTIL.clear();
    }
}
