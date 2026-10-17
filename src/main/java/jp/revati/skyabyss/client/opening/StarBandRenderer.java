package jp.revati.skyabyss.client.opening;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// B9 夜の星の欠け (docs/design/17 の 3.7節): 夜空の星が、幅 50 度ほどの帯のかたちで、30 秒かけて西から東へ消えて、また戻る (何かが上を通っている)。月は残る。
// 新しい描画は作らない考え方: 空を描いたあと (AFTER_SKY) に、空の色の帯を重ねるだけ。帯は東西の軸まわりの輪 (西の地平線 → 真上 → 東の地平線へ動く)。
// 星が回る向きとは関係なく、空の同じ場所を帯が横切る。月の方向の近くは帯の濃さを下げて、月は隠さない。
// 見た目の強さは、画面の効果の強さ (screenEffectStrength) には従わない (欠けが見えないと歌の意味がないため。ホラーの強さ 0 では、そもそも起こらない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StarBandRenderer {
    // 帯の半幅 (ラジアン。全体で約 50 度)
    private static final float HALF_WIDTH = 0.44f;
    private static final float RADIUS = 92f;
    private static final int PSI_STEPS = 16;
    private static final int RING_STEPS = 32;
    // 輪の両端を地平線の少し下まで広げる (空と地平線のあいだに星が覗かないように)
    private static final float RING_EXTRA = 0.14f;

    private StarBandRenderer() {
    }

    // 帯の中心の位置 (西の地平線 = π → 東の地平線 = 0)。progress は 0〜1。両端は空の外から入って出ていく
    static float center(float progress) {
        return Mth.lerp(progress, (float) Math.PI + HALF_WIDTH, -HALF_WIDTH);
    }

    // 輪の一点の方向 (東西の軸 X のまわり)。psi: X 軸からの角、ring: 輪の上の角 (0〜π が上半分)
    static float[] direction(float psi, float ring) {
        float s = Mth.sin(psi);
        return new float[]{Mth.cos(psi), s * Mth.sin(ring), s * Mth.cos(ring)};
    }

    // 帯の濃さ (中心 1 → 縁 0)
    static float profile(float psi, float center) {
        float x = (psi - center) / HALF_WIDTH;
        if (Math.abs(x) >= 1f) {
            return 0f;
        }
        float c = Mth.cos(x * (float) Math.PI * 0.5f);
        return c * c;
    }

    // 月 (太陽の反対側) の方向。空の回り方は LevelRenderer.renderSky と同じ: 太陽は (-sin θ, cos θ, 0)
    static float[] moonDirection(ClientLevel level, float partialTick) {
        float theta = level.getTimeOfDay(partialTick) * (float) (Math.PI * 2);
        return new float[]{Mth.sin(theta), -Mth.cos(theta), 0f};
    }

    // 月から離れるほど濃く (月の近くは帯を薄くして、月を残す)
    static float awayFromMoon(float[] dir, float[] moon) {
        float dot = Mth.clamp(dir[0] * moon[0] + dir[1] * moon[1] + dir[2] * moon[2], -1f, 1f);
        float angle = (float) Math.acos(dot);
        float x = Mth.clamp((angle - 0.14f) / (0.5f - 0.14f), 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float progress = OpeningClient.bandProgress();
        ClientLevel level = Minecraft.getInstance().level;
        if (progress < 0f || level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 昼に起こさないはずだが、念のため星の出ていない空には描かない
        if (level.getStarBrightness(partial) <= 0.01f) {
            return;
        }
        float center = center(progress);
        float lo = Math.max(0f, center - HALF_WIDTH);
        float hi = Math.min((float) Math.PI, center + HALF_WIDTH);
        if (hi <= lo) {
            return;
        }
        Vec3 sky = level.getSkyColor(event.getCamera().getPosition(), partial);
        float[] moon = moonDirection(level, partial);
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = (float) sky.x;
        float g = (float) sky.y;
        float b = (float) sky.z;
        for (int i = 0; i < PSI_STEPS; i++) {
            float psi0 = Mth.lerp(i / (float) PSI_STEPS, lo, hi);
            float psi1 = Mth.lerp((i + 1) / (float) PSI_STEPS, lo, hi);
            for (int j = 0; j < RING_STEPS; j++) {
                float ring0 = Mth.lerp(j / (float) RING_STEPS, -RING_EXTRA, (float) Math.PI + RING_EXTRA);
                float ring1 = Mth.lerp((j + 1) / (float) RING_STEPS, -RING_EXTRA, (float) Math.PI + RING_EXTRA);
                vertex(buffer, pose, psi0, ring0, center, moon, r, g, b);
                vertex(buffer, pose, psi1, ring0, center, moon, r, g, b);
                vertex(buffer, pose, psi1, ring1, center, moon, r, g, b);
                vertex(buffer, pose, psi0, ring1, center, moon, r, g, b);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float psi, float ring, float center, float[] moon, float r, float g, float b) {
        float[] d = direction(psi, ring);
        float alpha = profile(psi, center) * awayFromMoon(d, moon) * 0.96f;
        buffer.addVertex(pose, d[0] * RADIUS, d[1] * RADIUS, d[2] * RADIUS).setColor(r, g, b, alpha);
    }
}
