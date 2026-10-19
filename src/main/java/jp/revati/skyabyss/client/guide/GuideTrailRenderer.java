package jp.revati.skyabyss.client.guide;

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

// 地面の道しるべ。サーバーが探した歩ける道 (GuideClient.Trail) の上に、柔らかい金色のひし形の足あと (左右にずらして並べる) を置き、
// 5〜6 歩ごとに目的の向きを指す山形 (›) を置く。手前ほど濃く、約 40 ブロック先で消え、目的の方へ光が流れる。
// 道なしのときは、地面にまっすぐの破線 (16 ブロック)。床の高さは道の点の高さ (破線は上から床を探す)。
// 形 (ひし形・山形・破線) で読めるので、色が見分けにくくても伝わる。技の予告 (橙・赤・青白) とは色も形も違う。
// 描かない場面: 場面・画面・F1・オーバーワールド以外・目的がないとき・設定 OFF (GuideClient.visible)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GuideTrailRenderer {
    static final double SHOW_LENGTH = 40.0;
    static final double SPACING = 1.5;
    static final double DASH_LENGTH = 16.0;
    private static final int GOLD = 0xF2C14E;
    private static final int PALE = 0xFFE9A8;
    private static final int MARK_EVERY = 5;

    private GuideTrailRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
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
        Painter p = new Painter(buffer, pose, cam, arrive, time);
        GuideClient.Trail trail = GuideClient.trailFor(goal);
        if (trail != null) {
            drawTrail(p, trail.nodes(), eye);
        } else {
            drawDashes(p, level, eye, gx, gz, goalDist);
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

    // 道の点に沿って、プレイヤーに一番近い点から SHOW_LENGTH ブロック分
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
        double s = 0.0;
        double carry = SPACING * 0.5;
        int count = 0;
        boolean left = true;
        for (int i = from; i + 1 < nodes.length && s < SHOW_LENGTH; i++) {
            BlockPos a = nodes[i];
            BlockPos b = nodes[i + 1];
            double ax = a.getX() + 0.5;
            double az = a.getZ() + 0.5;
            double sx = b.getX() + 0.5 - ax;
            double sz = b.getZ() + 0.5 - az;
            double len = Math.sqrt(sx * sx + sz * sz);
            if (len < 1.0e-6) {
                continue;
            }
            double fx = sx / len;
            double fz = sz / len;
            while (carry <= len) {
                double t = carry / len;
                // 床の高さ: 段の境 (中間) で切り替わる
                double y = (t < 0.5 ? a.getY() : b.getY()) + 0.03;
                double x = ax + sx * t;
                double z = az + sz * t;
                double dist = s + carry;
                if (dist > SHOW_LENGTH) {
                    return;
                }
                count++;
                if (count % MARK_EVERY == 0) {
                    p.chevron(x, y, z, fx, fz, dist);
                } else {
                    double side = (left ? 1 : -1) * 0.17;
                    p.diamond(x - fz * side, y, z + fx * side, fx, fz, dist);
                    left = !left;
                }
                carry += SPACING;
            }
            carry -= len;
            s += len;
        }
    }

    // 道なし: 目印の向きへ、足もとの地面にそって破線
    private static void drawDashes(Painter p, ClientLevel level, Vec3 eye, double gx, double gz, double goalDist) {
        double fx = gx / goalDist;
        double fz = gz / goalDist;
        double reach = Math.min(DASH_LENGTH, goalDist - 2.0);
        double lastY = eye.y;
        for (double d = 1.6; d + 0.5 <= reach; d += 1.0) {
            double x = eye.x + fx * (d + 0.25);
            double z = eye.z + fz * (d + 0.25);
            double y = floorY(level, x, lastY, z);
            lastY = y;
            p.dash(x, y + 0.03, z, fx, fz, d);
        }
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

        Painter(BufferBuilder buf, Matrix4f pose, Vec3 cam, float base, double time) {
            this.buf = buf;
            this.pose = pose;
            this.cam = cam;
            this.base = base;
            this.time = time;
        }

        // 手前ほど濃く、遠いほど薄れる。光が目的の方へ流れる (距離に対する波)
        private float alpha(double dist) {
            float fade = (float) Mth.clamp(1.0 - dist / SHOW_LENGTH, 0.0, 1.0);
            float wave = 0.5f + 0.5f * (float) Math.sin((dist / 7.0 - time * 1.1) * Math.PI * 2.0);
            return base * fade * (0.55f + 0.45f * wave);
        }

        private int rgb(double dist) {
            float wave = 0.5f + 0.5f * (float) Math.sin((dist / 7.0 - time * 1.1) * Math.PI * 2.0);
            return mix(GOLD, PALE, wave * 0.7f);
        }

        // ひし形の足あと (進行方向に長い)
        void diamond(double x, double y, double z, double fx, double fz, double dist) {
            float a = alpha(dist);
            // 暗い縁取り (明るい床でも見える)
            quad(x, y, z, fx, fz, 0.21, 0.15, 0x1A1408, a * 0.45f);
            quad(x, y + 0.004, z, fx, fz, 0.17, 0.11, rgb(dist), a * 0.9f);
        }

        // 目的の向きを指す山形 (›)
        void chevron(double x, double y, double z, double fx, double fz, double dist) {
            float a = alpha(dist);
            double rx = -fz;
            double rz = fx;
            // 左右の腕 2 本 (先端が前)
            for (int side = -1; side <= 1; side += 2) {
                double cx = x - fx * 0.12 + rx * side * 0.17;
                double cz = z - fz * 0.12 + rz * side * 0.17;
                // 腕の向き: 先端 (x + f*0.2) へ寄せる
                double ax = fx * 0.55 - rx * side * 0.55;
                double az = fz * 0.55 - rz * side * 0.55;
                double al = Math.sqrt(ax * ax + az * az);
                ax /= al;
                az /= al;
                quad(cx, y, cz, ax, az, 0.27, 0.075, 0x1A1408, a * 0.45f);
                quad(cx, y + 0.004, cz, ax, az, 0.24, 0.05, mix(PALE, GOLD, 0.2f), a);
            }
        }

        // 破線の一つ (細長い)
        void dash(double x, double y, double z, double fx, double fz, double dist) {
            float a = alpha(dist * 2.0) * 0.8f;
            quad(x, y, z, fx, fz, 0.27, 0.085, 0x1A1408, a * 0.4f);
            quad(x, y + 0.004, z, fx, fz, 0.25, 0.06, mix(GOLD, PALE, 0.25f), a);
        }

        // 中心 (x, y, z)、向き (fx, fz)、長い半径 l・短い半径 w の菱形。dash/chevron の腕は細長い菱形で代用
        private void quad(double x, double y, double z, double fx, double fz, double l, double w, int rgb, float alpha) {
            double rx = -fz;
            double rz = fx;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
            vertex(x + fx * l, y, z + fz * l, r, g, b, a);
            vertex(x + rx * w, y, z + rz * w, r, g, b, a);
            vertex(x - fx * l, y, z - fz * l, r, g, b, a);
            vertex(x - rx * w, y, z - rz * w, r, g, b, a);
        }

        private void vertex(double x, double y, double z, int r, int g, int b, int a) {
            buf.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, a);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }
}
