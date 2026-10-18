package jp.revati.skyabyss.titan.tide.mob.client;

import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.tide.mob.BubblePuffer;
import jp.revati.skyabyss.titan.tide.mob.BubbleShot;
import jp.revati.skyabyss.titan.tide.mob.CoralThrall;
import jp.revati.skyabyss.titan.tide.mob.ReefScuttler;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 潮殻の背の敵の描画。GeckoLib のモデル (geo/entity/<名前>) と、目・海硝子を暗闇で光らせる層。
// 珊瑚憑き: 像のときはテクスチャを白い像のもの (_statue) にし、像だけの珊瑚 (骨 statue_coral) を見せる。
//           珊瑚の銛は別のモデル (geo/gear/coral_spear) を受け口 socket_main に付ける (像のときは白い銛)。
// 死ぬときはバニラの「横に倒れる」回転をせず、モデルの死のアニメーションで倒れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TideMobRenderers {
    private static final ResourceLocation BUBBLE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/bubble_shot.png");

    private TideMobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TideMobSetup.CORAL_THRALL.get(), ThrallRenderer::new);
        event.registerEntityRenderer(TideMobSetup.REEF_SCUTTLER.get(), ctx -> lowPoly(ctx, "reef_scuttler", 0.45f));
        event.registerEntityRenderer(TideMobSetup.BUBBLE_PUFFER.get(), ctx -> lowPoly(ctx, "bubble_puffer", 0.35f));
        event.registerEntityRenderer(TideMobSetup.BUBBLE_SHOT.get(), BubbleRenderer::new);
    }

    // 低ポリのメッシュ (client/lowpoly)。LowPoly.enabled = false で元のキューブの geo に戻る。死ぬときは横に倒れない
    static <T extends TideMob> LowPolyGeoRenderer<T> lowPoly(EntityRendererProvider.Context ctx, String name, float shadow) {
        return new LowPolyGeoRenderer<T>(ctx, name, 1.0f) {
            {
                this.shadowRadius = shadow;
            }

            @Override
            protected float getDeathMaxRotation(T animatable) {
                return 0f;
            }
        };
    }

    static class MobRenderer<T extends TideMob> extends GeoEntityRenderer<T> {
        MobRenderer(EntityRendererProvider.Context ctx, String name, float shadow) {
            this(ctx, new DefaultedEntityGeoModel<T>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name), true), shadow);
        }

        MobRenderer(EntityRendererProvider.Context ctx, DefaultedEntityGeoModel<T> model, float shadow) {
            super(ctx, model);
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = shadow;
        }

        @Override
        protected float getDeathMaxRotation(T animatable) {
            return 0f;
        }
    }

    // ---------------------------------------------------------------- 珊瑚憑き

    // 低ポリ (client/lowpoly): 像のときは白い石のアトラス (textures/lowpoly/coral_thrall_statue.png)。LowPoly.enabled = false で元のキューブに戻る
    static final class ThrallModel extends LowPolyGeoRenderer.Model<CoralThrall> {
        private static final ResourceLocation STATUE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/coral_thrall_statue.png");
        private static final ResourceLocation STATUE_LOW = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/coral_thrall_statue.png");

        ThrallModel() {
            super("coral_thrall");
        }

        @Override
        public ResourceLocation getTextureResource(CoralThrall thrall) {
            if (thrall.isStatue() && !LowPoly.debug) {
                return LowPoly.enabled ? STATUE_LOW : STATUE;
            }
            return super.getTextureResource(thrall);
        }
    }

    static final class ThrallRenderer extends LowPolyGeoRenderer<CoralThrall> {
        ThrallRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new ThrallModel(), t -> "coral_thrall", 1.0f);
            this.shadowRadius = 0.4f;
            addRenderLayer(new Either<>(this, CoralThrall::isStatue,
                    new GearLayer<>(this, "socket_main", "coral_spear_statue", mob -> "idle").activeWhen(() -> !LowPoly.enabled),
                    new GearLayer<>(this, "socket_main", "coral_spear", mob -> "idle").activeWhen(() -> !LowPoly.enabled)));
            addRenderLayer(new Either<CoralThrall>(this, CoralThrall::isStatue,
                    GearLayer.lowPoly(this, "socket_main", "coral_spear_statue"),
                    GearLayer.lowPoly(this, "socket_main", "coral_spear")));
        }

        @Override
        protected float getDeathMaxRotation(CoralThrall animatable) {
            return 0f;
        }

        @Override
        public void preRender(PoseStack poseStack, CoralThrall thrall, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            // 像だけの珊瑚 (モデルは全個体で共有なので、描くたびに決める)
            model.getBone("statue_coral").ifPresent(b -> b.setHidden(!thrall.isStatue()));
            super.preRender(poseStack, thrall, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }

    // 条件で、二つの層のどちらかを描く (像の銛と、ふつうの銛)
    static final class Either<T extends TideMob> extends GeoRenderLayer<T> {
        private final Predicate<T> test;
        private final GeoRenderLayer<T> yes;
        private final GeoRenderLayer<T> no;

        Either(GeoRenderer<T> renderer, Predicate<T> test, GeoRenderLayer<T> yes, GeoRenderLayer<T> no) {
            super(renderer);
            this.test = test;
            this.yes = yes;
            this.no = no;
        }

        @Override
        public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            (test.test(animatable) ? yes : no).renderForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick,
                    packedLight, packedOverlay);
        }
    }

    // ---------------------------------------------------------------- 泡 (カメラを向く一枚の絵)

    static final class BubbleRenderer extends EntityRenderer<BubbleShot> {
        private static final RenderType TYPE = RenderType.entityTranslucent(BUBBLE);

        BubbleRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(BubbleShot shot, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            float wobble = 1f + 0.08f * (float) Math.sin((shot.tickCount + partialTick) * 0.6f);
            pose.scale(0.55f * wobble, 0.55f / wobble, 0.55f);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            PoseStack.Pose last = pose.last();
            Matrix4f m = last.pose();
            VertexConsumer vc = buffers.getBuffer(TYPE);
            int full = 0xF000F0;
            vertex(vc, m, last, -0.5f, -0.5f, 0, 1, full);
            vertex(vc, m, last, 0.5f, -0.5f, 1, 1, full);
            vertex(vc, m, last, 0.5f, 0.5f, 1, 0, full);
            vertex(vc, m, last, -0.5f, 0.5f, 0, 0, full);
            pose.popPose();
            super.render(shot, yaw, partialTick, pose, buffers, light);
        }

        private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float u, float v, int light) {
            vc.addVertex(m, x, y, 0).setColor(255, 255, 255, 220).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(last, 0, 1, 0);
        }

        @Override
        public ResourceLocation getTextureLocation(BubbleShot shot) {
            return BUBBLE;
        }
    }
}
