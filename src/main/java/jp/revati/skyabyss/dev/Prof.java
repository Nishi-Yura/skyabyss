package jp.revati.skyabyss.dev;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.TreeMap;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 開発用の計測器 (見た目にも遊びにも影響しない)。環境変数 SKYABYSS_PROF=1 か JVM 引数 -Dskyabyss.prof=true のときだけ動く。
// 切っているときは ENABLED の判定ひとつだけ。600 回ごとに、区間ごとの平均 / 最大 (ms) と、そのスレッドの確保バイト数をログへ出す。
//   server.tick : サーバーの 1 tick 全体    client.frame : クライアントの 1 フレーム全体
//   ほかは long t = Prof.begin(); ... Prof.end("名前", t); で挟んだ区間
public final class Prof {
    public static final boolean ENABLED = "1".equals(System.getenv("SKYABYSS_PROF")) || Boolean.getBoolean("skyabyss.prof");
    private static final int WINDOW = 600;
    private static final Map<String, Stat> STATS = new TreeMap<>();

    private static final class Stat {
        long total;
        long max;
        int count;
        long alloc;
        // 場面の合計 (窓で 0 に戻らない。flush で出して 0 に戻す)
        long sTotal;
        long sMax;
        int sCount;
        long sAlloc;
    }

    private Prof() {
    }

    public static long begin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void end(String name, long start) {
        if (!ENABLED) {
            return;
        }
        record(name, System.nanoTime() - start);
    }

    private static void record(String name, long dt) {
        synchronized (STATS) {
            Stat s = STATS.computeIfAbsent(name, k -> new Stat());
            s.total += dt;
            s.sTotal += dt;
            s.sMax = Math.max(s.sMax, dt);
            s.sCount++;
            s.max = Math.max(s.max, dt);
            if (++s.count >= WINDOW) {
                SkyAbyss.LOGGER.info("[prof] {} avg {} ms  max {} ms  n={}  alloc {} KB/window", name,
                        String.format("%.4f", s.total / (double) s.count / 1e6), String.format("%.3f", s.max / 1e6), s.count, s.alloc / 1024);
                s.total = 0;
                s.max = 0;
                s.count = 0;
                s.alloc = 0;
            }
        }
    }

    // 1 フレームのうちに何度も呼ばれる区間 (骨ごとの描画など) 用。呼び出しごとに記録せず、フレームの終わりに合計を 1 サンプルとして記録する (描画スレッドだけ)
    private static final Map<String, long[]> FRAME_ACC = new java.util.HashMap<>();

    public static void acc(String name, long start) {
        if (!ENABLED) {
            return;
        }
        long[] a = FRAME_ACC.computeIfAbsent(name, k -> new long[1]);
        a[0] += System.nanoTime() - start;
    }

    private static void flushFrameAcc() {
        for (Map.Entry<String, long[]> e : FRAME_ACC.entrySet()) {
            if (e.getValue()[0] > 0) {
                record(e.getKey(), e.getValue()[0]);
                e.getValue()[0] = 0;
            }
        }
    }

    // ---- サンプラー: 2ms ごとにサーバー / 描画スレッドのスタックを撮り、mod のメソッドごとの「居た割合」を数える (flush で上位を書く)。コードに手を入れず全体の重いところを探す用
    private static final class Sampler {
        final Map<String, int[]> incl = new java.util.HashMap<>();
        final Map<String, int[]> leaf = new java.util.HashMap<>();
        int samples;
    }

    private static final Map<String, Sampler> SAMPLERS = new java.util.HashMap<>();
    private static boolean samplerStarted;

