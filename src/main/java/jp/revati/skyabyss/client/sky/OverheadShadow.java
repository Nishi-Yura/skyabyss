package jp.revati.skyabyss.client.sky;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// 上から落ちる影 (空の影・巨神の影) の共通の描画。
// 地面に影の板を貼る方式は、屋根・木の葉の下 (地面の板が覆われる) と屋内が明るいまま残り、屋根の縁に継ぎ目が出た。
// ここでは影を「世界の x, z の濃さの地図」にして、画面の画素ごとに、その画素の世界の位置 (深度から戻す) で色に掛ける。
// 輪郭の中なら、屋根の上も下も屋内も人や動物も同じ濃さで暗くなり、縁は地図の補間でなだらかにつながる。
// 地図は 3 ブロックの升目 128 x 128 (カメラの周り 384 ブロック。升目はワールドに固定して、動いてもちらつかない)。
// 影を落とす物の高さも地図に持ち (G)、それより上の面 (巨神の背中の上など) は暗くしない。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class OverheadShadow {
    private static final int N = 128;
    private static final int CELL = 3;
    // 空の影の最大の濃さ。巨神の影はそれより薄い
    private static final float SKY_DARK = 0.9f;
    private static final float TITAN_DARK = 0.78f;
    // 影の色 (濃さ 1 のとき画面の色に掛ける)。冷たい青みへ沈む
    private static final float[] TINT = {0.28f, 0.30f, 0.44f};

    private record Titan(double x, double y, double z, float yaw, TitanVolume.Shadow shadow, float strength) {
    }

    // 開発用の切り替え (台本の overheadshadow on|off)。off = 昔の地面の板と屋内の弱めに戻す
    public static boolean enabled = true;
    private static ShaderInstance shader;
    private static DynamicTexture field;
    private static RenderTarget depthCopy;
    private static final List<Titan> TITANS = new ArrayList<>();
    private static final float[] DARK = new float[N * N];
    private static final float[] TOP = new float[N * N];

    private OverheadShadow() {
    }

    @SubscribeEvent
    static void onShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance made = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "overhead_shadow"), DefaultVertexFormat.POSITION);
            event.registerShader(made, loaded -> shader = loaded);
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("Overhead shadow shader failed to load; falling back to ground decals", e);
            shader = null;
        }
    }

    // 画素ごとの影が使えるか (使えなければ、従来の地面の板に戻る)
    public static boolean ready() {
        return enabled && shader != null;
    }

    // 巨神の影を今のフレームに加える (巨神の renderer から。体の局所の 0 の高さ y と向き yaw、体の真上から見た輪郭、濃さの係数 0〜1)
    public static void addTitan(double x, double y, double z, float yaw, TitanVolume.Shadow shadow, float strength) {
        if (ready() && strength > 0.001f) {
            TITANS.add(new Titan(x, y, z, yaw, shadow, strength));
        }
    }

    // 影を画面にかける。一つ目の呼び出しだけが描く。レベル描画の AFTER_PARTICLES (体・ブロック・水・粒子が描き終わったあと) から呼ぶ
    static void render(Matrix4f pose, Matrix4f projection, Vec3 cam, SkyPass sky, double skyAge) {
        long t = jp.revati.skyabyss.dev.Prof.begin();
        render0(pose, projection, cam, sky, skyAge);
        jp.revati.skyabyss.dev.Prof.end("client.overheadShadow", t);
    }

    private static void render0(Matrix4f pose, Matrix4f projection, Vec3 cam, SkyPass sky, double skyAge) {
        if (!ready()) {
            TITANS.clear();
            return;
        }
        if (sky == null && TITANS.isEmpty()) {
            return;
        }
        double ox = Math.floor((cam.x - N * CELL / 2.0) / CELL) * CELL;
        double oz = Math.floor((cam.z - N * CELL / 2.0) / CELL) * CELL;
        double base = Math.floor(cam.y) - 256.0;
        // 入力 (升目の原点・空の影の時刻・巨神の影) が前のフレームと同じなら、焼き直しと転送を省いて前の地図をそのまま使う
        Object testCircle = SkyPassRenderer.testCircle;
        boolean same = fieldValid && lastOx == ox && lastOz == oz && lastBase == base && lastSky == sky
                && (sky == null || lastSkyAge == skyAge) && lastTestCircle == testCircle && TITANS.equals(lastTitans);
        if (same) {
            TITANS.clear();
            if (!lastAny) {
                return;
            }
        } else {
        java.util.Arrays.fill(DARK, 0f);
        // 影のない升目は「高い所まで落ちる」(1) にしておく。補間で縁の升目の高さが下がり、影が欠けるのを防ぐ
        java.util.Arrays.fill(TOP, 1f);

        boolean any = false;
        if (sky != null) {
            any |= bakeSky(sky, skyAge, ox, oz);
        }
        for (Titan t : TITANS) {
            any |= bakeTitan(t, ox, oz, base);
        }
        lastTitans = new ArrayList<>(TITANS);
        TITANS.clear();
        lastOx = ox;
        lastOz = oz;
        lastBase = base;
        lastSky = sky;
        lastSkyAge = skyAge;
        lastTestCircle = testCircle;
        lastAny = any;
        fieldValid = !any || upload();
        if (!any) {
            return;
        }
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (depthCopy == null) {
            depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);

        Matrix4f inv = new Matrix4f(projection).mul(RenderSystem.getModelViewMatrix()).mul(pose).invert();
        shader.setSampler("Depth", depthCopy.getDepthTextureId());
        shader.setSampler("Field", field.getId());
        shader.getUniform("InvViewProj").set(inv);
        shader.getUniform("CamPos").set((float) cam.x, (float) cam.y, (float) cam.z, (float) base);
        shader.getUniform("FieldRect").set((float) ox, (float) oz, 1f / (N * CELL), 0f);
        shader.getUniform("Tint").set(TINT[0], TINT[1], TINT[2], 0f);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // 画面の色 × 影の色 (乗算)。色相を灰色に潰さず、明るい所ほど深く沈む
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.SRC_COLOR,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> shader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1f, -1f, 0f);
        b.addVertex(1f, -1f, 0f);
        b.addVertex(1f, 1f, 0f);
        b.addVertex(-1f, 1f, 0f);
        BufferUploader.drawWithShader(b.buildOrThrow());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static boolean bakeSky(SkyPass pass, double age, double ox, double oz) {
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        boolean any = false;
        for (int i = 0; i < N; i++) {
            double x = ox + (i + 0.5) * CELL;
            if (SkyPassRenderer.testCircle == null && Math.abs(x - cx) > SkyPass.REACH) {
                continue;
            }
            for (int k = 0; k < N; k++) {
                double z = oz + (k + 0.5) * CELL;
                if (SkyPassRenderer.testCircle == null && Math.abs(z - cz) > SkyPass.REACH) {
                    continue;
                }
                float s = SkyPassRenderer.shadowAt(pass, x, z, age) * SKY_DARK;
                if (s > 0.002f) {
                    DARK[k * N + i] = s;
                    any = true;
                }
            }
        }
        return any;
    }

    // 巨神の輪郭 (体の局所の升目の角ごとの濃さ) を、体の向きに合わせて地図へ。地図の升目の中心を局所へ戻して補間する
    private static boolean bakeTitan(Titan t, double ox, double oz, double base) {
        TitanVolume.Shadow sh = t.shadow();
        double fx = -Mth.sin(t.yaw() * Mth.DEG_TO_RAD);
        double fz = Mth.cos(t.yaw() * Mth.DEG_TO_RAD);
        double lx1 = sh.x0() + sh.ni() * sh.tile();
        double lz1 = sh.z0() + sh.nj() * sh.tile();
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int c = 0; c < 4; c++) {
            double lx = (c & 1) == 0 ? sh.x0() : lx1;
            double lz = (c & 2) == 0 ? sh.z0() : lz1;
            double wx = t.x() + fz * lx + fx * lz;
            double wz = t.z() + fz * lz - fx * lx;
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        int i0 = Math.max(0, (int) Math.floor((minX - ox) / CELL));
        int i1 = Math.min(N - 1, (int) Math.floor((maxX - ox) / CELL));
        int k0 = Math.max(0, (int) Math.floor((minZ - oz) / CELL));
        int k1 = Math.min(N - 1, (int) Math.floor((maxZ - oz) / CELL));
        float top = (float) Mth.clamp((t.y() + 4.0 - base) / 512.0, 0.0, 1.0);
        boolean any = false;
        for (int i = i0; i <= i1; i++) {
            for (int k = k0; k <= k1; k++) {
                double dx = ox + (i + 0.5) * CELL - t.x();
                double dz = oz + (k + 0.5) * CELL - t.z();
                double lx = fz * dx - fx * dz;
                double lz = fx * dx + fz * dz;
                float a = sample(sh, (lx - sh.x0()) / sh.tile(), (lz - sh.z0()) / sh.tile()) * TITAN_DARK * t.strength();
                if (a <= 0.002f) {
                    continue;
                }
                int idx = k * N + i;
                TOP[idx] = DARK[idx] <= 0.002f ? top : Math.max(TOP[idx], top);
                DARK[idx] = 1f - (1f - DARK[idx]) * (1f - a);
                any = true;
            }
        }
        return any;
    }

    // 角の濃さの双一次補間 (升目の座標 gi, gj。範囲の外は 0)
    private static float sample(TitanVolume.Shadow sh, double gi, double gj) {
        if (gi < 0 || gj < 0 || gi > sh.ni() || gj > sh.nj()) {
            return 0f;
        }
        int i = Math.min((int) gi, sh.ni() - 1);
        int j = Math.min((int) gj, sh.nj() - 1);
        float u = (float) (gi - i);
        float v = (float) (gj - j);
        float a = Mth.lerp(v, sh.alphaAt(i, j), sh.alphaAt(i, j + 1));
        float b = Mth.lerp(v, sh.alphaAt(i + 1, j), sh.alphaAt(i + 1, j + 1));
        return Mth.lerp(u, a, b);
    }

    private static boolean fieldValid;
    private static boolean lastAny;
    private static double lastOx;
    private static double lastOz;
    private static double lastBase;
    private static double lastSkyAge;
    private static SkyPass lastSky;
    private static Object lastTestCircle;
    private static List<Titan> lastTitans = List.of();

    private static boolean upload() {
        if (field == null) {
            field = new DynamicTexture(N, N, false);
            field.setFilter(true, false);
        }
        var pixels = field.getPixels();
        for (int k = 0; k < N; k++) {
            for (int i = 0; i < N; i++) {
                int idx = k * N + i;
                int r = Mth.clamp(Math.round(DARK[idx] * 255f), 0, 255);
                int g = Mth.clamp(Math.round(TOP[idx] * 255f), 0, 255);
                // ABGR
                pixels.setPixelRGBA(i, k, 0xFF000000 | g << 8 | r);
            }
        }
        field.upload();
        // upload は昔の最近傍の指定に戻してしまうので、そのあとで双一次に直す (升目の段が見えてしまう)
        field.setFilter(true, false);
        return true;
    }
}
