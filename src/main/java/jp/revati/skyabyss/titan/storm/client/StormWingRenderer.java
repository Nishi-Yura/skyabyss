package jp.revati.skyabyss.titan.storm.client;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.client.lowpoly.LowPolyLodModel;

// 雷翼の巨神の描画。モデルを 8 倍に拡大し (近景・中景・遠景の 3 段、潮殻と同じ作り)、稲妻の筋と手首の瘤を暗闇で光らせる。
// モデルか絵がまだ無いあいだは、描かない (GeckoLib のエラーを出し続けないため)
public class StormWingRenderer extends LowPolyGeoRenderer<StormWingEntity> {
    private static final float SCALE = 8f;

    public StormWingRenderer(EntityRendererProvider.Context context) {
        this(context, new LodModel());
    }

    // 低ポリ (client/lowpoly, 15 の 9.6): 近・中・遠のメッシュ。LowPoly.enabled = false で元のキューブの geo に戻る。発光層は LowPolyGeoRenderer が持つ
    private StormWingRenderer(EntityRendererProvider.Context context, LodModel model) {
        super(context, model, model::meshName, SCALE);
        this.shadowRadius = 0f;
    }

    // GeckoLib 4.9.3 は LivingEntity でないエンティティの向きを描画に使わない。エンティティの yaw で回す (潮殻と同じ)
    @Override
    protected void applyRotations(StormWingEntity titan, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, titan.yRotO, titan.getYRot());
        super.applyRotations(titan, poseStack, ageInTicks, yaw, partialTick, nativeScale);
    }

    @Override
    public void render(StormWingEntity titan, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!StormClient.exists(getGeoModel().getModelResource(titan)) || !StormClient.exists(getGeoModel().getTextureResource(titan))) {
            return;
        }
        jp.revati.skyabyss.client.titan.TitanWeight.renderShadow(titan, partialTick, poseStack, buffer);
        super.render(titan, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    // 3 段の LOD (骨の名前とテクスチャは同じ)。カメラから体の軸 (頭の先から尾の先までの線分) までの距離で選ぶ
    private static final class LodModel extends LowPolyLodModel<StormWingEntity> {
        // 翼の幅が広いので、潮殻より少し遠くまで近景にする
        private static final double MID_DISTANCE_SQ = 120.0 * 120.0;
        private static final double FAR_DISTANCE_SQ = 240.0 * 240.0;
        // 体の軸 (ブロック): 頭の先は前へ 60、尾の先は後ろへ 60、高さは体の中ほど 25
        private static final double AXIS_FRONT = StormWingEntity.HEAD_FORWARD;
        private static final double AXIS_BACK = 60.0;
        private static final double AXIS_UP = StormWingEntity.GATE_UP;

        LodModel() {
            super("thunder_wing");
        }

        @Override
        protected int lod(StormWingEntity titan) {
            Entity camera = Minecraft.getInstance().cameraEntity;
            if (camera == null) {
                return 0;
            }
            double d = axisDistanceSq(titan, camera.getEyePosition());
            return d < MID_DISTANCE_SQ ? 0 : d < FAR_DISTANCE_SQ ? 1 : 2;
        }

        private static double axisDistanceSq(StormWingEntity titan, Vec3 eye) {
            double yaw = titan.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin((float) yaw);
            double fz = Mth.cos((float) yaw);
            double px = eye.x - titan.getX();
            double py = eye.y - (titan.getY() + AXIS_UP);
            double pz = eye.z - titan.getZ();
            double t = Mth.clamp(px * fx + pz * fz, -AXIS_BACK, AXIS_FRONT);
            double dx = px - fx * t;
            double dz = pz - fz * t;
            return dx * dx + py * py + dz * dz;
        }
    }
}
