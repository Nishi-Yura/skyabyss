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
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            render0(g, delta);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("client.objectiveMarker", prof);
        }
    }

    static void render0(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) {
            return;
        }
        Vec3 way = LampBandClient.waypoint();
        if (way != null) {
            // 光海の迷路 (版 25): ひし形の標は出さない。道しるべは左上の目的の帯の大きな矢印と距離 (ObjectiveStrip)
            return;
        }
        ObjectiveStrip.Resolved main = ObjectiveStrip.demo ? new ObjectiveStrip.Resolved(mc.player.blockPosition().offset(60, 3, -30), false) : ObjectiveStrip.resolve(mc);
        BlockPos mainPos = main.pos();
        ObjectiveStrip.Resolved sub = ObjectiveStrip.resolveTracked(mc);
        BlockPos subPos = sub.pos();
        // 目的の場所 (洞の口など) の下の地下に入ったら、口のひし形は出さない (割れ殻の洞で、入っても入口にひし形が残ってうるさかった)
        if (mainPos != null && underNear(mc, mainPos)) {
            mainPos = null;
        }
        if (subPos != null && underNear(mc, subPos)) {
            subPos = null;
        }
        Placement pm = mainPos == null ? null : place(g, mc, Vec3.atCenterOf(mainPos), BASE, 5.0);
        Placement ps = subPos == null || subPos.equals(mainPos) ? null : place(g, mc, Vec3.atCenterOf(subPos), BASE * 0.8f, 5.0);
        // 同じ場所・近い場所・画面で重なる標は一つにまとめる。金が先で、青緑は小さな点で「サブもここ」と添える
        if (pm != null && ps != null && merged(pm, ps, mainPos, subPos)) {
            show(g, mc, pm, GOLD, true);
            return;
        }
        if (pm != null) {
            show(g, mc, pm, GOLD, false);
        }
        if (ps != null) {
            show(g, mc, ps, TEAL, false);
        }
    }

    // 目的の場所から横に 40 以内で、4 ブロックより下にいる (その場所の地下の部屋に入った)
    static boolean underNear(Minecraft mc, BlockPos target) {
        double dx = mc.player.getX() - (target.getX() + 0.5);
        double dz = mc.player.getZ() - (target.getZ() + 0.5);
        return dx * dx + dz * dz < 40 * 40 && mc.player.getY() < target.getY() - 4;
    }

    // 3 ブロック以内か、画面の上で重なる (中心が 16 画素以内) なら一つにする
    static boolean merged(Placement a, Placement b, BlockPos pa, BlockPos pb) {
        if (pa != null && pb != null && pa.distSqr(pb) <= 9) {
            return true;
        }
        return a.onScreen == b.onScreen && Math.abs(a.x - b.x) < 16 && Math.abs(a.y - b.y) < 16;
    }

    // 置き場 (画面の中: 標の中心、外: 縁の点と向き)
    record Placement(int x, int y, boolean onScreen, float vx, float vy, float scale, String label) {
    }

    static final float BASE = 0.7f;

    // 世界の位置 pos を投影する。k = 大きさの比、hideUnder = これより近いと出さない (ブロック)。出さないなら null
    private static Placement place(GuiGraphics g, Minecraft mc, Vec3 pos, float k, double hideUnder) {
        Font font = mc.font;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        double dist = camPos.distanceTo(pos);
        if (dist < hideUnder) {
            return null;
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
        if (fwd > 0.3f) {
            float fx = w / 2f + (rightv / fwd) / tanHalf * (h / 2f);
            float fy = h / 2f - (upv / fwd) / tanHalf * (h / 2f);
            int lw = font.width(label);
            if (fx > lw / 2f + 8 && fx < w - lw / 2f - 8 && fy > 18 && fy < h - 30) {
                return new Placement(Math.round(fx), Math.round(fy - 6 - 3 * (float) Math.sin(time * 2.2)), true, 0, 0, scale, label);
            }
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
        return new Placement(Mth.clamp(edge[0], 14, w - 14), Mth.clamp(edge[1], 24, h - 36), false, vx, vy, scale, label);
    }

    private static void show(GuiGraphics g, Minecraft mc, Placement p, int rgb, boolean withSide) {
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float scale = p.scale;
        int sx = p.x;
        int sy = p.y;
        if (p.onScreen) {
            diamond(g, sx, sy, rgb, scale, 1f);
            g.drawString(font, p.label, sx - font.width(p.label) / 2, sy + (int) (7 * scale) + 3, 0xFF000000 | rgb, true);
        } else {
            diamond(g, sx, sy, rgb, scale, 0.95f);
            float ang = (float) Math.atan2(p.vy, p.vx) + (float) Math.PI / 2f;
            chevron(g, sx + Math.round(p.vx * 12 * scale), sy + Math.round(p.vy * 12 * scale), ang, rgb);
            int ty = sy + (p.vy > 0.3f ? -(int) (7 * scale) - 12 : (int) (7 * scale) + 3);
            g.drawString(font, p.label, Mth.clamp(sx - font.width(p.label) / 2, 3, w - font.width(p.label) - 3), ty, 0xFF000000 | rgb, true);
        }
        if (withSide) {
            sideDot(g, sx + Math.round(6 * scale) + 2, sy - Math.round(6 * scale) - 1);
        }
    }

    // サブのクエストも同じ場所にあることを示す小さな青緑の輪 (5 x 5)
    static void sideDot(GuiGraphics g, int cx, int cy) {
        // 塗りだけの描きを一度に送る (fill ごとに描き出さない。出る絵は同じ)
        g.drawManaged(() -> {
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, DARK);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000 | TEAL);
            g.fill(cx, cy, cx + 1, cy + 1, DARK);
        });
    }

    // ひし形: 暗い縁取り (1 画素ふくらませた同じ形) → 色 → 左上の明るい面
    static void diamond(GuiGraphics g, int cx, int cy, int rgb, float scale, float alpha) {
        // 塗りだけの描きを一度に送る (fill ごとに描き出さない。出る絵は同じ)
        g.drawManaged(() -> {
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
        });
    }

    // 形ごとの '#' の横の連なり {行, 始まり, 終わり (含まない)}。一度だけ求める (ひし形は縁取りで 9 回描くので、毎回文字列をなめない)
    private static final java.util.Map<String[], int[]> RUNS = new java.util.IdentityHashMap<>();

    private static int[] runsOf(String[] shape) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int row = 0; row < shape.length; row++) {
            String line = shape[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) == '#') {
                    int start = col;
                    while (col < line.length() && line.charAt(col) == '#') {
                        col++;
                    }
                    out.add(row);
                    out.add(start);
                    out.add(col);
                } else {
                    col++;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void paint(GuiGraphics g, String[] shape, int ox, int oy, int argb) {
        int[] runs = RUNS.computeIfAbsent(shape, ObjectiveMarker::runsOf);
        for (int i = 0; i < runs.length; i += 3) {
            g.fill(ox + runs[i + 1], oy + runs[i], ox + runs[i + 2], oy + runs[i] + 1, argb);
        }
    }

    // 向きを指す山形 (^ の形。rad = 時計回りの角度で 0 = 真上)
    private static void chevron(GuiGraphics g, int cx, int cy, float rad, int rgb) {
        // 塗りだけの描きを一度に送る (fill ごとに描き出さない。出る絵は同じ)
        g.drawManaged(() -> {
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
        });
    }
}
