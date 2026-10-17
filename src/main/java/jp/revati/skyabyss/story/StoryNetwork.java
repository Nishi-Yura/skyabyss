package jp.revati.skyabyss.story;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.story.SkyKeyScene;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 1 章の終わりのパケット (版 story-1)。共有の ModNetwork とは別の登録口
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StoryNetwork {
    private static final String PROTOCOL_VERSION = "story-1";

    private StoryNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ動くので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(SkyKeyScenePayload.TYPE, SkyKeyScenePayload.STREAM_CODEC, (payload, context) -> SkyKeyScene.open(payload));
    }
}
