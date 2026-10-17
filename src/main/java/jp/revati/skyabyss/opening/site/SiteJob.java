package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// 構造物を一つ建てる仕事 (サーバーを長く止めないよう、何 tick かに分けて進める)。BackBuildJobs と同じく、1 tick の予算 (ms) だけ進める。
//   LOAD    : 範囲のチャンクを読み込ませる (チケットを付けて待つ。ここでは重い処理をしない)
//   ANALYZE : 実際のブロックで地面の高さを測り、人の建物・水がないか確かめる (あれば場所をずらす = RELOCATE)。高さを決める
//   TERRAIN : 範囲を平らにならし (低い所は土を盛り、高い所は削り)、木・草を片付ける。まわりの 3 ブロックはなだらかにつなぐ
//   PLACE   : 部品のブロックを置く (低い段から)
//   FINISH  : 柵・壁・階段のつながり、看板、樽の中身、帳面など。印の位置を SiteData に書いて、建て終わりにする
// 置くときは近所の更新を起こさない (フラグ 2|16)。つながりは FINISH で計算する
public final class SiteJob {
    public static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_opening_site", Comparator.comparingLong(ChunkPos::toLong));
    public static final int FLAGS = 2 | 16;
    private static final int WET = Integer.MIN_VALUE;

    public enum Phase {
        LOAD, ANALYZE, TERRAIN, PLACE, FINISH, DONE, RELOCATE
    }

    // 種類ごとの仕上げ (CampBuilder など)
    public interface Post {
        void finish(SiteJob job);
    }

    public final ServerLevel level;
    public final SiteData data;
    public final SiteData.Entry entry;
    public final Blueprint bp;
    private final Post post;
    private final String label;

    public Phase phase = Phase.LOAD;
    public int rot;
    public boolean mirror;
    // 部品の原点 (x, z は場所の中心。y は地面の上面 H)
    public BlockPos origin;
    public int groundH;
    private int[] rect; // 世界の座標: x0, z0, x1, z1
    private final List<ChunkPos> tickets = new ArrayList<>();
    private int waited;
    private int relocations;
    // 地形の作業 (列ごと)
    private int[] cols; // 列の x, z, 目標の高さ、範囲の内側か (4 つずつ)
    private int colIndex;
    private int cellIndex;
    private BlockState topState = Blocks.GRASS_BLOCK.defaultBlockState();
    private BlockState fillState = Blocks.DIRT.defaultBlockState();
    private final List<BlockPos> placed = new ArrayList<>();
    public int placedCount;
    public int ticks;
    public long workNanos;
    private int[] groundCache; // 範囲 + 輪の列の地面の高さ
    // true なら、チャンクを (tick を待たずに) その場で読み込む。開発コマンドと自己テスト用
    public boolean blocking;

    public SiteJob(ServerLevel level, SiteData data, SiteData.Entry entry, Blueprint bp, String label, Post post) {
        this.level = level;
        this.data = data;
        this.entry = entry;
        this.bp = bp;
        this.label = label;
        this.post = post;
        this.rot = SiteFinder.rot(entry.yaw);
        this.mirror = SiteFinder.mirror(entry.yaw);
        this.origin = entry.pos;
    }

    public String label() {
        return label;
    }

    public boolean done() {
        return phase == Phase.DONE;
    }

    // deadline (System.nanoTime) まで進める。建て終わったら true
    public boolean step(long deadline) {
        long t0 = System.nanoTime();
        ticks++;
        try {
            while (phase != Phase.DONE && System.nanoTime() < deadline) {
                switch (phase) {
                    case LOAD -> load();
                    case ANALYZE -> analyze();
                    case TERRAIN -> terrain(deadline);
                    case PLACE -> place(deadline);
                    case FINISH -> finish();
                    case RELOCATE -> relocate();
                    default -> {
                    }
                }
                if (phase == Phase.LOAD && waited > 0) {
                    break; // チャンクを待つ間は tick を譲る
                }
            }
        } finally {
            workNanos += System.nanoTime() - t0;
        }
        return phase == Phase.DONE;
    }

    // ---------------------------------------------------------------- 範囲
    private int[] computeRect() {
        int[] r = bp.worldRect(rot, mirror, new BlockPos(origin.getX(), 0, origin.getZ()));
        int m = bp.ring + 1;
        return new int[] {r[0] - m, r[1] - m, r[2] + m, r[3] + m};
    }

