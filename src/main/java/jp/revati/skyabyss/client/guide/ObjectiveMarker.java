package jp.revati.skyabyss.client.guide;

import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.cinematic.CinematicClient;
import jp.revati.skyabyss.client.hud.HudManager;
import jp.revati.skyabyss.client.opening.OpeningClient;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import jp.revati.skyabyss.client.titan.LampBandClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 目的の標 (docs/design/18 の 8.3 節): いまの目的の場所を、クエストの道しるべのように「ひし形の標 + 距離」で画面に出す。
//  - 画面の中なら、その場所の上に浮かぶ金のひし形と、下に距離。
//  - 画面の外なら、縁の近くにひし形と、その先の向きを指す山形。距離つき。
//  - メインの目的は金。追跡中のサブのクエストは青緑 (小さめ)。光海の迷路では、次に向かう中継点 (灯の台・苗床・出口) に同じ標を出す。
// どの場面でも同じ作り (帯の矢印 GuideArrow も同じ金色)。場面・画面・F1・序盤の目覚めの間と、5 ブロック未満 (迷路は 3) では出さない。設定 questArrow で消せる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ObjectiveMarker {
    private static final int GOLD = 0xFFD866;
    private static final int TEAL = 0x7FE0D8;
    private static final int DARK = 0xFF0A0C10;
    // 11 x 11 のひし形 (# = 塗る)
    private static final String[] DIAMOND = {
            ".....#.....",
            "....###....",
            "...#####...",
            "..#######..",
            ".#########.",
            "###########",
            ".#########.",
            "..#######..",
            "...#####...",
            "....###....",
            ".....#.....",
    };
    // 内側の明るい面 (左上の半分)
    private static final String[] GLEAM = {
            "...........",
            ".....#.....",
            "....##.....",
            "...##......",
            "..##.......",
            "...........",
            "...........",
            "...........",
            "...........",
            "...........",
            "...........",
    };

    private ObjectiveMarker() {
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "objective_marker"), ObjectiveMarker::render);
    }

    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                && Config.QUEST_ARROW.get() && !OpeningClient.awakeActive() && !CinematicClient.active();
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) {
            return;
        }
        Vec3 way = LampBandClient.waypoint();
        if (way != null) {
            // 光海の迷路: 次の中継点 (迷路の道のりで次のマス)。見通しのない迷路なので、近いマスでも出す
            draw(g, mc, way, GOLD, 1f, 3.0);
            return;
        }
        ObjectiveStrip.Resolved main = ObjectiveStrip.demo ? new ObjectiveStrip.Resolved(mc.player.blockPosition().offset(60, 3, -30), false) : ObjectiveStrip.resolve(mc);
        BlockPos mainPos = main.pos();
        if (mainPos != null) {
            draw(g, mc, Vec3.atCenterOf(mainPos), GOLD, 1f, 5.0);
        }
        ObjectiveStrip.Resolved sub = ObjectiveStrip.resolveTracked(mc);
        BlockPos subPos = sub.pos();
        if (subPos != null && (mainPos == null || !subPos.equals(mainPos))) {
            draw(g, mc, Vec3.atCenterOf(subPos), TEAL, 0.8f, 5.0);
        }
    }

    // 世界の位置 pos を投影して、標を描く。rgb = 色、k = 大きさの比、hideUnder = これより近いと出さない (ブロック)
    private static void draw(GuiGraphics g, Minecraft mc, Vec3 pos, int rgb, float k, double hideUnder) {
        Font font = mc.font;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        double dist = camPos.distanceTo(pos);
        if (dist < hideUnder) {
            return;
        }
        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float tanHalf = (float) Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        Vec3 d = pos.subtract(camPos);
        float fwd = (float) (d.x * look.x + d.y * look.y + d.z * look.z);
        float upv = (float) (d.x * up.x + d.y * up.y + d.z * up.z);
        float rightv = -(float) (d.x * left.x + d.y * left.y + d.z * left.z);
        String label = Math.round(dist) + "m";
        float pulse = 0.5f + 0.5f * (float) Math.sin(net.minecraft.Util.getMillis() / 380.0);
        float scale = k * (1f + 0.06f * pulse);
        float time = (net.minecraft.Util.getMillis() % 100000) / 1000f;
        boolean onScreen = false;
        int sx = 0;
        int sy = 0;
        if (fwd > 0.3f) {
            float fx = w / 2f + (rightv / fwd) / tanHalf * (h / 2f);
            float fy = h / 2f - (upv / fwd) / tanHalf * (h / 2f);
            int lw = font.width(label);
            if (fx > lw / 2f + 8 && fx < w - lw / 2f - 8 && fy > 18 && fy < h - 30) {
                onScreen = true;
                sx = Math.round(fx);
                sy = Math.round(fy - 6 - 3 * (float) Math.sin(time * 2.2));
            }
        }
        if (onScreen) {
            diamond(g, sx, sy, rgb, scale, 1f);
            g.drawString(font, label, sx - font.width(label) / 2, sy + (int) (7 * scale) + 3, 0xFF000000 | rgb, true);
            return;
        }
        // 画面の外: 縁に標と、その先の向きを指す山形。置き場は HudManager (板・バニラの部品は避ける)
        float vx = rightv;
        float vy = -upv;
        if (fwd <= 0.3f && Math.abs(vx) < 1e-3f && Math.abs(vy) < 1e-3f) {
            vy = 1f;
        }
        float len = Mth.sqrt(vx * vx + vy * vy);
        if (len < 1e-4f) {
            vx = 0;
            vy = 1;
            len = 1;
        }
        vx /= len;
        vy /= len;
        int[] edge = HudManager.edgePoint(vx, vy);
        int ex = Mth.clamp(edge[0], 14, w - 14);
        int ey = Mth.clamp(edge[1], 24, h - 36);
        diamond(g, ex, ey, rgb, scale, 0.95f);
        // 向きの山形 (標の外側)
        float ang = (float) Math.atan2(vy, vx) + (float) Math.PI / 2f;
        chevron(g, ex + Math.round(vx * 12 * scale), ey + Math.round(vy * 12 * scale), ang, rgb);
        int ty = ey + (vy > 0.3f ? -(int) (7 * scale) - 12 : (int) (7 * scale) + 3);
        g.drawString(font, label, Mth.clamp(ex - font.width(label) / 2, 3, w - font.width(label) - 3), ty, 0xFF000000 | rgb, true);
    }

    // ひし形: 暗い縁取り (1 画素ふくらませた同じ形) → 色 → 左上の明るい面
    static void diamond(GuiGraphics g, int cx, int cy, int rgb, float scale, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().scale(scale, scale, 1f);
        int half = DIAMOND.length / 2;
        int shadow = ((int) (a * 0.85f) << 24) | 0x05060A;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    paint(g, DIAMOND, -half + dx, -half + dy, shadow);
                }
            }
        }
        paint(g, DIAMOND, -half, -half, (a << 24) | rgb);
        paint(g, GLEAM, -half, -half, ((int) (a * 0.9f) << 24) | 0xFFF6D8);
        g.pose().popPose();
    }

    private static void paint(GuiGraphics g, String[] shape, int ox, int oy, int argb) {
        for (int row = 0; row < shape.length; row++) {
            String line = shape[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) == '#') {
                    int start = col;
                    while (col < line.length() && line.charAt(col) == '#') {
                        col++;
                    }
                    g.fill(ox + start, oy + row, ox + col, oy + row + 1, argb);
                } else {
                    col++;
                }
            }
        }
    }

    // 向きを指す山形 (^ の形。rad = 時計回りの角度で 0 = 真上)
    private static void chevron(GuiGraphics g, int cx, int cy, float rad, int rgb) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(rad));
        for (int i = 0; i < 4; i++) {
            g.fill(-i - 1, i - 1, -i, i + 2, DARK);
            g.fill(i, i - 1, i + 1, i + 2, DARK);
        }
        for (int i = 0; i < 4; i++) {
            g.fill(-i, i, -i + 1, i + 1, 0xFF000000 | rgb);
            g.fill(i, i, i + 1, i + 1, 0xFF000000 | rgb);
        }
        g.pose().popPose();
    }
}
