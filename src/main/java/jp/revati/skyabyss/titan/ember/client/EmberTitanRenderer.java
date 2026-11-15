package jp.revati.skyabyss.titan.ember.client;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.client.lowpoly.LowPolyLodModel;

// 余燼の巨神の描画 (13 の 2.7節)。モデルを 8 倍に拡大し (近景・中景・遠景の 3 段、潮殻と同じ作り)、溶岩の割れ目・目・口の奥・火口を光らせる。
// 光の強さは巨神の様子で変える: 潜っているあいだは弱く、眠っているあいだは強く、息を止める 2 秒は口の奥まで最も強い。
// 光の明るさは体の中心 (地の中) ではなく火山の上で測る (EmberTitanEntity.getLightProbePosition)
public class EmberTitanRenderer extends LowPolyGeoRenderer<EmberTitanEntity> {
    private static final float SCALE = 8f;

    public EmberTitanRenderer(EntityRendererProvider.Context context) {
        this(context, new LodModel());
    }

    // 低ポリ (client/lowpoly, 15 の 9.6): 近・中・遠のメッシュ。
    // 光の強さを巨神の様子で変えるので、LowPolyGeoRenderer の発光層は使わず自前の DimmableGlowLayer をどちらの描画でも足す
    private EmberTitanRenderer(EntityRendererProvider.Context context, LodModel model) {
        super(context, model, model::meshName, SCALE);
        this.shadowRadius = 0f;
    }


    // GeckoLib 4.9.3 は LivingEntity でないエンティティの向きを描画に使わない (いつも南を向く。潮殻の 17.8節)。エンティティの yaw で回す
    @Override
    protected void applyRotations(EmberTitanEntity titan, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
                                  float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, titan.yRotO, titan.getYRot());
        super.applyRotations(titan, poseStack, ageInTicks, yaw, partialTick, nativeScale);
    }

    // 地面に落ちる影 (重さ。client/titan/TitanWeight)
    @Override
    public void render(EmberTitanEntity titan, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        jp.revati.skyabyss.client.titan.TitanWeight.renderShadow(titan, partialTick, poseStack, buffer);
        super.render(titan, entityYaw, partialTick, poseStack, buffer, packedLight);
    }



    // 3 段の LOD (骨の名前とテクスチャは同じ)。カメラから体の軸 (鼻先から尾の先までの線分) までの距離で選ぶ
    private static final class LodModel extends LowPolyLodModel<EmberTitanEntity> {
        // 体が長い (230 ブロック) ので、潮殻 (80 / 160) より少し遠くまで近景にする
        static final double MID_DISTANCE_SQ = 96.0 * 96.0;
        static final double FAR_DISTANCE_SQ = 192.0 * 192.0;
        // 体の軸の高さ: 眠っているときは背の中ほど (地面から 15 ブロック)、潜っているときは地面から出ている火山の裾 (底から 58)
        private static final double AXIS_UP = 15.0;
        private static final double AXIS_UP_BURROWED = 58.0;

        LodModel() {
            super("ember_titan");
        }

        @Override
        protected int lod(EmberTitanEntity titan) {
            Entity camera = Minecraft.getInstance().cameraEntity;
            if (camera == null) {
                return 0;
            }
            double d = axisDistanceSq(titan, camera.getEyePosition());
            return d < MID_DISTANCE_SQ ? 0 : d < FAR_DISTANCE_SQ ? 1 : 2;
        }

        private static double axisDistanceSq(EmberTitanEntity titan, Vec3 eye) {
            float yaw = titan.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            double px = eye.x - titan.getX();
            double py = eye.y - (titan.getY() + (titan.isSurfaced() ? AXIS_UP : AXIS_UP_BURROWED));
            double pz = eye.z - titan.getZ();
            double t = Mth.clamp(px * fx + pz * fz, -EmberTitanEntity.TAIL_BACK, EmberTitanEntity.SNOUT_FORWARD);
            double dx = px - fx * t;
            double dz = pz - fz * t;
            return dx * dx + py * py + dz * dz;
        }
    }
}
