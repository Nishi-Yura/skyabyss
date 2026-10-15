package jp.revati.skyabyss.client.entity;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneSpike;
import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 岩鯨の背の番兵たちの描画。どれも GeckoLib のモデルで、目や核を暗闇で光らせる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GuardRenderers {
    private static final ResourceLocation SPIKE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/bone_spike.png");

    private GuardRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BONE_SENTINEL.get(), ctx -> geo(ctx, "bone_sentinel", 1.0f));
        event.registerEntityRenderer(ModEntities.RIB_ARCHER.get(), ctx -> geo(ctx, "rib_archer", 1.0f));
        event.registerEntityRenderer(ModEntities.MOSS_CRAWLER.get(), ctx -> geo(ctx, "moss_crawler", 1.0f));
        event.registerEntityRenderer(ModEntities.WATERWAY_MAW.get(), ctx -> geo(ctx, "waterway_maw", 1.1f));
        event.registerEntityRenderer(ModEntities.WHALEBONE_CAPTAIN.get(), ctx -> geo(ctx, "whalebone_captain", 1.45f));
        event.registerEntityRenderer(ModEntities.BONE_SPIKE.get(), ctx -> new ArrowRenderer<BoneSpike>(ctx) {
            @Override
            public ResourceLocation getTextureLocation(BoneSpike entity) {
                return SPIKE;
            }
        });
    }

    private static <T extends Entity & GeoAnimatable> GeoEntityRenderer<T> geo(EntityRendererProvider.Context ctx, String name, float scale) {
        GeoEntityRenderer<T> renderer = new GeoEntityRenderer<>(ctx, new DefaultedEntityGeoModel<T>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name), true));
        renderer.withScale(scale);
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        return renderer;
    }
}
