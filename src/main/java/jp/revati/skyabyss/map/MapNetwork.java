package jp.revati.skyabyss.map;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.map.MapClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 旅の地図のパケット (版 map-1)。クライアントが開くとき・開いている間 5 秒ごとに頼み、サーバーがその人の印を返す (MapMarks.collect)。
// 海図 (TravelNetwork.Chart) は今までどおり別に届き、同じ画面に重ねて描く
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MapNetwork {
    private static final String PROTOCOL_VERSION = "map-1";
    // 頼みの間隔の下限 (tick。押し続け・連打でサーバーに集めさせすぎない)
    static final int MIN_INTERVAL = 20;

    private MapNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        answer(player, payload.open());
                    }
                })
                .playToClient(SheetPayload.TYPE, SheetPayload.STREAM_CODEC, (payload, context) -> MapClient.receive(payload));
    }

    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    static void answer(ServerPlayer player, boolean open) {
        long now = player.server.getTickCount();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL && !open) {
            return;
        }
        LAST.put(player.getUUID(), now);
        MapMarks.Sheet sheet = MapMarks.collect(player);
        PacketDistributor.sendToPlayer(player, new SheetPayload(open, sheet));
    }

    static void writeMark(RegistryFriendlyByteBuf buf, MapMarks.Mark m) {
        buf.writeVarInt(m.kind().ordinal());
        buf.writeUtf(m.id());
        buf.writeUtf(m.name());
        buf.writeUtf(m.extra());
        buf.writeVarInt(m.x());
        buf.writeVarInt(m.z());
        buf.writeBoolean(m.rumor());
        buf.writeInt(m.color());
    }

    static MapMarks.Mark readMark(RegistryFriendlyByteBuf buf) {
        int k = buf.readVarInt();
        MapMarks.Kind[] kinds = MapMarks.Kind.values();
        MapMarks.Kind kind = k >= 0 && k < kinds.length ? kinds[k] : MapMarks.Kind.SITE;
        return new MapMarks.Mark(kind, buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readInt());
    }

    // クライアント → サーバー: 印をください (open = 開いたとき。間隔の下限を待たない)
    public record Request(boolean open) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "map_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.open), buf -> new Request(buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → クライアント: その人の地図 (open = 開く頼みへの返事。画面がなければ開く)
    public record SheetPayload(boolean open, MapMarks.Sheet sheet) implements CustomPacketPayload {
        public static final Type<SheetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "map_sheet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SheetPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.open);
                    buf.writeVarInt(p.sheet.centerX());
                    buf.writeVarInt(p.sheet.centerZ());
                    buf.writeBoolean(p.sheet.townKnown());
                    buf.writeVarInt(p.sheet.marks().size());
                    p.sheet.marks().forEach(m -> writeMark(buf, m));
                },
                buf -> {
                    boolean open = buf.readBoolean();
                    int cx = buf.readVarInt();
                    int cz = buf.readVarInt();
                    boolean town = buf.readBoolean();
                    int n = Math.min(buf.readVarInt(), MapMarks.MAX);
                    List<MapMarks.Mark> marks = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        marks.add(readMark(buf));
                    }
                    return new SheetPayload(open, new MapMarks.Sheet(cx, cz, town, marks));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
