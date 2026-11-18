package jp.revati.skyabyss.client.fx;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// 光の表現の共通の描き方 (2026-11-15 の光の表現の選定)。足し合わせ (加算) の光を、追加のライブラリや後処理なしで描く。
//   光の帯   : カメラを向く帯。横断面は芯が白く、外へ柔らかく薄れる (BAND の絵)
//   光の点   : 柔らかくにじむ丸 (SOFT の絵)。大きさの違う 2〜3 枚を重ねて、発光して見せる
//   地の輪   : 床に寝た輪の帯
//   裂け目の中: 星・雲・波の明かりの絵を、何枚も違う速さで重ねて奥行きを出す (RiftFx)
// 絵はゲームの中で一度だけ作る (ファイルは持たない)。どれも白で、色は頂点の色で付ける。
public final class LightDraw {
    private LightDraw() {
    }

    public static final ResourceLocation SOFT = tex("soft");
    public static final ResourceLocation BAND = tex("band");
    public static final ResourceLocation STARS = tex("stars");
    public static final ResourceLocation CLOUD = tex("cloud");
    public static final ResourceLocation CAUSTIC = tex("caustic");
    public static final ResourceLocation STREAK = tex("streak");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dynamic/lightfx_" + name);
    }

    private static boolean made;

    // 絵を作って登録する (描画の糸でだけ呼ぶ)
    public static void ensure() {
        if (made) {
            return;
        }
        made = true;
        register(SOFT, soft(64));
        register(BAND, band(8, 64));
        register(STARS, stars(256, 0x5EED5));
        register(CLOUD, cloud(128, 0xC10D));
        register(CAUSTIC, caustic(128));
        register(STREAK, streak(64, 8));
    }

    private static void register(ResourceLocation id, NativeImage img) {
        DynamicTexture t = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(id, t);
        t.setFilter(true, false);
        t.bind();
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    // NativeImage は ABGR
    private static void put(NativeImage img, int x, int y, float a) {
        int ia = Mth.clamp((int) (a * 255f + 0.5f), 0, 255);
        img.setPixelRGBA(x, y, (ia << 24) | 0xFFFFFF);
    }

    private static NativeImage soft(int n) {
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x + 0.5f) / n * 2f - 1f;
                float dy = (y + 0.5f) / n * 2f - 1f;
                float r2 = dx * dx + dy * dy;
                // 芯は濃く、裾は長く (ガウスを 2 つ)
                float a = (float) (Math.exp(-r2 * 9.0) * 0.75 + Math.exp(-r2 * 3.2) * 0.35);
                a *= Mth.clamp((1f - r2) * 4f, 0f, 1f);
                put(img, x, y, a);
            }
        }
        return img;
    }

    // 横 (u) は同じ、縦 (v) が帯の横断面: 細い芯 + 広い光
    private static NativeImage band(int w, int h) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h - 0.5f;
            float core = (float) Math.exp(-(v * v) / (2 * 0.045 * 0.045));
            float glow = (float) Math.exp(-(v * v) / (2 * 0.17 * 0.17)) * 0.42f;
            float edge = Mth.clamp((0.5f - Math.abs(v)) * 12f, 0f, 1f);
            for (int x = 0; x < w; x++) {
                put(img, x, y, Math.min(1f, core + glow) * edge);
            }
        }
        return img;
    }

    // 頭が明るく尾が薄れる筋 (u = 長さ、v = 太さ)
    private static NativeImage streak(int w, int h) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h - 0.5f;
            float across = (float) Math.exp(-(v * v) / (2 * 0.16 * 0.16));
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w;
                float along = (float) Math.pow(u, 1.6) * Mth.clamp((1f - u) * 10f, 0f, 1f);
                put(img, x, y, across * along);
            }
        }
        return img;
    }

    // 継ぎ目のない星空: 大小の点。いくつかに十字の光
    private static NativeImage stars(int n, long seed) {
        float[] a = new float[n * n];
        java.util.Random r = new java.util.Random(seed);
        int count = n * n / 300;
        for (int i = 0; i < count; i++) {
            float cx = r.nextFloat() * n;
            float cy = r.nextFloat() * n;
            float big = r.nextFloat();
            float rad = 0.55f + big * big * big * 2.4f;
            float bright = 0.35f + r.nextFloat() * 0.65f;
            boolean cross = big > 0.93f;
            int reach = (int) Math.ceil(rad * (cross ? 4 : 2)) + 1;
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int x = Math.floorMod((int) cx + dx, n);
                    int y = Math.floorMod((int) cy + dy, n);
                    float fx = (int) cx + dx + 0.5f - cx;
                    float fy = (int) cy + dy + 0.5f - cy;
                    float d2 = fx * fx + fy * fy;
                    float v = (float) Math.exp(-d2 / (rad * rad)) * bright;
                    if (cross) {
                        v += (float) (Math.exp(-(fy * fy) / 0.35) * Math.exp(-Math.abs(fx) / (rad * 1.6))
                                + Math.exp(-(fx * fx) / 0.35) * Math.exp(-Math.abs(fy) / (rad * 1.6))) * bright * 0.5f;
                    }
                    a[y * n + x] = Math.max(a[y * n + x], Math.min(1f, v));
                }
            }
        }
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                put(img, x, y, a[y * n + x]);
            }
        }
        return img;
    }

    // 継ぎ目のない雲 (周期のある格子の値ノイズを 4 段)
    private static NativeImage cloud(int n, long seed) {
        java.util.Random r = new java.util.Random(seed);
        float[][] grids = new float[4][];
        int[] cells = {4, 8, 16, 32};
        for (int o = 0; o < 4; o++) {
            grids[o] = new float[cells[o] * cells[o]];
            for (int i = 0; i < grids[o].length; i++) {
                grids[o][i] = r.nextFloat();
            }
        }
        NativeImage img = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float v = 0;
                float amp = 0.55f;
                for (int o = 0; o < 4; o++) {
                    int c = cells[o];
                    float gx = (float) x / n * c;
                    float gy = (float) y / n * c;
                    int x0 = (int) gx;
                    int y0 = (int) gy;
                    float tx = smooth(gx - x0);
                    float ty = smooth(gy - y0);
                    float[] g = grids[o];
                    float a00 = g[(y0 % c) * c + (x0 % c)];
                    float a10 = g[(y0 % c) * c + ((x0 + 1) % c)];
                    float a01 = g[((y0 + 1) % c) * c + (x0 % c)];
                    float a11 = g[((y0 + 1) % c) * c + ((x0 + 1) % c)];
                    v += amp * Mth.lerp(ty, Mth.lerp(tx, a00, a10), Mth.lerp(tx, a01, a11));
                    amp *= 0.5f;
                }
                float k = Mth.clamp((v - 0.35f) / 0.55f, 0f, 1f);
                put(img, x, y, k * k);
            }
        }
        return img;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    // 継ぎ目のない水の明かり (揺れる網目)
    private static NativeImage caustic(int n) {
        NativeImage img = new NativeImage(n, n, false);
        double tau = Math.PI * 2;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double u = (double) x / n * tau;
                double v = (double) y / n * tau;
                double a = Math.sin(u * 3 + Math.sin(v * 2) * 1.6);
                double b = Math.sin(v * 3 + Math.sin(u * 2 + 1.3) * 1.6);
                double c = Math.sin((u + v) * 2 + Math.sin(u - v) * 1.2);
                double w = Math.max(0, 1 - Math.abs(a) * 3.2) + Math.max(0, 1 - Math.abs(b) * 3.2) + Math.max(0, 1 - Math.abs(c) * 4.0) * 0.6;
                put(img, x, y, (float) Math.min(1.0, w * 0.75));
            }
        }
        return img;
    }

    // ---------------------------------------------------------------- 描く

    // 足し合わせの描き始め。終わりは end
    public static BufferBuilder begin() {
        ensure();
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    // 描いて状態を戻す。depth: 奥行きを見るか (見ると物の陰に隠れる)
    public static void end(BufferBuilder buf, ResourceLocation texture, boolean depth) {
        MeshData mesh = buf.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (depth) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // 通常の重ね (暗い面を置く: 裂け目の奥の闇)
    public static void endAlpha(BufferBuilder buf, ResourceLocation texture, boolean depth) {
        MeshData mesh = buf.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (depth) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void v(BufferBuilder b, Matrix4f m, double x, double y, double z, float u, float v, int rgb, float a) {
        b.addVertex(m, (float) x, (float) y, (float) z).setUv(u, v)
                .setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, Mth.clamp(a, 0f, 1f));
    }

    // カメラを向く帯 (点の列。座標はカメラからの相対)。width は全幅。u は長さに沿って 0..uLen
    public static void ribbon(BufferBuilder b, Matrix4f m, Vec3[] pts, float width, int rgb, float alpha) {
        ribbon(b, m, pts, width, rgb, alpha, null);
    }

    // alphas: 点ごとの濃さの掛け目 (null = みな 1)
    public static void ribbon(BufferBuilder b, Matrix4f m, Vec3[] pts, float width, int rgb, float alpha, float[] alphas) {
        if (pts.length < 2) {
            return;
        }
        Vec3[] side = new Vec3[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Vec3 dir = pts[Math.min(pts.length - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 toCam = pts[i].scale(-1);
            Vec3 s = dir.cross(toCam);
            double len = s.length();
            side[i] = len < 1e-6 ? new Vec3(0, width * 0.5, 0) : s.scale(width * 0.5 / len);
        }
        for (int i = 0; i < pts.length - 1; i++) {
            float a0 = alpha * (alphas == null ? 1f : alphas[i]);
            float a1 = alpha * (alphas == null ? 1f : alphas[i + 1]);
            float u0 = (float) i / (pts.length - 1);
            float u1 = (float) (i + 1) / (pts.length - 1);
            Vec3 p0 = pts[i];
            Vec3 p1 = pts[i + 1];
            v(b, m, p0.x - side[i].x, p0.y - side[i].y, p0.z - side[i].z, u0, 0f, rgb, a0);
            v(b, m, p0.x + side[i].x, p0.y + side[i].y, p0.z + side[i].z, u0, 1f, rgb, a0);
            v(b, m, p1.x + side[i + 1].x, p1.y + side[i + 1].y, p1.z + side[i + 1].z, u1, 1f, rgb, a1);
            v(b, m, p1.x - side[i + 1].x, p1.y - side[i + 1].y, p1.z - side[i + 1].z, u1, 0f, rgb, a1);
        }
    }

    // カメラを向く四角 (SOFT・STREAK を貼る)。c はカメラからの相対
    public static void sprite(BufferBuilder b, Matrix4f m, Camera cam, Vec3 c, float size, int rgb, float alpha) {
        sprite(b, m, cam, c, size, size, 0f, rgb, alpha);
    }

    public static void sprite(BufferBuilder b, Matrix4f m, Camera cam, Vec3 c, float w, float h, float roll, int rgb, float alpha) {
        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();
        float cs = Mth.cos(roll);
        float sn = Mth.sin(roll);
        double rx = (left.x() * cs + up.x() * sn) * w * 0.5;
        double ry = (left.y() * cs + up.y() * sn) * w * 0.5;
        double rz = (left.z() * cs + up.z() * sn) * w * 0.5;
        double ux = (up.x() * cs - left.x() * sn) * h * 0.5;
        double uy = (up.y() * cs - left.y() * sn) * h * 0.5;
        double uz = (up.z() * cs - left.z() * sn) * h * 0.5;
        v(b, m, c.x - rx - ux, c.y - ry - uy, c.z - rz - uz, 0f, 0f, rgb, alpha);
        v(b, m, c.x + rx - ux, c.y + ry - uy, c.z + rz - uz, 1f, 0f, rgb, alpha);
        v(b, m, c.x + rx + ux, c.y + ry + uy, c.z + rz + uz, 1f, 1f, rgb, alpha);
        v(b, m, c.x - rx + ux, c.y - ry + uy, c.z - rz + uz, 0f, 1f, rgb, alpha);
    }

    // 床に寝た輪 (中心 c はカメラからの相対、半径 r、帯の幅 w)。BAND を貼る
    public static void groundRing(BufferBuilder b, Matrix4f m, Vec3 c, double r, double w, int rgb, float alpha) {
        int n = Mth.clamp((int) (r * 6), 24, 128);
        double r0 = Math.max(0, r - w * 0.5);
        double r1 = r + w * 0.5;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n;
            double a1 = Math.PI * 2 * (i + 1) / n;
            float u0 = (float) i / n * 4f;
            float u1 = (float) (i + 1) / n * 4f;
            v(b, m, c.x + Math.cos(a0) * r0, c.y, c.z + Math.sin(a0) * r0, u0, 0f, rgb, alpha);
            v(b, m, c.x + Math.cos(a0) * r1, c.y, c.z + Math.sin(a0) * r1, u0, 1f, rgb, alpha);
            v(b, m, c.x + Math.cos(a1) * r1, c.y, c.z + Math.sin(a1) * r1, u1, 1f, rgb, alpha);
            v(b, m, c.x + Math.cos(a1) * r0, c.y, c.z + Math.sin(a1) * r0, u1, 0f, rgb, alpha);
        }
    }

    // 傾いた輪 (軌道)。中心 c、半径 r、輪の面の二つの軸 ax・ay (単位)。カメラを向く帯として描く
    public static void orbit(BufferBuilder b, Matrix4f m, Vec3 c, double r, Vec3 ax, Vec3 ay, float width, int rgb, float alpha, float arcFrom, float arcTo) {
        int n = 64;
        Vec3[] pts = new Vec3[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = arcFrom + (arcTo - arcFrom) * i / (double) n;
            pts[i] = c.add(ax.scale(Math.cos(t) * r)).add(ay.scale(Math.sin(t) * r));
            // 弧の両端はなめらかに消える (閉じた輪なら端なし)
            float k = (float) i / n;
            al[i] = arcTo - arcFrom >= Math.PI * 2 - 1e-3 ? 1f : Mth.clamp(Math.min(k, 1 - k) * 6f, 0f, 1f);
        }
        ribbon(b, m, pts, width, rgb, alpha, al);
    }

    // 色を明るく (白へ寄せる)
    public static int whiten(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        r = (int) (r + (255 - r) * k);
        g = (int) (g + (255 - g) * k);
        bl = (int) (bl + (255 - bl) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
