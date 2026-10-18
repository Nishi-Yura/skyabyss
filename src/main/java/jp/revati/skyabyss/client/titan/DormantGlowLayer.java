package jp.revati.skyabyss.client.titan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.titan.end.DormantTitan;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 眠る巨神 (DormantTitan) の光る画素の層。眠ると光が揺らぎながら消える (glowFactor)。起きているあいだは AutoGlowingGeoLayer と同じ
public class DormantGlowLayer<T extends Entity & GeoAnimatable & DormantTitan> extends AutoGlowingGeoLayer<T> {
    public DormantGlowLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        float factor = animatable.glowFactor(partialTick);
        if (factor >= 0.999f) {
            super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            return;
        }
        if (factor <= 0.003f) {
            return;
        }
        RenderType glow = getRenderType(animatable, bufferSource);
        if (glow == null) {
            return;
        }
        int v = Mth.clamp(Math.round(factor * 255f), 0, 255);
        int colour = 0xFF000000 | (v << 16) | (v << 8) | v;
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, colour);
    }
}
