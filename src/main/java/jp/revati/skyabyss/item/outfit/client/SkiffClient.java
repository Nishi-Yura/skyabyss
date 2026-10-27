package jp.revati.skyabyss.item.outfit.client;

import com.mojang.datafixers.util.Pair;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.outfit.MaterialGear;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 小舟 (item/outfit/MaterialGear.SkiffEntity) の描画: バニラの舟の形に、小舟の絵 (textures/entity/skiff.png。選定の案の色で塗った) を貼る
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkiffClient {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/skiff.png");

    private SkiffClient() {
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MaterialGear.SKIFF_ENTITY.get(), SkiffRenderer::new);
    }

    static final class SkiffRenderer extends BoatRenderer {
        private final Pair<ResourceLocation, ListModel<Boat>> look;

        SkiffRenderer(EntityRendererProvider.Context context) {
            super(context, false);
            this.look = Pair.of(TEXTURE, new BoatModel(context.bakeLayer(ModelLayers.createBoatModelName(Boat.Type.OAK))));
        }

        @Override
        public Pair<ResourceLocation, ListModel<Boat>> getModelWithLocation(Boat boat) {
            return look;
        }
    }
}
