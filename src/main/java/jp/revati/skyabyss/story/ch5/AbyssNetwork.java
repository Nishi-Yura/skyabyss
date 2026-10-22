package jp.revati.skyabyss.story.ch5;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 深淵のパケット (別の版で登録する)。潜るときの圧 (淵の声・重さ・深さ) をその人へ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssNetwork {
    private static final String PROTOCOL_VERSION = "abyss-1";

    private AbyssNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Pressure.TYPE, Pressure.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.story.ch5.client.AbyssHud.set(payload.voice(), payload.weight(), payload.depth()));
    }

    // サーバー → その人: 淵の声 0〜100 (-1 = 深淵の外)、重さ 0〜100 (%)、深さ (ブロック)
    public record Pressure(int voice, int weight, int depth) implements CustomPacketPayload {
        public static final Type<Pressure> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_pressure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pressure> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pressure::voice, ByteBufCodecs.VAR_INT, Pressure::weight, ByteBufCodecs.VAR_INT, Pressure::depth, Pressure::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
