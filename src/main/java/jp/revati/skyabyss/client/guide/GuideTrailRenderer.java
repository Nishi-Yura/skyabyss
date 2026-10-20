package jp.revati.skyabyss.client.guide;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 地面の道しるべ。サーバーが探した歩ける道 (GuideClient.Trail) の上に、一本のつながった光の帯 (リボン) を床にそって敷く。
// 道の点は滑らかな曲線 (Catmull-Rom) にならし、床の高さにそって上下する。中心が明るく縁が溶ける帯で、ゆるやかな光の波が目的の方へ流れる。
// 手前ほど濃く、約 40 ブロック先で消え、足もとからは少し間を置いて立ち上がる。メインは金、サブ (追跡中のクエスト) は青緑。
// 道なしのときは、足もとの床にそって目印の向きへ 16 ブロックだけ、同じ帯を伸ばす (床の高さは上から床を探す)。
// 描かない場面: 場面・画面・F1・オーバーワールド以外・目的がないとき・設定 OFF (GuideClient.visible)。技の予告 (橙・赤・青白) とは色が違う。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GuideTrailRenderer {
    static final double SHOW_LENGTH = 40.0;
    static final double DASH_LENGTH = 16.0;
    private static final int GOLD = 0xF2C14E;
    private static final int PALE = 0xFFE9A8;
    private static final int TEAL = 0x7FE0D8;
    private static final int TEAL_PALE = 0xDCFFF8;
    private static final double STEP = 0.4;
    private static final double CORE = 0.17;
    private static final double EDGE = 0.25;

    private GuideTrailRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != null && mc.player != null && mc.screen == null && !mc.options.hideGui && drawMaze(event, mc, level)) {
            return;
        }
        BlockPos goal = GuideClient.goal();
        if (level == null || goal == null || !GuideClient.trailEnabled() || !GuideClient.visible(mc)) {
            return;
        }
        Vec3 eye = mc.player.position();
        double gx = goal.getX() + 0.5 - eye.x;
        double gz = goal.getZ() + 0.5 - eye.z;
        double goalDist = Math.sqrt(gx * gx + gz * gz);
        if (goalDist < 3.5) {
            return;
        }
        if (Config.QUEST_TRAIL.get() == Config.TrailMode.NEAR && goalDist > GuideClient.NEAR_RANGE) {
            return;
        }
        // 近づくと薄れる (着いたら消える)
        float arrive = (float) Mth.clamp((goalDist - 3.5) / 8.0, 0.0, 1.0);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        double time = (level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean side = GuideClient.goalIsSide();
        Painter p = new Painter(buffer, pose, cam, arrive, time, side ? TEAL : GOLD, side ? TEAL_PALE : PALE);
        GuideClient.Trail trail = GuideClient.trailFor(goal);
        if (trail != null) {
            drawTrail(p, trail.nodes(), eye);
        } else {
            drawStraight(p, level, eye, gx, gz, goalDist);
        }
        submit(buffer, false);
    }

    // 光海の迷路: 目的 (次の灯の台・苗床・出口) までの迷路の道のりを、床の一本の金の線で。道のりはサーバーが 1 秒ごとに送る (LampBandClient.route)。
    // 目的が変わればサーバーが道のりを引き直して送るので、線もすぐ変わる。水の中でも見える (水のあとに描く)
    private static boolean drawMaze(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        java.util.List<Vec3> route = jp.revati.skyabyss.client.titan.LampBandClient.route();
        if (route == null || route.isEmpty()) {
            return false;
        }
        Vec3 pos = mc.player.position();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        double time = (level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Painter p = new Painter(buffer, pose, cam, 1.0f, time, GOLD, PALE);
        // 最初の点だけ、いまの足もと (小数) から。あとはマスの真ん中
        java.util.List<double[]> pts = new ArrayList<>();
        double s = 0.0;
        double lx = pos.x;
        double lz = pos.z;
        double[][] ctrl = new double[route.size() + 1][];
        ctrl[0] = new double[] {pos.x, route.get(0).y, pos.z};
        for (int i = 1; i < ctrl.length; i++) {
            Vec3 v = route.get(i - 1);
            ctrl[i] = new double[] {v.x, v.y, v.z};
        }
        for (int i = 0; i + 1 < ctrl.length && s <= SHOW_LENGTH; i++) {
            double[] a = ctrl[i];
            double[] b = ctrl[i + 1];
            double[] p0 = ctrl[Math.max(0, i - 1)];
            double[] p3 = ctrl[Math.min(ctrl.length - 1, i + 2)];
            double len = Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[2] - b[2]) * (a[2] - b[2]));
            int n = Math.max(1, (int) Math.ceil(len / STEP));
            for (int k = 0; k < n; k++) {
                double t = (double) k / n;
                double x = cr(p0[0], a[0], b[0], p3[0], t);
                double z = cr(p0[2], a[2], b[2], p3[2], t);
                double y = Mth.lerp(smooth((t - 0.35) / 0.3), a[1], b[1]);
                s += Math.sqrt((x - lx) * (x - lx) + (z - lz) * (z - lz));
                lx = x;
                lz = z;
                pts.add(new double[] {x, y, z, s});
            }
        }
        p.ribbon(pts, 0.0);
        submit(buffer, true);
        return true;
    }

    // glow = 迷路 (暗い・霧の濃い場所): 足し合わせで光らせ、霧で消えないようにする
    private static void submit(BufferBuilder buffer, boolean glow) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.enableBlend();
        if (glow) {
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderFogStart(1.0e6f);
            RenderSystem.setShaderFogEnd(2.0e6f);
        } else {
            RenderSystem.defaultBlendFunc();
        }
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
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }


    // 道の点に沿って、プレイヤーに一番近い点から SHOW_LENGTH ブロック分。xz は滑らかな曲線、y は床の高さを線でつなぐ
    private static void drawTrail(Painter p, BlockPos[] nodes, Vec3 eye) {
        int from = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < nodes.length; i++) {
            double dx = nodes[i].getX() + 0.5 - eye.x;
            double dz = nodes[i].getZ() + 0.5 - eye.z;
            double d = dx * dx + dz * dz + Math.pow((nodes[i].getY() - eye.y) * 0.5, 2);
            if (d < bestD) {
                bestD = d;
                from = i;
            }
        }
        List<double[]> pts = new ArrayList<>();
        double s = 0.0;
        double[] last = null;
        for (int i = from; i + 1 < nodes.length && s <= SHOW_LENGTH; i++) {
            BlockPos a = nodes[i];
            BlockPos b = nodes[i + 1];
            BlockPos p0 = nodes[Math.max(0, i - 1)];
            BlockPos p3 = nodes[Math.min(nodes.length - 1, i + 2)];
            double len = Math.sqrt((double) (a.getX() - b.getX()) * (a.getX() - b.getX()) + (double) (a.getZ() - b.getZ()) * (a.getZ() - b.getZ()));
            int n = Math.max(1, (int) Math.ceil(len / STEP));
            for (int k = 0; k < n; k++) {
                double t = (double) k / n;
                double x = cr(p0.getX(), a.getX(), b.getX(), p3.getX(), t) + 0.5;
                double z = cr(p0.getZ(), a.getZ(), b.getZ(), p3.getZ(), t) + 0.5;
                double y = Mth.lerp(smooth((t - 0.35) / 0.3), a.getY(), b.getY());
                if (last != null) {
                    s += Math.sqrt((x - last[0]) * (x - last[0]) + (z - last[2]) * (z - last[2]));
                }
                last = new double[] {x, y, z, s};
                pts.add(last);
            }
        }
        p.ribbon(pts, 0.0);
    }

    private static double smooth(double x) {
        double c = Mth.clamp(x, 0.0, 1.0);
        return c * c * (3 - 2 * c);
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    // 道なし: 目印の向きへ、足もとの地面にそって帯
    private static void drawStraight(Painter p, ClientLevel level, Vec3 eye, double gx, double gz, double goalDist) {
        double fx = gx / goalDist;
        double fz = gz / goalDist;
        double reach = Math.min(DASH_LENGTH, goalDist - 2.0);
        double lastY = eye.y;
        List<double[]> pts = new ArrayList<>();
        for (double d = 0.8; d <= reach; d += 0.5) {
            double x = eye.x + fx * d;
            double z = eye.z + fz * d;
            double y = floorY(level, x, lastY, z);
            lastY = y;
            pts.add(new double[] {x, y, z, d});
        }
        p.ribbon(pts, DASH_LENGTH);
    }

    // (x, z) の床の高さ (足もとの高さの少し上から下へ探す。見つからなければ前の高さ)
    private static double floorY(ClientLevel level, double x, double refY, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(refY) + 2;
        for (int y = top; y >= top - 8; y--) {
            pos.set(Mth.floor(x), y, Mth.floor(z));
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return y + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return refY;
    }

    // ---- 描き手 ----

    static final class Painter {
        private final BufferBuilder buf;
        private final Matrix4f pose;
        private final Vec3 cam;
        private final float base;
        private final double time;
        private final int color;
        private final int pale;

        Painter(BufferBuilder buf, Matrix4f pose, Vec3 cam, float base, double time, int color, int pale) {
            this.buf = buf;
            this.pose = pose;
            this.cam = cam;
            this.base = base;
            this.time = time;
            this.color = color;
            this.pale = pale;
        }

        // 光の波が目的の方へ流れる (距離に対する波)
        private float wave(double dist) {
            return 0.5f + 0.5f * (float) Math.sin((dist / 6.0 - time * 1.2) * Math.PI * 2.0);
        }

        // 手前ほど濃く、遠いほど薄れる。足もとのごく近くは立ち上がる
        private float alpha(double dist, double len) {
            double span = len > 0 ? len : SHOW_LENGTH;
            float fade = (float) Mth.clamp(1.0 - dist / span, 0.0, 1.0);
            float rise = (float) Mth.clamp(dist / 0.9, 0.0, 1.0);
            return base * fade * rise * (0.62f + 0.38f * wave(dist));
        }

        // 点 (x, y, z, 距離) の列を、床の上の一本の帯にする。len > 0 なら、その長さで消える (道なし)
        void ribbon(List<double[]> pts, double len) {
            int n = pts.size();
            if (n < 2) {
                return;
            }
            double[][] nx = new double[n][];
            double[] lift = new double[n];
            for (int i = 0; i < n; i++) {
                double[] a = pts.get(Math.max(0, i - 1));
                double[] b = pts.get(Math.min(n - 1, i + 1));
                double tx = b[0] - a[0];
                double tz = b[2] - a[2];
                double tl = Math.sqrt(tx * tx + tz * tz);
                if (tl < 1.0e-6) {
                    tx = 0;
                    tz = 1;
                    tl = 1;
                }
                nx[i] = new double[] {tz / tl, -tx / tl};
                double slope = Math.abs(b[1] - a[1]) / Math.max(0.2, tl);
                lift[i] = 0.03 + Math.min(0.14, slope * 0.12);
            }
            for (int i = 0; i + 1 < n; i++) {
                double[] p = pts.get(i);
                double[] q = pts.get(i + 1);
                float a0 = alpha(p[3], len);
                float a1 = alpha(q[3], len);
                if (a0 <= 0.003f && a1 <= 0.003f) {
                    continue;
                }
                // 暗い縁取り (明るい床でも見える)
                vtx(p, nx[i], EDGE, lift[i], 0x1A1408, a0 * 0.34f);
                vtx(p, nx[i], -EDGE, lift[i], 0x1A1408, a0 * 0.34f);
                vtx(q, nx[i + 1], -EDGE, lift[i + 1], 0x1A1408, a1 * 0.34f);
                vtx(q, nx[i + 1], EDGE, lift[i + 1], 0x1A1408, a1 * 0.34f);
                // 光る帯: 中心が明るく縁が溶ける
                int c0 = mix(color, pale, wave(p[3]) * 0.75f);
                int c1 = mix(color, pale, wave(q[3]) * 0.75f);
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    vtx(p, nx[i], sgn * CORE, lift[i] + 0.004, c0, a0 * 0.1f);
                    vtx(p, nx[i], 0, lift[i] + 0.004, c0, a0 * 0.95f);
                    vtx(q, nx[i + 1], 0, lift[i + 1] + 0.004, c1, a1 * 0.95f);
                    vtx(q, nx[i + 1], sgn * CORE, lift[i + 1] + 0.004, c1, a1 * 0.1f);
                }
            }
        }

        private void vtx(double[] c, double[] n, double w, double lift, int rgb, float alpha) {
            int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
            buf.addVertex(pose, (float) (c[0] + n[0] * w - cam.x), (float) (c[1] + lift - cam.y), (float) (c[2] + n[1] * w - cam.z))
                    .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }
}
