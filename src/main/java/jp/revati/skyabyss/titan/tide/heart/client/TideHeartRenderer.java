package jp.revati.skyabyss.titan.tide.heart.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 潮殻の心臓の描画。段階ごとの geo とテクスチャ (巻貝の殻 → 破れた殻と光る部屋 → 内側の螺旋と三日月と水滴の輪) を切り替え、
// 核・部屋・月・水滴を暗闇で光らせる。模型は tools/install_tide_heart.py が書く
public class TideHeartRenderer extends GeoEntityRenderer<TideHeart> {
    // 模型は 約 36 単位 (2.25 ブロック) の大きさ。判定箱 (3 x 4.2) に合わせて 2 倍
    private static final float SCALE = 2.0f;

    public TideHeartRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(SCALE);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    static final class Model extends GeoModel<TideHeart> {
        private static final ResourceLocation ANIMATION = path("animations/entity/tide_heart.animation.json");

        @Override
        public ResourceLocation getModelResource(TideHeart heart) {
            return path("geo/entity/tide_heart_p" + phase(heart) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(TideHeart heart) {
            return path("textures/entity/tide_heart_p" + phase(heart) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(TideHeart heart) {
            return ANIMATION;
        }

        static int phase(TideHeart heart) {
            return Math.min(TideHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase())) + 1;
        }

        private static ResourceLocation path(String p) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
        }
    }
}
