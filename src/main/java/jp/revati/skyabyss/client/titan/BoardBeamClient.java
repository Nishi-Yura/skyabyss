package jp.revati.skyabyss.client.titan;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 乗り込み口・戻る場所の光の柱と枠 (docs/design/01 の 4.3「全巨神に共通の決まり」、titan.end.BoardGlow)。
// サーバーが乗れる人にだけ送り (TitanEndNetwork.Beam)、受け取った間だけ描く (数秒で消える)。巨神ごとの色の、柔らかい縦の光の柱 (交差した 2 枚の帯、上へ薄れる)
// と、入る場所をなぞる枠 (面はごく薄く、辺は距離に応じて太くして、100 ブロック先でも見える)。ゆっくり脈打つ。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BoardBeamClient {
    private record Entry(TitanEndNetwork.Beam beam, long until) {
    }

    private static final Map<String, Entry> BEAMS = new HashMap<>();
    private static long clientTick;

    private BoardBeamClient() {
    }

    public static void receive(TitanEndNetwork.Beam beam) {
        Minecraft.getInstance().execute(() -> BEAMS.put(beam.key(), new Entry(beam, clientTick + Math.max(1, beam.ttl()))));
    }

    // その key の知らせが表示中か
    public static boolean has(String key) {
        return BEAMS.containsKey(key);
    }

    // 全部消す (選定・撮影用)
    public static void clearAll() {
        Minecraft.getInstance().execute(BEAMS::clear);
    }

    // 表示中の知らせの数 (確認用)
    public static int active() {
        return BEAMS.size();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        clientTick++;
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : BEAMS.values()) {
            // A/B/C の粒 (CURRENT はサーバーの粒だけ)
            jp.revati.skyabyss.Config.MarkerStyle st = MarkerArt.style(MarkerArt.isReturn(e.beam()));
            if (st != jp.revati.skyabyss.Config.MarkerStyle.CURRENT) {
                MarkerArt.particles(mc, e.beam(), st, clientTick, MarkerArt.returnTitan(mc));
            }
        }
        Iterator<Entry> it = BEAMS.values().iterator();
        while (it.hasNext()) {
            if (it.next().until() < clientTick) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BEAMS.clear();
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || BEAMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = clientTick + partial;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 通常合成の面 (A/B/C の筋の本体。昼空でも色が乗る)。Tesselator は 1 本しか使えないので別の置き場
        ByteBufferBuilder alphaStore = new ByteBufferBuilder(1 << 17);
        BufferBuilder alphaBuf = new BufferBuilder(alphaStore, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Entry e : BEAMS.values()) {
            TitanEndNetwork.Beam b = e.beam();
            // 消えかけ (残り 10 tick) はなめらかに薄れる。現れるときも少し
            float life = Mth.clamp((e.until() - (float) time) / 10f, 0f, 1f);
            float pulse = 0.78f + 0.22f * Mth.sin((float) (time / 50.0 * Mth.TWO_PI));
            jp.revati.skyabyss.Config.MarkerStyle style = MarkerArt.style(MarkerArt.isReturn(b));
            if (style != jp.revati.skyabyss.Config.MarkerStyle.CURRENT) {
                MarkerArt.draw(buf, alphaBuf, pose, cam, b, style, life, time, MarkerArt.returnTitan(mc));
                continue;
            }
            Painter p = new Painter(buf, pose, cam, b.rgb(), life * pulse);
            p.beam(b);
            p.frame(b);
        }
        MeshData mesh = buf.build();
        MeshData alphaMesh = alphaBuf.build();
        if (mesh == null && alphaMesh == null) {
            alphaStore.close();
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        if (alphaMesh != null) {
            BufferUploader.drawWithShader(alphaMesh);
        }
        alphaStore.close();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static final class Painter {
        private final BufferBuilder buf;
        private final Matrix4f pose;
        private final Vec3 cam;
        private final int r;
        private final int g;
        private final int bl;
        private final float strength;

        Painter(BufferBuilder buf, Matrix4f pose, Vec3 cam, int rgb, float strength) {
            this.buf = buf;
            this.pose = pose;
            this.cam = cam;
            this.r = (rgb >> 16) & 0xFF;
            this.g = (rgb >> 8) & 0xFF;
            this.bl = rgb & 0xFF;
            this.strength = strength;
        }

        // 縦の光の柱: 交差した 2 枚の帯 (幅 2 半径)。下は濃く、上へ薄れる。加算で重ねるので控えめな濃さ
        void beam(TitanEndNetwork.Beam b) {
            if (b.beamTo() <= b.beamFrom()) {
                return;
            }
            double rad = Math.max(0.3, b.beamRadius());
            // 遠くでは少し太く (細い線が見えなくならないように)
            double dist = Math.sqrt((b.cx() - cam.x) * (b.cx() - cam.x) + (b.cz() - cam.z) * (b.cz() - cam.z));
            rad += Math.min(2.0, dist * 0.006);
            int steps = 8;
            double h = b.beamTo() - b.beamFrom();
            for (int plane = 0; plane < 2; plane++) {
                double dx = plane == 0 ? rad : 0;
                double dz = plane == 0 ? 0 : rad;
                for (int i = 0; i < steps; i++) {
                    double y0 = b.beamFrom() + h * i / steps;
                    double y1 = b.beamFrom() + h * (i + 1) / steps;
                    float a0 = alphaAt(i / (float) steps) * 0.34f * strength;
                    float a1 = alphaAt((i + 1) / (float) steps) * 0.34f * strength;
                    vertex(b.cx() - dx, y0, b.cz() - dz, a0);
                    vertex(b.cx() + dx, y0, b.cz() + dz, a0);
                    vertex(b.cx() + dx, y1, b.cz() + dz, a1);
                    vertex(b.cx() - dx, y1, b.cz() - dz, a1);
                }
            }
        }

        private static float alphaAt(float t) {
            return (1f - t) * (1f - t) * 0.85f + 0.15f * (1f - t);
        }

        // 枠: 面はごく薄く、辺は帯 (距離に応じて太く)
        void frame(TitanEndNetwork.Beam b) {
            if (b.hx() <= 0 || b.hy() <= 0 || b.hz() <= 0) {
                return;
            }
            double yaw = Math.toRadians(b.yaw());
            // yaw の向き (前 = (-sin, cos))、横 = 前を左へ 90 度 (cos, sin)
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            double sx = Math.cos(yaw);
            double sz = Math.sin(yaw);
            double[][] c = new double[8][];
            int n = 0;
            for (int ix = -1; ix <= 1; ix += 2) {
                for (int iy = -1; iy <= 1; iy += 2) {
                    for (int iz = -1; iz <= 1; iz += 2) {
                        c[n++] = new double[] {b.cx() + sx * b.hx() * ix + fx * b.hz() * iz, b.cy() + b.hy() * iy, b.cz() + sz * b.hx() * ix + fz * b.hz() * iz};
                    }
                }
            }
            // 面 (6 枚): 頂点の番号 = ix*4 + iy*2 + iz (-1 → 0、+1 → 1)
            int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            for (int[] f : faces) {
                for (int k : f) {
                    vertex(c[k][0], c[k][1], c[k][2], 0.07f * strength);
                }
            }
            double dist = Math.sqrt((b.cx() - cam.x) * (b.cx() - cam.x) + (b.cy() - cam.y) * (b.cy() - cam.y) + (b.cz() - cam.z) * (b.cz() - cam.z));
            double w = Math.max(0.05, Math.min(1.2, dist * 0.006));
            // 辺 (12 本): 頂点番号が 1 つだけ違う組
            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] e : edges) {
                ribbon(c[e[0]], c[e[1]], w, 0.55f * strength);
            }
        }

        // 辺 a→b の帯 (視線に垂直な向きに幅 w)
        private void ribbon(double[] a, double[] b, double w, float alpha) {
            double ex = b[0] - a[0];
            double ey = b[1] - a[1];
            double ez = b[2] - a[2];
            double mx = (a[0] + b[0]) / 2 - cam.x;
            double my = (a[1] + b[1]) / 2 - cam.y;
            double mz = (a[2] + b[2]) / 2 - cam.z;
            // 辺と視線の外積 = 視線に垂直で辺に垂直な向き
            double px = ey * mz - ez * my;
            double py = ez * mx - ex * mz;
            double pz = ex * my - ey * mx;
            double len = Math.sqrt(px * px + py * py + pz * pz);
            if (len < 1.0e-6) {
                return;
            }
            px = px / len * w;
            py = py / len * w;
            pz = pz / len * w;
            vertex(a[0] - px, a[1] - py, a[2] - pz, alpha);
            vertex(a[0] + px, a[1] + py, a[2] + pz, alpha);
            vertex(b[0] + px, b[1] + py, b[2] + pz, alpha);
            vertex(b[0] - px, b[1] - py, b[2] - pz, alpha);
        }

        private void vertex(double x, double y, double z, float alpha) {
            buf.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, bl, (int) (Mth.clamp(alpha, 0f, 1f) * 255f));
        }
    }
}
