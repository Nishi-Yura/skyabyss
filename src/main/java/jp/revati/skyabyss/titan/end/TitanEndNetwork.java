package jp.revati.skyabyss.titan.end;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 巨神に降ろされるときの暗転のパケット (サーバー → その人だけ)。共有の ModNetwork とは別の登録口にして、版を独立させる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanEndNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private TitanEndNetwork() {
    }

    // 画面が黒へ in tick で落ち、hold tick 黒のまま、out tick で明ける (out = 0 なら、黒のまま消える。場面が続きを受け持つとき)
    public record Fade(int in, int hold, int out) implements CustomPacketPayload {
        public static final Type<Fade> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "titan_end_fade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fade> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.in());
                    buf.writeVarInt(p.hold());
                    buf.writeVarInt(p.out());
                },
                buf -> new Fade(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Fade.TYPE, Fade.STREAM_CODEC, (payload, context) -> jp.revati.skyabyss.client.titan.CarryFadeClient.receive(payload));
    }
}
