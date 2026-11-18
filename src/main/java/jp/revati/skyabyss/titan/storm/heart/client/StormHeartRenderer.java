package jp.revati.skyabyss.titan.storm.heart.client;


import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// 雷翼の心臓の描画。段階ごとの geo とテクスチャ (包む翼 → 開いた翼と冠の翼 → 骨だけの翼と稲妻の網) を切り替え、
// 核・稲妻・網を光らせる (GlowLayer)。模型は tools/install/install_storm_heart.py が書く。
// 色が部屋の明かりで沈まないように、潮殻の心臓の直し方 (08 の 19.8節) を最初から使う:
//  - 心臓の光はブロックの光 15 で描く (部屋の弱い明かりは暖色に寄り、灰青と白銀の翼が茶色く濁るため)。面の陰は残る
//  - 光る画素は、基のテクスチャの上に _glowmask の色を目の光 (eyes: 陰なし・足し合わせ) で重ねる。
//    GeckoLib の AutoGlowingGeoLayer は光る画素を基から抜いて面の陰を受けさせるので、明るい面と同じ色に沈む
public class StormHeartRenderer extends LowPolyGeoRenderer<StormHeart> {
    // 模型は段階 1 で約 27 単位 (1.7 ブロック) の幅。判定箱 (4.5 x 6) に合わせて 3 倍 (前の 2 倍の 1.5 倍。迫力のため)
    private static final float SCALE = 6.0f;  // 2026-11-08 の声「心臓の大きさ2倍にして」で 2 倍

    public StormHeartRenderer(EntityRendererProvider.Context context) {
        // 低ポリ (client/lowpoly): 段階ごとに別のメッシュ。
        super(context, new Model(), h -> "storm_heart_p" + Model.phase(h), SCALE);
        this.shadowRadius = 0f;
    }


    @Override
    protected int getBlockLightLevel(StormHeart heart, BlockPos pos) {
        return 15;
    }


    // 倒れるときは横倒しにならず、死のアニメーションで見せる (HeartBoss)。倒れている間も赤く染めない
    @Override
    protected float getDeathMaxRotation(StormHeart heart) {
        return 0f;
    }

    @Override
    public int getPackedOverlay(StormHeart heart, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(heart.hurtTime > 0));
    }


    static final class Model extends GeoModel<StormHeart> {
        private static final ResourceLocation ANIMATION = path("animations/entity/storm_heart.animation.json");

        @Override
        public ResourceLocation getModelResource(StormHeart heart) {
            return path("geo/entity/storm_heart_p" + phase(heart) + "_lowpoly" + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(StormHeart heart) {
            return path("textures/lowpoly/storm_heart_p" + phase(heart) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(StormHeart heart) {
            return ANIMATION;
        }

        static int phase(StormHeart heart) {
            return Math.min(StormHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase())) + 1;
        }

        static ResourceLocation path(String p) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
        }
    }
}
