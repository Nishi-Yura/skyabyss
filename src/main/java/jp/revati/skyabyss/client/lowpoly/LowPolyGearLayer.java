package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

// 受け口の骨 (socket_main / socket_off) に低ポリのメッシュ (盾・武器) を描く層。GearLayer のアイテム/geo 版と同じ置き方で、
// メッシュの原点 (0,0,0) が受け口の pivot に来る (mesh JSON の "socket": true。頂点は受け口の pivot からの相対座標)。
// 向き・アニメは受け口の骨に付いている。使い方: GearLayer.lowPoly(renderer, "socket_off", "bone_sentinel_shield")
public class LowPolyGearLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
    private final String socketBone;
    private final String meshName;

    public LowPolyGearLayer(GeoRenderer<T> renderer, String socketBone, String meshName) {
        super(renderer);
        this.socketBone = socketBone;
        this.meshName = meshName;
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!LowPoly.enabled || !bone.getName().equals(socketBone)) {
            return;
        }
        LowPolyMesh mesh = LowPolyMeshes.get(meshName);
        if (mesh == null) {
            return;
        }
        poseStack.pushPose();
        // 骨の回転まで掛かっている。原点を骨の pivot へ移すだけ
        RenderUtil.translateToPivotPoint(poseStack, bone);
        int white = 0xFFFFFFFF;
        LowPoly.drawAll(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), mesh, packedLight, packedOverlay, white);
        if (mesh.glowMask != null) {
            LowPoly.drawAll(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentEmissive(mesh.glowMask)), mesh, 0xF000F0, packedOverlay, white);
        }
        poseStack.popPose();
    }
}
