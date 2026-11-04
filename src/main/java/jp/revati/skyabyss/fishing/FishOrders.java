package jp.revati.skyabyss.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// 魚の注文 (docs/design/54 の 6.2.4.1。2026-11-05 の封の箱の頁 2-1)。町の食べ物屋に「魚の注文を見る・納める」。
//   日替わり: その町の土地の魚 (ふつう・ときどき) 1 種 × 1〜3 匹 (日と土地で決まる。全員同じ)
//   週替わり: その土地のまれな魚 1 種 × 1 匹
//   納めると、買い取りの値 (Fish.Species.sell) の 3 倍 × 匹数の鐘銭と、土地の名声 +2 (週の注文は +5)。人ごと・土地ごとに日に一度 / 週に一度
//   どの大きさでもよい (小さい順に渡す)。都の食べ物屋は都のまわりの魚の注文
public final class FishOrders {
    public static final int PAY = 3;
    public static final int FAME = 2;
    public static final int WEEK_FAME = 5;
    public static final String DAY_KEY = "skyabyss_fish_order_day_";
    public static final String WEEK_KEY = "skyabyss_fish_order_week_";

    public record Order(Fish.Species species, int count, boolean weekly) {
        public int pay() {
            return species.sell() * PAY * count;
        }
    }

    private FishOrders() {
    }

    public static Order daily(int land, long day) {
        List<Fish.Species> pool = new ArrayList<>(Fish.of(land, 'C'));
        pool.addAll(Fish.of(land, 'U'));
        Random r = new Random(day * 0x9E3779B97F4A7C15L + land * 7919L);
        Fish.Species s = pool.isEmpty() ? Fish.of(ContinentMap.MEADOW, 'C').get(0) : pool.get(r.nextInt(pool.size()));
        return new Order(s, 1 + r.nextInt(3), false);
    }

    public static Order weekly(int land, long week) {
        List<Fish.Species> pool = Fish.of(land, 'R');
        if (pool.isEmpty()) {
            pool = Fish.of(land, 'U');
        }
        Random r = new Random(week * 0x632BE59BD9B4E019L + land * 104729L);
        Fish.Species s = pool.isEmpty() ? Fish.of(ContinentMap.MEADOW, 'U').get(0) : pool.get(r.nextInt(pool.size()));
        return new Order(s, 1, true);
    }

    static long today() {
        return jp.revati.skyabyss.util.DayClock.today().toEpochDay();
    }

    static long thisWeek() {
        return jp.revati.skyabyss.tower.TowerWagers.week().toEpochDay();
    }

    // 食べ物屋のいる所の土地 (釣りの土地。外れは都のまわり)
    public static int landAt(@Nullable Entity npc, ServerPlayer p) {
        int x = npc != null ? npc.getBlockX() : p.getBlockX();
        int z = npc != null ? npc.getBlockZ() : p.getBlockZ();
        ContinentMap map = ContinentMap.get();
        return Fish.fishingRegion(map.inside(x, z) ? map.region(x, z) : ContinentMap.MEADOW);
    }

    // 食べ物屋に話した: 注文を見せ、持っていれば納める
    public static void talk(ServerPlayer p, @Nullable Entity npc) {
        int land = landAt(npc, p);
        Order d = daily(land, today());
        Order w = weekly(land, thisWeek());
        boolean dDone = String.valueOf(today()).equals(p.getPersistentData().getString(DAY_KEY + land));
        boolean wDone = String.valueOf(thisWeek()).equals(p.getPersistentData().getString(WEEK_KEY + land));
        if (!dDone && deliver(p, d)) {
            p.getPersistentData().putString(DAY_KEY + land, String.valueOf(today()));
            reward(p, land, d);
            dDone = true;
        }
        if (!wDone && deliver(p, w)) {
            p.getPersistentData().putString(WEEK_KEY + land, String.valueOf(thisWeek()));
            reward(p, land, w);
            wDone = true;
        }
        Notice.hint(p, dDone ? "message.skyabyss.fish_order.daily_done" : "message.skyabyss.fish_order.daily", name(d), d.count(), d.pay());
        Notice.hint(p, wDone ? "message.skyabyss.fish_order.weekly_done" : "message.skyabyss.fish_order.weekly", name(w), w.count(), w.pay());
    }

    static Component name(Order o) {
        return Component.translatable("item.skyabyss." + o.species().itemId());
    }

    // 持ち物にその魚が count 匹あれば、小さい順に取って true
    public static boolean deliver(ServerPlayer p, Order o) {
        var inv = p.getInventory();
        net.minecraft.world.item.Item item = FishingSetup.FISH.get(o.species().id()).get();
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                have += inv.getItem(i).getCount();
            }
        }
        if (have < o.count()) {
            return false;
        }
        int left = o.count();
        while (left > 0) {
            int best = -1;
            int bestSize = Integer.MAX_VALUE;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item)) {
                    int mm = s.getOrDefault(FishingSetup.FISH_SIZE.get(), 0);
                    if (mm < bestSize) {
                        bestSize = mm;
                        best = i;
                    }
                }
            }
            if (best < 0) {
                return false;
            }
            inv.getItem(best).shrink(1);
            left--;
        }
        inv.setChanged();
        return true;
    }

    static void reward(ServerPlayer p, int land, Order o) {
        Coins.give(p, o.pay());
        if (land >= ContinentMap.WHALE && land <= ContinentMap.EMBER) {
            jp.revati.skyabyss.endgame.RegionPoints.add(p, land, o.weekly() ? WEEK_FAME : FAME);
        }
        Notice.gain(p, o.weekly() ? "message.skyabyss.fish_order.weekly_paid" : "message.skyabyss.fish_order.daily_paid", name(o), o.count(), o.pay());
        p.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6f, 1.2f);
    }
}
