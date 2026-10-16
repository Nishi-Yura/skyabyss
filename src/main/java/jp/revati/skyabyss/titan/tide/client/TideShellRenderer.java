package jp.revati.skyabyss.titan.tide.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 潮殻の巨神の描画。モデルを 8 倍に拡大し (近景・中景・遠景の 3 段、岩鯨と同じ作り)、遺跡の灯り・潮門・目を暗闇で光らせる。
// 光の明るさは、体の中心 (水の底) ではなく甲羅の頂の上で測る (TideShellEntity.getLightProbePosition)
public class TideShellRenderer extends GeoEntityRenderer<TideShellEntity> {
    private static final float SCALE = 8f;

    public TideShellRenderer(EntityRendererProvider.Context context) {
        super(context, new LodModel());
        withScale(SCALE);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;
    }

    // GeckoLib 4.9.3 は、LivingEntity でないエンティティの向きを描画に使わない (体の向きを 0 として 180 度だけ回す)。
    // そのままでは、いつも南を向いて描かれる (ゲームで見て、頭が進む向きの反対にあった)。エンティティの yaw で回す
    @Override
    protected void applyRotations(TideShellEntity titan, com.mojang.blaze3d.vertex.PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, titan.yRotO, titan.getYRot());
        super.applyRotations(titan, poseStack, ageInTicks, yaw, partialTick, nativeScale);
    }

    // 3 段の LOD (骨の名前とテクスチャは同じ)。カメラから体の軸 (頭の先から尾の先までの線分) までの距離で選ぶ
    private static final class LodModel extends DefaultedEntityGeoModel<TideShellEntity> {
        private static final ResourceLocation NEAR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/tide_shell.geo.json");
        private static final ResourceLocation MID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/tide_shell_mid.geo.json");
        private static final ResourceLocation FAR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/tide_shell_far.geo.json");
        // 横に広い (ひれの先まで ±75) ので、岩鯨 (64) より少し遠くまで近景にする
        private static final double MID_DISTANCE_SQ = 80.0 * 80.0;
        private static final double FAR_DISTANCE_SQ = 160.0 * 160.0;
        // 体の軸 (ブロック): 頭の先は前へ 87、尾の先は後ろへ 62、高さは甲羅の中ほど 20
        private static final double AXIS_FRONT = 87.0;
        private static final double AXIS_BACK = 62.0;
        private static final double AXIS_UP = 20.0;

        LodModel() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_shell"));
        }

        @Override
        public ResourceLocation getModelResource(TideShellEntity titan) {
            Entity camera = Minecraft.getInstance().cameraEntity;
            if (camera == null) {
                return NEAR;
            }
            double d = axisDistanceSq(titan, camera.getEyePosition());
            return d < MID_DISTANCE_SQ ? NEAR : d < FAR_DISTANCE_SQ ? MID : FAR;
        }

        private static double axisDistanceSq(TideShellEntity titan, Vec3 eye) {
            float yaw = titan.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
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