    // ---------------------------------------------------------------- LOAD
    private void load() {
        if (rect == null) {
            rect = computeRect();
            for (int cx = rect[0] >> 4; cx <= rect[2] >> 4; cx++) {
                for (int cz = rect[1] >> 4; cz <= rect[3] >> 4; cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    level.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
                    tickets.add(cp);
                }
            }
        }
        for (ChunkPos cp : tickets) {
            if (blocking) {
                level.getChunk(cp.x, cp.z);
                continue;
            }
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                waited++;
                return;
            }
        }
        waited = 0;
        phase = Phase.ANALYZE;
    }

    private void releaseTickets() {
        for (ChunkPos cp : tickets) {
            level.getChunkSource().removeRegionTicket(TICKET, cp, 2, cp);
        }
        tickets.clear();
        rect = null;
    }

    // ---------------------------------------------------------------- 地面
    // 実際のブロックで、地面 (木・草・葉を除いた、いちばん上の固いブロック) の高さ。水なら WET
    public int ground(int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (; y >= level.getMinBuildHeight(); y--) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) {
                return WET;
            }
            if (solidGround(s, p)) {
                return y;
            }
        }
        return level.getMinBuildHeight();
    }

    private boolean solidGround(BlockState s, BlockPos p) {
        if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || isPlant(s)) {
            return false;
        }
        return !s.getCollisionShape(level, p).isEmpty();
    }

    private static boolean isPlant(BlockState s) {
        return s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO) || s.is(Blocks.BAMBOO_SAPLING) || s.is(Blocks.PUMPKIN)
                || s.is(Blocks.MELON) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.MOSS_CARPET)
                || s.is(Blocks.BIG_DRIPLEAF) || s.is(Blocks.SMALL_DRIPLEAF) || s.is(Blocks.COCOA) || s.is(Blocks.VINE) || s.is(Blocks.BEE_NEST)
                || s.is(Blocks.AZALEA) || s.is(Blocks.FLOWERING_AZALEA);
    }

    // 自然の地面か (人が置いたのではない)
    private static boolean naturalGround(BlockState s) {
        return s.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.STONE_ORE_REPLACEABLES)
                || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.PACKED_MUD) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.CALCITE)
                || s.is(Blocks.TUFF) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE)
                || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.BEDROCK) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.DIRT_PATH);
    }

    // 地面の上に自然にある物か (空気・草花・雪・葉・木・つる・実)
    private static boolean naturalAbove(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || isPlant(s) || s.is(BlockTags.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.COBWEB) || s.is(Blocks.MANGROVE_ROOTS)
                || s.is(Blocks.HANGING_ROOTS);
    }

    // ---------------------------------------------------------------- ANALYZE
    private void analyze() {
        int x0 = rect[0];
        int z0 = rect[1];
        int x1 = rect[2];
        int z1 = rect[3];
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        groundCache = new int[w * d];
        int[] inner = bp.worldRect(rot, mirror, new BlockPos(origin.getX(), 0, origin.getZ()));
        List<Integer> hs = new ArrayList<>();
        boolean conflict = false;
        boolean skipCheck = entry.started;
        int height = Math.max(bp.maxY, 3) + 3;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1 && !conflict; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = ground(x, z);
                groundCache[(x - x0) + (z - z0) * w] = g;
                boolean in = x >= inner[0] && x <= inner[2] && z >= inner[1] && z <= inner[3];
                if (g == WET) {
                    if (in && !skipCheck) {
                        SkyAbyss.LOGGER.info("Opening site {}: water at {} {}", label, x, z);
                        conflict = true;
                        break;
                    }
                    groundCache[(x - x0) + (z - z0) * w] = origin.getY() - 1;
                    continue;
                }
                if (in) {
                    hs.add(g);
                }
                if (skipCheck) {
                    continue;
                }
                p.set(x, g, z);
                if (!naturalGround(level.getBlockState(p))) {
                    SkyAbyss.LOGGER.info("Opening site {}: {} at {} is not natural ground", label, level.getBlockState(p), p);
                    conflict = true;
                    break;
                }
                if (in) {
                    for (int y = g + 1; y <= g + height; y++) {
                        p.setY(y);
                        if (!naturalAbove(level.getBlockState(p))) {
                            SkyAbyss.LOGGER.info("Opening site {}: {} at {} is not natural", label, level.getBlockState(p), p);
                            conflict = true;
                            break;
                        }
                    }
                    if (conflict) {
                        break;
                    }
                }
            }
        }
        if (conflict) {
            phase = Phase.RELOCATE;
            return;
        }
        if (hs.isEmpty()) {
            phase = Phase.RELOCATE;
            return;
        }
        hs.sort(Comparator.naturalOrder());
        groundH = bp.anchor ? hs.get(hs.size() / 4) : hs.get(hs.size() / 2);
        origin = new BlockPos(origin.getX(), groundH, origin.getZ());
        // ならしたあとの上面のブロック: 範囲の中央の地面 (草なら草、砂なら砂、雪や石ならそれ)
        BlockPos cp = new BlockPos(origin.getX(), 0, origin.getZ());
        int cg = ground(cp.getX(), cp.getZ());
        if (cg != WET) {
            BlockState top = level.getBlockState(new BlockPos(cp.getX(), cg, cp.getZ()));
            if (top.is(BlockTags.SAND) || top.is(BlockTags.DIRT) || top.is(Blocks.GRAVEL) || top.is(BlockTags.TERRACOTTA)) {
                topState = top.is(Blocks.DIRT_PATH) ? Blocks.GRASS_BLOCK.defaultBlockState() : top;
                fillState = top.is(BlockTags.SAND) ? top : top.is(BlockTags.TERRACOTTA) ? top : Blocks.DIRT.defaultBlockState();
            }
        }
        buildColumns(inner);
        entry.started = true;
        data.setDirty();
        colIndex = 0;
        cellIndex = 0;
        phase = Phase.TERRAIN;
    }

    // 地形を直す列 (x, z, 目標の高さ, 内側か) を決める。内側 = 部品の範囲、外側 = 輪 (なだらかにつなぐ)
    private void buildColumns(int[] inner) {
        int x0 = rect[0];
        int z0 = rect[1];
        int w = rect[2] - x0 + 1;
        List<int[]> list = new ArrayList<>();
        for (int x = rect[0]; x <= rect[2]; x++) {
            for (int z = rect[1]; z <= rect[3]; z++) {
                int g = groundCache[(x - x0) + (z - z0) * w];
                boolean in = x >= inner[0] && x <= inner[2] && z >= inner[1] && z <= inner[3];
                int dist = Math.max(Math.max(inner[0] - x, x - inner[2]), Math.max(inner[1] - z, z - inner[3]));
                int target;
                if (in) {
                    target = bp.level ? groundH : g;
                } else if (bp.level && bp.ring > 0 && dist <= bp.ring) {
                    double t = dist / (double) (bp.ring + 1);
                    target = (int) Math.round(groundH + (g - groundH) * t);
                } else {
                    target = g;
                }
                list.add(new int[] {x, z, target, in ? 1 : 0, g});
            }
        }
        cols = new int[list.size() * 5];
        for (int i = 0; i < list.size(); i++) {
            System.arraycopy(list.get(i), 0, cols, i * 5, 5);
        }
    }

    // ---------------------------------------------------------------- TERRAIN
    private void terrain(long deadline) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = cols.length / 5;
        int clear = Math.max(bp.maxY, 4) + 4;
        while (colIndex < n) {
            int i = colIndex * 5;
            int x = cols[i];
            int z = cols[i + 1];
            int target = cols[i + 2];
            boolean in = cols[i + 3] == 1;
            int g = cols[i + 4];
            colIndex++;
            if (!(bp.level && (target != g || in)) && !in) {
                // 変えない列 (輪の外、または変えない部品)。木だけ片付ける
                clearAbove(p, x, z, g + 1, clear, false);
            } else {
                if (target > g) {
                    for (int y = g + 1; y <= target; y++) {
                        set(p.set(x, y, z), y == target ? topState : fillState);
                    }
                } else if (target < g) {
                    for (int y = g; y > target; y--) {
                        set(p.set(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    set(p.set(x, target, z), topState);
                } else if (in && !level.getBlockState(p.set(x, g, z)).equals(topState) && bp.level) {
                    // 同じ高さでも上面を揃える (部品の下は同じ地面に)
                    set(p.set(x, g, z), topState);
                }
                clearAbove(p, x, z, Math.max(target, g) + 1, clear, true);
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
        phase = Phase.PLACE;
        cellIndex = 0;
    }

    private void clearAbove(BlockPos.MutableBlockPos p, int x, int z, int from, int height, boolean inside) {
        // 上面の少し上から、片付ける高さまで。自然の物 (草・葉・木・雪) だけ
        for (int y = from; y <= from + height + (inside ? 4 : 10); y++) {
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            if (naturalAbove(s) || (inside && s.is(Blocks.WATER))) {
                set(p, Blocks.AIR.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------- PLACE
    private void place(long deadline) {
        List<Blueprint.Cell> cells = bp.cells;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (cellIndex < cells.size()) {
            Blueprint.Cell c = cells.get(cellIndex++);
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rot, mirror, origin);
            BlockState state = Blueprint.transform(c.state(), rot, mirror);
            if (!state.isAir() || !level.getBlockState(at).isAir()) {
                set(at, state);
                if (!state.isAir()) {
                    placed.add(at.immutable());
                    placedCount++;
                }
            }
            // 柱の根もとは地面まで延ばす (骨の脚)
            if (bp.anchor && c.y() == 0 && !state.isAir()) {
                for (int dy = 1; dy <= 14; dy++) {
                    p.set(at.getX(), at.getY() - dy, at.getZ());
                    BlockState below = level.getBlockState(p);
                    if (below.isAir() || below.canBeReplaced() || naturalAbove(below) || !below.getFluidState().isEmpty()) {
                        set(p, state);
                    } else {
                        break;
                    }
                }
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
        phase = Phase.FINISH;
    }

    // ---------------------------------------------------------------- FINISH
    private void finish() {
        // つながる物 (柵・板ガラス・石垣・階段) の形を、まわりに合わせる
        for (BlockPos at : placed) {
            BlockState s = level.getBlockState(at);
            Block b = s.getBlock();
            if (b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock) {
                BlockState u = Block.updateFromNeighbourShapes(s, level, at);
                if (u != s) {
                    level.setBlock(at, u, FLAGS);
                }
            }
        }
        post.finish(this);
        // 場所の高さを実際の地面に合わせる (立つ場所 = 上面 + 1)
        entry.pos = new BlockPos(origin.getX(), origin.getY() + 1, origin.getZ());
        entry.built = true;
        data.setDirty();
        releaseTickets();
        phase = Phase.DONE;
    }

    // ---------------------------------------------------------------- RELOCATE: 人の建物・水があった。少しずらして調べ直す
    private void relocate() {
        releaseTickets();
        relocations++;
        if (relocations > 14) {
            SkyAbyss.LOGGER.warn("Opening site {} at {}: gave up after {} moves (the land is built on or wet)", label, entry.pos, relocations - 1);
            data.entries().remove(entry);
            data.setDirty();
            phase = Phase.DONE;
            return;
        }
        SiteFinder.Probe probe = new SiteFinder.Probe(level);
        int ring = 1 + (relocations - 1) / 4;
        int baseX = entry.pos.getX();
        int baseZ = entry.pos.getZ();
        // 8 方向に 12 ブロックずつ広げる
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = Math.PI * 2 * (attempt + relocations * 0.37) / 8;
            int x = baseX + (int) Math.round(Math.cos(a) * 12 * ring);
            int z = baseZ + (int) Math.round(Math.sin(a) * 12 * ring);
            int half = Math.max(bp.rx1 - bp.rx0, bp.rz1 - bp.rz0) / 2 + 1;
            SiteFinder.Area ar = SiteFinder.area(probe, x, z, half, half, 8);
            if (ar.ok()) {
                SkyAbyss.LOGGER.info("Opening site {} moved from {} to {} {} (something was built or wet there)", label, entry.pos, x, z);
                entry.pos = new BlockPos(x, ar.median() + 1, z);
                origin = entry.pos;
                entry.started = false;
                data.setDirty();
                phase = Phase.LOAD;
                return;
            }
        }
        // この輪では見つからなかった。次の輪へ
        entry.pos = new BlockPos(baseX + 12 * ring, entry.pos.getY(), baseZ);
        origin = entry.pos;
        phase = Phase.RELOCATE;
    }

    // ---------------------------------------------------------------- 道具
    public void set(BlockPos at, BlockState state) {
        level.setBlock(at, state, FLAGS);
    }

    // 部品の印 (名前) の、世界の座標 (部品の座標の y は、構造物の最初の段 = 地面の上面 + 1 が 0)
    @Nullable
    public BlockPos mark(String name) {
        int[] m = bp.marks.get(name);
        if (m == null) {
            return null;
        }
        return Blueprint.transform(m[0], m[1] + 1, m[2], rot, mirror, origin);
    }

    // 小さな部品を、その場の地面の高さに置く (骨の石板)。印 "sign" の世界の座標を返す
    @Nullable
    public BlockPos placeSmall(Blueprint small, int x, int z, int rotation) {
        int g = ground(x, z);
        if (g == WET) {
            g = origin.getY();
        }
        BlockPos o = new BlockPos(x, g, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Blueprint.Cell c : small.cells) {
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rotation, false, o);
            clearAbove(p, at.getX(), at.getZ(), g + 1, 5, true);
        }
        for (Blueprint.Cell c : small.cells) {
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rotation, false, o);
            set(at, Blueprint.transform(c.state(), rotation, false));
        }
        int[] m = small.marks.get("sign");
        return m == null ? null : Blueprint.transform(m[0], m[1] + 1, m[2], rotation, false, o);
    }

    public int[] debugRect() {
        return rect == null ? null : Arrays.copyOf(rect, 4);
    }
}
