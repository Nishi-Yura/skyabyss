package jp.revati.skyabyss.client.facility;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.cinematic.CinematicClient;
import jp.revati.skyabyss.client.opening.OpeningClient;
import jp.revati.skyabyss.facility.AttendantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 係の人の頭の上の施設の印 (docs/design/35 の 2 章、07 の 11 章)。16x24 の淡い骨の色のドット絵 (textures/gui/npc_marker/fac_<役>.png)。
// 誰にでも同じに見える (クライアントだけで決める)。クエストの印 (NpcMarkerClient) はこの上に出る (QUEST_LIFT だけ上げる)。
// 32 ブロックまで。ゆっくり上下はしない (クエストの印と見分ける)。描かない場面はクエストの印と同じ (場面・画面・F1・設定 npcMarkers が OFF)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class FacilityIconClient {
    public static final double RANGE = 32.0;
    // クエストの印を、施設の印のぶん上げる (ブロック)
    public static final double QUEST_LIFT = 0.62;
    private static final float ICON_H = 0.75f;
    private static final float ICON_W = ICON_H * 16f / 24f;

    private FacilityIconClient() {
    }

    static ResourceLocation icon(String role) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/npc_marker/fac_" + role + ".png");
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.screen != null || mc.options.hideGui || !Config.NPC_MARKERS.get()
                || OpeningClient.awakeActive() || CinematicClient.active()) {
            return;
        }
        var list = level.getEntitiesOfClass(AttendantEntity.class, new AABB(mc.player.blockPosition()).inflate(RANGE));
        if (list.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector();
        Vector3f up = event.getCamera().getUpVector();
        Matrix4f pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (AttendantEntity npc : list) {
            Vec3 base = new Vec3(Mth.lerp(partial, npc.xo, npc.getX()), Mth.lerp(partial, npc.yo, npc.getY()), Mth.lerp(partial, npc.zo, npc.getZ()));
            double dist = cam.distanceTo(base);
            if (dist > RANGE) {
                continue;
            }
            float alpha = (float) Mth.clamp((RANGE - dist) / 8.0, 0.0, 1.0) * 0.92f;
            float scale = (float) Mth.clamp(dist / 12.0, 1.0, 1.8);
            // 深淵の生き残りは施設の人ではないので印を出さない (fac_survivor_* の絵はない)。荷運びのロクは品を取り替えるので道具屋の印
            String role = npc.roleId();
            if (AttendantEntity.extraRole(role)) {
                if (!role.equals("abyss_porter")) {
                    continue;
                }
                role = "tool_merchant";
            }
            Vec3 center = base.add(0, npc.getBbHeight() + 0.32, 0);
            quad(icon(role), pose, cam, left, up, center, ICON_W * scale, ICON_H * scale, alpha);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(ResourceLocation tex, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 c, float w, float h, float alpha) {
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        float hw = w / 2f;
        corner(buf, pose, cam, left, up, c, -hw, 0, 0f, 1f, a);
        corner(buf, pose, cam, left, up, c, hw, 0, 1f, 1f, a);
        corner(buf, pose, cam, left, up, c, hw, h, 1f, 0f, a);
        corner(buf, pose, cam, left, up, c, -hw, h, 0f, 0f, a);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private static void corner(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 c, float x, float y, float u, float v, int a) {
        float px = (float) (c.x - cam.x) - left.x * x + up.x * y;
        float py = (float) (c.y - cam.y) - left.y * x + up.y * y;
        float pz = (float) (c.z - cam.z) - left.z * x + up.z * y;
        buf.addVertex(pose, px, py, pz).setUv(u, v).setColor(255, 255, 255, a);
    }
}
