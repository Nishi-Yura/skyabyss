package jp.revati.skyabyss.client.fx;

import java.util.Arrays;

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
// 形: 始まりに部屋を測る。天井の面 = 向き x 輪の点ごとに上へ最初の固い所の下 (梁・肋が張り出していればその下に沿う)。
//     壁 = 64 の向き x 7 つの高さ (床からその向きの天井の縁まで) で、壁 (や張り出し) までの距離。高さごとに測るので、傾いた壁や上の張り出しにも沿う。
//     一本だけ近い柱 (同じ高さのほかの向きよりずっと近い) は壁と見なさず、柱は空の内側に残す。
// その少し内側に壁の面と天井の面を張る (壁より手前にあるので、心臓・人・予告は前に見え、壁と天井だけが空に替わる)。
// 描く段は半透明のブロックのあと。空は奥行きを書かないので、手前の生き物・心臓は奥行きで前に残り、粒・予告はこのあとに描かれて空の上に出る。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DomainSkyFx {
    private DomainSkyFx() {
    }

    private static final int DIRS = 64;
    // 天井の面の輪の数 (中心から壁の上の縁まで)
    private static final int RINGS = 10;
    // 壁の面の段 (高さ ROWS + 1 か所で測る)
    private static final int ROWS = 6;

    private static final class Sky {
        RiftFx.Theme theme;
        double start;
        int declare;
        double cx;
        double cz;
        double floor;
        // いちばん高い天井 (カメラが外にいるかの目安)
        double ceiling;
        // cap[向き][輪] = 天井の面の高さ (梁・肋の下)。輪 RINGS が壁の上の縁
        double[][] cap = new double[DIRS][RINGS + 1];
        // wall[向き][段]
        double[][] wall = new double[DIRS][ROWS + 1];
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

    private static boolean solid(ClientLevel level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    // 中心から向き a へ、高さ y で歩いて最初の固い面までの距離 (面を 0.05 刻みで正しく取る)。なければ -1
    private static double hit(ClientLevel level, double cx, double cz, double a, double y, double from, double far) {
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        for (double d = from; d < far; d += 0.5) {
            if (solid(level, cx + ca * d, y, cz + sa * d)) {
                double surf = d;
                while (surf > d - 0.5 && solid(level, cx + ca * (surf - 0.05), y, cz + sa * (surf - 0.05))) {
                    surf -= 0.05;
                }
                return surf - 0.05;
            }
        }
        return -1;
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
        s.start = now;
        s.declare = declare;
        s.cx = cx;
        s.cz = cz;
        s.floor = floor;
        s.origin = origin;
        double far = 80;
        double open = Math.max(10, radius * 1.6);
        // 1. 床の近くの壁 (部屋の広さの目安)
        double[] base = new double[DIRS];
        for (int i = 0; i < DIRS; i++) {
            double h = hit(level, cx, cz, Math.PI * 2 * i / DIRS, floor + 1.5, 2, far);
            base[i] = h < 0 ? open : h;
        }
        // 2. 天井の面: 中心から壁の手前まで、向き x 輪の点から上へ、最初の固い所の下。梁・肋が張り出していればその下に沿う。
        //    床から柱・壁に入っている点 (上へ歩き始めから固い) は分からないので、同じ輪の隣か一つ内の輪の値を使う。床の上 4 より下げない
        double avg = 0;
        for (double b : base) {
            avg += b / DIRS;
        }
        double fallback = floor + Math.max(14, avg * 0.9);
        // 1 ブロックおきの格子で、上へ最初の固い所 (始めから固い = 柱・壁の中は分からない NaN)
        double maxR = 0;
        for (double b : base) {
            maxR = Math.max(maxR, b);
        }
        int g = (int) Math.ceil(maxR) + 2;
        double[][] grid = new double[2 * g + 1][2 * g + 1];
        for (int gx = -g; gx <= g; gx++) {
            for (int gz = -g; gz <= g; gz++) {
                double x = cx + gx;
                double z = cz + gz;
                double v = Double.NaN;
                if (!solid(level, x, floor + 2.5, z)) {
                    v = fallback;
                    for (int y = (int) Math.floor(floor + 3); y < floor + 48; y++) {
                        if (solid(level, x, y + 0.5, z)) {
                            v = y - 0.2;
                            break;
                        }
                    }
                }
                grid[gx + g][gz + g] = v;
            }
        }
        // 天井の面の点は、まわり 2 ブロックの中のいちばん低い所 (細い梁・肋も面の下に入るように)
        double[][] cap = new double[DIRS][RINGS + 1];
        for (int i = 0; i < DIRS; i++) {
            double a = Math.PI * 2 * i / DIRS;
            for (int k = 0; k <= RINGS; k++) {
                double rr = base[i] * Math.min(0.97, (double) k / RINGS);
                double px = Math.cos(a) * rr;
                double pz = Math.sin(a) * rr;
                double v = Double.NaN;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dz * dz > 5) {
                            continue;
                        }
                        int gx = (int) Math.round(px) + dx + g;
                        int gz = (int) Math.round(pz) + dz + g;
                        if (gx < 0 || gz < 0 || gx > 2 * g || gz > 2 * g) {
                            continue;
                        }
                        double h = grid[gx][gz];
                        if (!Double.isNaN(h) && (Double.isNaN(v) || h < v)) {
                            v = h;
                        }
                    }
                }
                cap[i][k] = v;
            }
        }
        // 分からない点を埋める (同じ輪の隣 → 一つ内の輪 → 目安)
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < DIRS; i++) {
                for (int k = 0; k <= RINGS; k++) {
                    if (!Double.isNaN(cap[i][k])) {
                        continue;
                    }
                    double l = cap[Math.floorMod(i - 1, DIRS)][k];
                    double r = cap[(i + 1) % DIRS][k];
                    double in = k > 0 ? cap[i][k - 1] : Double.NaN;
                    cap[i][k] = !Double.isNaN(l) ? l : !Double.isNaN(r) ? r : !Double.isNaN(in) ? in : (pass == 2 ? fallback : Double.NaN);
                }
            }
        }
        // 中心は一つの点 (いちばん低い値にそろえる)
        double center = Double.MAX_VALUE;
        for (int i = 0; i < DIRS; i++) {
            center = Math.min(center, cap[i][0]);
        }
        double top = 0;
        for (int i = 0; i < DIRS; i++) {
            cap[i][0] = center;
            for (int k = 0; k <= RINGS; k++) {
                cap[i][k] = Math.max(floor + 4, cap[i][k]);
                top = Math.max(top, cap[i][k]);
            }
        }
        s.cap = cap;
        s.ceiling = top;
        // 3. 壁: 床から、その向きの天井の縁までを段に分けて、段ごとの高さで測る
        double lo = floor + 0.6;
        for (int r = 0; r <= ROWS; r++) {
            double[] row = new double[DIRS];
            for (int i = 0; i < DIRS; i++) {
                double hiI = cap[i][RINGS] - 0.4;
                double y = lo + (hiI - lo) * r / ROWS;
                double h = hit(level, cx, cz, Math.PI * 2 * i / DIRS, y, 1.5, far);
                row[i] = h < 0 ? open : h;
            }
            // 一本だけ近い柱は壁と見なさない (その段のなかほどの値の 0.6 倍より近ければ、両隣の遠い方へ)
            double[] sorted = row.clone();
            Arrays.sort(sorted);
            double median = sorted[DIRS / 2];
            for (int i = 0; i < DIRS; i++) {
                if (row[i] < median * 0.6) {
                    double left = row[Math.floorMod(i - 1, DIRS)];
                    double right = row[(i + 1) % DIRS];
                    row[i] = Math.max(row[i], Math.min(median, Math.max(left, right)));
                }
            }
            for (int i = 0; i < DIRS; i++) {
                s.wall[i][r] = Math.max(2.5, row[i] - 0.3);
            }
        }
        // 前線が届く遠さ (裂け目からいちばん遠い所まで)
        double reach = 0;
        for (int i = 0; i < DIRS; i++) {
            reach = Math.max(reach, wallPoint(s, i, 0).distanceTo(origin));
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
        Vec3 cam = event.getCamera().getPosition();
        // カメラが空の外 (壁の外・天井の上) にいるときは描かない (外から見ると部屋を覆い隠してしまう)
        if (outside(s, cam)) {
            return;
        }
        Matrix4f m = event.getPoseStack().last().pose();
        RiftFx.Theme th = s.theme;
        // 1. 奥の闇 (通常の重ね)
        BufferBuilder b = LightDraw.begin();
        shell(b, m, cam, s, front, 0, 0, 0, th.base(), 0.96f * f, false);
        LightDraw.endAlpha(b, LightDraw.BAND, true);
        // 2. 内側の空の層 (足し合わせ。壁は向きと高さで、天井は位置で貼る)
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

    private static boolean outside(Sky s, Vec3 cam) {
        if (cam.y > s.ceiling || cam.y < s.floor - 2) {
            return true;
        }
        double ca = Math.atan2(cam.z - s.cz, cam.x - s.cx);
        int ci = Math.floorMod((int) Math.round(ca / (Math.PI * 2) * DIRS), DIRS);
        double fr = Mth.clamp((cam.y - s.floor) / Math.max(1, s.cap[ci][RINGS] - s.floor) * ROWS, 0, ROWS);
        return Math.hypot(cam.x - s.cx, cam.z - s.cz) > radiusAt(s, ci, fr) - 0.2;
    }

    // 向き i、段 fr (0..ROWS の小数) の壁までの距離
    private static double radiusAt(Sky s, int i, double fr) {
        int k = Math.min(ROWS - 1, (int) Math.floor(fr));
        double u = fr - k;
        double[] w = s.wall[Math.floorMod(i, DIRS)];
        return w[k] + (w[k + 1] - w[k]) * u;
    }

    // 壁の面の点 (向き i、段 fr)。高さは床のすぐ上から天井まで
    private static Vec3 wallPoint(Sky s, int i, double fr) {
        double a = Math.PI * 2 * Math.floorMod(i, DIRS) / DIRS;
        double r = radiusAt(s, i, fr);
        double topI = s.cap[Math.floorMod(i, DIRS)][RINGS];
        double y = s.floor + 0.04 + (topI - s.floor - 0.04) * fr / ROWS;
        return new Vec3(s.cx + Math.cos(a) * r, y, s.cz + Math.sin(a) * r);
    }

    // 天井の面の点 (向き i、輪 k)
    private static Vec3 capPoint(Sky s, int i, int k) {
        int ii = Math.floorMod(i, DIRS);
        double a = Math.PI * 2 * ii / DIRS;
        double r = radiusAt(s, ii, ROWS) * k / RINGS;
        return new Vec3(s.cx + Math.cos(a) * r, s.cap[ii][k], s.cz + Math.sin(a) * r);
    }

    // 前線より裂け目に近い所だけ (前線のすぐ内側は少し薄く)
    private static float reveal(Sky s, Vec3 p, double front) {
        double d = p.distanceTo(s.origin);
        return (float) Mth.clamp((front - d) / 2.5, 0, 1);
    }

    // 壁の面と天井の面。layered = 層の絵の座標を付ける (false なら単色)
    private static void shell(BufferBuilder b, Matrix4f m, Vec3 cam, Sky s, double front, double scale, double ou, double ov, int rgb, float a, boolean layered) {
        // 横の貼り方の幅: 段ごとの平均の周りの長さ / 向きの数
        double avg = 0;
        for (double[] w : s.wall) {
            avg += w[ROWS / 2] / DIRS;
        }
        double unit = Math.PI * 2 * avg / DIRS;
        int sub = 2;
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < ROWS * sub; r++) {
                double f0 = (double) r / sub;
                double f1 = (double) (r + 1) / sub;
                Vec3 p00 = wallPoint(s, i, f0);
                Vec3 p10 = wallPoint(s, i + 1, f0);
                Vec3 p11 = wallPoint(s, i + 1, f1);
                Vec3 p01 = wallPoint(s, i, f1);
                double u0 = layered ? (i * unit) / scale + ou : 0.5;
                double u1 = layered ? ((i + 1) * unit) / scale + ou : 0.5;
                double v0 = layered ? -(p00.y - s.floor) / scale + ov : 0.5;
                double v1 = layered ? -(p01.y - s.floor) / scale + ov : 0.5;
                vtx(b, m, cam, p00, u0, v0, rgb, a * reveal(s, p00, front));
                vtx(b, m, cam, p10, u1, v0, rgb, a * reveal(s, p10, front));
                vtx(b, m, cam, p11, u1, v1, rgb, a * reveal(s, p11, front));
                vtx(b, m, cam, p01, u0, v1, rgb, a * reveal(s, p01, front));
            }
        }
        // 天井の面 (中心から壁の上の縁まで、梁・肋の下に沿う輪)
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < RINGS; r++) {
                Vec3 a0 = capPoint(s, i, r);
                Vec3 b0 = capPoint(s, i + 1, r);
                Vec3 a1 = capPoint(s, i, r + 1);
                Vec3 b1 = capPoint(s, i + 1, r + 1);
                vtx(b, m, cam, a0, uv(a0.x, scale, ou, layered), uv(a0.z, scale, ov, layered), rgb, a * reveal(s, a0, front));
                vtx(b, m, cam, b0, uv(b0.x, scale, ou, layered), uv(b0.z, scale, ov, layered), rgb, a * reveal(s, b0, front));
                vtx(b, m, cam, b1, uv(b1.x, scale, ou, layered), uv(b1.z, scale, ov, layered), rgb, a * reveal(s, b1, front));
                vtx(b, m, cam, a1, uv(a1.x, scale, ou, layered), uv(a1.z, scale, ov, layered), rgb, a * reveal(s, a1, front));
            }
        }
    }

    // 前線: 壁と天井の上で、裂け目からの距離が front の所に細く光る
    private static void frontGlow(BufferBuilder b, Matrix4f m, Vec3 cam, Sky s, double front, int rgb, float a) {
        int sub = 4;
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < ROWS * sub; r++) {
                double f0 = (double) r / sub;
                double f1 = (double) (r + 1) / sub;
                Vec3 p00 = wallPoint(s, i, f0);
                Vec3 p10 = wallPoint(s, i + 1, f0);
                Vec3 p11 = wallPoint(s, i + 1, f1);
                Vec3 p01 = wallPoint(s, i, f1);
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
        for (int i = 0; i < DIRS; i++) {
            for (int r = 0; r < RINGS; r++) {
                Vec3 a0 = capPoint(s, i, r);
                Vec3 b0 = capPoint(s, i + 1, r);
                Vec3 a1 = capPoint(s, i, r + 1);
                Vec3 b1 = capPoint(s, i + 1, r + 1);
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
