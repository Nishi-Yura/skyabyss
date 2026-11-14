package jp.revati.skyabyss.story.ch6.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.story.ch6.field.FallingLight;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// 光の塊 (story/ch6/field/FallingLight) を技の形の模型で描く (2026-11-12): 光 → 真珠 (降る光は大きく)、砂の礫 → 岩、三日月の刃 → 風の刃
public class FallingLightRenderer extends EntityRenderer<FallingLight> {
    private final LowPolyGeoRenderer<FallingLight> pearl;
    private final LowPolyGeoRenderer<FallingLight> rock;
    private final LowPolyGeoRenderer<FallingLight> blade;

    public FallingLightRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        pearl = new LowPolyGeoRenderer<>(ctx, "prop_pearl", 1f);
        rock = new LowPolyGeoRenderer<>(ctx, "prop_rock", 1f);
        blade = new LowPolyGeoRenderer<>(ctx, "prop_wind_blade", 1f);
    }

    @Override
    public void render(FallingLight e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        int look = e.look();
        float s = look == 1 ? 0.45f : look == 2 ? 1.1f : e.isShot() ? 0.6f : 1.1f;
        LowPolyGeoRenderer<FallingLight> r = look == 1 ? rock : look == 2 ? blade : pearl;
        pose.pushPose();
        pose.scale(s, s, s);
        r.render(e, e.getYRot(), partial, pose, buffers, 0xF000F0);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(FallingLight e) {
        return pearl.getTextureLocation(e);
    }
}
