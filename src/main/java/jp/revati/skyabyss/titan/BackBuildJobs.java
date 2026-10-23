package jp.revati.skyabyss.titan;

import java.util.ArrayDeque;
import java.util.Deque;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 巨神の背中 (岩鯨・潮殻・雷翼・余燼) の体を、起動時に一気に建てず、何 tick かに分けて建てる (13 の 24.5節)。
// 建て方の時間のほぼ全部 (9 割以上) は「体のブロックを置く」ループなので、そこを 1 列 (16 x 16 の柱) ずつ進められる形にし、
// 1 tick に使う時間 (予算) だけ進める。残り (部屋を掘る、中身を建てる、建てた印) は体を置き終えた tick に一度に済ませる (0.2〜0.5 秒)。
//   - 予算: 誰もいなければ 25 ms、人がいれば tick の余り次第で 10〜25 ms (budgetMs。予算のほかに、チャンクの読み込みと明るさの計算が 1 tick 20 ms ほどかかる)
//   - 建てている途中でサーバーが止まっても、建てた印は最後に付くので、次の起動でもう一度始める (置くのは「変わるブロックだけ」なので、置いた分は飛ばせる)
//   - 建てている途中で乗り込みなどが ensureBuilt / build を呼んだら、予定の続きをその場で最後まで進める (finishNow。最初からやり直さない)
// 体を置いたあとの仕上げ (部屋を掘る・中身・詰みの修復) も、Steps で一つずつ予算の中で進める (32 の R6 の調整: 一 tick に 5〜36 秒止まっていた)。
// 重い一つ (詰みの修復 BackReach.RepairJob など) は、それ自身が tick をまたいで続く仕事になる。建てる物と順は一度に建てるのと同じ
//   - チャンクは先に読み込みを頼み (チケット)、まだなら次の tick に回す (生成を待つあいだ tick を止めない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BackBuildJobs {
    public static final long BUDGET_IDLE_MS = 25;
    public static final long BUDGET_PLAYERS_MS = 10;

    // 先読みするチャンクの数と、そのチケット (読み込みが済むまで残す)
    static final int PREFETCH = 6;
    // tick の中から進めているときだけ、読み込みを待って次の tick に回せる (自己テストの runAll・finishNow はその場で読む)
    private static boolean inTick;
    static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
            net.minecraft.server.level.TicketType.create("skyabyss_back_build", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
    // 建てているあいだ読み込んだままにするチャンク (体を置いたあとの仕上げが、消えたチャンクをその場で読み直して tick を止めていた。
    // JFR で、雷翼の道・広場・外形の手順の 0.3〜0.9 秒はほとんど読み込みの待ちだった)。仕事が終わるとまとめて外す
    private static final java.util.Map<ResourceKey<Level>, it.unimi.dsi.fastutil.longs.LongOpenHashSet> HELD = new java.util.HashMap<>();

    public static void hold(ServerLevel level, net.minecraft.world.level.ChunkPos cp) {
        // 次の建設が始まったら、前の建設の外す予定は取り消す (外すのはその建設の終わりに)
        RELEASE_AT.remove(level);
        if (HELD.computeIfAbsent(level.dimension(), k -> new it.unimi.dsi.fastutil.longs.LongOpenHashSet()).add(cp.toLong())) {
            level.getChunkSource().addRegionTicket(TICKET, cp, 0, cp);
        }
    }

    // 仕上げの手順が触る範囲 (x1..x2, z1..z2) のチャンクを先に読み込んで待つ仕事。tick の中でだけ待つ (その場で最後まで進めるときは何もしない)
    // いま tick の中から進めているか (読み込みを待って次の tick に回してよいか)
    public static boolean inTick() {
        return inTick;
    }

    public static Job loadArea(ServerLevel level, int x1, int z1, int x2, int z2) {
        return new Job() {
            @Override
            public boolean step(long deadlineNanos) {
                if (!inTick) {
                    return true;
                }
                boolean all = true;
                for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
                    for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                        if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                            hold(level, new net.minecraft.world.level.ChunkPos(cx, cz));
                            all = false;
                        }
                    }
                }
                return all;
            }

            @Override
            public int placed() {
                return 0;
            }
        };
    }

    // 建て終えてから外すまでの tick (明るさの計算が済むのを待つ。すぐ外すと、明るさが古いまま保存されて暗い所が残った)
    static final int RELEASE_DELAY = 400;
    private static final java.util.Map<ServerLevel, Integer> RELEASE_AT = new java.util.HashMap<>();

    static void releaseAll(ServerLevel level) {
        RELEASE_AT.put(level, level.getServer().getTickCount() + RELEASE_DELAY);
    }

    private static void tickReleases(net.minecraft.server.MinecraftServer server) {
        if (RELEASE_AT.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        for (var e : new java.util.ArrayList<>(RELEASE_AT.entrySet())) {
            if (now >= e.getValue()) {
                RELEASE_AT.remove(e.getKey());
                releaseNow(e.getKey());
            }
        }
    }

    private static void releaseNow(ServerLevel level) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet held = HELD.remove(level.dimension());
        if (held == null) {
            return;
        }
        it.unimi.dsi.fastutil.longs.LongIterator it = held.iterator();
        while (it.hasNext()) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(it.nextLong());
            level.getChunkSource().removeRegionTicket(TICKET, cp, 0, cp);
        }
    }

    // 体を置く仕事。deadline (System.nanoTime) まで進める。終われば true
    public interface Job {
        boolean step(long deadlineNanos);

        int placed();
    }

    // 1 列ずつ進められる、体の置き方の共通部分 (チャンクの外側のループ、列の内側のループは、一括の place と同じ順)
    public abstract static class ColumnJob implements Job {
        private final ServerLevel level;
        private final int cx0;
        private final int cz0;
        private final int ncz;
        private final long total;
        private long index;
        private int placed;
        private LevelChunk chunk;
        private long chunkIndex = -1;

        protected ColumnJob(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
            this.level = level;
            this.cx0 = minX >> 4;
            this.cz0 = minZ >> 4;
            int ncx = (maxX >> 4) - cx0 + 1;
            this.ncz = (maxZ >> 4) - cz0 + 1;
            this.total = (long) ncx * ncz * 256;
        }

        // 1 列 (x, z) の柱を置く。置いた数を返す
        protected abstract int column(ServerLevel level, LevelChunk chunk, int x, int z);

        // 区画の一括置きで置いた数を足す
        protected void addPlaced(int n) {
            placed += n;
        }

        // チャンクの最初・最後に呼ぶ (区画の一括置きの準備と後始末。26 の 4.2)。既定は何もしない
        protected void beginChunk(ServerLevel level, LevelChunk chunk) {
        }

        protected void endChunk(ServerLevel level, LevelChunk chunk) {
        }

        private long requested = -1;

        // ci 番目のチャンクの座標
        private net.minecraft.world.level.ChunkPos chunkPos(long ci) {
            return new net.minecraft.world.level.ChunkPos(cx0 + (int) (ci / ncz), cz0 + (int) (ci % ncz));
        }

        // 次のいくつかのチャンクの読み込みを先に頼む (生成は別のスレッドで進む)
        private void prefetch(long ci) {
            long last = Math.min((total >> 8) - 1, ci + PREFETCH);
            for (long c = Math.max(ci, requested + 1); c <= last; c++) {
                net.minecraft.world.level.ChunkPos cp = chunkPos(c);
                hold(level, cp);
                requested = c;
            }
        }

        @Override
        public boolean step(long deadlineNanos) {
            boolean wait = inTick;
            while (index < total) {
                long ci = index >> 8;
                int col = (int) (index & 255);
                if (ci != chunkIndex) {
                    if (chunk != null) {
                        endChunk(level, chunk);
                    }
                    net.minecraft.world.level.ChunkPos cp = chunkPos(ci);
                    if (wait) {
                        prefetch(ci);
                        // まだ読み込めていなければ、この tick はここまで (読み込みを待って tick を止めない)
                        if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                            chunk = null;
                            chunkIndex = -1;
                            return false;
                        }
                    }
                    chunkIndex = ci;
                    chunk = level.getChunk(cp.x, cp.z);
                    beginChunk(level, chunk);
                }
                int x = ((cx0 + (int) (ci / ncz)) << 4) + (col >> 4);
                int z = ((cz0 + (int) (ci % ncz)) << 4) + (col & 15);
                placed += column(level, chunk, x, z);
                index++;
                if ((index & 255) == 0 && chunk != null) {
                    // チャンクを置き終えた (次の列が次のチャンク)
                    endChunk(level, chunk);
                    chunk = null;
                    chunkIndex = -1;
                }
                if (System.nanoTime() >= deadlineNanos) {
                    break;
                }
            }
            return index >= total;
        }

        @Override
        public int placed() {
            return placed;
        }

        public double progress() {
            return total == 0 ? 1 : (double) index / total;
        }
    }

    // 名前つきの手順を順に進める仕事 (体を置く・部屋を掘る・中身・修復 …)。一つの手順はふつう一度に済む (Runnable)。
    // 重い手順は Job (tick をまたいで続く) で渡す。手順ごとにかかった時間を数え、重いものは記録に出す
    public static final class Steps implements Job {
        private record Step(String name, Runnable run, java.util.function.Supplier<Job> job) {
        }

        private final String name;
        private final java.util.List<Step> steps = new java.util.ArrayList<>();
        private int index;
        private Job current;
        private long currentNanos;
        private int placed;
        private long longestNanos;
        private String longest = "";
        // 建てる重さの記録 (docs/design/32 の 5.3): 体を置く手順 (名前が body) と、それ以外の仕上げの手順の合計
        private long bodyNanos;
        private long finishNanos;

        public Steps(String name) {
            this.name = name;
        }

        public Steps then(String step, Runnable run) {
            steps.add(new Step(step, run, null));
            return this;
        }

        public Steps thenJob(String step, java.util.function.Supplier<Job> job) {
            steps.add(new Step(step, null, job));
            return this;
        }

        @Override
        public boolean step(long deadlineNanos) {
            while (index < steps.size()) {
                Step s = steps.get(index);
                long t0 = System.nanoTime();
                if (s.job() != null) {
                    if (current == null) {
                        current = s.job().get();
                        currentNanos = 0;
                    }
                    boolean done = current.step(deadlineNanos);
                    long dt = System.nanoTime() - t0;
                    currentNanos += dt;
                    note(s.name(), dt);
                    if (dt > 150_000_000L) {
                        SkyAbyss.LOGGER.info("{}: one slice of {} took {} ms", name, s.name(), dt / 1_000_000L);
                    }
                    if (!done) {
                        return false;
                    }
                    placed += current.placed();
                    current = null;
                    if (currentNanos > 200_000_000L) {
                        SkyAbyss.LOGGER.info("{}: {} took {} ms over several ticks", name, s.name(), currentNanos / 1_000_000L);
                    }
                } else {
                    s.run().run();
                    long dt = System.nanoTime() - t0;
                    note(s.name(), dt);
                    if (dt > 150_000_000L) {
                        SkyAbyss.LOGGER.info("{}: {} took {} ms", name, s.name(), dt / 1_000_000L);
                    }
                }
                index++;
                if (index >= steps.size()) {
                    SkyAbyss.LOGGER.info("{}: build work {} ms (body {} ms, finishing {} ms in {} steps)", name, (bodyNanos + finishNanos) / 1_000_000L,
                            bodyNanos / 1_000_000L, finishNanos / 1_000_000L, steps.size());
                }
                if (System.nanoTime() >= deadlineNanos) {
                    return index >= steps.size();
                }
            }
            return true;
        }

        private void note(String step, long dt) {
            if (step.equals("body")) {
                bodyNanos += dt;
            } else {
                finishNanos += dt;
            }
            if (dt > longestNanos) {
                longestNanos = dt;
                longest = step;
            }
        }

        // いちばん長くかかった一回 (一 tick の中で) の手順の名前と ms
        public String longest() {
            return longest + " " + longestNanos / 1_000_000L + " ms";
        }

        public long longestMs() {
            return longestNanos / 1_000_000L;
        }

        @Override
        public int placed() {
            return placed + (current != null ? current.placed() : 0);
        }
    }

    // そのディメンションの予約があれば、続きをその場で最後まで進めて仕上げる (乗り込みなどが背をすぐに要るとき)。進めたか
    public static boolean finishNow(ResourceKey<Level> dimension) {
        Task t = null;
        synchronized (BackBuildJobs.class) {
            for (Task q : QUEUE) {
                if (q.level.dimension() == dimension) {
                    t = q;
                    break;
                }
            }
            if (t == null) {
                return false;
            }
            QUEUE.remove(t);
        }
        long t0 = System.nanoTime();
        while (!t.job.step(Long.MAX_VALUE)) {
            // 最後まで
        }
        t.finish.run();
        releaseAll(t.level);
        SkyAbyss.LOGGER.info("Finished the {} at once (it was still building in the background): {} ms", t.name, (System.nanoTime() - t0) / 1_000_000L);
        return true;
    }

    private static final class Task {
        final String name;
        final ServerLevel level;
        final Job job;
        final Runnable finish;
        long workNanos;
        long maxTickNanos;
        int ticks;

        Task(String name, ServerLevel level, Job job, Runnable finish) {
            this.name = name;
            this.level = level;
            this.job = job;
            this.finish = finish;
        }
    }

    private static final Deque<Task> QUEUE = new ArrayDeque<>();

    private BackBuildJobs() {
    }

    // 体を置く仕事を予約する。同じディメンションの予約があれば入れ替える。終わったら finish を (サーバーの tick の中で) 呼ぶ
    public static synchronized void schedule(String name, ServerLevel level, Job job, Runnable finish) {
        cancel(level.dimension());
        QUEUE.add(new Task(name, level, job, finish));
        SkyAbyss.LOGGER.info("Queued the {} build; it will be placed a little each tick", name);
    }

    // そのディメンションの予約を取り消す。取り消したか
    public static synchronized boolean cancel(ResourceKey<Level> dimension) {
        return QUEUE.removeIf(t -> t.level.dimension() == dimension);
    }

    public static synchronized boolean pending(ResourceKey<Level> dimension) {
        return QUEUE.stream().anyMatch(t -> t.level.dimension() == dimension);
    }

    public static synchronized int pendingCount() {
        return QUEUE.size();
    }

    // 自己テスト用: 予約を最後まで進める (tick を待たない)。かかった ms の合計と、tick に分けた回数を返す
    public static synchronized long[] runAll() {
        long t0 = System.nanoTime();
        int steps = 0;
        while (!QUEUE.isEmpty()) {
            Task t = QUEUE.peek();
            long deadline = System.nanoTime() + BUDGET_IDLE_MS * 1_000_000L;
            steps++;
            if (t.job.step(deadline)) {
                QUEUE.poll();
                t.finish.run();
                releaseAll(t.level);
            }
        }
        return new long[] {(System.nanoTime() - t0) / 1_000_000L, steps};
    }

    // 人がいるときの予算は、サーバーの tick の余り次第 (2026-10-22: 背が建ち終わるまで数分かかっていた)。
    // ほかの仕事 (平均の tick の長さから、この建設の平均を引いたもの) が軽ければ、tick が 35 ms を超えない分だけ使う (10〜25 ms)
    static final long TICK_TARGET_MS = 35;
    private static long workEma;

    static long budgetMs(net.minecraft.server.MinecraftServer server) {
        if (server.getPlayerCount() == 0) {
            return BUDGET_IDLE_MS;
        }
        long others = Math.max(0, server.getAverageTickTimeNanos() - workEma) / 1_000_000L;
        return Math.max(BUDGET_PLAYERS_MS, Math.min(BUDGET_IDLE_MS, TICK_TARGET_MS - others));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        tickReleases(event.getServer());
        Task t;
        synchronized (BackBuildJobs.class) {
            t = QUEUE.peek();
        }
        if (t == null) {
            return;
        }
        long budget = budgetMs(event.getServer());
        long start = System.nanoTime();
        inTick = true;
        boolean done;
        try {
            done = t.job.step(start + budget * 1_000_000L);
        } finally {
            inTick = false;
        }
        t.ticks++;
        long work = System.nanoTime() - start;
        workEma = workEma == 0 ? work : (workEma * 7 + work) / 8;
        if (!done) {
            t.workNanos += work;
            t.maxTickNanos = Math.max(t.maxTickNanos, System.nanoTime() - start);
            return;
        }
        synchronized (BackBuildJobs.class) {
            QUEUE.remove(t);
        }
        long placedMs = (t.workNanos + System.nanoTime() - start) / 1_000_000L;
        long f0 = System.nanoTime();
        t.finish.run();
        releaseAll(t.level);
        t.maxTickNanos = Math.max(t.maxTickNanos, System.nanoTime() - start);
        SkyAbyss.LOGGER.info("Built the {} in the background: {} blocks placed in {} ticks ({} ms of work, budget {} ms, longest tick {} ms{}), finishing {} ms",
                t.name, t.job.placed(), t.ticks, placedMs, budget, t.maxTickNanos / 1_000_000L,
                t.job instanceof Steps st ? ", longest step " + st.longest() : "", (System.nanoTime() - f0) / 1_000_000L);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        synchronized (BackBuildJobs.class) {
            QUEUE.clear();
        }
        HELD.clear();
        RELEASE_AT.clear();
    }

    // 仕事が一つも無い所へ置く用の、何もしない仕事 (形の取り込みが無いとき)
    // 形の読み込み (load) を別のスレッドで済ませてから、仕事 (make) を作って進める。tick の中では読み込みを待つあいだ止めない
    // (岩鯨・雷翼・潮殻の形の読み込みで、体の最初の一切れが 0.3〜0.5 秒かかっていた)。その場で最後まで進めるときは、その場で読む
    public static Job afterAsync(Runnable load, java.util.function.Supplier<Job> make) {
        return new Job() {
            private java.util.concurrent.CompletableFuture<Void> loading;
            private Job job;

            @Override
            public boolean step(long deadlineNanos) {
                if (job == null) {
                    if (deadlineNanos != Long.MAX_VALUE) {
                        if (loading == null) {
                            loading = java.util.concurrent.CompletableFuture.runAsync(load, net.minecraft.Util.backgroundExecutor());
                        }
                        if (!loading.isDone()) {
                            return false;
                        }
                        loading.join();
                    } else {
                        load.run();
                    }
                    job = make.get();
                }
                return job.step(deadlineNanos);
            }

            @Override
            public int placed() {
                return job == null ? 0 : job.placed();
            }
        };
    }

    public static Job empty() {
        return new Job() {
            @Override
            public boolean step(long deadlineNanos) {
                return true;
            }

            @Override
            public int placed() {
                return 0;
            }
        };
    }

    // BlockPos の再利用 (列の中で使う)
    public static BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos();
    }
}
