package jp.revati.skyabyss.titan.storm;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.client.StormClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 雷翼の巨神のパケット (別の版で登録する)。白い閃光 (昇り風の頂・嵐に投げ返される・背中へ渡る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormNetwork {
    private static final String PROTOCOL_VERSION = "storm-1";

    private StormNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Flash.TYPE, Flash.STREAM_CODEC, (payload, context) -> StormClient.flash(payload.ticks()));
    }

    // サーバー → その人: 画面を白く瞬かせる (ticks のあいだに薄れて戻る)
    public record Flash(int ticks) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Flash::ticks, Flash::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
