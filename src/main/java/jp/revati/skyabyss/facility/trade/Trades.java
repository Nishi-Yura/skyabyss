package jp.revati.skyabyss.facility.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.facility.StashMenu;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 取引所の決まり (docs/design/35 の 6.11)。プレイヤーどうしの売り買い。決めるのはすべてここ (画面とコマンドはここを呼ぶ)。
//   出す: 出す品 (持ち物の枠から。4 枠まで) は出したときに預かる (取り下げ・期限切れで戻る)。ほしいものは品の型 (4 つまで) と鐘銭。一人 10 品まで、7 日で期限
//   申請: 出品のほしいもののまま、か、自分で選んだ品と鐘銭 (別の申し出)。申請しても品は動かない。一つの出品に一人一つ、一人 10 まで
//   受ける: 出す人が申請を一つ選ぶ。その瞬間に、申請した人の持ち物に申し出の品が全部あるか、両方の持ち物に入りきるかを調べ、
//           すべてそろえば一度に入れ替える (どれか欠ければ何も動かず、申請は残る)。申請した人がいない (オフライン) ときは受けられない
//   断る・取り下げる: 相手に知らせる (いなければ次に入ったとき)
//   品の合わせ方: 探して選んだ型 (部品のない品) は、その品なら何でも合う (付呪・名前・傷みがあってもよい。00 の 238)。
//                 持ち物から選んだ品 (部品のある品) は、同じ部品のものだけ (選んだ通りの品が渡る)
//   大事な品 (旅嚢の取り出せない品・守る品・クエストの印の品) は出せないし、ほしいものにもできない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Trades {
    private Trades() {
    }

    public static final int MAX_LISTINGS = 10;
    public static final int MAX_GIVE = 4;
    public static final int MAX_WANT = 4;
    public static final int MAX_APPS_PER_LISTING = 16;
    public static final int MAX_APPS_PER_PLAYER = 10;
    public static final int MAX_COINS = 9999;
    public static final int MAX_WANT_COUNT = 999;
    public static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;
    // 持ち物の本体 (ホットバーを含む 36 枠) だけを使う
    public static final int INV = 36;

    public enum Result {
        LISTED, APPLIED, ACCEPTED, REJECTED, WITHDRAWN, CANCELLED, CLAIMED, CLAIMED_PART,
        NOT_AT_KEEPER, TOO_MANY_LISTINGS, TOO_MANY_APPS, EMPTY_GIVE, EMPTY_WANT, TOO_MANY_ITEMS, NOT_ALLOWED, BAD_SLOT, LACK, NO_LISTING,
        OWN_LISTING, ALREADY_APPLIED, LISTING_FULL, NOT_SELLER, NO_APPLICATION, APPLICANT_AWAY, APPLICANT_LACKS, ROOM_APPLICANT, ROOM_SELLER,
        NOTHING_TO_CLAIM, EMPTY_OFFER;

        public String key() {
            return "message.skyabyss.trade." + name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean ok() {
            return ordinal() <= CLAIMED_PART.ordinal();
        }
    }

    // 枠と数 (持ち物の枠の番号 0..35)
    public record Pick(int slot, int count) {
    }

    // いる人を探す (自己テストは偽の人を足す)
    static BiFunction<MinecraftServer, UUID, ServerPlayer> online = (s, u) -> s.getPlayerList().getPlayer(u);

    @Nullable
    public static ServerPlayer online(MinecraftServer server, UUID who) {
        return online.apply(server, who);
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- 品の決まり
    // 出せる品・ほしいものにできる品か
    public static boolean allowed(ItemStack s) {
        return !s.isEmpty() && StashMenu.allowed(s) && QuestItems.token(s) == null;
    }

    public static ItemStack coins(int n) {
        return new ItemStack(FacilitySetup.BELL_COIN.get(), n);
    }

    public static boolean isCoin(ItemStack s) {
        return s.is(FacilitySetup.BELL_COIN.get());
    }

    // 同じ型をまとめる (数を足す)
    static List<ItemStack> combine(List<ItemStack> list) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : list) {
            if (s.isEmpty()) {
                continue;
            }
            boolean merged = false;
            for (ItemStack o : out) {
                if (ItemStack.isSameItemSameComponents(o, s)) {
                    o.setCount(o.getCount() + s.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                out.add(s.copyWithCount(s.getCount()));
            }
        }
        return out;
    }

    static List<ItemStack> main(ServerPlayer p) {
        return p.getInventory().items;
    }

    // 持ち物の品 s が型 t に合うか (型に部品がなければ品の種類だけ、あれば部品まで)
    static boolean matches(ItemStack s, ItemStack t) {
        return t.getComponentsPatch().isEmpty() ? ItemStack.isSameItem(s, t) : ItemStack.isSameItemSameComponents(s, t);
    }

    public static int count(List<ItemStack> inv, ItemStack template) {
        int n = 0;
        for (int i = 0; i < Math.min(INV, inv.size()); i++) {
            ItemStack s = inv.get(i);
            if (!s.isEmpty() && matches(s, template)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // 持ち物に型の品が全部そろっているか
    public static boolean hasAll(List<ItemStack> inv, List<ItemStack> templates) {
        for (ItemStack t : combine(templates)) {
            if (count(inv, t) < t.getCount()) {
                return false;
            }
        }
        return true;
    }

    // 取ったうえで入れたとき、入りきるか (持ち物の写しで試す)
    static boolean fits(List<ItemStack> inv, List<ItemStack> remove, List<ItemStack> add) {
        List<ItemStack> sim = new ArrayList<>();
        for (int i = 0; i < Math.min(INV, inv.size()); i++) {
            sim.add(inv.get(i).copy());
        }
        for (ItemStack t : combine(remove)) {
            int left = t.getCount();
            for (int i = 0; i < sim.size() && left > 0; i++) {
                ItemStack s = sim.get(i);
                if (!s.isEmpty() && matches(s, t)) {
                    int k = Math.min(left, s.getCount());
                    s.shrink(k);
                    left -= k;
                }
            }
        }
        for (ItemStack a : add) {
            int left = a.getCount();
            int max = a.getMaxStackSize();
            for (int i = 0; i < sim.size() && left > 0; i++) {
                ItemStack s = sim.get(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, a) && s.getCount() < max) {
                    int k = Math.min(left, max - s.getCount());
                    s.grow(k);
                    left -= k;
                }
            }
            for (int i = 0; i < sim.size() && left > 0; i++) {
                if (sim.get(i).isEmpty()) {
                    int k = Math.min(left, max);
                    sim.set(i, a.copyWithCount(k));
                    left -= k;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    // 持ち物から型の品を取る (そろっていることは先に調べておく)。取った品 (本当の品) を返す
    static List<ItemStack> take(ServerPlayer p, List<ItemStack> templates) {
        List<ItemStack> taken = new ArrayList<>();
        List<ItemStack> inv = main(p);
        for (ItemStack t : combine(templates)) {
            int left = t.getCount();
            for (int i = 0; i < Math.min(INV, inv.size()) && left > 0; i++) {
                ItemStack s = inv.get(i);
                if (!s.isEmpty() && matches(s, t)) {
                    int k = Math.min(left, s.getCount());
                    taken.add(s.split(k));
                    left -= k;
                }
            }
        }
        p.getInventory().setChanged();
        return taken;
    }

    // 持ち物へ入れる (入りきらない分は受け取り箱へ)。重なる数を越える型は分けて入れる
    static void put(ServerPlayer p, List<ItemStack> items) {
        TradeData data = TradeData.get(p.server);
        for (ItemStack s0 : items) {
            int left = s0.getCount();
            while (left > 0) {
                int k = Math.min(left, s0.getMaxStackSize());
                ItemStack s = s0.copyWithCount(k);
                p.getInventory().add(s);
                if (!s.isEmpty()) {
                    data.addMail(p.getUUID(), s);
                }
                left -= k;
            }
        }
        p.getInventory().setChanged();
    }

    // 重なる数ごとに分けた品 (受け取り箱・入れる試し用)
    static List<ItemStack> split(List<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) {
            int left = s.getCount();
            while (left > 0) {
                int k = Math.min(left, s.getMaxStackSize());
                out.add(s.copyWithCount(k));
                left -= k;
            }
        }
        return out;
    }

    // 枠から選んだ品 (同じ枠は足す)。足りない・出せない品なら null と理由
    private record Picked(List<ItemStack> stacks, List<Pick> picks, @Nullable Result error) {
    }

    private static Picked pick(ServerPlayer p, List<Pick> raw) {
        List<Pick> picks = new ArrayList<>();
        for (Pick k : raw) {
            if (k.slot() < 0 || k.slot() >= INV || k.count() <= 0) {
                return new Picked(List.of(), List.of(), Result.BAD_SLOT);
            }
            boolean merged = false;
            for (int i = 0; i < picks.size(); i++) {
                if (picks.get(i).slot() == k.slot()) {
                    picks.set(i, new Pick(k.slot(), picks.get(i).count() + k.count()));
                    merged = true;
                }
            }
            if (!merged) {
                picks.add(k);
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Pick k : picks) {
            ItemStack s = main(p).get(k.slot());
            if (s.isEmpty() || s.getCount() < k.count()) {
                return new Picked(List.of(), List.of(), Result.LACK);
            }
            if (!allowed(s)) {
                return new Picked(List.of(), List.of(), Result.NOT_ALLOWED);
            }
            stacks.add(s.copyWithCount(k.count()));
        }
        return new Picked(stacks, picks, null);
    }

    // ほしいもの (探して選んだ型。まっさらの品) と鐘銭から型の一覧を作る。出せない品なら null
    @Nullable
    static List<ItemStack> wants(List<Item> items, List<Integer> counts, int coins) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int n = i < counts.size() ? counts.get(i) : 1;
            if (it == null || it == Items.AIR || n <= 0) {
                continue;
            }
            ItemStack s = new ItemStack(it, Math.min(MAX_WANT_COUNT, n));
            if (!allowed(s)) {
                return null;
            }
            out.add(s);
        }
        out = combine(out);
        if (coins > 0) {
            out.add(coins(Math.min(MAX_COINS, coins)));
            out = combine(out);
        }
        return out;
    }

    // ---------------------------------------------------------------- 出す
    public static Result list(ServerPlayer seller, List<Pick> give, List<Item> wantItems, List<Integer> wantCounts, int coins) {
        TradeData data = TradeData.get(seller.server);
        if (data.of(seller.getUUID()).size() >= MAX_LISTINGS) {
            return Result.TOO_MANY_LISTINGS;
        }
        if (give.isEmpty()) {
            return Result.EMPTY_GIVE;
        }
        Picked p = pick(seller, give);
        if (p.error() != null) {
            return p.error();
        }
        if (p.picks().size() > MAX_GIVE || wantItems.size() > MAX_WANT) {
            return Result.TOO_MANY_ITEMS;
        }
        List<ItemStack> want = wants(wantItems, wantCounts, coins);
        if (want == null) {
            return Result.NOT_ALLOWED;
        }
        if (want.isEmpty()) {
            return Result.EMPTY_WANT;
        }
        // 預かる (枠から取る)
        for (Pick k : p.picks()) {
            main(seller).get(k.slot()).shrink(k.count());
        }
        seller.getInventory().setChanged();
        long now = now();
        TradeData.Listing l = data.create(seller.getUUID(), seller.getGameProfile().getName(), now, now + EXPIRE_MS);
        l.give.addAll(p.stacks());
        l.want.addAll(want);
        data.dirty();
        SkyAbyss.LOGGER.info("[trade] {} listed #{}", seller.getGameProfile().getName(), l.id);
        return Result.LISTED;
    }

    // 出す人が取り下げる (預けた品は持ち物へ。入りきらない分は受け取り箱)
    public static Result cancel(ServerPlayer seller, int id) {
        TradeData data = TradeData.get(seller.server);
        TradeData.Listing l = data.byId(id);
        if (l == null) {
            return Result.NO_LISTING;
        }
        if (!l.seller.equals(seller.getUUID())) {
            return Result.NOT_SELLER;
        }
        data.remove(l);
        put(seller, l.give);
        for (TradeData.Application a : l.apps.values()) {
            tell(seller.server, a.applicant, "message.skyabyss.trade.note_cancelled", l.sellerName, "#" + l.id);
        }
        return Result.CANCELLED;
    }

    // ---------------------------------------------------------------- 申請
    // offer = null ならほしいもののまま。そうでなければ自分で選んだ品 (枠) と鐘銭
    public static Result apply(ServerPlayer applicant, int id, @Nullable List<Pick> offer, int coins) {
        TradeData data = TradeData.get(applicant.server);
        TradeData.Listing l = data.byId(id);
        if (l == null) {
            return Result.NO_LISTING;
        }
        if (l.seller.equals(applicant.getUUID())) {
            return Result.OWN_LISTING;
        }
        if (l.apps.containsKey(applicant.getUUID())) {
            return Result.ALREADY_APPLIED;
        }
        if (l.apps.size() >= MAX_APPS_PER_LISTING) {
            return Result.LISTING_FULL;
        }
        if (data.appliedBy(applicant.getUUID()).size() >= MAX_APPS_PER_PLAYER) {
            return Result.TOO_MANY_APPS;
        }
        List<ItemStack> items;
        if (offer == null) {
            items = new ArrayList<>();
            for (ItemStack s : l.want) {
                items.add(s.copy());
            }
        } else {
            Picked p = pick(applicant, offer);
            if (p.error() != null) {
                return p.error();
            }
            if (p.picks().size() > MAX_WANT) {
                return Result.TOO_MANY_ITEMS;
            }
            items = new ArrayList<>(p.stacks());
            if (coins > 0) {
                items.add(coins(Math.min(MAX_COINS, coins)));
            }
            items = combine(items);
            if (items.isEmpty()) {
                return Result.EMPTY_OFFER;
            }
        }
        // 申請のときは持っているかだけ見る (取らない)
        if (!holds(applicant, items)) {
            return Result.LACK;
        }
        TradeData.Application a = new TradeData.Application(applicant.getUUID());
        a.name = applicant.getGameProfile().getName();
        a.time = now();
        a.offer.addAll(items);
        l.apps.put(a.applicant, a);
        data.dirty();
        tell(applicant.server, l.seller, "message.skyabyss.trade.note_applied", a.name, "#" + l.id);
        return Result.APPLIED;
    }

    // 申請した人が取り下げる
    public static Result withdraw(ServerPlayer applicant, int id) {
        TradeData data = TradeData.get(applicant.server);
        TradeData.Listing l = data.byId(id);
        if (l == null || l.apps.remove(applicant.getUUID()) == null) {
            return Result.NO_APPLICATION;
        }
        data.dirty();
        tell(applicant.server, l.seller, "message.skyabyss.trade.note_withdrawn", applicant.getGameProfile().getName(), "#" + l.id);
        return Result.WITHDRAWN;
    }

    // 出す人が断る
    public static Result reject(ServerPlayer seller, int id, UUID applicant) {
        TradeData data = TradeData.get(seller.server);
        TradeData.Listing l = data.byId(id);
        if (l == null) {
            return Result.NO_LISTING;
        }
        if (!l.seller.equals(seller.getUUID())) {
            return Result.NOT_SELLER;
        }
        if (l.apps.remove(applicant) == null) {
            return Result.NO_APPLICATION;
        }
        data.dirty();
        tell(seller.server, applicant, "message.skyabyss.trade.note_rejected", l.sellerName, "#" + l.id);
        return Result.REJECTED;
    }

    // 出す人が受ける (一度に入れ替える。そろわなければ何も動かさない)
    public static Result accept(ServerPlayer seller, int id, UUID applicantId) {
        MinecraftServer server = seller.server;
        TradeData data = TradeData.get(server);
        TradeData.Listing l = data.byId(id);
        if (l == null) {
            return Result.NO_LISTING;
        }
        if (!l.seller.equals(seller.getUUID())) {
            return Result.NOT_SELLER;
        }
        TradeData.Application a = l.apps.get(applicantId);
        if (a == null) {
            return Result.NO_APPLICATION;
        }
        ServerPlayer buyer = online(server, applicantId);
        if (buyer == null) {
            return Result.APPLICANT_AWAY;
        }
        if (!holds(buyer, a.offer)) {
            tell(server, applicantId, "message.skyabyss.trade.note_lacked", l.sellerName, "#" + l.id);
            return Result.APPLICANT_LACKS;
        }
        stageCoins(buyer, a.offer);
        if (!fits(main(buyer), a.offer, split(l.give))) {
            tell(server, applicantId, "message.skyabyss.trade.note_no_room", l.sellerName, "#" + l.id);
            return Result.ROOM_APPLICANT;
        }
        if (!fits(main(seller), List.of(), split(a.offer))) {
            return Result.ROOM_SELLER;
        }
        // ここから先は失敗しない (同じ手の中で、調べたとおりに動かす)
        data.remove(l);
        List<ItemStack> paid = take(buyer, a.offer);
        put(buyer, l.give);
        put(seller, combine(paid));
        data.dirty();
        jp.revati.skyabyss.advancement.Beats.fire(seller, "trade:done");
        jp.revati.skyabyss.advancement.Beats.fire(buyer, "trade:done");
        Notice.gain(seller, "message.skyabyss.trade.note_sold", a.name, "#" + l.id);
        Notice.gain(buyer, "message.skyabyss.trade.note_accepted", l.sellerName, "#" + l.id);
        TradeService.refresh(buyer);
        recent(seller.getUUID(), "message.skyabyss.trade.note_sold");
        recent(applicantId, "message.skyabyss.trade.note_accepted");
        seller.level().playSound(null, seller.getX(), seller.getY(), seller.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.3f);
        buyer.level().playSound(null, buyer.getX(), buyer.getY(), buyer.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.3f);
        for (TradeData.Application other : l.apps.values()) {
            if (!other.applicant.equals(applicantId)) {
                tell(server, other.applicant, "message.skyabyss.trade.note_taken", l.sellerName, "#" + l.id);
            }
        }
        SkyAbyss.LOGGER.info("[trade] #{} {} -> {}", l.id, l.sellerName, a.name);
        return Result.ACCEPTED;
    }

    // ---------------------------------------------------------------- 受け取り箱
    public static Result claim(ServerPlayer p) {
        TradeData data = TradeData.get(p.server);
        List<ItemStack> box = data.mail.get(p.getUUID());
        if (box == null || box.isEmpty()) {
            return Result.NOTHING_TO_CLAIM;
        }
        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack s : box) {
            ItemStack c = s.copy();
            p.getInventory().add(c);
            if (!c.isEmpty()) {
                keep.add(c);
            }
        }
        p.getInventory().setChanged();
        if (keep.isEmpty()) {
            data.mail.remove(p.getUUID());
        } else {
            data.mail.put(p.getUUID(), keep);
        }
        data.dirty();
        return keep.isEmpty() ? Result.CLAIMED : Result.CLAIMED_PART;
    }

    // ---------------------------------------------------------------- 期限
    // 期限の切れた出品を閉じる (品は出す人の受け取り箱へ)。閉じた数
    public static int expire(MinecraftServer server, long now) {
        TradeData data = TradeData.get(server);
        int n = 0;
        for (TradeData.Listing l : data.all()) {
            if (l.expires > now) {
                continue;
            }
            data.remove(l);
            for (ItemStack s : split(l.give)) {
                data.addMail(l.seller, s);
            }
            tell(server, l.seller, "message.skyabyss.trade.note_expired", "#" + l.id);
            for (TradeData.Application a : l.apps.values()) {
                tell(server, a.applicant, "message.skyabyss.trade.note_closed", l.sellerName, "#" + l.id);
            }
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- 知らせ
    // いる人にはすぐ、いなければ次に入ったときに
    static void tell(MinecraftServer server, UUID who, String key, String... args) {
        ServerPlayer p = online(server, who);
        if (p != null) {
            Notice.hint(p, key, (Object[]) args);
            TradeService.refresh(p);
        } else {
            TradeData.get(server).addNote(who, key, List.of(args));
        }
        recent(who, key);
    }

    // 自己テスト用: 最近の知らせ (人と鍵)
    static final List<String> RECENT = new ArrayList<>();

    static void recent(UUID who, String key) {
        RECENT.add(who + " " + key);
        while (RECENT.size() > 64) {
            RECENT.remove(0);
        }
    }

    // 入ったとき: たまった知らせを出し、受け取り箱に品があれば一言
    public static int flush(ServerPlayer p) {
        TradeData data = TradeData.get(p.server);
        List<TradeData.Note> list = data.takeNotes(p.getUUID());
        for (TradeData.Note n : list) {
            Notice.hint(p, n.key(), n.args().toArray());
        }
        if (!data.mail(p.getUUID()).isEmpty()) {
            Notice.hint(p, "message.skyabyss.trade.mail_waiting");
        }
        return list.size();
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            expire(p.server, now());
            flush(p);
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 1200 == 0) {
            expire(event.getServer(), now());
        }
    }

    // 画面の「払える」: 持ち物に型の品がそろっているか
    public static boolean canPay(ServerPlayer p, List<ItemStack> templates) {
        return holds(p, templates);
    }

    // 型の品がそろっているか。鐘銭は旅嚢の中も数える (2026-10-25: 鐘銭は袋にも入る)
    static boolean holds(ServerPlayer p, List<ItemStack> templates) {
        for (ItemStack t : combine(templates)) {
            int have = isCoin(t) ? jp.revati.skyabyss.facility.Coins.count(p) : count(main(p), t);
            if (have < t.getCount()) {
                return false;
            }
        }
        return true;
    }

    // 払う前に、持ち物で足りない鐘銭を旅嚢から持ち物へ出す (取るのは持ち物から)
    static void stageCoins(ServerPlayer p, List<ItemStack> templates) {
        int need = 0;
        for (ItemStack t : combine(templates)) {
            if (isCoin(t)) {
                need += t.getCount();
            }
        }
        int lack = need - count(main(p), coins(1));
        if (lack <= 0) {
            return;
        }
        int got = jp.revati.skyabyss.pouch.Pouch.take(p, FacilitySetup.BELL_COIN.get(), lack);
        while (got > 0) {
            int k = Math.min(got, coins(1).getMaxStackSize());
            ItemStack c = coins(k);
            p.getInventory().add(c);
            if (!c.isEmpty()) {
                jp.revati.skyabyss.pouch.Pouch.add(p, c);
            }
            got -= k;
        }
    }
}
