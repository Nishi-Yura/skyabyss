package jp.revati.skyabyss.client.entity;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// 岩鯨の心臓の模型。段階ごとに別の geo とテクスチャを使う (殻の枚数が違うため)
public class StoneWhaleHeartModel extends GeoModel<StoneWhaleHeart> {
    private static final ResourceLocation ANIMATION = path("animations/entity/stone_whale_heart.animation.json");

    @Override
    public ResourceLocation getModelResource(StoneWhaleHeart heart) {
        return path("geo/entity/stone_whale_heart_p" + phase(heart) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(StoneWhaleHeart heart) {
        return path("textures/entity/stone_whale_heart_p" + phase(heart) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(StoneWhaleHeart heart) {
        return ANIMATION;
    }

    // 段階は 1 から 3 の番号 (ファイル名に使う)
    static int phase(StoneWhaleHeart heart) {
        return Math.min(StoneWhaleHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase())) + 1;
    }

    private static ResourceLocation path(String p) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
    }
}
