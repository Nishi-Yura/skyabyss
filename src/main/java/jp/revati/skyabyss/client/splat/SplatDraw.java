package jp.revati.skyabyss.client.splat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// 点の模型を一体ぶん描く。GeckoLib が骨ごとにかけたポーズ (行列) を先に集めておき (capture)、最後にまとめて流す (flush)。
// 点はカメラを向いた四角。位置は骨二本の行列で動かした点を重みで混ぜる (継ぎ目で折れずに曲がる)。
// 不透明の点はふつうの実体の描き方 (明るさ・被弾の赤が効く)、光る点は明るさ一定、にじみと火の粉は足し算の柔らかい丸
public final class SplatDraw {
    private static final ResourceLocation SOLID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/splat/solid.png");
    private static final ResourceLocation SOFT = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/splat/soft.png");
    // この距離 (ブロック) までは全部の点、遠いほど間引いて大きくする (下限の割合は MIN_FRACTION)
    private static final float FULL_DETAIL = 20f;
    private static final float MIN_FRACTION = 0.18f;
    // 法線と視線の角の cos がこれより大きい (はっきり裏を向いた) 点は描かない。縁の点は残すので 0 より少し上
    private static final float CULL_COS2 = 0.2f * 0.2f;
    // 開発用: 直前の一体で裏向きとして飛ばした点の数 (計測の記録用)
    public static int culled;

    private final SplatCloud cloud;
    private final Matrix4f[] mats;
    private final boolean[] present;
    private final float[] boneScale;
    private int light;
    private int overlay;
    private int tint = -1;
    private boolean any;

