package jp.revati.skyabyss.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchConfig;
import jp.revati.skyabyss.pouch.PouchEntries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 初回の手引き (docs/design/18 の 12 章)。「品・出来事 → 始まる → 画面の隅に操作の案内が出続ける → した → できた」を一つの仕組みにしたもの。
//   登録 (Hint): ID・始まる品 (持っていたら始まる。旅嚢の中も数える)。品でなければ出来事で start を呼ぶ。済みの出来事は done を呼ぶ
//   サーバーは「今出ている手引きの ID」だけを持ち主に送る。文面と実際のキー名はクライアントが今のキー設定から作る (client/tutorial)
//   記録は人ごと (TutorialData。保存・死んでも引き継ぐ)。済んだものは二度と出ない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TutorialHints {
    private static final String PROTOCOL_VERSION = "tutorial-1";
    // 持ち物の見回りの間隔 (tick)
    public static final int CHECK_TICKS = 20;

    // item: 持っていたら (持ち物・旅嚢) 始まる品 (なければ出来事で始まる)
    public record Hint(String id, @Nullable Predicate<Item> item) {
    }

    public static final String LORE_BOOK = "lore_book";
    public static final String LORE_PAGE = "lore_page";
    public static final String POUCH = "pouch";
    public static final String WHISTLE = "whistle";
    public static final String RECORD = "record";
    public static final String PING = "ping";

    // 並びが画面の優先 (先のものが上に出る)
    public static final List<Hint> ALL = List.of(
            new Hint(POUCH, null),
            new Hint(LORE_BOOK, i -> i instanceof jp.revati.skyabyss.lore.LoreBookItem),
            new Hint(LORE_PAGE, i -> i instanceof jp.revati.skyabyss.lore.LorePageItem),
            // 呼び笛の手引きは出さない (ユーザー判断: 左の札で十分。強制しない)
            new Hint(RECORD, null),
            new Hint(PING, null));

    // 最後に送った中身 (変わったときだけ送る)
    private static final Map<UUID, List<String>> SENT = new ConcurrentHashMap<>();

    private TutorialHints() {
    }

    @Nullable
    public static Hint byId(String id) {
        for (Hint h : ALL) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return null;
    }

    // 出来事で始める。初めてなら true
    public static boolean start(ServerPlayer player, String id) {
        if (byId(id) == null || !TutorialData.of(player).start(id)) {
            return false;
        }
        sync(player, List.of());
        return true;
    }

    // 済ませる (した)。出ていたなら true。出ていなくても済みにする (先にした人には出さない)
    public static boolean done(ServerPlayer player, String id) {
        if (byId(id) == null) {
            return false;
        }
        boolean was = TutorialData.of(player).finish(id);
        if (was) {
            sync(player, List.of(id));
        }
        return was;
    }

    // 今出ている手引き (優先の並び)
    public static List<String> active(ServerPlayer player) {
        TutorialData d = TutorialData.of(player);
        List<String> out = new ArrayList<>();
        for (Hint h : ALL) {
            if (d.isActive(h.id())) {
                out.add(h.id());
            }
        }
        return out;
    }

    // 持ち物の見回り: 品を持っていたら始める。始まったら true
    public static boolean scan(ServerPlayer player) {
        TutorialData d = TutorialData.of(player);
        boolean started = false;
        for (Hint h : ALL) {
            if (h.item() != null && !d.isDone(h.id()) && !d.isActive(h.id()) && holds(player, h.item())) {
                started |= d.start(h.id());
            }
        }
        if (!d.isDone(POUCH) && !d.isActive(POUCH) && holdsPouchItem(player)) {
            started |= d.start(POUCH);
        }
        if (started) {
            sync(player, List.of());
        }
        return started;
    }

    private static boolean holds(ServerPlayer player, Predicate<Item> match) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && match.test(inv.getItem(i).getItem())) {
                return true;
            }
        }
        for (var e : PouchEntries.all()) {
            if (match.test(e.item()) && Pouch.count(player, e.item()) > 0) {
                return true;
            }
        }
        return false;
    }

    // 旅嚢に何か入った (旅嚢が有効なとき)
    private static boolean holdsPouchItem(ServerPlayer player) {
        if (!PouchConfig.enabled(player.server)) {
            return false;
        }
        for (var e : PouchEntries.enabled()) {
            if (Pouch.count(player, e.item()) > 0) {
                return true;
            }
        }
        return false;
    }

    // 持ち主へ今の状態を送る。変わっていなければ送らない (newlyDone があれば必ず送る)
    public static void sync(ServerPlayer player, List<String> newlyDone) {
        if (player.connection == null || player instanceof FakePlayer) {
            return;
        }
        List<String> act = active(player);
        if (newlyDone.isEmpty() && act.equals(SENT.get(player.getUUID()))) {
            return;
        }
        SENT.put(player.getUUID(), act);
        PacketDistributor.sendToPlayer(player, new Sync(act, newlyDone));
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && p.isAlive()) {
            int phase = (p.getUUID().hashCode() & 0x7fffffff) % CHECK_TICKS;
            if (p.tickCount % CHECK_TICKS == phase) {
                scan(p);
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        resend(event.getEntity());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resend(event.getEntity());
    }

    @SubscribeEvent
    static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        resend(event.getEntity());
    }

    private static void resend(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer p) {
            SENT.remove(p.getUUID());
            sync(p, List.of());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.client.tutorial.TutorialClient.receive(payload));
    }

    // サーバー → 持ち主: 今出ている手引きの ID と、いま済んだ ID
    public record Sync(List<String> active, List<String> done) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tutorial_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), Sync::active,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), Sync::done, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
