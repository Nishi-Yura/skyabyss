package jp.revati.skyabyss.titan.tide.heart.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import jp.revati.skyabyss.titan.tide.heart.TideBubble;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

// 泡の牢の描画: 薄い水色のガラスの箱を、判定の大きさで、ゆっくり揺らして描く (泡の粒はエンティティの側)
public class TideBubbleRenderer extends EntityRenderer<TideBubble> {
    private final BlockRenderDispatcher blocks;

    public TideBubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0f;
    }

    @Override
    public void render(TideBubble bubble, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = bubble.tickCount + partialTick;
        float wobble = 1f + 0.04f * Mth.sin(t * 0.35f);
        pose.pushPose();
        pose.translate(0, 1.2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2f));
        pose.scale(1.4f * wobble, 2.4f / wobble, 1.4f * wobble);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(bubble, yaw, partialTick, pose, buffers, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(TideBubble bubble) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
