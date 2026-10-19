package jp.revati.skyabyss.guide.nav;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

// 人ごとの道の探索の予定表 (サーバー)。画面にもプレイヤーにも依らない (K = 持ち主の鍵。本番は UUID)。
//  - クライアントが「いまの目的の場所」を知らせてくる (update)。知らせが 30 秒途絶えたら捨てる
//  - 探索 (PathFinder) は 1 tick に全員で合計 budget ノードまでしか進めない。1 回の探索は MAX_NODES まで。順番は持ち回り
//  - やり直す条件: 場所が変わった / 前の探索の出発点から 8 ブロック動いた / 5 秒たって 2 ブロック以上動いた / 道なしのとき 10 秒ごと
//  - 結果は持ち主にだけ届ける (Sink.deliver(owner, …))。前と同じ結果は送らない
public final class GuideScheduler<K> {
    public static final int RADIUS = 96;
    public static final int MAX_NODES = 6000;
    public static final int MAX_PATH = 192;
    public static final int STALE_TICKS = 600;
    public static final int MOVE_RECOMPUTE = 8;
    public static final int REFRESH_TICKS = 100;
    public static final int NOT_FOUND_RETRY = 200;
    // 目印のこの近さ (水平) に着いたら、道は出さない
    public static final int ARRIVED = 3;

    public record Where(BlockPos pos, PathFinder.Terrain terrain) {
    }

    public interface Env<K> {
        // 持ち主が探索できる状態 (オンライン・オーバーワールド) ならその位置と地形、そうでなければ null
        @Nullable
        Where where(K owner);
    }

    public record Delivery(BlockPos target, boolean found, List<BlockPos> nodes) {
    }

    public interface Sink<K> {
        void deliver(K owner, Delivery delivery);
    }

    private static final class Entry {
        BlockPos target;
        long heard;
        boolean dirty = true;
        BlockPos from;
        long at = Long.MIN_VALUE / 2;
        boolean lastFound = true;
        PathFinder run;
        BlockPos runTarget;
        Delivery lastSent;
    }

    private final Map<K, Entry> entries = new LinkedHashMap<>();
    private final Sink<K> sink;
    private int cursor;

    public GuideScheduler(Sink<K> sink) {
        this.sink = sink;
    }

    public int size() {
        return entries.size();
    }

    public boolean has(K owner) {
        return entries.containsKey(owner);
    }

    public void update(K owner, boolean active, @Nullable BlockPos target, long now) {
        if (!active || target == null) {
            entries.remove(owner);
            return;
        }
        Entry e = entries.computeIfAbsent(owner, k -> new Entry());
        e.heard = now;
        if (e.target == null || e.target.distSqr(target) > 4) {
            e.target = target;
            e.dirty = true;
            e.run = null;
        }
    }

    public void remove(K owner) {
        entries.remove(owner);
    }

    public void clear() {
        entries.clear();
    }

    // 1 tick 分。使ったノードの数を返す (budget を超えない)
    public int tick(long now, Env<K> env, int budget) {
        entries.values().removeIf(e -> now - e.heard > STALE_TICKS);
        if (entries.isEmpty() || budget <= 0) {
            return 0;
        }
        List<K> keys = new ArrayList<>(entries.keySet());
        int n = keys.size();
        int used = 0;
        int first = cursor % n;
        for (int i = 0; i < n && used < budget; i++) {
            K key = keys.get((first + i) % n);
            Entry e = entries.get(key);
            Where w = env.where(key);
            if (w == null) {
                e.run = null;
                continue;
            }
            used += serve(key, e, w, now, budget - used);
        }
        cursor++;
        return used;
    }

    private int serve(K key, Entry e, Where w, long now, int room) {
        BlockPos p = w.pos();
        double hx = p.getX() + 0.5 - (e.target.getX() + 0.5);
        double hz = p.getZ() + 0.5 - (e.target.getZ() + 0.5);
        if (hx * hx + hz * hz <= ARRIVED * ARRIVED) {
            e.run = null;
            send(key, e, new Delivery(e.target, true, List.of()));
            return 0;
        }
        if (e.run == null && shouldCompute(e, p, now)) {
            e.run = new PathFinder(w.terrain(), p, e.target, RADIUS, MAX_NODES, MAX_PATH);
            e.runTarget = e.target;
            e.from = p;
            e.at = now;
            e.dirty = false;
        }
        if (e.run == null) {
            return 0;
        }
        int before = e.run.expansions();
        // 1 人に 1 tick で使うのは最大 400 (ほかの人の分を食わない)
        boolean done = e.run.step(Math.min(room, 400));
        int used = e.run.expansions() - before;
        if (done) {
            PathFinder.Result r = e.run.result();
            e.run = null;
            e.lastFound = r.progress();
            send(key, e, new Delivery(e.runTarget, r.progress(), r.nodes()));
        }
        return used;
    }

    private static boolean shouldCompute(Entry e, BlockPos p, long now) {
        if (e.dirty || e.from == null) {
            return true;
        }
        double dx = p.getX() - e.from.getX();
        double dz = p.getZ() - e.from.getZ();
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved >= MOVE_RECOMPUTE) {
            return true;
        }
        if (e.lastFound) {
            return now - e.at >= REFRESH_TICKS && moved >= 2;
        }
        return now - e.at >= NOT_FOUND_RETRY;
    }

    private void send(K key, Entry e, Delivery d) {
        if (d.equals(e.lastSent)) {
            return;
        }
        e.lastSent = d;
        sink.deliver(key, d);
    }
}
