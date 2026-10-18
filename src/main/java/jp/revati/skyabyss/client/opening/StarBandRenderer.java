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

    // この mod 自身の星 (歌のあいだだけ描く)。数と大きさは、バニラの星 (1500 個、ほぼ 1 ピクセル) より目立つ程度
    private static final int STAR_COUNT = 2200;
    private static final float[] STAR_DIR = new float[STAR_COUNT * 3];
    private static final float[] STAR_SIZE = new float[STAR_COUNT];
    private static final float[] STAR_LIGHT = new float[STAR_COUNT];
    private static final float[] STAR_TINT = new float[STAR_COUNT];

    static {
        java.util.Random random = new java.util.Random(0x5EA5ABB5L);
        for (int i = 0; i < STAR_COUNT; i++) {
            // 球面に一様 (空は星の球ごと回るので、どの向きの星も使う)
            float y = -1f + random.nextFloat() * 2f;
            float phi = random.nextFloat() * (float) (Math.PI * 2);
            float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
            STAR_DIR[i * 3] = r * Mth.cos(phi);
            STAR_DIR[i * 3 + 1] = y;
            STAR_DIR[i * 3 + 2] = r * Mth.sin(phi);
            float rank = random.nextFloat();
            STAR_SIZE[i] = 0.2f + rank * rank * rank * 0.42f;
            STAR_LIGHT[i] = 0.55f + 0.45f * random.nextFloat();
            STAR_TINT[i] = random.nextFloat();
        }
    }

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
        if (level == null || (progress < 0f && OpeningClient.starLayer() <= 0.01f)) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 帯が動いていない間 (星の出入りだけ) は、帯は空の外に置く
        float center = progress < 0f ? -10f : center(progress);
        float lo = Math.max(0f, center - HALF_WIDTH);
        float hi = Math.min((float) Math.PI, center + HALF_WIDTH);
        // 昼に起こさないはずだが、念のため星の出ていない空には帯を描かない。自前の星は描く (星の出入りは時刻に頼らない)
        boolean band = progress >= 0f && hi > lo && level.getStarBrightness(partial) > 0.01f;
        Vec3 sky = level.getSkyColor(event.getCamera().getPosition(), partial);
        float[] moon = moonDirection(level, partial);
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        if (band) {
            drawBand(pose, sky, moon, center, lo, hi);
        }
        drawStars(pose, level, partial, center, moon);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // 空色の帯 (バニラの星を覆う)
    private static void drawBand(Matrix4f pose, Vec3 sky, float[] moon, float center, float lo, float hi) {
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
    }

    // 自前の星。向きはバニラの空と同じ回り方 (Y を -90 度、X を空の角度)。帯の中は、帯と同じ濃さの分だけ消える (帯の外の星はそのまま光り続ける)。
    // 星は空の描画のすぐあとに描くので、雲・地形・霧は上から被さる (雲が厚ければ見えない。それは自然)
    private static void drawStars(Matrix4f pose, ClientLevel level, float partial, float center, float[] moon) {
        float layer = OpeningClient.starLayer();
        if (layer <= 0.01f) {
            return;
        }
        float theta = level.getTimeOfDay(partial) * (float) (Math.PI * 2);
        float sin = Mth.sin(theta);
        float cos = Mth.cos(theta);
        double twinkle = (Minecraft.getInstance().level.getGameTime() + partial) * 0.07;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int i = 0; i < STAR_COUNT; i++) {
            float bx = STAR_DIR[i * 3];
            float by = STAR_DIR[i * 3 + 1];
            float bz = STAR_DIR[i * 3 + 2];
            // X 軸まわりに θ、そのあと Y 軸まわりに -90 度
            float y1 = by * cos - bz * sin;
            float z1 = by * sin + bz * cos;
            float[] d = {-z1, y1, bx};
            if (d[1] < -0.03f) {
                continue;
            }
            // 帯の中心との位置 (帯は X 軸からの角 psi で決まる)
            float psi = (float) Math.acos(Mth.clamp(d[0], -1f, 1f));
            // 帯の芯は星が完全に消え、縁だけ薄く残る (帯の濃さの 3 倍で頭打ち。帯の幅が一目で読める)
            float gone = Mth.clamp(profile(psi, center) * awayFromMoon(d, moon) * 3f, 0f, 1f);
            float alpha = layer * STAR_LIGHT[i] * (1f - gone) * (0.88f + 0.12f * (float) Math.sin(twinkle + i * 1.7));
            // 地平線に近いほど薄く
            alpha *= Mth.clamp((d[1] + 0.03f) / 0.2f, 0f, 1f);
            if (alpha <= 0.01f) {
                continue;
            }
            any = true;
            // 星の向きに直角な 2 本の軸で四角を作る
            float ax = 0f;
            float ay = d[2];
            float az = -d[1];
            float len = (float) Math.sqrt(ay * ay + az * az);
            if (len < 1.0e-3f) {
                ax = 0f;
                ay = 0f;
                az = 1f;
                len = 1f;
            }
            ax /= len;
            ay /= len;
            az /= len;
            float cx = d[1] * az - d[2] * ay;
            float cy = d[2] * ax - d[0] * az;
            float cz = d[0] * ay - d[1] * ax;
            float s = STAR_SIZE[i];
            float px = d[0] * RADIUS;
            float py = d[1] * RADIUS;
            float pz = d[2] * RADIUS;
            float r = 0.86f + 0.14f * STAR_TINT[i];
            float g = 0.9f + 0.1f * STAR_TINT[i];
            float b = 1.0f - 0.12f * STAR_TINT[i];
            buffer.addVertex(pose, px + (-ax - cx) * s, py + (-ay - cy) * s, pz + (-az - cz) * s).setColor(r, g, b, alpha);
            buffer.addVertex(pose, px + (ax - cx) * s, py + (ay - cy) * s, pz + (az - cz) * s).setColor(r, g, b, alpha);
            buffer.addVertex(pose, px + (ax + cx) * s, py + (ay + cy) * s, pz + (az + cz) * s).setColor(r, g, b, alpha);
            buffer.addVertex(pose, px + (-ax + cx) * s, py + (-ay + cy) * s, pz + (-az + cz) * s).setColor(r, g, b, alpha);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            if (any) {
                BufferUploader.drawWithShader(mesh);
            } else {
                mesh.close();
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float psi, float ring, float center, float[] moon, float r, float g, float b) {
        float[] d = direction(psi, ring);
        float alpha = profile(psi, center) * awayFromMoon(d, moon) * 0.96f;
        buffer.addVertex(pose, d[0] * RADIUS, d[1] * RADIUS, d[2] * RADIUS).setColor(r, g, b, alpha);
    }
}
