package jp.revati.skyabyss.titan.storm.raiju.client;


import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.storm.raiju.Raiju;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

// 雷獣の描画 (模型は tools/install/install_raiju.py が書く。ラフ A 狼型)。
// 背の鬣の棘・瞳・耳の先・爪・尾の芯を光らせる (GlowLayer: 基のテクスチャの上に _glowmask の色を目の光で足す。
// 潮殻の心臓と同じ描き方。GeckoLib の AutoGlowingGeoLayer は光る画素を基から抜いて面の陰を受けるため、暗い巣で沈む)。
// 死ぬときはバニラの「横に倒れる」回転をせず、模型の死のアニメで倒れる
public class RaijuRenderer extends LowPolyGeoRenderer<Raiju> {

    public RaijuRenderer(EntityRendererProvider.Context context) {
        // 低ポリ (client/lowpoly):。低ポリのときは LowPolyGeoRenderer の発光層
        super(context, new LowPolyGeoRenderer.Model<>("raiju", false), r -> "raiju", 1.0f);
        this.shadowRadius = 1.0f;
    }


    @Override
    protected float getDeathMaxRotation(Raiju animatable) {
        return 0f;
    }

}
