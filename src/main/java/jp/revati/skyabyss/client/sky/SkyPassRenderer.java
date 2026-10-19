package jp.revati.skyabyss.client.sky;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.sky.SkyPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 通過の描画。
// 1. 地面: 地形に沿ってひと続きに落ちる影（段差の側面にも影を貼る）
// 2. 空: 雲の上を飛ぶ巨大な何かの暗い姿と、その下に広がる厚い雲の層。雲の切れ間からだけ姿が見える
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkyPassRenderer {
    private static final float SHADOW_ALPHA = 0.6f;
    private static final int GROUND_RADIUS = 112;
    // 姿と嵐の雲の層は、バニラの四角い雲より下に置く（四角い雲が姿に重なって穴のように見えるのを防ぐ）。
    // 見上げると、嵐の雲 → その切れ間の姿 → さらに上にバニラの雲、の順に重なる
    private static final float BODY_BELOW_CLOUDS = 10f;
    private static final float DECK_BELOW_CLOUDS = 26f;
    private static final int BODY_STEP = 4;
    private static final int DECK_STEP = 8;
    private static final int DECK_RADIUS = 320;

    // 姿の層の高さ (本体のバニラの雲の下 BODY_BELOW_CLOUDS を基準に、縁は上、芯は下)
    private static final float BODY_HALO_RISE = 5f;
    private static final float BODY_CORE_DROP = 7f;

    // 開発用 (台本の skycircle): 空の影の代わりに、決まった円の影 {中心 x, z, 半径}。縁の位置を決めて撮る (null = 本物)
    public static double[] testCircle;

    // 地点の影の濃さ (0〜1)。画素ごとの影も、昔の地面の板も、ここから引く
    static float shadowAt(SkyPass pass, double x, double z, double age) {
        if (testCircle != null) {
            double t = Mth.clamp(0.5 - (Math.hypot(x - testCircle[0], z - testCircle[1]) - testCircle[2]) / 10.0, 0.0, 1.0);
            return (float) (t * t * (3.0 - 2.0 * t));
        }
        return pass.shadow(x, z, age);
    }

    private static int[] heights = new int[0];
    private static float[] field = new float[0];

    private SkyPassRenderer() {
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        SkyPass pass = SkyPassClient.current();
        ClientLevel level = Minecraft.getInstance().level;
        float storm = SkyPassClient.storm();
        // 上から落ちる影 (空の影・巨神の影) は、画面の画素ごとに世界の位置で掛ける。姿や雲より先に (その下の景色に掛ける)
        if (level != null) {
            boolean skyShadow = pass != null && pass.isActive(level.getGameTime());
            OverheadShadow.render(event.getPoseStack().last().pose(), RenderSystem.getProjectionMatrix(), event.getCamera().getPosition(),
                    skyShadow ? pass : null,
                    skyShadow ? pass.age(level.getGameTime(), event.getPartialTick().getGameTimeDeltaPartialTick(false)) : 0.0);
        }
        if (pass == null || level == null || (!pass.isActive(level.getGameTime()) && storm < 0.01f)) {
            return;
        }
        double age = pass.age(level.getGameTime(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        RenderSystem.depthMask(false);
        if (!OverheadShadow.ready()) {
            // 画素ごとの影が使えないときだけ、従来の地面の板
            drawGroundShadow(level, pass, age, cam, pose);
        }

        float cloudHeight = level.effects().getCloudHeight();
        if (!Float.isNaN(cloudHeight) && storm > 0.01f) {
            float viewRange = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
            // 姿は奥行きを書き込み、その上にあるバニラの雲を隠す
            RenderSystem.depthMask(true);
            drawBody(pass, age, cam, pose, cloudHeight - BODY_BELOW_CLOUDS, storm, viewRange);
            RenderSystem.depthMask(false);
            drawCloudDeck(level, pass, age, cam, pose, cloudHeight - DECK_BELOW_CLOUDS, storm, viewRange);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawGroundShadow(ClientLevel level, SkyPass pass, double age, Vec3 cam, Matrix4f pose) {
        if (!pass.isActive(level.getGameTime())) {
            return;
        }
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        int minX = Mth.floor(Math.max(cx - SkyPass.REACH, cam.x - GROUND_RADIUS));
        int maxX = Mth.floor(Math.min(cx + SkyPass.REACH, cam.x + GROUND_RADIUS));
        int minZ = Mth.floor(Math.max(cz - SkyPass.REACH, cam.z - GROUND_RADIUS));
        int maxZ = Mth.floor(Math.min(cz + SkyPass.REACH, cam.z + GROUND_RADIUS));
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        // 周囲1マス分も含めて高さを読み、段差の側面を貼れるようにする
        int w = maxX - minX + 3;
        int d = maxZ - minZ + 3;
        if (heights.length < w * d) {
            heights = new int[w * d];
        }
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                int x = minX - 1 + i;
                int z = minZ - 1 + k;
                heights[i * d + k] = level.hasChunk(x >> 4, z >> 4)
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : Integer.MIN_VALUE;
            }
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int i = x - minX + 1;
                int k = z - minZ + 1;
                int h = heights[i * d + k];
                if (h == Integer.MIN_VALUE) {
                    continue;
                }
                float s = shadowAt(pass, x + 0.5, z + 0.5, age);
                if (s <= 0.01f) {
                    continue;
                }
                any = true;
                float a = s * SHADOW_ALPHA;
                float x0 = (float) (x - cam.x);
                float z0 = (float) (z - cam.z);
                float y = (float) (h + 0.02 - cam.y);
                shadowQuad(buffer, pose, x0, y, z0, x0, y, z0 + 1, x0 + 1, y, z0 + 1, x0 + 1, y, z0, a);
                // 隣が低ければ、その段差の壁にも影を落とす
                int west = heights[(i - 1) * d + k];
                int east = heights[(i + 1) * d + k];
                int north = heights[i * d + k - 1];
                int south = heights[i * d + k + 1];
                if (west != Integer.MIN_VALUE && west < h) {
                    float yb = (float) (west - cam.y);
                    shadowQuad(buffer, pose, x0 - 0.02f, y, z0, x0 - 0.02f, yb, z0, x0 - 0.02f, yb, z0 + 1, x0 - 0.02f, y, z0 + 1, a);
                }
                if (east != Integer.MIN_VALUE && east < h) {
                    float yb = (float) (east - cam.y);
                    shadowQuad(buffer, pose, x0 + 1.02f, y, z0, x0 + 1.02f, y, z0 + 1, x0 + 1.02f, yb, z0 + 1, x0 + 1.02f, yb, z0, a);
                }
                if (north != Integer.MIN_VALUE && north < h) {
                    float yb = (float) (north - cam.y);
                    shadowQuad(buffer, pose, x0, y, z0 - 0.02f, x0 + 1, y, z0 - 0.02f, x0 + 1, yb, z0 - 0.02f, x0, yb, z0 - 0.02f, a);
                }
                if (south != Integer.MIN_VALUE && south < h) {
                    float yb = (float) (south - cam.y);
                    shadowQuad(buffer, pose, x0, y, z0 + 1.02f, x0, yb, z0 + 1.02f, x0 + 1, yb, z0 + 1.02f, x0 + 1, y, z0 + 1.02f, a);
                }
            }
        }
        draw(buffer, any);
    }

    private static void shadowQuad(BufferBuilder b, Matrix4f m,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float x3, float y3, float z3, float x4, float y4, float z4, float a) {
        b.addVertex(m, x1, y1, z1).setColor(0.02f, 0.02f, 0.05f, a);
        b.addVertex(m, x2, y2, z2).setColor(0.02f, 0.02f, 0.05f, a);
        b.addVertex(m, x3, y3, z3).setColor(0.02f, 0.02f, 0.05f, a);
        b.addVertex(m, x4, y4, z4).setColor(0.02f, 0.02f, 0.05f, a);
    }

    // 雲の中の巨大な姿。下から見上げると、雲の切れ間に暗い影のかたまりが見える。
    // 輪郭までの距離を一度だけ格子で求め、高さの違う 3 層に重ねる (層の高さが違うので、歩くと層がずれて奥行きが出る):
    //   上の層 = 広くぼやけた、明るく半透明の縁 (ひれ・肢のうすい部分)
    //   中の層 = 本体。輪郭はノイズで崩れ、ゆっくりうねる
    //   下の層 = 芯。胴と翼の付け根だけが一段濃く沈む
    // 濃淡のノイズは姿に付いて動く (姿の座標で引く) ので、肌がうごめいて見える。すべて頂点の色で描くので軽い
    private static void drawBody(SkyPass pass, double age, Vec3 cam, Matrix4f pose, float height, float storm, float viewRange) {
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        int minX = Mth.floor((cx - SkyPass.REACH) / BODY_STEP) * BODY_STEP;
        int minZ = Mth.floor((cz - SkyPass.REACH) / BODY_STEP) * BODY_STEP;
        int cells = (int) (SkyPass.REACH * 2 / BODY_STEP) + 2;
        int n = cells + 1;
        float visible = Math.min(1f, storm * 1.4f);
        if (field.length < n * n * 3) {
            field = new float[n * n * 3];
        }
        double dx = pass.dirX();
        double dz = pass.dirZ();
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                double x = minX + i * BODY_STEP;
                double z = minZ + k * BODY_STEP;
                double rx = x - cx;
                double rz = z - cz;
                // 姿の座標 (前後 u、横 v)
                double u = rx * dx + rz * dz;
                double v = -rx * dz + rz * dx;
                int idx = (i * n + k) * 3;
                // 描画距離の外 (濃さが 0 になる) は、重い計算の前に 0 を入れる (掛けて 0 になるだけなので値は同じ)
                float fade = visible * distanceFade(x - cam.x, z - cam.z, viewRange);
                if (fade <= 0f) {
                    field[idx] = field[idx + 1] = field[idx + 2] = 0f;
                    continue;
                }
                double d = pass.outlineDistance(x, z, age);
                if (d > 90.0) {
                    field[idx] = field[idx + 1] = field[idx + 2] = 0f;
                    continue;
                }
                // 縁の崩れ (大きなうねり + 細かい毛羽立ち)、翼を伝わる波
                double slow = age * 0.012;
                d += (noise(u / 34.0 + slow, v / 34.0 - slow * 0.7) - 0.5) * 22.0;
                d += (noise(u / 11.0 - slow * 3.0 + 40.0, v / 11.0 + slow * 2.0) - 0.5) * 7.0;
                d += Math.sin(u * 0.05 - age * 0.09) * Math.min(1.0, Math.abs(v) / 90.0) * 4.5;
                double mottle = 0.72 + 0.28 * noise(u / 22.0 + 11.0 - slow * 1.5, v / 22.0 + 3.0 + slow);
                float halo = (float) smooth(0.5 - (d - 6.0) / 60.0) * 0.34f;
                float body = (float) smooth(0.5 - d / 20.0) * 0.82f * (float) mottle;
                float core = (float) smooth(0.5 - (d + 15.0) / 22.0) * 0.88f;
                field[idx] = halo * fade;
                field[idx + 1] = body * fade;
                field[idx + 2] = core * fade;
            }
        }
        // 上の層は奥行きを書き込まない (うすい縁がバニラの雲を四角く切り抜かないように)
        RenderSystem.depthMask(false);
        drawLayer(cam, pose, height + BODY_HALO_RISE, minX, minZ, cells, n, 0, 0.17f, 0.18f, 0.26f, 0.17f, 0.18f, 0.26f);
        RenderSystem.depthMask(true);
        drawLayer(cam, pose, height, minX, minZ, cells, n, 1, 0.10f, 0.10f, 0.15f, 0.04f, 0.04f, 0.08f);
        drawLayer(cam, pose, height - BODY_CORE_DROP, minX, minZ, cells, n, 2, 0.03f, 0.03f, 0.06f, 0.012f, 0.012f, 0.025f);
    }

    // 格子の 1 層を描く。色は α が薄い所 (縁) → 濃い所 (中) へ (r0,g0,b0 → r1,g1,b1)
    private static void drawLayer(Vec3 cam, Matrix4f pose, float height, int minX, int minZ, int cells, int n, int layer,
                                  float r0, float g0, float b0, float r1, float g1, float b1) {
        float y = (float) (height - cam.y);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int i = 0; i < cells; i++) {
            for (int k = 0; k < cells; k++) {
                float a00 = field[(i * n + k) * 3 + layer];
                float a01 = field[(i * n + k + 1) * 3 + layer];
                float a11 = field[((i + 1) * n + k + 1) * 3 + layer];
                float a10 = field[((i + 1) * n + k) * 3 + layer];
                if (a00 + a10 + a11 + a01 <= 0.01f) {
                    continue;
                }
                any = true;
                float x0 = (float) (minX + i * BODY_STEP - cam.x);
                float z0 = (float) (minZ + k * BODY_STEP - cam.z);
                layerVertex(buffer, pose, x0, y, z0, a00, r0, g0, b0, r1, g1, b1);
                layerVertex(buffer, pose, x0, y, z0 + BODY_STEP, a01, r0, g0, b0, r1, g1, b1);
                layerVertex(buffer, pose, x0 + BODY_STEP, y, z0 + BODY_STEP, a11, r0, g0, b0, r1, g1, b1);
                layerVertex(buffer, pose, x0 + BODY_STEP, y, z0, a10, r0, g0, b0, r1, g1, b1);
            }
        }
        draw(buffer, any);
    }

    private static void layerVertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float a,
                                    float r0, float g0, float b0, float r1, float g1, float b1) {
        float t = Math.min(1f, a * 1.5f);
        b.addVertex(m, x, y, z).setColor(Mth.lerp(t, r0, r1), Mth.lerp(t, g0, g1), Mth.lerp(t, b0, b1), Math.min(1f, a));
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    // 嵐の雲の層。ゆっくり流れ、切れ間からだけ上の姿が見える。呼び声の瞬間は内側から光る
    private static void drawCloudDeck(ClientLevel level, SkyPass pass, double age, Vec3 cam, Matrix4f pose, float height, float storm, float viewRange) {
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        int minX = Mth.floor((cam.x - DECK_RADIUS) / DECK_STEP) * DECK_STEP;
        int minZ = Mth.floor((cam.z - DECK_RADIUS) / DECK_STEP) * DECK_STEP;
        int cells = DECK_RADIUS * 2 / DECK_STEP;
        float y = (float) (height - cam.y);
        double drift = age * pass.speed() * 0.35;
        double driftX = pass.dirX() * drift;
        double driftZ = pass.dirZ() * drift;
        float glow = SkyPassClient.glow();
        float range = Math.min(viewRange, DECK_RADIUS);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        // 隣り合う区画は角を共有するので、角の値は (cells+1)^2 個を一度だけ求める (区画ごとに求めた値と同じ)
        int n1 = cells + 1;
        if (deckGrid.length < n1 * n1) {
            deckGrid = new float[n1 * n1];
        }
        for (int i = 0; i < n1; i++) {
            for (int k = 0; k < n1; k++) {
                deckGrid[i * n1 + k] = deckAlpha(minX + i * DECK_STEP, minZ + k * DECK_STEP, cx, cz, cam, driftX, driftZ, storm, range);
            }
        }
        for (int i = 0; i < cells; i++) {
            for (int k = 0; k < cells; k++) {
                double x = minX + i * DECK_STEP;
                double z = minZ + k * DECK_STEP;
                float a00 = deckGrid[i * n1 + k];
                float a10 = deckGrid[(i + 1) * n1 + k];
                float a11 = deckGrid[(i + 1) * n1 + k + 1];
                float a01 = deckGrid[i * n1 + k + 1];
                if (a00 + a10 + a11 + a01 <= 0.01f) {
                    continue;
                }
                any = true;
                double near = Math.hypot(x - cx, z - cz);
                float lit = glow * (float) Mth.clamp(1.0 - near / 220.0, 0.0, 1.0);
                // 姿の近くの雲は、上の影を受けて暗く沈む (光をさえぎられる)
                float dim = 1f - 0.55f * (float) Mth.clamp(1.0 - near / 170.0, 0.0, 1.0);
                float r = (0.30f + lit * 0.45f) * dim;
                float g = (0.31f + lit * 0.40f) * dim;
                float bl = (0.36f + lit * 0.55f) * dim;
                float x0 = (float) (x - cam.x);
                float z0 = (float) (z - cam.z);
                buffer.addVertex(pose, x0, y, z0).setColor(r, g, bl, a00);
                buffer.addVertex(pose, x0, y, z0 + DECK_STEP).setColor(r, g, bl, a01);
                buffer.addVertex(pose, x0 + DECK_STEP, y, z0 + DECK_STEP).setColor(r, g, bl, a11);
                buffer.addVertex(pose, x0 + DECK_STEP, y, z0).setColor(r, g, bl, a10);
            }
        }
        draw(buffer, any);
    }

    private static float[] deckGrid = new float[0];

    private static float deckAlpha(double x, double z, double cx, double cz, Vec3 cam, double driftX, double driftZ, float storm, float range) {
        // 描画距離の外は濃さ 0 (最後に距離の薄れを掛けるので 0 になる)。先に見て、重いノイズを避ける
        float dfade = distanceFade(x - cam.x, z - cam.z, range);
        if (dfade <= 0f) {
            return 0f;
        }
        // 姿の近くでは、雲が姿から押しのけられるように外へ流れる (雲の模様を中心から遠ざける向きにずらす)
        double ox = x - cx;
        double oz = z - cz;
        double near = Math.hypot(ox, oz);
        double push = 38.0 * Math.exp(-near / 90.0);
        double sx = near > 1.0 ? ox / near * push : 0.0;
        double sz = near > 1.0 ? oz / near * push : 0.0;
        double n = noise((x - driftX - sx) / 90.0, (z - driftZ - sz) / 90.0) * 0.65 + noise((x - driftX - sx) / 35.0 + 7.3, (z - driftZ - sz) / 35.0 - 2.1) * 0.35;
        // 嵐が強いほど雲が厚くなり、切れ間が減る
        double threshold = 0.62 - storm * 0.3;
        double cover = Mth.clamp((n - threshold) / 0.18, 0.0, 1.0);
        return (float) (cover * 0.85 * storm) * dfade;
    }

    // 描画距離の端に向かって薄くし、空との境目を目立たなくする
    private static float distanceFade(double dx, double dz, float range) {
        double d = Math.hypot(dx, dz) / range;
        return (float) Mth.clamp((1.0 - d) / 0.4, 0.0, 1.0);
    }

    // なめらかな値ノイズ (0〜1)
    private static double noise(double x, double z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double ux = fx * fx * (3 - 2 * fx);
        double uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz);
        double b = hash(ix + 1, iz);
        double c = hash(ix, iz + 1);
        double d = hash(ix + 1, iz + 1);
        return Mth.lerp(uz, Mth.lerp(ux, a, b), Mth.lerp(ux, c, d));
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static void draw(BufferBuilder buffer, boolean any) {
        MeshData mesh = buffer.build();
        if (mesh != null && any) {
            BufferUploader.drawWithShader(mesh);
        } else if (mesh != null) {
            mesh.close();
        }
    }
}
