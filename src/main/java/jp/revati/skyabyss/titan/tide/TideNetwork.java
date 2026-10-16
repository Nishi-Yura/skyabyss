package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.client.TideClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 潮殻の巨神のパケット。ほかの担当とぶつからないように、別の版で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideNetwork {
    private static final String PROTOCOL_VERSION = "tide-1";

    private TideNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Plunge.TYPE, Plunge.STREAM_CODEC, (payload, context) -> TideClient.plunge(payload.ticks()));
    }

    // サーバー → 門をくぐった人: 画面を水の色に沈める (ticks のあいだに薄れて戻る)
    public record Plunge(int ticks) implements CustomPacketPayload {
        public static final Type<Plunge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_plunge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Plunge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Plunge::ticks, Plunge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
