package jp.revati.skyabyss.client.sky;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 昼の月 (2026-11-05 の決めごと c6_look = day。docs/design/46 の 1)。錨が緩んで月が寄ってくる間 (深淵のメインを終えてから、月の核を止めるまで)、
// 昼の空の北の高い所 (仰角 50 度) に、白く薄い大きな満月を描く。夜はバニラの月があるので薄れて消える。雨では薄く。
// 絵はバニラの月 (moon_phases の満月の区画) を、バニラの月と同じ足し合わせで描く (黒い所は空に足されない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DayMoonRenderer {
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    // 見かけの幅 (度)。バニラの月の区画はおよそ 23 度 (月の円はその 4 分の 1 ほど)。近い月なので 2 倍ほど
    static final double WIDTH_DEG = 50;
    static final double YAW = 180;
    static final double ELEV = 50;
    static final float ALPHA = 0.5f;

    private DayMoonRenderer() {
    }

    // 描くか: 深淵のメイン (main_abyss) を終え、月の核 (main_moon_core) をまだ止めていない
    static boolean wanted(QuestLog log) {
        QuestLog.Entry abyss = log.entry("main_abyss");
        QuestLog.Entry core = log.entry("main_moon_core");
        return abyss != null && abyss.done && (core == null || !core.done);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD || !wanted(QuestLog.of(mc.player))) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float angle = level.getTimeOfDay(partial);
        float day = Mth.clamp(Mth.cos(angle * Mth.TWO_PI) * 2f + 0.5f, 0f, 1f);
        float a = ALPHA * day * (1f - level.getRainLevel(partial) * 0.8f);
        if (a <= 0.01f) {
            return;
        }
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        Vec3 d = EndingSkyClient.dir(YAW, ELEV);
        double dist = 100.0;
        Vec3 rel = d.scale(dist);
        float w = (float) (dist * Math.tan(Math.toRadians(WIDTH_DEG * 0.5)));
        Vec3 right = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 up = right.cross(d);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOON);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // 満月は 4 × 2 の区画の左上 (u 0〜0.25、v 0〜0.5)
        EndingSkyClient.vertex(buf, pose, rel, right, up, -w, -w, 0.25f, 0.5f, 1f, 1f, 1f, a);
        EndingSkyClient.vertex(buf, pose, rel, right, up, w, -w, 0f, 0.5f, 1f, 1f, 1f, a);
        EndingSkyClient.vertex(buf, pose, rel, right, up, w, w, 0f, 0f, 1f, 1f, 1f, a);
        EndingSkyClient.vertex(buf, pose, rel, right, up, -w, w, 0.25f, 0f, 1f, 1f, 1f, a);
        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
