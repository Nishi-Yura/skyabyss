package jp.revati.skyabyss.client.prologue;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.prologue.PrologueRegistry;
import jp.revati.skyabyss.prologue.WatcherEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

// 見る者の描画。
// - 体は半透明で描き、薄れて消えられるようにする (濃さは WatcherEntity.alpha)
// - 目は光るマスクで描き、さらに遠くでも点として見えるよう、目の位置に小さな光のにじみ (glint) を重ねる。
//   にじみは距離に比例した大きさにして、40〜70 ブロック先でも画面上で 2〜3 画素ほどの「かすかな光」になる
// - 頭の向き・傾きは WatcherClient が決めた値を骨に入れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class WatcherRenderer extends GeoEntityRenderer<WatcherEntity> {
    private static final ResourceLocation GEO = path("geo/entity/watcher.geo.json");
    private static final ResourceLocation TEXTURE = path("textures/entity/watcher.png");
    private static final ResourceLocation GLOWMASK = path("textures/entity/watcher_glowmask.png");
    private static final ResourceLocation ANIMATION = path("animations/entity/watcher.animation.json");
    private static final ResourceLocation GLINT = path("textures/entity/watcher_glint.png");
    // 目の光は、geo の「eyes」骨 (頭の子、立方体なし) の支点を両目の真ん中として、左右に EYE_X (1/16 ブロック単位) ずつ離して置く。
    // 支点は tools/rough/watcher.py が案ごとに書くので、案を差し替えてもここは変えなくてよい
    private static final String EYE_BONE = "eyes";
    private static final float EYE_X = 1.0f;
    // 光のにじみの大きさ (半径, ブロック): 距離 × GLINT_SCALE、ただし GLINT_MIN より小さくしない。
    // ゲーム内で確かめた値: 真夜中の空はほぼ黒 (RGB 3,4,7) で体の影は見分けられないので、目の光だけが手がかりになる。
    // 0.0021・0.7 では 60 ブロック先でにじみが 1 画素ほどの灰色 (明るさ 25 前後) になり、見つけられなかった。
    // 0.0032・1.4 で、1080p・視野 70 度のとき半径 2〜3 画素、芯の明るさ 130 前後の「かすかな青白い点」になる
    private static final float GLINT_SCALE = 0.0032f;
    private static final float GLINT_MIN = 0.03f;
    private static final float GLINT_BRIGHTNESS = 1.4f;

    private final Vector3f[] eyes = {new Vector3f(), new Vector3f()};
    // 顔がどれだけカメラを向いているか (0〜1)。横や後ろから見たとき、頭の向こうの目の光が透けて見えないようにする
    private float facing = 1f;
    private boolean eyesFound;
    private final Matrix4f rootInverse = new Matrix4f();

    public WatcherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0f;
        addRenderLayer(new EyeLayer(this));
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PrologueRegistry.WATCHER.get(), WatcherRenderer::new);
    }

    @Override
    public void render(WatcherEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        float alpha = entity.alpha(partialTick);
        float glow = entity.eyeGlow(partialTick);
        if (alpha <= 0.004f && glow <= 0.004f) {
            return;
        }
        eyesFound = false;
        rootInverse.set(poseStack.last().pose()).invert();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (eyesFound && glow > 0.004f) {
            renderGlints(poseStack, bufferSource, glow * facing);
        }
    }

    // 目の位置に、カメラを向いた小さな光の板を描く (加算で、闇の中の点になる)
    private void renderGlints(PoseStack poseStack, MultiBufferSource bufferSource, float glow) {
        Quaternionf camera = this.entityRenderDispatcher.cameraOrientation();
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.eyes(GLINT));
        int c = (int) (Mth.clamp(glow * GLINT_BRIGHTNESS, 0f, 1f) * 255f);
        for (Vector3f eye : eyes) {
            // eye はこの描画の基準 (カメラからの相対位置) での座標なので、その長さがカメラからの距離
            Vector4f world = new Vector4f(eye, 1f).mul(poseStack.last().pose());
            float distance = new Vector3f(world.x(), world.y(), world.z()).length();
            float s = Math.max(GLINT_MIN, distance * GLINT_SCALE);
            Vector3f[] corners = {new Vector3f(-s, -s, 0), new Vector3f(-s, s, 0), new Vector3f(s, s, 0), new Vector3f(s, -s, 0)};
            float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
            for (int i = 0; i < 4; i++) {
                Vector3f p = camera.transform(corners[i]).add(eye);
                vc.addVertex(pose, p.x(), p.y(), p.z())
                        .setColor(c, c, c, 255)
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(0f, 1f, 0f);
            }
        }
    }

    // GeckoLib は生き物でないエンティティの体の向きを 0 (南向き) にしてしまうので、エンティティの向きを渡し直す
    @Override
    protected void applyRotations(WatcherEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
                                  float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        super.applyRotations(animatable, poseStack, ageInTicks, yaw, partialTick, nativeScale);
    }

    @Override
    public RenderType getRenderType(WatcherEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(WatcherEntity animatable, float partialTick, int packedLight) {
        return Color.ofRGBA(1f, 1f, 1f, Mth.clamp(animatable.alpha(partialTick), 0f, 1f));
    }

    @Override
    public boolean shouldShowName(WatcherEntity animatable) {
        return false;
    }

    private static ResourceLocation path(String p) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
    }

    // 頭の骨に、向き・見下ろし・首のかしげを入れる (骨の回転はラジアン。焼いたあとの向きでそのまま効く)
    static final class Model extends GeoModel<WatcherEntity> {
        @Override
        public ResourceLocation getModelResource(WatcherEntity animatable) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(WatcherEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(WatcherEntity animatable) {
            return ANIMATION;
        }

        @Override
        public void setCustomAnimations(WatcherEntity animatable, long instanceId, AnimationState<WatcherEntity> state) {
            float partial = state.getPartialTick();
            getBone("head").ifPresent(head -> {
                head.setRotY(animatable.headYaw(partial) * Mth.DEG_TO_RAD);
                head.setRotX(animatable.headPitch(partial) * Mth.DEG_TO_RAD);
                head.setRotZ(animatable.tilt(partial) * Mth.DEG_TO_RAD);
            });
        }
    }

    // 目の光るマスクを、目の光の強さで描く。ついでに頭の骨の位置から目の座標を拾っておく
    static final class EyeLayer extends GeoRenderLayer<WatcherEntity> {
        private final WatcherRenderer owner;

        EyeLayer(WatcherRenderer owner) {
            super(owner);
            this.owner = owner;
        }

        @Override
        public void render(PoseStack poseStack, WatcherEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float glow = Mth.clamp(animatable.eyeGlow(partialTick), 0f, 1f) * (owner.eyesFound ? owner.facing : 1f);
            if (glow <= 0.004f) {
                return;
            }
            RenderType eyes = RenderType.eyes(GLOWMASK);
            int c = (int) (glow * 255f);
            int color = 0xFF000000 | c << 16 | c << 8 | c;
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, eyes, bufferSource.getBuffer(eyes), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, color);
        }

        @Override
        public void renderForBone(PoseStack poseStack, WatcherEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!EYE_BONE.equals(bone.getName())) {
                return;
            }
            // 骨の支点 (焼いたあとの座標。x は反転済み) が両目の真ん中
            float cx = bone.getPivotX() / 16f;
            float cy = bone.getPivotY() / 16f;
            float cz = bone.getPivotZ() / 16f;
            Matrix4f toRoot = new Matrix4f(owner.rootInverse).mul(poseStack.last().pose());
            for (int i = 0; i < 2; i++) {
                float x = cx + (i == 0 ? -EYE_X : EYE_X) / 16f;
                Vector4f p = new Vector4f(x, cy, cz, 1f).mul(toRoot);
                owner.eyes[i].set(p.x(), p.y(), p.z());
            }
            // 顔の正面 = 骨の +z (ラフの前。焼いても z は反転しない)。
            // 骨の行列はカメラからの相対位置で表すので、目からカメラへの向きは「目の位置の逆」
            Matrix4f boneMatrix = poseStack.last().pose();
            Vector4f mid = new Vector4f(cx, cy, cz, 1f).mul(boneMatrix);
            Vector4f forward = new Vector4f(0f, 0f, 1f, 0f).mul(boneMatrix);
            Vector3f toCamera = new Vector3f(-mid.x(), -mid.y(), -mid.z()).normalize();
            float dot = new Vector3f(forward.x(), forward.y(), forward.z()).normalize().dot(toCamera);
            owner.facing = Mth.clamp((dot - 0.15f) / 0.45f, 0f, 1f);
            owner.eyesFound = true;
        }
    }
}
