package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
    // 大きな構造物 (足跡・骨) 用: 地面に沿って掘る・盛る列 (世界の列 → relief の行)、木と草を片付ける列
    private Map<Long, int[]> reliefAt;
    private LongOpenHashSet touched;
    // ANALYZE を何 tick かに分ける
    private boolean anaInit;
    private int anaX;
    private List<Integer> anaHs;

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
                    case ANALYZE -> analyze(deadline);
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
                if (phase == Phase.RELOCATE && relocSearch != null) {
                    break; // 別のスレッドの場所探しを待つ間は tick を譲る
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
    // 大きな範囲 (骨は 100 x 50) でも tick を使い切らないよう、列 (x) ごとに進めて、予算が尽きたら次の tick へ
    private void analyze(long deadline) {
        int x0 = rect[0];
        int z0 = rect[1];
        int x1 = rect[2];
        int z1 = rect[3];
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        int[] inner = bp.worldRect(rot, mirror, new BlockPos(origin.getX(), 0, origin.getZ()));
        if (!anaInit) {
            groundCache = new int[w * d];
            anaHs = new ArrayList<>();
            anaX = x0;
            anaInit = true;
        }
        boolean skipCheck = entry.started;
        // 人の建物があるかは、地面の少し上まで見れば足りる (高い所は自然の木)
        int height = Math.min(Math.max(bp.maxY, 3) + 3, 16);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (anaX <= x1) {
            int x = anaX;
            for (int z = z0; z <= z1; z++) {
                int g = ground(x, z);
                groundCache[(x - x0) + (z - z0) * w] = g;
                boolean in = x >= inner[0] && x <= inner[2] && z >= inner[1] && z <= inner[3];
                if (g == WET) {
                    if (in && !skipCheck) {
                        SkyAbyss.LOGGER.info("Opening site {}: water at {} {}", label, x, z);
                        conflictFound();
                        return;
                    }
                    groundCache[(x - x0) + (z - z0) * w] = origin.getY() - 1;
                    continue;
                }
                if (in) {
                    anaHs.add(g);
                }
                if (skipCheck) {
                    continue;
                }
                p.set(x, g, z);
                if (!naturalGround(level.getBlockState(p))) {
                    SkyAbyss.LOGGER.info("Opening site {}: {} at {} is not natural ground", label, level.getBlockState(p), p);
                    conflictFound();
                    return;
                }
                if (in) {
                    for (int y = g + 1; y <= g + height; y++) {
                        p.setY(y);
                        if (!naturalAbove(level.getBlockState(p))) {
                            SkyAbyss.LOGGER.info("Opening site {}: {} at {} is not natural", label, level.getBlockState(p), p);
                            conflictFound();
                            return;
                        }
                    }
                }
            }
            anaX++;
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
        anaInit = false;
        List<Integer> hs = anaHs;
        anaHs = null;
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
        buildSelective();
        entry.started = true;
        data.setDirty();
        colIndex = 0;
        cellIndex = 0;
        phase = Phase.TERRAIN;
    }

    private void conflictFound() {
        anaInit = false;
        anaHs = null;
        phase = Phase.RELOCATE;
    }

    // 掘る・盛る列 (relief) と、木・草を片付ける列 (relief の 1 まわり / 部品のブロックの clearNear まわり) を、世界の座標で作る
    private void buildSelective() {
        reliefAt = null;
        touched = null;
        if (bp.relief.isEmpty() && bp.clearNear <= 0) {
            return;
        }
        touched = new LongOpenHashSet();
        LongOpenHashSet base = new LongOpenHashSet();
        int reach;
        if (!bp.relief.isEmpty()) {
            reliefAt = new HashMap<>();
            for (int[] r : bp.relief) {
                BlockPos w = Blueprint.transform(r[0], 0, r[1], rot, mirror, origin);
                reliefAt.put(BlockPos.asLong(w.getX(), 0, w.getZ()), r);
                base.add(BlockPos.asLong(w.getX(), 0, w.getZ()));
            }
            reach = 1;
        } else {
            for (Blueprint.Cell c : bp.cells) {
                BlockPos w = Blueprint.transform(c.x(), 0, c.z(), rot, mirror, origin);
                base.add(BlockPos.asLong(w.getX(), 0, w.getZ()));
            }
            reach = bp.clearNear;
        }
        for (long k : base) {
            int bx = BlockPos.getX(k);
            int bz = BlockPos.getZ(k);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    touched.add(BlockPos.asLong(bx + dx, 0, bz + dz));
                }
            }
        }
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
        boolean selective = touched != null;
        while (colIndex < n) {
            int i = colIndex * 5;
            int x = cols[i];
            int z = cols[i + 1];
            int target = cols[i + 2];
            boolean in = cols[i + 3] == 1;
            int g = cols[i + 4];
            colIndex++;
            if (selective) {
                long key = BlockPos.asLong(x, 0, z);
                int[] rel = reliefAt == null ? null : reliefAt.get(key);
                if (rel != null) {
                    clearAbove(p, x, z, g + 1, 22, true);
                    applyRelief(p, x, z, g, rel);
                } else if (touched.contains(key)) {
                    clearAbove(p, x, z, g + 1, reliefAt != null ? 22 : clear, in);
                }
            } else if (!(bp.level && (target != g || in)) && !in) {
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

    // ---------------------------------------------------------------- 地面に沿って掘る・盛る (大きな足跡)。g はその列の地面の高さ (上面)
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private void applyRelief(BlockPos.MutableBlockPos p, int x, int z, int g, int[] r) {
        int a = r[3];
        switch (r[2]) {
            case 0 -> {
                for (int y = g; y > g - a; y--) {
                    if (!level.getBlockState(p.set(x, y, z)).isAir()) {
                        set(p, AIR);
                    }
                }
                set(p.set(x, g - a, z), bp.palette.get(r[4]));
                placedCount++;
                if (r[5] == 1 && waterOk(x, z, g, a)) {
                    set(p.set(x, g - a + 1, z), Blocks.WATER.defaultBlockState());
                }
            }
            case 1 -> {
                BlockState top = bp.palette.get(r[4]);
                BlockState fill = bp.palette.get(r[5]);
                for (int k = 1; k <= a; k++) {
                    set(p.set(x, g + k, z), k == a ? top : fill);
                    placedCount++;
                }
            }
            default -> {
                BlockState st = Blueprint.transform(bp.palette.get(r[4]), rot, mirror);
                for (int k = 1; k <= a; k++) {
                    set(p.set(x, g + k, z), st);
                    placedCount++;
                }
            }
        }
    }

    // 雨水を張ってよいか: 四方が水面 (底の一つ上) より高い壁か、同じ底の高さの雨水の列 (水が横へこぼれない)。Python の Grid.pool_ok と同じ規則
    private boolean waterOk(int x, int z, int g, int a) {
        int yw = g - a + 1;
        int w = rect[2] - rect[0] + 1;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dd : dirs) {
            int nx = x + dd[0];
            int nz = z + dd[1];
            int ng = groundCache[(nx - rect[0]) + (nz - rect[1]) * w];
            int[] nr = reliefAt.get(BlockPos.asLong(nx, 0, nz));
            int top = ng;
            if (nr != null) {
                top = nr[2] == 0 ? ng - nr[3] : ng + nr[3];
            }
            if (top >= yw) {
                continue;
            }
            if (nr != null && nr[2] == 0 && nr[3] == a && ng - nr[3] == g - a && nr[5] == 1) {
                continue;
            }
            return false;
        }
        return true;
    }

    // その列の、いちばん上の固い地面の一つ上 (歩いて立つ場所)。from から下へ探す
    public BlockPos standAt(int x, int z, int from) {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos(x, from, z);
        for (int y = from; y > level.getMinBuildHeight(); y--) {
            q.setY(y);
            BlockState st = level.getBlockState(q);
            if (!st.getCollisionShape(level, q).isEmpty() || !st.getFluidState().isEmpty()) {
                return new BlockPos(x, y + 1, z);
            }
        }
        return new BlockPos(x, from, z);
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
    // 探すのは重い (地形生成の高さを何十回も問い合わせる) ので、別のスレッドでやって、できるまで tick を譲る (自己テストの blocking はその場で)。
    // 輪ごとに 12 方向 (輪の幅 = 部品の半分の大きさの 0.6 倍、最小 14) を調べ、4 つの輪まで。ほかの構造物と重ならない
    private java.util.concurrent.CompletableFuture<BlockPos> relocSearch;
    private BlockPos home;

    private static int otherRadius(String kind) {
        return switch (kind) {
            case "camp" -> 12;
            case "shelter" -> 16;
            case "footprint" -> SiteFinder.FOOT_HALF_Z;
            case "ribs" -> SiteFinder.RIB_HALF_LONG;
            default -> 3;
        };
    }

    private void relocate() {
        if (relocSearch != null) {
            if (!relocSearch.isDone()) {
                return;
            }
            BlockPos found = relocSearch.join();
            relocSearch = null;
            applyRelocation(found);
            return;
        }
        releaseTickets();
        relocations++;
        if (home == null) {
            home = entry.pos;
        }
        if (relocations > 4) {
            SkyAbyss.LOGGER.warn("Opening site {} at {}: gave up after {} moves (the land is built on or wet)", label, home, relocations - 1);
            data.entries().remove(entry);
            data.setDirty();
            phase = Phase.DONE;
            return;
        }
        int[] wr = bp.worldRect(rot, mirror, new BlockPos(home.getX(), 0, home.getZ()));
        int hx = (wr[2] - wr[0]) / 2 + 1;
        int hz = (wr[3] - wr[1]) / 2 + 1;
        int step = Math.max(14, (int) (Math.max(hx, hz) * 0.6));
        int ring = relocations;
        int range = Math.max(hx, hz) >= 16 ? 40 : 8;
        BlockPos base = home;
        List<BlockPos> others = new ArrayList<>();
        List<Integer> radii = new ArrayList<>();
        for (SiteData.Entry o : data.entries()) {
            if (o != entry) {
                others.add(o.pos);
                radii.add(otherRadius(o.kind));
            }
        }
        int mine = Math.max(hx, hz);
        int k = relocations;
        SiteFinder.Probe probe = new SiteFinder.Probe(level);
        java.util.function.Supplier<BlockPos> search = () -> {
            for (int attempt = 0; attempt < 12; attempt++) {
                double ang = Math.PI * 2 * (attempt + k * 0.37) / 12;
                int x = base.getX() + (int) Math.round(Math.cos(ang) * step * ring);
                int z = base.getZ() + (int) Math.round(Math.sin(ang) * step * ring);
                boolean clear = true;
                for (int i = 0; i < others.size(); i++) {
                    if (Math.hypot(x - others.get(i).getX(), z - others.get(i).getZ()) < mine + radii.get(i) + 6) {
                        clear = false;
                        break;
                    }
                }
                if (!clear) {
                    continue;
                }
                SiteFinder.Area ar = SiteFinder.area(probe, x, z, hx, hz, range);
                if (ar.ok()) {
                    return new BlockPos(x, ar.median() + 1, z);
                }
            }
            return null;
        };
        if (blocking) {
            applyRelocation(search.get());
        } else {
            relocSearch = java.util.concurrent.CompletableFuture.supplyAsync(search, net.minecraft.Util.backgroundExecutor());
        }
    }

    private void applyRelocation(@Nullable BlockPos found) {
        if (found == null) {
            phase = Phase.RELOCATE; // 次の輪へ
            return;
        }
        SkyAbyss.LOGGER.info("Opening site {} moved from {} to {} {} (something was built or wet there)", label, entry.pos, found.getX(), found.getZ());
        entry.pos = found;
        origin = entry.pos;
        entry.started = false;
        data.setDirty();
        phase = Phase.LOAD;
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
