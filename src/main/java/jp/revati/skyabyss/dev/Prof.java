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
        long dt = System.nanoTime() - start;
        synchronized (STATS) {
            Stat s = STATS.computeIfAbsent(name, k -> new Stat());
            s.total += dt;
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
            STATS.computeIfAbsent(name, k -> new Stat()).alloc += d;
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
                addAlloc("client.frame", frameAlloc);
                end("client.frame", frameStart);
            }
        }
    }
}
