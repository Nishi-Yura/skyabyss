package jp.revati.skyabyss.client.entity;

import jp.revati.skyabyss.entity.StoneWhaleHeart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 岩鯨の心臓の描画。段階に応じた模型と、暗闇で光る核や割れ目を描く。模型は model 側で段階ごとに切り替える
public class StoneWhaleHeartRenderer extends GeoEntityRenderer<StoneWhaleHeart> {
    // 模型は 24 単位 (1.5 ブロック) の大きさ。判定箱 (3.4 x 4.2) に合わせて拡大する
    private static final float SCALE = 2.5f;

    public StoneWhaleHeartRenderer(EntityRendererProvider.Context context) {
        super(context, new StoneWhaleHeartModel());
        withScale(SCALE);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // 倒れるときは横倒しにならず、死のアニメーションで見せる (HeartBoss)。倒れている間も赤く染めない
    @Override
    protected float getDeathMaxRotation(StoneWhaleHeart heart) {
        return 0f;
    }

    @Override
    public int getPackedOverlay(StoneWhaleHeart heart, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(heart.hurtTime > 0));
    }
}
