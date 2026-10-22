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
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphGeometry;
import jp.revati.skyabyss.combat.TelegraphGround;
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
    private TelegraphRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            onRenderLevel0(event);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("client.telegraph", prof);
        }
    }

    static void onRenderLevel0(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
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
        Telegraph.Style style = d.style;
        float areaA = hc ? 0.32f : 0.22f;
        float fillA = hc ? 0.58f : 0.44f;
        float curtain = hc ? 1.7f : 1.1f;
        // 安全な場所の印は出さない (2026-10-22: 印は赤だけ)
        if (style == Telegraph.Style.SAFE) {
            return;
        }
        float wm = style == Telegraph.Style.DANGER ? 0.1f : 0.3f;
        // 縁の模様 (色だけに頼らない): 壁で止まる技 = 実線、壁を抜ける技 = 破線 (濃い赤)、押すだけ = 点
        Pattern pattern = switch (style) {
            case PIERCE -> Pattern.DASH;
            case HARMLESS -> Pattern.DOT;
            default -> Pattern.SOLID;
        };
        float prog = Math.min(1f, t / d.warn);
        // どれも赤。壁で止まる技は明るい赤から赤へ、壁を抜ける技は濃い赤のまま (当たる瞬間に白く光る)
        int rgb = style == Telegraph.Style.PIERCE ? Telegraph.Style.PIERCE.rgb
                : style == Telegraph.Style.HARMLESS ? Telegraph.Style.HARMLESS.rgb : mix(0xFF5A40, 0xFF1A10, prog);
        if (style == Telegraph.Style.HARMLESS) {
            areaA *= 0.7f;
            fillA *= 0.6f;
        }
        p.pattern = pattern;
        p.centerX = shape.centerX();
        p.centerZ = shape.centerZ();

        if (t < d.warn) {
            // 近づくと白く・濃く、最後の 1/4 は脈打つ
            float pulse = prog > 0.75f && style != Telegraph.Style.HARMLESS ? 0.10f * (float) Math.sin(t * 1.3f) : 0f;
            if (style == Telegraph.Style.SAFE) {
                // 安全な場所は満ちない。青白の面と破線の縁がずっと同じ濃さで出る
                p.faces(g -> TelegraphGeometry.area(shape, g), rgb, 0.26f);
                p.lines(g -> TelegraphGeometry.outline(shape, g), mix(rgb, 0xFFFFFF, 0.6f), 0.95f, hc ? 0.22f : 0.14f, curtain * 0.7f);
                return;
            }
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, areaA);
            p.faces(g -> TelegraphGeometry.fill(shape, prog, g), mix(rgb, 0xFFFFFF, wm * prog), fillA + 0.22f * prog + pulse);
            p.pattern = Pattern.SOLID;
            p.lines(g -> TelegraphGeometry.outline(shape, g), rgb, 0.28f + 0.2f * prog, hc ? 0.75f : 0.55f, 0f);
            p.pattern = pattern;
            p.lines(g -> TelegraphGeometry.outline(shape, g), mix(rgb, 0xFFFFFF, 0.55f), 1f, hc ? 0.3f : 0.22f, curtain * (0.6f + 0.5f * prog));
            p.pattern = Pattern.SOLID;
            p.lines(g -> TelegraphGeometry.front(shape, prog, g), mix(rgb, 0xFFFFFF, 0.85f), 1f, hc ? 0.3f : 0.2f, curtain * 0.6f);
            p.pattern = pattern;
            if (shape.kind() == TelegraphShape.KIND_SWEEP) {
                // 帯の始まりの向き (これから振り回す帯)
                TelegraphShape blade = shape.at(0);
                p.faces(g -> TelegraphGeometry.area(blade, g), mix(rgb, 0xFFFFFF, 0.3f), 0.28f + 0.3f * prog);
                p.lines(g -> TelegraphGeometry.edgesOf(blade, g), mix(rgb, 0xFFFFFF, 0.85f), 1f, hc ? 0.3f : 0.2f, curtain * 0.7f);
            }
            targetMarker(p, d, t, hc);
            return;
        }
        float after = t - d.warn;
        if (d.active > 0 && after < d.active) {
            float q = after / d.active;
            TelegraphShape now = shape.at(q);
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, areaA * 0.7f);
            p.faces(g -> TelegraphGeometry.swept(shape, q, g), rgb, fillA);
            p.faces(g -> TelegraphGeometry.area(now, g), mix(rgb, 0xFFFFFF, 0.5f), hc ? 0.85f : 0.65f);
            p.pattern = Pattern.SOLID;
            p.lines(g -> TelegraphGeometry.edgesOf(now, g), 0xFFFFFF, 1f, hc ? 0.32f : 0.22f, curtain * 1.2f);
            return;
        }
        // 当たった瞬間の光 (非動体)、動くものは終わりの余韻
        float since = d.active > 0 ? after - d.active : after;
        float f = Math.max(0f, 1f - since / TelegraphClient.FLASH_TICKS);
        if (style == Telegraph.Style.SAFE) {
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, 0.26f * f);
            return;
        }
        if (d.active > 0) {
            p.faces(g -> TelegraphGeometry.area(shape, g), rgb, areaA * 0.7f * f);
            return;
        }
        float flash = style == Telegraph.Style.HARMLESS ? 0.5f : 0.85f;
        p.faces(g -> TelegraphGeometry.area(shape, g), mix(rgb, 0xFFFFFF, 0.75f), flash * f);
        p.pattern = Pattern.SOLID;
        p.lines(g -> TelegraphGeometry.outline(shape, g), 0xFFFFFF, f, hc ? 0.4f : 0.3f, curtain * 1.5f * f);
        p.lines(g -> TelegraphGeometry.outline(shape, g), rgb, 0.5f * f, 1.2f + 1.2f * (1f - f), 0f);
    }

    // 狙われた本人にだけ: 帯 (突き・扇・振り回す帯) の根もとに輪と、向かう先を指す矢印
    private static void targetMarker(Painter p, TelegraphClient.Decal d, float t, boolean hc) {
        if (d.target == null || Minecraft.getInstance().player == null || !d.target.equals(Minecraft.getInstance().player.getUUID())) {
            return;
        }
        int kind = d.shape.kind();
        if (kind != TelegraphShape.KIND_LINE && kind != TelegraphShape.KIND_CONE && kind != TelegraphShape.KIND_SWEEP) {
            return;
        }
        double[] prm = d.shape.params();
        double ox = prm[0];
        double oz = prm[1];
        double angle = kind == TelegraphShape.KIND_SWEEP ? prm[2] + prm[3] * 0.5 : prm[2];
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.5f);
        double r = 0.9;
        int n = 20;
        float w = hc ? 0.2f : 0.14f;
        p.pattern = Pattern.SOLID;
        p.lines(g -> {
            for (int i = 0; i < n; i++) {
                double a0 = 2 * Math.PI * i / n;
                double a1 = 2 * Math.PI * (i + 1) / n;
                g.seg(ox + Math.cos(a0) * r, oz + Math.sin(a0) * r, ox + Math.cos(a1) * r, oz + Math.sin(a1) * r);
            }
        }, 0xFFFFFF, pulse, w, 0.5f);
        // 矢印 (輪の外から前へ)
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double tipD = 2.5;
        double baseD = 1.5;
        double half = 0.55;
        p.faces(g -> g.tri(ox + c * tipD, oz + s * tipD, ox + c * baseD - s * half, oz + s * baseD + c * half,
                ox + c * baseD + s * half, oz + s * baseD - c * half), 0xFFFFFF, pulse * 0.9f);
    }

    private static void lanes(Painter p, TelegraphShape shape, boolean hc) {
        int safe = Telegraph.Style.SAFE.rgb;
        Pattern keep = p.pattern;
        p.faces(g -> TelegraphGeometry.lanes(shape, g), safe, hc ? 0.42f : 0.28f);
        p.pattern = Pattern.DASH;
        p.lines(g -> TelegraphGeometry.laneEdges(shape, g), mix(safe, 0xFFFFFF, 0.6f), 0.95f, hc ? 0.2f : 0.12f, hc ? 0.8f : 0.45f);
        p.pattern = keep;
    }

    enum Pattern {
        SOLID,
        DASH,
        DOT
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

    // 面・線・幕を、歩ける床の面にだけ出す。列ごとの床 (TelegraphGround) で三角形・線を切り、段差・半ブロックはその高さで描く。
    // 床の無い所 (壁の上・穴・空中・天井の上) は出さない。範囲の全部が平らな床なら (広間など) 切らずにそのまま出す。
    private static final class Painter {
        private static final double GRID = 0.5;
        private final BufferBuilder buf;
        private final Matrix4f pose;
        private final Vec3 cam;
        private final boolean hc;
        private TelegraphGround ground;
        private double flat = Double.NaN;
        private float lift;
        private float[] unloaded;
        Pattern pattern = Pattern.SOLID;
        double centerX;
        double centerZ;
        private final double[] poly = new double[40];
        private final double[] clipOut = new double[40];
        private final double[] clipTmp = new double[40];
        private final double[] cuts = new double[64];

        Painter(BufferBuilder buf, Matrix4f pose, Vec3 cam, ClientLevel level, boolean hc) {
            this.buf = buf;
            this.pose = pose;
            this.cam = cam;
            this.hc = hc;
        }

        void begin(TelegraphClient.Decal d) {
            this.ground = d.ground;
            this.flat = ground.flatHeight(d.shape.bounds());
            float f = (float) d.floor;
            this.unloaded = new float[] {f, f, f, f};
            double[] b = d.shape.bounds();
            double dist = Math.hypot((b[0] + b[2]) / 2 - cam.x, (b[1] + b[3]) / 2 - cam.z) + Math.abs(cam.y - d.floor);
            // 遠いほど深度の刻みが粗いので、浮かせる量を増やす
            this.lift = (float) (0.025 + 0.0016 * dist);
        }

        private float[] col(int bx, int bz) {
            float[] c = ground.column(bx, bz);
            return c != null ? c : unloaded;
        }

        void faces(FaceSource source, int rgb, float alpha) {
            if (alpha <= 0.005f) {
                return;
            }
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            float a = Math.min(1f, alpha);
            source.emit((x0, z0, x1, z1, x2, z2) -> tri(x0, z0, x1, z1, x2, z2, r, g, b, a));
        }

        private void tri(double x0, double z0, double x1, double z1, double x2, double z2, float r, float g, float b, float a) {
            if (!Double.isNaN(flat)) {
                emitTri(x0, z0, x1, z1, x2, z2, (float) flat + lift, r, g, b, a);
                return;
            }
            int cx0 = (int) Math.floor(Math.min(x0, Math.min(x1, x2)) + 1e-9);
            int cx1 = (int) Math.floor(Math.max(x0, Math.max(x1, x2)) - 1e-9);
            int cz0 = (int) Math.floor(Math.min(z0, Math.min(z1, z2)) + 1e-9);
            int cz1 = (int) Math.floor(Math.max(z0, Math.max(z1, z2)) - 1e-9);
            cx1 = Math.max(cx0, cx1);
            cz1 = Math.max(cz0, cz1);
            // 全部の列が同じ高さの平らな床なら切らずに出す。全部が床なしなら出さない
            boolean fast = true;
            float h = Float.NaN;
            boolean first = true;
            for (int x = cx0; x <= cx1 && fast; x++) {
                for (int z = cz0; z <= cz1; z++) {
                    float[] c = col(x, z);
                    if (!TelegraphGround.uniform(c) || (!first && !TelegraphGround.same(h, c[0]))) {
                        fast = false;
                        break;
                    }
                    h = c[0];
                    first = false;
                }
            }
            if (fast) {
                if (!Float.isNaN(h)) {
                    emitTri(x0, z0, x1, z1, x2, z2, h + lift, r, g, b, a);
                }
                return;
            }
            for (int x = cx0; x <= cx1; x++) {
                for (int z = cz0; z <= cz1; z++) {
                    float[] c = col(x, z);
                    if (TelegraphGround.uniform(c)) {
                        if (!Float.isNaN(c[0])) {
                            clipEmit(x0, z0, x1, z1, x2, z2, x, z, x + 1, z + 1, c[0] + lift, r, g, b, a);
                        }
                        continue;
                    }
                    for (int s = 0; s < 4; s++) {
                        if (Float.isNaN(c[s])) {
                            continue;
                        }
                        double sx = x + (s & 1) * GRID;
                        double sz = z + (s >> 1) * GRID;
                        clipEmit(x0, z0, x1, z1, x2, z2, sx, sz, sx + GRID, sz + GRID, c[s] + lift, r, g, b, a);
                    }
                }
            }
        }

        private void clipEmit(double x0, double z0, double x1, double z1, double x2, double z2, double minX, double minZ, double maxX, double maxZ,
                float y, float r, float g, float b, float a) {
            poly[0] = x0;
            poly[1] = z0;
            poly[2] = x1;
            poly[3] = z1;
            poly[4] = x2;
            poly[5] = z2;
            int n = TelegraphGround.clipRect(poly, 3, minX, minZ, maxX, maxZ, clipOut, clipTmp);
            for (int i = 1; i + 1 < n; i++) {
                emitTri(clipOut[0], clipOut[1], clipOut[2 * i], clipOut[2 * i + 1], clipOut[2 * i + 2], clipOut[2 * i + 3], y, r, g, b, a);
            }
        }

        private void emitTri(double x0, double z0, double x1, double z1, double x2, double z2, float y, float r, float g, float b, float a) {
            vertex(x0, z0, y, r, g, b, a);
            vertex(x1, z1, y, r, g, b, a);
            vertex(x2, z2, y, r, g, b, a);
            vertex(x2, z2, y, r, g, b, a);
        }

        void lines(LineSource source, int rgb, float alpha, float width, float curtainHeight) {
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            float a = Math.min(1f, alpha);
            double[] acc = {0};
            source.emit((x0, z0, x1, z1) -> {
                double dx = x1 - x0;
                double dz = z1 - z0;
                double len = Math.hypot(dx, dz);
                if (len < 1e-6) {
                    return;
                }
                double ux = dx / len;
                double uz = dz / len;
                switch (pattern) {
                    case SOLID -> piece(x0, z0, x1, z1, r, g, b, a, width, curtainHeight, true);
                    case DASH, DOT -> {
                        // 破線 (0.55 描いて 0.35 あける) / 点 (0.18 描いて 0.42 あける)。長さは続けて数える
                        double on = pattern == Pattern.DASH ? 0.55 : 0.18;
                        double period = pattern == Pattern.DASH ? 0.9 : 0.6;
                        double at = 0;
                        while (at < len) {
                            double phase = (acc[0] + at) % period;
                            if (phase < on) {
                                double seg = Math.min(on - phase, len - at);
                                piece(x0 + ux * at, z0 + uz * at, x0 + ux * (at + seg), z0 + uz * (at + seg), r, g, b, a, width, curtainHeight, false);
                                at += Math.max(seg, 0.01);
                            } else {
                                at += Math.max(period - phase, 0.02);
                            }
                        }
                        acc[0] += len;
                    }
                }
            });
        }

        // 線の 1 片: 平らな帯と、立つ幕。extend = 両端を少し伸ばして折れ目のすき間を埋める。
        // 床の列 (半マス) の境でさらに切り、それぞれの床の高さで描く。床の無い所は描かない
        private void piece(double x0, double z0, double x1, double z1, float r, float g, float b, float a, float width, float curtainHeight, boolean extend) {
            double dx = x1 - x0;
            double dz = z1 - z0;
            double len = Math.hypot(dx, dz);
            if (len < 1e-6) {
                return;
            }
            double ext = extend ? width * 0.5 : 0;
            double ax = x0 - dx / len * ext;
            double az = z0 - dz / len * ext;
            double bx = x1 + dx / len * ext;
            double bz = z1 + dz / len * ext;
            double nx = -dz / len * width * 0.5;
            double nz = dx / len * width * 0.5;
            // 切る位置 (a→b の線の上の割合)
            int nc = 0;
            cuts[nc++] = 0;
            if (Double.isNaN(flat)) {
                nc = cutsAlong(ax, bx, nc);
                nc = cutsAlong(az, bz, nc);
                java.util.Arrays.sort(cuts, 1, nc);
            }
            cuts[nc++] = 1;
            double total = len + 2 * ext;
            double t0Cur = ext / total;
            double t1Cur = 1 - ext / total;
            for (int i = 0; i + 1 < nc; i++) {
                double ta = cuts[i];
                double tb = cuts[i + 1];
                if (tb - ta < 1e-7) {
                    continue;
                }
                double h = Double.isNaN(flat) ? ground.heightAt(ax + (bx - ax) * (ta + tb) * 0.5, az + (bz - az) * (ta + tb) * 0.5) : flat;
                if (Double.isNaN(h)) {
                    continue;
                }
                float base = (float) h + lift;
                float up = base + 0.012f;
                double sx0 = ax + (bx - ax) * ta;
                double sz0 = az + (bz - az) * ta;
                double sx1 = ax + (bx - ax) * tb;
                double sz1 = az + (bz - az) * tb;
                if (hc) {
                    // 高コントラスト: 縁の下に黒い縁取り
                    double wx = nx * 1.9;
                    double wz = nz * 1.9;
                    vertex(sx0 + wx, sz0 + wz, base + 0.004f, 0, 0, 0, 0.7f);
                    vertex(sx1 + wx, sz1 + wz, base + 0.004f, 0, 0, 0, 0.7f);
                    vertex(sx1 - wx, sz1 - wz, base + 0.004f, 0, 0, 0, 0.7f);
                    vertex(sx0 - wx, sz0 - wz, base + 0.004f, 0, 0, 0, 0.7f);
                }
                vertex(sx0 + nx, sz0 + nz, up, r, g, b, a);
                vertex(sx1 + nx, sz1 + nz, up, r, g, b, a);
                vertex(sx1 - nx, sz1 - nz, up, r, g, b, a);
                vertex(sx0 - nx, sz0 - nz, up, r, g, b, a);
                double ca0 = Math.max(ta, t0Cur);
                double cb0 = Math.min(tb, t1Cur);
                if (curtainHeight > 0.02f && cb0 > ca0 + 1e-7) {
                    // 縁に立つ低い幕: 下は縁の色、上へ消える。低い目線では床の面が薄く見えるので、これで範囲が読める
                    float ca = a * (hc ? 0.75f : 0.55f);
                    double cx0 = ax + (bx - ax) * ca0;
                    double cz0 = az + (bz - az) * ca0;
                    double cx1 = ax + (bx - ax) * cb0;
                    double cz1 = az + (bz - az) * cb0;
                    vertex(cx0, cz0, up, r, g, b, ca);
                    vertex(cx1, cz1, up, r, g, b, ca);
                    vertex(cx1, cz1, up + curtainHeight, r, g, b, 0f);
                    vertex(cx0, cz0, up + curtainHeight, r, g, b, 0f);
                }
            }
        }

        // 座標 from→to が、半マスごとの格子線を横切る割合を cuts に足す
        private int cutsAlong(double from, double to, int nc) {
            double lo = Math.min(from, to);
            double hi = Math.max(from, to);
            if (hi - lo < 1e-9) {
                return nc;
            }
            for (double k = Math.floor(lo / GRID + 1e-9) + 1; k * GRID < hi - 1e-9 && nc < cuts.length - 2; k++) {
                double t = (k * GRID - from) / (to - from);
                if (t > 1e-7 && t < 1 - 1e-7) {
                    cuts[nc++] = t;
                }
            }
            return nc;
        }

        private void vertex(double x, double z, float y, float r, float g, float b, float a) {
            buf.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
        }
    }
}
