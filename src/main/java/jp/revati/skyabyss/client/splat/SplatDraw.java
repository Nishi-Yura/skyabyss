package jp.revati.skyabyss.client.splat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// 点の模型を描く入口。
// 体のモブ (LowPolyGeoRenderer): GeckoLib が骨ごとにかけたポーズを先に集め (capture)、最後にまとめて描く (flush)。継ぎ目は骨二本で混ぜる。
// それ以外 (防具・品・NPC の持ち物・攻撃の形など): drawRigid で、呼び出しのポーズのまま骨一本 (または全部) を描く。
// ふつうは GPU (SplatGpu)。シェーダーが無いときは CPU でカメラを向いた四角を流す (遠いほど間引いて大きくする)。
// にじみと火の粉 (足し算の点。手彫りの模型だけ) は CPU で描く
public final class SplatDraw {
    private static final ResourceLocation SOLID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/splat/solid.png");
    private static final ResourceLocation SOFT = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/splat/soft.png");
    private static final float CULL_COS2 = SplatGpu.CULL_COS * SplatGpu.CULL_COS;

    private final SplatCloud cloud;
    private final Matrix4f[] mats;
    private final boolean[] present;
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
        this.light = light;
        this.overlay = overlay;
        this.tint = tint;
        any = true;
    }

    public void flush(MultiBufferSource source, float partialTick) {
        flush(source, partialTick, 1f);
    }

    // glow: 光る点の明るさ (1 = いつも最大。眠る巨神は 0 へ消えていく)
    public void flush(MultiBufferSource source, float partialTick, float glow) {
        if (!any) {
            return;
        }
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        Matrix4f first = null;
        for (int i = 0; i < mats.length; i++) {
            if (present[i]) {
                first = mats[i];
                break;
            }
        }
        float frac = SplatGpu.fraction(first);
        // GUI (持ち物の画面の人など) は、画面の絵と同じ流れ (バッファ) に乗せる。その場で描くと、あとから描かれる絵に上書きされる
        if (SplatGpu.ready() && !gui()) {
            SplatGpu.drawSkinned(cloud, mats, present, light, overlay, tint, glow);
        } else {
            cpu(source.getBuffer(RenderType.entityCutoutNoCull(SOLID)), cloud.solid, 0, cloud.solid.n, frac, mats, present, null, light, overlay, tint, false, 0f);
        }
        if (cloud.additive.n > 0) {
            Minecraft mc = Minecraft.getInstance();
            float time = mc.level == null ? 0f : (mc.level.getGameTime() + partialTick) / 20f;
            cpu(source.getBuffer(RenderType.eyes(SOFT)), cloud.additive, 0, cloud.additive.n, 1f, mats, present, null, light, overlay, tint, true, time);
        }
        jp.revati.skyabyss.dev.Prof.acc("client.splat.flush", prof);
    }

    // 低ポリのメッシュの代わりに点の模型を描く (メッシュを描いていた所から呼ぶ)。tex: 塗る絵 (null = メッシュの絵)、bone: 骨の名前 (null = 全部)。
    // 模型がまだ出来ていなければ何も描かない (裏で作っている)
    public static void mesh(jp.revati.skyabyss.client.lowpoly.LowPolyMesh mesh, ResourceLocation tex, String bone, Matrix4f pose, MultiBufferSource source,
                            int light, int overlay, int tint) {
        SplatCloud c = SplatClouds.get(mesh, tex);
        if (c == null) {
            return;
        }
        int b = bone == null ? -1 : c.boneIndex(bone);
        if (bone != null && b < 0) {
            return;
        }
        drawRigid(c, b, pose, source, light, overlay, tint);
    }

    // 呼び出しのポーズのまま、骨一本 (bone < 0 なら全部) を描く。source は CPU に戻るときだけ使う
    public static void drawRigid(SplatCloud cloud, int bone, Matrix4f pose, MultiBufferSource source, int light, int overlay, int tint) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        if (SplatGpu.ready() && (source == null || !gui())) {
            SplatGpu.drawRigid(cloud, bone, pose, light, overlay, tint);
        } else if (source != null) {
            float frac = SplatGpu.fraction(pose);
            VertexConsumer buf = source.getBuffer(RenderType.entityCutoutNoCull(SOLID));
            if (bone >= 0) {
                cpu(buf, cloud.solid, cloud.boneStart[bone], cloud.boneCount[bone], frac, null, null, pose, light, overlay, tint, false, 0f);
            } else {
                for (int b = 0; b < cloud.bones.length; b++) {
                    cpu(buf, cloud.solid, cloud.boneStart[b], cloud.boneCount[b], frac, null, null, pose, light, overlay, tint, false, 0f);
                }
            }
        }
        jp.revati.skyabyss.dev.Prof.acc("client.splat.rigid", prof);
    }

    // 正射影 = GUI の中で描いている
    static boolean gui() {
        return RenderSystem.getProjectionMatrix().m33() > 0.5f;
    }

    // CPU の描き方。rigid が null でなければ全部の点をその行列で (骨の混ぜは無し)、null なら骨ごとの mats で混ぜる
    private static void cpu(VertexConsumer buf, SplatCloud.Part p, int start, int n, float frac, Matrix4f[] mats, boolean[] present, Matrix4f rigid,
                            int light, int overlay, int tint, boolean additive, float time) {
        // 四角を広げる向き = カメラの右と上 (ModelViewMat の行)。正射影 (GUI) では裏向きの間引きをしない
        Matrix4f mv = RenderSystem.getModelViewMatrix();
        Vector3f right = new Vector3f(mv.m00(), mv.m10(), mv.m20()).normalize();
        Vector3f up = new Vector3f(mv.m01(), mv.m11(), mv.m21()).normalize();
        boolean ortho = RenderSystem.getProjectionMatrix().m33() > 0.5f;
        float grow = (float) Math.sqrt(1f / frac);
        int count = (int) Math.ceil(n * frac);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        int tr = (tint >> 16) & 255;
        int tg = (tint >> 8) & 255;
        int tb = tint & 255;
        boolean tinted = (tint & 0xFFFFFF) != 0xFFFFFF;
        for (int j = 0; j < count; j++) {
            int i = start + j;
            int ia = p.boneA[i] & 255;
            Matrix4f ma = rigid != null ? rigid : (present[ia] ? mats[ia] : null);
            if (ma == null) {
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
            ma.transformPosition(px, py, pz, a);
            int qx = p.nx[i], qy = p.ny[i], qz = p.nz[i];
            if (!ortho && (qx | qy | qz) != 0) {
                float wx = ma.m00() * qx + ma.m10() * qy + ma.m20() * qz;
                float wy = ma.m01() * qx + ma.m11() * qy + ma.m21() * qz;
                float wz = ma.m02() * qx + ma.m12() * qy + ma.m22() * qz;
                float dot = wx * a.x + wy * a.y + wz * a.z;
                if (dot > 0f && dot * dot > CULL_COS2 * (wx * wx + wy * wy + wz * wz) * (a.x * a.x + a.y * a.y + a.z * a.z)) {
                    continue;
                }
            }
            if (rigid == null) {
                float wa = p.weightA[i];
                int ib = p.boneB[i] & 255;
                if (wa < 0.999f && ib != ia && present[ib]) {
                    mats[ib].transformPosition(px, py, pz, b);
                    a.lerp(b, 1f - wa);
                }
            }
            float sc = (float) Math.sqrt(ma.m00() * ma.m00() + ma.m01() * ma.m01() + ma.m02() * ma.m02());
            float h = size * 0.5f * sc;
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
            float lx = right.x * h, ly = right.y * h, lz = right.z * h;
            float ux = up.x * h, uy = up.y * h, uz = up.z * h;
            buf.addVertex(a.x - lx + ux, a.y - ly + uy, a.z - lz + uz, col, 0f, 0f, overlay, lt, 0f, 1f, 0f);
            buf.addVertex(a.x - lx - ux, a.y - ly - uy, a.z - lz - uz, col, 0f, 1f, overlay, lt, 0f, 1f, 0f);
            buf.addVertex(a.x + lx - ux, a.y + ly - uy, a.z + lz - uz, col, 1f, 1f, overlay, lt, 0f, 1f, 0f);
            buf.addVertex(a.x + lx + ux, a.y + ly + uy, a.z + lz + uz, col, 1f, 0f, overlay, lt, 0f, 1f, 0f);
        }
    }

    private static int scale(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * f);
        int b = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
