package jp.revati.skyabyss.titan.tide.back.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.back.TideBackSetup;
import jp.revati.skyabyss.titan.tide.back.TideBeckoner;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 潮殻の背の本番の描画: 潮招き (GeckoLib、目と指先の海硝子を光らせる)。群れの巣は島のブロックで見せるので、エンティティは描かない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TideBackClient {
    private TideBackClient() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TideBackSetup.REEF_NEST.get(), NoopRenderer::new);
        event.registerEntityRenderer(TideBackSetup.TIDE_BECKONER.get(), BeckonerRenderer::new);
    }

    // 低ポリのメッシュ (client/lowpoly)。LowPoly.enabled = false で元のキューブの geo に戻る。発光層は LowPolyGeoRenderer が持つ
    static final class BeckonerRenderer extends jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<TideBeckoner> {
        BeckonerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, "tide_beckoner", 1.0f);
            this.shadowRadius = 1.8f;
        }

        @Override
        protected float getDeathMaxRotation(TideBeckoner animatable) {
            return 0f;
        }
    }
}
