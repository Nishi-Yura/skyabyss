package jp.revati.skyabyss.titan.tide.heart.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 潮殻の心臓の描画。段階ごとの geo とテクスチャ (巻貝の殻 → 破れた殻と光る部屋 → 内側の螺旋と三日月と水滴の輪) を切り替え、
// 核・部屋・月・水滴を光らせる (GlowLayer)。模型は tools/install/install_tide_heart.py が書く
public class TideHeartRenderer extends jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<TideHeart> {
    // 模型は 約 36 単位 (2.25 ブロック) の大きさ。判定箱 (4.5 x 6.3) に合わせて 3 倍 (迫力のため、前の 2 倍の 1.5 倍)
    private static final float SCALE = 3.0f;

    public TideHeartRenderer(EntityRendererProvider.Context context) {
        // 低ポリ (client/lowpoly): 段階ごとに別のメッシュ。LowPoly.enabled = false で元のキューブの geo と自前の発光層に戻る
        super(context, new Model(), h -> "tide_heart_p" + Model.phase(h), SCALE);
        addRenderLayer(new GlowLayer(this));
    }

    // 低ポリのときは LowPolyGeoRenderer の発光層、キューブのときは下の GlowLayer
    @Override
    protected boolean autoGlow() {
        return LowPoly.enabled;
    }


    // 倒れるときは横倒しにならず、死のアニメーションで見せる (HeartBoss)。倒れている間も赤く染めない
    @Override
    protected float getDeathMaxRotation(TideHeart heart) {
        return 0f;
    }

    @Override
    public int getPackedOverlay(TideHeart heart, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(heart.hurtTime > 0));
    }

    // 心臓は真珠の殻ごと、かすかに自分で光る。部屋の明かり (空の光が無く、ブロックの光 11〜13) のままだと、
    // 明かりの色が暖色に寄って淡い殻が泥のような茶色に見えたため、ブロックの光を最大にして白い光で描く
    // (面の向きの陰は残るので形は読める)。光る画素は GlowLayer がさらに光を足す
    @Override
    protected int getBlockLightLevel(TideHeart heart, BlockPos pos) {
        return 15;
    }

    // 光る画素 (核・光る部屋・三日月・水滴) を本当に光らせる。GeckoLib の AutoGlowingGeoLayer は発光の画素を基から抜き、
    // 面の向きの陰を受ける描き方で描くので、明るい殻と同じ灰白に沈んでいた (段階 3 の三日月)。
    // ここでは基のテクスチャはそのまま描き、その上に _glowmask の色を目の光 (eyes: 陰なし・足し合わせ) で重ねる。
    // 発光の絵の色 = 足す光の強さ (tools/install/install_tide_heart.py の GLOW)。光らない画素は黒 (足しても変わらない)
    static final class GlowLayer extends GeoRenderLayer<TideHeart> {
        GlowLayer(TideHeartRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, TideHeart heart, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (heart.isInvisible() || LowPoly.enabled) {
                return;
            }
            RenderType glow = RenderType.eyes(Model.path("textures/entity/tide_heart_p" + Model.phase(heart) + "_glowmask.png"));
            getRenderer().reRender(model, poseStack, buffers, heart, glow, buffers.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, getRenderer().getRenderColor(heart, partialTick, packedLight).argbInt());
        }
    }

    static final class Model extends GeoModel<TideHeart> {
        private static final ResourceLocation ANIMATION = path("animations/entity/tide_heart.animation.json");

        @Override
        public ResourceLocation getModelResource(TideHeart heart) {
            if (LowPoly.enabled) {
                return path("geo/entity/tide_heart_p" + phase(heart) + "_lowpoly" + (LowPoly.debug ? "_debug" : "") + ".geo.json");
            }
            return path("geo/entity/tide_heart_p" + phase(heart) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(TideHeart heart) {
            if (LowPoly.enabled) {
                return LowPoly.debug ? path("textures/lowpoly/debug.png") : path("textures/lowpoly/tide_heart_p" + phase(heart) + ".png");
            }
            return path("textures/entity/tide_heart_p" + phase(heart) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(TideHeart heart) {
            return ANIMATION;
        }

        static int phase(TideHeart heart) {
            return Math.min(TideHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase())) + 1;
        }

        static ResourceLocation path(String p) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
        }
    }
}
