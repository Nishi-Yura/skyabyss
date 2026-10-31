package jp.revati.skyabyss.client.founder;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.accessory.AccessoryMeshes;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.story.finale.founder.FounderItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 空見の肩掛け (見た目だけ。docs/design/53 の 6 章) を、着ている人の背中と肩に描く。ほかの人にも見える (人の付け足しのデータ FounderItems.SHAWL)。
// メッシュは lowpoly/items/founder_shawl/common (tools/rough/lowpoly/cloud/founder_keepsakes.py)。座標はプレイヤーの体の大きさで、首の付け根 = y 24
public final class ShawlLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final String MESH = "items/founder_shawl/common";
    static final float NECK = 24f;

    public ShawlLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!LowPoly.accessoryWorn || player.isInvisible() || player.isSpectator() || !FounderItems.wearing(player)) {
            return;
        }
        LowPolyMesh mesh = LowPolyMeshes.get(MESH);
        if (mesh == null) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // モデルの座標は y が下向き。設計 (y 上) の首の付け根を体の原点へ
        pose.scale(1f, -1f, 1f);
        pose.translate(0f, -NECK / 16f, 0f);
        AccessoryMeshes.draw(mesh, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Register {
        @SubscribeEvent
        static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r && r.getModel() instanceof PlayerModel<?>) {
                    @SuppressWarnings("unchecked")
                    LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pr = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) r;
                    pr.addLayer(new ShawlLayer(pr));
                }
            }
        }
    }
}
