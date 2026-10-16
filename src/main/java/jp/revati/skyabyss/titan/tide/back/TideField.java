package jp.revati.skyabyss.titan.tide.back;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// 潮の溜めの水を一段ずつ置き、一段ずつ消す (08 の 3.4節)。
// 溜めは「段ごとの、水を置く所の一覧」で持つ。一覧は建てたあと (と、形が変わったとき) に、段ごとに種の所から横へたどって作る
// (囲いの中の空いている所をすべて拾うので、階段のすき間などから水が漏れない)。
// 満ちるときは下の段から、引くときは上の段から。水の源を、隣の更新を起こさないフラグで置く。
// 一 tick で動かすブロックの数は BUDGET まで。チャンクが読み込まれていない溜めは触らず、読み込まれたら追いつく
public final class TideField {
    // 一 tick に置く・消すブロックの数の上限 (干潟の招き潮を 1 秒ほどで満たせる数)
    public static final int BUDGET = 160;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static final class Pool {
        public final String id;
        public final int base;
        final List<List<BlockPos>> layers;
        final Set<ChunkPos> chunks = new HashSet<>();
        // 段ごとに「水がある」と分かっているか (null は分からない: 起動直後。目標に合わせて置き直す)
        final Boolean[] applied;
        int working = -1;
        boolean workingFill;
        int cursor;
        int target;

        Pool(String id, int base, List<List<BlockPos>> layers) {
            this.id = id;
            this.base = base;
            this.layers = layers;
            this.applied = new Boolean[layers.size()];
            for (List<BlockPos> l : layers) {
                for (BlockPos c : l) {
                    chunks.add(new ChunkPos(c));
                }
            }
        }

        public int depth() {
            return layers.size();
        }

        // いま水がある段の数 (分からない段があれば -1)
        public int level() {
            int n = 0;
            for (int k = 0; k < applied.length; k++) {
                if (applied[k] == null) {
                    return -1;
                }
                if (applied[k]) {
                    n = k + 1;
                }
            }
            return n;
        }

        public int target() {
            return target;
        }

        public boolean settled() {
            return working < 0 && level() == target;
        }

        public List<BlockPos> layer(int k) {
            return layers.get(k);
        }

        public int size() {
            int n = 0;
            for (List<BlockPos> l : layers) {
                n += l.size();
            }
            return n;
        }
    }

    private final Map<String, Pool> pools = new LinkedHashMap<>();

    public Pool put(String id, int base, List<List<BlockPos>> layers) {
        Pool old = pools.get(id);
        Pool p = new Pool(id, base, layers);
        if (old != null && old.depth() == p.depth()) {
            // 形が変わっても、分かっている水の段は引き継ぐ (新しい所は次の tick で目標に合わせる)
            for (int k = 0; k < p.applied.length; k++) {
                p.applied[k] = null;
            }
        }
        pools.put(id, p);
        return p;
    }

    public Pool pool(String id) {
        return pools.get(id);
    }

    public Iterable<Pool> pools() {
        return pools.values();
    }

    public void clear() {
        pools.clear();
    }

    // 目標を決めて、ブロックを動かす (毎 tick)
    public void tick(ServerLevel level, ToIntFunction<Pool> targets) {
        int budget = BUDGET;
        for (Pool p : pools.values()) {
            p.target = Math.max(0, Math.min(p.depth(), targets.applyAsInt(p)));
        }
        for (Pool p : pools.values()) {
            if (budget <= 0) {
                break;
            }
            if (!loaded(level, p)) {
                continue;
            }
            budget = work(level, p, budget);
        }
    }

    // 目標まですぐに動かす (自己テストとリセット用。チャンクを読み込む)
    public static void settleNow(ServerLevel level, Pool p, int target) {
        p.target = Math.max(0, Math.min(p.depth(), target));
        int guard = 0;
        while (!p.settled() && guard++ < 1000) {
            work(level, p, Integer.MAX_VALUE);
        }
    }

    private static boolean loaded(ServerLevel level, Pool p) {
        for (ChunkPos c : p.chunks) {
            if (!level.getChunkSource().hasChunk(c.x, c.z)) {
                return false;
            }
        }
        return true;
    }

