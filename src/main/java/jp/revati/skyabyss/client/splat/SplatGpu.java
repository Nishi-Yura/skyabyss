package jp.revati.skyabyss.client.splat;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
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

// 点の模型の GPU の描き方。不透明の点は模型ごとに一度だけ VBO に入れ (SplatCloud ごと)、毎フレームは骨の行列 (最大 16 本) とカメラの向きを
// シェーダー (shaders/core/splat.*) へ渡して一回で描く。CPU は点の数に関わらずほぼ何もしない。
// シェーダーが読めない・骨が 16 本を超える模型は、今までの CPU の描き方 (SplatDraw) に戻る。台本の `splat cpu|gpu` で切り替えて比べられる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SplatGpu {
    public static final int MAX_BONES = 16;
    public static volatile boolean enabled = true;

    private static ShaderInstance shader;
    private static final Map<SplatCloud, VertexBuffer> BUFFERS = new IdentityHashMap<>();
    private static final Matrix4f ZERO = new Matrix4f().zero();

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

    public static boolean ready(SplatCloud cloud) {
        return enabled && shader != null && cloud.bones.length <= MAX_BONES;
    }

    // リソースの再読み込みで模型が入れ替わったら、古い VBO を捨てる (描画スレッドから)
    static void clear() {
        RenderSystem.assertOnRenderThread();
        for (VertexBuffer b : BUFFERS.values()) {
            b.close();
        }
        BUFFERS.clear();
    }

    private static VertexBuffer buffer(SplatCloud cloud) {
        VertexBuffer vb = BUFFERS.get(cloud);
        if (vb != null) {
            return vb;
        }
        SplatCloud.Part p = cloud.solid;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(p.n * 4 * DefaultVertexFormat.NEW_ENTITY.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            for (int i = 0; i < p.n; i++) {
                float h = p.size[i] * 0.5f;
                int bones = ((p.boneB[i] & 255) << 16) | (p.boneA[i] & 255);
                int wf = ((p.flags[i] & 255) << 16) | Math.round(p.weightA[i] * 255f);
                float nx = p.nx[i] / 127f, ny = p.ny[i] / 127f, nz = p.nz[i] / 127f;
                int c = p.argb[i] | 0xFF000000;
                // 角の順は CPU の描き方と同じ (左上・左下・右下・右上。UV0 = 左と上の向きの量)
                b.addVertex(p.x[i], p.y[i], p.z[i], c, h, h, bones, wf, nx, ny, nz);
                b.addVertex(p.x[i], p.y[i], p.z[i], c, h, -h, bones, wf, nx, ny, nz);
                b.addVertex(p.x[i], p.y[i], p.z[i], c, -h, -h, bones, wf, nx, ny, nz);
                b.addVertex(p.x[i], p.y[i], p.z[i], c, -h, h, bones, wf, nx, ny, nz);
            }
            MeshData mesh = b.buildOrThrow();
            vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh);
            VertexBuffer.unbind();
        }
        BUFFERS.put(cloud, vb);
        SkyAbyss.LOGGER.info("[splat] uploaded {} ({} splats) to the GPU", cloud.name, p.n);
        return vb;
    }

    // 不透明の点を一回で描く。mats/present は骨の番号ごとのポーズ (カメラが原点の空間)
    static void draw(SplatCloud cloud, Matrix4f[] mats, boolean[] present, int light, int overlay, int tint, float cullCos) {
        VertexBuffer vb = buffer(cloud);
        ShaderInstance sh = shader;
        for (int i = 0; i < MAX_BONES; i++) {
            Uniform u = sh.getUniform("Bone" + i);
            if (u != null) {
                u.set(i < mats.length && present[i] ? mats[i] : ZERO);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        Vector3f left = mc.gameRenderer.getMainCamera().getLeftVector();
        Vector3f up = mc.gameRenderer.getMainCamera().getUpVector();
        set(sh, "CamLeft", left.x, left.y, left.z);
        set(sh, "CamUp", up.x, up.y, up.z);
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
            cc.set(cullCos);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        vb.bind();
        vb.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), sh);
        VertexBuffer.unbind();
        mc.gameRenderer.overlayTexture().teardownOverlayColor();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.enableCull();
    }

    private static void set(ShaderInstance sh, String name, float x, float y, float z) {
        Uniform u = sh.getUniform(name);
        if (u != null) {
            u.set(x, y, z);
        }
    }
}
