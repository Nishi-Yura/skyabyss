package jp.revati.skyabyss.story.ch6.rift.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import jp.revati.skyabyss.story.ch6.rift.EchoShade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

// 自分の残像の描画 (docs/design/46 の 4.6)。持ち主の姿 (肌) を借りて、青白く透かして描く。
//   なぞる残像は青白い白、人の残像 (剣守の段 3) は藍。薄れていくとき・光の中の人の残像は薄く。歩きの揺れは位置の差から (EchoShade.walk*)
public class EchoShadeRenderer extends EntityRenderer<EchoShade> {
    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    public EchoShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.0f;
    }

    private static PlayerSkin skin(UUID owner) {
        if (owner != null && Minecraft.getInstance().getConnection() != null) {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(owner);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(owner == null ? UUID.randomUUID() : owner);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoShade e) {
        return skin(e.owner()).texture();
    }

    @Override
    public void render(EchoShade e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        PlayerSkin s = skin(e.owner());
        PlayerModel<LivingEntity> m = s.model() == PlayerSkin.Model.SLIM ? slim : wide;
        float walk = e.walkPos - (1 - pt) * Mth.lerp(pt, e.walkSpeedO, e.walkSpeed);
        float amt = Math.min(1f, Mth.lerp(pt, e.walkSpeedO, e.walkSpeed));
        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        pose(m, walk, amt, pitch);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0, -1.501, 0);
        float a = (1f - e.fade()) * (e.chasing() ? 0.6f : 0.55f);
        int alpha = Math.max(8, (int) (a * 255));
        int color = e.chasing() ? (alpha << 24) | 0x5A68C8 : (alpha << 24) | 0xC8DCFF;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(s.texture()));
        m.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        pose.popPose();
        super.render(e, yaw, pt, pose, buf, light);
    }

    // 歩きの揺れ (人の模型の決まりと同じ形)。頭は上下の向きだけ
    private static void pose(PlayerModel<LivingEntity> m, float walk, float amt, float pitch) {
        m.young = false;
        m.crouching = false;
        m.head.xRot = pitch * Mth.DEG_TO_RAD;
        m.head.yRot = 0;
        m.body.xRot = 0;
        m.rightArm.xRot = Mth.cos(walk * 0.6662f + Mth.PI) * 1.6f * amt * 0.5f;
        m.leftArm.xRot = Mth.cos(walk * 0.6662f) * 1.6f * amt * 0.5f;
        m.rightArm.zRot = 0.05f;
        m.leftArm.zRot = -0.05f;
        m.rightLeg.xRot = Mth.cos(walk * 0.6662f) * 1.4f * amt;
        m.leftLeg.xRot = Mth.cos(walk * 0.6662f + Mth.PI) * 1.4f * amt;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }
}
