package jp.revati.skyabyss.client.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.PaperCutLayer;
import jp.revati.skyabyss.client.fx.LightDraw;
import jp.revati.skyabyss.combat.AttackLook;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 技の光 (2026-11-15 の光の表現の選定 4・5・8)。粒 (AttackFx) と床の印 (TelegraphRenderer) の上に、足し合わせの光を重ねる。
//   光線   (4): 運びが BEAM の技は、元から先まで芯の白い光の帯。振り回し (SWEEP) は元から今の先端への帯と、通った跡の光の弧
//   溜め   (4): 溜めが GATHER の技は、予告のあいだ光の筋が技の元へ吸い込まれ、元が明るくなっていく (溜めが目で読める)
//   当たり (5): 心臓・領域の技 (見た目つき) と、心臓のあとの主の技が当たった瞬間、床を走って広がる光の輪
//   軌道   (8): 月の主と塔の主は、予告のあいだ体のまわりを光の軌道が回り、放つ瞬間に外へ散る
// 判定・予告の長さには触れない。色は属性 (石・水・雷・熾) か主の見せ方の色。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AttackLightFx {
    private AttackLightFx() {
    }

    static int elementRgb(AttackLook.Element e) {
        return switch (e) {
            case WATER -> 0x7FD8FF;
            case STORM -> 0xB9A6FF;
            case EMBER -> 0xFF8A3C;
            default -> 0xBFE6CC;
        };
    }

    // 心臓のあとの主の色 (BossImpactFx と同じ言葉)。主でなければ -1
    static int bossRgb(ClientLevel level, int owner) {
        Entity e = owner >= 0 ? level.getEntity(owner) : null;
        if (!(e instanceof LivingEntity le)) {
            return -1;
        }
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (path.startsWith("tower_")) {
            return 0xB8D2FF;
        }
        return switch (PaperCutLayer.style(le)) {
            case KING -> 0xF2C75C;
            case WARDEN -> 0x5CE6D2;
            case FOUNDER -> 0xF6F6FF;
            case MOON -> 0xDCE8FF;
            case LORD -> PaperCutLayer.lordRgbOf(le);
            default -> -1;
        };
    }

    // 軌道を回す主か (月の主・塔の主)
    static boolean orbits(Entity e) {
        if (!(e instanceof LivingEntity le)) {
            return false;
        }
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        // 塔は主 (塔の主・大きな目) だけ。手下 (目・弓の目・欠片) には付けない
        return path.startsWith("tower_") ? path.equals("tower_master") || path.equals("tower_eye_big") : PaperCutLayer.style(le) == PaperCutLayer.Style.MOON;
    }

    // ---------------------------------------------------------------- 当たりの輪
    private record Ring(double x, double y, double z, double radius, int rgb, double start) {
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    static final float RING_TICKS = 16f;

    // 当たった瞬間 (TelegraphClient から 1 回)
    static void impact(ClientLevel level, TelegraphClient.Decal d) {
        int rgb;
        if (d.look != AttackLook.NONE) {
            if (d.look.roar()) {
                return;
            }
            rgb = elementRgb(d.look.element);
        } else {
            rgb = bossRgb(level, d.owner);
            if (rgb < 0) {
                return;
            }
        }
        TelegraphShape s = d.shape.at(1.0);
        double[] c = AttackFx.center(s);
        double r = AttackFx.halfSize(s);
        double y = AttackFx.ground(d, c[0], c[1]);
        if (RINGS.size() > 24) {
            RINGS.remove(0);
        }
        RINGS.add(new Ring(c[0], y + 0.12, c[1], Math.min(r, 18), rgb, level.getGameTime()));
    }

    // 撮影: その場に輪を一つ
    public static void testRing(double x, double y, double z, double radius, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            RINGS.add(new Ring(x, y, z, radius, rgb, mc.level.getGameTime()));
        }
    }

    // 月の主の技の合図 (fx/OrbitCuePayload): 主の番号 -> {始まりの時刻, 放つまでの tick}
    private static final java.util.Map<Integer, double[]> CUES = new java.util.HashMap<>();

    public static void cue(jp.revati.skyabyss.fx.OrbitCuePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            CUES.put(p.entityId(), new double[] {mc.level.getGameTime(), p.ticks()});
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CUES.clear();
        RINGS.clear();
    }

    // ---------------------------------------------------------------- 描く
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        Vec3 cam = event.getCamera().getPosition();
        Camera camera = event.getCamera();
        Matrix4f m = event.getPoseStack().last().pose();
        RINGS.removeIf(r -> now - r.start() > RING_TICKS + 8);
        CUES.values().removeIf(c -> now - c[0] > c[1] + 10);
        boolean any = !RINGS.isEmpty() || !CUES.isEmpty();
        for (TelegraphClient.Decal d : TelegraphClient.decals()) {
            if (d.look != AttackLook.NONE || d.owner >= 0) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        BufferBuilder band = LightDraw.begin();
        List<Runnable> soft = new ArrayList<>();
        List<Runnable> streak = new ArrayList<>();
        Set<Integer> orbited = new HashSet<>();
        for (TelegraphClient.Decal d : TelegraphClient.decals()) {
            double t = now - d.localStart;
            if (t < 0) {
                continue;
            }
            AttackLook look = d.look;
            if (look != AttackLook.NONE) {
                int rgb = elementRgb(look.element);
                if (look.travel == AttackLook.Travel.BEAM) {
                    beam(level, band, soft, m, cam, camera, d, t, rgb);
                } else if (look.travel == AttackLook.Travel.SWEEP) {
                    sweep(level, band, m, cam, d, t, rgb);
                }
                if (look.windup == AttackLook.Windup.GATHER && t < d.warn) {
                    gather(level, band, soft, streak, m, cam, camera, d, t, rgb);
                }
            }
            if (d.owner >= 0 && !orbited.contains(d.owner)) {
                Entity e = level.getEntity(d.owner);
                if (e != null && orbits(e) && t < d.warn + 8) {
                    orbited.add(d.owner);
                    orbit(level, band, soft, m, cam, camera, e, d.warn, t, bossRgb(level, d.owner), partial);
                }
            }
        }
        // 月の主の技の合図 (予告を使わない主)
        for (var en : CUES.entrySet()) {
            if (orbited.contains(en.getKey())) {
                continue;
            }
            Entity e = level.getEntity(en.getKey());
            double t = now - en.getValue()[0];
            if (e != null && t >= 0 && t < en.getValue()[1] + 8) {
                orbit(level, band, soft, m, cam, camera, e, (int) en.getValue()[1], t, bossRgb(level, en.getKey()), partial);
            }
        }
        for (Ring r : RINGS) {
            ring(band, soft, m, cam, camera, r, now);
        }
        LightDraw.end(band, LightDraw.BAND, true);
        if (!streak.isEmpty()) {
            BufferBuilder sb = LightDraw.begin();
            current = sb;
            streak.forEach(Runnable::run);
            LightDraw.end(sb, LightDraw.STREAK, true);
        }
        if (!soft.isEmpty()) {
            BufferBuilder sb = LightDraw.begin();
            current = sb;
            soft.forEach(Runnable::run);
            LightDraw.end(sb, LightDraw.SOFT, true);
        }
        current = null;
    }

    // 後から描く束 (光の点・筋は別の絵なので、帯のあとにまとめて描く)
    private static BufferBuilder current;


    private static Vec3 rel(double x, double y, double z, Vec3 cam) {
        return new Vec3(x - cam.x, y - cam.y, z - cam.z);
    }

    // 帯の濃さ: 放ってから FIRE_HOLD tick は満ち、FIRE_FADE tick で細く薄れる (一瞬の技でも目に残る)
    static final int FIRE_HOLD = 6;
    static final int FIRE_FADE = 8;

    private static float fire(TelegraphClient.Decal d, double t) {
        double end = d.warn + Math.max(FIRE_HOLD, d.active);
        if (t < d.warn || t > end + FIRE_FADE) {
            return 0f;
        }
        float in = (float) Mth.clamp((t - d.warn) / 1.5, 0, 1);
        float out = (float) Mth.clamp((end + FIRE_FADE - t) / FIRE_FADE, 0, 1);
        return in * out * out;
    }

    // ---- 光線: 元から帯の先まで
    private static void beam(ClientLevel level, BufferBuilder b, List<Runnable> soft, Matrix4f m, Vec3 cam, Camera camera, TelegraphClient.Decal d, double t, int rgb) {
        double[] src = AttackFx.source(level, d);
        double[][] ax = AttackFx.axis(level, d);
        // 放つ直前: 細く震える狙いの線
        if (t >= d.warn - 6 && t < d.warn) {
            float k = (float) ((t - (d.warn - 6)) / 6.0);
            Vec3[] pts = beamPoints(d, src, ax, cam, 14);
            LightDraw.ribbon(b, m, pts, 0.12f + 0.1f * k, LightDraw.whiten(rgb, 0.5f), 0.35f + 0.4f * k);
            return;
        }
        float a = fire(d, t);
        if (a <= 0.01f) {
            return;
        }
        Vec3[] pts = beamPoints(d, src, ax, cam, 18);
        float flick = 0.9f + 0.1f * Mth.sin((float) t * 2.3f);
        float w = (float) Math.min(1.6, 0.9 + AttackFx.halfSize(d.shape) * 0.04);
        LightDraw.ribbon(b, m, pts, 3.4f * w, rgb, 0.45f * a * flick);
        LightDraw.ribbon(b, m, pts, 1.3f * w, LightDraw.whiten(rgb, 0.35f), 0.8f * a);
        LightDraw.ribbon(b, m, pts, 0.42f * w, 0xFFFFFF, 0.95f * a);
        // 元と先のにじみ (元は体の表の、カメラ側)
        Vec3 s0 = surface(pts[0], bodyRadius(level, d));
        Vec3 s1 = pts[pts.length - 1];
        soft.add(() -> {
            LightDraw.sprite(current, m, camera, s0, 4.2f * w, rgb, 0.85f * a);
            LightDraw.sprite(current, m, camera, s1, 3.0f * w, LightDraw.whiten(rgb, 0.3f), 0.7f * a);
        });
    }

    // 技の主の体の半径 (光を体の外に出すため)。主がいなければ小さく
    private static double bodyRadius(ClientLevel level, TelegraphClient.Decal d) {
        Entity e = d.owner >= 0 ? level.getEntity(d.owner) : null;
        return e == null ? 0.5 : Math.max(0.8, e.getBbWidth() * 0.75);
    }

    // 体の中心 c (カメラからの相対) から、カメラの方へ半径 r 出た点
    private static Vec3 surface(Vec3 c, double r) {
        double len = c.length();
        return len < r + 0.5 ? c : c.subtract(c.scale(r / len));
    }

    private static Vec3[] beamPoints(TelegraphClient.Decal d, double[] src, double[][] ax, Vec3 cam, int n) {
        Vec3[] pts = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            double x = ax[0][0] + (ax[1][0] - ax[0][0]) * u;
            double z = ax[0][1] + (ax[1][1] - ax[0][1]) * u;
            if (i == 0) {
                x = src[0];
                z = src[2];
            }
            // 当たりの瞬間に床から出る範囲の模型 (AreaFx) の上を通す
            double gy = AttackFx.ground(d, x, z) + 2.4;
            double y = src[1] + (gy - src[1]) * Math.min(1, u * 4);
            pts[i] = rel(x, y, z, cam);
        }
        return pts;
    }

    private static Vec3 along(Vec3[] p, double u) {
        double f = u * (p.length - 1);
        int i = Mth.clamp((int) f, 0, p.length - 2);
        double k = f - i;
        return p[i].add(p[i + 1].subtract(p[i]).scale(k));
    }

    // ---- 振り回し: 元から今の先端への帯と、通った跡の弧
    private static void sweep(ClientLevel level, BufferBuilder b, Matrix4f m, Vec3 cam, TelegraphClient.Decal d, double t, int rgb) {
        if (d.active <= 0 || t < d.warn || t > d.warn + d.active + 4) {
            return;
        }
        double p = Mth.clamp((t - d.warn) / d.active, 0, 1);
        float a = (float) Mth.clamp((d.warn + d.active + 4 - t) / 4.0, 0, 1);
        double[] src = AttackFx.source(level, d);
        int trail = 10;
        Vec3[] tips = new Vec3[trail];
        float[] al = new float[trail];
        for (int k = 0; k < trail; k++) {
            double pk = Math.max(0, p - (trail - 1 - k) * 0.025);
            double[] tip = farthest(d.shape.at(pk), src);
            tips[k] = rel(tip[0], AttackFx.ground(d, tip[0], tip[1]) + 1.0, tip[1], cam);
            al[k] = (float) k / (trail - 1);
        }
        LightDraw.ribbon(b, m, tips, 1.4f, rgb, 0.7f * a, al);
        LightDraw.ribbon(b, m, tips, 0.45f, LightDraw.whiten(rgb, 0.5f), 0.9f * a, al);
        Vec3 s = rel(src[0], src[1], src[2], cam);
        Vec3 tip = tips[trail - 1];
        Vec3[] line = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            line[i] = s.add(tip.subtract(s).scale(i / 7.0));
        }
        LightDraw.ribbon(b, m, line, 1.6f, rgb, 0.35f * a);
        LightDraw.ribbon(b, m, line, 0.35f, 0xFFFFFF, 0.75f * a);
    }

    private static double[] farthest(TelegraphShape s, double[] src) {
        List<double[]> o = AttackFx.outline(s, 24);
        double[] far = AttackFx.center(s);
        double best = -1;
        for (double[] q : o) {
            double dd = Math.hypot(q[0] - src[0], q[1] - src[2]);
            if (dd > best) {
                best = dd;
                far = q;
            }
        }
        return far;
    }

    // ---- 溜め: 光の筋が元へ吸い込まれる
    private static void gather(ClientLevel level, BufferBuilder b, List<Runnable> soft, List<Runnable> streak, Matrix4f m, Vec3 cam, Camera camera,
                               TelegraphClient.Decal d, double t, int rgb) {
        double p = Mth.clamp(t / Math.max(1, d.warn), 0, 1);
        double[] srcA = AttackFx.source(level, d);
        Vec3 src = rel(srcA[0], srcA[1], srcA[2], cam);
        // 主の体の外から吸い込まれる (体の中の筋は殻に隠れるので、体の半径より外だけ)
        double body = bodyRadius(level, d);
        Vec3 face = surface(src, body);
        int n = 8 + (int) (14 * p);
        float fade = (float) Mth.clamp((d.warn - t) / 3.0, 0, 1);
        streak.add(() -> {
            for (int k = 0; k < n; k++) {
                java.util.Random r = new java.util.Random(d.id * 7919L + k * 104729L);
                double period = 12 + r.nextDouble() * 8;
                double ph = ((t + r.nextDouble() * period) / period) % 1.0;
                double a0 = r.nextDouble() * Math.PI * 2;
                double el = (r.nextDouble() - 0.4) * 1.1;
                double R = body + 0.6 + 6.0 * Math.pow(1 - ph, 1.4);
                Vec3 dir = new Vec3(Math.cos(a0) * Math.cos(el), Math.sin(el), Math.sin(a0) * Math.cos(el));
                Vec3 head = src.add(dir.scale(Math.max(body + 0.2, R - 1.8)));
                Vec3 tail = src.add(dir.scale(R + 0.8 + 1.8 * p));
                float a = (float) Math.sin(ph * Math.PI) * (0.45f + 0.55f * (float) p) * fade;
                LightDraw.ribbon(current, m, new Vec3[] {tail, head}, 0.38f + 0.18f * (float) p, LightDraw.whiten(rgb, 0.3f), a);
            }
        });
        soft.add(() -> {
            float pulse = 0.85f + 0.15f * Mth.sin((float) t * 0.9f);
            LightDraw.sprite(current, m, camera, face, (float) (1.0 + 3.2 * p) * pulse, rgb, (0.35f + 0.6f * (float) p) * fade);
            LightDraw.sprite(current, m, camera, face, (float) (0.4 + 1.2 * p), 0xFFFFFF, (0.3f + 0.6f * (float) p) * fade);
        });
    }

    // ---- 当たりの輪
    private static void ring(BufferBuilder b, List<Runnable> soft, Matrix4f m, Vec3 cam, Camera camera, Ring r, double now) {
        for (int pass = 0; pass < 2; pass++) {
            double age = now - r.start() - pass * 5;
            if (age < 0 || age > RING_TICKS) {
                continue;
            }
            double k = age / RING_TICKS;
            double e = 1 - Math.pow(1 - k, 3);
            double rad = r.radius() * (0.2 + 1.0 * e) * (pass == 1 ? 0.75 : 1);
            double w = Mth.clamp(r.radius() * 0.16, 0.35, 1.1) * (1 - 0.6 * k) * (pass == 1 ? 0.7 : 1);
            float a = (float) Math.pow(1 - k, 1.4);
            Vec3 c = rel(r.x(), r.y(), r.z(), cam);
            LightDraw.groundRing(b, m, c, rad, w * 2.4, r.rgb(), 0.45f * a);
            LightDraw.groundRing(b, m, c, rad, w, LightDraw.whiten(r.rgb(), 0.45f), 0.95f * a);
        }
        double age = now - r.start();
        if (age >= 0 && age < 6) {
            // 当たった所の床が一瞬明るむ (床に寝た光。立った丸は出さない)
            Vec3 c = rel(r.x(), r.y(), r.z(), cam);
            float a = (float) (1 - age / 6);
            double rr = Math.min(9, 1 + r.radius() * 0.5);
            LightDraw.groundRing(b, m, c, rr * 0.5, rr, LightDraw.whiten(r.rgb(), 0.3f), 0.55f * a);
        }
    }

    // ---- 軌道: 月の主・塔の主のまわり
    private static void orbit(ClientLevel level, BufferBuilder b, List<Runnable> soft, Matrix4f m, Vec3 cam, Camera camera, Entity e,
                              int warn, double t, int rgb, float partial) {
        if (rgb < 0) {
            rgb = 0xDCE8FF;
        }
        double ex = Mth.lerp(partial, e.xo, e.getX());
        double ey = Mth.lerp(partial, e.yo, e.getY());
        double ez = Mth.lerp(partial, e.zo, e.getZ());
        double p = Mth.clamp(t / Math.max(1, warn), 0, 1);
        double release = t >= warn ? Mth.clamp((t - warn) / 8.0, 0, 1) : 0;
        float a = (float) ((0.35 + 0.65 * Math.min(1, p * 2)) * (1 - release));
        if (a <= 0.01f) {
            return;
        }
        // 当たりの箱より大きく描く主が多いので、高さからも見積もる (体の外を回す)
        double baseR = Math.max(1.8, Math.max(e.getBbWidth() * 0.9, e.getBbHeight() * 0.55) + 1.6);
        double R = baseR * (1 - 0.3 * p) * (1 + 1.5 * release);
        double spin = t * 0.05;
        int col = rgb;
        // 足もとに重なって回る三本の輪 (2026-11-15 の選定 C)。弧の進む先が明るく、尾が薄れる (回る向きが読める)
        Vec3 c = rel(ex, ey + 0.12, ez, cam);
        for (int k = 0; k < 3; k++) {
            double rr = R * (1.2 + 0.75 * k);
            double dir = k % 2 == 0 ? 1 : -1.3;
            double rot = spin * dir + k;
            double span = Math.PI * 1.35;
            int n = 64;
            Vec3[] pts = new Vec3[n + 1];
            float[] al = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n;
                // 進む向きの先 (u = 1) が頭
                double th = dir > 0 ? rot + span * u : rot + span * (1 - u);
                pts[i] = c.add(Math.cos(th) * rr, 0, Math.sin(th) * rr);
                al[i] = (float) Math.pow(u, 1.6) * Mth.clamp((float) ((1 - u) * 18), 0f, 1f);
            }
            LightDraw.ribbon(b, m, pts, 0.24f, k == 0 ? LightDraw.whiten(col, 0.4f) : col, 0.9f * a, al);
            Vec3 head = pts[n - 1];
            float fa = a;
            soft.add(() -> LightDraw.sprite(current, m, camera, head, 0.9f, LightDraw.whiten(col, 0.6f), 0.8f * fa));
        }
    }
}
