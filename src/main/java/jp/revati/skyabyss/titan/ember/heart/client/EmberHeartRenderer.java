package jp.revati.skyabyss.titan.ember.heart.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 余燼の心臓の描画。段階ごとの geo とテクスチャ (熔岩の殻 → 熔け落ちる殻と白熱の心臓 → 灰の殻と暗い残り火) を切り替え、
// 割れ目・核・熾・火の粉を光らせる (GlowLayer)。模型は tools/install/install_ember_heart.py が書く。
// 色が沈まない描き方は潮殻の心臓 (08 の 19.8節) と同じ: ブロックの光を最大にして殻を白い光で描き、
// 光る画素は基のテクスチャの上に _glowmask の色を目の光 (陰なし・足し合わせ) で重ねる
public class EmberHeartRenderer extends LowPolyGeoRenderer<EmberHeart> {
    // 模型は 約 24 単位 (1.5 ブロック) の大きさ。判定箱 (4.5 x 6.3) に合わせて 3 倍 (迫力のため、前の 3 x 4.2 の 1.5 倍)
    private static final float SCALE = 3.0f;

    public EmberHeartRenderer(EntityRendererProvider.Context context) {
        // 低ポリ (client/lowpoly): 段階ごとに別のメッシュ (ember_heart_pN)。LowPoly.enabled = false で元のキューブの geo に戻る
        super(context, new Model(), h -> "ember_heart_p" + Model.phase(h), SCALE);
        addRenderLayer(new GlowLayer(this));
    }

    // 発光は低ポリでもキューブでも下の GlowLayer (目の光で足す)。AutoGlowingGeoLayer は光る画素に面の陰がかかり、核・割れ目が沈む
    @Override
    protected boolean autoGlow() {
        return false;
    }

    // 部屋の明かり (段階 3 では血管が消えて暗い) に殻が沈まないよう、ブロックの光を最大にする。面の陰は残るので形は読める
    @Override
    protected int getBlockLightLevel(EmberHeart heart, BlockPos pos) {
        return 15;
    }

    // 光る画素 (割れ目・核・熾・火の粉) を本当に光らせる。基のテクスチャはそのまま描き、その上に _glowmask の色を

    // 倒れるときは横倒しにならず、死のアニメーションで見せる (HeartBoss)。倒れている間も赤く染めない
    @Override
    protected float getDeathMaxRotation(EmberHeart heart) {
        return 0f;
    }

    @Override
    public int getPackedOverlay(EmberHeart heart, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(heart.hurtTime > 0));
    }

    // 目の光 (RenderType.eyes: 陰なし・足し合わせ) で重ねる。発光の絵の色 = 足す光の強さ (tools/install/install_ember_heart.py の GLOW)
    static final class GlowLayer extends GeoRenderLayer<EmberHeart> {
        GlowLayer(EmberHeartRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, EmberHeart heart, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (heart.isInvisible()) {
                return;
            }
            // 低ポリ: アトラスの光る画素の色だけを載せた絵 (tools/install/install_lowpoly_ember_npc.py が焼く)
            RenderType glow = RenderType.eyes(Model.path(LowPoly.enabled
                    ? "textures/lowpoly/ember_heart_p" + Model.phase(heart) + "_glow.png"
                    : "textures/entity/ember_heart_p" + Model.phase(heart) + "_glowmask.png"));
            getRenderer().reRender(model, poseStack, buffers, heart, glow, buffers.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, getRenderer().getRenderColor(heart, partialTick, packedLight).argbInt());
        }
    }

    static final class Model extends GeoModel<EmberHeart> {
        private static final ResourceLocation ANIMATION = path("animations/entity/ember_heart.animation.json");

        @Override
        public ResourceLocation getModelResource(EmberHeart heart) {
            if (LowPoly.enabled) {
                return path("geo/entity/ember_heart_p" + phase(heart) + "_lowpoly" + (LowPoly.debug ? "_debug" : "") + ".geo.json");
            }
            return path("geo/entity/ember_heart_p" + phase(heart) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(EmberHeart heart) {
            if (LowPoly.enabled) {
                return LowPoly.debug ? path("textures/lowpoly/debug.png") : path("textures/lowpoly/ember_heart_p" + phase(heart) + ".png");
            }
            return path("textures/entity/ember_heart_p" + phase(heart) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(EmberHeart heart) {
            return ANIMATION;
        }

        static int phase(EmberHeart heart) {
            return Math.min(EmberHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase())) + 1;
        }

        static ResourceLocation path(String p) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
        }
    }
}
