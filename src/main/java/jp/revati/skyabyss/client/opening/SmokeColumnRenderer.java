package jp.revati.skyabyss.client.opening;

import java.util.Arrays;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 宿りの煙の柱 (docs/design/17 の 3.2節)。地形や木に隠れて「煙の立つ方」が見えない問題への答え。
// サーバーが SiteNetwork で送る煙の位置 ("smoke") へ、クライアントだけで高さ約 100 ブロックの柱を描く (その場所のチャンクが読まれていなくてもよい)。
//   根もとは濃く細く、昇るほど太く薄くなって、風に流れてたなびく。木のてっぺんや丘より高いので、葉の上に出る。
//   深度は見る (手前の山や地面には隠れる) が、書き込まない。山に完全に隠れても、上の部分は稜線の上に出る。
//   遠くは薄れるが、200 ブロック先でも読める濃さを残す。根もとから 20 ブロック以内は、バニラの焚き火の煙に任せて消す (柱が目の前を塞がない)。
// バニラの焚き火の煙は、そのまま出る (これは別に重ねる)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SmokeColumnRenderer {
    public static final float HEIGHT = 100f;
    // 1 つの煙のかたまりが根もとから頂まで昇る時間 (秒)
    private static final float LIFE = 70f;
    private static final int PUFFS = 56;
    private static final ResourceLocation PUFF_TEX = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dynamic/smoke_puff");
    private static boolean texReady;
    // 開発用: 環境変数 SKYABYSS_NO_SMOKE_COLUMN があれば描かない (柱の有り無しの比べ撮り用)
    private static final boolean DISABLED = System.getenv("SKYABYSS_NO_SMOKE_COLUMN") != null;

    private SmokeColumnRenderer() {
    }

    // 柱の根もと (煙の焚き火。無ければ宿り)
    static BlockPos source() {
        BlockPos p = SiteTargets.site("smoke");
        return p != null ? p : SiteTargets.site("shelter");
    }

    // 風の向き (世界に一つ。根もとの座標から決める。毎回同じ向きにたなびく)
    static float windYaw(BlockPos src) {
        return (float) (((src.getX() * 31L + src.getZ() * 17L) & 1023) / 1024.0 * Math.PI * 2);
    }

    // 煙のかたまり i の、時刻 t (秒) での 0〜1 の年齢
    static float age(int i, double t) {
        double a = (t / LIFE + (double) i / PUFFS) % 1.0;
        return (float) (a < 0 ? a + 1 : a);
    }

    private static void ensureTexture() {
        if (texReady) {
            return;
        }
        texReady = true;
        int n = 64;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, n, n, false);
        java.util.Random r = new java.util.Random(7);
        // 数個の小さな丸をずらして重ねた、ふわふわの輪郭
        float[][] lobes = new float[7][3];
        for (int k = 0; k < lobes.length; k++) {
            lobes[k][0] = (r.nextFloat() - 0.5f) * 0.34f;
            lobes[k][1] = (r.nextFloat() - 0.5f) * 0.34f;
            lobes[k][2] = 0.30f + r.nextFloat() * 0.14f;
        }
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = (x + 0.5f) / n - 0.5f;
                float v = (y + 0.5f) / n - 0.5f;
                float a = 0f;
                for (float[] l : lobes) {
                    float d = Mth.sqrt((u - l[0]) * (u - l[0]) + (v - l[1]) * (v - l[1])) / l[2];
                    float c = Mth.clamp(1f - d, 0f, 1f);
                    a = Math.max(a, c * c * (3f - 2f * c));
                }
                int alpha = Mth.clamp((int) (a * 255f), 0, 255);
                img.setPixelRGBA(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        tex.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(PUFF_TEX, tex);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockPos src = source();
        if (level == null || src == null || mc.player == null || level.dimension() != Level.OVERWORLD || DISABLED) {
            return;
        }
        var cam = event.getCamera();
        double cx = cam.getPosition().x;
        double cy = cam.getPosition().y;
        double cz = cam.getPosition().z;
        double sx = src.getX() + 0.5;
        double sz = src.getZ() + 0.5;
        double baseY = src.getY() + 1.0;
        double horizontal = Math.hypot(sx - cx, sz - cz);
        if (horizontal > 1500) {
            return;
        }
        ensureTexture();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = (level.getGameTime() + partial) / 20.0;
        float wind = windYaw(src);
        double wx = Math.cos(wind);
        double wz = Math.sin(wind);

        // 空の明るさに合わせた色。昼は灰茶、日暮れと夜は霧の色へ寄る (夜は根もとに火の色の明かりを足す)
        float[] fog = RenderSystem.getShaderFogColor();
        float day = dayLight(level, partial);
        float baseR = Mth.lerp(day, 0.22f, 0.42f);
        float baseG = Mth.lerp(day, 0.21f, 0.40f);
        float baseB = Mth.lerp(day, 0.22f, 0.39f);

        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();
        double[] px = new double[PUFFS];
        double[] py = new double[PUFFS];
        double[] pz = new double[PUFFS];
        float[] ps = new float[PUFFS];
        float[] pa = new float[PUFFS];
        float[] ph = new float[PUFFS];
        Integer[] order = new Integer[PUFFS];
        for (int i = 0; i < PUFFS; i++) {
            float a = age(i, t);
            float h = a * HEIGHT;
            // 風でたなびく: 昇るほど横へ流れる。ゆるい蛇行
            float drift = a * a * 34f + a * 6f;
            float sway = Mth.sin((float) (t * 0.35 + i * 1.7) + a * 5f) * (1.5f + a * 5f);
            px[i] = sx + wx * drift - wz * sway;
            pz[i] = sz + wz * drift + wx * sway;
            py[i] = baseY + h;
            float dx = (float) (px[i] - cx);
            float dy = (float) (py[i] - cy);
            float dz = (float) (pz[i] - cz);
            float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            // 大きさ: 根もとは細く、昇るほど広がる。遠くでも細い線にならないよう距離でも膨らませる
            ps[i] = (1.6f + a * 11f) * (1f + dist / 160f);
            // 濃さ: 根もとは濃く、昇ると薄く、頂で消える。遠くは薄れるが読める濃さを残す
            float fade = Mth.clamp((1f - a) / 0.14f, 0f, 1f) * Mth.clamp(a / 0.04f, 0f, 1f);
            float dense = Mth.lerp(a, 0.62f, 0.30f);
            float far = Mth.lerp(Mth.clamp((dist - 150f) / 600f, 0f, 1f), 1f, 0.55f);
            // 目の前では薄く (柱が視界を塞がない)。根もとの近く (20 ブロック) はバニラの煙に任せる
            float near = Mth.clamp((dist - 10f) / 30f, 0f, 1f);
            float nearBase = a < 0.3f ? Mth.clamp((float) (horizontal - 20.0) / 28f, 0f, 1f) : 1f;
            pa[i] = dense * fade * far * near * nearBase;
            ph[i] = a;
            order[i] = i;
        }
        final double fcx = cx;
        final double fcy = cy;
        final double fcz = cz;
        Arrays.sort(order, (p, q) -> Double.compare(d2(px[q], py[q], pz[q], fcx, fcy, fcz), d2(px[p], py[p], pz[p], fcx, fcy, fcz)));

        Matrix4f pose = event.getPoseStack().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int k = 0; k < PUFFS; k++) {
            int i = order[k];
            if (pa[i] <= 0.004f) {
                continue;
            }
            double dist = Math.sqrt(d2(px[i], py[i], pz[i], cx, cy, cz));
            float mix = Mth.clamp((float) (dist - 120.0) / 700f, 0f, 0.6f);
            float r = Mth.lerp(mix, baseR, fog[0]);
            float g = Mth.lerp(mix, baseG, fog[1]);
            float b = Mth.lerp(mix, baseB, fog[2]);
            // 根もとの火の色 (下 14 ブロックだけ、ほんのり温かく)
            float warm = Mth.clamp(1f - ph[i] * HEIGHT / 14f, 0f, 1f) * (1f - day * 0.6f);
            r = Mth.lerp(warm, r, 0.95f);
            g = Mth.lerp(warm, g, 0.55f);
            b = Mth.lerp(warm, b, 0.28f);
            quad(buf, pose, px[i] - cx, py[i] - cy, pz[i] - cz, left, up, ps[i], i * 2.399f + ph[i] * 0.5f, r, g, b, pa[i]);
        }
        MeshData mesh = buf.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PUFF_TEX);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // 0 (夜) 〜 1 (昼)
    private static float dayLight(ClientLevel level, float partial) {
        float angle = level.getTimeOfDay(partial) * (float) (Math.PI * 2);
        float sun = -Mth.cos(angle);
        return Mth.clamp(sun * 1.6f + 0.35f, 0f, 1f);
    }

    private static double d2(double x, double y, double z, double cx, double cy, double cz) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    // 回した、カメラに向く板
    private static void quad(BufferBuilder buf, Matrix4f pose, double x, double y, double z, Vector3f left, Vector3f up,
                             float size, float rot, float r, float g, float b, float a) {
        float c = Mth.cos(rot) * size;
        float s = Mth.sin(rot) * size;
        float ax = left.x * c - up.x * s;
        float ay = left.y * c - up.y * s;
        float az = left.z * c - up.z * s;
        float bx = left.x * s + up.x * c;
        float by = left.y * s + up.y * c;
        float bz = left.z * s + up.z * c;
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;
        buf.addVertex(pose, fx - ax - bx, fy - ay - by, fz - az - bz).setUv(0f, 0f).setColor(r, g, b, a);
        buf.addVertex(pose, fx + ax - bx, fy + ay - by, fz + az - bz).setUv(1f, 0f).setColor(r, g, b, a);
        buf.addVertex(pose, fx + ax + bx, fy + ay + by, fz + az + bz).setUv(1f, 1f).setColor(r, g, b, a);
        buf.addVertex(pose, fx - ax + bx, fy - ay + by, fz - az + bz).setUv(0f, 1f).setColor(r, g, b, a);
    }
}
