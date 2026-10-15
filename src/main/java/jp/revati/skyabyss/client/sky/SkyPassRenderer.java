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
import jp.revati.skyabyss.sky.SkyPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 通過の描画。
// 1. 地面: 地形に沿ってひと続きに落ちる影（段差の側面にも影を貼る）
// 2. 空: 雲の上を飛ぶ巨大な何かの暗い姿と、その下に広がる厚い雲の層。雲の切れ間からだけ姿が見える
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkyPassRenderer {
    private static final float SHADOW_ALPHA = 0.6f;
    private static final int GROUND_RADIUS = 112;
    // 姿と嵐の雲の層は、バニラの四角い雲より下に置く（四角い雲が姿に重なって穴のように見えるのを防ぐ）。
    // 見上げると、嵐の雲 → その切れ間の姿 → さらに上にバニラの雲、の順に重なる
    private static final float BODY_BELOW_CLOUDS = 10f;
    private static final float DECK_BELOW_CLOUDS = 26f;
    private static final int BODY_STEP = 4;
    private static final int DECK_STEP = 8;
    private static final int DECK_RADIUS = 320;

    private static int[] heights = new int[0];

    private SkyPassRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        SkyPass pass = SkyPassClient.current();
        ClientLevel level = Minecraft.getInstance().level;
        float storm = SkyPassClient.storm();
        if (pass == null || level == null || (!pass.isActive(level.getGameTime()) && storm < 0.01f)) {
            return;
        }
        double age = pass.age(level.getGameTime(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        RenderSystem.depthMask(false);
        drawGroundShadow(level, pass, age, cam, pose);

        float cloudHeight = level.effects().getCloudHeight();
        if (!Float.isNaN(cloudHeight) && storm > 0.01f) {
            float viewRange = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
            // 姿は奥行きを書き込み、その上にあるバニラの雲を隠す
            RenderSystem.depthMask(true);
            drawBody(pass, age, cam, pose, cloudHeight - BODY_BELOW_CLOUDS, storm, viewRange);
            RenderSystem.depthMask(false);
            drawCloudDeck(level, pass, age, cam, pose, cloudHeight - DECK_BELOW_CLOUDS, storm, viewRange);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawGroundShadow(ClientLevel level, SkyPass pass, double age, Vec3 cam, Matrix4f pose) {
        if (!pass.isActive(level.getGameTime())) {
            return;
        }
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        int minX = Mth.floor(Math.max(cx - SkyPass.REACH, cam.x - GROUND_RADIUS));
        int maxX = Mth.floor(Math.min(cx + SkyPass.REACH, cam.x + GROUND_RADIUS));
        int minZ = Mth.floor(Math.max(cz - SkyPass.REACH, cam.z - GROUND_RADIUS));
        int maxZ = Mth.floor(Math.min(cz + SkyPass.REACH, cam.z + GROUND_RADIUS));
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        // 周囲1マス分も含めて高さを読み、段差の側面を貼れるようにする
        int w = maxX - minX + 3;
        int d = maxZ - minZ + 3;
        if (heights.length < w * d) {
            heights = new int[w * d];
        }
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                int x = minX - 1 + i;
                int z = minZ - 1 + k;
                heights[i * d + k] = level.hasChunk(x >> 4, z >> 4)
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : Integer.MIN_VALUE;
            }
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int i = x - minX + 1;
                int k = z - minZ + 1;
                int h = heights[i * d + k];
                if (h == Integer.MIN_VALUE) {
                    continue;
                }
                float s = pass.shadow(x + 0.5, z + 0.5, age);
                if (s <= 0.01f) {
                    continue;
                }
                any = true;
                float a = s * SHADOW_ALPHA;
                float x0 = (float) (x - cam.x);
                float z0 = (float) (z - cam.z);
                float y = (float) (h + 0.02 - cam.y);
                shadowQuad(buffer, pose, x0, y, z0, x0, y, z0 + 1, x0 + 1, y, z0 + 1, x0 + 1, y, z0, a);
                // 隣が低ければ、その段差の壁にも影を落とす
                int west = heights[(i - 1) * d + k];
                int east = heights[(i + 1) * d + k];
                int north = heights[i * d + k - 1];
                int south = heights[i * d + k + 1];
                if (west != Integer.MIN_VALUE && west < h) {
                    float yb = (float) (west - cam.y);
                    shadowQuad(buffer, pose, x0 - 0.02f, y, z0, x0 - 0.02f, yb, z0, x0 - 0.02f, yb, z0 + 1, x0 - 0.02f, y, z0 + 1, a);
                }
                if (east != Integer.MIN_VALUE && east < h) {
                    float yb = (float) (east - cam.y);
                    shadowQuad(buffer, pose, x0 + 1.02f, y, z0, x0 + 1.02f, y, z0 + 1, x0 + 1.02f, yb, z0 + 1, x0 + 1.02f, yb, z0, a);
                }
                if (north != Integer.MIN_VALUE && north < h) {
                    float yb = (float) (north - cam.y);
                    shadowQuad(buffer, pose, x0, y, z0 - 0.02f, x0 + 1, y, z0 - 0.02f, x0 + 1, yb, z0 - 0.02f, x0, yb, z0 - 0.02f, a);
                }
                if (south != Integer.MIN_VALUE && south < h) {
                    float yb = (float) (south - cam.y);
                    shadowQuad(buffer, pose, x0, y, z0 + 1.02f, x0, yb, z0 + 1.02f, x0 + 1, yb, z0 + 1.02f, x0 + 1, y, z0 + 1.02f, a);
                }
            }
        }
        draw(buffer, any);
    }

    private static void shadowQuad(BufferBuilder b, Matrix4f m,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float x3, float y3, float z3, float x4, float y4, float z4, float a) {
        b.addVertex(m, x1, y1, z1).setColor(0.02f, 0.02f, 0.05f, a);
        b.addVertex(m, x2, y2, z2).setColor(0.02f, 0.02f, 0.05f, a);
        b.addVertex(m, x3, y3, z3).setColor(0.02f, 0.02f, 0.05f, a);
        b.addVertex(m, x4, y4, z4).setColor(0.02f, 0.02f, 0.05f, a);
    }

    // 雲の中の巨大な姿。下から見上げると、雲の切れ間に暗い影のかたまりが見える
    private static void drawBody(SkyPass pass, double age, Vec3 cam, Matrix4f pose, float height, float storm, float viewRange) {
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        int minX = Mth.floor((cx - SkyPass.REACH) / BODY_STEP) * BODY_STEP;
        int minZ = Mth.floor((cz - SkyPass.REACH) / BODY_STEP) * BODY_STEP;
        int cells = (int) (SkyPass.REACH * 2 / BODY_STEP) + 2;
        float y = (float) (height - cam.y);
        float visible = Math.min(1f, storm * 1.4f);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int i = 0; i < cells; i++) {
            for (int k = 0; k < cells; k++) {
                double x = minX + i * BODY_STEP;
                double z = minZ + k * BODY_STEP;
                float a00 = pass.shadow(x, z, age);
                float a10 = pass.shadow(x + BODY_STEP, z, age);
                float a11 = pass.shadow(x + BODY_STEP, z + BODY_STEP, age);
                float a01 = pass.shadow(x, z + BODY_STEP, age);
                if (a00 + a10 + a11 + a01 <= 0.01f) {
                    continue;
                }
                any = true;
                float fade = visible * distanceFade(x - cam.x, z - cam.z, viewRange);
                float x0 = (float) (x - cam.x);
                float z0 = (float) (z - cam.z);
                bodyVertex(buffer, pose, x0, y, z0, a00 * fade);
                bodyVertex(buffer, pose, x0, y, z0 + BODY_STEP, a01 * fade);
                bodyVertex(buffer, pose, x0 + BODY_STEP, y, z0 + BODY_STEP, a11 * fade);
                bodyVertex(buffer, pose, x0 + BODY_STEP, y, z0, a10 * fade);
            }
        }
        draw(buffer, any);
    }

    private static void bodyVertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float s) {
        b.addVertex(m, x, y, z).setColor(0.05f, 0.05f, 0.08f, Math.min(1f, s * 0.95f));
    }

    // 嵐の雲の層。ゆっくり流れ、切れ間からだけ上の姿が見える。呼び声の瞬間は内側から光る
    private static void drawCloudDeck(ClientLevel level, SkyPass pass, double age, Vec3 cam, Matrix4f pose, float height, float storm, float viewRange) {
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        int minX = Mth.floor((cam.x - DECK_RADIUS) / DECK_STEP) * DECK_STEP;
        int minZ = Mth.floor((cam.z - DECK_RADIUS) / DECK_STEP) * DECK_STEP;
        int cells = DECK_RADIUS * 2 / DECK_STEP;
        float y = (float) (height - cam.y);
        double drift = age * pass.speed() * 0.35;
        double driftX = pass.dirX() * drift;
        double driftZ = pass.dirZ() * drift;
        float glow = SkyPassClient.glow();
        float range = Math.min(viewRange, DECK_RADIUS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int i = 0; i < cells; i++) {
            for (int k = 0; k < cells; k++) {
                double x = minX + i * DECK_STEP;
                double z = minZ + k * DECK_STEP;
                float a00 = deckAlpha(x, z, cam, driftX, driftZ, storm, range);
                float a10 = deckAlpha(x + DECK_STEP, z, cam, driftX, driftZ, storm, range);
                float a11 = deckAlpha(x + DECK_STEP, z + DECK_STEP, cam, driftX, driftZ, storm, range);
                float a01 = deckAlpha(x, z + DECK_STEP, cam, driftX, driftZ, storm, range);
                if (a00 + a10 + a11 + a01 <= 0.01f) {
                    continue;
                }
                any = true;
                double near = Math.hypot(x - cx, z - cz);
                float lit = glow * (float) Mth.clamp(1.0 - near / 220.0, 0.0, 1.0);
                float r = 0.30f + lit * 0.45f;
                float g = 0.31f + lit * 0.40f;
                float bl = 0.36f + lit * 0.55f;
                float x0 = (float) (x - cam.x);
                float z0 = (float) (z - cam.z);
                buffer.addVertex(pose, x0, y, z0).setColor(r, g, bl, a00);
                buffer.addVertex(pose, x0, y, z0 + DECK_STEP).setColor(r, g, bl, a01);
                buffer.addVertex(pose, x0 + DECK_STEP, y, z0 + DECK_STEP).setColor(r, g, bl, a11);
                buffer.addVertex(pose, x0 + DECK_STEP, y, z0).setColor(r, g, bl, a10);
            }
        }
        draw(buffer, any);
    }

    private static float deckAlpha(double x, double z, Vec3 cam, double driftX, double driftZ, float storm, float range) {
        double n = noise((x - driftX) / 90.0, (z - driftZ) / 90.0) * 0.65 + noise((x - driftX) / 35.0 + 7.3, (z - driftZ) / 35.0 - 2.1) * 0.35;
        // 嵐が強いほど雲が厚くなり、切れ間が減る
        double threshold = 0.62 - storm * 0.3;
        double cover = Mth.clamp((n - threshold) / 0.18, 0.0, 1.0);
        return (float) (cover * 0.85 * storm) * distanceFade(x - cam.x, z - cam.z, range);
    }

    // 描画距離の端に向かって薄くし、空との境目を目立たなくする
    private static float distanceFade(double dx, double dz, float range) {
        double d = Math.hypot(dx, dz) / range;
        return (float) Mth.clamp((1.0 - d) / 0.4, 0.0, 1.0);
    }

    // なめらかな値ノイズ (0〜1)
    private static double noise(double x, double z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double ux = fx * fx * (3 - 2 * fx);
        double uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz);
        double b = hash(ix + 1, iz);
        double c = hash(ix, iz + 1);
        double d = hash(ix + 1, iz + 1);
        return Mth.lerp(uz, Mth.lerp(ux, a, b), Mth.lerp(ux, c, d));
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static void draw(BufferBuilder buffer, boolean any) {
        MeshData mesh = buffer.build();
        if (mesh != null && any) {
            BufferUploader.drawWithShader(mesh);
        } else if (mesh != null) {
            mesh.close();
        }
    }
}