    public SplatDraw(SplatCloud cloud) {
        this.cloud = cloud;
        int n = cloud.bones.length;
        mats = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            mats[i] = new Matrix4f();
        }
        present = new boolean[n];
        boneScale = new float[n];
    }

    public SplatCloud cloud() {
        return cloud;
    }

    public void begin() {
        java.util.Arrays.fill(present, false);
        any = false;
    }

    // 骨一本の今のポーズを覚える (renderCubesOfBone から)
    public void capture(String bone, Matrix4f pose, int light, int overlay, int tint) {
        int i = cloud.boneIndex(bone);
        if (i < 0) {
            return;
        }
        mats[i].set(pose);
        present[i] = true;
        boneScale[i] = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01() + pose.m02() * pose.m02());
        this.light = light;
        this.overlay = overlay;
        this.tint = tint;
        any = true;
    }

    public void flush(MultiBufferSource source, float partialTick) {
        if (!any) {
            return;
        }
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        culled = 0;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();
        // 体の中心までの距離 (描画の空間はカメラが原点)
        Matrix4f m0 = null;
        for (int i = 0; i < mats.length; i++) {
            if (present[i]) {
                m0 = mats[i];
                break;
            }
        }
        float dist = m0 == null ? 0f : new Vector3f(m0.m30(), m0.m31(), m0.m32()).length();
        float frac = dist <= FULL_DETAIL ? 1f : Math.max(MIN_FRACTION, (FULL_DETAIL / dist) * (FULL_DETAIL / dist));
        float grow = (float) Math.sqrt(1f / frac);

        if (SplatGpu.ready(cloud)) {
            // GPU: 点は VBO にあり、骨の行列だけ渡して全部を一回で描く (間引きは要らない)
            SplatGpu.draw(cloud, mats, present, light, overlay, tint, (float) Math.sqrt(CULL_COS2));
        } else {
            VertexConsumer solid = source.getBuffer(RenderType.entityCutoutNoCull(SOLID));
            drawPart(solid, cloud.solid, (int) (cloud.solid.n * frac), grow, left, up, false, 0f);
        }
        float time = mc.level == null ? 0f : (mc.level.getGameTime() + partialTick) / 20f;
        VertexConsumer add = source.getBuffer(RenderType.eyes(SOFT));
        drawPart(add, cloud.additive, cloud.additive.n, Math.min(grow, 1.6f), left, up, true, time);
        jp.revati.skyabyss.dev.Prof.acc("client.splat.flush", prof);
    }

    private void drawPart(VertexConsumer buf, SplatCloud.Part p, int count, float grow, Vector3f left, Vector3f up, boolean additive, float time) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        int tr = (tint >> 16) & 255;
        int tg = (tint >> 8) & 255;
        int tb = tint & 255;
        boolean tinted = (tint & 0xFFFFFF) != 0xFFFFFF;
        for (int i = 0; i < count; i++) {
            int ia = p.boneA[i];
            if (!present[ia]) {
                continue;
            }
            float px = p.x[i];
            float py = p.y[i];
            float pz = p.z[i];
            float size = p.size[i] * grow;
            int c = p.argb[i];
            int flags = p.flags[i];
            if ((flags & SplatCloud.F_EMBER) != 0) {
                // 火の粉: 点ごとにずらした周期で昇り、揺れながら消える
                float phase = ((i * 0.6180339f) % 1f);
                float period = 2.4f + phase * 1.8f;
                float u = ((time / period) + phase) % 1f;
                py += u * 14f / 16f;
                px += (float) Math.sin((time + phase * 10f) * 2.1f) * 0.6f / 16f * u;
                float fade = (u < 0.12f ? u / 0.12f : 1f) * (1f - u);
                size *= 1f - u * 0.5f;
                c = scale(c, fade);
            }
            Matrix4f ma = mats[ia];
            ma.transformPosition(px, py, pz, a);
            // 裏を向いた点は描かない (表の点に隠れる)。カメラは原点なので、点の位置がそのまま視線の向き
            int qx = p.nx[i], qy = p.ny[i], qz = p.nz[i];
            if ((qx | qy | qz) != 0) {
                float wx = ma.m00() * qx + ma.m10() * qy + ma.m20() * qz;
                float wy = ma.m01() * qx + ma.m11() * qy + ma.m21() * qz;
                float wz = ma.m02() * qx + ma.m12() * qy + ma.m22() * qz;
                float dot = wx * a.x + wy * a.y + wz * a.z;
                if (dot > 0f && dot * dot > CULL_COS2 * (wx * wx + wy * wy + wz * wz) * (a.x * a.x + a.y * a.y + a.z * a.z)) {
                    culled++;
                    continue;
                }
            }
            float wa = p.weightA[i];
            int ib = p.boneB[i];
            if (wa < 0.999f && ib != ia && present[ib]) {
                mats[ib].transformPosition(px, py, pz, b);
                a.lerp(b, 1f - wa);
            }
            float h = size * 0.5f * boneScale[ia];
            int r = (c >> 16) & 255;
            int g = (c >> 8) & 255;
            int bl = c & 255;
            if (tinted && !additive) {
                r = r * tr / 255;
                g = g * tg / 255;
                bl = bl * tb / 255;
            }
            int col = 0xFF000000 | (r << 16) | (g << 8) | bl;
            int lt = additive || (flags & SplatCloud.F_GLOW) != 0 ? LightTexture.FULL_BRIGHT : light;
            float lx = left.x * h, ly = left.y * h, lz = left.z * h;
            float ux = up.x * h, uy = up.y * h, uz = up.z * h;
            buf.addVertex(a.x + lx + ux, a.y + ly + uy, a.z + lz + uz, col, 0f, 0f, overlay, lt, 0f, 1f, 0f);
            buf.addVertex(a.x + lx - ux, a.y + ly - uy, a.z + lz - uz, col, 0f, 1f, overlay, lt, 0f, 1f, 0f);
            buf.addVertex(a.x - lx - ux, a.y - ly - uy, a.z - lz - uz, col, 1f, 1f, overlay, lt, 0f, 1f, 0f);
            buf.addVertex(a.x - lx + ux, a.y - ly + uy, a.z - lz + uz, col, 1f, 0f, overlay, lt, 0f, 1f, 0f);
        }
    }

    private static int scale(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * f);
        int b = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