    private static synchronized void startSampler() {
        if (samplerStarted) {
            return;
        }
        samplerStarted = true;
        Thread th = new Thread(() -> {
            Map<String, Thread> targets = new java.util.HashMap<>();
            int round = 0;
            while (true) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
                if (round++ % 500 == 0) {
                    targets.clear();
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        if (t.getName().equals("Server thread") || t.getName().equals("Render thread")) {
                            targets.put(t.getName(), t);
                        }
                    }
                }
                for (Map.Entry<String, Thread> e : targets.entrySet()) {
                    String tn = e.getKey();
                    StackTraceElement[] st = e.getValue().getStackTrace();
                    if (st.length == 0) {
                        continue;
                    }
                    Sampler sm;
                    synchronized (SAMPLERS) {
                        sm = SAMPLERS.computeIfAbsent(tn, k -> new Sampler());
                        sm.samples++;
                        java.util.Set<String> seen = new java.util.HashSet<>();
                        String leafKey = null;
                        for (StackTraceElement f : st) {
                            if (f.getClassName().startsWith("jp.revati.skyabyss.") && !f.getClassName().startsWith("jp.revati.skyabyss.dev.Prof")) {
                                String k = f.getClassName().substring(19) + "." + f.getMethodName();
                                if (leafKey == null) {
                                    leafKey = k;
                                }
                                if (seen.add(k)) {
                                    sm.incl.computeIfAbsent(k, x -> new int[1])[0]++;
                                }
                            }
                        }
                        String lk = leafKey == null ? "(vanilla) " + st[0].getClassName() + "." + st[0].getMethodName() : leafKey;
                        sm.leaf.computeIfAbsent(lk, x -> new int[1])[0]++;
                    }
                }
            }
        }, "skyabyss-prof-sampler");
        th.setDaemon(true);
        th.start();
    }

    private static void flushSamplers(String tag) {
        synchronized (SAMPLERS) {
            for (Map.Entry<String, Sampler> e : SAMPLERS.entrySet()) {
                Sampler sm = e.getValue();
                if (sm.samples < 10) {
                    continue;
                }
                SkyAbyss.LOGGER.info("[prof] {} SAMPLER {} samples={}", tag, e.getKey(), sm.samples);
                for (String[] pair : new String[][] {{"incl", "inclusive"}, {"leaf", "leaf"}}) {
                    Map<String, int[]> src = pair[0].equals("incl") ? sm.incl : sm.leaf;
                    src.entrySet().stream().sorted((a, b) -> b.getValue()[0] - a.getValue()[0]).limit(pair[0].equals("incl") ? 22 : 14).forEach(x ->
                            SkyAbyss.LOGGER.info("[prof] {} S-{} {} {} {}%", tag, pair[1], e.getKey().substring(0, 1), x.getKey(), String.format("%.1f", 100.0 * x.getValue()[0] / sm.samples)));
                }
                sm.incl.clear();
                sm.leaf.clear();
                sm.samples = 0;
            }
        }
    }

    // いまの窓を途中でも書き出して 0 に戻す (台本の `prof <場面>`)。場面ごとの数字を取る用
    public static void flush(String tag) {
        if (!ENABLED) {
            return;
        }
        startSampler();
        flushSamplers(tag);
        synchronized (STATS) {
            for (Map.Entry<String, Stat> e : STATS.entrySet()) {
                Stat s = e.getValue();
                if (s.sCount > 0) {
                    SkyAbyss.LOGGER.info("[prof] {} {} avg {} ms  max {} ms  n={}  alloc {} KB/n", tag, e.getKey(),
                            String.format("%.4f", s.sTotal / (double) s.sCount / 1e6), String.format("%.3f", s.sMax / 1e6), s.sCount, s.sAlloc / 1024 / Math.max(1, s.sCount));
                }
                s.total = 0;
                s.max = 0;
                s.count = 0;
                s.alloc = 0;
                s.sTotal = 0;
                s.sMax = 0;
                s.sCount = 0;
                s.sAlloc = 0;
            }
        }
    }

    // いまのスレッドの確保バイト数 (addAlloc に渡す "前" の値)
    public static long allocNow() {
        if (!ENABLED || !(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean t)) {
            return 0L;
        }
        return t.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    // 区間に確保バイトを足す。end より前に呼ぶ
    public static void addAlloc(String name, long before) {
        if (!ENABLED) {
            return;
        }
        long d = allocNow() - before;
        synchronized (STATS) {
            Stat st = STATS.computeIfAbsent(name, k -> new Stat());
            st.alloc += d;
            st.sAlloc += d;
        }
    }

    private static long serverStart;
    private static long serverAlloc;
    private static long frameStart;
    private static long frameAlloc;

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    public static final class ServerHook {
        @SubscribeEvent
        static void pre(ServerTickEvent.Pre e) {
            if (ENABLED) {
                serverStart = System.nanoTime();
                serverAlloc = allocNow();
            }
        }

        @SubscribeEvent
        static void post(ServerTickEvent.Post e) {
            if (ENABLED) {
                addAlloc("server.tick", serverAlloc);
                end("server.tick", serverStart);
            }
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class ClientHook {
        @SubscribeEvent
        static void pre(RenderFrameEvent.Pre e) {
            if (ENABLED) {
                frameStart = System.nanoTime();
                frameAlloc = allocNow();
            }
        }

        @SubscribeEvent
        static void post(RenderFrameEvent.Post e) {
            if (ENABLED) {
                flushFrameAcc();
                addAlloc("client.frame", frameAlloc);
                end("client.frame", frameStart);
            }
        }
    }
}
