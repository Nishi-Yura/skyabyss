package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 店の売り買い (docs/design/35 の 3.1・3.3、00 の 223・224)。決めるのはすべてサーバー。画面 (client/facility/ShopScreen) は
// 並びと値段を映し、押した品の ID を返すだけ。押すたびに値段・手持ち・日の限りを調べ直し、払えなければ何もしない。
//   買う: 鐘銭と素材 (巨神の素材など) を払う。素材は持ち物と旅嚢の両方から (QuestItems)
//   売る: 店の買い取りの表の品を per 個ごとに鐘銭へ。印の付いた品 (クエストの品)・傷んだ品・名前を付けた品は売れない。旅嚢の中の品も売れる
//   店の人から 8 ブロック離れたら画面を閉じる (npc のない開き方 = コマンド・自己テストは問わない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ShopService {
    public static final double REACH = 8.0;

    record Session(String shop, int npcId) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ShopService() {
    }

    public static long day(ServerPlayer player) {
        return player.server.overworld().getDayTime() / 24000L;
    }

    // 開く (npc = 店の人。null = どこからでも)
    public static boolean open(ServerPlayer player, String shopId, @Nullable Entity npc) {
        ShopDef shop = ShopManager.shop(shopId);
        if (shop == null) {
            SkyAbyss.LOGGER.warn("[shop] no shop {}", shopId);
            return false;
        }
        SESSIONS.put(player.getUUID(), new Session(shopId, npc == null ? -1 : npc.getId()));
        send(player, view(player, shop));
        return true;
    }

    // 自己テスト用: いま開いている店
    @Nullable
    public static String openShop(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.shop();
    }

    static void closed(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    private static boolean reach(ServerPlayer player, Session s) {
        if (s.npcId() < 0) {
            return true;
        }
        Entity e = player.level().getEntity(s.npcId());
        return e != null && e.isAlive() && e.distanceToSqr(player) <= REACH * REACH;
    }

    // ---------------------------------------------------------------- 画面に映すもの
    public static FacilityNetwork.ShopView view(ServerPlayer player, ShopDef shop) {
        long day = day(player);
        int chapter = Coins.chapter(player);
        ShopLedger ledger = player.getData(FacilitySetup.LEDGER);
        List<FacilityNetwork.GoodView> goods = new ArrayList<>();
        for (ShopDef.Good g : shop.today(day)) {
            List<ItemStack> mats = new ArrayList<>();
            List<Integer> have = new ArrayList<>();
            for (ShopDef.Price p : g.materials()) {
                mats.add(new ItemStack(p.item(), p.count()));
                have.add(materialCount(player, p.item()));
            }
            int left = g.limit() <= 0 ? -1 : Math.max(0, g.limit() - ledger.bought(shop.id(), g.id(), day));
            boolean rotating = shop.pool().contains(g);
            goods.add(new FacilityNetwork.GoodView(g.id(), g.stack(), g.coins(), mats, have, left, chapter < g.chapter(), g.chapter(), rotating));
        }
        List<FacilityNetwork.SellView> sells = new ArrayList<>();
        for (int i = 0; i < shop.buys().size(); i++) {
            ShopDef.Buy b = shop.buys().get(i);
            sells.add(new FacilityNetwork.SellView(i, new ItemStack(b.item(), b.per()), b.coins(), sellableCount(player, b.item())));
        }
        return new FacilityNetwork.ShopView(shop.id(), shop.titleKey(), Coins.count(player), goods, sells);
    }

    static int materialCount(ServerPlayer player, Item item) {
        return QuestItems.count(player, BuiltInRegistries.ITEM.getKey(item).toString(), null);
    }

    // ---------------------------------------------------------------- 買う
    public enum Result { OK, NO_SHOP, FAR, NO_GOOD, LOCKED, SOLD_OUT, NO_COINS, NO_MATERIALS }

    public static Result buy(ServerPlayer player, String shopId, String goodId, int times) {
        Session s = SESSIONS.get(player.getUUID());
        ShopDef shop = ShopManager.shop(shopId);
        if (shop == null || s == null || !s.shop().equals(shopId)) {
            return Result.NO_SHOP;
        }
        if (!reach(player, s)) {
            close(player);
            return Result.FAR;
        }
        Result r = buy(player, shop, goodId, times);
        send(player, view(player, shop));
        return r;
    }

    // セッションを問わない中身 (自己テストもここを呼ぶ)
    public static Result buy(ServerPlayer player, ShopDef shop, String goodId, int times) {
        long day = day(player);
        ShopDef.Good g = shop.good(goodId, day);
        if (g == null) {
            return Result.NO_GOOD;
        }
        if (Coins.chapter(player) < g.chapter()) {
            return Result.LOCKED;
        }
        ShopLedger ledger = player.getData(FacilitySetup.LEDGER);
        int n = Math.max(1, Math.min(times, 64));
        if (g.limit() > 0) {
            n = Math.min(n, g.limit() - ledger.bought(shop.id(), g.id(), day));
            if (n <= 0) {
                Notice.hint(player, "message.skyabyss.shop.sold_out");
                return Result.SOLD_OUT;
            }
        }
        if (Coins.count(player) < g.coins() * n) {
            Notice.hint(player, "message.skyabyss.shop.no_coins");
            return Result.NO_COINS;
        }
        for (ShopDef.Price p : g.materials()) {
            if (materialCount(player, p.item()) < p.count() * n) {
                Notice.hint(player, "message.skyabyss.shop.no_materials", new ItemStack(p.item()).getHoverName());
                return Result.NO_MATERIALS;
            }
        }
        Coins.take(player, g.coins() * n);
        for (ShopDef.Price p : g.materials()) {
            QuestItems.take(player, BuiltInRegistries.ITEM.getKey(p.item()).toString(), null, p.count() * n);
        }
        for (int i = 0; i < n; i++) {
            Quests.give(player, g.stack());
        }
        if (g.limit() > 0) {
            ledger.add(shop.id(), g.id(), day, n);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
        jp.revati.skyabyss.advancement.Beats.fire(player, "shop:buy");
        return Result.OK;
    }

    // ---------------------------------------------------------------- 売る
    public static Result sell(ServerPlayer player, String shopId, int index, int times) {
        Session s = SESSIONS.get(player.getUUID());
        ShopDef shop = ShopManager.shop(shopId);
        if (shop == null || s == null || !s.shop().equals(shopId)) {
            return Result.NO_SHOP;
        }
        if (!reach(player, s)) {
            close(player);
            return Result.FAR;
        }
        Result r = sell(player, shop, index, times);
        send(player, view(player, shop));
        return r;
    }

    public static Result sell(ServerPlayer player, ShopDef shop, int index, int times) {
        if (index < 0 || index >= shop.buys().size()) {
            return Result.NO_GOOD;
        }
        ShopDef.Buy b = shop.buys().get(index);
        int lots = Math.min(Math.max(1, times), sellableCount(player, b.item()) / b.per());
        if (lots <= 0) {
            Notice.hint(player, "message.skyabyss.shop.nothing_to_sell");
            return Result.NO_MATERIALS;
        }
        takeSellable(player, b.item(), lots * b.per());
        Coins.give(player, lots * b.coins());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.5f, 1.1f);
        return Result.OK;
    }

    // 売れる品か (印のない・傷んでいない・名前を付けていない)
    static boolean plain(ItemStack s) {
        return !s.isEmpty() && QuestItems.token(s) == null && !s.isDamaged() && !s.has(DataComponents.CUSTOM_NAME);
    }

    public static int sellableCount(ServerPlayer player, Item item) {
        int n = Pouch.count(player, item);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && plain(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // 持ち物から先に、足りなければ旅嚢から
    private static void takeSellable(ServerPlayer player, Item item, int count) {
        int left = count;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && plain(s)) {
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
            }
        }
        if (left > 0) {
            Pouch.take(player, item, left);
        }
        inv.setChanged();
    }

    // ---------------------------------------------------------------- 送る・閉じる
    public static void close(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            send(player, new FacilityNetwork.CloseScreen());
        }
    }

    static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            Session s = SESSIONS.get(player.getUUID());
            if (s != null && (!player.isAlive() || !reach(player, s))) {
                close(player);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }
}
