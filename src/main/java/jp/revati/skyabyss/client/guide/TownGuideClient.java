package jp.revati.skyabyss.client.guide;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.TownGuide;
import jp.revati.skyabyss.facility.TownGuideNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 都の案内のクライアント側 (facility/TownGuide。docs/design/35 の 6.12・18 の 8.5)。
//  - 案内人の一覧 (Menu) が届いたら TownGuideScreen を開く。選んだら Pick を送る
//  - 道 (Path) が届いたら、道にそって 3 ブロックごとに色つきの小さな灯り (施設の種類の色) を地面に置く。見えるのはこの人だけ
//  - 足もとから先 56 ブロックまでを描く。手前ほど明るく、光の波が戸の方へ流れる。戸の前の灯りは一回り大きい
//  - 消えるとき: 戸に着いた (3.5 ブロック) / 5 分たった / 別の施設を選んだ / 都から離れた (戸から 260) / 世界を移った。消えるときはゆっくり薄れる
//  - 目的の帯の一行「〜へ向かおう」と距離・画面の目印 (ObjectiveStrip.resolve) は、案内のあいだ戸を指す
// 描画は灯りの数 (多くて 20 ほど) x 小さな箱と光の輪だけ。道しるべの帯 (GuideTrailRenderer) より軽い
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TownGuideClient {
    static final double SHOW = 56.0;
    static final double ARRIVE = 3.5;
    static final double LEAVE = 260.0;
    static final long TIMEOUT_MS = 5 * 60_000L;
    static final long FADE_IN_MS = 700L;
    static final long FADE_OUT_MS = 1400L;

    record Route(String id, TownGuide.Group group, BlockPos door, boolean reached, List<TownGuide.Lantern> lanterns, long at) {
    }

    @Nullable
    private static volatile Route route;
    // 消えかけの道 (薄れながら描く)
    @Nullable
    private static volatile Route fading;
    private static long fadingAt;
    private static int nearest;

    private TownGuideClient() {
    }

    // ---------------------------------------------------------------- 受け口
    public static void menu(TownGuideNetwork.Menu menu) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new jp.revati.skyabyss.client.facility.TownGuideScreen(menu));
    }

    public static void path(TownGuideNetwork.Path p) {
        if (p.id().isEmpty()) {
            clear(false);
            return;
        }
        Route old = route;
        if (old != null) {
            fading = old;
            fadingAt = Util.getMillis();
        }
        route = new Route(p.id(), TownGuide.Group.of(p.group()), p.door(), p.reached(), TownGuide.lanterns(p.nodes()), Util.getMillis());
        nearest = 0;
    }

    // 一覧で選んだ (空 = やめる)
    public static void pick(String id) {
        PacketDistributor.sendToServer(new TownGuideNetwork.Pick(id));
        if (id.isEmpty()) {
            clear(false);
        }
    }

    public static boolean active() {
        return route != null;
    }

    @Nullable
    public static String currentId() {
        Route r = route;
        return r == null ? null : r.id;
    }

    @Nullable
    public static BlockPos door() {
        Route r = route;
        return r == null ? null : r.door;
    }

    // 帯の一行 (案内のあいだだけ)
    @Nullable
    public static String objective() {
        Route r = route;
        if (r == null) {
            return null;
        }
        return Component.translatable("objective.skyabyss.town_guide", Component.translatable("gui.skyabyss.town_guide.place." + r.id + ".to")).getString();
    }

    static void clear(boolean arrived) {
        Route r = route;
        if (r == null) {
            return;
        }
        fading = r;
        fadingAt = Util.getMillis();
        route = null;
        Minecraft mc = Minecraft.getInstance();
        if (arrived && mc.player != null) {
            mc.gui.setOverlayMessage(Component.translatable("message.skyabyss.town_guide.arrived",
                    Component.translatable("gui.skyabyss.town_guide.place." + r.id + ".to")), false);
        }
    }

    // ---------------------------------------------------------------- 毎 tick: 着いた・時間切れ・離れた
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Route r = route;
        if (r == null) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            route = null;
            fading = null;
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            clear(false);
            return;
        }
        double dx = r.door.getX() + 0.5 - mc.player.getX();
        double dz = r.door.getZ() + 0.5 - mc.player.getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 <= ARRIVE * ARRIVE && Math.abs(r.door.getY() - mc.player.getY()) < 4.5) {
            clear(true);
        } else if (d2 > LEAVE * LEAVE || Util.getMillis() - r.at > TIMEOUT_MS) {
            clear(false);
        }
    }

    // ---------------------------------------------------------------- 描画
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = Util.getMillis();
        Route r = route;
        Route f = fading;
        if (f != null && now - fadingAt > FADE_OUT_MS) {
            fading = null;
            f = null;
        }
        if (r == null && f == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        double time = (mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0;
        Vec3 me = mc.player.position();
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int[] count = {0};
        if (r != null) {
            float in = Mth.clamp((now - r.at) / (float) FADE_IN_MS, 0f, 1f);
            nearest = nearestIndex(r.lanterns, me, nearest);
            draw(glow, pose, cam, r, nearest, in, time, now - r.at, count);
        }
        if (f != null) {
            float out = 1f - Mth.clamp((now - fadingAt) / (float) FADE_OUT_MS, 0f, 1f);
            draw(glow, pose, cam, f, nearestIndex(f.lanterns, me, 0), out, time, Long.MAX_VALUE, count);
        }
        MeshData mesh = glow.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
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
    }

    // いちばん近い灯り (前の答えのまわりから探す。道は一本なので、だいたい前の近く)
    static int nearestIndex(List<TownGuide.Lantern> ls, Vec3 me, int hint) {
        if (ls.isEmpty()) {
            return 0;
        }
        int best = Mth.clamp(hint, 0, ls.size() - 1);
        double bestD = Double.MAX_VALUE;
        int from = Math.max(0, best - 24);
        int to = Math.min(ls.size(), best + 24);
        if (hint <= 0) {
            from = 0;
            to = ls.size();
        }
        for (int i = from; i < to; i++) {
            TownGuide.Lantern l = ls.get(i);
            double d = (l.x() - me.x) * (l.x() - me.x) + (l.z() - me.z) * (l.z() - me.z) + (l.y() - me.y) * (l.y() - me.y) * 0.5;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        // 遠く離れた (道から外れて大回りした) ときは全部から探し直す
        if (bestD > 24 * 24 && hint > 0) {
            return nearestIndex(ls, me, 0);
        }
        return best;
    }

    private static void draw(BufferBuilder buf, Matrix4f pose, Vec3 cam, Route r, int from, float base, double time, long age, int[] count) {
        List<TownGuide.Lantern> ls = r.lanterns;
        if (ls.isEmpty() || base <= 0.01f) {
            return;
        }
        int rgb = r.group.rgb;
        int pale = mix(rgb, 0xFFFFFF, 0.55f);
        double s0 = ls.get(from).s();
        for (int i = Math.max(0, from - 1); i < ls.size(); i++) {
            TownGuide.Lantern l = ls.get(i);
            double ahead = l.s() - s0;
            if (ahead > SHOW) {
                break;
            }
            boolean last = i == ls.size() - 1;
            // 選んだすぐあとは、足もとから戸の方へ順に灯る (0.9 秒で 56 ブロック)
            double lit = age / 900.0 * SHOW - ahead;
            float on = (float) Mth.clamp(lit / 4.0, 0.0, 1.0);
            float fade = (float) Mth.clamp(1.0 - Math.max(0, ahead) / SHOW, 0.0, 1.0);
            float wave = 0.5f + 0.5f * (float) Math.sin((l.s() / 9.0 - time * 0.9) * Math.PI * 2.0);
            float flicker = 0.92f + 0.08f * (float) Math.sin(time * 11.0 + i * 1.7);
            float a = base * on * (0.35f + 0.65f * fade) * flicker;
            if (a <= 0.01f) {
                continue;
            }
            double k = last ? 1.5 : 1.0;
            // 床の光の輪 (中が明るく、縁が溶ける)
            ring(buf, pose, cam, l, 0.95 * k, mix(rgb, pale, wave * 0.5f), a * (0.30f + 0.18f * wave));
            // 灯りの台と火袋 (小さな箱)。火袋は種類の色、笠と台は暗い鉄
            double h = 0.06;
            box(buf, pose, cam, l.x(), l.y() + h, l.z(), 0.15 * k, 0.04 * k, 0x2A241C, a);
            box(buf, pose, cam, l.x(), l.y() + h + 0.04 * k, l.z(), 0.11 * k, 0.26 * k, mix(rgb, pale, 0.1f + 0.25f * wave), a * 0.95f);
            box(buf, pose, cam, l.x(), l.y() + h + 0.30 * k, l.z(), 0.14 * k, 0.05 * k, 0x2A241C, a);
            // 上に淡い光の玉 (遠くからも見える)
            halo(buf, pose, cam, l.x(), l.y() + h + 0.17 * k, l.z(), 0.38 * k, pale, a * 0.28f * (0.6f + 0.4f * wave));
            count[0]++;
        }
    }

    // 床の輪: 中心から 8 つの扇 (中心が濃く、縁は 0)
    private static void ring(BufferBuilder buf, Matrix4f pose, Vec3 cam, TownGuide.Lantern l, double r, int rgb, float a) {
        float y = (float) (l.y() + 0.02 - cam.y);
        float cx = (float) (l.x() - cam.x);
        float cz = (float) (l.z() - cam.z);
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n;
            double a1 = Math.PI * 2 * (i + 1) / n;
            vtx(buf, pose, cx, y, cz, rgb, a);
            vtx(buf, pose, cx, y, cz, rgb, a);
            vtx(buf, pose, cx + (float) (Math.cos(a1) * r), y, cz + (float) (Math.sin(a1) * r), rgb, 0f);
            vtx(buf, pose, cx + (float) (Math.cos(a0) * r), y, cz + (float) (Math.sin(a0) * r), rgb, 0f);
        }
    }

    // 箱 (底の中心 x, y, z、半分の幅 hw、高さ h)。上と四方の面だけ (底は床に隠れる)
    private static void box(BufferBuilder buf, Matrix4f pose, Vec3 cam, double x, double y, double z, double hw, double h, int rgb, float a) {
        float x0 = (float) (x - hw - cam.x);
        float x1 = (float) (x + hw - cam.x);
        float y0 = (float) (y - cam.y);
        float y1 = (float) (y + h - cam.y);
        float z0 = (float) (z - hw - cam.z);
        float z1 = (float) (z + hw - cam.z);
        int side = mix(rgb, 0x000000, 0.18f);
        vtx(buf, pose, x0, y1, z0, rgb, a);
        vtx(buf, pose, x1, y1, z0, rgb, a);
        vtx(buf, pose, x1, y1, z1, rgb, a);
        vtx(buf, pose, x0, y1, z1, rgb, a);
        float[][] faces = {{x0, z0, x1, z0}, {x1, z0, x1, z1}, {x1, z1, x0, z1}, {x0, z1, x0, z0}};
        for (float[] f : faces) {
            vtx(buf, pose, f[0], y0, f[1], side, a);
            vtx(buf, pose, f[2], y0, f[3], side, a);
            vtx(buf, pose, f[2], y1, f[3], side, a);
            vtx(buf, pose, f[0], y1, f[1], side, a);
        }
    }

    // 光の玉: 互いに直交する 2 枚の板 (中心が濃く縁が 0。どの向きからも見える)
    private static void halo(BufferBuilder buf, Matrix4f pose, Vec3 cam, double x, double y, double z, double r, int rgb, float a) {
        float cx = (float) (x - cam.x);
        float cy = (float) (y - cam.y);
        float cz = (float) (z - cam.z);
        float rr = (float) r;
        for (int k = 0; k < 2; k++) {
            float ux = k == 0 ? rr : 0f;
            float uz = k == 0 ? 0f : rr;
            // 4 つの三角 (中心 → 上下左右)。QUADS なので中心を 2 回
            float[][] pts = {{ux, 0, uz}, {0, rr, 0}, {-ux, 0, -uz}, {0, -rr, 0}};
            for (int i = 0; i < 4; i++) {
                float[] p = pts[i];
                float[] q = pts[(i + 1) % 4];
                vtx(buf, pose, cx, cy, cz, rgb, a);
                vtx(buf, pose, cx, cy, cz, rgb, a);
                vtx(buf, pose, cx + q[0], cy + q[1], cz + q[2], rgb, 0f);
                vtx(buf, pose, cx + p[0], cy + p[1], cz + p[2], rgb, 0f);
            }
        }
    }

    private static void vtx(BufferBuilder buf, Matrix4f pose, float x, float y, float z, int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        buf.addVertex(pose, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    // 開発用 (自己テスト・台本): いまの灯りの数
    public static int lanternCount() {
        Route r = route;
        return r == null ? 0 : r.lanterns.size();
    }
}
