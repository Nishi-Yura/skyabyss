package jp.revati.skyabyss.client.wheel;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 輪の選び画面。中心から見たマウスの向きで項目を選び、輪のキーを離すか左クリックで開く。中心に近い (DEAD 以内) と何も選ばない。
// 項目は上から時計回り。選んだ項目は金の縁で少し大きく、中心にその名前と、その画面のキーを出す
public final class WheelScreen extends Screen {
    static final int RADIUS = 92;
    static final int CELL = 22;
    static final int DEAD = 26;
    private static final int GOLD = 0xFFE7B54A;
    private static final int EDGE = 0xFF3A4A58;
    private static final int FILL = 0xE0141A22;
    private static final int FILL_ON = 0xF02A2618;

    private int selected = -1;
    private int age;
    // 撮影用: キーを押していなくても開いたまま、決めた項目を選んだ見た目にする (-1 = マウスのとおり)
    private boolean keep;
    private int forced = -1;

    public static WheelScreen forShots(int selected) {
        WheelScreen s = new WheelScreen();
        s.keep = true;
        s.forced = selected;
        return s;
    }

    public WheelScreen() {
        super(Component.translatable("gui.skyabyss.wheel.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        // 押してすぐ離した (2 tick 未満) ときは、輪を開いたままにする (クリックで選べる)
        if (!keep && age >= 2 && !WheelClient.held()) {
            choose();
        }
    }

    private void choose() {
        List<WheelClient.Entry> list = WheelClient.entries();
        if (selected >= 0 && selected < list.size()) {
            WheelClient.open(list.get(selected));
        } else if (age >= 6) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && selected >= 0) {
            WheelClient.open(WheelClient.entries().get(selected));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<WheelClient.Entry> list = WheelClient.entries();
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        int n = list.size();
        if (dx * dx + dy * dy >= DEAD * DEAD) {
            // 上 (−y) を 0 として時計回りの角
            double a = Math.atan2(dx, -dy);
            if (a < 0) {
                a += Math.PI * 2;
            }
            selected = (int) Math.floor((a + Math.PI / n) / (Math.PI * 2 / n)) % n;
        } else {
            selected = -1;
        }
        if (forced >= 0) {
            selected = forced;
        }
        Font font = this.font;
        // 輪の筋 (選んだ向きへ細い線)
        if (selected >= 0) {
            double a = Math.PI * 2 * selected / n;
            int ex = cx + (int) Math.round(Math.sin(a) * (RADIUS - CELL - 4));
            int ey = cy - (int) Math.round(Math.cos(a) * (RADIUS - CELL - 4));
            line(g, cx, cy, ex, ey, 0x99E7B54A);
        }
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.sin(a) * RADIUS);
            int y = cy - (int) Math.round(Math.cos(a) * RADIUS);
            boolean on = i == selected;
            int half = on ? CELL + 3 : CELL;
            g.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, on ? GOLD : EDGE);
            g.fill(x - half, y - half, x + half, y + half, on ? FILL_ON : FILL);
            g.pose().pushPose();
            float s = on ? 1.6f : 1.35f;
            g.pose().translate(x - 8 * s, y - 8 * s - 5, 0);
            g.pose().scale(s, s, 1f);
            g.renderItem(list.get(i).icon(), 0, 0);
            g.pose().popPose();
            Component name = Component.translatable("gui.skyabyss.wheel." + list.get(i).id());
            int w = font.width(name);
            float ts = w > half * 2 - 4 ? (half * 2 - 4) / (float) w : 1f;
            g.pose().pushPose();
            g.pose().translate(x, y + half - 11, 0);
            g.pose().scale(ts, ts, 1f);
            g.drawString(font, name, -w / 2, 0, on ? GOLD : 0xFFD8DEE6, true);
            g.pose().popPose();
        }
        // 中心: 選んだ項目の名前とキー。選んでいないときは使い方
        if (selected >= 0) {
            WheelClient.Entry e = list.get(selected);
            Component name = Component.translatable("gui.skyabyss.wheel." + e.id());
            g.drawCenteredString(font, name, cx, cy - 9, GOLD);
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.wheel.key", e.key().getTranslatedKeyMessage()), cx, cy + 3, 0xFF9AA6B2);
        } else {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.wheel.hint"), cx, cy - 4, 0xFF9AA6B2);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 2) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }
}
