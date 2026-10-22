package jp.revati.skyabyss.story.ch5.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.warden.WardenSetup;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 心臓の番人の仮の見た目 (36 の 6.6)。体は描かず、光の中にいるときだけ番人が出す煙と魂の粒 (HeartWarden.outline) が輪郭になる。
// 3 案のラフから選んだら、光の中でだけ描くモデルに替える
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WardenClient {
    private WardenClient() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WardenSetup.HEART_WARDEN.get(), NoopRenderer::new);
    }
}
