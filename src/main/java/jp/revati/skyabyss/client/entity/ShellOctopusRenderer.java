package jp.revati.skyabyss.client.entity;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.dungeon.mob.ShellOctopus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;

// 殻負いの大蛸 (dungeon/mob/ShellOctopus): 切り落とされた腕 (ShellOctopus.CUT_ORDER の順) の骨を描かない
public class ShellOctopusRenderer extends LowPolyGeoRenderer<ShellOctopus> {
    public ShellOctopusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "shell_octopus", 2.0f);
    }

    @Override
    public void preRender(PoseStack poseStack, ShellOctopus animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        int cut = 5 - animatable.arms();
        for (int i = 0; i < ShellOctopus.CUT_ORDER.length; i++) {
            boolean hide = i < cut;
            int k = ShellOctopus.CUT_ORDER[i];
            model.getBone("arm" + k).ifPresent(b -> b.setHidden(hide));
            model.getBone("arm" + k + "_m").ifPresent(b -> b.setHidden(hide));
            model.getBone("arm" + k + "_tip").ifPresent(b -> b.setHidden(hide));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
