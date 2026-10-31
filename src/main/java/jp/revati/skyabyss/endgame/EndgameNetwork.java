package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// エンドコンテンツ (巡り路など。docs/design/54) のパケット。共有の ModNetwork とは別の登録口にして、版を独立させる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EndgameNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private EndgameNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(CircuitPayload.TYPE, CircuitPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.endgame.client.CircuitClient.receive(payload)));
    }
}
