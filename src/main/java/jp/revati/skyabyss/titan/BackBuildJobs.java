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
//   - 建てている途中で乗り込みなどが ensureBuilt / build を呼んだら、予定を取り消して、その場で最後まで建てる (今までどおり)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BackBuildJobs {
    public static final long BUDGET_IDLE_MS = 25;
    public static final long BUDGET_PLAYERS_MS = 10;

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

        @Override
        public boolean step(long deadlineNanos) {
            while (index < total) {
                long ci = index >> 8;
                int col = (int) (index & 255);
                if (ci != chunkIndex) {
                    chunkIndex = ci;
                    chunk = level.getChunk(cx0 + (int) (ci / ncz), cz0 + (int) (ci % ncz));
                }
                int x = ((cx0 + (int) (ci / ncz)) << 4) + (col >> 4);
                int z = ((cz0 + (int) (ci % ncz)) << 4) + (col & 15);
                placed += column(level, chunk, x, z);
                index++;
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

    private static final class Task {
        final String name;
        final ServerLevel level;
        final Job job;
        final Runnable finish;
        long workNanos;
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
        boolean done = t.job.step(start + budget * 1_000_000L);
        t.ticks++;
        if (!done) {
            t.workNanos += System.nanoTime() - start;
            return;
        }
        synchronized (BackBuildJobs.class) {
            QUEUE.remove(t);
        }
        long placedMs = (t.workNanos + System.nanoTime() - start) / 1_000_000L;
        long f0 = System.nanoTime();
        t.finish.run();
        SkyAbyss.LOGGER.info("Built the {} in the background: {} blocks placed in {} ticks ({} ms of work, longest tick budget {} ms), finishing {} ms",
                t.name, t.job.placed(), t.ticks, placedMs, budget, (System.nanoTime() - f0) / 1_000_000L);
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
