package jp.revati.skyabyss.story.finale;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// サーバー → クライアント: 本人の選んだ結末 (calm / break / accept。まだなら空)。空の描き方を替える (client/sky/EndingSkyClient)
public record EndingPayload(String ending) implements CustomPacketPayload {
    public static final Type<EndingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "finale_ending"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EndingPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
            EndingPayload::ending, EndingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 終章の通信の登録口 (版を他と独立させる)
    @EventBusSubscriber(modid = SkyAbyss.MODID)
    public static final class Network {
        private Network() {
        }

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.client.sky.EndingSkyClient.receive(payload.ending()));
        }
    }
}
