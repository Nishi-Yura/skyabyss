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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 終章の結末のあとの空 (docs/design/50 の 4.4。本人の選んだ結末だけ。story/finale/FinaleEnding が EndingPayload で送る)。
// 雲の目と同じく「方向」に置く板 (空のすぐあとに描くので、地形と雲が手前に重なる):
//   壊す       北東の高い空に走る裂け目 (昼も夜も。夜は縁の光が強い)
//   受け入れる 巡礼者の翼が昼の空をゆっくり巡る (夜は薄い)
//   鎮める     何も足さない (空の色味と月の遠さは ChapterSky の段で)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EndingSkyClient {
    private static final ResourceLocation CRACK = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/environment/finale_crack.png");
    private static final ResourceLocation WING = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/environment/finale_wing.png");
    private static String ending = "";

    private EndingSkyClient() {
    }

    public static void receive(String e) {
        ending = e == null ? "" : e;
    }

    public static String ending() {
        return ending;
    }

    static Vec3 dir(double yawDeg, double elevDeg) {
        double y = Math.toRadians(yawDeg);
        double p = Math.toRadians(elevDeg);
        return new Vec3(-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || ending.isEmpty() || ending.equals("calm")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float angle = level.getTimeOfDay(partial);
        float day = Mth.clamp(Mth.cos(angle * Mth.TWO_PI) * 2f + 0.5f, 0f, 1f);
        float clear = 1f - level.getRainLevel(partial) * 0.7f;
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        double dist = Math.min(mc.options.getEffectiveRenderDistance() * 16.0 * 2.0, 600.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (ending.equals("break")) {
            // 裂け目: 幅 80 度、高さはその 0.2。夜ほど縁の光が強い
            float a = (0.75f + 0.25f * (1f - day)) * clear;
            RenderSystem.setShaderTexture(0, CRACK);
            plate(pose, dir(220, 48), dist, 80, 0.2f, 0f, 1f, 1f, 1f, a);
        } else if (ending.equals("accept")) {
            // 翼: 空を巡る (ゲーム内の 1 日で 3 周)。幅 56 度
            double yaw = (level.getGameTime() + partial) * 0.045 % 360.0;
            float a = (0.25f + 0.7f * day) * clear;
            RenderSystem.setShaderTexture(0, WING);
            plate(pose, dir(yaw, 52 + 6 * Math.sin(Math.toRadians(yaw * 2))), dist, 56, 0.8f, (float) Math.toRadians(yaw * 0.5 % 30 - 15), 1f, 0.97f, 0.9f, a);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // 方向 d の距離 dist に、見かけの幅 widthDeg 度・縦横比 aspect の板を、tilt だけ傾けて置く
    static void plate(Matrix4f pose, Vec3 d, double dist, double widthDeg, float aspect, float tilt, float r, float g, float b, float a) {
        Vec3 rel = d.scale(dist);
        float w = (float) (dist * Math.tan(Math.toRadians(widthDeg * 0.5)));
        float h = w * aspect;
        Vec3 flatRight = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 flatUp = flatRight.cross(d);
        Vec3 right = flatRight.scale(Math.cos(tilt)).add(flatUp.scale(Math.sin(tilt)));
        Vec3 up = flatUp.scale(Math.cos(tilt)).subtract(flatRight.scale(Math.sin(tilt)));
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(buf, pose, rel, right, up, -w, -h, 0, 1, r, g, b, a);
        vertex(buf, pose, rel, right, up, w, -h, 1, 1, r, g, b, a);
        vertex(buf, pose, rel, right, up, w, h, 1, 0, r, g, b, a);
        vertex(buf, pose, rel, right, up, -w, h, 0, 0, r, g, b, a);
        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    static void vertex(BufferBuilder buf, Matrix4f pose, Vec3 rel, Vec3 right, Vec3 up, float u, float v, float tu, float tv,
                       float r, float g, float b, float a) {
        float x = (float) (rel.x + right.x * u + up.x * v);
        float y = (float) (rel.y + right.y * u + up.y * v);
        float z = (float) (rel.z + right.z * u + up.z * v);
        buf.addVertex(pose, x, y, z).setUv(tu, tv).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
    }
}
