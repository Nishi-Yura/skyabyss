package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.BackGroupData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 順路の区画 (docs/design/32 の 5 章)。一つの順路に一つの番号 (1〜MAX) を割り当て、グループの記録 keep.trial.slot に書く。
// 番号は小さい空きから。順路が終わると返し、REUSE_AFTER のあいだは同じ番号を別の順路に渡さない (空きがなければ渡す)。
// 区画の場所は背の次元の体から遠い所 (x = ORIGIN_X + 番号 × STRIDE、z = ORIGIN_Z)。手本の座標にこのずれを足した所へ、
// その順路が引いた三つのうち写せるセル (TrialCell.relocatable) を、背の中の手本の部屋からブロックごと写す (build)。順路が終わると空気に戻す (clear)。
// 写しのセルの決まりは TrialRuntime が手本の座標で動かす (TrialFrame)。そのあいだ手本の部屋のチャンクを読み込んでおく (TICKET)。
// 写せないセル・原型は、背の中の手本の部屋をグループごとの上書きで共有する
// 写す・空気に戻すのは、tick ごとに予算 (BUDGET_MS) の中で少しずつ進める (buildSoon・release。R6 の調整: 一 tick に 1〜2 秒止まっていた)。
// 写し先のチャンクは先に読み込みを頼み、読めたものから写す。運ぶ前に写しが済んでいなければ、運ぶ側が待つ (ready) か、その場で済ませる (finishNow)。
// 写す中身と順は一度に写すのと同じ
@net.neoforged.fml.common.EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TrialSlot {
    private TrialSlot() {
    }

    public static final String SLOT = "keep.trial.slot";
    public static final int MAX = 32;
    public static final int REUSE_AFTER = 20 * 60 * 3;
    public static final int ORIGIN_X = 4096;
    public static final int ORIGIN_Z = 4096;
    public static final int STRIDE = 1024;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_trial_slot", Comparator.comparingLong(ChunkPos::toLong));

    // 返した番号 → 返した時刻 (背ごと)
    private static final Map<String, Map<Integer, Long>> RELEASED = new HashMap<>();
    // 写した箱 (背 + 番号 → 写しの座標の箱)。空気に戻すとき使う
    private static final Map<String, List<AABB>> COPIED = new HashMap<>();
    // 写しを動かしているあいだ読み込む、手本の部屋のチャンク (背 → チャンク → 使っている番号)
    private static final Map<String, Map<Long, Set<Integer>>> HELD = new HashMap<>();

    public static int of(BackGroupData.Record r) {
        return Math.max(0, r.getInt(SLOT));
    }

    private static String dim(ServerLevel back) {
        return back.dimension().location().toString();
    }

    // 番号 n の区画のずれ
    public static TrialFrame frame(int n) {
        return n <= 0 ? TrialFrame.ZERO : new TrialFrame(ORIGIN_X + n * STRIDE, 0, ORIGIN_Z);
    }

    // そのグループが、そのセルを区画の写しで動かすか (順路が動いていて、番号があり、そのセルを引いていて、写せるセル)
    public static TrialFrame frameOf(ServerLevel back, UUID group, TrialCell cell) {
        if (!cell.relocatable()) {
            return TrialFrame.ZERO;
        }
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null || TrialRoute.step(r) <= 0 || of(r) <= 0 || !(',' + r.getString(TrialRoute.ROUTE) + ',').contains(',' + cell.def().id() + ',')) {
            return TrialFrame.ZERO;
        }
        return frame(of(r));
    }

    // いま使っている番号
    public static TreeSet<Integer> inUse(ServerLevel back) {
        TreeSet<Integer> used = new TreeSet<>();
        BackGroupData data = BackGroupData.get(back);
        for (UUID g : data.ids()) {
            BackGroupData.Record r = data.peek(g);
            if (r != null && of(r) > 0) {
                used.add(of(r));
            }
        }
        return used;
    }

    // 番号を割り当てる (もう持っていればそのまま)。空きがなければ 0
    public static int assign(ServerLevel back, BackGroupData.Record r) {
        if (of(r) > 0) {
            return of(r);
        }
        TreeSet<Integer> used = inUse(back);
        Map<Integer, Long> released = RELEASED.getOrDefault(dim(back), Map.of());
        long now = back.getGameTime();
        int pick = 0;
        for (int n = 1; n <= MAX; n++) {
            Long at = released.get(n);
            if (!used.contains(n) && (at == null || now - at >= REUSE_AFTER || now < at)) {
                pick = n;
                break;
            }
        }
        if (pick == 0) {
            // 後片付けの間の番号しか空いていなければ、それを使う (待たせない)
            for (int n = 1; n <= MAX; n++) {
                if (!used.contains(n)) {
                    pick = n;
                    break;
                }
            }
        }
        if (pick > 0) {
            r.putInt(SLOT, pick);
            Map<Integer, Long> rel = RELEASED.get(dim(back));
            if (rel != null) {
                rel.remove(pick);
            }
        }
        return pick;
    }

    // 番号を返す (写しを空気に戻し、手本の部屋の読み込みをやめる。空気に戻すのは tick ごとに少しずつ)
    public static void release(ServerLevel back, BackGroupData.Record r) {
        int n = of(r);
        if (n <= 0) {
            return;
        }
        r.remove(SLOT);
        clearSoon(back, n);
        RELEASED.computeIfAbsent(dim(back), k -> new HashMap<>()).put(n, back.getGameTime());
    }

    // ---------------------------------------------------------------- 写す・空気に戻す
    // その順路の引いた三つのうち、写せるセルを番号 n の区画へ写す (その場で最後まで)。写したセルの数
    public static int build(ServerLevel back, int n, TrialDraw.Draw draw) {
        int cells = buildSoon(back, n, draw);
        finishNow(back, n);
        return cells;
    }

    // 写す予定を立てる (前の写しを空気に戻す仕事のあと、引いた順に一つずつ)。tick ごとに少しずつ進む。写すセルの数
    public static int buildSoon(ServerLevel back, int n, TrialDraw.Draw draw) {
        if (n <= 0) {
            return 0;
        }
        clearSoon(back, n);
        TrialFrame f = frame(n);
        List<AABB> copied = new ArrayList<>();
        int cells = 0;
        String key = dim(back) + "#" + n;
        for (TrialDraw.Pick p : draw.picks()) {
            TrialCell cell = TrialCells.get(p.cellId());
            if (cell == null || !cell.relocatable()) {
                continue;
            }
            AABB box = cell.buildBox();
            queue(key, new Part(cell.def().id(), box, f, false));
            copied.add(f.box(box));
            hold(back, box, n);
            cells++;
        }
        COPIED.put(key, copied);
        return cells;
    }

    // 番号 n の区画で、セル cellId の写しが済んでいるか (写す予定がない・写し終えた)
    public static boolean ready(ServerLevel back, int n, String cellId) {
        java.util.ArrayDeque<Part> q = JOBS.get(dim(back) + "#" + n);
        if (q == null) {
            return true;
        }
        for (Part p : q) {
            if (p.clear() || p.cellId().equals(cellId)) {
                return false;
            }
        }
        return true;
    }

    // 番号 n の区画の残りの仕事を、その場で最後まで進める
    public static void finishNow(ServerLevel back, int n) {
        String key = dim(back) + "#" + n;
        java.util.ArrayDeque<Part> q = JOBS.get(key);
        if (q == null) {
            return;
        }
        while (!q.isEmpty()) {
            Part p = q.peek();
            if (p.step(back, Long.MAX_VALUE, false)) {
                q.poll();
                p.done(back, n);
            }
        }
        JOBS.remove(key);
    }

    // 一つの仕事: 手本の箱をずれ f の所へ写す (clear なら box そのものを空気に戻す)。列 (x, z) ごとに進める
    private static final class Part {
        private final String cellId;
        private final TrialFrame f;
        private final boolean clear;
        private final int x0;
        private final int y0;
        private final int z0;
        private final int x1;
        private final int y1;
        private final int z1;
        private int col;
        private long blocks;
        private long nanos;
        private boolean requested;

        Part(String cellId, AABB box, TrialFrame f, boolean clear) {
            this.cellId = cellId;
            this.f = f;
            this.clear = clear;
            this.x0 = (int) Math.floor(box.minX);
            this.y0 = (int) Math.floor(box.minY);
            this.z0 = (int) Math.floor(box.minZ);
            this.x1 = (int) Math.ceil(box.maxX) - 1;
            this.y1 = (int) Math.ceil(box.maxY) - 1;
            this.z1 = (int) Math.ceil(box.maxZ) - 1;
        }

        String cellId() {
            return cellId;
        }

        boolean clear() {
            return clear;
        }

        boolean started() {
            return col > 0;
        }

        // 写し先 (clear なら box そのもの) の列の座標
        private int dstX(int x) {
            return clear ? x : x + (int) Math.round(f.dx());
        }

        private int dstZ(int z) {
            return clear ? z : z + (int) Math.round(f.dz());
        }

        // 写し先のチャンクの読み込みを頼む
        private void request(ServerLevel back) {
            for (int cx = dstX(x0) >> 4; cx <= dstX(x1) >> 4; cx++) {
                for (int cz = dstZ(z0) >> 4; cz <= dstZ(z1) >> 4; cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    back.getChunkSource().addRegionTicket(BUILD_TICKET, cp, 0, cp);
                }
            }
            requested = true;
        }

        // deadline まで進める。終われば true。wait なら、写し先のチャンクがまだ読めていない列で、この tick を終える
        boolean step(ServerLevel back, long deadline, boolean wait) {
            long t0 = System.nanoTime();
            if (wait && !requested) {
                request(back);
            }
            int nz = z1 - z0 + 1;
            int total = (x1 - x0 + 1) * nz;
            int ya = Math.max(back.getMinBuildHeight(), y0);
            int yb = Math.min(back.getMaxBuildHeight() - 1, y1);
            BlockPos.MutableBlockPos src = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            try {
                while (col < total) {
                    int x = x0 + col / nz;
                    int z = z0 + col % nz;
                    if (wait && back.getChunkSource().getChunkNow(dstX(x) >> 4, dstZ(z) >> 4) == null) {
                        return false;
                    }
                    for (int y = ya; y <= yb; y++) {
                        src.set(x, y, z);
                        if (clear) {
                            if (!back.getBlockState(src).isAir()) {
                                back.setBlock(src, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                                blocks++;
                            }
                            continue;
                        }
                        BlockState s = back.getBlockState(src);
                        BlockPos dst = f.at(src.immutable());
                        if (back.getBlockState(dst) != s) {
                            back.setBlock(dst, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            blocks++;
                        }
                        BlockEntity be = back.getBlockEntity(src);
                        if (be != null) {
                            CompoundTag tag = be.saveWithFullMetadata(back.registryAccess());
                            BlockEntity to = back.getBlockEntity(dst);
                            if (to != null) {
                                to.loadWithComponents(tag, back.registryAccess());
                                to.setChanged();
                            }
                        }
                    }
                    col++;
                    if (System.nanoTime() >= deadline) {
                        return col >= total;
                    }
                }
                return true;
            } finally {
                nanos += System.nanoTime() - t0;
            }
        }

        // 済んだ: 写し先のチャンクの読み込みの頼みをやめる
        void done(ServerLevel back, int n) {
            if (requested) {
                for (int cx = dstX(x0) >> 4; cx <= dstX(x1) >> 4; cx++) {
                    for (int cz = dstZ(z0) >> 4; cz <= dstZ(z1) >> 4; cz++) {
                        ChunkPos cp = new ChunkPos(cx, cz);
                        back.getChunkSource().removeRegionTicket(BUILD_TICKET, cp, 0, cp);
                    }
                }
            }
            if (!clear) {
                SkyAbyss.LOGGER.info("Trial slot {}: copied {} ({} blocks, {} ms of work)", n, cellId, blocks, nanos / 1_000_000L);
            }
        }
    }

    // 区画ごとの仕事の列 (背 + 番号 → 順に進める仕事)
    private static final Map<String, java.util.ArrayDeque<Part>> JOBS = new java.util.LinkedHashMap<>();
    private static final TicketType<ChunkPos> BUILD_TICKET = TicketType.create("skyabyss_trial_slot_build", Comparator.comparingLong(ChunkPos::toLong));
    // 一 tick に写す・戻す仕事に使う時間 (その背の全部の区画で)
    public static final long BUDGET_MS = 6;

    private static void queue(String key, Part p) {
        JOBS.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(p);
    }

    // まだ写し・戻しの仕事が残っているか (自己テスト)
    public static boolean busy(ServerLevel back, int n) {
        java.util.ArrayDeque<Part> q = JOBS.get(dim(back) + "#" + n);
        return q != null && !q.isEmpty();
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (JOBS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String d = dim(level) + "#";
        long deadline = System.nanoTime() + BUDGET_MS * 1_000_000L;
        for (java.util.Iterator<Map.Entry<String, java.util.ArrayDeque<Part>>> it = JOBS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, java.util.ArrayDeque<Part>> e = it.next();
            if (!e.getKey().startsWith(d)) {
                continue;
            }
            int n = Integer.parseInt(e.getKey().substring(d.length()));
            java.util.ArrayDeque<Part> q = e.getValue();
            while (!q.isEmpty() && System.nanoTime() < deadline) {
                Part p = q.peek();
                if (!p.step(level, deadline, true)) {
                    break;
                }
                q.poll();
                p.done(level, n);
            }
            if (q.isEmpty()) {
                it.remove();
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
    }

    // 手本の箱を、ずれ f の所へブロックごと写す (ブロックの実体の中身も)。置いたブロックの数
    static long copy(ServerLevel back, AABB box, TrialFrame f) {
        int x0 = (int) Math.floor(box.minX);
        int y0 = Math.max(back.getMinBuildHeight(), (int) Math.floor(box.minY));
        int z0 = (int) Math.floor(box.minZ);
        int x1 = (int) Math.ceil(box.maxX) - 1;
        int y1 = Math.min(back.getMaxBuildHeight() - 1, (int) Math.ceil(box.maxY) - 1);
        int z1 = (int) Math.ceil(box.maxZ) - 1;
        long n = 0;
        BlockPos.MutableBlockPos src = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    src.set(x, y, z);
                    BlockState s = back.getBlockState(src);
                    BlockPos dst = f.at(src.immutable());
                    if (back.getBlockState(dst) != s) {
                        back.setBlock(dst, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        n++;
                    }
                    BlockEntity be = back.getBlockEntity(src);
                    if (be != null) {
                        CompoundTag tag = be.saveWithFullMetadata(back.registryAccess());
                        BlockEntity to = back.getBlockEntity(dst);
                        if (to != null) {
                            to.loadWithComponents(tag, back.registryAccess());
                            to.setChanged();
                        }
                    }
                }
            }
        }
        return n;
    }

    // 番号 n の区画の写しを空気に戻す (その場で)
    public static void clear(ServerLevel back, int n) {
        clearSoon(back, n);
        finishNow(back, n);
    }

    // 番号 n の区画の写しを空気に戻す予定を立てる (まだ写し始めていない分は写さない)。tick ごとに少しずつ進む
    public static void clearSoon(ServerLevel back, int n) {
        String key = dim(back) + "#" + n;
        java.util.ArrayDeque<Part> q = JOBS.get(key);
        if (q != null) {
            q.removeIf(p -> !p.clear() && !p.started());
        }
        List<AABB> boxes = COPIED.remove(key);
        if (boxes != null) {
            for (AABB box : boxes) {
                queue(key, new Part("", box, TrialFrame.ZERO, true));
            }
        }
        unhold(back, n);
    }

    // 写しの箱 (自己テスト)
    public static List<AABB> copied(ServerLevel back, int n) {
        return COPIED.getOrDefault(dim(back) + "#" + n, List.of());
    }

    // ---------------------------------------------------------------- 手本の部屋の読み込み
    private static void hold(ServerLevel back, AABB box, int n) {
        Map<Long, Set<Integer>> held = HELD.computeIfAbsent(dim(back), k -> new HashMap<>());
        for (int cx = ((int) Math.floor(box.minX)) >> 4; cx <= ((int) Math.ceil(box.maxX)) >> 4; cx++) {
            for (int cz = ((int) Math.floor(box.minZ)) >> 4; cz <= ((int) Math.ceil(box.maxZ)) >> 4; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                Set<Integer> users = held.computeIfAbsent(cp.toLong(), k -> new HashSet<>());
                if (users.isEmpty()) {
                    back.getChunkSource().addRegionTicket(TICKET, cp, 2, cp);
                }
                users.add(n);
            }
        }
    }

    private static void unhold(ServerLevel back, int n) {
        Map<Long, Set<Integer>> held = HELD.get(dim(back));
        if (held == null) {
            return;
        }
        for (Map.Entry<Long, Set<Integer>> e : new ArrayList<>(held.entrySet())) {
            if (e.getValue().remove(n) && e.getValue().isEmpty()) {
                ChunkPos cp = new ChunkPos(e.getKey());
                back.getChunkSource().removeRegionTicket(TICKET, cp, 2, cp);
                held.remove(e.getKey());
            }
        }
    }

    // その座標がどの区画か (番号)。区画の外なら 0
    // 体の外の、閉じた部屋の並ぶ所 (区画 1〜32 の枠と、心臓の間のある 0 番の枠、自己テストの 33 番の枠) か。
    // 背の決まり (雲より下へ落ちた・島から流された・灰の原へ出た) で人を到着の台へ戻さない所
    public static boolean inInstance(double x, double z) {
        int n = (int) Math.floor((x - ORIGIN_X + STRIDE / 2.0) / STRIDE);
        return n >= 0 && n <= MAX + 1 && Math.abs(z - ORIGIN_Z) <= STRIDE / 2.0;
    }

    public static int slotAt(double x, double z) {
        int n = (int) Math.floor((x - ORIGIN_X + STRIDE / 2.0) / STRIDE);
        if (n < 1 || n > MAX || Math.abs(z - ORIGIN_Z) > STRIDE / 2.0) {
            return 0;
        }
        return n;
    }

    @Nullable
    public static AABB area(int n) {
        if (n <= 0) {
            return null;
        }
        TrialFrame f = frame(n);
        return new AABB(f.dx() - STRIDE / 2.0, -64, f.dz() - STRIDE / 2.0, f.dx() + STRIDE / 2.0, 400, f.dz() + STRIDE / 2.0);
    }

    public static void clearAll() {
        JOBS.clear();
        RELEASED.clear();
        COPIED.clear();
        HELD.clear();
    }
}
