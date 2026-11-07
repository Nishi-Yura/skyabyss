package jp.revati.skyabyss.titan.shrine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// 地上の巨神の祠の建物 (2026-11-07 の頁で決めた、巨神ごとに別の作り)。部品 shrine_<巨神> (tools/gen/world/build_titan_shrines.py) を置く。
//   岩鯨 = 鯨骨の聖堂 / 潮殻 = 潮の神殿 / 雷翼 = 雷の歌の環 / 余燼 = 炉の門
//   部品の原点は広場の真ん中で、y 0 = 地面の一つ上。印 tablet (碑)・pad (集合台の真ん中の立つ所)・pad_r・forge・path_end・radius
//   置く: 広場のまわり (半径 radius + 3) と参道の帯の上を空け (木・地形・前の版の祠)、部品を置き、部品の列の下を地面まで巨神の石で支える。
//   参道の終わり (path_end) から先は、地面の高さまで一段ずつの道を延ばす (道の手前で止める)。大陸の道の上には何も置かない
final class ShrineBuild {
    private ShrineBuild() {
    }
    // 建て直すための版 (TitanShrine.Data の ver)。2 = 巨神ごとの祠、3 = 雷翼の羽根の段を面でつなぐ (2026-11-07「ハーフブロックが接していない」)
    static final int VERSION = 3;
    static final int CLEAR_UP = 36;
    static final int FOOT_DOWN = 24;
    static final int PATH_MORE = 16;

    record Layout(Blueprint bp, BlockPos tablet, BlockPos pad, int padR, BlockPos forge, int pathHalf, int pathEnd, int radius) {
    }

    private static final Map<String, Layout> CACHE = new HashMap<>();

    static synchronized Layout layout(String titan) {
        return CACHE.computeIfAbsent(titan, t -> {
            Blueprint bp = Blueprint.get("shrine_" + t);
            return new Layout(bp, mark(bp, "tablet"), mark(bp, "pad"), bp.marks.get("pad_r")[0], mark(bp, "forge"), bp.marks.get("path_end")[0],
                    bp.marks.get("path_end")[2], bp.marks.get("radius")[0]);
        });
    }

    private static BlockPos mark(Blueprint bp, String name) {
        int[] m = bp.marks.get(name);
        return new BlockPos(m[0], m[1], m[2]);
    }

    // 碑の位置 → 部品の原点 (広場の真ん中、地面の一つ上)
    static BlockPos originOf(BlockPos tablet, String titan) {
        return tablet.subtract(layout(titan).tablet());
    }

    static BlockPos tabletOf(BlockPos origin, String titan) {
        return origin.offset(layout(titan).tablet());
    }

    // 集合台の真ん中の立つ所
    static BlockPos padOf(BlockPos tablet, String titan) {
        return originOf(tablet, titan).offset(layout(titan).pad());
    }

    static BlockPos forgeOf(BlockPos tablet, String titan) {
        return originOf(tablet, titan).offset(layout(titan).forge());
    }

