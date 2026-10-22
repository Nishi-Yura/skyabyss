package jp.revati.skyabyss.story.ch5.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.warden.WardenSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 心臓の番人の描画の登録 (docs/design/36 の 6.6)。案 B (肋の獣) の本番モデル。見えるのは光の中だけ (WardenRenderer)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WardenClient {
    private WardenClient() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WardenSetup.HEART_WARDEN.get(), WardenRenderer::new);
    }
}
