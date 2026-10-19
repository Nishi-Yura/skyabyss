package jp.revati.skyabyss.client.npc;

import java.util.HashMap;
import java.util.Map;

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
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcMarkers.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// NPC の頭の上の印 (サーバーが人ごとに決めて送る。npc/NpcMarkers)。その人の画面にしか出ない。
//   形: メイン (金) は「!」新しい話、「?」報告。サブ (青緑) は「!」を使わず、巻物 = 新しい頼み、チェックの巻物 = 報告。吹き出し = 新しい雑談 (16x24 のドット絵・暗い縁取り・やわらかい光)。ゆっくり上下し、新しい印は脈打つ
//   見え方: 遠くからも読める (距離に応じて大きく。64 ブロックで消える)。16 ブロックまでは壁越しでも見える。それより遠くは見通せるときだけ
//   描かない場面: 場面・画面・F1・序盤の目覚め・設定 npcMarkers が OFF
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class NpcMarkerClient {
    public static final double THROUGH_WALLS = 16.0;
    public static final double MAX_RANGE = 64.0;
    static final int GOLD = 0xF2C14E;
    static final int CYAN = 0x7FD6D0;
    static final int PALE = 0xE9E3D4;
    // 絵は 16 x 24 のドット絵 (tools/gen_npc_marker_icons.py)。暗い縁取り・明るい縁と影つき。光はやわらかい丸
    private static final String DIR = "textures/gui/npc_marker/";
    private static final ResourceLocation GLOW = tex("glow");
    private static final ResourceLocation BANG_GOLD = tex("bang_gold");
    private static final ResourceLocation SCROLL_TEAL = tex("scroll_teal");
    private static final ResourceLocation QUESTION_GOLD = tex("question_gold");
    private static final ResourceLocation SCROLL_CHECK_TEAL = tex("scroll_check_teal");
    private static final ResourceLocation CHAT = tex("chat");
    // 近くでの高さ (ブロック。絵の余白を含む 24 画素ぶん。見える形は約 0.55)
    private static final float ICON_H = 1.0f;
    private static final float ICON_W = ICON_H * 16f / 24f;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, DIR + name + ".png");
    }

    // 受け取った印 (NPC の entity id → 印) と、壁の向こうかどうかの記憶 (5 tick ごと)・濃さ
    private static final Map<Integer, Marker> MARKERS = new HashMap<>();
    private static final Map<Integer, Float> FADE = new HashMap<>();
    private static final Map<Integer, Boolean> SIGHT = new HashMap<>();
    private static int tick;

    private NpcMarkerClient() {
    }

    public static void receive(int[] ids, int[] markers) {
        MARKERS.clear();
        for (int i = 0; i < ids.length; i++) {
            Marker m = Marker.values()[Mth.clamp(markers[i], 0, Marker.values().length - 1)];
            if (m != Marker.NONE) {
                MARKERS.put(ids[i], m);
            }
        }
        FADE.keySet().retainAll(MARKERS.keySet());
        SIGHT.keySet().retainAll(MARKERS.keySet());
    }

    // 開発用 (撮影): いま見えている印の数
    public static int count() {
        return MARKERS.size();
    }

    public static Marker markerOf(int id) {
        return MARKERS.getOrDefault(id, Marker.NONE);
    }

    // 開発用 (撮影・自己テスト): 受け取ったものとして印を置く
    public static void putForTest(int id, Marker marker) {
        MARKERS.put(id, marker);
    }

    static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.options.hideGui && Config.NPC_MARKERS.get()
                && !OpeningClient.awakeActive() && !CinematicClient.active();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            onClientTick0(event);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("client.npcMarkerTick", prof);
        }
    }

    static void onClientTick0(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || MARKERS.isEmpty()) {
            return;
        }
        tick++;
        for (Map.Entry<Integer, Marker> e : MARKERS.entrySet()) {
            Entity entity = level.getEntity(e.getKey());
            if (!(entity instanceof NpcEntity npc)) {
                FADE.put(e.getKey(), 0f);
                continue;
            }
            Vec3 head = npc.position().add(0, npc.getBbHeight() + 0.6, 0);
            double dist = mc.player.getEyePosition().distanceTo(head);
            if ((tick + e.getKey()) % 5 == 0 || !SIGHT.containsKey(e.getKey())) {
                SIGHT.put(e.getKey(), dist <= THROUGH_WALLS || clear(level, mc.player, head));
            }
            boolean show = dist <= MAX_RANGE && SIGHT.getOrDefault(e.getKey(), false);
            float fade = FADE.getOrDefault(e.getKey(), 0f);
            FADE.put(e.getKey(), Mth.clamp(fade + (show ? 0.12f : -0.12f), 0f, 1f));
        }
    }

    private static boolean clear(ClientLevel level, Entity from, Vec3 to) {
        return level.clip(new ClipContext(from.getEyePosition(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from))
                .getType() == HitResult.Type.MISS;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKERS.clear();
        FADE.clear();
        SIGHT.clear();
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            onRenderLevel0(event);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("client.npcMarkerRender", prof);
        }
    }

    static void onRenderLevel0(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MARKERS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !visible(mc)) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector();
        Vector3f up = event.getCamera().getUpVector();
        Matrix4f pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = (level.getGameTime() + partial) / 20.0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 16 ブロックまでは壁越しでも見えるよう、奥行きは見ない (遠い印を隠すかどうかは onClientTick が決める)
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (Map.Entry<Integer, Marker> e : MARKERS.entrySet()) {
            float fade = FADE.getOrDefault(e.getKey(), 0f);
            Entity entity = level.getEntity(e.getKey());
            if (fade <= 0.01f || !(entity instanceof NpcEntity npc)) {
                continue;
            }
            Marker marker = e.getValue();
            Vec3 base = new Vec3(Mth.lerp(partial, npc.xo, npc.getX()), Mth.lerp(partial, npc.yo, npc.getY()), Mth.lerp(partial, npc.zo, npc.getZ()));
            double dist = cam.distanceTo(base);
            // 頭の上でゆっくり上下する (周期 2.5 秒ほど)。新しい・報告の印は、やわらかく脈打つ。遠いほど大きく (読める大きさを保つが、大きくなりすぎない)
            boolean urgent = marker != Marker.CHAT;
            double bob = Math.sin(time * 2.5 + e.getKey()) * (urgent ? 0.07 : 0.04);
            Vec3 center = base.add(0, npc.getBbHeight() + 0.45 + bob, 0);
            float scale = (float) Mth.clamp(dist / 10.0, 1.0, 2.4);
            float pulse = urgent ? 0.5f + 0.5f * (float) Math.sin(time * 3.2 + e.getKey()) : 0f;
            scale *= 1f + 0.06f * pulse;
            float alpha = fade * (float) Mth.clamp((MAX_RANGE - dist) / 20.0, 0.0, 1.0);
            int rgb = color(marker);
            // 光 (印の色のやわらかい丸。脈に合わせて明るさが変わる) → 絵
            float glowSize = ICON_H * scale * 1.9f;
            drawQuad(GLOW, pose, cam, left, up, center.add(0, 0, 0), ICON_H * scale * 0.5f, glowSize, glowSize, rgb, alpha * (urgent ? 0.30f + 0.22f * pulse : 0.16f));
            drawQuad(icon(marker), pose, cam, left, up, center, 0f, ICON_W * scale, ICON_H * scale, 0xFFFFFF, alpha);
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    static ResourceLocation icon(Marker marker) {
        return switch (marker) {
            case NEW_MAIN -> BANG_GOLD;
            case NEW_SIDE -> SCROLL_TEAL;
            case READY_MAIN -> QUESTION_GOLD;
            case READY_SIDE -> SCROLL_CHECK_TEAL;
            default -> CHAT;
        };
    }

    static int color(Marker marker) {
        return switch (marker) {
            case NEW_MAIN, READY_MAIN -> GOLD;
            case NEW_SIDE, READY_SIDE -> CYAN;
            default -> PALE;
        };
    }

    // カメラの向きの板に一枚描く。center の下端から centerLift だけ上が板の中心 (光は絵の中心に重ねる)
    private static void drawQuad(ResourceLocation texture, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 center, float centerLift,
                                 float w, float h, int rgb, float alpha) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        // 画面の右 = カメラの左ベクトルの逆。絵の下端が center、光は中心が center + centerLift
        float y0 = centerLift > 0 ? centerLift - h / 2f : 0f;
        float hw = w / 2f;
        corner(buf, pose, cam, left, up, center, -hw, y0, 0f, 1f, r, g, b, a);
        corner(buf, pose, cam, left, up, center, hw, y0, 1f, 1f, r, g, b, a);
        corner(buf, pose, cam, left, up, center, hw, y0 + h, 1f, 0f, r, g, b, a);
        corner(buf, pose, cam, left, up, center, -hw, y0 + h, 0f, 0f, r, g, b, a);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private static void corner(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 center, float x, float y, float u, float v,
                               int r, int g, int b, int a) {
        float px = (float) (center.x - cam.x) - left.x * x + up.x * y;
        float py = (float) (center.y - cam.y) - left.y * x + up.y * y;
        float pz = (float) (center.z - cam.z) - left.z * x + up.z * y;
        buf.addVertex(pose, px, py, pz).setUv(u, v).setColor(r, g, b, a);
    }
}
