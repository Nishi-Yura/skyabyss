package jp.revati.skyabyss.client.splat;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

// 点の模型の GPU の描き方。不透明の点は模型ごとに一度だけ GPU のバッファに入れ (点一つにつき 36 バイト)、
// 点一つを一つのインスタンスとして、四角の 4 頂点をシェーダー (shaders/core/splat.*) が作る。毎フレームは骨の行列 (最大 16 本) を渡すだけ。
// 骨が 16 本を超える模型や、骨一本ずつ描く呼び出し (防具・品・NPC の持ち物) は、骨ごとに ForceBone で描く (継ぎ目の混ぜは無し)。
// 遠いほど骨ごとの範囲の先頭だけを描いて間引く。シェーダーが読めないときは CPU の描き方 (SplatDraw) に戻る。台本の `splat cpu|gpu`
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SplatGpu {
    public static final int MAX_BONES = 16;
    public static volatile boolean enabled = true;
    private static final int STRIDE = 36;
    // この距離 (ブロック) までは全部の点、遠いほど間引く (下限の割合)
    private static final float FULL_DETAIL = 24f;
    private static final float MIN_FRACTION = 0.15f;
    public static final float CULL_COS = 0.2f;

    private static ShaderInstance shader;
    private static final Map<SplatCloud, Gpu> BUFFERS = new IdentityHashMap<>();
    private static final Matrix4f ZERO = new Matrix4f().zero();

    private record Gpu(int vao, int vbo, int count) {
    }

    private SplatGpu() {
    }

    @SubscribeEvent
    static void onShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance made = new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "splat"),
                    DefaultVertexFormat.NEW_ENTITY);
            event.registerShader(made, loaded -> shader = loaded);
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("[splat] GPU shader failed to load; splats fall back to the CPU path", e);
            shader = null;
        }
    }

    public static boolean ready() {
        return enabled && shader != null;
    }

    static void clear() {
        RenderSystem.assertOnRenderThread();
        for (Gpu g : BUFFERS.values()) {
            free(g);
        }
        BUFFERS.clear();
    }

    static void release(SplatCloud cloud) {
        RenderSystem.assertOnRenderThread();
        Gpu g = BUFFERS.remove(cloud);
        if (g != null) {
            free(g);
        }
    }

    private static void free(Gpu g) {
        GL30.glDeleteVertexArrays(g.vao());
        GL15.glDeleteBuffers(g.vbo());
    }

    private static Gpu buffer(SplatCloud cloud) {
        Gpu g = BUFFERS.get(cloud);
        if (g != null) {
            return g;
        }
        SplatCloud.Part p = cloud.solid;
        ByteBuffer buf = MemoryUtil.memAlloc(Math.max(1, p.n) * STRIDE);
        for (int i = 0; i < p.n; i++) {
            buf.putFloat(p.x[i]).putFloat(p.y[i]).putFloat(p.z[i]);
            int c = p.argb[i];
            buf.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 255);
            buf.putFloat(p.size[i] * 0.5f).putFloat(0f);
            buf.putShort((short) (p.boneA[i] & 255)).putShort((short) (p.boneB[i] & 255));
            buf.putShort((short) Math.round(p.weightA[i] * 255f)).putShort((short) (p.flags[i] & 255));
            buf.put(p.nx[i]).put(p.ny[i]).put(p.nz[i]).put((byte) 0);
        }
        buf.flip();
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);
        for (int a = 0; a < 6; a++) {
            GL20.glEnableVertexAttribArray(a);
            GL33.glVertexAttribDivisor(a, 1);
        }
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        g = new Gpu(vao, vbo, p.n);
        BUFFERS.put(cloud, g);
        return g;
    }

    // 範囲 [start, start+count) を描く (属性の先頭をずらしてインスタンスで)
    private static void drawRange(int start, int count) {
        if (count <= 0) {
            return;
        }
        long o = (long) start * STRIDE;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, o);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, o + 12);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, STRIDE, o + 16);
        GL30.glVertexAttribIPointer(3, 2, GL11.GL_SHORT, STRIDE, o + 24);
        GL30.glVertexAttribIPointer(4, 2, GL11.GL_SHORT, STRIDE, o + 28);
        GL20.glVertexAttribPointer(5, 3, GL11.GL_BYTE, true, STRIDE, o + 32);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, count);
    }

    // 間引きの割合 (行列の原点のカメラからの距離で。GUI の正射影では間引かない)
    static float fraction(Matrix4f pose) {
        if (RenderSystem.getProjectionMatrix().m33() > 0.5f) {
            return 1f;
        }
        Vector3f t = RenderSystem.getModelViewMatrix().transformPosition(pose.m30(), pose.m31(), pose.m32(), new Vector3f());
        float d = t.length();
        return d <= FULL_DETAIL ? 1f : Math.max(MIN_FRACTION, (FULL_DETAIL / d) * (FULL_DETAIL / d));
    }

    // 一体ぶん: 骨ごとのポーズ (present の骨だけ) で描く。骨が 16 本までなら継ぎ目を混ぜて一度に、超えれば骨ごとに
    static void drawSkinned(SplatCloud cloud, Matrix4f[] mats, boolean[] present, int light, int overlay, int tint, float glow) {
        Matrix4f first = null;
        for (int i = 0; i < mats.length; i++) {
            if (present[i]) {
                first = mats[i];
                break;
            }
        }
        if (first == null) {
            return;
        }
        float frac = fraction(first);
        Gpu g = begin(cloud, light, overlay, tint);
        setFloat("Grow", (float) Math.sqrt(1f / frac));
        setFloat("GlowFade", glow);
        int nb = cloud.bones.length;
        if (nb <= MAX_BONES) {
            for (int i = 0; i < MAX_BONES; i++) {
                setMat("Bone" + i, i < nb && present[i] ? mats[i] : ZERO);
            }
            setInt("ForceBone", -1);
            shader.apply();
            for (int b = 0; b < nb; b++) {
                if (present[b]) {
                    drawRange(cloud.boneStart[b], (int) Math.ceil(cloud.boneCount[b] * frac));
                }
            }
        } else {
            setInt("ForceBone", 0);
            for (int b = 0; b < nb; b++) {
                if (present[b] && cloud.boneCount[b] > 0) {
                    setMat("Bone0", mats[b]);
                    shader.apply();
                    drawRange(cloud.boneStart[b], (int) Math.ceil(cloud.boneCount[b] * frac));
                }
            }
        }
        end(g);
    }

    // 骨一本 (bone < 0 なら全部の骨) を同じポーズで描く (防具・品・NPC の持ち物など、骨ごとにポーズを付けて呼ぶ側)
    static void drawRigid(SplatCloud cloud, int bone, Matrix4f pose, int light, int overlay, int tint) {
        float frac = fraction(pose);
        Gpu g = begin(cloud, light, overlay, tint);
        setFloat("Grow", (float) Math.sqrt(1f / frac));
        setFloat("GlowFade", 1f);
        setInt("ForceBone", 0);
        setMat("Bone0", pose);
        shader.apply();
        if (bone >= 0) {
            drawRange(cloud.boneStart[bone], (int) Math.ceil(cloud.boneCount[bone] * frac));
        } else {
            for (int b = 0; b < cloud.bones.length; b++) {
                drawRange(cloud.boneStart[b], (int) Math.ceil(cloud.boneCount[b] * frac));
            }
        }
        end(g);
    }

    private static Gpu begin(SplatCloud cloud, int light, int overlay, int tint) {
        Gpu g = buffer(cloud);
        ShaderInstance sh = shader;
        Uniform l = sh.getUniform("LightUV");
        if (l != null) {
            l.set((float) (light & 0xFFFF), (float) ((light >>> 16) & 0xFFFF));
        }
        Uniform o = sh.getUniform("OverlayUV");
        if (o != null) {
            o.set((float) (overlay & 0xFFFF), (float) ((overlay >>> 16) & 0xFFFF));
        }
        Uniform t = sh.getUniform("Tint");
        if (t != null) {
            t.set(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
        }
        Uniform cc = sh.getUniform("CullCos");
        if (cc != null) {
            cc.set(CULL_COS);
        }
        Minecraft mc = Minecraft.getInstance();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), mc.getWindow());
        GL30.glBindVertexArray(g.vao());
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, g.vbo());
        return g;
    }

    private static void end(Gpu g) {
        shader.clear();
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        BufferUploader.invalidate();
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.overlayTexture().teardownOverlayColor();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.enableCull();
    }

    private static void setMat(String name, Matrix4f m) {
        Uniform u = shader.getUniform(name);
        if (u != null) {
            u.set(m);
        }
    }

    private static void setFloat(String name, float v) {
        Uniform u = shader.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }

    private static void setInt(String name, int v) {
        Uniform u = shader.getUniform(name);
        if (u != null) {
            u.set(v);
        }
    }
}
