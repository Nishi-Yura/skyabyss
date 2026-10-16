package jp.revati.skyabyss.titan.tide.heart;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.heart.client.TideHeartClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 潮殻の心臓のパケット。ほかの担当とぶつからないように、別の版で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideHeartNetwork {
    private static final String PROTOCOL_VERSION = "tide-heart-1";

    private TideHeartNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Nudge.TYPE, Nudge.STREAM_CODEC, (payload, context) -> TideHeartClient.nudge(payload.x(), payload.y(), payload.z()));
    }

    // 人の速さに足す (渦潮・引き潮の引き寄せ、跳ね上げ)。サーバーは人の本当の速さを知らないので、クライアントで足す
    public static void nudge(ServerPlayer player, double x, double y, double z) {
        PacketDistributor.sendToPlayer(player, new Nudge((float) x, (float) y, (float) z));
    }

    public record Nudge(float x, float y, float z) implements CustomPacketPayload {
        public static final Type<Nudge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_heart_nudge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Nudge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Nudge::x, ByteBufCodecs.FLOAT, Nudge::y, ByteBufCodecs.FLOAT, Nudge::z, Nudge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
