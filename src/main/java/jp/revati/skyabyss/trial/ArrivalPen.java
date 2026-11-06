package jp.revati.skyabyss.trial;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 到着の台の囲い (2026-11-07 の声「岩鯨以外の巨神の待つ間で歩き回れてしまう」)。岩鯨の台は胸壁で囲ってあるが、潮殻 (殻の上の砂浜)・
// 雷翼 (尾羽の上の長い道)・余燼 (灰の原) の台はまわりが開いていて、鐘を待つあいだに背の上を歩いて回れた。背から下りるのは笛なので、台の外に用はない。
// 入口の箱 (TrialRoute.entryBox。到着の足もと ±6) のすぐ外の一周 (±RING) に、その巨神の石の塀 (1.5 の高さ。跳んで越せない) と、上に見えない壁を置く。
//   その列の地面 (足もとの 6 下〜2 上の、上が空いた固い所) の上に塀。地面がない列 (空・海の上) は見えない壁だけ (宙に塀を浮かせない)。
//   足の高さとその上が両方とも固い列 (体・崖) は、もう塞がっているので何もしない。空気・草・水の所だけに置く (台のほかの物は壊さない)
// 背を建て直しても戻るように、人が背にいるあいだ CHECK_EVERY ごとに見て、欠けた所を置く (何度置いても同じ形)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ArrivalPen {
    public static final int RING = 7;
    static final int CHECK_EVERY = 200;
    static final int DOWN = 6;
    static final int UP = 2;

    private ArrivalPen() {
    }

    // 囲う巨神と、その塀
    public static BlockState wallOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> Blocks.PRISMARINE_WALL.defaultBlockState();
            case Titans.THUNDER_WING -> Blocks.DIORITE_WALL.defaultBlockState();
            case Titans.EMBER -> Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
            default -> null;
        };
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 41) {
            return;
        }
        for (String titan : new String[] {Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER}) {
            ServerLevel back = server.getLevel(Titans.backOf(titan));
            if (back != null && !back.players().isEmpty()) {
                place(back, titan);
            }
        }
    }

    // 囲いを置く (読み込まれていない列は飛ばす)。置いたブロックの数
    public static int place(ServerLevel back, String titan) {
        BlockState wall = wallOf(titan);
        if (wall == null) {
            return 0;
        }
        BlockPos f = RouteSeals.safeFeet(titan);
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        int n = 0;
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RING) {
                    continue;
                }
                int x = f.getX() + dx;
                int z = f.getZ() + dz;
                if (!back.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                if (solid(back, new BlockPos(x, f.getY(), z)) && solid(back, new BlockPos(x, f.getY() + 1, z))) {
                    continue;
                }
                // 地面: 足もとの 2 上から 6 下へ、上が空いた固い所 (塀・見えない壁は地面に数えない)
                Integer ground = null;
                for (int y = f.getY() + UP; y >= f.getY() - DOWN; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (solid(back, p) && !mine(back, p, wall) && (!solid(back, p.above()) || mine(back, p.above(), wall))) {
                        ground = y;
                        break;
                    }
                }
                int top = f.getY() + 2;
                int from;
                if (ground != null) {
                    from = ground + 1;
                    n += put(back, new BlockPos(x, from, z), wall);
                    from++;
                } else {
                    from = f.getY() - 1;
                }
                for (int y = from; y <= Math.max(top, from + 1); y++) {
                    n += put(back, new BlockPos(x, y, z), barrier);
                }
            }
        }
        if (n > 0) {
            SkyAbyss.LOGGER.info("Arrival pen of the {}: placed {} blocks", titan, n);
        }
        return n;
    }

    // 囲いの一周のうち、足の高さと頭の高さのどちらも塞がっていない列の数 (0 なら台から歩いて出られない。自己テスト)
    public static int openColumns(ServerLevel back, String titan) {
        BlockPos f = RouteSeals.safeFeet(titan);
        int open = 0;
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RING) {
                    continue;
                }
                BlockPos feet = new BlockPos(f.getX() + dx, f.getY(), f.getZ() + dz);
                if (!blocks(back, feet) && !blocks(back, feet.above())) {
                    open++;
                }
            }
        }
        return open;
    }

    private static boolean blocks(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return s.is(Blocks.BARRIER) || !s.getCollisionShape(back, p).isEmpty();
    }

    private static boolean solid(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return !s.getCollisionShape(back, p).isEmpty() && !s.is(Blocks.BARRIER);
    }

    private static boolean mine(ServerLevel back, BlockPos p, BlockState wall) {
        BlockState s = back.getBlockState(p);
        return s.is(wall.getBlock()) || s.is(Blocks.BARRIER);
    }

    private static int put(ServerLevel back, BlockPos p, BlockState state) {
        BlockState s = back.getBlockState(p);
        if (s.is(state.getBlock())) {
            return 0;
        }
        if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
            back.setBlock(p, state, Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }
}
