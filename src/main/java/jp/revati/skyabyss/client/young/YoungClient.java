package jp.revati.skyabyss.client.young;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.young.YoungEntity;
import jp.revati.skyabyss.titan.young.YoungKind;
import jp.revati.skyabyss.titan.young.YoungSetup;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 巨神の子のクライアント側: 描画の登録 (低ポリのメッシュ。アニメは geo の idle / move / appear / dissolve)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class YoungClient {
    private YoungClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (YoungKind kind : YoungKind.values()) {
            event.registerEntityRenderer(YoungSetup.type(kind), ctx -> new YoungRenderer(ctx, kind));
        }
    }

    static final class YoungRenderer extends LowPolyGeoRenderer<YoungEntity> {
        YoungRenderer(EntityRendererProvider.Context ctx, YoungKind kind) {
            super(ctx, kind.model, kind.scale);
            this.shadowRadius = 2.2f;
        }

        // GeckoLib 4.9.3 は、LivingEntity でないエンティティの向きを描画に使わない (いつも南向き)。エンティティの yaw で回す
        @Override
        protected void applyRotations(YoungEntity young, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
            float yaw = Mth.rotLerp(partialTick, young.yRotO, young.getYRot());
            super.applyRotations(young, poseStack, ageInTicks, yaw, partialTick, nativeScale);
        }
    }
}
