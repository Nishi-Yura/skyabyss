package jp.revati.skyabyss.titan.storm.raiju.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.storm.raiju.Raiju;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 雷獣の描画 (模型は tools/install_raiju.py が書く。ラフ A 狼型)。
// 背の鬣の棘・瞳・耳の先・爪・尾の芯を光らせる (GlowLayer: 基のテクスチャの上に _glowmask の色を目の光で足す。
// 潮殻の心臓と同じ描き方。GeckoLib の AutoGlowingGeoLayer は光る画素を基から抜いて面の陰を受けるため、暗い巣で沈む)。
// 死ぬときはバニラの「横に倒れる」回転をせず、模型の死のアニメで倒れる
public class RaijuRenderer extends LowPolyGeoRenderer<Raiju> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/raiju_glowmask.png");

    public RaijuRenderer(EntityRendererProvider.Context context) {
        // 低ポリ (client/lowpoly): LowPoly.enabled = false で元のキューブの geo と自前の発光層に戻る。低ポリのときは LowPolyGeoRenderer の発光層
        super(context, new LowPolyGeoRenderer.Model<>("raiju", false), r -> "raiju", 1.0f);
        addRenderLayer(new GlowLayer(this));
        this.shadowRadius = 1.0f;
    }

    @Override
    protected boolean autoGlow() {
        return LowPoly.enabled;
    }

    @Override
    protected float getDeathMaxRotation(Raiju animatable) {
        return 0f;
    }

    static final class GlowLayer extends GeoRenderLayer<Raiju> {
        GlowLayer(RaijuRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, Raiju raiju, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (raiju.isInvisible() || LowPoly.enabled) {
                return;
            }
            RenderType glow = RenderType.eyes(GLOW);
            getRenderer().reRender(model, poseStack, buffers, raiju, glow, buffers.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, getRenderer().getRenderColor(raiju, partialTick, packedLight).argbInt());
        }
    }
}
