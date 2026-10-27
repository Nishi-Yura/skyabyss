package jp.revati.skyabyss.item.outfit.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.outfit.ConsumableGear;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 旅の矢・光の矢の飛ぶ姿 (バニラの矢の形に、選定の案の色の絵。tools/rough/arrow_entities.py)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ArrowLooks {
    private ArrowLooks() {
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ConsumableGear.TRAVEL_ARROW_ENTITY.get(), c -> new Look(c, "travel_arrow"));
        event.registerEntityRenderer(ConsumableGear.GLOW_ARROW_ENTITY.get(), c -> new Look(c, "glow_arrow"));
    }

    static final class Look extends ArrowRenderer<ConsumableGear.ModArrow> {
        private final ResourceLocation texture;

        Look(EntityRendererProvider.Context context, String name) {
            super(context);
            this.texture = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/" + name + ".png");
        }

        @Override
        public ResourceLocation getTextureLocation(ConsumableGear.ModArrow entity) {
            return texture;
        }
    }
}
