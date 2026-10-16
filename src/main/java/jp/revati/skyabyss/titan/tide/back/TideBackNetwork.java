package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.back.client.TideGaugeHud;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 潮殻の背の本番のパケット (潮見の目盛り)。ほかの担当とぶつからないよう、別の版で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideBackNetwork {
    private static final String PROTOCOL_VERSION = "tide-back-1";

    private TideBackNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Gauge.TYPE, Gauge.STREAM_CODEC, (payload, context) -> TideGaugeHud.receive(payload));
    }

    // サーバー → 背中にいる人: 潮の段階・次に変わるまでの tick (止めているときは -1)・満ち具合・招き潮の予告か・鳴った石の数
    public record Gauge(int phase, int ticksLeft, float fullness, boolean beckon, int stones) implements CustomPacketPayload {
        public static final Type<Gauge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_gauge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Gauge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Gauge::phase,
                ByteBufCodecs.INT, Gauge::ticksLeft,
                ByteBufCodecs.FLOAT, Gauge::fullness,
                ByteBufCodecs.BOOL, Gauge::beckon,
                ByteBufCodecs.VAR_INT, Gauge::stones,
                Gauge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
