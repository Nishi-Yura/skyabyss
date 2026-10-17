package jp.revati.skyabyss.titan.ember.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 余燼の巨神の描画 (13 の 2.7節)。モデルを 8 倍に拡大し (近景・中景・遠景の 3 段、潮殻と同じ作り)、溶岩の割れ目・目・口の奥・火口を光らせる。
// 光の強さは巨神の様子で変える: 潜っているあいだは弱く、眠っているあいだは強く、息を止める 2 秒は口の奥まで最も強い。
// 光の明るさは体の中心 (地の中) ではなく火山の上で測る (EmberTitanEntity.getLightProbePosition)
public class EmberTitanRenderer extends GeoEntityRenderer<EmberTitanEntity> {
    private static final float SCALE = 8f;

    public EmberTitanRenderer(EntityRendererProvider.Context context) {
        super(context, new LodModel());
        withScale(SCALE);
        addRenderLayer(new DimmableGlowLayer(this));
        this.shadowRadius = 0f;
    }

    // GeckoLib 4.9.3 は LivingEntity でないエンティティの向きを描画に使わない (いつも南を向く。潮殻の 17.8節)。エンティティの yaw で回す
    @Override
    protected void applyRotations(EmberTitanEntity titan, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
                                  float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, titan.yRotO, titan.getYRot());
        super.applyRotations(titan, poseStack, ageInTicks, yaw, partialTick, nativeScale);
    }

    // 光る画素の強さ (0〜1)。glowmask の色にかける (光る描き方は足し合わせなので、色を下げると暗くなる)
    static float glowStrength(EmberTitanEntity titan) {
        return switch (titan.syncedMode()) {
            case SWIM, APPROACH -> 0.45f;
            case RISE, SINK -> 0.75f;
            case SLEEP -> switch (titan.syncedBreath()) {
                case IN -> 0.9f;
                case HOLD -> 1.0f;
                case OUT -> 0.85f;
                case PAUSE -> 0.8f;
            };
        };
    }

    private static final class DimmableGlowLayer extends AutoGlowingGeoLayer<EmberTitanEntity> {
        DimmableGlowLayer(EmberTitanRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, EmberTitanEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            RenderType glow = getRenderType(animatable, bufferSource);
            if (glow == null) {
                return;
            }
            int v = Mth.clamp(Math.round(glowStrength(animatable) * 255f), 0, 255);
            int colour = 0xFF000000 | (v << 16) | (v << 8) | v;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, colour);
        }
    }

    // 3 段の LOD (骨の名前とテクスチャは同じ)。カメラから体の軸 (鼻先から尾の先までの線分) までの距離で選ぶ
    private static final class LodModel extends DefaultedEntityGeoModel<EmberTitanEntity> {
        private static final ResourceLocation NEAR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/ember_titan.geo.json");
        private static final ResourceLocation MID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/ember_titan_mid.geo.json");
        private static final ResourceLocation FAR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/ember_titan_far.geo.json");
        // 体が長い (230 ブロック) ので、潮殻 (80 / 160) より少し遠くまで近景にする
        static final double MID_DISTANCE_SQ = 96.0 * 96.0;
        static final double FAR_DISTANCE_SQ = 192.0 * 192.0;
        // 体の軸の高さ: 眠っているときは背の中ほど (地面から 15 ブロック)、潜っているときは地面から出ている火山の裾 (底から 58)
        private static final double AXIS_UP = 15.0;
        private static final double AXIS_UP_BURROWED = 58.0;

        LodModel() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_titan"));
        }

        @Override
        public ResourceLocation getModelResource(EmberTitanEntity titan) {
            Entity camera = Minecraft.getInstance().cameraEntity;
            if (camera == null) {
                return NEAR;
            }
            double d = axisDistanceSq(titan, camera.getEyePosition());
            return d < MID_DISTANCE_SQ ? NEAR : d < FAR_DISTANCE_SQ ? MID : FAR;
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
