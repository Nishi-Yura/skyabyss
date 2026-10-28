package jp.revati.skyabyss.pouch;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.util.PlayerMaps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 旅嚢のパケット。画面の中の操作 (置く・出す・作り直し) はメニューのクリックとボタンで済むので、ここは画面の外のものだけ。
//   Use      (持ち主 → サーバー): 輪・短押しで袋の品を使う。品が空なら「前回の道具」(サーバーが覚える)
//   Open     (持ち主 → サーバー): キー I・つまみから旅嚢の画面を開く
//   OpenBook (サーバー → 持ち主): 旅嚢から伝承の書を使ったとき、書の画面を開かせる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PouchNet {
    private static final String PROTOCOL_VERSION = "pouch-4";

    // 最後に使った道具 (サーバーが覚える。短押しはこれを使う)
    private static final Map<UUID, ResourceLocation> LAST = PlayerMaps.track(new ConcurrentHashMap<>());

    private PouchNet() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToServer(Use.TYPE, Use.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        use(player, payload.item());
                    }
                })
                .playToServer(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PouchSetup.openMenu(player);
                    }
                })
                .playToServer(Viewed.TYPE, Viewed.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        viewed(player, payload.item());
                    }
                })
                .playToClient(OpenBook.TYPE, OpenBook.STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.lore.client.LoreClient.openBook());
    }

    // 輪・短押しの結果を処理する。item が空なら前回の道具 (なければ使える品の先頭)。結果を返す (自己テストも使う)
    public static PouchUsable.Result use(ServerPlayer player, Optional<ResourceLocation> requested) {
        ResourceLocation id = requested.orElseGet(() -> LAST.get(player.getUUID()));
        if (id == null) {
            var usable = PouchUsable.usable(player);
            if (usable.isEmpty()) {
                return PouchUsable.Result.NONE;
            }
            id = usable.get(0).id();
        }
        PouchEntries.Entry e = PouchEntries.byId(id);
        if (e == null) {
            return PouchUsable.Result.NONE;
        }
        PouchUsable.Result result = PouchUsable.usePouch(player, e.item());
        if (result == PouchUsable.Result.OK || result == PouchUsable.Result.REFUSED) {
            LAST.put(player.getUUID(), id);
        }
        return result;
    }

    // 画面で見た品の "NEW" の印を外す (自分の袋だけ。印のない品は何も起きない)。外したら true
    public static boolean viewed(ServerPlayer player, ResourceLocation id) {
        if (!Pouch.data(player).clearFresh(id)) {
            return false;
        }
        Pouch.sync(player);
        return true;
    }

    // 覚えた前回の道具 (自己テスト・確かめ用)
    public static ResourceLocation last(ServerPlayer player) {
        return LAST.get(player.getUUID());
    }

    public record Use(Optional<ResourceLocation> item) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pouch_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Use::item, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 画面で見た品 ("NEW" の印を外す。持ち主のものだけ。ない印は何も起きない)
    public record Viewed(ResourceLocation item) implements CustomPacketPayload {
        public static final Type<Viewed> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pouch_viewed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Viewed> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Viewed::item, Viewed::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pouch_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenBook() implements CustomPacketPayload {
        public static final Type<OpenBook> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pouch_open_book"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenBook> STREAM_CODEC = StreamCodec.unit(new OpenBook());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
