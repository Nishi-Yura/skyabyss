package jp.revati.skyabyss.client.combat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.TelegraphGeometry;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 技の予告の床の印 (MMO の予兆表示)。サーバーが送った形 (TelegraphClient) を、床の高さに沿って描く。
//   うすい全範囲の面 + 中心 (手前) から満ちていく濃い面 + くっきり明るい縁 + 縁に立つ低い幕 (低い目線でも見える)
//   当たる瞬間は白く光って消える。波は動く輪と、安全な筋 (緑)。振り回す帯は通ったあとの扇と、いまの帯。
// 面は TelegraphGeometry (サーバーの自己テストが contains と突き合わせる) で刻み、頂点ごとに床の高さを引く。
// 奥行きは見る・書かない。床のすぐ上に少し浮かせ、遠いほど浮かせて z-fighting を避ける。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TelegraphRenderer {
    private static final int GREEN = 0x4CFF6A;

    private TelegraphRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (Config.TELEGRAPH_STYLE.get() == Config.TelegraphStyle.PARTICLES) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        boolean any = false;
        for (TelegraphClient.Decal ignored : TelegraphClient.decals()) {
            any = true;
            break;
        }
        if (!any) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        boolean hc = Config.TELEGRAPH_HIGH_CONTRAST.get();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Painter painter = new Painter(buffer, pose, cam, level, hc);
        boolean drew = false;
        for (TelegraphClient.Decal d : TelegraphClient.decals()) {
            float t = (float) (level.getGameTime() - d.localStart) + partial;
            if (t < 0 || t > d.lifetime()) {
                continue;
            }
            painter.begin(d);
            drawDecal(painter, d, t, hc);
            drew = true;
        }
        if (!drew) {
            return;
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -2.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ---- 1 つの印の描き分け ----

    private static void drawDecal(Painter p, TelegraphClient.Decal d, float t, boolean hc) {
        TelegraphShape shape = d.shape;
        int rgb = d.rgb;
        float areaA = hc ? 0.22f : 0.12f;
        float fillA = hc ? 0.50f : 0.34f;
        float curtain = hc ? 1.0f : 0.6f;

        if (t < d.warn) {
            float prog = t / d.warn;
            // 近づくと白く・濃く、最後の 1/4 は脈打つ
            float pulse = prog > 0.75f ? 0.10f * (float) Math.sin(t * 1.3f) : 0f;
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, areaA);
            p.faces(g -> TelegraphGeometry.fill(shape, prog, g), mix(rgb, 0xFFFFFF, 0.3f * prog), fillA + 0.22f * prog + pulse);
            if (shape.kind() == TelegraphShape.KIND_WAVE) {
                lanes(p, shape, hc);
            }
            p.lines(g -> TelegraphGeometry.outline(shape, g), mix(rgb, 0xFFFFFF, 0.55f), 0.95f, hc ? 0.2f : 0.12f, curtain * (0.55f + 0.45f * prog));
            p.lines(g -> TelegraphGeometry.front(shape, prog, g), mix(rgb, 0xFFFFFF, 0.85f), 1f, hc ? 0.3f : 0.2f, curtain * 0.6f);
            if (shape.kind() == TelegraphShape.KIND_SWEEP) {
                // 帯の始まりの向き (これから振り回す帯)
                TelegraphShape blade = shape.at(0);
                p.faces(g -> TelegraphGeometry.area(blade, g), mix(rgb, 0xFFFFFF, 0.3f), 0.28f + 0.3f * prog);
                p.lines(g -> TelegraphGeometry.edgesOf(blade, g), mix(rgb, 0xFFFFFF, 0.85f), 1f, hc ? 0.3f : 0.2f, curtain * 0.7f);
            }
            return;
        }
        float after = t - d.warn;
        if (d.active > 0 && after < d.active) {
            float q = after / d.active;
            TelegraphShape now = shape.at(q);
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, areaA * 0.7f);
            if (shape.kind() == TelegraphShape.KIND_WAVE) {
                lanes(p, shape, hc);
            }
            p.faces(g -> TelegraphGeometry.swept(shape, q, g), rgb, fillA);
            p.faces(g -> TelegraphGeometry.area(now, g), mix(rgb, 0xFFFFFF, 0.5f), hc ? 0.85f : 0.65f);
            p.lines(g -> TelegraphGeometry.edgesOf(now, g), 0xFFFFFF, 1f, hc ? 0.32f : 0.22f, curtain * 1.2f);
            return;
        }
        // 当たった瞬間の光 (非動体)、動くものは終わりの余韻
        float since = d.active > 0 ? after - d.active : after;
        float f = Math.max(0f, 1f - since / TelegraphClient.FLASH_TICKS);
        if (d.active > 0) {
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, areaA * 0.7f * f);
            return;
        }
        p.faces(g -> TelegraphGeometry.area(shape, g), mix(rgb, 0xFFFFFF, 0.75f), 0.85f * f);
        p.lines(g -> TelegraphGeometry.outline(shape, g), 0xFFFFFF, f, hc ? 0.3f : 0.2f, curtain * 1.3f * f);
    }

    private static void lanes(Painter p, TelegraphShape shape, boolean hc) {
        p.faces(g -> TelegraphGeometry.lanes(shape, g), GREEN, hc ? 0.42f : 0.28f);
        p.lines(g -> TelegraphGeometry.laneEdges(shape, g), mix(GREEN, 0xFFFFFF, 0.3f), 0.95f, hc ? 0.2f : 0.12f, hc ? 0.8f : 0.45f);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    @FunctionalInterface
    private interface FaceSource {
        void emit(TelegraphGeometry.Tri out);
    }

    @FunctionalInterface
    private interface LineSource {
        void emit(TelegraphGeometry.Seg out);
    }

    // 頂点に床の高さを引いて、面・線・幕をバッファへ出す
    private static final class Painter {
        private final BufferBuilder buf;
        private final Matrix4f pose;
        private final Vec3 cam;
        private final ClientLevel level;
        private final boolean hc;
        private TelegraphClient.Decal decal;
        private float lift;

        Painter(BufferBuilder buf, Matrix4f pose, Vec3 cam, ClientLevel level, boolean hc) {
            this.buf = buf;
            this.pose = pose;
            this.cam = cam;
            this.level = level;
            this.hc = hc;
        }

        void begin(TelegraphClient.Decal d) {
            this.decal = d;
            double[] b = d.shape.bounds();
            double dist = Math.hypot((b[0] + b[2]) / 2 - cam.x, (b[1] + b[3]) / 2 - cam.z) + Math.abs(cam.y - d.floor);
            // 遠いほど深度の刻みが粗いので、浮かせる量を増やす
            this.lift = (float) (0.025 + 0.0016 * dist);
        }

        void faces(FaceSource source, int rgb, float alpha) {
            if (alpha <= 0.005f) {
                return;
            }
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            float a = Math.min(1f, alpha);
            source.emit((x0, z0, x1, z1, x2, z2) -> {
                vertex(x0, z0, lift, r, g, b, a);
                vertex(x1, z1, lift, r, g, b, a);
                vertex(x2, z2, lift, r, g, b, a);
                vertex(x2, z2, lift, r, g, b, a);
            });
        }

        void lines(LineSource source, int rgb, float alpha, float width, float curtainHeight) {
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            float a = Math.min(1f, alpha);
            float up = lift + 0.012f;
            source.emit((x0, z0, x1, z1) -> {
                double dx = x1 - x0;
                double dz = z1 - z0;
                double len = Math.hypot(dx, dz);
                if (len < 1e-6) {
                    return;
                }
                // 線の両端を少し伸ばして、折れ目のすき間を埋める
                double ex = dx / len * width * 0.5;
                double ez = dz / len * width * 0.5;
                double ax = x0 - ex;
                double az = z0 - ez;
                double bx = x1 + ex;
                double bz = z1 + ez;
                double nx = -dz / len * width * 0.5;
                double nz = dx / len * width * 0.5;
                if (hc) {
                    // 高コントラスト: 縁の下に黒い縁取り
                    double wx = nx * 1.9;
                    double wz = nz * 1.9;
                    vertex(ax + wx, az + wz, lift + 0.004f, 0, 0, 0, 0.7f);
                    vertex(bx + wx, bz + wz, lift + 0.004f, 0, 0, 0, 0.7f);
                    vertex(bx - wx, bz - wz, lift + 0.004f, 0, 0, 0, 0.7f);
                    vertex(ax - wx, az - wz, lift + 0.004f, 0, 0, 0, 0.7f);
                }
                vertex(ax + nx, az + nz, up, r, g, b, a);
                vertex(bx + nx, bz + nz, up, r, g, b, a);
                vertex(bx - nx, bz - nz, up, r, g, b, a);
                vertex(ax - nx, az - nz, up, r, g, b, a);
                if (curtainHeight > 0.02f) {
                    // 縁に立つ低い幕: 下は縁の色、上へ消える。低い目線では床の面が薄く見えるので、これで範囲が読める
                    float ca = a * (hc ? 0.75f : 0.55f);
                    vertex(x0, z0, up, r, g, b, ca);
                    vertex(x1, z1, up, r, g, b, ca);
                    vertex(x1, z1, up + curtainHeight, r, g, b, 0f);
                    vertex(x0, z0, up + curtainHeight, r, g, b, 0f);
                }
            });
        }

        private void vertex(double x, double z, float height, float r, float g, float b, float a) {
            double y = decal.ground(level, x, z) + height;
            buf.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
        }
    }
}
