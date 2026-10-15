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

// 雲の目の描画 (シェーダーなしで成り立つ。テクスチャを貼った板だけ)。
// 雲の高さに、こちらを向いた板として置く (どこから見ても、こちらを見ている):
//   1. 雲のかたまり (夜の雲の色) — 目の周りに雲が集まり、目が閉じると引いていく
//   2. 白目 (象牙色) と 3. 瞳 (金の輪) — 上下の瞼の間 (アーモンド形の切れ間) にだけ見える
//   4. 上下の瞼 = 雲 — 開くと上下に割れ、閉じると合わさる。切れ間の縁は目の光でかすかに金色に照らされる
// 大きさは段階で幅44〜60ブロック。画面いっぱいにはならない (170ブロック先で視野の15〜20度)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CloudEyeRenderer {
    private static final ResourceLocation PATCH = tex("patch");
    private static final ResourceLocation SCLERA = tex("sclera");
    private static final ResourceLocation IRIS = tex("iris");
    private static final ResourceLocation LID = tex("lid");
    private static final ResourceLocation LID_GLOW = tex("lid_glow");
    // 目の縦の半分の高さ (幅に対する割合)、瞼の雲の厚さ (目の高さに対する割合)、瞼が左右に伸びる長さ (幅に対する割合)
    static final float HALF_HEIGHT = 0.21f;
    static final float LID_THICKNESS = 1.35f;
    static final float LID_REACH = 0.78f;
    // 瞳の半径 (目の縦の半分に対する割合)。瞼より少し大きく、上下が瞼に隠れる
    static final float IRIS_RADIUS = 1.08f;
    // 瞳の動く幅 (目の幅・縦の半分に対する割合)、縁の光の強さ (tools/rough/sky_eye.py と同じ値)
    static final float IRIS_TRAVEL_X = 0.2f;
    static final float IRIS_TRAVEL_Y = 0.55f;
    static final float GLOW_ALPHA = 0.35f;
    private static final int STRIPS = 48;

    private CloudEyeRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/environment/sky_eye/" + name + ".png");
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
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
        Vec3 cam = event.getCamera().getPosition();
        Vec3 rel = eye.pos.subtract(cam);
        float width = eye.width;
        // 遠くの描画の切れる距離 (描画距離の4倍) より手前に寄せる。見かけの大きさは変えない
        double far = mc.options.getEffectiveRenderDistance() * 16.0 * 4.0 * 0.8;
        double dist = rel.length();
        if (dist > far) {
            double k = far / dist;
            rel = rel.scale(k);
            width *= (float) k;
        }
        Vec3 d = rel.normalize();
        Vec3 right = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 up = right.cross(d);
        Matrix4f pose = event.getPoseStack().last().pose();

        float open = eye.open(partial);
        float glow = Mth.clamp(open * 1.15f, 0f, 1f) * CloudEyeClient.intensity();
        Vec3 cloudColor = level.getCloudColor(partial);
        float cr = (float) cloudColor.x;
        float cg = (float) cloudColor.y;
        float cb = (float) cloudColor.z;
        float halfH = width * HALF_HEIGHT;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // 1. 雲のかたまり
        RenderSystem.setShaderTexture(0, PATCH);
        BufferBuilder b = begin();
        quad(b, pose, rel, right, up, -width * 1.25f, width * 1.25f, -width * 0.62f, width * 0.62f, 0, 1, 0, 1,
                cr * 0.95f, cg * 0.95f, cb * 0.95f, patch * 0.8f);
        draw(b);

        if (open > 0.002f) {
            // 2. 白目: 切れ間の中だけ。目の光は弱めの金白 (夜空で眩しすぎない)
            float s = 0.32f + 0.5f * glow;
            RenderSystem.setShaderTexture(0, SCLERA);
            b = begin();
            clipped(b, pose, rel, right, up, width, halfH, open, -width / 2f, width / 2f, -halfH, halfH,
                    s, s * 0.97f, s * 0.9f, Math.min(1f, open * 4f));
            draw(b);

            // 3. 瞳: 地上を探し、やがてこちらを向く
            float r = halfH * IRIS_RADIUS;
            float ix = eye.irisX(partial) * width * IRIS_TRAVEL_X;
            float iy = eye.irisY(partial) * halfH * IRIS_TRAVEL_Y;
            float ib = 0.42f + 0.58f * glow;
            RenderSystem.setShaderTexture(0, IRIS);
            b = begin();
            clipped(b, pose, rel, right, up, width, halfH, open, ix - r, ix + r, iy - r, iy + r,
                    ib, ib, ib, Math.min(1f, open * 4f));
            draw(b);
        }

        // 4. 瞼の雲 (上下)。開いていなくても、合わさった雲として描く
        RenderSystem.setShaderTexture(0, LID);
        b = begin();
        lids(b, pose, rel, right, up, width, halfH, open, cr * 1.05f, cg * 1.05f, cb * 1.08f, patch * 0.95f);
        draw(b);

        // 切れ間の縁を、目の光が照らす (足し合わせ)
        if (open > 0.002f) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderTexture(0, LID_GLOW);
            b = begin();
            lidGlow(b, pose, rel, right, up, width, halfH, open, 1.0f, 0.78f, 0.42f, glow * GLOW_ALPHA);
            draw(b);
            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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

    // 区切った縦の帯ごとに、[x0,x1]×[y0,y1] の板を切れ間で切り取って貼る (テクスチャはその板全体に貼った位置のまま)
    private static void clipped(BufferBuilder b, Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up, float width, float halfH, float open,
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
            vertex(b, pose, rel, right, up, s0, lo0, tu0, (y1 - lo0) / (y1 - y0), r, g, bl, a);
            vertex(b, pose, rel, right, up, s1, lo1, tu1, (y1 - lo1) / (y1 - y0), r, g, bl, a);
            vertex(b, pose, rel, right, up, s1, hi1, tu1, (y1 - hi1) / (y1 - y0), r, g, bl, a);
            vertex(b, pose, rel, right, up, s0, hi0, tu0, (y1 - hi0) / (y1 - y0), r, g, bl, a);
        }
    }

    // 上下の瞼。切れ間の縁から外へ、厚さ LID_THICKNESS の雲の帯。テクスチャの V=1 が縁 (ほつれた側)
    private static void lids(BufferBuilder b, Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up, float width, float halfH, float open,
                             float r, float g, float bl, float a) {
        float reach = width * LID_REACH;
        float thick = halfH * LID_THICKNESS;
        float step = reach * 2f / STRIPS;
        for (int i = 0; i < STRIPS; i++) {
            float u0 = -reach + i * step;
            float u1 = u0 + step;
            float a0 = aperture(u0, width, halfH, open);
            float a1 = aperture(u1, width, halfH, open);
            float t0 = (u0 + reach) / (2f * reach);
            float t1 = (u1 + reach) / (2f * reach);
            // 上の瞼 (テクスチャの上半分)
            vertex(b, pose, rel, right, up, u0, a0, t0, 0.5f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, a1, t1, 0.5f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, a1 + thick, t1, 0f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u0, a0 + thick, t0, 0f, r, g, bl, a);
            // 下の瞼 (テクスチャの下半分。縁が上)
            vertex(b, pose, rel, right, up, u0, -a0 - thick, t0, 1f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, -a1 - thick, t1, 1f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, -a1, t1, 0.5f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u0, -a0, t0, 0.5f, r, g, bl, a);
        }
    }

    // 縁の光: 切れ間の縁から少しだけ外へ (目の高さの 0.45)
    private static void lidGlow(BufferBuilder b, Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up, float width, float halfH, float open,
                                float r, float g, float bl, float a) {
        float half = width / 2f;
        float thick = halfH * 0.45f;
        float step = width / STRIPS;
        for (int i = 0; i < STRIPS; i++) {
            float u0 = -half + i * step;
            float u1 = u0 + step;
            float a0 = aperture(u0, width, halfH, open);
            float a1 = aperture(u1, width, halfH, open);
            float t0 = (u0 + half) / width;
            float t1 = (u1 + half) / width;
            vertex(b, pose, rel, right, up, u0, a0 - thick * 0.25f, t0, 1f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, a1 - thick * 0.25f, t1, 1f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, a1 + thick, t1, 0f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u0, a0 + thick, t0, 0f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u0, -a0 - thick, t0, 0f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, -a1 - thick, t1, 0f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u1, -a1 + thick * 0.25f, t1, 1f, r, g, bl, a);
            vertex(b, pose, rel, right, up, u0, -a0 + thick * 0.25f, t0, 1f, r, g, bl, a);
        }
    }

    private static void quad(BufferBuilder b, Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up, float x0, float x1, float y0, float y1,
                             float tu0, float tu1, float tv0, float tv1, float r, float g, float bl, float a) {
        vertex(b, pose, rel, right, up, x0, y0, tu0, tv1, r, g, bl, a);
        vertex(b, pose, rel, right, up, x1, y0, tu1, tv1, r, g, bl, a);
        vertex(b, pose, rel, right, up, x1, y1, tu1, tv0, r, g, bl, a);
        vertex(b, pose, rel, right, up, x0, y1, tu0, tv0, r, g, bl, a);
    }

    private static void vertex(BufferBuilder b, Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up, float u, float v, float tu, float tv,
                               float r, float g, float bl, float a) {
        float x = (float) (rel.x + right.x * u + up.x * v);
        float y = (float) (rel.y + right.y * u + up.y * v);
        float z = (float) (rel.z + right.z * u + up.z * v);
        b.addVertex(pose, x, y, z).setUv(tu, tv).setColor(r, g, bl, Mth.clamp(a, 0f, 1f));
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
