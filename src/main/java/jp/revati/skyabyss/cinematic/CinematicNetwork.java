package jp.revati.skyabyss.cinematic;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.cinematic.CinematicClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 場面のパケット (版 cinematic-1。ほかの担当とぶつからない)
//   Start (サーバー → その人だけ): 場面の名前、原点 (足もと)、前の向き、狙う物 (エンティティ ID と、見つからないときの位置)
//   Stop  (サーバー → その人): サーバーが場面を打ち切った (次元の移動・時間切れ)
//   End   (クライアント → サーバー): 最後まで見た / スキップした。サーバーは物語の効果をここで適用する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CinematicNetwork {
    private static final String PROTOCOL_VERSION = "cinematic-1";

    private CinematicNetwork() {
    }

    public record StartPayload(String scene, double x, double y, double z, float yaw, int targetId, double tx, double ty, double tz)
            implements CustomPacketPayload {
        public static final Type<StartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cinematic_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.scene());
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            buf.writeFloat(p.yaw());
            buf.writeVarInt(p.targetId());
            buf.writeDouble(p.tx());
            buf.writeDouble(p.ty());
            buf.writeDouble(p.tz());
        }, buf -> new StartPayload(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StopPayload(String scene) implements CustomPacketPayload {
        public static final Type<StopPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cinematic_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StopPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.scene()),
                buf -> new StopPayload(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EndPayload(String scene, boolean skipped) implements CustomPacketPayload {
        public static final Type<EndPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cinematic_end"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EndPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.scene());
            buf.writeBoolean(p.skipped());
        }, buf -> new EndPayload(buf.readUtf(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行される
        event.registrar(PROTOCOL_VERSION)
                .playToClient(StartPayload.TYPE, StartPayload.STREAM_CODEC, (payload, context) -> CinematicClient.receive(payload))
                .playToClient(StopPayload.TYPE, StopPayload.STREAM_CODEC, (payload, context) -> CinematicClient.receive(payload))
                .playToServer(EndPayload.TYPE, EndPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        Cinematics.end(player, payload.scene(), payload.skipped());
                    }
                });
    }
}
