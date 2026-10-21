package jp.revati.skyabyss.client.facility;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 訓練場の木偶の的 (facility.TrainingDummy) は描かない。見た目は設計図のブロックの木偶のまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TrainingDummyClient {
    private TrainingDummyClient() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FacilitySetup.TRAINING_DUMMY.get(), NoopRenderer::new);
    }
}
