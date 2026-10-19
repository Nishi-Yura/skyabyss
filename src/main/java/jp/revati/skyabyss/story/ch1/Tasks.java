package jp.revati.skyabyss.story.ch1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 少しあとにやる小さな仕事 (歌石の数え鳴らし、遅れて応える鐘など)。保存しない (再起動で消えても、物語は止まらない作りにする)。サーバーの tick が数える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Tasks {
    private record Task(long at, Runnable run) {
    }

    private static final List<Task> QUEUE = new ArrayList<>();
    private static long now;

    private Tasks() {
    }

    // delay tick あとに run を呼ぶ (サーバースレッド)
    public static synchronized void later(int delay, Runnable run) {
        QUEUE.add(new Task(now + Math.max(0, delay), run));
    }

    // 自己テスト用: いま溜まっている数
    public static synchronized int pending() {
        return QUEUE.size();
    }

    // 自己テスト用: 全部いますぐ流す
    public static synchronized void flush() {
        List<Task> all = new ArrayList<>(QUEUE);
        QUEUE.clear();
        all.forEach(t -> t.run().run());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        List<Runnable> due = List.of();
        synchronized (Tasks.class) {
            now++;
            if (QUEUE.isEmpty()) {
                return;
            }
            due = new ArrayList<>();
            Iterator<Task> it = QUEUE.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.at() <= now) {
                    due.add(t.run());
                    it.remove();
                }
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("[ch1] a delayed task failed", e);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        synchronized (Tasks.class) {
            QUEUE.clear();
        }
    }
}
