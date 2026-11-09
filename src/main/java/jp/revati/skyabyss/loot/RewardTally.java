package jp.revati.skyabyss.loot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 渡した報酬の控え (人ごと、少しのあいだ)。報いの祭壇 (rpg/aspect/AspectNet) が、心臓のあとに手に入れた箱・鐘銭・素材も並べるのに使う
// (2026-11-10 の声「アスペクトだけじゃなくて、素材とかも最後手に入るのみえるようにしたい」)。保存しない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RewardTally {
    // 覚えておく長さ (server の tick)
    static final int KEEP = 20 * 60;

    private record Entry(long tick, ItemStack stack) {
    }

    private static final Map<UUID, Deque<Entry>> LOG = new ConcurrentHashMap<>();

    private RewardTally() {
    }

    public static void record(UUID who, long tick, List<ItemStack> items) {
        Deque<Entry> q = LOG.computeIfAbsent(who, k -> new ArrayDeque<>());
        synchronized (q) {
            for (ItemStack s : items) {
                if (!s.isEmpty()) {
                    q.addLast(new Entry(tick, s.copy()));
                }
            }
            while (!q.isEmpty() && q.peekFirst().tick() < tick - KEEP) {
                q.removeFirst();
            }
        }
    }

    // from の tick から後に渡したもの (同じ品はまとめる)
    public static List<ItemStack> since(UUID who, long from) {
        List<ItemStack> out = new ArrayList<>();
        Deque<Entry> q = LOG.get(who);
        if (q == null) {
            return out;
        }
        synchronized (q) {
            for (Entry e : q) {
                if (e.tick() < from) {
                    continue;
                }
                boolean merged = false;
                for (ItemStack o : out) {
                    if (ItemStack.isSameItemSameComponents(o, e.stack())) {
                        o.grow(e.stack().getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    out.add(e.stack().copy());
                }
            }
        }
        return out;
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        LOG.clear();
    }
}
