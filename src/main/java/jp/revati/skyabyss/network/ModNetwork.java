package jp.revati.skyabyss.network;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.sky.SkyPassClient;
import jp.revati.skyabyss.sky.SkyPassPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// パケットの登録。プロトコルを変えたらバージョンを上げる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(SkyPassPayload.TYPE, SkyPassPayload.STREAM_CODEC, (payload, context) -> SkyPassClient.receive(payload.pass()));
    }
}
