package jp.revati.skyabyss.notice;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.notice.NoticeClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 知らせのパケットの登録 (別の版 notice-1。ほかの担当とぶつからない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NoticeNetwork {
    private static final String PROTOCOL_VERSION = "notice-1";

    private NoticeNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行される
        event.registrar(PROTOCOL_VERSION)
                .playToClient(NoticePayload.TYPE, NoticePayload.STREAM_CODEC, (payload, context) -> NoticeClient.receive(payload));
    }
}
