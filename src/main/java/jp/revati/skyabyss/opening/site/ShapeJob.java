package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.opening.site.ChapterGrounds.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// 跡地の「整える形」を建てる仕事 (一辺 160 まで)。SiteJob と同じく、1 tick の予算だけ進める。
// 形の四角を 16 列ずつの帯に分け、帯のチャンクを読み込ませて (チケット)、列ごとに ChapterGrounds.shapeTop の高さへ盛る・削る・水を張る。
//   丘   盛るだけ (削らない)。中は土、深い所は石、上は草。盛る列の木と草は片づける
//   峰   盛るだけ。石・安山岩・凝灰岩の筋、上の方は雪
//   入り江 掘って海面まで水を張る (底は砂と砂利、底の下 3 と浜の縁の壁は石で閉じる = 水が洞窟へ抜けない)。縁は砂の浜
//   灰の盆地 削って (低い所は盛って) 鉢の形に。上は凝灰岩・砂利・灰色のコンクリートの粉・玄武岩のまだら (灰の地)
// 置くときは近所の更新を起こさない (フラグ 2|16。水は流れ出さない)。鉱石・洞窟は形の下にそのまま残る
final class ShapeJob {
    private static final int FLAGS = SiteJob.FLAGS;
    // 帯の幅 (列)
    private static final int BAND = 16;
    // 盛る列で片づける木の高さ
    private static final int CLEAR_UP = 32;

    final ServerLevel level;
    final SiteData data;
    final SiteData.Ground ground;
    private final Shape shape;
    private final int x0;
    private final int z0;
    private final int x1;
    private final int z1;
    private final RandomSource random;
    private int bandZ;
    private int col;
    private final List<ChunkPos> tickets = new ArrayList<>();
    private boolean loaded;
    boolean blocking;
    int placed;
    int ticks;
    long workNanos;

    ShapeJob(ServerLevel level, SiteData data, SiteData.Ground ground) {
        this.level = level;
        this.data = data;
        this.ground = ground;
        this.shape = ChapterGrounds.shapeOf(ground);
        int h = ChapterGrounds.half(shape);
        BlockPos c = ground.shapeCenter;
        // 入り江は浜の外の壁 (1 列) まで
        int pad = shape == Shape.COVE ? 1 : 0;
        x0 = c.getX() - h - pad;
        z0 = c.getZ() - h - pad;
        x1 = c.getX() + h + pad;
        z1 = c.getZ() + h + pad;
        bandZ = z0;
        random = RandomSource.create(c.asLong());
    }

    // deadline まで進める。終わったら true
    boolean step(long deadline) {
        long t0 = System.nanoTime();
        ticks++;
        try {
            while (bandZ <= z1 && System.nanoTime() < deadline) {
                if (!loaded) {
                    if (!load()) {
                        return false;
                    }
                }
                int bz1 = Math.min(z1, bandZ + BAND - 1);
                int width = x1 - x0 + 1;
                int rows = bz1 - bandZ + 1;
                while (col < width * rows && System.nanoTime() < deadline) {
                    int x = x0 + col % width;
                    int z = bandZ + col / width;
                    column(x, z);
                    col++;
                }
                if (col >= width * rows) {
                    release();
                    col = 0;
                    bandZ = bz1 + 1;
                }
            }
        } finally {
            workNanos += System.nanoTime() - t0;
        }
        return bandZ > z1;
    }

