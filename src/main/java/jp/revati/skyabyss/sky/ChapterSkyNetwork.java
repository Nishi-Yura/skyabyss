package jp.revati.skyabyss.sky;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.sky.CloudEyeClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 1章の空の演出のパケット。共有の ModNetwork とは別の登録口にして、互いの版を独立させる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterSkyNetwork {
    private static final String PROTOCOL_VERSION = "2";

    private ChapterSkyNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(CloudEyePayload.TYPE, CloudEyePayload.STREAM_CODEC, (payload, context) -> CloudEyeClient.receive(payload))
                .playToClient(ChapterTintPayload.TYPE, ChapterTintPayload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.sky.ChapterTintClient.receive(payload))
                .playToServer(CloudEyeEndPayload.TYPE, CloudEyeEndPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CloudEyeEvents.onClientEnded(player, payload.id(), CloudEyeOutcome.byId(payload.outcome()));
                    }
                });
    }
}
