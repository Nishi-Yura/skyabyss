package jp.revati.skyabyss.titan.storm.back;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 避雷の柱 (12 の 4.2節・5.2節・5.3節)。東の試練・雷獣・心臓で共通。
// 柱は「根元の座標」(BlockPos) で表す。建てる側が register で登録し、ここが満ちる段と接地を覚える (メモリだけ。リセットで消える)。
// - find(level, pos, 3.0): pos から 3 ブロック以内 (水平) で一番近い柱。招雷の輪がここに入ると、雷は柱へ落ちる
// - charge(level, rod, n): 柱に雷を n 回受けさせる。新しい段を返す。満ちたら (3 段) 先が光り続ける
// - isGrounded(rod) / ground(rod, ticks): 接地の栓 (雷獣の戦い)。接地している柱へ渡った雷獣は痺れて落ちる
public final class StormRods {
    // 柱が満ちる段 (東の試練・心臓の段階 1)
    public static final int FULL = 3;
    // 柱の高さ (根元から先まで)
    public static final int HEIGHT = 6;

    private static final Map<ResourceKey<Level>, Map<BlockPos, Rod>> RODS = new HashMap<>();
    // 接地は level を渡さずに見られるよう、柱の座標だけで覚える (柱の座標はディメンションをまたいで重ならない)
    private static final Map<BlockPos, Long> GROUNDED = new HashMap<>();
    private static long now;

    public static final class Rod {
        public final BlockPos base;
        public final int full;
        int charge;

        Rod(BlockPos base, int full) {
            this.base = base.immutable();
            this.full = full;
        }

        public int charge() {
            return charge;
        }

        public boolean full() {
            return charge >= full;
        }

        // 柱の先 (雷が落ちる所)
        public Vec3 tip() {
            return new Vec3(base.getX() + 0.5, base.getY() + HEIGHT, base.getZ() + 0.5);
        }
    }

    private StormRods() {
    }

    // 柱を登録する (建てたとき・戦いが始まったとき)。すでにあれば段を空に戻す
    public static Rod register(ServerLevel level, BlockPos base) {
        return register(level, base, FULL);
    }

    public static Rod register(ServerLevel level, BlockPos base, int full) {
        Rod rod = new Rod(base, full);
        RODS.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>()).put(rod.base, rod);
        GROUNDED.remove(rod.base);
        return rod;
    }

    public static void unregister(ServerLevel level, BlockPos base) {
        Map<BlockPos, Rod> m = RODS.get(level.dimension());
        if (m != null) {
            m.remove(base);
        }
        GROUNDED.remove(base);
    }

    // そのディメンションの柱をすべて外す (リセット)
    public static void clear(ServerLevel level) {
        Map<BlockPos, Rod> m = RODS.remove(level.dimension());
        if (m != null) {
            m.keySet().forEach(GROUNDED::remove);
        }
    }

    public static java.util.Collection<Rod> all(ServerLevel level) {
        Map<BlockPos, Rod> m = RODS.get(level.dimension());
        return m == null ? java.util.List.of() : java.util.Collections.unmodifiableCollection(m.values());
    }

    @Nullable
    public static Rod get(ServerLevel level, BlockPos base) {
        Map<BlockPos, Rod> m = RODS.get(level.dimension());
        return m == null ? null : m.get(base);
    }

    // pos から radius ブロック以内 (水平の距離。高さは柱の根元から先まで +-2) で一番近い柱の根元 (なければ null)
    @Nullable
    public static BlockPos find(ServerLevel level, Vec3 pos, double radius) {
        Map<BlockPos, Rod> m = RODS.get(level.dimension());
        if (m == null) {
            return null;
        }
        BlockPos best = null;
        double bestD = radius * radius;
        for (Rod r : m.values()) {
            if (pos.y < r.base.getY() - 2 || pos.y > r.base.getY() + HEIGHT + 2) {
                continue;
            }
            double dx = pos.x - (r.base.getX() + 0.5);
            double dz = pos.z - (r.base.getZ() + 0.5);
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = r.base;
            }
        }
        return best;
    }

    @Nullable
    public static BlockPos find(ServerLevel level, BlockPos pos, double radius) {
        return find(level, Vec3.atBottomCenterOf(pos), radius);
    }

    // 柱に雷を n 回受けさせる。新しい段を返す (登録されていない柱なら 0)。満ちた瞬間に音と光
    public static int charge(ServerLevel level, BlockPos rod, int n) {
        Rod r = get(level, rod);
        if (r == null) {
            return 0;
        }
        boolean wasFull = r.full();
        r.charge = Math.min(r.full, r.charge + n);
        Vec3 tip = r.tip();
        level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.COPPER_HIT,
                SoundSource.BLOCKS, 1.5f, 0.8f + 0.2f * r.charge);
        if (!wasFull && r.full()) {
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 1.4f);
            StormFx.burst(level, ParticleTypes.ELECTRIC_SPARK, tip, 40, 0.6);
        }
        return r.charge;
    }

    public static int chargeOf(ServerLevel level, BlockPos rod) {
        Rod r = get(level, rod);
        return r == null ? 0 : r.charge;
    }

    public static boolean isFull(ServerLevel level, BlockPos rod) {
        Rod r = get(level, rod);
        return r != null && r.full();
    }

    // 柱を空に戻す (心臓の「翼が裂ける」のあと)
    public static void empty(ServerLevel level, BlockPos rod) {
        Rod r = get(level, rod);
        if (r != null) {
            r.charge = 0;
        }
    }

    public static boolean isGrounded(BlockPos rod) {
        Long until = GROUNDED.get(rod);
        return until != null && until > now;
    }

    // 柱を ticks のあいだ接地させる (先の光が消える)
    public static void ground(BlockPos rod, int ticks) {
        GROUNDED.put(rod.immutable(), now + ticks);
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        static void onTick(ServerTickEvent.Post event) {
            now++;
        }

        @SubscribeEvent
        static void onStopped(ServerStoppedEvent event) {
            RODS.clear();
            GROUNDED.clear();
            now = 0;
        }
    }
}
