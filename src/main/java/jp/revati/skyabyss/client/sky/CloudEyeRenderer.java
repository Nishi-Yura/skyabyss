package jp.revati.skyabyss.client.sky;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 雲の目の描画 v2 (シェーダーなしで成り立つ。テクスチャを貼った板だけ)。
// 目は天体と同じく「方向」に置く (プレイヤーが動いても空の同じ所にある = とても遠い)。
// 空のすぐあと (AFTER_SKY) に描くので、地形もバニラの雲もその手前に重なる (雲の層より奥にいる)。
// 重ねる順:
//   1. 顔 (目のまわりの皮膚と羽毛。閉じた瞼もここに描いてある)
//   2. 白目と瞳 — 瞼の間 (アーモンド形の切れ間) にだけ見える。3. 瞼の縁の影
//   4. 空気の霞 (青灰。遠くに見せ、明暗の差を弱める)
//   5. 雲の帯 (上下) — 目より小さな切れ間を開けて割れる。目の端・まわりの顔は雲に隠れ、切れ間からしか見えない
// 大きさは視野の角度で決める (段階で 40〜55 度。画面の横のおよそ 3〜4 割。ただし雲が大半を隠す)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CloudEyeRenderer {
    private static final ResourceLocation FACE = tex("face");
    private static final ResourceLocation SCLERA = tex("sclera");
    private static final ResourceLocation IRIS = tex("iris");
    private static final ResourceLocation CLOUD = tex("cloud");
    private static final ResourceLocation HAZE = tex("haze");
    // 目の縦の半分の高さ (幅に対する割合)。顔の板の半分の大きさ (tools/rough/sky_eye_v2.py と同じ)
    static final float HALF_HEIGHT = 0.21f;
    static final float FACE_X = 0.95f;
    static final float FACE_Y = 0.55f;
    // 瞳の半径 (目の縦の半分に対する割合)。瞼より少し大きく、上下が瞼に隠れる
    static final float IRIS_RADIUS = 1.08f;
    // 瞳の動く幅 (目の幅・縦の半分に対する割合)
    static final float IRIS_TRAVEL_X = 0.2f;
    static final float IRIS_TRAVEL_Y = 0.55f;
    // 雲のかたまりの広がり (目の幅に対する割合。横の半分、縦の半分)、外の縁のぼかし、濃さ (バニラの雲と同じ 0.8 前後)
    static final float CLOUD_X_FADE = 1.12f;
    static final float CLOUD_Y = 0.64f;
    static final float CLOUD_SOFT = 0.05f;
    static final float CLOUD_ALPHA = 0.8f;
    // 切れ間の長さの半分と、上下の高さ (目の幅に対する割合)。目の幅の半分 (0.5) より短く、目の端は雲に隠れる
    static final float GAP_HALF_LENGTH = 0.41f;
    static final float GAP_TOP = 0.31f;
    static final float GAP_BOTTOM = 0.25f;
    // 雲の帯の柱の数 (割れた縁を、バニラの雲のような段々にする)
    private static final int COLUMNS = 26;
    private static final float EDGE_STEP = 0.028f;
    private static final int STRIPS = 48;

    private CloudEyeRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/environment/sky_eye/" + name + ".png");
    }

    // 見比べ用の案 (開発用。-Dskyabyss.eyeVariant=a|b|c、ゲーム中は撮影用の /eyevariant)。
    // a: 雲の層より奥 (採用)、b: バニラの雲より手前、c: 奥でさらに大きく
    static char variant() {
        String v = System.getProperty("skyabyss.eyeVariant", "a");
        return v.isEmpty() ? 'a' : v.charAt(0);
    }

    // 目の幅 (視野の角度、度)。段階の幅 (44/52/60) から決める
    static float angularWidth(float stageWidth) {
        return stageWidth * (variant() == 'c' ? 1.15f : 0.92f);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        boolean behind = variant() != 'b';
        RenderLevelStageEvent.Stage want = behind ? RenderLevelStageEvent.Stage.AFTER_SKY : RenderLevelStageEvent.Stage.AFTER_WEATHER;
        if (event.getStage() != want) {
            return;
        }
        CloudEyeClient.Eye eye = CloudEyeClient.current();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (eye == null || level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float patch = eye.patch(partial);
        if (patch <= 0.001f) {
            return;
        }
        // 空の段階では、まだカメラの向きが掛かっていない (LevelRenderer は空のあとで modelView に掛ける)
        Matrix4f pose = behind ? new Matrix4f(event.getModelViewMatrix()) : event.getPoseStack().last().pose();

        // 方向に置く。距離は見かけの大きさに関係しないので、描画の切れる距離より十分手前にする
        double dist = Math.min(mc.options.getEffectiveRenderDistance() * 16.0 * 4.0 * 0.5, 600.0);
        Vec3 d = eye.dir;
        Vec3 rel = d.scale(dist);
        float width = (float) (2.0 * dist * Math.tan(Math.toRadians(eye.angle * 0.5)));
        Vec3 flatRight = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 flatUp = flatRight.cross(d);
        // 目は少し傾ける (向きは毎回変わる)
        double tilt = Math.toRadians(eye.tilt);
        Vec3 right = flatRight.scale(Math.cos(tilt)).add(flatUp.scale(Math.sin(tilt)));
        Vec3 up = flatUp.scale(Math.cos(tilt)).subtract(flatRight.scale(Math.sin(tilt)));
        Frame f = new Frame(pose, rel, right, up);

        float open = eye.open(partial);
        float glow = Mth.clamp(open * 1.15f, 0f, 1f) * CloudEyeClient.intensity();
        Vec3 cloudColor = level.getCloudColor(partial);
        float cr = (float) cloudColor.x;
        float cg = (float) cloudColor.y;
        float cb = (float) cloudColor.z;
        // 遠くの空気の色: 夜の雲の色を少し青へ。目の色はこれに寄せる (空気遠近)
        float hr = cr * 0.85f + 0.035f;
        float hg = cg * 0.88f + 0.045f;
        float hb = cb * 0.95f + 0.08f;
        float halfH = width * HALF_HEIGHT;
        // 切れ間の開き: 気配の間に雲が集まり、開く前半で割れきる
        float part = eye.part(partial);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (behind) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // 1. 顔 (切れ間が開くほど見える。雲の帯が外を隠す)
        float faceA = Mth.clamp(part * 1.4f, 0f, 1f) * patch;
        RenderSystem.setShaderTexture(0, FACE);
        BufferBuilder b = begin();
        quad(b, f, -width * FACE_X, width * FACE_X, -width * FACE_Y, width * FACE_Y, 0, 1, 0, 1,
                mix(0.62f, hr, 0.45f), mix(0.66f, hg, 0.45f), mix(0.78f, hb, 0.45f), faceA);
        draw(b);

        if (open > 0.002f) {
            // 2. 白目と瞳: 光は抑えめ。霞の色に寄せて明暗の差を弱める
            float s = 0.3f + 0.4f * glow;
            RenderSystem.setShaderTexture(0, SCLERA);
            b = begin();
            clipped(b, f, width, halfH, open, -width / 2f, width / 2f, -halfH, halfH,
                    mix(s, hr, 0.42f), mix(s * 0.97f, hg, 0.42f), mix(s * 0.92f, hb, 0.42f), Math.min(1f, open * 4f) * patch);
            draw(b);

            float r = halfH * IRIS_RADIUS;
            float ix = eye.irisX(partial) * width * IRIS_TRAVEL_X;
            float iy = eye.irisY(partial) * halfH * IRIS_TRAVEL_Y;
            float ib = 0.36f + 0.36f * glow;
            RenderSystem.setShaderTexture(0, IRIS);
            b = begin();
            clipped(b, f, width, halfH, open, ix - r, ix + r, iy - r, iy + r,
                    mix(ib, hr, 0.35f), mix(ib, hg, 0.35f), mix(ib * 0.98f, hb, 0.35f), Math.min(1f, open * 4f) * patch);
            draw(b);

            // 3. 瞼の縁の影 (上の瞼が白目に落とす影と、縁の暗い線)
            RenderSystem.setShaderTexture(0, HAZE);
            b = begin();
            lidShadow(b, f, width, halfH, open, 0.03f, 0.035f, 0.05f, 0.75f * patch);
            draw(b);
        }

        // 4. 空気の霞
        RenderSystem.setShaderTexture(0, HAZE);
        b = begin();
        quad(b, f, -width * 0.8f, width * 0.8f, -width * 0.42f, width * 0.42f, 0, 1, 0, 1, hr, hg, hb, 0.5f * faceA);
        draw(b);

        // 5. 雲の帯
        RenderSystem.setShaderTexture(0, CLOUD);
        b = begin();
        clouds(b, f, width, eye.seed, part, cr * 0.8f, cg * 0.8f, cb * 0.84f, patch);
        draw(b);
        // 割れた縁: 目の光と月明かりで、内側の縁だけ少し明るい
        b = begin();
        cloudRims(b, f, width, eye.seed, part, cr * 1.4f + 0.01f, cg * 1.42f + 0.012f, cb * 1.45f + 0.02f, patch * (0.3f + 0.25f * glow));
        draw(b);
        // 目の手前を横切る雲は、バニラの雲そのもの (目は空の段階で描くので、近くの雲が必ず手前を流れる)

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private record Frame(Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up) {
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // 切れ間 (瞼の間) の上下の幅。u は目の中心から横の位置 (-W/2〜W/2)
    static float aperture(float u, float width, float halfH, float open) {
        float t = 2f * u / width;
        float shape = 1f - t * t;
        if (shape <= 0f) {
            return 0f;
        }
        return open * halfH * (float) Math.pow(shape, 0.72);
    }

    // 雲の切れ間の中心 (目の幅に対する割合)。目の真ん中から少しずらし、傾ける (枠に収めない)
    private static float gapCenterX(int seed) {
        return (((seed >> 3 & 1) == 0) ? 1f : -1f) * (0.06f + (seed >> 4 & 3) * 0.012f);
    }

    private static float gapCenterY(int seed) {
        return 0.03f + ((seed >> 6 & 3) - 1.5f) * 0.01f;
    }

    private static float gapSlope(int seed) {
        return ((seed >> 8 & 7) - 3.5f) * 0.025f;
    }

    // 柱ごとのゆらぎ (0〜1)
    private static float hash(int i, int seed, int salt) {
        int h = i * 0x27d4eb2d ^ seed * 0x165667b1 ^ salt * 0x9e3779b9;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }

    // 柱 i の切れ間の上下 (目の幅に対する割合、切れ間の中心から)。柱ごとに割れる時がずれ、縁は段々になる
    private static float gapEdge(int i, float uc, int seed, float part, boolean top) {
        float t = (uc - gapCenterX(seed)) / GAP_HALF_LENGTH;
        float shape = 1f - t * t;
        if (shape <= 0f) {
            return 0f;
        }
        float delay = hash(i, seed, top ? 1 : 2) * 0.35f;
        float p = Mth.clamp((part - delay) / (1f - delay), 0f, 1f);
        p = p * p * (3f - 2f * p);
        // 大きなうねり (切れ間を楕円にしない) と、柱ごとの細かいでこぼこ
        float wave = Mth.sin(uc * 6.5f + (seed & 0xFF) * 0.7f + (top ? 0f : 2.1f));
        float jitter = 0.72f + 0.28f * hash(i, seed, top ? 3 : 4) + 0.2f * wave;
        float g = (top ? GAP_TOP : GAP_BOTTOM) * (float) Math.pow(shape, 0.55) * jitter * p;
        return Math.round(g / EDGE_STEP) * EDGE_STEP;
    }

    private static void clouds(BufferBuilder b, Frame f, float width, int seed, float part, float r, float g, float bl, float a) {
        float x0 = -CLOUD_X_FADE;
        float step = 2f * CLOUD_X_FADE / COLUMNS;
        float cy = gapCenterY(seed);
        float slope = gapSlope(seed);
        for (int i = 0; i < COLUMNS; i++) {
            float u0 = x0 + i * step;
            float u1 = u0 + step;
            float uc = (u0 + u1) * 0.5f;
            float ell = 1f - (uc / CLOUD_X_FADE) * (uc / CLOUD_X_FADE);
            if (ell <= 0f) {
                continue;
            }
            float ax = a * CLOUD_ALPHA;
            // 柱ごとに少し明るさを変える (雲のかたまりの凹凸)
            float lit = 0.93f + 0.12f * hash(i, seed, 11);
            float mid = cy + slope * uc;
            for (int s = 0; s < 2; s++) {
                boolean top = s == 0;
                float edge = gapEdge(i, uc, seed, part, top);
                float sign = top ? 1f : -1f;
                // 外の縁: 楕円に、柱ごとのでこぼこ (バニラの雲のかたまりのような段々)
                float bump = 0.5f * hash(i, seed, top ? 7 : 8) + 0.5f * hash(i + 1, seed, top ? 7 : 8);
                float outer = CLOUD_Y * (float) Math.sqrt(ell) * (0.72f + 0.5f * bump);
                outer = Math.round(outer / (EDGE_STEP * 2f)) * EDGE_STEP * 2f;
                if (outer <= edge + EDGE_STEP) {
                    continue;
                }
                float e0 = mid + sign * edge;
                float e1 = mid + sign * outer;
                float e2 = e1 + sign * CLOUD_SOFT;
                // テクスチャは縁と一緒に動く (割れた雲が押しのけられる)
                float tv0 = 0f;
                float tv1 = Math.abs(e1 - e0) / 0.3f;
                float tv2 = tv1 + Math.abs(e2 - e1) / 0.3f;
                float tu0 = u0 / 0.3f + (top ? 0f : 0.5f);
                float tu1 = u1 / 0.3f + (top ? 0f : 0.5f);
                vertexW(b, f, width, u0, e0, tu0, tv0, r * lit, g * lit, bl * lit, ax);
                vertexW(b, f, width, u1, e0, tu1, tv0, r * lit, g * lit, bl * lit, ax);
                vertexW(b, f, width, u1, e1, tu1, tv1, r * lit, g * lit, bl * lit, ax);
                vertexW(b, f, width, u0, e1, tu0, tv1, r * lit, g * lit, bl * lit, ax);
                vertexW(b, f, width, u0, e1, tu0, tv1, r * lit, g * lit, bl * lit, ax);
                vertexW(b, f, width, u1, e1, tu1, tv1, r * lit, g * lit, bl * lit, ax);
                vertexW(b, f, width, u1, e2, tu1, tv2, r * lit, g * lit, bl * lit, 0f);
                vertexW(b, f, width, u0, e2, tu0, tv2, r * lit, g * lit, bl * lit, 0f);
            }
        }
    }

    private static void cloudRims(BufferBuilder b, Frame f, float width, int seed, float part, float r, float g, float bl, float a) {
        float x0 = -CLOUD_X_FADE;
        float step = 2f * CLOUD_X_FADE / COLUMNS;
        float cy = gapCenterY(seed);
        float slope = gapSlope(seed);
        float thick = EDGE_STEP * 0.8f;
        for (int i = 0; i < COLUMNS; i++) {
            float u0 = x0 + i * step;
            float u1 = u0 + step;
            float uc = (u0 + u1) * 0.5f;
            float mid = cy + slope * uc;
            for (int s = 0; s < 2; s++) {
                boolean top = s == 0;
                float edge = gapEdge(i, uc, seed, part, top);
                if (edge <= 0f) {
                    continue;
                }
                float sign = top ? 1f : -1f;
                float e0 = mid + sign * edge;
                float e1 = e0 + sign * thick;
                float ar = a * (0.6f + 0.4f * hash(i, seed, top ? 5 : 6));
                vertexW(b, f, width, u0, e0, 0.25f, 0.5f, r, g, bl, ar);
                vertexW(b, f, width, u1, e0, 0.75f, 0.5f, r, g, bl, ar);
                vertexW(b, f, width, u1, e1, 0.75f, 0.5f, r, g, bl, ar * 0.2f);
                vertexW(b, f, width, u0, e1, 0.25f, 0.5f, r, g, bl, ar * 0.2f);
            }
        }
    }

    // 区切った縦の帯ごとに、[x0,x1]×[y0,y1] の板を切れ間で切り取って貼る (テクスチャはその板全体に貼った位置のまま)
    private static void clipped(BufferBuilder b, Frame f, float width, float halfH, float open,
                                float x0, float x1, float y0, float y1, float r, float g, float bl, float a) {
        float step = width / STRIPS;
        for (int i = 0; i < STRIPS; i++) {
            float u0 = -width / 2f + i * step;
            float u1 = u0 + step;
            float s0 = Math.max(u0, x0);
            float s1 = Math.min(u1, x1);
            if (s1 <= s0) {
                continue;
            }
            float a0 = aperture(s0, width, halfH, open);
            float a1 = aperture(s1, width, halfH, open);
            float lo0 = Math.max(-a0, y0);
            float hi0 = Math.min(a0, y1);
            float lo1 = Math.max(-a1, y0);
            float hi1 = Math.min(a1, y1);
            if (hi0 <= lo0 && hi1 <= lo1) {
                continue;
            }
            hi0 = Math.max(hi0, lo0);
            hi1 = Math.max(hi1, lo1);
            float tu0 = (s0 - x0) / (x1 - x0);
            float tu1 = (s1 - x0) / (x1 - x0);
            vertex(b, f, s0, lo0, tu0, (y1 - lo0) / (y1 - y0), r, g, bl, a);
            vertex(b, f, s1, lo1, tu1, (y1 - lo1) / (y1 - y0), r, g, bl, a);
            vertex(b, f, s1, hi1, tu1, (y1 - hi1) / (y1 - y0), r, g, bl, a);
            vertex(b, f, s0, hi0, tu0, (y1 - hi0) / (y1 - y0), r, g, bl, a);
        }
    }

    // 瞼の縁: 縁のすぐ外の暗い線と、上の瞼が白目に落とす影 (内側へ薄れる)
    private static void lidShadow(BufferBuilder b, Frame f, float width, float halfH, float open, float r, float g, float bl, float a) {
        float half = width / 2f;
        float step = width / STRIPS;
        float line = width * 0.012f;
        float shadow = halfH * 0.35f * open;
        for (int i = 0; i < STRIPS; i++) {
            float u0 = -half + i * step;
            float u1 = u0 + step;
            float a0 = aperture(u0, width, halfH, open);
            float a1 = aperture(u1, width, halfH, open);
            // 上の縁の線
            vertex(b, f, u0, a0, 0.5f, 0.5f, r, g, bl, a);
            vertex(b, f, u1, a1, 0.5f, 0.5f, r, g, bl, a);
            vertex(b, f, u1, a1 + line, 0.5f, 0.5f, r, g, bl, a);
            vertex(b, f, u0, a0 + line, 0.5f, 0.5f, r, g, bl, a);
            // 下の縁の線
            vertex(b, f, u0, -a0 - line, 0.5f, 0.5f, r, g, bl, a * 0.8f);
            vertex(b, f, u1, -a1 - line, 0.5f, 0.5f, r, g, bl, a * 0.8f);
            vertex(b, f, u1, -a1, 0.5f, 0.5f, r, g, bl, a * 0.8f);
            vertex(b, f, u0, -a0, 0.5f, 0.5f, r, g, bl, a * 0.8f);
            // 上の瞼の影 (白目の上)
            float d0 = Math.min(shadow, a0 * 2f);
            float d1 = Math.min(shadow, a1 * 2f);
            vertex(b, f, u0, a0 - d0, 0.5f, 0.5f, r, g, bl, 0f);
            vertex(b, f, u1, a1 - d1, 0.5f, 0.5f, r, g, bl, 0f);
            vertex(b, f, u1, a1, 0.5f, 0.5f, r, g, bl, a * 0.7f);
            vertex(b, f, u0, a0, 0.5f, 0.5f, r, g, bl, a * 0.7f);
        }
    }

    private static void quad(BufferBuilder b, Frame f, float x0, float x1, float y0, float y1,
                             float tu0, float tu1, float tv0, float tv1, float r, float g, float bl, float a) {
        vertex(b, f, x0, y0, tu0, tv1, r, g, bl, a);
        vertex(b, f, x1, y0, tu1, tv1, r, g, bl, a);
        vertex(b, f, x1, y1, tu1, tv0, r, g, bl, a);
        vertex(b, f, x0, y1, tu0, tv0, r, g, bl, a);
    }

    // 目の幅を単位にした座標で置く
    private static void vertexW(BufferBuilder b, Frame f, float width, float u, float v, float tu, float tv,
                                float r, float g, float bl, float a) {
        vertex(b, f, u * width, v * width, tu, tv, r, g, bl, a);
    }

    private static void vertex(BufferBuilder b, Frame f, float u, float v, float tu, float tv,
                               float r, float g, float bl, float a) {
        float x = (float) (f.rel.x + f.right.x * u + f.up.x * v);
        float y = (float) (f.rel.y + f.right.y * u + f.up.y * v);
        float z = (float) (f.rel.z + f.right.z * u + f.up.z * v);
        b.addVertex(f.pose, x, y, z).setUv(tu, tv).setColor(Mth.clamp(r, 0f, 1f), Mth.clamp(g, 0f, 1f), Mth.clamp(bl, 0f, 1f),
                Mth.clamp(a, 0f, 1f));
    }

    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
