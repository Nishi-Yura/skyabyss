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
//   - 予算: 誰もいなければ 25 ms、人がいれば 10 ms (予算のほかに、チャンクの読み込みと明るさの計算が 1 tick 20 ms ほどかかる)
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
            net.minecraft.server.level.TicketType.create("skyabyss_back_build", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 400);

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
                level.getChunkSource().addRegionTicket(TICKET, cp, 0, cp);
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
                if (System.nanoTime() >= deadlineNanos) {
                    return index >= steps.size();
                }
            }
            return true;
        }

        private void note(String step, long dt) {
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
            }
        }
        return new long[] {(System.nanoTime() - t0) / 1_000_000L, steps};
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Task t;
        synchronized (BackBuildJobs.class) {
            t = QUEUE.peek();
        }
        if (t == null) {
            return;
        }
        long budget = event.getServer().getPlayerCount() > 0 ? BUDGET_PLAYERS_MS : BUDGET_IDLE_MS;
        long start = System.nanoTime();
        inTick = true;
        boolean done;
        try {
            done = t.job.step(start + budget * 1_000_000L);
        } finally {
            inTick = false;
        }
        t.ticks++;
        if (!done) {
            t.workNanos += System.nanoTime() - start;
            t.maxTickNanos = Math.max(t.maxTickNanos, System.nanoTime() - start);
            return;
        }
        synchronized (BackBuildJobs.class) {
            QUEUE.remove(t);
        }
        long placedMs = (t.workNanos + System.nanoTime() - start) / 1_000_000L;
        long f0 = System.nanoTime();
        t.finish.run();
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
    }

    // 仕事が一つも無い所へ置く用の、何もしない仕事 (形の取り込みが無いとき)
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
