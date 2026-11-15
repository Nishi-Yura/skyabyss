package jp.revati.skyabyss.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.fx.RiftPayload;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 空間の裂け目 (2026-11-15 の光の表現の選定 1・2)。縦に細い光の線が走り、太くなり、向こうに「その場所の内側の空」がのぞく。
//   領域の始まり (DomainClient の宣言): 心臓の間の上に開き、宣言の終わりに広がって溶ける (空は替えない。霧の色だけが残る)
//   渡るとき (RiftPayload: 順路の部屋から次へ・深淵の門・月の門): 目の前に開き、広がって画面を覆い、覆った中で運ばれ、
//                                                          閉じるまぶたのように細くなって新しい場所が見える
// 内側の空は巨神ごと: 岩鯨 = 鉱脈が星のように光る岩の闇、潮殻 = 水面越しの揺れる光、雷翼 = 雷雲と閃き、余燼 = 昇る熾の夜。
// 深淵 = 底のない闇に骨色の淡い光、月 = 濃い夜に銀の星。色と縁の形は巨神の形の言葉に合わせる (動画の紫のネオンには寄せない)。
// 内側の絵は、星・雲・波の明かりを違う速さと大きさで重ね、見る位置がずれると層ごとにずれる (奥行き)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class RiftFx {
    private RiftFx() {
    }

    // ---------------------------------------------------------------- 内側の空
    record Layer(ResourceLocation tex, int rgb, float alpha, float scale, float du, float dv, float parallax) {
    }

    record Theme(int edge, int glow, int base, Layer[] layers, boolean flicker, float jag) {
    }

    static Theme theme(String id) {
        return switch (id) {
            case Titans.TIDE_SHELL -> new Theme(0xBDF0FF, 0x3FB8E8, 0x03121C, new Layer[] {
                    new Layer(LightDraw.CLOUD, 0x0E4A66, 0.9f, 2.6f, 0.002f, -0.001f, 0.02f),
                    new Layer(LightDraw.CAUSTIC, 0x2FA6D6, 0.38f, 2.2f, 0.004f, 0.006f, 0.05f),
                    new Layer(LightDraw.CAUSTIC, 0x9AE6FF, 0.16f, 1.3f, -0.006f, 0.004f, 0.09f),
                    new Layer(LightDraw.STARS, 0xD8F8FF, 0.55f, 1.1f, 0f, 0.012f, 0.12f)}, false, 0.3f);
            case Titans.THUNDER_WING -> new Theme(0xE4DCFF, 0x8F78FF, 0x07061A, new Layer[] {
                    new Layer(LightDraw.CLOUD, 0x2A2068, 1f, 2.2f, 0.004f, 0.001f, 0.02f),
                    new Layer(LightDraw.CLOUD, 0x5A48C0, 0.5f, 1.2f, -0.006f, 0.002f, 0.06f),
                    new Layer(LightDraw.STARS, 0xF0ECFF, 0.7f, 1.3f, 0.002f, 0f, 0.1f)}, true, 0.8f);
            case Titans.EMBER -> new Theme(0xFFD0A0, 0xFF6A24, 0x170402, new Layer[] {
                    new Layer(LightDraw.CLOUD, 0x5A1406, 1f, 2.4f, 0.001f, 0.003f, 0.02f),
                    new Layer(LightDraw.CLOUD, 0xB03A10, 0.45f, 1.3f, -0.002f, 0.006f, 0.05f),
                    new Layer(LightDraw.STARS, 0xFFB060, 0.9f, 1.0f, 0f, 0.02f, 0.1f),
                    new Layer(LightDraw.STARS, 0xFFE0B0, 0.5f, 0.6f, 0.003f, 0.035f, 0.16f)}, false, 0.55f);
            case RiftPayload.ABYSS -> new Theme(0xE6DCC2, 0x7A6A90, 0x020205, new Layer[] {
                    new Layer(LightDraw.CLOUD, 0x1A1030, 0.9f, 3.0f, 0.001f, -0.001f, 0.01f),
                    new Layer(LightDraw.CLOUD, 0x3A2A3A, 0.35f, 1.6f, -0.0015f, 0.001f, 0.04f),
                    new Layer(LightDraw.STARS, 0xCFC3A0, 0.4f, 1.6f, 0f, -0.004f, 0.08f)}, false, 0.6f);
            case RiftPayload.MOON -> new Theme(0xF4F8FF, 0x9AB4FF, 0x040718, new Layer[] {
                    new Layer(LightDraw.CLOUD, 0x18244E, 0.9f, 2.8f, 0.001f, 0f, 0.015f),
                    new Layer(LightDraw.STARS, 0xFFFFFF, 0.95f, 1.6f, 0.001f, 0f, 0.05f),
                    new Layer(LightDraw.STARS, 0xC8D6FF, 0.7f, 0.8f, -0.002f, 0.001f, 0.12f)}, false, 0.2f);
            default -> new Theme(0xDDF2E4, 0x86C8A6, 0x0A1210, new Layer[] {
                    new Layer(LightDraw.CLOUD, 0x24342E, 1f, 2.4f, 0.001f, 0.001f, 0.02f),
                    new Layer(LightDraw.CLOUD, 0x4E6A5C, 0.4f, 1.3f, -0.002f, 0f, 0.05f),
                    new Layer(LightDraw.STARS, 0xC8F4D8, 0.85f, 1.2f, 0f, 0.002f, 0.09f),
                    new Layer(LightDraw.STARS, 0xFFF2C8, 0.45f, 0.7f, 0.002f, -0.001f, 0.15f)}, false, 0.7f);
        };
    }

    // 領域の名 -> 巨神
    public static String themeOfDomain(String domain) {
        return switch (domain) {
            case "stone_sea" -> Titans.STONE_WHALE;
            case "ebb_flow" -> Titans.TIDE_SHELL;
            case "thunder_cage" -> Titans.THUNDER_WING;
            default -> Titans.EMBER;
        };
    }

    // ---------------------------------------------------------------- 開いている裂け目
    static final class Rift {
        final Theme theme;
        final String themeId;
        final double start;
        // 場に立つ裂け目の中心 (ワールド座標)・高さ・半幅
        final Vec3 at;
        final double height;
        final double halfWidth;
        // 領域: 宣言の長さ。渡る: 0
        final int declare;
        final boolean pass;
        final long seed;

        Rift(String themeId, double start, Vec3 at, double height, double halfWidth, int declare, boolean pass) {
            this.themeId = themeId;
            this.theme = theme(themeId);
            this.start = start;
            this.at = at;
            this.height = height;
            this.halfWidth = halfWidth;
            this.declare = declare;
            this.pass = pass;
            this.seed = (long) (start * 7919) ^ themeId.hashCode();
        }

        double end() {
            return pass ? PASS_END : declare + 14;
        }
    }

    private static final List<Rift> RIFTS = new ArrayList<>();
    // 撮影: 動きを止める時刻 (tick。0 未満 = 止めない)
    static float freezeAge = -1f;
    // 渡る裂け目の時刻 (tick): 0〜10 線が走る / 10〜20 太る / 20〜26 迫って覆う / 26〜40 覆ったまま (ここで運ばれる) / 40〜54 閉じて見える
    static final float OPEN = 10;
    static final float WIDEN = 20;
    static final float COVER = RiftPayload.LEAD;
    static final float HOLD_END = 40;
    static final float PASS_END = 54;

    private static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
    }

    private static double age(Rift r, double t) {
        return freezeAge >= 0 ? freezeAge : t - r.start;
    }

    // 渡る: 目の前 3.2 ブロックに開く
    public static void receive(RiftPayload p) {
        pass(p.theme());
    }

    public static void pass(String themeId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        RIFTS.removeIf(r -> r.pass);
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 at = eye.add(flat.scale(3.2)).add(0, 0.1, 0);
        RIFTS.add(new Rift(themeId, now(0), at, 3.4, 1.15, 0, true));
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 0.28f, 1.5f, false);
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.9f, 0.5f, false);
    }

    // 領域の宣言: 部屋の中心の上に、宣言の長さだけ開く (DomainClient の START から)
    public static void domain(String domainId, double cx, double cz, double floor, double radius, int declare) {
        // 心臓の体の上、部屋の天井の下に開く (部屋の真ん中は心臓の体の中なので)
        Minecraft mc = Minecraft.getInstance();
        double base = floor + 3.5;
        if (mc.level != null) {
            net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(cx - radius, floor - 4, cz - radius, cx + radius, floor + 40, cz + radius);
            for (jp.revati.skyabyss.entity.HeartBoss hb : mc.level.getEntitiesOfClass(jp.revati.skyabyss.entity.HeartBoss.class, around)) {
                base = Math.max(base, hb.getBoundingBox().maxY + 1.5);
            }
        }
        double ceiling = floor + 48;
        if (mc.level != null) {
            for (int y = (int) Math.floor(base); y < floor + 48; y++) {
                if (!mc.level.getBlockState(net.minecraft.core.BlockPos.containing(cx, y, cz)).isAir()) {
                    ceiling = y;
                    break;
                }
            }
        }
        double h = Mth.clamp(radius * 0.75, 7, 15);
        h = Math.max(4, Math.min(h, ceiling - 1.0 - base));
        double y = base + h * 0.5;
        RIFTS.removeIf(r -> !r.pass);
        RIFTS.add(new Rift(themeOfDomain(domainId), now(0), new Vec3(cx, y, cz), h, h * 0.3, Math.max(30, declare), false));
    }

    // 撮影: 場に好きな大きさの裂け目を置く
    public static void place(String themeId, double x, double y, double z, double height, int declare) {
        RIFTS.removeIf(r -> !r.pass);
        RIFTS.add(new Rift(themeId, now(0), new Vec3(x, y, z), height, height * 0.3, declare, false));
    }

    public static void clear() {
        RIFTS.clear();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RIFTS.clear();
    }

    // ---------------------------------------------------------------- 形

    // 裂け目の輪郭 (中心から見た横 x・縦 y。右の縁を上から下、左の縁を下から上の順。閉じた輪)
    static double[][] outline(Rift r, double t, double halfW, double halfH, int perSide) {
        int n = perSide * 2;
        double[][] pts = new double[n][2];
        java.util.Random rnd = new java.util.Random(r.seed);
        double[] jag = new double[n];
        for (int i = 0; i < n; i++) {
            jag[i] = rnd.nextDouble() * 2 - 1;
        }
        long flickerStep = (long) (t / 2);
        java.util.Random fl = new java.util.Random(r.seed ^ flickerStep * 31);
        for (int i = 0; i < n; i++) {
            boolean right = i < perSide;
            int k = right ? i : i - perSide;
            // y: 右は上 (+1) から下 (-1)、左は下から上
            double s = (k + 0.5) / perSide;
            double yy = right ? 1 - 2 * s : -1 + 2 * s;
            // 割れ目: 菱形にぎざぎざ。刻みの深さは巨神ごと (潮殻は浅く、雷翼は深い)。止まった刻み + 2 tick ごとの小さな震え。
            // 刻みは一つおきに強く、先の近くは浅くして、尖った先が細く伸びて見えるように
            double base = Math.pow(Math.max(0, 1 - Math.abs(yy)), 0.95);
            double notch = (i % 2 == 0 ? 1.0 : 0.55) * jag[i];
            double j = notch * 0.34 * r.theme.jag * Math.sqrt(base) + (fl.nextDouble() - 0.5) * 0.07 * base;
            double w = base * (1 + j);
            pts[i][0] = (right ? 1 : -1) * w * halfW;
            pts[i][1] = yy * halfH;
        }
        return pts;
    }

    // ---------------------------------------------------------------- 場に描く
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RIFTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double t = now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        RIFTS.removeIf(r -> freezeAge < 0 && t - r.start > r.end());
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        for (Rift r : RIFTS) {
            double age = age(r, t);
            double[] f = frame(r, age);
            if (f == null) {
                continue;
            }
            drawWorld(m, cam, r, t, f[0], f[1], (float) f[2]);
        }
    }

    // 時刻ごとの (半幅, 半高さ, 濃さ)。null = 場には描かない
    static double[] frame(Rift r, double age) {
        if (r.pass) {
            if (age >= COVER) {
                return null;
            }
            double hh = r.height * 0.5 * ease(Mth.clamp(age / OPEN, 0, 1));
            double hw = r.halfWidth * Mth.lerp(ease(Mth.clamp((age - OPEN) / (WIDEN - OPEN), 0, 1)), 0.1, 1.0);
            double grow = 1 + 6 * Math.pow(Mth.clamp((age - WIDEN) / (COVER - WIDEN), 0, 1), 2);
            return new double[] {hw * grow, hh * grow, 1};
        }
        double d = r.declare;
        double open = d * 0.3;
        double widen = d * 0.72;
        double hh = r.height * 0.5 * ease(Mth.clamp(age / open, 0, 1));
        double hw = r.halfWidth * Mth.lerp(ease(Mth.clamp((age - open) / (widen - open), 0, 1)), 0.08, 1.0);
        double burst = Mth.clamp((age - widen) / (d + 14 - widen), 0, 1);
        double grow = 1 + 1.6 * ease(burst);
        float alpha = (float) (1 - burst * burst);
        if (alpha <= 0.01f) {
            return null;
        }
        return new double[] {hw * grow * (1 + burst * 0.8), hh * grow, alpha};
    }

    static double ease(double x) {
        return x * x * (3 - 2 * x);
    }

    private static void drawWorld(Matrix4f m, Vec3 cam, Rift r, double t, double halfW, double halfH, float alpha) {
        if (halfH < 0.02) {
            return;
        }
        // 縦に立ち、横はいつもカメラへ向く
        Vec3 rel = r.at.subtract(cam);
        Vec3 toCam = new Vec3(-rel.x, 0, -rel.z);
        Vec3 right = toCam.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(toCam.z, 0, -toCam.x).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        int perSide = 28;
        double[][] o = outline(r, t, halfW, halfH, perSide);
        Vec3[] pts = new Vec3[o.length];
        for (int i = 0; i < o.length; i++) {
            pts[i] = rel.add(right.scale(o[i][0])).add(up.scale(o[i][1]));
        }
        // 見る位置のずれ (層ごとの奥行き)
        double px = cam.x * right.x + cam.z * right.z;
        double py = cam.y;
        Theme th = r.theme;
        float flick = th.flicker ? flicker(r, t) : 1f;
        // 1. 奥の闇 (通常の重ね)
        BufferBuilder b = LightDraw.begin();
        fan(b, m, rel, pts, null, 0, 0, 0, th.base, 0.97f * alpha);
        LightDraw.endAlpha(b, LightDraw.BAND, true);
        // 2. 内側の空の層 (足し合わせ)
        for (Layer L : th.layers) {
            b = LightDraw.begin();
            double ou = t * L.du() + px * L.parallax() / 4.0;
            double ov = t * L.dv() - py * L.parallax() / 4.0;
            fan(b, m, rel, pts, o, L.scale() * 4.0, ou, ov, L.rgb(), L.alpha() * alpha * (L.tex() == LightDraw.CLOUD ? 1f : flick));
            LightDraw.end(b, L.tex(), true);
        }
        // 雷翼: 内側の閃き
        if (th.flicker && flick > 1.15f) {
            b = LightDraw.begin();
            Vec3[] bolt = bolt(r, t, rel, right, up, halfW, halfH);
            LightDraw.ribbon(b, m, bolt, 0.35f, LightDraw.whiten(th.edge, 0.4f), 0.9f * alpha);
            LightDraw.end(b, LightDraw.BAND, true);
        }
        // 3. 縁
        b = LightDraw.begin();
        Vec3[] loop = close(pts);
        double scaleW = Math.max(0.6, Math.min(2.2, halfH * 0.12));
        LightDraw.ribbon(b, m, loop, (float) (1.5 * scaleW), th.glow, 0.55f * alpha * flick);
        LightDraw.ribbon(b, m, loop, (float) (0.42 * scaleW), LightDraw.whiten(th.edge, 0.35f), 0.95f * alpha * flick);
        LightDraw.ribbon(b, m, loop, (float) (0.12 * scaleW), 0xFFFFFF, 0.9f * alpha);
        // 内側にもう一本、暗めの縁 (裂けた面が二重にめくれて見える。奥行き)
        Vec3[] inner = new Vec3[loop.length];
        for (int i = 0; i < loop.length; i++) {
            Vec3 q = loop[i].subtract(rel);
            inner[i] = rel.add(q.x * 0.86, q.y * 0.95, q.z * 0.86);
        }
        LightDraw.ribbon(b, m, inner, (float) (0.5 * scaleW), th.glow, 0.32f * alpha * flick);
        cracks(b, m, r, t, rel, right, up, halfW, halfH, scaleW, alpha);
        LightDraw.end(b, LightDraw.BAND, true);
        b = LightDraw.begin();
        shards(b, m, r, t, rel, right, up, o, scaleW, alpha);
        LightDraw.end(b, LightDraw.STREAK, true);
        // 4. 上下の先の小さなにじみ (丸く見えないよう小さく薄く。縦に長い光にする)
        b = LightDraw.begin();
        var camObj = Minecraft.getInstance().gameRenderer.getMainCamera();
        LightDraw.sprite(b, m, camObj, rel.add(up.scale(halfH)), (float) (0.7 * scaleW), (float) (2.4 * scaleW), 0f, th.glow, 0.5f * alpha);
        LightDraw.sprite(b, m, camObj, rel.add(up.scale(-halfH)), (float) (0.7 * scaleW), (float) (2.4 * scaleW), 0f, th.glow, 0.5f * alpha);
        LightDraw.end(b, LightDraw.SOFT, true);
    }

    static float flicker(Rift r, double t) {
        long step = (long) t;
        java.util.Random rnd = new java.util.Random(r.seed ^ (step * 0x9E3779B9L));
        float v = 0.85f + rnd.nextFloat() * 0.25f;
        if (rnd.nextFloat() < 0.12f) {
            v = 1.35f;
        }
        return v;
    }

    private static Vec3[] close(Vec3[] pts) {
        Vec3[] loop = new Vec3[pts.length + 1];
        System.arraycopy(pts, 0, loop, 0, pts.length);
        loop[pts.length] = pts[0];
        return loop;
    }

    // 中心から輪郭への扇。o があれば層の絵の座標 (局所の x・y を大きさ scale で割る)
    private static void fan(BufferBuilder b, Matrix4f m, Vec3 c, Vec3[] pts, double[][] o, double scale, double ou, double ov, int rgb, float a) {
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float uc = 0.5f;
            float vc = 0.5f;
            float ui = 0.5f;
            float vi = 0.5f;
            float uj = 0.5f;
            float vj = 0.5f;
            if (o != null) {
                uc = (float) ou;
                vc = (float) ov;
                ui = (float) (o[i][0] / scale + ou);
                vi = (float) (-o[i][1] / scale + ov);
                uj = (float) (o[j][0] / scale + ou);
                vj = (float) (-o[j][1] / scale + ov);
            }
            LightDraw.v(b, m, c.x, c.y, c.z, uc, vc, rgb, a);
            LightDraw.v(b, m, c.x, c.y, c.z, uc, vc, rgb, a);
            LightDraw.v(b, m, pts[i].x, pts[i].y, pts[i].z, ui, vi, rgb, a);
            LightDraw.v(b, m, pts[j].x, pts[j].y, pts[j].z, uj, vj, rgb, a);
        }
    }

    // 雷翼の内側に走る一本の閃き
    private static Vec3[] bolt(Rift r, double t, Vec3 c, Vec3 right, Vec3 up, double hw, double hh) {
        java.util.Random rnd = new java.util.Random(r.seed ^ ((long) t * 131));
        int n = 7;
        Vec3[] p = new Vec3[n];
        double x = (rnd.nextDouble() - 0.5) * hw * 0.6;
        for (int i = 0; i < n; i++) {
            double y = hh * 0.8 - 1.6 * hh * i / (n - 1);
            x += (rnd.nextDouble() - 0.5) * hw * 0.5;
            double lim = hw * 0.7 * Math.pow(Math.max(0, 1 - Math.pow(y / hh, 2)), 0.8);
            x = Mth.clamp(x, -lim, lim);
            p[i] = c.add(right.scale(x)).add(up.scale(y));
        }
        return p;
    }

    // 上下の先から伸びるひび (ぎざぎざの細い光)
    private static void cracks(BufferBuilder b, Matrix4f m, Rift r, double t, Vec3 c, Vec3 right, Vec3 up, double hw, double hh, double sw, float alpha) {
        java.util.Random rnd = new java.util.Random(r.seed * 17);
        for (int k = 0; k < 4; k++) {
            boolean top = k % 2 == 0;
            double dir = top ? 1 : -1;
            int n = 6;
            Vec3[] p = new Vec3[n];
            double x = 0;
            double side = (k < 2 ? 1 : -1) * (0.15 + rnd.nextDouble() * 0.25);
            for (int i = 0; i < n; i++) {
                double y = dir * (hh + hh * 0.38 * i / (n - 1));
                x += side * hh * 0.06 + (rnd.nextDouble() - 0.5) * hh * 0.07;
                p[i] = c.add(right.scale(x)).add(up.scale(y));
            }
            float[] al = new float[n];
            for (int i = 0; i < n; i++) {
                al[i] = 1f - (float) i / (n - 1);
            }
            LightDraw.ribbon(b, m, p, (float) (0.32 * sw), LightDraw.whiten(r.theme.edge, 0.3f), 0.85f * alpha, al);
        }
    }

    // 縁から外へ飛ぶ光の欠片
    private static void shards(BufferBuilder b, Matrix4f m, Rift r, double t, Vec3 c, Vec3 right, Vec3 up, double[][] o, double sw, float alpha) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        int n = 18;
        for (int k = 0; k < n; k++) {
            java.util.Random rnd = new java.util.Random(r.seed + k * 977L);
            double period = 18 + rnd.nextDouble() * 14;
            double ph = (t / period + rnd.nextDouble()) % 1.0;
            int idx = rnd.nextInt(o.length);
            double ox = o[idx][0];
            double oy = o[idx][1];
            double len = Math.hypot(ox, oy) + 1e-3;
            double out = ph * (1.2 + rnd.nextDouble()) * sw * 2.2;
            Vec3 p = c.add(right.scale(ox + ox / len * out)).add(up.scale(oy + oy / len * out));
            float a = (float) (Math.sin(ph * Math.PI)) * 0.9f * alpha;
            float roll = (float) Math.atan2(oy, ox);
            LightDraw.sprite(b, m, cam, p, (float) (0.7 * sw), (float) (0.16 * sw), roll, LightDraw.whiten(r.theme.edge, 0.2f), a);
        }
    }

    // ---------------------------------------------------------------- 画面を覆う (渡る裂け目)
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Rift r = null;
        for (Rift x : RIFTS) {
            if (x.pass) {
                r = x;
            }
        }
        if (r == null) {
            return;
        }
        double t = now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        double age = age(r, t);
        if (age < COVER - 5) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        // 覆う: COVER-5〜COVER で全面へ。閉じる: HOLD_END〜PASS_END で横に細くなって消える
        float cover = (float) Mth.clamp((age - (COVER - 5)) / 5.0, 0, 1);
        double close = Mth.clamp((age - HOLD_END) / (PASS_END - HOLD_END), 0, 1);
        double halfW = w * 1.25 * (1 - ease(close));
        double halfH = h * 0.95 * (1 - 0.2 * close);
        if (halfW < 1) {
            return;
        }
        drawScreen(g, r, t, w, h, halfW, halfH, cover);
    }

    private static void drawScreen(GuiGraphics g, Rift r, double t, int w, int h, double halfW, double halfH, float alpha) {
        Matrix4f m = g.pose().last().pose();
        double cx = w * 0.5;
        double cy = h * 0.5;
        int perSide = 28;
        double[][] o = outline(r, t, halfW, halfH, perSide);
        Vec3 c = new Vec3(cx, cy, 0);
        Vec3[] pts = new Vec3[o.length];
        for (int i = 0; i < o.length; i++) {
            pts[i] = new Vec3(cx + o[i][0], cy - o[i][1], 0);
        }
        Theme th = r.theme;
        float flick = th.flicker ? flicker(r, t) : 1f;
        BufferBuilder b = LightDraw.begin();
        fan(b, m, c, pts, null, 0, 0, 0, th.base, alpha);
        LightDraw.endAlpha(b, LightDraw.BAND, false);
        double unit = Math.max(w, h) / 3.0;
        for (Layer L : th.layers) {
            b = LightDraw.begin();
            double ou = t * L.du();
            double ov = t * L.dv();
            fan(b, m, c, pts, o, L.scale() * unit, ou, ov, L.rgb(), L.alpha() * alpha * (L.tex() == LightDraw.CLOUD ? 1f : flick));
            LightDraw.end(b, L.tex(), false);
        }
        // 縁 (閉じるときに見える。覆いきっている間は画面の外)
        b = LightDraw.begin();
        Vec3[] loop = close(pts);
        ribbon2d(b, m, loop, 26f, th.glow, 0.6f * alpha * flick);
        ribbon2d(b, m, loop, 7f, LightDraw.whiten(th.edge, 0.35f), 0.95f * alpha);
        ribbon2d(b, m, loop, 2f, 0xFFFFFF, 0.9f * alpha);
        LightDraw.end(b, LightDraw.BAND, false);
    }

    // 画面の上の帯 (2D)。width は画面の画素
    private static void ribbon2d(BufferBuilder b, Matrix4f m, Vec3[] pts, float width, int rgb, float a) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i];
            Vec3 p1 = pts[i + 1];
            Vec3 d0 = pts[Math.min(pts.length - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 d1 = pts[Math.min(pts.length - 1, i + 2)].subtract(pts[i]);
            Vec3 s0 = side2d(d0, width);
            Vec3 s1 = side2d(d1, width);
            LightDraw.v(b, m, p0.x - s0.x, p0.y - s0.y, 0, 0f, 0f, rgb, a);
            LightDraw.v(b, m, p0.x + s0.x, p0.y + s0.y, 0, 0f, 1f, rgb, a);
            LightDraw.v(b, m, p1.x + s1.x, p1.y + s1.y, 0, 1f, 1f, rgb, a);
            LightDraw.v(b, m, p1.x - s1.x, p1.y - s1.y, 0, 1f, 0f, rgb, a);
        }
    }

    private static Vec3 side2d(Vec3 d, float width) {
        double len = Math.hypot(d.x, d.y);
        if (len < 1e-6) {
            return new Vec3(0, width * 0.5, 0);
        }
        return new Vec3(-d.y / len * width * 0.5, d.x / len * width * 0.5, 0);
    }

    // 撮影: rift pass <theme> / rift world <theme> x y z <高さ> <宣言 tick> / rift clear / rift freeze <tick|off>
    public static void shotCommand(String rest) {
        String[] a = rest.trim().split("\\s+");
        if (a.length == 0) {
            return;
        }
        switch (a[0]) {
            case "pass" -> pass(a.length > 1 ? a[1] : Titans.STONE_WHALE);
            case "world" -> place(a[1], Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]),
                    a.length > 5 ? Double.parseDouble(a[5]) : 10, a.length > 6 ? Integer.parseInt(a[6]) : 80);
            case "clear" -> clear();
            case "freeze" -> freezeAge = a.length < 2 || a[1].equals("off") ? -1f : Float.parseFloat(a[1]);
            default -> {
            }
        }
    }
}
