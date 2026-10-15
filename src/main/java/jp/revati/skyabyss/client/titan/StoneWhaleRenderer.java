package jp.revati.skyabyss.client.titan;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 岩鯨の描画。モデルを8倍に拡大し (近景・中景・遠景の 3 段)、目と背中の紋様を暗闇で光らせる。背中から地上へ垂れる巨大な鎖もここで描く
public class StoneWhaleRenderer extends GeoEntityRenderer<StoneWhaleEntity> {
    private static final float SCALE = 8f;
    private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    // 鎖1つの見た目の太さと、模様が繰り返す長さ (ブロック)
    private static final float CHAIN_WIDTH = 0.9f;
    private static final float CHAIN_REPEAT = 3.0f;

    public StoneWhaleRenderer(EntityRendererProvider.Context context) {
        super(context, new LodModel());
        withScale(SCALE);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;
    }

    @Override
    public void render(StoneWhaleEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        float length = entity.chainLength();
        if (length > 0.1f) {
            // 鎖は背中の尾の側の縁 (錨石のある所) から、脇腹ごしに垂れる
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            Vec3 top = StoneWhaleEntity.chainTop(0, 0, 0, yaw);
            poseStack.pushPose();
            poseStack.translate(top.x, top.y, top.z);
            renderChain(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN)), length, packedLight);
            poseStack.popPose();
            // 背中の留め具から、張り出した垂れ口まで斜めに渡る鎖
            Vec3 anchor = StoneWhaleEntity.chainAnchor(0, 0, 0, yaw);
            Vec3 span = top.subtract(anchor);
            poseStack.pushPose();
            poseStack.translate(anchor.x, anchor.y, anchor.z);
            poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0, -1, 0), span.toVector3f().normalize()));
            renderChain(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN)), (float) span.length(), packedLight);
            poseStack.popPose();
        }
    }

    // バニラの鎖と同じく、十字に交差した2枚の面に鎖の模様を貼る。
    // テクスチャは繰り返し指定できないので、一定の長さごとに面を分けて並べる
    private static void renderChain(PoseStack poseStack, VertexConsumer buffer, float length, int light) {
        Matrix4f m = poseStack.last().pose();
        float w = CHAIN_WIDTH / 2;
        for (float top = 0f; top > -length; top -= CHAIN_REPEAT) {
            float bottom = Math.max(-length, top - CHAIN_REPEAT);
            float v = (top - bottom) / CHAIN_REPEAT;
            quad(buffer, m, -w, top, 0, w, bottom, 0, 0f, 3f / 16f, v, light);
            quad(buffer, m, 0, top, -w, 0, bottom, w, 3f / 16f, 6f / 16f, v, light);
        }
    }

    private static void quad(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float u1, float u2, float v, int light) {
        b.addVertex(m, x1, y1, z1).setColor(1f, 1f, 1f, 1f).setUv(u1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        b.addVertex(m, x1, y2, z1).setColor(1f, 1f, 1f, 1f).setUv(u1, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        b.addVertex(m, x2, y2, z2).setColor(1f, 1f, 1f, 1f).setUv(u2, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        b.addVertex(m, x2, y1, z2).setColor(1f, 1f, 1f, 1f).setUv(u2, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
    }

    // 3 段の LOD (骨の名前とテクスチャは同じ)。カメラから体の軸 (頭の先から尾びれの先までの線分) までの距離で選ぶ。
    // 体が長い (約 147 ブロック) ので、中心からの距離では頭や尾の近くにいるときに粗いモデルが出てしまう
    private static final class LodModel extends DefaultedEntityGeoModel<StoneWhaleEntity> {
        private static final ResourceLocation NEAR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/stone_whale.geo.json");
        private static final ResourceLocation MID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/stone_whale_mid.geo.json");
        private static final ResourceLocation FAR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/stone_whale_far.geo.json");
        private static final double MID_DISTANCE_SQ = 64.0 * 64.0;
        private static final double FAR_DISTANCE_SQ = 160.0 * 160.0;
        // 体の軸 (ブロック): 頭の先は前へ 66、尾びれの先は後ろへ 81、高さは体の中心 20
        private static final double AXIS_FRONT = 66.0;
        private static final double AXIS_BACK = 81.0;
        private static final double AXIS_UP = 20.0;

        LodModel() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "stone_whale"));
        }

        @Override
        public ResourceLocation getModelResource(StoneWhaleEntity whale) {
            Entity camera = Minecraft.getInstance().cameraEntity;
            if (camera == null) {
                return NEAR;
            }
            double d = axisDistanceSq(whale, camera.getEyePosition());
            return d < MID_DISTANCE_SQ ? NEAR : d < FAR_DISTANCE_SQ ? MID : FAR;
        }

        private static double axisDistanceSq(StoneWhaleEntity whale, Vec3 eye) {
            float yaw = whale.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            double px = eye.x - whale.getX();
            double py = eye.y - (whale.getY() + AXIS_UP);
            double pz = eye.z - whale.getZ();
            double t = Mth.clamp(px * fx + pz * fz, -AXIS_BACK, AXIS_FRONT);
            double dx = px - fx * t;
            double dz = pz - fz * t;
            return dx * dx + py * py + dz * dz;
        }
    }
}
