package jp.revati.skyabyss.client.entity;

import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// 「B 切り絵の悪夢」(2026-11-08 の声「心臓の後のボスたちの見た目をすべてこういうのにして」。tools/gen/mobs/paper_bosses.py が体を塗り直す)。
// 心臓の後のボス (五章の淵の主と深みの王・心臓の番人・六章の月・終章の雲海) のまわりに、切り抜いた紙の菱形 (生成りと桃色) をゆっくり浮かせて回す。
// 体の大きさに合わせて輪の半径と高さを決める。倒れているあいだは出さない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PaperCutLayer {
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/paper_cut.png");
    static final Set<String> TYPES = Set.of("full_keeper", "moon_core", "moon_scale", "shadow_embracer", "waning_blade", "lamp_eye", "last_bellkeeper",
            "pilgrim_eye", "pilgrim_hand", "pilgrim_moon", "pilgrim_plume", "rewinder", "founder", "heart_warden");
    static final int COUNT = 10;

    private PaperCutLayer() {
    }

    public static boolean paper(LivingEntity e) {
        if (e instanceof jp.revati.skyabyss.entity.GuardMob g && g.bossLook() != 0) {
            return true;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(SkyAbyss.MODID)
                && TYPES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
    }

    // GeckoLib の描き手は RenderLivingEvent を出さないので、世界を描き終えたところで、近くのボスのまわりに描く
    @SubscribeEvent
    static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        net.minecraft.world.phys.Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        for (net.minecraft.world.entity.Entity ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof LivingEntity e) || e.distanceToSqr(cam) > 96 * 96 || e.isDeadOrDying() || e.isInvisible() || !paper(e)) {
                continue;
            }
            double x = Mth.lerp(pt, e.xo, e.getX()) - cam.x;
            double y = Mth.lerp(pt, e.yo, e.getY()) - cam.y;
            double z = Mth.lerp(pt, e.zo, e.getZ()) - cam.z;
            pose.pushPose();
            pose.translate(x, y, z);
            draw(pose, vc, e, (e.tickCount + pt) / 20f);
            pose.popPose();
        }
        buf.endBatch(RenderType.entityCutoutNoCull(TEX));
    }

    static void draw(PoseStack pose, VertexConsumer vc, LivingEntity e, float t) {
        float w = e.getBbWidth();
        float h = e.getBbHeight();
        float ring = w * 0.6f + 1.4f;
        float size = Mth.clamp(h * 0.16f, 0.5f, 1.5f);
        for (int i = 0; i < COUNT; i++) {
            float a = t * 0.35f + i * Mth.TWO_PI / COUNT;
            float y = h * (0.25f + 0.75f * (0.5f + 0.5f * Mth.sin(t * 0.8f + i * 1.7f)));
            float r = ring * (0.85f + 0.15f * Mth.sin(t * 0.6f + i));
            pose.pushPose();
            pose.translate(Mth.cos(a) * r, y, Mth.sin(a) * r);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(-a + t * 1.3f + i));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(12f * Mth.sin(t * 1.1f + i)));
            float s = size * (i % 3 == 0 ? 1.35f : 1f);
            quad(pose.last().pose(), vc, s, i % 2 == 0 ? 0f : 0.5f);
            pose.popPose();
        }
    }

    private static void quad(Matrix4f m, VertexConsumer vc, float s, float u0) {
        float u1 = u0 + 0.5f;
        int light = LightTexture.FULL_BRIGHT;
        int ov = OverlayTexture.NO_OVERLAY;
        vc.addVertex(m, -s, -s, 0).setColor(255, 255, 255, 255).setUv(u0, 1).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, s, -s, 0).setColor(255, 255, 255, 255).setUv(u1, 1).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, s, s, 0).setColor(255, 255, 255, 255).setUv(u1, 0).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, -s, s, 0).setColor(255, 255, 255, 255).setUv(u0, 0).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
    }
}