    // 支えの石
    static BlockState footing(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> Blocks.SANDSTONE.defaultBlockState();
            case Titans.THUNDER_WING -> Blocks.ANDESITE.defaultBlockState();
            case Titans.EMBER -> Blocks.BLACKSTONE.defaultBlockState();
            default -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        };
    }

    // 建てる (origin = 広場の真ん中、地面の一つ上)。置いたブロックの数
    static int build(ServerLevel level, BlockPos origin, String titan) {
        Layout l = layout(titan);
        Blueprint bp = l.bp();
        int r = l.radius() + 3;
        int zEnd = l.pathEnd() + PATH_MORE;
        for (int x = origin.getX() - r - 16; x <= origin.getX() + r + 16; x += 16) {
            for (int z = origin.getZ() - r - 16; z <= origin.getZ() + zEnd + 16; z += 16) {
                level.getChunk(x >> 4, z >> 4);
            }
        }
        boolean roads = jp.revati.skyabyss.world.continent.RoadClearance.live();
        BlockState air = Blocks.AIR.defaultBlockState();
        int n = 0;
        // 上を空ける: 広場の円と参道の帯
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= l.pathEnd() + 1; dz++) {
                boolean plaza = dx * dx + dz * dz <= r * r;
                boolean path = Math.abs(dx) <= l.pathHalf() + 2 && dz > 0;
                if (!plaza && !path) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (roads && jp.revati.skyabyss.world.continent.ContinentChunkGenerator.roadAt(x, z) != null) {
                    continue;
                }
                for (int y = origin.getY(); y <= origin.getY() + CLEAR_UP; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, 2);
                    }
                }
            }
        }
        // 部品
        Map<Long, Integer> lowest = new HashMap<>();
        List<BlockPos> shaped = new ArrayList<>();
        for (Blueprint.Cell c : bp.cells) {
            BlockPos p = origin.offset(c.x(), c.y(), c.z());
            if (roads && jp.revati.skyabyss.world.continent.ContinentChunkGenerator.roadAt(p.getX(), p.getZ()) != null) {
                continue;
            }
            if (!c.state().isAir()) {
                lowest.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p.getY(), Math::min);
            }
            level.setBlock(p, c.state(), 2);
            n++;
            Block b = c.state().getBlock();
            if (b instanceof StairBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof FenceBlock) {
                shaped.add(p);
            }
        }
        // 支え
        BlockState foot = footing(titan);
        for (Map.Entry<Long, Integer> e : lowest.entrySet()) {
            BlockPos col = BlockPos.of(e.getKey());
            n += footing(level, new BlockPos(col.getX(), e.getValue() - 1, col.getZ()), foot);
        }
        // 参道の続き (地面まで)
        n += extendPath(level, origin, l, foot, roads);
        for (BlockPos p : shaped) {
            BlockState s = level.getBlockState(p);
            BlockState fixed = s;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(dir);
                fixed = fixed.updateShape(dir, level.getBlockState(q), level, p, q);
            }
            if (fixed != s) {
                level.setBlock(p, fixed, 2);
            }
        }
        SkyAbyss.LOGGER.info("Built the {} shrine at {}: {} blocks", titan, origin.toShortString(), n);
        return n;
    }

    private static int footing(ServerLevel level, BlockPos from, BlockState foot) {
        int n = 0;
        BlockPos p = from;
        for (int i = 0; i < FOOT_DOWN; i++) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) {
                break;
            }
            level.setBlock(p, foot, 2);
            n++;
            p = p.below();
        }
        return n;
    }

    // 参道の終わりの先を、地面の高さまで一段ずつ (一段 1 まで)。道 (縁を含む) にかかる手前で止める
    private static int extendPath(ServerLevel level, BlockPos origin, Layout l, BlockState foot, boolean roads) {
        int n = 0;
        int f = 0; // 床の高さ (原点の y からの差。0 = 部品の参道の終わりの床)
        BlockState floor = foot;
        BlockState edge = level.getBlockState(origin.offset(l.pathHalf(), 0, l.pathEnd()));
        if (edge.isAir()) {
            edge = foot;
        }
        for (int dz = l.pathEnd() + 1; dz <= l.pathEnd() + PATH_MORE; dz++) {
            int z = origin.getZ() + dz;
            boolean road = false;
            int g = Integer.MIN_VALUE;
            for (int dx = -l.pathHalf(); dx <= l.pathHalf(); dx++) {
                int x = origin.getX() + dx;
                if (roads && jp.revati.skyabyss.world.continent.ContinentChunkGenerator.roadAt(x, z) != null) {
                    road = true;
                }
                g = Math.max(g, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1 - origin.getY());
            }
            if (road || f == g) {
                break;
            }
            f += Integer.signum(g - f);
            for (int dx = -l.pathHalf(); dx <= l.pathHalf(); dx++) {
                BlockPos p = new BlockPos(origin.getX() + dx, origin.getY() + f, z);
                level.setBlock(p, Math.abs(dx) == l.pathHalf() ? edge : floor, 2);
                for (int y = 1; y <= 5; y++) {
                    if (!level.getBlockState(p.above(y)).isAir()) {
                        level.setBlock(p.above(y), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                n += 1 + footing(level, p.below(), foot);
            }
        }
        return n;
    }

    // 守る箱の半径 (広場の円)
    static int guardRadius(String titan) {
        return layout(titan).radius() + 1;
    }

    // 集合台の上か (台の真ん中から水平に半径 + 0.5、高さ ±2)
    static boolean onPad(double x, double y, double z, BlockPos tablet, String titan) {
        BlockPos pad = padOf(tablet, titan);
        double dx = x - (pad.getX() + 0.5);
        double dz = z - (pad.getZ() + 0.5);
        double rr = layout(titan).padR() + 0.5;
        return dx * dx + dz * dz <= rr * rr && Math.abs(y - pad.getY()) <= 2;
    }

    @Nullable
    static String titanOfTablet(java.util.Map<String, BlockPos> pos, BlockPos p) {
        for (Map.Entry<String, BlockPos> e : pos.entrySet()) {
            if (e.getValue().equals(p)) {
                return e.getKey();
            }
        }
        return null;
    }
}
