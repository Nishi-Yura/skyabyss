package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.cinematic.CinematicClient;
import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.party.PartyLocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 仲間の位置の表示 (docs/design/10 の 10.9、18 の HUD の置き場)。サーバーから届いた位置を、画面の上に投影して描く:
// - 画面の中の仲間: 頭の上に名前と距離の小さな札 (壁の向こうでも出る。光る輪郭ではなく HUD)
// - 画面の外の仲間: 画面の端の小さな矢印と名前・距離
// - 別の場所 (別の次元) の仲間: 仲間の一覧 (PartyClient) の行に「別の場所: 〇〇」
// 場面の最中・画面を開いている間・F1 では出さない。設定 partyMarkers で消せる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PartyMarkers {
    private static List<PartyLocator.Entry> entries = List.of();

    private PartyMarkers() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_markers"), PartyMarkers::render);
        }
    }

    public static void receive(PartyLocator.Pos pos) {
        entries = pos.entries();
    }

    public static void clear() {
        entries = List.of();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    // 仲間の一覧の行に足す「別の場所」の一行。同じ場所・位置が来ていない人は null
    public static Component elsewhereLine(String memberId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        String here = mc.level.dimension().location().toString();
        for (PartyLocator.Entry e : entries) {
            if (e.id().equals(memberId) && !e.dimension().equals(here)) {
                return Component.translatable("hud.skyabyss.party_pos.elsewhere", dimensionName(e.dimension()));
            }
        }
        return null;
    }

    public static Component dimensionName(String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        String path = id == null ? dimension : id.getPath();
        return Component.translatableWithFallback("hud.skyabyss.party_pos.dim." + path, path);
    }

    // 画面の外の矢印を置く長方形 (左上の目的の帯・右の仲間の一覧・下のホットバーを避ける)
    private static int[] arrowRect(int w, int h) {
        return new int[] { 14, 46, w - PartyClient.W - 44, h - 56 };
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (entries.isEmpty() || !Config.PARTY_MARKERS.get() || mc.player == null || mc.level == null || mc.options.hideGui
                || mc.screen != null || mc.getDebugOverlay().showDebugScreen() || CinematicClient.active()) {
            return;
        }
        Font font = mc.font;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float tanHalf = (float) Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        float partial = delta.getGameTimeDeltaPartialTick(false);
        String here = mc.level.dimension().location().toString();
        int[] rect = arrowRect(w, h);
        List<int[]> taken = new ArrayList<>();
        for (PartyLocator.Entry e : entries) {
            if (!e.dimension().equals(here)) {
                continue;
            }
            Vec3 pos = new Vec3(e.x() + 0.5, e.y() + 2.1, e.z() + 0.5);
            Player live = null;
            try {
                live = mc.level.getPlayerByUUID(java.util.UUID.fromString(e.id()));
            } catch (IllegalArgumentException ex) {
                // 控えの名前が壊れていても落ちない
            }
            if (live != null) {
                pos = live.getPosition(partial).add(0, live.getBbHeight() + 0.35, 0);
            }
            int dist = (int) Math.round(mc.player.position().distanceTo(pos));
            if (live != null && dist < 4) {
                continue;
            }
            Vec3 d = pos.subtract(camPos);
            float fwd = (float) (d.x * look.x + d.y * look.y + d.z * look.z);
            float upv = (float) (d.x * up.x + d.y * up.y + d.z * up.z);
            float rightv = -(float) (d.x * left.x + d.y * left.y + d.z * left.z);
            Component label = Component.literal(e.name() + "  " + dist + "m");
            int lw = font.width(label);
            if (fwd > 0.3f) {
                float sx = w / 2f + (rightv / fwd) / tanHalf * (h / 2f);
                float sy = h / 2f - (upv / fwd) / tanHalf * (h / 2f);
                if (sx > lw / 2f + 6 && sx < w - lw / 2f - 6 && sy > 14 && sy < h - 14) {
                    drawTag(g, font, label, Math.round(sx), Math.round(sy), lw, taken, e.healthPercent());
                    continue;
                }
            }
            // 画面の外: 中心から向きに延ばして、長方形の縁に矢印
            float vx = rightv;
            float vy = -upv;
            float len = Mth.sqrt(vx * vx + vy * vy);
            if (len < 1e-4f) {
                vx = 0;
                vy = 1;
                len = 1;
            }
            vx /= len;
            vy /= len;
            float cx = w / 2f;
            float cy = h / 2f;
            float hw = Math.max(1, vx > 0 ? rect[2] - cx : cx - rect[0]);
            float hh = Math.max(1, vy > 0 ? rect[3] - cy : cy - rect[1]);
            float t = Math.min(Math.abs(vx) < 1e-4f ? Float.MAX_VALUE : hw / Math.abs(vx), Math.abs(vy) < 1e-4f ? Float.MAX_VALUE : hh / Math.abs(vy));
            int ax = Math.round(cx + vx * t);
            int ay = Math.round(cy + vy * t);
            drawArrow(g, ax, ay, (float) Math.atan2(vy, vx) + (float) Math.PI / 2f, e.healthPercent());
            int lx = Mth.clamp(ax, lw / 2 + 4, w - PartyClient.W - 12 - lw / 2);
            int ly = ay + 8;
            if (ay > cy) {
                ly = ay - 18;
            }
            placeLabel(g, font, label, lx, ly, lw, taken);
        }
    }

    // 頭の上の札: 小さな三角 + 名前と距離。重なったら上へずらす
    private static void drawTag(GuiGraphics g, Font font, Component label, int sx, int sy, int lw, List<int[]> taken, int hp) {
        int y = sy - 14;
        int[] box = place(sx - lw / 2 - 3, y - 2, lw + 6, 12, taken, -12);
        g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], 0x58000000);
        g.drawString(font, label, box[0] + 3, box[1] + 2, TextStyle.alpha(0.88f) | TextStyle.BODY.color(), true);
        int ty = box[1] + box[3] + 1;
        int color = TextStyle.alpha(0.8f) | hpColor(hp);
        g.fill(sx - 2, ty, sx + 3, ty + 1, color);
        g.fill(sx - 1, ty + 1, sx + 2, ty + 2, color);
        g.fill(sx, ty + 2, sx + 1, ty + 3, color);
    }

    private static void placeLabel(GuiGraphics g, Font font, Component label, int cx, int y, int lw, List<int[]> taken) {
        int[] box = place(cx - lw / 2 - 3, y - 2, lw + 6, 12, taken, 12);
        g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], 0x58000000);
        g.drawString(font, label, box[0] + 3, box[1] + 2, TextStyle.alpha(0.88f) | TextStyle.CAPTION.color() | 0x202020, true);
    }

    // すでに置いたものと重ならない場所へ、step ずつずらして置く
    private static int[] place(int x, int y, int bw, int bh, List<int[]> taken, int step) {
        int[] box = { x, y, bw, bh };
        for (int i = 0; i < 6; i++) {
            boolean hit = false;
            for (int[] o : taken) {
                if (box[0] < o[0] + o[2] && o[0] < box[0] + box[2] && box[1] < o[1] + o[3] && o[1] < box[1] + box[3]) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                break;
            }
            box[1] += step;
        }
        taken.add(box);
        return box;
    }

    private static int hpColor(int hp) {
        return hp > 50 ? 0x9FE8B0 : hp > 20 ? 0xF2D27A : 0xF08A7A;
    }

    // 上向きの小さな三角を angle だけ回して描く
    private static void drawArrow(GuiGraphics g, int x, int y, float angle, int hp) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        int c = TextStyle.alpha(0.85f) | hpColor(hp);
        for (int j = 0; j < 9; j++) {
            int half = j / 2;
            g.fill(-half, -5 + j, half + 1, -4 + j, c);
        }
        pose.popPose();
    }
}
