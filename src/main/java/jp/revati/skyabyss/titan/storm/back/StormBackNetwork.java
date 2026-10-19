package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.back.client.StormGaugeHud;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 雷翼の背のパケット (風見の目盛り)。ほかの担当とぶつからないよう、別の版で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBackNetwork {
    private static final String PROTOCOL_VERSION = "storm-back-2";

    private StormBackNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Gauge.TYPE, Gauge.STREAM_CODEC, (payload, context) -> StormGaugeHud.receive(payload));
    }

    // サーバー → 背中にいる人: 拍の段階・次に変わるまでの tick (止めているときは -1)・嵐の強さ (0〜1)・鳴った石の数
    // + 目的の板 (StormGuide.Goal): 文の id (空なら出さない)・進みの点 (done / total)・文に入れる数 a b c
    public record Gauge(int phase, int ticksLeft, float strength, int stones, String goal, int done, int total, int a, int b, int c)
            implements CustomPacketPayload {
        public static final Type<Gauge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_gauge"));
        // composite は 6 つまでなので、書き読みを並べる
        public static final StreamCodec<RegistryFriendlyByteBuf, Gauge> STREAM_CODEC = StreamCodec.of((buf, g) -> {
            buf.writeVarInt(g.phase);
            buf.writeInt(g.ticksLeft);
            buf.writeFloat(g.strength);
            buf.writeVarInt(g.stones);
            buf.writeUtf(g.goal);
            buf.writeVarInt(g.done);
            buf.writeVarInt(g.total);
            buf.writeVarInt(g.a);
            buf.writeVarInt(g.b);
            buf.writeVarInt(g.c);
        }, buf -> new Gauge(buf.readVarInt(), buf.readInt(), buf.readFloat(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
