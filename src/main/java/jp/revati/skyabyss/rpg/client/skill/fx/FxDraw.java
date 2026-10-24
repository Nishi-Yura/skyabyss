package jp.revati.skyabyss.rpg.client.skill.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.dev.Prof;
import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 技の見た目の形 (手元だけで描く)。TelegraphRenderer と同じく、RenderLevelStageEvent で板 (quad) を積んで一度に描く。
// 形は一つの Prim で表す。作り方: Prim.arc / ring / disc / beam / fan / bolt / shell / line の一つを選び、
// color・width・life・ease・glow・trail を付けて FxDraw.add(cast, prim) へ。段取り (c.at) の中で呼ぶ。
//
// 道具 (Kind):
//   ARC    弧の帯 (三日月。振りの軌跡。頭が進み、尾が遅れて消える)        billboard 帯
//   LINE   まっすぐな筋 (突き・矢の白い筋。頭が進み、尾が遅れる)          billboard 帯
//   RING   地面の輪 (半径 r0 → r1 へ広がる。幅 width)                    水平の帯
//   DISC   地面の円盤 (中心から縁へ消える。半径 r0 → r1)                 水平・柔らかい円
//   FAN    地面の扇 (中心から角 ±half の扇。ぼやけた縁)                   水平・柔らかい円
//   BEAM   縦の光の柱 (底 a から高さ h へ伸びる。カメラに向く)           billboard 帯
//   BOLT   ぎざぎざの線 (a から b へ、折れ目に揺れ。稲妻の形)            billboard 帯 (揺れは時間で変わる)
//   SHELL  縦の衝撃の殻 (中心から半径 r0 → r1 へ広がる、高さ h の壁)     縦の帯
//
// 時間: birth (発動の tick) から life tick。進み具合 p = 0..1、ease で形の伸び・広がりを決め、
//       色の濃さは (1-p)^1.2 で消える。glow > 0 のとき同じ形を太く薄く一枚重ねる (光のにじみ)。
// 予算: 1 発動は SkillFx.CAST_PRIMS 個まで、全体は MAX_PRIMS 個まで (足りないものは捨てる)。
//       1 形は 16 区切りまで。頂点は一度に一つの BufferBuilder で作り、テクスチャは 2 回だけ切り替える。
// 一人称の注意: 板の頂点が CLEAR (1.2 ブロック) より近いカメラの位置にある区切りは描かない。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class FxDraw {
    public static final int MAX_PRIMS = 96;
    public static final double CLEAR = 1.2;
    private static final boolean LOG = System.getenv("SKYABYSS_SKILLFX_LOG") != null;

    private static final ResourceLocation GLOW_TEX = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/fx/glow.png");
    private static final ResourceLocation BAND_TEX = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/fx/band.png");

    private static final List<Prim> PRIMS = new ArrayList<>();
    private static int lastFrameQuads;

    private FxDraw() {
    }

    public enum Kind { ARC, LINE, RING, DISC, FAN, BEAM, BOLT, SHELL }

    // 進み方
    public enum Ease {
        LINEAR, OUT, OUT3, IN2, SMOOTH;

        public double apply(double p) {
            return switch (this) {
                case LINEAR -> p;
                case OUT -> Math.sqrt(p);
                case OUT3 -> 1 - Math.pow(1 - p, 3);
                case IN2 -> p * p;
                case SMOOTH -> p * p * (3 - 2 * p);
            };
        }
    }

    // 一つの形。作るときはビルダーのように付けていく
    public static final class Prim {
        final Kind kind;
        long birth;
        int life = 10;
        int rgb = 0xFFFFFF;
        float alpha = 1f;
        double width = 0.2;
        double glow;
        double trail = 0.45;
        Ease ease = Ease.OUT3;
        // 形の材料 (種類ごとに使い方が違う)
        Vec3 a = Vec3.ZERO;
        Vec3 b = Vec3.ZERO;
        Vec3 u = new Vec3(1, 0, 0);
        Vec3 v = new Vec3(0, 1, 0);
        double r0;
        double r1;
        double a0;
        double a1;
        double h;
        long seed;
        double jag;
        // 扇は中心の向き (ラジアン・水平) と角度の半分
        double dir;
        double half;

        Prim(Kind kind) {
            this.kind = kind;
        }

        public static Prim arc(Vec3 center, Vec3 u, Vec3 v, double r, double from, double to) {
            Prim p = new Prim(Kind.ARC);
            p.a = center;
            p.u = u;
            p.v = v;
            p.r0 = r;
            p.a0 = from;
            p.a1 = to;
            return p;
        }

        public static Prim line(Vec3 from, Vec3 to) {
            Prim p = new Prim(Kind.LINE);
            p.a = from;
            p.b = to;
            return p;
        }

        public static Prim ring(Vec3 center, double r0, double r1) {
            Prim p = new Prim(Kind.RING);
            p.a = center;
            p.r0 = r0;
            p.r1 = r1;
            return p;
        }

        public static Prim disc(Vec3 center, double r0, double r1) {
            Prim p = new Prim(Kind.DISC);
            p.a = center;
            p.r0 = r0;
            p.r1 = r1;
            return p;
        }

        public static Prim fan(Vec3 center, double dir, double half, double r0, double r1) {
            Prim p = new Prim(Kind.FAN);
            p.a = center;
            p.dir = dir;
            p.half = half;
            p.r0 = r0;
            p.r1 = r1;
            return p;
        }

        public static Prim beam(Vec3 base, double h) {
            Prim p = new Prim(Kind.BEAM);
            p.a = base;
            p.h = h;
            return p;
        }

        public static Prim bolt(Vec3 from, Vec3 to, double jag, long seed) {
            Prim p = new Prim(Kind.BOLT);
            p.a = from;
            p.b = to;
            p.jag = jag;
            p.seed = seed;
            return p;
        }

        public static Prim shell(Vec3 center, double r0, double r1, double h) {
            Prim p = new Prim(Kind.SHELL);
            p.a = center;
            p.r0 = r0;
            p.r1 = r1;
            p.h = h;
            return p;
        }

        public Prim color(int rgb) {
            this.rgb = rgb;
            return this;
        }

        public Prim alpha(float a) {
            this.alpha = a;
            return this;
        }

        public Prim width(double w) {
            this.width = w;
            return this;
        }

        public Prim life(int ticks) {
            this.life = ticks;
            return this;
        }

        public Prim ease(Ease e) {
            this.ease = e;
            return this;
        }

        public Prim glow(double g) {
            this.glow = g;
            return this;
        }

        public Prim trail(double t) {
            this.trail = t;
            return this;
        }
    }

    // 形を一つ足す。上限を超えるときは捨てる (false)。cast の上限は SkillFx.Cast.prims で数える
    public static boolean add(SkillFx.Cast c, Prim p) {
        if (c.prims >= SkillFx.CAST_PRIMS || PRIMS.size() >= MAX_PRIMS) {
            return false;
        }
        p.birth = c.level.getGameTime();
        PRIMS.add(p);
        c.prims++;
        return true;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PRIMS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        long now = mc.level.getGameTime();
        PRIMS.removeIf(p -> now - p.birth > p.life + 2);
    }

    // ================================================================ 描く

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PRIMS.isEmpty()) {
            return;
        }
        long prof = Prof.begin();
        try {
            draw(event);
        } finally {
            Prof.end("client.skillfx", prof);
        }
    }

    private static void draw(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        long now = level.getGameTime();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        // Tesselator は一度に一つの作りしか持てないので、テクスチャごとに順に作って描く
        int quads = 0;
        quads += drawGroup(cam, pose, now, partial, true);
        quads += drawGroup(cam, pose, now, partial, false);
        lastFrameQuads = quads;
        if (LOG && quads > 0 && (now % 40) == 0) {
            SkyAbyss.LOGGER.info("[skillfx] frame prims {} quads {}", PRIMS.size(), quads);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // glow = true は円盤・扇 (glow.png)、false は帯 (band.png)。一つのテクスチャ分を作って描き、板の数を返す
    private static int drawGroup(Vec3 cam, Matrix4f pose, long now, float partial, boolean glow) {
        Painter g = new Painter(cam, pose);
        for (Prim p : PRIMS) {
            if ((p.kind == Kind.DISC || p.kind == Kind.FAN) != glow) {
                continue;
            }
            double t = (now - p.birth) + partial;
            if (t < 0 || t > p.life) {
                continue;
            }
            paint(g, p, Mth.clamp(t / p.life, 0, 1), now);
        }
        if (g.buf == null) {
            return 0;
        }
        MeshData mesh = g.buf.build();
        if (mesh == null) {
            return g.quads;
        }
        RenderSystem.setShaderTexture(0, glow ? GLOW_TEX : BAND_TEX);
        BufferUploader.drawWithShader(mesh);
        return g.quads;
    }

    // 種類ごとの描き分け。glow > 0 のときは同じ形を太く薄く重ねる
    private static void paint(Painter g, Prim p, double prog, long now) {
        double e = p.ease.apply(prog);
        double fade = Math.pow(1 - prog, 1.2) * p.alpha;
        if (fade <= 0.001) {
            return;
        }
        if (p.glow > 0) {
            Prim halo = copy(p);
            halo.width = p.width * (1 + p.glow);
            paintOne(g, halo, e, fade * 0.35, prog);
        }
        paintOne(g, p, e, fade, prog);
    }

    private static Prim copy(Prim p) {
        Prim c = new Prim(p.kind);
        c.birth = p.birth;
        c.life = p.life;
        c.rgb = p.rgb;
        c.alpha = p.alpha;
        c.width = p.width;
        c.glow = 0;
        c.trail = p.trail;
        c.ease = p.ease;
        c.a = p.a;
        c.b = p.b;
        c.u = p.u;
        c.v = p.v;
        c.r0 = p.r0;
        c.r1 = p.r1;
        c.a0 = p.a0;
        c.a1 = p.a1;
        c.h = p.h;
        c.seed = p.seed;
        c.jag = p.jag;
        c.dir = p.dir;
        c.half = p.half;
        return c;
    }

    private static void paintOne(Painter g, Prim p, double e, double fade, double prog) {
        switch (p.kind) {
            case ARC -> arc(g, p, e, fade);
            case LINE -> line(g, p, e, fade);
            case RING -> ring(g, p, e, fade);
            case DISC -> disc(g, p, e, fade);
            case FAN -> fan(g, p, e, fade);
            case BEAM -> beam(g, p, e, fade);
            case BOLT -> bolt(g, p, e, fade, prog);
            case SHELL -> shell(g, p, e, fade);
        }
    }

    // 弧: 頭 (e) が a0 から a1 へ進み、尾は trail だけ遅れる。幅は真ん中が太い三日月
    private static void arc(Painter g, Prim p, double e, double fade) {
        double span = p.a1 - p.a0;
        double dir = span < 0 ? -1 : 1;
        double head = p.a0 + span * e;
        double tail = head - dir * Math.abs(span) * p.trail;
        if ((tail - p.a0) * dir < 0) {
            tail = p.a0;
        }
        int n = 16;
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double ang = tail + (head - tail) * t;
            double c = Math.cos(ang);
            double s = Math.sin(ang);
            pts[i] = p.a.add(p.u.scale(c * p.r0)).add(p.v.scale(s * p.r0));
            w[i] = p.width * Math.pow(Math.sin(Math.PI * t), 0.6);
            al[i] = (float) (fade * (0.35 + 0.65 * t));
        }
        g.strip(pts, w, al, p.rgb, false);
    }

    // 直線: 頭が a から b へ進み、尾が遅れる
    private static void line(Painter g, Prim p, double e, double fade) {
        int n = 16;
        double tailT = Math.max(0, e - p.trail);
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            pts[i] = p.a.lerp(p.b, tailT + (e - tailT) * t);
            w[i] = p.width * (0.4 + 0.6 * t);
            al[i] = (float) (fade * t);
        }
        g.strip(pts, w, al, p.rgb, false);
    }

    // 地面の輪 (水平の帯。半径が r0 から r1 へ)
    private static void ring(Painter g, Prim p, double e, double fade) {
        double r = p.r0 + (p.r1 - p.r0) * e;
        int n = 16;
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double ang = Math.PI * 2 * i / n;
            pts[i] = p.a.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            w[i] = p.width;
            al[i] = (float) fade;
        }
        g.strip(pts, w, al, p.rgb, true);
    }

    // 地面の円盤 (中心から縁へ消える。半径が r0 から r1 へ)
    private static void disc(Painter g, Prim p, double e, double fade) {
        double r = p.r0 + (p.r1 - p.r0) * e;
        g.fan(p.a, 0, Math.PI, r, p.rgb, (float) fade, true);
    }

    private static void fan(Painter g, Prim p, double e, double fade) {
        double r = p.r0 + (p.r1 - p.r0) * e;
        g.fan(p.a, p.dir, p.half, r, p.rgb, (float) fade, false);
    }

    // 縦の光の柱: 底から高さ h へ伸び、上ほど淡い
    private static void beam(Painter g, Prim p, double e, double fade) {
        int n = 8;
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            pts[i] = p.a.add(0, p.h * e * t, 0);
            w[i] = p.width * (1 - 0.35 * t);
            al[i] = (float) (fade * (1 - 0.5 * t));
        }
        g.strip(pts, w, al, p.rgb, false);
    }

    // 稲妻: 直線の上に折れ目を置き、その折れ目が時間で揺れる (2 tick ごとに形が変わる)
    private static void bolt(Painter g, Prim p, double e, double fade, double prog) {
        int n = 10;
        long frame = (long) (prog * p.life) / 2;
        Vec3 dir = p.b.subtract(p.a);
        Vec3 side = Math.abs(dir.y) > 0.9 * dir.length() ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(dir).normalize();
        Vec3 perp2 = dir.cross(side).normalize();
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1];
        float[] al = new float[n + 1];
        int reach = (int) Math.round(e * n);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            long h = mix(p.seed * 31 + frame * 7 + i);
            double j = ((h & 1023) / 1023.0 * 2 - 1) * p.jag * Math.sin(Math.PI * t);
            double k = (((h >> 10) & 1023) / 1023.0 * 2 - 1) * p.jag * 0.5 * Math.sin(Math.PI * t);
            pts[i] = p.a.lerp(p.b, t).add(side.scale(j)).add(perp2.scale(k));
            w[i] = p.width * (i <= reach ? 1 : 0);
            al[i] = (float) (fade * (i <= reach ? 1 : 0));
        }
        g.strip(pts, w, al, p.rgb, false);
    }

    private static long mix(long x) {
        x ^= x >>> 17;
        x *= 0x9E3779B97F4A7C15L;
        x ^= x >>> 31;
        return x;
    }

    // 衝撃の殻: 中心から広がる縦の壁 (高さ h。上と下がぼやける)
    private static void shell(Painter g, Prim p, double e, double fade) {
        double r = p.r0 + (p.r1 - p.r0) * e;
        int n = 16;
        Vec3[] pts = new Vec3[n + 1];
        double[] w = new double[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double ang = Math.PI * 2 * i / n;
            pts[i] = p.a.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            w[i] = p.h;
            al[i] = (float) fade;
        }
        g.wall(pts, w, al, p.rgb);
    }

    // ================================================================ 頂点を積む

    private static final class Painter {
        final Vec3 cam;
        final Matrix4f pose;
        BufferBuilder buf;
        int quads;

        Painter(Vec3 cam, Matrix4f pose) {
            this.cam = cam;
            this.pose = pose;
        }

        private BufferBuilder buf() {
            if (buf == null) {
                buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            return buf;
        }

        // 点列の帯 (板を区切りごとに張る)。flat = 水平 (地面の輪)、vertical = 縦の壁 (幅 = 高さ、点は水平の輪)。
        // 板は TEX の縦 (v) を幅の方向に使う。stripe の両端はテクスチャの縁へ、中心は明るく
        void strip(Vec3[] pts, double[] w, float[] al, int rgb, boolean flat) {
            float r = ((rgb >> 16) & 255) / 255f;
            float gg = ((rgb >> 8) & 255) / 255f;
            float b = (rgb & 255) / 255f;
            int n = pts.length - 1;
            for (int i = 0; i < n; i++) {
                Vec3 p0 = pts[i];
                Vec3 p1 = pts[i + 1];
                if (nearCam(p0) && nearCam(p1)) {
                    continue;
                }
                Vec3 mid = p0.lerp(p1, 0.5);
                Vec3 dir = p1.subtract(p0);
                if (dir.lengthSqr() < 1e-8) {
                    continue;
                }
                // 水平の輪は上向きの法線、それ以外はカメラに向く板
                Vec3 side = flat ? dir.cross(new Vec3(0, 1, 0)) : dir.cross(cam.subtract(mid));
                if (side.lengthSqr() < 1e-10) {
                    continue;
                }
                side = side.normalize();
                double w0 = w[i] * 0.5;
                double w1 = w[i + 1] * 0.5;
                Vec3 s0 = side.scale(w0);
                Vec3 s1 = side.scale(w1);
                float a0 = al[i];
                float a1 = al[i + 1];
                vertex(p0.subtract(s0), 0f, 0f, a0, r, gg, b);
                vertex(p1.subtract(s1), 0f, 1f, a1, r, gg, b);
                vertex(p1.add(s1), 1f, 1f, a1, r, gg, b);
                vertex(p0.add(s0), 1f, 0f, a0, r, gg, b);
                quads++;
            }
        }

        // 縦の壁 (衝撃の殻): 点列の水平の輪を、高さ w (中心が a.y) の壁に伸ばす。v は高さ (中心が濃い)
        void wall(Vec3[] pts, double[] w, float[] al, int rgb) {
            float r = ((rgb >> 16) & 255) / 255f;
            float g = ((rgb >> 8) & 255) / 255f;
            float b = (rgb & 255) / 255f;
            for (int i = 0; i + 1 < pts.length; i++) {
                Vec3 p0 = pts[i];
                Vec3 p1 = pts[i + 1];
                if (nearCam(p0) && nearCam(p1)) {
                    continue;
                }
                double hh0 = w[i] * 0.5;
                double hh1 = w[i + 1] * 0.5;
                vertex(p0.add(0, -hh0, 0), 0f, 0f, al[i], r, g, b);
                vertex(p1.add(0, -hh1, 0), 0f, 1f, al[i + 1], r, g, b);
                vertex(p1.add(0, hh1, 0), 1f, 1f, al[i + 1], r, g, b);
                vertex(p0.add(0, hh0, 0), 1f, 0f, al[i], r, g, b);
                quads++;
            }
        }

        // 円盤・扇: 中心から、角 dir ± half の扇の区切り (テクスチャの中心は円の中心)
        void fan(Vec3 c, double dir, double half, double r, int rgb, float alpha, boolean full) {
            float rr = ((rgb >> 16) & 255) / 255f;
            float gg = ((rgb >> 8) & 255) / 255f;
            float bb = (rgb & 255) / 255f;
            int n = full ? 20 : 12;
            if (nearCam(c) && r < CLEAR) {
                return;
            }
            for (int i = 0; i < n; i++) {
                double a0 = full ? Math.PI * 2 * i / n : dir - half + 2 * half * i / n;
                double a1 = full ? Math.PI * 2 * (i + 1) / n : dir - half + 2 * half * (i + 1) / n;
                Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
                Vec3 p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                if (nearCam(p0) && nearCam(p1)) {
                    continue;
                }
                vertex(c, 0.5f, 0.5f, alpha, rr, gg, bb);
                vertex(p0, (float) (0.5 + 0.5 * Math.cos(a0)), (float) (0.5 + 0.5 * Math.sin(a0)), alpha, rr, gg, bb);
                vertex(p1, (float) (0.5 + 0.5 * Math.cos(a1)), (float) (0.5 + 0.5 * Math.sin(a1)), alpha, rr, gg, bb);
                vertex(c, 0.5f, 0.5f, alpha, rr, gg, bb);
                quads++;
            }
        }

        private boolean nearCam(Vec3 p) {
            double dx = p.x - cam.x;
            double dy = p.y - cam.y;
            double dz = p.z - cam.z;
            return dx * dx + dy * dy + dz * dz < CLEAR * CLEAR;
        }

        private void vertex(Vec3 p, float u, float v, float a, float r, float g, float b) {
            buf().addVertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                    .setUv(u, v).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
        }
    }

    // 直近のフレームで描いた板の数 (計測用・Prof と併用)
    public static int lastFrameQuads() {
        return lastFrameQuads;
    }
}
