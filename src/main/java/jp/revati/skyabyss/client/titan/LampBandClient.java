package jp.revati.skyabyss.client.titan;

import java.util.List;

import jp.revati.skyabyss.hud.HudLayout;
import jp.revati.skyabyss.titan.dungeon.LampBand;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 光海の「灯 n / 3 …」の帯 (LampBand の受け口と描画)。左上の帯の、目的の帯の下に一行 (HudLayout の Id.LAMP)。
// 届いてから 50 tick (約 2.5 秒) で消える (サーバーは 1 秒ごとに送り直す)
public final class LampBandClient {
    private static final int LIFE = 50;
    private static Component text = Component.empty();
    private static long receivedAt = Long.MIN_VALUE;

    private LampBandClient() {
    }

    private static java.util.List<net.minecraft.world.phys.Vec3> route = java.util.List.of();

    // 目的までの迷路の道のり (届いて 2.5 秒のあいだ。なければ null)。床の線 (GuideTrailRenderer) が使う
    public static java.util.List<net.minecraft.world.phys.Vec3> route() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && receivedAt != Long.MIN_VALUE && !route.isEmpty()
                && mc.level.getGameTime() - receivedAt <= LIFE && mc.level.getGameTime() >= receivedAt ? route : null;
    }

    // 迷路の中にいて道のりが届いているか (目的の標・帯の矢印を出さない目印)
    public static net.minecraft.world.phys.Vec3 waypoint() {
        java.util.List<net.minecraft.world.phys.Vec3> r = route();
        return r == null ? null : r.get(0);
    }

    public static void receive(LampBand.Payload p) {
        text = p.text();
        route = p.route();
        Minecraft mc = Minecraft.getInstance();
        receivedAt = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
    }

    // 開発用: いま出している文 (出ていなければ空)。台本のログ用
    public static String shownNow() {
        return visible(Minecraft.getInstance()) ? text.getString() : "";
    }

    private static boolean visible(Minecraft mc) {
        // 一度も届いていない (receivedAt が初期値) ときと、文が空のときは出さない (空の青緑の枠が出ていた)
        return mc.player != null && mc.level != null && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                && receivedAt != Long.MIN_VALUE && !text.getString().isEmpty()
                && mc.level.getGameTime() - receivedAt <= LIFE && mc.level.getGameTime() >= receivedAt;
    }

    // 帯の幅は目的の帯と同じ (GUI 倍率が高い画面で伸びすぎない)。長い文は折り返して 3 行まで
    public static void requests(Minecraft mc, List<HudLayout.Request> out) {
        if (!visible(mc)) {
            return;
        }
        Font font = mc.font;
        String full = text.getString();
        int want = jp.revati.skyabyss.client.quest.ObjectiveStrip.bandWidth(mc);
        out.add(HudLayout.Request.of(HudLayout.Id.LAMP, HudLayout.Region.TOP_LEFT, HudLayout.Fit.keyed(List.of(full, want), (maxW, maxH) -> {
            int w = Math.min(want, maxW - 6);
            int n = Math.min(3, (maxH - 2) / 10);
            if (n < 1 || w < 60) {
                return null;
            }
            int lines = Math.min(n, jp.revati.skyabyss.lore.LoreWrap.wrap(full, w - 6, font::width).size());
            return new HudLayout.Size(w + 6, lines * 10 + 2);
        })));
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        HudLayout.Rect rect = jp.revati.skyabyss.client.hud.HudManager.rect(HudLayout.Id.LAMP);
        if (rect == null || !visible(mc)) {
            return;
        }
        Font font = mc.font;
        int x = rect.x() + 3;
        int y = rect.y() + 2;
        int w = rect.w() - 6;
        boolean quiet = mc.screen != null || mc.player.hurtTime > 0;
        float a = quiet ? 0.45f : 1f;
        List<String> lines = new java.util.ArrayList<>(jp.revati.skyabyss.lore.LoreWrap.wrap(text.getString(), w - 6, font::width));
        int max = Math.max(1, (rect.h() - 2) / 10);
        if (lines.size() > max) {
            lines = new java.util.ArrayList<>(lines.subList(0, max));
            lines.set(max - 1, font.plainSubstrByWidth(lines.get(max - 1), w - 6 - font.width("…")) + "…");
        }
        int bottom = y + lines.size() * 10 - 2;
        g.fill(x - 3, y - 2, x + w + 3, bottom, ((int) (0x50 * a)) << 24 | 0x0A0C10);
        g.fill(x - 3, y - 2, x - 2, bottom, ((int) (255 * a)) << 24 | 0x7FD6D0);
        int ty = y + 1;
        for (String line : lines) {
            g.drawString(font, line, x + 1, ty, ((int) (255 * a)) << 24 | 0xE9E3D4, true);
            ty += 10;
        }
    }
}