    private static int work(ServerLevel level, Pool p, int budget) {
        while (budget > 0) {
            if (p.working < 0) {
                int fill = -1;
                for (int k = 0; k < p.target; k++) {
                    if (!Boolean.TRUE.equals(p.applied[k])) {
                        fill = k;
                        break;
                    }
                }
                int drain = -1;
                for (int k = p.depth() - 1; k >= p.target; k--) {
                    if (!Boolean.FALSE.equals(p.applied[k])) {
                        drain = k;
                        break;
                    }
                }
                // 引くのを先に (上の段から消す)。満たすのは、目標より上がすべて消えてから
                if (drain >= 0) {
                    p.working = drain;
                    p.workingFill = false;
                } else if (fill >= 0) {
                    p.working = fill;
                    p.workingFill = true;
                } else {
                    return budget;
                }
                p.cursor = 0;
            }
            List<BlockPos> cells = p.layers.get(p.working);
            BlockState water = Blocks.WATER.defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            while (p.cursor < cells.size() && budget > 0) {
                BlockPos c = cells.get(p.cursor++);
                BlockState s = level.getBlockState(c);
                boolean logged = s.hasProperty(BlockStateProperties.WATERLOGGED);
                if (p.workingFill) {
                    if (logged) {
                        // 階段など、水を抱えられるブロックは水に沈める (隣の水が勝手に入り、引いても残るのを防ぐ)
                        if (!s.getValue(BlockStateProperties.WATERLOGGED)) {
                            level.setBlock(c, s.setValue(BlockStateProperties.WATERLOGGED, true), FLAGS);
                            budget--;
                        }
                    } else if (s.isAir() || (s.is(Blocks.WATER) && !s.getFluidState().isSource())) {
                        level.setBlock(c, water, FLAGS);
                        budget--;
                    }
                } else if (logged) {
                    if (s.getValue(BlockStateProperties.WATERLOGGED)) {
                        level.setBlock(c, s.setValue(BlockStateProperties.WATERLOGGED, false), FLAGS);
                        budget--;
                    }
                } else if (s.is(Blocks.WATER)) {
                    level.setBlock(c, air, FLAGS);
                    budget--;
                }
            }
            if (p.cursor >= cells.size()) {
                p.applied[p.working] = p.workingFill;
                p.working = -1;
            }
        }
        return budget;
    }

    // 段ごとの水を置く所を、種の所から横へたどって集める。box (x1, x2, z1, z2) の外へは出ない。
    // 空気と水の所を通る。solid が true の所 (閉じている門など) は壁とみなす。
    // 上の段の種は、下の段で拾った所の真上 (空いていれば)
    // 段ごとではなく立体でたどる (段の下のすき間や、台の段のくぼみに水が落ちて残らないように)。見つけた所を高さで分ける
    public static List<List<BlockPos>> flood(ServerLevel level, int x1, int x2, int z1, int z2, List<BlockPos> seeds, int base, int depth,
            Predicate<BlockPos> solid) {
        List<List<BlockPos>> layers = new ArrayList<>();
        for (int k = 0; k < depth; k++) {
            layers.add(new ArrayList<>());
        }
        int top = base + depth - 1;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos s : seeds) {
            for (int y = base; y <= top; y++) {
                BlockPos p = new BlockPos(s.getX(), y, s.getZ());
                if (passable(level, p, solid) && seen.add(p.asLong())) {
                    queue.add(p);
                }
            }
        }
        int count = 0;
        while (!queue.isEmpty() && count < 40000) {
            BlockPos c = queue.poll();
            layers.get(c.getY() - base).add(c);
            count++;
            for (BlockPos n : new BlockPos[] {c.north(), c.south(), c.east(), c.west(), c.above(), c.below()}) {
                if (n.getX() < x1 || n.getX() > x2 || n.getZ() < z1 || n.getZ() > z2 || n.getY() < base || n.getY() > top) {
                    continue;
                }
                if (seen.add(n.asLong()) && passable(level, n, solid)) {
                    queue.add(n);
                }
            }
        }
        return layers;
    }

    private static boolean passable(ServerLevel level, BlockPos p, Predicate<BlockPos> solid) {
        if (solid.test(p)) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        return s.isAir() || s.is(Blocks.WATER) || s.hasProperty(BlockStateProperties.WATERLOGGED);
    }
}
