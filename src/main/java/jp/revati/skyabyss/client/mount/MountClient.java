package jp.revati.skyabyss.client.mount;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.mount.MountEntity;
import jp.revati.skyabyss.mount.MountNetwork;
import jp.revati.skyabyss.mount.MountSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

// 乗り物のクライアント側: 描画の登録 (見た目ごとの geo と絵)、厩舎の画面の出し入れ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MountClient {
    private MountClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MountSetup.MOUNT.get(), MountRenderer::new);
    }

    public static void view(MountNetwork.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MountShopScreen s) {
            s.update(view);
        } else {
            mc.setScreen(new MountShopScreen(view));
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MountShopScreen) {
            mc.setScreen(null);
        }
    }

    static final class MountRenderer extends GeoEntityRenderer<MountEntity> {
        MountRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MountModel());
            this.shadowRadius = 0.8f;
        }
    }

    static final class MountModel extends GeoModel<MountEntity> {
        private static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "animations/entity/mount.animation.json");

        @Override
        public ResourceLocation getModelResource(MountEntity e) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + e.look().model() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(MountEntity e) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/" + e.look().model() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(MountEntity e) {
            return ANIM;
        }
    }
}
