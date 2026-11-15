package jp.revati.skyabyss.client.fx;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.domain.DomainClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 領域の空 (2026-11-15 の声「心臓の上に出てくる背景で周りの見た目がおおわれるようにして」)。
// 宣言の後半、心臓の上に開いた裂け目 (RiftFx) の向こうの空が、裂け目から広がって部屋の壁と天井を覆う。光る前線が走り、
// 通ったあとは巨神の内側の空 (RiftFx.theme の層) になる。床は覆わない (予告の印が読めるように)。
// 領域が続くあいだ覆ったまま。砕かれたらすぐ、時間切れならゆっくり薄れて部屋が戻る。
// 形: 始まりに部屋の中心から 40 の向きへ壁までの距離と天井の高さを測り、その少し内側に壁の帯と天井の面を張る
// (壁より手前にあるので、心臓・人・予告は前に見え、壁と天井だけが空に替わる)。
// 描く段は半透明のブロックのあと。空は奥行きを書かないので、手前の生き物・心臓は奥行きで前に残り、粒・予告はこのあとに描かれて空の上に出る。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DomainSkyFx {
    private DomainSkyFx() {
    }

    private static final int DIRS = 40;
    private static final int ROWS = 6;

    private static final class Sky {
        RiftFx.Theme theme;
        long seed;
        double start;
        int declare;
        double cx;
        double cz;
        double floor;
        double ceiling;
        double[] wall = new double[DIRS];
        Vec3 origin;
        double reach;
        // 終わり: 0 = 続いている、1 = 時間切れ、2 = 砕かれた
        int ended;
        double endedAt;
    }

    private static Sky sky;

    static void clear() {
        sky = null;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        sky = null;
    }

    // 領域の宣言の始まり (RiftFx.domain から)。origin = 裂け目の中心
    static void start(String themeId, double cx, double cz, double floor, double radius, int declare, Vec3 origin, double now) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Sky s = new Sky();
        s.theme = RiftFx.theme(themeId);
        s.seed = themeId.hashCode();
        s.start = now;
        s.declare = declare;
        s.cx = cx;
        s.cz = cz;
        s.floor = floor;
        s.origin = origin;
        // 壁: 床から 1.5 と 4 の高さで外へ歩き、先に当たった所。当たらなければ広い所 (半径の 1.6 倍の筒)
        // 部屋の隅まで届くよう遠くまで測る (四角い部屋の隅は中心から半径の 1.4 倍以上ある)
        double far = 80;
        double open = Math.max(10, radius * 1.6);
        for (int i = 0; i < DIRS; i++) {
            double a = Math.PI * 2 * i / DIRS;
            double best = Double.MAX_VALUE;
            for (double hy : new double[] {1.5, 4.0}) {
                for (double d = 2; d < far; d += 0.5) {
                    BlockPos p = BlockPos.containing(cx + Math.cos(a) * d, floor + hy, cz + Math.sin(a) * d);
                    if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                        // 半ブロック刻みで見つけた所から細かく戻り、壁の面を正しく取る (面より奥に空を張ると壁に隠れる)
                        double surf = d;
                        while (surf > d - 0.5) {
                            double back = surf - 0.05;
                            BlockPos q = BlockPos.containing(cx + Math.cos(a) * back, floor + hy, cz + Math.sin(a) * back);
                            if (level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                                break;
                            }
                            surf = back;
                        }
                        best = Math.min(best, surf - 0.05);
                        break;
                    }
                }
            }
            // 壁が見つからない向き (開けた所) は広い筒
            s.wall[i] = best == Double.MAX_VALUE ? open : Math.max(3, best - 0.3);
        }
        // 天井: 中心と、壁の半ばの 8 点から上へ。いちばん低い所。当たらなければ壁の広さに見合う高さ
        double ceil = Double.MAX_VALUE;
        for (int k = -1; k < 8; k++) {
            double x = cx;
            double z = cz;
            if (k >= 0) {
                int i = k * DIRS / 8;
                double a = Math.PI * 2 * i / DIRS;
                x += Math.cos(a) * s.wall[i] * 0.5;
                z += Math.sin(a) * s.wall[i] * 0.5;
            }
            for (int y = (int) Math.floor(floor + 2); y < floor + 48; y++) {
                BlockPos p = BlockPos.containing(x, y, z);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    ceil = Math.min(ceil, y);
                    break;
                }
            }
        }
        double avgWall = 0;
        for (double w : s.wall) {
            avgWall += w / DIRS;
        }
        s.ceiling = ceil == Double.MAX_VALUE ? floor + Math.max(14, avgWall * 0.9) : ceil - 0.2;
        // 前線が届く遠さ (裂け目からいちばん遠い隅まで)
        double reach = 0;
        for (int i = 0; i < DIRS; i++) {
            double a = Math.PI * 2 * i / DIRS;
            Vec3 lo = new Vec3(cx + Math.cos(a) * s.wall[i], floor, cz + Math.sin(a) * s.wall[i]);
            reach = Math.max(reach, lo.distanceTo(origin));
        }
        s.reach = reach + 2;
        sky = s;
    }

    // 0..1: 空がどこまで広がったか (宣言の 55% から終わりの 10 tick あとまで)
    private static double spread(Sky s, double t) {
        double from = s.declare * 0.55;
        double to = s.declare + 10;
        double k = Mth.clamp((t - s.start - from) / (to - from), 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static float fade(Sky s, double t) {
        if (s.ended == 0) {
            return 1f;
        }
        double len = s.ended == 2 ? 14 : 50;
        return (float) Mth.clamp(1 - (t - s.endedAt) / len, 0, 1);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || sky == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Sky s = sky;
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 領域の終わりを見張る (通信が途切れても、領域が消えたら引く)
        int state = DomainClient.state();
        if (s.ended == 0 && t - s.start > 4 && state != 0) {
            s.ended = state == 2 ? 2 : 1;
            s.endedAt = t;
        }
        float f = fade(s, t);
        if (f <= 0.001f) {
            if (s.ended != 0) {
                sky = null;
            }
            return;
        }
        double spread = spread(s, t);
        if (spread <= 0.001) {
            return;
        }
        double front = spread * s.reach;
        // カメラが空の外 (壁の外・天井の上) にいるときは描かない (外から見ると部屋を覆い隠してしまう)
        Vec3 camPos = event.getCamera().getPosition();
        double ca = Math.atan2(camPos.z - s.cz, camPos.x - s.cx);
        int ci = Math.floorMod((int) Math.round(ca / (Math.PI * 2) * DIRS), DIRS);
        if (Math.hypot(camPos.x - s.cx, camPos.z - s.cz) > s.wall[ci] - 0.2 || camPos.y > s.ceiling || camPos.y < s.floor - 2) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        RiftFx.Theme th = s.theme;
        // 1. 奥の闇 (通常の重ね)
        BufferBuilder b = LightDraw.begin();
        shell(b, m, cam, s, front, 0, 0, 0, th.base(), 0.96f * f, false);
        LightDraw.endAlpha(b, LightDraw.BAND, true);
        // 2. 内側の空の層 (足し合わせ。壁の帯は角度と高さで、天井は位置で貼る)
        for (RiftFx.Layer L : th.layers()) {
            b = LightDraw.begin();
            // 壁は遠いので、裂け目の中より細かく貼る (星が大きな粒に見えないように)
            shell(b, m, cam, s, front, L.scale() * 1.7, t * L.du(), t * L.dv(), L.rgb(), L.alpha() * f, true);
            LightDraw.end(b, L.tex(), true);
        }
        // 3. 広がる前線 (光る帯)
        if (spread < 0.999) {
            b = LightDraw.begin();
            frontGlow(b, m, cam, s, front, th.glow(), 0.45f * f);
            LightDraw.end(b, LightDraw.BAND, true);
        }
    }

    private static Vec3 wallPoint(Sky s, int i, double y) {
        double a = Math.PI * 2 * (i % DIRS) / DIRS;
        return new Vec3(s.cx + Math.cos(a) * s.wall[i % DIRS], y, s.cz + Math.sin(a) * s.wall[i % DIRS]);
    }

    // 前線より裂け目に近い所だけ (前線のすぐ内側は少し薄く)
    private static float reveal(Sky s, Vec3 p, double front) {
        double d = p.distanceTo(s.origin);
        return (float) Mth.clamp((front - d) / 2.5, 0, 1);
    }

    // 壁の帯と天井の面。layered = 層の絵の座標を付ける (false なら単色)
    private static void shell(BufferBuilder b, Matrix4f m, Vec3 cam, Sky s, double front, double scale, double ou, double ov, int rgb, float a, boolean layered) {
        double lo = s.floor + 0.04;
        double hi = s.ceiling;
        // 壁
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < ROWS; r++) {
                double y0 = lo + (hi - lo) * r / ROWS;
                double y1 = lo + (hi - lo) * (r + 1) / ROWS;
                Vec3 p00 = wallPoint(s, i, y0);
                Vec3 p10 = wallPoint(s, i + 1, y0);
                Vec3 p11 = wallPoint(s, i + 1, y1);
                Vec3 p01 = wallPoint(s, i, y1);
                double u0 = 0.5;
                double u1 = 0.5;
                double v0 = 0.5;
                double v1 = 0.5;
                if (layered) {
                    // 角度 x 平均の半径で横に貼る (どの壁でも同じ密度)
                    double circ = 0;
                    for (double w : s.wall) {
                        circ += w;
                    }
                    double unit = Math.PI * 2 * circ / DIRS / DIRS;
                    u0 = (i * unit) / scale + ou;
                    u1 = ((i + 1) * unit) / scale + ou;
                    v0 = -(y0 - lo) / scale + ov;
                    v1 = -(y1 - lo) / scale + ov;
                }
                vtx(b, m, cam, p00, u0, v0, rgb, a * reveal(s, p00, front));
                vtx(b, m, cam, p10, u1, v0, rgb, a * reveal(s, p10, front));
                vtx(b, m, cam, p11, u1, v1, rgb, a * reveal(s, p11, front));
                vtx(b, m, cam, p01, u0, v1, rgb, a * reveal(s, p01, front));
            }
        }
        // 天井 (中心から壁の縁への扇を 3 段の輪に分ける。広がりの濃さがなめらかに乗るように)
        Vec3 c = new Vec3(s.cx, hi, s.cz);
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < 3; r++) {
                double k0 = r / 3.0;
                double k1 = (r + 1) / 3.0;
                Vec3 a0 = lerp(c, wallPoint(s, i, hi), k0);
                Vec3 b0 = lerp(c, wallPoint(s, i + 1, hi), k0);
                Vec3 a1 = lerp(c, wallPoint(s, i, hi), k1);
                Vec3 b1 = lerp(c, wallPoint(s, i + 1, hi), k1);
                vtx(b, m, cam, a0, uv(a0.x, scale, ou, layered), uv(a0.z, scale, ov, layered), rgb, a * reveal(s, a0, front));
                vtx(b, m, cam, b0, uv(b0.x, scale, ou, layered), uv(b0.z, scale, ov, layered), rgb, a * reveal(s, b0, front));
                vtx(b, m, cam, b1, uv(b1.x, scale, ou, layered), uv(b1.z, scale, ov, layered), rgb, a * reveal(s, b1, front));
                vtx(b, m, cam, a1, uv(a1.x, scale, ou, layered), uv(a1.z, scale, ov, layered), rgb, a * reveal(s, a1, front));
            }
        }
    }

    // 前線: 壁と天井の上で、裂け目からの距離が front の所に細く光る
    private static void frontGlow(BufferBuilder b, Matrix4f m, Vec3 cam, Sky s, double front, int rgb, float a) {
        double lo = s.floor + 0.04;
        double hi = s.ceiling;
        int rows = ROWS * 3;
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < rows; r++) {
                double y0 = lo + (hi - lo) * r / rows;
                double y1 = lo + (hi - lo) * (r + 1) / rows;
                Vec3 p00 = wallPoint(s, i, y0);
                Vec3 p10 = wallPoint(s, i + 1, y0);
                Vec3 p11 = wallPoint(s, i + 1, y1);
                Vec3 p01 = wallPoint(s, i, y1);
                float g00 = band(s, p00, front);
                float g10 = band(s, p10, front);
                float g11 = band(s, p11, front);
                float g01 = band(s, p01, front);
                if (g00 + g10 + g11 + g01 < 0.01f) {
                    continue;
                }
                vtx(b, m, cam, p00, 0.5, 0.5, rgb, a * g00);
                vtx(b, m, cam, p10, 0.5, 0.5, rgb, a * g10);
                vtx(b, m, cam, p11, 0.5, 0.5, rgb, a * g11);
                vtx(b, m, cam, p01, 0.5, 0.5, rgb, a * g01);
            }
        }
        Vec3 c = new Vec3(s.cx, hi, s.cz);
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < 8; r++) {
                Vec3 a0 = lerp(c, wallPoint(s, i, hi), r / 8.0);
                Vec3 b0 = lerp(c, wallPoint(s, i + 1, hi), r / 8.0);
                Vec3 a1 = lerp(c, wallPoint(s, i, hi), (r + 1) / 8.0);
                Vec3 b1 = lerp(c, wallPoint(s, i + 1, hi), (r + 1) / 8.0);
                float ga0 = band(s, a0, front);
                float gb0 = band(s, b0, front);
                float gb1 = band(s, b1, front);
                float ga1 = band(s, a1, front);
                if (ga0 + gb0 + gb1 + ga1 < 0.01f) {
                    continue;
                }
                vtx(b, m, cam, a0, 0.5, 0.5, rgb, a * ga0);
                vtx(b, m, cam, b0, 0.5, 0.5, rgb, a * gb0);
                vtx(b, m, cam, b1, 0.5, 0.5, rgb, a * gb1);
                vtx(b, m, cam, a1, 0.5, 0.5, rgb, a * ga1);
            }
        }
    }

    private static float band(Sky s, Vec3 p, double front) {
        double d = p.distanceTo(s.origin) - front;
        return (float) Math.exp(-(d * d) / (2 * 0.6 * 0.6));
    }

    private static double uv(double v, double scale, double o, boolean layered) {
        return layered ? v / scale + o : 0.5;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return a.add(b.subtract(a).scale(k));
    }

    private static void vtx(BufferBuilder b, Matrix4f m, Vec3 cam, Vec3 p, double u, double v, int rgb, float a) {
        LightDraw.v(b, m, p.x - cam.x, p.y - cam.y, p.z - cam.z, (float) u, (float) v, rgb, a);
    }
}
