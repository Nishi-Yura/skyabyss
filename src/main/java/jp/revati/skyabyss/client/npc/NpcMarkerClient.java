package jp.revati.skyabyss.client.npc;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
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
//   形: 「!」新しい話・頼み、「?」報告、小さな点 = 新しい雑談。金 = メイン、青緑 = サブ、点は淡い色
//   見え方: 遠くからも読める (距離に応じて大きく。64 ブロックで消える)。16 ブロックまでは壁越しでも見える。それより遠くは見通せるときだけ
//   描かない場面: 場面・画面・F1・序盤の目覚め・設定 npcMarkers が OFF
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class NpcMarkerClient {
    public static final double THROUGH_WALLS = 16.0;
    public static final double MAX_RANGE = 64.0;
    static final int GOLD = 0xF2C14E;
    static final int CYAN = 0x7FD6D0;
    static final int PALE = 0xE9E3D4;
    private static final int OUTLINE = 0x15110A;

    // 5 x 8 の画素の「!」「?」と 3 x 3 の点
    static final String[] BANG = {".###.", ".###.", ".###.", ".###.", "..#..", ".....", ".###.", ".###."};
    static final String[] QUESTION = {".###.", "##.##", "...##", "..##.", ".##..", ".....", ".##..", ".##.."};
    static final String[] DOT = {".#.", "###", ".#."};

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
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Map.Entry<Integer, Marker> e : MARKERS.entrySet()) {
            float fade = FADE.getOrDefault(e.getKey(), 0f);
            Entity entity = level.getEntity(e.getKey());
            if (fade <= 0.01f || !(entity instanceof NpcEntity npc)) {
                continue;
            }
            Marker marker = e.getValue();
            Vec3 base = new Vec3(Mth.lerp(partial, npc.xo, npc.getX()), Mth.lerp(partial, npc.yo, npc.getY()), Mth.lerp(partial, npc.zo, npc.getZ()));
            double dist = cam.distanceTo(base);
            // 頭の上でゆっくり上下する。遠いほど大きく (読める大きさを保つ)
            double bob = Math.sin(time * 3.0 + e.getKey()) * 0.05;
            Vec3 center = base.add(0, npc.getBbHeight() + 0.55 + bob, 0);
            float scale = (float) Mth.clamp(dist / 12.0, 1.0, 3.2);
            if (marker.ready()) {
                scale *= 1f + 0.06f * (float) Math.sin(time * 5.0);
            }
            float alpha = fade * (float) Mth.clamp((MAX_RANGE - dist) / 20.0, 0.0, 1.0);
            draw(buffer, pose, cam, left, up, center, scale, marker, alpha);
            any = true;
        }
        MeshData mesh = any ? buffer.build() : null;
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 16 ブロックまでは壁越しでも見えるよう、奥行きは見ない (遠い印を隠すかどうかは onClientTick が決める)
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    static String[] glyph(Marker marker) {
        return switch (marker) {
            case NEW_MAIN, NEW_SIDE -> BANG;
            case READY_MAIN, READY_SIDE -> QUESTION;
            default -> DOT;
        };
    }

    static int color(Marker marker) {
        return switch (marker) {
            case NEW_MAIN, READY_MAIN -> GOLD;
            case NEW_SIDE, READY_SIDE -> CYAN;
            default -> PALE;
        };
    }

    // 画素の絵を、カメラの向きの板に描く。中心の下端が center。縁取りは画素を一回り広げた暗い色
    private static void draw(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 center, float scale, Marker marker,
                             float alpha) {
        String[] g = glyph(marker);
        float cell = 0.055f * scale * (marker == Marker.CHAT ? 0.9f : 1f);
        int h = g.length;
        int w = g[0].length();
        int rgb = color(marker);
        for (int pass = 0; pass < 2; pass++) {
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    boolean on = g[row].charAt(col) == '#';
                    boolean edge = false;
                    if (pass == 0 && !on) {
                        for (int dr = -1; dr <= 1 && !edge; dr++) {
                            for (int dc = -1; dc <= 1; dc++) {
                                int r = row + dr;
                                int c = col + dc;
                                if (r >= 0 && r < h && c >= 0 && c < w && g[r].charAt(c) == '#') {
                                    edge = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (pass == 0 ? !(on || edge) : !on) {
                        continue;
                    }
                    // 列は中心から左右、行は下端から上へ (row 0 が一番上)
                    float cx = (col - (w - 1) / 2f) * cell;
                    float cy = (h - 1 - row) * cell;
                    float half = cell * 0.5f;
                    int color = pass == 0 ? OUTLINE : rgb;
                    quad(buf, pose, cam, left, up, center, cx, cy, half, color, pass == 0 ? alpha * 0.8f : alpha);
                }
            }
        }
    }

    private static void quad(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 center, float cx, float cy, float half,
                             int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        // 画面の右 = カメラの左ベクトルの逆
        corner(buf, pose, cam, left, up, center, cx - half, cy - half, r, g, b, a);
        corner(buf, pose, cam, left, up, center, cx + half, cy - half, r, g, b, a);
        corner(buf, pose, cam, left, up, center, cx + half, cy + half, r, g, b, a);
        corner(buf, pose, cam, left, up, center, cx - half, cy + half, r, g, b, a);
    }

    private static void corner(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 center, float x, float y, int r, int g,
                               int b, int a) {
        float px = (float) (center.x - cam.x) - left.x * x + up.x * y;
        float py = (float) (center.y - cam.y) - left.y * x + up.y * y;
        float pz = (float) (center.z - cam.z) - left.z * x + up.z * y;
        buf.addVertex(pose, px, py, pz).setColor(r, g, b, a);
    }
}
