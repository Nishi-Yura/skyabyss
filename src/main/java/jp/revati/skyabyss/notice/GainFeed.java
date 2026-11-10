package jp.revati.skyabyss.notice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 手に入れた品を、左の列に品の絵で出す (2026-11-10 の声「手に入れたアイテムはすべて左にアイテムのアイコンで」)。
// 品を渡す口 (quest/Quests.give・facility/Coins.give など) が note で知らせ、同じ tick の分を一人ずつまとめて一度に送る
// (宝箱や報酬で何品も渡しても知らせは一つ。同じ品は数を足す)。
// 渡す所が自分で絵つきの知らせ (Notice.gainItems) を出すときは二重にしない: まとめに入っている品はそちらを送らず、先に送った分はまとめから引く
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GainFeed {
    private static final Map<UUID, Map<Item, ItemStack>> PENDING = new HashMap<>();
    private static final Map<UUID, Map<Item, Integer>> SENT = new HashMap<>();
    private static int muted;

    private GainFeed() {
    }

    // 渡した品を知らせる (stack は写しを取る。渡す前に呼ぶ: 渡すと数が減る)
    public static void note(ServerPlayer p, ItemStack stack) {
        if (muted > 0 || stack.isEmpty() || p instanceof FakePlayer || p.connection == null) {
            return;
        }
        Map<Item, ItemStack> map = PENDING.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
        ItemStack have = map.get(stack.getItem());
        if (have == null) {
            map.put(stack.getItem(), stack.copy());
        } else {
            have.grow(stack.getCount());
        }
    }

    // 中で呼ぶ渡しを数えない (まとめて一度 note したあとの、小分けの渡し)
    public static void quietly(Runnable give) {
        muted++;
        try {
            give.run();
        } finally {
            muted--;
        }
    }

    // Notice.gainItems から: この品がみな、まとめに入っているか (入っていれば、まとめが出すので送らない)
    static boolean covers(ServerPlayer p, List<ItemStack> items) {
        Map<Item, ItemStack> map = PENDING.get(p.getUUID());
        if (map == null || items.isEmpty()) {
            return false;
        }
        for (ItemStack s : items) {
            if (!map.containsKey(s.getItem())) {
                return false;
            }
        }
        return true;
    }

    // Notice.gainItems が送った分 (あとから同じ tick に note されても、まとめから引く)
    static void sent(ServerPlayer p, List<ItemStack> items) {
        Map<Item, Integer> map = SENT.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        for (ItemStack s : items) {
            map.merge(s.getItem(), s.getCount(), Integer::sum);
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            SENT.clear();
            return;
        }
        for (var e : PENDING.entrySet()) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || p.connection == null) {
                continue;
            }
            Map<Item, Integer> sent = SENT.getOrDefault(e.getKey(), Map.of());
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack s : e.getValue().values()) {
                int n = s.getCount() - sent.getOrDefault(s.getItem(), 0);
                if (n > 0) {
                    list.add(s.copyWithCount(n));
                }
            }
            if (list.isEmpty()) {
                continue;
            }
            if (list.size() > 64) {
                list = list.subList(0, 64);
            }
            PacketDistributor.sendToPlayer(p, new GainItemsPayload(List.copyOf(list), jp.revati.skyabyss.loot.TitanLoot.rewardLine(list)));
        }
        PENDING.clear();
        SENT.clear();
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }
}
