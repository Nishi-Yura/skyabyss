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
    public static final int WIDTH = 230;
    private static final int ROW = 14;
    private static final int LIFE = 50;
    private static Component text = Component.empty();
    private static long receivedAt = Long.MIN_VALUE;

    private LampBandClient() {
    }

    public static void receive(LampBand.Payload p) {
        text = p.text();
        Minecraft mc = Minecraft.getInstance();
        receivedAt = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
    }

    // 開発用: いま出している文 (出ていなければ空)。台本のログ用
    public static String shownNow() {
        return visible(Minecraft.getInstance()) ? text.getString() : "";
    }

    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                && mc.level.getGameTime() - receivedAt <= LIFE && mc.level.getGameTime() >= receivedAt;
    }

    public static void requests(Minecraft mc, List<HudLayout.Request> out) {
        if (visible(mc)) {
            out.add(HudLayout.Request.of(HudLayout.Id.LAMP, HudLayout.Region.TOP_LEFT, WIDTH + 6, ROW - 2));
        }
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
        boolean quiet = mc.screen != null || mc.player.hurtTime > 0;
        float a = quiet ? 0.45f : 1f;
        g.fill(x - 3, y - 2, x + WIDTH + 3, y + ROW - 4, ((int) (0x50 * a)) << 24 | 0x0A0C10);
        g.fill(x - 3, y - 2, x - 2, y + ROW - 4, ((int) (255 * a)) << 24 | 0x7FD6D0);
        g.drawString(font, font.substrByWidth(text, WIDTH - 2).getString(), x + 1, y + 1, ((int) (255 * a)) << 24 | 0xE9E3D4, true);
    }
}
