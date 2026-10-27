package jp.revati.skyabyss.item.outfit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.item.outfit.MaterialGear;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 小舟 (item/outfit/MaterialGear.SkiffEntity) の描画: 低ポリの平底の渡し舟 (2026-10-28 の選定 A。lowpoly/skiff.json、tools/rough/lowpoly/mounts/skiff.py)。
// 揺れ (傷を受けたとき) と向きはバニラの舟と同じ。低ポリの形が読めないときだけ、バニラの舟の形に小舟の絵を貼る
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkiffClient {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/skiff.png");
    static final String MESH = "skiff";
    static final int WHITE = 0xFFFFFFFF;

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

        @Override
        public void render(Boat boat, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
            LowPolyMesh mesh = LowPolyMeshes.get(MESH);
            if (mesh == null) {
                super.render(boat, yaw, partialTicks, pose, buffers, light);
                return;
            }
            pose.pushPose();
            pose.translate(0.0F, -0.05F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            float hurt = boat.getHurtTime() - partialTicks;
            float damage = Math.max(0.0F, boat.getDamage() - partialTicks);
            if (hurt > 0.0F) {
                pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * boat.getHurtDir()));
            }
            LowPoly.drawAll(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), mesh, light, OverlayTexture.NO_OVERLAY, WHITE);
            pose.popPose();
        }
    }
}