    // 帯のチャンクを読み込ませる (読み込まれるまで false)
    private boolean load() {
        int bz1 = Math.min(z1, bandZ + BAND - 1);
        if (tickets.isEmpty()) {
            for (int cx = (x0 - 1) >> 4; cx <= (x1 + 1) >> 4; cx++) {
                for (int cz = (bandZ - 1) >> 4; cz <= (bz1 + 1) >> 4; cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    level.getChunkSource().addRegionTicket(SiteJob.TICKET, cp, 2, cp);
                    tickets.add(cp);
                }
            }
        }
        for (ChunkPos cp : tickets) {
            if (blocking) {
                level.getChunk(cp.x, cp.z);
            } else if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                return false;
            }
        }
        loaded = true;
        return true;
    }

    void release() {
        for (ChunkPos cp : tickets) {
            level.getChunkSource().removeRegionTicket(SiteJob.TICKET, cp, 2, cp);
        }
        tickets.clear();
        loaded = false;
    }

    private void set(BlockPos p, BlockState s) {
        if (!level.getBlockState(p).equals(s)) {
            level.setBlock(p, s, FLAGS);
            placed++;
        }
    }

    // 地面 (木・葉・草・雪を除いた、いちばん上の固いブロック)。水の列は水の下の地面
    private int surface(int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (; y > level.getMinBuildHeight(); y--) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || soft(s)) {
                continue;
            }
            return y;
        }
        return y;
    }

    // 地面に数えない物 (木・葉・草花・雪・つる)
    private static boolean soft(BlockState s) {
        return s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(Blocks.SNOW) || s.is(Blocks.VINE) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM)
                || s.is(Blocks.MANGROVE_ROOTS) || s.is(Blocks.BEE_NEST) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.COCOA);
    }

    // 上の木・葉・草を、from から CLEAR_UP だけ片づける (空気に。水はそのまま)
    private void clearAbove(int x, int z, int from) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = from; y <= from + CLEAR_UP; y++) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty() && soft(s)) {
                set(p.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private void column(int x, int z) {
        int sea = level.getSeaLevel();
        int natural = surface(x, z);
        int target = ChapterGrounds.shapeTop(ground, x, z, natural, sea);
        boolean wall = shape == Shape.COVE && target == Integer.MIN_VALUE;
        if (target == Integer.MIN_VALUE && !wall) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        switch (shape) {
            case HILL, PEAK -> {
                if (target <= natural) {
                    return;
                }
                clearAbove(x, z, natural + 1);
                for (int y = natural + 1; y <= target; y++) {
                    p.setY(y);
                    set(p.immutable(), raised(y, target));
                }
                if (shape == Shape.PEAK && target >= ChapterGrounds.PEAK_Y + 6) {
                    p.setY(target + 1);
                    set(p.immutable(), Blocks.SNOW.defaultBlockState());
                }
            }
            case BASIN -> {
                clearAbove(x, z, natural + 1);
                // 削る (上を空気に。水も抜く)
                for (int y = natural; y > target; y--) {
                    p.setY(y);
                    set(p.immutable(), Blocks.AIR.defaultBlockState());
                }
                for (int y = natural + 1; y <= target; y++) {
                    p.setY(y);
                    set(p.immutable(), y >= target - 2 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
                // 低い所の水 (池) は埋める
                for (int y = target + 1; y <= target + 6; y++) {
                    p.setY(y);
                    if (!level.getBlockState(p).getFluidState().isEmpty()) {
                        set(p.immutable(), Blocks.AIR.defaultBlockState());
                    }
                }
                // 灰 (粉のコンクリート) と砂利は落ちるブロック。その下が洞窟の空気・水だと列ごと 1 段落ちて床にくぼみが残った
                // (2026-10-24 の build audit の FLOOR_DIMPLE)。砂利の下を石で支える
                p.setY(target - 2);
                BlockState under = level.getBlockState(p);
                if (under.isAir() || under.canBeReplaced() || !under.getFluidState().isEmpty() || under.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
                    set(p.immutable(), Blocks.STONE.defaultBlockState());
                }
                p.setY(target);
                set(p.immutable(), ash());
                p.setY(target - 1);
                set(p.immutable(), random.nextInt(3) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
            }
            case COVE -> cove(x, z, natural, target, sea, wall);
            default -> {
            }
        }
    }

    private void cove(int x, int z, int natural, int target, int sea, boolean wall) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        int floor = sea - ChapterGrounds.COVE_FLOOR;
        if (wall) {
            // 形の外の 1 列: 水が抜けないよう、底から海面の下までの穴 (空気・水) を石で閉じる
            for (int y = floor - 3; y < sea; y++) {
                p.setY(y);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !s.getFluidState().isEmpty()) {
                    set(p.immutable(), Blocks.STONE.defaultBlockState());
                }
            }
            return;
        }
        if (target < sea) {
            // 水の所: 上の物を片づけ、海面の下まで水、底は砂・砂利・粘土、その下 3 は石で閉じる
            int top = Math.max(natural, sea + 1);
            clearAbove(x, z, top + 1);
            for (int y = top; y >= sea; y--) {
                p.setY(y);
                set(p.immutable(), Blocks.AIR.defaultBlockState());
            }
            for (int y = sea - 1; y > target; y--) {
                p.setY(y);
                set(p.immutable(), Blocks.WATER.defaultBlockState());
            }
            p.setY(target);
            int r = random.nextInt(10);
            set(p.immutable(), r < 6 ? Blocks.SAND.defaultBlockState() : r < 9 ? Blocks.GRAVEL.defaultBlockState() : Blocks.CLAY.defaultBlockState());
            for (int y = target - 1; y >= target - 3; y--) {
                p.setY(y);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) {
                    set(p.immutable(), Blocks.STONE.defaultBlockState());
                }
            }
            return;
        }
        // 浜: 海面の高さの砂 (高い地面は削らない。低い所・水は砂で埋める)。下は底まで石で閉じる
        for (int y = floor - 3; y < sea; y++) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                set(p.immutable(), Blocks.STONE.defaultBlockState());
            }
        }
        if (natural <= target) {
            clearAbove(x, z, natural + 1);
            for (int y = Math.min(natural + 1, target); y <= target; y++) {
                p.setY(y);
                set(p.immutable(), Blocks.SAND.defaultBlockState());
            }
            p.setY(target - 1);
            set(p.immutable(), Blocks.SANDSTONE.defaultBlockState());
        }
    }

    // 盛った列のブロック (y の高さ、top = その列の上面)
    private BlockState raised(int y, int top) {
        if (shape == Shape.PEAK) {
            if (y == top && top >= ChapterGrounds.PEAK_Y + 6) {
                return Blocks.SNOW_BLOCK.defaultBlockState();
            }
            int band = Math.floorMod(y * 7 + (int) (random.nextInt(3)), 23);
            return band < 3 ? Blocks.ANDESITE.defaultBlockState() : band < 5 ? Blocks.TUFF.defaultBlockState()
                    : y == top && random.nextInt(6) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
        if (y == top) {
            return random.nextInt(14) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return top - y <= 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    // 灰の地の上面 (まだら)
    private BlockState ash() {
        int r = random.nextInt(20);
        if (r < 7) {
            return Blocks.GRAY_CONCRETE_POWDER.defaultBlockState();
        }
        if (r < 11) {
            return Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState();
        }
        if (r < 15) {
            return Blocks.TUFF.defaultBlockState();
        }
        if (r < 18) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        return random.nextBoolean() ? Blocks.BASALT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }
}
