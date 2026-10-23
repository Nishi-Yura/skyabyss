package jp.revati.skyabyss.client.entity;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// 岩鯨の心臓の模型。段階ごとに別の geo とテクスチャを使う (殻の枚数が違うため)
public class StoneWhaleHeartModel extends GeoModel<StoneWhaleHeart> {
    private static final ResourceLocation ANIMATION = path("animations/entity/stone_whale_heart.animation.json");

    @Override
    public ResourceLocation getModelResource(StoneWhaleHeart heart) {
        // 低ポリ: 段階ごとの骨だけの geo (tools/install/install_lowpoly.py が stone_whale_heart_pN として書く)
        if (LowPoly.enabled) {
            return path("geo/entity/" + meshName(heart) + "_lowpoly" + (LowPoly.debug ? "_debug" : "") + ".geo.json");
        }
        return LowPoly.cubeOr(path("geo/entity/stone_whale_heart_p" + phase(heart) + ".geo.json"),
                path("geo/entity/" + meshName(heart) + "_lowpoly.geo.json"));
    }

    @Override
    public ResourceLocation getTextureResource(StoneWhaleHeart heart) {
        if (LowPoly.enabled) {
            return LowPoly.debug ? path("textures/lowpoly/debug.png") : path("textures/lowpoly/" + meshName(heart) + ".png");
        }
        return LowPoly.cubeOr(path("textures/entity/stone_whale_heart_p" + phase(heart) + ".png"), path("textures/lowpoly/" + meshName(heart) + ".png"));
    }

    @Override
    public ResourceLocation getAnimationResource(StoneWhaleHeart heart) {
        return ANIMATION;
    }

    // 低ポリのメッシュ (lowpoly/stone_whale_heart_pN.json) の名前
    static String meshName(StoneWhaleHeart heart) {
        return "stone_whale_heart_p" + phase(heart);
    }

    // 段階は 1 から 3 の番号 (ファイル名に使う)
    static int phase(StoneWhaleHeart heart) {
        return Math.min(StoneWhaleHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase())) + 1;
    }

    private static ResourceLocation path(String p) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, p);
    }
}
