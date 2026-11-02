package jp.revati.skyabyss.story.ch6.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

// 月の空 (docs/design/46 の 3.1)。真っ黒な空に、細かい星と、北東の高い所に浮かぶ地上の大陸 (textures/environment/moon_earth.png)。
// 雲なし。霧は遠くだけ (月の砂が地平で白く溶ける)。バニラの太陽と月は描かない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MoonClient {
    public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moon");
    static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/environment/moon_earth.png");
    static final int STARS = 1800;
    private static float[] stars;

    private MoonClient() {
    }

    @SubscribeEvent
    static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS, new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
                return new Vec3(0.012, 0.014, 0.024);   // 空気がないので、空は黒い (遠景も闇へ)
            }

            @Override
            public boolean isFoggyAt(int x, int y) {
                return false;
            }

            @Override
            public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera, Matrix4f projection,
                                     boolean isFoggy, Runnable setupFog) {
                drawSky(modelView, ticks + partialTick);
                return true;
            }

            @Override
            public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack pose, double camX, double camY, double camZ,
                                        Matrix4f modelView, Matrix4f projection) {
                return true;
            }
        });
    }

    static void drawSky(Matrix4f modelView, float time) {
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        net.minecraft.client.renderer.FogRenderer.setupNoFog();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 星 (ゆっくり瞬く)
        if (stars == null) {
            stars = makeStars();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < STARS; i++) {
            float x = stars[i * 5], y = stars[i * 5 + 1], z = stars[i * 5 + 2], s = stars[i * 5 + 3], ph = stars[i * 5 + 4];
            float a = 0.55f + 0.45f * (float) Math.sin(time * 0.02f + ph);
            // 星の向きに垂直な二つの軸
            float ux = -z, uz = x;
            float ul = (float) Math.sqrt(ux * ux + uz * uz) + 1e-4f;
            ux /= ul;
            uz /= ul;
            float vx = y * uz, vy = z * ux - x * uz, vz = -y * ux;
            float cx = x * 100, cy = y * 100, cz = z * 100;
            int c = (int) (a * 255);
            b.addVertex(m, cx - ux * s - vx * s, cy - vy * s, cz - uz * s - vz * s).setColor(c, c, 255, c);
            b.addVertex(m, cx + ux * s - vx * s, cy - vy * s, cz + uz * s - vz * s).setColor(c, c, 255, c);
            b.addVertex(m, cx + ux * s + vx * s, cy + vy * s, cz + uz * s + vz * s).setColor(c, c, 255, c);
            b.addVertex(m, cx - ux * s + vx * s, cy + vy * s, cz - uz * s + vz * s).setColor(c, c, 255, c);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        // 地上の大陸: 着く所から北を向くと、左上の高い空 (北北西、仰角 34 度)。ゆっくり回る
        double el = Math.toRadians(34), az = Math.toRadians(-22);
        float dx = (float) (Math.cos(el) * Math.sin(az)), dy = (float) Math.sin(el), dz = (float) (-Math.cos(el) * Math.cos(az));
        // 向きに垂直な二つの軸 (u は水平、v は u と向きに垂直)。少しずつ回す
        float ux = -dz, uz = dx;
        float ul = (float) Math.sqrt(ux * ux + uz * uz);
        ux /= ul;
        uz /= ul;
        float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
        double rot = time * 0.00005;
        float c = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float ax = ux * c + vx * sn, ay = vy * sn, az2 = uz * c + vz * sn;
        float bx = -ux * sn + vx * c, by = vy * c, bz = -uz * sn + vz * c;
        float r = 30f;
        float cx = dx * 100, cy = dy * 100, cz = dz * 100;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, EARTH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferBuilder t = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        t.addVertex(m, cx - ax * r - bx * r, cy - ay * r - by * r, cz - az2 * r - bz * r).setUv(0, 1);
        t.addVertex(m, cx + ax * r - bx * r, cy + ay * r - by * r, cz + az2 * r - bz * r).setUv(1, 1);
        t.addVertex(m, cx + ax * r + bx * r, cy + ay * r + by * r, cz + az2 * r + bz * r).setUv(1, 0);
        t.addVertex(m, cx - ax * r + bx * r, cy - ay * r + by * r, cz - az2 * r + bz * r).setUv(0, 0);
        BufferUploader.drawWithShader(t.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    static float[] makeStars() {
        RandomSource r = RandomSource.create(0x5354415253L);
        float[] out = new float[STARS * 5];
        for (int i = 0; i < STARS; i++) {
            double x, y, z, l;
            do {
                x = r.nextDouble() * 2 - 1;
                y = r.nextDouble() * 2 - 1;
                z = r.nextDouble() * 2 - 1;
                l = x * x + y * y + z * z;
            } while (l > 1 || l < 0.01 || y < -0.15);
            l = Math.sqrt(l);
            out[i * 5] = (float) (x / l);
            out[i * 5 + 1] = (float) (y / l);
            out[i * 5 + 2] = (float) (z / l);
            out[i * 5 + 3] = 0.12f + r.nextFloat() * r.nextFloat() * 0.35f;
            out[i * 5 + 4] = r.nextFloat() * 6.28f;
        }
        return out;
    }
}
