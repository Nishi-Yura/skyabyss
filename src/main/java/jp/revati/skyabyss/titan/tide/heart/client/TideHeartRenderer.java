package jp.revati.skyabyss.titan.tide.heart.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// 潮殻の心臓の描画。段階ごとの geo とテクスチャ (巻貝の殻 → 破れた殻と光る部屋 → 内側の螺旋と三日月と水滴の輪) を切り替え、
// 核・部屋・月・水滴を光らせる (GlowLayer)。模型は tools/install/install_tide_heart.py が書く
public class TideHeartRenderer extends jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<TideHeart> {
    // 模型は 約 36 単位 (2.25 ブロック) の大きさ。判定箱 (4.5 x 6.3) に合わせて 3 倍 (迫力のため、前の 2 倍の 1.5 倍)
    private static final float SCALE = 6.0f;  // 2026-11-08 の声「心臓の大きさ2倍にして」で 2 倍

    public TideHeartRenderer(EntityRendererProvider.Context context) {
        // 低ポリ (client/lowpoly): 段階ごとに別のメッシュ。
        super(context, new Model(), h -> "tide_heart_p" + Model.phase(h), SCALE);
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


    static final class Model extends GeoModel<TideHeart> {
        private static final ResourceLocation ANIMATION = path("animations/entity/tide_heart.animation.json");

        @Override
        public ResourceLocation getModelResource(TideHeart heart) {
            return path("geo/entity/tide_heart_p" + phase(heart) + "_lowpoly" + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(TideHeart heart) {
            return path("textures/lowpoly/tide_heart_p" + phase(heart) + ".png");
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
