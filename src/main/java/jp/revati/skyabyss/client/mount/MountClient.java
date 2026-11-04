package jp.revati.skyabyss.client.mount;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.mount.MountEntity;
import jp.revati.skyabyss.mount.MountNetwork;
import jp.revati.skyabyss.mount.MountSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.EnumMap;
import java.util.Map;

import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.mount.MountLook;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.model.GeoModel;

// 乗り物のクライアント側: 描画の登録 (見た目ごとの geo と絵)、厩舎の画面の出し入れ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MountClient {
    private MountClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MountSetup.MOUNT.get(), MountRenderer::new);
    }

    public static void view(MountNetwork.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MountShopScreen s) {
            s.update(view);
        } else {
            mc.setScreen(new MountShopScreen(view));
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MountShopScreen) {
            mc.setScreen(null);
        }
    }

    // 見た目ごとの低ポリ (lowpoly/mount_<id>.json、tools/gen/mobs/mounts_lp/)。光る見た目 (雷の雛・炉の狐) は発光マスクで光る
    static final class MountRenderer extends LowPolyGeoRenderer<MountEntity> {
        private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

        MountRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MountModel(), e -> e.look().model(), 1f);
            this.shadowRadius = 0.8f;
        }

        // 絆 III (MountData.bond): 鞍の左右に飾りの房 (朱の房と金の結び目。歩くと揺れる。見た目だけ)
        @Override
        public void render(MountEntity entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose,
                net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
            super.render(entity, entityYaw, partialTick, pose, buffers, light);
            if (entity.bond() < 3 || entity.isInvisible()) {
                return;
            }
            float yaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            float sway = (float) Math.sin((entity.tickCount + partialTick) * 0.25) * 8f + (float) entity.getDeltaMovement().horizontalDistance() * 60f;
            com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(WHITE));
            MountLook look = entity.look();
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - yaw));
                // 鞍の脇の外 (荷袋や体に埋もれないよう、当たりの幅より少し外)
                pose.translate(side * (look.width * 0.5 + 0.06), look.seat + 0.04, -0.15);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Math.min(40f, sway)));
                // 結び目 (金) と房 (朱)
                quad(vc, pose, -0.07f, 0f, 0.07f, -0.1f, 0xFFE8C27A, light);
                quad(vc, pose, -0.05f, -0.1f, 0.05f, -0.55f, 0xFFC8402E, light);
                pose.popPose();
            }
        }

        private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack pose, float x0, float y0, float x1, float y1, int argb, int light) {
            var last = pose.last();
            int a = argb >>> 24, r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
            vc.addVertex(last, x0, y0, 0).setColor(r, gg, b, a).setUv(0, 0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
            vc.addVertex(last, x0, y1, 0).setColor(r, gg, b, a).setUv(0, 1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
            vc.addVertex(last, x1, y1, 0).setColor(r, gg, b, a).setUv(1, 1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
            vc.addVertex(last, x1, y0, 0).setColor(r, gg, b, a).setUv(1, 0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
        }
    }

    // 低ポリのときは骨だけの geo と低ポリのアトラス、切ったときは骨ごとの箱の geo。アニメは見た目ごと (骨が違う)
    static final class MountModel extends GeoModel<MountEntity> {
        private static final ResourceLocation DEBUG_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/debug.png");
        private static final Map<MountLook, ResourceLocation[]> RES = new EnumMap<>(MountLook.class);

        private static ResourceLocation[] res(MountLook look) {
            return RES.computeIfAbsent(look, l -> new ResourceLocation[] {
                    rl("geo/entity/" + l.model() + ".geo.json"), rl("geo/entity/" + l.model() + "_lowpoly.geo.json"),
                    rl("geo/entity/" + l.model() + "_lowpoly_debug.geo.json"), rl("textures/entity/" + l.model() + ".png"),
                    rl("textures/lowpoly/" + l.model() + ".png"), rl("animations/entity/" + l.model() + ".animation.json")});
        }

        private static ResourceLocation rl(String path) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
        }

        @Override
        public ResourceLocation getModelResource(MountEntity e) {
            ResourceLocation[] r = res(e.look());
            return LowPoly.enabled ? (LowPoly.debug ? r[2] : r[1]) : LowPoly.cubeOr(r[0], r[1]);
        }

        @Override
        public ResourceLocation getTextureResource(MountEntity e) {
            ResourceLocation[] r = res(e.look());
            return LowPoly.enabled ? (LowPoly.debug ? DEBUG_TEXTURE : r[4]) : LowPoly.cubeOr(r[3], r[4]);
        }

        @Override
        public ResourceLocation getAnimationResource(MountEntity e) {
            return res(e.look())[5];
        }

        // 脚の運び (歩き・駆け) は時間でなく歩いた距離で進める: 速さが変わっても足が滑らない
        @Override
        public void applyMolangQueries(AnimationState<MountEntity> state, double animTime) {
            super.applyMolangQueries(state, animTime);
            double d = state.getAnimatable().travel(state.getPartialTick());
            MathParser.setVariable("variable.mount_dist", () -> d);
        }
    }
}
