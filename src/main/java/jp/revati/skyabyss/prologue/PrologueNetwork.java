package jp.revati.skyabyss.prologue;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.prologue.WatcherClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 序章の演出のパケット。共有の ModNetwork とは別の登録口にして、互いの版を独立させる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PrologueNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private PrologueNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(WatcherPayload.TYPE, WatcherPayload.STREAM_CODEC, (payload, context) -> WatcherClient.receive(payload))
                .playToServer(WatcherEndPayload.TYPE, WatcherEndPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        WatcherEvents.onClientEnded(player, payload.id(), WatcherOutcome.byId(payload.outcome()));
                    }
                });
    }
}
