package jp.revati.skyabyss.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 潮の番人の「満ち潮の輪」が床に置く一時の水。
//   flood(level, cx, y, cz, r, ticks)  円の中の柱ごとに、足の高さ y から上の最初の空気 (y か y+1。下が空気でない所) に水源を置く。
//                                      部屋の 1 段の浅瀬は 2 段に、乾いた床は 1 段になる。空気のマスだけを使い、ほかの物は壊さない
//   ticks のあとに戻す                  置いた前に空気だったマス (円の外 2 マスまで、足の高さとその上) で、今水のものをすべて空気に戻す。
//                                      置いた水源どうしの間に水源が生まれ (無限の水)、置いたマスだけ戻すと水が残っていた (2026-11-07 の声「水消えない」)。
//                                      チャンクが読み込まれていなければ、読み込まれるまで待って戻す (前は記録を捨てて水が残った)。
//                                      持ち主が倒れても、サーバーが止まるときも、残りはすべて戻す (部屋は元どおり)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ShoreFlood {
    public record Patch(ResourceKey<Level> dimension, List<BlockPos> cells, List<BlockPos> dry, long until) {
    }

    private static final List<Patch> ACTIVE = new CopyOnWriteArrayList<>();

    private ShoreFlood() {
    }

    // 水を置き、戻す記録を残す。置いたマスの数が 0 でも記録は返す
    public static Patch flood(ServerLevel level, double cx, int y, double cz, double radius, int ticks) {
        Set<BlockPos> taken = new HashSet<>();
        for (Patch p : ACTIVE) {
            taken.addAll(p.cells());
        }
        List<BlockPos> cells = new ArrayList<>();
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double px = Math.floor(cx) + dx + 0.5 - cx;
                double pz = Math.floor(cz) + dz + 0.5 - cz;
                if (px * px + pz * pz > radius * radius) {
                    continue;
                }
                BlockPos column = BlockPos.containing(cx + px, y, cz + pz);
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                for (int up = 0; up <= 1; up++) {
                    BlockPos at = column.above(up);
                    if (taken.contains(at)) {
                        break;
                    }
                    if (level.getBlockState(at).isAir()) {
                        if (!level.getBlockState(at.below()).isAir()) {
                            level.setBlock(at, Blocks.WATER.defaultBlockState(), 3);
                            cells.add(at.immutable());
                        }
                        break;
                    }
                    if (level.getFluidState(at).isEmpty()) {
                        break;
                    }
                }
            }
        }
        // 戻すときに見るマス: 置く前に空気だった所 (円の外 2 マスまで、y と y+1)
        List<BlockPos> dry = new ArrayList<>();
        int rr = r + 2;
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dz = -rr; dz <= rr; dz++) {
                for (int up = 0; up <= 1; up++) {
                    BlockPos at = BlockPos.containing(cx + dx, y + up, cz + dz);
                    if (cells.contains(at) || (level.hasChunkAt(at) && level.getBlockState(at).isAir() && !taken.contains(at))) {
                        dry.add(at.immutable());
                    }
                }
            }
        }
        Patch patch = new Patch(level.dimension(), List.copyOf(cells), List.copyOf(dry), level.getGameTime() + ticks);
        ACTIVE.add(patch);
        return patch;
    }

    // 期限の来た水を戻す (サーバーの tick から。試験からも now を渡して呼べる)
    public static int revertDue(MinecraftServer server, long now) {
        int n = 0;
        for (Patch p : ACTIVE) {
            if (p.until() <= now && loaded(server, p)) {
                n += revert(server, p);
            }
        }
        return n;
    }

    static boolean loaded(MinecraftServer server, Patch p) {
        ServerLevel level = server.getLevel(p.dimension());
        if (level == null) {
            return true;
        }
        for (BlockPos at : p.dry()) {
            if (!level.hasChunkAt(at)) {
                return false;
            }
        }
        return true;
    }

    // 一つの記録を今すぐ戻す。戻したマスの数 (読み込まれていない所は戻せない)
    public static int revert(MinecraftServer server, Patch p) {
        ACTIVE.remove(p);
        ServerLevel level = server.getLevel(p.dimension());
        if (level == null) {
            return 0;
        }
        int n = 0;
        java.util.Set<BlockPos> done = new HashSet<>();
        for (List<BlockPos> list : List.of(p.cells(), p.dry())) {
            for (BlockPos at : list) {
                if (done.add(at) && level.hasChunkAt(at) && level.getFluidState(at).is(Fluids.WATER) && level.getBlockState(at).is(Blocks.WATER)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                    n++;
                }
            }
        }
        return n;
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static List<Patch> active() {
        return List.copyOf(ACTIVE);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!ACTIVE.isEmpty() && event.getServer().getTickCount() % 5 == 0) {
            revertDue(event.getServer(), event.getServer().overworld().getGameTime());
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        for (Patch p : List.copyOf(ACTIVE)) {
            revert(event.getServer(), p);
        }
        ACTIVE.clear();
    }
}
