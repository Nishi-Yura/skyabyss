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
    // wetOk の部品で、水だった列 (触らない)
    private LongOpenHashSet wetCols;
    // 石の台 (pillar): 水の上に建てる小さな部品 (道しるべの石標、動かし尽くした章の小要素)。範囲の内の水の列は水底から石を積み、
    // 輪の水の列は触らない。台の上面は水面の一つ上
    private boolean pillar;
    private LongOpenHashSet pillarCols;
    // 石の台にしてよい部品の大きさ (範囲の一辺)
    static final int PILLAR_MAX_SIDE = 9;
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
    // 水の列の、水の下の地面の高さ (fillWater の整地の起点)
    private int seabed(int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        for (int y = top; y > level.getMinBuildHeight(); y--) {
            p.setY(y);
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
            if (!st.isAir() && st.getFluidState().isEmpty()) {
                return y;
            }
        }
        return top;
    }

    // 水の列の、いちばん上の水のブロックの高さ
    private int waterTop(int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1; y > level.getMinBuildHeight(); y--) {
            p.setY(y);
            if (!level.getBlockState(p).getFluidState().isEmpty()) {
                return y;
            }
        }
        return level.getSeaLevel() - 1;
    }

    // 水の上で動かさずに石の台を立てる部品か (道しるべの石標)
    private boolean pillarFirst() {
        return ChapterGrounds.ROAD_BLUEPRINT.equals(entry.blueprint) && smallEnough();
    }

    private boolean smallEnough() {
        int[] r = bp.worldRect(rot, mirror, BlockPos.ZERO);
        return r[2] - r[0] + 1 <= PILLAR_MAX_SIDE && r[3] - r[1] + 1 <= PILLAR_MAX_SIDE;
    }

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
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.BEDROCK) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.DIRT_PATH)
                // 章の跡地の灰の盆地の上面 (ShapeJob。灰のまだら)
                || s.is(Blocks.GRAY_CONCRETE_POWDER) || s.is(Blocks.LIGHT_GRAY_CONCRETE_POWDER) || s.is(Blocks.BASALT);
    }

    // 地面の上に自然にある物か (空気・草花・雪・葉・木・つる・実)
    private static boolean naturalAbove(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || isPlant(s) || s.is(BlockTags.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.COBWEB) || s.is(Blocks.MANGROVE_ROOTS)
                || s.is(Blocks.HANGING_ROOTS) || s.is(Blocks.MOSSY_COBBLESTONE);
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
            wetCols = new LongOpenHashSet();
            pillarCols = new LongOpenHashSet();
            anaHs = new ArrayList<>();
            anaX = x0;
            anaInit = true;
        }
        // 都 (fillWater) は世界の始まりに一度だけ置く大きな部品なので、人の建物・水の検査で場所を動かさない (整地で吸収する)
        boolean skipCheck = entry.started || force || bp.fillWater;
        // 人の建物があるかは、地面の少し上まで見れば足りる (高い所は自然の木)
        int height = Math.min(Math.max(bp.maxY, 3) + 3, 16);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (anaX <= x1) {
            int x = anaX;
            for (int z = z0; z <= z1; z++) {
                int g = ground(x, z);
                groundCache[(x - x0) + (z - z0) * w] = g;
                boolean in = x >= inner[0] && x <= inner[2] && z >= inner[1] && z <= inner[3];
                if (g == WET && bp.wetOk) {
                    wetCols.add(BlockPos.asLong(x, 0, z));
                    groundCache[(x - x0) + (z - z0) * w] = origin.getY() - 1;
                    continue;
                }
                if (g == WET && !pillar && in && !skipCheck && pillarFirst()) {
                    // 道しるべの石標は、水の上なら動かさずにその場へ石の台を立てる (道の線から外れないように)
                    pillar = true;
                    anaInit = false;
                    anaHs = null;
                    SkyAbyss.LOGGER.info("Opening site {}: water at {} {}; standing it on a stone pillar", label, x, z);
                    return;
                }
                if (g == WET && pillar) {
                    long key = BlockPos.asLong(x, 0, z);
                    pillarCols.add(key);
                    groundCache[(x - x0) + (z - z0) * w] = seabed(x, z);
                    if (in) {
                        anaHs.add(waterTop(x, z) + 1);
                    }
                    continue;
                }
                if (g == WET && bp.fillWater) {
                    // 都: 池や川は土で埋めて整える (水の底までは見ない。目標の高さまで土を盛る)
                    groundCache[(x - x0) + (z - z0) * w] = Math.min(origin.getY() - 1, seabed(x, z));
                    continue;
                }
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
        // 地下の部屋が地面の中に収まるか (斜面で横から顔を出さない)。収まらなければ次の場所へ
        if (!bp.level && !force && !entry.siteId.isEmpty() && !bp.deepRoofs.isEmpty() && !roofsCovered()) {
            SkyAbyss.LOGGER.info("Opening site {} at {}: underground rooms would break the surface; moving", label, origin);
            conflictFound();
            return;
        }
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

    // どの列でも、自然の地面が地下の部屋の天井より 2 段以上上にあるか (水の列は不可)
    private boolean roofsCovered() {
        for (java.util.Map.Entry<Long, Integer> e : bp.deepRoofs.entrySet()) {
            int lx = BlockPos.getX(e.getKey());
            int lz = BlockPos.getZ(e.getKey());
            BlockPos w = Blueprint.transform(lx, e.getValue() + 1, lz, rot, mirror, origin);
            int g = ground(w.getX(), w.getZ());
            if (g == WET || g < w.getY() + 2) {
                return false;
            }
        }
        return true;
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
            if (pillar && pillarCols.contains(BlockPos.asLong(x, 0, z))) {
                // 石の台: 内の列は水底から上面まで石 (上面は石レンガ)、輪の水の列は触らない
                if (in) {
                    for (int y = g + 1; y <= target; y++) {
                        set(p.set(x, y, z), y == target ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    }
                    clearAbove(p, x, z, target + 1, clear, true);
                }
            } else if (selective) {
                long key = BlockPos.asLong(x, 0, z);
                int[] rel = reliefAt == null ? null : reliefAt.get(key);
                if (wetCols != null && wetCols.contains(key)) {
                    // 水の列は触らない
                } else if (rel != null) {
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

    // 足跡の雨水を張った所 (finish で、横にこぼれる水を取り除く)
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet puddles = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    // 張った雨水のうち、横・下が空気 (隣の水が条件を満たさず張られなかった所) で流れ出すものを、止まるまで消す (水の条件は隣へ伝わらないため)
    private void trimLeakingPuddles() {
        if (puddles.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        boolean changed = true;
        int removed = 0;
        for (int guard = 0; changed && guard < 64; guard++) {
            changed = false;
            for (long l : puddles.toLongArray()) {
                BlockPos at = BlockPos.of(l);
                if (!level.getBlockState(at).is(Blocks.WATER)) {
                    continue;
                }
                for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH,
                        net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
                    BlockState n = level.getBlockState(q.set(at).move(d));
                    if (n.isAir() || (n.getFluidState().isEmpty() && n.canBeReplaced())) {
                        level.setBlock(at, AIR, FLAGS);
                        changed = true;
                        removed++;
                        break;
                    }
                }
            }
        }
        SkyAbyss.LOGGER.info("Opening site {}: puddle cells {}, trimmed {} that would leak sideways", label, puddles.size(), removed);
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
                    puddles.add(BlockPos.asLong(x, g - a + 1, z));
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
            // ならさない小さな部品 (積み石・柱) は、置く高さが 3x3 の中央値なので、低い列は下が空く。最下段の下の空きを土 (いちばん下は上面の土) で埋める
            // (建築の監査 SITE_FLOAT)。地下の部品 (部屋) と、足もとを延ばす骨・掘る足跡は対象外
            // ならす大きな部品 (都) も、床の段 (y=-1) の下が空いている所 (起伏の下・崖の縁) は、下の地面まで埋める
            boolean floorUnder = bp.level && bp.fillWater && c.y() == -1 && !state.isAir()
                    && !bp.deepColumns.contains(BlockPos.asLong(c.x(), 0, c.z()));
            if ((!bp.level && !bp.anchor && bp.relief.isEmpty() && bp.minY >= -2 && c.y() <= 0 && !state.isAir()) || floorUnder) {
                for (int dy = 1; dy <= (floorUnder ? 12 : 8); dy++) {
                    p.set(at.getX(), at.getY() - dy, at.getZ());
                    BlockState below = level.getBlockState(p);
                    if (below.isAir() || below.canBeReplaced() || naturalAbove(below)) {
                        set(p, dy == 1 ? topState : fillState);
                    } else {
                        break;
                    }
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

    // 掘った所の隣にあった自然の水 (浜・池の縁) が、掘った穴へ流れ出さないように、水の源の横・下の空気を足もとの石 (無ければ土) で埋める
    private void sealShoreWater() {
        if (rect == null) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int sealed = 0;
        for (int pass = 0; pass < 3; pass++) {
            int before = sealed;
            for (int x = rect[0] - 1; x <= rect[2] + 1; x++) {
                for (int z = rect[1] - 1; z <= rect[3] + 1; z++) {
                    for (int y = origin.getY() + 2; y >= origin.getY() - 10; y--) {
                        p.set(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (!s.getFluidState().isSource() || !s.is(Blocks.WATER)) {
                            continue;
                        }
                        for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH,
                                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
                            q.set(p).move(d);
                            BlockState n = level.getBlockState(q);
                            if (n.isAir() || (n.getFluidState().isEmpty() && n.canBeReplaced())) {
                                BlockState under = level.getBlockState(q.below());
                                level.setBlock(q, under.isSolidRender(level, q.below()) && !under.getBlock().hasDynamicShape() ? under : Blocks.DIRT.defaultBlockState(), FLAGS);
                                sealed++;
                            }
                        }
                    }
                }
            }
            if (sealed == before) {
                break;
            }
        }
        if (sealed > 0) {
            SkyAbyss.LOGGER.info("Opening site {}: sealed {} air cells beside natural water", label, sealed);
        }
    }

    // 建てたブロックのうち、まわり 26 マスに固いブロックが何も無い (地形の凹凸で取り残された) 宙に浮いた 1 個を消す。載っていた薄い飾り (カーペットなど) も一緒に消す
    private void pruneStrayBlocks() {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int removed = 0;
        int before;
        int pass = 0;
        do {
            before = removed;
            removed += pruneStrayPass(q);
        } while (removed > before && ++pass < 4);
        if (removed > 0) {
            SkyAbyss.LOGGER.info("Opening site {}: removed {} stray floating blocks", label, removed);
        }
    }

    private int pruneStrayPass(BlockPos.MutableBlockPos q) {
        int removed = 0;
        for (BlockPos at : placed) {
            BlockState s = level.getBlockState(at);
            if (s.getCollisionShape(level, at).isEmpty() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
                continue;
            }
            boolean alone = true;
            for (int dx = -1; dx <= 1 && alone; dx++) {
                for (int dy = -1; dy <= 1 && alone; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx | dy | dz) != 0 && !level.getBlockState(q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz)).getCollisionShape(level, q).isEmpty()) {
                            alone = false;
                            break;
                        }
                    }
                }
            }
            if (alone) {
                level.setBlock(at, AIR, FLAGS);
                BlockState up = level.getBlockState(at.above());
                if (up.getCollisionShape(level, at.above()).isEmpty() && !up.isAir() && up.getFluidState().isEmpty() && up.is(Blocks.MOSS_CARPET)) {
                    level.setBlock(at.above(), AIR, FLAGS);
                }
                removed++;
            }
        }
        return removed;
    }

    // ならした範囲の、支えを失ったつる (木や葉を片づけたあとに宙にぶら下がる) を消す。上から順に調べるので、ぶら下がった列は全部消える
    private void pruneOrphanVines() {
        if (rect == null) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + Math.max(bp.maxY, 3) + 26);
        for (int x = rect[0] - 1; x <= rect[2] + 1; x++) {
            for (int z = rect[1] - 1; z <= rect[3] + 1; z++) {
                for (int y = top; y >= origin.getY() - 2; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(Blocks.VINE) && !s.canSurvive(level, p)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
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
        trimLeakingPuddles();
        sealShoreWater();
        pruneOrphanVines();
        pruneStrayBlocks();
        settle();
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
    // 輪ごとに 12 方向 (輪の幅 = 部品の半分の大きさの半分、最小 14) を調べ、12 の輪まで。ほかの構造物と重ならず、骨は野営地から 205〜375、足跡は道から 30〜155 に収める
    private java.util.concurrent.CompletableFuture<BlockPos> relocSearch;
    private BlockPos home;
    // 動かし尽くしたので、元の場所に強行して建てる (足跡だけ)。conflict の確認を飛ばす
    private boolean force;

    static int radiusOf(SiteData.Entry e) {
        return e.radius > 0 ? e.radius : otherRadius(e.kind);
    }

    private static int otherRadius(String kind) {
        return switch (kind) {
            case "camp" -> 12;
            case "shelter" -> 110;
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
        // 大きな構造物 (足跡・骨) は 12 の輪まで。小さな物 (野営地・宿り・積み石) は 14 回まで (輪は 3 回ごとに外へ)
        int maxRings = bp.wetOk ? 12 : 14;
        if (force) {
            // 強行しても建てられなかった (水ばかり): ここで本当に諦める
            SkyAbyss.LOGGER.warn("Opening site {} at {}: no ground to build on even when forced", label, home);
            data.remove(entry);
            phase = Phase.DONE;
            return;
        }
        if (relocations > maxRings && entry.kind.equals("footprint") && home != null) {
            // 足跡は「ここに巨神が通った」という物語の目印で、足跡の頁 (prologue/footprints) の条件でもある。動かし尽くして消すより、
            // 元の場所で、人の建物や木があっても (水の列は避けて) 建てる。first_hour_log B-6 (同じ種でも足跡が 0〜1 個の世界があった)
            force = true;
            relocations = 0;
            SkyAbyss.LOGGER.warn("Opening site {} at {}: no free land after {} moves; building it in place anyway", label, home, maxRings);
            entry.pos = home;
            origin = home;
            entry.started = false;
            data.setDirty();
            phase = Phase.LOAD;
            return;
        }
        if (relocations > maxRings && !pillar && !entry.siteId.isEmpty() && smallEnough() && home != null) {
            // 章の小要素で小さな物は、動かし尽くしたら元の場所に石の台を立てて建てる (沼・海ばかりの世界でも諦めない)
            pillar = true;
            force = true;
            relocations = 0;
            SkyAbyss.LOGGER.warn("Opening site {} at {}: no dry land after {} moves; standing it on a stone pillar in place", label, home, maxRings);
            entry.pos = home;
            origin = home;
            entry.started = false;
            data.setDirty();
            phase = Phase.LOAD;
            return;
        }
        if (relocations > maxRings) {
            SkyAbyss.LOGGER.warn("Opening site {} at {}: gave up after {} moves (the land is built on or wet)", label, home, relocations - 1);
            data.remove(entry);
            phase = Phase.DONE;
            return;
        }
        int[] wr = bp.worldRect(rot, mirror, new BlockPos(home.getX(), 0, home.getZ()));
        int hx = (wr[2] - wr[0]) / 2 + 1;
        int hz = (wr[3] - wr[1]) / 2 + 1;
        int step = Math.max(14, Math.max(hx, hz) / 2);
        // 小さな物は輪をゆっくり広げる (3 回ごとに 1 つ外へ。道が大きく変わらない)
        int ring = bp.wetOk ? relocations : 1 + (relocations - 1) / 3;
        int range = Math.max(hx, hz) >= 16 ? 40 : 8;
        BlockPos base = home;
        List<BlockPos> others = new ArrayList<>();
        List<Integer> radii = new ArrayList<>();
        for (SiteData.Entry o : data.allEntries()) {
            if (o != entry) {
                others.add(o.pos);
                radii.add(radiusOf(o));
            }
        }
        int mine = Math.max(hx, hz);
        // 水がまじってよい部品は、乾いていてほしい範囲を半分に (池は水の列を残して建てる)
        int coreX = bp.wetOk ? hx / 2 : hx;
        int coreZ = bp.wetOk ? hz / 2 : hz;
        int k = relocations;
        SiteData.Entry campE = data.first("camp");
        SiteData.Entry shelterE = data.first("shelter");
        String kind = entry.kind;
        // 一章の骨は一章の跡地 (docs/design/33 の 3 章) の中に置くので、野営地からの距離ではなく、跡地の中心から 150 以内で動かす
        SiteData.Ground whaleGround = kind.equals("ribs") ? data.ground(jp.revati.skyabyss.story.Chapters.WHALE.id()) : null;
        // 一章の骨は、跡地が取っておいた岩鯨の祠の台座 (ChapterGrounds.shrineSpot) の上へ動かさない (祠は骨の脇。台座 9×9)
        if (whaleGround != null && whaleGround.shrine != null) {
            others.add(whaleGround.shrine);
            radii.add(5);
        }
        BlockPos campPos = campE == null || whaleGround != null ? null : campE.pos;
        BlockPos shelterPos = shelterE == null ? null : shelterE.pos;
        BlockPos anchor = whaleGround != null ? whaleGround.center : entry.anchor;
        int maxDist = whaleGround != null ? ChapterPlanner.MAX_DIST : entry.maxDist;
        List<BlockPos> otherPrints = new ArrayList<>();
        if (kind.equals("footprint")) {
            for (SiteData.Entry o : data.entries("footprint")) {
                if (o != entry && Math.abs(o.index - entry.index) == 1) {
                    otherPrints.add(o.pos);
                }
            }
        }
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
                if (!clear || !withinLimits(kind, x, z, campPos, shelterPos, otherPrints) || (anchor != null && Math.hypot(x - anchor.getX(), z - anchor.getZ()) > maxDist)) {
                    continue;
                }
                SiteFinder.Area ar = SiteFinder.area(probe, x, z, coreX, coreZ, range);
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

    // 動かしても守る距離 (骨は野営地から、足跡は道から)
    private static boolean withinLimits(String kind, int x, int z, @Nullable BlockPos camp, @Nullable BlockPos shelter, List<BlockPos> footprints) {
        if (camp == null) {
            return true;
        }
        if (kind.equals("ribs")) {
            double d = Math.hypot(x - camp.getX(), z - camp.getZ());
            if (d < 205 || d > 375) {
                return false;
            }
            // 宿りとは違う方角 (82 度以上離す)
            if (shelter != null) {
                double a1 = Math.atan2(z - camp.getZ(), x - camp.getX());
                double a2 = Math.atan2(shelter.getZ() - camp.getZ(), shelter.getX() - camp.getX());
                double diff = Math.abs(Math.toDegrees(a1 - a2));
                diff = diff > 180 ? 360 - diff : diff;
                return diff >= 82;
            }
            return true;
        }
        if (shelter != null && !kind.equals("shelter") && SiteFinder.capital()
                && Math.hypot(x - shelter.getX(), z - shelter.getZ()) < (kind.equals("footprint") ? SiteFinder.CAPITAL_FOOT_MIN : SiteFinder.CAPITAL_CAIRN_STOP)) {
            return false;
        }
        if (kind.equals("footprint") && shelter != null) {
            double dx = shelter.getX() - camp.getX();
            double dz = shelter.getZ() - camp.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double dp = SiteFinder.distToPath(x, z, camp, dx / len, dz / len, len);
            if (dp < 30 || dp > 155) {
                return false;
            }
            // 隣の足跡との一歩の間隔 (66〜135)
            for (BlockPos o : footprints) {
                double dd = Math.hypot(x - o.getX(), z - o.getZ());
                if (dd < 66 || dd > 135) {
                    return false;
                }
            }
            return true;
        }
        return true;
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

    // ---------------------------------------------------------------- 仕上げ (すべての部品。build audit の地形の決まり)
    // 地形の凹凸で残る浮き・くぼみ・漏れ・取り残しを、建て終わりにまとめて直す。部品ごとの仕上げ (Post) より前
    private void settle() {
        if (rect == null || origin == null) {
            return;
        }
        int a = supportBase();
        int b = fillDimples();
        int c = clearFloatingPlants(origin, Math.max(rect[2] - rect[0], rect[3] - rect[1]) / 2 + 3);
        int d = clearStand();
        int e = sealFluids();
        int f = pruneLoneNatural();
        if (a + b + c + d + e + f > 0) {
            SkyAbyss.LOGGER.info("Opening site {}: settle base {} dimples {} plants {} stand {} fluids {} lone {}", label, a, b, c, d, e, f);
        }
    }

    // 部品の世界の列 (空気でないセルの列)
    private LongOpenHashSet bpColumns() {
        LongOpenHashSet cols = new LongOpenHashSet();
        for (Blueprint.Cell c : bp.cells) {
            if (!c.state().isAir()) {
                BlockPos at = Blueprint.transform(c.x(), 0, c.z(), rot, mirror, origin);
                cols.add(BlockPos.asLong(at.getX(), 0, at.getZ()));
            }
        }
        return cols;
    }

    // 最下段 (部品の y <= 0) の下が空いている列を、下の地面まで埋める (build audit の SITE_FLOAT と同じ見方。地下の列・骨・足跡は除く)
    private int supportBase() {
        if (bp.anchor || !bp.relief.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> lowest = new HashMap<>();
        for (Blueprint.Cell c : bp.cells) {
            if (c.state().isAir() || c.y() > 0) {
                continue;
            }
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rot, mirror, origin);
            lowest.merge(BlockPos.asLong(at.getX(), 0, at.getZ()), at.getY(), Math::min);
        }
        LongOpenHashSet deep = new LongOpenHashSet();
        for (long dc : bp.deepColumns) {
            BlockPos at = Blueprint.transform(BlockPos.getX(dc), 0, BlockPos.getZ(dc), rot, mirror, origin);
            deep.add(BlockPos.asLong(at.getX(), 0, at.getZ()));
        }
        if (bp.minY < -2 && deep.size() * 2 >= lowest.size()) {
            return 0;
        }
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (var en : lowest.entrySet()) {
            long key = en.getKey();
            if (deep.contains(key)) {
                continue;
            }
            int x = BlockPos.getX(key);
            int z = BlockPos.getZ(key);
            for (int dy = 1; dy <= 8; dy++) {
                p.set(x, en.getValue() - dy, z);
                BlockState below = level.getBlockState(p);
                if (!below.getFluidState().isEmpty() || !below.getCollisionShape(level, p).isEmpty()) {
                    break;
                }
                level.setBlock(p, fillState, FLAGS);
                n++;
            }
        }
        return n;
    }

    // まわり 4 方向が同じ高さなのに、その 1 列だけ低いくぼみを埋める (build audit の FLOOR_DIMPLE)。
    // 部品の列と足跡の地形は 1 段だけ (わざとの穴・縦穴を埋めない)、外の地面は 4 段まで。くぼみの上が空気でない列 (はしご・松明) は触らない
    private int fillDimples() {
        LongOpenHashSet cols = bpColumns();
        int maxDeep = bp.relief.isEmpty() ? 4 : 1;
        int n = fillLocalDimples();
        for (int x = rect[0] - 3; x <= rect[2] + 3; x++) {
            for (int z = rect[1] - 3; z <= rect[3] + 3; z++) {
                int top = surfaceY(x, z);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int around = Integer.MIN_VALUE;
                boolean even = true;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int t = surfaceY(x + d[0], z + d[1]);
                    if (around == Integer.MIN_VALUE) {
                        around = t;
                    }
                    if (t == Integer.MIN_VALUE || t != around) {
                        even = false;
                        break;
                    }
                }
                int gap = around - top;
                if (!even || gap < 1 || gap > (cols.contains(BlockPos.asLong(x, 0, z)) ? 1 : maxDeep)) {
                    continue;
                }
                boolean clear = true;
                for (int y = top + 1; y <= around + 2 && clear; y++) {
                    BlockPos q = new BlockPos(x, y, z);
                    BlockState o = level.getBlockState(q);
                    clear = o.isAir() || (naturalAbove(o) && o.getFluidState().isEmpty() && o.getCollisionShape(level, q).isEmpty() && !o.is(Blocks.VINE));
                }
                if (!clear) {
                    continue;
                }
                BlockState skin = level.getBlockState(new BlockPos(x + 1, around, z));
                BlockState under = level.getBlockState(new BlockPos(x, top, z));
                for (int y = top + 1; y <= around; y++) {
                    level.setBlock(new BlockPos(x, y, z), y == around ? skin : under, FLAGS);
                }
                n++;
            }
        }
        return n;
    }

    // どの高さでも (骨のあばらの下など、上に別の物がある列も): 丸ごとの床の 1 マスが、4 方向の 1 高い床に囲まれて 1 低いなら、その上を隣の床で埋める
    private int fillLocalDimples() {
        int n = 0;
        int y0 = origin.getY() + Math.min(bp.minY, 0) - 6;
        int y1 = origin.getY() + Math.max(bp.maxY, 3) + 2;
        for (int x = rect[0] - 3; x <= rect[2] + 3; x++) {
            for (int z = rect[1] - 3; z <= rect[3] + 3; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (!fullAt(x, y, z) || !clearAt(x, y + 1, z) || !clearAt(x, y + 2, z)) {
                        continue;
                    }
                    boolean ring = true;
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = x + d[0];
                        int nz = z + d[1];
                        if (!fullAt(nx, y + 1, nz) || !clearAt(nx, y + 2, nz) || !clearAt(nx, y + 3, nz)) {
                            ring = false;
                            break;
                        }
                    }
                    if (ring) {
                        level.setBlock(new BlockPos(x, y + 1, z), level.getBlockState(new BlockPos(x + 1, y + 1, z)), FLAGS);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private boolean fullAt(int x, int y, int z) {
        BlockPos q = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(q);
        return s.getFluidState().isEmpty() && !s.is(BlockTags.LEAVES) && Block.isShapeFullBlock(s.getCollisionShape(level, q));
    }

    private boolean clearAt(int x, int y, int z) {
        BlockPos q = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(q);
        return s.getFluidState().isEmpty() && s.getCollisionShape(level, q).isEmpty() && !s.is(Blocks.LADDER) && !s.is(Blocks.VINE);
    }

    // 列の上面 (空気・草花・はしごなどを抜けた、いちばん上の丸ごとの固いブロック) の y。水・葉・木・丸ごとでないブロックが上面なら MIN_VALUE
    private int surfaceY(int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = origin.getY() + Math.max(bp.maxY, 3) + 4; y >= origin.getY() + Math.min(bp.minY, 0) - 8; y--) {
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (s.getCollisionShape(level, p).isEmpty()) {
                continue;
            }
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || !Block.isShapeFullBlock(s.getCollisionShape(level, p))) {
                return Integer.MIN_VALUE;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    // 中心 c のまわり (水平 r・高さ -4〜部品の高さ +2) で、足もとの支えをなくした草花・作物を消す (build audit の ATTACH_SUPPORT)。消した数
    public int clearFloatingPlants(BlockPos c, int r) {
        int n = 0;
        int top = Math.max(6, bp.maxY + 2);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -4, -r), c.offset(r, top, r))) {
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.world.level.block.BushBlock) || !st.getFluidState().isEmpty()) {
                continue;
            }
            BlockState below = level.getBlockState(p.below());
            if (below.isAir() || below.getCollisionShape(level, p.below()).isEmpty() && below.getBlock() != st.getBlock()) {
                level.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), FLAGS);
                n++;
            }
        }
        return n;
    }

    // 立つ場所の印 (stand) が自然の地面に埋まっていれば掘り、足もとが空なら埋める (build audit の SITE_STAND)
    private int clearStand() {
        BlockPos at = mark("stand");
        if (at == null) {
            return 0;
        }
        int n = 0;
        for (BlockPos q : new BlockPos[] {at, at.above()}) {
            BlockState s = level.getBlockState(q);
            if (!s.isAir() && (naturalGround(s) || naturalAbove(s)) && s.getFluidState().isEmpty()) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
                n++;
            }
        }
        BlockState below = level.getBlockState(at.below());
        if (below.getFluidState().isEmpty() && below.getCollisionShape(level, at.below()).isEmpty()) {
            level.setBlock(at.below(), topState, FLAGS);
            n++;
        }
        return n;
    }

    // 範囲のまわり (3 マス) の水・溶岩の源の横・下の空気を、その下のブロック (なければ土・石) で埋める (build audit の FLUID_LEAK)
    private int sealFluids() {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int y0 = origin.getY() + Math.min(bp.minY, 0) - 10;
        int y1 = origin.getY() + Math.max(bp.maxY, 2) + 2;
        net.minecraft.core.Direction[] dirs = {net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
        for (int pass = 0; pass < 3; pass++) {
            int before = n;
            for (int x = rect[0] - 3; x <= rect[2] + 3; x++) {
                for (int z = rect[1] - 3; z <= rect[3] + 3; z++) {
                    for (int y = y1; y >= y0; y--) {
                        p.set(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (!s.getFluidState().isSource() || !(s.is(Blocks.WATER) || s.is(Blocks.LAVA))) {
                            continue;
                        }
                        for (net.minecraft.core.Direction d : dirs) {
                            q.set(p).move(d);
                            BlockState nb = level.getBlockState(q);
                            if (nb.isAir() || (nb.getFluidState().isEmpty() && nb.canBeReplaced() && nb.getCollisionShape(level, q).isEmpty())) {
                                BlockPos qb = q.below();
                                BlockState under = level.getBlockState(qb);
                                BlockState fill = under.isSolidRender(level, qb) && !under.getBlock().hasDynamicShape() && under.getFluidState().isEmpty() ? under
                                        : s.is(Blocks.LAVA) ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                                level.setBlock(q, fill, FLAGS);
                                n++;
                            }
                        }
                    }
                }
            }
            if (n == before) {
                break;
            }
        }
        return n;
    }

    // 範囲のまわり (3 マス) で、まわり 26 マスに固いブロックが無い自然のブロック (割れた氷・削り残しの土) を消す (build audit の FLOAT_BLOCK)。
    // 氷は、下か横が水なら水に戻す
    private int pruneLoneNatural() {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int y0 = origin.getY() + Math.min(bp.minY, 0) - 4;
        int y1 = origin.getY() + Math.max(bp.maxY, 2) + 4;
        for (int x = rect[0] - 3; x <= rect[2] + 3; x++) {
            for (int z = rect[1] - 3; z <= rect[3] + 3; z++) {
                for (int y = y1; y >= y0; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.getCollisionShape(level, p).isEmpty() || !naturalGround(s)) {
                        continue;
                    }
                    boolean alone = true;
                    boolean water = false;
                    for (int dx = -1; dx <= 1 && alone; dx++) {
                        for (int dy = -1; dy <= 1 && alone; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if ((dx | dy | dz) == 0) {
                                    continue;
                                }
                                BlockState o = level.getBlockState(q.set(x + dx, y + dy, z + dz));
                                if (!o.getCollisionShape(level, q).isEmpty()) {
                                    alone = false;
                                    break;
                                }
                                water |= o.is(Blocks.WATER) && dy <= 0;
                            }
                        }
                    }
                    if (alone) {
                        level.setBlock(p, s.is(BlockTags.ICE) && water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
                        n++;
                    }
                }
            }
        }
        return n;
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
