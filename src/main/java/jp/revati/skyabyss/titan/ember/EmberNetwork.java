package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.client.EmberHeatClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 余燼の骨組みのパケット (熱の目盛り)。ほかの担当は、自分のパケットを自分のクラスで別の版 (例: "ember-surface-1") として登録する
// (潮殻の TideNetwork・TideBackNetwork と同じ。同じ registrar に足し合うと、ファイルがぶつかるため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberNetwork {
    private static final String PROTOCOL_VERSION = "ember-1";

    private EmberNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Heat.TYPE, Heat.STREAM_CODEC, (payload, context) -> EmberHeatClient.receive(payload.heat()));
    }

    // サーバー → 余燼の背にいる人: 自分の今の熱 (0〜100)。変わったときと 2 秒ごと。背中を出ると 0
    public record Heat(float heat) implements CustomPacketPayload {
        public static final Type<Heat> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_heat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Heat::heat, Heat::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
