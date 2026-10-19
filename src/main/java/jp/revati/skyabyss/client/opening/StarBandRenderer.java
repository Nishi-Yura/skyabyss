package jp.revati.skyabyss.client.opening;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.Config.StarEffectVariant;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModSounds;
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
        StarEffectVariant variant = StarEffect.variant();
        boolean modern = variant != StarEffectVariant.CURRENT;
        // 帯が動いていない間 (星の出入りだけ) は、帯は空の外に置く
        float center = progress < 0f ? -10f : (modern ? modernCenter(progress) : center(progress));
        float lo = Math.max(0f, center - HALF_WIDTH);
        float hi = Math.min((float) Math.PI, center + HALF_WIDTH);
        // 昼に起こさないはずだが、念のため星の出ていない空には帯を描かない。自前の星は描く (星の出入りは時刻に頼らない)
        boolean band = progress >= 0f && hi > lo && level.getStarBrightness(partial) > 0.01f;
        Vec3 sky = level.getSkyColor(event.getCamera().getPosition(), partial);
        float[] moon = moonDirection(level, partial);
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        float time = level.getGameTime() + partial;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        if (!modern) {
            if (band) {
                drawBand(pose, sky, moon, center, lo, hi);
            }
        } else {
            drawModern(pose, variant, sky, moon, center, progress, time, level.getStarBrightness(partial) > 0.01f);
        }
        drawStars(pose, level, partial, center, moon, variant, time);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------- A〜C (選定用の別案)
    // 影の中心の位置。西の地平線の外から入り、東の地平線の外へ抜けるまで (影の尾が空から出きるまで)
    static float modernCenter(float progress) {
        return Mth.lerp(progress, (float) Math.PI + 0.45f, -0.8f);
    }

    // 影のかたち。dd = 中心からの psi のずれ (負 = 進む先)、ring = 空を南北に渡る角。
    // 戻り値は輪郭からの内側への深さ (正 = 影の中、負 = 外) をラジアンで。進む先は丸く短く、後ろは長い尾、ひれのような出っぱりが両脇に出る
    static float margin(float dd, float ring) {
        float t = Mth.clamp((ring - (float) Math.PI * 0.5f) / ((float) Math.PI * 0.5f), -1.4f, 1.4f);
        float finA = Mth.sin(2.2f + t * 5.5f);
        float finB = Mth.sin(-1.0f + t * 4.0f);
        float front = Math.max(0f, 0.20f * (1f - 0.55f * t * t) + 0.12f * finA * finA * (finA > 0 ? 1f : 0f));
        float back = Math.max(0f, 0.48f * (1f - 0.5f * t * t) + 0.20f * finB * finB * (finB > 0 ? 1f : 0f));
        return Math.min(dd + front, back - dd);
    }

    private static float smoothstep(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    private static float exp(float x) {
        return (float) Math.exp(x);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    // 影の帯のうち、星を覆う面 (バニラの星を隠す) と、輪郭の光・ゆがみの線を描く
    private static void drawModern(Matrix4f pose, StarEffectVariant variant, Vec3 sky, float[] moon, float center, float progress, float time, boolean starsUp) {
        if (progress < 0f) {
            // 動いていない間。B は自前の星だけにするため、空全体を覆う面を、星の出入りに合わせて敷く
            if (variant == StarEffectVariant.B && OpeningClient.starLayer() > 0.01f) {
                drawVeil(pose, variant, sky, moon, center, time, 0f, (float) Math.PI, true);
            }
            return;
        }
        if (!starsUp && OpeningClient.starLayer() <= 0.01f) {
            return;
        }
        // 影の及ぶ範囲 (長さ + 余白)
        float lo = Math.max(0f, center - 0.55f);
        float hi = Math.min((float) Math.PI, center + 0.85f);
        if (variant == StarEffectVariant.B) {
            // 空全体の覆い (自前の星だけを見せる)。ゆがみで動く星を、元の位置のバニラの星と重ねないため
            drawVeil(pose, variant, sky, moon, center, time, 0f, (float) Math.PI, true);
        } else if (hi > lo) {
            drawVeil(pose, variant, sky, moon, center, time, lo, hi, false);
        }
        if (variant != StarEffectVariant.A && hi > lo) {
            drawEdgeGlow(pose, variant, moon, center, time, lo, hi);
        }
        if (variant == StarEffectVariant.C) {
            playSwell(progress);
        }
    }

    private static boolean swellPlayed;

    // C: 影が空に入ってくるとき、低く遠い音がうねる (台本の撮影では音は 0)
    private static void playSwell(float progress) {
        if (progress < 0.04f) {
            swellPlayed = false;
        } else if (!swellPlayed) {
            swellPlayed = true;
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(ModSounds.SKY_CALL.get().getLocation(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.45f, 0.5f, net.minecraft.util.RandomSource.create(),
                    false, 0, net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        }
    }

    private static void drawVeil(Matrix4f pose, StarEffectVariant variant, Vec3 sky, float[] moon, float center, float time,
                                 float lo, float hi, boolean whole) {
        int psiSteps = whole ? 24 : 40;
        int ringSteps = whole ? 32 : 64;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = (float) sky.x;
        float g = (float) sky.y;
        float b = (float) sky.z;
        float layer = OpeningClient.starLayer();
        for (int i = 0; i < psiSteps; i++) {
            float psi0 = Mth.lerp(i / (float) psiSteps, lo, hi);
            float psi1 = Mth.lerp((i + 1) / (float) psiSteps, lo, hi);
            for (int j = 0; j < ringSteps; j++) {
                float ring0 = Mth.lerp(j / (float) ringSteps, -RING_EXTRA, (float) Math.PI + RING_EXTRA);
                float ring1 = Mth.lerp((j + 1) / (float) ringSteps, -RING_EXTRA, (float) Math.PI + RING_EXTRA);
                veilVertex(buffer, pose, whole, layer, psi0, ring0, center, moon, r, g, b);
                veilVertex(buffer, pose, whole, layer, psi1, ring0, center, moon, r, g, b);
                veilVertex(buffer, pose, whole, layer, psi1, ring1, center, moon, r, g, b);
                veilVertex(buffer, pose, whole, layer, psi0, ring1, center, moon, r, g, b);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void veilVertex(BufferBuilder buffer, Matrix4f pose, boolean whole, float layer,
                                   float psi, float ring, float center, float[] moon, float r, float g, float b) {
        float[] d = direction(psi, ring);
        float away = awayFromMoon(d, moon);
        float alpha;
        float dark = 1f;
        if (whole) {
            alpha = layer * away * 0.98f;
        } else {
            // A: 影の中は空よりさらに暗く沈む (縁はやわらかく)。肋骨の筋が、うっすら濃淡になる
            float m = margin(psi - center, ring);
            float inside = smoothstep(0.5f + m / 0.10f);
            float rib = 0.5f + 0.5f * Mth.cos((psi - center) * 38f);
            alpha = inside * away * (0.80f + 0.12f * rib) * layer;
            dark = 0.45f;
        }
        buffer.addVertex(pose, d[0] * RADIUS, d[1] * RADIUS, d[2] * RADIUS).setColor(r * dark, g * dark, b * dark, alpha);
    }

    // 影の輪郭に沿う光。B = 細く淡い、ゆがみの縁のゆらぎ。C = オーロラのような淡い青緑の幕
    private static void drawEdgeGlow(Matrix4f pose, StarEffectVariant variant, float[] moon, float center, float time, float lo, float hi) {
        boolean aurora = variant == StarEffectVariant.C;
        int psiSteps = 56;
        int ringSteps = 72;
        float layer = OpeningClient.starLayer();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < psiSteps; i++) {
            float psi0 = Mth.lerp(i / (float) psiSteps, lo, hi);
            float psi1 = Mth.lerp((i + 1) / (float) psiSteps, lo, hi);
            for (int j = 0; j < ringSteps; j++) {
                float ring0 = Mth.lerp(j / (float) ringSteps, -RING_EXTRA, (float) Math.PI + RING_EXTRA);
                float ring1 = Mth.lerp((j + 1) / (float) ringSteps, -RING_EXTRA, (float) Math.PI + RING_EXTRA);
                glowVertex(buffer, pose, aurora, layer, psi0, ring0, center, moon, time);
                glowVertex(buffer, pose, aurora, layer, psi1, ring0, center, moon, time);
                glowVertex(buffer, pose, aurora, layer, psi1, ring1, center, moon, time);
                glowVertex(buffer, pose, aurora, layer, psi0, ring1, center, moon, time);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void glowVertex(BufferBuilder buffer, Matrix4f pose, boolean aurora, float layer, float psi, float ring,
                                   float center, float[] moon, float time) {
        float[] d = direction(psi, ring);
        float m = margin(psi - center, ring);
        float dd = psi - center;
        float r;
        float g;
        float b;
        float alpha;
        if (aurora) {
            // 縁を芯にした幕。外へ長く尾を引き、ゆっくり波打つ
            float edge = exp(-(m * m) / (2f * 0.07f * 0.07f));
            float outer = m < 0f ? exp(m / 0.16f) : 0f;
            float curtain = 0.62f + 0.38f * Mth.sin(ring * 11f + time * 0.05f + dd * 18f);
            float flow = 0.75f + 0.25f * Mth.sin(time * 0.03f + ring * 3f);
            alpha = Math.max(edge * 0.55f, outer * 0.35f) * curtain * flow * layer * (0.5f + 0.5f * awayFromMoon(d, moon));
            float h = Mth.clamp(0.5f + m * 2.5f, 0f, 1f);
            r = Mth.lerp(h, 0.55f, 0.85f);
            g = Mth.lerp(h, 0.95f, 0.98f);
            b = Mth.lerp(h, 0.85f, 1.0f);
        } else {
            // B: 輪郭のすぐ外に、細い光のゆらぎが 2 本。ゆがみの縁がわずかに光って見える程度
            float ring1 = exp(-((m + 0.03f) * (m + 0.03f)) / (2f * 0.018f * 0.018f));
            float ring2 = exp(-((m + 0.12f) * (m + 0.12f)) / (2f * 0.03f * 0.03f)) * 0.5f;
            float shimmer = 0.6f + 0.4f * Mth.sin(time * 0.22f + ring * 14f - dd * 25f);
            alpha = (ring1 + ring2) * shimmer * 0.20f * layer * awayFromMoon(d, moon);
            r = 0.75f;
            g = 0.85f;
            b = 1.0f;
        }
        buffer.addVertex(pose, d[0] * RADIUS, d[1] * RADIUS, d[2] * RADIUS).setColor(r, g, b, alpha);
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
    private static void drawStars(Matrix4f pose, ClientLevel level, float partial, float center, float[] moon, StarEffectVariant variant, float time) {
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
            float gone;
            if (variant == StarEffectVariant.CURRENT) {
                // 帯の芯は星が完全に消え、縁だけ薄く残る (帯の濃さの 3 倍で頭打ち。帯の幅が一目で読める)
                gone = Mth.clamp(profile(psi, center) * awayFromMoon(d, moon) * 3f, 0f, 1f);
            } else {
                float ring = (float) Math.atan2(d[1], d[2]);
                float dd = psi - center;
                float m = margin(dd, ring);
                float away = awayFromMoon(d, moon);
                float jitter = frac(i * 0.618034f);
                if (variant == StarEffectVariant.A) {
                    // 輪郭の縁 (約 7 度) で、星ごとに少しずつずれて瞬いて消える。肋骨の筋の隙間は少し遅れて消える (筋に沿って星の点が残る)
                    float o = Mth.clamp(0.5f + m / 0.12f, 0f, 1f);
                    float rib = Mth.cos(dd * 38f) > 0.55f ? 0.28f : 0f;
                    gone = Mth.clamp((o - jitter * 0.55f - rib) * 6f, 0f, 1f) * away;
                } else if (variant == StarEffectVariant.B) {
                    // 巨大な物体のまわりで光が曲がる: 星が影の縁から外へ押しのけられ、縁の近くでゆらぎ、通ったあとの空は一度暗く、少しずつ戻る
                    float x = dd / 0.5f;
                    float env = exp(-Math.max(0f, -m) * Math.max(0f, -m) / (2f * 0.30f * 0.30f));
                    float ripple = 0.022f * Mth.sin(x * 9f - time * 0.35f + ring * 5f) * exp(-x * x * 0.6f);
                    float shift = (0.17f * x * exp(-x * x * 1.4f) + ripple) * env;
                    float[] moved = direction(psi + shift, ring);
                    d[0] = moved[0];
                    d[1] = moved[1];
                    d[2] = moved[2];
                    float inside = Mth.clamp(0.5f + m / 0.10f, 0f, 1f);
                    float wake = dd > 0f ? exp(-dd * 2.2f) * smoothstep(m / 0.25f + 0.4f) : 0f;
                    gone = Mth.clamp(inside * 0.9f + wake * 0.55f, 0f, 1f) * away;
                } else {
                    // C: 影の中の星は、光の幕に洗われて半分ほど薄れる (消しはしない)
                    float o = Mth.clamp(0.5f + m / 0.16f, 0f, 1f);
                    gone = o * 0.55f * away;
                }
            }
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
