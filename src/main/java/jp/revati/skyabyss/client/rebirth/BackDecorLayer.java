package jp.revati.skyabyss.client.rebirth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rebirth.RebirthConfig;
import jp.revati.skyabyss.rebirth.RebirthTier;
import jp.revati.skyabyss.rebirth.RebirthView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 背中の飾り (C08)。転生 5 回で小さな紋章、10 回で短い布、20 回で光の羽。重ねて足す (20 回なら三つとも)。
// 防具の上に薄く載せる: 胸当ての外側は体の背から 3 ピクセルなので、布は 3.2〜3.6、紋章はその上 (布があれば 3.6〜4.0、なければ 3.2〜3.6) に置き、防具と重ならない。
// 鞘羽 (エリトラ) を着けている間は描かない。自分・ほかの人それぞれ設定で切れる。一人称では自分の体が描かれないので出ない。
// すべて閉じた箱の組み合わせ (布と羽は付け根を紋章の板・肩へ沈める)。画像は 32×32 の 1 枚 (紋章・布・羽・縁の 4 区画。灰色で作り、段の色を掛ける)
public final class BackDecorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/rebirth_back.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public BackDecorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    // 飾りの有無 (回数の閾値)
    public static boolean hasEmblem(int count) {
        return count >= 5;
    }

    public static boolean hasCloth(int count) {
        return count >= 10;
    }

    public static boolean hasWings(int count) {
        return count >= 20;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int n = RebirthView.count(p);
        if (!hasEmblem(n) || p.isInvisible() || p.isSpectator() || p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ElytraItem) {
            return;
        }
        boolean self = p == Minecraft.getInstance().player;
        if (!RebirthConfig.on(self ? RebirthConfig.BACK_DECOR_SELF : RebirthConfig.BACK_DECOR_OTHERS)) {
            return;
        }
        long t = RebirthClient.timeMs();
        boolean still = RebirthClient.still();
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        float emblemZ = 3.2f;
        if (hasCloth(n)) {
            // 布: 肩から腰へ。歩くと少し揺れる (動きを減らす設定では一定の傾き)
            float sway = 4f + (still ? 0f : Mth.clamp(limbSwingAmount, 0f, 1f) * 14f + Mth.sin(ageInTicks * 0.06f) * 1.2f);
            pose.pushPose();
            pose.translate(0f, 0.5f / 16f, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(sway));
            int cloth = RebirthTier.border(n, t, still, 0.3f);
            box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), -4f, 0f, 3.2f, 4f, 11f, 3.6f, 16, 0, 32, 16, tint(cloth, 0.62f), light);
            pose.popPose();
            emblemZ = 3.6f;
        }
        // 紋章: 肩甲の間の小さな板 (6×6)。布の上
        box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), -3f, 2f, emblemZ - 0.2f, 3f, 8f, emblemZ + 0.4f, 0, 0, 16, 16,
                tint(RebirthTier.accent(n, t, still), 1f), light);
        if (hasWings(n)) {
            wings(pose, buffers, p, n, t, still, ageInTicks, emblemZ);
        }
        pose.popPose();
    }

    // 光の羽: 紋章の板の両脇から扇に 4 枚ずつ (薄い箱)。付け根の箱を板の中へ沈める。ゆっくり羽ばたく
    private static void wings(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, int n, long t, boolean still, float age, float z) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float flap = still ? 0f : Mth.sin(age * 0.09f) * 5f;
        float[] length = {10f, 12f, 11f, 8f};
        float[] angle = {20f, 46f, 70f, 92f};
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                pose.translate(side * 2.4f / 16f, 3.0f / 16f, (z + 0.1f) / 16f);
                pose.mulPose(Axis.ZP.rotationDegrees(side * (angle[i] + flap * (i + 1) / 4f) + 180f));
                int rgb = RebirthTier.border(n, t, still, i * 0.12f + (side > 0 ? 0.5f : 0f));
                // 付け根 (幅 1.6) を板の中へ 1 ピクセル沈めて、先へ length
                box(pose, vc, -0.8f, -1f, -0.3f, 0.8f, length[i], 0.1f, 0, 16, 16, 32, (0xC8 << 24) | (rgb & 0xFFFFFF), FULL_BRIGHT);
                pose.popPose();
            }
        }
    }

    private static int tint(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * f));
        int b = Math.min(255, Math.round((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // 閉じた箱 (座標はピクセル。背 +z の面に u0v0-u1v1 の絵、ほかの面は縁の区画 (16,16)-(20,20) の 1 色)。色は ARGB
    private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            int u0, int v0, int u1, int v1, int argb, int light) {
        PoseStack.Pose pp = pose.last();
        float a = x0 / 16f, b = y0 / 16f, c = z0 / 16f, d = x1 / 16f, e = y1 / 16f, f = z1 / 16f;
        float eu0 = 17 / 32f, ev0 = 17 / 32f, eu1 = 19 / 32f, ev1 = 19 / 32f;
        // 背 (+z): 絵。x を反転して読める向きに (背中から見て左が +x)
        quad(pp, vc, d, b, f, a, b, f, a, e, f, d, e, f, 0, 0, 1, u0 / 32f, v0 / 32f, u1 / 32f, v1 / 32f, argb, light);
        // 前 (-z)
        quad(pp, vc, a, b, c, d, b, c, d, e, c, a, e, c, 0, 0, -1, eu0, ev0, eu1, ev1, argb, light);
        // 左右
        quad(pp, vc, d, b, c, d, b, f, d, e, f, d, e, c, 1, 0, 0, eu0, ev0, eu1, ev1, argb, light);
        quad(pp, vc, a, b, f, a, b, c, a, e, c, a, e, f, -1, 0, 0, eu0, ev0, eu1, ev1, argb, light);
        // 上下 (y は下向きが +)
        quad(pp, vc, a, b, f, d, b, f, d, b, c, a, b, c, 0, -1, 0, eu0, ev0, eu1, ev1, argb, light);
        quad(pp, vc, a, e, c, d, e, c, d, e, f, a, e, f, 0, 1, 0, eu0, ev0, eu1, ev1, argb, light);
    }

    private static void quad(PoseStack.Pose pp, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float nx, float ny, float nz, float u0, float v0, float u1, float v1, int argb, int light) {
        vc.addVertex(pp, x0, y0, z0).setColor(argb).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pp, nx, ny, nz);
        vc.addVertex(pp, x1, y1, z1).setColor(argb).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pp, nx, ny, nz);
        vc.addVertex(pp, x2, y2, z2).setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pp, nx, ny, nz);
        vc.addVertex(pp, x3, y3, z3).setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pp, nx, ny, nz);
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Register {
        @SubscribeEvent
        static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r && r.getModel() instanceof PlayerModel<?>) {
                    @SuppressWarnings("unchecked")
                    LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pr = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) r;
                    pr.addLayer(new BackDecorLayer(pr));
                }
            }
        }
    }
}
